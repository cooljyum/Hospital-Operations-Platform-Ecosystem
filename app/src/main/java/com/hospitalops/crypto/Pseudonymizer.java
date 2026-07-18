package com.hospitalops.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Phase 4 Step 4.1: HMAC-SHA-256 가명화(pseudonymization).
 *
 * <p>내부 불변 PK({@code patient_id} 등)는 절대 바꾸지 않는다 — 이 클래스는 그 PK를
 * 화면/API 응답 등 "외부 노출" 지점에서만 감싸는 표시 계층이다. 같은 원본 입력은
 * 항상 같은 가명 식별자를 만들어내야 하므로(결정론적) 무작위 salt 대신 서버가
 * 보관하는 고정 HMAC 키로 keyed hash를 계산한다 — 키가 없으면 원본 값을 복원할 수
 * 없지만, 키를 아는 쪽에서는 같은 원본에 대해 항상 같은 출력을 재현할 수 있다
 * (예: 같은 환자의 서로 다른 화면·API 응답에 걸쳐 동일 가명 식별자를 일관되게 노출).</p>
 *
 * <p>HMAC 키는 {@code app.crypto.pseudonymizer.hmac-key} 설정(환경변수
 * {@code PSEUDONYMIZER_HMAC_KEY})으로 주입한다 — 코드에 하드코딩하지 않는다.
 * application.yml의 기본값은 로컬 개발/테스트 전용 placeholder이고, 운영 배포에서는
 * 반드시 환경변수로 실제 키를 덮어써야 한다(4.2의 KEK와 마찬가지로 비밀은 설정 밖에서
 * 주입되는 것이 원칙이나, 가명화 키는 기동 필수 조건까지는 아니므로 개발 편의를 위한
 * fallback을 둔다 — 절대 기동을 막을 필요가 없는 상수 치환 계층이기 때문이다).</p>
 *
 * <p>출력은 원본 값을 부분 문자열로도, 가역적인 형태로도 포함하지 않는다 —
 * HMAC-SHA-256 다이제스트를 16진수로 인코딩한 64자 문자열뿐이다.</p>
 */
@Component
public class Pseudonymizer {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final SecretKeySpec keySpec;

	public Pseudonymizer(@Value("${app.crypto.pseudonymizer.hmac-key}") String hmacKey) {
		if (hmacKey == null || hmacKey.isBlank()) {
			throw new IllegalStateException(
					"app.crypto.pseudonymizer.hmac-key(PSEUDONYMIZER_HMAC_KEY)가 설정되지 않았습니다. "
							+ "가명화 HMAC 키 없이는 Pseudonymizer를 생성할 수 없습니다.");
		}
		this.keySpec = new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
	}

	/**
	 * 원본 식별자(예: 내부 PK를 문자열화한 값)를 결정론적 가명 식별자로 치환한다.
	 * 같은 입력에 대해서는 항상 같은 출력을 반환하며, 출력만으로는 원본 값을 복원할 수
	 * 없다(HMAC은 일방향 함수이고, 키는 이 컴포넌트 밖으로 노출되지 않는다).
	 *
	 * @param rawIdentifier 노출하면 안 되는 원본 식별자(내부 PK 등). null 불가.
	 * @return 64자 16진수 HMAC-SHA-256 다이제스트 문자열
	 */
	public String pseudonymize(String rawIdentifier) {
		Objects.requireNonNull(rawIdentifier, "rawIdentifier는 null일 수 없습니다.");
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(keySpec);
			byte[] digest = mac.doFinal(rawIdentifier.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC-SHA-256 가명화 처리 실패", e);
		}
	}
}
