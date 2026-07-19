# Runbook: 백업·복구 (mysqldump 전체 백업 → 손상 → 복구)

> 대상: 로컬 native MySQL 8.4(`mysqld`, `hospital_ops` 스키마, 25개 테이블 — 애플리케이션
> 테이블 19종 + Spring Batch 메타데이터 6종). 이 문서는 표준 MySQL 도구(`mysqldump`/
> `mysql`)를 이용한 전체 백업·복구 절차와, **2026-07-20에 실제로 수행한 백업→의도적
> 손상→복구 리허설의 실측 로그**(가공 없음)를 함께 기록한다. §5는 그 실측 결과이고,
> §1~§4는 그로부터 뽑아낸 재사용 가능한 절차다.

## 0. 배경 — 이 프로젝트에서 백업·복구가 의미하는 범위

이 프로젝트는 아직 정기 백업 자동화(cron/스케줄러)나 원격 백업 저장소를 갖추고 있지
않다(범위 밖 — Phase 8은 "운영 증빙"이 목적이며 백업 자동화 자체는 별도 과제다). 이
문서가 다루는 것은:

1. **논리 백업(logical backup)**: `mysqldump`로 스키마 + 데이터 전체를 SQL 텍스트로
   내보낸다. 이 프로젝트 규모(총 행 수 약 1만 건 미만, 덤프 파일 약 3.7MB)에서는
   물리 백업(파일시스템 스냅샷, `mysqlbackup` 등)보다 논리 백업이 절차가 단순하고
   버전 간 이식성이 높아 적합하다.
2. **전체 스키마 단위 복구**: 테이블 단위가 아니라 `hospital_ops` 데이터베이스 전체가
   사라지는 최악의 시나리오(DROP DATABASE급 사고)를 기준으로 복구 절차를 검증한다 —
   이보다 좁은 범위(단일 테이블 손상)는 이 절차의 부분집합이므로 별도로 다루지 않는다.
3. **복구 소요시간 실측**: "복구에 얼마나 걸리는가"를 추정이 아니라 실제 타임스탬프
   기준으로 산출해, RTO(Recovery Time Objective) 산정의 근거 데이터로 남긴다.

## 1. 사전 조건

