# MediFlow EMR 재구축 체크리스트 — propose.md 기능 완성 + draft.png 구조 일치

> 이 문서는 실행 중 진행 상황을 추적하는 살아있는 체크리스트다. 원본 계획 승인 기록은
> Claude 세션의 plan 파일(`hashed-juggling-gray.md`, 로컬 전용, 저장소 밖)에 있었고, 이 문서가
> 저장소에 커밋되는 정본이다. 각 체크박스는 `md/work.md` Codex 위임 파이프라인의 step 1개
> 단위다 — codex 세션 하나(정찰→지시→구현→판정→커밋)로 완료한다.

## Context

이 프로젝트는 이미 `md/propose.md`의 원안 기능(운영 콘솔: FHIR 변환, RBAC, break-glass, 감사
로그, 배치, 통계, Oracle 분기)을 대부분 구현 완료한 상태다. 하지만 UI는 여전히 AdminLTE
뼈대뿐이고 `md/design.md`(Notion 스타일)와 `docs/draft.png`(= `docs/design/reference-layout-mediflow-emr.png`,
MediFlow EMR 목업)는 전혀 적용되지 않았다. 사용자는 draft.png의 전체 임상 메뉴(접수/예약/진료실/
간호/검사/수납/입원/보험청구)를 실제 백엔드가 뒷받침하는 진짜 화면으로 재현하고, AI Assistant
패널(SOAP 자동생성·진단코드 추천·처방 추천·예약 예측·음성 진료 기록)도 실제 Claude API
호출로 구현하기로 결정했다.

**완료 기준**: 아래 체크리스트를 전부 완료하면 (a) propose.md에 있는 모든 것이 실제로 동작하고,
(b) draft.png와 레이아웃 구조가 "비슷"이 아니라 "일치"해야 한다.

**확정된 판단 사항**:
- 데이터 모델: 기존 `VISIT`/`LAB_RESULT`/`PRESCRIPTION`/`DIAGNOSIS`는 Synthea 배치 적재
  전용으로 그대로 두고, 새 `ENCOUNTER_SESSION` 테이블이 접수·진료실·간호·검사·수납·입원·
  보험청구의 공통 앵커가 된다(Phase 13.1에서 codex 정찰로 재확인 후 확정).
- 설정 화면: 읽기 전용 운영 대시보드.
- 음성 진료 기록(STT): 포함 — 브라우저 Web Speech API(서버측 STT 연동 없음).
- AI 모델: `claude-sonnet-5`, Java SDK `com.anthropic:anthropic-java`, 순수 `messages.create()`.

전체 배경·RBAC 표·구조 일치 검증 기준 14개 항목의 상세 서술은 최초 승인된 plan 내용을 아래에
그대로 보존한다.

**2026-07-26 추가**: `docs/design/design_guide.md`가 새로 확보됐다 — `docs/draft.png`를
1672×941px 기준으로 픽셀 단위 실측 분석한 완전한 스펙(정확한 grid 좌표, 완성된 CSS 전문,
권장 HTML 구조, 이미지 오버레이 검수법 포함). **이 문서의 색상·spacing·radius·좌표 값이
`md/design.md`의 대응 값보다 우선한다** — primary `#1671e2`(문서작성 시점의 `md/design.md`
`#0075de`가 아님), canvas `#ffffff`, header `#fbfbfb`, sidebar `#fafafa`, border `#e8eaed` 등.
`md/design.md`는 배지(중립 배경+텍스트/점, 채움 칩 금지)·이모지 금지 같은 질적 원칙만 계속
유효. Phase 11의 셸 구현은 `docs/design/design_guide.md` §2(HTML 구조)·§3(CSS)를 출발점으로
삼고, §7의 이미지 오버레이 방법으로 검수한다.

---

## RBAC 매핑 (기본값, 구현 중 조정 가능)

