# Runbook: 배치 job 실패 → 재처리

> 대상: `fhirSyncJob`(Phase 2 Step 2.1, 레거시 HIS → `FHIR_RESOURCE_CACHE` 증분 동기화)과
> `summaryRefreshJob`(Phase 6 Step 6.1, `PATIENT_VISIT_SUMMARY` 전체 재계산). 이 문서는
> 두 배치 중 하나가 실패했을 때 감지 → 원인 진단 → 재처리 → 정합성 검증까지의 절차를
> 다룬다. §6은 2026-07-19~20에 이 절차 그대로를 실제로 실행해 강제 실패 → 재처리 →
> 성공까지 재현한 실측 로그다(가공 없음).

## 0. 배경 — 이 배치는 Spring Batch 표준 restart를 쓰지 않는다

`SyncJobRunner`/`SummaryRefreshJobRunner`(둘 다 `runOnce()`)는 실행마다 다음처럼
`runAt` identifying 파라미터를 **현재 시각으로 새로 부여**한다:

```java
JobParameters params = new JobParametersBuilder()
        .addLong("runAt", System.currentTimeMillis())
        .toJobParameters();
```

Spring Batch는 `JobParameters`가 다르면 항상 **새 `JobInstance`**로 인식한다. 즉 이
프로젝트는 "실패한 동일 `JobInstance`를 이어서 재시작"하는 Spring Batch의 표준
restart(`JobOperator.restart(executionId)` 등)를 **의도적으로 쓰지 않는다** — 그런
API를 노출하는 컨트롤러/엔드포인트도 코드베이스에 없다(`JobLauncher`를 직접 호출하는
지점은 `SyncJobRunner`/`SummaryRefreshJobRunner`의 `run()`/`runOnce()` 뿐이다).

대신 재처리는 두 가지 설계 특성에 전적으로 의존한다:

1. **`fhirSyncJob`(Patient/Encounter/Observation/MedicationRequest 4개 스텝)**: 각
   스텝(`PatientSyncTasklet` 등)은 `SYNC_WATERMARK.last_synced_at`보다 큰
   `updated_at` 행만 읽고, 스텝 끝에서 워터마크를 레거시 테이블의 현재
   `MAX(updated_at)`으로 전진시킨다(`SyncWatermarkService.advanceWatermarkToTableMax`).
   그리고 `FHIR_RESOURCE_CACHE` upsert는 `(resource_type, source_table_pk)` UNIQUE
   제약(`V5` 마이그레이션, `uq_fhir_cache_type_pk`)을 키로 삼는 find-or-create
   방식(`FhirResourceCacheUpsertService.upsert`)이라 **몇 번을 다시 upsert해도 새
   행이 생기지 않는다**(멱등).
2. **`summaryRefreshJob`**: 워터마크 자체가 없고 매번 `PATIENT_VISIT_SUMMARY` 전체를
   재계산하는 설계라(Phase 6 Step 6.1) 다시 돌려도 항상 원본 집계와 최종 상태가
   일치한다(자연히 멱등).

추가로 각 fhirSyncJob 스텝은 `StepBuilder(...).tasklet(tasklet, transactionManager)`로
구성되어 **스텝 1회 실행 = 트랜잭션 1개**다. 스텝 도중 예외가 나면 그 스텝에서 이미
upsert한 행과 워터마크 전진(`advanceWatermarkToTableMax`)까지 **전부 롤백**된다 —
즉 실패한 스텝은 부분 반영을 남기지 않는다(§6.2에서 실측 확인: 실패 직후
`SYNC_WATERMARK`/`FHIR_RESOURCE_CACHE`가 실행 전과 완전히 동일했다). 이미 성공한
이전 스텝들(예: Patient 스텝이 실패해도 그 이전에 실행됐을 스텝은 없지만, 순서상
뒤 스텝이 실패하면 앞 스텝은 이미 커밋되어 있음)은 별도 트랜잭션이므로 그대로
유지된다.

**결론**: 원인을 해결한 뒤 **잡을 다시 실행하기만 하면** 안전하게 재처리된다 —
워터마크를 수동으로 굴리거나 캐시 테이블을 지울 필요가 없는 것이 기본 경로다(예외
케이스는 §4.2).

