# Codex 위임 구현 파이프라인 가이드

> 큰 작업을 step으로 쪼개 진행할 때, **step의 정찰(코드 탐색)·실제 구현·수정을 codex(OpenAI Codex CLI)
> 세션 하나가 담당**하고, Claude(오케스트레이터·subagent)는 **의도 파악 → 방향 결정 → 지시서 저작 →
> 결과 판정**만 한다. Claude는 소스 파일을 직접 읽어들이지 않고 **codex의 정찰 보고서와 diff만 읽는다.**
> 목적: 작업 퀄리티를 유지하면서 토큰 소모를 Claude → codex로 옮긴다.
> 이 문서는 그 호출 방식과 함정(시행착오)을 고정한다. 다음엔 바로 이 패턴을 쓴다.

---

## 0. 핵심 원칙

- **역할 분리**: codex = 정찰자·구현자·수정자. Claude = 설계자·판정자.
  - codex가 하는 일: 관련 코드 탐색·요약 보고, 지시서대로 구현, 결함 지적받고 수정, (고위험 step) 별도 세션 적대 검증.
  - Claude가 하는 일: 사용자 의도 해석, 관련 설계/스펙 문서 대조, 방향·계약 결정, 지시서 저작, diff·빌드·테스트로 판정, 커밋.
- **step 하나 = codex 세션 하나.** 첫 호출(정찰)에서 받은 **세션 ID로 resume를 이어가며** 지시→구현→수정을
  같은 세션에서 진행한다. codex가 정찰 때 읽은 코드가 세션에 남아 있으므로, 지시서는 그 맥락 위에 짧게
  쓰면 되고, FAIL 수정도 "결함 X만 고쳐라" 한 줄로 끝난다. **매 라운드 새 세션 금지**(맥락 재로딩 낭비).
- **Claude는 소스 파일을 통째로 읽지 않는다.** 지시서 저작에 필요한 정보는 codex 정찰 보고서로 받고,
  검증은 `git diff`(변경분만)로 한다. 정찰 보고가 부족하면 resume로 추가 질문한다. 예외: diff만으로
  판정이 안 서는 좁은 구간은 해당 부분만 직접 읽는다(전체 파일 로딩은 최후 수단).
- **판정 권한은 Claude에 있다.** codex의 "완료했다" 자기보고를 검증으로 인정하지 않는다 — diff를 직접
  읽고 빌드/테스트를 자기 손으로 돌린 뒤에만 PASS 처리한다.
- **FAIL이어도 Claude가 손으로 고치지 않는다.** 결함을 구체적으로 적어 같은 세션에 resume로 수정을
  시킨다(구현 토큰은 끝까지 codex 몫).
- 품질을 떨어뜨리지 않는 선이 전제다: **지시서가 부실하면 이 구조 전체가 무너진다.** codex가 모호함을
  자기 판단으로 메우게 두지 않는다 — 계약(인터페이스·엣지케이스·금지사항)을 지시서에 못 박는다(§3).

### 0-1. 검증은 Claude가 적대적으로 한다

codex가 "지시대로 했다"고 보고해도 **codex는 자기 구현의 맹점을 스스로 못 본다.** Claude(판정자)는
도장이 아니라 적대자로 검증한다 (모델이 서로 달라 같은 실수를 할 확률이 낮다 — 교차검증의 존재 이유):

1. **완결성 감사**: "지시한 게 존재하나?"가 아니라 "완료 기준 중 빠진 게 없는지, 구현이 커버 안 한
   케이스가 있는지"를 diff 전체를 읽고 판단한다.
2. **오탐 감사**: 판정·필터·가드 로직이면 정상 입력이 잘못 걸릴 케이스를 Claude가 직접 열거해 본다.
3. **상호작용 면(surface) 검증**: 새 코드가 기존 로직(레거시 휴리스틱, 병행 기능)과 공존할 때
   충돌·중복·모순이 있는지 본다. 상호작용 버그는 diff 밖에 산다 — 의심되면 resume로 codex에게
   "이 변경이 Y와 충돌하는지 검사해 보고하라"를 시켜 확인한다.
4. **닫힌 루프 검증**: 산출물이 다시 입력이 되는 기능(예: 봇이 낸 응답을 다시 학생/사용자 입력으로
   되먹이는 구조)은 실제로 왕복 테스트한다. 자기모순은 여기서만 잡힌다.

> 초록불(codex "완료")은 결승선이 아니라 "직접 뜯어봐라"는 신호다. 독립적 적대자가 못 깨뜨릴 때까지 반복한다.

---

## 1. 사전 조건

```bash
command -v codex && codex --version
```
설치 경로는 환경마다 다르므로 `command -v codex`로 확인한다.

---

## 2026-07-21 ML 실투자 전환 검증 기록 (Codex)

### 판정