| URL 패턴 | 허용 역할 |
|---|---|
| `/dashboard` (기존) | 전 5역할 |
| `/audit/preview` (기존) | ROLE_AUDITOR |
| `/reports/patient-visit-summary` (기존) | ROLE_AUDITOR, ROLE_SYSTEM_ADMIN |
| `/stats/**` (기존) | ROLE_AUDITOR, ROLE_SYSTEM_ADMIN |
| `/admin/system` (기존, 설정으로 확장) | ROLE_SYSTEM_ADMIN |
| `/reception/**` (접수) | ROLE_REGISTRAR(쓰기), ROLE_NURSE(읽기) |
| `/appointments/**` (예약) | ROLE_REGISTRAR(쓰기), ROLE_PHYSICIAN/ROLE_NURSE(읽기) |
| `/exam-room/**` (진료실) | ROLE_PHYSICIAN(쓰기), ROLE_NURSE(읽기) |
| `/nursing/**` (간호) | ROLE_NURSE(쓰기), ROLE_PHYSICIAN(읽기) |
| `/lab-orders/**` (검사) | ROLE_PHYSICIAN(오더), ROLE_NURSE(기록/조회) |
| `/payments/**` (수납) | ROLE_REGISTRAR, ROLE_SYSTEM_ADMIN |
| `/admissions/**` (입원) | ROLE_PHYSICIAN(입원결정), ROLE_NURSE(병동관리), ROLE_REGISTRAR(행정) |
| `/insurance-claims/**` (보험청구) | ROLE_REGISTRAR, ROLE_SYSTEM_ADMIN |
| `/ai/**` (AI 제안 엔드포인트) | ROLE_PHYSICIAN만 |

## 구조적 일치 검증 기준 (Phase 16, "비슷 아니고 일치" 판정용)

1. 헤더: 전체 폭, 흰 배경, 하단 헤어라인
2. 헤더 좌측: 로고+워드마크
3. 헤더 중앙: 검색 입력 + 단축키 힌트(⌘K 등)
4. 헤더 우측(좌→우): 요약 pill 버튼 / 카운트 배지 있는 벨 아이콘 / 다크모드 토글 / 아바타+이름+직함
5. 사이드바: 고정폭, eyebrow 섹션 레이블
6. 사이드바 nav: 아이콘+텍스트, 현재 라우트 행에 동적(`th:classappend`, 하드코딩 아님) 하이라이트
7. 사이드바: 즐겨찾기 고정 블록
8. 사이드바 푸터: 메뉴 편집 링크 + 연결 상태(dot+텍스트)
9. 메인: 인사말 헤더(오늘 날짜 + 주요 액션 버튼)
10. 메트릭 카드 정확히 4개: 아이콘+레이블+숫자+보조텍스트, 헤어라인 테두리, **채움 배경색 없음**
11. 3단 그리드: 좌(목록)/중앙(탭 상세)/우(관련 오더·활동)
12. 좌측 목록 행: dot+텍스트 상태(기존 `audit/list.html`의 `.dot` 패턴 재사용, 새 클래스 만들지 않음)
13. 중앙 컬럼: 탭 바 ≥2개
14. 우측 AI 패널: 접이식, 톤 다른 배경, 빠른실행 카드 ≥1 + 그래프 있는 인사이트 카드 ≥1 + 면책 문구 원문 그대로

---

## Phase 11 — 디자인 시스템 기반 + 기존 실 화면 3개에 셸 적용

> **스펙 소스 갱신 (11.1 진행 중 반영)**: `docs/design/design_guide.md`가 추가됐다 — `docs/draft.png`(=`docs/design/reference-layout-mediflow-emr.png`)를 1672×941px 기준 픽셀 단위로 실측한 스펙(정확한 grid 좌표, 완성 CSS 전문, 권장 HTML 구조, 색상 hex, 라운드 값, 픽셀 검수 방법 포함). Phase 11 전체(및 이후 화면)에서 색상·타이포·spacing·radius의 **구체적 hex/치수 값은 이 문서가 `md/design.md`보다 우선**한다(더 정밀한 실측 소스이므로). `md/design.md`는 배지 규칙(중립 배경+텍스트/점, 채움 칩 금지)과 이모지 금지 같은 **질적 원칙**만 계속 유효하다.

- [x] 11.1 `docs/design/design_guide.md` §3 `:root` 토큰 → `static/css/tokens.css`. **완료 기준**: 파일 존재, `layout/default.html :: head`에서 로드, `design_guide.md`의 `:root` 변수 전부(치수/색상/라운드/폰트)가 새 CSS에 동일한 이름·값으로 존재.
- [ ] 11.2 `topnavbar` 프래그먼트 재구축(구조 사실 1~4). **완료 기준**: 렌더링 HTML에 구조 사실 1~4 존재.
- [ ] 11.3 `sidebar` 프래그먼트 재구축: 11개 항목 전체. **완료 기준**: 구조 사실 5~8 존재, 기존 4개 실 화면 라우팅 정상.
- [ ] 11.4 사이드바 활성 상태 동적 하이라이트. **완료 기준**: `/audit/preview` 방문 시 감사 로그 행이 하이라이트.
- [ ] 11.5 재사용 콘텐츠 셸 프래그먼트(인사말/4카드/3단그리드/AI패널 크롬). **완료 기준**: 구조 사실 9~14 존재.
- [ ] 11.6 `audit/list.html` 리스킨. **완료 기준**: `AuditLogControllerIT` 무수정 통과.
- [ ] 11.7 `reports/patient-visit-summary.html` 리스킨. **완료 기준**: `PatientVisitSummaryControllerIT` 무수정 통과.
- [ ] 11.8 `stats/patient-count-by-gender-age-band.html` 리스킨. **완료 기준**: `StatsViewControllerIT` 무수정 통과.
- [ ] 11.9 AdminLTE 잔여 스타일 충돌 정리. **완료 기준**: 시각적 충돌 없음.

