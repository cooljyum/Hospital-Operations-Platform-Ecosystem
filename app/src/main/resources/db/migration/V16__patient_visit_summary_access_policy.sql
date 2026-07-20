-- Phase 9 Step 9.1: PATIENT_VISIT_SUMMARY(V13) 리포팅 화면(/reports/patient-visit-summary)을
-- ACCESS_POLICY_RULES에 등록.
--
-- 배경: 이 화면은 환자별(표시용 식별자 synthetic_patient_no 기준) 방문/검사/처방 건수와
-- 최초/최근 방문일을 노출한다 - /stats/**(V12, k=5 익명 교집합 통계)보다 훨씬 식별성이
-- 높다(환자 단위 row가 그대로 나열됨). V12와 동일한 판단 근거(교차표/식별 단위 데이터는
-- 민감 정보)를 그대로 적용해, 감사자(ROLE_AUDITOR)와 전산관리자(ROLE_SYSTEM_ADMIN)만
-- 접근 가능하도록 등록한다 - 의사/간호사/원무 역할은 SecurityConfig의 기본 인가 규칙
-- (ACCESS_POLICY_RULES에 없는 URL은 인증만 되면 통과)로 새지 않도록 명시적으로 좁힌다.
INSERT INTO ACCESS_POLICY_RULES (role_name, url_pattern, allowed, description) VALUES
    ('ROLE_AUDITOR',      '/reports/patient-visit-summary', TRUE, '환자별 방문 요약 리포트(PATIENT_VISIT_SUMMARY) - 감사자/전산관리자 전용'),
    ('ROLE_SYSTEM_ADMIN', '/reports/patient-visit-summary', TRUE, '환자별 방문 요약 리포트(PATIENT_VISIT_SUMMARY) - 감사자/전산관리자 전용');
