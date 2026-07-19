# 장애 재현 로그 2: 배치 job(fhirSyncJob) 강제 실패

> Phase 8 Step 8.1. `fhirSyncJob`(Phase 2.1)이 의존하는 레거시 테이블을 일시적으로
> 이름 변경(RENAME)해 실제 SQL 오류로 job을 실패시켰다. 재현 도중 Prometheus
> 카운터의 실제 관측 공백(콜드스타트 문제)을 발견해 코드로 수정했다(별도 커밋,
> gemini 검증 포함) - 이 문서는 그 경위도 함께 기록한다.

## 0. 재현 대상 선정 이유

`fhirSyncJob`의 `PatientSyncTasklet`은 `PATIENT` 테이블을 직접 JDBC로 읽는다.
`PATIENT`는 JPA `@Entity`가 아니므로(순수 JdbcTemplate 대상 - `FHIR_RESOURCE_CACHE`,
`APP_USER` 등만 JPA 엔티티), 이 테이블을 RENAME해도 Hibernate `ddl-auto: validate`
스키마 검증(앱 기동 자체를 막는 체크)에 걸리지 않는다. 처음에는 `FHIR_RESOURCE_CACHE`
(JPA 엔티티)를 RENAME 시도했으나 `SchemaManagementException: missing table
[fhir_resource_cache]`로 앱 **기동 자체**가 실패해버려(배치 job이 아니라 앱 전체가
죽는 시나리오가 되어버림) 재현 대상을 `PATIENT`로 바꿨다.

## 1. 재현 절차

### 1.1 SYNC_WATERMARK가 이미 캐치업된 상태 확인

```
mysql> SELECT * FROM SYNC_WATERMARK;
resource_type       last_synced_at        updated_at
Patient              2026-07-18 19:52:22   2026-07-19 04:52:23
```

캐치업 상태라 `PatientSyncTasklet`의 증분 쿼리(`WHERE updated_at > watermark`)가
빈 결과셋을 반환하면 upsert 경로 자체를 안 타므로, 실패를 확실히 유발하려면 먼저
watermark를 넘어서는 새 변경을 만들어야 한다:

```sql
UPDATE PATIENT SET updated_at = NOW() WHERE patient_id = 1;
-- PATIENT.updated_at은 `ON UPDATE CURRENT_TIMESTAMP`라 값 변경 없는 UPDATE로도 자동 갱신됨
```

### 1.2 PATIENT 테이블을 일시적으로 이름 변경

```sql
RENAME TABLE PATIENT TO PATIENT_BAK;
```

### 1.3 sync.job.enabled=true로 앱 기동(배치가 기동 시 1회 자동 실행)

```powershell
.\gradlew.bat bootRun --no-daemon --args="--sync.job.enabled=true"
```

## 2. 증상 (실측)

### 2.1 애플리케이션 로그(원문 발췌)

```
Caused by: java.sql.SQLSyntaxErrorException: Table 'hospital_ops.patient' doesn't exist
	at com.mysql.cj.jdbc.exceptions.SQLError.createSQLException(SQLError.java:112)
	...
	at org.springframework.jdbc.core.JdbcTemplate.execute(JdbcTemplate.java:658)

2026-07-19T23:36:56.xxx INFO ... o.s.batch.core.step.AbstractStep : Step: [syncPatientStep] executed in 63ms
2026-07-19T23:36:56.xxx INFO ... o.s.b.c.l.s.TaskExecutorJobLauncher : Job: [SimpleJob: [name=fhirSyncJob]]
  completed with the following parameters: [{'runAt':'...'}] and the following status: [FAILED] in 95ms
2026-07-19T23:36:56.xxx INFO ... com.hospitalops.batch.SyncJobRunner : fhirSyncJob 실행 완료: status=FAILED
```

**중요 관찰**: `SyncJobRunner.runOnce()`는 이 SQL 예외를 다시 던지지 않는다(Spring
Batch가 tasklet 내부 예외를 잡아 `JobExecution`을 `FAILED` 상태로 정상 반환하는
표준 동작 - launch 자체가 실패하는 경우와 다름). 그래서 애플리케이션은 **죽지 않고
계속 서비스한다** - 이는 배치 실패가 전체 서비스 장애로 번지지 않는 바람직한 격리이다
(뒤이어 Tomcat의 `DispatcherServlet`이 정상 초기화되는 로그로 확인됨).

### 2.2 메트릭(원문)

```
hospitalops_batch_job_runs_total{job="fhirSyncJob",status="failure"} 1.0
```

## 3. 발견한 관측성 버그와 수정 (부수 산출물)

최초 재현 시 위 카운터가 `1.0`으로 정확히 기록됐음에도, Grafana 알림 규칙
(`increase(hospitalops_batch_job_runs_total{status="failure"}[5m]) > 0`)이
**발동하지 않았다.** 원인 조사 결과:

- Micrometer `Counter`는 최초 `increment()` 전까지 해당 태그 조합의 Prometheus
  시계열이 아예 존재하지 않는다("lazy 등록").
