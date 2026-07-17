# 프로젝트 완성 계획 — Hospital FHIR Ops Lab

> `deliverable.md`(설계서) + `final_summary.md`(3자 합의)에 입각한 실행 계획.
> 현재 저장소는 문서만 있고 코드는 전무한 상태(백지)에서 시작한다.

---

## 0. 이번 계획에서 확정한 선택 옵션

deliverable.md §4가 열어뒀던 옵션 중 두 가지를 이번에 확정했다.

| 옵션 | 확정 | 근거 |
|---|---|---|
| 프론트엔드 | **Thymeleaf + AdminLTE** | Gemini CLI에 `deliverable.md`/`final_summary.md` 기반으로 비교를 요청한 결과, "P0(운영 증빙) 완성도·1인 개발 리스크·채용 어필" 세 기준 모두에서 우위로 추천(`RECOMMENDATION: Thymeleaf`). final_summary.md의 gemini 안과도 일치. |
| 보안·비식별 구현 깊이 | **심화형** (사용자 지정) | HMAC-SHA-256 가명화, AES-256-GCM 엔벨로프 암호화(+키 회전 runbook), date shifting, k=5+small-cell suppression, 3단계(PENDING/APPROVED/REJECTED) 승인 상태머신 — claude 안 기준을 채택. |

**미확정으로 남겨둔 것** (P2 시점에 재결정, 지금 막지 않음):
- Oracle 19c 분기의 조회 최적화 — MV 유지(gemini 안) vs 요약 테이블 통일(claude 안). MySQL 단일 구현이 끝난 뒤 Oracle 분기 착수 시점에 결정.
- k=5의 과억제 우려(gemini 제기) — blocking 아님, 일단 k=5로 구현하고 실데이터로 억제율 확인 후 조정 여부 판단.

---

## 1. 실행 원칙

- 이 저장소의 `CLAUDE.md`(루트) 규칙에 따라, **단순 수치 변경이 아닌 모든 구현 작업은 `../agents/gemini.md`의 검증 루프**(step 구현 → Gemini CLI에 acceptance criteria로 검증 위임 → `VERDICT: PASS` 확인 → 대상 파일만 커밋)를 따른다.
- 아래 Phase/Step은 그 루프에 넣을 **step 단위**로 이미 쪼개져 있다. 실행 시 각 step을 `../agents/gemini.md` §4c의 subagent 위임 템플릿에 그대로 넣을 수 있게 "대상 파일"과 "acceptance criteria"를 명시했다.
- 순서는 **의존성 순**이다 — 상위 Phase 없이는 하위 Phase가 성립하지 않는다(예: 배치·FHIR 변환 전에는 감사 로그 화면을 붙일 대상이 없음).
- deliverable.md의 P0(필수 6종)를 완성하는 것이 이 프로젝트의 실질적 "완성" 기준이다. P1·P2는 여유가 있을 때 추가.

---

## 2. Phase 0 — 부트스트랩

**목표**: 빈 저장소에서 "실행되는 빈 배" 상태까지.

| Step | 내용 | 대상 파일/디렉터리 | Acceptance criteria |
|---|---|---|---|
| 0.1 | 저장소 구조 설계 (`/app`(Spring Boot), `/docker`, `/docs`, `/scripts`) | 루트 디렉터리 구조 | 각 디렉터리 존재, README에 구조 설명 |
| 0.2 | Spring Boot 3.x + Java 17 스캐폴딩 (Gradle), 의존성: Spring Web, Thymeleaf, Spring Security, Spring Batch, Spring Data JPA, Flyway, HAPI FHIR 구조체 라이브러리, JUnit5 | `/app/build.gradle*`, `/app/src/main/java/...` | `./gradlew build` 성공, `/app/src/main/resources/application.yml` 존재 |
| 0.3 | AdminLTE 정적 자산 통합 (Thymeleaf 레이아웃 프래그먼트) | `/app/src/main/resources/templates`, `/static` | 로그인 없이 접근 시 302, 더미 페이지 렌더 확인 |
| 0.4 | Docker Compose 골격: MySQL 8.x, app, Prometheus, Grafana, Nginx | `/docker/docker-compose.yml` | `docker compose up`으로 MySQL·app 컨테이너 기동, app이 MySQL에 연결 성공(헬스체크 200) |
| 0.5 | Synthea 합성 데이터 생성기 도입 확인 (출력 포맷·라이선스 점검, 소량 생성 시연) | `/scripts/gen-synthetic-data.*`, `/docs/synthea-notes.md` | Synthea 실행 산출물(FHIR bundle 또는 CSV)이 `/data/synthetic/`에 생성됨 |

---

## 3. Phase 1 — 레거시 HIS 스키마 + 데이터 정본

**목표**: "가짜 병원 DB"를 실제로 세운다.

