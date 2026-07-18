package com.hospitalops.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Phase 4 Step 4.2: AES-256-GCM 엔벨로프 암호화(envelope encryption).
 *
 * <p>고정 키로 데이터를 직접 암호화하지 않는다 — 매 {@link #encrypt(byte[])} 호출마다
 * 새로운 무작위 DEK(Data Encryption Key)를 생성해 실제 평문을 암호화하고, 그 DEK 자체를
 * KEK(Key Encryption Key)로 다시 감싸(wrap) 암호문과 함께 반환한다({@link EnvelopeEncryptedData}).
 * 복호화 시에는 KEK로 래핑된 DEK를 먼저 풀고, 그렇게 복원한 DEK로 실제 암호문을 복호화한다.
 * 이렇게 하면 (a) 대량 데이터마다 서로 다른 키가 쓰여 하나의 DEK 유출이 전체 데이터로
 * 번지지 않고, (b) 키 회전 시 각 레코드의 실제 암호문을 다시 암호화할 필요 없이 DEK
 * 래핑만 새 KEK로 다시 하면 된다({@code docs/runbooks/key-rotation.md} 참고).</p>
 *
 * <p>KEK는 {@code app.crypto.envelope.kek}(환경변수 {@code ENVELOPE_KEK})로만 주입되고,
 * 운영에서는 Docker secret으로 공급된다는 전제다. 이 프로퍼티가 비어 있으면(로컬 개발에서
 * 환경변수를 안 주고 기동하면) 이 빈의 생성자가 즉시 예외를 던져 애플리케이션 컨텍스트
 * 기동 자체가 실패한다 — KEK 없이 반쪽짜리로 뜨는 상태를 허용하지 않기 위함이다.</p>
 */
@Component
public class EnvelopeCrypto {

	private static final String KEY_ALGORITHM = "AES";
	private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_TAG_LENGTH_BITS = 128;
	private static final int GCM_IV_LENGTH_BYTES = 12;
	private static final int DEK_LENGTH_BYTES = 32; // AES-256

	private final SecretKeySpec kek;
	private final SecureRandom secureRandom = new SecureRandom();

	public EnvelopeCrypto(@Value("${app.crypto.envelope.kek}") String kekBase64) {
		if (kekBase64 == null || kekBase64.isBlank()) {
			throw new IllegalStateException(
					"app.crypto.envelope.kek(ENVELOPE_KEK)가 설정되지 않았습니다. KEK 없이는 "
							+ "EnvelopeCrypto를 생성할 수 없어 애플리케이션을 기동할 수 없습니다. "
							+ "docs/runbooks/key-rotation.md 참고.");
		}
		byte[] kekBytes;
		try {
			kekBytes = Base64.getDecoder().decode(kekBase64);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException(
					"app.crypto.envelope.kek는 Base64로 인코딩된 값이어야 합니다.", e);
		}
		if (kekBytes.length != DEK_LENGTH_BYTES) {
			throw new IllegalStateException(
					"KEK는 AES-256용 32바이트여야 합니다(디코딩된 길이=" + kekBytes.length + "바이트).");
		}
		this.kek = new SecretKeySpec(kekBytes, KEY_ALGORITHM);
	}

	/**
	 * 평문을 엔벨로프 암호화한다: 무작위 DEK 생성 -> DEK로 평문 암호화 -> KEK로 DEK 래핑.
	 */
	public EnvelopeEncryptedData encrypt(byte[] plaintext) {
		Objects.requireNonNull(plaintext, "plaintext는 null일 수 없습니다.");

		byte[] dekBytes = new byte[DEK_LENGTH_BYTES];
		secureRandom.nextBytes(dekBytes);
		try {
			SecretKeySpec dek = new SecretKeySpec(dekBytes, KEY_ALGORITHM);

			byte[] dataIv = randomIv();
			byte[] ciphertext = gcmCrypt(Cipher.ENCRYPT_MODE, dek, dataIv, plaintext);

			byte[] wrapIv = randomIv();
			byte[] wrappedDek = gcmCrypt(Cipher.ENCRYPT_MODE, kek, wrapIv, dekBytes);

			return new EnvelopeEncryptedData(ciphertext, dataIv, wrappedDek, wrapIv);
		} finally {
			Arrays.fill(dekBytes, (byte) 0);
		}
	}

	/**
	 * {@link #encrypt(byte[])}가 만든 엔벨로프를 복호화한다: KEK로 DEK 언래핑 -> 그 DEK로
	 * 실제 암호문을 복호화.
	 */
	public byte[] decrypt(EnvelopeEncryptedData data) {
		Objects.requireNonNull(data, "data는 null일 수 없습니다.");

		byte[] dekBytes = gcmCrypt(Cipher.DECRYPT_MODE, kek, data.wrapIv(), data.wrappedDek());
		try {
			SecretKeySpec dek = new SecretKeySpec(dekBytes, KEY_ALGORITHM);
			return gcmCrypt(Cipher.DECRYPT_MODE, dek, data.dataIv(), data.ciphertext());
		} finally {
			Arrays.fill(dekBytes, (byte) 0);
		}
	}

	private byte[] randomIv() {
		byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
		secureRandom.nextBytes(iv);
		return iv;
	}

	private byte[] gcmCrypt(int mode, SecretKey key, byte[] iv, byte[] input) {
		try {
			Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			cipher.init(mode, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
			return cipher.doFinal(input);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("AES-256-GCM 처리 실패", e);
		}
	}

	/**
	 * 엔벨로프 암호화 산출물: 실제 암호문 + 그 IV, 그리고 KEK로 래핑된 DEK + 그 IV.
	 * 네 값 모두 영속화 대상이다(복호화에 전부 필요).
	 */
	public record EnvelopeEncryptedData(byte[] ciphertext, byte[] dataIv, byte[] wrappedDek, byte[] wrapIv) {
	}
}
