package com.hospitalops.batch;

import com.hospitalops.fhir.FhirResourceCacheUpsertService;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.hospitalops.batch.JdbcTemporalSupport.toLocalDate;
import static com.hospitalops.batch.JdbcTemporalSupport.toLong;
import static com.hospitalops.batch.JdbcTemporalSupport.toText;

/**
 * Phase 2 Step 2.1: PATIENT(레거시 HIS) -> FHIR {@code Patient} 증분 동기화 스텝.
 *
 * <p>이 시점에는 아직 Step 2.3의 전용 PatientMapper가 없어 매핑 로직을 이 Tasklet
 * 안에 인라인으로 둔다. Step 2.3에서 fhir 패키지의 순수 함수 매퍼로 추출하고 이
 * Tasklet은 그 매퍼를 호출하도록 리팩터링할 예정이다.</p>
 */
@Component
public class PatientSyncTasklet implements Tasklet {

	static final String RESOURCE_TYPE = "Patient";
	private static final String LEGACY_TABLE = "PATIENT";

	private final JdbcTemplate jdbcTemplate;
	private final SyncWatermarkService watermarkService;
	private final FhirResourceCacheUpsertService cacheUpsertService;

	public PatientSyncTasklet(JdbcTemplate jdbcTemplate, SyncWatermarkService watermarkService,
			FhirResourceCacheUpsertService cacheUpsertService) {
		this.jdbcTemplate = jdbcTemplate;
		this.watermarkService = watermarkService;
		this.cacheUpsertService = cacheUpsertService;
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		LocalDateTime watermark = watermarkService.currentWatermark(RESOURCE_TYPE);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT patient_id, synthetic_patient_no, birth_date, death_date, gender, race, " +
						"ethnicity, marital_status, first_name, middle_name, last_name " +
						"FROM PATIENT WHERE updated_at > ? ORDER BY updated_at",
				watermark);

		for (Map<String, Object> row : rows) {
			long patientId = toLong(row.get("patient_id"));
			Patient resource = toFhirPatient(row, patientId);
			cacheUpsertService.upsert(RESOURCE_TYPE, patientId, resource);
		}

		watermarkService.advanceWatermarkToTableMax(RESOURCE_TYPE, LEGACY_TABLE);
		contribution.incrementWriteCount(rows.size());
		return RepeatStatus.FINISHED;
	}

	private Patient toFhirPatient(Map<String, Object> row, long patientId) {
		Patient patient = new Patient();
		patient.setId("patient-" + patientId);

		patient.addIdentifier()
				.setSystem("urn:hospital-ops:synthetic-patient-no")
				.setValue(toText(row.get("synthetic_patient_no")));

		String lastName = toText(row.get("last_name"));
		String firstName = toText(row.get("first_name"));
		String middleName = toText(row.get("middle_name"));
		if (lastName != null || firstName != null) {
			var name = patient.addName();
			if (lastName != null) {
				name.setFamily(lastName);
			}
			if (firstName != null) {
				name.addGiven(firstName);
			}
			if (middleName != null) {
				name.addGiven(middleName);
			}
		}

		patient.setGender(mapGender(toText(row.get("gender"))));

		LocalDate birthDate = toLocalDate(row.get("birth_date"));
		if (birthDate != null) {
			patient.setBirthDate(java.sql.Date.valueOf(birthDate));
		}

		LocalDate deathDate = toLocalDate(row.get("death_date"));
		if (deathDate != null) {
			patient.setDeceased(new org.hl7.fhir.r4.model.DateTimeType(java.sql.Date.valueOf(deathDate)));
		}

		String maritalStatus = toText(row.get("marital_status"));
		if (maritalStatus != null) {
			patient.setMaritalStatus(new org.hl7.fhir.r4.model.CodeableConcept().setText(maritalStatus));
		}

		return patient;
	}

	private static Enumerations.AdministrativeGender mapGender(String rawGender) {
		if (rawGender == null) {
			return Enumerations.AdministrativeGender.UNKNOWN;
		}
		return switch (rawGender.trim().toUpperCase()) {
			case "M" -> Enumerations.AdministrativeGender.MALE;
			case "F" -> Enumerations.AdministrativeGender.FEMALE;
			default -> Enumerations.AdministrativeGender.OTHER;
		};
	}
}
