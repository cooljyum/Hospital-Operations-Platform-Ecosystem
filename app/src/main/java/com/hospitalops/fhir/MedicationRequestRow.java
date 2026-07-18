package com.hospitalops.fhir;

import java.time.LocalDateTime;

/**
 * Phase 2 Step 2.3: PRESCRIPTION(레거시 HIS) 한 행을 {@link MedicationRequestMapper}에
 * 넘기기 위한 순수 DTO. {@link ObservationRow}와 같은 이유로 CODE_SET 조회 결과
 * {@code Coding}은 매퍼 호출부가 별도로 넘긴다.
 */
public record MedicationRequestRow(
		long prescriptionId,
		long patientId,
		Long visitId,
		String medicationCode,
		String medicationDescription,
		LocalDateTime startedAt,
		LocalDateTime stoppedAt,
		String reasonCode,
		String reasonDescription) {
}
