package com.hospitalops.fhir;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
