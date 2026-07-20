# Oracle 19c 분기 문서 (Phase 10 Step 10.1)

> deliverable.md §2가 예고한 "대형병원 지원 시 Oracle 19c 버전을 별도 분기 문서로"의
> 실제 산출물이다. **이 프로젝트의 DB 정본 구현은 여전히 MySQL 8.x 단일**이며, 이
> 디렉터리는 애플리케이션을 Oracle로 이중 운영하기 위한 것이 아니라 "채용 SQL 시험이
> Oracle/MySQL 택1"이라는 전제에 대응하는 **분기 참고 문서**다(PLAN.md §0, §12 10.1).

## 1. 이 문서가 다루는 범위

- MySQL 정본 스키마(V1~V16, `app/src/main/resources/db/migration/`)를 Oracle SQL
  방언으로 번역한 DDL(`schema-oracle.sql`).
- MySQL↔Oracle 문법 차이표(`mysql-oracle-dialect-diff.md`).
- Oracle 분기의 조회 최적화 오브젝트(`query-optimization-mv.sql`) — 결정 배경은
  아래 §2.
- 실제 Oracle 컨테이너에 위 DDL을 적용하고 `EXPLAIN PLAN`까지 실측한 결과(§3).

## 2. 결정 배경 — MV vs 요약 테이블

### 2.1 미확정 사항이었던 이유

PLAN.md §0 및 deliverable.md §4 옵션표에 "Oracle 분기의 조회 최적화: Materialized
View 사용(A) vs MySQL과 동일하게 요약 테이블 통일(B)"이 미확정으로 남아 있었다.
`final_summary.md`(3자 논의 기록)에서도 이미 gemini 안(MV 유지)과 claude 안(양 분기
모두 요약 테이블, MV 전면 배제)이 "명시 잔여 이견"(blocking 아님)으로 갈려 있었다.
실제 구현은 claude 안대로 진행되어, **MySQL 분기는 이미 물리 요약 테이블
(`PATIENT_VISIT_SUMMARY`, V13) + Spring Batch refresh job(`SummaryRefreshJob`/
`SummaryRefreshTasklet`)으로 완성**되어 있었다.

### 2.2 재결정 — Gemini CLI 자문 (2026-07-20)

이 프로젝트가 이전에도 미확정 기술 옵션(프론트엔드 스택 Thymeleaf 확정 등)을 Gemini
CLI와 논의해 결정한 선례를 그대로 따랐다. `deliverable.md`/`final_summary.md`를
컨텍스트로 제공하고, "MySQL 쪽은 이미 물리 요약테이블+refresh job으로 구현 완료된
상태에서 Oracle 분기를 어떻게 할지"를 물었다(`gemini --approval-mode=yolo -m
flash-lite`, pro/flash 쿼터 소진으로 flash-lite 사용).

**Gemini 응답 원문(요지)**:

> 포트폴리오의 목적은 DB 이중 운영이 아닌 "Oracle 최적화 역량"의 직접적인
> 증빙입니다. 배치 기반 요약 테이블은 MySQL에서도 구현했으므로, Oracle에서 MV를
> 채택하는 것이 해당 DB 고유의 성능 튜닝 기능을 깊이 이해하고 활용할 줄 안다는 점을
> 더 강력하게 어필할 수 있습니다. 구현체 분리는 1인 개발 환경에서 "데이터베이스별
> 최적화 전략의 차이"를 보여주는 훌륭한 기술 근거가 되며, 면접에서 DB 네이티브
> 기능을 활용한 설계 의도를 설명하는 것이 더 높은 전문성을 보여줍니다.
>
> **RECOMMENDATION: MATERIALIZED_VIEW**

### 2.3 최종 결정

**Oracle 분기는 Materialized View를 채택한다.** MySQL 분기(물리 요약 테이블 +
Spring Batch refresh)와 의도적으로 다른 구현 수단을 쓴다 — 같은 문제(반복 집계
비용)를 각 DB의 관용적인 방식으로 풀었음을 보여주는 것이 이 분기 문서의 목적이며,
이는 `final_summary.md`에 남아있던 gemini 안(MV 유지)과 최종적으로 일치한다.
집계 "정의"(어떤 컬럼을, 어떻게 집계하는가)는 두 분기가 동일해야 하므로
`SummaryRefreshTasklet`의 SQL 구조를 MV 정의에 그대로 이식했고, 그 정의를
물리화·갱신하는 "수단"만 DB별로 다르게 가져갔다(자세한 내용은
`query-optimization-mv.sql` 주석).

## 3. Oracle 19c 실기동 검증

### 3.1 실행 환경 — 정직한 고지

deliverable.md §5가 명시한 대로 "Oracle 19c의 Docker 실행 방식(라이선스·이미지)"부터
확인했다. 결과:

- **공식 Oracle 19c 이미지**(`container-registry.oracle.com/database/enterprise` 또는
  `standard2`)는 Oracle Container Registry의 OTN 계정 로그인·라이선스 동의가 필요하며,
  이 자동화된 에이전트 세션에서는 브라우저 기반 OTN 인증을 수행할 수 없어 받을 수
  없었다.
- 대안으로 커뮤니티 유지 이미지 **`gvenzl/oracle-free:23-slim`**(무료, 인증 불필요,
  Docker Hub 공개)을 사용했다. 이 이미지는 Oracle Database **Free** 23ai 기반이다
  (컨테이너 내부 `v$version` 조회 결과: `Oracle AI Database 26ai Free Release
  23.26.2.0.0`) — **엄밀히는 19c가 아니다.**
- 19c와 23ai(Free)는 같은 SQL/PLSQL 방언 계열(Oracle RDBMS 코어)이고, 이 문서가
  다루는 DDL(IDENTITY 컬럼, VIRTUAL 생성 컬럼, MATERIALIZED VIEW, DBMS_XPLAN 등)은
  전부 19c에서도 동일하게 지원되는 기능이라 **문법 검증 목적으로는 실질적으로
  동등하다.** 다만 19c 고유의 마이너 차이(옵티마이저 기본값, 일부 초기화 파라미터
  등)까지 100% 동일하다고 보장하지는 않는다 — 실제 Oracle 19c 인스턴스에서의 최종
  재검증은 별도로 필요하다(§4 절차 참고).

### 3.2 실행 결과 — 실제로 띄우고 적용함

이 환경(Docker Desktop 정상 동작 확인됨)에서 실제로 컨테이너를 띄우고 검증까지
완료했다:

```
docker pull gvenzl/oracle-free:23-slim     # 이미지 크기 약 2.87GB
docker run -d --name oracle-branch-test \
  -e ORACLE_PASSWORD=*** -e ORACLE_DATABASE=HOSPITALOPS \
  -p 1521:1521 gvenzl/oracle-free:23-slim
