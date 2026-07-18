package com.hospitalops.batch;

import com.hospitalops.fhir.FhirResourceCacheUpsertService;
import com.hospitalops.fhir.PatientMapper;
import com.hospitalops.fhir.PatientRow;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.hospitalops.batch.JdbcTemporalSupport.toLocalDate;
import static com.hospitalops.batch.JdbcTemporalSupport.toLong;
import static com.hospitalops.batch.JdbcTemporalSupport.toText;

/**
 * Phase 2 Step 2.1/2.3: PATIENT(레거시 HIS) -> FHIR {@code Patient} 증분 동기화 스텝.
 *
 * <p>Step 2.1에서는 매핑 로직이 이 Tasklet 안에 인라인으로 있었으나, Step 2.3에서
 * {@code com.hospitalops.fhir.PatientMapper}(순수 함수, 단위테스트 있음)로 추출하고
 * 이 Tasklet은 행 조회 + DTO 변환 + 매퍼 호출 + 캐시 upsert만 담당하도록 리팩터링했다.</p>
 */
@Component
public class PatientSyncTasklet implements Tasklet {

	static final String RESOURCE_TYPE = "Patient";
	private static final String LEGACY_TABLE = "PATIENT";

	private final JdbcTemplate jdbcTemplate;
	private final SyncWatermarkService watermarkService;
	private final FhirResourceCacheUpsertService cacheUpsertService;

	public PatientSyncTasklet(JdbcTemplate jdbcTemplate, SyncWatermarkService watermarkService,
			FhirResourceCacheUpsertService cacheUpsertService) {
		this.jdbcTemplate = jdbcTemplate;
		this.watermarkService = watermarkService;
		this.cacheUpsertService = cacheUpsertService;
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		LocalDateTime watermark = watermarkService.currentWatermark(RESOURCE_TYPE);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT patient_id, synthetic_patient_no, birth_date, death_date, gender, race, " +
						"ethnicity, marital_status, first_name, middle_name, last_name " +
						"FROM PATIENT WHERE updated_at > ? ORDER BY updated_at",
				watermark);

		for (Map<String, Object> row : rows) {
			PatientRow patientRow = toRow(row);
			Patient resource = PatientMapper.toFhir(patientRow);
			// Patient 리소스 자신은 소속 환자가 없으므로 patient_fhir_id는 NULL이다.
			cacheUpsertService.upsert(RESOURCE_TYPE, patientRow.patientId(), resource, null);
		}

		watermarkService.advanceWatermarkToTableMax(RESOURCE_TYPE, LEGACY_TABLE);
		contribution.incrementWriteCount(rows.size());
		return RepeatStatus.FINISHED;
	}

	private static PatientRow toRow(Map<String, Object> row) {
		return new PatientRow(
				toLong(row.get("patient_id")),
				toText(row.get("synthetic_patient_no")),
				toLocalDate(row.get("birth_date")),
				toLocalDate(row.get("death_date")),
				toText(row.get("gender")),
				toText(row.get("marital_status")),
				toText(row.get("first_name")),
				toText(row.get("middle_name")),
				toText(row.get("last_name")));
	}
}
