# 슬로우 쿼리 튜닝 1: AUDIT_LOG 감사 로그 검색(target_pk 필터)

> Phase 8 Step 8.3. 로컬 native MySQL 8.4(`mysqld.exe`, `hospital_ops` 스키마)를 대상으로
> 실측했다. 모든 EXPLAIN/EXPLAIN ANALYZE/실행시간 값은 아래에 실측 그대로 기록한다
> (가공/상상 없음).

## 0. 대상 쿼리 선정 근거

Phase 5.2에서 만든 감사 로그 조회 화면(`/audit/preview`, ROLE_AUDITOR 전용)은
`AuditLogRepository#search`(`app/src/main/java/com/hospitalops/security/AuditLogRepository.java`)의
아래 JPQL을 쓴다:

```java
@Query("SELECT a FROM AuditLog a WHERE "
        + "(:actorUsername IS NULL OR a.actorUsername = :actorUsername) "
        + "AND (:targetPk IS NULL OR a.targetPk = :targetPk) "
        + "AND (:from IS NULL OR a.occurredAt >= :from) "
        + "AND (:to IS NULL OR a.occurredAt <= :to) "
        + "ORDER BY a.occurredAt DESC, a.auditId DESC")
List<AuditLog> search(...);
```

행위자/대상 PK/기간을 전부 선택적으로 받는 "catch-all" 패턴이다. `AUDIT_LOG`
(`V10__audit_log.sql`)에는 다음 인덱스만 있었다:

- `idx_audit_log_actor_occurred (actor_username, occurred_at)`
- `idx_audit_log_result_code (result_code)`

`target_pk`에는 인덱스가 없다. `target_pk`는 `BulkDecryptionApprovalService`
(`app/src/main/java/com/hospitalops/approval/BulkDecryptionApprovalService.java`)가
대량 복호화 요청 ID를 기록하는 실제 값이므로, 감사자가 "이 요청/대상 PK로 무슨 일이
있었는지" 행위자·기간 없이 `target_pk`만으로 검색하는 것은 실제 UI가 지원하는 경로다.
이 경로는 인덱스를 전혀 타지 못해 항상 풀 테이블 스캔이 발생한다 — 이번 튜닝 대상.

## 1. 데이터 규모와 벤치마크 데이터에 대한 메모

이 프로젝트의 실제 시드 데이터는 합성(Synthea) 데이터라 작다(튜닝 시작 시점
`AUDIT_LOG` 21건, `PATIENT` 12건 등). 21건로는 인덱스 유무와 무관하게 수 ms 이하라
"개선폭(ms)"을 의미 있게 잴 수 없다. 그래서 CLAUDE.md 작업 지시대로, **측정
목적으로만** `AUDIT_LOG`에 합성 데이터를 임시로 50만 건 늘려 실측한 뒤, 측정이 끝나는
즉시 원래 21건 상태로 되돌렸다(§4 참고 — 최종 검증 결과 21건, `MAX(audit_id)=276`으로
튜닝 시작 전과 완전히 동일).

벤치마크 데이터 생성 스크립트(요지, 임시 TEMPORARY TABLE 두 개를 교차 조인해
`target_pk`가 서로 다른 50만 건 생성 — MySQL은 같은 TEMPORARY TABLE을 한 문장에서
두 번 참조(self-join)할 수 없어 별도 테이블 두 개를 썼다):

```sql
CREATE TEMPORARY TABLE seq1000_a (n INT PRIMARY KEY);
INSERT INTO seq1000_a (n)
WITH RECURSIVE seq AS (
  SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 1000
)
SELECT n FROM seq;

CREATE TEMPORARY TABLE seq1000_b (n INT PRIMARY KEY);
INSERT INTO seq1000_b (n) SELECT n FROM seq1000_a;

INSERT INTO audit_log (actor_username, target_pk, action, purpose, masked, ip_address, success, result_code, occurred_at)
SELECT
  CONCAT('bench_user_', MOD(a.n * 1000 + b.n, 50)),
  CONCAT('BENCH-', a.n * 1000 + b.n),
  'BENCH_ACTION',
  'Step 8.3 tuning benchmark synthetic row (temporary, deleted after measurement)',
  0, '127.0.0.1', 1, 'BENCH_SUCCESS',
  DATE_SUB(NOW(), INTERVAL MOD(a.n * 1000 + b.n, 200000) MINUTE)
FROM seq1000_a a JOIN seq1000_b b
LIMIT 500000;

ANALYZE TABLE audit_log;
```

실행 결과: `audit_log_row_count_after_seed = 500021` (기존 21건 + 신규 50만 건).

