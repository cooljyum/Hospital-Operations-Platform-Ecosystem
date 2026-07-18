package com.hospitalops.batch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Phase 2 Step 2.1: 리소스 타입별 워터마크 조회/전진 로직을 한 곳에 모은다(4개
 * Tasklet이 공통으로 사용).
 *
 * <p>워터마크는 "이번 실행에서 읽은 row들의 updated_at 중 최댓값"이 아니라 항상
 * "레거시 테이블 전체의 현재 MAX(updated_at)"으로 전진시킨다 — 두 값이 실행 시점에
 * 같은 테이블을 보는 한 결과적으로 같지만, 후자가 "이번에 실제로 읽은 행이 0건이라도
 * 워터마크 자체는 안전하게 유지된다"를 별도 분기 없이 보장해 더 단순하다.</p>
 */
@Service
public class SyncWatermarkService {

	static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0, 0);

	private final SyncWatermarkRepository repository;
	private final JdbcTemplate jdbcTemplate;

	public SyncWatermarkService(SyncWatermarkRepository repository, JdbcTemplate jdbcTemplate) {
		this.repository = repository;
		this.jdbcTemplate = jdbcTemplate;
	}

	public LocalDateTime currentWatermark(String resourceType) {
		return repository.findById(resourceType)
				.map(SyncWatermark::getLastSyncedAt)
				.orElse(EPOCH);
	}

	/**
	 * @param resourceType    SYNC_WATERMARK/FHIR_RESOURCE_CACHE와 공유하는 FHIR 리소스 타입명
	 * @param legacyTableName 워터마크 산출 기준이 되는 레거시 테이블명(고정 상수만 호출부에서 넘긴다)
	 */
	@Transactional
	public void advanceWatermarkToTableMax(String resourceType, String legacyTableName) {
		LocalDateTime maxUpdatedAt = jdbcTemplate.queryForObject(
				"SELECT MAX(updated_at) FROM " + legacyTableName, LocalDateTime.class);
		if (maxUpdatedAt == null) {
			// 레거시 테이블이 비어 있으면 전진시킬 값이 없다.
			return;
		}
		LocalDateTime current = currentWatermark(resourceType);
		if (maxUpdatedAt.isAfter(current)) {
			SyncWatermark watermark = repository.findById(resourceType)
					.orElseGet(() -> new SyncWatermark(resourceType, EPOCH));
			watermark.setLastSyncedAt(maxUpdatedAt);
			repository.save(watermark);
		}
	}
}
