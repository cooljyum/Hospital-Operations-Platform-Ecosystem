-- Phase 2 Step 2.2: 로컬 코드셋 테이블(CODE_SET)
--
-- 설계 판단: LAB_RESULT.code(LOINC)/PRESCRIPTION.medication_code(RxNorm)를 FHIR
-- Coding(system/code/display)으로 바꿀 때, 매퍼(Step 2.3)가 하드코딩된 switch/if-else로
-- 코드를 나열하지 않도록 이 테이블에서 조회하게 한다. code_system 컬럼으로 도메인을
-- 구분한다("LAB_RESULT"/"PRESCRIPTION" — 레거시 테이블명과 동일하게 맞춰 어느 컬럼
-- 계열에서 온 코드인지 바로 알 수 있게 함). fhir_system은 실제 표준 코드 시스템
-- URI(LOINC=http://loinc.org, RxNorm=http://www.nlm.nih.gov/research/umls/rxnorm)다.
--
-- 시드 데이터는 로컬 hospital_ops의 실제 LAB_RESULT/PRESCRIPTION 데이터에서 실측한
-- 값이다(2026-07-18, PATIENT 12명 표본 기준 LAB_RESULT distinct code=136건,
-- PRESCRIPTION distinct medication_code=44건 중 발생 빈도 상위 다수를 시드로 넣음 —
-- 전부는 아니다). 코드셋에 없는 코드는 Step 2.3의 매퍼가 원본 code/description을 그대로
-- text로 넣는 폴백 경로를 타게 하려는 의도로, 일부러 전량을 채우지 않았다.

CREATE TABLE CODE_SET (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    code_system         VARCHAR(30)  NOT NULL,
    source_code         VARCHAR(50)  NOT NULL,
    source_description  VARCHAR(500) NULL,
    fhir_system         VARCHAR(255) NOT NULL,
    fhir_code           VARCHAR(50)  NOT NULL,
    fhir_display        VARCHAR(500) NULL,
    CONSTRAINT uq_code_set_system_source UNIQUE (code_system, source_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- LAB_RESULT.code -> LOINC
INSERT INTO CODE_SET (code_system, source_code, source_description, fhir_system, fhir_code, fhir_display) VALUES
('LAB_RESULT', '8462-4',  'Diastolic Blood Pressure',                              'http://loinc.org', '8462-4',  'Diastolic Blood Pressure'),
('LAB_RESULT', '8480-6',  'Systolic Blood Pressure',                               'http://loinc.org', '8480-6',  'Systolic Blood Pressure'),
('LAB_RESULT', '72514-3', 'Pain severity - 0-10 verbal numeric rating [Score] - Reported', 'http://loinc.org', '72514-3', 'Pain severity - 0-10 verbal numeric rating [Score] - Reported'),
('LAB_RESULT', '8302-2',  'Body Height',                                           'http://loinc.org', '8302-2',  'Body Height'),
('LAB_RESULT', '29463-7', 'Body Weight',                                           'http://loinc.org', '29463-7', 'Body Weight'),
('LAB_RESULT', '8867-4',  'Heart rate',                                            'http://loinc.org', '8867-4',  'Heart rate'),
('LAB_RESULT', '9279-1',  'Respiratory rate',                                      'http://loinc.org', '9279-1',  'Respiratory rate'),
('LAB_RESULT', '72166-2', 'Tobacco smoking status',                                'http://loinc.org', '72166-2', 'Tobacco smoking status'),
('LAB_RESULT', '39156-5', 'Body mass index (BMI) [Ratio]',                         'http://loinc.org', '39156-5', 'Body mass index (BMI) [Ratio]'),
('LAB_RESULT', '76437-3', 'Primary insurance',                                     'http://loinc.org', '76437-3', 'Primary insurance'),
('LAB_RESULT', '67875-5', 'Employment status - current',                          'http://loinc.org', '67875-5', 'Employment status - current'),
('LAB_RESULT', '56051-6', 'Hispanic or Latino',                                    'http://loinc.org', '56051-6', 'Hispanic or Latino'),
('LAB_RESULT', '82589-3', 'Highest level of education',                           'http://loinc.org', '82589-3', 'Highest level of education'),
('LAB_RESULT', '32624-9', 'Race',                                                  'http://loinc.org', '32624-9', 'Race'),
('LAB_RESULT', '56799-0', 'Address',                                               'http://loinc.org', '56799-0', 'Address'),
('LAB_RESULT', '54899-0', 'Preferred language',                                    'http://loinc.org', '54899-0', 'Preferred language'),
('LAB_RESULT', '71802-3', 'Housing status',                                        'http://loinc.org', '71802-3', 'Housing status'),
('LAB_RESULT', '93038-8', 'Stress level',                                          'http://loinc.org', '93038-8', 'Stress level'),
('LAB_RESULT', '2093-3',  'Cholesterol [Mass/volume] in Serum or Plasma',          'http://loinc.org', '2093-3',  'Cholesterol [Mass/volume] in Serum or Plasma'),
('LAB_RESULT', '6690-2',  'Leukocytes [#/volume] in Blood by Automated count',     'http://loinc.org', '6690-2',  'Leukocytes [#/volume] in Blood by Automated count'),
('LAB_RESULT', '2571-8',  'Triglyceride [Mass/volume] in Serum or Plasma',         'http://loinc.org', '2571-8',  'Triglyceride [Mass/volume] in Serum or Plasma'),
('LAB_RESULT', '789-8',   'Erythrocytes [#/volume] in Blood by Automated count',   'http://loinc.org', '789-8',   'Erythrocytes [#/volume] in Blood by Automated count'),
('LAB_RESULT', '18262-6', 'Cholesterol in LDL [Mass/volume] in Serum or Plasma by Direct assay', 'http://loinc.org', '18262-6', 'Cholesterol in LDL [Mass/volume] in Serum or Plasma by Direct assay'),
('LAB_RESULT', '786-4',   'MCHC [Entitic Mass/volume] in Red Blood Cells by Automated count', 'http://loinc.org', '786-4', 'MCHC [Entitic Mass/volume] in Red Blood Cells by Automated count'),
('LAB_RESULT', '788-0',   'Erythrocyte [DistWidth] in Blood by Automated count',   'http://loinc.org', '788-0',   'Erythrocyte [DistWidth] in Blood by Automated count'),
('LAB_RESULT', '787-2',   'MCV [Entitic mean volume] in Red Blood Cells by Automated count', 'http://loinc.org', '787-2', 'MCV [Entitic mean volume] in Red Blood Cells by Automated count'),
('LAB_RESULT', '777-3',   'Platelets [#/volume] in Blood by Automated count',      'http://loinc.org', '777-3',   'Platelets [#/volume] in Blood by Automated count'),
('LAB_RESULT', '785-6',   'MCH [Entitic mass] by Automated count',                 'http://loinc.org', '785-6',   'MCH [Entitic mass] by Automated count');

-- PRESCRIPTION.medication_code -> RxNorm (상위 빈도 다수만 시드 — 나머지는 매퍼의
-- 폴백 경로로 원본 code/description이 text로만 채워지는 것을 의도적으로 남겨 둔다)
INSERT INTO CODE_SET (code_system, source_code, source_description, fhir_system, fhir_code, fhir_display) VALUES
('PRESCRIPTION', '310798', 'Hydrochlorothiazide 25 MG Oral Tablet',                              'http://www.nlm.nih.gov/research/umls/rxnorm', '310798', 'Hydrochlorothiazide 25 MG Oral Tablet'),
('PRESCRIPTION', '308136', 'amLODIPine 2.5 MG Oral Tablet',                                       'http://www.nlm.nih.gov/research/umls/rxnorm', '308136', 'amLODIPine 2.5 MG Oral Tablet'),
('PRESCRIPTION', '895996', '120 ACTUAT fluticasone propionate 0.044 MG/ACTUAT Metered Dose Inhaler [Flovent]', 'http://www.nlm.nih.gov/research/umls/rxnorm', '895996', '120 ACTUAT fluticasone propionate 0.044 MG/ACTUAT Metered Dose Inhaler [Flovent]'),
('PRESCRIPTION', '630208', 'albuterol 0.83 MG/ML Inhalation Solution',                            'http://www.nlm.nih.gov/research/umls/rxnorm', '630208', 'albuterol 0.83 MG/ML Inhalation Solution'),
('PRESCRIPTION', '314076', 'lisinopril 10 MG Oral Tablet',                                        'http://www.nlm.nih.gov/research/umls/rxnorm', '314076', 'lisinopril 10 MG Oral Tablet'),
('PRESCRIPTION', '313521', 'tropicamide 5 MG/ML Ophthalmic Solution',                             'http://www.nlm.nih.gov/research/umls/rxnorm', '313521', 'tropicamide 5 MG/ML Ophthalmic Solution'),
('PRESCRIPTION', '313782', 'Acetaminophen 325 MG Oral Tablet',                                    'http://www.nlm.nih.gov/research/umls/rxnorm', '313782', 'Acetaminophen 325 MG Oral Tablet'),
('PRESCRIPTION', '310965', 'Ibuprofen 200 MG Oral Tablet',                                        'http://www.nlm.nih.gov/research/umls/rxnorm', '310965', 'Ibuprofen 200 MG Oral Tablet'),
('PRESCRIPTION', '313820', 'Acetaminophen 160 MG Chewable Tablet',                                'http://www.nlm.nih.gov/research/umls/rxnorm', '313820', 'Acetaminophen 160 MG Chewable Tablet'),
('PRESCRIPTION', '562251', 'Amoxicillin 250 MG / Clavulanate 125 MG Oral Tablet',                 'http://www.nlm.nih.gov/research/umls/rxnorm', '562251', 'Amoxicillin 250 MG / Clavulanate 125 MG Oral Tablet'),
('PRESCRIPTION', '849574', 'Naproxen sodium 220 MG Oral Tablet',                                  'http://www.nlm.nih.gov/research/umls/rxnorm', '849574', 'Naproxen sodium 220 MG Oral Tablet'),
('PRESCRIPTION', '198405', 'Ibuprofen 100 MG Oral Tablet',                                        'http://www.nlm.nih.gov/research/umls/rxnorm', '198405', 'Ibuprofen 100 MG Oral Tablet'),
('PRESCRIPTION', '206905', 'Ibuprofen 400 MG Oral Tablet [Ibu]',                                  'http://www.nlm.nih.gov/research/umls/rxnorm', '206905', 'Ibuprofen 400 MG Oral Tablet [Ibu]'),
('PRESCRIPTION', '834102', 'Penicillin V Potassium 500 MG Oral Tablet',                           'http://www.nlm.nih.gov/research/umls/rxnorm', '834102', 'Penicillin V Potassium 500 MG Oral Tablet'),
('PRESCRIPTION', '1049630','diphenhydrAMINE Hydrochloride 25 MG Oral Tablet',                     'http://www.nlm.nih.gov/research/umls/rxnorm', '1049630','diphenhydrAMINE Hydrochloride 25 MG Oral Tablet'),
('PRESCRIPTION', '309097', 'Cefuroxime 250 MG Oral Tablet',                                       'http://www.nlm.nih.gov/research/umls/rxnorm', '309097', 'Cefuroxime 250 MG Oral Tablet'),
('PRESCRIPTION', '834061', 'Penicillin V Potassium 250 MG Oral Tablet',                           'http://www.nlm.nih.gov/research/umls/rxnorm', '834061', 'Penicillin V Potassium 250 MG Oral Tablet'),
('PRESCRIPTION', '308192', 'Amoxicillin 500 MG Oral Tablet',                                      'http://www.nlm.nih.gov/research/umls/rxnorm', '308192', 'Amoxicillin 500 MG Oral Tablet'),
('PRESCRIPTION', '197454', 'cephalexin 500 MG Oral Tablet',                                       'http://www.nlm.nih.gov/research/umls/rxnorm', '197454', 'cephalexin 500 MG Oral Tablet'),
('PRESCRIPTION', '308182', 'Amoxicillin 250 MG Oral Capsule',                                     'http://www.nlm.nih.gov/research/umls/rxnorm', '308182', 'Amoxicillin 250 MG Oral Capsule'),
('PRESCRIPTION', '197511', 'ciprofloxacin 250 MG Oral Tablet',                                    'http://www.nlm.nih.gov/research/umls/rxnorm', '197511', 'ciprofloxacin 250 MG Oral Tablet'),
('PRESCRIPTION', '860975', '24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet',   'http://www.nlm.nih.gov/research/umls/rxnorm', '860975', '24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet'),
('PRESCRIPTION', '310325', 'ferrous sulfate 325 MG Oral Tablet',                                  'http://www.nlm.nih.gov/research/umls/rxnorm', '310325', 'ferrous sulfate 325 MG Oral Tablet'),
('PRESCRIPTION', '309362', 'Clopidogrel 75 MG Oral Tablet',                                       'http://www.nlm.nih.gov/research/umls/rxnorm', '309362', 'Clopidogrel 75 MG Oral Tablet'),
('PRESCRIPTION', '312961', 'Simvastatin 20 MG Oral Tablet',                                       'http://www.nlm.nih.gov/research/umls/rxnorm', '312961', 'Simvastatin 20 MG Oral Tablet'),
('PRESCRIPTION', '866412', '24 HR metoprolol succinate 100 MG Extended Release Oral Tablet',      'http://www.nlm.nih.gov/research/umls/rxnorm', '866412', '24 HR metoprolol succinate 100 MG Extended Release Oral Tablet'),
('PRESCRIPTION', '705129', 'Nitroglycerin 0.4 MG/ACTUAT Mucosal Spray',                           'http://www.nlm.nih.gov/research/umls/rxnorm', '705129', 'Nitroglycerin 0.4 MG/ACTUAT Mucosal Spray'),
('PRESCRIPTION', '311700', 'Midazolam 1 MG/ML Injectable Solution',                               'http://www.nlm.nih.gov/research/umls/rxnorm', '311700', 'Midazolam 1 MG/ML Injectable Solution');
