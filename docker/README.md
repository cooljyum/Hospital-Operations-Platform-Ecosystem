# /docker

Docker Compose 기반 로컬 인프라 정의. Phase 0.4에서 골격이 추가됐다.

## 구성 서비스 (`docker-compose.yml`)

| 서비스 | 이미지 | 역할 |
|---|---|---|
| `mysql` | `mysql:8.0` | 레거시 HIS 스키마가 올라갈 정본 DB. `mysql-data` named volume으로 영속화, `mysqladmin ping` healthcheck. |
| `app` | `../app/Dockerfile`로 빌드 | Spring Boot 애플리케이션. `mysql`이 healthy가 된 뒤에만 기동(`depends_on: condition: service_healthy`). healthcheck는 `/actuator/health`(Phase 7.1에서 Actuator 도입 완료, `SecurityConfig`에서 인증 없이 `permitAll`). |
| `prometheus` | `prom/prometheus:latest` | `prometheus/prometheus.yml`을 마운트해 `app:8080/actuator/prometheus`를 스크레이프(2026-07-23 세션 실기동 검증에서 `up{job="hospital-ops-app"}` 확인). |
| `grafana` | `grafana/grafana:latest` | `grafana/provisioning/`(datasource로 Prometheus 자동 등록)과 `grafana/dashboards/`(Phase 7.2에서 채울 대시보드 JSON)를 마운트. |
| `nginx` | `nginx:stable` | `nginx/nginx.conf`로 `app`에 리버스 프록시(80 포트). |

모든 서비스는 `hospital-ops-net` 브리지 네트워크로 묶여 있고, MySQL/Prometheus/Grafana 데이터는 각각 named volume(`mysql-data`/`prometheus-data`/`grafana-data`)으로 영속화된다.

## 디렉터리

```
docker/
├── docker-compose.yml
├── .env.example          # DB_*, GRAFANA_* 환경변수 예시 (실값은 .env로 별도 복사, git 미추적)
├── nginx/
│   └── nginx.conf
├── prometheus/
│   └── prometheus.yml
└── grafana/
    ├── provisioning/
    │   ├── datasources/datasource.yml
    │   └── dashboards/dashboard-provider.yml
    └── dashboards/        # Phase 7.2에서 대시보드 JSON 추가 예정 (현재 빈 폴더, README로 자리 표시)
```

## 사용법

```bash
cp docker/.env.example docker/.env   # 실제 비밀번호 값과 ENVELOPE_KEK를 채운 뒤 사용
cd docker
docker compose up -d
```

`.env`에는 `DB_*`/`GRAFANA_*`뿐 아니라 **`ENVELOPE_KEK`(Base64 인코딩된 32바이트 키)도 반드시
채워야 한다** — 비워두면 `app` 컨테이너가 `EnvelopeCrypto` 빈 생성 단계에서 즉시
`IllegalStateException`으로 죽는다(의도된 fail-fast, `.env.example`의 안내 주석 참고).

로컬에 native MySQL(포트 3306)을 별도로 띄워둔 상태라면 `docker compose up` 전에 먼저 멈춰야
포트 충돌이 나지 않는다(`docs/local-dev-mysql.md` 참고, `Stop-Process -Name mysqld`).

## ✅ 이 저장소가 작업된 머신에서의 검증 상태 (2026-07-23 세션 실기동 검증)

Docker Desktop(`29.6.1`)과 WSL2가 정상 동작하는 환경에서 `docker compose up -d`를 실제로 실행해
5개 컨테이너(mysql/app/prometheus/grafana/nginx)가 전부 `healthy`/`running` 상태로 기동하는 것을
확인했다. 이 검증 전에는 아래 표의 두 가지 blocking 버그 때문에 `app` 컨테이너가 재시작
루프(`Restarting`)에 빠져 있었고(2026-07-21 재검토 세션에서 처음 진단, `md/ai-reviewer-briefing.md`
§7.2 참고), 이번 세션에서 실제로 고쳤다.

| # | 증상 | 원인 | 조치 |
|---|---|---|---|
| 1 | `app` 컨테이너가 즉시 죽음(`IllegalStateException`) | `docker-compose.yml`의 `app.environment`에 `ENVELOPE_KEK` 미배선 | `environment`에 `ENVELOPE_KEK: ${ENVELOPE_KEK}` 추가 |
| 2 | Flyway 초기화 단계에서 `Public Key Retrieval is not allowed` | MySQL 8.0 컨테이너의 `caching_sha2_password` 인증 플러그인 + JDBC URL에 `allowPublicKeyRetrieval=true` 부재 | `application.yml`의 datasource URL에 `&allowPublicKeyRetrieval=true` 추가 |
| 3 | 위 두 개를 고친 뒤에도 `app`이 계속 재시작 — Hibernate `SchemaManagementException: Schema-validation: missing table [access_policy_rules]` | 이번 세션에 실기동으로 **새로 발견**: 로컬 native Windows MySQL은 테이블명을 대소문자 구분 없이 다루지만(`lower_case_table_names` 플랫폼 기본값), 리눅스 컨테이너의 MySQL 기본값(0, 대소문자 구분)에서는 `@Table(name="ACCESS_POLICY_RULES")` 같은 대문자 테이블명이 Hibernate `SpringPhysicalNamingStrategy`가 소문자로 정규화한 이름(`access_policy_rules`)과 실제로 일치하지 않아 스키마 검증이 실패함 | `mysql` 서비스에 `command: --lower-case-table-names=1` 추가해 로컬과 동일하게 대소문자 비구분으로 맞춤 |

검증 후 확인한 것:
- `GET http://localhost:8080/actuator/health` → `200 {"status":"UP"}` (컨테이너 내부 직접 접근)
- `GET http://localhost/login` → `200`, `GET http://localhost/` → `200` (nginx 리버스 프록시 경유)
- Prometheus `http://localhost:9090/api/v1/targets` → `job="hospital-ops-app"` 타겟 `health:"up"`
  (`app:8080/actuator/prometheus` 정상 스크레이프)
- Grafana `http://localhost:3000/api/datasources` → `Prometheus` datasource가 `http://prometheus:9090`으로
  자동 프로비저닝됨
- `docker compose logs app`에 KEK/DB 관련 에러 없음(Flyway `Successfully validated 17 migrations`,
  `Schema is up to date`)
- 검증 후 `docker compose down -v`로 컨테이너/볼륨 전량 정리, 로컬 native MySQL 재기동 및
  `PATIENT` 12건 데이터 무결성 확인 완료(`md/ai-reviewer-briefing.md` §7.2 참고)

**참고**: `allowPublicKeyRetrieval=true`는 운영 환경에서는 통상 권장되지 않는 옵션이지만, 이
프로젝트는 실환자 데이터가 없는 로컬/폐쇄망 Docker Compose 시연 전제이므로 트레이드오프로
선택했다(대안인 `mysql_native_password` 플러그인 전환이나 SSL 활성화는 추가 초기화 스크립트·
인증서 관리 복잡도를 더하는 데 비해 이 프로젝트 성격에서 얻는 이득이 크지 않다고 판단).
