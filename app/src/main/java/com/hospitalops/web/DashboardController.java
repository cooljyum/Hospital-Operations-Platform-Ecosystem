package com.hospitalops.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 업무 모듈의 집계 수치를 보여주는 대시보드 컨트롤러.
 */
@Controller
public class DashboardController {
	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/dashboard")
	public String dashboard(Model model) {
		model.addAttribute("stats", dashboardService.buildStats());
		return "dashboard";
	}

}
