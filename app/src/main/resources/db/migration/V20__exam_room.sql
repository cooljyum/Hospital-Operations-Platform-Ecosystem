-- 진료실 CRUD 모듈: 진료실 세션 정보와 의사/전산관리자 접근 정책.
CREATE TABLE EXAM_ROOM_SESSION (
    exam_room_session_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    room_no VARCHAR(20) NOT NULL,
    physician_username VARCHAR(50) NOT NULL,
    started_at DATETIME NOT NULL,
    ended_at DATETIME NULL,
    notes VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exam_room_session_patient FOREIGN KEY (patient_id) REFERENCES PATIENT (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_PHYSICIAN', '/exam-room/**', TRUE, '진료실 화면 - 의사 전용'),
    ('ROLE_SYSTEM_ADMIN', '/exam-room/**', TRUE, '진료실 화면 - 의사/전산관리자 전용');