- **현재 모델 상태: shadow 관찰 전용**
- **오늘자 보정/PIT: BLOCKED — KRX master confirmed through 2026-07-19, bar window 2026-07-21**
- **v4 replay: POLICY_NOT_EXECUTABLE — max score 0.54717658 < frozen min_score 0.55**
- **모의 승인: BLOCKED — 새 실행 가능 candidate와 prospective OOT 필요**
- **제한 실거래·challenger·champion 전환: 아직 불가**

### 현재 증거

- 모델: `ml_directional_centroid:readiness-20260720-v4`
- 모델 파일 SHA-256: `0085c07c55af204abf2ee99d7379b62801ea2e93dc42730f5599beabbfa76aa4`
- artifact overwrite 금지와 exact SHA gate 적용, 재학습·덮어쓰기 없음
- 오늘자 예측: 21종목, exact SHA 21/21, 중복 0, 실현 평가 0, signal/order 0
- ML store: feature row 80,240 / value 5,295,840 / label 244,185 / dataset SHA `6ff5d10c…`
- 기업행동: 27,296/27,296 적용, mismatch 0, 생존편향 coverage 0.996466
- run 5: 보정·ML store·v4 SHA 통과 후 오늘자 PIT master coverage에서 정직 중단
- 워크포워드: 3 folds, 평균 test accuracy 0.542706, precision@10 0.556614, rank IC 0.097839, OOS Sharpe 0.334892
- OOT: 현재 SHA에 결속된 `MEASURED` 결과 없음
- 실현 성과 드리프트: 평가 0/40으로 판정 보류
- DB: Alembic 0019, foreign key violation 0, capital policy 0
- corrections signoff approval #2 만료: 2026-08-20 00:07:14 KST. 현재 checklist 실패를 덮지 않음

### 피처 드리프트 원인

- 최근 7세션, 누락 0, 59개 측정 중 significant 27개
- data error 0, raw `close`·`dollar_volume` 정의 문제 2, 나머지는 시장 레짐 변화
- 기준 분포를 현재 데이터로 다시 고정해 경고를 지우지 않음

### 오늘 완료한 안전 백로그

- CLI·API·worker·order-time·저수준 registry의 canonical 단일 전환 gate
- immutable model registry, exact artifact SHA prediction/outcome 집계
- typed prediction→signal→order→fill→execution outcome와 fee/tax/slippage/3-way reconciliation
- challenger logical book 한도: 총 1%, 종목 0.25%, 일손실 0.25%, 미체결 3
- old-book liquidation-only SELL, flat+대사 완료 contract
- OOT session/cohort 통계와 shadow 100행 / paper 420행 분리
- standing capital policy DB/API/order-time 강제와 append-only revoke
- SQLite FK 상시 활성화, prediction 삭제 409+audit, signoff 만료 경고
- ML 화면에서 prediction/paper execution/live execution 증거 분리
- 전체 pytest 934건 및 Ruff 통과, dashboard build 통과

### 새 challenger 개발 실험

- v4 frozen snapshot, development end 2025-12-30, 현재 v4 test/OOT 재사용 0
- PIT 50,278표본·2,446세션, purged WF 12 folds, 5개 non-overlap cohort
- 선택 `rank_levels_volatility_rank`, 66→59피처
- 정책 `top_k=10`, `min_score=0.50`, 개발 OOS 실행 coverage 98.94%
- 후보 SHA `7c8e4936b7c5660367606b72560b1dc9c4328f5e68a210044ff231180436ba19`
- registry 등록·승격 없음. 운영 verifier/training contract 통합 → 새 candidate freeze → 신규 prospective OOT가 다음 단계

### 다음 순서

1. 유효한 KRX 브라우저 세션으로 master snapshot을 2026-07-21 이후까지 갱신
2. corrections/PIT와 current-model reuse pipeline 재실행
3. 선택 59피처 운영 contract 통합, 새 버전·SHA·정책 freeze 및 candidate 등록
4. OOT-A 100행 후 paper; paper prediction 100/10세션 + execution 20/10세션 + SELL exit 5
5. 별도 OOT-B 420행·20세션·4 cohort와 KIS lifecycle 통과 후 50,000원 smoke → 100,000원 bounded live
6. live prediction/execution 20건·10세션·SELL exit 5, 정상 drift·대사·capital policy 후 champion 심사

### 2026-07-21 Claude CLI 독립 검증

- 실행: Claude CLI `2.1.216`, 파일 수정 도구를 금지하고 `Read/Grep/Glob`만 허용
- 최종 원문: `CLAUDE_VERDICT: AGREE_WITH_CHANGES`
- 합의: 현재 v4는 shadow-only이며 OOT 100행만으로 live 승인하면 안 된다. 예측 평가 수와 실제 주문·체결·비용·대사 성과를 분리해야 한다.
- 당시 지적한 다단계 승격, 실행성과 미분리, typed 귀속·book 위험예산·장기 자본정책 부재는 후속 구현으로 해소
- 최신 코드 재검증은 Claude 세션 한도로 중단됐고 reset 2026-07-22 01:40 KST 뒤 heartbeat에서 재시도
- 상세 근거와 파일별 라인: `C:\Users\pc\Documents\Stock\docs\ml_live_trading_roadmap_20260721.md` §14