## 1. 장애 감지

세 가지 경로가 있다(우선순위 순):

### 1.1 Grafana 알림(권장 — 상시 모니터링)

`docker/grafana/provisioning/alerting/incident-scenarios-alerting.yml`의
`batch-job-failure-alert` 규칙이 다음 조건으로 10초마다 평가된다:

```
sum(hospitalops_batch_job_runs_total{status="failure"}) > 0
```

`increase()`(변화율)가 아니라 **누적 순간값 임계치**를 쓴다 — Phase 8 Step 8.1에서
"프로세스 기동 직후 수백 ms 안에 배치가 실패하면 Prometheus 스크레이프(15s)가 그
찰나의 0→1 전이를 놓쳐 `increase()`가 0으로 계산되는" 관측 공백을 실측했기 때문이다
(`docs/incidents/2026-07-19-batch-job-forced-failure.md` §3). 이 규칙은 "누적 실패가
1건이라도 있으면 프로세스가 재시작(카운터 리셋)되기 전까지 계속 미해결로 취급"하는
배치 실패 알림의 운영 의미에 더 맞는다. 알림 발동/해소가 실제로 동작함은 같은
문서 §4~§5에서 Grafana Alerting HTTP API(`/api/alertmanager/grafana/api/v2/alerts`)
응답으로 이미 검증됐다.

### 1.2 Prometheus 메트릭 직접 확인

```
GET /actuator/prometheus
```

```
hospitalops_batch_job_runs_total{job="fhirSyncJob",status="failure"} 1.0
hospitalops_batch_job_runs_total{job="fhirSyncJob",status="success"} 0.0
hospitalops_batch_job_runs_total{job="summaryRefreshJob",status="failure"} 0.0
hospitalops_batch_job_runs_total{job="summaryRefreshJob",status="success"} 0.0
```

`job` 태그로 어느 배치가 실패했는지, `status="failure"`가 0보다 크면 실패가 있었음을
바로 알 수 있다(생성자에서 0으로 미리 등록해 두므로 실패가 없어도 4개 시계열이 항상
존재한다 — Step 8.1 수정).

### 1.3 애플리케이션 로그 패턴

`SyncJobRunner`/`SummaryRefreshJobRunner`가 매 실행 결과를 아래 형태로 남긴다:

```
... com.hospitalops.batch.SyncJobRunner : fhirSyncJob 실행 완료: status=FAILED
```

또는 Spring Batch 자체 로그:

```
... o.s.b.c.l.s.TaskExecutorJobLauncher : Job: [SimpleJob: [name=fhirSyncJob]]
    completed with the following parameters: [...] and the following status: [FAILED] in ...ms
... o.s.batch.core.step.AbstractStep : Encountered an error executing step <stepName> in job <jobName>
```

`status=FAILED`/`status: [FAILED]` 문자열로 grep 가능하다. 이 예외는 Spring Batch가
tasklet 내부에서 잡아 `JobExecution`을 `FAILED`로 정상 반환하는 것이라(§0), **애플리케이션
자체는 죽지 않고 계속 서비스한다** — "배치 실패"와 "앱 전체 장애"를 로그만으로도
구분해야 한다.

## 2. 원인 진단 절차

1. **애플리케이션 로그에서 스택트레이스 확인**: 어느 스텝(`syncPatientStep`,
   `syncEncounterStep`, `syncObservationStep`, `syncMedicationRequestStep`, 또는
   `summaryRefreshJob`의 스텝)에서 어떤 예외(`SQLSyntaxErrorException`,
   `SQLTransientConnectionException` 등)가 났는지 확인한다.
