package com.hospitalops.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 Step 4.1 acceptance criteria 검증: 동일 입력 -> 동일 해시(결정론적),
 * 원본 PK는 출력 어디에도 노출되지 않음. 순수 함수라 Spring 컨텍스트 없이
 * {@link Pseudonymizer}를 직접 생성해서 검증한다(PatientMapperTests와 동일한 패턴).
 */
class PseudonymizerTests {

	private static final String TEST_KEY = "unit-test-only-hmac-key";

	@Test
	void sameInputAlwaysProducesSameDeterministicHash() {
		Pseudonymizer pseudonymizer = new Pseudonymizer(TEST_KEY);

		String first = pseudonymizer.pseudonymize("patient-id-42");
		String second = pseudonymizer.pseudonymize("patient-id-42");

		assertThat(first).isEqualTo(second);
	}

	@Test
	void differentInputsProduceDifferentHashes() {
		Pseudonymizer pseudonymizer = new Pseudonymizer(TEST_KEY);

		String forty2 = pseudonymizer.pseudonymize("patient-id-42");
		String forty3 = pseudonymizer.pseudonymize("patient-id-43");

		assertThat(forty2).isNotEqualTo(forty3);
	}

	@Test
	void differentKeysProduceDifferentHashesForSameInput() {
		Pseudonymizer keyA = new Pseudonymizer(TEST_KEY);
		Pseudonymizer keyB = new Pseudonymizer("a-completely-different-key");

		assertThat(keyA.pseudonymize("patient-id-42"))
				.isNotEqualTo(keyB.pseudonymize("patient-id-42"));
	}

	@Test
	void outputIsA64CharacterHexDigestAndNeverContainsTheRawInput() {
		Pseudonymizer pseudonymizer = new Pseudonymizer(TEST_KEY);
		String rawIdentifier = "internal-pk-999999";

		String pseudonymized = pseudonymizer.pseudonymize(rawIdentifier);

		// HMAC-SHA-256 다이제스트 -> 32바이트 -> 16진수 인코딩 시 64자.
		assertThat(pseudonymized).hasSize(64).matches("^[0-9a-f]{64}$");
		// 원본 PK(가공 전 문자열)가 출력 문자열 안에 그대로 노출되면 안 된다.
		assertThat(pseudonymized).doesNotContain(rawIdentifier);
	}

	@Test
	void rejectsNullInput() {
		Pseudonymizer pseudonymizer = new Pseudonymizer(TEST_KEY);

		assertThatThrownBy(() -> pseudonymizer.pseudonymize(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void constructorRejectsBlankOrMissingHmacKey() {
		assertThatThrownBy(() -> new Pseudonymizer(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new Pseudonymizer("")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new Pseudonymizer("   ")).isInstanceOf(IllegalStateException.class);
	}
}