```

- 최초 기동(first database startup, 데이터 파일 압축 해제 포함) 완료까지 약 20초.
- `hospital_ops` 스키마 유저를 별도로 생성(`CREATE USER` + `CONNECT`/`RESOURCE` +
  `CREATE MATERIALIZED VIEW`/`CREATE TRIGGER`/`CREATE SEQUENCE` 권한 부여)한 뒤,
  `sqlplus`로 `schema-oracle.sql` 전체(테이블 10개, 트리거 5개, 인덱스 9개, 시드
  INSERT 63행)를 적용 — **전 구문 에러 없이 성공**(로그 전문에서 `ORA-`/`SP2-`
  패턴 0건 확인).
- 최소 합성 테스트 데이터(환자 3명, 방문 3건, 검사 3건, 처방 2건 — MySQL 정본
  PATIENT 12건 데이터와는 완전히 무관한 별도 인프라·별도 값)를 직접 삽입.
- `query-optimization-mv.sql`을 적용해 `PATIENT_VISIT_SUMMARY_MV` 생성 성공, 값
  검증 결과 `SummaryRefreshTasklet`과 동일한 집계 로직으로 정확한 값 산출 확인:

  | patient_id | visit_count | lab_result_count | prescription_count |
  |---|---|---|---|
  | 1 | 2 | 2 | 2 |
  | 2 | 1 | 1 | 0 |
  | 3(방문 없음) | 0 | 0 | 0 |

  (환자 3은 방문·검사·처방이 전혀 없는 케이스 — LEFT JOIN + COALESCE로 0건 행이
  올바르게 채워짐을 확인. `PATIENT_VISIT_SUMMARY`(V13) 주석이 명시한 "방문이 없는
  환자도 0건 행으로 채워진다" 요구사항과 동일하게 동작.)

### 3.3 REFRESH ON DEMAND 실측 — MySQL 배치 패턴과의 동등성 확인

MV를 `REFRESH COMPLETE ON DEMAND`로 만들었으므로(원본 테이블 트랜잭션에 자동
연동되지 않음 — MySQL `SummaryRefreshJob`이 "명시적 트리거 시에만 전체 재계산"인
것과 동일한 운영 패턴), 실제로 확인했다:

1. 환자 3에 새 방문 1건 INSERT + COMMIT.
2. MV 즉시 조회 → `visit_count = 0` (갱신 전이라 반영 안 됨 — 예상대로).
3. `DBMS_MVIEW.REFRESH('PATIENT_VISIT_SUMMARY_MV', 'C')` 실행.
4. MV 재조회 → `visit_count = 1` (갱신 후 정확히 반영됨).

### 3.4 EXPLAIN PLAN 실측 — MV 사용 전/후 비교

```
-- 원본 3-way 집계 쿼리(MV 없이 매번 재계산한다고 가정)
Plan hash value: 2275036927
--------------------------------------------------------------------
| Id  | Operation             | Name         | Rows  | Cost (%CPU)|
--------------------------------------------------------------------
|   0 | SELECT STATEMENT      |              |     8 |    10  (0)|
|   1 |  HASH JOIN OUTER      |              |     8 |    10  (0)|
|   2 |   HASH JOIN OUTER     |              |     4 |     7  (0)|
|   3 |    NESTED LOOPS OUTER |              |     2 |     4  (0)|
|   4 |     INDEX UNIQUE SCAN | SYS_C008647  |     1 |     1  (0)|
|   5 |     VIEW              |              |     2 |     3  (0)|
|   6 |      SORT GROUP BY    |              |     2 |     3  (0)|
|   7 |       TABLE ACCESS FULL| PRESCRIPTION|     2 |     3  (0)|
|   8 |    VIEW               |              |     2 |     3  (0)|
|   9 |     HASH GROUP BY     |              |     2 |     3  (0)|
|  10 |      TABLE ACCESS FULL | VISIT        |     2 |     3  (0)|
|  11 |   VIEW                |              |     2 |     3  (0)|
|  12 |    HASH GROUP BY      |              |     2 |     3  (0)|
|  13 |     TABLE ACCESS FULL  | LAB_RESULT   |     2 |     3  (0)|
--------------------------------------------------------------------