2. **Spring Batch 메타데이터 테이블로 교차 확인**(로그가 유실/회전됐을 때도 이 방법은
   유효 — `BATCH_JOB_EXECUTION`/`BATCH_STEP_EXECUTION`은 DB에 영구 기록됨):

   ```sql
   SELECT je.JOB_EXECUTION_ID, ji.JOB_NAME, je.STATUS, je.START_TIME, je.END_TIME
   FROM BATCH_JOB_EXECUTION je
   JOIN BATCH_JOB_INSTANCE ji ON je.JOB_INSTANCE_ID = ji.JOB_INSTANCE_ID
   ORDER BY je.JOB_EXECUTION_ID DESC LIMIT 5;

   SELECT se.STEP_EXECUTION_ID, se.STEP_NAME, se.STATUS, se.EXIT_CODE, se.EXIT_MESSAGE
   FROM BATCH_STEP_EXECUTION se
   ORDER BY se.STEP_EXECUTION_ID DESC LIMIT 5;
   ```

   `STATUS='FAILED'`인 최신 `JOB_EXECUTION_ID`를 찾고, 같은 실행의 `BATCH_STEP_EXECUTION`
   행 중 `STATUS='FAILED'`인 스텝의 `EXIT_MESSAGE`(예외 클래스 + 메시지 전문)를 읽는다.
3. **워터마크/캐시 상태로 "부분 반영이 있었는지" 확인**(§0의 트랜잭션 경계 특성을
   신뢰하되, 의심스러우면 직접 대조):

   ```sql
   SELECT * FROM SYNC_WATERMARK;
   SELECT resource_type, COUNT(*) FROM FHIR_RESOURCE_CACHE GROUP BY resource_type;
   ```

   실패한 스텝의 `resource_type`에 대해 이 값들이 실패 직전과 동일하면(§0에서 설명한
   트랜잭션 롤백대로) 부분 반영 없이 깨끗하게 실패한 것 — 별도 정리 없이 §3으로 진행.
4. **근본 원인 해결**: 진단된 원인(레거시 테이블 누락/이름 변경, DB 커넥션 풀 고갈,
   디스크 공간 부족 등 — `docs/incidents/`의 다른 장애 시나리오와 겹칠 수 있음)을
   실제로 고친다. 원인이 해결되기 **전에** 재처리를 시도하면 같은 실패가 반복될
   뿐이다.

## 3. 재처리 절차 (기본 경로 — 워터마크 기반 재실행)

원인 해결 후, **잡을 다시 실행**한다. 이 프로젝트에는 배치를 트리거하는 REST
엔드포인트가 없다(`SyncJobRunner`/`SummaryRefreshJobRunner`는 둘 다
`CommandLineRunner` — 앱 기동 시 프로퍼티가 켜져 있으면 1회 자동 실행되는 구조다).
따라서 재처리는 **앱을 해당 프로퍼티를 켠 채로 재기동**하는 방식으로 수행한다.

### 3.1 로컬(`./gradlew bootRun`)

```powershell
cd app
$env:DB_HOST="localhost"; $env:DB_PORT="3306"; $env:DB_NAME="hospital_ops"
$env:DB_USERNAME="hospital_ops"; $env:DB_PASSWORD="changeme"
$env:ENVELOPE_KEK="<로컬 개발용 32바이트 Base64 키>"
# fhirSyncJob 재처리:
.\gradlew.bat bootRun --no-daemon --args="--sync.job.enabled=true"
# summaryRefreshJob 재처리:
.\gradlew.bat bootRun --no-daemon --args="--summary.refresh.job.enabled=true"
```

기동 로그에서 `status=COMPLETED`(또는 Spring Batch 로그의 `status: [COMPLETED]`)를
확인하면 재처리 성공이다.

### 3.2 Docker Compose 배포

현재 `docker/docker-compose.yml`의 `app` 서비스 `environment`에는 `SYNC_JOB_ENABLED`/
`SUMMARY_REFRESH_JOB_ENABLED`/`ENVELOPE_KEK` 등이 매핑되어 있지 않다(DB_* 값만
전달됨) — 이는 이번 조사 중 발견한 기존 배포 구성의 공백이며, Step 8.2 범위를 벗어나
`docker-compose.yml` 자체는 고치지 않았다(별도 후속 조치로 남겨 둠 — §6.5 참고). 그
전까지 Docker 배포본에서 재처리하려면 재기동 시 환경변수를 명시적으로 주입해야 한다:

```powershell
docker compose -f docker/docker-compose.yml up -d --force-recreate `
  -e SYNC_JOB_ENABLED=true app
# 또는 compose 파일이 -e를 지원하지 않는 버전이면 일회성 오버라이드로:
docker compose -f docker/docker-compose.yml run --rm `
  -e SYNC_JOB_ENABLED=true app
```

