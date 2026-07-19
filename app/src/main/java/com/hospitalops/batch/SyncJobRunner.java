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
 * Phase 2 Step 2.1: fhirSyncJob 실행 트리거.
 *
 * <p>{@code spring.batch.job.enabled=false}(application.yml)로 Spring Boot의 자동 배치
 * 기동을 꺼 두고, 이 러너가 {@code sync.job.enabled=true}일 때만 명시적으로
 * {@link JobLauncher}를 통해 job을 실행한다 — Phase 1의 SyntheaLoaderRunner와 같은
 * "기본 비활성 + 명시적 opt-in" 패턴이다.</p>
 *
 * <p>매 실행마다 {@code runAt} identifying 파라미터를 새로 부여해 Spring Batch가 매번
 * 새 JobInstance로 인식하게 한다 — 실제 멱등성(재실행해도 신규 upsert 없음)은 Spring
 * Batch의 JobInstance 재실행 방지 메커니즘이 아니라 SYNC_WATERMARK 기반 증분 쿼리
 * 자체가 보장한다(이 job은 "몇 번이고 다시 돌려도 되는" 배치로 설계됐다).</p>
 *
 * <p>Phase 7 Step 7.1: {@code runOnce()}(자동 실행 경로와 테스트/수동 트리거 경로가
 * 공유하는 단일 진입점)에서 매 실행 결과(성공/실패)를 {@code hospitalops.batch.job.runs}
 * 카운터로 기록한다 - job/status 태그로 구분해 {@code /actuator/prometheus}에 노출된다.
 * jobLauncher.run 자체가 예외를 던지는 경우(job 실행 자체 실패)도 실패로 집계한다.</p>
 */
@Component
public class SyncJobRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(SyncJobRunner.class);

	private static final String METRIC_NAME = "hospitalops.batch.job.runs";
	private static final String JOB_TAG_VALUE = "fhirSyncJob";

	private final SyncJobProperties properties;
	private final JobLauncher jobLauncher;
	private final Job fhirSyncJob;
	private final MeterRegistry meterRegistry;

	public SyncJobRunner(SyncJobProperties properties, JobLauncher jobLauncher, Job fhirSyncJob,
			MeterRegistry meterRegistry) {
		this.properties = properties;
		this.jobLauncher = jobLauncher;
		this.fhirSyncJob = fhirSyncJob;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public void run(String... args) throws Exception {
		if (!properties.isEnabled()) {
			log.debug("SyncJobRunner: sync.job.enabled=false, 동기화 배치를 건너뜁니다.");
			return;
		}
		JobExecution execution = runOnce();
		log.info("fhirSyncJob 실행 완료: status={}", execution.getStatus());
	}

	/** 프로퍼티와 무관하게 즉시 1회 실행한다(테스트/수동 트리거용). */
	public JobExecution runOnce() throws Exception {
		JobParameters params = new JobParametersBuilder()
				.addLong("runAt", System.currentTimeMillis())
				.toJobParameters();
		try {
			JobExecution execution = jobLauncher.run(fhirSyncJob, params);
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
