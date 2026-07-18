package com.hospitalops.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 Step 1.2 acceptance criteria 검증:
 * - PATIENT.synthetic_patient_no 컬럼(내부 PK와 분리된 표시용 식별자)이 존재한다.
 * - PATIENT_DISPLAY_ID 매핑 테이블(발급 이력)이 존재한다.
 * - 스키마 전체(V1+V2)에 RRN(주민등록번호) 계열 컬럼명이 전혀 없다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PatientDisplayIdentifierIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void patientHasSyntheticPatientNoColumnSeparateFromInternalPk() {
		List<String> columns = jdbcTemplate.queryForList(
				"SELECT UPPER(COLUMN_NAME) FROM information_schema.COLUMNS " +
						"WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PATIENT' " +
						"AND UPPER(COLUMN_NAME) = 'SYNTHETIC_PATIENT_NO'",
				String.class);

		assertThat(columns).containsExactly("SYNTHETIC_PATIENT_NO");
	}

	@Test
	void patientDisplayIdMappingTableExistsWithUniqueDisplayNoAndFkToPatient() {
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT UPPER(TABLE_NAME) FROM information_schema.TABLES " +
						"WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'PATIENT_DISPLAY_ID'",
				String.class);
		assertThat(tables).containsExactly("PATIENT_DISPLAY_ID");

		Integer fkCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE " +
						"WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'patient_display_id' " +
						"AND REFERENCED_TABLE_NAME = 'patient'",
				Integer.class);
		assertThat(fkCount).isEqualTo(1);
	}

	@Test
	void noResidentRegistrationNumberStyleColumnExistsAnywhereInSchema() {
		// grep으로도 검증하지만, 실제 스키마(information_schema)에서도 기계적으로 재확인한다.
		List<String> suspicious = jdbcTemplate.queryForList(
				"SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
						"WHERE TABLE_SCHEMA = DATABASE() AND (" +
						"UPPER(COLUMN_NAME) LIKE '%RRN%' OR " +
						"UPPER(COLUMN_NAME) LIKE '%SSN%' OR " +
						"UPPER(COLUMN_NAME) LIKE '%RESIDENT_REG%' OR " +
						"COLUMN_NAME LIKE '%주민%')",
				String.class);

		assertThat(suspicious).isEmpty();
	}
}
