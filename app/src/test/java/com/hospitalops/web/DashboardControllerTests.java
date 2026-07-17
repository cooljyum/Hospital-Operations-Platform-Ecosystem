package com.hospitalops.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0.3 acceptance criteria: 로그인 없이 /dashboard 접근 시 302(로그인 페이지로 리다이렉트).
 *
 * SecurityConfig가 아직 없는 이 시점(Phase 3.1 이전)에도 spring-boot-starter-security가
 * classpath에 있으면 Spring Boot의 기본 보안 자동 설정이 모든 요청을 인증 필요로 만들고,
 * 미인증 요청은 기본 로그인 페이지로 302 리다이렉트한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
		"spring.datasource.url=jdbc:h2:mem:hospital_ops_test_web;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureMockMvc
class DashboardControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void dashboardRedirectsToLoginWhenUnauthenticated() throws Exception {
		// 실제 브라우저는 기본적으로 Accept: text/html 을 보내며, 이때 Spring Security의
		// 컨텐츠 협상 기반 AuthenticationEntryPoint가 폼 로그인 리다이렉트(302)를 선택한다.
		// (Accept 헤더가 없으면 Basic-auth 챌린지(401)로 빠지므로 명시적으로 지정한다.)
		mockMvc.perform(get("/dashboard").accept(MediaType.TEXT_HTML))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	@WithMockUser
	void dashboardRendersAdminLteLayoutWhenAuthenticated() throws Exception {
		mockMvc.perform(get("/dashboard").accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("adminlte.min.css")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"wrapper\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("Phase 0 스캐폴딩 확인용 더미 페이지")));
	}

}
