# 사회자 최종 요약 v2 (왜곡 검증 정정 반영) — 병원 전산 관리직 포트폴리오

r8 왜곡 검증에서 codex(4건)·claude(1건)의 정정을 받아 재구조화했다: **"전원 합의 코어"와
"참가자별 확정 변형"을 분리**한다. r7 기준 전원 position=FHIR-EMR-INTEGRATION-LAB
(0.90/0.88/1.0), changed=false 3/3, blocking 0, U1~U3 covered.

## A. 전원 합의 코어 (3자 일치 확인분만)

1. **라벨: FHIR-EMR-INTEGRATION-LAB** — "합성 병원 데이터 기반 FHIR 연계 + 운영 증빙 랩".
2. **프로젝트 정체성**: 합성 레거시 HIS DB 정본 + 제한된 FHIR R4 리소스(Patient·Encounter·
   Observation·MedicationRequest 중심 4~5종) 변환·조회 + **운영·유지보수 증빙을 1급 산출물로**
   (장애 재현, 배치 재처리, SQL 튜닝 기록, 백업·복구 리허설, 감사 화면). 실환자 데이터 금지,
   1인 완성 범위(제외 목록 명시), 산출물 P0/P1/P2 우선순위화.
3. **스택 공통 코어**: Java 17 + Spring Boot 3.x / **단일 DB — 기본 MySQL 8.x, 대형병원 지원
   시 Oracle 19c 분기 문서** / Docker Compose / Prometheus + Grafana / Spring Security
   폼·세션 로그인 + RBAC(Keycloak 제거, OIDC 표현 삭제) / 합성 데이터(Synthea 등).
   K8s·AWS·MongoDB·Loki·OTel은 전원 제외.
4. **설계 공통 원칙**: 동기화=배치 pull(워터마크+멱등 upsert), CDC·실시간 배제 / 식별자는
   **불변 내부 PK**를 기준으로 하고 SALT 정기 순환은 배제(감사 연결 보전) / RRN(주민번호)
   기반 설계 폐기 / 응급 조회는 break-glass 경로 필요(구현 방식은 변형) / 감사 로그에 조회
   쿼리 "원문"은 저장하지 않는다.
5. **기각**: OPENEHR-CLINICAL-CORE, CLOUD-NATIVE-EMR-SANDBOX (전원 cannot_accept).

## B. 참가자별 확정 변형 (비차단 — 각자의 최종안에서만 확정된 것)

- **codex 안**: React 18+TS5 프론트. 표시용 식별자는 `synthetic_patient_no`(해시 미사용).
  대량 조회·내보내기는 **차단 또는 감사 로그 대상으로만 시연**(승인 상태머신 미채택).
  `access_policy_rules` + BREAK_GLASS 사유(건당 감사 기록·Grafana 알림·사후 감사).
  비식별·암호화 세부(HMAC/AES/date shifting/k-익명성)는 자기 P0에 확정하지 않음.
- **claude 안**: React+TS 프론트. 비식별 패키지 확정 — HMAC-SHA-256 가명화, AES-256-GCM +
  엔벨로프 암호화(KEK Docker secret, 기본 회전=KEK 교체+DEK 재래핑, 비상=전체 재암호화
  runbook), date shifting(환자별 고정 오프셋), k=5 + small-cell suppression. 대량 복호는
  **3상태(PENDING/APPROVED/REJECTED) 승인 상태머신** + 건당 AuditEvent. 조회 최적화는
  **양 분기(MySQL·Oracle) 모두 물리 요약 테이블 + Spring Batch refresh, 네이티브 MV 전면
  배제**.
- **gemini 안**: Thymeleaf + AdminLTE 프론트. 조회 최적화는 MySQL 분기=요약 테이블+Spring
  Batch, **Oracle 분기=MV 유지**. 해시는 표시·외부 노출용으로만(내부는 불변 PK), 교체 시
  매핑 테이블로 감사 이력 보전. AUDIT_LOG에 purpose·is_masked 포함, 쿼리는 파라미터 바인딩
  템플릿만 기록.

## C. 명시 잔여 이견 (전원 비차단 — blocking 0)

1. **Oracle 분기의 MV 사용 여부**: gemini 유지 vs claude 전면 배제 (MySQL 분기는 요약
   테이블로 일치).
2. **프론트 스택**: React+TS(codex·claude) vs Thymeleaf+AdminLTE(gemini).
3. k=5 값: claude 유지(근거 보강), gemini는 과억제 우려 제기했으나 blocking 아님.

## D. 진행 기록

r2·r4·r6 반박이 과대 설계(스택 15종+·DB 병행), 직무 부정합(개발자 편중), RRN 위험,
MV-MySQL 비정합, SALT 순환 모순을 잡았고 r3·r5·r7에서 해소. r4에서 3인 만장일치로 "포지션
3분할=라벨 해석 차이" 판정 후 r5에서 단일 라벨 정렬.
