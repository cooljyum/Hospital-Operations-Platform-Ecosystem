package com.hospitalops.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Phase 3 Step 3.3: AUDIT_LOG 테이블 매핑.
 *
 * <p>PLAN.md Phase 5.1이 정식으로 만들기로 계획했던 스키마를 Step 3.3(break-glass)이
 * 먼저 필요로 해서 앞당겨 만들었다(V10 마이그레이션 주석 참고). 컬럼은 deliverable.md
 * §3.3/Phase 5.1 원문 그대로: 행위자/대상PK/행위/목적/마스킹여부/IP/성공여부/시각 +
 * break-glass 전용 result_code. 쿼리 원문(SQL 리터럴)은 어떤 필드에도 담지 않는다 —
 * {@code purpose}는 사용자가 입력한 자유텍스트 사유일 뿐이다.</p>
 */
@Entity
@Table(name = "AUDIT_LOG")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "audit_id")
	private Long auditId;

	@Column(name = "actor_username", nullable = false, length = 50)
	private String actorUsername;

	@Column(name = "target_pk", length = 100)
	private String targetPk;

	@Column(name = "action", nullable = false, length = 100)
	private String action;

	@Column(name = "purpose", length = 500)
	private String purpose;

	@Column(name = "masked", nullable = false)
	private boolean masked;

	@Column(name = "ip_address", length = 45)
	private String ipAddress;

	@Column(name = "success", nullable = false)
	private boolean success;

	@Column(name = "result_code", nullable = false, length = 50)
	private String resultCode;

	@Column(name = "occurred_at", insertable = false, updatable = false)
	private LocalDateTime occurredAt;

	protected AuditLog() {
		// JPA
	}

	public AuditLog(String actorUsername, String targetPk, String action, String purpose,
			boolean masked, String ipAddress, boolean success, String resultCode) {
		this.actorUsername = actorUsername;
		this.targetPk = targetPk;
		this.action = action;
		this.purpose = purpose;
		this.masked = masked;
		this.ipAddress = ipAddress;
		this.success = success;
		this.resultCode = resultCode;
	}

	public Long getAuditId() {
		return auditId;
	}

	public String getActorUsername() {
		return actorUsername;
	}

	public String getTargetPk() {
		return targetPk;
	}

	public String getAction() {
		return action;
	}

	public String getPurpose() {
		return purpose;
	}

	public boolean isMasked() {
		return masked;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public boolean isSuccess() {
		return success;
	}

	public String getResultCode() {
		return resultCode;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}
}
