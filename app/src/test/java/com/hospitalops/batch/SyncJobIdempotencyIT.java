package com.hospitalops.batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 Step 2.1 acceptance criteria 검증(PLAN.md §4):
 * "job 2회 연속 실행 시 2회차에 신규 upsert 0건(멱등 확인), 워터마크 테이블에 마지막
 * 실행 시각 기록".
 *
 * <p>로컬 실제 MySQL(hospital_ops, Phase 1에서 이미 Synthea 데이터가 적재돼 있다는
 * 전제 — PATIENT 12건 등)에 대해 직접 fhirSyncJob을 두 번 연속 실행해 확인한다.
 * H2로 대체하지 않는다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SyncJobIdempotencyIT {

	@Autowired
	private SyncJobRunner syncJobRunner;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void secondConsecutiveRunProducesNoNewCacheRowsAndWatermarkIsRecorded() throws Exception {
		JobExecution first = syncJobRunner.runOnce();
		assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);

		long cacheCountAfterFirst = count("FHIR_RESOURCE_CACHE");
		// Phase 1 적재 데이터가 있다는 전제(PATIENT 12건 등) — 최초 실행에서 4개
		// 리소스 타입 전부에 대해 최소 1건 이상 upsert 됐어야 한다.
		assertThat(cacheCountAfterFirst).isGreaterThan(0);

		List<Map<String, Object>> countsByType = jdbcTemplate.queryForList(
				"SELECT resource_type, COUNT(*) AS cnt FROM FHIR_RESOURCE_CACHE GROUP BY resource_type");
		Map<String, Long> byType = countsByType.stream().collect(java.util.stream.Collectors.toMap(
				row -> (String) row.get("resource_type"),
				row -> ((Number) row.get("cnt")).longValue()));
		assertThat(byType).containsOnlyKeys("Patient", "Encounter", "Observation", "MedicationRequest", "Condition");
		byType.values().forEach(cnt -> assertThat(cnt).isGreaterThan(0L));

		// 각 리소스 타입의 이번 synced_at 스냅샷(2회차 실행 후 "하나도 안 바뀌었는지"
		// 대조하기 위한 기준선).
		List<Map<String, Object>> syncedAtAfterFirst = jdbcTemplate.queryForList(
				"SELECT resource_type, source_table_pk, synced_at FROM FHIR_RESOURCE_CACHE " +
						"ORDER BY resource_type, source_table_pk");

		Map<String, LocalDateTime> watermarksAfterFirst = jdbcTemplate.queryForList(
						"SELECT resource_type, last_synced_at FROM SYNC_WATERMARK")
				.stream()
				.collect(java.util.stream.Collectors.toMap(
						row -> (String) row.get("resource_type"),
						row -> JdbcTemporalSupport.toLocalDateTime(row.get("last_synced_at"))));
		assertThat(watermarksAfterFirst).containsOnlyKeys("Patient", "Encounter", "Observation", "MedicationRequest", "Condition");
		watermarksAfterFirst.values().forEach(ts -> assertThat(ts).isAfter(SyncWatermarkService.EPOCH));

		// --- 2회차 실행 ---
		JobExecution second = syncJobRunner.runOnce();
		assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);

		long cacheCountAfterSecond = count("FHIR_RESOURCE_CACHE");
		assertThat(cacheCountAfterSecond).isEqualTo(cacheCountAfterFirst);

		List<Map<String, Object>> syncedAtAfterSecond = jdbcTemplate.queryForList(
				"SELECT resource_type, source_table_pk, synced_at FROM FHIR_RESOURCE_CACHE " +
						"ORDER BY resource_type, source_table_pk");
		// 신규 upsert 0건 == 기존 행의 synced_at도 전혀 갱신되지 않아야 한다(워터마크가
		// 이미 1회차에서 테이블 MAX(updated_at)까지 전진했으므로 2회차는 대상 row가 0건).
		assertThat(syncedAtAfterSecond).isEqualTo(syncedAtAfterFirst);

		Map<String, LocalDateTime> watermarksAfterSecond = jdbcTemplate.queryForList(
						"SELECT resource_type, last_synced_at FROM SYNC_WATERMARK")
				.stream()
				.collect(java.util.stream.Collectors.toMap(
						row -> (String) row.get("resource_type"),
						row -> JdbcTemporalSupport.toLocalDateTime(row.get("last_synced_at"))));
		assertThat(watermarksAfterSecond).isEqualTo(watermarksAfterFirst);
	}

	private long count(String table) {
		Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
		return value == null ? 0 : value;
	}
}
