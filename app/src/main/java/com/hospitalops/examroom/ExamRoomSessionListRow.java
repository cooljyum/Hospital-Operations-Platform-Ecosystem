package com.hospitalops.examroom;

/** 진료실 목록에 내부 PATIENT PK를 노출하지 않기 위한 화면 전용 행. */
public record ExamRoomSessionListRow(ExamRoomSession examRoomSession, String syntheticPatientNo, String patientName) {
}
