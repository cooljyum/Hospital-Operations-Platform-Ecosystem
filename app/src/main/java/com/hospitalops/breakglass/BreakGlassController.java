package com.hospitalops.breakglass;

import com.hospitalops.security.AuditLog;
import com.hospitalops.security.AuditLogRepository;
import com.hospitalops.security.BreakGlassSessionAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Phase 3 Step 3.3: Break-glass 응급 접근.
 *
 * <p>deliverable.md §3.3: "응급 상황은 사유 필수 입력 후 접근을 허용하되, 전용 결과
 * 코드로 감사 기록 + Grafana 알림 + 사후 감사 대상으로 처리."</p>
 *
 * <p>흐름: {@code POST /breakglass/grant}(reason 필수) -> 세션에 임시 플래그 세팅 ->
 * 평소 막혀 있던 {@code /emergency/**}를 이 세션에 한해 한시적으로 허용
 * ({@link com.hospitalops.security.SecurityConfig}가 이 플래그를 검사) -> AUDIT_LOG에
 * 전용 result_code({@value #RESULT_CODE_GRANTED})로 기록 -> WARN 로그로 명확한 알림
 * 마커를 남긴다(Phase 7.2 Grafana 알림 규칙이 이 마커를 그대로 쓸 수 있게).</p>
 *
 * <p>실제 Slack/이메일 알림 연동은 범위 밖(PLAN.md 지시) — 구조화된 WARN 로그면
 * 충분하다.</p>
 */
@Controller
public class BreakGlassController {

	private static final Logger log = LoggerFactory.getLogger(BreakGlassController.class);

	private static final String RESULT_CODE_GRANTED = "BREAK_GLASS_GRANTED";
	private static final String ACTION_GRANT = "BREAK_GLASS_ACCESS_GRANT";

	private final AuditLogRepository auditLogRepository;

	public BreakGlassController(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@PostMapping("/breakglass/grant")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> grant(
			@RequestParam(required = false) String reason,
			HttpServletRequest request,
			Authentication authentication) {

		if (reason == null || reason.isBlank()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "reason은 필수 입력값입니다."));
		}

		HttpSession session = request.getSession(true);
		session.setAttribute(BreakGlassSessionAttributes.GRANTED, Boolean.TRUE);

		String actor = authentication != null ? authentication.getName() : "unknown";
		String ip = request.getRemoteAddr();

		auditLogRepository.save(new AuditLog(
				actor,
				null, // 대상 PK 없음 — 이 단계는 특정 환자 레코드가 아니라 접근 권한 부여 자체를 감사
				ACTION_GRANT,
				reason,
				false,
				ip,
				true,
				RESULT_CODE_GRANTED));

		// ALERT 마커: Phase 7.2 Grafana 알림 규칙이 이 로그 패턴으로 트리거하게 설계됨.
		log.warn("ALERT: break-glass access granted - actor={}, reason={}, ip={}", actor, reason, ip);

		return ResponseEntity.ok(Map.of("granted", true));
	}

	/**
	 * 평소엔 막혀 있지만 break-glass 세션 플래그가 있으면 열리는 placeholder 리소스.
	 * 실제 환자데이터 화면이 아직 없어(Phase 4/5 이후 채워짐), Step 3.2의 placeholder와
	 * 같은 성격의 더미 화면으로 대신한다.
	 */
	@GetMapping("/emergency/patient-record")
	public String emergencyPatientRecord() {
		return "emergency-patient-record";
	}
}