-- MV 조회
Plan hash value: 1085620961
--------------------------------------------------------------------------------
| Id  | Operation                       | Name                             | Cost |
--------------------------------------------------------------------------------
|   0 | SELECT STATEMENT                |                                   |    1 |
|   1 |  MAT_VIEW ACCESS BY INDEX ROWID | PATIENT_VISIT_SUMMARY_MV         |    1 |
|   2 |   INDEX UNIQUE SCAN             | IDX_PATIENT_VISIT_SUMMARY_MV_PK  |    0 |
--------------------------------------------------------------------------------
```

**결과 해석**: 원본 집계 쿼리는 `VISIT`/`LAB_RESULT`/`PRESCRIPTION` 3개 테이블을
각각 `TABLE ACCESS FULL` + `GROUP BY`(HASH/SORT) 한 뒤 `PATIENT`와 3중 OUTER JOIN
(비용 10) 하는 반면, MV 조회는 인덱스 유니크 스캔 1건 + `MAT_VIEW ACCESS BY INDEX
ROWID` 1건(비용 1)으로 끝난다. **이 표본(환자 3명, 각 테이블 2~3행)에서도 비용이
10→1로 90% 감소했고**, 원본 쿼리의 비용은 각 원본 테이블의 행 수에 비례해 계속
증가하는 반면(옵티마이저가 매번 전체 스캔+재집계) MV 조회는 데이터 규모와 무관하게
인덱스 스캔 O(1) 비용을 유지하므로, 실제 병원 규모 데이터(수만~수십만 행)에서는
격차가 훨씬 커진다 — MySQL 물리 요약 테이블이 같은 이유로 채택됐던 것과 동일한
논리다.

## 4. 실 Oracle 19c 환경에서 재검증하는 절차

이 문서의 검증은 Oracle Database Free 23ai(§3.1)로 수행했다. 실제 채용 SQL 시험/면접
전에 진짜 Oracle 19c로 재검증하려면:

1. Oracle Cloud Free Tier 또는 사내/개인 라이선스가 있는 Oracle 19c 인스턴스를
   확보한다(Container Registry 이미지를 쓰려면 `https://container-registry.oracle.com`
   에서 OTN 계정으로 로그인 후 `docker login container-registry.oracle.com` 필요).
2. `schema-oracle.sql`을 그대로(또는 §3.1이 언급한 마이너 차이를 사전 검토 후)
   적용한다. V5 Spring Batch 메타데이터 테이블은 `spring-batch-core-5.2.6.jar` 내
   `org/springframework/batch/core/schema-oracle.sql`을 반드시 함께 적용한다(직접
   손으로 만들지 않는다 — 이유는 `mysql-oracle-dialect-diff.md` V5 항목 참고).
3. `query-optimization-mv.sql`을 적용하고, 실제 규모의 합성 데이터(Synthea 대량
   생성)로 EXPLAIN PLAN을 재측정해 §3.4의 소규모 표본 결과가 대규모에서도 같은
   방향(MV 우위)으로 유지되는지 확인한다.
4. 옵티마이저 통계(`DBMS_STATS.GATHER_TABLE_STATS`) 수집 여부에 따라 비용 추정치가
   달라질 수 있으므로, 실측 전 반드시 통계를 수집한 뒤 비교한다.

## 5. 정리 — 환경 뒷정리

이 검증에 사용한 `oracle-branch-test` 컨테이너와 `gvenzl/oracle-free:23-slim`
이미지는 검증 완료 후 제거했다(대용량 이미지의 디스크 점유를 남기지 않기 위해).
재현하려면 §3.2의 명령을 그대로 다시 실행하면 된다. MySQL 정본 데이터(PATIENT 12건
등)는 이 Step에서 전혀 손대지 않았다 — Oracle 컨테이너는 완전히 별도의 임시 인프라로
띄우고 지웠다.
