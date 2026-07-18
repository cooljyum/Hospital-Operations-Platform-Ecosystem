package com.hospitalops.fhir;

import org.hl7.fhir.r4.model.Coding;
import org.springframework.stereotype.Service;

/**
 * Phase 2 Step 2.2: CODE_SET 조회를 캡슐화해 FHIR {@link Coding}을 만들어 주는 서비스.
 * Step 2.3의 ObservationMapper/MedicationRequestMapper가 이 서비스를 통해서만 코드셋을
 * 조회한다(매퍼 코드 안에 코드 목록을 하드코딩하지 않기 위함).
 *
 * <p>코드셋에 없는 코드(폴백)는 system 없이 원본 code만 담은 {@link Coding}을 돌려준다
 * — CodeableConcept.text에 원본 description을 채우는 것은 호출부(매퍼) 책임이다.</p>
 */
@Service
public class CodeSetLookupService {

	private final CodeSetRepository repository;

	public CodeSetLookupService(CodeSetRepository repository) {
		this.repository = repository;
	}

	public Coding toCoding(String codeSystem, String sourceCode, String sourceDescription) {
		if (sourceCode == null) {
			return null;
		}
		return repository.findByCodeSystemAndSourceCode(codeSystem, sourceCode)
				.map(codeSet -> new Coding()
						.setSystem(codeSet.getFhirSystem())
						.setCode(codeSet.getFhirCode())
						.setDisplay(codeSet.getFhirDisplay()))
				.orElseGet(() -> new Coding().setCode(sourceCode).setDisplay(sourceDescription));
	}
}