| 항목 | 값 |
|---|---|
| MySQL 서버 | 로컬 native MySQL 8.4.9 (`C:\Program Files\MySQL\MySQL Server 8.4\`), 서비스로 상시 기동 |
| 클라이언트 도구 | 같은 설치 경로의 `mysqldump.exe`/`mysql.exe` (PATH에 없으면 세션에서 `$env:Path`에 추가) |
| 접속 계정 | `hospital_ops`(애플리케이션 계정). `GRANT ALL PRIVILEGES ON hospital_ops.* TO hospital_ops@localhost` — `PROCESS`(전역) 권한은 없음(§5.1 경고 참고) |
| 대상 스키마 | `hospital_ops` (테이블 25개: 애플리케이션 19 + `batch_*` 메타데이터 6) |

## 2. 백업 절차

```powershell
$env:Path += ";C:\Program Files\MySQL\MySQL Server 8.4\bin"
mysqldump -u hospital_ops -p<password> -h localhost -P 3306 `
  --single-transaction --routines --triggers --events --databases hospital_ops `
  > backup.sql 2> dump-stderr.log
```

플래그 의미:
- `--single-transaction`: InnoDB 테이블을 잠금 없이(스냅샷 기준) 일관된 상태로
  덤프한다(운영 중 쓰기가 있어도 안전).
- `--routines --triggers --events`: 저장 프로시저/트리거/이벤트도 함께 백업(현재
  스키마엔 없지만 향후 추가 대비).
- `--databases hospital_ops`: 출력 파일에 `CREATE DATABASE IF NOT EXISTS`/`USE`
  문을 포함시켜, 스키마 자체가 없는 상태에서도 이 파일 하나만으로 복구 가능하게 한다.

**주의(실측으로 확인된 것)**: `hospital_ops` 계정은 전역 `PROCESS` 권한이 없어
`mysqldump`가 테이블스페이스 메타데이터를 덤프하려다 다음 경고를 낸다:

```
mysqldump: Error: 'Access denied; you need (at least one of) the PROCESS privilege(s) for this operation' when trying to dump tablespaces
```

이 경고는 **치명적이지 않다** — 파일 끝에 `-- Dump completed on ...` 마커가 정상
출력되고, 전 테이블의 `CREATE TABLE`/`INSERT INTO`가 빠짐없이 포함됨을 §5에서 실측
확인했다(테이블스페이스 메타데이터는 일반 스키마 복구에 필요하지 않음). 다만 백업
스크립트를 자동화할 때 이 문자열이 stderr에 항상 나타난다는 것을 실패 판정 조건에서
제외해야 한다(단순 종료 코드/stderr 존재 여부만으로 실패 판정하면 오탐).

## 3. 손상 시나리오 (예시 — 실제 사고 유형)

이 절차는 아래와 같은 실제 운영 사고를 가정한다:

> **시나리오**: 온콜 운영자가 스테이징 환경 정리 스크립트를 실행하려다, 터미널의 접속
> 프로파일이 실제로는 로컬 프로덕션 DB(`hospital_ops`)를 가리키고 있음을 인지하지
> 못한 채 `DROP DATABASE hospital_ops;`를 실행해버렸다. 스키마와 데이터가 모두
> 즉시 사라졌다.

이 시나리오를 선택한 이유: 단일 테이블 삭제(`DELETE FROM PATIENT` 등)보다 훨씬 넓은
피해 범위(스키마 자체 소실)를 다루므로, 복구 절차가 "전체 재생성 + 전 테이블 복원"을
실제로 커버하는지 검증할 수 있다 — 더 좁은 손상(단일 테이블/행 삭제)은 이 절차의
부분집합이다.

## 4. 복구 절차

```powershell
$env:Path += ";C:\Program Files\MySQL\MySQL Server 8.4\bin"
Get-Content backup.sql -Raw | mysql -u hospital_ops -p<password> -h localhost -P 3306
```

백업 파일이 `CREATE DATABASE IF NOT EXISTS hospital_ops` + `USE hospital_ops;`로
시작하므로, 스키마가 통째로 사라진 상태에서도 대상 스키마를 지정하지 않고 그대로
파이프하면 파일 안의 문이 스키마를 재생성하고 그 안에 데이터를 채운다.

## 5. 실측 리허설 로그 (2026-07-20 00:40 ~ 00:44 KST)

환경: 로컬 native MySQL 8.4.9, `hospital_ops` 계정, PowerShell 7. 이하 모든 값은
그 시각에 실제로 실행한 커맨드의 원본 출력에서 그대로 옮겼다(추정치 없음).

### 5.0 리허설 시작 전 기준선(baseline) 확정

```sql
mysql> SHOW TABLES;
-- 25 rows: access_policy_rules, app_role, app_user, app_user_role, audit_log,
-- batch_job_execution, batch_job_execution_context, batch_job_execution_params,
-- batch_job_execution_seq, batch_job_instance, batch_job_seq, batch_step_execution,
-- batch_step_execution_context, batch_step_execution_seq, bulk_decryption_request,
-- code_set, fhir_resource_cache, flyway_schema_history, lab_result, patient,
-- patient_display_id, patient_visit_summary, prescription, sync_watermark, visit

mysql> SELECT COUNT(*) FROM PATIENT;                 -- 12
mysql> SELECT COUNT(*) FROM AUDIT_LOG;                -- 21
mysql> SELECT MAX(audit_id) FROM AUDIT_LOG;           -- 276
mysql> SELECT version, description, success FROM flyway_schema_history
        ORDER BY installed_rank DESC LIMIT 5;
-- 15  audit log target pk index          1
-- 14  actuator access policy             1
-- 13  patient visit summary              1
-- 12  stats access policy                1
-- 11  bulk decryption approval           1
```

전 테이블 행 수(기준선, `COUNT(*)`):

| 테이블 | 행 수 | 테이블 | 행 수 |
|---|---:|---|---:|
| access_policy_rules | 13 | fhir_resource_cache | 4428 |
| app_role | 5 | flyway_schema_history | 15 |
| app_user | 5 | lab_result | 3759 |
| app_user_role | 5 | patient | 12 |
| audit_log | 21 | patient_display_id | 12 |
| batch_job_execution | 67 | patient_visit_summary | 12 |
| batch_job_execution_context | 67 | prescription | 145 |
| batch_job_execution_params | 67 | sync_watermark | 4 |
| batch_job_execution_seq | 1 | visit | 509 |
| batch_job_instance | 67 | bulk_decryption_request | 0 |
| batch_job_seq | 1 | | |
| batch_step_execution | 253 | | |
| batch_step_execution_context | 253 | | |
| batch_step_execution_seq | 1 | | |
| code_set | 56 | | |

이 표가 §5.5 복구 후 검증의 대조 기준이다.

### 5.1 백업 실행 — 실측

추가 안전 스냅샷(리허설용 백업과 별개, 만약을 위한 보험) 먼저 확보:

```
=== SAFETY BACKUP START: 2026-07-20 00:40:30.573 ===
=== SAFETY BACKUP END:   2026-07-20 00:40:31.085 ===
```
(`safety-snapshot-before-rehearsal.sql`, 3,872,233 bytes)

이어서 리허설 본 백업(이후 복구에 실제로 사용한 파일):

```powershell
$t0 = Get-Date
mysqldump -u hospital_ops -pchangeme -h localhost -P 3306 `
  --single-transaction --routines --triggers --events --databases hospital_ops `
  > rehearsal-backup.sql 2> rehearsal-dump-stderr.log
$t1 = Get-Date
```

```
=== REHEARSAL BACKUP START: 2026-07-20 00:41:01.077 ===
=== REHEARSAL BACKUP END:   2026-07-20 00:41:01.538 ===
ELAPSED_SECONDS: 0.4609772
```

stderr 원문(§2에서 설명한 예상된 경고, 치명적 아님):
```
mysqldump: [Warning] Using a password on the command line interface can be insecure.
mysqldump: Error: 'Access denied; you need (at least one of) the PROCESS privilege(s) for this operation' when trying to dump tablespaces
```

산출물:
- 파일: `rehearsal-backup.sql`
- **파일 크기: 3,872,233 bytes (약 3.69 MiB)**
- **백업 소요시간: 0.461초**
- 파일 끝 확인: `-- Dump completed on 2026-07-20  0:41:01` (정상 종료 마커)
- 무결성 확인: `CREATE DATABASE /*!32312 IF NOT EXISTS*/ \`hospital_ops\` ...` 및
  25개 테이블 전부에 대한 `-- Table structure for table \`<name>\`` 섹션이 파일에
  존재함을 `Select-String`으로 확인(누락 없음).

### 5.2 의도적 손상 실행 — 실측

```
=== INCIDENT START: 2026-07-20 00:41:46.715 ===
mysql -u hospital_ops -pchangeme -h localhost -P 3306 -e "DROP DATABASE hospital_ops;"
=== INCIDENT END:   2026-07-20 00:41:47.173 ===
```

손상 확인:
```
mysql> SHOW DATABASES;
Database
information_schema
performance_schema
-- hospital_ops 스키마 자체가 목록에서 사라짐

mysql> SELECT COUNT(*) FROM PATIENT;
ERROR 1049 (42000): Unknown database 'hospital_ops'
```

§3의 사고 시나리오(`DROP DATABASE hospital_ops`)를 실제로 실행해 스키마·데이터
전체가 소실된 상태를 만들었다.

### 5.3 복구 실행 — 실측

```powershell
$t0 = Get-Date
Get-Content rehearsal-backup.sql -Raw | mysql -u hospital_ops -pchangeme -h localhost -P 3306
$t1 = Get-Date
```

```
=== RESTORE START: 2026-07-20 00:42:04.447 ===
=== RESTORE END:   2026-07-20 00:42:06.439 ===
ELAPSED_SECONDS: 1.9928649
```

stderr: 비밀번호 CLI 경고 1줄 외 에러 없음(`mysql: [Warning] Using a password on
the command line interface can be insecure.`).

**복구 소요시간(실측): 1.993초** — 손상 인지 후 "백업 파일로부터 스키마 재생성 +
전 테이블 데이터 복원"까지 걸린 순수 실행 시간. (참고: 사고 인지·의사결정 시간은
포함하지 않은 순수 기술적 복구 실행 시간이다. §5.2 손상 시각 `00:41:47`부터 §5.3
복구 완료 `00:42:06.439`까지, 즉 "장애 발생 ~ 데이터 원복 완료"까지의 총 경과는
약 19.7초였다 — 이 리허설에서는 원인 진단 없이 바로 복구 커맨드를 실행했으므로
이 총 경과시간은 실제 사고 대응(감지·의사결정 포함)보다 짧게 나온다는 점을 감안해야
한다.)

