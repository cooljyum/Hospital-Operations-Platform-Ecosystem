-- Phase 1 Step 1.2: 표시용 식별자(synthetic_patient_no) 분리
--
-- 설계 판단: PLAN.md 원문이 "컬럼 분리 + 매핑 테이블"을 함께 언급하므로 둘을 병행한다.
--   1) PATIENT.synthetic_patient_no  : 현재 유효한 표시용 ID를 PATIENT 행에 직접 두어
--      화면 조회 시 조인 없이 바로 꺼내 쓰는 통상 경로를 빠르게 한다.
--   2) PATIENT_DISPLAY_ID 매핑 테이블 : 표시용 ID는 유출·오남용 시 재발급될 수 있어야
--      하므로, 발급 이력(언제 어떤 값이 어떤 patient_id에 매핑됐는지, 현재 활성 여부)을
--      별도로 남긴다. PATIENT.synthetic_patient_no는 이 이력 중 "현재 활성" 값의 캐시다.
--
-- 내부 불변 PK(patient_id)는 절대 외부 노출하지 않고, 화면·API 등 외부 노출에는 이
-- synthetic_patient_no만 사용한다. 이 컬럼/테이블 어디에도 주민등록번호(RRN) 계열
-- 데이터는 없다 — 오직 무작위/해시 기반의 표시용 문자열만 저장한다.

ALTER TABLE PATIENT
    ADD COLUMN synthetic_patient_no VARCHAR(32) NULL AFTER source_patient_uuid;

CREATE TABLE PATIENT_DISPLAY_ID (
    display_id_seq        BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id             BIGINT       NOT NULL,
    synthetic_patient_no    VARCHAR(32)  NOT NULL,
    issued_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active              TINYINT(1)   NOT NULL DEFAULT 1,
    CONSTRAINT uq_patient_display_id_no UNIQUE (synthetic_patient_no),
    CONSTRAINT fk_patient_display_id_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- synthetic_patient_no는 발급 즉시 채워지므로(적재 배치가 PATIENT_DISPLAY_ID에 먼저 기록한
-- 뒤 PATIENT에 캐시), 안정화 이후에는 NOT NULL + UNIQUE로 굳힌다. 이 마이그레이션 시점에는
-- 기존 행이 없으므로(V1 직후) 즉시 제약을 걸어도 안전하다.
ALTER TABLE PATIENT
    MODIFY COLUMN synthetic_patient_no VARCHAR(32) NOT NULL,
    ADD CONSTRAINT uq_patient_synthetic_no UNIQUE (synthetic_patient_no);
