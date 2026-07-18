package com.hospitalops.fhir;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Phase 2 Step 2.1: FHIR_RESOURCE_CACHE 전용 Repository.
 *
 * <p>Step 2.4의 FhirController는 오직 이 Repository만 통해 FHIR 리소스를 조회한다
 * (레거시 PATIENT/VISIT/LAB_RESULT/PRESCRIPTION repository를 직접 참조하지 않는다).</p>
 */
public interface FhirResourceCacheRepository extends JpaRepository<FhirResourceCache, Long> {

	Optional<FhirResourceCache> findByResourceTypeAndSourceTablePk(String resourceType, Long sourceTablePk);

	Optional<FhirResourceCache> findByResourceTypeAndFhirId(String resourceType, String fhirId);

	/**
	 * Phase 2 보완(Step 2.4 search): {@code GET /fhir/{Type}?patient={patientFhirId}}가
	 * 사용하는 조회. 매칭이 없으면 빈 리스트를 반환한다(컨트롤러가 이를 빈 Bundle로 감싼다).
	 */
	List<FhirResourceCache> findByResourceTypeAndPatientFhirId(String resourceType, String patientFhirId);
}
