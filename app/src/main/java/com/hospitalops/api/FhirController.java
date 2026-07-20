package com.hospitalops.api;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.hospitalops.fhir.FhirResourceCache;
import com.hospitalops.fhir.FhirResourceCacheRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 2 Step 2.4: FHIR read-only REST API.
 *
 * <p>이 컨트롤러는 오직 {@link FhirResourceCacheRepository}(FHIR_RESOURCE_CACHE 전용
 * Repository)만 조회한다 — PATIENT/VISIT/LAB_RESULT/PRESCRIPTION 등 레거시 repository를
 * 직접 참조하지 않는다(PLAN.md Phase 2 acceptance criteria의 핵심 경계). Step 2.1의
 * SyncJob이 미리 변환해 채워 둔 FHIR JSON을 그대로 돌려준다 — 이 요청 경로에서 다시
 * 레거시 DB를 조회하거나 HAPI FHIR 매퍼를 재실행하지 않는다.</p>
 *
 * <p>{id}는 FHIR_RESOURCE_CACHE.fhir_id 값(예: "patient-1", "encounter-42")과 정확히
 * 일치해야 한다 — SyncJob의 각 Mapper가 리소스에 부여하는 {@code Resource.id}와 같은
 * 값이라 클라이언트가 응답 본문의 {@code id} 필드를 그대로 다음 조회에 재사용할 수 있다.</p>
 *
 * <p>이 애플리케이션에는 아직 전용 SecurityConfig가 없어(Phase 3.1에서 도입 예정)
 * spring-boot-starter-security의 기본 자동 설정이 그대로 적용된다 — 즉 이 엔드포인트도
 * 기본적으로 인증이 필요하다(미인증 요청은 401/302).</p>
 *
 * <p>Phase 2 보완: 독립 검증(Claude subagent, gemini.md §0-3)에서 PLAN.md 원문 "REST API:
 * FHIR read/search 최소 프로파일"의 "search" 부분이 빠져 있다는 완결성 갭이 지적돼 최소
 * {@code patient} search 파라미터를 추가했다(가장 흔한 FHIR search 파라미터). {@code GET
 * /fhir/Encounter?patient={patientFhirId}} 등은 read({@code GET /fhir/Encounter/{id}})와
 * 경로가 겹치지 않아(하나는 path variable, 하나는 query string) 스프링 MVC가 문제없이
 * 구분한다. 응답은 FHIR search 표준대로 {@code Bundle}(type=searchset)로 감싼다 — 매칭이
 * 0건이어도 404가 아니라 200 + total=0 빈 Bundle이다(search의 표준 동작).</p>
 */
@RestController
@RequestMapping("/fhir")
public class FhirController {

	private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

	private final FhirResourceCacheRepository repository;
	private final FhirContext fhirContext;

	public FhirController(FhirResourceCacheRepository repository, FhirContext fhirContext) {
		this.repository = repository;
		this.fhirContext = fhirContext;
	}

	@GetMapping(value = "/Patient/{id}")
	public ResponseEntity<String> getPatient(@PathVariable String id) {
		return findResource("Patient", id);
	}

	@GetMapping(value = "/Encounter/{id}")
	public ResponseEntity<String> getEncounter(@PathVariable String id) {
		return findResource("Encounter", id);
	}

	@GetMapping(value = "/Observation/{id}")
	public ResponseEntity<String> getObservation(@PathVariable String id) {
		return findResource("Observation", id);
	}

	@GetMapping(value = "/MedicationRequest/{id}")
	public ResponseEntity<String> getMedicationRequest(@PathVariable String id) {
		return findResource("MedicationRequest", id);
	}

	@GetMapping(value = "/Condition/{id}")
	public ResponseEntity<String> getCondition(@PathVariable String id) {
		return findResource("Condition", id);
	}

	@GetMapping(value = "/Encounter")
	public ResponseEntity<String> searchEncounter(@RequestParam(required = false) String patient) {
		return searchByPatient("Encounter", patient);
	}

	@GetMapping(value = "/Observation")
	public ResponseEntity<String> searchObservation(@RequestParam(required = false) String patient) {
		return searchByPatient("Observation", patient);
	}

	@GetMapping(value = "/MedicationRequest")
	public ResponseEntity<String> searchMedicationRequest(@RequestParam(required = false) String patient) {
		return searchByPatient("MedicationRequest", patient);
	}

	@GetMapping(value = "/Condition")
	public ResponseEntity<String> searchCondition(@RequestParam(required = false) String patient) {
		return searchByPatient("Condition", patient);
	}

	private ResponseEntity<String> findResource(String resourceType, String fhirId) {
		return repository.findByResourceTypeAndFhirId(resourceType, fhirId)
				.map(cache -> ResponseEntity.ok()
						.contentType(FHIR_JSON)
						.body(cache.getResourceJson()))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
						.body(resourceType + "/" + fhirId + "이(가) FHIR_RESOURCE_CACHE에 없습니다."));
	}

	/**
	 * {@code patient} 파라미터가 없거나 매칭되는 캐시 row가 없으면 빈 Bundle(total=0)을
	 * 200으로 반환한다 — FHIR search 표준상 빈 결과는 404가 아니다.
	 */
	private ResponseEntity<String> searchByPatient(String resourceType, String patientFhirId) {
		List<FhirResourceCache> matches = patientFhirId == null
				? List.of()
				: repository.findByResourceTypeAndPatientFhirId(resourceType, patientFhirId);

		IParser parser = fhirContext.newJsonParser();
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);
		bundle.setTotal(matches.size());
		for (FhirResourceCache cache : matches) {
			IBaseResource resource = parser.parseResource(cache.getResourceJson());
			bundle.addEntry().setResource((Resource) resource);
		}

		return ResponseEntity.ok()
				.contentType(FHIR_JSON)
				.body(parser.encodeResourceToString(bundle));
	}
}
