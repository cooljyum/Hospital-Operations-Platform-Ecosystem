package com.hospitalops.batch;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Phase 6 Step 6.1: PATIENT_VISIT_SUMMARY 전체 재계산 배치(summaryRefreshJob).
 *
 * <p>단일 Tasklet 스텝(summaryRefreshStep)으로 구성한다 — fhirSyncJob(SyncJobConfig)과
 * 달리 리소스 타입별 순서 의존성이 없으므로 스텝을 나눌 이유가 없다.</p>
 */
@Configuration
public class SummaryRefreshJobConfig {

	@Bean
	public Job summaryRefreshJob(JobRepository jobRepository, Step summaryRefreshStep) {
		return new JobBuilder("summaryRefreshJob", jobRepository)
				.start(summaryRefreshStep)
				.build();
	}

	@Bean
	public Step summaryRefreshStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			SummaryRefreshTasklet tasklet) {
		return new StepBuilder("summaryRefreshStep", jobRepository)
				.tasklet(tasklet, transactionManager)
				.build();
	}
}
