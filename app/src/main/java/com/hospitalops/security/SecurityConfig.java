package com.hospitalops.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase 3 Step 3.1/3.2/3.3: Spring Security 폼+세션 로그인 + RBAC + break-glass 설정.
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
 *
 * <p>Step 3.3: {@code /emergency/**}는 ACCESS_POLICY_RULES에 없는(=평소 아무 역할도
 * 접근 불가한) 경로다. 커스텀 {@link AuthorizationManager}로, 세션에
 * {@link BreakGlassSessionAttributes#GRANTED} 플래그가 있을 때만 한시적으로 허용한다
 * ({@code com.hospitalops.breakglass.BreakGlassController}가 이 플래그를 세팅).</p>
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
			// Phase 7 Step 7.1: Prometheus 스크레이퍼는 로그인 세션 없이 호출한다 - 역할
			// 기반(ACCESS_POLICY_RULES) 대상이 아니라 /login, /adminlte/**와 같은 성격의
			// 하드코딩 permitAll 인프라 경로로 취급한다. /actuator/metrics는 여기 포함하지
			// 않는다 - 개별 메트릭 값을 드러내므로 ACCESS_POLICY_RULES(V14)로 감사자/
			// 전산관리자 전용으로 좁힌다(roleNamesByUrlPattern 루프가 처리).
			auth.requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll();
			roleNamesByUrlPattern.forEach((urlPattern, roleNames) ->
					auth.requestMatchers(urlPattern).hasAnyAuthority(roleNames.toArray(new String[0])));
			auth.requestMatchers("/emergency/**").access(breakGlassAuthorizationManager());
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

	/**
	 * /emergency/** 는 평소엔 아무도 접근할 수 없다(ACCESS_POLICY_RULES에 이 패턴에 대한
	 * row가 없음) — 오직 현재 세션에 break-glass 플래그가 세팅된 경우에만 허용한다.
	 * 이 플래그는 인증된 사용자가 {@code POST /breakglass/grant}(사유 필수)를 거쳐야만
	 * 얻을 수 있다.
	 */
	private AuthorizationManager<RequestAuthorizationContext> breakGlassAuthorizationManager() {
		return (authentication, context) -> {
			HttpServletRequest request = context.getRequest();
			HttpSession session = request.getSession(false);
			boolean granted = session != null
					&& Boolean.TRUE.equals(session.getAttribute(BreakGlassSessionAttributes.GRANTED));
			return new AuthorizationDecision(granted);
		};
	}
}
