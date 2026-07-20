package com.hospitalops.fhir;

import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.r4.model.Condition;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 10 Step 10.2 acceptance criteria 검증: DiagnosisMapper 필드 매핑 단위테스트 +
 * HAPI FHIR 구조 검증.
 */
class DiagnosisMapperTests {

	@Test
	void mapsActiveConditionWithoutAbatementWhenNotResolved() {
		DiagnosisRow row = new DiagnosisRow(
				501L, 7L, 42L,
				LocalDate.of(2020, 1, 1), null,
				"http://snomed.info/sct", "44054006", "Diabetes");

		Condition condition = DiagnosisMapper.toFhir(row);

		assertThat(condition.getIdElement().getIdPart()).isEqualTo("condition-501");
		assertThat(condition.getSubject().getReference()).isEqualTo("Patient/patient-7");
		assertThat(condition.getEncounter().getReference()).isEqualTo("Encounter/encounter-42");
		assertThat(condition.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://snomed.info/sct");
		assertThat(condition.getCode().getCodingFirstRep().getCode()).isEqualTo("44054006");
		assertThat(condition.getCode().getText()).isEqualTo("Diabetes");
		assertThat(condition.getClinicalStatus().getCodingFirstRep().getCode()).isEqualTo("active");
		assertThat(condition.getVerificationStatus().getCodingFirstRep().getCode()).isEqualTo("confirmed");
		assertThat(condition.getOnsetDateTimeType().getValueAsString()).isEqualTo("2020-01-01");
		assertThat(condition.hasAbatement()).isFalse();
	}

	@Test
	void mapsResolvedConditionWithAbatementWhenStopPresent() {
		DiagnosisRow row = new DiagnosisRow(
				502L, 7L, 42L,
				LocalDate.of(1993, 8, 22), LocalDate.of(2022, 7, 24),
				"http://snomed.info/sct", "31642005", "Acute gingivitis");

		Condition condition = DiagnosisMapper.toFhir(row);

		assertThat(condition.getClinicalStatus().getCodingFirstRep().getCode()).isEqualTo("resolved");
		assertThat(condition.hasAbatement()).isTrue();
		assertThat(condition.getAbatementDateTimeType().getValueAsString()).isEqualTo("2022-07-24");
	}

	@Test
	void icd10CodeSystemIsPreservedAsIs() {
		DiagnosisRow row = new DiagnosisRow(
				503L, 7L, 42L,
				LocalDate.of(2020, 1, 1), null,
				"http://hl7.org/fhir/sid/icd-10", "E11.9", "Type 2 diabetes mellitus");

		Condition condition = DiagnosisMapper.toFhir(row);

		assertThat(condition.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://hl7.org/fhir/sid/icd-10");
	}

	@Test
	void mappedConditionPassesHapiFhirStructuralValidation() {
		DiagnosisRow row = new DiagnosisRow(
				504L, 7L, 42L,
				LocalDate.of(1993, 8, 22), LocalDate.of(2022, 7, 24),
				"http://snomed.info/sct", "31642005", "Acute gingivitis");

		Condition condition = DiagnosisMapper.toFhir(row);

		ValidationResult result = FhirValidatorTestSupport.VALIDATOR.validateWithResult(condition);
		assertThat(result.isSuccessful())
				.withFailMessage(() -> "FHIR validation failed: " + result.getMessages())
				.isTrue();
	}

	@Test
	void mappedActiveConditionAlsoPassesHapiFhirStructuralValidation() {
		DiagnosisRow row = new DiagnosisRow(
				505L, 7L, 42L,
				LocalDate.of(2020, 1, 1), null,
				"http://snomed.info/sct", "44054006", "Diabetes");

		Condition condition = DiagnosisMapper.toFhir(row);

		ValidationResult result = FhirValidatorTestSupport.VALIDATOR.validateWithResult(condition);
		assertThat(result.isSuccessful())
				.withFailMessage(() -> "FHIR validation failed: " + result.getMessages())
				.isTrue();
	}
}
