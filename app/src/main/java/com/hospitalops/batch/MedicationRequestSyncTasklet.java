package com.hospitalops.batch;

import com.hospitalops.fhir.FhirResourceCacheUpsertService;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Reference;
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
 * Phase 2 Step 2.1: PRESCRIPTION(레거시 HIS) -> FHIR {@code MedicationRequest} 증분
 * 동기화 스텝.
 *
 * <p>PRESCRIPTION.medication_code도 ObservationSyncTasklet과 같은 이유로 Step 2.2
 * 코드셋 테이블 없이 원본 code/description 폴백만 둔다(Step 2.3에서 보강).</p>
 */
@Component
public class MedicationRequestSyncTasklet implements Tasklet {

	static final String RESOURCE_TYPE = "MedicationRequest";
	private static final String LEGACY_TABLE = "PRESCRIPTION";

	private final JdbcTemplate jdbcTemplate;
	private final SyncWatermarkService watermarkService;
	private final FhirResourceCacheUpsertService cacheUpsertService;

	public MedicationRequestSyncTasklet(JdbcTemplate jdbcTemplate, SyncWatermarkService watermarkService,
			FhirResourceCacheUpsertService cacheUpsertService) {
		this.jdbcTemplate = jdbcTemplate;
		this.watermarkService = watermarkService;
		this.cacheUpsertService = cacheUpsertService;
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
			long prescriptionId = toLong(row.get("prescription_id"));
			MedicationRequest resource = toFhirMedicationRequest(row, prescriptionId);
			cacheUpsertService.upsert(RESOURCE_TYPE, prescriptionId, resource);
		}

		watermarkService.advanceWatermarkToTableMax(RESOURCE_TYPE, LEGACY_TABLE);
		contribution.incrementWriteCount(rows.size());
		return RepeatStatus.FINISHED;
	}

	private MedicationRequest toFhirMedicationRequest(Map<String, Object> row, long prescriptionId) {
		MedicationRequest medicationRequest = new MedicationRequest();
		medicationRequest.setId("medicationrequest-" + prescriptionId);
		medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);

		long patientId = toLong(row.get("patient_id"));
		medicationRequest.setSubject(new Reference("Patient/patient-" + patientId));

		Long visitId = toLong(row.get("visit_id"));
		if (visitId != null) {
			medicationRequest.setEncounter(new Reference("Encounter/encounter-" + visitId));
		}

		LocalDateTime stoppedAt = toLocalDateTime(row.get("stopped_at"));
		medicationRequest.setStatus(stoppedAt != null ? MedicationRequest.MedicationRequestStatus.COMPLETED
				: MedicationRequest.MedicationRequestStatus.ACTIVE);

		String code = toText(row.get("medication_code"));
		String description = toText(row.get("medication_description"));
		CodeableConcept medicationConcept = new CodeableConcept();
		if (code != null) {
			// Step 2.3에서 CODE_SET(RxNorm) 조회로 보강할 자리.
			medicationConcept.addCoding(new Coding().setCode(code).setDisplay(description));
		}
		medicationConcept.setText(description != null ? description : code);
		medicationRequest.setMedication(medicationConcept);

		LocalDateTime startedAt = toLocalDateTime(row.get("started_at"));
		if (startedAt != null) {
			medicationRequest.setAuthoredOn(java.sql.Timestamp.valueOf(startedAt));
		}

		String reasonCode = toText(row.get("reason_code"));
		String reasonDescription = toText(row.get("reason_description"));
		if (reasonCode != null || reasonDescription != null) {
			CodeableConcept reason = new CodeableConcept();
			if (reasonCode != null) {
				reason.addCoding(new Coding().setCode(reasonCode).setDisplay(reasonDescription));
			}
			reason.setText(reasonDescription != null ? reasonDescription : reasonCode);
			medicationRequest.addReasonCode(reason);
		}

		return medicationRequest;
	}
}
