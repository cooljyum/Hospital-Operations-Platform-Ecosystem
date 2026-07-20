# FHIR 변환 데모 (Phase 8 Step 8.6, Phase 10 Step 10.2 갱신)

> Phase 2 Step 2.1~2.4에서 구현된 "레거시 HIS DB row -> FHIR R4 JSON" 변환 파이프라인을
> 실제 애플리케이션(로컬 native MySQL 8.4 + `./gradlew bootRun`)으로 기동해 **실제
> 배치 실행 -> 실제 REST API 호출**을 수행하고, 그 결과(원본 row raw 출력 + 변환된
> FHIR JSON raw 출력)를 기록한 문서다. 상상 데이터/가짜 JSON은 없다 — 이 문서의 모든
> row·JSON은 §5에 기록된 방식으로 이 세션에서 직접 캡처했다. 조회/증분 동기화만
> 수행했으며 `PATIENT`/`AUDIT_LOG` 등 정본 테이블은 시연 전후로 동일하다(§7).
>
> **Phase 10 Step 10.2 갱신**: 5번째 FHIR 리소스 `Condition`(레거시 `DIAGNOSIS`)을
> 추가했다. §0/§3(e)/§6/§7을 이번에 실측 갱신했고, 나머지 §1~§4/§3(a)-(d)는 Phase 8
> 시연 당시 그대로 남겨 둔다(매퍼 코드를 이번에 수정하지 않았으므로 여전히 유효).

## 0. 변환 파이프라인 개요

레거시 HIS 테이블 5개가 FHIR R4 리소스 5종으로 매핑된다(Phase 10 Step 10.2에서
`DIAGNOSIS` -> `Condition`을 추가해 `final_summary.md`의 "4~5종" 여지를 5종으로 확정):

| 레거시 테이블 | FHIR 리소스 | 매퍼 | 배치 Tasklet |
|---|---|---|---|
| `PATIENT` | `Patient` | `com.hospitalops.fhir.PatientMapper` | `PatientSyncTasklet` |
| `VISIT` | `Encounter` | `com.hospitalops.fhir.EncounterMapper` | `EncounterSyncTasklet` |
| `LAB_RESULT` | `Observation` | `com.hospitalops.fhir.ObservationMapper` | `ObservationSyncTasklet` |
| `PRESCRIPTION` | `MedicationRequest` | `com.hospitalops.fhir.MedicationRequestMapper` | `MedicationRequestSyncTasklet` |
| `DIAGNOSIS` | `Condition` | `com.hospitalops.fhir.DiagnosisMapper` | `DiagnosisSyncTasklet` |

변환은 **요청 시점이 아니라 배치 시점**에 일어난다:

1. `fhirSyncJob`(`com.hospitalops.batch.SyncJobConfig`)이 `syncPatientStep ->
   syncEncounterStep -> syncObservationStep -> syncMedicationRequestStep ->
   syncDiagnosisStep` 순서로 5개 Tasklet을 실행한다(마지막 스텝은 Phase 10 Step
   10.2에서 추가). 각 Tasklet은 `SYNC_WATERMARK`에 저장된 리소스 타입별
   워터마크보다 `updated_at`이 이후인 레거시 row만 증분으로 읽어(`JdbcTemplate`),
   해당 순수 매퍼(`*Mapper.toFhir(...)`)를 호출해 HAPI FHIR 리소스 객체를 만들고,
   `FhirResourceCacheUpsertService`로 JSON 직렬화해 `FHIR_RESOURCE_CACHE`
   테이블에 멱등 upsert한다(`(resource_type, source_table_pk)` UNIQUE가 자연키).
2. `com.hospitalops.api.FhirController`(`GET /fhir/{Type}/{id}`,
   `GET /fhir/{Type}?patient={patientFhirId}`)는 **오직 `FHIR_RESOURCE_CACHE`만
   조회**한다 — 요청 경로에서 레거시 DB를 다시 조회하거나 매퍼를 재실행하지 않는다
   (PLAN.md Phase 2의 계층 경계). `Observation`/`MedicationRequest`는 원본 코드
   (`LAB_RESULT.code`/`PRESCRIPTION.medication_code`)를 매퍼가 직접 조회하지 않고
   `CodeSetLookupService`(`CODE_SET` 테이블)가 미리 LOINC/RxNorm `Coding`으로
   해석해 매퍼에 넘긴다(코드셋에 없으면 `system` 없이 원본 코드+description만
   폴백으로 채운다). `Condition`은 CODE_SET을 쓰지 않는다 — Synthea
   `conditions.csv`의 SYSTEM 컬럼이 이미 완전한 FHIR 코드시스템 URI(SNOMED CT
   또는 ICD-10)를 제공하므로 `DIAGNOSIS.code_system`에 원본 그대로 저장해 두었다가
   `DiagnosisMapper`가 그 값을 곧바로 `Coding.system`에 쓴다(V17 마이그레이션 설계
   판단 참고).

