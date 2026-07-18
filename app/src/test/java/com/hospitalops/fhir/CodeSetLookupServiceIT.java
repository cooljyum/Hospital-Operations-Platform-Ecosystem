package com.hospitalops.fhir;

import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 Step 2.2 acceptance criteria 검증(PLAN.md §4): "코드셋 테이블 존재, 변환
 * 로직이 하드코딩 대신 이 테이블 참조". 로컬 실제 MySQL(hospital_ops, V6 Flyway
 * 마이그레이션으로 CODE_SET 시드 데이터가 이미 적용돼 있음)에 대해 직접 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CodeSetLookupServiceIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CodeSetLookupService lookupService;

	@Test
	void codeSetTableExistsAndIsSeededFromRealLabAndPrescriptionData() {
		Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CODE_SET", Long.class);
		assertThat(total).isGreaterThan(0);

		Long labRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM CODE_SET WHERE code_system = 'LAB_RESULT'", Long.class);
		Long prescriptionRows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM CODE_SET WHERE code_system = 'PRESCRIPTION'", Long.class);
		assertThat(labRows).isGreaterThan(0);
		assertThat(prescriptionRows).isGreaterThan(0);
	}

	@Test
	void toCodingResolvesKnownLoincCodeToLoincSystemFromCodeSetTable() {
		// 8462-4 = Diastolic Blood Pressure, 실제 LAB_RESULT 데이터에 109건 존재(V6 시드에 포함).
		Coding coding = lookupService.toCoding("LAB_RESULT", "8462-4", "무시되는 원본 설명");

		assertThat(coding.getSystem()).isEqualTo("http://loinc.org");
		assertThat(coding.getCode()).isEqualTo("8462-4");
		assertThat(coding.getDisplay()).isEqualTo("Diastolic Blood Pressure");
	}

	@Test
	void toCodingFallsBackToRawCodeAndDescriptionWhenNotInCodeSet() {
		Coding coding = lookupService.toCoding("LAB_RESULT", "NOT-A-REAL-CODE", "원본 설명 그대로");

		assertThat(coding.getSystem()).isNull();
		assertThat(coding.getCode()).isEqualTo("NOT-A-REAL-CODE");
		assertThat(coding.getDisplay()).isEqualTo("원본 설명 그대로");
	}
}
