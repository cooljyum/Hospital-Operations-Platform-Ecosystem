package com.hospitalops.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 Step 4.2 acceptance criteria 검증: 암호화 -> 복호화 라운드트립, KEK 없이는
 * 빈 생성 실패, 엔벨로프 패턴(무작위 DEK + KEK로 DEK 래핑) 실제 구현 여부. 순수 함수라
 * Spring 컨텍스트 없이 {@link EnvelopeCrypto}를 직접 생성해서 검증한다.
 */
class EnvelopeCryptoTests {

	// 테스트 전용 32바이트(AES-256) Base64 KEK. 운영 키가 아니다.
	private static final String TEST_KEK = Base64.getEncoder().encodeToString(
			"0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

	@Test
	void encryptThenDecryptRoundTripReturnsOriginalPlaintext() {
		EnvelopeCrypto envelopeCrypto = new EnvelopeCrypto(TEST_KEK);
		byte[] plaintext = "환자 진단 상세 - 민감정보".getBytes(StandardCharsets.UTF_8);

		EnvelopeCrypto.EnvelopeEncryptedData encrypted = envelopeCrypto.encrypt(plaintext);
		byte[] decrypted = envelopeCrypto.decrypt(encrypted);

		assertThat(decrypted).isEqualTo(plaintext);
	}

	@Test
	void ciphertextNeverContainsThePlaintextBytes() {
		EnvelopeCrypto envelopeCrypto = new EnvelopeCrypto(TEST_KEK);
		byte[] plaintext = "SENSITIVE-MARKER-VALUE".getBytes(StandardCharsets.UTF_8);

		EnvelopeCrypto.EnvelopeEncryptedData encrypted = envelopeCrypto.encrypt(plaintext);

		assertThat(new String(encrypted.ciphertext(), StandardCharsets.UTF_8))
				.doesNotContain("SENSITIVE-MARKER-VALUE");
	}

	@Test
	void eachEncryptionUsesAFreshRandomDekSoRepeatedCallsProduceDifferentWrappedDeks() {
		EnvelopeCrypto envelopeCrypto = new EnvelopeCrypto(TEST_KEK);
		byte[] plaintext = "same plaintext".getBytes(StandardCharsets.UTF_8);

		EnvelopeCrypto.EnvelopeEncryptedData first = envelopeCrypto.encrypt(plaintext);
		EnvelopeCrypto.EnvelopeEncryptedData second = envelopeCrypto.encrypt(plaintext);

		// 엔벨로프 패턴 검증: 매번 새 DEK를 생성해 래핑하므로, 같은 평문이라도 래핑된
		// DEK와 암호문이 매번 달라야 한다(고정 키로 직접 암호화하는 방식이 아님).
		assertThat(first.wrappedDek()).isNotEqualTo(second.wrappedDek());
		assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());

		// 그럼에도 둘 다 원래 평문으로 복호화되어야 한다(서로 다른 DEK를 각자 올바르게 언래핑).
		assertThat(envelopeCrypto.decrypt(first)).isEqualTo(plaintext);
		assertThat(envelopeCrypto.decrypt(second)).isEqualTo(plaintext);
	}

	@Test
	void wrappingTheDekWithAWrongKekFailsToDecrypt() {
		EnvelopeCrypto envelopeCrypto = new EnvelopeCrypto(TEST_KEK);
		String otherKek = Base64.getEncoder().encodeToString(
				"fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));
		EnvelopeCrypto wrongKeyCrypto = new EnvelopeCrypto(otherKek);

		EnvelopeCrypto.EnvelopeEncryptedData encrypted = envelopeCrypto.encrypt("secret".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> wrongKeyCrypto.decrypt(encrypted))
				.isInstanceOf(IllegalStateException.class)
				.hasCauseInstanceOf(GeneralSecurityException.class);
	}

	@Test
	void constructorRejectsMissingOrBlankKek() {
		assertThatThrownBy(() -> new EnvelopeCrypto(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new EnvelopeCrypto("")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new EnvelopeCrypto("   ")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void constructorRejectsNonBase64Kek() {
		assertThatThrownBy(() -> new EnvelopeCrypto("not-valid-base64-!!!@@@"))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void constructorRejectsKekThatIsNot32BytesAfterDecoding() {
		String tooShort = Base64.getEncoder().encodeToString("short-key".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> new EnvelopeCrypto(tooShort)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void encryptedRecordCarriesIndependentIvsForDataAndWrappedDek() {
		EnvelopeCrypto envelopeCrypto = new EnvelopeCrypto(TEST_KEK);

		EnvelopeCrypto.EnvelopeEncryptedData encrypted =
				envelopeCrypto.encrypt("value".getBytes(StandardCharsets.UTF_8));

		assertThat(encrypted.dataIv()).hasSize(12);
		assertThat(encrypted.wrapIv()).hasSize(12);
		assertThat(Arrays.equals(encrypted.dataIv(), encrypted.wrapIv())).isFalse();
	}
}