재처리가 끝나면 상시 서비스 컨테이너는 `SYNC_JOB_ENABLED` 없이(기본값 false) 원래
상태로 되돌린다 — 이 프로퍼티가 켜진 채로 계속 떠 있으면 **앱이 재기동될 때마다**
배치가 자동으로 다시 실행되므로 상시 활성화하지 않는다.

## 4. 특수 케이스

### 4.1 이미 성공 처리된 리소스 타입을 강제로 다시 처리해야 할 때

`FHIR_RESOURCE_CACHE`의 특정 행이 손상되었거나(예: 매핑 버그로 잘못된 FHIR JSON이
upsert된 뒤 코드는 고쳤지만 데이터는 남아있는 경우) 워터마크 이전 구간을 다시
동기화해야 할 때는, 워터마크를 과거로 되돌린 뒤 재실행한다:

```sql
UPDATE SYNC_WATERMARK
SET last_synced_at = '<재처리를 원하는 시작 시각 직전 값>'
WHERE resource_type = 'Patient';  -- Encounter/Observation/MedicationRequest도 동일
```

이후 §3의 재기동 절차를 그대로 따르면 된다. `FHIR_RESOURCE_CACHE`가
`(resource_type, source_table_pk)` UNIQUE 제약을 키로 한 find-or-create upsert이므로
**같은 행이 다시 처리돼도 새 행이 생기지 않고 기존 행이 덮어써진다**(중복 없음) —
`synced_at`만 최신 처리 시각으로 갱신된다.

### 4.2 summaryRefreshJob

워터마크가 없는 전체 재계산 설계라 특수 케이스가 없다 — §3.1/§3.2의 기본 절차(재기동)만
반복하면 항상 `PATIENT_VISIT_SUMMARY`가 원본 집계와 일치하는 최종 상태로 수렴한다.

## 5. 재처리 후 데이터 정합성 검증

1. **잡 상태**: 로그의 `status=COMPLETED` 또는

   ```sql
   SELECT STATUS FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID DESC LIMIT 1;
   -- 'COMPLETED' 확인
   ```

2. **메트릭**: `/actuator/prometheus`에서 재기동 후 새 프로세스 기준

   ```
   hospitalops_batch_job_runs_total{job="fhirSyncJob",status="success"} >= 1.0
   hospitalops_batch_job_runs_total{job="fhirSyncJob",status="failure"} == 0.0
   ```

   (재기동은 새 JVM 프로세스이므로 카운터가 0부터 다시 시작한다 — 이전 실행의
   `failure` 누적치는 이 값에 안 남는다. Grafana 알림도 이 시점에 자동으로 resolve된다.)

3. **워터마크 전진 확인**:

   ```sql
   SELECT resource_type, last_synced_at FROM SYNC_WATERMARK;
   ```

   실패를 유발했던 리소스 타입의 `last_synced_at`이 재처리 시점의 레거시 테이블
   `MAX(updated_at)`까지 전진했는지 확인한다.

4. **중복 upsert 없음 확인**(멱등성의 핵심 검증):

   ```sql
   SELECT resource_type, source_table_pk, COUNT(*) AS cnt
   FROM FHIR_RESOURCE_CACHE
   GROUP BY resource_type, source_table_pk
   HAVING cnt > 1;
   -- 0 행이어야 한다(uq_fhir_cache_type_pk UNIQUE 제약이 이미 DB 레벨에서 보장하지만,
   -- 애플리케이션 upsert 로직이 실제로 그 제약대로 동작하는지 확인하는 의미가 있다)
   ```

   재처리 전후 `FHIR_RESOURCE_CACHE`의 `resource_type`별 `COUNT(*)`가 (신규로 늘어난
   레거시 행이 없는 한) 재처리 전과 동일해야 하고, 재처리 대상이었던 행의 `synced_at`만
   갱신되어 있어야 한다.

## 6. 실측 재현 로그 (2026-07-19 23:15 ~ 2026-07-20 00:20 KST)

Step 8.1(`docs/incidents/2026-07-19-batch-job-forced-failure.md`)에서 이미 검증한
"레거시 `PATIENT` 테이블 RENAME → `fhirSyncJob` 실패" 시나리오를 다시 재현하되, 이번엔
**위 §1~§5 절차 그대로만**을 따라가며 재처리했다. 환경은 로컬 native MySQL 8.4 +
`./gradlew.bat bootRun`(Step 8.1과 동일).

