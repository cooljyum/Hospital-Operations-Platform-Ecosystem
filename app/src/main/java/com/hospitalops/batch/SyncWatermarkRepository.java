package com.hospitalops.batch;

import org.springframework.data.jpa.repository.JpaRepository;

/** Phase 2 Step 2.1: SYNC_WATERMARK 전용 Repository. */
public interface SyncWatermarkRepository extends JpaRepository<SyncWatermark, String> {
}
