package com.hospitalops.reports;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 9 Step 9.1: PATIENT_VISIT_SUMMARY(V13, Phase 6 Step 6.1) 조회 전용 Repository.
 *
 * <p>이 물리 요약 테이블은 {@code summaryRefreshJob}(SummaryRefreshTasklet)이 명시적으로
 * 트리거될 때만 전체 재계산되므로, 이 화면이 보여주는 값은 "실시간"이 아니라
 * {@link #latestRefreshedAt()} 시점 기준 스냅샷이다 - 컨트롤러가 그 시각을 화면에 함께
 * 노출해 사용자가 데이터 신선도를 알 수 있게 한다.</p>
 *
 * <p>PATIENT와 조인해 표시용 식별자({@code synthetic_patient_no})와 이름만 가져온다 -
 * 내부 불변 PK({@code patient_id})는 SELECT 절에 두지 않는다(deliverable.md §3.2 원칙).</p>
 */
@Repository
public class PatientVisitSummaryRepository {

	private static final String BASE_SELECT = """
			SELECT p.synthetic_patient_no, p.last_name, p.first_name,
				s.visit_count, s.first_visit_at, s.last_visit_at,
				s.lab_result_count, s.prescription_count, s.refreshed_at
			FROM PATIENT_VISIT_SUMMARY s
			JOIN PATIENT p ON p.patient_id = s.patient_id
			WHERE (? IS NULL OR p.synthetic_patient_no LIKE CONCAT('%', ?, '%'))
			""";

	private static final RowMapper<PatientVisitSummaryRow> ROW_MAPPER = (rs, rowNum) -> new PatientVisitSummaryRow(
			rs.getString("synthetic_patient_no"),
			rs.getString("last_name"),
			rs.getString("first_name"),
			rs.getLong("visit_count"),
			rs.getObject("first_visit_at", LocalDateTime.class),
			rs.getObject("last_visit_at", LocalDateTime.class),
			rs.getLong("lab_result_count"),
			rs.getLong("prescription_count"),
			rs.getObject("refreshed_at", LocalDateTime.class));

	private final JdbcTemplate jdbcTemplate;

	public PatientVisitSummaryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * 표시용 식별자({@code synthetic_patient_no}) 부분 일치 검색(선택) + 페이지네이션.
	 * {@code syntheticPatientNoQuery}가 {@code null}이면 필터를 적용하지 않는다.
	 * 방문 건수 내림차순, 동률이면 최근 방문일 내림차순으로 정렬한다.
	 */
	public List<PatientVisitSummaryRow> search(String syntheticPatientNoQuery, int page, int size) {
		int safeSize = Math.max(size, 1);
		int safePage = Math.max(page, 0);
		String sql = BASE_SELECT + "ORDER BY s.visit_count DESC, s.last_visit_at DESC LIMIT ? OFFSET ?";
		return jdbcTemplate.query(sql, ROW_MAPPER,
				syntheticPatientNoQuery, syntheticPatientNoQuery, safeSize, safePage * safeSize);
	}

	/** {@link #search}와 동일한 필터 조건의 전체 건수(페이지네이션용). */
	public long count(String syntheticPatientNoQuery) {
		String sql = "SELECT COUNT(*) FROM PATIENT_VISIT_SUMMARY s "
				+ "JOIN PATIENT p ON p.patient_id = s.patient_id "
				+ "WHERE (? IS NULL OR p.synthetic_patient_no LIKE CONCAT('%', ?, '%'))";
		Long result = jdbcTemplate.queryForObject(sql, Long.class, syntheticPatientNoQuery, syntheticPatientNoQuery);
		return result != null ? result : 0L;
	}

	/** summaryRefreshJob이 요약 테이블을 마지막으로 재계산한 시각. 행이 없으면 {@code null}. */
	public LocalDateTime latestRefreshedAt() {
		return jdbcTemplate.queryForObject("SELECT MAX(refreshed_at) FROM PATIENT_VISIT_SUMMARY", LocalDateTime.class);
	}

	/** 요약 테이블에 행이 하나라도 있는지(=summaryRefreshJob이 최소 1회 실행됐는지). */
	public boolean hasAnyData() {
		Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PATIENT_VISIT_SUMMARY", Long.class);
		return result != null && result > 0;
	}
}