### 6.1 사전 상태 확인 및 강제 실패 유발

```sql
mysql> SELECT * FROM SYNC_WATERMARK;
resource_type       last_synced_at        updated_at
Encounter            2026-07-18 19:52:22   2026-07-19 04:52:23
MedicationRequest    2026-07-18 09:09:09   2026-07-18 20:10:35
Observation          2026-07-18 09:09:08   2026-07-18 20:10:35
Patient              2026-07-19 14:34:33   2026-07-19 23:49:29

mysql> SELECT resource_type, COUNT(*) FROM FHIR_RESOURCE_CACHE GROUP BY resource_type;
Encounter 511 / MedicationRequest 145 / Observation 3759 / Patient 13
```

```sql
UPDATE PATIENT SET updated_at = NOW() WHERE patient_id = 1;   -- watermark를 넘어서는 변경 생성
RENAME TABLE PATIENT TO PATIENT_BAK;                           -- 원인 유발(§2와 동일한 시나리오)
```

앱을 `sync.job.enabled=true`로 기동:

```powershell
$env:ENVELOPE_KEK="<세션용 32바이트 Base64 키>"
.\gradlew.bat bootRun --no-daemon --args="--sync.job.enabled=true"
```

### 6.2 감지 (§1) — 실측

애플리케이션 로그(원문):

```
2026-07-20T00:16:06.488+09:00 ERROR ... o.s.batch.core.step.AbstractStep : Encountered an error executing step syncPatientStep in job fhirSyncJob
org.springframework.jdbc.BadSqlGrammarException: PreparedStatementCallback; bad SQL grammar [SELECT patient_id, ... FROM PATIENT WHERE updated_at > ? ORDER BY updated_at]
Caused by: java.sql.SQLSyntaxErrorException: Table 'hospital_ops.patient' doesn't exist
2026-07-20T00:16:06.520+09:00 INFO ... TaskExecutorJobLauncher : Job: [SimpleJob: [name=fhirSyncJob]] completed with the following parameters: [...] and the following status: [FAILED] in 113ms
2026-07-20T00:16:06.520+09:00 INFO ... com.hospitalops.batch.SyncJobRunner : fhirSyncJob 실행 완료: status=FAILED
```

`/actuator/prometheus`(원문):

```
hospitalops_batch_job_runs_total{job="fhirSyncJob",status="failure"} 1.0
hospitalops_batch_job_runs_total{job="fhirSyncJob",status="success"} 0.0
hospitalops_batch_job_runs_total{job="summaryRefreshJob",status="failure"} 0.0
hospitalops_batch_job_runs_total{job="summaryRefreshJob",status="success"} 0.0
```

Grafana 알림 발동/해소 자체는 Step 8.1에서 이 정확히 같은 시나리오로 이미
`/api/alertmanager/grafana/api/v2/alerts` 응답까지 실측 확인됐다(위 문서 §4~§5) —
이번 재현에서는 시간 절약을 위해 Prometheus/Grafana 컨테이너는 별도로 띄우지 않고
`/actuator/prometheus` 원본 값(위)으로 §1.2 경로만 다시 실측했다.

### 6.3 원인 진단 (§2) — 실측

```sql
mysql> SELECT je.JOB_EXECUTION_ID, ji.JOB_NAME, je.STATUS, je.START_TIME, je.END_TIME
       FROM BATCH_JOB_EXECUTION je JOIN BATCH_JOB_INSTANCE ji
       ON je.JOB_INSTANCE_ID = ji.JOB_INSTANCE_ID ORDER BY je.JOB_EXECUTION_ID DESC LIMIT 3;
JOB_EXECUTION_ID  JOB_NAME     STATUS     START_TIME                  END_TIME
66                fhirSyncJob  FAILED     2026-07-19 15:16:06.406880  2026-07-19 15:16:06.520356
65                fhirSyncJob  COMPLETED  2026-07-19 14:49:28.738290  2026-07-19 14:49:30.000818
64                fhirSyncJob  FAILED     2026-07-19 14:44:59.866645  2026-07-19 14:44:59.964314

mysql> SELECT STEP_EXECUTION_ID, STEP_NAME, STATUS, EXIT_CODE, LEFT(EXIT_MESSAGE,120)
       FROM BATCH_STEP_EXECUTION ORDER BY STEP_EXECUTION_ID DESC LIMIT 3;
249  syncPatientStep            FAILED     FAILED     org.springframework.jdbc.BadSqlGrammarException: ...
248  syncMedicationRequestStep  COMPLETED  COMPLETED
247  syncObservationStep        COMPLETED  COMPLETED
```

