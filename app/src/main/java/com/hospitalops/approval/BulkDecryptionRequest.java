package com.hospitalops.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Phase 4 Step 4.5: BULK_DECRYPTION_REQUEST 테이블 매핑 + 3단계 승인 상태머신.
 *
 * <p>Step 4.2(EnvelopeCrypto)의 대량 복호 요청을 게이팅하는 워크플로: "복호 요청을
 * 만든다({@link #BulkDecryptionRequest(String, String)}, 항상 {@code PENDING}) ->
 * 관리자가 승인({@link #approve(String, String)})/거부({@link #reject(String, String)})
 * -> 승인된 것만 실제 복호 실행 가능({@code BulkDecryptionApprovalService#
 * ensureApprovedForDecryption})".</p>
 *
 * <p>상태 전이 규칙은 이 엔티티 자신이 강제한다(도메인 불변식) - {@code PENDING}
 * 상태일 때만 {@code approve}/{@code reject}를 허용하고, 그 외 어떤 상태에서 호출돼도
 * {@link IllegalStateException}을 던지며 필드는 전혀 바뀌지 않는다. 즉 역행(APPROVED/
 * REJECTED -> 다른 상태)이 구조적으로 불가능하다.</p>
 */
@Entity
@Table(name = "BULK_DECRYPTION_REQUEST")
public class BulkDecryptionRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "request_id")
	private Long requestId;

	@Column(name = "requested_by", nullable = false, length = 50)
	private String requestedBy;

	@Column(name = "reason", nullable = false, length = 500)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private BulkDecryptionStatus status;

	@Column(name = "requested_at", insertable = false, updatable = false)
	private LocalDateTime requestedAt;

	@Column(name = "decided_by", length = 50)
	private String decidedBy;

	@Column(name = "decided_at")
	private LocalDateTime decidedAt;

	@Column(name = "decision_note", length = 500)
	private String decisionNote;

	protected BulkDecryptionRequest() {
		// JPA
	}

	/** 새 요청은 항상 {@link BulkDecryptionStatus#PENDING}으로 시작한다. */
	public BulkDecryptionRequest(String requestedBy, String reason) {
		this.requestedBy = requestedBy;
		this.reason = reason;
		this.status = BulkDecryptionStatus.PENDING;
	}

	/** {@code PENDING -> APPROVED}. PENDING이 아니면 예외를 던지고 상태는 그대로다. */
	public void approve(String decidedBy, String decisionNote) {
		transitionTo(BulkDecryptionStatus.APPROVED, decidedBy, decisionNote);
	}

	/** {@code PENDING -> REJECTED}. PENDING이 아니면 예외를 던지고 상태는 그대로다. */
	public void reject(String decidedBy, String decisionNote) {
		transitionTo(BulkDecryptionStatus.REJECTED, decidedBy, decisionNote);
	}

	private void transitionTo(BulkDecryptionStatus target, String decidedBy, String decisionNote) {
		if (this.status != BulkDecryptionStatus.PENDING) {
			throw new IllegalStateException(
					"PENDING 상태에서만 전이할 수 있습니다(현재 상태=" + this.status
							+ ", 시도한 전이=" + target + ", requestId=" + requestId + ")");
		}
		this.status = target;
		this.decidedBy = decidedBy;
		this.decisionNote = decisionNote;
		this.decidedAt = LocalDateTime.now();
	}

	public Long getRequestId() {
		return requestId;
	}

	public String getRequestedBy() {
		return requestedBy;
	}

	public String getReason() {
		return reason;
	}

	public BulkDecryptionStatus getStatus() {
		return status;
	}

	public LocalDateTime getRequestedAt() {
		return requestedAt;
	}

	public String getDecidedBy() {
		return decidedBy;
	}

	public LocalDateTime getDecidedAt() {
		return decidedAt;
	}

	public String getDecisionNote() {
		return decisionNote;
	}
}
