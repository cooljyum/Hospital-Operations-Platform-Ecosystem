-- Phase 6 Step 6.1: 환자별 방문 요약 물리 테이블(PATIENT_VISIT_SUMMARY)
--
-- 배경: 환자 대시보드/조회 화면에서 "이 환자가 지금까지 몇 번 방문했는지, 마지막 방문이
-- 언제인지, 검사/처방 이력이 몇 건인지"는 매 조회마다 VISIT/LAB_RESULT/PRESCRIPTION을
-- 각각 GROUP BY로 집계해야 하는 흔한 질의다. 데이터가 커질수록 매 요청마다 3개 테이블을
-- COUNT/MIN/MAX로 재집계하는 비용이 누적되므로, 이 결과를 물리 테이블로 미리 계산해
-- 두고 Spring Batch job(SummaryRefreshJob, Step 6.1 후속 커밋)이 명시적으로 트리거될 때만
-- 전체 재계산하는 패턴을 둔다(Oracle 분기의 MV 대안은 PLAN.md §0 각주대로 Phase 10에서
-- 재결정 — 이 마이그레이션은 MySQL 단일 구현만 다룬다).
--
-- patient_id는 PATIENT.patient_id 1:1 매핑이며, 방문/검사/처방이 하나도 없는 환자도
-- refresh 시 0건 행으로 채워진다(집계 job이 LEFT JOIN 기반이므로).

CREATE TABLE PATIENT_VISIT_SUMMARY (
    patient_id             BIGINT       NOT NULL PRIMARY KEY,
    visit_count            BIGINT       NOT NULL DEFAULT 0,
    first_visit_at         DATETIME     NULL,
    last_visit_at          DATETIME     NULL,
    lab_result_count       BIGINT       NOT NULL DEFAULT 0,
    prescription_count     BIGINT       NOT NULL DEFAULT 0,
    refreshed_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_patient_visit_summary_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
