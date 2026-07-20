package com.hospitalops.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 1 Step 1.3: Synthea 산출 CSV(patients/encounters/observations/medications)를
 * 레거시 HIS 스키마(PATIENT/VISIT/LAB_RESULT/PRESCRIPTION)에 적재하는 배치.
 *
 * <p>Phase 10 Step 10.2: conditions.csv -> DIAGNOSIS 적재를 추가했다(5번째 legacy 테이블).</p>
 *
 * <p>Synthea CSV의 UUID(Id/PATIENT/ENCOUNTER 컬럼)를 자연키로 삼아 upsert하므로
 * 재실행해도 중복 행이 생기지 않는다(멱등). PATIENT.source_patient_uuid,
 * VISIT.source_encounter_uuid, 그리고 LAB_RESULT/PRESCRIPTION의 복합 UNIQUE 제약
 * (V1+V3 마이그레이션 참고)이 그 자연키를 강제한다.</p>
 *
 * <p>patients.csv에는 Synthea가 생성한 SSN 컬럼이 존재하지만 이 로더는 그 값을
 * 절대 읽지도 저장하지도 않는다 — 레거시 스키마에 RRN 계열 컬럼을 두지 않는다는
 * Step 1.2 정책과 일치시키기 위함이다.</p>
 *
 * <p>기본적으로는 아무 것도 하지 않는다({@code synthea.loader.enabled=false}가 기본값).
 * 평소 앱 기동/테스트가 이 배치를 의도치 않게 실행하지 않도록 하기 위함이며, 실제
 * 적재는 명시적으로 활성화했을 때만 수행된다.</p>
 */
