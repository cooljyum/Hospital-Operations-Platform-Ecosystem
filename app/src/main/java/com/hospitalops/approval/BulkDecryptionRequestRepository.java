package com.hospitalops.approval;

import org.springframework.data.jpa.repository.JpaRepository;

/** Phase 4 Step 4.5: BULK_DECRYPTION_REQUEST 전용 Repository. */
public interface BulkDecryptionRequestRepository extends JpaRepository<BulkDecryptionRequest, Long> {
}
