package com.hospitalops.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Phase 3 Step 3.3: AUDIT_LOG 전용 Repository. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findByActorUsernameAndResultCode(String actorUsername, String resultCode);

	/**
	 * Phase 5 Step 5.2: 감사 로그 조회 화면의 필터(행위자/기간/대상) 검색.
	 *
	 * <p>각 파라미터는 전부 선택 사항이다 - {@code NULL}로 넘어온 조건은 JPQL의
	 * {@code :param IS NULL OR ...} 분기로 건너뛴다(=필터를 적용하지 않음). 컨트롤러가
	 * 공백 문자열을 미리 {@code null}로 정규화해서 넘긴다.</p>
	 */
	@Query("SELECT a FROM AuditLog a WHERE "
			+ "(:actorUsername IS NULL OR a.actorUsername = :actorUsername) "
			+ "AND (:targetPk IS NULL OR a.targetPk = :targetPk) "
			+ "AND (:from IS NULL OR a.occurredAt >= :from) "
			+ "AND (:to IS NULL OR a.occurredAt <= :to) "
			+ "ORDER BY a.occurredAt DESC, a.auditId DESC")
	List<AuditLog> search(
			@Param("actorUsername") String actorUsername,
			@Param("targetPk") String targetPk,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to);
}
