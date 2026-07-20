package com.hospitalops.fhir;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;

/**
 * Phase 10 Step 10.2: DIAGNOSIS(레거시 HIS) -> FHIR {@code Condition} 순수 매퍼.
 *
 * <p>{@link EncounterMapper}와 같은 이유로 CODE_SET 조회가 없다 -- 다만 EncounterMapper가
 * 고정 시스템 상수(SNOMED CT)를 쓰는 것과 달리, conditions.csv는 행마다 SYSTEM 컬럼에
 * 서로 다른 코드시스템 URI(SNOMED CT 또는 ICD-10)를 직접 제공하므로 {@link DiagnosisRow#codeSystem()}
 * 값을 그대로 {@code Coding.system}에 사용한다.</p>
 *
 * <p>clinicalStatus/verificationStatus 판단 근거(설계 판단, 원본 데이터에 상태 필드가
 * 없어 START/STOP 존재 여부로 파생): {@code resolvedAt}(STOP)이 있으면 clinicalStatus=
 * resolved, 없으면 active. verificationStatus는 Synthea가 "확정 진단"만 산출하므로 항상
 * confirmed로 고정한다. abatement(종결일)는 clinicalStatus=resolved일 때만 채운다 -- FHIR R4
 * Condition 제약(con-4: abatement는 clinicalStatus가 inactive/resolved/remission일 때만
 * 허용)을 만족시키기 위함이며, 이 매퍼가 실제로 그 조건을 지키는지는
 * {@code DiagnosisMapperTests}의 HAPI FhirValidator 테스트로 확인한다.</p>
 */
public final class DiagnosisMapper {

	private static final String CLINICAL_STATUS_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-clinical";
	private static final String VERIFICATION_STATUS_SYSTEM =
			"http://terminology.hl7.org/CodeSystem/condition-ver-status";

	private DiagnosisMapper() {
	}

	public static Condition toFhir(DiagnosisRow row) {
		Condition condition = new Condition();
		condition.setId("condition-" + row.diagnosisId());
		condition.setSubject(new Reference("Patient/patient-" + row.patientId()));
		condition.setEncounter(new Reference("Encounter/encounter-" + row.visitId()));

		CodeableConcept code = new CodeableConcept();
		Coding coding = new Coding().setCode(row.code()).setDisplay(row.description());
		if (row.codeSystem() != null && !row.codeSystem().isBlank()) {
			coding.setSystem(row.codeSystem());
		}
		code.addCoding(coding);
		code.setText(row.description() != null ? row.description() : row.code());
		condition.setCode(code);

		boolean resolved = row.resolvedAt() != null;
		condition.setClinicalStatus(new CodeableConcept().addCoding(new Coding()
				.setSystem(CLINICAL_STATUS_SYSTEM)
				.setCode(resolved ? "resolved" : "active")
				.setDisplay(resolved ? "Resolved" : "Active")));
		condition.setVerificationStatus(new CodeableConcept().addCoding(new Coding()
				.setSystem(VERIFICATION_STATUS_SYSTEM)
				.setCode("confirmed")
				.setDisplay("Confirmed")));

		if (row.diagnosedAt() != null) {
			condition.setOnset(new DateTimeType(row.diagnosedAt().toString()));
		}
		if (resolved) {
			condition.setAbatement(new DateTimeType(row.resolvedAt().toString()));
		}

		return condition;
	}
}