### 5.4 복구 후 검증 — 실측

전 테이블 행 수(복구 직후 재조회):

```
access_policy_rules : 13          fhir_resource_cache : 4428
app_role : 5                      flyway_schema_history : 15
app_user : 5                      lab_result : 3759
app_user_role : 5                 patient : 12
audit_log : 21                    patient_display_id : 12
batch_job_execution : 67          patient_visit_summary : 12
batch_job_execution_context : 67  prescription : 145
batch_job_execution_params : 67   sync_watermark : 4
batch_job_execution_seq : 1       visit : 509
batch_job_instance : 67
batch_job_seq : 1
batch_step_execution : 253
batch_step_execution_context : 253
batch_step_execution_seq : 1
bulk_decryption_request : 0
code_set : 56
```

25개 테이블 전부 §5.0 기준선과 **완전히 일치**(행 수 델타 0).

핵심 값 재확인:
```sql
mysql> SELECT COUNT(*) FROM PATIENT;               -- 12  (기준선과 일치)
mysql> SELECT COUNT(*) FROM AUDIT_LOG;              -- 21  (기준선과 일치)
mysql> SELECT MAX(audit_id) FROM AUDIT_LOG;         -- 276 (기준선과 일치)
mysql> SELECT version, description, success FROM flyway_schema_history
        ORDER BY installed_rank DESC LIMIT 5;
-- 15  audit log target pk index          1   (기준선과 일치)
-- 14  actuator access policy             1
-- 13  patient visit summary              1
-- 12  stats access policy                1
-- 11  bulk decryption approval           1

mysql> SHOW TABLES;   -- 25개 테이블 전부 재생성됨(기준선과 동일한 테이블 목록)
```

