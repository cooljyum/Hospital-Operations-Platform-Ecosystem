package com.hospitalops.stats;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 Step 4.4 acceptance criteria 검증: 셀 값 &lt;5인 통계는 억제 표시로만 나오고
 * 원값이 어디에도(직렬화된 JSON 포함) 노출되지 않는다. 순수 함수 + Jackson 직렬화만
 * 확인하면 되므로 Spring 컨텍스트 없이 검증한다.
 */
class SuppressedCellTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void rawCountsBelowFiveAreSuppressed() {
		for (long rawCount : new long[] {0, 1, 2, 3, 4}) {
			SuppressedCell cell = SuppressedCell.of(rawCount);

			assertThat(cell.isSuppressed()).as("rawCount=" + rawCount).isTrue();
			assertThat(cell.getCount()).as("rawCount=" + rawCount).isNull();
			assertThat(cell.getDisplay()).isEqualTo(SuppressedCell.SUPPRESSED_LABEL);
		}
	}

	@Test
	void rawCountsAtOrAboveFiveAreNotSuppressed() {
		for (long rawCount : new long[] {5, 6, 42, 1000}) {
			SuppressedCell cell = SuppressedCell.of(rawCount);

			assertThat(cell.isSuppressed()).as("rawCount=" + rawCount).isFalse();
			assertThat(cell.getCount()).as("rawCount=" + rawCount).isEqualTo(rawCount);
			assertThat(cell.getDisplay()).isEqualTo(String.valueOf(rawCount));
		}
	}

	@Test
	void suppressedCellJsonNeverContainsTheRawCountField() throws Exception {
		SuppressedCell cell = SuppressedCell.of(3);

		String json = objectMapper.writeValueAsString(cell);

		// 원값(3)이 count 필드로 직렬화되면 안 된다 - count는 null이어야 한다.
		assertThat(json).contains("\"count\":null");
		assertThat(json).doesNotContain("\"count\":3");
		assertThat(json).contains(SuppressedCell.SUPPRESSED_LABEL);
	}

	@Test
	void nonSuppressedCellJsonDoesExposeTheActualCount() throws Exception {
		SuppressedCell cell = SuppressedCell.of(6);

		String json = objectMapper.writeValueAsString(cell);

		assertThat(json).contains("\"count\":6");
	}

	@Test
	void rejectsNegativeRawCount() {
		assertThatThrownBy(() -> SuppressedCell.of(-1)).isInstanceOf(IllegalArgumentException.class);
	}
}
