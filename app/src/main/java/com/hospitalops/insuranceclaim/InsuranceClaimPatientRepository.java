package com.hospitalops.insuranceclaim;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** PATIENT의 표시용 식별자와 이름을 읽기 전용으로 조회한다. */
@Repository
public class InsuranceClaimPatientRepository {

	private final JdbcTemplate jdbcTemplate;

	public InsuranceClaimPatientRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<InsuranceClaimPatient> findBySyntheticPatientNo(String syntheticPatientNo) {
		List<InsuranceClaimPatient> patients = jdbcTemplate.query("""
				SELECT patient_id, synthetic_patient_no, last_name, first_name
				FROM PATIENT
				WHERE synthetic_patient_no = ?
				""", this::mapPatient, syntheticPatientNo);
		return patients.stream().findFirst();
	}

	public Optional<InsuranceClaimPatient> findByPatientId(Long patientId) {
		List<InsuranceClaimPatient> patients = jdbcTemplate.query("""
				SELECT patient_id, synthetic_patient_no, last_name, first_name
				FROM PATIENT
				WHERE patient_id = ?
				""", this::mapPatient, patientId);
		return patients.stream().findFirst();
	}

	public Map<Long, InsuranceClaimPatient> findByPatientIds(Collection<Long> patientIds) {
		if (patientIds.isEmpty()) {
			return Map.of();
		}
		String placeholders = patientIds.stream().map(id -> "?").collect(Collectors.joining(", "));
		List<InsuranceClaimPatient> patients = jdbcTemplate.query("""
				SELECT patient_id, synthetic_patient_no, last_name, first_name
				FROM PATIENT
				WHERE patient_id IN (""" + placeholders + ")", this::mapPatient, patientIds.toArray());
		return patients.stream().collect(Collectors.toMap(
				InsuranceClaimPatient::patientId, patient -> patient, (first, ignored) -> first, LinkedHashMap::new));
	}

	private InsuranceClaimPatient mapPatient(java.sql.ResultSet resultSet, int rowNum) throws java.sql.SQLException {
		return new InsuranceClaimPatient(
				resultSet.getLong("patient_id"),
				resultSet.getString("synthetic_patient_no"),
				resultSet.getString("last_name"),
				resultSet.getString("first_name"));
	}
}
