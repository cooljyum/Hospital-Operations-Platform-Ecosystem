package com.hospitalops.stats;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Phase 4 Step 4.4: PATIENT 테이블 기준 성별×연령대(10년 단위) 환자 수 원값 집계.
 *
 * <p>레거시 HIS 스키마(PATIENT)에서 실제로 존재하는 {@code gender}/{@code birth_date}
 * 컬럼만으로 만들 수 있는 집계축이다. 이 클래스는 <b>원값(raw count)</b>만 반환한다 —
 * k=5 억제는 이 클래스의 책임이 아니라 {@link PatientStatsService}의 책임이다(계층
 * 분리로 "억제를 깜빡한 경로"가 생기지 않게 한다).</p>
 */
@Repository
public class PatientStatsRepository {

	private static final String COUNT_BY_GENDER_AND_AGE_BAND_SQL =
			"SELECT gender, "
					+ "FLOOR(TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) / 10) * 10 AS age_band_start, "
					+ "COUNT(*) AS patient_count "
					+ "FROM PATIENT "
					+ "WHERE birth_date IS NOT NULL AND gender IS NOT NULL "
					+ "GROUP BY gender, age_band_start "
					+ "ORDER BY gender, age_band_start";

	private final JdbcTemplate jdbcTemplate;

	public PatientStatsRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** 성별×연령대(10년 단위 시작값)별 환자 수 원값. 억제 처리 전 값이다. */
	public List<GenderAgeBandRawCount> rawCountsByGenderAndAgeBand() {
		return jdbcTemplate.query(COUNT_BY_GENDER_AND_AGE_BAND_SQL, (rs, rowNum) -> new GenderAgeBandRawCount(
				rs.getString("gender"),
				rs.getInt("age_band_start"),
				rs.getLong("patient_count")));
	}
}
