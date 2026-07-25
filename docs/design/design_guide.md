# 1. 레이아웃 구조부터 고정해야 함

첨부 이미지의 실제 기준 크기는 **1672 × 941px**이다. 이 화면은 일반적인 반응형 대시보드처럼 모든 영역을 `1fr`로 나누면 동일하게 나오지 않는다.

핵심 구조는 다음과 같다.

| 영역           |                           기준 좌표 및 크기 |
| ------------ | -----------------------------------: |
| 상단 헤더        |      `x: 0 / y: 0 / w: 1672 / h: 70` |
| 좌측 사이드바      |     `x: 0 / y: 70 / w: 234 / h: 835` |
| 하단 상태바       |    `x: 0 / y: 905 / w: 1672 / h: 36` |
| 메인 콘텐츠 시작점   |                     `x: 264 / y: 99` |
| 메인 콘텐츠 너비    |                             `1084px` |
| 메인 콘텐츠 높이    |                              `790px` |
| AI 패널        | `x: 1368 / y: 124 / w: 274 / h: 765` |
| 메인과 AI 사이 간격 |                               `20px` |
| 상단 인사 영역     |                               `54px` |
| 요약 카드 영역     |                       `1026 × 100px` |
| 하단 3열 패널     |                       `1084 × 596px` |

하단 3열은 아래처럼 고정해야 한다.

```text
260px | 14px gap | 560px | 14px gap | 236px
```

상단 요약 카드 4개는 전체 메인 너비를 전부 사용하지 않는다.

```text
240px | 14px | 240px | 14px | 240px | 14px | 264px
```

따라서 마지막 요약 카드 오른쪽에는 의도적으로 약 `58px`의 빈 공간이 남는다. 이 부분을 `repeat(4, 1fr)`로 늘리면 원본과 다르게 보인다.

---

# 2. 권장 HTML 구조

내용은 임시 텍스트로 두고, 먼저 아래 구조를 그대로 잡는 편이 낫다.