---

## 2. 표준 호출 & 세션 resume

### 2a. 첫 호출 (step 시작 = 정찰)

```bash
codex exec --skip-git-repo-check --dangerously-bypass-approvals-and-sandbox \
  -m gpt-5.6-terra \
  -c model_reasoning_effort="medium" \
  -c service_tier="default" \
  "<정찰 프롬프트 — §3 ① 참고>" < /dev/null
```

출력 배너에서 **세션 ID를 반드시 캡처**한다 (이후 모든 라운드가 이 ID로 이어진다):
```
session id: 019f8dcf-1341-7523-a715-0235b265946b
```

### 2b. 이어지는 호출 (지시→구현, FAIL→수정, 추가 질문)

```bash
codex exec resume <세션ID> --skip-git-repo-check --dangerously-bypass-approvals-and-sandbox \
  -m gpt-5.6-terra \
  -c model_reasoning_effort="medium" \
  -c service_tier="default" \
  "<다음 라운드 프롬프트>" < /dev/null
```

- 🚨 **`--last` 금지.** "가장 최근 세션"은 병렬 subagent·병행 codex 세션이 있으면 **남의 세션을 잡는다.**
  반드시 캡처해둔 UUID로 지정한다.
- resume에서도 `-m`·reasoning·bypass 플래그를 매번 명시한다 — config.toml 기본값에 의존하지 않는다
  (전역 설정이 나중에 바뀌어도 이 파이프라인은 영향받지 않게).
- 실측(다른 프로젝트 환경, codex-cli 0.144.5, 2026-07-23): 같은 세션 ID로 resume 시 이전 라운드
  내용을 정확히 기억했고, 라운드당 토큰 ~6.5k 수준(맥락 재로딩 없음). 이 환경에서 재확인 전엔
  버전 차이로 동작이 다를 수 있으니 첫 사용 시 짧게 스팟체크할 것.

**모델·강도 고정값**
| 항목 | 값 | 의미 |
|------|-----|------|
| 모델 | `gpt-5.6-terra` | 계정에서 쓸 수 있는 지정 모델(환경마다 사용 가능 모델이 다르면 교체) |
| 추론 강도 | `medium` | 표준 — 지시서가 구체적이므로 충분. 품질 저하가 관찰되면 그 step만 `xhigh`로 올릴 것 |
| 속도(service tier) | `default`(표준) | |

**플래그 의미(공통)**
| 플래그 | 이유 |
|--------|------|
| `exec` | 비대화형 1회 실행 모드 (TUI 안 띄움) |
| `--skip-git-repo-check` | 더티 작업 트리에서도 실행 허용 |
| `--dangerously-bypass-approvals-and-sandbox` | OS 샌드박스 헬퍼가 환경에 따라 깨져 있을 수 있어 샌드박스를 끄고 실행. codex가 실제로 파일을 쓰므로, 통제는 지시서의 "대상 파일 밖 수정 금지" + `git status` 전/후 대조로 한다 |
| `< /dev/null` | stdin을 닫는다. 안 닫으면 stdin 대기 상태로 무한 대기할 수 있음 |

필요 시 `-c 'mcp_servers.<name>.enabled=false'`로 이번 실행에 불필요한 MCP 서버를 개별적으로 끌 수 있다
(전역 config는 건드리지 않음). 생략해도 결과 자체는 동일하고, 로그만 지저분해진다.

### 2c. git 안전망 (구현 라운드마다)

```bash
git status --porcelain > /tmp/codex_pre.txt        # 구현 라운드 전 baseline
codex exec resume <ID> ... < /dev/null              # 구현/수정 실행
git status --porcelain | diff /tmp/codex_pre.txt -  # 델타 = codex가 만든 변경
```
델타가 **이번 step 대상 파일 밖**이면 codex가 지시를 벗어난 것 → 스코프 밖 변경만 되돌리고
resume로 "대상 파일 밖을 건드렸다. X를 되돌렸으니 다시는 건드리지 마라"를 고지한다.
(사용자 병행 작업으로 인한 무관 변경은 §4b 귀속 판단으로 흡수 — 전부 codex 탓 아님.)

---

## 3. step 루프 — 정찰 → 지시 → 구현 → 판정 → (수정)

한 step은 codex 세션 하나 안에서 아래 라운드로 진행한다.

### ① 정찰 (codex, 첫 호출)

