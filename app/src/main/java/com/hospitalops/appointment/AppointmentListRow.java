package com.hospitalops.appointment;

/** 예약 목록에 내부 PATIENT PK를 노출하지 않기 위한 화면 전용 행. */
public record AppointmentListRow(Appointment appointment, String syntheticPatientNo, String patientName) {
}
