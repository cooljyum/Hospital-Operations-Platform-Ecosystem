package com.hospitalops.fhir;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;

import java.sql.Timestamp;

/**
 * Phase 2 Step 2.3: VISIT(레거시 HIS) -> FHIR {@code Encounter} 순수 매퍼.
 *
 * <p>encounter_code/encounter_description은 Synthea 원본이 이미 SNOMED CT 코드+표시명
 * 쌍을 담고 있어(CODE_SET 조회가 필요한 LAB_RESULT.code/PRESCRIPTION.medication_code와
 * 달리) 고정 시스템 상수로 바로 Coding을 구성한다 — CODE_SET 의존이 없어 Patient와
 * 마찬가지로 완전한 순수 함수다.</p>
 */
public final class EncounterMapper {

	private static final String SNOMED_SYSTEM = "http://snomed.info/sct";

	private EncounterMapper() {
	}

	public static Encounter toFhir(EncounterRow row) {
		Encounter encounter = new Encounter();
		encounter.setId("encounter-" + row.visitId());
		encounter.setSubject(new Reference("Patient/patient-" + row.patientId()));

		encounter.setStatus(row.stoppedAt() != null ? Encounter.EncounterStatus.FINISHED
				: Encounter.EncounterStatus.INPROGRESS);

		if (row.startedAt() != null || row.stoppedAt() != null) {
			Period period = new Period();
			if (row.startedAt() != null) {
				period.setStart(Timestamp.valueOf(row.startedAt()));
			}
			if (row.stoppedAt() != null) {
				period.setEnd(Timestamp.valueOf(row.stoppedAt()));
			}
			encounter.setPeriod(period);
		}

		if (row.encounterClass() != null) {
			encounter.setClass_(new Coding()
					.setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
					.setCode(mapEncounterClassToActCode(row.encounterClass()))
					.setDisplay(row.encounterClass()));
		}

		if (row.encounterCode() != null) {
			CodeableConcept type = new CodeableConcept();
			type.addCoding(new Coding().setSystem(SNOMED_SYSTEM).setCode(row.encounterCode())
					.setDisplay(row.encounterDescription()));
			type.setText(row.encounterDescription() != null ? row.encounterDescription() : row.encounterCode());
			encounter.addType(type);
		}

		if (row.reasonCode() != null || row.reasonDescription() != null) {
			CodeableConcept reason = new CodeableConcept();
			if (row.reasonCode() != null) {
				reason.addCoding(new Coding().setSystem(SNOMED_SYSTEM).setCode(row.reasonCode())
						.setDisplay(row.reasonDescription()));
			}
			reason.setText(row.reasonDescription() != null ? row.reasonDescription() : row.reasonCode());
			encounter.addReasonCode(reason);
		}

		return encounter;
	}

	/**
	 * Synthea encounters.csv의 ENCOUNTERCLASS는 자유 소문자 단어(ambulatory/outpatient/
	 * wellness/urgentcare/emergency/inpatient/snf/virtual)이지 v3-ActCode 코드 자체가
	 * 아니다 — 그냥 대문자로 바꾼다고 유효한 코드가 되지 않는다(FhirValidator로 실제
	 * 확인된 버그: "AMBULATORY"는 v3-ActCode에 없고 올바른 코드는 "AMB"다).
	 *
	 * <p>이 표는 CODE_SET 테이블 대상이 아니다 — LAB_RESULT.code/PRESCRIPTION.
	 * medication_code처럼 크고 이질적인 코드 공간이 아니라, Synthea가 실제로 내보내는
	 * 값이 8개뿐인 고정 열거형이고 FHIR 표준 코드 시스템(v3-ActCode)로의 1:1 변환이다
	 * (Patient.gender 매핑과 같은 성격).</p>
	 */
	private static String mapEncounterClassToActCode(String rawEncounterClass) {
		return switch (rawEncounterClass.trim().toLowerCase()) {
			case "ambulatory", "outpatient", "wellness", "urgentcare" -> "AMB";
			case "emergency" -> "EMER";
			case "inpatient", "snf" -> "IMP";
			case "virtual" -> "VR";
			case "home" -> "HH";
			default -> "AMB";
		};
	}
}
