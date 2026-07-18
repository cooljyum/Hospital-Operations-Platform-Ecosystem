-- Phase 3 Step 3.3: AUDIT_LOG 스키마 — Phase 5.1을 앞당겨 지금 만든다.
--
-- 배경: PLAN.md Phase 5.1이 AUDIT_LOG 스키마(행위자/대상PK/행위/목적/마스킹여부/IP/
-- 성공여부/시각, 쿼리 원문 미저장)를 정식으로 만들기로 계획돼 있었지만, 이번 Step 3.3
-- (break-glass)이 그보다 먼저 AUDIT_LOG에 실제로 기록해야 하는 상황이다. 그래서 이
-- 마이그레이션에서 Phase 5.1 원문이 요구하는 컬럼 구성을 최대한 반영해 AUDIT_LOG를
-- 만든다 — Phase 5.1은 이 스키마를 검토·필요시 보완만 하면 된다(예: 인덱스 추가,
-- 조회 화면에 맞춘 컬럼 보강 등). 이 사실은 커밋 메시지에도 명시한다.
--
-- 컬럼 설계 (deliverable.md §3.3/Phase 5.1 원문 그대로):
--   행위자        -> actor_username (지금은 로그인 계정명만 기록. 별도 actor_id FK는
--                    Phase 5.1에서 필요성 재검토)
--   대상 PK       -> target_pk (불변 내부 PK 참조 문자열 — 프로젝트 원칙상 표시용 식별자가
--                    아니라 항상 불변 PK를 기록해야 함. break-glass처럼 특정 레코드
--                    대상이 없는 이벤트는 NULL 허용)
--   행위          -> action (예: "BREAK_GLASS_ACCESS_GRANT")
--   목적          -> purpose (break-glass의 "사유" 필수 입력값이 여기로 들어간다)
--   마스킹 여부   -> masked
--   IP            -> ip_address
--   성공 여부     -> success
--   시각          -> occurred_at
--   (+break-glass 전용) result_code — "전용 결과 코드"로 기록하라는 AC 요구사항.
--   지금은 BREAK_GLASS_GRANTED 하나만 쓰이지만, Phase 5.1/이후 이벤트 확장을 고려해
--   범용 컬럼으로 둔다(SUCCESS/FAILURE 같은 일반 코드도 이 컬럼에 넣을 수 있게).
--
-- 쿼리 원문(SQL 리터럴)은 어떤 컬럼에도 저장하지 않는다 — purpose는 사용자가 입력한
-- 자유텍스트 사유일 뿐, SQL 파라미터 바인딩 값이 아니다.
CREATE TABLE AUDIT_LOG (
    audit_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_username  VARCHAR(50)  NOT NULL,
    target_pk       VARCHAR(100) NULL,
    action          VARCHAR(100) NOT NULL,
    purpose         VARCHAR(500) NULL,
    masked          BOOLEAN      NOT NULL DEFAULT FALSE,
    ip_address      VARCHAR(45)  NULL,
    success         BOOLEAN      NOT NULL,
    result_code     VARCHAR(50)  NOT NULL,
    occurred_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_audit_log_actor_occurred ON AUDIT_LOG (actor_username, occurred_at);
CREATE INDEX idx_audit_log_result_code ON AUDIT_LOG (result_code);
