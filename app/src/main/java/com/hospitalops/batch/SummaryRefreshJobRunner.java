package com.hospitalops.batch;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Phase 6 Step 6.1: summaryRefreshJob 실행 트리거.
 *
 * <p>{@code spring.batch.job.enabled=false}(application.yml)로 Spring Boot의 자동 배치
 * 기동을 꺼 둔 상태에서, 이 러너가 {@code summary.refresh.job.enabled=true}일 때만
 * 명시적으로 {@link JobLauncher}를 통해 job을 실행한다 — SyncJobRunner와 같은
 * "기본 비활성 + 명시적 opt-in" 패턴이다(평소 앱 기동/테스트에서 자동 실행되지 않음).</p>
 *
 * <p>매 실행마다 {@code runAt} identifying 파라미터를 새로 부여해 Spring Batch가 매번
 * 새 JobInstance로 인식하게 한다 — 이 job은 워터마크 없이 매번 전체 재계산하는 설계라
 * "다시 돌려도 안전"하다(멱등: 여러 번 실행해도 최종 상태는 원본 집계와 항상 일치).</p>
 *
 * <p>Phase 7 Step 7.1: SyncJobRunner와 동일하게 {@code runOnce()}에서 매 실행 결과를
 * {@code hospitalops.batch.job.runs} 카운터(job=summaryRefreshJob, status=success|failure
 * 태그)로 기록한다.</p>
 */
@Component
public class SummaryRefreshJobRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(SummaryRefreshJobRunner.class);

	private static final String METRIC_NAME = "hospitalops.batch.job.runs";
	private static final String JOB_TAG_VALUE = "summaryRefreshJob";

	private final SummaryRefreshProperties properties;
	private final JobLauncher jobLauncher;
	private final Job summaryRefreshJob;
	private final MeterRegistry meterRegistry;

	public SummaryRefreshJobRunner(SummaryRefreshProperties properties, JobLauncher jobLauncher,
			Job summaryRefreshJob, MeterRegistry meterRegistry) {
		this.properties = properties;
		this.jobLauncher = jobLauncher;
		this.summaryRefreshJob = summaryRefreshJob;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public void run(String... args) throws Exception {
		if (!properties.isEnabled()) {
			log.debug("SummaryRefreshJobRunner: summary.refresh.job.enabled=false, 재계산 배치를 건너뜁니다.");
			return;
		}
		JobExecution execution = runOnce();
		log.info("summaryRefreshJob 실행 완료: status={}", execution.getStatus());
	}

	/** 프로퍼티와 무관하게 즉시 1회 실행한다(테스트/수동 트리거용). */
	public JobExecution runOnce() throws Exception {
		JobParameters params = new JobParametersBuilder()
				.addLong("runAt", System.currentTimeMillis())
				.toJobParameters();
		try {
			JobExecution execution = jobLauncher.run(summaryRefreshJob, params);
			recordOutcome(execution.getStatus().isUnsuccessful() ? "failure" : "success");
			return execution;
		} catch (Exception e) {
			recordOutcome("failure");
			throw e;
		}
	}

	private void recordOutcome(String status) {
		Counter.builder(METRIC_NAME)
				.tag("job", JOB_TAG_VALUE)
				.tag("status", status)
				.description("Spring Batch job 실행 결과 카운트(성공/실패)")
				.register(meterRegistry)
				.increment();
	}
}
