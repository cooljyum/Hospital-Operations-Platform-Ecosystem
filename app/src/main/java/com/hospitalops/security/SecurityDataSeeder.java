package com.hospitalops.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 3 Step 3.1/3.2: 로컬 검증·통합테스트용 계정을 앱 기동 시 심는다.
 *
 * <p>PLAN.md Step 3.1이 명시적으로 허용한 대안 — BCrypt 해시를 시드 SQL에 하드코딩하는
 * 대신, 애플리케이션 부팅 시 {@link PasswordEncoder}로 인코딩해 없으면 생성한다. 이미
 * 존재하는 계정은 건드리지 않는다(멱등 — 재기동/재테스트마다 중복 생성되지 않음).</p>
 *
 * <p>H2 인메모리 + Flyway 비활성 조합으로 도는 기존 스모크 테스트(Phase 0의
 * {@code HospitalOpsLabApplicationTests}, {@code DashboardControllerTests})는
 * APP_USER/APP_ROLE 테이블 자체가 없으므로, 이 러너가 그 컨텍스트에서 그대로 실행되면
 * 예외로 컨텍스트 기동이 깨진다. {@code app.security.seed-enabled=false}로 그 테스트들의
 * 프로퍼티에서 명시적으로 꺼서 회피한다(기본값은 true).</p>
 */
@Component
@ConditionalOnProperty(name = "app.security.seed-enabled", havingValue = "true", matchIfMissing = true)
public class SecurityDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SecurityDataSeeder.class);

	/** 로컬 검증 전용 테스트 계정 공통 비밀번호. 운영 배포 대상이 아니다. */
	static final String LOCAL_TEST_PASSWORD = "ChangeMe123!";

	private final AppUserRepository appUserRepository;
	private final AppRoleRepository appRoleRepository;
	private final PasswordEncoder passwordEncoder;

	public SecurityDataSeeder(AppUserRepository appUserRepository, AppRoleRepository appRoleRepository,
			PasswordEncoder passwordEncoder) {
		this.appUserRepository = appUserRepository;
		this.appRoleRepository = appRoleRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		// Step 3.1: 폼 로그인 흐름을 실제로 검증할 최소 1개 계정.
		seedUserIfAbsent("admin", "ROLE_SYSTEM_ADMIN");
	}

	/**
	 * username 계정이 없으면 생성해 roleNames를 부여한다. 이미 있으면 아무것도 하지 않는다
	 * (기존 계정의 비밀번호·역할을 덮어쓰지 않음 — 로컬에서 수동으로 바꾼 값을 보존).
	 */
	void seedUserIfAbsent(String username, String... roleNames) {
		if (appUserRepository.existsByUsername(username)) {
			return;
		}

		AppUser user = new AppUser(username, passwordEncoder.encode(LOCAL_TEST_PASSWORD), true);
		for (String roleName : roleNames) {
			AppRole role = appRoleRepository.findByRoleName(roleName)
					.orElseThrow(() -> new IllegalStateException(
							"APP_ROLE에 " + roleName + " 이(가) 없습니다 — Flyway 마이그레이션 확인 필요"));
			user.addRole(role);
		}
		appUserRepository.save(user);
		log.info("로컬 테스트 계정 시드: username={}, roles={}", username, roleNames);
	}
}
