package com.hospitalops.stats;

/**
 * Phase 4 Step 4.4: {@link PatientStatsRepository}가 반환하는 원값(raw count) DTO.
 *
 * <p>패키지 내부 전용이다 — 이 레코드는 {@link PatientStatsService}만 사용하고,
 * 컨트롤러는 이 타입을 절대 직접 다루지 않는다({@link PatientGenderAgeBandCount}로
 * 변환된 뒤에만 컨트롤러에 전달됨). 이렇게 계층을 분리해 두면, 원값이 억제 로직을
 * 거치지 않고 실수로 바로 응답에 직렬화될 경로 자체가 없다.</p>
 */
record GenderAgeBandRawCount(String gender, int ageBandStart, long rawCount) {
}
