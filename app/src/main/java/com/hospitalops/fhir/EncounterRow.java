package com.hospitalops.fhir;

import java.time.LocalDateTime;

/** Phase 2 Step 2.3: VISIT(레거시 HIS) 한 행을 {@link EncounterMapper}에 넘기기 위한 순수 DTO. */
public record EncounterRow(
		long visitId,
		long patientId,
		LocalDateTime startedAt,
		LocalDateTime stoppedAt,
		String encounterClass,
		String encounterCode,
		String encounterDescription,
		String reasonCode,
		String reasonDescription) {
}
