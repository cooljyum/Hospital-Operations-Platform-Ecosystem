package com.hospitalops.examroom;

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
class ExamRoomControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesExamRoomSessionTableAndBothAccessPolicyRules() {
		Integer tableCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = DATABASE() AND table_name = 'EXAM_ROOM_SESSION'
				""", Integer.class);
		assertThat(tableCount).isEqualTo(1);

		var roles = jdbcTemplate.queryForList(
				"SELECT role_name FROM ACCESS_POLICY_RULES WHERE url_pattern = '/exam-room/**' ORDER BY role_name",
				String.class);
		assertThat(roles).containsExactly("ROLE_PHYSICIAN", "ROLE_SYSTEM_ADMIN");
	}

	@ParameterizedTest(name = "{0} -> allowed={1}")
	@CsvSource({
			"admin, true",
			"physician, true",
			"nurse, false",
			"registrar, false",
			"auditor, false",
	})
	void onlyPhysicianAndSystemAdminCanAccessExamRoomScreen(String username, boolean allowed) throws Exception {
		MockHttpSession session = loginAs(username);

		mockMvc.perform(get("/exam-room").session(session))
				.andExpect(allowed ? status().isOk() : status().isForbidden());
	}

	@Test
	void physicianCreatesExamRoomSessionAndItAppearsInList() throws Exception {
		String patientNo = existingSyntheticPatientNo();
		String notes = "EXAM-ROOM-CREATE-" + UUID.randomUUID();
		MockHttpSession session = loginAs("physician");

		mockMvc.perform(post("/exam-room").session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("roomNo", "101")
					.param("startedAt", "2026-08-05T09:30")
					.param("endedAt", "2026-08-05T10:00")
					.param("notes", notes)
					.param("status", "WAITING"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/exam-room"));

		MvcResult result = mockMvc.perform(get("/exam-room").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(patientNo).contains(notes).contains("physician");
	}

	@Test
	void physicianUpdatesExamRoomSession() throws Exception {
		MockHttpSession session = loginAs("physician");
		Long examRoomSessionId = createExamRoomSession(session, "EXAM-ROOM-UPDATE-" + UUID.randomUUID());
		String updatedNotes = "EXAM-ROOM-UPDATED-" + UUID.randomUUID();
		String patientNo = existingSyntheticPatientNo();

		mockMvc.perform(get("/exam-room/{id}/edit", examRoomSessionId).session(session))
				.andExpect(status().isOk());

		mockMvc.perform(post("/exam-room/{id}", examRoomSessionId).session(session).with(csrf())
					.param("syntheticPatientNo", patientNo)
					.param("roomNo", "201")
					.param("startedAt", "2026-08-05T10:45")
					.param("endedAt", "2026-08-05T11:30")
					.param("notes", updatedNotes)
					.param("status", "DONE"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/exam-room"));

		MvcResult result = mockMvc.perform(get("/exam-room").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).contains(updatedNotes).contains("DONE");
	}

	@Test
	void physicianDeletesExamRoomSession() throws Exception {
		MockHttpSession session = loginAs("physician");
		String notes = "EXAM-ROOM-DELETE-" + UUID.randomUUID();
		Long examRoomSessionId = createExamRoomSession(session, notes);

		mockMvc.perform(post("/exam-room/{id}/delete", examRoomSessionId).session(session).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/exam-room"));

		MvcResult result = mockMvc.perform(get("/exam-room").session(session))
				.andExpect(status().isOk())
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(notes);
	}

	private Long createExamRoomSession(MockHttpSession session, String notes) throws Exception {
		mockMvc.perform(post("/exam-room").session(session).with(csrf())
					.param("syntheticPatientNo", existingSyntheticPatientNo())
					.param("roomNo", "101")
					.param("startedAt", "2026-08-05T09:30")
					.param("endedAt", "2026-08-05T10:00")
					.param("notes", notes)
					.param("status", "WAITING"))
				.andExpect(status().is3xxRedirection());
		return jdbcTemplate.queryForObject(
				"SELECT exam_room_session_id FROM EXAM_ROOM_SESSION WHERE notes = ?", Long.class, notes);
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
