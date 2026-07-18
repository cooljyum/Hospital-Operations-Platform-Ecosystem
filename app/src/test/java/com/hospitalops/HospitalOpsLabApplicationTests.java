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
		"spring.jpa.hibernate.ddl-auto=none",
		// Phase 3 Step 3.1: APP_USER/APP_ROLE 테이블이 없는 이 H2 컨텍스트에서
		// SecurityDataSeeder(ApplicationRunner)가 시딩을 시도해 컨텍스트 기동이 깨지는 것을 방지.
		"app.security.seed-enabled=false"
})
class HospitalOpsLabApplicationTests {

	@Test
	void contextLoads() {
	}

}
