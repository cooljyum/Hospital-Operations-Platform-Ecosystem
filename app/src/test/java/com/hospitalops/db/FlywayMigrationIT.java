package com.hospitalops.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 Step 1.1 acceptance criteria 검증: 컨텍스트 로딩 시 Spring Boot의 자동 Flyway
 * 마이그레이션이 로컬 MySQL(hospital_ops)에 실제로 적용되고, PATIENT/VISIT/LAB_RESULT/
 * PRESCRIPTION 4개 테이블이 실존하는지 확인한다.
 *
 * application.yml의 기본 datasource(로컬 MySQL, hospital_ops/changeme)를 그대로 사용한다 —
 * H2로 대체하지 않는다(Phase 1의 목적 자체가 "진짜 DB에 진짜 스키마"이기 때문).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FlywayMigrationIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void fourLegacyHisTablesExistAfterFlywayMigration() {
		// 참고: Windows MySQL은 기본적으로 lower_case_table_names=1이라 테이블이 물리적으로
		// 소문자로 저장된다(CREATE TABLE PATIENT로 만들어도 실제로는 patient) — UPPER()로
		// 대소문자 무관하게 비교한다.
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT UPPER(TABLE_NAME) FROM information_schema.TABLES " +
						"WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) IN " +
						"('PATIENT','VISIT','LAB_RESULT','PRESCRIPTION')",
				String.class);

		assertThat(tables)
				.containsExactlyInAnyOrder("PATIENT", "VISIT", "LAB_RESULT", "PRESCRIPTION");
	}

	@Test
	void patientPrimaryKeyIsImmutableAutoIncrementBigint() {
		List<String> columnTypes = jdbcTemplate.queryForList(
				"SELECT COLUMN_TYPE FROM information_schema.COLUMNS " +
						"WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PATIENT' " +
						"AND COLUMN_NAME = 'patient_id' AND EXTRA LIKE '%auto_increment%'",
				String.class);

		assertThat(columnTypes).containsExactly("bigint");
	}
}
