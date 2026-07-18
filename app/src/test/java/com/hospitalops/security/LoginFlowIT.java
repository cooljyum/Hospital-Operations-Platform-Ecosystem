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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 Step 3.1 acceptance criteria 검증(PLAN.md §5): "미인증 접근 시 로그인 페이지
 * 리다이렉트, 로그인 성공 시 세션 발급". 로컬 실제 MySQL(hospital_ops)에 대해 실제 admin
 * 계정(SecurityDataSeeder가 부팅 시 시드)으로 로그인해 세션이 실제로 발급되고, 그 세션으로
 * /dashboard가 200을 반환하는지까지 실측한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LoginFlowIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unauthenticatedDashboardRedirectsToLoginPage() throws Exception {
		mockMvc.perform(get("/dashboard").accept(MediaType.TEXT_HTML))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", endsWith("/login")));
	}

	@Test
	void loginWithRealAdminCredentialsGrantsSessionThatCanAccessDashboard() throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/login")
						.param("username", "admin")
						.param("password", SecurityDataSeeder.LOCAL_TEST_PASSWORD)
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", endsWith("/dashboard")))
				.andReturn();

		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
		assertThat(session).isNotNull();

		mockMvc.perform(get("/dashboard").session(session).accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk());
	}

	@Test
	void loginWithWrongPasswordIsRejectedAndDoesNotGrantDashboardAccess() throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/login")
						.param("username", "admin")
						.param("password", "definitely-wrong-password")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", containsString("error")))
				.andReturn();

		MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
		if (session != null) {
			mockMvc.perform(get("/dashboard").session(session).accept(MediaType.TEXT_HTML))
					.andExpect(status().isFound());
		}
	}
}