| Step | 내용 | 대상 파일 | Acceptance criteria |
|---|---|---|---|
| 1.1 | Flyway 마이그레이션: `PATIENT`/`VISIT`/`LAB_RESULT`/`PRESCRIPTION` + 불변 내부 PK(`patient_id` 등) 설계 | `/app/src/main/resources/db/migration/V1__*.sql` | `flyway migrate` 성공, 4개 테이블 존재, PK가 auto-increment 또는 UUID(변경 불가)로 정의됨 |
| 1.2 | 표시용 식별자 컬럼 분리: `synthetic_patient_no`(내부 PK와 별도) + 매핑 테이블 | `V2__*.sql` | RRN 계열 컬럼이 스키마 어디에도 존재하지 않음(grep으로 기계 검증 가능) |
| 1.3 | Synthea 산출 데이터 → 레거시 스키마 적재 배치/스크립트 | `/app/.../batch/SyntheaLoader*.java` | 적재 후 4개 테이블에 row 존재, 재실행해도 중복 없음(멱등) |

---

## 4. Phase 2 — 배치 동기화 & FHIR 변환

**목표**: 레거시 DB → FHIR R4 리소스로 연계.

| Step | 내용 | 대상 파일 | Acceptance criteria |
|---|---|---|---|
| 2.1 | Spring Batch: 워터마크 기반 증분 pull + 멱등 upsert job | `/app/.../batch/SyncJob*.java` | job 2회 연속 실행 시 2회차에 신규 upsert 0건(멱등 확인), 워터마크 테이블에 마지막 실행 시각 기록 |
| 2.2 | 로컬 코드셋 테이블(코드 값 고정) | `V3__*.sql` + 시드 데이터 | 코드셋 테이블 존재, 변환 로직이 하드코딩 대신 이 테이블 참조 |
| 2.3 | FHIR 변환 계층: Patient/Encounter/Observation/MedicationRequest 4종 매핑 | `/app/.../fhir/*Mapper.java` | 4개 리소스 타입 각각 단위테스트로 매핑 필드 검증, HAPI FHIR validator 통과 |
| 2.4 | REST API: FHIR read/search 최소 프로파일 (변환 계층 경유, DB 직접 노출 금지) | `/app/.../api/FhirController*.java` | `GET /fhir/Patient/{id}` 등 4개 리소스 조회 가능, 컨트롤러가 레거시 repository를 직접 참조하지 않음(변환 계층만 참조) |

---

## 5. Phase 3 — 인증/인가/RBAC

| Step | 내용 | 대상 파일 | Acceptance criteria |
|---|---|---|---|
| 3.1 | Spring Security 폼+세션 로그인 | `/app/.../security/SecurityConfig.java` | 미인증 접근 시 로그인 페이지 리다이렉트, 로그인 성공 시 세션 발급 |
| 3.2 | RBAC 5역할 + `access_policy_rules` 테이블 | `V4__*.sql`, `/app/.../security/*` | 5역할 각각 접근 가능 화면이 다름을 통합테스트로 검증 |
| 3.3 | Break-glass 응급 접근 경로(사유 입력 → 접근 허용 → 감사 기록 → 알림 훅) | `/app/.../breakglass/*` | break-glass 경로로 접근 시 AUDIT_LOG에 전용 결과 코드로 기록됨 |

---

## 6. Phase 4 — 심화형 보안·비식별

| Step | 내용 | 대상 파일 | Acceptance criteria |
|---|---|---|---|
| 4.1 | HMAC-SHA-256 가명화(표시·외부 노출용, 내부는 불변 PK 유지) | `/app/.../crypto/Pseudonymizer.java` | 동일 입력 → 동일 해시(결정론적), 원본 PK는 응답 어디에도 노출되지 않음 |
| 4.2 | AES-256-GCM 엔벨로프 암호화 (KEK=Docker secret, DEK 래핑) + 키 회전 runbook | `/app/.../crypto/EnvelopeCrypto.java`, `/docs/runbooks/key-rotation.md` | 암호화→복호화 라운드트립 테스트 통과, KEK 없이는 기동 실패, runbook 문서에 회전 절차 단계별 명시 |
| 4.3 | Date shifting (환자별 고정 오프셋) | `/app/.../crypto/DateShifter.java` | 같은 환자의 모든 날짜 필드가 동일 오프셋만큼 이동, 환자 간 오프셋은 상이 |
| 4.4 | k=5 익명성 + small-cell suppression 통계 뷰 | `/app/.../stats/*` | 셀 값 <5인 통계는 화면에 억제 표시로 나옴(원값 노출 없음) |
| 4.5 | 대량 복호 3단계 승인 상태머신(PENDING/APPROVED/REJECTED) + 건당 AuditEvent | `V5__*.sql`, `/app/.../approval/*` | 상태 전이가 PENDING→APPROVED/REJECTED로만 가능(역행 불가), 각 전이마다 AuditEvent 1건 생성 |

---

## 7. Phase 5 — 감사 로그

