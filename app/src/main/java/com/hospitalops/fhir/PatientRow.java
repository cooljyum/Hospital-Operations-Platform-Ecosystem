package com.hospitalops.fhir;

import java.time.LocalDate;

/**
 * Phase 2 Step 2.3: PATIENT(레거시 HIS) 한 행을 {@link PatientMapper}에 넘기기 위한
 * 순수 DTO. JdbcTemplate의 {@code Map<String,Object>}를 매퍼가 직접 받지 않게 해
 * 매퍼를 데이터 접근 계층과 완전히 분리한다(단위테스트가 Map 대신 이 레코드를 바로
 * 생성해서 쓸 수 있다).
 */
public record PatientRow(
		long patientId,
		String syntheticPatientNo,
		LocalDate birthDate,
		LocalDate deathDate,
		String gender,
		String maritalStatus,
		String firstName,
		String middleName,
		String lastName) {
}