```html
<!doctype html>
<html lang="ko">
<head>
  <meta charset="UTF-8" />
  <meta
    name="viewport"
    content="width=device-width, initial-scale=1.0"
  />
  <title>EMR Dashboard Layout</title>

  <link
    rel="stylesheet"
    as="style"
    crossorigin
    href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/static/pretendard.css"
  />

  <link rel="stylesheet" href="./style.css" />
</head>

<body>
  <div class="emr-shell">

    <!-- 상단 헤더 -->
    <header class="topbar">
      <div class="topbar-brand">
        <span class="brand-symbol"></span>
        <span class="brand-name">MediFlow EMR</span>
      </div>

      <div class="topbar-content">
        <div class="search-control">
          <span class="search-icon"></span>
          <span class="search-placeholder">환자 검색</span>
          <span class="search-shortcut">⌘ K</span>
        </div>

        <div class="topbar-actions">
          <button class="topbar-wide-button">예약 126명</button>
          <button class="topbar-icon-button"></button>
          <button class="topbar-icon-button"></button>

          <button class="profile-control">
            <span class="profile-avatar"></span>

            <span class="profile-text">
              <strong>사용자 이름</strong>
              <small>진료과</small>
            </span>

            <span class="profile-chevron"></span>
          </button>
        </div>
      </div>
    </header>

    <!-- 좌측 사이드바 -->
    <aside class="sidebar">
      <div class="sidebar-scroll">
        <section class="sidebar-section">
          <h2 class="sidebar-section-title">오늘 진료</h2>

          <nav class="sidebar-nav">
            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>대시보드</span>
            </a>

            <a class="sidebar-item is-active" href="#">
              <span class="sidebar-icon"></span>
              <span>접수</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>예약</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>진료실</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>간호</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>검사</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>수납</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>입원</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>보험청구</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>통계</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>설정</span>
            </a>
          </nav>
        </section>

        <section class="sidebar-section sidebar-favorites">
          <h2 class="sidebar-section-title">즐겨찾기</h2>

          <nav class="sidebar-nav sidebar-nav-compact">
            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>외래 접수</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>검사 결과 확인</span>
            </a>

            <a class="sidebar-item" href="#">
              <span class="sidebar-icon"></span>
              <span>미수금 관리</span>
            </a>
          </nav>
        </section>
      </div>

      <button class="sidebar-edit-button">
        <span class="sidebar-icon"></span>
        <span>메뉴 편집</span>
      </button>
    </aside>

    <!-- 중앙 작업 영역 -->
    <main class="workspace">
      <div class="workspace-layout">

        <section class="dashboard-core">

          <!-- 상단 제목 -->
          <header class="page-heading">
            <div class="page-heading-copy">
              <h1>좋은 아침입니다, 사용자님.</h1>
              <p>오늘도 업무를 시작해 주세요.</p>
            </div>

            <div class="page-heading-actions">
              <span class="page-date">2024년 8월 14일</span>
              <button class="secondary-button">오늘 일정</button>
            </div>
          </header>

          <!-- 요약 카드 -->
          <section class="summary-grid">
            <article class="summary-card">
              <span class="summary-icon"></span>
              <div class="summary-copy">
                <span class="summary-label">오늘 예약</span>
                <strong class="summary-value">126명</strong>
                <small class="summary-description">예약 142명</small>
              </div>
            </article>

            <article class="summary-card">
              <span class="summary-icon"></span>
              <div class="summary-copy">
                <span class="summary-label">현재 대기</span>
                <strong class="summary-value">23명</strong>
                <small class="summary-description">평균 대기 18분</small>
              </div>
            </article>

            <article class="summary-card">
              <span class="summary-icon"></span>
              <div class="summary-copy">
                <span class="summary-label">수납 대기</span>
                <strong class="summary-value">5명</strong>
                <small class="summary-description">총 320,000원</small>
              </div>
            </article>

            <article class="summary-card">
              <span class="summary-icon"></span>
              <div class="summary-copy">
                <span class="summary-label">미수금</span>
                <strong class="summary-value">120만원</strong>
                <small class="summary-description">총 6건</small>
              </div>
            </article>
          </section>

          <!-- 하단 3열 -->
          <section class="primary-grid">

            <!-- 좌측 일정 패널 -->
            <article class="panel schedule-panel">
              <header class="panel-header">
                <strong>오늘 진료 일정</strong>
                <span class="panel-count">16/32</span>
              </header>

              <div class="schedule-column-header">
                <span>시간</span>
                <span>환자</span>
                <span>상태</span>
              </div>

              <div class="panel-scroll schedule-list">
                <div class="schedule-row">
                  <span>09:00</span>
                  <span>환자 이름</span>
                  <span>진료중</span>
                </div>

                <div class="schedule-row">
                  <span>09:10</span>
                  <span>환자 이름</span>
                  <span>대기</span>
                </div>

                <div class="schedule-row">
                  <span>09:20</span>
                  <span>환자 이름</span>
                  <span>완료</span>
                </div>
              </div>

              <footer class="panel-footer">
                <button class="panel-footer-button">더보기</button>
              </footer>
            </article>

            <!-- 중앙 환자 패널 -->
            <article class="panel patient-panel">
              <header class="patient-summary">
                <div class="patient-avatar"></div>

                <div class="patient-information">
                  <div class="patient-name-row">
                    <strong>환자 이름</strong>
                    <span>45세</span>
                    <span class="patient-divider"></span>
                    <span>010-0000-0000</span>
                  </div>

                  <p>환자 관련 간단한 설명 영역</p>

                  <div class="patient-tags">
                    <span>과거력</span>
                    <span>고혈압</span>
                    <span>알레르기</span>
                  </div>
                </div>

                <div class="patient-actions">
                  <button class="small-button">환자 상세</button>
                  <button class="square-button"></button>
                </div>
              </header>

              <nav class="patient-tabs">
                <button class="patient-tab is-active">SOAP</button>
                <button class="patient-tab">처방</button>
                <button class="patient-tab">LAB</button>
                <button class="patient-tab">PACS</button>
                <button class="patient-tab">보험</button>
              </nav>

              <div class="panel-scroll patient-document">
                <section class="document-row">
                  <strong class="document-letter">S</strong>
                  <div class="document-content">
                    <h3>Subjective</h3>
                    <p>문서 내용이 들어가는 영역</p>
                  </div>
                </section>

                <section class="document-row">
                  <strong class="document-letter">O</strong>
                  <div class="document-content">
                    <h3>Objective</h3>
                    <p>문서 내용이 들어가는 영역</p>
                  </div>
                </section>

                <section class="document-row">
                  <strong class="document-letter">A</strong>
                  <div class="document-content">
                    <h3>Assessment</h3>
                    <p>문서 내용이 들어가는 영역</p>
                  </div>
                </section>

                <section class="document-row">
                  <strong class="document-letter">P</strong>
                  <div class="document-content">
                    <h3>Plan</h3>
                    <p>문서 내용이 들어가는 영역</p>
                  </div>
                </section>
              </div>

              <footer class="patient-toolbar">
                <span class="patient-modified">2024-08-14 09:05</span>

                <div class="patient-tool-buttons">
                  <button></button>
                  <button></button>
                  <button></button>
                  <button></button>
                </div>
              </footer>
            </article>

            <!-- 우측 오더 패널 -->
            <article class="panel order-panel">
              <header class="panel-header">
                <strong>오더 / 티켓</strong>
                <button class="plain-icon-button"></button>
              </header>

              <div class="panel-scroll order-content">
                <section class="order-section">
                  <div class="order-section-title">
                    <strong>진행 중 오더</strong>
                    <span>3</span>
                  </div>

                  <div class="order-list">
                    <div class="order-row">항목 1</div>
                    <div class="order-row">항목 2</div>
                    <div class="order-row">항목 3</div>
                  </div>
                </section>

                <section class="order-section">
                  <div class="order-section-title">
                    <strong>최근 처방</strong>
                    <span>2</span>
                  </div>

                  <div class="order-list">
                    <div class="order-row">처방 항목</div>
                    <div class="order-row">처방 항목</div>
                  </div>
                </section>

                <section class="order-section">
                  <div class="order-section-title">
                    <strong>최근 활동</strong>
                  </div>

                  <div class="order-list">
                    <div class="order-row">활동 항목</div>
                    <div class="order-row">활동 항목</div>
                  </div>
                </section>
              </div>

              <footer class="panel-footer">
                <button class="wide-outline-button">전체 활동 보기</button>
              </footer>
            </article>
          </section>
        </section>

        <!-- 우측 AI 패널 -->
        <aside class="assistant-panel">
          <header class="assistant-header">
            <div class="assistant-title">
              <span class="assistant-symbol"></span>
              <strong>AI Assistant</strong>
            </div>

            <button class="assistant-close"></button>
          </header>

          <div class="assistant-body">
            <section class="assistant-section">
              <h2>빠른 실행</h2>

              <div class="assistant-action-list">
                <button class="assistant-action"></button>
                <button class="assistant-action"></button>
                <button class="assistant-action"></button>
                <button class="assistant-action"></button>
                <button class="assistant-action"></button>
              </div>
            </section>

            <section class="assistant-section assistant-insight-section">
              <h2>오늘의 인사이트</h2>

              <article class="insight-card insight-chart"></article>
              <article class="insight-card insight-tip"></article>
            </section>
          </div>

          <footer class="assistant-footer">
            AI 결과는 참고용이며 최종 판단이 필요합니다.
          </footer>
        </aside>

      </div>
    </main>

    <!-- 하단 상태바 -->
    <footer class="statusbar">
      <span class="connection-state">연결: 정상</span>
      <span>서버: KR-01</span>
      <span>v2.4.1</span>
    </footer>
  </div>
</body>
</html>
```

