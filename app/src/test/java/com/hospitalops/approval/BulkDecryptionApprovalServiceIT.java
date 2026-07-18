package com.hospitalops.approval;

import com.hospitalops.security.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 Step 4.5 acceptance criteria 검증: 각 상태 전이마다 AuditEvent(AUDIT_LOG)가
 * 정확히 1건씩 생성되고, 잘못된 전이 시도는 거부되며 그 시도만으로는 추가 AuditLog가
 * 생기지 않는다. 로컬 실제 MySQL(hospital_ops)에 대해 실제 저장/조회를 수행한다.
 * {@code @Transactional}로 테스트 종료 시 자동 롤백한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class BulkDecryptionApprovalServiceIT {

	@Autowired
	private BulkDecryptionApprovalService service;

	@Autowired
	private BulkDecryptionRequestRepository requestRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Test
	void creatingARequestPersistsPendingRowAndOneAuditLogEntry() {
		long auditCountBefore = auditLogRepository.count();

		BulkDecryptionRequest created = service.createRequest("admin", "감사 목적 대량 복호 요청", "127.0.0.1");

		assertThat(created.getRequestId()).isNotNull();
		assertThat(created.getStatus()).isEqualTo(BulkDecryptionStatus.PENDING);
		assertThat(requestRepository.findById(created.getRequestId())).isPresent();
		assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore + 1);
	}

	@Test
	void approvingAPendingRequestTransitionsItAndAddsExactlyOneAuditLogEntry() {
		BulkDecryptionRequest created = service.createRequest("admin", "reason", "127.0.0.1");
		long auditCountAfterCreate = auditLogRepository.count();

		BulkDecryptionRequest approved = service.approve(created.getRequestId(), "auditor1", "승인함", "127.0.0.1");

		assertThat(approved.getStatus()).isEqualTo(BulkDecryptionStatus.APPROVED);
		assertThat(auditLogRepository.count()).isEqualTo(auditCountAfterCreate + 1);
	}

	@Test
	void rejectingAPendingRequestTransitionsItAndAddsExactlyOneAuditLogEntry() {
		BulkDecryptionRequest created = service.createRequest("admin", "reason", "127.0.0.1");
		long auditCountAfterCreate = auditLogRepository.count();

		BulkDecryptionRequest rejected = service.reject(created.getRequestId(), "auditor1", "거부함", "127.0.0.1");

		assertThat(rejected.getStatus()).isEqualTo(BulkDecryptionStatus.REJECTED);
		assertThat(auditLogRepository.count()).isEqualTo(auditCountAfterCreate + 1);
	}

	@Test
	void approvingAnAlreadyApprovedRequestFailsAndDoesNotAddAnAuditLogEntry() {
		BulkDecryptionRequest created = service.createRequest("admin", "reason", "127.0.0.1");
		service.approve(created.getRequestId(), "auditor1", "1차 승인", "127.0.0.1");
		long auditCountAfterFirstApproval = auditLogRepository.count();

		assertThatThrownBy(() -> service.approve(created.getRequestId(), "auditor2", "2차 승인 시도", "127.0.0.1"))
				.isInstanceOf(IllegalStateException.class);

		assertThat(auditLogRepository.count()).isEqualTo(auditCountAfterFirstApproval);
		assertThat(requestRepository.findById(created.getRequestId()).orElseThrow().getStatus())
				.isEqualTo(BulkDecryptionStatus.APPROVED);
	}

	@Test
	void rejectingAnAlreadyRejectedRequestFailsAndDoesNotAddAnAuditLogEntry() {
		BulkDecryptionRequest created = service.createRequest("admin", "reason", "127.0.0.1");
		service.reject(created.getRequestId(), "auditor1", "1차 거부", "127.0.0.1");
		long auditCountAfterFirstRejection = auditLogRepository.count();

		assertThatThrownBy(() -> service.reject(created.getRequestId(), "auditor2", "2차 거부 시도", "127.0.0.1"))
				.isInstanceOf(IllegalStateException.class);

		assertThat(auditLogRepository.count()).isEqualTo(auditCountAfterFirstRejection);
	}

	@Test
	void approvedRequestCannotBeLaterRejected() {
		BulkDecryptionRequest created = service.createRequest("admin", "reason", "127.0.0.1");
		service.approve(created.getRequestId(), "auditor1", "승인함", "127.0.0.1");

		assertThatThrownBy(() -> service.reject(created.getRequestId(), "auditor2", "번복 시도", "127.0.0.1"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void bulkDecryptionIsGatedOnApprovedStatusOnly() {
		BulkDecryptionRequest pending = service.createRequest("admin", "reason", "127.0.0.1");

		assertThatThrownBy(() -> service.ensureApprovedForDecryption(pending.getRequestId()))
				.as("PENDING 상태에서는 복호가 게이팅되어야 한다")
				.isInstanceOf(IllegalStateException.class);

		service.approve(pending.getRequestId(), "auditor1", "승인함", "127.0.0.1");

		assertThatCode(() -> service.ensureApprovedForDecryption(pending.getRequestId()))
				.as("APPROVED 상태에서는 복호가 허용되어야 한다")
				.doesNotThrowAnyException();
	}

	@Test
	void bulkDecryptionIsGatedForRejectedRequestsToo() {
		BulkDecryptionRequest request = service.createRequest("admin", "reason", "127.0.0.1");
		service.reject(request.getRequestId(), "auditor1", "거부함", "127.0.0.1");

		assertThatThrownBy(() -> service.ensureApprovedForDecryption(request.getRequestId()))
				.isInstanceOf(IllegalStateException.class);
	}
}