(참고: START_TIME은 UTC로 저장되어 로그의 KST 시각보다 9시간 이른 값으로 보인다 —
`15:16:06` UTC = `00:16:06` KST, 로그와 일치.)

부분 반영 여부 확인(§2.3) — 실패 **직후** 조회:

```sql
mysql> SELECT * FROM SYNC_WATERMARK WHERE resource_type='Patient';
Patient  2026-07-19 14:34:33  2026-07-19 23:49:29   -- 실패 전과 완전히 동일(전진 없음)

mysql> SELECT resource_type, COUNT(*) FROM FHIR_RESOURCE_CACHE GROUP BY resource_type;
Encounter 511 / MedicationRequest 145 / Observation 3759 / Patient 13   -- 실패 전과 완전히 동일
```

§0에서 설명한 "실패한 스텝은 트랜잭션 롤백으로 부분 반영을 남기지 않는다"가 실측으로
확인됐다.

### 6.4 재처리 (§3) — 실측

```sql
RENAME TABLE PATIENT_BAK TO PATIENT;   -- 근본 원인 해결
```

앱 재기동(§3.1과 동일 커맨드, `sync.job.enabled=true`):

```
2026-07-20T00:20:28.505+09:00 INFO ... TaskExecutorJobLauncher : Job: [SimpleJob: [name=fhirSyncJob]] launched ...
2026-07-20T00:20:28.550+09:00 INFO ... Executing step: [syncPatientStep]
2026-07-20T00:20:29.644+09:00 INFO ... Step: [syncPatientStep] executed in 1s94ms
2026-07-20T00:20:29.674+09:00 INFO ... Executing step: [syncEncounterStep]
2026-07-20T00:20:29.691+09:00 INFO ... Step: [syncEncounterStep] executed in 17ms
2026-07-20T00:20:29.732+09:00 INFO ... Executing step: [syncObservationStep]
2026-07-20T00:20:29.765+09:00 INFO ... Step: [syncObservationStep] executed in 32ms
2026-07-20T00:20:29.786+09:00 INFO ... Executing step: [syncMedicationRequestStep]
2026-07-20T00:20:29.803+09:00 INFO ... Step: [syncMedicationRequestStep] executed in 16ms
2026-07-20T00:20:29.828+09:00 INFO ... TaskExecutorJobLauncher : Job: [SimpleJob: [name=fhirSyncJob]] completed with the following parameters: [...] and the following status: [COMPLETED] in 1s294ms
2026-07-20T00:20:29.828+09:00 INFO ... com.hospitalops.batch.SyncJobRunner : fhirSyncJob 실행 완료: status=COMPLETED
```

### 6.5 재처리 후 정합성 검증 (§5) — 실측

```sql
mysql> SELECT JOB_EXECUTION_ID, STATUS FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID DESC LIMIT 1;
67  COMPLETED

mysql> SELECT * FROM SYNC_WATERMARK WHERE resource_type='Patient';
Patient  2026-07-19 15:15:32  2026-07-20 00:20:29   -- 워터마크가 patient_id=1의 새 updated_at까지 전진

mysql> SELECT resource_type, COUNT(*) FROM FHIR_RESOURCE_CACHE GROUP BY resource_type;
Encounter 511 / MedicationRequest 145 / Observation 3759 / Patient 13   -- 행 수 불변(중복 없음)

mysql> SELECT id, source_table_pk, synced_at FROM FHIR_RESOURCE_CACHE
       WHERE resource_type='Patient' AND source_table_pk=1;
id=1  source_table_pk=1  synced_at=2026-07-19 15:20:30   -- 같은 id가 유지된 채 synced_at만 갱신(upsert 확인)
```

