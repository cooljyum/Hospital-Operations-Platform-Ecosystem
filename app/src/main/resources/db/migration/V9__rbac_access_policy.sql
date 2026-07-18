-- Phase 3 Step 3.2: RBAC 5역할 완성 + ACCESS_POLICY_RULES 테이블.
--
-- deliverable.md §3.3 원문: RBAC 5역할 = 의사/간호사/원무/전산관리자/감사자.
-- ROLE_SYSTEM_ADMIN은 V8에서 이미 시딩됐다(Step 3.1의 admin 계정이 참조 중) — 나머지
-- 4역할만 여기서 추가한다.
--
-- 설계 판단: "누가 어떤 사유로 무엇을 볼 수 있나"를 데이터로 관리한다는 deliverable.md
-- 원칙에 맞춰, SecurityConfig가 authorizeHttpRequests를 하드코딩된 hasRole() 나열 대신
-- 이 테이블을 앱 기동 시 읽어 구성한다(com.hospitalops.security.SecurityConfig 참고).
-- 완전 동적 URL 매칭(요청마다 DB 조회)까지는 과한 엔지니어링으로 판단해, "기동 시 1회
-- 로드 -> 정적 SecurityFilterChain 구성"으로 절충했다 — 그래도 "정책 추가/변경은 코드가
-- 아니라 이 테이블에 row를 추가/수정하는 것"이라는 원칙은 지킨다.
--
-- url_pattern은 Spring Security의 AntPathRequestMatcher 패턴 문자열을 그대로 담는다.
-- allowed=false row는 지금은 쓰지 않지만(전부 허용 규칙만 시드), 향후 "명시적으로
-- 차단"을 표현할 수 있게 스키마에 미리 열어 둔다.
CREATE TABLE ACCESS_POLICY_RULES (
    rule_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name     VARCHAR(50)  NOT NULL,
    url_pattern   VARCHAR(200) NOT NULL,
    allowed       BOOLEAN      NOT NULL DEFAULT TRUE,
    description   VARCHAR(255) NULL,
    CONSTRAINT fk_access_policy_role FOREIGN KEY (role_name) REFERENCES APP_ROLE (role_name),
    CONSTRAINT uq_access_policy_role_pattern UNIQUE (role_name, url_pattern)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO APP_ROLE (role_name) VALUES
    ('ROLE_PHYSICIAN'),
    ('ROLE_NURSE'),
    ('ROLE_REGISTRAR'),
    ('ROLE_AUDITOR');

-- /dashboard: 5역할 전원 공통 접근(placeholder 화면이 아직 /dashboard 하나뿐이므로
-- "인증만 되면 전 역할 공통"을 코드 하드코딩이 아니라 이 테이블의 5개 row로 표현한다).
INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_PHYSICIAN',    '/dashboard', TRUE, '대시보드 - 전 역할 공통 접근'),
    ('ROLE_NURSE',        '/dashboard', TRUE, '대시보드 - 전 역할 공통 접근'),
    ('ROLE_REGISTRAR',    '/dashboard', TRUE, '대시보드 - 전 역할 공통 접근'),
    ('ROLE_SYSTEM_ADMIN', '/dashboard', TRUE, '대시보드 - 전 역할 공통 접근'),
    ('ROLE_AUDITOR',      '/dashboard', TRUE, '대시보드 - 전 역할 공통 접근'),
    -- /admin/system: 전산관리자 전용 placeholder("추후 시스템 관리 화면").
    ('ROLE_SYSTEM_ADMIN', '/admin/system', TRUE, '시스템 관리 화면(placeholder) - 전산관리자 전용'),
    -- /audit/preview: 감사자 전용 placeholder(Phase 5.2에서 실제 감사로그 화면으로 대체 예정).
    ('ROLE_AUDITOR',      '/audit/preview', TRUE, '감사 로그 화면(placeholder) - 감사자 전용, Phase 5.2에서 대체 예정');