---

# 3. 전체 CSS

아래 CSS는 **1672 × 941px에서 원본과 같은 좌표 구조가 나오도록 잡은 값**이다.

```css
:root {
  /* =========================================================
     APP SHELL
  ========================================================= */
  --topbar-height: 70px;
  --statusbar-height: 36px;
  --sidebar-width: 234px;

  /* =========================================================
     WORKSPACE
  ========================================================= */
  --workspace-padding-top: 29px;
  --workspace-padding-right: 30px;
  --workspace-padding-bottom: 16px;
  --workspace-padding-left: 30px;

  --assistant-width: 274px;
  --assistant-gap: 20px;
  --assistant-top-offset: 25px;

  /* =========================================================
     MAIN CONTENT
  ========================================================= */
  --heading-height: 54px;
  --summary-height: 100px;
  --section-gap: 20px;

  --primary-left-width: 260px;
  --primary-right-width: 236px;
  --primary-gap: 14px;

  /* =========================================================
     COLOR
  ========================================================= */
  --color-canvas: #ffffff;
  --color-header: #fbfbfb;
  --color-sidebar: #fafafa;
  --color-statusbar: #fafafa;

  --color-panel: #ffffff;
  --color-panel-soft: #fbfcfe;

  --color-border: #e8eaed;
  --color-border-soft: #f0f1f3;
  --color-border-strong: #dde1e6;

  --color-text: #171a1f;
  --color-text-secondary: #555d69;
  --color-text-muted: #818894;
  --color-text-faint: #a4aab3;

  --color-primary: #1671e2;
  --color-primary-soft: #edf3fb;
  --color-primary-border: #c9dff9;

  --color-success: #15966d;
  --color-warning: #ff7a1a;
  --color-danger: #e5484d;

  /* =========================================================
     RADIUS
  ========================================================= */
  --radius-control: 10px;
  --radius-nav: 11px;
  --radius-panel: 12px;
  --radius-assistant: 20px;

  /* =========================================================
     TYPE
  ========================================================= */
  --font-family:
    "Pretendard",
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;
}


/* =========================================================
   RESET
========================================================= */

*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body {
  width: 100%;
  height: 100%;
  margin: 0;
}

html {
  background: var(--color-canvas);
}

body {
  min-width: 1672px;
  min-height: 941px;
  overflow: hidden;

  color: var(--color-text);
  background: var(--color-canvas);

  font-family: var(--font-family);
  font-size: 13px;
  line-height: 1.45;

  letter-spacing: -0.018em;
  -webkit-font-smoothing: antialiased;
  text-rendering: geometricPrecision;
}

button,
input,
textarea,
select {
  color: inherit;
  font: inherit;
}

button {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

a {
  color: inherit;
  text-decoration: none;
}

h1,
h2,
h3,
p {
  margin: 0;
}

strong,
span,
small {
  min-width: 0;
}

button,
a {
  -webkit-tap-highlight-color: transparent;
}


/* =========================================================
   APP SHELL
========================================================= */

.emr-shell {
  width: 100vw;
  height: 100vh;
  min-width: 1672px;
  min-height: 941px;

  display: grid;

  grid-template-columns:
    var(--sidebar-width)
    minmax(0, 1fr);

  grid-template-rows:
    var(--topbar-height)
    minmax(0, 1fr)
    var(--statusbar-height);

  overflow: hidden;
  background: var(--color-canvas);
}


/* =========================================================
   TOPBAR
========================================================= */

.topbar {
  grid-column: 1 / -1;
  grid-row: 1;

  display: grid;
  grid-template-columns:
    var(--sidebar-width)
    minmax(0, 1fr);

  min-width: 0;

  background: var(--color-header);
  border-bottom: 1px solid var(--color-border);
}

.topbar-brand {
  display: flex;
  align-items: center;

  gap: 11px;
  padding-left: 32px;

  border-right: 1px solid var(--color-border);
}

.brand-symbol {
  width: 27px;
  height: 29px;
  flex: 0 0 auto;

  border: 3px solid #2680f7;
  border-radius: 9px;

  transform: rotate(0deg);
}

.brand-name {
  overflow: hidden;

  font-size: 16px;
  font-weight: 700;
  line-height: 20px;
  white-space: nowrap;
}

.topbar-content {
  min-width: 0;

  display: flex;
  align-items: center;
  justify-content: space-between;

  gap: 24px;

  padding-right: 20px;
  padding-left: 24px;
}

.search-control {
  width: 363px;
  height: 40px;
  flex: 0 0 363px;

  display: grid;
  grid-template-columns:
    20px
    minmax(0, 1fr)
    auto;

  align-items: center;
  gap: 10px;

  padding: 0 14px;

  background: #ffffff;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-control);
}

.search-icon {
  width: 16px;
  height: 16px;

  border: 1.7px solid #5d6570;
  border-radius: 50%;

  position: relative;
}

.search-icon::after {
  content: "";
  position: absolute;

  width: 6px;
  height: 1.7px;

  right: -5px;
  bottom: -2px;

  background: #5d6570;
  border-radius: 999px;
  transform: rotate(45deg);
}

.search-placeholder {
  overflow: hidden;

  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 450;

  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-shortcut {
  color: var(--color-text-muted);
  font-size: 12px;
  font-weight: 550;
}

.topbar-actions {
  display: flex;
  align-items: center;

  gap: 9px;
  flex: 0 0 auto;
}

.topbar-wide-button,
.topbar-icon-button {
  height: 40px;

  background: #ffffff;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-control);
}

.topbar-wide-button {
  min-width: 124px;
  padding: 0 16px;

  font-size: 13px;
  font-weight: 600;
}

.topbar-icon-button {
  width: 47px;
  flex: 0 0 47px;
}

.profile-control {
  height: 48px;
  min-width: 145px;

  display: grid;
  grid-template-columns:
    36px
    minmax(0, 1fr)
    16px;

  align-items: center;
  column-gap: 10px;

  padding: 0 0 0 8px;
}

.profile-avatar {
  width: 36px;
  height: 36px;

  background: #eef0f3;
  border-radius: 50%;
}

.profile-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;

  gap: 1px;
}

.profile-text strong {
  font-size: 13px;
  font-weight: 650;
  line-height: 18px;
}

.profile-text small {
  color: var(--color-text-muted);
  font-size: 11px;
  line-height: 16px;
}

.profile-chevron {
  width: 7px;
  height: 7px;

  border-right: 1.5px solid #444a53;
  border-bottom: 1.5px solid #444a53;

  transform: rotate(45deg) translateY(-2px);
}


/* =========================================================
   SIDEBAR
========================================================= */

.sidebar {
  grid-column: 1;
  grid-row: 2;

  min-height: 0;

  display: grid;
  grid-template-rows:
    minmax(0, 1fr)
    36px;

  row-gap: 12px;

  padding:
    25px
    18px
    14px
    12px;

  overflow: hidden;

  background: var(--color-sidebar);
  border-right: 1px solid var(--color-border);
}

.sidebar-scroll {
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;

  scrollbar-width: none;
}

.sidebar-scroll::-webkit-scrollbar {
  display: none;
}

.sidebar-section-title {
  margin:
    0
    0
    11px
    18px;

  color: #727985;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;

  gap: 0;
}

.sidebar-item {
  width: 100%;
  height: 44px;

  position: relative;

  display: grid;
  grid-template-columns:
    22px
    minmax(0, 1fr);

  align-items: center;
  column-gap: 12px;

  padding:
    0
    15px
    0
    19px;

  color: #5d6570;
  border-radius: var(--radius-nav);

  font-size: 14px;
  font-weight: 500;
}

.sidebar-item:hover {
  background: #f2f3f5;
}

.sidebar-item.is-active {
  color: var(--color-primary);
  background: var(--color-primary-soft);
  font-weight: 650;
}

.sidebar-item.is-active::before {
  content: "";

  position: absolute;
  top: 4px;
  bottom: 4px;
  left: 0;

  width: 3px;

  background: var(--color-primary);
  border-radius: 0 999px 999px 0;
}

.sidebar-icon {
  width: 18px;
  height: 18px;

  display: block;

  border: 1.5px solid currentColor;
  border-radius: 5px;

  opacity: 0.84;
}

.sidebar-favorites {
  margin-top: 34px;
  padding-top: 22px;

  border-top: 1px solid var(--color-border-soft);
}

.sidebar-nav-compact .sidebar-item {
  height: 32px;
  grid-template-columns:
    18px
    minmax(0, 1fr);

  column-gap: 10px;
  padding-left: 18px;

  font-size: 12px;
}

.sidebar-nav-compact .sidebar-icon {
  width: 13px;
  height: 13px;
  border-radius: 3px;
}

.sidebar-edit-button {
  width: 100%;
  height: 36px;

  display: flex;
  align-items: center;

  gap: 10px;
  padding: 0 13px;

  color: var(--color-text-secondary);
  background: #ffffff;

  border: 1px solid var(--color-border-strong);
  border-radius: 8px;

  font-size: 12px;
  font-weight: 500;
}

.sidebar-edit-button .sidebar-icon {
  width: 14px;
  height: 14px;
}


/* =========================================================
   WORKSPACE
========================================================= */

.workspace {
  grid-column: 2;
  grid-row: 2;

  min-width: 0;
  min-height: 0;

  padding:
    var(--workspace-padding-top)
    var(--workspace-padding-right)
    var(--workspace-padding-bottom)
    var(--workspace-padding-left);

  overflow: hidden;

  background: var(--color-canvas);
}

.workspace-layout {
  width: 100%;
  height: 100%;

  min-width: 0;
  min-height: 0;

  display: grid;

  grid-template-columns:
    minmax(0, 1fr)
    var(--assistant-width);

  column-gap: var(--assistant-gap);
}


/* =========================================================
   DASHBOARD CORE
========================================================= */

.dashboard-core {
  min-width: 0;
  min-height: 0;

  display: grid;

  grid-template-rows:
    var(--heading-height)
    var(--summary-height)
    minmax(0, 1fr);

  row-gap: var(--section-gap);
}


/* =========================================================
   PAGE HEADING
========================================================= */

.page-heading {
  width: 1026px;
  max-width: 100%;

  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  gap: 24px;
}

.page-heading-copy {
  min-width: 0;
}

.page-heading-copy h1 {
  color: #111317;

  font-size: 24px;
  font-weight: 720;
  line-height: 31px;

  letter-spacing: -0.035em;
}

.page-heading-copy p {
  margin-top: 4px;

  color: var(--color-text-muted);

  font-size: 13px;
  font-weight: 400;
  line-height: 19px;
}

.page-heading-actions {
  display: flex;
  align-items: center;

  gap: 16px;
  flex: 0 0 auto;

  margin-top: 10px;
}

.page-date {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 32px;
}

.secondary-button {
  height: 34px;
  padding: 0 16px;

  color: #3d434c;
  background: #ffffff;

  border: 1px solid var(--color-border-strong);
  border-radius: 9px;

  font-size: 12px;
  font-weight: 600;
}


/* =========================================================
   SUMMARY
========================================================= */

.summary-grid {
  width: 1026px;
  height: var(--summary-height);

  display: grid;

  grid-template-columns:
    240px
    240px
    240px
    264px;

  column-gap: 14px;
}

.summary-card {
  min-width: 0;
  min-height: 0;

  display: grid;
  grid-template-columns:
    29px
    minmax(0, 1fr);

  align-items: start;
  column-gap: 15px;

  padding:
    17px
    19px;

  overflow: hidden;

  background: var(--color-panel);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-panel);
}

.summary-icon {
  width: 25px;
  height: 25px;

  display: block;

  margin-top: 3px;

  border: 1.8px solid #20242b;
  border-radius: 6px;
}

.summary-copy {
  min-width: 0;

  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.summary-label {
  color: #454b54;

  font-size: 12px;
  font-weight: 650;
  line-height: 18px;
}

.summary-value {
  margin-top: 2px;

  color: #171a1f;

  font-size: 22px;
  font-weight: 720;
  line-height: 29px;

  letter-spacing: -0.025em;
  font-variant-numeric: tabular-nums;
}

.summary-description {
  margin-top: 1px;

  color: var(--color-text-muted);

  font-size: 11px;
  font-weight: 450;
  line-height: 16px;
}


/* =========================================================
   PRIMARY GRID
========================================================= */

.primary-grid {
  min-width: 0;
  min-height: 0;

  display: grid;

  grid-template-columns:
    var(--primary-left-width)
    minmax(0, 1fr)
    var(--primary-right-width);

  column-gap: var(--primary-gap);
}

.primary-grid > * {
  min-width: 0;
  min-height: 0;
}


/* =========================================================
   COMMON PANEL
========================================================= */

.panel {
  min-width: 0;
  min-height: 0;

  overflow: hidden;

  background: var(--color-panel);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-panel);
}

.panel-header {
  height: 54px;
  flex: 0 0 54px;

  display: flex;
  align-items: center;
  justify-content: space-between;

  gap: 12px;

  padding: 0 16px;

  border-bottom: 1px solid var(--color-border-soft);
}

.panel-header strong {
  color: #30343a;

  font-size: 13px;
  font-weight: 680;
  line-height: 18px;
}

.panel-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;

  min-width: 32px;
  height: 22px;

  color: var(--color-text-muted);

  font-size: 10px;
  font-weight: 600;
}

.panel-scroll {
  min-width: 0;
  min-height: 0;

  overflow-y: auto;
  overflow-x: hidden;

  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: #d9dde2 transparent;
}

.panel-scroll::-webkit-scrollbar {
  width: 5px;
}

.panel-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.panel-scroll::-webkit-scrollbar-thumb {
  background: #d9dde2;
  border-radius: 999px;
}

.panel-footer {
  height: 48px;

  display: flex;
  align-items: center;
  justify-content: center;

  padding: 0 14px;

  background: #ffffff;
  border-top: 1px solid var(--color-border-soft);
}

.panel-footer-button {
  color: #555c66;
  font-size: 12px;
  font-weight: 550;
}


/* =========================================================
   SCHEDULE PANEL
========================================================= */

.schedule-panel {
  display: grid;

  grid-template-rows:
    54px
    36px
    minmax(0, 1fr)
    48px;
}

.schedule-column-header {
  display: grid;

  grid-template-columns:
    68px
    minmax(0, 1fr)
    54px;

  align-items: center;

  padding:
    0
    15px;

  color: #656d78;
  background: #fcfcfd;

  border-bottom: 1px solid var(--color-border-soft);

  font-size: 11px;
  font-weight: 550;
}

.schedule-column-header span:last-child {
  text-align: right;
}

.schedule-list {
  background: #ffffff;
}

.schedule-row {
  height: 42px;

  display: grid;

  grid-template-columns:
    68px
    minmax(0, 1fr)
    54px;

  align-items: center;

  padding:
    0
    15px;

  border-bottom: 1px solid var(--color-border-soft);

  font-size: 12px;
}

.schedule-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schedule-row span:first-child {
  color: #3f464f;
  font-variant-numeric: tabular-nums;
}

.schedule-row span:nth-child(2) {
  color: #282c32;
  font-weight: 550;
}

.schedule-row span:last-child {
  color: #69717c;
  text-align: right;
}


/* =========================================================
   PATIENT PANEL
========================================================= */

.patient-panel {
  display: grid;

  grid-template-rows:
    94px
    46px
    minmax(0, 1fr)
    48px;
}

.patient-summary {
  min-width: 0;

  display: grid;

  grid-template-columns:
    38px
    minmax(0, 1fr)
    auto;

  align-items: start;
  column-gap: 12px;

  padding:
    15px
    15px
    11px;

  border-bottom: 1px solid var(--color-border-soft);
}

.patient-avatar {
  width: 38px;
  height: 38px;

  background: #eee4ff;
  border-radius: 50%;
}

.patient-information {
  min-width: 0;
}

.patient-name-row {
  min-width: 0;

  display: flex;
  align-items: center;

  gap: 10px;

  overflow: hidden;
  white-space: nowrap;
}

.patient-name-row strong {
  color: #20242a;

  font-size: 16px;
  font-weight: 720;
  line-height: 22px;
}

.patient-name-row span {
  color: #717984;

  font-size: 11px;
  line-height: 20px;
}

.patient-divider {
  width: 1px;
  height: 13px;
  flex: 0 0 1px;

  background: var(--color-border);
}

.patient-information p {
  margin-top: 5px;

  overflow: hidden;

  color: #4f5661;

  font-size: 12px;
  line-height: 17px;

  text-overflow: ellipsis;
  white-space: nowrap;
}

.patient-tags {
  display: flex;
  align-items: center;

  gap: 10px;

  margin-top: 8px;
}

.patient-tags span {
  color: #5f6670;
  font-size: 11px;
  line-height: 16px;
}

.patient-actions {
  display: flex;
  align-items: center;

  gap: 8px;
}

.small-button {
  height: 30px;
  padding: 0 13px;

  background: #ffffff;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;

  font-size: 11px;
  font-weight: 600;
}

.square-button {
  width: 30px;
  height: 30px;

  background: #ffffff;
  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
}

.patient-tabs {
  display: flex;
  align-items: flex-end;

  gap: 4px;

  padding: 0 8px;

  border-bottom: 1px solid var(--color-border);
}

.patient-tab {
  height: 46px;

  position: relative;

  padding: 0 14px;

  color: #5e6671;

  font-size: 12px;
  font-weight: 500;
}

.patient-tab.is-active {
  color: var(--color-primary);
  font-weight: 650;
}

.patient-tab.is-active::after {
  content: "";

  position: absolute;
  right: 9px;
  bottom: -1px;
  left: 9px;

  height: 2px;

  background: var(--color-primary);
  border-radius: 999px 999px 0 0;
}

.patient-document {
  padding:
    15px
    15px
    18px;
}

.document-row {
  display: grid;

  grid-template-columns:
    19px
    minmax(0, 1fr);

  column-gap: 8px;

  margin-bottom: 18px;
}

.document-letter {
  color: #111317;

  font-size: 17px;
  font-weight: 750;
  line-height: 22px;
}

.document-content h3 {
  color: #25292f;

  font-size: 12px;
  font-weight: 680;
  line-height: 20px;
}

.document-content p {
  margin-top: 2px;

  color: #5c646f;

  font-size: 11px;
  line-height: 18px;
}

.patient-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;

  gap: 12px;

  padding:
    0
    15px;

  border-top: 1px solid var(--color-border-soft);
}

.patient-modified {
  color: #8a919b;

  font-size: 10px;
  font-variant-numeric: tabular-nums;
}

.patient-tool-buttons {
  display: flex;
  align-items: center;

  gap: 7px;
}

.patient-tool-buttons button {
  width: 30px;
  height: 30px;

  border: 1px solid var(--color-border-strong);
  border-radius: 8px;
}


/* =========================================================
   ORDER PANEL
========================================================= */

.order-panel {
  display: grid;

  grid-template-rows:
    54px
    minmax(0, 1fr)
    54px;
}

.plain-icon-button {
  width: 26px;
  height: 26px;
}

.order-content {
  background: #ffffff;
}

.order-section {
  padding:
    15px
    16px;

  border-bottom: 1px solid var(--color-border-soft);
}

.order-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;

  gap: 10px;

  margin-bottom: 10px;
}

.order-section-title strong {
  color: #343940;

  font-size: 12px;
  font-weight: 680;
}

.order-section-title span {
  min-width: 20px;
  height: 20px;

  display: inline-flex;
  align-items: center;
  justify-content: center;

  color: #737b86;
  background: #f4f5f6;

  border-radius: 999px;

  font-size: 10px;
}

.order-list {
  display: flex;
  flex-direction: column;

  gap: 9px;
}

.order-row {
  min-height: 22px;

  display: flex;
  align-items: center;

  color: #555d68;

  font-size: 11px;
  line-height: 16px;
}

.wide-outline-button {
  width: 100%;
  height: 32px;

  color: #464d56;
  background: #ffffff;

  border: 1px solid var(--color-border-strong);
  border-radius: 8px;

  font-size: 11px;
  font-weight: 600;
}


/* =========================================================
   AI ASSISTANT
========================================================= */

.assistant-panel {
  min-width: 0;
  min-height: 0;

  margin-top: var(--assistant-top-offset);

  display: grid;

  grid-template-rows:
    54px
    minmax(0, 1fr)
    36px;

  overflow: hidden;

  background:
    linear-gradient(
      180deg,
      #ffffff 0%,
      #fbfdff 100%
    );

  border: 1px solid var(--color-primary-border);
  border-radius: var(--radius-assistant);

  box-shadow:
    0 8px 28px rgba(22, 113, 226, 0.035);
}

.assistant-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  gap: 12px;

  padding:
    0
    17px;

  border-bottom: 1px solid rgba(232, 234, 237, 0.65);
}

.assistant-title {
  display: flex;
  align-items: center;

  gap: 10px;
}

.assistant-symbol {
  width: 17px;
  height: 17px;

  position: relative;
}

.assistant-symbol::before,
.assistant-symbol::after {
  content: "";

  position: absolute;
  inset: 3px;

  background: var(--color-primary);
  transform: rotate(45deg);
  border-radius: 2px;
}

.assistant-symbol::after {
  inset: 6px;
  background: #ffffff;
}

.assistant-title strong {
  color: #26354a;

  font-size: 14px;
  font-weight: 700;
}

.assistant-close {
  width: 28px;
  height: 28px;

  position: relative;
}

.assistant-close::before,
.assistant-close::after {
  content: "";

  position: absolute;
  top: 13px;
  left: 8px;

  width: 12px;
  height: 1px;

  background: #616975;
}

.assistant-close::before {
  transform: rotate(45deg);
}

.assistant-close::after {
  transform: rotate(-45deg);
}

.assistant-body {
  min-height: 0;

  overflow-y: auto;
  overflow-x: hidden;

  padding:
    9px
    14px
    16px;

  scrollbar-width: none;
}

.assistant-body::-webkit-scrollbar {
  display: none;
}

.assistant-section {
  padding-top: 6px;
}

.assistant-section + .assistant-section {
  margin-top: 24px;
  padding-top: 21px;

  border-top: 1px solid var(--color-border-soft);
}

.assistant-section h2 {
  margin:
    0
    0
    12px
    2px;

  color: #525b67;

  font-size: 11px;
  font-weight: 650;
  line-height: 17px;
}

.assistant-action-list {
  display: flex;
  flex-direction: column;

  gap: 7px;
}

.assistant-action {
  width: 100%;
  height: 51px;

  background: #ffffff;

  border: 1px solid var(--color-border);
  border-radius: 10px;
}

.assistant-insight-section {
  display: flex;
  flex-direction: column;

  gap: 11px;
}

.insight-card {
  width: 100%;

  background: #ffffff;
  border: 1px solid var(--color-border);
  border-radius: 11px;
}

.insight-chart {
  height: 126px;
}

.insight-tip {
  height: 98px;
}

.assistant-footer {
  display: flex;
  align-items: center;

  padding: 0 15px;

  overflow: hidden;

  color: #979da5;

  font-size: 9px;
  line-height: 13px;

  text-overflow: ellipsis;
  white-space: nowrap;
}


/* =========================================================
   STATUS BAR
========================================================= */

.statusbar {
  grid-column: 1 / -1;
  grid-row: 3;

  display: flex;
  align-items: center;

  gap: 27px;

  padding:
    0
    28px;

  color: #6c737d;
  background: var(--color-statusbar);

  border-top: 1px solid var(--color-border);

  font-size: 10px;
  line-height: 14px;

  font-variant-numeric: tabular-nums;
}

.connection-state {
  position: relative;
  padding-left: 12px;
}

.connection-state::before {
  content: "";

  position: absolute;
  top: 50%;
  left: 0;

  width: 5px;
  height: 5px;

  background: #23a66f;
  border-radius: 50%;

  transform: translateY(-50%);
}
```

