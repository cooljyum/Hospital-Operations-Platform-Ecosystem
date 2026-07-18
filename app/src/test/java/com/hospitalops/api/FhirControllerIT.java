package com.hospitalops.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 Step 2.4 acceptance criteria 검증(PLAN.md §4): "GET /fhir/Patient/{id} 등
 * 4개 리소스 조회 가능, 컨트롤러가 레거시 repository를 직접 참조하지 않음(변환 계층만
 * 참조)". 로컬 실제 MySQL(hospital_ops, Step 2.1~2.3에서 이미 FHIR_RESOURCE_CACHE가
 * 채워져 있음)에 대해 직접 확인한다.
 *
 * <p>이 애플리케이션에는 아직 SecurityConfig가 없어(Phase 3.1 이전) spring-boot-
 * starter-security의 기본 자동 설정이 이 엔드포인트에도 인증을 요구한다 — 인증 상태
 * 검증은 {@code @WithMockUser}로 한다(DashboardControllerTests와 동일한 패턴).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class FhirControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void unauthenticatedRequestIsNotAllowedThrough() throws Exception {
		String anyFhirId = firstFhirId("Patient");

		MvcResult result = mockMvc.perform(get("/fhir/Patient/{id}", anyFhirId)).andReturn();

		assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
	}

	@Test
	@WithMockUser
	void getPatientReturnsStoredFhirJsonForRealSyncedRow() throws Exception {
		String fhirId = firstFhirId("Patient");

		mockMvc.perform(get("/fhir/Patient/{id}", fhirId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"resourceType\":\"Patient\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"id\":\"" + fhirId + "\"")));
	}

	@Test
	@WithMockUser
	void getEncounterReturnsStoredFhirJsonForRealSyncedRow() throws Exception {
		String fhirId = firstFhirId("Encounter");

		mockMvc.perform(get("/fhir/Encounter/{id}", fhirId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"resourceType\":\"Encounter\"")));
	}

	@Test
	@WithMockUser
	void getObservationReturnsStoredFhirJsonForRealSyncedRow() throws Exception {
		String fhirId = firstFhirId("Observation");

		mockMvc.perform(get("/fhir/Observation/{id}", fhirId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"resourceType\":\"Observation\"")));
	}

	@Test
	@WithMockUser
	void getMedicationRequestReturnsStoredFhirJsonForRealSyncedRow() throws Exception {
		String fhirId = firstFhirId("MedicationRequest");

		mockMvc.perform(get("/fhir/MedicationRequest/{id}", fhirId).accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"resourceType\":\"MedicationRequest\"")));
	}

	@Test
	@WithMockUser
	void unknownIdReturnsNotFound() throws Exception {
		mockMvc.perform(get("/fhir/Patient/{id}", "does-not-exist-12345").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	/**
	 * Phase 2 보완: 독립 검증(Claude, 04dec26~fe662ab)에서 지적된 "search" 완결성 갭 해소.
	 * 하드코딩된 기대값이 아니라 FHIR_RESOURCE_CACHE.patient_fhir_id에 대한 실측 COUNT(*)와
	 * search 결과 Bundle의 total/entry 개수를 직접 대조한다(V7 마이그레이션 + SyncJob 재실행
	 * 으로 patient_fhir_id가 채워져 있어야 이 테스트가 통과한다).
	 */
	@Test
	@WithMockUser
	void searchEncounterByPatientMatchesActualCacheCountForRealPatient() throws Exception {
		String patientFhirId = firstFhirId("Patient");
		long expectedCount = countCacheRowsForPatient("Encounter", patientFhirId);
		assertThat(expectedCount).isGreaterThan(0L);

		MvcResult result = mockMvc.perform(get("/fhir/Encounter")
						.param("patient", patientFhirId)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"resourceType\":\"Bundle\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"searchset\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"total\":" + expectedCount)))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		long entryOccurrences = countOccurrences(body, "\"resourceType\":\"Encounter\"");
		assertThat(entryOccurrences).isEqualTo(expectedCount);
	}

	@Test
	@WithMockUser
	void searchObservationByPatientMatchesActualCacheCountForRealPatient() throws Exception {
		String patientFhirId = firstFhirId("Patient");
		long expectedCount = countCacheRowsForPatient("Observation", patientFhirId);
		assertThat(expectedCount).isGreaterThan(0L);

		mockMvc.perform(get("/fhir/Observation")
						.param("patient", patientFhirId)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"resourceType\":\"Bundle\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"total\":" + expectedCount)));
	}

	@Test
	@WithMockUser
	void searchMedicationRequestByPatientMatchesActualCacheCountForRealPatient() throws Exception {
		String patientFhirId = firstFhirId("Patient");
		long expectedCount = countCacheRowsForPatient("MedicationRequest", patientFhirId);
		assertThat(expectedCount).isGreaterThan(0L);

		mockMvc.perform(get("/fhir/MedicationRequest")
						.param("patient", patientFhirId)
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"resourceType\":\"Bundle\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"total\":" + expectedCount)));
	}

	@Test
	@WithMockUser
	void searchWithUnknownPatientReturnsEmptyBundleNotNotFound() throws Exception {
		mockMvc.perform(get("/fhir/Encounter")
						.param("patient", "patient-does-not-exist-99999")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"resourceType\":\"Bundle\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"total\":0")));
	}

	private long countCacheRowsForPatient(String resourceType, String patientFhirId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM FHIR_RESOURCE_CACHE WHERE resource_type = ? AND patient_fhir_id = ?",
				Long.class, resourceType, patientFhirId);
		return count == null ? 0 : count;
	}

	private static long countOccurrences(String haystack, String needle) {
		long count = 0;
		int index = 0;
		while ((index = haystack.indexOf(needle, index)) != -1) {
			count++;
			index += needle.length();
		}
		return count;
	}

	private String firstFhirId(String resourceType) {
		java.util.List<String> ids = jdbcTemplate.queryForList(
				"SELECT fhir_id FROM FHIR_RESOURCE_CACHE WHERE resource_type = ? ORDER BY id LIMIT 1",
				String.class, resourceType);
		if (ids.isEmpty()) {
			throw new IllegalStateException(
					"FHIR_RESOURCE_CACHE에 resource_type=" + resourceType + " row가 없습니다 — "
							+ "먼저 fhirSyncJob(Step 2.1)을 실행해야 합니다.");
		}
		return ids.get(0);
	}
}