```
탐색·보고 작업이다. 어떤 파일도 수정하지 마라.

[과제 개요] <이 step에서 하려는 것 한 줄 — 뭘 보고해야 할지 알 만큼만>

[보고하라]
1. <관련 파일들>의 구조: 관련 함수/클래스 시그니처, 타입, 이 기능이 끼어들 지점.
2. 이 코드베이스에서 유사한 기존 패턴 예시(파일 경로 + 핵심 형태 요약).
3. 이 변경이 상호작용할 수 있는 주변 코드(호출부, 이벤트, 등록부 등).
4. 구현 시 주의해야 할 함정(기존 제약, 엣지케이스)이 보이면 지적하라.
간결하게, 코드 전문 복붙 말고 계약(시그니처·형태) 위주로 보고하라.
```

Claude는 이 보고서(+ 관련 설계/스펙 문서, 사용자 의도)만으로 지시서를 쓴다. 보고가 부족하면
resume로 추가 질문한다. **Claude가 소스 파일을 통째로 읽는 것은 최후 수단.**

### ② 구현 지시 (Claude 저작 → codex resume)

정찰 맥락이 세션에 있으므로 지시서는 **계약과 결정사항 위주로 짧게** 쓴다. codex가 판단할 여지를
남기지 않는 것이 목표다 — 판단(설계 선택, 트레이드오프)은 Claude가 이미 끝낸 상태로 넘긴다.

```
이제 구현하라. 방금 네가 정찰한 코드 기준이다.

[목표] <이 step이 달성하려는 것 한 줄>
[대상 파일] <생성/수정할 파일 전체 경로 — 이 밖의 파일은 절대 수정 금지>

[정확한 요구사항]
1. <인터페이스/함수 시그니처/타입 — 이름까지 명시>
2. <동작 — 입력별 기대 출력, 엣지케이스(빈 값/중복/에러) 처리 방식>
3. <설계 결정사항 — 정찰 보고에서 갈렸던 선택지가 있으면 어느 쪽인지 Claude가 확정해서 명시>
4. <이 프로젝트 컨벤션 — 보안 규칙, 네이밍, 아키텍처 패턴 등 CLAUDE.md/README에 있는 것>
5. <해서는 안 되는 것 — 예: 새 디렉토리 생성 금지, 기존 함수 시그니처 변경 금지 등>

[완료 기준] <기계적으로 확인 가능한 조건 — Claude가 그대로 판정에 씀>
- 예: 빌드/린트/테스트 명령이 통과
- 예: 특정 함수/엔드포인트/스키마가 정확히 이 형태로 존재

[출력] 변경한 파일 목록과 각 파일에서 한 일을 요약하라.
```

원칙:
- **완결형 예시 코드를 주지 마라 — 계약(인터페이스·규칙)으로 줘라.** 완성형 예시는 그대로 베껴져
  맥락에 안 맞게 끼워 맞춰진다.
- "완료 기준"이 곧 ④ 판정의 acceptance criteria다. 여기서 헐렁하면 판정도 헐렁해진다.

### ③ 구현 (codex) — §2c git 안전망을 감싸서 실행

### ④ 판정 (Claude — §0-1 적대적 검증)

- a. §2c 델타가 대상 파일 안에만 있는지 확인.
- b. `git diff`(또는 병렬 시 `git diff HEAD -- <대상 파일들>`)로 **실제 코드를 직접 읽고**
  완료 기준·요구사항 전 항목 대조.
- c. 빌드/테스트 **Claude가 직접 실행**한다. 번들러 빌드가 타입체크를 생략하는 프로젝트라면
  타입체커를 별도로 실행한다. 테스트는 대상 범위만 좁혀서 돌린다.
- **PASS** → step 대상 파일만 명시 pathspec으로 커밋(§4). 다음 step으로.
- **FAIL** → ⑤로.

### ⑤ 수정 (codex resume — Claude가 손대지 않는다)

```
검증 결과 결함이 있다. 아래만 고쳐라. 다른 부분은 건드리지 마라.

[결함] <무엇이, 어디서(파일:줄), 왜 잘못됐는지 — 기대 동작과 실제 동작 대비로 구체적으로>
[수정 후] <어떤 상태가 되어야 하는지>
```

수정 후 ④로 돌아간다. 같은 step이 2~3회 반복 FAIL이면 §4 "진짜 멈춰야 할 때"로.

### 3b. 고위험 step — 별도 codex 검증 세션 추가 (VERDICT)

동시성·보안·인증·결제·상태머신·데이터 마이그레이션 등 **틀리면 비싼 step**은 ④ Claude 판정에 더해,
**구현 세션과 다른 새 codex 세션**에 적대 검증을 시킨다 (구현자와 검증자의 세션 분리로 교차검증 확보,
토큰은 여전히 codex 쪽에서 소모):