---

# 4. 1672×941 기준 실제 배치 계산

CSS가 정상이라면 주요 영역 좌표가 아래처럼 나와야 한다.

## 메인 콘텐츠

```text
사이드바 너비       234px
워크스페이스 왼쪽 여백 30px

메인 콘텐츠 x = 234 + 30
              = 264px
```

```text
전체 너비              1672px
사이드바                234px
좌우 워크스페이스 여백   60px
AI 패널                 274px
메인-AI 간격             20px

메인 콘텐츠 너비
= 1672 - 234 - 60 - 274 - 20
= 1084px
```

## 메인 콘텐츠 높이

```text
전체 높이          941px
상단 헤더           70px
하단 상태바         36px
워크스페이스 높이   835px
```

```text
워크스페이스 높이   835px
상단 여백            29px
하단 여백            16px

실제 내부 높이
= 835 - 29 - 16
= 790px
```

```text
인사 영역           54px
간격                20px
요약 카드          100px
간격                20px
하단 패널          596px

총합
= 54 + 20 + 100 + 20 + 596
= 790px
```

따라서 Y 좌표는 정확히 다음처럼 구성된다.

```text
메인 시작       y = 70 + 29 = 99
인사 종료       y = 153
요약 시작       y = 173
요약 종료       y = 273
하단 패널 시작  y = 293
하단 패널 종료  y = 889
```

