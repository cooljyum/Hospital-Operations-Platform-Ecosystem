package com.hospitalops.appointment;

/** PATIENT를 변경하지 않고 예약 화면에 필요한 식별·표시 정보만 담는 읽기 전용 값 객체. */
public record AppointmentPatient(Long patientId, String syntheticPatientNo, String lastName, String firstName) {

	public String patientName() {
		return ((lastName == null ? "" : lastName) + (firstName == null ? "" : firstName)).trim();
	}
}
