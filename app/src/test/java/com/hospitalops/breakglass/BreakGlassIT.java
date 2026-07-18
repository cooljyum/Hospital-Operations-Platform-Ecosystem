package com.hospitalops.breakglass;

import com.hospitalops.security.SecurityDataSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 Step 3.3 acceptance criteria 검증(PLAN.md §5): "break-glass 경로로 접근 시
 * AUDIT_LOG에 전용 결과 코드로 기록됨". 로컬 실제 MySQL(hospital_ops)에 대해 실제 계정으로
 * break-glass 요청을 날리고, AUDIT_LOG에 BREAK_GLASS_GRANTED row가 실제로 생기는지
 * SQL로 직접 확인한다. reason 없이 요청하면 거부되는지도 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BreakGlassIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void emergencyResourceIsBlockedByDefaultForAnyRole() throws Exception {
		MockHttpSession session = loginAs("physician");

		mockMvc.perform(get("/emergency/patient-record").session(session).accept(MediaType.TEXT_HTML))
				.andExpect(status().isForbidden());
	}

	@Test
	void grantWithoutReasonIsRejected() throws Exception {
		MockHttpSession session = loginAs("nurse");

		mockMvc.perform(post("/breakglass/grant").session(session).with(csrf()))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/breakglass/grant").session(session).param("reason", "   ").with(csrf()))
				.andExpect(status().isBadRequest());

		// 거부됐으므로 세션에 break-glass 플래그가 없어 emergency 리소스는 여전히 막혀 있어야 한다.
		mockMvc.perform(get("/emergency/patient-record").session(session).accept(MediaType.TEXT_HTML))
				.andExpect(status().isForbidden());
	}

	@Test
	void grantWithReasonOpensEmergencyResourceAndRecordsAuditLog() throws Exception {
		MockHttpSession session = loginAs("registrar");
		String reason = "응급 심정지 대응 - 담당의 부재로 원무팀 임시 조회";

		mockMvc.perform(post("/breakglass/grant").session(session).param("reason", reason).with(csrf()))
				.andExpect(status().isOk());

		mockMvc.perform(get("/emergency/patient-record").session(session).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk());

		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT actor_username, action, purpose, success, result_code FROM audit_log " +
						"WHERE actor_username = ? AND result_code = 'BREAK_GLASS_GRANTED' " +
						"ORDER BY audit_id DESC LIMIT 1",
				"registrar");

		assertThat(row.get("actor_username")).isEqualTo("registrar");
		assertThat(row.get("action")).isEqualTo("BREAK_GLASS_ACCESS_GRANT");
		assertThat(row.get("purpose")).isEqualTo(reason);
		assertThat(row.get("result_code")).isEqualTo("BREAK_GLASS_GRANTED");
		assertThat((Boolean) row.get("success")).isTrue();
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
