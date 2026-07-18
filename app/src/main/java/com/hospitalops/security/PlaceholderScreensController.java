package com.hospitalops.security;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Phase 3 Step 3.2: RBAC 5역할이 "화면별로 접근 가부가 실제로 다르다"를 증명할 실제
 * 화면이 아직 /dashboard 하나뿐이므로, 이후 Phase에서 채워질 URL을 미리 역할별로
 * 게이팅하는 최소 placeholder 화면을 둔다. 인가 결정 자체는 이 컨트롤러가 아니라
 * {@link SecurityConfig}가 ACCESS_POLICY_RULES 테이블 기반으로 내린다(이 컨트롤러는
 * 인증된 요청이 이미 통과한 뒤의 뷰 렌더링만 담당).
 *
 * <p>{@code /audit/preview}(감사 로그 placeholder)는 Phase 5 Step 5.2에서
 * {@code com.hospitalops.audit.AuditLogController}(실제 감사 로그 조회 화면)로
 * 대체되어 이 클래스에서 제거됐다 - ACCESS_POLICY_RULES의 해당 row는 그대로 재사용된다.</p>
 */
@Controller
public class PlaceholderScreensController {

	/** ROLE_SYSTEM_ADMIN 전용. 추후 시스템 관리 화면으로 대체될 더미. */
	@GetMapping("/admin/system")
	public String adminSystem() {
		return "admin-system";
	}
}
