package com.hospitalops.batch;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Phase 6 Step 6.1: PATIENT_VISIT_SUMMARY(V13) 전체 재계산.
 *
 * <p>증분 워터마크 없이 매 실행마다 "delete 전체 + 원본 3개 테이블(VISIT/LAB_RESULT/
 * PRESCRIPTION) 재집계 insert"를 하나의 트랜잭션(step의 {@code PlatformTransactionManager})
 * 안에서 수행한다 — MySQL {@code TRUNCATE}는 암묵적 커밋을 유발해 스텝 트랜잭션 경계를
 * 깨뜨리므로 대신 {@code DELETE FROM}을 쓴다.</p>
 *
 * <p>집계 방식: VISIT/LAB_RESULT/PRESCRIPTION을 각각 {@code patient_id}로 먼저
 * GROUP BY 하여 서브쿼리로 만든 뒤 PATIENT에 LEFT JOIN한다. LAB_RESULT/PRESCRIPTION은
 * PATIENT를 통하지 않고 자체 {@code patient_id} 컬럼을 이미 갖고 있으므로, VISIT을 거쳐
 * 다시 조인하는 다대다 팬아웃 없이 정확한 건수를 얻는다. 방문/검사/처방이 하나도 없는
 * 환자도 COALESCE(...,0)으로 0건 행이 채워진다.</p>
 */
@Component
public class SummaryRefreshTasklet implements Tasklet {

	private static final String DELETE_ALL = "DELETE FROM PATIENT_VISIT_SUMMARY";

	private static final String REFRESH_INSERT = """
			INSERT INTO PATIENT_VISIT_SUMMARY
				(patient_id, visit_count, first_visit_at, last_visit_at,
				 lab_result_count, prescription_count, refreshed_at)
			SELECT
				p.patient_id,
				COALESCE(v.visit_count, 0),
				v.first_visit_at,
				v.last_visit_at,
				COALESCE(l.lab_result_count, 0),
				COALESCE(pr.prescription_count, 0),
				NOW()
			FROM PATIENT p
			LEFT JOIN (
				SELECT patient_id, COUNT(*) AS visit_count,
					MIN(started_at) AS first_visit_at, MAX(started_at) AS last_visit_at
				FROM VISIT
				GROUP BY patient_id
			) v ON v.patient_id = p.patient_id
			LEFT JOIN (
				SELECT patient_id, COUNT(*) AS lab_result_count
				FROM LAB_RESULT
				GROUP BY patient_id
			) l ON l.patient_id = p.patient_id
			LEFT JOIN (
				SELECT patient_id, COUNT(*) AS prescription_count
				FROM PRESCRIPTION
				GROUP BY patient_id
			) pr ON pr.patient_id = p.patient_id
			""";

	private final JdbcTemplate jdbcTemplate;

	public SummaryRefreshTasklet(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		jdbcTemplate.update(DELETE_ALL);
		int inserted = jdbcTemplate.update(REFRESH_INSERT);
		contribution.incrementWriteCount(inserted);
		return RepeatStatus.FINISHED;
	}
}
