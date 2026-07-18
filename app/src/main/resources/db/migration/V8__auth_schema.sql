-- Phase 3 Step 3.1: Spring Security 폼+세션 로그인을 위한 인증 스키마.
--
-- APP_USER/APP_ROLE/APP_USER_ROLE 3테이블 구성. Step 3.2(RBAC 5역할)가 이 위에
-- ACCESS_POLICY_RULES를 얹어 인가 결정을 데이터 기반으로 내린다.
--
-- 비밀번호는 이 마이그레이션에서 심지 않는다 — BCrypt 해시를 SQL에 하드코딩하는 대신
-- 애플리케이션 부팅 시 SecurityDataSeeder(com.hospitalops.security)가 PasswordEncoder로
-- 인코딩해 없으면 생성하는 방식을 택했다(PLAN.md Step 3.1 지시사항이 명시적으로 허용한
-- 대안). ROLE_SYSTEM_ADMIN 역할만 이 시점에 미리 심어 admin 계정이 참조할 수 있게 한다
-- (나머지 4역할은 Step 3.2에서 추가 — RBAC 5역할 세트가 V9에서 완성된다).

CREATE TABLE APP_ROLE (
    role_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(50) NOT NULL,
    CONSTRAINT uq_app_role_name UNIQUE (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE APP_USER (
    user_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_user_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE APP_USER_ROLE (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_app_user_role_user FOREIGN KEY (user_id) REFERENCES APP_USER (user_id),
    CONSTRAINT fk_app_user_role_role FOREIGN KEY (role_id) REFERENCES APP_ROLE (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO APP_ROLE (role_name) VALUES ('ROLE_SYSTEM_ADMIN');
