package com.hospitalops.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Phase 3 Step 3.1: APP_USER 전용 Repository. */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByUsername(String username);

	boolean existsByUsername(String username);
}
