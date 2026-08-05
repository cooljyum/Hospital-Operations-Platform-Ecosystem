-- 검사 오더 CRUD 모듈: 검사 오더 정보와 의사/간호사/전산관리자 접근 정책.
CREATE TABLE LAB_ORDER (
    lab_order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    ordered_at DATETIME NOT NULL,
    ordered_by_username VARCHAR(50) NOT NULL,
    test_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_summary VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lab_order_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_PHYSICIAN', '/lab-order/**', TRUE, '검사 오더 화면 - 의사/간호사 전용'),
    ('ROLE_NURSE', '/lab-order/**', TRUE, '검사 오더 화면 - 의사/간호사 전용'),
    ('ROLE_SYSTEM_ADMIN', '/lab-order/**', TRUE, '검사 오더 화면 - 전산관리자 전용');
