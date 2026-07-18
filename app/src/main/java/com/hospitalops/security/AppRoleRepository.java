package com.hospitalops.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Phase 3 Step 3.1: APP_ROLE 전용 Repository. */
public interface AppRoleRepository extends JpaRepository<AppRole, Long> {

	Optional<AppRole> findByRoleName(String roleName);
}
