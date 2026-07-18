package com.hospitalops.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Phase 4 Step 4.3: Date shifting(환자별 고정 오프셋).
 *
 * <p>같은 환자의 모든 날짜 필드(생년월일, 내원 일시, 검사 관찰 일시, 처방 시작/종료 등)는
 * 항상 같은 일수만큼 이동해야 한다 — 그래야 날짜 간 간격(예: 입원 기간, 진단부터 처방까지
 * 걸린 기간)처럼 임상적으로 의미 있는 상대적 시간 구조가 비식별 후에도 보존된다. 반대로
 * 환자마다는 서로 다른 오프셋을 써야, 여러 환자의 절대 날짜를 비교해 원래 날짜를 역산하는
 * 공격을 막을 수 있다.</p>
 *
 * <p>오프셋은 {@link Pseudonymizer}와 같은 방식(HMAC-SHA-256)으로 환자 식별자에서
 * 결정론적으로 유도하되, <b>별도의 키</b>({@code app.crypto.date-shifter.hmac-key},
 * 환경변수 {@code DATE_SHIFTER_HMAC_KEY})를 쓴다 — 두 키가 같으면 가명화 출력과
 * 날짜 오프셋 사이에 알려진 관계가 생겨 공격 표면이 늘어나기 때문이다. 오프셋 범위는
 * [-{@value #MAX_OFFSET_DAYS}, {@value #MAX_OFFSET_DAYS}]일이다.</p>
 */
@Component
public class DateShifter {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	static final long MAX_OFFSET_DAYS = 365;

	private final SecretKeySpec keySpec;

	public DateShifter(@Value("${app.crypto.date-shifter.hmac-key}") String hmacKey) {
		if (hmacKey == null || hmacKey.isBlank()) {
			throw new IllegalStateException(
					"app.crypto.date-shifter.hmac-key(DATE_SHIFTER_HMAC_KEY)가 설정되지 않았습니다. "
							+ "날짜 오프셋 HMAC 키 없이는 DateShifter를 생성할 수 없습니다.");
		}
		this.keySpec = new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
	}

	/**
	 * 주어진 환자 식별자에 대한 고정 오프셋(일 단위)을 계산한다. 같은 식별자는 항상 같은
	 * 오프셋을 반환하고(결정론적), 서로 다른 식별자는 (사실상 항상) 서로 다른 오프셋을
	 * 반환한다. 범위는 [-{@link #MAX_OFFSET_DAYS}, {@link #MAX_OFFSET_DAYS}].
	 */
	public long offsetDaysFor(String patientIdentifier) {
		Objects.requireNonNull(patientIdentifier, "patientIdentifier는 null일 수 없습니다.");
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(keySpec);
			byte[] digest = mac.doFinal(patientIdentifier.getBytes(StandardCharsets.UTF_8));
			long raw = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
			long range = 2 * MAX_OFFSET_DAYS + 1;
			return Math.floorMod(raw, range) - MAX_OFFSET_DAYS;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("날짜 오프셋 HMAC 계산 실패", e);
		}
	}

	/** 같은 환자의 {@link LocalDate} 필드(예: birth_date)를 고정 오프셋만큼 이동한다. */
	public LocalDate shift(String patientIdentifier, LocalDate date) {
		if (date == null) {
			return null;
		}
		return date.plusDays(offsetDaysFor(patientIdentifier));
	}

	/** 같은 환자의 {@link LocalDateTime} 필드(예: started_at, observed_at)를 고정 오프셋만큼 이동한다. */
	public LocalDateTime shift(String patientIdentifier, LocalDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return dateTime.plusDays(offsetDaysFor(patientIdentifier));
	}
}
