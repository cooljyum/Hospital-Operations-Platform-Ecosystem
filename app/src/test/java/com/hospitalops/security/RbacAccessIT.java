package com.hospitalops.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 Step 3.2 acceptance criteria 검증(PLAN.md §5): "5역할 각각 접근 가능 화면이
 * 다름을 통합테스트로 검증". 로컬 실제 MySQL(hospital_ops)에 대해 SecurityDataSeeder가
 * 시드한 5개 실제 계정(admin/physician/nurse/registrar/auditor)으로 각각 실제 로그인을
 * 수행하고, ACCESS_POLICY_RULES 기반 인가 결정이 역할마다 실제로 다르게 나오는지 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RbacAccessIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void allFiveRolesCanAccessCommonDashboard() throws Exception {
		for (String username : new String[] {"admin", "physician", "nurse", "registrar", "auditor"}) {
			MockHttpSession session = loginAs(username);
			mockMvc.perform(get("/dashboard").session(session).accept(MediaType.TEXT_HTML))
					.andExpect(status().isOk());
		}
	}

	@Test
	void onlySystemAdminCanAccessAdminSystemPlaceholder() throws Exception {
		MockHttpSession adminSession = loginAs("admin");
		mockMvc.perform(get("/admin/system").session(adminSession).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk());

		for (String username : new String[] {"physician", "nurse", "registrar", "auditor"}) {
			MockHttpSession session = loginAs(username);
			mockMvc.perform(get("/admin/system").session(session).accept(MediaType.TEXT_HTML))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void onlyAuditorCanAccessAuditPreviewPlaceholder() throws Exception {
		MockHttpSession auditorSession = loginAs("auditor");
		mockMvc.perform(get("/audit/preview").session(auditorSession).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk());

		for (String username : new String[] {"admin", "physician", "nurse", "registrar"}) {
			MockHttpSession session = loginAs(username);
			mockMvc.perform(get("/audit/preview").session(session).accept(MediaType.TEXT_HTML))
					.andExpect(status().isForbidden());
		}
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
