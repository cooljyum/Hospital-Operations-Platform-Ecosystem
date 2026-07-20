package com.hospitalops.stats;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 9 Step 9.2: Phase 4 Step 4.4의 k=5 익명성 통계(REST API
 * {@code GET /stats/patient-count-by-gender-age-band})를 성별×연령대 교차표 화면으로
 * 노출한다.
 *
 * <p>{@link StatsController}(REST API)는 그대로 두고, 이 컨트롤러가 같은
 * {@link PatientStatsService}를 재사용해 Thymeleaf 화면만 추가한다 — 통계 계산·억제
 * 로직은 새로 만들지 않는다(호출하는 곳은 오직
 * {@link PatientStatsService#genderByAgeBandCounts()} 한 지점뿐).</p>
 *
 * <p>URL은 {@code /stats/patient-count-by-gender-age-band/view}로,
 * ACCESS_POLICY_RULES(V12 마이그레이션)에 이미 등록된 {@code /stats/**} 와일드카드
 * 패턴에 포함된다 — V12 코멘트가 "향후 이 패키지에 추가될 다른 통계 엔드포인트도 같은
 * 정책을 자동으로 상속하게 한다"고 명시한 그대로이므로, 이 화면을 위한 별도 마이그레이션은
 * 추가하지 않는다. 다만 마이그레이션이 없다고 RBAC 검증까지 생략하지 않고,
 * {@code StatsViewControllerIT}로 감사자/전산관리자만 200이고 나머지 역할은 403임을
 * 직접 재확인한다.</p>
 *
 * <p>화면은 {@link PatientGenderAgeBandCount#cell()}의
 * {@link SuppressedCell#getDisplay()}만 렌더한다 — {@link SuppressedCell#getCount()}
 * (억제된 셀은 {@code null})는 컨트롤러·템플릿 어디에서도 참조하지 않는다. 그래서 억제된
 * 셀의 실제 인원수가 화면에 노출될 경로가 구조적으로 없다.</p>
 */
@Controller
public class StatsViewController {

	private final PatientStatsService patientStatsService;

	public StatsViewController(PatientStatsService patientStatsService) {
		this.patientStatsService = patientStatsService;
	}

	@GetMapping("/stats/patient-count-by-gender-age-band/view")
	public String view(Model model) {
		List<PatientGenderAgeBandCount> counts = patientStatsService.genderByAgeBandCounts();

		List<String> genders = counts.stream()
				.map(PatientGenderAgeBandCount::gender)
				.distinct()
				.sorted()
				.toList();

		List<Integer> ageBands = counts.stream()
				.map(PatientGenderAgeBandCount::ageBandStart)
				.distinct()
				.sorted()
				.toList();

		Map<String, String> displayByKey = new LinkedHashMap<>();
		for (PatientGenderAgeBandCount count : counts) {
			displayByKey.put(cellKey(count.gender(), count.ageBandStart()), count.cell().getDisplay());
		}

		model.addAttribute("genders", genders);
		model.addAttribute("ageBands", ageBands);
		model.addAttribute("displayByKey", displayByKey);
		model.addAttribute("hasAnyData", !counts.isEmpty());
		model.addAttribute("suppressedLabel", SuppressedCell.SUPPRESSED_LABEL);
		model.addAttribute("kThreshold", SuppressedCell.K_THRESHOLD);

		return "stats/patient-count-by-gender-age-band";
	}

	private static String cellKey(String gender, int ageBandStart) {
		return gender + "|" + ageBandStart;
	}
}