AI 패널은 메인 시작점보다 `25px` 아래에서 시작한다.

```text
AI 시작 y = 99 + 25 = 124
AI 종료 y = 889
AI 높이 = 765px
```

---

# 5. 원본처럼 보이게 만드는 핵심 CSS 규칙

## 모든 Grid 자식에 `min-width: 0`

중앙 환자 패널 안에 긴 텍스트가 들어가면 Grid 열 너비를 밀어내는 문제가 생긴다.

```css
.workspace-layout,
.dashboard-core,
.primary-grid,
.primary-grid > *,
.patient-information {
  min-width: 0;
}
```

이 설정이 없으면 중앙 패널이 커지면서 오른쪽 오더 패널과 AI 패널 위치가 틀어진다.

## 페이지 전체 스크롤 금지

이 화면은 브라우저 전체가 스크롤되는 방식이 아니다.

```css
html,
body,
.emr-shell {
  overflow: hidden;
}
```

스크롤은 다음 영역에만 둔다.

```css
.sidebar-scroll,
.panel-scroll,
.assistant-body {
  overflow-y: auto;
}
```

## 카드 그림자를 거의 사용하지 않기

원본은 그림자가 아니라 `1px border`로 영역을 구분한다.

```css
.panel,
.summary-card {
  border: 1px solid #e8eaed;
  box-shadow: none;
}
```

