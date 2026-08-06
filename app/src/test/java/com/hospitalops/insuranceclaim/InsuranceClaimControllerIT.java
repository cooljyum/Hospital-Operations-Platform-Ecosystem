package com.hospitalops.insuranceclaim;

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
class InsuranceClaimControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesInsuranceClaimTableAndBothAccessPolicyRules() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name = 'INSURANCE_CLAIM'
				""", Integer.class);
		assertThat(tableCount).isEqualTo(1);

		var roles = jdbcTemplate.queryForList(
				"SELECT role_name FROM ACCESS_POLICY_RULES WHERE url_pattern = '/insurance-claim/**' ORDER BY role_name",
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
	void onlyRegistrarAndSystemAdminCanAccessInsuranceClaimScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		mockMvc.perform(get("/insurance-claim").session(session))
				.andExpect(allowed ? status().isOk() : status().isForbidden());
	}

	@Test
	void registrarCreatesInsuranceClaimAndItAppearsInList() throws Exception {
		String patientNo = existingSyntheticPatientNo();
		String insurerName = "INSURANCE-CREATE-" + UUID.randomUUID();
		MockHttpSession session = loginAs("registrar");

		mockMvc.perform(post("/insurance-claim").session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("insurerName", insurerName)
					.param("claimAmount", "123456.78")
					.param("claimStatus", "SUBMITTED")
					.param("submittedAt", "2026-08-05T09:30"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/insurance-claim"));

		MvcResult result = mockMvc.perform(get("/insurance-claim").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(patientNo).contains(insurerName).contains("registrar");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT processed_at FROM INSURANCE_CLAIM WHERE insurer_name = ?", Object.class, insurerName)).isNull();
	}

	@Test
	void registrarUpdatesInsuranceClaim() throws Exception {
		MockHttpSession session = loginAs("registrar");
		Long insuranceClaimId = createInsuranceClaim(session, "INSURANCE-UPDATE-" + UUID.randomUUID());
		String updatedInsurerName = "INSURANCE-UPDATED-" + UUID.randomUUID();
		String patientNo = existingSyntheticPatientNo();

		mockMvc.perform(get("/insurance-claim/{id}/edit", insuranceClaimId).session(session))
				.andExpect(status().isOk());

		mockMvc.perform(post("/insurance-claim/{id}", insuranceClaimId).session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("insurerName", updatedInsurerName)
					.param("claimAmount", "99999.99")
					.param("claimStatus", "APPROVED")
					.param("submittedAt", "2026-08-05T10:45")
					.param("processedAt", "2026-08-06T11:00"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/insurance-claim"));

		MvcResult result = mockMvc.perform(get("/insurance-claim").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(updatedInsurerName).contains("APPROVED");
	}

	@Test
	void registrarDeletesInsuranceClaim() throws Exception {
		MockHttpSession session = loginAs("registrar");
		String insurerName = "INSURANCE-DELETE-" + UUID.randomUUID();
		Long insuranceClaimId = createInsuranceClaim(session, insurerName);

		mockMvc.perform(post("/insurance-claim/{id}/delete", insuranceClaimId).session(session).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/insurance-claim"));

		MvcResult result = mockMvc.perform(get("/insurance-claim").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(insurerName);
	}

	private Long createInsuranceClaim(MockHttpSession session, String insurerName) throws Exception {
		mockMvc.perform(post("/insurance-claim").session(session).with(csrf())
					.param("syntheticPatientNo", existingSyntheticPatientNo())
					.param("insurerName", insurerName)
					.param("claimAmount", "123456.78")
					.param("claimStatus", "SUBMITTED")
					.param("submittedAt", "2026-08-05T09:30"))
				.andExpect(status().is3xxRedirection());
		return jdbcTemplate.queryForObject(
				"SELECT insurance_claim_id FROM INSURANCE_CLAIM WHERE insurer_name = ?", Long.class, insurerName);
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