## 1. 호출 방법 (재현 절차)

```powershell
cd app
$env:DB_HOST="localhost"; $env:DB_PORT="3306"; $env:DB_NAME="hospital_ops"
$env:DB_USERNAME="hospital_ops"; $env:DB_PASSWORD="changeme"
$env:ENVELOPE_KEK="<세션용 32바이트 Base64 키>"
$env:SYNC_JOB_ENABLED="true"   # 기동 시 fhirSyncJob 1회 자동 실행(기본값 false)
.\gradlew.bat bootRun --no-daemon
```

`SYNC_JOB_ENABLED=true`(`sync.job.enabled`, `SyncJobProperties`)를 켜지 않으면
`fhirSyncJob`이 기동 시 자동 실행되지 않는다(기본 비활성 + 명시적 opt-in 패턴,
`SyncJobRunner`). 이번 시연에서는 이 값을 켜서 기동과 동시에 배치가 실행되는 것을
로그로 직접 확인했다(§2).

### 엔드포인트 (인증 요건)

| 엔드포인트 | 설명 |
|---|---|
| `GET /fhir/Patient/{id}` | 단건 조회. `{id}`는 `FHIR_RESOURCE_CACHE.fhir_id`(예: `patient-1`) |
| `GET /fhir/Encounter/{id}` | 단건 조회 |
| `GET /fhir/Observation/{id}` | 단건 조회 |
| `GET /fhir/MedicationRequest/{id}` | 단건 조회 |
| `GET /fhir/Condition/{id}` | 단건 조회(Phase 10 Step 10.2 추가) |
| `GET /fhir/Encounter?patient={patientFhirId}` | search(`Bundle` searchset, 0건도 200) |
| `GET /fhir/Observation?patient={patientFhirId}` | search |
| `GET /fhir/MedicationRequest?patient={patientFhirId}` | search |
| `GET /fhir/Condition?patient={patientFhirId}` | search(Phase 10 Step 10.2 추가) |

`SecurityConfig`에 `/fhir/**`용 `ACCESS_POLICY_RULES` row가 없어 역할 제한은 없지만,
마지막 규칙 `auth.anyRequest().authenticated()`에 걸려 **로그인 세션이 반드시
필요하다**(역할 무관, 아무 `APP_USER`나 가능). 미인증 요청은 302로 `/login`으로
리다이렉트됨을 실측 확인했다(§4). 존재하지 않는 `{id}`는 404 + 안내 텍스트를
반환한다(§4).

## 2. 실제 기동 로그 (fhirSyncJob 실행 확인)

```
2026-07-20T01:12:57.790+09:00  INFO ... TaskExecutorJobLauncher : Job: [SimpleJob: [name=fhirSyncJob]] launched with the following parameters: [{'runAt':'{value=1784477577737, ...}'}]
2026-07-20T01:12:57.852+09:00  INFO ... SimpleStepHandler : Executing step: [syncPatientStep]
2026-07-20T01:12:57.946+09:00  INFO ... AbstractStep      : Step: [syncPatientStep] executed in 94ms
2026-07-20T01:12:57.972+09:00  INFO ... SimpleStepHandler : Executing step: [syncEncounterStep]
2026-07-20T01:12:57.987+09:00  INFO ... AbstractStep      : Step: [syncEncounterStep] executed in 15ms
2026-07-20T01:12:58.020+09:00  INFO ... SimpleStepHandler : Executing step: [syncObservationStep]
2026-07-20T01:12:58.036+09:00  INFO ... AbstractStep      : Step: [syncObservationStep] executed in 15ms
2026-07-20T01:12:58.052+09:00  INFO ... SimpleStepHandler : Executing step: [syncMedicationRequestStep]
2026-07-20T01:12:58.084+09:00  INFO ... AbstractStep      : Step: [syncMedicationRequestStep] executed in 32ms
2026-07-20T01:12:58.100+09:00  INFO ... TaskExecutorJobLauncher : Job: [SimpleJob: [name=fhirSyncJob]] completed ... status: [COMPLETED] in 257ms
```

4개 스텝이 순서대로(Patient -> Encounter -> Observation -> MedicationRequest)
`COMPLETED` 상태로 실행됐다. 이번 실행에서는 각 리소스 타입의 `SYNC_WATERMARK`가
이미 레거시 테이블 `MAX(updated_at)`을 따라잡은 상태였기 때문에(직전 실행이 이미
전량 동기화해 둠) 신규로 upsert된 행은 0건이었다 — `FHIR_RESOURCE_CACHE`는 실행
전후로 동일하게 `Patient 13 / Encounter 511 / Observation 3759 / MedicationRequest
145`건이었다(§7). 그러나 아래 §3의 응답은 **이 파이프라인이 과거 실행에서 실제로
만들어 캐시에 넣어 둔, 지금 이 순간에도 `GET /fhir/**`로 실측 재현되는 진짜 변환
결과**다 — 매퍼 코드 자체는 이 세션에서 전혀 수정하지 않았으므로 §4의 HAPI 단위
테스트(같은 코드 경로)가 곧 이 캐시 데이터의 생성 로직이 유효함을 뒷받침한다.

