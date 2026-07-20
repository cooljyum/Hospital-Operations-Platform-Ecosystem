-- Phase 10 Step 10.2: FHIR 리소스 확장 5번째 종 -- DIAGNOSIS(레거시) -> FHIR Condition
--
-- 배경: PLAN.md §12 Phase 10 Step 10.2 "FHIR 리소스 확장(4종 외 추가 리소스)". Synthea
-- conditions.csv(START/STOP/PATIENT/ENCOUNTER/SYSTEM/CODE/DESCRIPTION)를 적재할
-- 신규 legacy 테이블. Phase 1.1 원칙(불변 내부 PK만 식별자로 사용, RRN 미사용) 그대로
-- 따른다.
--
-- 설계 판단 1: code_system 컬럼을 별도로 둔다. LAB_RESULT.code/PRESCRIPTION.medication_code와
-- 달리, conditions.csv는 SYSTEM 컬럼에 이미 완전한 FHIR 코드시스템 URI를 직접 제공한다
-- (실측 결과 http://snomed.info/sct 뿐 아니라 http://hl7.org/fhir/sid/icd-10도 섞여
-- 있음 -- EncounterMapper의 encounter_code처럼 단일 상수로 고정할 수 없다). 따라서 이
-- 도메인은 CODE_SET(Step 2.2) 변환이 필요 없다 -- 원본 시스템 URI를 그대로 컬럼에
-- 보존해 두었다가 DiagnosisMapper가 그 값을 곧바로 Coding.system에 쓴다.
--
-- 설계 판단 2: diagnosed_at(START)은 실측 결과 295건 전부 공란이 없어 NOT NULL로
-- 정의한다. resolved_at(STOP)은 86/295건만 존재(진행 중인 진단이 다수)하므로 NULL 허용.
-- 자연키(visit_id, code, diagnosed_at)는 실측 데이터에서 충돌이 없음을 확인했다(V3가
-- LAB_RESULT/PRESCRIPTION에 적용한 STORED 컬럼 우회가 필요한 사례는 발견되지 않음).
--
-- description 폭(500)은 V4가 다른 자유 텍스트 설명 컬럼에 적용한 여유치와 동일하게 맞춘다.

CREATE TABLE DIAGNOSIS (
    diagnosis_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id                BIGINT       NOT NULL,
    patient_id              BIGINT       NOT NULL,
    diagnosed_at             DATE         NOT NULL,
    resolved_at              DATE         NULL,
    code_system              VARCHAR(255) NOT NULL,
    code                     VARCHAR(50)  NOT NULL,
    description              VARCHAR(500) NULL,
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_diagnosis_natural UNIQUE (visit_id, code, diagnosed_at),
    CONSTRAINT fk_diagnosis_visit FOREIGN KEY (visit_id) REFERENCES VISIT (visit_id),
    CONSTRAINT fk_diagnosis_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
