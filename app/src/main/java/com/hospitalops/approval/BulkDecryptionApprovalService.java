package com.hospitalops.approval;

import com.hospitalops.security.AuditLog;
import com.hospitalops.security.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 4 Step 4.5: 대량 복호 요청/승인/거부 + 각 전이마다 AuditLog(AUDIT_LOG) 1건 기록.
 *
 * <p>흐름: {@link #createRequest(String, String, String)}(항상 PENDING 생성) ->
 * {@link #approve(Long, String, String, String)} 또는
 * {@link #reject(Long, String, String, String)}(관리자 결정, PENDING에서만 가능) ->
 * {@link #ensureApprovedForDecryption(Long)}(실제 대량 복호 실행 전 게이트 - APPROVED가
 * 아니면 예외).</p>
 *
 * <p>전이가 실패(잘못된 상태에서 시도)하면 {@link BulkDecryptionRequest}가 상태를 바꾸지
 * 않고 예외를 던지므로, 이 서비스도 그 지점에서 저장·감사로그 기록 없이 예외를 그대로
 * 전파한다 - 즉 AuditLog는 오직 <b>실제로 일어난</b> 전이에 대해서만 1건씩 생긴다.</p>
 */
@Service
public class BulkDecryptionApprovalService {

	static final String ACTION_REQUEST_CREATED = "BULK_DECRYPTION_REQUEST_CREATED";
	static final String ACTION_APPROVED = "BULK_DECRYPTION_APPROVED";
	static final String ACTION_REJECTED = "BULK_DECRYPTION_REJECTED";
	static final String RESULT_CODE_PENDING = "BULK_DECRYPTION_PENDING";
	static final String RESULT_CODE_APPROVED = "BULK_DECRYPTION_APPROVED";
	static final String RESULT_CODE_REJECTED = "BULK_DECRYPTION_REJECTED";

	private final BulkDecryptionRequestRepository repository;
	private final AuditLogRepository auditLogRepository;

	public BulkDecryptionApprovalService(BulkDecryptionRequestRepository repository,
			AuditLogRepository auditLogRepository) {
		this.repository = repository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional
	public BulkDecryptionRequest createRequest(String requestedBy, String reason, String ipAddress) {
		BulkDecryptionRequest saved = repository.save(new BulkDecryptionRequest(requestedBy, reason));

		auditLogRepository.save(new AuditLog(
				requestedBy,
				String.valueOf(saved.getRequestId()),
				ACTION_REQUEST_CREATED,
				reason,
				false,
				ipAddress,
				true,
				RESULT_CODE_PENDING));

		return saved;
	}

	@Transactional
	public BulkDecryptionRequest approve(Long requestId, String decidedBy, String decisionNote, String ipAddress) {
		BulkDecryptionRequest request = findOrThrow(requestId);
		request.approve(decidedBy, decisionNote); // PENDING이 아니면 여기서 예외 - 아래 저장/감사로그 실행 안 됨
		BulkDecryptionRequest saved = repository.save(request);

		auditLogRepository.save(new AuditLog(
				decidedBy,
				String.valueOf(requestId),
				ACTION_APPROVED,
				decisionNote,
				false,
				ipAddress,
				true,
				RESULT_CODE_APPROVED));

		return saved;
	}

	@Transactional
	public BulkDecryptionRequest reject(Long requestId, String decidedBy, String decisionNote, String ipAddress) {
		BulkDecryptionRequest request = findOrThrow(requestId);
		request.reject(decidedBy, decisionNote); // PENDING이 아니면 여기서 예외 - 아래 저장/감사로그 실행 안 됨
		BulkDecryptionRequest saved = repository.save(request);

		auditLogRepository.save(new AuditLog(
				decidedBy,
				String.valueOf(requestId),
				ACTION_REJECTED,
				decisionNote,
				false,
				ipAddress,
				true,
				RESULT_CODE_REJECTED));

		return saved;
	}

	/**
	 * 실제 대량 복호(EnvelopeCrypto 해제) 실행 전 게이트. APPROVED가 아니면 예외를 던져
	 * 복호 자체가 실행되지 않게 막는다 - "승인된 것만 실제 복호 실행 가능"의 강제 지점.
	 */
	public void ensureApprovedForDecryption(Long requestId) {
		BulkDecryptionRequest request = findOrThrow(requestId);
		if (request.getStatus() != BulkDecryptionStatus.APPROVED) {
			throw new IllegalStateException(
					"승인되지 않은 요청은 대량 복호를 실행할 수 없습니다(requestId=" + requestId
							+ ", status=" + request.getStatus() + ")");
		}
	}

	private BulkDecryptionRequest findOrThrow(Long requestId) {
		return repository.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("대량 복호 요청을 찾을 수 없습니다: " + requestId));
	}
}