측정용 쿼리는 `AuditLogRepository#search`가 실제로 `actorUsername`/`from`/`to`는
바인딩하지 않고(NULL) `targetPk`만 지정했을 때 Hibernate가 서버로 보내는 파라미터
바인딩을 그대로 리터럴로 재현한 것이다(`(:param IS NULL OR col = :param)` 패턴에서
미사용 파라미터는 실제로 SQL NULL이 바인딩된다):

```sql
SELECT * FROM audit_log
WHERE (NULL IS NULL OR actor_username = NULL)
  AND ('BENCH-250000' IS NULL OR target_pk = 'BENCH-250000')
  AND (NULL IS NULL OR occurred_at >= NULL)
  AND (NULL IS NULL OR occurred_at <= NULL)
ORDER BY occurred_at DESC, audit_id DESC;
```

## 2. 튜닝 전 (인덱스 없음)

### 2.1 EXPLAIN

```
+----+-------------+-----------+------------+------+---------------+------+---------+------+--------+----------+-----------------------------+
| id | select_type | table     | partitions | type | possible_keys | key  | key_len | ref  | rows   | filtered | Extra                       |
+----+-------------+-----------+------------+------+---------------+------+---------+------+--------+----------+-----------------------------+
|  1 | SIMPLE      | audit_log | NULL       | ALL  | NULL          | NULL | NULL    | NULL | 495214 |    10.00 | Using where; Using filesort |
+----+-------------+-----------+------------+------+---------------+------+---------+------+--------+----------+-----------------------------+
```

`type: ALL` = 인덱스를 전혀 못 타는 풀 테이블 스캔. `rows: 495214`(전체 행 거의
전부를 스캔 예상). `Extra: Using where; Using filesort` — `ORDER BY occurred_at
DESC, audit_id DESC`를 인덱스로 못 풀어 별도 정렬(filesort)까지 필요.

### 2.2 EXPLAIN ANALYZE (실측 실행계획)

```
-> Sort: audit_log.occurred_at DESC, audit_log.audit_id DESC  (cost=52126 rows=495214) (actual time=487..487 rows=0 loops=1)
    -> Filter: (audit_log.target_pk = 'BENCH-250000')  (cost=52126 rows=495214) (actual time=487..487 rows=0 loops=1)
        -> Table scan on audit_log  (cost=52126 rows=495214) (actual time=0.387..418 rows=500021 loops=1)
```

옵티마이저 비용 `cost=52126`, 실측 총 소요 `actual time≈487ms`(테이블 스캔 자체가
0.387~418ms, 그 위에 필터+정렬로 487ms까지 누적).

### 2.3 실측 실행시간 (`SET profiling=1` + 실제 쿼리 3회, 서로 다른 target_pk)

| target_pk | Duration (초) |
|---|---|
| `BENCH-250000` | 0.50792200 |
| `BENCH-400000` | 0.48155350 |
| `BENCH-100000` | 0.43721750 |
| **평균** | **≈ 475.6 ms** |

## 3. 튜닝 조치

`target_pk`에 단일 컬럼 인덱스를 추가했다. 실제 사용 패턴상 `target_pk`가 주어지면
결과는 사실상 1건(대량복호화 요청 ID 등 값 자체가 유니크에 가까움)이라 정렬 컬럼까지
포함한 복합 인덱스는 불필요하다고 판단해 단일 컬럼 인덱스로 최소화했다.

Flyway 마이그레이션: `app/src/main/resources/db/migration/V15__audit_log_target_pk_index.sql`

```sql
CREATE INDEX idx_audit_log_target_pk ON AUDIT_LOG (target_pk);
```

`./gradlew.bat bootRun`(로컬 native MySQL 대상)으로 앱을 기동해 Flyway가 정식으로
적용하도록 했다(수동 DDL + `flyway_schema_history` 수기 조작이 아니라 실제 앱 기동
경로로 체크섬까지 정상 기록):

```
o.f.core.internal.command.DbMigrate : Migrating schema `hospital_ops` to version "15 - audit log target pk index"
o.f.core.internal.command.DbMigrate : Successfully applied 1 migration to schema `hospital_ops`, now at version v15 (execution time 00:01.676s)
...
Started HospitalOpsLabApplication in 10.924 seconds (process running for 11.543)
```

적용 후 `SHOW CREATE TABLE audit_log`에 인덱스 반영 확인:

```
KEY `idx_audit_log_actor_occurred` (`actor_username`,`occurred_at`),
KEY `idx_audit_log_result_code` (`result_code`),
KEY `idx_audit_log_target_pk` (`target_pk`)
```

## 4. 튜닝 후 (인덱스 적용, 동일 50만 건 데이터로 재측정)

### 4.1 EXPLAIN

