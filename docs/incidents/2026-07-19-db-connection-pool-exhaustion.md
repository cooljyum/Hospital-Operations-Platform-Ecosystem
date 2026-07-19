# 장애 재현 로그 1: DB 커넥션 풀(HikariCP) 고갈

> Phase 8 Step 8.1. 실제 애플리케이션(로컬 native MySQL 8.4 + `./gradlew bootRun`)과
> 실제 Grafana/Prometheus(Docker Compose)를 대상으로 재현했다. 모든 커맨드·로그·메트릭
> 값은 아래에 실측 그대로 기록한다(가공/상상 없음).

## 0. 환경

- MySQL: 로컬 네이티브 `mysqld.exe`(MySQL 8.4), `hospital_ops` 스키마, 기존 시드 데이터
  (`PATIENT` 12건, `FHIR_RESOURCE_CACHE` 4428건).
- 앱: `app/` 디렉터리에서 `./gradlew.bat bootRun`으로 호스트에서 직접 기동(Phase 7.2와
  동일한 방식 - `docker/prometheus/prometheus.yml`의 `host.docker.internal:8080` 타겟).
- Prometheus + Grafana: `docker compose up -d prometheus grafana`(컨테이너화, `docker/`
  디렉터리).

## 1. 재현 절차

### 1.1 HikariCP 풀을 의도적으로 작게 기동

프로덕션 기본값이 아니라 재현을 결정적으로 만들기 위한 테스트값이다(HikariCP는
`connectionTimeout`에 250ms 미만을 허용하지 않는다 - 최초 50ms로 시도했다가
`IllegalArgumentException: connectionTimeout cannot be less than 250ms`로 기동
자체가 실패해 250ms로 조정했다).

```powershell
cd app
$env:DB_HOST="localhost"; $env:DB_PORT="3306"; $env:DB_NAME="hospital_ops"
$env:DB_USERNAME="hospital_ops"; $env:DB_PASSWORD="changeme"
$env:ENVELOPE_KEK="<로컬 개발용 32바이트 Base64 키>"
.\gradlew.bat bootRun --no-daemon --args="--spring.datasource.hikari.maximum-pool-size=1 --spring.datasource.hikari.connection-timeout=250"
```

기동 로그로 실제 반영 확인(`/actuator/prometheus`):

```
hikaricp_connections_max{pool="HikariPool-1"} 1.0
```

### 1.2 인증 세션 확보 후 동시 요청 400건 투입

Spring Security 폼 로그인(CSRF 토큰 필요) 후 세션 쿠키를 재사용해, `.NET
HttpClient`로 진짜 동시(true concurrency, OS 프로세스 fork 오버헤드 없이) 400개
GET 요청을 `/fhir/Patient/patient-1`에 투입했다.

```powershell
curl.exe -c cookiesA2.txt -s http://localhost:8080/login -o loginA2.html
# hidden _csrf 값 파싱 후
curl.exe -s -o NUL -w "Login HTTP:%{http_code}`n" -b cookiesA2.txt -c cookiesA2.txt `
  --data-urlencode "username=physician" --data-urlencode "password=ChangeMe123!" `
  --data-urlencode "_csrf=$csrf" http://localhost:8080/login
# => Login HTTP:302 (성공, /dashboard로 리다이렉트)

Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$tasks = 1..400 | ForEach-Object {
    $req = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, "http://localhost:8080/fhir/Patient/patient-1")
    $req.Headers.Add("Cookie", "JSESSIONID=$jsessionid")
    $client.SendAsync($req)
}
[System.Threading.Tasks.Task]::WaitAll($tasks)
```

## 2. 증상 (실측)

### 2.1 HTTP 응답 분포

```
Elapsed ms: 774
Name Count
---- -----
200    397
500      3
```

(동일한 재현을 두 번 실행 - 1차: 394/6, 2차(알림 규칙 반영 후 재실행): 397/3.
매번 실제로 몇 건이 타임아웃되는지는 스레드 스케줄링에 따라 변동되지만, `pool
size=1 + timeout=250ms + 동시 400요청` 조합에서는 매번 재현됨.)

### 2.2 애플리케이션 로그(원문 발췌)

```
2026-07-19T23:27:59.131+09:00 ERROR 28164 --- [hospital-ops-lab] [o-8080-exec-135] o.h.engine.jdbc.spi.SqlExceptionHelper
  : HikariPool-1 - Connection is not available, request timed out after 253ms (total=1, active=1, idle=0, waiting=192)
2026-07-19T23:27:59.154+09:00 ERROR 28164 --- [hospital-ops-lab] [o-8080-exec-135] o.a.c.c.C.[.[.[/].[dispatcherServlet]
  : Servlet.service() for servlet [dispatcherServlet] threw exception [Request processing failed:
  org.springframework.dao.DataAccessResourceFailureException: Unable to acquire JDBC Connection
  [HikariPool-1 - Connection is not available, request timed out after 253ms (total=1, active=1, idle=0, waiting=192)]]
  with root cause
java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 253ms
  (total=1, active=1, idle=0, waiting=192)
	at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:714)
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:184)
```

