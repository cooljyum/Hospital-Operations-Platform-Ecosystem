# Hospital FHIR Ops Lab

합성 병원 데이터를 FHIR R4로 연계하는 미니 시스템. 기능보다 **운영 증빙**(장애 대응 로그북,
SQL 튜닝, 백업·복구 리허설, 감사 로그)이 핵심인 병원 전산직 지원용 포트폴리오다. 실환자 데이터는
전혀 사용하지 않으며, 모든 데이터는 [Synthea](https://github.com/synthetichealth/synthea)로
생성한 합성 환자다.

> "개발을 해봤다"가 아니라 **"시스템을 운영해 봤다"를 증명**하는 구성 — 자세한 기획 배경은
> [`docs/planning/deliverable.md`](docs/planning/deliverable.md) 참조.

## 기술 스택

| 계층 | 스택 |
|---|---|
| 백엔드 | Java 17 + Spring Boot 3.x, Spring Batch, Spring Data JPA, Spring Security(폼·세션 로그인 + RBAC) |
| FHIR | HAPI FHIR 구조체 라이브러리(전체 서버 없이 R4 리소스 매핑만) |
| DB | MySQL 8.x(기본/정본) + Oracle 19c 분기 문서([`docs/oracle-branch/`](docs/oracle-branch/)) |
| 프론트 | Thymeleaf + AdminLTE |
| 인프라 | Docker Compose + Nginx |
| 관측 | Prometheus + Micrometer(Actuator) + Grafana(대시보드·알림) |
| 마이그레이션·테스트 | Flyway, JUnit 5 |
| 데이터 | Synthea 합성 환자 데이터 생성기 |

## 아키텍처 개요

```
[합성 레거시 HIS DB (MySQL)]  ← 정본. Synthea 산출 데이터 적재
        │  Spring Batch: 워터마크 기반 증분 pull + 멱등 upsert (CDC/실시간 배제)
        ▼
[FHIR_RESOURCE_CACHE]  ← Patient·Encounter·Observation·MedicationRequest·Condition (5종)
        │  HAPI FHIR 구조체로 매핑, 조회 전용 REST API
        ▼
[Thymeleaf + AdminLTE 화면]  ← RBAC 5역할, break-glass 응급 접근, 감사 로그
```

핵심 설계 원칙:

- **식별자**: 불변 내부 PK만 사용. 주민번호(RRN) 기반 식별자·주기적 SALT 순환은 배제(감사 추적성 보전).
- **동기화**: 배치 pull(워터마크 + 멱등 upsert)만 사용, CDC·실시간 연동 배제.
- **응급 접근**: break-glass 경로는 사유 필수 입력 + 감사 기록 + Grafana 알림 연동.
- **감사 로그**: 원문 쿼리 문자열은 저장하지 않고 파라미터화된 템플릿만 기록.
- **비식별·암호화(심화형)**: HMAC-SHA-256 가명화, AES-256-GCM 엔벨로프 암호화(+키 회전 runbook), date shifting, k=5 익명성 + small-cell suppression, 대량 복호 3단계(PENDING/APPROVED/REJECTED) 승인 흐름.

## 프로젝트 구조

| 경로 | 내용 |
|---|---|
| `/app` | Spring Boot 3.x + Java 17 애플리케이션(Gradle) |
| `/docker` | Docker Compose 기반 로컬 인프라(MySQL, app, Prometheus, Grafana, Nginx) — [`docker/README.md`](docker/README.md) |
| `/docs/planning` | 기획 문서 — [`deliverable.md`](docs/planning/deliverable.md)(제안서), [`final_summary.md`](docs/planning/final_summary.md)(3자 합의), [`PLAN.md`](docs/planning/PLAN.md)(실행 계획, Phase 0~10 전체) |
| `/docs/agents` | Gemini CLI 기반 적대적 검증 파이프라인 — [`gemini.md`](docs/agents/gemini.md) |
| `/docs/incidents` | 장애 시나리오 재현·대응 로그북 (P0) |
| `/docs/runbooks` | 배치 재처리·백업복구·키 회전 runbook (P0) |
| `/docs/tuning` | 슬로우 쿼리 튜닝 전후 기록 (P0) |
| `/docs/demos` | 감사 로그 화면·FHIR 변환 데모 (P0) |
| `/docs/oracle-branch` | Oracle 19c 분기 설계 + 실기동 검증 (P2) |
| `/docs/DESIGN.md` | UI 디자인 시스템(컬러·타이포·컴포넌트 규칙) |
| `/scripts` | 합성 데이터 생성, 로컬 MySQL 기동 등 운영 스크립트 |

## 빠른 시작

```bash
cp docker/.env.example docker/.env   # 실제 값으로 채운 뒤 사용
cd docker
docker compose up -d
```

세부 서비스 구성·헬스체크는 [`docker/README.md`](docker/README.md) 참조. Docker/WSL2가 준비되지
않은 환경에서는 [`docs/local-dev-mysql.md`](docs/local-dev-mysql.md)의 로컬 MySQL 직접 설치
경로로 대체할 수 있다.

RBAC 5역할(`ROLE_PHYSICIAN`/`ROLE_NURSE`/`ROLE_REGISTRAR`/`ROLE_SYSTEM_ADMIN`/`ROLE_AUDITOR`)
계정으로 로그인해 역할별 화면 접근 차이를 확인할 수 있다 — 감사 로그 조회(`/audit/preview`)·
요약 통계(`/reports/patient-visit-summary`, `/stats/**`)는 감사자·전산관리자 전용이다.

## 운영 증빙 산출물

이 포트폴리오의 실질 목표는 기능 구현이 아니라 아래 **P0 6종**이다(완료 시 "완성"으로 간주,
[`docs/planning/deliverable.md`](docs/planning/deliverable.md) §3.4 정의).

| # | 산출물 | 문서 |
|---|---|---|
| P0-1 | 장애 시나리오 재현·대응 로그북 (DB 커넥션 고갈·배치 실패·디스크 임계·MySQL 다운 4종) | [`docs/incidents/`](docs/incidents/) |
| P0-2 | 배치 실패 → 재처리 runbook | [`docs/runbooks/batch-retry.md`](docs/runbooks/batch-retry.md) |
| P0-3 | 슬로우 쿼리 튜닝 전후 기록(실행계획 비교) | [`docs/tuning/`](docs/tuning/) |
| P0-4 | 백업·복구 리허설(복구 소요시간 실측) | [`docs/runbooks/backup-restore.md`](docs/runbooks/backup-restore.md) |
| P0-5 | 감사 로그 조회 화면 시연 | [`docs/demos/audit-log-screen.md`](docs/demos/audit-log-screen.md) |
| P0-6 | FHIR 변환 데모 | [`docs/demos/fhir-conversion.md`](docs/demos/fhir-conversion.md) |

P1(여유 산출물): 요약 테이블 리포팅 화면(`/reports/patient-visit-summary`), 비식별 통계 화면
(`/stats/**`, k=5). P2(여유 산출물): [Oracle 19c 분기 문서](docs/oracle-branch/), FHIR 리소스
확장(Condition 추가로 5종). 전체 Phase별 진행 내역은 [`docs/planning/PLAN.md`](docs/planning/PLAN.md)
참조.

## 채용 요건 ↔ 증빙 매핑

| 채용에서 실제로 보는 것 | 이 포트폴리오의 증빙 |
|---|---|
| SQL(Oracle/MySQL 택1) | 튜닝 전후 기록·실행계획 비교, Oracle 분기 문서 |
| Java 계열 역량 | Spring Boot 서비스 전체 |
| EMR/HIS 운영·유지보수 감각 | 장애 로그북·재처리 runbook·백업 리허설 |
| 최신 트렌드 이해 | FHIR R4 변환 데모 |
| 개인정보·보안 의식 | RBAC·응급 예외(break-glass)·감사로그·비식별 설계 |

## 검증 방식

모든 비-사소 변경은 `CLAUDE.md`가 정의한 정책에 따라 Gemini CLI 기반 적대적 검증 루프
([`docs/agents/gemini.md`](docs/agents/gemini.md))를 거쳐 `VERDICT: PASS`를 받은 뒤에만
완료로 간주했다. 각 Step은 실제 환경(로컬 MySQL, Docker Compose, 실제 HTTP 호출)에서 재현한
raw 증거(로그·쿼리 결과·EXPLAIN PLAN·스크린샷)를 근거로 문서화했다.
