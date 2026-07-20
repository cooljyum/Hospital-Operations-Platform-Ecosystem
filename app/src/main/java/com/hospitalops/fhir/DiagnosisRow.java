package com.hospitalops.fhir;

import java.time.LocalDate;

/**
 * Phase 10 Step 10.2: DIAGNOSIS(레거시 HIS) 한 행을 {@link DiagnosisMapper}에 넘기기 위한
 * 순수 DTO. {@code codeSystem}은 원본 Synthea conditions.csv의 SYSTEM 컬럼 값(완전한
 * FHIR 코드시스템 URI -- http://snomed.info/sct 또는 http://hl7.org/fhir/sid/icd-10)을
 * 그대로 담는다. LAB_RESULT/PRESCRIPTION과 달리 CODE_SET 변환 없이 이 값을 곧바로
 * {@link DiagnosisMapper}가 {@code Coding.system}에 사용한다(V17 마이그레이션 설계
 * 판단 1 참고).
 */
public record DiagnosisRow(
		long diagnosisId,
		long patientId,
		long visitId,
		LocalDate diagnosedAt,
		LocalDate resolvedAt,
		String codeSystem,
		String code,
		String description) {
}
