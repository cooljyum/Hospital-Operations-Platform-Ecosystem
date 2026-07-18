package com.hospitalops.batch;

import com.hospitalops.fhir.EncounterMapper;
import com.hospitalops.fhir.EncounterRow;
import com.hospitalops.fhir.FhirResourceCacheUpsertService;
import org.hl7.fhir.r4.model.Encounter;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.hospitalops.batch.JdbcTemporalSupport.toLocalDateTime;
import static com.hospitalops.batch.JdbcTemporalSupport.toLong;
import static com.hospitalops.batch.JdbcTemporalSupport.toText;

/**
 * Phase 2 Step 2.1/2.3: VISIT(레거시 HIS) -> FHIR {@code Encounter} 증분 동기화 스텝.
 * Step 2.3에서 매핑 로직을 {@code com.hospitalops.fhir.EncounterMapper}로 추출했다.
 */
@Component
public class EncounterSyncTasklet implements Tasklet {

	static final String RESOURCE_TYPE = "Encounter";
	private static final String LEGACY_TABLE = "VISIT";

	private final JdbcTemplate jdbcTemplate;
	private final SyncWatermarkService watermarkService;
	private final FhirResourceCacheUpsertService cacheUpsertService;

	public EncounterSyncTasklet(JdbcTemplate jdbcTemplate, SyncWatermarkService watermarkService,
			FhirResourceCacheUpsertService cacheUpsertService) {
		this.jdbcTemplate = jdbcTemplate;
		this.watermarkService = watermarkService;
		this.cacheUpsertService = cacheUpsertService;
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		LocalDateTime watermark = watermarkService.currentWatermark(RESOURCE_TYPE);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT visit_id, patient_id, started_at, stopped_at, encounter_class, encounter_code, " +
						"encounter_description, reason_code, reason_description " +
						"FROM VISIT WHERE updated_at > ? ORDER BY updated_at",
				watermark);

		for (Map<String, Object> row : rows) {
			EncounterRow encounterRow = toRow(row);
			Encounter resource = EncounterMapper.toFhir(encounterRow);
			cacheUpsertService.upsert(RESOURCE_TYPE, encounterRow.visitId(), resource);
		}

		watermarkService.advanceWatermarkToTableMax(RESOURCE_TYPE, LEGACY_TABLE);
		contribution.incrementWriteCount(rows.size());
		return RepeatStatus.FINISHED;
	}

	private static EncounterRow toRow(Map<String, Object> row) {
		return new EncounterRow(
				toLong(row.get("visit_id")),
				toLong(row.get("patient_id")),
				toLocalDateTime(row.get("started_at")),
				toLocalDateTime(row.get("stopped_at")),
				toText(row.get("encounter_class")),
				toText(row.get("encounter_code")),
				toText(row.get("encounter_description")),
				toText(row.get("reason_code")),
				toText(row.get("reason_description")));
	}
}
