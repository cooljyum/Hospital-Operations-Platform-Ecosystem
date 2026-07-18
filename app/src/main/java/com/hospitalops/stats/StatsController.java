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
 * <p>이 경로는 ACCESS_POLICY_RULES에 아직 등록돼 있지 않다 - Phase 2의
 * {@code FhirController}와 같은 위치로, SecurityConfig의
 * {@code auth.anyRequest().authenticated()} 기본 규칙에 따라 인증된 사용자라면 역할
 * 무관하게 접근 가능하다. 역할별 세분화(예: 감사자/관리자 전용)가 필요하면 이후
 * ACCESS_POLICY_RULES에 전용 row를 추가해 좁히면 된다(이번 step 범위 밖).</p>
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
