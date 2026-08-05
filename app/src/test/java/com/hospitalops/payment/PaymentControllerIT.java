package com.hospitalops.payment;

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
class PaymentControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesPaymentTableAndBothAccessPolicyRules() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name = 'PAYMENT'
				""", Integer.class);
		assertThat(tableCount).isEqualTo(1);

		var roles = jdbcTemplate.queryForList(
				"SELECT role_name FROM ACCESS_POLICY_RULES WHERE url_pattern = '/payment/**' ORDER BY role_name",
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
	void onlyRegistrarAndSystemAdminCanAccessPaymentScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		mockMvc.perform(get("/payment").session(session))
				.andExpect(allowed ? status().isOk() : status().isForbidden());
	}

	@Test
	void registrarCreatesPaymentAndItAppearsInList() throws Exception {
		String patientNo = existingSyntheticPatientNo();
		String paymentMethod = uniquePaymentMethod();
		MockHttpSession session = loginAs("registrar");

		mockMvc.perform(post("/payment").session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("paidAt", "2026-08-05T09:30")
					.param("amount", "15000.50")
					.param("paymentMethod", paymentMethod)
					.param("status", "PAID"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/payment"));

		MvcResult result = mockMvc.perform(get("/payment").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(patientNo).contains(paymentMethod).contains("registrar");
	}

	@Test
	void registrarUpdatesPayment() throws Exception {
		MockHttpSession session = loginAs("registrar");
		Long paymentId = createPayment(session, uniquePaymentMethod());
		String updatedPaymentMethod = uniquePaymentMethod();
		String patientNo = existingSyntheticPatientNo();

		mockMvc.perform(get("/payment/{id}/edit", paymentId).session(session))
				.andExpect(status().isOk());

		mockMvc.perform(post("/payment/{id}", paymentId).session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("paidAt", "2026-08-05T10:45")
					.param("amount", "20000.00")
					.param("paymentMethod", updatedPaymentMethod)
					.param("status", "REFUNDED"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/payment"));

		MvcResult result = mockMvc.perform(get("/payment").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(updatedPaymentMethod).contains("REFUNDED");
	}

	@Test
	void registrarDeletesPayment() throws Exception {
		MockHttpSession session = loginAs("registrar");
		String paymentMethod = uniquePaymentMethod();
		Long paymentId = createPayment(session, paymentMethod);

		mockMvc.perform(post("/payment/{id}/delete", paymentId).session(session).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/payment"));

		MvcResult result = mockMvc.perform(get("/payment").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(paymentMethod);
	}

	private Long createPayment(MockHttpSession session, String paymentMethod) throws Exception {
		mockMvc.perform(post("/payment").session(session).with(csrf())
					.param("syntheticPatientNo", existingSyntheticPatientNo())
					.param("paidAt", "2026-08-05T09:30")
					.param("amount", "10000.00")
					.param("paymentMethod", paymentMethod)
					.param("status", "PAID"))
				.andExpect(status().is3xxRedirection());
		return jdbcTemplate.queryForObject(
				"SELECT payment_id FROM PAYMENT WHERE payment_method = ?", Long.class, paymentMethod);
	}

	private String existingSyntheticPatientNo() {
		return jdbcTemplate.queryForObject(
				"SELECT synthetic_patient_no FROM PATIENT ORDER BY patient_id LIMIT 1", String.class);
	}

	private String uniquePaymentMethod() {
		return "P-" + UUID.randomUUID();
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
