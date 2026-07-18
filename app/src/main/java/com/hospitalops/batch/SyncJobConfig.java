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
 * Phase 2 Step 2.1: 레거시 HIS -> FHIR_RESOURCE_CACHE 증분 동기화 배치(fhirSyncJob).
 *
 * <p>4개 Tasklet 스텝을 순서대로 실행한다(Patient -> Encounter -> Observation ->
 * MedicationRequest). 각 스텝은 독립적으로 자기 리소스 타입의 워터마크만 다루므로
 * 순서 자체는 결과에 영향을 주지 않지만, 참조 관계(Observation/MedicationRequest가
 * Patient/Encounter를 Reference로 가리킴)를 고려해 참조 대상을 먼저 동기화한다.</p>
 */
@Configuration
public class SyncJobConfig {

	@Bean
	public Job fhirSyncJob(JobRepository jobRepository, Step syncPatientStep, Step syncEncounterStep,
			Step syncObservationStep, Step syncMedicationRequestStep) {
		return new JobBuilder("fhirSyncJob", jobRepository)
				.start(syncPatientStep)
				.next(syncEncounterStep)
				.next(syncObservationStep)
				.next(syncMedicationRequestStep)
				.build();
	}

	@Bean
	public Step syncPatientStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			PatientSyncTasklet tasklet) {
		return new StepBuilder("syncPatientStep", jobRepository)
				.tasklet(tasklet, transactionManager)
				.build();
	}

	@Bean
	public Step syncEncounterStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			EncounterSyncTasklet tasklet) {
		return new StepBuilder("syncEncounterStep", jobRepository)
				.tasklet(tasklet, transactionManager)
				.build();
	}

	@Bean
	public Step syncObservationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			ObservationSyncTasklet tasklet) {
		return new StepBuilder("syncObservationStep", jobRepository)
				.tasklet(tasklet, transactionManager)
				.build();
	}

	@Bean
	public Step syncMedicationRequestStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			MedicationRequestSyncTasklet tasklet) {
		return new StepBuilder("syncMedicationRequestStep", jobRepository)
				.tasklet(tasklet, transactionManager)
				.build();
	}
}
