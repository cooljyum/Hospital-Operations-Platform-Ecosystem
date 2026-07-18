package com.hospitalops.fhir;

import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 Step 2.3 acceptance criteria 검증: PatientMapper 필드 매핑 단위테스트 +
 * HAPI FHIR {@code FhirValidator} 구조 검증. 순수 함수라 Spring 컨텍스트/DB 없이
 * {@link PatientRow}만 직접 생성해서 검증한다.
 */
class PatientMapperTests {

	@Test
	void mapsCoreDemographicFieldsFromPatientRow() {
		PatientRow row = new PatientRow(
				7L, "SP-ABCDEF123456",
				LocalDate.of(1980, 5, 1), null,
				"F", "M", "Jane", "Q", "Doe");

		Patient patient = PatientMapper.toFhir(row);

		assertThat(patient.getIdElement().getIdPart()).isEqualTo("patient-7");
		assertThat(patient.getIdentifierFirstRep().getSystem()).isEqualTo("urn:hospital-ops:synthetic-patient-no");
		assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo("SP-ABCDEF123456");
		assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Doe");
		assertThat(patient.getNameFirstRep().getGivenAsSingleString()).contains("Jane").contains("Q");
		assertThat(patient.getGender()).isEqualTo(Enumerations.AdministrativeGender.FEMALE);
		assertThat(patient.getBirthDate()).isEqualTo(java.sql.Date.valueOf(LocalDate.of(1980, 5, 1)));
		assertThat(patient.hasDeceasedDateTimeType()).isFalse();
		assertThat(patient.getMaritalStatus().getText()).isEqualTo("M");
	}

	@Test
	void unknownGenderCodeMapsToOtherNotAHardcodedException() {
		PatientRow row = new PatientRow(8L, "SP-000000000000", null, null, "X", null, null, null, "Roe");

		Patient patient = PatientMapper.toFhir(row);

		assertThat(patient.getGender()).isEqualTo(Enumerations.AdministrativeGender.OTHER);
	}

	@Test
	void missingGenderMapsToUnknown() {
		PatientRow row = new PatientRow(9L, "SP-111111111111", null, null, null, null, null, null, "Roe");

		Patient patient = PatientMapper.toFhir(row);

		assertThat(patient.getGender()).isEqualTo(Enumerations.AdministrativeGender.UNKNOWN);
	}

	@Test
	void deathDateIsMappedToDeceasedDateTime() {
		PatientRow row = new PatientRow(10L, "SP-222222222222",
				LocalDate.of(1950, 1, 1), LocalDate.of(2020, 12, 31),
				"M", null, "John", null, "Smith");

		Patient patient = PatientMapper.toFhir(row);

		assertThat(patient.hasDeceasedDateTimeType()).isTrue();
		assertThat(patient.getDeceasedDateTimeType().getValueAsCalendar().get(java.util.Calendar.YEAR)).isEqualTo(2020);
	}

	@Test
	void mappedPatientPassesHapiFhirStructuralValidation() {
		PatientRow row = new PatientRow(
				7L, "SP-ABCDEF123456",
				LocalDate.of(1980, 5, 1), null,
				"F", "M", "Jane", "Q", "Doe");

		Patient patient = PatientMapper.toFhir(row);

		ValidationResult result = FhirValidatorTestSupport.VALIDATOR.validateWithResult(patient);
		assertThat(result.isSuccessful())
				.withFailMessage(() -> "FHIR validation failed: " + result.getMessages())
				.isTrue();
	}
}
