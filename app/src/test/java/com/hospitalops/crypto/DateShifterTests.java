package com.hospitalops.crypto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 Step 4.3 acceptance criteria 검증: 같은 환자의 모든 날짜 필드가 동일 오프셋만큼
 * 이동하고, 환자 간 오프셋은 상이함을 직접 검증한다. 순수 함수라 Spring 컨텍스트 없이
 * {@link DateShifter}를 직접 생성해서 검증한다.
 */
class DateShifterTests {

	private static final String TEST_KEY = "unit-test-only-date-shifter-key";

	@Test
	void sameAppliedOffsetForTwoDifferentDateFieldsOfTheSamePatient() {
		DateShifter dateShifter = new DateShifter(TEST_KEY);
		String patientIdentifier = "patient-1001";

		LocalDate birthDate = LocalDate.of(1980, 5, 1);
		LocalDateTime visitStartedAt = LocalDateTime.of(2024, 3, 10, 9, 30);

		LocalDate shiftedBirthDate = dateShifter.shift(patientIdentifier, birthDate);
		LocalDateTime shiftedVisitStartedAt = dateShifter.shift(patientIdentifier, visitStartedAt);

		long birthDateOffsetDays = ChronoUnit.DAYS.between(birthDate, shiftedBirthDate);
		long visitOffsetDays = ChronoUnit.DAYS.between(visitStartedAt.toLocalDate(), shiftedVisitStartedAt.toLocalDate());

		assertThat(birthDateOffsetDays)
				.as("같은 환자의 서로 다른 두 날짜 필드는 같은 오프셋만큼 이동해야 한다")
				.isEqualTo(visitOffsetDays);
		// 시각(시:분)은 그대로 보존되어야 한다(날짜만 이동).
		assertThat(shiftedVisitStartedAt.toLocalTime()).isEqualTo(visitStartedAt.toLocalTime());
	}

	@Test
	void twoDifferentPatientsGetDifferentOffsets() {
		DateShifter dateShifter = new DateShifter(TEST_KEY);

		long offsetPatientA = dateShifter.offsetDaysFor("patient-1001");
		long offsetPatientB = dateShifter.offsetDaysFor("patient-2002");

		assertThat(offsetPatientA).isNotEqualTo(offsetPatientB);
	}

	@Test
	void sameInputAlwaysProducesTheSameOffsetDeterministically() {
		DateShifter dateShifter = new DateShifter(TEST_KEY);

		long first = dateShifter.offsetDaysFor("patient-4242");
		long second = dateShifter.offsetDaysFor("patient-4242");

		assertThat(first).isEqualTo(second);
	}

	@Test
	void offsetStaysWithinConfiguredRange() {
		DateShifter dateShifter = new DateShifter(TEST_KEY);

		for (int i = 0; i < 50; i++) {
			long offset = dateShifter.offsetDaysFor("patient-" + i);
			assertThat(offset).isBetween(-DateShifter.MAX_OFFSET_DAYS, DateShifter.MAX_OFFSET_DAYS);
		}
	}

	@Test
	void nullDateFieldsPassThroughAsNull() {
		DateShifter dateShifter = new DateShifter(TEST_KEY);

		assertThat(dateShifter.shift("patient-1001", (LocalDate) null)).isNull();
		assertThat(dateShifter.shift("patient-1001", (LocalDateTime) null)).isNull();
	}

	@Test
	void differentDateShifterKeysProduceDifferentOffsetsForSamePatient() {
		DateShifter keyA = new DateShifter(TEST_KEY);
		DateShifter keyB = new DateShifter("a-completely-different-date-shifter-key");

		assertThat(keyA.offsetDaysFor("patient-1001"))
				.isNotEqualTo(keyB.offsetDaysFor("patient-1001"));
	}

	@Test
	void constructorRejectsBlankOrMissingHmacKey() {
		assertThatThrownBy(() -> new DateShifter(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new DateShifter("")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new DateShifter("   ")).isInstanceOf(IllegalStateException.class);
	}
}
