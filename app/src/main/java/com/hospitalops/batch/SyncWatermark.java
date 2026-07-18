package com.hospitalops.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Phase 2 Step 2.1: 리소스 타입별 마지막 동기화 시각(워터마크). SyncJob의 각 스텝은
 * 이 값보다 {@code updated_at}이 이후인 레거시 row만 다시 읽는다(증분 pull).
 *
 * <p>PK는 레거시 테이블명이 아니라 변환 대상 FHIR 리소스 타입명("Patient","Encounter",
 * "Observation","MedicationRequest")이다 — FHIR_RESOURCE_CACHE.resource_type과 같은
 * 어휘를 써서 두 테이블을 자연스럽게 대응시키기 위함.</p>
 */
@Entity
@Table(name = "SYNC_WATERMARK")
public class SyncWatermark {

	@Id
	@Column(name = "resource_type", length = 30)
	private String resourceType;

	@Column(name = "last_synced_at", nullable = false)
	private LocalDateTime lastSyncedAt;

	protected SyncWatermark() {
		// JPA
	}

	public SyncWatermark(String resourceType, LocalDateTime lastSyncedAt) {
		this.resourceType = resourceType;
		this.lastSyncedAt = lastSyncedAt;
	}

	public String getResourceType() {
		return resourceType;
	}

	public LocalDateTime getLastSyncedAt() {
		return lastSyncedAt;
	}

	public void setLastSyncedAt(LocalDateTime lastSyncedAt) {
		this.lastSyncedAt = lastSyncedAt;
	}
}