AI 패널에만 아주 약한 푸른 그림자를 허용한다.

```css
.assistant-panel {
  box-shadow:
    0 8px 28px rgba(22, 113, 226, 0.035);
}
```

## 카드 반경을 전부 같게 하지 않기

```css
.summary-card,
.panel {
  border-radius: 12px;
}

.assistant-panel {
  border-radius: 20px;
}

.search-control,
.topbar-wide-button,
.topbar-icon-button {
  border-radius: 10px;
}
```

AI 패널 반경이 다른 일반 카드보다 훨씬 크다.

## 중앙 하단 패널만 유동 너비

```css
.primary-grid {
  grid-template-columns:
    260px
    minmax(0, 1fr)
    236px;
}
```

좌측 일정과 우측 오더는 고정하고, 화면이 넓어졌을 때 중앙 환자 차트만 늘어나게 해야 한다.

---

# 6. 반응형 처리 방식

이 디자인을 처음부터 모바일 카드형으로 재배치하면 원본과의 비교가 어려워진다. 우선 데스크톱을 완성한 뒤 별도 반응형을 추가하는 방식이 맞다.

픽셀 비교 단계에서는 다음 설정을 유지한다.

```css
body,
.emr-shell {
  min-width: 1672px;
  min-height: 941px;
}
```

