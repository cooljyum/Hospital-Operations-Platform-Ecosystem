-- 입원 CRUD 모듈: 입원 정보와 간호사/의사/전산관리자 접근 정책.
CREATE TABLE ADMISSION (
    admission_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    ward VARCHAR(50) NOT NULL,
    bed_no VARCHAR(20) NOT NULL,
    admitted_at DATETIME NOT NULL,
    discharged_at DATETIME NULL,
    reason VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    admitted_by_username VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_admission_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_NURSE', '/admission/**', TRUE, '입원 화면 - 간호사/의사 전용'),
    ('ROLE_PHYSICIAN', '/admission/**', TRUE, '입원 화면 - 간호사/의사 전용'),
    ('ROLE_SYSTEM_ADMIN', '/admission/**', TRUE, '입원 화면 - 전산관리자 전용');