## 3. 리소스 타입별 원본 -> FHIR 샘플 (patient_id=1 기준)

모든 원본 row는 `mysql.exe -u hospital_ops hospital_ops`로, 모든 FHIR JSON은
로그인 세션 쿠키를 가진 `curl.exe`로 `GET http://localhost:8080/fhir/...`를 호출해
그대로 캡처했다(가공 없음, §5).

### (a) Patient — `PATIENT.patient_id=1` -> `GET /fhir/Patient/patient-1`

원본 row (`SELECT * FROM PATIENT WHERE patient_id=1`, 매퍼가 쓰는 컬럼만 발췌):

```
patient_id: 1
synthetic_patient_no: SP-F26A7CC2E0D5
birth_date: 1989-04-14
death_date: NULL
gender: M
marital_status: S
first_name: Clint766
middle_name: Robbie31
last_name: Thiel172
```

FHIR JSON (`GET /fhir/Patient/patient-1` 실제 응답):

```json
{
  "resourceType": "Patient",
  "id": "patient-1",
  "identifier": [
    { "system": "urn:hospital-ops:synthetic-patient-no", "value": "SP-F26A7CC2E0D5" }
  ],
  "name": [
    { "family": "Thiel172", "given": ["Clint766", "Robbie31"] }
  ],
  "gender": "male",
  "birthDate": "1989-04-14",
  "maritalStatus": { "text": "S" }
}
```

필드 매핑: `patient_id` -> `id`(`patient-{id}`) · `synthetic_patient_no` ->
`identifier[0].value`(system=`urn:hospital-ops:synthetic-patient-no`) ·
`last_name`/`first_name`/`middle_name` -> `name[0].family`/`given[0]`/`given[1]` ·
`gender`(`M`) -> `gender`(`male`, `PatientMapper.mapGender`) · `birth_date` ->
`birthDate` · `death_date`(NULL) -> `deceased*` 미설정(row 10건 테스트 케이스로
별도 확인, §4) · `marital_status` -> `maritalStatus.text`.

### (b) Encounter — `VISIT.visit_id=1` -> `GET /fhir/Encounter/encounter-1`

원본 row:

```
visit_id: 1
patient_id: 1
started_at: 2007-06-08 14:43:17
stopped_at: 2007-06-08 15:35:53
encounter_class: wellness
encounter_code: 162673000
encounter_description: General examination of patient (procedure)
reason_code: NULL
reason_description: NULL
```

FHIR JSON:

```json
{
  "resourceType": "Encounter",
  "id": "encounter-1",
  "status": "finished",
  "class": {
    "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
    "code": "AMB",
    "display": "wellness"
  },
  "type": [
    {
      "coding": [
        { "system": "http://snomed.info/sct", "code": "162673000", "display": "General examination of patient (procedure)" }
      ],
      "text": "General examination of patient (procedure)"
    }
  ],
  "subject": { "reference": "Patient/patient-1" },
  "period": { "start": "2007-06-08T14:43:17+09:00", "end": "2007-06-08T15:35:53+09:00" }
}
```

필드 매핑: `visit_id` -> `id`(`encounter-{id}`) · `patient_id` -> `subject.reference`
(`Patient/patient-{id}`) · `stopped_at` NULL 여부 -> `status`(`finished`/`in-progress`)
· `started_at`/`stopped_at` -> `period.start`/`period.end` · `encounter_class`
(`wellness`) -> `class.code`(`AMB`, `mapEncounterClassToActCode` — v3-ActCode 고정
매핑표, 예: `emergency`->`EMER`, `inpatient`/`snf`->`IMP`) · `encounter_code`/
`encounter_description`(SNOMED CT) -> `type[0].coding[0]` · `reason_code`/
`reason_description`(NULL이므로 이번 샘플엔 없음) -> `reasonCode[0]`(존재 시).

### (c) Observation — `LAB_RESULT.lab_result_id=4` -> `GET /fhir/Observation/observation-4`

원본 row:

```
lab_result_id: 4
visit_id: 10
patient_id: 1
observed_at: 2017-06-23 14:43:17
category: vital-signs
code: 8302-2
description: Body Height
result_value: 170.6
units: cm
value_type: numeric
```

FHIR JSON:

