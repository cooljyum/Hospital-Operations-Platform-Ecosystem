# Hospital-Operations-Platform-Ecosystem
합성 병원 데이터를 FHIR R4로 연계하는 미니 시스템. 기능보다 운영 증빙(장애 대응 로그북, SQL 튜닝, 백업·복구 리허설, 감사 로그)이 핵심인 병원 전산직 지원용 포트폴리오.

## 프로젝트 구조

| 경로 | 내용 |
|---|---|
| `/app` | Spring Boot 3.x + Java 17 애플리케이션(Gradle) |
| `/docker` | Docker Compose 기반 로컬 인프라(MySQL, app, Prometheus, Grafana, Nginx) |
| `/docs` | 설계·계획·운영 증빙 문서 (`docs/DESIGN.md`, `docs/planning/`, `docs/agents/`, `docs/incidents/`, `docs/runbooks/`, `docs/tuning/` 등) |
| `/scripts` | 합성 데이터 생성 등 운영 스크립트 |

전체 실행 계획은 [`docs/planning/PLAN.md`](docs/planning/PLAN.md) 참조.
