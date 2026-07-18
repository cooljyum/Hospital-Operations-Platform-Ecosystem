package com.hospitalops.batch;

import com.hospitalops.fhir.CodeSetLookupService;
import com.hospitalops.fhir.FhirResourceCacheUpsertService;
import com.hospitalops.fhir.MedicationRequestMapper;
import com.hospitalops.fhir.MedicationRequestRow;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MedicationRequest;
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
 * Phase 2 Step 2.1/2.3: PRESCRIPTION(레거시 HIS) -> FHIR {@code MedicationRequest}
 * 증분 동기화 스텝. Step 2.3에서 매핑 로직을
 * {@code com.hospitalops.fhir.MedicationRequestMapper}로 추출하고,
 * PRESCRIPTION.medication_code -> RxNorm Coding 해석은
 * {@link CodeSetLookupService}로 이 Tasklet이 미리 수행한다.
 */
@Component
public class MedicationRequestSyncTasklet implements Tasklet {

	static final String RESOURCE_TYPE = "MedicationRequest";
	private static final String LEGACY_TABLE = "PRESCRIPTION";
	private static final String CODE_SYSTEM = "PRESCRIPTION";

	private final JdbcTemplate jdbcTemplate;
	private final SyncWatermarkService watermarkService;
	private final FhirResourceCacheUpsertService cacheUpsertService;
	private final CodeSetLookupService codeSetLookupService;

	public MedicationRequestSyncTasklet(JdbcTemplate jdbcTemplate, SyncWatermarkService watermarkService,
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
				"SELECT prescription_id, visit_id, patient_id, medication_code, medication_description, " +
						"started_at, stopped_at, reason_code, reason_description " +
						"FROM PRESCRIPTION WHERE updated_at > ? ORDER BY updated_at",
				watermark);

		for (Map<String, Object> row : rows) {
			MedicationRequestRow medicationRequestRow = toRow(row);
			Coding resolvedCode = codeSetLookupService.toCoding(
					CODE_SYSTEM, medicationRequestRow.medicationCode(), medicationRequestRow.medicationDescription());
			MedicationRequest resource = MedicationRequestMapper.toFhir(medicationRequestRow, resolvedCode);
			cacheUpsertService.upsert(RESOURCE_TYPE, medicationRequestRow.prescriptionId(), resource);
		}

		watermarkService.advanceWatermarkToTableMax(RESOURCE_TYPE, LEGACY_TABLE);
		contribution.incrementWriteCount(rows.size());
		return RepeatStatus.FINISHED;
	}

	private static MedicationRequestRow toRow(Map<String, Object> row) {
		return new MedicationRequestRow(
				toLong(row.get("prescription_id")),
				toLong(row.get("patient_id")),
				toLong(row.get("visit_id")),
				toText(row.get("medication_code")),
				toText(row.get("medication_description")),
				toLocalDateTime(row.get("started_at")),
				toLocalDateTime(row.get("stopped_at")),
				toText(row.get("reason_code")),
				toText(row.get("reason_description")));
	}
}
