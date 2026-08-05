package com.hospitalops.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "PAYMENT")
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "payment_id")
	private Long paymentId;

	@Column(name = "patient_id", nullable = false)
	private Long patientId;

	@Column(name = "paid_at", nullable = false)
	private LocalDateTime paidAt;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "payment_method", nullable = false, length = 50)
	private String paymentMethod;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "processed_by_username", nullable = false, length = 50)
	private String processedByUsername;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected Payment() {
		// JPA
	}

	public Payment(Long patientId, LocalDateTime paidAt, BigDecimal amount, String paymentMethod,
			String status, String processedByUsername) {
		this.patientId = patientId;
		this.paidAt = paidAt;
		this.amount = amount;
		this.paymentMethod = paymentMethod;
		this.status = status;
		this.processedByUsername = processedByUsername;
	}

	public void update(Long patientId, LocalDateTime paidAt, BigDecimal amount, String paymentMethod,
			String status) {
		this.patientId = patientId;
		this.paidAt = paidAt;
		this.amount = amount;
		this.paymentMethod = paymentMethod;
		this.status = status;
	}

	public Long getPaymentId() {
		return paymentId;
	}

	public Long getPatientId() {
		return patientId;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getPaymentMethod() {
		return paymentMethod;
	}

	public String getStatus() {
		return status;
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
