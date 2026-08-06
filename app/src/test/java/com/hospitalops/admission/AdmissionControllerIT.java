package com.hospitalops.admission;

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
class AdmissionControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesAdmissionTableAndThreeAccessPolicyRules() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name = 'ADMISSION'
				""", Integer.class);
		assertThat(tableCount).isEqualTo(1);

		var roles = jdbcTemplate.queryForList(
				"SELECT role_name FROM ACCESS_POLICY_RULES WHERE url_pattern = '/admission/**' ORDER BY role_name",
				String.class);
		assertThat(roles).containsExactly("ROLE_NURSE", "ROLE_PHYSICIAN", "ROLE_SYSTEM_ADMIN");
	}

	@ParameterizedTest(name = "{0} -> allowed={1}")
	@CsvSource({
			"admin, true",
			"physician, true",
			"nurse, true",
			"registrar, false",
			"auditor, false",
	})
	void onlyNursePhysicianAndSystemAdminCanAccessAdmissionScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		mockMvc.perform(get("/admission").session(session))
				.andExpect(allowed ? status().isOk() : status().isForbidden());
	}

	@Test
	void nurseCreatesAdmissionAndItAppearsInList() throws Exception {
		String patientNo = existingSyntheticPatientNo();
		String reason = "ADMISSION-CREATE-" + UUID.randomUUID();
		MockHttpSession session = loginAs("nurse");

		mockMvc.perform(post("/admission").session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("ward", "3병동")
					.param("bedNo", "301-A")
					.param("admittedAt", "2026-08-05T09:30")
					.param("reason", reason)
					.param("status", "ADMITTED"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admission"));

		MvcResult result = mockMvc.perform(get("/admission").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(patientNo).contains(reason).contains("nurse");
	}

	@Test
	void nurseUpdatesAdmission() throws Exception {
		MockHttpSession session = loginAs("nurse");
		Long admissionId = createAdmission(session, "ADMISSION-UPDATE-" + UUID.randomUUID());
		String updatedReason = "ADMISSION-UPDATED-" + UUID.randomUUID();
		String patientNo = existingSyntheticPatientNo();

		mockMvc.perform(get("/admission/{id}/edit", admissionId).session(session))
				.andExpect(status().isOk());

		mockMvc.perform(post("/admission/{id}", admissionId).session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("ward", "4병동")
					.param("bedNo", "401-B")
					.param("admittedAt", "2026-08-05T10:45")
					.param("dischargedAt", "2026-08-06T11:00")
					.param("reason", updatedReason)
					.param("status", "DISCHARGED"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admission"));

		MvcResult result = mockMvc.perform(get("/admission").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(updatedReason).contains("DISCHARGED");
	}

	@Test
	void nurseDeletesAdmission() throws Exception {
		MockHttpSession session = loginAs("nurse");
		String reason = "ADMISSION-DELETE-" + UUID.randomUUID();
		Long admissionId = createAdmission(session, reason);

		mockMvc.perform(post("/admission/{id}/delete", admissionId).session(session).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admission"));

		MvcResult result = mockMvc.perform(get("/admission").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(reason);
	}

	private Long createAdmission(MockHttpSession session, String reason) throws Exception {
		mockMvc.perform(post("/admission").session(session).with(csrf())
					.param("syntheticPatientNo", existingSyntheticPatientNo())
					.param("ward", "3병동")
					.param("bedNo", "301-A")
					.param("admittedAt", "2026-08-05T09:30")
					.param("reason", reason)
					.param("status", "ADMITTED"))
				.andExpect(status().is3xxRedirection());
		return jdbcTemplate.queryForObject(
				"SELECT admission_id FROM ADMISSION WHERE reason = ?", Long.class, reason);
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
