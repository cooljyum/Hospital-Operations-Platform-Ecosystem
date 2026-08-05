package com.hospitalops.payment;

/** 수납 목록에 내부 PATIENT PK를 노출하지 않기 위한 화면 전용 행. */
public record PaymentListRow(Payment payment, String syntheticPatientNo, String patientName) {
}
