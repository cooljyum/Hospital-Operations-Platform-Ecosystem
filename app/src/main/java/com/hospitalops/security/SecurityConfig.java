package com.hospitalops.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase 3 Step 3.1/3.2: Spring Security 폼+세션 로그인 + RBAC 설정.
 *
 * <p>DB 백엔드 인증은 {@link AppUserDetailsService}(UserDetailsService 빈) +
 * {@link #passwordEncoder()}(PasswordEncoder 빈) 조합만으로 Spring Boot가 자동으로
 * DaoAuthenticationProvider를 구성한다 — 별도의 AuthenticationManager 설정이 필요 없다.</p>
 *
 * <p>Step 3.2: 인가 규칙(authorizeHttpRequests)은 ACCESS_POLICY_RULES 테이블을 앱 기동
 * 시점에 1회 읽어 구성한다 — 하드코딩된 {@code hasRole(...)} 나열 대신 "url_pattern별로
 * 허용된 role_name 목록"을 DB에서 그룹핑해 {@code hasAnyAuthority(...)}로 매핑한다. 완전
 * 동적(요청마다 DB 조회)은 이 프로젝트 규모에서 과한 엔지니어링으로 판단해, "기동 시 로드 ->
 * 정적 SecurityFilterChain 구성"으로 절충했다(정책 변경은 여전히 테이블 row만 바꾸면 됨,
 * 반영은 재기동 시점).</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http,
			AccessPolicyRuleRepository accessPolicyRuleRepository) throws Exception {

		Map<String, List<String>> roleNamesByUrlPattern = accessPolicyRuleRepository.findByAllowedTrue().stream()
				.collect(Collectors.groupingBy(AccessPolicyRule::getUrlPattern,
						Collectors.mapping(AccessPolicyRule::getRoleName, Collectors.toList())));

		http.authorizeHttpRequests(auth -> {
			auth.requestMatchers("/login", "/adminlte/**").permitAll();
			roleNamesByUrlPattern.forEach((urlPattern, roleNames) ->
					auth.requestMatchers(urlPattern).hasAnyAuthority(roleNames.toArray(new String[0])));
			auth.anyRequest().authenticated();
		});

		http.formLogin(form -> form
				.loginPage("/login")
				.defaultSuccessUrl("/dashboard")
				.permitAll()
		);

		http.logout(LogoutConfigurer::permitAll);

		return http.build();
	}
}
