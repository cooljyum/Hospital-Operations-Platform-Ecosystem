package com.hospitalops.examroom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "EXAM_ROOM_SESSION")
public class ExamRoomSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "exam_room_session_id")
	private Long examRoomSessionId;

	@Column(name = "patient_id", nullable = false)
	private Long patientId;

	@Column(name = "room_no", nullable = false, length = 20)
	private String roomNo;

	@Column(name = "physician_username", nullable = false, length = 50)
	private String physicianUsername;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	@Column(name = "notes", length = 500)
	private String notes;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected ExamRoomSession() {
		// JPA
	}

	public ExamRoomSession(Long patientId, String roomNo, String physicianUsername, LocalDateTime startedAt,
			LocalDateTime endedAt, String notes, String status) {
		this.patientId = patientId;
		this.roomNo = roomNo;
		this.physicianUsername = physicianUsername;
		this.startedAt = startedAt;
		this.endedAt = endedAt;
		this.notes = notes;
		this.status = status;
	}

	public void update(Long patientId, String roomNo, LocalDateTime startedAt, LocalDateTime endedAt,
			String notes, String status) {
		this.patientId = patientId;
		this.roomNo = roomNo;
		this.startedAt = startedAt;
		this.endedAt = endedAt;
		this.notes = notes;
		this.status = status;
	}

	public Long getExamRoomSessionId() {
		return examRoomSessionId;
	}

	public Long getPatientId() {
		return patientId;
	}

	public String getRoomNo() {
		return roomNo;
	}

	public String getPhysicianUsername() {
		return physicianUsername;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}

	public String getNotes() {
		return notes;
	}

	public String getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
