package com.hospitalops.admission;

/** 입원 목록에 내부 PATIENT PK를 노출하지 않기 위한 화면 전용 행. */
public record AdmissionListRow(Admission admission, String syntheticPatientNo, String patientName) {
}
