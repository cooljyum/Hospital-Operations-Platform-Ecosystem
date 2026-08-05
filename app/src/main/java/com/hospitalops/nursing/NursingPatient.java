package com.hospitalops.nursing;

/** PATIENT를 변경하지 않고 간호 화면에 필요한 식별·표시 정보만 담는 읽기 전용 값 객체. */
public record NursingPatient(Long patientId, String syntheticPatientNo, String lastName, String firstName) {

	public String patientName() {
		return ((lastName == null ? "" : lastName) + (firstName == null ? "" : firstName)).trim();
	}
}
