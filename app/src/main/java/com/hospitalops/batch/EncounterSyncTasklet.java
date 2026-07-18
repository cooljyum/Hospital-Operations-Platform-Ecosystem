package com.hospitalops.batch;

import com.hospitalops.fhir.FhirResourceCacheUpsertService;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;
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
 * Phase 2 Step 2.1: VISIT(레거시 HIS) -> FHIR {@code Encounter} 증분 동기화 스텝.
 *
 * <p>encounter_code/encounter_description은 Synthea 원본이 이미 SNOMED CT 코드+표시명
 * 쌍을 담고 있어(코드->시스템 매핑 테이블이 필요한 LAB_RESULT.code/PRESCRIPTION.
 * medication_code와 달리) Step 2.2 코드셋 테이블 없이도 고정 시스템(SNOMED CT)으로 바로
 * Coding을 구성할 수 있다.</p>
 */
@Component
public class EncounterSyncTasklet implements Tasklet {

	static final String RESOURCE_TYPE = "Encounter";
	private static final String LEGACY_TABLE = "VISIT";
	private static final String SNOMED_SYSTEM = "http://snomed.info/sct";

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
			long visitId = toLong(row.get("visit_id"));
			Encounter resource = toFhirEncounter(row, visitId);
			cacheUpsertService.upsert(RESOURCE_TYPE, visitId, resource);
		}

		watermarkService.advanceWatermarkToTableMax(RESOURCE_TYPE, LEGACY_TABLE);
		contribution.incrementWriteCount(rows.size());
		return RepeatStatus.FINISHED;
	}

	private Encounter toFhirEncounter(Map<String, Object> row, long visitId) {
		Encounter encounter = new Encounter();
		encounter.setId("encounter-" + visitId);

		long patientId = toLong(row.get("patient_id"));
		encounter.setSubject(new Reference("Patient/patient-" + patientId));

		LocalDateTime startedAt = toLocalDateTime(row.get("started_at"));
		LocalDateTime stoppedAt = toLocalDateTime(row.get("stopped_at"));
		encounter.setStatus(stoppedAt != null ? Encounter.EncounterStatus.FINISHED
				: Encounter.EncounterStatus.INPROGRESS);

		if (startedAt != null || stoppedAt != null) {
			Period period = new Period();
			if (startedAt != null) {
				period.setStart(java.sql.Timestamp.valueOf(startedAt));
			}
			if (stoppedAt != null) {
				period.setEnd(java.sql.Timestamp.valueOf(stoppedAt));
			}
			encounter.setPeriod(period);
		}

		String encounterClass = toText(row.get("encounter_class"));
		if (encounterClass != null) {
			encounter.setClass_(new Coding()
					.setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
					.setCode(encounterClass.toUpperCase())
					.setDisplay(encounterClass));
		}

		String encounterCode = toText(row.get("encounter_code"));
		String encounterDescription = toText(row.get("encounter_description"));
		if (encounterCode != null) {
			CodeableConcept type = new CodeableConcept();
			type.addCoding(new Coding().setSystem(SNOMED_SYSTEM).setCode(encounterCode).setDisplay(encounterDescription));
			if (encounterDescription != null) {
				type.setText(encounterDescription);
			}
			encounter.addType(type);
		}

		String reasonCode = toText(row.get("reason_code"));
		String reasonDescription = toText(row.get("reason_description"));
		if (reasonCode != null || reasonDescription != null) {
			CodeableConcept reason = new CodeableConcept();
			if (reasonCode != null) {
				reason.addCoding(new Coding().setSystem(SNOMED_SYSTEM).setCode(reasonCode).setDisplay(reasonDescription));
			}
			reason.setText(reasonDescription != null ? reasonDescription : reasonCode);
			encounter.addReasonCode(reason);
		}

		return encounter;
	}
}
