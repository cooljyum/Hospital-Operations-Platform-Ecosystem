-- Phase 1 Step 1.1: 레거시 HIS 핵심 스키마 (PATIENT / VISIT / LAB_RESULT / PRESCRIPTION)
-- 모든 PK는 불변 내부 식별자(BIGINT AUTO_INCREMENT)로 고정한다. 외부 노출용 식별자는
-- Step 1.2(V2)에서 별도로 분리한다. 이 테이블에는 주민등록번호(RRN) 계열 컬럼을 두지 않는다
-- (Synthea patients.csv의 SSN 컬럼은 의도적으로 적재 대상에서 제외한다).

CREATE TABLE PATIENT (
    patient_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_patient_uuid    VARCHAR(64)  NOT NULL,
    birth_date             DATE         NULL,
    death_date             DATE         NULL,
    prefix                 VARCHAR(20)  NULL,
    first_name             VARCHAR(100) NULL,
    middle_name            VARCHAR(100) NULL,
    last_name              VARCHAR(100) NULL,
    suffix                 VARCHAR(20)  NULL,
    maiden_name            VARCHAR(100) NULL,
    marital_status         VARCHAR(10)  NULL,
    race                   VARCHAR(50)  NULL,
    ethnicity              VARCHAR(50)  NULL,
    gender                 VARCHAR(10)  NULL,
    birthplace              VARCHAR(255) NULL,
    address                VARCHAR(255) NULL,
    city                   VARCHAR(100) NULL,
    state                  VARCHAR(100) NULL,
    county                 VARCHAR(100) NULL,
    zip                    VARCHAR(20)  NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_patient_source_uuid UNIQUE (source_patient_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE VISIT (
    visit_id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id             BIGINT       NOT NULL,
    source_encounter_uuid  VARCHAR(64)  NOT NULL,
    started_at             DATETIME     NULL,
    stopped_at             DATETIME     NULL,
    encounter_class        VARCHAR(50)  NULL,
    encounter_code         VARCHAR(50)  NULL,
    encounter_description  VARCHAR(255) NULL,
    reason_code            VARCHAR(50)  NULL,
    reason_description     VARCHAR(255) NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_visit_source_uuid UNIQUE (source_encounter_uuid),
    CONSTRAINT fk_visit_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE LAB_RESULT (
    lab_result_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id               BIGINT       NOT NULL,
    patient_id             BIGINT       NOT NULL,
    observed_at            DATETIME     NULL,
    category               VARCHAR(50)  NULL,
    code                   VARCHAR(50)  NOT NULL,
    description            VARCHAR(255) NULL,
    result_value           VARCHAR(255) NULL,
    units                  VARCHAR(50)  NULL,
    value_type             VARCHAR(20)  NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_lab_result_natural UNIQUE (visit_id, code, observed_at),
    CONSTRAINT fk_lab_result_visit FOREIGN KEY (visit_id) REFERENCES VISIT (visit_id),
    CONSTRAINT fk_lab_result_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE PRESCRIPTION (
    prescription_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id               BIGINT       NOT NULL,
    patient_id             BIGINT       NOT NULL,
    medication_code        VARCHAR(50)  NOT NULL,
    medication_description VARCHAR(255) NULL,
    started_at             DATETIME     NULL,
    stopped_at             DATETIME     NULL,
    reason_code            VARCHAR(50)  NULL,
    reason_description     VARCHAR(255) NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_prescription_natural UNIQUE (visit_id, medication_code, started_at),
    CONSTRAINT fk_prescription_visit FOREIGN KEY (visit_id) REFERENCES VISIT (visit_id),
    CONSTRAINT fk_prescription_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
