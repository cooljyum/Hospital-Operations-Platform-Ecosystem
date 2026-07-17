# /docker

Docker Compose 기반 로컬 인프라 정의. Phase 0.4에서 골격이 추가됐다.

## 구성 서비스 (`docker-compose.yml`)

| 서비스 | 이미지 | 역할 |
|---|---|---|
| `mysql` | `mysql:8.0` | 레거시 HIS 스키마가 올라갈 정본 DB. `mysql-data` named volume으로 영속화, `mysqladmin ping` healthcheck. |
| `app` | `../app/Dockerfile`로 빌드 | Spring Boot 애플리케이션. `mysql`이 healthy가 된 뒤에만 기동(`depends_on: condition: service_healthy`). 현재 healthcheck는 `/login`(Spring Security 기본 로그인 페이지)을 임시로 쓰며, Phase 7.1에서 Actuator 도입 후 `/actuator/health`로 교체 예정. |
| `prometheus` | `prom/prometheus:latest` | `prometheus/prometheus.yml`을 마운트해 `app:8080/actuator/prometheus`를 스크레이프(Actuator 도입 전까지는 타겟 DOWN이 정상). |
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
cp docker/.env.example docker/.env   # 실제 비밀번호 값으로 채운 뒤 사용
cd docker
docker compose up -d
```

## ⚠️ 이 저장소가 작업된 머신에서의 검증 상태

**이 머신에서는 아직 실제 기동 검증을 하지 못했다.** Docker Desktop은 설치되어 있으나 WSL2가
설치돼 있지 않고(설치에는 관리자 권한이 필요), 이 작업 세션은 비관리자 권한으로 실행돼 WSL2를
설치할 수 없었다. 그 결과 `docker`/`docker compose` 명령 자체가 PATH에 없어 다음이 미검증 상태다:

- `docker compose up`으로 5개 컨테이너가 실제로 기동하는지
- `app`이 `mysql`에 연결에 성공해 healthcheck가 200(또는 `/login` 200)을 반환하는지
- `nginx` 리버스 프록시, `prometheus` 스크레이프, `grafana` datasource 프로비저닝이 실제로 동작하는지

컴포즈 파일과 참조 경로(Dockerfile, nginx.conf, prometheus.yml, grafana provisioning)의 존재 여부,
YAML 문법 유효성은 `python -c "import yaml; yaml.safe_load(...)"`로 점검했다(자세한 내역은 Step
0.4 커밋 메시지 참고).

**사용자가 나중에 WSL2 + Docker Desktop을 준비한 뒤**, 이 디렉터리에서 위 "사용법"대로
`docker compose up -d`를 직접 실행해 기동 여부를 확인할 수 있다. 문제가 있다면(예: healthcheck
실패, 포트 충돌) `docker compose logs <서비스명>`으로 원인을 확인한다.
