package com.hospitalops.fhir;

import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 Step 2.3 acceptance criteria 검증: EncounterMapper 필드 매핑 단위테스트 +
 * HAPI FHIR 구조 검증.
 */
class EncounterMapperTests {

	@Test
	void mapsClassPeriodAndTypeFromEncounterRow() {
		EncounterRow row = new EncounterRow(
				42L, 7L,
				LocalDateTime.of(2020, 1, 1, 9, 0),
				LocalDateTime.of(2020, 1, 1, 10, 30),
				"ambulatory", "185345009", "Encounter for symptom",
				"444814009", "Viral sinusitis");

		Encounter encounter = EncounterMapper.toFhir(row);

		assertThat(encounter.getIdElement().getIdPart()).isEqualTo("encounter-42");
		assertThat(encounter.getSubject().getReference()).isEqualTo("Patient/patient-7");
		assertThat(encounter.getStatus()).isEqualTo(Encounter.EncounterStatus.FINISHED);
		assertThat(encounter.getClass_().getCode()).isEqualTo("AMB");
		assertThat(encounter.getTypeFirstRep().getCodingFirstRep().getSystem()).isEqualTo("http://snomed.info/sct");
		assertThat(encounter.getTypeFirstRep().getCodingFirstRep().getCode()).isEqualTo("185345009");
		assertThat(encounter.getReasonCodeFirstRep().getText()).isEqualTo("Viral sinusitis");
		assertThat(encounter.getPeriod().getStart()).isNotNull();
		assertThat(encounter.getPeriod().getEnd()).isNotNull();
	}

	@Test
	void ongoingEncounterWithoutStopTimeIsInProgress() {
		EncounterRow row = new EncounterRow(
				43L, 7L, LocalDateTime.of(2020, 1, 1, 9, 0), null,
				"emergency", null, null, null, null);

		Encounter encounter = EncounterMapper.toFhir(row);

		assertThat(encounter.getStatus()).isEqualTo(Encounter.EncounterStatus.INPROGRESS);
	}

	@Test
	void mappedEncounterPassesHapiFhirStructuralValidation() {
		EncounterRow row = new EncounterRow(
				42L, 7L,
				LocalDateTime.of(2020, 1, 1, 9, 0),
				LocalDateTime.of(2020, 1, 1, 10, 30),
				"ambulatory", "185345009", "Encounter for symptom",
				"444814009", "Viral sinusitis");

		Encounter encounter = EncounterMapper.toFhir(row);

		ValidationResult result = FhirValidatorTestSupport.VALIDATOR.validateWithResult(encounter);
		assertThat(result.isSuccessful())
				.withFailMessage(() -> "FHIR validation failed: " + result.getMessages())
				.isTrue();
	}
}
