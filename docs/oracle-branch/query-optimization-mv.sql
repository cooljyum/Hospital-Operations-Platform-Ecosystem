-- =====================================================================
-- Hospital FHIR Ops Lab — Oracle 19c 분기 조회 최적화: Materialized View
-- =====================================================================
-- MySQL 분기(V13, PATIENT_VISIT_SUMMARY)는 물리 요약 테이블 + Spring Batch
-- refresh job(SummaryRefreshJob/SummaryRefreshTasklet)으로 구현되어 있다.
-- Oracle 분기는 §0 미확정 사항을 재결정한 결과(README.md "결정 배경" 참고)
-- MySQL과 다르게 Oracle 네이티브 Materialized View를 채택한다 — 두 분기가
-- 각 DB의 관용적인 방식으로 같은 문제(반복 집계 비용)를 풀었음을 보여주는
-- 것이 이 분기 문서의 목적이다.
--
-- 집계 로직은 SummaryRefreshTasklet.REFRESH_INSERT(app/src/main/java/com/
-- hospitalops/batch/SummaryRefreshTasklet.java)의 SQL과 동일한 구조
-- (VISIT/LAB_RESULT/PRESCRIPTION을 각각 patient_id로 GROUP BY한 서브쿼리를
-- PATIENT에 LEFT JOIN)를 그대로 옮겼다 — 두 분기의 "무엇을 보여주는 화면인가"
-- 는 동일해야 하므로 집계 정의 자체는 이식했고, 그 정의를 물리화하는 수단만
-- DB별로 다르게 가져간다.
--
-- REFRESH 전략: ON DEMAND + COMPLETE. MySQL 쪽 SummaryRefreshJob이 "명시적
-- 트리거 시에만 전체 재계산"(증분 워터마크 없음)이라는 동일한 운영 패턴을
-- 쓰므로, Oracle MV도 ON COMMIT(각 원본 테이블 트랜잭션마다 자동 갱신)이 아닌
-- ON DEMAND로 맞춰 두 분기의 "언제 최신값이 되는가"에 대한 운영 설명이
-- 일치하게 했다. 갱신은 DBMS_MVIEW.REFRESH(...)를 배치에서 호출하는 방식으로,
-- MySQL의 SummaryRefreshJob과 같은 위치(운영자가 명시적으로 트리거하는
-- Spring Batch job)에서 실행하는 것을 전제로 문서화한다(Oracle 분기는 미착수
-- 상태이므로 이 Job 자체는 아직 Java로 구현되어 있지 않다 — README.md
-- "실기동 결과" 참고).

CREATE MATERIALIZED VIEW PATIENT_VISIT_SUMMARY_MV
    BUILD IMMEDIATE
    REFRESH COMPLETE ON DEMAND
    AS
    SELECT
        p.patient_id                           AS patient_id,
        COALESCE(v.visit_count, 0)               AS visit_count,
        v.first_visit_at                           AS first_visit_at,
        v.last_visit_at                               AS last_visit_at,
        COALESCE(l.lab_result_count, 0)                 AS lab_result_count,
        COALESCE(pr.prescription_count, 0)                 AS prescription_count,
        SYSTIMESTAMP                                         AS refreshed_at
    FROM PATIENT p
    LEFT JOIN (
        SELECT patient_id, COUNT(*) AS visit_count,
               MIN(started_at) AS first_visit_at, MAX(started_at) AS last_visit_at
        FROM VISIT
        GROUP BY patient_id
    ) v ON v.patient_id = p.patient_id
    LEFT JOIN (
        SELECT patient_id, COUNT(*) AS lab_result_count
        FROM LAB_RESULT
        GROUP BY patient_id
    ) l ON l.patient_id = p.patient_id
    LEFT JOIN (
        SELECT patient_id, COUNT(*) AS prescription_count
        FROM PRESCRIPTION
        GROUP BY patient_id
    ) pr ON pr.patient_id = p.patient_id;

-- MV 자체에 patient_id가 유일하므로, PATIENT_VISIT_SUMMARY(V13)의
-- "patient_id BIGINT PRIMARY KEY"와 동등한 조회 성능을 내려면 MV 위에
-- 조회용 인덱스를 별도로 둔다(MV는 결과 집합을 저장하는 실제 테이블 성격의
-- 오브젝트이므로 일반 인덱스를 만들 수 있다).
CREATE UNIQUE INDEX idx_patient_visit_summary_mv_pk ON PATIENT_VISIT_SUMMARY_MV (patient_id);

-- 운영 시 갱신 호출 예시(SummaryRefreshJob의 Oracle 분기 대응 Tasklet이 실행할 구문):
--   BEGIN
--       DBMS_MVIEW.REFRESH('PATIENT_VISIT_SUMMARY_MV', 'C');  -- 'C' = Complete refresh
--   END;
--   /

-- =====================================================================
-- 실측 절차 (README.md "실기동 결과"에 실제 출력 포함)
-- =====================================================================
-- 1) MV가 없다고 가정한 원본 집계 쿼리의 EXPLAIN PLAN(3-way GROUP BY 서브쿼리
--    + LEFT JOIN, HASH JOIN/GROUP BY 비용 다수 발생)과
-- 2) MV 조회(SELECT * FROM PATIENT_VISIT_SUMMARY_MV WHERE patient_id = :id)의
--    EXPLAIN PLAN(TABLE ACCESS BY INDEX ROWID 1건)을 비교해 비용 차이를 확인한다.

-- 원본 집계 쿼리(MV 없이 매번 재계산한다고 가정할 때의 비용 기준선)
EXPLAIN PLAN SET STATEMENT_ID = 'raw_aggregate' FOR
SELECT
    p.patient_id,
    COALESCE(v.visit_count, 0)       AS visit_count,
    v.first_visit_at,
    v.last_visit_at,
    COALESCE(l.lab_result_count, 0)  AS lab_result_count,
    COALESCE(pr.prescription_count, 0) AS prescription_count
FROM PATIENT p
LEFT JOIN (
    SELECT patient_id, COUNT(*) AS visit_count,
           MIN(started_at) AS first_visit_at, MAX(started_at) AS last_visit_at
    FROM VISIT
    GROUP BY patient_id
) v ON v.patient_id = p.patient_id
LEFT JOIN (
    SELECT patient_id, COUNT(*) AS lab_result_count
    FROM LAB_RESULT
    GROUP BY patient_id
) l ON l.patient_id = p.patient_id
LEFT JOIN (
    SELECT patient_id, COUNT(*) AS prescription_count
    FROM PRESCRIPTION
    GROUP BY patient_id
) pr ON pr.patient_id = p.patient_id
WHERE p.patient_id = 1;

SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'raw_aggregate', 'BASIC +COST +ROWS'));

-- MV 조회 비용
EXPLAIN PLAN SET STATEMENT_ID = 'mv_lookup' FOR
SELECT * FROM PATIENT_VISIT_SUMMARY_MV WHERE patient_id = 1;

SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, 'mv_lookup', 'BASIC +COST +ROWS'));
