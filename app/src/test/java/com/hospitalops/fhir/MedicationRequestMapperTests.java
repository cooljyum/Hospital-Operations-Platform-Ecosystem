package com.hospitalops.fhir;

import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 Step 2.3 acceptance criteria 검증: MedicationRequestMapper 필드 매핑
 * 단위테스트 + HAPI FHIR 구조 검증.
 */
class MedicationRequestMapperTests {

	@Test
	void mapsActiveMedicationRequestWithResolvedRxNormCoding() {
		MedicationRequestRow row = new MedicationRequestRow(
				901L, 7L, 42L, "310798", "Hydrochlorothiazide 25 MG Oral Tablet",
				LocalDateTime.of(2020, 1, 1, 9, 0), null,
				"38341003", "Hypertension");
		Coding resolvedCode = new Coding().setSystem("http://www.nlm.nih.gov/research/umls/rxnorm")
				.setCode("310798").setDisplay("Hydrochlorothiazide 25 MG Oral Tablet");

		MedicationRequest medicationRequest = MedicationRequestMapper.toFhir(row, resolvedCode);

		assertThat(medicationRequest.getIdElement().getIdPart()).isEqualTo("medicationrequest-901");
		assertThat(medicationRequest.getSubject().getReference()).isEqualTo("Patient/patient-7");
		assertThat(medicationRequest.getEncounter().getReference()).isEqualTo("Encounter/encounter-42");
		assertThat(medicationRequest.getStatus()).isEqualTo(MedicationRequest.MedicationRequestStatus.ACTIVE);
		assertThat(medicationRequest.getIntent()).isEqualTo(MedicationRequest.MedicationRequestIntent.ORDER);
		assertThat(medicationRequest.getMedicationCodeableConcept().getCodingFirstRep().getSystem())
				.isEqualTo("http://www.nlm.nih.gov/research/umls/rxnorm");
		assertThat(medicationRequest.getReasonCodeFirstRep().getText()).isEqualTo("Hypertension");
		assertThat(medicationRequest.getAuthoredOn()).isNotNull();
	}

	@Test
	void stoppedMedicationRequestIsCompleted() {
		MedicationRequestRow row = new MedicationRequestRow(
				902L, 7L, 42L, "310798", "Hydrochlorothiazide 25 MG Oral Tablet",
				LocalDateTime.of(2020, 1, 1, 9, 0), LocalDateTime.of(2020, 2, 1, 9, 0),
				null, null);

		MedicationRequest medicationRequest = MedicationRequestMapper.toFhir(row, null);

		assertThat(medicationRequest.getStatus()).isEqualTo(MedicationRequest.MedicationRequestStatus.COMPLETED);
	}

	@Test
	void unresolvedCodeFallsBackToTextOnlyMedicationConcept() {
		MedicationRequestRow row = new MedicationRequestRow(
				903L, 7L, 42L, "NOT-IN-CODESET", "Some Unmapped Drug",
				LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null);
		Coding fallbackCode = new Coding().setCode("NOT-IN-CODESET").setDisplay("Some Unmapped Drug");

		MedicationRequest medicationRequest = MedicationRequestMapper.toFhir(row, fallbackCode);

		assertThat(medicationRequest.getMedicationCodeableConcept().getCodingFirstRep().getSystem()).isNull();
		assertThat(medicationRequest.getMedicationCodeableConcept().getText()).isEqualTo("Some Unmapped Drug");
	}

	@Test
	void mappedMedicationRequestPassesHapiFhirStructuralValidation() {
		MedicationRequestRow row = new MedicationRequestRow(
				901L, 7L, 42L, "310798", "Hydrochlorothiazide 25 MG Oral Tablet",
				LocalDateTime.of(2020, 1, 1, 9, 0), null,
				"38341003", "Hypertension");
		Coding resolvedCode = new Coding().setSystem("http://www.nlm.nih.gov/research/umls/rxnorm")
				.setCode("310798").setDisplay("Hydrochlorothiazide 25 MG Oral Tablet");

		MedicationRequest medicationRequest = MedicationRequestMapper.toFhir(row, resolvedCode);

		ValidationResult result = FhirValidatorTestSupport.VALIDATOR.validateWithResult(medicationRequest);
		assertThat(result.isSuccessful())
				.withFailMessage(() -> "FHIR validation failed: " + result.getMessages())
				.isTrue();
	}
}
