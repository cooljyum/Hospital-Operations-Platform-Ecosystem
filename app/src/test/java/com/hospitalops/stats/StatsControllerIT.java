package com.hospitalops.stats;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 Step 4.4 acceptance criteria 검증(엄격): 셀 값 &lt;5인 통계는 억제 표시로만
 * 나오고, 그 실제 원값이 응답 문자열 어디에도 숫자로 등장하지 않는다. 로컬 실제
 * MySQL(hospital_ops)에 통제된 소량 행을 직접 삽입해(성별 값을 실제 데이터와 절대 겹치지
 * 않는 "TESTF"/"TESTM"으로 둬 기존에 얼마나 많은 Synthea 데이터가 이미 적재돼 있든
 * 결과 셀이 항상 정확히 이 테스트가 넣은 건수와 일치하게 만든다), API 응답에서 억제
 * 여부를 직접 확인한다. {@code @Transactional}로 테스트 종료 시 삽입한 행을 자동
 * 롤백한다(운영 데이터에 잔여물을 남기지 않음).
 *
 * <p>이월 이슈 수정(V12 마이그레이션): {@code /stats/**}는 이제 ACCESS_POLICY_RULES에
 * ROLE_AUDITOR/ROLE_SYSTEM_ADMIN 전용으로 등록돼 있다. 억제 로직 테스트는 그래서
 * physician이 아니라 auditor로 로그인한다({@link #cellsBelowFiveAreSuppressedAndCellsAtOrAboveFiveShowTheRealCount()}).
 * {@link #roleBasedAccessControlOnStatsEndpoint(String, boolean)}가 5개 역할 전원에 대해
 * 감사자/관리자는 200, 의사/간호사/원무는 403임을 직접 검증한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class StatsControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void cellsBelowFiveAreSuppressedAndCellsAtOrAboveFiveShowTheRealCount() throws Exception {
		// 3명 - k=5 미만이라 억제되어야 함.
		insertTestPatient("STATS-TEST-F-1", "STF-0001", "TESTF", "1990-06-15");
		insertTestPatient("STATS-TEST-F-2", "STF-0002", "TESTF", "1990-06-15");
		insertTestPatient("STATS-TEST-F-3", "STF-0003", "TESTF", "1990-06-15");

		// 6명 - k=5 이상이라 실제 값이 그대로 노출되어야 함.
		insertTestPatient("STATS-TEST-M-1", "STM-0001", "TESTM", "1985-01-01");
		insertTestPatient("STATS-TEST-M-2", "STM-0002", "TESTM", "1985-01-01");
		insertTestPatient("STATS-TEST-M-3", "STM-0003", "TESTM", "1985-01-01");
		insertTestPatient("STATS-TEST-M-4", "STM-0004", "TESTM", "1985-01-01");
		insertTestPatient("STATS-TEST-M-5", "STM-0005", "TESTM", "1985-01-01");
		insertTestPatient("STATS-TEST-M-6", "STM-0006", "TESTM", "1985-01-01");

		MockHttpSession session = loginAs("auditor");

		MvcResult result = mockMvc.perform(get("/stats/patient-count-by-gender-age-band").session(session))
				.andExpect(status().isOk())
				.andReturn();

		String body = result.getResponse().getContentAsString();

		// 억제된 셀(TESTF, 원값=3)의 실제 숫자는 응답 어디에도 숫자로 나가면 안 된다.
		assertThat(body).contains("\"gender\":\"TESTF\"");
		assertThat(body).doesNotContain("\"count\":3");
		assertThat(body).contains(SuppressedCell.SUPPRESSED_LABEL);

		// 억제되지 않은 셀(TESTM, 원값=6)은 실제 값이 그대로 노출되어야 한다.
		assertThat(body).contains("\"gender\":\"TESTM\"");
		assertThat(body).contains("\"count\":6");
	}

	/**
	 * V12 마이그레이션 이월 이슈 수정 검증: {@code /stats/**}는 감사자/전산관리자만
	 * 200을 받고, 나머지 3역할(의사/간호사/원무)은 403으로 차단되어야 한다.
	 */
	@ParameterizedTest(name = "{0} -> allowed={1}")
	@CsvSource({
			"admin, true",
			"auditor, true",
			"physician, false",
			"nurse, false",
			"registrar, false",
	})
	void roleBasedAccessControlOnStatsEndpoint(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		var expectedStatus = allowed ? status().isOk() : status().isForbidden();

		mockMvc.perform(get("/stats/patient-count-by-gender-age-band").session(session))
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