```json
{
  "resourceType": "Observation",
  "id": "observation-4",
  "status": "final",
  "category": [{ "text": "vital-signs" }],
  "code": {
    "coding": [{ "system": "http://loinc.org", "code": "8302-2", "display": "Body Height" }],
    "text": "Body Height"
  },
  "subject": { "reference": "Patient/patient-1" },
  "encounter": { "reference": "Encounter/encounter-10" },
  "effectiveDateTime": "2017-06-23T14:43:17+09:00",
  "valueQuantity": { "value": 170.6, "unit": "cm", "system": "http://unitsofmeasure.org", "code": "cm" }
}
```

필드 매핑: `lab_result_id` -> `id`(`observation-{id}`) · `patient_id` ->
`subject.reference` · `visit_id` -> `encounter.reference`(`Encounter/encounter-{id}`)
· `category` -> `category[0].text` · `code`(`8302-2`)가 `CODE_SET`(codeSystem=
`LAB_RESULT`)에서 LOINC로 해석돼 `code.coding[0]`(system=`http://loinc.org`)로
채워짐, `description` -> `code.text` · `observed_at` -> `effectiveDateTime` ·
`result_value`(`170.6`, `value_type=numeric`) -> `valueQuantity.value`(숫자 파싱
성공) · `units`(`cm`, 이미 UCUM 표기) -> `valueQuantity.unit`/`.system`(UCUM)/`.code`.

### (d) MedicationRequest — `PRESCRIPTION.prescription_id=1` -> `GET /fhir/MedicationRequest/medicationrequest-1`

원본 row:

```
prescription_id: 1
visit_id: 32
patient_id: 1
medication_code: 310965
medication_description: Ibuprofen 200 MG Oral Tablet
started_at: 2020-10-07 15:08:15
stopped_at: 2020-10-23 15:08:15
reason_code: NULL
reason_description: NULL
```

FHIR JSON:

```json
{
  "resourceType": "MedicationRequest",
  "id": "medicationrequest-1",
  "status": "completed",
  "intent": "order",
  "medicationCodeableConcept": {
    "coding": [{ "system": "http://www.nlm.nih.gov/research/umls/rxnorm", "code": "310965", "display": "Ibuprofen 200 MG Oral Tablet" }],
    "text": "Ibuprofen 200 MG Oral Tablet"
  },
  "subject": { "reference": "Patient/patient-1" },
  "encounter": { "reference": "Encounter/encounter-32" },
  "authoredOn": "2020-10-07T15:08:15+09:00"
}
```

필드 매핑: `prescription_id` -> `id`(`medicationrequest-{id}`) · `patient_id` ->
`subject.reference` · `visit_id` -> `encounter.reference` · `stopped_at` 존재 여부
(`2020-10-23 15:08:15`, NULL 아님) -> `status`(`completed`, NULL이면 `active`) ·
`intent`는 항상 고정값 `order` · `medication_code`(`310965`)가 `CODE_SET`
(codeSystem=`PRESCRIPTION`)에서 RxNorm으로 해석돼
`medicationCodeableConcept.coding[0]`(system=RxNorm URI)로 채워짐,
`medication_description` -> `.text` · `started_at` -> `authoredOn`.

### (e) search — `GET /fhir/Encounter?patient=patient-1`

```json
{ "resourceType": "Bundle", "type": "searchset", "total": 19, "entry": [ /* 19건, encounter-1부터 encounter-52까지 */ ] }
```

`VISIT` 테이블에서 `patient_id=1`인 행 19건과 정확히 일치(`SELECT COUNT(*) FROM
VISIT WHERE patient_id=1` = 19, 별도 확인). `Bundle.entry[].resource`가 각각
(b)와 동일한 구조의 `Encounter`다 — 예: 첫 entry가 `encounter-1`로 (b)의 응답과
동일.

### (f) Condition — `DIAGNOSIS.diagnosis_id=1` -> `GET /fhir/Condition/condition-1` (Phase 10 Step 10.2, 이번 세션 실측)

원본 row (`SELECT * FROM DIAGNOSIS WHERE diagnosis_id=1`):

```
diagnosis_id: 1
visit_id: 2
patient_id: 2
diagnosed_at: 1993-08-22
resolved_at: 2022-07-24
code_system: http://snomed.info/sct
code: 31642005
description: Acute gingivitis
```

FHIR JSON (`GET /fhir/Condition/condition-1` 실제 응답, 2026-07-20 세션에서 캡처):

```json
{
  "resourceType": "Condition",
  "id": "condition-1",
  "clinicalStatus": {
    "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/condition-clinical", "code": "resolved", "display": "Resolved" }]
  },
  "verificationStatus": {
    "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/condition-ver-status", "code": "confirmed", "display": "Confirmed" }]
  },
  "code": {
    "coding": [{ "system": "http://snomed.info/sct", "code": "31642005", "display": "Acute gingivitis" }],
    "text": "Acute gingivitis"
  },
  "subject": { "reference": "Patient/patient-2" },
  "encounter": { "reference": "Encounter/encounter-2" },
  "onsetDateTime": "1993-08-22",
  "abatementDateTime": "2022-07-24"
}
```

