-- 접수 CRUD 기준 모듈: 접수 정보와 원무/전산관리자 접근 정책.
CREATE TABLE RECEPTION (
    reception_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    received_at DATETIME NOT NULL,
    chief_complaint VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    receptionist_username VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reception_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_REGISTRAR', '/reception/**', TRUE, '접수 화면 - 원무 전용'),
    ('ROLE_SYSTEM_ADMIN', '/reception/**', TRUE, '접수 화면 - 원무/전산관리자 전용');
