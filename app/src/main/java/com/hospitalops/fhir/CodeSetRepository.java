package com.hospitalops.fhir;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Phase 2 Step 2.2: CODE_SET 전용 Repository. */
public interface CodeSetRepository extends JpaRepository<CodeSet, Long> {

	Optional<CodeSet> findByCodeSystemAndSourceCode(String codeSystem, String sourceCode);
}
