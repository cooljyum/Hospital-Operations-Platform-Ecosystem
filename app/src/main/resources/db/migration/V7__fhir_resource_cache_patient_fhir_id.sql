-- Phase 2 보완: FHIR search(patient 파라미터) 지원을 위한 patient_fhir_id 컬럼 추가
--
-- 배경: 독립 검증(Claude subagent, gemini.md §0-3)에서 PLAN.md Phase 2 Step 2.4 원문
-- "REST API: FHIR read/search 최소 프로파일"의 "search" 부분이 구현되지 않았다는 완결성
-- 갭이 지적됐다(read-by-id만 존재). GET /fhir/Encounter?patient={patientFhirId} 같은
-- FHIR 표준 search를 지원하려면 FHIR_RESOURCE_CACHE가 "이 리소스가 어느 환자에 속하는지"를
-- 알아야 하는데, 지금 스키마(V5)에는 그런 컬럼이 없다.
--
-- 값 규칙: Patient 리소스 자신은 NULL(자기 자신이 환자이므로 소속 환자 개념이 없음).
-- Encounter/Observation/MedicationRequest는 "patient-" + patientId(각 Mapper가
-- Reference("Patient/patient-" + row.patientId())에 이미 쓰고 있는 것과 동일한 어휘 —
-- Step 2.3 EncounterMapper/ObservationMapper/MedicationRequestMapper 참고)로 SyncJob이
-- upsert 시점에 채운다.
--
-- 조회 패턴은 항상 (resource_type, patient_fhir_id) 조합이므로 복합 인덱스를 둔다.
ALTER TABLE FHIR_RESOURCE_CACHE
    ADD COLUMN patient_fhir_id VARCHAR(64) NULL AFTER fhir_id;

CREATE INDEX idx_fhir_cache_type_patient ON FHIR_RESOURCE_CACHE (resource_type, patient_fhir_id);
