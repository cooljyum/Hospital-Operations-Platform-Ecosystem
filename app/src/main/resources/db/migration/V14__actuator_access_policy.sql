-- Phase 7 Step 7.1: Actuator 메트릭 상세 조회(/actuator/metrics) 접근 제어.
--
-- 배경: Phase 7.1에서 spring-boot-starter-actuator + micrometer-registry-prometheus를
-- 도입하며 health/info/prometheus/metrics 4개 엔드포인트만 노출한다
-- (management.endpoints.web.exposure.include, application.yml 참고). 이 중
-- /actuator/health, /actuator/info, /actuator/prometheus는 Prometheus가 로그인 세션
-- 없이 주기적으로 스크레이핑해야 하므로 SecurityConfig에 하드코딩된 permitAll()로 둔다
-- (V9 이전부터 있던 /login, /adminlte/** 처리와 같은 성격 - RBAC 역할 기반이 아니라
-- 인프라 스크레이프 경로).
--
-- 반면 /actuator/metrics는 개별 메트릭 이름·값(배치 job 성공/실패 횟수, break-glass
-- 발급 횟수 등 내부 운영 상태)을 나열해 보여준다 - /stats/**(V12)와 같은 성격의 민감
-- 정보로 판단해 감사자/전산관리자 전용으로 좁힌다. 목록 엔드포인트(/actuator/metrics)와
-- 개별 메트릭 상세 엔드포인트(/actuator/metrics/{name})를 둘 다 명시적으로 등록한다
-- (Ant 스타일 "/**" 패턴이 트레일링 세그먼트 없는 베이스 경로까지 매칭하는지 환경마다
-- 미묘하게 갈릴 수 있어, 이 마이그레이션에서는 추측 대신 두 경로를 각각 명시한다).
INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_AUDITOR',      '/actuator/metrics',    TRUE, 'Actuator 메트릭 목록 조회 - 감사자/전산관리자 전용'),
    ('ROLE_SYSTEM_ADMIN', '/actuator/metrics',    TRUE, 'Actuator 메트릭 목록 조회 - 감사자/전산관리자 전용'),
    ('ROLE_AUDITOR',      '/actuator/metrics/**', TRUE, 'Actuator 메트릭 상세 조회 - 감사자/전산관리자 전용'),
    ('ROLE_SYSTEM_ADMIN', '/actuator/metrics/**', TRUE, 'Actuator 메트릭 상세 조회 - 감사자/전산관리자 전용');