`/actuator/prometheus`(재기동 후 새 프로세스 — 원문):

```
hospitalops_batch_job_runs_total{job="fhirSyncJob",status="failure"} 0.0
hospitalops_batch_job_runs_total{job="fhirSyncJob",status="success"} 1.0
hospitalops_batch_job_runs_total{job="summaryRefreshJob",status="failure"} 0.0
hospitalops_batch_job_runs_total{job="summaryRefreshJob",status="success"} 0.0
```

§4.2에서 예고한 "중복 upsert 없음" 쿼리도 확인:

```sql
mysql> SELECT resource_type, source_table_pk, COUNT(*) AS cnt FROM FHIR_RESOURCE_CACHE
       GROUP BY resource_type, source_table_pk HAVING cnt > 1;
Empty set   -- 중복 없음
```

**결론**: §1~§5 절차만으로 실제 강제 실패(`fhirSyncJob`/`syncPatientStep`, JOB_EXECUTION_ID
66)를 감지·진단하고, 재처리(JOB_EXECUTION_ID 67, `COMPLETED`)까지 재현했다. 재처리
전후로 `PATIENT`/`FHIR_RESOURCE_CACHE`/`SYNC_WATERMARK` 스키마·데이터에 이상 없음을
확인했고, `PATIENT_BAK` 임시 테이블도 정리했다(§6.4에서 `RENAME TABLE PATIENT_BAK TO
PATIENT` 실행).

### 6.6 세션 중 발견한 기존 구성 공백 (참고, 이번 Step 범위 밖)

`docker/docker-compose.yml`의 `app` 서비스 `environment`에는 `SYNC_JOB_ENABLED`/
`SUMMARY_REFRESH_JOB_ENABLED`/`ENVELOPE_KEK`/`PSEUDONYMIZER_HMAC_KEY`/
`DATE_SHIFTER_HMAC_KEK` 등이 매핑돼 있지 않다(`DB_HOST`/`DB_PORT`/`DB_NAME`/
`DB_USERNAME`/`DB_PASSWORD`만 있음). `ENVELOPE_KEK`는 기본값이 없으므로(§`app/src/main/
resources/application.yml`) 이 compose 파일 그대로는 `app` 컨테이너가 `EnvelopeCrypto`
빈 생성에서 즉시 fail-fast로 기동 실패한다. Step 8.2는 runbook 작성이 범위이므로
`docker-compose.yml` 자체는 고치지 않았고, §3.2에 이를 전제로 한 임시 우회(재기동 시
환경변수 명시 오버라이드)를 기록해 두었다 — 후속 Step에서 compose 파일의
`environment` 블록 정비가 필요하다.

## 7. 체크리스트 요약

- [ ] Grafana 알림(`batch-job-failure-alert`) 또는 `/actuator/prometheus`의
      `hospitalops_batch_job_runs_total{status="failure"} > 0`으로 실패를 감지했는가?
- [ ] `BATCH_STEP_EXECUTION.EXIT_MESSAGE`(또는 앱 로그)로 실패한 스텝과 근본 원인을
      특정했는가?
- [ ] 근본 원인을 **먼저** 해결했는가(원인 해결 전 재처리 금지)?
- [ ] `SYNC_WATERMARK`/`FHIR_RESOURCE_CACHE`가 실패 시점과 동일한지 확인해 부분 반영이
      없음을 검증했는가(§0의 트랜잭션 롤백 특성 신뢰 + 실측 대조)?
- [ ] `sync.job.enabled=true`(또는 `summary.refresh.job.enabled=true`)로 앱을
      재기동해 재처리했는가?
- [ ] 재처리 후 잡 상태가 `COMPLETED`인가?
- [ ] 재처리 후 `SYNC_WATERMARK`가 전진했는가(fhirSyncJob)?
- [ ] `(resource_type, source_table_pk)` 기준 중복 행이 0건인가?
- [ ] Docker 배포본이라면, 재처리용으로 켰던 `SYNC_JOB_ENABLED`/
      `SUMMARY_REFRESH_JOB_ENABLED`를 다시 껐는가(재기동마다 배치가 자동 실행되는 것을
      방지)?
