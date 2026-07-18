package com.hospitalops.fhir;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Reference;

import java.sql.Timestamp;

/**
 * Phase 2 Step 2.3: PRESCRIPTION(레거시 HIS) -> FHIR {@code MedicationRequest} 순수 매퍼.
 *
 * <p>{@link ObservationMapper}와 같은 이유로 CODE_SET 조회 결과 {@link Coding}은
 * 호출부(MedicationRequestSyncTasklet)가 넘겨준다.</p>
 */
public final class MedicationRequestMapper {

	private MedicationRequestMapper() {
	}

	public static MedicationRequest toFhir(MedicationRequestRow row, Coding resolvedMedicationCode) {
		MedicationRequest medicationRequest = new MedicationRequest();
		medicationRequest.setId("medicationrequest-" + row.prescriptionId());
		medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
		medicationRequest.setSubject(new Reference("Patient/patient-" + row.patientId()));

		if (row.visitId() != null) {
			medicationRequest.setEncounter(new Reference("Encounter/encounter-" + row.visitId()));
		}

		medicationRequest.setStatus(row.stoppedAt() != null ? MedicationRequest.MedicationRequestStatus.COMPLETED
				: MedicationRequest.MedicationRequestStatus.ACTIVE);

		CodeableConcept medicationConcept = new CodeableConcept();
		if (resolvedMedicationCode != null) {
			medicationConcept.addCoding(resolvedMedicationCode);
		}
		medicationConcept.setText(row.medicationDescription() != null ? row.medicationDescription() : row.medicationCode());
		medicationRequest.setMedication(medicationConcept);

		if (row.startedAt() != null) {
			medicationRequest.setAuthoredOn(Timestamp.valueOf(row.startedAt()));
		}

		if (row.reasonCode() != null || row.reasonDescription() != null) {
			CodeableConcept reason = new CodeableConcept();
			if (row.reasonCode() != null) {
				reason.addCoding(new Coding().setCode(row.reasonCode()).setDisplay(row.reasonDescription()));
			}
			reason.setText(row.reasonDescription() != null ? row.reasonDescription() : row.reasonCode());
			medicationRequest.addReasonCode(reason);
		}

		return medicationRequest;
	}
}
