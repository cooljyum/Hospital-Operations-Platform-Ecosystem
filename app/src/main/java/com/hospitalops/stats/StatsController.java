package com.hospitalops.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 4 Step 4.4: k=5 익명성 + small-cell suppression이 적용된 통계 REST API.
 *
 * <p>{@code GET /stats/patient-count-by-gender-age-band} - 성별×연령대(10년 단위)별
 * 환자 수. 셀 값이 5 미만이면 {@link SuppressedCell#SUPPRESSED_LABEL}로만 표시되고
 * 실제 숫자는 응답 어디에도 없다({@link PatientStatsService}가 억제를 강제).</p>
 *
 * <p>{@code /stats/**}는 ACCESS_POLICY_RULES(V12 마이그레이션)에 ROLE_AUDITOR /
 * ROLE_SYSTEM_ADMIN 전용으로 등록돼 있다 - 셀 값 자체는 k=5 미만이면 억제되지만
 * 교차표(성별×연령대) 구조 자체가 민감하므로, 의사/간호사/원무 역할은
 * SecurityConfig에서 403으로 차단된다.</p>
 */
@RestController
public class StatsController {

	private final PatientStatsService patientStatsService;

	public StatsController(PatientStatsService patientStatsService) {
		this.patientStatsService = patientStatsService;
	}

	@GetMapping("/stats/patient-count-by-gender-age-band")
	public List<PatientGenderAgeBandCount> patientCountByGenderAndAgeBand() {
		return patientStatsService.genderByAgeBandCounts();
	}
}
