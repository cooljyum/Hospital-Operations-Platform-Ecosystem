# 장애 재현 로그 4: MySQL 프로세스 중단 → 앱 재연결 실패

> Phase 8 Step 8.1. 로컬 네이티브 `mysqld.exe` 프로세스를 강제 종료해 DB 완전
> 다운을 재현했다. 단순히 "DB가 죽었다 -> 살렸다 -> 끝"이 아니라, **MySQL을
> 되살린 뒤에도 앱이 한동안 재연결하지 못한 실제 2차 장애**를 발견해 그 원인과
> 조치까지 기록한다.

## 1. 재현 절차

### 1.1 MySQL 프로세스 강제 종료

```powershell
Get-Process mysqld  # Id 22196, 27488 확인
Stop-Process -Id 22196,27488 -Force
Test-NetConnection -ComputerName localhost -Port 3306
# => TcpTestSucceeded : False
```

**MYSQL_DOWN_START: 2026-07-19 23:52:08 (KST)**

### 1.2 앱(정상 HikariCP 설정, maximum-pool-size 기본값 10)에 요청 5회 투입

```powershell
for ($i=1; $i -le 5; $i++) {
  $t0 = Get-Date
  $code = curl.exe -s -o NUL -w "%{http_code}" -b cookiesD.txt "http://localhost:8080/fhir/Patient/patient-1"
  $elapsed = ((Get-Date) - $t0).TotalMilliseconds
  Write-Output "Attempt $i : HTTP=$code elapsed_ms=$([math]::Round($elapsed))"
}
```

## 2. 증상 1: 요청당 약 30초 대기 후 실패 (실측)

```
Attempt 1 : HTTP=500 elapsed_ms=30144
Attempt 2 : HTTP=500 elapsed_ms=30051
Attempt 3 : HTTP=500 elapsed_ms=30059
Attempt 4 : HTTP=500 elapsed_ms=30064
Attempt 5 : HTTP=500 elapsed_ms=30068
```

HikariCP 기본 `connectionTimeout`(30000ms)이 그대로 사용자 체감 지연이 된다 -
DB가 완전히 죽어 있는 동안 클라이언트는 (즉각 실패가 아니라) 매 요청마다 정확히
~30초를 기다린 뒤에야 500을 받는다. 애플리케이션 로그(원문 발췌):

```
WARN ... com.zaxxer.hikari.pool.PoolBase : HikariPool-1 - Pool is empty, failed to
  create/setup connection (091e5e68-c350-4c33-998a-337953bd11e4)
com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure
Caused by: com.mysql.cj.exceptions.CJCommunicationsException: Communications link failure
Caused by: java.net.ConnectException: Connection refused: getsockopt
```

`/actuator/health` 응답(Spring Boot `DataSourceHealthIndicator`가 정확히 DOWN
판정):

```
$ curl -s -w "\nHTTP:%{http_code}\n" http://localhost:8080/actuator/health
{"status":"DOWN"}
HTTP:503
```

## 3. MySQL 재기동

```powershell
Start-Process -FilePath "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" `
  -ArgumentList '--defaults-file="C:\ProgramData\MySQL\MySQL Server 8.4\my.ini"' -WindowStyle Hidden
Test-NetConnection -ComputerName localhost -Port 3306  # => True (수 초 내)
```

**MYSQL_RESTART_ISSUED: 2026-07-19 23:56:46 (KST)** - TCP 3306은 곧바로 다시
열렸고, `mysql.exe` CLI로 `SELECT 1;`도 즉시 성공했다(DB 자체는 건강).

## 4. 증상 2 (2차 장애, 예상 밖 발견): MySQL이 살아난 뒤에도 앱은 계속 실패

MySQL 포트가 열리고 CLI 쿼리도 성공하는데도, **앱의 JDBC 재연결은 수 분간 계속
실패**했다:

```
2026-07-19T23:57:32.466 ERROR ... o.h.engine.jdbc.spi.SqlExceptionHelper
  : Public Key Retrieval is not allowed
com.mysql.cj.exceptions.UnableToConnectException: Public Key Retrieval is not allowed
```

(이 오류가 23:57:32부터 23:59:01+까지 반복 - 약 90초 이상 동일 오류로 계속
재시도-실패.)

### 원인 분석

- 앱의 JDBC URL(`application.yml`)은 `useSSL=false`이고 `allowPublicKeyRetrieval`을
  지정하지 않는다.
- MySQL 8의 기본 인증 플러그인 `caching_sha2_password`는 **최초(full) 인증**
  시 클라이언트가 서버의 RSA 공개키를 받아야 하는데, SSL이 아닌 평문 채널로
  이 키를 주고받는 것은 MITM에 취약해질 수 있어 MySQL Connector/J는
  `allowPublicKeyRetrieval=true`를 명시하지 않으면 **거부**한다.
- 서버는 한 번 full 인증에 성공한 계정에 대해 "fast-auth" 캐시를 메모리에
  유지해, 이후 접속은 RSA 교환 없이 빠르게 통과시킨다. 이 캐시는 **MySQL
  프로세스 재기동으로 초기화**된다.
- 즉, MySQL 재기동 직후에는 `hospital_ops` 계정의 fast-auth 캐시가 비어 있어,
  Connector/J 기반 앱은 (SSL도 안 쓰고 `allowPublicKeyRetrieval`도 안 켜져
  있으므로) **영구적으로 재연결에 실패**한다 - 시간이 지나도 저절로 낫지 않는다.

### 조치: fast-auth 캐시 재적재

앱과 동일한 host 문자열(`localhost`)로 `mysql.exe` CLI에서 재인증을 1회
수행해 서버측 캐시를 재적재했다(`mysql` 공식 클라이언트는 이 제약이 없어
정상적으로 full 인증을 통과한다):

```powershell
& $mysql -u hospital_ops -pchangeme -h localhost hospital_ops -e "SELECT 1;"
# => 1  (성공)
curl.exe -s -o NUL -w "Fhir check HTTP:%{http_code}\n" -m 10 -b cookiesD.txt `
  "http://localhost:8080/fhir/Patient/patient-1"
# => Fhir check HTTP:200   (직후 앱 재연결도 성공)
```

