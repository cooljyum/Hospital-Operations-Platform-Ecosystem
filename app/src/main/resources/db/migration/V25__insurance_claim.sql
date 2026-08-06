-- 보험청구 CRUD 모듈: 합성 데모 청구 정보와 원무/전산관리자 접근 정책.
CREATE TABLE INSURANCE_CLAIM (
    insurance_claim_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    insurer_name VARCHAR(100) NOT NULL,
    claim_amount DECIMAL(12,2) NOT NULL,
    claim_status VARCHAR(20) NOT NULL,
    submitted_at DATETIME NOT NULL,
    processed_at DATETIME NULL,
    processed_by_username VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_insurance_claim_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_REGISTRAR', '/insurance-claim/**', TRUE, '보험청구 화면 - 원무 전용'),
    ('ROLE_SYSTEM_ADMIN', '/insurance-claim/**', TRUE, '보험청구 화면 - 원무/전산관리자 전용');
