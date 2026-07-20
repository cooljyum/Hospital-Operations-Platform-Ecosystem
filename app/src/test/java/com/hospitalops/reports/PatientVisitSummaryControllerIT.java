package com.hospitalops.reports;

import com.hospitalops.batch.SummaryRefreshJobRunner;
import com.hospitalops.security.SecurityDataSeeder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9 Step 9.1 acceptance criteria 검증(PLAN.md §11): "Phase 6.1 데이터 기반 화면 렌더".
 *
 * <p>로컬 실제 MySQL(hospital_ops)에 통제된 마커 환자(synthetic_patient_no에 "RPT-IT-"
 * 접두어)를 VISIT 2건/LAB_RESULT 3건/PRESCRIPTION 2건과 함께 시드하고, summaryRefreshJob을
 * 실제로 실행한 뒤 화면이 그 값을 원본 집계와 정확히 일치하게 보여주는지 확인한다
 * ({@link SummaryRefreshJobIT}가 이미 검증한 "요약 테이블 값이 원본 집계와 일치"를,
 * 이 화면이 그 값을 왜곡 없이 그대로 렌더한다는 것까지 재확인하는 셈이다).</p>
 *
 * <p>summaryRefreshJob은 전체 재계산(DELETE + INSERT ... SELECT) 배치라
 * {@code @Transactional} 롤백에 의존하지 않는다 - {@code com.hospitalops.batch.SummaryRefreshJobIT}와
 * 동일하게 {@link AfterEach}에서 시드한 행을 명시적으로 삭제한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PatientVisitSummaryControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SummaryRefreshJobRunner jobRunner;

	private Long seededPatientId;
	private String marker;

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
	void screenRendersSummaryValuesMatchingDirectAggregationAfterRefreshJob() throws Exception {
		seededPatientId = seedPatientWithKnownActivity();

		JobExecution execution = jobRunner.runOnce();
		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

		MockHttpSession session = loginAs("auditor");

		MvcResult result = mockMvc.perform(get("/reports/patient-visit-summary")
						.session(session)
						.param("q", marker))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();

		// 표시 ID(synthetic_patient_no)는 나오지만, 내부 불변 PK(patient_id)는 화면 어디에도
		// 노출되지 않아야 한다(deliverable.md §3.2 원칙).
		assertThat(body).contains(marker);
		assertThat(body).doesNotContain(">" + seededPatientId + "<");

		// 원본 집계: VISIT 2건, LAB_RESULT 3건, PRESCRIPTION 2건 - 테이블 셀 마크업 그대로 대조.
		assertThat(body).contains("<td>2</td>"); // 방문 건수 셀
		assertThat(body).contains("<td>3</td>"); // 검사 건수 셀
	}

	@ParameterizedTest(name = "{0} -> allowed={1}")
	@CsvSource({
			"admin, true",
			"auditor, true",
			"physician, false",
			"nurse, false",
			"registrar, false",
	})
	void onlyAuditorAndSystemAdminCanAccessReportScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		var expectedStatus = allowed ? status().isOk() : status().isForbidden();

		mockMvc.perform(get("/reports/patient-visit-summary").session(session))
				.andExpect(expectedStatus);
	}

	private Long seedPatientWithKnownActivity() {
		// 짧게 유지: synthetic_patient_no(VARCHAR(32)) 등 접미사가 붙는 컬럼에서 잘림
		// (data truncation)이 나지 않도록 마커 자체를 짧게 만든다.
		marker = "RPT-IT-" + UUID.randomUUID().toString().substring(0, 12);
		jdbcTemplate.update(
				"INSERT INTO PATIENT (source_patient_uuid, synthetic_patient_no, birth_date, gender, first_name, last_name) "
						+ "VALUES (?, ?, '1980-01-01', 'F', 'ReportTest', 'Patient')",
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
				"INSERT INTO VISIT (patient_id, source_encounter_uuid, started_at, encounter_class) "
						+ "VALUES (?, ?, ?, 'ambulatory')",
				patientId, encounterUuid, startedAt);
		return jdbcTemplate.queryForObject(
				"SELECT visit_id FROM VISIT WHERE source_encounter_uuid = ?", Long.class, encounterUuid);
	}

	private void insertLabResult(Long visitId, Long patientId, String code) {
		jdbcTemplate.update(
				"INSERT INTO LAB_RESULT (visit_id, patient_id, observed_at, code, result_value) "
						+ "VALUES (?, ?, NOW(), ?, '1.0')",
				visitId, patientId, code);
	}

	private void insertPrescription(Long visitId, Long patientId, String medicationCode) {
		jdbcTemplate.update(
				"INSERT INTO PRESCRIPTION (visit_id, patient_id, medication_code, started_at) "
						+ "VALUES (?, ?, ?, NOW())",
				visitId, patientId, medicationCode);
	}

	private MockHttpSession loginAs(String username) throws Exception {
		MvcResult result = mockMvc.perform(post("/login")
						.param("username", username)
						.param("password", SecurityDataSeeder.LOCAL_TEST_PASSWORD)
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andReturn();
		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).as("로그인 성공 시 세션이 발급되어야 함: " + username).isNotNull();
		return session;
	}
}
