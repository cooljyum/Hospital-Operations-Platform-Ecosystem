package com.hospitalops.nursing;

import com.hospitalops.security.SecurityDataSeeder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class NursingControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesNursingRecordTableAndBothAccessPolicyRules() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name = 'NURSING_RECORD'
				""", Integer.class);
		assertThat(tableCount).isEqualTo(1);

		var roles = jdbcTemplate.queryForList(
				"SELECT role_name FROM ACCESS_POLICY_RULES WHERE url_pattern = '/nursing/**' ORDER BY role_name",
				String.class);
		assertThat(roles).containsExactly("ROLE_NURSE", "ROLE_SYSTEM_ADMIN");
	}

	@ParameterizedTest(name = "{0} -> allowed={1}")
	@CsvSource({
			"admin, true",
			"physician, false",
			"nurse, true",
			"registrar, false",
			"auditor, false",
	})
	void onlyNurseAndSystemAdminCanAccessNursingScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		mockMvc.perform(get("/nursing").session(session))
				.andExpect(allowed ? status().isOk() : status().isForbidden());
	}

	@Test
	void nurseCreatesNursingRecordAndItAppearsInList() throws Exception {
		String patientNo = existingSyntheticPatientNo();
		String notes = "NURSING-CREATE-" + UUID.randomUUID();
		MockHttpSession session = loginAs("nurse");

		mockMvc.perform(post("/nursing").session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("recordedAt", "2026-08-05T09:30")
					.param("temperature", "36.5")
					.param("bloodPressure", "120/80")
					.param("pulse", "72")
					.param("notes", notes))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/nursing"));

		MvcResult result = mockMvc.perform(get("/nursing").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(patientNo).contains(notes).contains("nurse");
	}

	@Test
	void nurseUpdatesNursingRecord() throws Exception {
		MockHttpSession session = loginAs("nurse");
		Long nursingRecordId = createNursingRecord(session, "NURSING-UPDATE-" + UUID.randomUUID());
		String updatedNotes = "NURSING-UPDATED-" + UUID.randomUUID();
		String patientNo = existingSyntheticPatientNo();

		mockMvc.perform(get("/nursing/{id}/edit", nursingRecordId).session(session))
				.andExpect(status().isOk());

		mockMvc.perform(post("/nursing/{id}", nursingRecordId).session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("recordedAt", "2026-08-05T10:45")
					.param("temperature", "37.1")
					.param("bloodPressure", "130/85")
					.param("pulse", "80")
					.param("notes", updatedNotes))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/nursing"));

		MvcResult result = mockMvc.perform(get("/nursing").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(updatedNotes).contains("130/85");
	}

	@Test
	void nurseDeletesNursingRecord() throws Exception {
		MockHttpSession session = loginAs("nurse");
		String notes = "NURSING-DELETE-" + UUID.randomUUID();
		Long nursingRecordId = createNursingRecord(session, notes);

		mockMvc.perform(post("/nursing/{id}/delete", nursingRecordId).session(session).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/nursing"));

		MvcResult result = mockMvc.perform(get("/nursing").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(notes);
	}

	private Long createNursingRecord(MockHttpSession session, String notes) throws Exception {
		mockMvc.perform(post("/nursing").session(session).with(csrf())
					.param("syntheticPatientNo", existingSyntheticPatientNo())
					.param("recordedAt", "2026-08-05T09:30")
					.param("temperature", "36.5")
					.param("bloodPressure", "120/80")
					.param("pulse", "72")
					.param("notes", notes))
				.andExpect(status().is3xxRedirection());
		return jdbcTemplate.queryForObject(
				"SELECT nursing_record_id FROM NURSING_RECORD WHERE notes = ?", Long.class, notes);
	}

	private String existingSyntheticPatientNo() {
		return jdbcTemplate.queryForObject(
				"SELECT synthetic_patient_no FROM PATIENT ORDER BY patient_id LIMIT 1", String.class);
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
