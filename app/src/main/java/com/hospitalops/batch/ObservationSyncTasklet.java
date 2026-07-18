package com.hospitalops.batch;

import com.hospitalops.fhir.FhirResourceCacheUpsertService;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
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
 * Phase 2 Step 2.1: LAB_RESULT(레거시 HIS) -> FHIR {@code Observation} 증분 동기화 스텝.
 *
 * <p>LAB_RESULT.code는 Step 2.2의 CODE_SET 코드셋 테이블을 조회해 system/code/display를
 * 채워야 하지만, 코드셋 테이블은 아직 없다(다음 step). 이번 step에서는 원본 code/
 * description을 그대로 담는 폴백 형태(system 없는 Coding + text)로 두고, Step 2.3에서
 * 전용 ObservationMapper로 추출하며 코드셋 조회를 붙인다.</p>
 */
@Component
public class ObservationSyncTasklet implements Tasklet {

	static final String RESOURCE_TYPE = "Observation";
	private static final String LEGACY_TABLE = "LAB_RESULT";

	private final JdbcTemplate jdbcTemplate;
	private final SyncWatermarkService watermarkService;
	private final FhirResourceCacheUpsertService cacheUpsertService;

	public ObservationSyncTasklet(JdbcTemplate jdbcTemplate, SyncWatermarkService watermarkService,
			FhirResourceCacheUpsertService cacheUpsertService) {
		this.jdbcTemplate = jdbcTemplate;
		this.watermarkService = watermarkService;
		this.cacheUpsertService = cacheUpsertService;
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
			long labResultId = toLong(row.get("lab_result_id"));
			Observation resource = toFhirObservation(row, labResultId);
			cacheUpsertService.upsert(RESOURCE_TYPE, labResultId, resource);
		}

		watermarkService.advanceWatermarkToTableMax(RESOURCE_TYPE, LEGACY_TABLE);
		contribution.incrementWriteCount(rows.size());
		return RepeatStatus.FINISHED;
	}

	private Observation toFhirObservation(Map<String, Object> row, long labResultId) {
		Observation observation = new Observation();
		observation.setId("observation-" + labResultId);
		observation.setStatus(Observation.ObservationStatus.FINAL);

		long patientId = toLong(row.get("patient_id"));
		observation.setSubject(new Reference("Patient/patient-" + patientId));

		Long visitId = toLong(row.get("visit_id"));
		if (visitId != null) {
			observation.setEncounter(new Reference("Encounter/encounter-" + visitId));
		}

		String category = toText(row.get("category"));
		if (category != null) {
			observation.addCategory(new CodeableConcept().setText(category));
		}

		String code = toText(row.get("code"));
		String description = toText(row.get("description"));
		CodeableConcept codeableConcept = new CodeableConcept();
		if (code != null) {
			// Step 2.3에서 CODE_SET 조회로 system/display를 보강할 자리 — 지금은 원본
			// code만 담는 시스템 미상 Coding + text 폴백만 둔다.
			codeableConcept.addCoding(new Coding().setCode(code).setDisplay(description));
		}
		codeableConcept.setText(description != null ? description : code);
		observation.setCode(codeableConcept);

		LocalDateTime observedAt = toLocalDateTime(row.get("observed_at"));
		if (observedAt != null) {
			observation.setEffective(new DateTimeType(java.sql.Timestamp.valueOf(observedAt)));
		}

		String resultValue = toText(row.get("result_value"));
		String units = toText(row.get("units"));
		String valueType = toText(row.get("value_type"));
		if (resultValue != null) {
			if ("numeric".equalsIgnoreCase(valueType)) {
				try {
					Quantity quantity = new Quantity().setValue(Double.parseDouble(resultValue));
					if (units != null) {
						quantity.setUnit(units).setCode(units);
					}
					observation.setValue(quantity);
				} catch (NumberFormatException e) {
					observation.setValue(new StringType(resultValue));
				}
			} else {
				observation.setValue(new StringType(resultValue));
			}
		}

		return observation;
	}
}
