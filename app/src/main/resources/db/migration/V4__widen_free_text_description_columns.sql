-- Phase 1 Step 1.3: 자유 텍스트 설명 컬럼 폭 확장 (SyntheaLoader 실제 적재 중 발견)
--
-- medications.csv의 DESCRIPTION은 복합 처방(Pack, 예: 여러 성분/용량을 나열하는
-- "{...} Pack [Natazia 28 Day]" 형태)에서 최대 266자까지 나타나 기존
-- PRESCRIPTION.medication_description VARCHAR(255)를 초과해 적재 중
-- MysqlDataTruncation("Data too long for column 'medication_description'")로 실패했다.
-- 실측 최대값(266) 대비 여유를 두고 600으로 넓히고, 같은 계열의 다른 자유 텍스트
-- 설명 컬럼도 향후 데이터 재생성(환자 수 증가 등) 시 유사하게 잘릴 위험을 줄이기 위해
-- 함께 여유 있게 넓혀 둔다. (이번 실측치: encounters DESCRIPTION 최대 64,
-- REASONDESCRIPTION 최대 70, observations DESCRIPTION 최대 138 — 모두 255 이내였지만
-- 표본이 12명뿐이라 여유를 둔다.)

ALTER TABLE PRESCRIPTION MODIFY COLUMN medication_description VARCHAR(600) NULL;
ALTER TABLE PRESCRIPTION MODIFY COLUMN reason_description VARCHAR(500) NULL;
ALTER TABLE VISIT MODIFY COLUMN encounter_description VARCHAR(500) NULL;
ALTER TABLE VISIT MODIFY COLUMN reason_description VARCHAR(500) NULL;
ALTER TABLE LAB_RESULT MODIFY COLUMN description VARCHAR(500) NULL;
