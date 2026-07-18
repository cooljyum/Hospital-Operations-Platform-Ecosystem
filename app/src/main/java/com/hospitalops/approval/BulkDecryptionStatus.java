package com.hospitalops.approval;

/**
 * Phase 4 Step 4.5: 대량 복호 요청의 3단계 상태.
 *
 * <p>허용되는 전이는 {@code PENDING -> APPROVED} 또는 {@code PENDING -> REJECTED}뿐이다.
 * 그 반대 방향이나 {@code APPROVED}/{@code REJECTED}에서 다른 상태로의 전이는 전부
 * 불가(역행 불가) - {@link BulkDecryptionRequest#approve(String, String)}/
 * {@link BulkDecryptionRequest#reject(String, String)}가 이 규칙을 강제한다.</p>
 */
public enum BulkDecryptionStatus {
	PENDING,
	APPROVED,
	REJECTED
}
