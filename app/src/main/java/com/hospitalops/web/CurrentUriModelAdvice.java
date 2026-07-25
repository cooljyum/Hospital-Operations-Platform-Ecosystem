package com.hospitalops.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Phase 11.4: sidebar 활성 하이라이트용 현재 요청 URI를 모든 화면 모델에 주입한다.
 */
@ControllerAdvice
public class CurrentUriModelAdvice {

	@ModelAttribute("currentUri")
	public String currentUri(HttpServletRequest request) {
		return request.getRequestURI();
	}
}
