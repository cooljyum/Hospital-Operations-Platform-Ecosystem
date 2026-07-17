package com.hospitalops.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Phase 0.3 스캐폴딩용 더미 대시보드 컨트롤러.
 * AdminLTE 정적 자산 + Thymeleaf 레이아웃 프래그먼트가 정상 렌더되는지 확인하는 용도.
 */
@Controller
public class DashboardController {

	@GetMapping("/dashboard")
	public String dashboard() {
		return "dashboard";
	}

}
