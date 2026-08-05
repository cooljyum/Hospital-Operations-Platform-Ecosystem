package com.hospitalops.laborder;

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
class LabOrderControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesLabOrderTableAndAllAccessPolicyRules() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name = 'LAB_ORDER'
				""", Integer.class);
		assertThat(tableCount).isEqualTo(1);

		var roles = jdbcTemplate.queryForList(
				"SELECT role_name FROM ACCESS_POLICY_RULES WHERE url_pattern = '/lab-order/**' ORDER BY role_name",
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
	void onlyPhysicianNurseAndSystemAdminCanAccessLabOrderScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		mockMvc.perform(get("/lab-order").session(session))
				.andExpect(allowed ? status().isOk() : status().isForbidden());
	}

	@Test
	void physicianCreatesLabOrderAndItAppearsInList() throws Exception {
		String patientNo = existingSyntheticPatientNo();
		String testName = "LAB-ORDER-CREATE-" + UUID.randomUUID();
		MockHttpSession session = loginAs("physician");

		mockMvc.perform(post("/lab-order").session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("orderedAt", "2026-08-05T09:30")
					.param("testName", testName)
					.param("status", "ORDERED")
					.param("resultSummary", "검사 결과 대기"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/lab-order"));

		MvcResult result = mockMvc.perform(get("/lab-order").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(patientNo).contains(testName).contains("physician");
	}

	@Test
	void physicianUpdatesLabOrder() throws Exception {
		MockHttpSession session = loginAs("physician");
		Long labOrderId = createLabOrder(session, "LAB-ORDER-UPDATE-" + UUID.randomUUID());
		String updatedTestName = "LAB-ORDER-UPDATED-" + UUID.randomUUID();
		String patientNo = existingSyntheticPatientNo();

		mockMvc.perform(get("/lab-order/{id}/edit", labOrderId).session(session))
				.andExpect(status().isOk());

		mockMvc.perform(post("/lab-order/{id}", labOrderId).session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("orderedAt", "2026-08-05T10:45")
					.param("testName", updatedTestName)
					.param("status", "IN_PROGRESS")
					.param("resultSummary", "검사 진행 중"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/lab-order"));

		MvcResult result = mockMvc.perform(get("/lab-order").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(updatedTestName).contains("IN_PROGRESS");
	}

	@Test
	void physicianDeletesLabOrder() throws Exception {
		MockHttpSession session = loginAs("physician");
		String testName = "LAB-ORDER-DELETE-" + UUID.randomUUID();
		Long labOrderId = createLabOrder(session, testName);

		mockMvc.perform(post("/lab-order/{id}/delete", labOrderId).session(session).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/lab-order"));

		MvcResult result = mockMvc.perform(get("/lab-order").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(testName);
	}

	private Long createLabOrder(MockHttpSession session, String testName) throws Exception {
		mockMvc.perform(post("/lab-order").session(session).with(csrf())
					.param("syntheticPatientNo", existingSyntheticPatientNo())
					.param("orderedAt", "2026-08-05T09:30")
					.param("testName", testName)
					.param("status", "ORDERED")
					.param("resultSummary", "검사 결과 대기"))
				.andExpect(status().is3xxRedirection());
		return jdbcTemplate.queryForObject(
				"SELECT lab_order_id FROM LAB_ORDER WHERE test_name = ?", Long.class, testName);
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