필드 매핑: `diagnosis_id` -> `id`(`condition-{id}`) · `patient_id` ->
`subject.reference` · `visit_id` -> `encounter.reference` · `code_system`
(Synthea `conditions.csv`의 SYSTEM 컬럼, 이미 완전한 FHIR 시스템 URI) ->
`code.coding[0].system`(원본 그대로, CODE_SET 변환 없음) · `code`/`description`
-> `code.coding[0].code`/`.display`, `code.text` · `resolved_at` 존재 여부
(`2022-07-24`, NULL 아님) -> `clinicalStatus.coding[0].code`(`resolved`, NULL이면
`active`) · `verificationStatus`는 항상 고정값 `confirmed`(설계 판단 — Synthea가
확정 진단만 산출) · `diagnosed_at` -> `onsetDateTime` · `resolved_at`(존재 시) ->
`abatementDateTime`(clinicalStatus=resolved일 때만 채움 — FHIR R4 Condition
제약 con-4 충족, `DiagnosisMapperTests`의 HAPI 검증 테스트로 확인).

같은 환자의 진행 중(미해소) 진단 예시(`diagnosis_id=2`, `resolved_at` NULL) ->
`GET /fhir/Condition/condition-2`:

```json
{
  "resourceType": "Condition",
  "id": "condition-2",
  "clinicalStatus": { "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/condition-clinical", "code": "active", "display": "Active" }] },
  "verificationStatus": { "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/condition-ver-status", "code": "confirmed", "display": "Confirmed" }] },
  "code": { "coding": [{ "system": "http://snomed.info/sct", "code": "224299000", "display": "Received higher education (finding)" }], "text": "Received higher education (finding)" },
  "subject": { "reference": "Patient/patient-1" },
  "encounter": { "reference": "Encounter/encounter-1" },
  "onsetDateTime": "2007-06-09"
}
```

`resolved_at`이 NULL이라 `clinicalStatus`가 `active`이고 `abatementDateTime`
자체가 응답에 없다(설계대로 con-4 위반을 피하려고 abatement를 아예 세팅하지
않음).

### (g) search — `GET /fhir/Condition?patient=patient-2` (Phase 10 Step 10.2)

```json
{ "resourceType": "Bundle", "type": "searchset", "total": 14, "entry": [ /* 14건, 첫 entry가 condition-1 */ ] }
```

`DIAGNOSIS` 테이블에서 `patient_id=2`인 행 14건과 정확히 일치(`SELECT COUNT(*)
FROM DIAGNOSIS WHERE patient_id=2` = 14, 별도 확인).

## 4. HAPI FHIR validator 통과 근거

Phase 2.3에서 각 매퍼는 HAPI FHIR `FhirValidator`(`FhirInstanceValidator` +
`DefaultProfileValidationSupport` + `InMemoryTerminologyServerValidationSupport`,
R4 구조 검증 — `app/src/test/java/com/hospitalops/fhir/FhirValidatorTestSupport.java`)
로 구조 검증하는 단위테스트를 갖고 있다. Phase 10 Step 10.2에서 5번째 매퍼
(`DiagnosisMapper`)를 추가하며 5개 매퍼 테스트 클래스를 전부 재실행해 최신 통과
여부를 실측했다:

```powershell
cd app
.\gradlew.bat test --tests "com.hospitalops.fhir.*MapperTests" --no-daemon
```

결과(`build/test-results/test/TEST-com.hospitalops.fhir.*MapperTests.xml`, 이번
세션에 새로 생성됨):

| 테스트 클래스 | tests | failures | errors | HAPI validation 테스트 |
|---|---|---|---|---|
| `PatientMapperTests` | 5 | 0 | 0 | `mappedPatientPassesHapiFhirStructuralValidation()` PASS |
| `EncounterMapperTests` | 3 | 0 | 0 | `mappedEncounterPassesHapiFhirStructuralValidation()` PASS |
| `ObservationMapperTests` | 4 | 0 | 0 | `mappedObservationPassesHapiFhirStructuralValidation()` PASS |
| `MedicationRequestMapperTests` | 4 | 0 | 0 | `mappedMedicationRequestPassesHapiFhirStructuralValidation()` PASS |
| `DiagnosisMapperTests`(Phase 10 신규) | 5 | 0 | 0 | `mappedConditionPassesHapiFhirStructuralValidation()` + `mappedActiveConditionAlsoPassesHapiFhirStructuralValidation()` 둘 다 PASS(resolved/active 케이스 각각 검증) |

