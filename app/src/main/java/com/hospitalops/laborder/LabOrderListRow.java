package com.hospitalops.laborder;

/** 검사 오더 목록에 내부 PATIENT PK를 노출하지 않기 위한 화면 전용 행. */
public record LabOrderListRow(LabOrder labOrder, String syntheticPatientNo, String patientName) {
}