## 5. 복구 확인 및 시각

```
$ curl -s http://localhost:8080/actuator/health
{"status":"UP"}
hikaricp_connections_idle{pool="HikariPool-1"} 10.0
```

**SCENARIO_D_RECOVERY_CONFIRMED: 2026-07-20 00:00:17 (KST)** - `/fhir/Patient/patient-1`
200 응답, `/actuator/health` UP, HikariCP idle 커넥션 10개(정상 풀 완전 회복)
모두 확인.

**총 장애 지속시간**: 23:52:08(MySQL 다운) ~ 00:00:17(앱 완전 정상화) = **약
8분 9초**. 그중 MySQL 프로세스 자체는 23:56:46 재기동 직후(수 초 내) TCP/쿼리
레벨로는 정상이었지만, §4의 fast-auth 캐시 문제로 **앱 레벨 실제 복구는 그보다
약 3분 30초 더 지연**됐다 - "DB가 다시 살아났다"와 "애플리케이션이 실제로
사용 가능하다"는 별개 문제임을 보여주는 실측 사례다.

## 6. Grafana/메트릭 연동

같은 원인(HikariCP가 새 커넥션을 만들지 못함)이 `hikari-pool-exhaustion-alert`
규칙(시나리오 1 문서 참고)의 조건도 함께 충족시켰다 - 다운 기간 중 커넥션
시도 실패가 반복되며 `hikaricp_connections_timeout_total`이 3(시나리오 1
재현치)에서 **24**까지 증가했다. 이 규칙이 이 시나리오 자체로도 실제
발동함을 별도로 재검증하기 위해, 짧은 2차 재현(MySQL을 다시 순간 중단 ->
요청 1건 실패 -> 즉시 재기동)을 추가로 실행하고 Grafana Alerting API 응답을
직접 캡처했다:

```powershell
# RE_VERIFY_MYSQL_DOWN_START (KST): 2026-07-20 00:07:12
Stop-Process -Id 23124,26756 -Force
curl.exe -s -o NUL -w "%{http_code}" -m 35 -b cookiesD.txt "http://localhost:8080/fhir/Patient/patient-1"
# => 500 (약 30초 대기 후, §2와 동일 패턴)

# RE_VERIFY_MYSQL_RESTART (KST): 2026-07-20 00:08:02
Start-Process mysqld.exe ...
```

```
hikaricp_connections_timeout_total{pool="HikariPool-1"} 25.0
```

```json
{
  "annotations": {
    "__values__": "{\"A\":1.0527386997273407,\"B\":1.0527386997273407,\"C\":1}",
    "summary": "HikariCP 커넥션 획득 타임아웃이 최근 5분 내 발생했습니다."
  },
  "startsAt": "2026-07-19T15:08:10Z",
  "status": { "state": "active" },
  "labels": {
    "alertname": "DB connection pool exhaustion (HikariCP timeout)",
    "severity": "critical",
    "category": "database"
  }
}
```

(`/api/alertmanager/grafana/api/v2/alerts` 실 응답 - startsAt UTC 15:08:10 =
KST 00:08:10, 위 2차 재현 직후. `hikari-pool-exhaustion-alert`가 "풀 고갈"뿐
아니라 "DB 완전 다운"에도 동일하게 반응함을 실제 API 응답으로 확인.)

즉 **하나의 알림 규칙(HikariCP 타임아웃)이 "풀 고갈"과 "DB 완전 다운"이라는
서로 다른 두 근본 원인을 모두 감지**한다 - 알림 자체는 근본 원인을 구분하지
못하므로, 실제 대응 시에는 `/actuator/health`(DOWN이면 DB 자체 문제,
UP인데도 타임아웃이면 순수 풀 크기 문제)로 1차 분기하는 것을 권고 조치로
남긴다.

2차 재현 이후 최종 복구도 재확인했다: `mysql.exe -h localhost` CLI로 fast-auth
캐시를 재적재한 뒤 **FINAL_RECOVERY_TIMESTAMP (KST): 2026-07-20 00:08:52**에
`/fhir/Patient/patient-1` 200, `/actuator/health` `{"status":"UP"}`을 확인했다.

## 7. 프로덕션 권고사항

1. **JDBC URL에 `allowPublicKeyRetrieval=true` 추가(또는 `useSSL=true` +
   적절한 인증서 체계로 전환)** - 이번에 재현된 "MySQL 재기동 후 앱이 스스로
   복구되지 않는" 실패 모드를 근본적으로 없앤다. 현재는 범위 밖(Step 8.1은
   재현·기록이 목적)이라 `application.yml`은 변경하지 않았다 - 이 발견을
   근거로 별도 후속 작업으로 제안한다.
2. DB 완전 다운 상황에서 요청이 30초씩 붙잡히는 것은 사용자 경험상 나쁘다 -
   §1(시나리오 1 문서)에서도 동일하게 권고했듯 더 짧은 `connectionTimeout` +
   서킷 브레이커/헬스체크 기반 fail-fast 게이트웨이 규칙 도입을 검토할 가치가
   있다.
