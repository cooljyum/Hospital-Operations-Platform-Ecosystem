package com.hospitalops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Phase 0.4에서 docker-compose로 실제 MySQL 컨테이너가 붙기 전까지,
 * 이 스모크 테스트는 인메모리 H2로 컨텍스트 로딩만 확인하고 Flyway는 끈다.
 * 운영 프로필(application.yml)의 MySQL 접속 정보는 그대로 유지된다.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:hospital_ops_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.enabled=false",
		// Phase 3 Step 3.2: SecurityConfig의 SecurityFilterChain 빈이 부팅 시
		// AccessPolicyRuleRepository로 ACCESS_POLICY_RULES 테이블을 실제로 조회한다.
		// Flyway가 꺼진 이 H2 컨텍스트엔 그 테이블이 없으므로, ddl-auto=none 대신
		// create-drop으로 바꿔 Hibernate가 JPA 엔티티(AppUser/AppRole/AccessPolicyRule 등)
		// 기반으로 스키마를 자동 생성하게 한다(빈 테이블이라도 쿼리 자체는 성공해야 함).
		"spring.jpa.hibernate.ddl-auto=create-drop",
		// Phase 3 Step 3.1: APP_USER/APP_ROLE 테이블이 없는 이 H2 컨텍스트에서
		// SecurityDataSeeder(ApplicationRunner)가 시딩을 시도해 컨텍스트 기동이 깨지는 것을 방지.
		"app.security.seed-enabled=false"
})
class HospitalOpsLabApplicationTests {

	@Test
	void contextLoads() {
	}

}
