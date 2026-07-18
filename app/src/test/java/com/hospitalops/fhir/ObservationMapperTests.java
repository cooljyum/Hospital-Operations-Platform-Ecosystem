package com.hospitalops.fhir;

import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 Step 2.3 acceptance criteria 검증: ObservationMapper 필드 매핑 단위테스트
 * (CODE_SET 조회 결과를 흉내 낸 Coding을 직접 넘겨 순수 함수로 검증) + HAPI FHIR
 * 구조 검증.
 */
class ObservationMapperTests {

	@Test
	void mapsNumericValueWithResolvedLoincCoding() {
		ObservationRow row = new ObservationRow(
				501L, 7L, 42L,
				LocalDateTime.of(2020, 1, 1, 9, 5),
				"vital-signs", "8462-4", "Diastolic Blood Pressure",
				"80", "mm[Hg]", "numeric");
		Coding resolvedCode = new Coding().setSystem("http://loinc.org").setCode("8462-4")
				.setDisplay("Diastolic Blood Pressure");

		Observation observation = ObservationMapper.toFhir(row, resolvedCode);

		assertThat(observation.getIdElement().getIdPart()).isEqualTo("observation-501");
		assertThat(observation.getSubject().getReference()).isEqualTo("Patient/patient-7");
		assertThat(observation.getEncounter().getReference()).isEqualTo("Encounter/encounter-42");
		assertThat(observation.getStatus()).isEqualTo(Observation.ObservationStatus.FINAL);
		assertThat(observation.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://loinc.org");
		assertThat(observation.getValueQuantity().getValue().doubleValue()).isEqualTo(80.0);
		assertThat(observation.getValueQuantity().getUnit()).isEqualTo("mm[Hg]");
	}

	@Test
	void fallbackCodingWithoutSystemStillProducesTextValue() {
		ObservationRow row = new ObservationRow(
				502L, 7L, 42L, LocalDateTime.of(2020, 1, 1, 9, 5),
				null, "UNKNOWN-CODE", "Some free-text result",
				"positive", "", "text");
		Coding fallbackCode = new Coding().setCode("UNKNOWN-CODE").setDisplay("Some free-text result");

		Observation observation = ObservationMapper.toFhir(row, fallbackCode);

		assertThat(observation.getCode().getCodingFirstRep().getSystem()).isNull();
		assertThat(observation.getCode().getText()).isEqualTo("Some free-text result");
		assertThat(observation.getValue()).isInstanceOf(StringType.class);
		assertThat(((StringType) observation.getValue()).getValue()).isEqualTo("positive");
	}

	@Test
	void nonNumericResultValueForNumericTypeFallsBackToStringInsteadOfThrowing() {
		ObservationRow row = new ObservationRow(
				503L, 7L, 42L, LocalDateTime.of(2020, 1, 1, 9, 5),
				null, "8462-4", "Diastolic Blood Pressure",
				"not-a-number", "mm[Hg]", "numeric");

		Observation observation = ObservationMapper.toFhir(row, null);

		assertThat(observation.getValue()).isInstanceOf(StringType.class);
		assertThat(observation.hasValueQuantity()).isFalse();
	}

	@Test
	void mappedObservationPassesHapiFhirStructuralValidation() {
		ObservationRow row = new ObservationRow(
				501L, 7L, 42L, LocalDateTime.of(2020, 1, 1, 9, 5),
				"vital-signs", "8462-4", "Diastolic Blood Pressure",
				"80", "mm[Hg]", "numeric");
		Coding resolvedCode = new Coding().setSystem("http://loinc.org").setCode("8462-4")
				.setDisplay("Diastolic Blood Pressure");

		Observation observation = ObservationMapper.toFhir(row, resolvedCode);

		ValidationResult result = FhirValidatorTestSupport.VALIDATOR.validateWithResult(observation);
		assertThat(result.isSuccessful())
				.withFailMessage(() -> "FHIR validation failed: " + result.getMessages())
				.isTrue();
	}
}