실제 서비스 단계에서 최소 데스크톱 폭을 낮추려면 요약 카드만 유동형으로 전환한다.

```css
@media (max-width: 1671px) {
  body,
  .emr-shell {
    min-width: 1440px;
  }

  .summary-grid {
    width: 100%;

    grid-template-columns:
      repeat(4, minmax(0, 1fr));
  }

  .page-heading {
    width: 100%;
  }
}
```

다만 이 미디어쿼리를 적용한 상태는 첨부 이미지와 픽셀 단위로 동일한 상태는 아니다.

---

# 7. 이미지 오버레이로 검수하는 방법

참고 이미지를 프로젝트에 `reference.png`로 저장한 뒤 아래 비교용 CSS를 추가한다.

```css
body.is-comparing::after {
  content: "";

  position: fixed;
  inset: 0;
  z-index: 999999;

  width: 1672px;
  height: 941px;

  pointer-events: none;

  background:
    url("./reference.png")
    left top
    / 1672px 941px
    no-repeat;

  opacity: 0.32;
  mix-blend-mode: multiply;
}
```

HTML의 `body`에 클래스를 넣으면 된다.

```html
<body class="is-comparing">
```

개발자 도구에서는 뷰포트를 반드시 다음으로 맞춘다.

```text
Width: 1672
Height: 941
Device pixel ratio: 1
Zoom: 100%
```

