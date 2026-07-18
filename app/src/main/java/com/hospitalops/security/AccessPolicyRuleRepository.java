package com.hospitalops.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Phase 3 Step 3.2: ACCESS_POLICY_RULES 전용 Repository. */
public interface AccessPolicyRuleRepository extends JpaRepository<AccessPolicyRule, Long> {

	List<AccessPolicyRule> findByAllowedTrue();
}
