package com.hospitalops.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;

/**
 * Phase 2 Step 2.3: 각 Mapper 단위테스트가 공유하는 HAPI FHIR {@link FhirValidator}.
 *
 * <p>PLAN.md 지시대로 "구조 검증, 기본 R4 인스턴스 검증기 정도"만 쓴다(전체 프로파일
 * 검증은 과함) — {@link FhirInstanceValidator} + 기본 R4 프로파일 지원({@link
 * DefaultProfileValidationSupport}) + 인메모리 최소 용어 서비스({@link
 * InMemoryTerminologyServerValidationSupport})만 등록한다.</p>
 *
 * <p>{@link FhirContext}/{@link FhirValidator} 생성 비용이 커서(리소스 정의·프로파일
 * 스캔) 테스트 클래스마다 새로 만들지 않고 정적 필드로 한 번만 만들어 공유한다.</p>
 */
final class FhirValidatorTestSupport {

	static final FhirContext CONTEXT = FhirContext.forR4();
	static final FhirValidator VALIDATOR = buildValidator();

	private FhirValidatorTestSupport() {
	}

	private static FhirValidator buildValidator() {
		ValidationSupportChain chain = new ValidationSupportChain(
				new DefaultProfileValidationSupport(CONTEXT),
				new InMemoryTerminologyServerValidationSupport(CONTEXT));
		FhirInstanceValidator instanceValidator = new FhirInstanceValidator(chain);

		FhirValidator validator = CONTEXT.newValidator();
		validator.registerValidatorModule(instanceValidator);
		return validator;
	}
}
