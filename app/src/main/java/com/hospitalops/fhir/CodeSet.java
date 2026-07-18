package com.hospitalops.fhir;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Phase 2 Step 2.2: 레거시 코드(LAB_RESULT.code/PRESCRIPTION.medication_code) ->
 * FHIR Coding(system/code/display) 변환용 로컬 코드셋.
 *
 * <p>{@code code_system}은 어느 레거시 컬럼 계열에서 온 코드인지 구분하는 내부 분류축
 * ("LAB_RESULT" 또는 "PRESCRIPTION")이고, {@code fhir_system}은 실제 표준 코드 시스템
 * URI(LOINC/RxNorm 등)다. Step 2.3의 매퍼는 (code_system, source_code)로 이 테이블을
 * 조회하고, 없으면 원본 code/description을 text로만 채우는 폴백을 탄다({@link
 * CodeSetLookupService} 참고) — 매퍼 코드 안에 코드 목록을 하드코딩하지 않기 위함이다.</p>
 */
@Entity
@Table(name = "CODE_SET", uniqueConstraints = {
		@UniqueConstraint(name = "uq_code_set_system_source", columnNames = {"code_system", "source_code"})
})
public class CodeSet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "code_system", nullable = false, length = 30)
	private String codeSystem;

	@Column(name = "source_code", nullable = false, length = 50)
	private String sourceCode;

	@Column(name = "source_description", length = 500)
	private String sourceDescription;

	@Column(name = "fhir_system", nullable = false, length = 255)
	private String fhirSystem;

	@Column(name = "fhir_code", nullable = false, length = 50)
	private String fhirCode;

	@Column(name = "fhir_display", length = 500)
	private String fhirDisplay;

	protected CodeSet() {
		// JPA
	}

	public Long getId() {
		return id;
	}

	public String getCodeSystem() {
		return codeSystem;
	}

	public String getSourceCode() {
		return sourceCode;
	}

	public String getSourceDescription() {
		return sourceDescription;
	}

	public String getFhirSystem() {
		return fhirSystem;
	}

	public String getFhirCode() {
		return fhirCode;
	}

	public String getFhirDisplay() {
		return fhirDisplay;
	}
}
