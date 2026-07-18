package com.hospitalops.stats;

/**
 * Phase 4 Step 4.4: 응답/화면에 노출되는 성별×연령대 환자 수 통계 셀.
 *
 * <p>{@code cell}이 {@link SuppressedCell}이므로, 이 레코드를 그대로 JSON 직렬화해도
 * 억제된 셀의 실제 count는 절대 포함되지 않는다.</p>
 */
public record PatientGenderAgeBandCount(String gender, int ageBandStart, SuppressedCell cell) {
}
