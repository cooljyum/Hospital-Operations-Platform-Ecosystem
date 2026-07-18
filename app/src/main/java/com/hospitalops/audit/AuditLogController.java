package com.hospitalops.audit;

import com.hospitalops.security.AuditLog;
import com.hospitalops.security.AuditLogRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Phase 5 Step 5.2: 감사 로그 조회 화면(ROLE_AUDITOR 전용, Thymeleaf).
 *
 * <p>URL은 Phase 3 Step 3.2가 이미 {@code ACCESS_POLICY_RULES}에 감사자 전용으로
 * 시딩해 둔 {@code /audit/preview}를 그대로 재사용한다({@code V9__rbac_access_policy.sql}
 * 참고, description에도 "Phase 5.2에서 대체 예정"이라고 명시돼 있었다) - 이 컨트롤러가
 * 기존 {@code com.hospitalops.security.PlaceholderScreensController}의 placeholder
 * 매핑을 대체한다. 이미 감사자 전용으로 게이팅된 URL을 재사용하므로 새 마이그레이션
 * (V13) 없이도 "ROLE_AUDITOR로만 접근 가능"이 그대로 성립한다.</p>
 *
 * <p>필터(행위자/기간/대상)는 전부 선택적 쿼리 파라미터다. 값이 비어 있으면 해당 조건은
 * 적용하지 않는다({@link AuditLogRepository#search}가 {@code NULL} 파라미터를 "필터
 * 없음"으로 처리).</p>
 */
@Controller
public class AuditLogController {

	private final AuditLogRepository auditLogRepository;

	public AuditLogController(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@GetMapping("/audit/preview")
	public String list(
			@RequestParam(name = "actorUsername", required = false) String actorUsername,
			@RequestParam(name = "targetPk", required = false) String targetPk,
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			Model model) {

		String normalizedActorUsername = blankToNull(actorUsername);
		String normalizedTargetPk = blankToNull(targetPk);
		// 종료일(to)은 "그 날 하루 전체"를 포함하도록 자정 직전 시각까지로 넓혀서 비교한다.
		LocalDateTime fromInclusive = from != null ? from.atStartOfDay() : null;
		LocalDateTime toInclusive = to != null ? to.atTime(LocalTime.MAX) : null;

		List<AuditLog> logs = auditLogRepository.search(
				normalizedActorUsername, normalizedTargetPk, fromInclusive, toInclusive);

		model.addAttribute("logs", logs);
		model.addAttribute("actorUsername", actorUsername);
		model.addAttribute("targetPk", targetPk);
		model.addAttribute("from", from);
		model.addAttribute("to", to);

		return "audit/list";
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}
}
