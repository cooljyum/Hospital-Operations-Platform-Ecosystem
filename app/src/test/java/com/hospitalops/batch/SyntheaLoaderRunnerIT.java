package com.hospitalops.batch;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Phase 1 Step 1.3 acceptance criteria 검증: 적재 후 4개 테이블에 row가 존재하고,
 * 재실행해도 중복이 없는지(멱등) 실제 로컬 MySQL(hospital_ops)에 대해 직접 확인한다.
 *
 * <p>{@code data/synthetic/csv}(저장소 루트, Phase 0.5 산출물)가 로컬 디스크에 있는
 * 환경에서만 실행한다 — 그 디렉터리가 없는 환경(CI 등)에서는 스킵한다(assumeTrue).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SyntheaLoaderRunnerIT {

	@Autowired
	private SyntheaLoaderRunner loaderRunner;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void loadingTwiceInARowProducesNoDuplicateRows() {
		Path csvDir = Path.of("..", "data", "synthetic", "csv").normalize();
		assumeTrue(Files.exists(csvDir.resolve("patients.csv")),
				"로컬 data/synthetic/csv가 없어 SyntheaLoaderRunnerIT를 스킵합니다: " + csvDir.toAbsolutePath());

		// 주의: 이 DB는 로컬에 상주하는 진짜 MySQL이라 이전 테스트 실행의 결과가 그대로
		// 남아 있을 수 있다(적재 자체가 멱등이므로 "이전 실행 대비 증가"를 기대할 수
		// 없다 — 오히려 그게 재확인하려는 성질이다). 그래서 "이번 실행 전/후" 델타가
		// 아니라 CSV가 실제로 담고 있는 절대 행 수(patients.csv=12명 등)를 기준으로
		// 검증하고, 첫 번째 호출과 두 번째 호출 사이의 델타(=0)로 멱등성을 확인한다.
		SyntheaLoaderRunner.LoadSummary first = loaderRunner.load(csvDir);

		long patientsAfterFirst = count("patient");
		long visitsAfterFirst = count("visit");
		long labResultsAfterFirst = count("lab_result");
		long prescriptionsAfterFirst = count("prescription");

		assertThat(first.patients()).isEqualTo(12);
		assertThat(patientsAfterFirst).isEqualTo(12);
		assertThat(visitsAfterFirst).isEqualTo(first.visits());
		assertThat(visitsAfterFirst).isGreaterThan(0);
		assertThat(labResultsAfterFirst).isGreaterThan(0);
		assertThat(prescriptionsAfterFirst).isGreaterThan(0);

		SyntheaLoaderRunner.LoadSummary second = loaderRunner.load(csvDir);

		long patientsAfterSecond = count("patient");
		long visitsAfterSecond = count("visit");
		long labResultsAfterSecond = count("lab_result");
		long prescriptionsAfterSecond = count("prescription");

		assertThat(patientsAfterSecond).isEqualTo(patientsAfterFirst);
		assertThat(visitsAfterSecond).isEqualTo(visitsAfterFirst);
		assertThat(labResultsAfterSecond).isEqualTo(labResultsAfterFirst);
		assertThat(prescriptionsAfterSecond).isEqualTo(prescriptionsAfterFirst);
		assertThat(second.patients()).isEqualTo(first.patients());
		assertThat(second.visits()).isEqualTo(first.visits());
	}

	@Test
	void derivedDisplayNoIsDeterministicAndDoesNotLeakSourceUuid() {
		String uuid = "40cb7be2-9c26-6328-3aa0-71db4ed81e06";
		String first = SyntheaLoaderRunner.deriveDisplayNo(uuid);
		String second = SyntheaLoaderRunner.deriveDisplayNo(uuid);

		assertThat(first).isEqualTo(second);
		assertThat(first).doesNotContain(uuid);
	}

	private long count(String table) {
		Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
		return value == null ? 0 : value;
	}
}
