package com.hospitalops.batch;

import com.hospitalops.fhir.CodeSetLookupService;
import com.hospitalops.fhir.FhirResourceCacheUpsertService;
import com.hospitalops.fhir.ObservationMapper;
import com.hospitalops.fhir.ObservationRow;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
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
 * Phase 2 Step 2.1/2.3: LAB_RESULT(레거시 HIS) -> FHIR {@code Observation} 증분
 * 동기화 스텝.
 *
 * <p>Step 2.3에서 매핑 로직을 {@code com.hospitalops.fhir.ObservationMapper}로 추출하고,
 * LAB_RESULT.code -> LOINC Coding 해석은 {@link CodeSetLookupService}(CODE_SET 테이블
 * 조회)로 이 Tasklet이 미리 수행해 매퍼에 넘긴다(매퍼는 Repository에 의존하지 않는
 * 순수 함수로 유지).</p>
 */
@Component
public class ObservationSyncTasklet implements Tasklet {

	static final String RESOURCE_TYPE = "Observation";
	private static final String LEGACY_TABLE = "LAB_RESULT";
	private static final String CODE_SYSTEM = "LAB_RESULT";

	private final JdbcTemplate jdbcTemplate;
	private final SyncWatermarkService watermarkService;
	private final FhirResourceCacheUpsertService cacheUpsertService;
	private final CodeSetLookupService codeSetLookupService;

	public ObservationSyncTasklet(JdbcTemplate jdbcTemplate, SyncWatermarkService watermarkService,
			FhirResourceCacheUpsertService cacheUpsertService, CodeSetLookupService codeSetLookupService) {
		this.jdbcTemplate = jdbcTemplate;
		this.watermarkService = watermarkService;
		this.cacheUpsertService = cacheUpsertService;
		this.codeSetLookupService = codeSetLookupService;
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		LocalDateTime watermark = watermarkService.currentWatermark(RESOURCE_TYPE);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT lab_result_id, visit_id, patient_id, observed_at, category, code, description, " +
						"result_value, units, value_type " +
						"FROM LAB_RESULT WHERE updated_at > ? ORDER BY updated_at",
				watermark);

		for (Map<String, Object> row : rows) {
			ObservationRow observationRow = toRow(row);
			Coding resolvedCode = codeSetLookupService.toCoding(
					CODE_SYSTEM, observationRow.code(), observationRow.description());
			Observation resource = ObservationMapper.toFhir(observationRow, resolvedCode);
			// "patient-" + patientId는 ObservationMapper가 Reference("Patient/patient-" + ...)에
			// 이미 쓰고 있는 것과 동일한 어휘다(Step 2.4 search의 patient_fhir_id).
			cacheUpsertService.upsert(RESOURCE_TYPE, observationRow.labResultId(), resource,
					"patient-" + observationRow.patientId());
		}

		watermarkService.advanceWatermarkToTableMax(RESOURCE_TYPE, LEGACY_TABLE);
		contribution.incrementWriteCount(rows.size());
		return RepeatStatus.FINISHED;
	}

	private static ObservationRow toRow(Map<String, Object> row) {
		return new ObservationRow(
				toLong(row.get("lab_result_id")),
				toLong(row.get("patient_id")),
				toLong(row.get("visit_id")),
				toLocalDateTime(row.get("observed_at")),
				toText(row.get("category")),
				toText(row.get("code")),
				toText(row.get("description")),
				toText(row.get("result_value")),
				toText(row.get("units")),
				toText(row.get("value_type")));
	}
}