@Component
public class SyntheaLoaderRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(SyntheaLoaderRunner.class);

	private final JdbcTemplate jdbcTemplate;
	private final SyntheaLoaderProperties properties;

	public SyntheaLoaderRunner(JdbcTemplate jdbcTemplate, SyntheaLoaderProperties properties) {
		this.jdbcTemplate = jdbcTemplate;
		this.properties = properties;
	}

	@Override
	public void run(String... args) {
		if (!properties.isEnabled()) {
			log.debug("SyntheaLoaderRunner: synthea.loader.enabled=false, 적재를 건너뜁니다.");
			return;
		}
		String csvDir = properties.getCsvDir();
		if (csvDir == null || csvDir.isBlank()) {
			throw new IllegalStateException(
					"synthea.loader.enabled=true 인데 synthea.loader.csv-dir이 설정되지 않았습니다. " +
							"--synthea.loader.csv-dir=<patients/encounters/observations/medications.csv가 있는 디렉터리> 로 지정하세요.");
		}
		LoadSummary summary = load(Path.of(csvDir));
		log.info("Synthea 적재 완료: {}", summary);
	}

	/**
	 * 지정한 디렉터리의 5개 CSV를 순서대로(환자 -> 내원 -> 검사결과/처방/진단) 적재한다.
	 * 여러 번 호출해도 안전하다(upsert 기반 멱등).
	 */
	public LoadSummary load(Path csvDir) {
		try {
			Map<String, Long> patientIdByUuid = loadPatients(csvDir.resolve("patients.csv"));
			Map<String, Long> visitIdByUuid = loadEncounters(csvDir.resolve("encounters.csv"), patientIdByUuid);
			int labResultsUpserted = loadObservations(csvDir.resolve("observations.csv"), patientIdByUuid, visitIdByUuid);
			int prescriptionsUpserted = loadMedications(csvDir.resolve("medications.csv"), patientIdByUuid, visitIdByUuid);
			int diagnosesUpserted = loadConditions(csvDir.resolve("conditions.csv"), patientIdByUuid, visitIdByUuid);
			return new LoadSummary(patientIdByUuid.size(), visitIdByUuid.size(), labResultsUpserted,
					prescriptionsUpserted, diagnosesUpserted);
		} catch (IOException e) {
			throw new IllegalStateException("Synthea CSV 적재 중 I/O 오류: " + csvDir, e);
		}
	}

	private Map<String, Long> loadPatients(Path csvFile) throws IOException {
		Map<String, Long> patientIdByUuid = new LinkedHashMap<>();
		for (Map<String, String> row : SyntheaCsvReader.readAsMaps(csvFile)) {
			String sourceUuid = required(row, "Id");
			String displayNo = deriveDisplayNo(sourceUuid);

			// 신규 삽입 여부는 INSERT 실행 전에 명시적으로 SELECT해서 판단한다(아래에서
			// PATIENT_DISPLAY_ID 발급 이력을 딱 한 번만 남기기 위해 필요) — JDBC 드라이버의
			// affected-rows 반환값(useAffectedRows 커넥션 옵션)에 의존하지 않는다. MySQL
			// Connector/J는 기본값(useAffectedRows=false)에서 "found rows" 모드로 동작해
			// INSERT ... ON DUPLICATE KEY UPDATE가 신규 삽입이든 값 변화 없는 재실행이든
			// 똑같이 1을 반환하므로, affected==1을 "신규 삽입"의 신호로 쓸 수 없다(실제로
			// 이 구현 초기 버전이 이 문제로 재실행 시 PATIENT_DISPLAY_ID 유니크 제약을
			// 위반했다).
			boolean alreadyExisted = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) > 0 FROM PATIENT WHERE source_patient_uuid = ?", Boolean.class, sourceUuid));

			jdbcTemplate.update(
					"INSERT INTO PATIENT (source_patient_uuid, synthetic_patient_no, birth_date, death_date, " +
							"prefix, first_name, middle_name, last_name, suffix, maiden_name, marital_status, " +
							"race, ethnicity, gender, birthplace, address, city, state, county, zip) " +
							"VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
							"ON DUPLICATE KEY UPDATE birth_date=VALUES(birth_date), death_date=VALUES(death_date), " +
							"prefix=VALUES(prefix), first_name=VALUES(first_name), middle_name=VALUES(middle_name), " +
							"last_name=VALUES(last_name), suffix=VALUES(suffix), maiden_name=VALUES(maiden_name), " +
							"marital_status=VALUES(marital_status), race=VALUES(race), ethnicity=VALUES(ethnicity), " +
							"gender=VALUES(gender), birthplace=VALUES(birthplace), address=VALUES(address), " +
							"city=VALUES(city), state=VALUES(state), county=VALUES(county), zip=VALUES(zip)",
					sourceUuid, displayNo,
					parseDate(row.get("BIRTHDATE")), parseDate(row.get("DEATHDATE")),
					blank(row.get("PREFIX")), blank(row.get("FIRST")), blank(row.get("MIDDLE")),
					blank(row.get("LAST")), blank(row.get("SUFFIX")), blank(row.get("MAIDEN")),
					blank(row.get("MARITAL")), blank(row.get("RACE")), blank(row.get("ETHNICITY")),
					blank(row.get("GENDER")), blank(row.get("BIRTHPLACE")), blank(row.get("ADDRESS")),
					blank(row.get("CITY")), blank(row.get("STATE")), blank(row.get("COUNTY")), blank(row.get("ZIP")));

			Long patientId = jdbcTemplate.queryForObject(
					"SELECT patient_id FROM PATIENT WHERE source_patient_uuid = ?", Long.class, sourceUuid);

			// 삽입 전에는 없었던 행이면 이번이 최초 발급이므로 표시용 ID 발급 이력을 함께
			// 남긴다. 재실행 시(alreadyExisted=true)에는 이미 발급된 synthetic_patient_no를
			// 그대로 유지한다(위 UPDATE 절이 synthetic_patient_no를 건드리지 않으므로).
			if (!alreadyExisted) {
				jdbcTemplate.update(
						"INSERT INTO PATIENT_DISPLAY_ID (patient_id, synthetic_patient_no, is_active) VALUES (?,?,1)",
						patientId, displayNo);
			}
			patientIdByUuid.put(sourceUuid, patientId);
		}
		return patientIdByUuid;
	}

	private Map<String, Long> loadEncounters(Path csvFile, Map<String, Long> patientIdByUuid) throws IOException {
		Map<String, Long> visitIdByUuid = new LinkedHashMap<>();
		for (Map<String, String> row : SyntheaCsvReader.readAsMaps(csvFile)) {
			String sourceUuid = required(row, "Id");
			String patientUuid = row.get("PATIENT");
			Long patientId = patientIdByUuid.get(patientUuid);
			if (patientId == null) {
				log.warn("encounters.csv Id={}가 참조하는 PATIENT {}를 patients.csv에서 찾지 못해 건너뜁니다.",
						sourceUuid, patientUuid);
				continue;
			}

			jdbcTemplate.update(
					"INSERT INTO VISIT (patient_id, source_encounter_uuid, started_at, stopped_at, " +
							"encounter_class, encounter_code, encounter_description, reason_code, reason_description) " +
							"VALUES (?,?,?,?,?,?,?,?,?) " +
							"ON DUPLICATE KEY UPDATE patient_id=VALUES(patient_id), started_at=VALUES(started_at), " +
							"stopped_at=VALUES(stopped_at), encounter_class=VALUES(encounter_class), " +
							"encounter_code=VALUES(encounter_code), encounter_description=VALUES(encounter_description), " +
							"reason_code=VALUES(reason_code), reason_description=VALUES(reason_description)",
					patientId, sourceUuid,
					parseDateTime(row.get("START")), parseDateTime(row.get("STOP")),
					blank(row.get("ENCOUNTERCLASS")), blank(row.get("CODE")), blank(row.get("DESCRIPTION")),
					blank(row.get("REASONCODE")), blank(row.get("REASONDESCRIPTION")));

			Long visitId = jdbcTemplate.queryForObject(
					"SELECT visit_id FROM VISIT WHERE source_encounter_uuid = ?", Long.class, sourceUuid);
			visitIdByUuid.put(sourceUuid, visitId);
		}
		return visitIdByUuid;
	}

	private int loadObservations(Path csvFile, Map<String, Long> patientIdByUuid, Map<String, Long> visitIdByUuid)
			throws IOException {
		int upserted = 0;
		for (Map<String, String> row : SyntheaCsvReader.readAsMaps(csvFile)) {
			String patientUuid = row.get("PATIENT");
			String encounterUuid = row.get("ENCOUNTER");
			Long patientId = patientIdByUuid.get(patientUuid);
			Long visitId = visitIdByUuid.get(encounterUuid);
			if (patientId == null || visitId == null) {
				log.warn("observations.csv 행(PATIENT={}, ENCOUNTER={})의 매핑을 찾지 못해 건너뜁니다.",
						patientUuid, encounterUuid);
				continue;
			}

			jdbcTemplate.update(
					"INSERT INTO LAB_RESULT (visit_id, patient_id, observed_at, category, code, description, " +
							"result_value, units, value_type) VALUES (?,?,?,?,?,?,?,?,?) " +
							"ON DUPLICATE KEY UPDATE patient_id=VALUES(patient_id), category=VALUES(category), " +
							"description=VALUES(description), units=VALUES(units), value_type=VALUES(value_type)",
					visitId, patientId, parseDateTime(row.get("DATE")),
					blank(row.get("CATEGORY")), required(row, "CODE"), blank(row.get("DESCRIPTION")),
					blank(row.get("VALUE")), blank(row.get("UNITS")), blank(row.get("TYPE")));
			upserted++;
		}
		return upserted;
	}

	private int loadMedications(Path csvFile, Map<String, Long> patientIdByUuid, Map<String, Long> visitIdByUuid)
			throws IOException {
		int upserted = 0;
		for (Map<String, String> row : SyntheaCsvReader.readAsMaps(csvFile)) {
			String patientUuid = row.get("PATIENT");
			String encounterUuid = row.get("ENCOUNTER");
			Long patientId = patientIdByUuid.get(patientUuid);
			Long visitId = visitIdByUuid.get(encounterUuid);
			if (patientId == null || visitId == null) {
				log.warn("medications.csv 행(PATIENT={}, ENCOUNTER={})의 매핑을 찾지 못해 건너뜁니다.",
						patientUuid, encounterUuid);
				continue;
			}

			jdbcTemplate.update(
					"INSERT INTO PRESCRIPTION (visit_id, patient_id, medication_code, medication_description, " +
							"started_at, stopped_at, reason_code, reason_description) VALUES (?,?,?,?,?,?,?,?) " +
							"ON DUPLICATE KEY UPDATE patient_id=VALUES(patient_id), " +
							"medication_description=VALUES(medication_description), " +
							"reason_code=VALUES(reason_code), reason_description=VALUES(reason_description)",
					visitId, patientId, required(row, "CODE"), blank(row.get("DESCRIPTION")),
					parseDateTime(row.get("START")), parseDateTime(row.get("STOP")),
					blank(row.get("REASONCODE")), blank(row.get("REASONDESCRIPTION")));
			upserted++;
		}
		return upserted;
	}

	/**
	 * Phase 10 Step 10.2: conditions.csv(START/STOP/PATIENT/ENCOUNTER/SYSTEM/CODE/
	 * DESCRIPTION) -> DIAGNOSIS 적재. SYSTEM 컬럼(완전한 FHIR 코드시스템 URI)을 그대로
	 * code_system에 저장한다 -- V17 마이그레이션 설계 판단 1 참고(CODE_SET 변환 불필요).
	 */
	private int loadConditions(Path csvFile, Map<String, Long> patientIdByUuid, Map<String, Long> visitIdByUuid)
			throws IOException {
		int upserted = 0;
		for (Map<String, String> row : SyntheaCsvReader.readAsMaps(csvFile)) {
			String patientUuid = row.get("PATIENT");
			String encounterUuid = row.get("ENCOUNTER");
			Long patientId = patientIdByUuid.get(patientUuid);
			Long visitId = visitIdByUuid.get(encounterUuid);
			if (patientId == null || visitId == null) {
				log.warn("conditions.csv 행(PATIENT={}, ENCOUNTER={})의 매핑을 찾지 못해 건너뜁니다.",
						patientUuid, encounterUuid);
				continue;
			}
			if (blank(row.get("START")) == null) {
				// DIAGNOSIS.diagnosed_at은 NOT NULL(V17) -- 실측 데이터(295건)에는 없었지만
				// START가 비어 있는 행이 나오면 삽입 대신 건너뛴다(다른 loader들의 방어적
				// skip 패턴과 동일).
				log.warn("conditions.csv 행(PATIENT={}, ENCOUNTER={}, CODE={})의 START가 비어 있어 건너뜁니다.",
						patientUuid, encounterUuid, row.get("CODE"));
				continue;
			}

			jdbcTemplate.update(
					"INSERT INTO DIAGNOSIS (visit_id, patient_id, diagnosed_at, resolved_at, code_system, code, description) " +
							"VALUES (?,?,?,?,?,?,?) " +
							"ON DUPLICATE KEY UPDATE patient_id=VALUES(patient_id), resolved_at=VALUES(resolved_at), " +
							"code_system=VALUES(code_system), description=VALUES(description)",
					visitId, patientId, parseDate(row.get("START")), parseDate(row.get("STOP")),
					blank(row.get("SYSTEM")), required(row, "CODE"), blank(row.get("DESCRIPTION")));
			upserted++;
		}
		return upserted;
	}

	private static String required(Map<String, String> row, String column) {
		String value = blank(row.get(column));
		if (value == null) {
			throw new IllegalStateException("필수 컬럼 " + column + "이 비어 있는 CSV 행: " + row);
		}
		return value;
	}

	private static String blank(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static LocalDate parseDate(String raw) {
		String value = blank(raw);
		return value == null ? null : LocalDate.parse(value);
	}

	private static LocalDateTime parseDateTime(String raw) {
		String value = blank(raw);
		if (value == null) {
			return null;
		}
		// Synthea 타임스탬프는 ISO-8601 UTC(예: 2007-06-08T14:43:17Z) 형식이다.
		return OffsetDateTime.parse(value).toLocalDateTime();
	}

	/**
	 * source_patient_uuid로부터 결정론적으로 표시용 식별자를 파생한다(같은 입력 -> 항상
	 * 같은 출력이라 재실행해도 값이 흔들리지 않는다). 내부 불변 PK(patient_id)와는 값
	 * 공간이 전혀 다른 별도 문자열이며, 원본 UUID를 역산할 수 없도록 해시를 거친다.
	 */
	static String deriveDisplayNo(String sourcePatientUuid) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] hash = sha256.digest(sourcePatientUuid.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 6; i++) {
				hex.append(String.format("%02X", hash[i]));
			}
			return "SP-" + hex;
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
		}
	}

	public record LoadSummary(int patients, int visits, int labResultsUpserted, int prescriptionsUpserted,
			int diagnosesUpserted) {
	}
}
