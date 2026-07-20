package com.hospitalops.reports;

import java.time.LocalDateTime;

/**
 * Phase 9 Step 9.1: PATIENT_VISIT_SUMMARY(V13) 한 행 + 표시에 필요한 PATIENT 조인 필드.
 *
 * <p>deliverable.md §3.2 원칙("환자 식별은 불변 내부 PK로 통일하되, 화면 표시용 번호는
 * 별도 컬럼으로 분리")에 따라 내부 불변 PK({@code patient_id})는 이 화면 어디에도 담지
 * 않는다 - 표시용 식별자는 {@link #syntheticPatientNo()}뿐이다.</p>
 */
public record PatientVisitSummaryRow(
		String syntheticPatientNo,
		String lastName,
		String firstName,
		long visitCount,
		LocalDateTime firstVisitAt,
		LocalDateTime lastVisitAt,
		long labResultCount,
		long prescriptionCount,
		LocalDateTime refreshedAt) {
}
