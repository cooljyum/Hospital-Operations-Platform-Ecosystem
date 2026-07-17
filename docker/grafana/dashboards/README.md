# /docker/grafana/dashboards

Grafana 대시보드 JSON 산출물 위치 (PLAN.md Phase 7.2, `/docker/grafana/dashboards/*.json`).

이 폴더는 `docker/grafana/provisioning/dashboards/dashboard-provider.yml`이 파일 프로바이더로
바라보는 경로(`/var/lib/grafana/dashboards`)에 마운트된다(`docker/docker-compose.yml`의
`grafana` 서비스 volumes 참고). Phase 7.2에서 대시보드 3종 이상(배치 상태·장애 알림·break-glass
알림)을 이 폴더에 JSON 파일로 추가하면 Grafana 기동 시 자동 로드된다.

Phase 0.4 시점에는 빈 폴더이며, git이 빈 디렉터리를 추적하지 않으므로 이 README가 자리 표시자
역할을 겸한다.
