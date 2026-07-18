package com.hospitalops.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 2 Step 2.1: HAPI FHIR R4 {@link FhirContext}를 애플리케이션 전역 싱글턴 빈으로
 * 노출한다. {@code FhirContext.forR4()}는 생성 비용이 커서(리소스 정의 스캔 등) 요청/배치
 * 스텝마다 새로 만들지 않고 한 번만 만들어 재사용해야 한다는 것이 HAPI FHIR 공식 권고다.
 */
@Configuration
public class FhirContextConfig {

	@Bean
	public FhirContext fhirContext() {
		return FhirContext.forR4();
	}
}
