package com.hospitalops.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Phase 3 Step 3.3: AUDIT_LOG 전용 Repository. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findByActorUsernameAndResultCode(String actorUsername, String resultCode);
}
