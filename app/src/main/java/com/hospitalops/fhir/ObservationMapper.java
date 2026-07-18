package com.hospitalops.fhir;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;

import java.sql.Timestamp;

/**
 * Phase 2 Step 2.3: LAB_RESULT(레거시 HIS) -> FHIR {@code Observation} 순수 매퍼.
 *
 * <p>{@code code}는 매퍼가 직접 CODE_SET(Repository)을 조회하지 않는다 — 호출부
 * (SyncJob의 ObservationSyncTasklet)가 {@link CodeSetLookupService}로 미리 해석한
 * {@link Coding}을 넘겨준다. 그래야 이 매퍼가 DB 의존 없는 순수 함수로 남아 단위테스트가
 * Repository/Spring 컨텍스트 없이도 가능하다.</p>
 */
public final class ObservationMapper {

	private ObservationMapper() {
	}

	public static Observation toFhir(ObservationRow row, Coding resolvedCode) {
		Observation observation = new Observation();
		observation.setId("observation-" + row.labResultId());
		observation.setStatus(Observation.ObservationStatus.FINAL);
		observation.setSubject(new Reference("Patient/patient-" + row.patientId()));

		if (row.visitId() != null) {
			observation.setEncounter(new Reference("Encounter/encounter-" + row.visitId()));
		}

		if (row.category() != null) {
			observation.addCategory(new CodeableConcept().setText(row.category()));
		}

		CodeableConcept codeableConcept = new CodeableConcept();
		if (resolvedCode != null) {
			codeableConcept.addCoding(resolvedCode);
		}
		codeableConcept.setText(row.description() != null ? row.description() : row.code());
		observation.setCode(codeableConcept);

		if (row.observedAt() != null) {
			observation.setEffective(new DateTimeType(Timestamp.valueOf(row.observedAt())));
		}

		if (row.resultValue() != null) {
			if ("numeric".equalsIgnoreCase(row.valueType())) {
				try {
					Quantity quantity = new Quantity().setValue(Double.parseDouble(row.resultValue()));
					if (row.units() != null && !row.units().isBlank()) {
						// Synthea observations.csv의 UNITS는 이미 UCUM 표기(mm[Hg], mg/dL 등)다.
						// FHIR Quantity의 qty-3 제약("code가 있으면 system도 있어야 함")을
						// 만족시키려면 system=UCUM을 반드시 함께 채워야 한다(FhirValidator로
						// 실제 확인된 버그 — system 없이 code만 채웠더니 검증 실패).
						quantity.setUnit(row.units())
								.setSystem("http://unitsofmeasure.org")
								.setCode(row.units());
					}
					observation.setValue(quantity);
				} catch (NumberFormatException e) {
					observation.setValue(new StringType(row.resultValue()));
				}
			} else {
				observation.setValue(new StringType(row.resultValue()));
			}
		}

		return observation;
	}
}
