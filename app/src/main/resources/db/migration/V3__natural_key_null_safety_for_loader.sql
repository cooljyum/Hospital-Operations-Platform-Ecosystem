-- Phase 1 Step 1.3: LAB_RESULT/PRESCRIPTION 자연키 보강 (SyntheaLoader 멱등성 전제조건)
--
-- 배경: V1에서 정의한 자연키(visit_id, code, observed_at) / (visit_id, medication_code,
-- started_at)만으로는 실제 Synthea 데이터에서 서로 다른 두 사실이 충돌한다.
--   - observations.csv: 같은 encounter/code/timestamp에 서로 다른 VALUE를 갖는 행이
--     실존한다(예: 같은 수술 절차 내러티브에 대해 "CABG Grafts: 3"과
--     "CABG Arterial Conduits: 2"가 같은 타임스탬프로 별도 관측치로 기록됨).
--   - medications.csv: 같은 encounter/code/시작일에서 STOP(종료일)만 다른 행이 실존한다
--     (동일 약을 같은 날 재처방하되 이번엔 더 길게 복용하는 경우).
-- 자연키를 (..., result_value) / (..., stopped_at)까지 넓혀야 이 두 사실을 서로 다른
-- 행으로 보존하면서도 멱등 upsert(재실행 시 중복 없음)를 유지할 수 있다.
--
-- 추가 함정 1: medications.csv의 STOP은 진행 중인 처방에서 공란(=NULL)일 수 있다(이번
-- 표본에서 16건). MySQL UNIQUE 제약은 NULL을 서로 다른 값으로 취급하므로(NULL <> NULL),
-- stopped_at이 NULL인 행끼리는 유니크 충돌이 발생하지 않아 재실행 시 중복 삽입된다.
-- 이를 막기 위해 NULL을 고정 센티널로 치환하는 STORED 생성 컬럼을 만들고, 유니크 제약은
-- 그 생성 컬럼에 건다(실제 result_value/stopped_at 컬럼 자체는 NULL 허용을 그대로 유지해
-- Phase 2 FHIR 매핑 시 "종료일 없음"의 의미를 보존한다).
--
-- 추가 함정 2: 기존 uq_lab_result_natural / uq_prescription_natural 인덱스는 visit_id를
-- 선두 컬럼으로 하는 유일한 인덱스라 각각의 FK(fk_lab_result_visit/fk_prescription_visit)
-- 제약을 뒷받침하고 있다. 이를 먼저 DROP하면 "needed in a foreign key constraint" 에러가
-- 난다 -> 항상 "새 인덱스 추가 -> 기존 인덱스 제거 -> 이름 정리" 순서를 지켜 FK가 항상
-- 인덱스를 가진 상태를 유지한다.

ALTER TABLE LAB_RESULT
    ADD COLUMN result_value_key VARCHAR(255)
        AS (COALESCE(result_value, '')) STORED NOT NULL AFTER result_value;

ALTER TABLE LAB_RESULT
    ADD CONSTRAINT uq_lab_result_natural_v2 UNIQUE (visit_id, code, observed_at, result_value_key);
ALTER TABLE LAB_RESULT DROP INDEX uq_lab_result_natural;
ALTER TABLE LAB_RESULT RENAME INDEX uq_lab_result_natural_v2 TO uq_lab_result_natural;

ALTER TABLE PRESCRIPTION
    ADD COLUMN stopped_at_key DATETIME
        AS (COALESCE(stopped_at, '9999-12-31 23:59:59')) STORED NOT NULL AFTER stopped_at;

ALTER TABLE PRESCRIPTION
    ADD CONSTRAINT uq_prescription_natural_v2 UNIQUE (visit_id, medication_code, started_at, stopped_at_key);
ALTER TABLE PRESCRIPTION DROP INDEX uq_prescription_natural;
ALTER TABLE PRESCRIPTION RENAME INDEX uq_prescription_natural_v2 TO uq_prescription_natural;
