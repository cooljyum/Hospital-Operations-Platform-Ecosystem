package com.hospitalops.approval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 Step 4.5 acceptance criteria 검증(핵심): 상태 전이가 PENDING -> APPROVED
 * 또는 PENDING -> REJECTED로만 가능하고, 그 반대나 APPROVED/REJECTED에서 다른 상태로의
 * 전이는 전부 불가(역행 불가)함을 실패 케이스로 직접 검증한다. 순수 도메인 객체라 Spring
 * 컨텍스트/DB 없이 검증한다.
 */
class BulkDecryptionRequestTests {

	@Test
	void newRequestStartsAsPending() {
		BulkDecryptionRequest request = new BulkDecryptionRequest("admin", "감사 목적 대량 복호");

		assertThat(request.getStatus()).isEqualTo(BulkDecryptionStatus.PENDING);
		assertThat(request.getDecidedBy()).isNull();
		assertThat(request.getDecidedAt()).isNull();
	}

	@Test
	void pendingCanTransitionToApproved() {
		BulkDecryptionRequest request = new BulkDecryptionRequest("admin", "reason");

		request.approve("auditor1", "승인함");

		assertThat(request.getStatus()).isEqualTo(BulkDecryptionStatus.APPROVED);
		assertThat(request.getDecidedBy()).isEqualTo("auditor1");
		assertThat(request.getDecisionNote()).isEqualTo("승인함");
		assertThat(request.getDecidedAt()).isNotNull();
	}

	@Test
	void pendingCanTransitionToRejected() {
		BulkDecryptionRequest request = new BulkDecryptionRequest("admin", "reason");

		request.reject("auditor1", "거부함");

		assertThat(request.getStatus()).isEqualTo(BulkDecryptionStatus.REJECTED);
		assertThat(request.getDecidedBy()).isEqualTo("auditor1");
		assertThat(request.getDecisionNote()).isEqualTo("거부함");
	}

	@Test
	void approvedCannotBeApprovedAgain() {
		BulkDecryptionRequest request = new BulkDecryptionRequest("admin", "reason");
		request.approve("auditor1", "승인함");

		assertThatThrownBy(() -> request.approve("auditor2", "다시 승인 시도"))
				.isInstanceOf(IllegalStateException.class);
		// 상태/결정자 정보가 두 번째 시도로 바뀌면 안 된다.
		assertThat(request.getStatus()).isEqualTo(BulkDecryptionStatus.APPROVED);
		assertThat(request.getDecidedBy()).isEqualTo("auditor1");
	}

	@Test
	void approvedCannotBeRejected() {
		BulkDecryptionRequest request = new BulkDecryptionRequest("admin", "reason");
		request.approve("auditor1", "승인함");

		assertThatThrownBy(() -> request.reject("auditor2", "뒤늦은 거부 시도"))
				.isInstanceOf(IllegalStateException.class);
		assertThat(request.getStatus()).isEqualTo(BulkDecryptionStatus.APPROVED);
	}

	@Test
	void rejectedCannotBeApproved() {
		BulkDecryptionRequest request = new BulkDecryptionRequest("admin", "reason");
		request.reject("auditor1", "거부함");

		assertThatThrownBy(() -> request.approve("auditor2", "뒤늦은 승인 시도"))
				.isInstanceOf(IllegalStateException.class);
		assertThat(request.getStatus()).isEqualTo(BulkDecryptionStatus.REJECTED);
	}

	@Test
	void rejectedCannotBeRejectedAgain() {
		BulkDecryptionRequest request = new BulkDecryptionRequest("admin", "reason");
		request.reject("auditor1", "거부함");

		assertThatThrownBy(() -> request.reject("auditor2", "다시 거부 시도"))
				.isInstanceOf(IllegalStateException.class);
		assertThat(request.getStatus()).isEqualTo(BulkDecryptionStatus.REJECTED);
	}

	@Test
	void thereIsNoWayToTransitionBackToPending() {
		// BulkDecryptionRequest에는 PENDING으로 되돌리는 메서드 자체가 없다 -
		// approve/reject 둘 다 PENDING에서 시작해서 각각 APPROVED/REJECTED로만 간다.
		// 이 테스트는 그 계약을 문서화한다: 생성자만이 PENDING을 만드는 유일한 경로다.
		BulkDecryptionRequest approved = new BulkDecryptionRequest("admin", "reason");
		approved.approve("auditor1", "승인함");
		assertThat(approved.getStatus()).isNotEqualTo(BulkDecryptionStatus.PENDING);

		BulkDecryptionRequest rejected = new BulkDecryptionRequest("admin", "reason");
		rejected.reject("auditor1", "거부함");
		assertThat(rejected.getStatus()).isNotEqualTo(BulkDecryptionStatus.PENDING);
	}
}