**애플리케이션 레벨 검증**(복구된 DB로 실제 앱을 기동해 조회까지 확인):

```powershell
$env:DB_HOST="localhost"; $env:DB_PORT="3306"; $env:DB_NAME="hospital_ops"
$env:DB_USERNAME="hospital_ops"; $env:DB_PASSWORD="changeme"
$env:ENVELOPE_KEK="<세션용 32바이트 Base64 키>"
.\gradlew.bat bootRun --no-daemon
```

기동 로그(원문 발췌):
```
o.f.core.internal.command.DbValidate  : Successfully validated 15 migrations (execution time 00:00.081s)
o.f.core.internal.command.DbMigrate   : Current version of schema `hospital_ops`: 15
o.f.core.internal.command.DbMigrate   : Schema `hospital_ops` is up to date. No migration necessary.
o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8080 (http) with context path '/'
c.hospitalops.HospitalOpsLabApplication : Started HospitalOpsLabApplication in 9.339 seconds
```

Flyway가 복구된 스키마를 **마이그레이션 불필요(이미 V15까지 적용됨)** 상태로
인식했다 — `flyway_schema_history`가 복구 후에도 정확히 원래 상태임을 앱 기동
경로로도 교차 확인.

```
GET /actuator/health → {"status":"UP"}
```

