# Synthea 합성 데이터 생성기 도입 노트 (Phase 0 Step 0.5)

> PLAN.md Phase 0 Step 0.5 — "Synthea 합성 데이터 생성기 도입 확인 (출력 포맷·라이선스 점검, 소량 생성 시연)".
> 이 프로젝트는 실환자 데이터를 다루지 않는다(README.md 원칙). Synthea는 Phase 1 이후
> "가짜 병원 DB"(레거시 HIS 스키마)에 적재할 합성 환자 데이터의 원천이다.

---

## 1. 개요

[Synthea](https://github.com/synthetichealth/synthea)는 MITRE가 개발한 오픈소스 합성 환자
데이터 생성기다. 통계적 질병 진행 모델(모듈)을 기반으로 완전히 가상인 환자의 생애 전체
병력(내원·진단·처방·검사·시술 등)을 시뮬레이션해 FHIR R4 리소스 또는 CSV로 출력한다.
실제 인물과 무관한 100% 합성 데이터이므로 개인정보 이슈 없이 자유롭게 사용·배포·커밋할 수 있다.

## 2. 라이선스 — 주의: MIT 아님, Apache License 2.0

이번 step 착수 지시에는 "Synthea는 MIT 라이선스"라고 되어 있었으나, 실제로 확인한 결과
**Apache License 2.0**이다. 아래 두 가지 방법으로 교차 확인했다:

- GitHub Repository API (`GET /repos/synthetichealth/synthea`) → `license.spdx_id = "Apache-2.0"`,
  `license.name = "Apache License 2.0"`
- 저장소 루트 `LICENSE` 파일(`https://raw.githubusercontent.com/synthetichealth/synthea/master/LICENSE`) →
  Apache License, Version 2.0, January 2004 전문.

**결론**: Synthea = **Apache License 2.0**. MIT보다 조건이 조금 더 있지만
(고지·변경사항 명시 의무, NOTICE 파일 보존 의무 등) 상업적/비상업적 사용·재배포·수정 모두 허용되는
permissive 라이선스라는 점은 MIT와 동일하다. 이 프로젝트가 Synthea를 "그대로 실행해 산출물만 소비"하는
방식(소스 수정 없음, 재배포 없음)이므로 실질적인 제약은 없다. 다만 Synthea 소스 자체를 이 저장소에
재배포하지는 않는다(→ §4 vendor jar 미커밋 방침과도 일치).

- 저작권자: MITRE Corporation 및 기여자
- 원문: http://www.apache.org/licenses/LICENSE-2.0

## 3. 버전

- 다운로드한 릴리스 태그: **`v4.0.0`** (GitHub Releases, 2026-03-05 게시 — `master-branch-latest`
  롤링 빌드 대신 고정 태그를 사용해 재현성 확보)
- 실행 시 Synthea가 자체 보고하는 내부 빌드 버전(생성된 `data/synthetic/metadata/*.json`의
  `"version"` 필드): `v3.4.0-18-ga07a65555` — release jar 빌드 시점의 내부 git describe 결과이며,
  배포 태그(`v4.0.0`)와 다른 문자열이 나오는 것은 Synthea 자체의 버전 태깅 방식 때문이다(참고용).
- 실행 자산: `synthea-with-dependencies.jar` (모든 의존성이 포함된 단일 실행 jar, 약 192MB)
- 실행 환경: Java 17 (Temurin 17.0.19)로 정상 실행 확인.

## 4. 도입 방식 — 저장소 클론 대신 릴리스 jar 직접 사용

Synthea GitHub 저장소 전체를 클론해 Gradle로 빌드하는 대신, GitHub Releases에 배포된
`synthea-with-dependencies.jar`를 직접 다운로드해 실행하는 방식을 택했다(더 가볍고 빠름,
로컬 Gradle 불필요).

- 다운로드 위치: `/scripts/vendor/synthea-with-dependencies.jar`
- **git에 커밋하지 않는다** — 용량이 크고(약 192MB) 다운로드로 언제든 재현 가능하기 때문.
  `.gitignore`에 `/scripts/vendor/` 규칙 추가(루트 `.gitignore`의 기존 `*.jar` 규칙과 중복되지만
  의도를 명시하기 위해 별도 라인으로 추가).
- 재현 방법: `/scripts/gen-synthetic-data.ps1`이 jar가 없으면 자동으로
  `https://github.com/synthetichealth/synthea/releases/download/v4.0.0/synthea-with-dependencies.jar`
  에서 다운로드한다(`-ForceDownload`로 강제 재다운로드 가능, `-JarVersion`으로 다른 태그 지정 가능).

## 5. 출력 포맷

### 5.1 FHIR R4 Bundle JSON (기본, 항상 생성)

환자 1명당 파일 1개, `data/synthetic/fhir/<이름>_<UUID>.json`. 각 파일은 FHIR
`Bundle`(`type: "transaction"`) 리소스이며, `entry[]` 배열에 그 환자의 전체 생애 리소스가
시간순으로 들어 있다. 실제로 생성해본 파일(`Booker670_Bednar518_...json`, 약 698KB) 기준
`entry` 216개, 리소스 타입 분포:

| resourceType | 개수 |
|---|---|
| Observation | 64 |
| DiagnosticReport | 29 |
| Procedure | 23 |
| Claim | 19 |
| ExplanationOfBenefit | 19 |
| DocumentReference | 18 |
| Encounter | 18 |
| Condition | 14 |
| Immunization | 6 |
| CarePlan / CareTeam / ImagingStudy / MedicationRequest / Patient / Provenance | 각 1 |

환자별 Bundle 외에 실행마다 `hospitalInformation<seed>.json`(Organization/Location 리소스),
`practitionerInformation<seed>.json`(Practitioner/PractitionerRole 리소스)도 함께 생성된다.

각 Bundle의 최상위 구조:
```json
{
  "resourceType": "Bundle",
  "type": "transaction",
  "entry": [
    { "fullUrl": "urn:uuid:...", "resource": { "resourceType": "Patient", ... }, "request": { "method": "POST", "url": "Patient" } },
    { "fullUrl": "urn:uuid:...", "resource": { "resourceType": "Encounter", ... }, "request": { ... } },
    ...
  ]
}
```

**발췌 샘플**: `/docs/samples/synthea-sample-patient-bundle.json` — 위 실제 실행 산출물 중
대표 리소스 타입 10종(Patient/Encounter/Condition/Observation/Procedure/MedicationRequest/
Immunization/DiagnosticReport/Claim/CarePlan)의 첫 항목만 추려 26KB로 축약한 것(원본은
216 entry·약 700KB라 전체를 커밋하지 않았다). 유효한 JSON임을 재파싱으로 확인함.

### 5.2 CSV (옵션, `--exporter.csv.export=true`)

`-IncludeCsv` 스위치로 함께 생성 가능. `data/synthetic/csv/`에 18개 테이블(정규화된 관계형
형태)이 생성된다: `patients, encounters, conditions, observations, procedures, medications,
immunizations, allergies, careplans, devices, imaging_studies, supplies, claims,
claims_transactions, payers, payer_transitions, organizations, providers`.

`patients.csv` 헤더 예시(실제 생성 결과):
```
Id,BIRTHDATE,DEATHDATE,SSN,DRIVERS,PASSPORT,PREFIX,FIRST,MIDDLE,LAST,SUFFIX,MAIDEN,MARITAL,
RACE,ETHNICITY,GENDER,BIRTHPLACE,ADDRESS,CITY,STATE,COUNTY,FIPS,ZIP,LAT,LON,
HEALTHCARE_EXPENSES,HEALTHCARE_COVERAGE,INCOME
```
`SSN` 등 식별자처럼 보이는 컬럼도 전부 Synthea가 무작위 생성한 가짜 값이다(예: `999-xx-xxxx`
형태 — `999`는 미국 SSA가 실제 SSN에 절대 배정하지 않는 접두라 합성 데이터임이 값 자체로도
드러난다). Phase 1에서 레거시 스키마에 적재할 때는 이런 컬럼도 내부 PK와 분리해 다룰 예정(PLAN.md
Phase 1.2 참고).

## 6. 실행 방법 — `/scripts/gen-synthetic-data.ps1`

```powershell
# 기본값(인구 10명, Massachusetts, FHIR JSON만)
./scripts/gen-synthetic-data.ps1

# 소량 시연: 12명, Massachusetts주 Bedford시, 시드 고정(재현 가능), CSV도 함께
./scripts/gen-synthetic-data.ps1 -PopulationSize 12 -State Massachusetts -City Bedford -IncludeCsv -Seed 20260718
```

주요 파라미터: `-PopulationSize`(기본 10), `-State`(기본 Massachusetts), `-City`(선택),
`-Seed`(선택, 지정 시 재현 가능한 동일 인구 집단 생성), `-IncludeCsv`(스위치), `-OutputDir`(기본
`<repo>/data/synthetic`), `-JarVersion`(기본 `v4.0.0`), `-ForceDownload`(스위치).

동작: (1) `scripts/vendor/synthea-with-dependencies.jar`가 없으면 다운로드 → (2) Java 실행 파일을
`PATH` 또는 Eclipse Adoptium/Java 표준 설치 경로에서 탐색 → (3) `java -jar ... -p <N> [-s <seed>]
--exporter.baseDirectory=<output> --exporter.fhir.export=true [--exporter.csv.export=true]
<State> [<City>]` 실행 → (4) 산출물 파일 목록을 출력.

## 7. 실제 소량 생성 시연 결과 (로컬 검증 근거)

**실행 커맨드**:
```
./scripts/gen-synthetic-data.ps1 -PopulationSize 12 -State Massachusetts -City Bedford -IncludeCsv -Seed 20260718
```

**실행 결과 요약** (Synthea 자체 로그 발췌):
```
Running with options:
Population: 12
Seed: 20260718
Location: Bedford, Massachusetts
Records: total=12, alive=12, dead=0
You've just generated 12 patients!
```

**산출물 위치**: `data/synthetic/` (저장소 루트 기준, git 커밋 대상 아님 — `.gitignore`의 `/data/` 규칙)

**생성된 파일 (33개 파일, `Get-ChildItem -Recurse` 실측 — 14+18+1)**:

| 디렉터리 | 파일 수 | 내용 |
|---|---|---|
| `data/synthetic/fhir/` | 14 | 환자 12명 Bundle JSON + `hospitalInformation*.json` + `practitionerInformation*.json` |
| `data/synthetic/csv/` | 18 | 정규화된 CSV 18개 테이블 (총합 약 8.7MB, 그중 `claims_transactions.csv` 2.9MB, `observations.csv` 696KB가 최대) |
| `data/synthetic/metadata/` | 1 | 실행 메타데이터(JSON) — seed, 환자 수, 실행시각, Synthea 내부 버전 등 |

**`patients.csv` 검증**: 헤더 1줄 + 데이터 12줄 = 총 13줄(`Get-Content | Measure-Object -Line`로 확인)
→ population 12와 일치.

**JSON 유효성 확인**: `Booker670_Bednar518_...json`을 PowerShell `ConvertFrom-Json`으로 파싱 성공
(`resourceType: Bundle`, `type: transaction`, `entry.Count: 216`). `/docs/samples/synthea-sample-patient-bundle.json`
(발췌본)도 재파싱해 유효성 재확인함(§5.1 참고).

**실행 메타데이터** (`data/synthetic/metadata/*.json` 전문):
```json
{
  "runID": "3d242743-8c42-47a0-bab3-fee2e64ec7d4",
  "seed": 20260718,
  "referenceTime": "20260718",
  "endTime": "20260718",
  "version": "v3.4.0-18-ga07a65555",
  "patientCount": 12,
  "providerCount": 1327,
  "payerCount": 9,
  "javaVersion": "17.0.19",
  "runTimeInSeconds": 30,
  "city": "Bedford",
  "state": "Massachusetts"
}
```

**재현성 확인**: 최초 시도에서는 스크립트의 `-Seed` 파라미터가 PowerShell `[Nullable[int]]` 바인딩
문제로 실제 전달되지 않는 버그가 있었음을 발견·수정했다(`.HasValue`가 커맨드라인 바인딩 시 채워지지
않아 대신 `$PSBoundParameters.ContainsKey('Seed')`로 판별하도록 교체). 수정 후 재실행 로그에서
`-s 20260718`이 실제 Java 인자에 포함되고 Synthea 로그에도 `Seed: 20260718`로 정확히 반영됨을
확인했다.

## 8. Acceptance criteria 대조

PLAN.md 명시 기준: "Synthea 실행 산출물(FHIR bundle 또는 CSV)이 `/data/synthetic/`에 생성됨"

- [x] FHIR Bundle JSON 12개(환자 1명당 1개) + 부속 파일 2개가 `/data/synthetic/fhir/`에 생성됨
- [x] (옵션까지 포함) CSV 18개 테이블이 `/data/synthetic/csv/`에 생성됨
- [x] 실행 메타데이터가 `/data/synthetic/metadata/`에 생성됨
- [x] 생성된 JSON의 유효성을 직접 재파싱하여 확인함
- [x] 라이선스 점검 완료(Apache License 2.0으로 정정 확인)
- [x] 출력 포맷 문서화 완료(본 문서 §5)
