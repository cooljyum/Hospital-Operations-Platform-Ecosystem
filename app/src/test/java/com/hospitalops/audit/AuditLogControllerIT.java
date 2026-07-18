package com.hospitalops.audit;

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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 Step 5.2 acceptance criteria 검증(PLAN.md §7): "감사자 role로만 접근 가능,
 * 필터(행위자/기간/대상) 동작". 로컬 실제 MySQL(hospital_ops)에 통제된 3건의 AUDIT_LOG
 * 마커 행을 직접 삽입해(다른 통합테스트/실제 운영 흐름이 만든 행과 절대 겹치지 않는
 * "AUDIT-FILTER-..." 접두어를 써서) 필터 조합마다 결과가 실제로 좁혀지는지 확인한다.
 * {@code @Transactional}로 테스트 종료 시 삽입한 행을 자동 롤백한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class AuditLogControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final String ACTOR_A = "audit-filter-actor-A";
	private static final String ACTOR_B = "audit-filter-actor-B";
	private static final String TARGET_A = "AUDIT-FILTER-TARGET-A";
	private static final String TARGET_B = "AUDIT-FILTER-TARGET-B";
	private static final String TARGET_C = "AUDIT-FILTER-TARGET-C";

	@Test
	void auditorSeesAllThreeMarkerRowsWithNoFilter() throws Exception {
		seedMarkerRows();
		MockHttpSession session = loginAs("auditor");

		MvcResult result = mockMvc.perform(get("/audit/preview").session(session))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains(TARGET_A).contains(TARGET_B).contains(TARGET_C);
	}

	@Test
	void actorUsernameFilterNarrowsToThatActorOnly() throws Exception {
		seedMarkerRows();
		MockHttpSession session = loginAs("auditor");

		MvcResult result = mockMvc.perform(get("/audit/preview")
						.session(session)
						.param("actorUsername", ACTOR_A))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		// ACTOR_A는 TARGET_A, TARGET_C 두 건을 남겼다.
		assertThat(body).contains(TARGET_A).contains(TARGET_C);
		// ACTOR_B가 남긴 TARGET_B는 필터로 걸러져야 한다.
		assertThat(body).doesNotContain(TARGET_B);
	}

	@Test
	void targetPkFilterNarrowsToThatTargetOnly() throws Exception {
		seedMarkerRows();
		MockHttpSession session = loginAs("auditor");

		MvcResult result = mockMvc.perform(get("/audit/preview")
						.session(session)
						.param("targetPk", TARGET_B))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains(TARGET_B);
		assertThat(body).doesNotContain(TARGET_A).doesNotContain(TARGET_C);
	}

	@Test
	void dateRangeFilterNarrowsToRowsWithinRange() throws Exception {
		seedMarkerRows();
		MockHttpSession session = loginAs("auditor");

		// TARGET_A=2020-01-10, TARGET_B=2020-01-20, TARGET_C=2020-02-01 중
		// [2020-01-15, 2020-01-25] 범위에는 TARGET_B만 들어온다.
		MvcResult result = mockMvc.perform(get("/audit/preview")
						.session(session)
						.param("from", "2020-01-15")
						.param("to", "2020-01-25"))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains(TARGET_B);
		assertThat(body).doesNotContain(TARGET_A).doesNotContain(TARGET_C);
	}

	@Test
	void combinedActorAndDateFiltersNarrowFurtherThanEitherAlone() throws Exception {
		seedMarkerRows();
		MockHttpSession session = loginAs("auditor");

		// ACTOR_A는 TARGET_A(2020-01-10)와 TARGET_C(2020-02-01) 두 건을 남겼지만,
		// 기간을 [2020-01-25, 2020-02-05]로 좁히면 TARGET_C만 남아야 한다.
		MvcResult result = mockMvc.perform(get("/audit/preview")
						.session(session)
						.param("actorUsername", ACTOR_A)
						.param("from", "2020-01-25")
						.param("to", "2020-02-05"))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).contains(TARGET_C);
		assertThat(body).doesNotContain(TARGET_A).doesNotContain(TARGET_B);
	}

	@ParameterizedTest(name = "{0} -> allowed={1}")
	@CsvSource({
			"auditor, true",
			"admin, false",
			"physician, false",
			"nurse, false",
			"registrar, false",
	})
	void onlyAuditorRoleCanAccessAuditLogScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		var expectedStatus = allowed ? status().isOk() : status().isForbidden();

		mockMvc.perform(get("/audit/preview").session(session))
				.andExpect(expectedStatus);
	}

	private void seedMarkerRows() {
		insertAuditLog(ACTOR_A, TARGET_A, "TEST_ACTION_A", LocalDateTime.of(2020, 1, 10, 10, 0));
		insertAuditLog(ACTOR_B, TARGET_B, "TEST_ACTION_B", LocalDateTime.of(2020, 1, 20, 10, 0));
		insertAuditLog(ACTOR_A, TARGET_C, "TEST_ACTION_C", LocalDateTime.of(2020, 2, 1, 10, 0));
	}

	private void insertAuditLog(String actorUsername, String targetPk, String action, LocalDateTime occurredAt) {
		jdbcTemplate.update(
				"INSERT INTO audit_log "
						+ "(actor_username, target_pk, action, purpose, masked, ip_address, success, result_code, occurred_at) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				actorUsername, targetPk, action, "필터 검증용 테스트 사유", false, "127.0.0.1", true,
				"AUDIT_FILTER_TEST", occurredAt);
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
