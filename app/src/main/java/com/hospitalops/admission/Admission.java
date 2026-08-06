package com.hospitalops.admission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ADMISSION")
public class Admission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "admission_id")
	private Long admissionId;

	@Column(name = "patient_id", nullable = false)
	private Long patientId;

	@Column(name = "ward", nullable = false, length = 50)
	private String ward;

	@Column(name = "bed_no", nullable = false, length = 20)
	private String bedNo;

	@Column(name = "admitted_at", nullable = false)
	private LocalDateTime admittedAt;

	@Column(name = "discharged_at")
	private LocalDateTime dischargedAt;

	@Column(name = "reason", length = 500)
	private String reason;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "admitted_by_username", nullable = false, length = 50)
	private String admittedByUsername;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected Admission() {
		// JPA
	}

	public Admission(Long patientId, String ward, String bedNo, LocalDateTime admittedAt,
			LocalDateTime dischargedAt, String reason, String status, String admittedByUsername) {
		this.patientId = patientId;
		this.ward = ward;
		this.bedNo = bedNo;
		this.admittedAt = admittedAt;
		this.dischargedAt = dischargedAt;
		this.reason = reason;
		this.status = status;
		this.admittedByUsername = admittedByUsername;
	}

	public void update(Long patientId, String ward, String bedNo, LocalDateTime admittedAt,
			LocalDateTime dischargedAt, String reason, String status) {
		this.patientId = patientId;
		this.ward = ward;
		this.bedNo = bedNo;
		this.admittedAt = admittedAt;
		this.dischargedAt = dischargedAt;
		this.reason = reason;
		this.status = status;
	}

	public Long getAdmissionId() {
		return admissionId;
	}

	public Long getPatientId() {
		return patientId;
	}

	public String getWard() {
		return ward;
	}

	public String getBedNo() {
		return bedNo;
	}

	public LocalDateTime getAdmittedAt() {
		return admittedAt;
	}

	public LocalDateTime getDischargedAt() {
		return dischargedAt;
	}

	public String getReason() {
		return reason;
	}

	public String getStatus() {
		return status;
	}

	public String getAdmittedByUsername() {
		return admittedByUsername;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
