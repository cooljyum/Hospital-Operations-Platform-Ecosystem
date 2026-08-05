-- 예약 CRUD 모듈: 예약 정보와 원무/전산관리자 접근 정책.
CREATE TABLE APPOINTMENT (
    appointment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    scheduled_at DATETIME NOT NULL,
    department VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL,
    memo VARCHAR(500) NULL,
    booked_by_username VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_REGISTRAR', '/appointment/**', TRUE, '예약 화면 - 원무 전용'),
    ('ROLE_SYSTEM_ADMIN', '/appointment/**', TRUE, '예약 화면 - 원무/전산관리자 전용');
