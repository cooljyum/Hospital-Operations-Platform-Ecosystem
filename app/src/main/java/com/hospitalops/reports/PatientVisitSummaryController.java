package com.hospitalops.reports;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Phase 9 Step 9.1: 요약 테이블(PATIENT_VISIT_SUMMARY, Phase 6 Step 6.1) 리포팅 화면.
 *
 * <p>{@code /reports/patient-visit-summary}는 ACCESS_POLICY_RULES(V16 마이그레이션)에
 * ROLE_AUDITOR/ROLE_SYSTEM_ADMIN 전용으로 등록돼 있다 - 환자 단위 row가 그대로 나열되는
 * 화면이라 /stats/**(V12, k=5 억제된 교차표)보다 식별성이 높으므로 같은 두 역할로만
 * 좁힌다(V16 마이그레이션 주석 참고).</p>
 *
 * <p>검색어({@code q})는 표시용 식별자({@code synthetic_patient_no}) 부분 일치 필터이고,
 * 페이지({@code page}, 0-base)는 {@link #PAGE_SIZE}건 단위로 나눈다. 둘 다 선택적
 * 쿼리 파라미터다 - 값이 없으면 전체를 1페이지부터 보여준다.</p>
 */
@Controller
public class PatientVisitSummaryController {

	private static final int PAGE_SIZE = 20;

	private final PatientVisitSummaryRepository repository;

	public PatientVisitSummaryController(PatientVisitSummaryRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/reports/patient-visit-summary")
	public String list(
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "page", defaultValue = "0") int page,
			Model model) {

		String normalizedQuery = blankToNull(q);
		int safePage = Math.max(page, 0);

		List<PatientVisitSummaryRow> rows = repository.search(normalizedQuery, safePage, PAGE_SIZE);
		long total = repository.count(normalizedQuery);
		int totalPages = (int) Math.max(1, Math.ceil(total / (double) PAGE_SIZE));
		// 요청된 페이지가 실제 마지막 페이지보다 뒤라면(예: 필터를 바꿔 결과가 줄어든 경우)
		// 화면에 보여줄 "현재 페이지" 표시는 실제 마지막 페이지로 보정한다.
		int displayPage = Math.min(safePage, totalPages - 1);

		model.addAttribute("rows", rows);
		model.addAttribute("q", q);
		model.addAttribute("page", displayPage);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("total", total);
		model.addAttribute("hasAnyData", repository.hasAnyData());
		model.addAttribute("refreshedAt", repository.latestRefreshedAt());

		return "reports/patient-visit-summary";
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}
}