- 이 배치는 앱 기동 직후 262ms 안에 실패했다(`Tomcat started` 23:44:59.717 ->
  `Job ... FAILED` 23:44:59.979). Prometheus 스크레이프 간격(15s) 안에 "0" 값을
  포착할 기회가 사실상 없어, `increase()`가 비교할 이전 샘플이 없는 채로 "처음
  나타난 값 1"을 관측 - 증가량 0으로 계산됐다(Prometheus increase()의 구조적
  한계 - 시계열이 처음 등장하는 순간의 값은 "증가"로 잡히지 않는다).

**수정**(커밋 `6103b8c`, gemini CLI(`-m flash`) 검증 `VERDICT: PASS`):
`SyncJobRunner`/`SummaryRefreshJobRunner` 생성자에서 `status=success`/`failure`
두 시계열을 카운트 0으로 미리 `register()`한다(increment 없이). 파일:
`app/src/main/java/com/hospitalops/batch/SyncJobRunner.java`,
`app/src/main/java/com/hospitalops/batch/SummaryRefreshJobRunner.java`.

수정 후 재현하면 4개 시계열이 모두 즉시 보인다:

```
hospitalops_batch_job_runs_total{job="fhirSyncJob",status="failure"} 1.0
hospitalops_batch_job_runs_total{job="fhirSyncJob",status="success"} 0.0
hospitalops_batch_job_runs_total{job="summaryRefreshJob",status="failure"} 0.0
hospitalops_batch_job_runs_total{job="summaryRefreshJob",status="success"} 0.0
```

그러나 **여전히** "부팅 직후 262ms 안의 실패"라는 타이밍 자체는 15초 스크레이프
간격보다 훨씬 짧아, "0 상태가 실제로 스크레이프될 기회"가 보장되지 않는다(0으로
등록은 되지만, Prometheus가 그 0을 한 번이라도 관측하기 전에 1로 바뀌어버릴 수
있음). 이 구조적 문제 때문에, Grafana 알림 규칙 자체를 `increase()`(변화율) 대신
**순간값 임계치**(`sum(hospitalops_batch_job_runs_total{status="failure"}) > 0`)로
바꿨다 - 상세 근거는 `docker/grafana/provisioning/alerting/incident-scenarios-alerting.yml`
규칙 2번 주석 참고. 이 방식은 "누적 실패가 1건이라도 있으면 재시작(=카운터 리셋)
전까지 계속 미해결로 취급"하는 배치 실패 알림의 운영 의미에도 더 부합한다.

## 4. Grafana 알림 연동 (수정 후 재현, 실제 firing 확인)

```json
[
  {
    "annotations": {
      "__values__": "{\"A\":1,\"B\":1,\"C\":1}",
      "summary": "배치 job(fhirSyncJob/summaryRefreshJob) 실행이 실패했습니다."
    },
    "startsAt": "2026-07-19T14:48:20Z",
    "status": { "state": "active" },
    "labels": {
      "alertname": "Batch job execution failed",
      "severity": "warning",
      "category": "batch"
    }
  }
]
```

(`/api/alertmanager/grafana/api/v2/alerts` 실 응답 - startsAt UTC 14:48:20 =
KST 23:48:20, RENAME 재현 직후.)

## 5. 대응 조치 및 복구

1. **원인 조치**: `RENAME TABLE PATIENT_BAK TO PATIENT;`로 테이블명 복원.
2. **재처리**: `sync.job.enabled=true`로 앱 재기동해 job을 다시 실행.

```
2026-07-19T23:49:29.945 INFO ... syncMedicationRequestStep executed in 34ms
2026-07-19T23:49:30.010 INFO ... Job: [SimpleJob: [name=fhirSyncJob]] completed ...
  and the following status: [COMPLETED] in 1s262ms
2026-07-19T23:49:30.012 INFO ... fhirSyncJob 실행 완료: status=COMPLETED
```

3. **복구 시각**: **2026-07-19 23:49:30 (KST)** - job이 `COMPLETED`로 재실행된
   시각. 메트릭도 `status="success"` 1.0으로 갱신되고 `status="failure"`는 새
   프로세스라 0으로 리셋됨을 확인.
4. **알림 해소 확인**: 재기동 후 20초 뒤 `/api/alertmanager/grafana/api/v2/alerts`
   재조회 결과 활성 알림 없음("No active alerts") - 알림이 정상적으로 resolve됨.

## 6. 후속 조치 제안

- 이 문서 §3에서 발견한 "부팅 직후 즉시 실패는 스크레이프 타이밍에 따라
  increase() 알림이 놓칠 수 있다"는 구조적 한계는 다른 counter 기반 알림
  규칙(예: break-glass)에도 잠재적으로 동일하게 적용될 수 있다 - break-glass는
  발생 빈도가 낮고 기동 직후 262ms 안에 발생할 개연성이 사실상 없어 실무
  리스크는 낮지만, 원칙적으로 인지해 둘 가치가 있다.
- Step 8.2(배치 실패 → 재처리 runbook)에서 이 재현 절차를 표준 재처리 문서로
  발전시킬 수 있다(§5의 "RENAME 복원 + 재기동" 절차는 이번 케이스에 한정된
  임시 재현 수단이며, 실제 운영 재처리 runbook은 워터마크 기반 재실행 절차를
  중심으로 별도로 작성해야 한다).