`BUILD SUCCESSFUL` — 21개 테스트 전부 통과(0 failures / 0 errors), 5개 리소스
타입 각각 `ValidationResult.isSuccessful()`이 `true`임을 assert하는 테스트가
포함돼 있다. 이 테스트가 검증하는 `Patient`/`Encounter`/`Observation`/
`MedicationRequest`/`Condition` 객체는 §3에서 API로 실제 확인한 것과 **동일한
매퍼 코드**(`PatientMapper.toFhir`/`EncounterMapper.toFhir`/
`ObservationMapper.toFhir`/`MedicationRequestMapper.toFhir`/
`DiagnosisMapper.toFhir`)가 만든 결과다 — 이 세션에서 4종 기존 매퍼 코드를 전혀
수정하지 않았으므로, 단위테스트의 구조 검증 통과가 §3에서 실제로 캐시/API로
확인한 리소스에도 그대로 적용된다.

(참고: 런타임 `FhirResourceCacheUpsertService.upsert(...)`는 HAPI validator를
호출하지 않는다 — PLAN.md 지시대로 "구조 검증"은 단위테스트 계층에서만 수행하고,
배치 upsert 경로는 직렬화만 한다. 이는 설계된 경계이지 누락이 아니다.)

## 5. 캡처 방법 (실측 방식)

1. `Get-Process mysqld` / `Get-CimInstance Win32_Process` 로 기존에 떠 있던
   native `mysqld`(세션 시작 전부터 실행 중, 그대로 둠)와 gradle daemon(FHIR
   변환과 무관)을 먼저 확인해, 앱이 아직 기동되지 않은 상태임을 확인했다.
2. `mysql.exe -u hospital_ops -h localhost -P 3306 hospital_ops -e "..."` 로
   `PATIENT`/`VISIT`/`LAB_RESULT`/`PRESCRIPTION`/`AUDIT_LOG`/`FHIR_RESOURCE_CACHE`/
   `SYNC_WATERMARK`의 실제 row/건수를 조회했다(§3의 원본 row, §7의 기준선).
3. PowerShell `RNGCryptoServiceProvider`로 세션용 32바이트 `ENVELOPE_KEK`를
   생성하고(`EnvelopeCrypto` 빈은 이 값이 없으면 기동 자체가 fail-fast — 이번
   시연 대상인 `/fhir/**`/배치 경로와는 무관하지만 Spring 컨텍스트 기동에는
   필요), `SYNC_JOB_ENABLED=true`와 함께 `./gradlew.bat bootRun --no-daemon`을
   백그라운드로 실행했다. 로그(§2)로 `fhirSyncJob`이 `COMPLETED`로 끝난 것을
   확인했다.
4. `curl.exe -c cookies.txt http://localhost:8080/login`으로 로그인 폼의 `_csrf`
   히든값을 파싱하고, `physician`/`ChangeMe123!`(`SecurityDataSeeder.
   LOCAL_TEST_PASSWORD`)로 실제 `POST /login`을 수행해 인증된 `JSESSIONID`
   쿠키를 확보했다(302 -> `/dashboard`로 실제 리다이렉트되는 것으로 로그인 성공
   확인 — Step 8.5와 동일 절차).
5. 그 쿠키로 `curl.exe -b cookies.txt http://localhost:8080/fhir/{Type}/{id}` 및
   `.../fhir/Encounter?patient=patient-1`을 호출해 실제 응답 JSON을 파일로
   저장했다(§3에 그대로 인용). 미인증 상태(쿠키 없이) 호출은 302 `Location:
   http://localhost:8080/login`로 리다이렉트됨을, 존재하지 않는 id
   (`patient-999999`)는 `404` + `"Patient/patient-999999이(가)
   FHIR_RESOURCE_CACHE에 없습니다."`를 반환함을 별도로 확인했다.

## 6. 확인된 기능 목록

- [x] `PATIENT` -> `Patient`, `VISIT` -> `Encounter`, `LAB_RESULT` -> `Observation`,
      `PRESCRIPTION` -> `MedicationRequest` 4종 전부 실제 API 응답으로 확인
- [x] `DIAGNOSIS` -> `Condition`(Phase 10 Step 10.2 추가 5번째 리소스) 실제 API
      응답으로 확인(§3(f)/(g))
- [x] `fhirSyncJob`이 5개 Tasklet을 Patient -> Encounter -> Observation ->
      MedicationRequest -> Condition 순서로 실행하고 `COMPLETED` 상태로 종료(§2,
      §8 로그)
- [x] `GET /fhir/{Type}/{id}` 단건 조회가 로그인 세션 인증을 요구(미인증 302
      `/login` 리다이렉트, 역할 무관하게 로그인만 되면 허용) — `Condition`도
      동일하게 실측 확인(§8)
- [x] 존재하지 않는 `{id}`는 404 + 텍스트 안내(빈 캐시가 아니라 정상 미존재
      케이스로 처리됨) — `condition-999999`로도 실측 확인(§8)
