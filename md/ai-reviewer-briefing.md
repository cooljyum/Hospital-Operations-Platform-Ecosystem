# AI 리뷰어 브리핑 — Hospital FHIR Ops Lab

> 이 문서는 이 대화 맥락을 전혀 모르는 **외부 AI 코드 리뷰어**를 위해 작성됐다. 목적은
> 프로젝트를 빠르게 파악시키는 것이지, 리뷰 체크리스트를 강요하는 것이 아니다 — 무엇을
> 어떻게 볼지는 리뷰어가 독립적으로 판단하면 된다. 여기 적힌 모든 사실은 2026-07-21
> 세션에서 코드/문서를 직접 읽고 명령을 직접 실행해 확인한 것이며(전체 재검토 세션),
> 추측이나 이전 세션 보고서의 재인용이 아니다. 실행한 명령과 실측값은 각 절에 그대로
> 남겨뒀다 — 리뷰어가 재현하거나 의심할 수 있도록.

---

## 1. 프로젝트 정체성

**"Hospital FHIR Ops Lab"** — 병원 전산 관리직(EMR/HIS 운영·유지보수) 지원을 위한 1인 개발
포트폴리오다. 합성 병원 데이터([Synthea](https://github.com/synthetichealth/synthea)로 생성)를
FHIR R4로 연계하는 미니 시스템을 만들되, **이 프로젝트의 실질적 핵심은 기능이 아니라 "운영
증빙"**이다 — 실제로 장애를 재현하고 대응한 로그북, 배치 실패→재처리 runbook, 슬로우 쿼리
튜닝 전후 기록, 백업·복구 리허설(복구 소요시간 실측), 감사 로그 조회 화면 시연, FHIR 변환
데모. 기획 배경은 `docs/planning/deliverable.md`(제안서) §1이 명시한다: "개발을 해봤다"가
아니라 "시스템을 운영해 봤다"를 증명하는 구성.

**실환자 데이터는 어디에도 없다.** 모든 PATIENT/VISIT/LAB_RESULT/PRESCRIPTION/DIAGNOSIS
데이터는 Synthea 합성 환자 생성기 산출물을 적재한 것이며(`docs/synthea-notes.md`,
`SyntheaLoaderRunner`), 주민등록번호(RRN) 계열 식별자는 스키마 어디에도 존재하지 않는다(이
설계 원칙은 §4에서 상술).

---

## 2. 기술 스택 (실측, `app/build.gradle` 기준)

| 계층 | 스택 | 버전(실측) |
|---|---|---|
| 언어/런타임 | Java 17 (toolchain 고정) | — |
| 프레임워크 | Spring Boot | `3.5.16` (`org.springframework.boot` 플러그인) |
| 의존성 관리 | `io.spring.dependency-management` | `1.1.7` |
| 웹/뷰 | Spring Web + Thymeleaf + `thymeleaf-extras-springsecurity6` | Boot BOM 관리 |
| 인증/인가 | Spring Security(폼+세션) | Boot BOM 관리 |
| 배치 | Spring Batch | Boot BOM 관리 |
| ORM | Spring Data JPA | Boot BOM 관리 |
| 마이그레이션 | Flyway core + `flyway-mysql` | Boot BOM 관리 |
| DB 드라이버 | `com.mysql:mysql-connector-j` | Boot BOM 관리(런타임 실측 `9.7.0`) |
| FHIR | `ca.uhn.hapi.fhir:hapi-fhir-structures-r4` | `8.10.0`(구조체 라이브러리만, 전체 FHIR 서버 아님) |
| FHIR 검증(테스트 전용) | `hapi-fhir-validation` + `hapi-fhir-validation-resources-r4` + `hapi-fhir-caching-caffeine` | `8.10.0` |
| 관측 | Spring Boot Actuator + `micrometer-registry-prometheus` | Boot BOM 관리 |
| 테스트 | JUnit 5(`spring-boot-starter-test`), `spring-security-test`, `spring-batch-test`, H2(테스트 런타임 전용) | Boot BOM 관리 |
| 빌드 도구 | Gradle Wrapper | `9.5.1`(Docker 빌드 로그 실측) |
| DB(운영) | MySQL | `8.0`(Docker) / `8.4`(로컬 native, winget `Oracle.MySQL`) |
| 인프라 | Docker Compose(`docker/docker-compose.yml`): MySQL, app, Prometheus, Grafana, Nginx | Compose spec v2(버전 필드 없음, 최신 문법) |

프론트엔드는 AdminLTE 정적 자산(`app/src/main/resources/static/adminlte/`)을 Thymeleaf 레이아웃
프래그먼트로 통합했다(React 등 SPA 프레임워크 미사용 — `docs/planning/PLAN.md` §0에서 Gemini
CLI 자문 결과로 Thymeleaf를 확정했다고 기록돼 있음, 근거는 §12 참고).

---

## 3. 아키텍처

```
[레거시 HIS DB (MySQL, 정본)]  PATIENT / VISIT / LAB_RESULT / PRESCRIPTION / DIAGNOSIS
        │  Spring Batch: 워터마크 기반 증분 pull + 멱등 upsert (fhirSyncJob, CDC/실시간 배제)
        ▼
[FHIR_RESOURCE_CACHE]  Patient / Encounter / Observation / MedicationRequest / Condition (5종)
        │  조회 전용 — REST API가 원본 레거시 테이블을 직접 참조하지 않음
        ▼
[REST API (FhirController) / Thymeleaf+AdminLTE 화면]  RBAC 5역할, break-glass, 감사 로그
```

### 3.1 패키지 구조 (`app/src/main/java/com/hospitalops/`, 파일 수는 2026-07-21 실측)

| 패키지 | 파일 수 | 역할 | 대표 클래스 |
|---|---|---|---|
| `api` | 1 | FHIR REST API 컨트롤러(캐시 전용 조회) | `FhirController` |
| `fhir` | 17 | FHIR 변환 계층 — Row DTO, Mapper, 캐시 엔티티/리포지토리/upsert 서비스, 코드셋 조회 | `PatientMapper`/`EncounterMapper`/`ObservationMapper`/`MedicationRequestMapper`/`DiagnosisMapper`, `FhirResourceCacheUpsertService`, `CodeSetLookupService` |
| `batch` | 19 | Spring Batch: Synthea 적재, FHIR 동기화 Tasklet 5종, 요약 테이블 refresh, 워터마크 관리 | `SyncJobConfig`, `PatientSyncTasklet`~`DiagnosisSyncTasklet`, `SummaryRefreshTasklet`, `SyntheaLoaderRunner` |
| `security` | 14 | 인증(폼+세션), RBAC(`ACCESS_POLICY_RULES`), 감사 로그 엔티티, break-glass 세션 속성, 로컬 계정 시더 | `SecurityConfig`, `AppUserDetailsService`, `AccessPolicyRule(Repository)`, `AuditLog(Repository)`, `SecurityDataSeeder` |
| `breakglass` | 1 | 응급 접근 부여 컨트롤러 | `BreakGlassController` |
| `audit` | 1 | 감사 로그 조회 화면(감사자 전용) | `AuditLogController` |
| `crypto` | 3 | 가명화·암호화·날짜 이동 | `Pseudonymizer`, `EnvelopeCrypto`, `DateShifter` |
| `approval` | 4 | 대량 복호 3단계 승인 상태머신 | `BulkDecryptionRequest(Repository)`, `BulkDecryptionApprovalService`, `BulkDecryptionStatus` |
| `stats` | 7 | k=5 익명성 + small-cell suppression 통계(REST + 화면) | `PatientStatsService/Repository`, `SuppressedCell`, `StatsController`, `StatsViewController` |
| `reports` | 3 | 요약 테이블(`PATIENT_VISIT_SUMMARY`) 리포팅 화면 | `PatientVisitSummaryController/Repository` |
| `web` | 1 | 기타 화면 컨트롤러 | — |

컨트롤러 계층은 레거시 리포지토리를 직접 참조하지 않고 `fhir` 패키지의 매퍼/캐시를 경유한다
(Phase 2 설계 원칙, `docs/planning/PLAN.md` §4 acceptance criteria).

---

## 4. 도메인 핵심 설계 원칙

| 원칙 | 코드 위치 | 이유 |
|---|---|---|
| **불변 내부 PK**(RRN 미사용) | `V1__legacy_his_core_schema.sql`(auto-increment PK), `V2__patient_display_identifier.sql`(표시용 `synthetic_patient_no` 분리) | 표시용 식별자를 주기적으로 바꾸는 설계는 과거 감사 기록과의 연결을 끊는다 — 감사 추적성이 최우선 |
| **워터마크 + 멱등 upsert**(CDC 배제) | `com.hospitalops.batch.SyncWatermarkService`, `SyncJobConfig`, `*SyncTasklet` | 배치 pull만 사용, 실시간 CDC는 1인 개발 범위 밖으로 의도적 배제 |
| **Break-glass 응급 접근** | `com.hospitalops.breakglass.BreakGlassController`, `com.hospitalops.security.BreakGlassSessionAttributes`, `SecurityConfig.breakGlassAuthorizationManager()` | `/emergency/**`는 평소 `ACCESS_POLICY_RULES`에 없어 아무도 접근 불가 — 사유 입력 후에만 세션 플래그로 한시 허용, 전용 감사 기록 |
| **감사로그(원문 쿼리 미저장)** | `com.hospitalops.security.AuditLog`, `V10__audit_log.sql` | 파라미터 바인딩 템플릿만 기록, SQL 리터럴(개인정보 문자열) 미저장 |
| **HMAC-SHA-256 가명화** | `com.hospitalops.crypto.Pseudonymizer` | 표시·외부 노출용 결정론적 해시, 내부는 불변 PK 유지, 원본 PK는 응답에 노출 안 함 |
| **AES-256-GCM 엔벨로프 암호화 + 키 회전** | `com.hospitalops.crypto.EnvelopeCrypto`, `docs/runbooks/key-rotation.md` | 레코드마다 무작위 DEK 생성 → DEK로 암호화 → KEK로 DEK 래핑. 키 회전 시 DEK 재래핑만 하면 되고 전체 재암호화 불필요 |
| **Date shifting**(환자별 고정 오프셋) | `com.hospitalops.crypto.DateShifter` | 같은 환자의 모든 날짜 필드가 동일 오프셋만큼 이동, 환자 간 오프셋 상이(HMAC 기반 결정론적 오프셋 산출로 추정 — 클래스명·패키지 위치로 확인, 상세 알고리즘은 클래스 참고) |
| **k=5 익명성 + small-cell suppression** | `com.hospitalops.stats.PatientStatsService/Repository`, `SuppressedCell` | 셀 값 <5인 통계는 원값 대신 억제 표시(`"< 5"`)로만 노출 |
| **3단계 승인 상태머신**(대량 복호) | `com.hospitalops.approval.BulkDecryptionRequest/Status/ApprovalService`, `V11__bulk_decryption_approval.sql` | `PENDING → APPROVED/REJECTED`만 가능(역행 불가), 전이마다 AuditEvent 1건 |

---

## 5. RBAC 5역할과 접근 정책

역할: `ROLE_PHYSICIAN`/`ROLE_NURSE`/`ROLE_REGISTRAR`/`ROLE_SYSTEM_ADMIN`/`ROLE_AUDITOR`
(`V9__rbac_access_policy.sql`이 스키마·시드 도입).

**정책은 하드코딩된 `hasRole(...)` 나열이 아니라 DB 테이블 `access_policy_rules`**
(`rule_id`, `role_name`, `url_pattern`, `allowed`, `description`)로 관리된다.
`SecurityConfig.securityFilterChain(...)`이 **앱 기동 시점에 1회** 이 테이블을 읽어
`url_pattern`별로 허용된 `role_name` 목록을 그룹핑한 뒤 `hasAnyAuthority(...)`로 정적
`SecurityFilterChain`을 구성한다 — 완전 동적(요청마다 DB 조회)이 아니라 "기동 시 로드"
절충이며, 정책 변경은 테이블 row만 바꾸면 되지만 반영에는 재기동이 필요하다(코드 주석에
설계 의도 명시, `app/src/main/java/com/hospitalops/security/SecurityConfig.java` 21~39행).

2026-07-21 세션에 로컬 DB에서 실측한 `access_policy_rules` 15개 row는 다음 URL 패턴을
다룬다: `/dashboard`(전 역할), `/admin/system`(전산관리자), `/audit/preview`(감사자),
`/stats/**`(감사자·전산관리자, V12), `/actuator/metrics`, `/actuator/metrics/**`(감사자·
전산관리자, V14), `/reports/patient-visit-summary`(감사자·전산관리자, V16). `/emergency/**`는
이 테이블에 row가 없다(위 break-glass 참고). `/fhir/**`도 row가 없어 역할 제한은 없지만
`auth.anyRequest().authenticated()`에 걸려 로그인 세션은 필요하다(무슨 역할이든).
`/actuator/health`, `/actuator/info`, `/actuator/prometheus`는 `ACCESS_POLICY_RULES`가
아니라 `SecurityConfig`에 하드코딩된 `permitAll()`이다(Prometheus 스크레이퍼가 세션 없이
호출하는 인프라 경로로 취급, 코드 주석 근거 명시).

---

## 6. FHIR 매핑 5종

| 레거시 테이블 | FHIR 리소스 | 매퍼 | 배치 Tasklet |
|---|---|---|---|
| `PATIENT` | `Patient` | `com.hospitalops.fhir.PatientMapper` | `PatientSyncTasklet` |
| `VISIT` | `Encounter` | `com.hospitalops.fhir.EncounterMapper` | `EncounterSyncTasklet` |
| `LAB_RESULT` | `Observation` | `com.hospitalops.fhir.ObservationMapper` | `ObservationSyncTasklet` |
| `PRESCRIPTION` | `MedicationRequest` | `com.hospitalops.fhir.MedicationRequestMapper` | `MedicationRequestSyncTasklet` |
| `DIAGNOSIS` | `Condition` | `com.hospitalops.fhir.DiagnosisMapper` | `DiagnosisSyncTasklet`(Phase 10.2 추가, `V17__diagnosis.sql`) |

`Observation`/`MedicationRequest`는 `CODE_SET`(`V6__code_set.sql`, `CodeSetLookupService`)을
거쳐 LOINC/RxNorm으로 코드를 해석한다. `Condition`은 Synthea `conditions.csv`가 이미 완전한
FHIR 코드시스템 URI(SNOMED CT/ICD-10)를 제공하므로 `CODE_SET`을 쓰지 않고 원본 그대로 보존한다
(`docs/demos/fhir-conversion.md` §0에 설계 판단 근거 기록). 변환은 요청 시점이 아니라 배치
시점에 일어나며(`fhirSyncJob`이 5개 Tasklet을 순서대로 실행), `FhirController`는 오직
`FHIR_RESOURCE_CACHE`만 조회한다.

---

## 7. 실행 방법 (2026-07-21 세션 실측 — 두 경로 모두 실제 실행해 확인)

### 7.1 로컬 직접 실행 (native MySQL + `./gradlew bootRun`) — 동작 확인됨

```powershell
# 1) 로컬 MySQL(scripts/start-local-mysql.ps1 — winget Oracle.MySQL 8.4를 일반 프로세스로 기동)
# 이미 3306에서 떠 있으면 스크립트가 자동으로 스킵한다.

cd app
$env:DB_HOST="localhost"; $env:DB_PORT="3306"; $env:DB_NAME="hospital_ops"
$env:DB_USERNAME="hospital_ops"; $env:DB_PASSWORD="changeme"
$env:ENVELOPE_KEK="<Base64 인코딩된 32바이트 키>"   # 필수 — 없으면 EnvelopeCrypto 빈 생성 실패로 기동 자체가 안 됨
.\gradlew.bat bootRun --no-daemon
```

`ENVELOPE_KEK`는 `app/src/main/resources/application.yml`의 `app.crypto.envelope.kek:
${ENVELOPE_KEK:}`로 주입되며, 빈 문자열이면 `EnvelopeCrypto` 생성자가 `IllegalStateException`을
던져 Spring 컨텍스트 기동 자체를 막는다(의도된 fail-fast, §4 표 참고). 이 경로는 여러 세션에서
반복 검증됐고(`docs/demos/fhir-conversion.md`, `docs/demos/audit-log-screen.md` 등에 실측 로그
있음), 이번 재검토 세션에서도 `./gradlew.bat test`(전체 테스트, native MySQL 미기동 상태에서도
컨텍스트 일부는 H2로 대체돼 돌아감)를 재실행해 정상 동작을 재확인했다(§8).

### 7.2 Docker Compose 경로 — **2026-07-23 세션에 3개의 blocking 버그를 모두 고치고 5개 컨테이너 정상 기동까지 재검증 완료**

2026-07-21 재검토 세션은 진단만 하고 수정하지 않았다(아래는 그 세션의 원 기록). 2026-07-23
세션에서 사용자 승인 하에 실제로 고쳤고, `docker compose up -d`로 5개 컨테이너가 전부
`healthy`/`running`이 되는 것까지 재검증했다. 상세 절차·검증 결과는 `docker/README.md`
"이 저장소가 작업된 머신에서의 검증 상태" 절에 남겨뒀다 — 여기서는 요약만 적는다.

**2026-07-21 세션에서 발견한 원 기록** (이하 두 버그는 그 세션의 실측 그대로 보존):

- Docker Desktop(`29.6.1`)과 WSL2(`Ubuntu` distro, `docker-desktop` distro)가 이미 설치돼 있고
  `docker info`/`docker ps`가 정상 응답한다.
- `docker compose up -d`를 실제로 실행해 5개 컨테이너(mysql/app/prometheus/grafana/nginx)
  이미지를 빌드·기동을 시도했다. mysql/prometheus/grafana/nginx 4개는 정상 기동(healthy)했지만,
  `app` 컨테이너는 계속 재시작 루프(`Restarting (1)`)에 빠졌다.
- 원인 ①: `docker-compose.yml`의 `app.environment`에 `ENVELOPE_KEK`가 배선돼 있지 않아
  `EnvelopeCrypto` 생성자가 즉시 예외를 던짐.
- 원인 ②: MySQL 8.0 컨테이너가 기본 인증 플러그인 `caching_sha2_password`로 `hospital_ops`
  유저를 생성하는데, `application.yml`의 JDBC URL에 `allowPublicKeyRetrieval=true`가 없어
  Flyway 초기화 단계에서 `Public Key Retrieval is not allowed` 예외로 먼저 죽음.
- 두 버그를 임시로 우회해 5개 컨테이너 전부 정상 기동까지는 확인했으나, **그 세션에서는 실제
  파일을 고치지 않고 우회를 전부 원상복구했다.**

**2026-07-23 세션에서 실제 수정하며 새로 발견한 세 번째 버그**: 위 ①②만 고친 뒤 재기동해보니
`app`이 여전히 재시작 루프에 빠졌다. 로그를 보니 `Hibernate SchemaManagementException:
Schema-validation: missing table [access_policy_rules]`였다 — Flyway는 `ACCESS_POLICY_RULES`
(대문자, `V9__rbac_access_policy.sql`과 `AccessPolicyRule.java`의 `@Table(name="ACCESS_POLICY_RULES")`에
쓰인 그대로)로 테이블을 만들었지만, Spring Boot의 `SpringPhysicalNamingStrategy`는 `@Table`에
명시된 이름도 항상 소문자로 정규화해 검증한다. **로컬 native Windows MySQL은
`lower_case_table_names`가 플랫폼 기본값으로 대소문자를 구분하지 않아** 이 불일치가 가려져 있었지만,
**리눅스 컨테이너의 MySQL 기본값(0, 대소문자 구분)에서는 실제로 검증 실패**로 이어졌다 — 이전
어떤 문서에도 언급되지 않았던, 이번 실기동에서 처음 발견한 버그다(같은 이유로 `APP_ROLE`,
`APP_USER`, `AUDIT_LOG` 등 대문자로 명시된 다른 엔티티 테이블도 동일한 잠재 위험이 있었다).
`docker-compose.yml`의 `mysql` 서비스에 `command: --lower-case-table-names=1`을 추가해 컨테이너
MySQL을 로컬과 동일한 대소문자 비구분 동작으로 맞춰 해결했다.

세 버그를 모두 고친 뒤 실제로 검증한 것: 5개 컨테이너 전부 `healthy`/`running`,
`GET /actuator/health` → `200 UP`(직접 접근·nginx 경유 모두), Prometheus가
`app:8080/actuator/prometheus`를 `health:"up"`으로 스크레이프, Grafana에 Prometheus datasource
자동 프로비저닝 확인, `docker compose logs app`에 KEK/DB 에러 없음. 검증 후
`docker compose down -v`로 컨테이너·볼륨 전량 정리, 로컬 native MySQL 재기동 및 `PATIENT` 12건
데이터 무결성 확인까지 마쳤다.

---

## 8. 테스트 (2026-07-21 세션 재실행 결과)

```powershell
cd app
.\gradlew.bat test --no-daemon
```

결과: **`BUILD SUCCESSFUL`, `build/test-results/test/*.xml` 28개 파일 합산 136 tests, 0
failures, 0 errors.** 이 수치는 `docs/demos/fhir-conversion.md` §8.3(Phase 10.2 시점 기록)이
보고한 "136 tests, 0 failures, 0 errors"와 정확히 일치한다 — 즉 최신 상태에서도 문서에 적힌
수치는 유효하다(stale하지 않음). 테스트 실행 JVM에는 `build.gradle`의 `tasks.named('test')`
블록이 테스트 전용 `ENVELOPE_KEK`를 하드코딩 주입한다(운영 키 아님, 32바이트 무작위 Base64).

---

## 9. 운영 증빙 산출물 지도 (실제 파일 존재 확인, 2026-07-21)

| 등급 | # | 산출물 | 경로 | 확인 |
|---|---|---|---|---|
| P0 | 1 | 장애 시나리오 재현·대응 로그북(4종) | `docs/incidents/` — `2026-07-19-batch-job-forced-failure.md`, `2026-07-19-mysql-outage-reconnect-failure.md`, `2026-07-19-db-connection-pool-exhaustion.md`, `2026-07-19-disk-space-threshold.md` | 4개 파일 실존 |
| P0 | 2 | 배치 실패 → 재처리 runbook | `docs/runbooks/batch-retry.md` | 실존 |
| P0 | 3 | 슬로우 쿼리 튜닝 전후 기록 | `docs/tuning/2026-07-20-audit-log-target-pk.md`(1건) | 실존 — `AUDIT_LOG.target_pk` 인덱스 추가(`V15`) 사례 |
| P0 | 4 | 백업·복구 리허설 | `docs/runbooks/backup-restore.md` | 실존 |
| P0 | 5 | 감사 로그 조회 화면 시연 | `docs/demos/audit-log-screen.md` | 실존 |
| P0 | 6 | FHIR 변환 데모 | `docs/demos/fhir-conversion.md` | 실존, Phase 10.2에서 Condition 추가분까지 갱신됨 |
| P1 | - | 요약 테이블 리포팅 화면 | `/reports/patient-visit-summary`, `com.hospitalops.reports.*`, `V13__patient_visit_summary.sql` | 코드/라우트 실존, RBAC(V16) 확인 |
| P1 | - | 비식별 통계 화면 | `/stats/**`, `com.hospitalops.stats.*`, `V12__stats_access_policy.sql` | 코드/라우트 실존 |
| P2 | - | Oracle 19c 분기 문서 | `docs/oracle-branch/README.md`, `mysql-oracle-dialect-diff.md` | 실존, §10에 정직성 재확인 |
| P2 | - | FHIR 리소스 확장(Condition, 5종) | `DiagnosisMapper`, `DiagnosisSyncTasklet`, `V17__diagnosis.sql` | 실존, DB row 295건 확인(§10) |
| 추가 runbook | - | 키 회전 절차 | `docs/runbooks/key-rotation.md` | 실존(Phase 4.2 산출물, README 표에는 미포함이나 실제 존재) |

관측(Phase 7): Actuator `/actuator/prometheus` 노출, Grafana 대시보드 JSON은
`docker/grafana/dashboards/`에 위치(파일 존재는 확인했으나 이번 세션에서 내용까지 상세
검증하지는 않음).

---

## 10. 알려진 한계·리스크 (심각도 포함, 이번 재검토 세션에서 직접 확인)

### 즉시 실행 실패를 유발했던 것 (2026-07-23 세션에 모두 수정·재검증 완료)

1. **[해결됨, 2026-07-23] Docker Compose 경로가 기동 불가했던 문제.** 세 개의 독립된 버그가
   있었다(§7.2에 상세):
   - `docker-compose.yml`의 `app.environment`에 `ENVELOPE_KEK` 미배선 → `environment`에
     `ENVELOPE_KEK: ${ENVELOPE_KEK}` 추가로 해결.
   - MySQL 컨테이너의 `caching_sha2_password` 인증 플러그인 + JDBC URL에
     `allowPublicKeyRetrieval=true` 부재로 Flyway 커넥션 단계에서 실패 → `application.yml`
     datasource URL에 `&allowPublicKeyRetrieval=true` 추가로 해결.
   - (2026-07-23 세션에 위 두 개를 고치는 과정에서 새로 발견) 리눅스 컨테이너 MySQL의
     대소문자 구분 기본값과 Hibernate `SpringPhysicalNamingStrategy`의 소문자 정규화가 충돌해
     `Schema-validation: missing table [access_policy_rules]`로 실패 → `mysql` 서비스에
     `command: --lower-case-table-names=1` 추가로 해결.
   세 수정 후 `docker compose up -d`로 5개 컨테이너 전부 `healthy`/`running` 상태 기동,
   `/actuator/health` 200, nginx 리버스 프록시, Prometheus 스크레이프, Grafana datasource
   프로비저닝까지 실제로 재검증했다(§7.2). 검증 후 정리(`docker compose down -v`)와 로컬 native
   MySQL 재기동·데이터 무결성(`PATIENT` 12건) 확인도 마쳤다.

2. **[낮음/설계상 알려짐] `FHIR_RESOURCE_CACHE`와 원본 테이블 간 건수 불일치.** 2026-07-21
   실측: `PATIENT` 12건 vs 캐시 `Patient` 13건, `VISIT` 509건 vs 캐시 `Encounter` 511건
   (`Observation`/`MedicationRequest`/`Condition`은 정확히 일치: 3759/145/295). 이는
   `docs/demos/fhir-conversion.md` §7이 Phase 8.6에서 이미 발견하고 정직하게 문서화한
   내용과 정확히 같은 수치·같은 원인이다 — 캐시가 upsert-only 구조라 원본에서 삭제된
   레거시 row의 캐시 항목을 정리하지 않기 때문(Phase 2 개발/검증 중 생성된 뒤 이후 삭제된
   row로 추정). **숨겨진 버그가 아니라 이미 알려지고 문서화된 설계 gap**이며, 캐시 정리
   메커니즘(예: 소프트 삭제 반영)이 없다는 것은 여전히 유효한 개선 여지다.

### 문서만 stale했던 것 (2026-07-23 갱신 완료)

3. **[해결됨] `docker/README.md`의 "이 머신에서는 WSL2가 없어 미검증" 고지가 stale했다.**
   Docker Desktop과 WSL2는 정상 동작하며, 2026-07-23 세션에 `docker compose up`을 실제로
   실행해 5개 컨테이너가 전부 정상 기동하는 것까지 확인했다(위 버그 3개를 실제로 고친 뒤).
   `docker/README.md`를 "검증 불가"에서 "검증했고 정상 기동한다"로 갱신했다(수정 이력·실측
   검증 항목은 해당 문서 참고).

4. **`PLAN.md`의 마이그레이션 파일 번호와 실제 파일 번호가 다르다.** 예: PLAN.md는 RBAC을
   `V4__*.sql`로, 감사 로그를 `V6__*.sql`로 예고했지만 실제로는 각각 `V9__rbac_access_policy.sql`,
   `V10__audit_log.sql`이다(중간에 `V3`(natural key null safety), `V4`(widen text columns) 등
   계획에 없던 마이그레이션이 추가돼 번호가 밀림). **이것은 거짓 문서가 아니라 PLAN.md가
   "사전 계획" 문서이고 사후 기록 문서가 아니기 때문**(실행 중 필요해진 마이그레이션이
   자연스럽게 끼워짐) — 다만 외부 리뷰어가 PLAN.md의 파일 번호를 곧이곧대로 따라가면 혼동할
   수 있으므로 유의할 것.

### 사소하거나 이미 정직하게 고지된 것

5. **Oracle 19c 분기가 실제로는 Oracle Database Free 23ai(`gvenzl/oracle-free:23-slim`)로
   검증됐다.** 공식 19c 이미지는 OTN 계정 인증이 필요해 자동화 세션에서 받을 수 없었기
   때문이다. `docs/oracle-branch/README.md` §3.1이 이 사실("엄밀히는 19c가 아니다")을 이미
   정직하게 고지하고 있음을 재확인했다 — 문제라기보다는 리뷰어가 "19c"라는 제목만 보고
   오해하지 않도록 알려두는 차원.

6. **Gemini CLI 검증 쿼터 소진 → `flash-lite` 폴백이 잦았다.** 커밋 로그 실측: 전체 45개
   커밋 중 21개가 커밋 메시지에 `TerminalQuotaError`/쿼터 소진을 언급한다. 즉 상당수 Step의
   최종 `VERDICT: PASS`가 최상위 모델(`pro`)이 아니라 `flash-lite`(경량 모델)에서 나왔다.
   이는 검증 엄밀도를 낮출 수 있는 요인이다 — §11(투명성)에서 상술.

7. **원본 확인**: `mysqld` 프로세스가 2개(PID 25912, 29640) 떠 있는 것은 부모/자식 프로세스
   구조로 보이며 이상 동작 신호는 아니다(두 프로세스 모두 이번 세션 시작 전부터 존재, 그대로
   둠).

---

## 11. 문서 색인

| 문서 | 다루는 내용 |
|---|---|
| `README.md`(루트) | 프로젝트 개요, 기술 스택, 아키텍처, 빠른 시작, 채용 매핑 |
| `CLAUDE.md`(루트) | 이 저장소에서 작업할 때 지켜야 하는 정책 — 단순 수치 변경 외 모든 작업은 Gemini CLI 검증 루프를 거쳐야 함, 디자인 작업은 `md/design.md` 우선 확인, 운영 콘솔 레이아웃은 `docs/design/reference-layout-mediflow-emr.png` 기준 |
| `docs/planning/deliverable.md` | 최초 제안서 — 왜 이 포트폴리오인가, 기술 스택 선택 이유, 설계서 초안, P0/P1/P2 우선순위 정의 |
| `docs/planning/final_summary.md` | (제안서에 대한) 3자(사용자+Claude+Gemini로 추정) 합의 기록 |
| `docs/planning/PLAN.md` | 실행 계획 — Phase 0~10을 step 단위로 쪼갠 실행 순서와 각 step의 acceptance criteria(사전 계획 문서, 실제 파일 번호와는 다를 수 있음, §10-4 참고) |
| `md/gemini.md` | Gemini CLI 기반 적대적 검증 파이프라인 — 호출 방식, 프롬프트 템플릿, 쿼터 소진 시 대체 경로(§0-2 Phase 배칭, §0-3 Claude subagent 대체 검증) |
| `md/design.md` | UI 디자인 시스템(컬러·타이포·컴포넌트 규칙, 배지/라벨 스타일 등) |
| `docs/local-dev-mysql.md` | Docker 없이 로컬 native MySQL을 직접 설치·기동하는 경로 |
| `docs/oracle-branch/` | Oracle 19c(실제로는 23ai Free) 분기 DDL·방언 차이·MV 실기동 검증 |
| `docs/incidents/`, `docs/runbooks/`, `docs/tuning/`, `docs/demos/` | P0 운영 증빙 6종 원본(§9 표 참고) |

---

## 12. 검증 이력에 대한 투명성 — 왜 "이미 검증됐다"고 안이하게 넘기면 안 되는가

이 프로젝트의 **모든 구현 Step은 이미 Gemini CLI 기반 적대적 검증(`md/gemini.md`)을
거쳐 `VERDICT: PASS`를 받은 뒤 커밋됐다** — CLAUDE.md가 이를 강제하는 정책이고, 커밋 로그
대부분이 "gemini 검증: ... VERDICT: PASS"를 커밋 메시지에 남기고 있다(예: `585115b`, `f02580e`
등 다수 확인). 그러나 이 검증에는 아래와 같은 **구조적 한계**가 있고, 외부 AI 리뷰어는 이를
감안해 독립적으로 판단해야 한다:

1. **구현자(Claude)가 검증 기준(acceptance criteria)도 함께 작성한다.** `gemini.md` §0-1이
   이 문제를 인지하고 "적대적 프롬프트"를 쓰라고 명시하지만, 애초에 Claude가 "무엇을 검증할지"
   기준 자체를 정하므로 Claude의 맹점이 기준 설계 단계에서 이미 반영될 수 있다.
2. **Gemini 쿼터 소진으로 `flash-lite`(경량 모델) 폴백이 잦았다.** §10-6에서 실측했듯
   전체 45개 커밋 중 21개가 쿼터 소진을 언급한다 — 즉 다수의 `VERDICT: PASS`가 최상위 모델의
   판정이 아니라 경량 모델의 판정이었을 가능성이 높다. 경량 모델은 상대적으로 얕은 검증을
   내놓을 위험이 있다.
3. **`gemini.md` §0-3의 "Claude subagent 대체 검증" 경로도 쓰였다** — 이 경우 검증자가
   Gemini(교차 모델)조차 아니라 **같은 모델 계열(Claude)의 다른 인스턴스**였다는 뜻이다.
   문서 자체가 "같은 모델이 같은 모델을 검증하는 것이라 교차 모델 검증보다 약하다"고 인정하고
   있다.
4. **이번 재검토 세션 자체도 같은 한계 안에 있다.** 이 브리핑 문서를 작성한 것도 Claude이므로,
   여기 적힌 "발견한 문제들" 역시 Claude의 시야 밖에 있는 문제는 담지 못했을 수 있다(다만 §7.2의
   Docker 버그와 §10의 항목들은 실제 명령 실행·코드 읽기로 확인한 것이며 상상이 아니다).

**결론**: `VERDICT: PASS` 이력이 많다는 것이 "이미 충분히 검증됐다"는 뜻으로 읽혀서는 안
된다. 특히 (a) `flash-lite`/Claude-subagent로 대체 검증된 Step, (b) Docker Compose 경로처럼
"실제 문서화된 실행 경로 중 한 번도 정상 기동까지 실측되지 않았던" 부분(§10-1)은 외부 리뷰어의
독립적 재검증이 특히 가치 있는 지점이다.

---

## 부록: 이번 재검토 세션에서 실행한 주요 명령 (재현 가능성 확보)

```powershell
git log --oneline --all
cd app; .\gradlew.bat test
mysql -u hospital_ops -pchangeme hospital_ops -e "SELECT ... FROM PATIENT/VISIT/LAB_RESULT/PRESCRIPTION/DIAGNOSIS/AUDIT_LOG/FHIR_RESOURCE_CACHE/PATIENT_VISIT_SUMMARY"
mysql -u hospital_ops -pchangeme hospital_ops -e "SELECT * FROM flyway_schema_history"
mysql -u hospital_ops -pchangeme hospital_ops -e "SELECT * FROM access_policy_rules"
docker info; docker compose up -d; docker compose ps; docker logs hospital-ops-app; docker compose down -v
```

세션 종료 시 정리: Docker 컨테이너/볼륨 전량 제거(`docker compose down -v`), `docker-compose.yml`/
`docker/.env`의 임시 변경 원상복구(git diff 없음 확인), 이번 세션이 띄운 gradle 프로세스 외
추가로 띄운 애플리케이션 프로세스 없음, 세션 시작 전부터 떠 있던 native `mysqld`는 그대로 둠,
`PATIENT` 등 정본 테이블 row 수는 세션 전후 12건으로 불변 확인.