```
+----+-------------+-----------+------------+------+-------------------------+-------------------------+---------+-------+------+----------+----------------+
| id | select_type | table     | partitions | type | possible_keys           | key                     | key_len | ref   | rows | filtered | Extra          |
+----+-------------+-----------+------------+------+-------------------------+-------------------------+---------+-------+------+----------+----------------+
|  1 | SIMPLE      | audit_log | NULL       | ref  | idx_audit_log_target_pk | idx_audit_log_target_pk | 403     | const |    1 |   100.00 | Using filesort |
+----+-------------+-----------+------------+------+-------------------------+-------------------------+---------+-------+------+----------+----------------+
```

`type: ALL` → `ref`(인덱스 동등 조회). `rows: 495214` → `1`. `filtered: 10.00` →
`100.00`. `Using where`가 사라짐(필터가 인덱스 조회 자체로 대체됨). `Using
filesort`는 남아있으나 결과가 1건뿐이라 사실상 비용 없음(아래 EXPLAIN ANALYZE 참고).

### 4.2 EXPLAIN ANALYZE (실측 실행계획)

```
-> Sort: audit_log.occurred_at DESC, audit_log.audit_id DESC  (cost=0.604 rows=1) (actual time=0.0217..0.0217 rows=0 loops=1)
    -> Index lookup on audit_log using idx_audit_log_target_pk (target_pk='BENCH-250000')  (cost=0.604 rows=1) (actual time=0.0153..0.0153 rows=0 loops=1)
```

옵티마이저 비용 `cost=52126 → 0.604`(약 86,300배 감소), 실측 총 소요
`actual time≈487ms → ≈0.0217ms`.

### 4.3 실측 실행시간 (`SET profiling=1` + 실제 쿼리 3회, 튜닝 전과 동일한 target_pk 3개)

| target_pk | Duration (초) |
|---|---|
| `BENCH-250000` | 0.00033500 |
| `BENCH-400000` | 0.00068250 |
| `BENCH-100000` | 0.00034350 |
| **평균** | **≈ 0.454 ms** |

## 5. 개선폭 요약

| 지표 | 튜닝 전 | 튜닝 후 | 개선폭 |
|---|---|---|---|
| EXPLAIN `type` | `ALL`(풀 스캔) | `ref`(인덱스 동등 조회) | - |
| EXPLAIN `rows` (추정) | 495,214 | 1 | ≈ 495,214배 감소 |
| EXPLAIN `filtered` | 10.00% | 100.00% | - |
| 옵티마이저 `cost` (EXPLAIN ANALYZE) | 52,126 | 0.604 | ≈ 86,300배 감소 |
| EXPLAIN ANALYZE `actual time` | ≈ 487 ms | ≈ 0.0217 ms | ≈ 22,400배 감소 |
| 실측 쿼리 실행시간 평균 (`SHOW PROFILES`, 3회) | ≈ 475.6 ms | ≈ 0.454 ms | ≈ 1,048배 감소 (약 475 ms 단축) |

50만 건 규모의 `AUDIT_LOG`에서 `target_pk` 단일 조건 검색이 **약 476 ms → 0.45 ms**로
개선됐다(측정 방식별로 배율은 다르지만, 클라이언트 체감에 가장 가까운 `SHOW PROFILES`
실측 기준 약 1,000배 단축).

## 6. 원복 확인

측정 종료 직후 벤치마크 데이터를 삭제하고 `AUTO_INCREMENT`를 원상복구했다:

```sql
DELETE FROM audit_log WHERE result_code = 'BENCH_SUCCESS' OR action = 'BENCH_ACTION' OR target_pk LIKE 'BENCH-%';
ALTER TABLE audit_log AUTO_INCREMENT = 277;
ANALYZE TABLE audit_log;
```

결과:

- `audit_log_row_count_after_cleanup = 21` (튜닝 시작 전과 동일)
- `max_audit_id_after_cleanup = 276` (튜닝 시작 전과 동일)
- `remaining_bench_rows = 0`
- `SELECT audit_id, actor_username, target_pk, action, result_code, occurred_at FROM audit_log ORDER BY audit_id;` 출력이 튜닝 시작 전 백업 스냅샷(21건, 전부 `BREAK_GLASS_ACCESS_GRANT`/`BREAK_GLASS_GRANTED`, `target_pk IS NULL`)과 행 단위로 정확히 일치.
- 다른 테이블 row count 전부 튜닝 시작 전과 동일 (`PATIENT 12`, `FHIR_RESOURCE_CACHE 4307`, `LAB_RESULT 3666`, `VISIT 509`, `PRESCRIPTION 145` 등 — `information_schema.tables.TABLE_ROWS`는 InnoDB 근사치라 `audit_log`만 `DELETE` 직후 일시적으로 `0`으로 보였으나, `COUNT(*)` 실측치는 21로 정확함).

인덱스 자체(`idx_audit_log_target_pk`)는 벤치마크 데이터와 무관하게 스키마 변경이므로
그대로 유지한다 — `V15__audit_log_target_pk_index.sql`이 정식 산출물이다.
