-- Phase 4 Step 4.4 이월 이슈 수정: /stats/** 를 ACCESS_POLICY_RULES에 등록.
--
-- 배경: Step 4.4(com.hospitalops.stats, GET /stats/patient-count-by-gender-age-band)가
-- 추가한 k=5 익명성 통계 엔드포인트가 이 테이블에 row 없이 남아 있어, SecurityConfig의
-- 기본 폴백 규칙(auth.anyRequest().authenticated())으로 빠졌다 — 즉 인증만 되면 RBAC
-- 5역할 중 아무나(의사/간호사/원무 포함) 접근 가능했다. 셀 값 자체는 억제되지만 교차표
-- (성별×연령대) 구조 자체가 민감하므로, 감사자(ROLE_AUDITOR)와 전산관리자
-- (ROLE_SYSTEM_ADMIN)만 접근 가능하도록 좁힌다.
--
-- url_pattern은 "/stats/**"로 등록해 향후 이 패키지에 추가될 다른 통계 엔드포인트도
-- 같은 정책을 자동으로 상속하게 한다(V9의 다른 row들처럼 엔드포인트 1개마다 정확히 매칭하는
-- 대신, 이 패키지 전체를 감사자/관리자 전용으로 못박는 편이 "통계는 민감 정보"라는 의도에
-- 더 맞는다고 판단).
INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_AUDITOR',      '/stats/**', TRUE, '통계(k=5 익명성) API - 감사자/전산관리자 전용'),
    ('ROLE_SYSTEM_ADMIN', '/stats/**', TRUE, '통계(k=5 익명성) API - 감사자/전산관리자 전용');
