package com.hospitalops.reception;

/** 접수 목록에 내부 PATIENT PK를 노출하지 않기 위한 화면 전용 행. */
public record ReceptionListRow(Reception reception, String syntheticPatientNo, String patientName) {
}
