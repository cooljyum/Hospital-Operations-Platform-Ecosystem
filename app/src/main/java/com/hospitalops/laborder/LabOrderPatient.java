package com.hospitalops.laborder;

/** PATIENT를 변경하지 않고 검사 오더 화면에 필요한 식별·표시 정보만 담는 읽기 전용 값 객체. */
public record LabOrderPatient(Long patientId, String syntheticPatientNo, String lastName, String firstName) {

	public String patientName() {
		return ((lastName == null ? "" : lastName) + (firstName == null ? "" : firstName)).trim();
	}
}