```
읽기 전용 검증 작업이다. 어떤 파일도 수정하지 마라. (너는 이 코드를 작성하지 않았다.)

[검증 대상] git diff HEAD -- <대상 파일들> 의 변경분
[목표] <이 step이 달성하려던 것>

[적대 검증 기준]
1. <완료 기준들 — ②의 것 그대로>
2. 이 열거/커버리지에서 빠진 케이스를 전부 나열하라(없으면 '누락 없음' 명시).
3. 이 판정/가드/필터에 정상 입력이 잘못 걸리는 경우를 전부 찾아라.
4. 이 변경이 함께 도는 기존 계층과 충돌·중복·모순하는 지점을 찾아라.

[출력] 각 기준의 결과를 한 줄씩 적고,
마지막 줄에 정확히 'VERDICT: PASS' 또는 'VERDICT: FAIL (사유)' 만 출력하라.
```

VERDICT: FAIL이면 그 사유를 ⑤ 수정 라운드의 [결함]으로 넘긴다(구현 세션에 resume).
일반 step은 이 단계 생략 — Claude 판정(④)만으로 충분하다.

---

## 4. 진행 정책 (커밋·자동 진행)

- **step별 커밋**: PASS 받은 step은 그 즉시 **대상 파일만 명시적으로 `git add`** 후 커밋.
  메시지에 step 번호·목표 명시. 무관한 사용자 변경(병행 작업)은 절대 staging 금지. 한 step = 한 커밋(원칙).
- **묻지 않고 계속**: PASS면 사용자 확인 없이 다음 step을 바로 시작한다. "계속할까요?" 금지.
- **push**: 매 step push하지 않는다. 기능이 일관된 마일스톤에 도달했거나 사용자가 요청할 때 push.
  (미완 기능을 공유 브랜치에 매 step 흘리지 않기 위함)
- **진짜 멈춰야 할 때만 사용자에게 묻는다**:
  1) 설계가 갈리는 진짜 모호함(코드/맥락으로 못 정함), 2) 같은 step이 수정 재지시에도 FAIL 반복(2~3회),
  3) 되돌리기 어렵거나 외부로 나가는 작업(force push·삭제·배포 등). 그 외에는 멈추지 않는다.

---

## 4b. 안전망 운용 — 과반응 금지 (중요)

codex 실행 후 `git status`에 변경이 보여도 **전부 codex 탓이 아니다.** 사용자는 그 사이
다른 파일을 병행 편집할 수 있다. 다음 절차로 **귀속(attribution)** 부터 판단한다:

1. **델타가 step 대상 파일인가?**
   - 예 → codex가 지시대로 건드린 것. §0-1대로 내용 검증.
   - 아니오(무관한 문서·다른 기능·사용자의 알려진 WIP) → **codex 아님.** 그대로 둔다. 되돌리거나 캐묻지 않는다.
2. 애매하면 **사용자에게 단정 짓지 말고** "이건 의도한 변경인가요?"로 가볍게 확인. *codex 탓으로 단정 보고 금지.*
3. 커밋 시에는 어차피 **step 대상 파일만 명시적으로 `git add`** 하므로, 무관한 변경은 자연히 섞이지 않는다.

> 실제로, codex 실행 후 다수의 파일 변경이 보였지만 알고 보니 사용자가 별도로 진행한 무관한 작업
> (다른 기능/문서 정리)이었던 사례가 있었다. step 대상 파일이 아니면 스코프 밖 → codex 무관.
> 이런 걸 codex 탓으로 오인하지 말 것.

---

## 4c. subagent 위임 모드 — step 실행의 기본형

step 작업은 오케스트레이터(메인 세션)가 직접 하지 않고 **step(또는 작업 묶음) 담당 subagent에게 위임**한다.
subagent가 §3 루프 전체(정찰 지시→지시서 저작→구현 호출→판정→수정 재지시→커밋)를 자기 턴 안에서
완주하고, 오케스트레이터는 계획·투입·독립 검증·다음 step 투입만 맡는다.

### 3계층 역할 분리

| 계층 | 하는 일 | 하지 않는 일 |
|------|---------|--------------|
| 오케스트레이터(메인) | step 분할, 사용자 의도·설계 문서 대조, subagent 투입, 보고의 **git 독립 검증**, 다음 step 투입 | 직접 구현, 지시서 세부 저작(subagent에 위임) |
| subagent(Claude) | codex 정찰 지시 → 보고서 기반 §3② 지시서 저작 → codex 구현 → **직접 판정(§0-1)** → FAIL 시 resume 수정 재지시 → PASS 시 명시 pathspec 커밋 → 결과 보고 | 직접 코드 작성(codex 몫), 소스 파일 통읽기(정찰 보고로 대체), 스코프 밖 파일 접촉, 사용자에게 직접 질문 |
| codex | 정찰 보고, 지시서대로 **실제 구현**, 결함 수정, (고위험) 별도 세션 VERDICT 검증 | 지시서에 없는 설계 판단, 자기 구현의 최종 판정 |

핵심: **구현 주체는 codex, 판정 주체는 Claude다.** codex의 "완료했다" 보고만으로 PASS 처리 금지 —
subagent는 반드시 diff를 직접 읽고 빌드/테스트를 자기 손으로 돌려야 한다.

### subagent 투입 프롬프트 템플릿 (필수 요소 전부 포함)