- [x] `GET /fhir/Encounter?patient={id}` search가 `Bundle`(searchset)로 감싸
      반환하고, `total`이 레거시 `VISIT` 테이블의 실제 매칭 건수(19건)와 일치.
      `GET /fhir/Condition?patient={id}`도 동일 패턴으로 `DIAGNOSIS` 실제 매칭
      건수(14건)와 일치(§3(g))
- [x] `LAB_RESULT.code`/`PRESCRIPTION.medication_code` -> LOINC/RxNorm 코드
      해석이 `CODE_SET` 조회를 통해 실제로 동작(§3(c)/(d)의 `system` URI)
- [x] `VISIT.encounter_class`(자유 소문자 단어) -> FHIR v3-ActCode 고정 코드
      변환(`wellness`->`AMB`)이 실제 응답에 반영됨
- [x] `DIAGNOSIS.code_system`(Synthea가 제공하는 완전한 FHIR 시스템 URI, SNOMED CT
      또는 ICD-10) -> `Condition.code.coding[0].system` 원본 그대로 보존 확인(§3(f))
- [x] `DIAGNOSIS.resolved_at` 존재 여부 -> `clinicalStatus`(resolved/active) 파생
      및 `abatementDateTime`이 resolved일 때만 채워짐(con-4 충족)을 resolved/active
      두 케이스 모두 실제 응답으로 확인(§3(f))
- [x] 5개 매퍼 전부 HAPI FHIR `FhirValidator` 구조 검증 단위테스트를 이 세션에
      재실행해 통과 확인(§4, 21 tests / 0 failures / 0 errors)

## 7. DB 변형 여부 확인

이 Step은 조회 + 증분 동기화(멱등 upsert, 대상은 파생 캐시 테이블
`FHIR_RESOURCE_CACHE`이지 정본 테이블이 아님)만 수행했다. `PATIENT`/`AUDIT_LOG`
등 정본 테이블은 건드리지 않았다 — `com.hospitalops.api.FhirController`와
`batch` 패키지의 4개 SyncTasklet 어디에도 `AuditLog`를 쓰는 코드가 없음을 grep으로
확인했고(감사 로그에 쓰기를 하는 곳은 Step 8.5에서 이미 확인한 대로
`BreakGlassController`/`BulkDecryptionApprovalService`뿐), 이번 시연 중 로그인 1회
+ FHIR 조회만 수행해 그 경로를 전혀 거치지 않았다.

시연 전후 실측(동일):

```
mysql> SELECT COUNT(*) AS audit_log_count, MAX(audit_id) AS max_audit_id FROM AUDIT_LOG;
+------------------+--------------+
| audit_log_count  | max_audit_id |
+------------------+--------------+
|               21 |          276 |
+------------------+--------------+

mysql> SELECT COUNT(*) AS patient_count FROM PATIENT;
+---------------+
| patient_count |
+---------------+
|            12 |
+---------------+
```

기준선(`AUDIT_LOG` 21건/`MAX(audit_id)=276`, `PATIENT` 12건, Step 8.5와 동일)과
정확히 일치 — 이 Step으로 인한 정본 데이터 변형 없음.

`FHIR_RESOURCE_CACHE`(파생 캐시, 정본 아님)는 시연 전후 모두 `Patient 13 /
Encounter 511 / Observation 3759 / MedicationRequest 145`건으로 동일했다 —
`fhirSyncJob`은 실행됐지만(§2) 각 리소스 타입의 `SYNC_WATERMARK`가 이미 레거시
테이블 최댓값을 따라잡은 상태라 신규 upsert가 0건이었다(§2 참고). `Patient` 캐시
13건 vs `PATIENT` 테이블 12건(및 `Encounter` 511건 vs `VISIT` 509건)의 차이는
과거(Phase 2 개발/검증 중) 캐시에 반영된 뒤 이후 삭제된 레거시 row(예:
`source_table_pk=691`인 `patient-691` 캐시 항목 — 현재 `PATIENT`에는 없음)가
캐시에 그대로 남아있기 때문으로 보인다(캐시는 삭제를 반영하지 않는 upsert-only
구조 — 이 Step의 범위 밖이라 별도로 정리하지 않았다). §3의 샘플은 전부
`patient_id=1`(양쪽 테이블에 모두 존재, 가장 최근인 2026-07-19에 재동기화된 행)
기준이라 이 불일치의 영향을 받지 않는다.

## 8. Phase 10 Step 10.2 실측 기록 (`Condition` 추가, 2026-07-20 세션)

### 8.1 절차

1. 세션 시작 시 로컬 native `mysqld`(PID 25912, 29640)가 이미 떠 있는 상태를
   확인하고 그대로 두었다(정본 DB, 이번 세션이 새로 띄우지 않음).
