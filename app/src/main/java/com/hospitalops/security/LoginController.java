package com.hospitalops.security;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Phase 3 Step 3.1: 로그인 페이지 렌더링. 실제 인증 처리(POST /login)는
 * {@link SecurityConfig}의 formLogin 설정(Spring Security의 내장
 * UsernamePasswordAuthenticationFilter)이 담당하고, 이 컨트롤러는 GET /login에 대해
 * 로그인 폼 뷰만 반환한다.
 */
@Controller
public class LoginController {

	@GetMapping("/login")
	public String login() {
		return "login";
	}
}
