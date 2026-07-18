package com.hospitalops.batch;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Phase 2 Step 2.1: {@code JdbcTemplate.queryForList(...)}가 돌려주는 {@code Map}의
 * DATE/DATETIME 컬럼 값은 JDBC 드라이버/버전에 따라 {@link java.sql.Date}/
 * {@link java.sql.Timestamp} 또는 이미 변환된 {@code java.time.*} 타입일 수 있다.
 * 각 SyncJob Tasklet에서 매번 방어적으로 분기하지 않도록 한 곳에 모은다.
 */
final class JdbcTemporalSupport {

	private JdbcTemporalSupport() {
	}

	static LocalDate toLocalDate(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof LocalDate localDate) {
			return localDate;
		}
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime.toLocalDate();
		}
		if (value instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toLocalDateTime().toLocalDate();
		}
		throw new IllegalStateException("예상치 못한 날짜 타입: " + value.getClass());
	}

	static LocalDateTime toLocalDateTime(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime;
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toLocalDateTime();
		}
		if (value instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate().atStartOfDay();
		}
		throw new IllegalStateException("예상치 못한 일시 타입: " + value.getClass());
	}

	static String toText(Object value) {
		return value == null ? null : value.toString();
	}

	static Long toLong(Object value) {
		return value == null ? null : ((Number) value).longValue();
	}
}
