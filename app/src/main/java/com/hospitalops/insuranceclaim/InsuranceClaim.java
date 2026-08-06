package com.hospitalops.insuranceclaim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "INSURANCE_CLAIM")
public class InsuranceClaim {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "insurance_claim_id")
	private Long insuranceClaimId;

	@Column(name = "patient_id", nullable = false)
	private Long patientId;

	@Column(name = "insurer_name", nullable = false, length = 100)
	private String insurerName;

	@Column(name = "claim_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal claimAmount;

	@Column(name = "claim_status", nullable = false, length = 20)
	private String claimStatus;

	@Column(name = "submitted_at", nullable = false)
	private LocalDateTime submittedAt;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;

	@Column(name = "processed_by_username", nullable = false, length = 50)
	private String processedByUsername;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected InsuranceClaim() {
		// JPA
	}

	public InsuranceClaim(Long patientId, String insurerName, BigDecimal claimAmount, String claimStatus,
			LocalDateTime submittedAt, LocalDateTime processedAt, String processedByUsername) {
		this.patientId = patientId;
		this.insurerName = insurerName;
		this.claimAmount = claimAmount;
		this.claimStatus = claimStatus;
		this.submittedAt = submittedAt;
		this.processedAt = processedAt;
		this.processedByUsername = processedByUsername;
	}

	public void update(Long patientId, String insurerName, BigDecimal claimAmount, String claimStatus,
			LocalDateTime submittedAt, LocalDateTime processedAt) {
		this.patientId = patientId;
		this.insurerName = insurerName;
		this.claimAmount = claimAmount;
		this.claimStatus = claimStatus;
		this.submittedAt = submittedAt;
		this.processedAt = processedAt;
	}

	public Long getInsuranceClaimId() {
		return insuranceClaimId;
	}

	public Long getPatientId() {
		return patientId;
	}

	public String getInsurerName() {
		return insurerName;
	}

	public BigDecimal getClaimAmount() {
		return claimAmount;
	}

	public String getClaimStatus() {
		return claimStatus;
	}

	public LocalDateTime getSubmittedAt() {
		return submittedAt;
	}

	public LocalDateTime getProcessedAt() {
		return processedAt;
	}

	public String getProcessedByUsername() {
		return processedByUsername;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
