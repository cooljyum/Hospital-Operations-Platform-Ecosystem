package com.hospitalops.reception;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "RECEPTION")
public class Reception {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "reception_id")
	private Long receptionId;

	@Column(name = "patient_id", nullable = false)
	private Long patientId;

	@Column(name = "received_at", nullable = false)
	private LocalDateTime receivedAt;

	@Column(name = "chief_complaint", length = 500)
	private String chiefComplaint;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "receptionist_username", nullable = false, length = 50)
	private String receptionistUsername;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected Reception() {
		// JPA
	}

	public Reception(Long patientId, LocalDateTime receivedAt, String chiefComplaint,
			String status, String receptionistUsername) {
		this.patientId = patientId;
		this.receivedAt = receivedAt;
		this.chiefComplaint = chiefComplaint;
		this.status = status;
		this.receptionistUsername = receptionistUsername;
	}

	public void update(Long patientId, LocalDateTime receivedAt, String chiefComplaint, String status) {
		this.patientId = patientId;
		this.receivedAt = receivedAt;
		this.chiefComplaint = chiefComplaint;
		this.status = status;
	}

	public Long getReceptionId() {
		return receptionId;
	}

	public Long getPatientId() {
		return patientId;
	}

	public LocalDateTime getReceivedAt() {
		return receivedAt;
	}

	public String getChiefComplaint() {
		return chiefComplaint;
	}

	public String getStatus() {
		return status;
	}

	public String getReceptionistUsername() {
		return receptionistUsername;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