```
[step 목표] <한 줄>
[참고 문서] <관련 설계/스펙 문서 경로·절>
[대상 파일] <명시 — 이 밖의 파일은 접촉 금지>
[완료 기준] <기계적으로 확인 가능한 조건들 — codex 지시서·판정 양쪽에 그대로 쓸 것>
[고위험 여부] <예/아니오 — 예면 §3b 별도 codex 검증 세션 필수>

[필수 규칙]
1. 이 문서(codex 가이드)를 먼저 읽고 §2 호출·resume 형식과 §3 루프(정찰→지시→구현→판정→수정)를 그대로 따르라.
2. 실제 코드 작성·수정은 **전부 codex에게 시킨다.** 네가 직접 파일을 고치지 마라(FAIL 재시도도
   같은 codex 세션에 resume로 수정 지시 — 네가 손으로 고치는 게 아니다).
3. **첫 codex 호출의 배너에서 세션 ID를 캡처**하고, 이후 라운드는 전부 그 ID로
   `codex exec resume <ID> ...` 하라. **`--last` 금지**(병행 세션의 것을 잡는다).
4. codex·빌드·테스트는 전부 **포그라운드(blocking)로 실행**하고 **그 턴 안에서 결과를 받아라.**
   백그라운드로 던지고 "완료 알림을 기다리겠다"며 턴을 끝내지 마라. 오래 걸려도 그 자리에서 기다린다.
   테스트는 대상 범위만 좁혀 돌려라.
5. 판정은 네(subagent)가 `git diff`를 직접 읽고 완료 기준과 대조 + 빌드/테스트 직접 실행으로 하라.
   codex의 완료 보고 문구를 옮겨 적는 걸로 판정을 대신하지 마라.
6. 소스 파일을 통째로 읽지 마라 — 필요한 정보는 codex 정찰 보고서·추가 질문(resume)으로 얻어라.
7. 줄바꿈 자동변환 환경(core.autocrlf 등)이라면 바이트/내용 비교는 저장소 blob 기준으로 하라
   (워킹트리 개행 대조는 오탐).
8. PASS 후 커밋은 **대상 파일만 명시 pathspec으로** `git add`. 그 외 파일 staging 절대 금지.
9. FAIL이면 결함을 구체화해 같은 세션에 resume로 수정 지시. 같은 step 2~3회 반복 FAIL이거나
   설계가 갈리는 모호함이면 **더 진행하지 말고 그 상태 그대로 보고하라**
   (사용자에게 묻지 말 것 — 판단은 오케스트레이터가 한다).

[보고 형식] step별로: 커밋 해시 / codex 세션 ID / 판정 시 확인한 완료 기준 항목별 결과 /
(고위험이면) VERDICT 원문 / FAIL이 있었다면 사유·수정 라운드 수 / 이월 발견사항.
```

프롬프트에 왜 저 항목들이 필수인가 (전부 실제 겪은 사고 유형):
- **세션 ID 캡처·`--last` 금지(3)**: 병행 codex 세션(사용자·다른 subagent)이 있으면 `--last`가
  남의 세션에 이어붙는다. UUID 지정만이 안전하다.
- **포그라운드 강제(4)**: subagent가 codex·테스트를 백그라운드로 돌리고 알림을 기다리다 턴을
  끝내는 패턴이 반복적으로 관찰됐다.
- **직접 diff 판정(5)**: codex 자기보고를 그대로 믿으면 §0-1 적대적 검증이 무력화된다.
- **blob 기준 비교(7)**: 줄바꿈 자동변환 환경에서 워킹트리 개행 vs 저장소 blob 바이트 비교는
  헛 FAIL을 낸다.

### 순차 투입이 기본 — 단, 아래 3조건을 만족하면 병렬 허용

step subagent는 **기본적으로 한 번에 하나만** 투입한다. git-status 안전망(§2c)과 명시 pathspec
커밋 위생이 전부 워킹트리 상태를 전제하므로, 아무 준비 없이 같은 저장소에 subagent를 병렬로 풀면
서로의 델타를 오염시켜 안전망 자체가 무의미해진다. (사용자의 병행 세션은 §4b대로 귀속 판단으로 흡수.)

무너지는 지점은 정확히 셋이고, 전부 **인덱스(스테이징 영역)가 워크트리당 하나**라는 사실에서 나온다:

1. A가 `git add`로 자기 파일을 stage하고 B도 stage한 뒤 A가 `git commit`하면 **B의 미완성 편집이
   A 커밋에 딸려 들어간다.**
2. 판정 범위를 `git diff --cached`로 보는 방식도 인덱스 공유가 전제라 같이 무너진다.
3. `git status` 전후 델타 안전망이 남의 변경을 자기 델타로 오인한다.

따라서 **아래 3조건을 전부 만족할 때만** 병렬 투입한다. 하나라도 미충족이면 순차로 돌아간다.