| Step | 내용 | 대상 파일 | Acceptance criteria |
|---|---|---|---|
| 5.1 | AUDIT_LOG 스키마(행위자/대상PK/행위/목적/마스킹여부/IP/성공여부/시각) — 쿼리 원문 미저장, 파라미터 바인딩 템플릿만 기록 | `V6__*.sql` | 감사 로그 어떤 row에도 SQL 리터럴 값(개인정보 문자열)이 저장되지 않음(템플릿 문자열만) |
| 5.2 | 감사 로그 조회 화면(감사자 전용, Thymeleaf) | `/app/.../templates/audit/*.html` | 감사자 role로만 접근 가능, 필터(행위자/기간/대상) 동작 |

---

## 8. Phase 6 — 조회 최적화 (MySQL)

| Step | 내용 | 대상 파일 | Acceptance criteria |
|---|---|---|---|
| 6.1 | 물리 요약 테이블 + Spring Batch refresh job | `V7__*.sql`, `/app/.../batch/SummaryRefreshJob.java` | refresh job 실행 후 요약 테이블 값이 원본 집계와 일치 |

*(Oracle 분기는 §0의 미확정 사항 — Phase 10에서 재결정 후 착수)*

---

## 9. Phase 7 — 관측성

| Step | 내용 | 대상 파일 | Acceptance criteria |
|---|---|---|---|
| 7.1 | Actuator + Micrometer 메트릭 노출 | `/app/.../application.yml` | `/actuator/prometheus` 200 응답, 커스텀 메트릭(배치 성공/실패 카운트 등) 노출 |
| 7.2 | Grafana 대시보드(배치 상태·장애 알림·break-glass 알림) | `/docker/grafana/dashboards/*.json` | Grafana에 대시보드 3종 이상 로드됨, break-glass 발생 시 알림 규칙 트리거 확인 |

---

## 10. Phase 8 — 운영 증빙 (P0 필수 6종, 이 포트폴리오의 실질 목표)

> Phase 1~7이 "증빙할 시스템"을 만드는 과정이라면, Phase 8은 deliverable.md가 명시한 **차별화 코어** — 실제로 장애를 내고 기록한다.

| Step | 내용 | 산출물 위치 | Acceptance criteria |
|---|---|---|---|
| 8.1 | 장애 시나리오 재현·대응 로그북 (DB 커넥션 고갈·배치 실패·디스크 임계 등 4종 내외, Grafana 알림 연동) | `/docs/incidents/*.md` | 4종 각각 재현 절차·증상·Grafana 알림 스크린샷·대응 조치·복구 시각이 기록됨 |
| 8.2 | 배치 실패 → 재처리 runbook | `/docs/runbooks/batch-retry.md` | 실제 배치 강제 실패 후 runbook대로 재처리해 성공까지 재현 |
| 8.3 | 슬로우 쿼리 튜닝 전후 기록(실행계획 비교 1건 이상) | `/docs/tuning/*.md` | EXPLAIN 결과 전/후 비교, 개선폭(ms 또는 cost) 수치 명시 |
| 8.4 | 백업·복구 리허설(복구 소요시간 실측) | `/docs/runbooks/backup-restore.md` | 실제 백업→의도적 데이터 손상→복구 실행, 소요시간 실측치 기록 |
| 8.5 | 감사 로그 조회 화면 시연 자료 | Phase 5.2 산출물 참조 | 스크린샷/데모 스크립트로 문서화 |
| 8.6 | FHIR 변환 데모 | Phase 2.3/2.4 산출물 참조 | 레거시 row → FHIR JSON 변환 결과 샘플을 문서에 첨부 |

**이 6종이 모두 채워지면 "완성"으로 간주한다** (deliverable.md 정의).

---

## 11. Phase 9 — P1 (여유 시 진행)

| Step | 내용 | Acceptance criteria |
|---|---|---|
| 9.1 | 요약 테이블 리포팅 화면 | Phase 6.1 데이터 기반 화면 렌더 |
| 9.2 | 비식별 통계 화면 | Phase 4.4 k=5 통계를 화면으로 노출 |

## 12. Phase 10 — P2 (여유 시 진행)

| Step | 내용 | 비고 |
|---|---|---|
| 10.1 | Oracle 19c 분기 문서/구현 | 착수 시점에 MV vs 요약테이블 재결정(§0) |
| 10.2 | FHIR 리소스 확장 | 4종 외 추가 리소스 |

---

## 13. 진행 방식 요약

1. Phase 순서대로 진행하되, 같은 Phase 내 step은 병렬 설계 가능해도 **실행은 순차**(CLAUDE.md/../agents/gemini.md 정책상 subagent 병렬 투입 금지).
2. 각 step 완료 시 `../agents/gemini.md` 루프로 PASS 받은 뒤에만 다음 step으로 진행, step 대상 파일만 커밋.
3. Phase 8(P0 운영 증빙)이 끝나야 "포트폴리오 완성"이며, 그 전까지 P1/P2 착수는 없음.
4. Oracle MV 여부·k=5 조정 등 미확정 사항은 해당 Phase 도달 시점에 재논의(필요하면 다시 Gemini CLI에 비교 요청).
