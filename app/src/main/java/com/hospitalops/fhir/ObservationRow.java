package com.hospitalops.fhir;

import java.time.LocalDateTime;

/**
 * Phase 2 Step 2.3: LAB_RESULT(레거시 HIS) 한 행을 {@link ObservationMapper}에 넘기기
 * 위한 순수 DTO. code/description 자체는 담지만, CODE_SET 조회로 얻은 {@code Coding}은
 * 매퍼 호출부(SyncJob Tasklet)가 별도로 만들어 매퍼에 넘긴다 — 매퍼가 Repository에
 * 의존하지 않는 순수 함수로 남게 하기 위함.
 */
public record ObservationRow(
		long labResultId,
		long patientId,
		Long visitId,
		LocalDateTime observedAt,
		String category,
		String code,
		String description,
		String resultValue,
		String units,
		String valueType) {
}
