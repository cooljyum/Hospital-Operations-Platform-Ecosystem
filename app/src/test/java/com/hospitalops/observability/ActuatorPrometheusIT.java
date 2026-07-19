package com.hospitalops.observability;

import com.hospitalops.batch.SyncJobRunner;
import com.hospitalops.security.SecurityDataSeeder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 Step 7.1 acceptance criteria 검증(PLAN.md §9): "/actuator/prometheus 200
 * 응답, 커스텀 메트릭(배치 성공/실패 카운트 등) 노출".
 *
 * <p>로컬 실제 MySQL(hospital_ops)에 대해 실제로 {@link SyncJobRunner#runOnce()}와
 * break-glass grant 흐름을 실행시켜, 그 결과로 등록되는 커스텀 카운터
 * ({@code hospitalops.batch.job.runs}, {@code hospitalops.breakglass.grants})가
 * {@code /actuator/prometheus} 응답 본문에 실제로 나타나는지 직접 확인한다.</p>
 *
 * <p>접근 제어도 함께 검증한다: {@code /actuator/health,info,prometheus}는 인증 없이
 * (Prometheus 스크레이퍼 시나리오) 200이어야 하고, {@code /actuator/metrics}는
 * ACCESS_POLICY_RULES(V14)에 따라 감사자/전산관리자만 200, 나머지 3역할은 403이어야
 * 한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ActuatorPrometheusIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SyncJobRunner syncJobRunner;

	@Test
	void prometheusEndpointIsReachableWithoutLoginAndExposesCustomBatchJobMetric() throws Exception {
		JobExecution execution = syncJobRunner.runOnce();
		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

		// 세션 없이(로그인 없이) 호출 - Prometheus 스크레이퍼는 인증하지 않는다.
		MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains("hospitalops_batch_job_runs");
		assertThat(body).contains("job=\"fhirSyncJob\"");
		assertThat(body).contains("status=\"success\"");
	}

	@Test
	void healthAndInfoEndpointsAreReachableWithoutLogin() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
	}

	@Test
	void breakGlassGrantIsRecordedAsCustomMetricVisibleInPrometheusEndpoint() throws Exception {
		MockHttpSession session = loginAs("nurse");
		String reason = "Phase 7.1 관측성 검증용 응급 접근";

		mockMvc.perform(post("/breakglass/grant").session(session).param("reason", reason).with(csrf()))
				.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains("hospitalops_breakglass_grants");
	}

	/**
	 * V14 마이그레이션 검증: {@code /actuator/metrics}는 감사자/전산관리자만 200을
	 * 받고, 나머지 3역할(의사/간호사/원무)은 403으로 차단되어야 한다.
	 */
	@ParameterizedTest(name = "{0} -> allowed={1}")
	@CsvSource({
			"admin, true",
			"auditor, true",
			"physician, false",
			"nurse, false",
			"registrar, false",
	})
	void roleBasedAccessControlOnActuatorMetricsEndpoint(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		var expectedStatus = allowed ? status().isOk() : status().isForbidden();

		mockMvc.perform(get("/actuator/metrics").session(session))
				.andExpect(expectedStatus);
	}

	@Test
	void actuatorMetricsEndpointRedirectsToLoginWhenUnauthenticated() throws Exception {
		mockMvc.perform(get("/actuator/metrics"))
				.andExpect(status().is3xxRedirection());
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
