package com.hospitalops.stats;

/**
 * Phase 4 Step 4.4: k=5 익명성 + small-cell suppression.
 *
 * <p>통계 셀(예: 특정 성별×연령대 조합의 환자 수)의 실제 값이 {@link #K_THRESHOLD} 미만이면
 * 그 값을 어떤 형태로도 응답에 실어 보내지 않는다. 이 클래스가 그 경계를 강제하는
 * 유일한 지점이다 — {@link #of(long)}로 만들어진 인스턴스는, 원값이 임계치 미만이었다면
 * <b>그 원값을 아예 필드로 갖고 있지 않는다</b>({@link #getCount()}가 {@code null}).
 * 그래서 이 객체를 Jackson으로 직렬화하든 로그로 찍든, 억제된 셀의 실제 숫자가 어딘가에
 * 실수로 노출될 방법이 구조적으로 없다(값 자체가 인스턴스 안에 존재하지 않으므로).</p>
 */
public final class SuppressedCell {

	/** k=5 익명성 임계치: 이 값 미만인 셀은 억제한다. */
	public static final int K_THRESHOLD = 5;

	/** 억제된 셀에 노출하는 표시 문자열. */
	public static final String SUPPRESSED_LABEL = "< 5";

	private final Long count;
	private final String display;
	private final boolean suppressed;

	private SuppressedCell(Long count, String display, boolean suppressed) {
		this.count = count;
		this.display = display;
		this.suppressed = suppressed;
	}

	/**
	 * 원값(rawCount)으로부터 억제 여부가 반영된 셀을 만든다. rawCount가 {@link #K_THRESHOLD}
	 * 미만이면 반환되는 인스턴스는 그 값을 어디에도 보관하지 않는다.
	 */
	public static SuppressedCell of(long rawCount) {
		if (rawCount < 0) {
			throw new IllegalArgumentException("rawCount는 음수일 수 없습니다: " + rawCount);
		}
		if (rawCount < K_THRESHOLD) {
			return new SuppressedCell(null, SUPPRESSED_LABEL, true);
		}
		return new SuppressedCell(rawCount, String.valueOf(rawCount), false);
	}

	/** 억제되지 않은 경우에만 실제 값을 반환한다. 억제된 경우 항상 {@code null}. */
	public Long getCount() {
		return count;
	}

	/** 화면/응답에 노출할 표시 문자열 - 억제된 경우 {@value #SUPPRESSED_LABEL}. */
	public String getDisplay() {
		return display;
	}

	public boolean isSuppressed() {
		return suppressed;
	}
}
