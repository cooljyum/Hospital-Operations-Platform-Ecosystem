# /docker/grafana/dashboards

Grafana 대시보드 JSON 산출물 위치 (PLAN.md Phase 7.2, `/docker/grafana/dashboards/*.json`).

이 폴더는 `docker/grafana/provisioning/dashboards/dashboard-provider.yml`이 파일 프로바이더로
바라보는 경로(`/var/lib/grafana/dashboards`)에 마운트된다(`docker/docker-compose.yml`의
`grafana` 서비스 volumes 참고). Grafana 기동 시 아래 대시보드가 자동 로드된다.

Phase 7.2에서 추가된 대시보드 3종:

- `batch-job-status.json` — 배치 job(fhirSyncJob/summaryRefreshJob) 성공/실패 카운트·비율
  (`hospitalops_batch_job_runs_total`).
- `breakglass-access.json` — break-glass 응급 접근 승인 발생 추이
  (`hospitalops_breakglass_grants_total`). 짝을 이루는 알림 규칙은
  `docker/grafana/provisioning/alerting/breakglass-alerting.yml`에 있다.
- `app-health-jvm.json` — 표준 Micrometer/JVM 메트릭 기반 앱 헬스(가동시간, 힙 메모리, HTTP 요청량).
