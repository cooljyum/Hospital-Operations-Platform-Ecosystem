package com.hospitalops.insuranceclaim;

/** 보험청구 목록에 내부 PATIENT PK를 노출하지 않기 위한 화면 전용 행. */
public record InsuranceClaimListRow(InsuranceClaim insuranceClaim, String syntheticPatientNo, String patientName) {
}
