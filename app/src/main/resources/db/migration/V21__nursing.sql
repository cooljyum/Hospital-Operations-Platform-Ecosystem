-- 간호 CRUD 모듈: 활력징후 기록과 간호사/전산관리자 접근 정책.
CREATE TABLE NURSING_RECORD (
    nursing_record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    recorded_at DATETIME NOT NULL,
    nurse_username VARCHAR(50) NOT NULL,
    temperature DECIMAL(4,1) NULL,
    blood_pressure VARCHAR(20) NULL,
    pulse INT NULL,
    notes VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_nursing_record_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_NURSE', '/nursing/**', TRUE, '간호 기록 화면 - 간호사 전용'),
    ('ROLE_SYSTEM_ADMIN', '/nursing/**', TRUE, '간호 기록 화면 - 간호사/전산관리자 전용');
