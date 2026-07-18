package com.hospitalops.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Phase 2 Step 2.1: HAPI FHIR 리소스 객체를 JSON으로 직렬화해 FHIR_RESOURCE_CACHE에
 * 멱등 upsert하는 공용 헬퍼. batch 패키지의 각 SyncJob Tasklet이 이 서비스를 통해서만
 * 캐시 테이블에 쓴다(직접 SQL을 흩뿌리지 않기 위함).
 */
@Service
public class FhirResourceCacheUpsertService {

	private final FhirResourceCacheRepository repository;
	private final FhirContext fhirContext;

	public FhirResourceCacheUpsertService(FhirResourceCacheRepository repository, FhirContext fhirContext) {
		this.repository = repository;
		this.fhirContext = fhirContext;
	}

	/**
	 * @param resourceType   FHIR 리소스 타입명("Patient","Encounter","Observation","MedicationRequest")
	 * @param sourceTablePk  변환 원본 레거시 테이블의 불변 내부 PK
	 * @param resource       변환된 HAPI FHIR 리소스(이미 {@code setId(...)}로 fhir_id가 설정돼 있어야 함)
	 */
	@Transactional
	public void upsert(String resourceType, long sourceTablePk, IBaseResource resource) {
		String fhirId = resource.getIdElement().getIdPart();
		String json = fhirContext.newJsonParser().encodeResourceToString(resource);

		FhirResourceCache entity = repository
				.findByResourceTypeAndSourceTablePk(resourceType, sourceTablePk)
				.orElseGet(FhirResourceCache::new);
		entity.setResourceType(resourceType);
		entity.setSourceTablePk(sourceTablePk);
		entity.setFhirId(fhirId);
		entity.setResourceJson(json);
		entity.setSyncedAt(LocalDateTime.now());
		repository.save(entity);
	}
}