복구된 데이터가 실제로 조회 가능함을 라이브 쿼리로 확인:
```sql
mysql> SELECT audit_id, action FROM audit_log ORDER BY audit_id DESC LIMIT 3;
audit_id  action
276       BREAK_GLASS_ACCESS_GRANT
275       BREAK_GLASS_ACCESS_GRANT
244       BREAK_GLASS_ACCESS_GRANT
```
(`audit_id=276`이 §5.0 기준선의 `MAX(audit_id)`와 일치 — 감사 로그 최상단 레코드가
정확히 원래 그 행임을 확인.)

검증 후 앱 프로세스(`bootRun` 자바 프로세스, PID 6100)와 gradlew 래퍼 프로세스(PID
5296)를 종료하고 포트 8080이 해제됐음을 확인했다(사전에 떠 있던 다른 gradle
데몬 프로세스는 이 세션이 띄운 것이 아니므로 건드리지 않았다).

### 5.5 결론

| 항목 | 실측값 |
|---|---|
| 백업 파일 크기 | 3,872,233 bytes (약 3.69 MiB) |
| 백업 소요시간 | 0.461초 |
| **복구 소요시간(순수 실행)** | **1.993초** |
| 장애 발생 ~ 복구 완료 총 경과(리허설 기준) | 약 19.7초 |
| 복구 후 데이터 정합성 | 25/25 테이블 행 수 완전 일치, `PATIENT`=12·`AUDIT_LOG`=21·`MAX(audit_id)`=276·`flyway_schema_history`=V15 전부 기준선과 동일 |
| 애플리케이션 레벨 검증 | `/actuator/health` UP, Flyway "마이그레이션 불필요" 판정, 감사 로그 라이브 쿼리로 `audit_id=276` 확인 |

이 규모(총 데이터 약 3.7MB, 최대 테이블 `fhir_resource_cache` 4,428행)에서
`mysqldump`/`mysql` 표준 도구만으로 **RTO 2초 미만**을 실증했다. 데이터가 수십~수백
배 커지면 `--single-transaction` 덤프/복구 시간이 비례해 늘어날 수 있으므로, 실제
운영 규모에 도달하면 이 리허설을 반드시 그 시점의 데이터 볼륨으로 재실시해야 한다
(현재 수치를 그대로 운영 RTO로 가정하지 말 것).

## 6. 체크리스트 요약

- [ ] 백업 전 대상 스키마/계정 권한을 확인했는가(`PROCESS` 권한 없음 경고는
      치명적이지 않음을 인지)?
- [ ] `mysqldump --single-transaction --databases <schema>`로 백업했는가(테이블
      단위가 아니라 `CREATE DATABASE`/`USE`가 포함된 전체 스키마 덤프)?
- [ ] 백업 파일 끝에 `-- Dump completed` 마커가 있는가, 전 테이블의
      `-- Table structure for table` 섹션이 빠짐없이 있는가?
- [ ] 손상 전 별도의 안전 스냅샷을 추가로 하나 더 떠 뒀는가(리허설/실제 사고
      복구 파일과는 별개)?
- [ ] 복구 시작·완료 시각을 정확히 타임스탬프로 기록해 소요시간을 산출했는가?
- [ ] 복구 후 **전 테이블** 행 수를 손상 전 기준선과 대조했는가(일부 테이블만
      확인하고 끝내지 않았는가)?
- [ ] 애플리케이션을 실제로 기동해 `/actuator/health`와 최소 1개 이상의 라이브
      쿼리(예: 감사 로그 최신 행)로 복구된 데이터가 조회 가능함을 확인했는가?
- [ ] 백업 임시 파일과 기동해 둔 애플리케이션 프로세스를 리허설 종료 시 정리했는가
      (덤프 파일을 레포에 커밋하지 않았는가)?
