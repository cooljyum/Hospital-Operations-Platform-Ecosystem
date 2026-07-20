package com.hospitalops.stats;

import com.hospitalops.security.SecurityDataSeeder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 9 Step 9.2 acceptance criteria 검증(PLAN.md §11): "Phase 4.4 k=5 통계를 화면으로
 * 노출". {@link StatsControllerIT}가 이미 REST API 레벨에서 검증한 k=5 억제를, 이 화면이
 * 왜곡 없이(억제된 셀은 실제 숫자 없이 "&lt; 5"만) 렌더하는지, 그리고 RBAC이 화면에도
 * 동일하게 적용되는지(별도 V17 마이그레이션 없이 V12의 {@code /stats/**} 와일드카드로
 * 상속됨을 실측) 재확인한다.
 *
 * <p>로컬 실제 MySQL(hospital_ops)에 통제된 소량 행을 직접 삽입해(성별 값을 실제 데이터와
 * 절대 겹치지 않는 "VIEWTESTF"/"VIEWTESTM"으로 둬, 기존에 얼마나 많은 Synthea 데이터가
 * 이미 적재돼 있든 결과 셀이 항상 정확히 이 테스트가 넣은 건수와 일치하게 만든다).
 * {@code @Transactional}로 테스트 종료 시 삽입한 행을 자동 롤백한다(운영 데이터에
 * 잔여물을 남기지 않음).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class StatsViewControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void suppressedCellsShowOnlyLabelAndUnsuppressedCellsShowRealCount() throws Exception {
		// 3명 - k=5 미만이라 억제되어야 함.
		insertTestPatient("STATS-VIEW-F-1", "SVF-0001", "VIEWTESTF", "1990-06-15");
		insertTestPatient("STATS-VIEW-F-2", "SVF-0002", "VIEWTESTF", "1990-06-15");
		insertTestPatient("STATS-VIEW-F-3", "SVF-0003", "VIEWTESTF", "1990-06-15");

		// 6명 - k=5 이상이라 실제 값이 그대로 노출되어야 함.
		insertTestPatient("STATS-VIEW-M-1", "SVM-0001", "VIEWTESTM", "1985-01-01");
		insertTestPatient("STATS-VIEW-M-2", "SVM-0002", "VIEWTESTM", "1985-01-01");
		insertTestPatient("STATS-VIEW-M-3", "SVM-0003", "VIEWTESTM", "1985-01-01");
		insertTestPatient("STATS-VIEW-M-4", "SVM-0004", "VIEWTESTM", "1985-01-01");
		insertTestPatient("STATS-VIEW-M-5", "SVM-0005", "VIEWTESTM", "1985-01-01");
		insertTestPatient("STATS-VIEW-M-6", "SVM-0006", "VIEWTESTM", "1985-01-01");

		MockHttpSession session = loginAs("auditor");

		MvcResult result = mockMvc.perform(get("/stats/patient-count-by-gender-age-band/view")
						.session(session)
						.accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andReturn();

		String body = result.getResponse().getContentAsString();

		assertThat(body).contains("VIEWTESTF");
		assertThat(body).contains("VIEWTESTM");
		// Thymeleaf th:text는 기본적으로 HTML 이스케이프한다 - "< 5"는 응답 바이트 상으로는
		// "&lt; 5"로 나가고 브라우저가 이를 "< 5"로 렌더한다(둘 다 동일한 억제 라벨을 뜻함).
		assertThat(body).contains(HtmlUtils.htmlEscape(SuppressedCell.SUPPRESSED_LABEL));

		// 억제된 셀(VIEWTESTF, 원값=3)의 실제 숫자는 표에 "3"으로 등장하면 안 된다
		// (다른 셀이 우연히 원값 3이더라도 마찬가지로 억제되어야 하므로, 이 assertion은
		// 전체 표에 대해서도 유효하다).
		assertThat(body).doesNotContain("<td>3</td>");
		// 억제되지 않은 셀(VIEWTESTM, 원값=6)은 실제 값이 그대로 노출되어야 한다.
		assertThat(body).contains("<td>6</td>");
	}

	@ParameterizedTest(name = "{0} -> allowed={1}")
	@CsvSource({
			"admin, true",
			"auditor, true",
			"physician, false",
			"nurse, false",
			"registrar, false",
	})
	void onlyAuditorAndSystemAdminCanAccessStatsViewScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		var expectedStatus = allowed ? status().isOk() : status().isForbidden();

		mockMvc.perform(get("/stats/patient-count-by-gender-age-band/view")
						.session(session)
						.accept(MediaType.TEXT_HTML))
				.andExpect(expectedStatus);
	}

	private void insertTestPatient(String sourceUuid, String syntheticNo, String gender, String birthDate) {
		jdbcTemplate.update(
				"INSERT INTO PATIENT (source_patient_uuid, synthetic_patient_no, birth_date, gender) "
						+ "VALUES (?, ?, ?, ?)",
				sourceUuid, syntheticNo, birthDate, gender);
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
