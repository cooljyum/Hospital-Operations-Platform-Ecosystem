package com.hospitalops.fhir;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Phase 2 Step 2.1: 레거시 HIS row를 FHIR R4 리소스 JSON으로 변환한 결과를 담는 캐시.
 * SyncJob(batch 패키지)이 이 테이블에 upsert하고, Step 2.4의 FhirController는 오직 이
 * 캐시(전용 Repository)만 조회한다 — 레거시 PATIENT/VISIT/LAB_RESULT/PRESCRIPTION
 * repository를 API 계층이 직접 참조하지 않게 하기 위한 경계다.
 *
 * <p>(resource_type, source_table_pk) 복합 UNIQUE가 멱등 upsert의 자연키다: 같은 레거시
 * row(예: 같은 patient_id)를 다시 동기화해도 새 행이 생기지 않고 기존 행이 갱신된다.
 * (resource_type, fhir_id)는 Step 2.4의 REST 조회(GET /fhir/{Type}/{id})가 사용하는
 * 별도 UNIQUE 조회 경로다.</p>
 */
@Entity
@Table(name = "FHIR_RESOURCE_CACHE", uniqueConstraints = {
		@UniqueConstraint(name = "uq_fhir_cache_type_pk", columnNames = {"resource_type", "source_table_pk"}),
		@UniqueConstraint(name = "uq_fhir_cache_type_fhir_id", columnNames = {"resource_type", "fhir_id"})
})
public class FhirResourceCache {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "resource_type", nullable = false, length = 30)
	private String resourceType;

	@Column(name = "source_table_pk", nullable = false)
	private Long sourceTablePk;

	@Column(name = "fhir_id", nullable = false, length = 64)
	private String fhirId;

	@Lob
	@Column(name = "resource_json", nullable = false, columnDefinition = "LONGTEXT")
	private String resourceJson;

	@Column(name = "synced_at", nullable = false)
	private LocalDateTime syncedAt;

	protected FhirResourceCache() {
		// JPA
	}

	public Long getId() {
		return id;
	}

	public String getResourceType() {
		return resourceType;
	}

	public void setResourceType(String resourceType) {
		this.resourceType = resourceType;
	}

	public Long getSourceTablePk() {
		return sourceTablePk;
	}

	public void setSourceTablePk(Long sourceTablePk) {
		this.sourceTablePk = sourceTablePk;
	}

	public String getFhirId() {
		return fhirId;
	}

	public void setFhirId(String fhirId) {
		this.fhirId = fhirId;
	}

	public String getResourceJson() {
		return resourceJson;
	}

	public void setResourceJson(String resourceJson) {
		this.resourceJson = resourceJson;
	}

	public LocalDateTime getSyncedAt() {
		return syncedAt;
	}

	public void setSyncedAt(LocalDateTime syncedAt) {
		this.syncedAt = syncedAt;
	}
}