| 조건 | 내용 |
|------|------|
| ① 파일 소유권 배타 | 버킷 간 대상 파일 집합이 **완전히 서로소**. 한 파일은 정확히 한 에이전트만 소유. 오케스트레이터가 투입 전에 파일→버킷 맵을 확정하고 교집합이 0임을 확인한다. |
| ② 공유 핫 파일 선점 | 여러 버킷이 공통으로 append해야 하는 파일(타입 유니온·카탈로그·라우터 등록부 등)은 병렬 대상에서 **제외**하고, 선행 순차 step에서 필요한 항목을 **미리 전부 등록**한다. 이후 병렬 에이전트에겐 읽기 전용. |
| ③ 인덱스 우회 커밋 | 커밋은 `git add` 없이 `git commit -m <msg> -- <파일들>`로 **인덱스를 무시하고 경로만** 커밋. 판정 범위도 `git diff --cached`가 아니라 **명시 파일 목록**(`git diff HEAD -- <파일들>`)으로 본다. |

병렬로 투입할 때 subagent 프롬프트에 추가할 문구:

```
[병렬 실행 중 — 워킹트리 공유 주의]
- 너 말고 다른 에이전트가 같은 저장소에서 동시에 작업 중이다. codex 지시서의 [대상 파일] 밖은
  codex에게도 절대 수정하지 말라고 명시하고, 너 자신도 읽기만 해라. 다른 버킷의 파일이 더티하거나
  인덱스에 스테이징돼 있어도 정상이다.
- codex resume는 반드시 **네가 캡처한 세션 ID**로만 하라(`--last` 절대 금지 — 남의 세션을 잡는다).
- `git add` / `git stash` / `git checkout --` / `git reset` 을 쓰지 마라. 커밋은 반드시
  `git commit -m "<msg>" -- <대상 파일들>` 형태(인덱스 우회).
- 판정 범위는 `git diff HEAD -- <대상 파일들>` 로 보라. `git status` 전체나
  `git diff --cached`를 근거로 쓰면 남의 변경으로 헛 FAIL이 난다.
- 커밋 직후 `git show --stat HEAD` 로 스코프 밖 파일 혼입 여부를 확인해 보고에 포함하라.
```

또한 병렬 시 오케스트레이터는 **각 커밋의 파일 목록이 그 버킷 소유 집합의 부분집합인지** 사후
대조한다(아래 "오케스트레이터의 독립 검증" 1~2번을 버킷별로 수행).

> 대안: 에이전트마다 워크트리를 분리하면 위 3문제가 원천 소멸한다. 단 버킷들이 같은 파일을
> 공유하면 병합 충돌 비용이 커지므로, **파일이 서로소일 땐 같은 워크트리 + 위 3조건이 더 싸다.**
> 서로소로 나눌 수 없을 때만 워크트리 격리를 고려한다.

### 오케스트레이터의 독립 검증 — 자기보고를 믿지 마라

subagent 보고를 받으면 **git으로 대조한 뒤에만** 그 step을 완료 처리한다:

1. `git log --oneline <투입 전 HEAD>..HEAD` — 커밋 수·메시지가 보고와 일치하는가. (자기보고
   커밋 수가 실제와 달랐던 사례가 있다 — 수치는 항상 git이 정본.)
2. `git show --stat <해시>` — 각 커밋에 스코프 밖 파일이 섞이지 않았는가.
3. 보고에 완료 기준 항목별 판정 결과가 있는가. codex 완료 보고 문구만 인용돼 있으면 미판정으로
   간주하고 오케스트레이터가 직접 `git diff`로 재확인한다. 고위험 step인데 VERDICT 원문이 없으면
   오케스트레이터가 §3b 검증 세션을 직접 돌린다.
4. 빌드/타입체크 게이트를 오케스트레이터가 한 번 더 돌린다(번들러 빌드가 타입체크를 생략하는
   프로젝트라면 타입체커 직접 실행 필수).

### subagent가 도중에 죽었을 때

subagent가 최종 보고 전에 종료(크레딧 소진·프로세스 사망)돼도 **커밋이 진실**이다. `git log`로
어디까지 PASS·커밋됐는지 재구성하고, 미완 부분만 완료 기준을 조정해 새 subagent를 재투입한다.
죽은 subagent의 codex 세션 ID를 보고·로그에서 건질 수 있으면 새 subagent에게 넘겨 그 세션에
resume하게 한다(정찰 맥락 재활용). 못 건지면 새 세션으로 정찰부터.

### subagent가 codex를 백그라운드에 던지고 정지했을 때 — 재개 루프 금지, 직접 돌려라

subagent가 "codex 완료 알림 대기하겠다"며 결과 없이 멈추면, **재개를 반복시키지 마라.** 재개해봐야
subagent는 백그라운드 codex를 다시 폴링하다 또 멈춘다. 효율적 복구는:

1. **오케스트레이터가 codex를 직접 포그라운드로 실행한다** — 구현은 여전히 codex가 하므로 토큰
   분배 원칙이 안 깨진다. subagent가 캡처한 세션 ID가 있으면 그 세션에 resume, 없으면 §3 루프를
   오케스트레이터가 직접 진행한다. 완료 후 오케스트레이터가 §0-1 판정을 하고, PASS면
   **오케스트레이터가 명시 pathspec으로 커밋**.
2. 판정 결과 결함이 있으면 resume로 수정 재지시한다(Claude가 직접 고치지 않는다).
3. **동시 codex 프로세스 정리**: 백그라운드로 새던 codex가 orphan으로 남아 있으면, 다음 codex
   실행이 그것들을 발견해 정리하려다 엉킨다. 새 codex 전에 관련 프로세스를 정리한다.
4. **포그라운드 codex가 타임아웃**되면 라운드 범위를 좁혀라(구현을 파일 단위로 쪼개 여러 resume
   라운드로). 백그라운드 실행은 병행 에이전트가 죽일 수 있다 — 포그라운드 좁힌 라운드가 가장 안정적.

> 핵심: codex를 **누가** 돌리든 구현 주체는 codex, 판정 주체는 Claude다. subagent가 못 기다리면
> 오케스트레이터가 포그라운드로 돌려 루프를 끊는 게 정석이다. "재개해서 또 기다리게" 하지 마라.

---

## 5. 함정 & 트러블슈팅

| 증상 | 원인 | 대응 |
|------|------|------|
| stdin 대기 메시지 후 무한 대기 | stdin 미닫힘 | `< /dev/null` 추가 |
| resume가 엉뚱한 작업 맥락에서 돈다 | `--last`가 병행 세션(사용자·다른 subagent)의 것을 잡음 | **항상 캡처한 UUID로 resume** |
| Windows 샌드박스 헬퍼 실행 실패 (`orchestrator_helper_launch_failed` 등) | `--sandbox read-only`가 OS 샌드박스 헬퍼를 못 찾음 | `--sandbox read-only` 쓰지 말고 `--dangerously-bypass-approvals-and-sandbox` 사용 |
| MCP 관련 transport/연결 오류 로그 | codex 설정에 등록된 로컬 MCP 서버가 안 떠 있음 | **대개 무해**(codex가 일반 셸 명령으로 폴백해 정상 동작). 로그를 깔끔히 하려면 §2처럼 해당 서버만 `-c`로 끄기 |
| 토큰 사용량 비정상 폭증 | 깨진 샌드박스에서 재시도 thrash | 샌드박스 플래그 교정으로 해결됨 |
| codex가 결과 없이 종료 | orphan codex 프로세스들을 발견해 정리하다 엉킴 | 새 codex 전에 남은 프로세스 정리 |
| 백그라운드 codex가 사망 | 병행 에이전트가 백그라운드 프로세스를 죽일 수 있음 | codex는 포그라운드로. 길면 라운드를 좁혀라 |
| codex 구현이 지시서 밖 파일까지 수정 | 지시서의 [대상 파일] 경계가 느슨했거나 "알아서" 류 표현이 섞임 | 스코프 밖 변경만 되돌리고 resume로 경계 재고지 |

> 정리: **OS 샌드박스에 기대지 않는다.** 지시서의 "이 파일만" 경계 + 실행 후 `git status` 대조가 안전망이다.
> MCP 노이즈는 결과에 영향이 없다 — 필요 서버만 끄거나 그냥 무시하면 된다.

---

## 6. 검증된 예시 (실측, codex-cli 0.144.5, 다른 프로젝트 환경 2026-07-23)

```bash
# ① 첫 호출 — 세션 ID가 배너에 찍힌다
codex exec --skip-git-repo-check --dangerously-bypass-approvals-and-sandbox \
  -m gpt-5.6-terra -c model_reasoning_effort="medium" \
  "기억 테스트다. 비밀 코드는 'MANGO-42'다. ..." < /dev/null
# → session id: 019f8dcf-1341-7523-a715-0235b265946b / tokens ~6.4k

# ② 그 ID로 resume — 이전 라운드 맥락을 기억한다
codex exec resume 019f8dcf-1341-7523-a715-0235b265946b \
  --skip-git-repo-check --dangerously-bypass-approvals-and-sandbox \
  -m gpt-5.6-terra -c model_reasoning_effort="medium" \
  "아까 기억하라고 한 비밀 코드가 뭐였지?" < /dev/null
# → MANGO-42 (정확) / tokens ~6.6k — 맥락 재로딩 비용 없음
```

읽기 전용 확인만 필요할 때(구현 없이 존재 여부 등):
```bash
codex exec --skip-git-repo-check --dangerously-bypass-approvals-and-sandbox \
  "읽기 전용 확인. 수정 금지. <경로>가 존재하는가?
   마지막 줄에 'RESULT: <PRESENT|ABSENT>' 만 출력하라." < /dev/null
```
