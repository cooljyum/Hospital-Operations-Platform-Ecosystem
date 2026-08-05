package com.hospitalops.nursing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "NURSING_RECORD")
public class NursingRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "nursing_record_id")
	private Long nursingRecordId;

	@Column(name = "patient_id", nullable = false)
	private Long patientId;

	@Column(name = "recorded_at", nullable = false)
	private LocalDateTime recordedAt;

	@Column(name = "nurse_username", nullable = false, length = 50)
	private String nurseUsername;

	@Column(name = "temperature", precision = 4, scale = 1)
	private BigDecimal temperature;

	@Column(name = "blood_pressure", length = 20)
	private String bloodPressure;

	@Column(name = "pulse")
	private Integer pulse;

	@Column(name = "notes", length = 500)
	private String notes;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected NursingRecord() {
		// JPA
	}

	public NursingRecord(Long patientId, LocalDateTime recordedAt, String nurseUsername, BigDecimal temperature,
			String bloodPressure, Integer pulse, String notes) {
		this.patientId = patientId;
		this.recordedAt = recordedAt;
		this.nurseUsername = nurseUsername;
		this.temperature = temperature;
		this.bloodPressure = bloodPressure;
		this.pulse = pulse;
		this.notes = notes;
	}

	public void update(Long patientId, LocalDateTime recordedAt, BigDecimal temperature, String bloodPressure,
			Integer pulse, String notes) {
		this.patientId = patientId;
		this.recordedAt = recordedAt;
		this.temperature = temperature;
		this.bloodPressure = bloodPressure;
		this.pulse = pulse;
		this.notes = notes;
	}

	public Long getNursingRecordId() {
		return nursingRecordId;
	}

	public Long getPatientId() {
		return patientId;
	}

	public LocalDateTime getRecordedAt() {
		return recordedAt;
	}

	public String getNurseUsername() {
		return nurseUsername;
	}

	public BigDecimal getTemperature() {
		return temperature;
	}

	public String getBloodPressure() {
		return bloodPressure;
	}

	public Integer getPulse() {
		return pulse;
	}

	public String getNotes() {
		return notes;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
