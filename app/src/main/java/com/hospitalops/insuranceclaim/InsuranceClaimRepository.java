package com.hospitalops.insuranceclaim;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, Long> {
}
