package com.hospitalops.reception;

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
class ReceptionControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesReceptionTableAndBothAccessPolicyRules() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name = 'RECEPTION'
				""", Integer.class);
		assertThat(tableCount).isEqualTo(1);

		var roles = jdbcTemplate.queryForList(
				"SELECT role_name FROM ACCESS_POLICY_RULES WHERE url_pattern = '/reception/**' ORDER BY role_name",
				String.class);
		assertThat(roles).containsExactly("ROLE_REGISTRAR", "ROLE_SYSTEM_ADMIN");
	}

	@ParameterizedTest(name = "{0} -> allowed={1}")
	@CsvSource({
			"admin, true",
			"physician, false",
			"nurse, false",
			"registrar, true",
			"auditor, false",
	})
	void onlyRegistrarAndSystemAdminCanAccessReceptionScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		mockMvc.perform(get("/reception").session(session))
				.andExpect(allowed ? status().isOk() : status().isForbidden());
	}

	@Test
	void registrarCreatesReceptionAndItAppearsInList() throws Exception {
		String patientNo = existingSyntheticPatientNo();
		String complaint = "RECEPTION-CREATE-" + UUID.randomUUID();
		MockHttpSession session = loginAs("registrar");

		mockMvc.perform(post("/reception").session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("receivedAt", "2026-08-05T09:30")
					.param("chiefComplaint", complaint)
					.param("status", "WAITING"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/reception"));

		MvcResult result = mockMvc.perform(get("/reception").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(patientNo).contains(complaint).contains("registrar");
	}

	@Test
	void registrarUpdatesReception() throws Exception {
		MockHttpSession session = loginAs("registrar");
		Long receptionId = createReception(session, "RECEPTION-UPDATE-" + UUID.randomUUID());
		String updatedComplaint = "RECEPTION-UPDATED-" + UUID.randomUUID();
		String patientNo = existingSyntheticPatientNo();

		mockMvc.perform(get("/reception/{id}/edit", receptionId).session(session))
				.andExpect(status().isOk());

		mockMvc.perform(post("/reception/{id}", receptionId).session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("receivedAt", "2026-08-05T10:45")
					.param("chiefComplaint", updatedComplaint)
					.param("status", "IN_PROGRESS"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/reception"));

		MvcResult result = mockMvc.perform(get("/reception").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(updatedComplaint).contains("IN_PROGRESS");
	}

	@Test
	void registrarDeletesReception() throws Exception {
		MockHttpSession session = loginAs("registrar");
		String complaint = "RECEPTION-DELETE-" + UUID.randomUUID();
		Long receptionId = createReception(session, complaint);

		mockMvc.perform(post("/reception/{id}/delete", receptionId).session(session).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/reception"));

		MvcResult result = mockMvc.perform(get("/reception").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(complaint);
	}

	private Long createReception(MockHttpSession session, String complaint) throws Exception {
		mockMvc.perform(post("/reception").session(session).with(csrf())
					.param("syntheticPatientNo", existingSyntheticPatientNo())
					.param("receivedAt", "2026-08-05T09:30")
					.param("chiefComplaint", complaint)
					.param("status", "WAITING"))
				.andExpect(status().is3xxRedirection());
		return jdbcTemplate.queryForObject(
				"SELECT reception_id FROM RECEPTION WHERE chief_complaint = ?", Long.class, complaint);
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