검수 순서는 다음과 같이 고정한다.

1. 헤더 하단선이 `y=70`인지 확인
2. 사이드바 오른쪽선이 `x=234`인지 확인
3. 메인 첫 카드 시작점이 `x=264`인지 확인
4. 하단 패널 시작점이 `y=293`인지 확인
5. AI 패널이 `x=1368`, `y=124`인지 확인
6. 하단 상태바가 `y=905`에서 시작하는지 확인
7. 마지막으로 글자 크기와 내부 패딩 조정

처음부터 글자와 아이콘을 조정하면 바깥 레이아웃 오차를 찾기 어렵다.

---

# 8. 원본과 달라지는 대표적인 구현 실수

```css
/* 잘못된 예시 */
.summary-grid {
  grid-template-columns: repeat(4, 1fr);
}
```

상단 마지막 카드가 너무 길어져 원본과 달라진다.

```css
/* 잘못된 예시 */
.primary-grid {
  grid-template-columns: 1fr 2fr 1fr;
}
```

왼쪽 일정 패널과 오른쪽 오더 패널 비율이 원본과 달라진다.

```css
/* 잘못된 예시 */
.workspace {
  max-width: 1440px;
  margin: 0 auto;
}
```

메인 콘텐츠와 AI 패널이 중앙 정렬되면서 사이드바 기준 좌표가 틀어진다.

```css
/* 잘못된 예시 */
.panel {
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.08);
}
```

원본보다 카드가 지나치게 떠 보이고 일반적인 AI 생성 대시보드처럼 보인다.

```css
/* 잘못된 예시 */
body {
  overflow-y: auto;
}
```

하단 상태바가 화면 아래로 밀리고 각 패널의 높이가 일정하지 않게 된다.

가장 중요한 것은 **헤더 70px, 사이드바 234px, 상태바 36px, 메인 1084px, AI 274px의 외곽 구조를 먼저 절대 좌표 수준으로 맞추는 것**이다. 내부 문구와 아이콘은 그 이후에 교체해야 한다.
