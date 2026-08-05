package com.hospitalops.laborder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "LAB_ORDER")
public class LabOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "lab_order_id")
	private Long labOrderId;

	@Column(name = "patient_id", nullable = false)
	private Long patientId;

	@Column(name = "ordered_at", nullable = false)
	private LocalDateTime orderedAt;

	@Column(name = "ordered_by_username", nullable = false, length = 50)
	private String orderedByUsername;

	@Column(name = "test_name", nullable = false, length = 200)
	private String testName;

	@Column(name = "status", nullable = false, length = 20)
	private String status;

	@Column(name = "result_summary", length = 500)
	private String resultSummary;

	@Column(name = "created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	protected LabOrder() {
		// JPA
	}

	public LabOrder(Long patientId, LocalDateTime orderedAt, String orderedByUsername, String testName,
			String status, String resultSummary) {
		this.patientId = patientId;
		this.orderedAt = orderedAt;
		this.orderedByUsername = orderedByUsername;
		this.testName = testName;
		this.status = status;
		this.resultSummary = resultSummary;
	}

	public void update(Long patientId, LocalDateTime orderedAt, String testName, String status, String resultSummary) {
		this.patientId = patientId;
		this.orderedAt = orderedAt;
		this.testName = testName;
		this.status = status;
		this.resultSummary = resultSummary;
	}

	public Long getLabOrderId() {
		return labOrderId;
	}

	public Long getPatientId() {
		return patientId;
	}

	public LocalDateTime getOrderedAt() {
		return orderedAt;
	}

	public String getOrderedByUsername() {
		return orderedByUsername;
	}

	public String getTestName() {
		return testName;
	}

	public String getStatus() {
		return status;
	}

	public String getResultSummary() {
		return resultSummary;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
