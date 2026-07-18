package com.hospitalops.fhir;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;

/**
 * Phase 2 Step 2.3: PATIENT(레거시 HIS) -> FHIR {@code Patient} 순수 매퍼.
 *
 * <p>입력은 {@link PatientRow}(DTO)뿐이고 출력은 HAPI FHIR {@link Patient} 객체뿐이다
 * — DB/Repository 의존이 전혀 없어 단위테스트가 그대로 생성자만으로 검증할 수 있다.</p>
 */
public final class PatientMapper {

	private PatientMapper() {
	}

	public static Patient toFhir(PatientRow row) {
		Patient patient = new Patient();
		patient.setId("patient-" + row.patientId());

		patient.addIdentifier()
				.setSystem("urn:hospital-ops:synthetic-patient-no")
				.setValue(row.syntheticPatientNo());

		if (row.lastName() != null || row.firstName() != null) {
			HumanName name = patient.addName();
			if (row.lastName() != null) {
				name.setFamily(row.lastName());
			}
			if (row.firstName() != null) {
				name.addGiven(row.firstName());
			}
			if (row.middleName() != null) {
				name.addGiven(row.middleName());
			}
		}

		patient.setGender(mapGender(row.gender()));

		if (row.birthDate() != null) {
			patient.setBirthDate(java.sql.Date.valueOf(row.birthDate()));
		}

		if (row.deathDate() != null) {
			patient.setDeceased(new DateTimeType(java.sql.Date.valueOf(row.deathDate())));
		}

		if (row.maritalStatus() != null) {
			patient.setMaritalStatus(new CodeableConcept().setText(row.maritalStatus()));
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