## Phase 12 — 실제 대시보드

- [ ] 12.1 대시보드 메트릭 4종 쿼리 설계. **완료 기준**: 테스트로 실제 카운트 검증.
- [ ] 12.2 `DashboardController` 실 데이터 바인딩. **완료 기준**: `DashboardControllerTests`가 실제 모델 속성 검증.
- [ ] 12.3 `dashboard.html` 새 셸로 재구축. **완료 기준**: 조작된 숫자 없음.

## Phase 13 — 신규 임상 모듈 (13.1이 기반, 이후 순차)

- [ ] **13.1 접수 (기반 — ENCOUNTER_SESSION 확정)**. **완료 기준**: 테이블 컬럼 확인, `ReceptionControllerIT`, `./gradlew test` 통과.
- [ ] 13.2 예약: `APPOINTMENT` + RBAC + 컨트롤러/뷰.
- [ ] 13.3 진료실(SOAP 셸, AI 미포함): `CLINICAL_NOTE` + RBAC + 탭형 상세 패널.
- [ ] 13.4 간호: `VITAL_SIGNS` + RBAC + 뷰.
- [ ] 13.5 검사: `LAB_ORDER` + RBAC + 뷰(결과 탭은 실제 과거 `LAB_RESULT` 조회).
- [ ] 13.6 수납: `PAYMENT` + RBAC + 뷰.
- [ ] 13.7 입원: `ADMISSION` + RBAC + 뷰.
- [ ] 13.8 보험청구: `INSURANCE_CLAIM` + RBAC + 뷰.
- [ ] 13.9 설정: `/admin/system` 읽기전용 운영 대시보드로 확장.

## Phase 14 — 실제 AI 연동

- [ ] 14.1 `com.anthropic:anthropic-java` 의존성 + `ANTHROPIC_API_KEY` fail-fast 설정.
- [ ] 14.2 SOAP 자동생성 (`claude-sonnet-5`, 감사 로그 `AI_SOAP_GENERATED`).
- [ ] 14.3 ICD-10 진단코드 추천 (구조화 JSON).
- [ ] 14.4 처방 추천 (구조화 JSON, 실제 과거 이력 컨텍스트).
- [ ] 14.5 예약 예측 (LLM 아님, 휴리스틱 집계).
- [ ] 14.6 진료실 AI 패널 연결: 결과는 "적용" 명시 확인 전까지 자동 반영 안 됨 + 면책 문구.
- [ ] 14.7 대시보드 AI 패널: 예측 카드 + 인사이트 카드(그래프).
- [ ] **14.8 음성 진료 기록(STT)**: 브라우저 Web Speech API, 미지원 브라우저는 정직하게 비활성 안내.

## Phase 15 — RBAC 감사 + 셸 마무리

- [ ] 15.1 누적된 `ACCESS_POLICY_RULES`를 RBAC 표와 대조, 불일치는 새 마이그레이션으로 수정.
- [ ] 15.2 모든 사이드바 `sec:authorize`가 실제 RBAC와 일치하는지 검증.

## Phase 16 — 최종 구조 일치 + 기능 완성도 감사 (Definition of Done)

- [ ] 16.1 구조 일치 검증 기준 14개 × 13개 화면 × 5개 역할 pass/fail 매트릭스.
- [ ] 16.2 propose.md §3.4 P0/P1/P2 항목 전체 "실제로 동작 검증됨" 재확인.
- [ ] 16.3 `./gradlew test` + `./gradlew build` 전체 통과.

---

## 핵심 참조 파일
- `app/src/main/resources/templates/layout/default.html`
- `app/src/main/java/com/hospitalops/security/SecurityConfig.java`
- `app/src/main/resources/db/migration/` (최신 버전 확인 후 다음 번호 사용)
- `app/src/main/java/com/hospitalops/crypto/EnvelopeCrypto.java` (fail-fast 설정 패턴 참고)
- `app/build.gradle`
- `md/work.md` (Codex 위임 파이프라인)
- `md/design.md` (디자인 토큰)
- `docs/design/reference-layout-mediflow-emr.png` / `docs/draft.png` (레이아웃 기준)