`waiting=192`는 그 순간 커넥션 획득을 기다리며 큐잉된 스레드 수 - 풀 크기 1에
동시요청 400이 몰리면서 실제로 대기 큐가 수백 건까지 쌓였음을 보여준다.

### 2.3 Prometheus 메트릭(원문)

```
# HELP hikaricp_connections_timeout_total Connection timeout total count
# TYPE hikaricp_connections_timeout_total counter
hikaricp_connections_timeout_total{pool="HikariPool-1"} 3.0
```

## 3. Grafana 알림 연동

산출물: `docker/grafana/provisioning/alerting/incident-scenarios-alerting.yml`
(rule uid `hikari-pool-exhaustion-alert`) - 조건: `increase(hikaricp_connections_timeout_total[5m]) > 0`.

Grafana Alerting HTTP API(`/api/alertmanager/grafana/api/v2/alerts`, Basic Auth
`admin:changeme_admin`)로 실제 firing 상태를 확인했다(Phase 7.2 break-glass
검증과 동일한 방식):

```json
[
  {
    "annotations": {
      "__values__": "{\"A\":3.158149605055846,\"B\":3.158149605055846,\"C\":1}",
      "summary": "HikariCP 커넥션 획득 타임아웃이 최근 5분 내 발생했습니다."
    },
    "startsAt": "2026-07-19T14:33:10Z",
    "status": { "state": "active" },
    "labels": {
      "alertname": "DB connection pool exhaustion (HikariCP timeout)",
      "severity": "critical",
      "category": "database"
    }
  }
]
```

(startsAt은 UTC - KST 23:33:10에 해당하며, 위 §1.2 재현 직후다.)

## 4. 대응 조치 및 복구

1. **즉시 조치**: 부하를 유발한 테스트 트래픽을 중단(재현 스크립트 종료).
2. **원인 조치**: `maximum-pool-size=1`은 재현을 위한 인위적 설정이었으므로,
   앱을 기본 HikariCP 설정(`maximum-pool-size` 미지정 -> 기본값 10)으로
   재기동했다.
3. **검증**: 재기동 후 `/actuator/prometheus`의 `hikaricp_connections_max`가
   `10.0`으로 복귀했고, 인증 세션으로 `/fhir/Patient/patient-1`을 호출해
   200을 확인했다.

```
TIMESTAMP (KST): 2026-07-20 00:06:30
hikaricp_connections_max{pool="HikariPool-1"} 10.0
Fhir check HTTP:200
```

4. **복구 시각**: 위 재확인은 사후 재점검이며, 실제 복구 자체는 §1.2 재현
   직후 기본 HikariCP 설정으로 재기동한 시점(이 앱 프로세스는 이후 시나리오
   2/3/4에서도 연속 재사용됐다 - `docs/incidents/2026-07-19-batch-job-forced-failure.md`
   §5의 **2026-07-19 23:49:30** 최초 정상 기동이 사실상 이 시나리오의 복구
   시점과 같다)에 이미 완료됐다 - 별도의 데이터 손상이나 수동 복구 작업이
   필요 없는 순수 설정 문제였다. 위 `TIMESTAMP 00:06:30` 재확인은 그 정상
   상태가 이후에도 계속 유지되고 있음을 재검증한 것이다.

## 5. 프로덕션 권고사항 (이번 재현에서 얻은 결론)

- `maximum-pool-size`는 워크로드의 동시성 요구를 반영해 산정해야 한다(기본값
  10은 로컬 검증용으로는 충분하지만, 실제 동시 사용자 수 대비 산정 필요).
  `waiting`이 두 자릿수 이상으로 쌓이면 Grafana 알림이 즉시 발동하도록 이번에
  `hikari-pool-exhaustion-alert` 규칙을 상시 배포했다.
- HikariCP의 `connectionTimeout` 기본값(30s)은 요청이 실패로 확정되기까지 최대
  30초를 사용자가 대기하게 만든다(§6 DB 다운 시나리오에서 실측). 빠른 실패가
  필요한 API라면 더 짧은 타임아웃 + 서킷 브레이커 도입을 검토할 가치가 있다
  (이번 Step 범위 밖이라 실제 적용은 하지 않음).
