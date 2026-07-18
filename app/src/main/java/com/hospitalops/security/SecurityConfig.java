package com.hospitalops.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 3 Step 3.1: Spring Security 폼+세션 로그인 설정.
 *
 * <p>DB 백엔드 인증은 {@link AppUserDetailsService}(UserDetailsService 빈) +
 * {@link #passwordEncoder()}(PasswordEncoder 빈) 조합만으로 Spring Boot가 자동으로
 * DaoAuthenticationProvider를 구성한다 — 별도의 AuthenticationManager 설정이 필요 없다.</p>
 *
 * <p>인가 규칙(authorizeHttpRequests)은 Step 3.2에서 ACCESS_POLICY_RULES 테이블을 부팅 시
 * 읽어 동적으로 구성하도록 확장된다. 이 시점(3.1)에는 정적 규칙만 둔다: 로그인 페이지와
 * AdminLTE 정적 자산은 permitAll, 그 외 전부 인증 필요.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers("/login", "/adminlte/**").permitAll()
				.anyRequest().authenticated()
		);

		http.formLogin(form -> form
				.loginPage("/login")
				.defaultSuccessUrl("/dashboard")
				.permitAll()
		);

		http.logout(LogoutConfigurer::permitAll);

		return http.build();
	}
}
