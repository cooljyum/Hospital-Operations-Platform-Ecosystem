package com.hospitalops.batch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6 Step 6.1 acceptance criteria 검증(PLAN.md §8): "refresh job 실행 후 요약
 * 테이블 값이 원본 집계와 일치".
 *
 * <p>로컬 실제 MySQL(hospital_ops)에 통제된 마커 환자(source_patient_uuid에
 * "SUMMARY-IT-" 접두어)를 직접 시드해 VISIT 2건(1건은 방문 시각 없음)/LAB_RESULT 3건/
 * PRESCRIPTION 2건을 원본 테이블에 넣은 뒤 summaryRefreshJob을 실행하고, 요약 테이블
 * 값을 (a) 시드 시점에 예정한 값과, (b) 원본 테이블에 직접 실행한 집계 쿼리 결과와
 * 각각 대조한다. 배치 job이 JobRepository 트랜잭션(REQUIRES_NEW)을 쓰므로 SyncJob 계열
 * IT와 동일하게 {@code @Transactional} 롤백에 의존하지 않고, {@link AfterEach}에서
 * 시드한 행을 명시적으로 삭제한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SummaryRefreshJobIT {

	@Autowired
	private SummaryRefreshJobRunner jobRunner;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long seededPatientId;

	@AfterEach
	void cleanUp() {
		if (seededPatientId == null) {
			return;
		}
		jdbcTemplate.update("DELETE FROM PATIENT_VISIT_SUMMARY WHERE patient_id = ?", seededPatientId);
		jdbcTemplate.update("DELETE FROM LAB_RESULT WHERE patient_id = ?", seededPatientId);
		jdbcTemplate.update("DELETE FROM PRESCRIPTION WHERE patient_id = ?", seededPatientId);
		jdbcTemplate.update("DELETE FROM VISIT WHERE patient_id = ?", seededPatientId);
		jdbcTemplate.update("DELETE FROM PATIENT WHERE patient_id = ?", seededPatientId);
	}

	@Test
	void refreshJobMakesSummaryTableMatchDirectAggregationOverSeededData() throws Exception {
		seededPatientId = seedPatientWithKnownActivity();

		JobExecution execution = jobRunner.runOnce();
		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

		// (a) 시드 시점에 우리가 넣은 값과 정확히 일치하는지: VISIT 2건, LAB_RESULT 3건,
		// PRESCRIPTION 2건, 방문 시각은 2024-01-01 / 2024-03-15(순서 없이 넣었으므로
		// first/last가 실제로 MIN/MAX로 계산됐는지도 함께 검증).
		Map<String, Object> summaryRow = jdbcTemplate.queryForMap(
				"SELECT visit_count, first_visit_at, last_visit_at, lab_result_count, prescription_count " +
						"FROM PATIENT_VISIT_SUMMARY WHERE patient_id = ?",
				seededPatientId);

		assertThat(((Number) summaryRow.get("visit_count")).longValue()).isEqualTo(2L);
		assertThat(JdbcTemporalSupport.toLocalDateTime(summaryRow.get("first_visit_at")))
				.isEqualTo(LocalDateTime.of(2024, 1, 1, 9, 0));
		assertThat(JdbcTemporalSupport.toLocalDateTime(summaryRow.get("last_visit_at")))
				.isEqualTo(LocalDateTime.of(2024, 3, 15, 14, 30));
		assertThat(((Number) summaryRow.get("lab_result_count")).longValue()).isEqualTo(3L);
		assertThat(((Number) summaryRow.get("prescription_count")).longValue()).isEqualTo(2L);

		// (b) 원본 테이블에 직접 실행한 집계 쿼리와 요약 테이블 전체(모든 환자, 시드 데이터
		// 뿐 아니라 기존에 있던 환자 포함)를 대조 - refresh job이 "일부만" 맞춘 게 아니라
		// 전체 재계산이 원본 집계와 정확히 일치함을 확인한다.
		List<Map<String, Object>> mismatches = jdbcTemplate.queryForList("""
				SELECT s.patient_id
				FROM PATIENT_VISIT_SUMMARY s
				JOIN (
					SELECT p.patient_id,
						COALESCE(v.visit_count, 0) AS visit_count,
						v.first_visit_at,
						v.last_visit_at,
						COALESCE(l.lab_result_count, 0) AS lab_result_count,
						COALESCE(pr.prescription_count, 0) AS prescription_count
					FROM PATIENT p
					LEFT JOIN (SELECT patient_id, COUNT(*) AS visit_count,
						MIN(started_at) AS first_visit_at, MAX(started_at) AS last_visit_at
						FROM VISIT GROUP BY patient_id) v ON v.patient_id = p.patient_id
					LEFT JOIN (SELECT patient_id, COUNT(*) AS lab_result_count
						FROM LAB_RESULT GROUP BY patient_id) l ON l.patient_id = p.patient_id
					LEFT JOIN (SELECT patient_id, COUNT(*) AS prescription_count
						FROM PRESCRIPTION GROUP BY patient_id) pr ON pr.patient_id = p.patient_id
				) direct ON direct.patient_id = s.patient_id
				WHERE s.visit_count <> direct.visit_count
					OR s.lab_result_count <> direct.lab_result_count
					OR s.prescription_count <> direct.prescription_count
					OR NOT (s.first_visit_at <=> direct.first_visit_at)
					OR NOT (s.last_visit_at <=> direct.last_visit_at)
				""");
		assertThat(mismatches).as("요약 테이블 값이 원본 직접 집계와 다른 환자 목록").isEmpty();

		// 전체 환자 수만큼 요약 행이 존재하는지(방문 이력 없는 환자도 0건 행으로 채워짐).
		Long patientCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PATIENT", Long.class);
		Long summaryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PATIENT_VISIT_SUMMARY", Long.class);
		assertThat(summaryCount).isEqualTo(patientCount);
	}

	private Long seedPatientWithKnownActivity() {
		// 짧게 유지: synthetic_patient_no(VARCHAR(32))·LAB_RESULT.code(VARCHAR(50)) 등
		// 접미사가 붙는 컬럼에서 잘림(data truncation)이 나지 않도록 마커 자체를 짧게 만든다.
		String marker = "SUM-IT-" + UUID.randomUUID().toString().substring(0, 12);
		jdbcTemplate.update(
				"INSERT INTO PATIENT (source_patient_uuid, synthetic_patient_no, birth_date, gender, first_name, last_name) " +
						"VALUES (?, ?, '1980-01-01', 'F', 'SummaryTest', 'Patient')",
				marker, marker);
		Long patientId = jdbcTemplate.queryForObject(
				"SELECT patient_id FROM PATIENT WHERE source_patient_uuid = ?", Long.class, marker);

		Long visit1 = insertVisit(patientId, marker + "-V1", LocalDateTime.of(2024, 3, 15, 14, 30));
		Long visit2 = insertVisit(patientId, marker + "-V2", LocalDateTime.of(2024, 1, 1, 9, 0));

		insertLabResult(visit1, patientId, marker + "-LAB-1");
		insertLabResult(visit1, patientId, marker + "-LAB-2");
		insertLabResult(visit2, patientId, marker + "-LAB-3");

		insertPrescription(visit1, patientId, marker + "-RX-1");
		insertPrescription(visit2, patientId, marker + "-RX-2");

		return patientId;
	}

	private Long insertVisit(Long patientId, String encounterUuid, LocalDateTime startedAt) {
		jdbcTemplate.update(
				"INSERT INTO VISIT (patient_id, source_encounter_uuid, started_at, encounter_class) " +
						"VALUES (?, ?, ?, 'ambulatory')",
				patientId, encounterUuid, startedAt);
		return jdbcTemplate.queryForObject(
				"SELECT visit_id FROM VISIT WHERE source_encounter_uuid = ?", Long.class, encounterUuid);
	}

	private void insertLabResult(Long visitId, Long patientId, String code) {
		jdbcTemplate.update(
				"INSERT INTO LAB_RESULT (visit_id, patient_id, observed_at, code, result_value) " +
						"VALUES (?, ?, NOW(), ?, '1.0')",
				visitId, patientId, code);
	}

	private void insertPrescription(Long visitId, Long patientId, String medicationCode) {
		jdbcTemplate.update(
				"INSERT INTO PRESCRIPTION (visit_id, patient_id, medication_code, started_at) " +
						"VALUES (?, ?, ?, NOW())",
				visitId, patientId, medicationCode);
	}
}
