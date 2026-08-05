package com.hospitalops.nursing;

/** 간호 기록 목록에 내부 PATIENT PK를 노출하지 않기 위한 화면 전용 행. */
public record NursingRecordListRow(NursingRecord nursingRecord, String syntheticPatientNo, String patientName) {
}