2. V17 마이그레이션(`DIAGNOSIS` 테이블) 작성 후 `.\gradlew.bat test --tests
   "com.hospitalops.batch.SyntheaLoaderRunnerIT" --no-daemon`을 실행해
   `conditions.csv`(295건)를 `DIAGNOSIS`에 실제 적재했다(로컬 DB에 대한 실제
   INSERT — 이 테스트 자체가 `SyntheaLoaderRunner.load(...)`를 실제 DB에 대해
   호출하는 구조).
3. `ENVELOPE_KEK`(기존 세션과 동일한 테스트용 키) + `SYNC_JOB_ENABLED=true`로
   `.\gradlew.bat bootRun --no-daemon`을 백그라운드 기동, 로그로 `fhirSyncJob`의
   5번째 스텝 `syncDiagnosisStep`이 실행되고 `COMPLETED`로 끝난 것을 확인했다:

   ```
   2026-07-20T10:33:43.779+09:00  INFO ... SimpleStepHandler : Executing step: [syncDiagnosisStep]
   2026-07-20T10:33:43.805+09:00  INFO ... AbstractStep      : Step: [syncDiagnosisStep] executed in 25ms
   2026-07-20T10:33:43.821+09:00  INFO ... TaskExecutorJobLauncher : Job: [SimpleJob: [name=fhirSyncJob]] completed ... status: [COMPLETED] in 311ms
   ```
4. §5와 동일한 방식(로그인 -> 쿠키 확보 -> `curl.exe -b cookies.txt`)으로
   `GET /fhir/Condition/{id}`, `GET /fhir/Condition?patient=...`, 미인증 요청,
   존재하지 않는 id를 실제 호출해 §3(f)/(g)의 응답을 그대로 캡처했다.
5. 시연 후 `Stop-Process`로 이번 세션이 띄운 gradle/bootRun 프로세스만 종료했고
   (PID 26468 앱, 27096/23780/26416 gradle wrapper·daemon), 세션 시작 전부터
   떠 있던 `mysqld`(25912, 29640)는 그대로 두었다.

### 8.2 DB 변형 여부 (Phase 10 Step 10.2 기준)

이번 Step은 **신규 테이블(`DIAGNOSIS`)에만 데이터를 적재**했고, 기존 4개 정본
테이블(`PATIENT`/`VISIT`/`LAB_RESULT`/`PRESCRIPTION`)은 실측상 전혀 변형되지
않았다(세션 시작 직후와 전체 작업 종료 직후 두 시점 실측):

```
patient  visit  lab_result  prescription  diagnosis
12       509    3759        145           0     <- 세션 시작 직후(작업 착수 전)
12       509    3759        145           295   <- 전체 테스트/시연 종료 후
```

`AUDIT_LOG`는 25건 -> 29건으로 늘었다 — 이번 세션의 `gradlew test`가
`LoginFlowIT`/`RbacAccessIT`/`BreakGlassIT` 등 로그인·break-glass 관련 통합
테스트를 함께 실행했기 때문이며(이 Step이 직접 감사 로그에 쓰기를 하는 코드
경로는 없다 — `DiagnosisSyncTasklet`/`FhirController`의 `Condition` 관련 코드
어디에도 `AuditLog` 관련 호출이 없음), 감사 로그가 append-only로 정상 누적된
것이지 정본 데이터 훼손이 아니다. `PATIENT`/`VISIT`/`LAB_RESULT`/`PRESCRIPTION`
4개 정본 테이블 건수는 작업 지시(§9)대로 정확히 보존됐다.

`FHIR_RESOURCE_CACHE`는 4428건(세션 시작 시) -> 4723건(종료 시, `+295` =
`DIAGNOSIS` 295건 전량이 `Condition` 캐시로 신규 upsert)으로 늘었다 — 이는
파생 캐시 테이블이라 정본 훼손이 아니다:

```
resource_type      COUNT(*)
Condition           295
Encounter            511
MedicationRequest    145
Observation         3759
Patient               13
```

### 8.3 전체 테스트 스위트 (Java 테스트, 기존 129개 베이스라인 대비)

```powershell
cd app
.\gradlew.bat test --no-daemon
```

`build/test-results/test/*.xml` 실측 합산: **136 tests, 0 failures, 0 errors**
(`BUILD SUCCESSFUL`). 신규 5개(`DiagnosisMapperTests`) + 기존 `FhirControllerIT`에
추가한 2개(`getConditionReturnsStoredFhirJsonForRealSyncedRow`,
`searchConditionByPatientMatchesActualCacheCountForRealPatient`) = 129 + 7 = 136으로
정확히 일치. 기존 129개 테스트도 전부 그대로 통과했다(`SyncJobIdempotencyIT`의
`containsOnlyKeys` 단언에 `"Condition"`을 추가한 것을 제외하면 기존 테스트 로직
변경 없음).
