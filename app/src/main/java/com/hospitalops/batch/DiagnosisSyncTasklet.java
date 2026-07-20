package com.hospitalops.batch;

import com.hospitalops.fhir.DiagnosisMapper;
import com.hospitalops.fhir.DiagnosisRow;
import com.hospitalops.fhir.FhirResourceCacheUpsertService;
import org.hl7.fhir.r4.model.Condition;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.hospitalops.batch.JdbcTemporalSupport.toLocalDate;
import static com.hospitalops.batch.JdbcTemporalSupport.toLong;
import static com.hospitalops.batch.JdbcTemporalSupport.toText;

/**
 * Phase 10 Step 10.2: DIAGNOSIS(레거시 HIS) -> FHIR {@code Condition} 증분 동기화 스텝.
 *
 * <p>기존 4종(Patient/Encounter/Observation/MedicationRequest)의 SyncTasklet과 동일한
 * 패턴(워터마크 기반 증분 pull -> 순수 매퍼 호출 -> {@link FhirResourceCacheUpsertService}로
 * 멱등 upsert)을 따른다. CODE_SET 조회가 없다는 점만 Observation/MedicationRequest와
 * 다르다(DiagnosisRow.codeSystem이 이미 완전한 FHIR 시스템 URI -- {@link DiagnosisMapper}
 * 참고).</p>
 */
@Component
public class DiagnosisSyncTasklet implements Tasklet {

	static final String RESOURCE_TYPE = "Condition";
	private static final String LEGACY_TABLE = "DIAGNOSIS";

	private final JdbcTemplate jdbcTemplate;
	private final SyncWatermarkService watermarkService;
	private final FhirResourceCacheUpsertService cacheUpsertService;

	public DiagnosisSyncTasklet(JdbcTemplate jdbcTemplate, SyncWatermarkService watermarkService,
			FhirResourceCacheUpsertService cacheUpsertService) {
		this.jdbcTemplate = jdbcTemplate;
		this.watermarkService = watermarkService;
		this.cacheUpsertService = cacheUpsertService;
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		LocalDateTime watermark = watermarkService.currentWatermark(RESOURCE_TYPE);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT diagnosis_id, visit_id, patient_id, diagnosed_at, resolved_at, code_system, code, description " +
						"FROM DIAGNOSIS WHERE updated_at > ? ORDER BY updated_at",
				watermark);

		for (Map<String, Object> row : rows) {
			DiagnosisRow diagnosisRow = toRow(row);
			Condition resource = DiagnosisMapper.toFhir(diagnosisRow);
			// "patient-" + patientId는 DiagnosisMapper가 Reference("Patient/patient-" + ...)에
			// 이미 쓰고 있는 것과 동일한 어휘다(Step 2.4 search의 patient_fhir_id).
			cacheUpsertService.upsert(RESOURCE_TYPE, diagnosisRow.diagnosisId(), resource,
					"patient-" + diagnosisRow.patientId());
		}

		watermarkService.advanceWatermarkToTableMax(RESOURCE_TYPE, LEGACY_TABLE);
		contribution.incrementWriteCount(rows.size());
		return RepeatStatus.FINISHED;
	}

	private static DiagnosisRow toRow(Map<String, Object> row) {
		return new DiagnosisRow(
				toLong(row.get("diagnosis_id")),
				toLong(row.get("patient_id")),
				toLong(row.get("visit_id")),
				toLocalDate(row.get("diagnosed_at")),
				toLocalDate(row.get("resolved_at")),
				toText(row.get("code_system")),
				toText(row.get("code")),
				toText(row.get("description")));
	}
}
