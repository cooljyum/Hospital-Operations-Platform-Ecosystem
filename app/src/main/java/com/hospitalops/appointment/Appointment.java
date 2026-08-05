package com.hospitalops.appointment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "APPOINTMENT")
public class Appointment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "appointment_id")
	private Long appointmentId;

	@Column(name = "patient_id", nullable = false)
	private Long patientId;

	@Column(name = "scheduled_at", nullable = false)
	private LocalDateTime scheduledAt;

	@Column(name = "department", length = 100)
	private String department;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "memo", length = 500)
	private String memo;

	@Column(name = "booked_by_username", nullable = false, length = 50)
	private String bookedByUsername;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected Appointment() {
		// JPA
	}

	public Appointment(Long patientId, LocalDateTime scheduledAt, String department,
			String status, String memo, String bookedByUsername) {
		this.patientId = patientId;
		this.scheduledAt = scheduledAt;
		this.department = department;
		this.status = status;
		this.memo = memo;
		this.bookedByUsername = bookedByUsername;
	}

	public void update(Long patientId, LocalDateTime scheduledAt, String department, String status, String memo) {
		this.patientId = patientId;
		this.scheduledAt = scheduledAt;
		this.department = department;
		this.status = status;
		this.memo = memo;
	}

	public Long getAppointmentId() {
		return appointmentId;
	}

	public Long getPatientId() {
		return patientId;
	}

	public LocalDateTime getScheduledAt() {
		return scheduledAt;
	}

	public String getDepartment() {
		return department;
	}

	public String getStatus() {
		return status;
	}

	public String getMemo() {
		return memo;
	}

	public String getBookedByUsername() {
		return bookedByUsername;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
