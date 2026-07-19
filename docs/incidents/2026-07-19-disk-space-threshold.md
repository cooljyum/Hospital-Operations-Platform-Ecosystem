# 장애 재현 로그 3: 디스크 여유공간 임계치 근접

> Phase 8 Step 8.1. 개발 워크스테이션의 실제 디스크 여유공간은 약 50%로,
> 프로덕션 권고 임계치(15% 미만)까지 실제로 채우는 것은 수백 GB를 소모해야 해
> 비현실적이고 공유 환경에서 위험하다(디스크가 사용자의 실제 개발 머신).
> 그래서 이 시나리오는 "임계값 일시 조정 테스트"로 재현했다 - Grafana 알림
> 파이프라인(Prometheus 쿼리 -> 조건 평가 -> Alerting 상태 전이 -> API로 조회)
> 자체가 **실제 라이브 메트릭**으로 정상 동작하는지를 검증하는 것이 목적이다.
> 이 방식은 임계값만 테스트용으로 바꿨을 뿐, 관측되는 disk_free_bytes/
> disk_total_bytes 값 자체는 완전히 실측치다(가짜 데이터 주입 없음).

## 0. 사용한 메트릭

Spring Boot Actuator의 표준 Micrometer 메트릭(추가 코드 없이 기본 노출):

```
# HELP disk_free_bytes Usable space for path
disk_free_bytes{path="C:\Users\...\Hospital-Operations-Platform-Ecosystem\app\."} 2.58260504576E11
# HELP disk_total_bytes Total space for path
disk_total_bytes{path="C:\Users\...\Hospital-Operations-Platform-Ecosystem\app\."} 5.10995197952E11
```

## 1. 실측 디스크 상태

```powershell
PS> Get-PSDrive C | Select-Object Used,Free,...
UsedGB  : 235.34
FreeGB  : 240.56
TotalGB : 475.9
```

Prometheus 쿼리 `disk_free_bytes/disk_total_bytes` 실측값: **0.505406678628435**
(여유공간 약 50.5%). 프로덕션 임계치(15% 미만)에는 한참 못 미친다(정상 상태).

## 2. 재현 절차 (임계값 일시 조정 테스트)

### 2.1 프로덕션 규칙 확인 - 정상 상태에서는 발동하지 않음

`docker/grafana/provisioning/alerting/incident-scenarios-alerting.yml`의
`disk-space-low-alert` 규칙(임계값 `< 0.15`)을 provisioning한 상태에서:

```powershell
Invoke-RestMethod http://localhost:3000/api/alertmanager/grafana/api/v2/alerts ...
# => No active alerts (production threshold correctly not firing on healthy disk)
```

### 2.2 임계값을 실측 여유비율이 교차하는 값(0.55)으로 일시 변경

```yaml
# (테스트 중 일시 변경분 - 커밋에는 포함되지 않음, 최종적으로 0.15로 복원)
conditions:
  - evaluator:
      type: lt
      params: [0.55]   # 실측 0.5054 < 0.55 이므로 교차
```

```powershell
cd docker
docker compose restart grafana
Start-Sleep -Seconds 15
Invoke-RestMethod http://localhost:3000/api/alertmanager/grafana/api/v2/alerts ...
```

## 3. 증상 / Grafana 알림 연동 (실제 firing, API 원문)

```json
[
  {
    "annotations": {
      "__value_string__": "[ var='A' ... value=0.505406678628435 ], [ var='B' ... value=0.505406678628435 ], [ var='C' ... type='threshold' value=1 ]",
      "__values__": "{\"A\":0.505406678628435,\"B\":0.505406678628435,\"C\":1}",
      "summary": "디스크 여유 공간이 임계치(55% - 재현 테스트용 임시값) 미만입니다."
    },
    "startsAt": "2026-07-19T14:50:50Z",
    "status": { "state": "active" },
    "labels": {
      "alertname": "Disk free space below threshold",
      "severity": "critical",
      "category": "infrastructure"
    }
  }
]
```

(startsAt UTC 14:50:50 = KST 23:50:50, §2.2 재현 직후. `A` 값 0.505406678628435는
§1의 실측 여유비율과 정확히 일치 - 알림 조건 평가가 실제 라이브 Prometheus 값을
정확히 사용하고 있음을 확인.)

## 4. 대응 조치 및 복구 (임계값 원복)

```yaml
conditions:
  - evaluator:
      type: lt
      params: [0.15]   # 프로덕션 값으로 복원, 커밋된 최종 상태
```

```powershell
cd docker
docker compose restart grafana
Start-Sleep -Seconds 15
Invoke-RestMethod http://localhost:3000/api/alertmanager/grafana/api/v2/alerts ...
# => No active alerts (production threshold correctly not firing on healthy disk)
```

재확인 시점 기록(사후 재점검):

```
TIMESTAMP (KST): 2026-07-20 00:06:31
[] (no active alerts)
```

**복구 시각**: 임계값을 0.15로 원복하고 Grafana를 재기동해 "no active
alerts"를 최초로 확인한 시점 - §2.2(23:50:50 firing 확인) 직후, §3(23:52:08
MySQL 다운 시나리오 시작) 이전 사이에 수행됐다(정확한 초 단위 재기동 로그는
남기지 않았으나 두 타임스탬프로 상한/하한이 확정된다). 위 `00:06:31` 재확인은
그 정상(비발동) 상태가 이후에도 유지되고 있음을 재검증한 것이다. 실제 디스크
상태는 처음부터 정상이었으므로("장애"가 물리적으로 존재한 적이 없음) 이
시나리오의 "복구"는 테스트 임계값 원복을 의미한다.

## 5. 결론 및 커밋된 최종 상태

- `docker/grafana/provisioning/alerting/incident-scenarios-alerting.yml`에는
  **프로덕션 임계치 0.15가 최종적으로 커밋**되어 있다(§2.2/§4의 0.55는 이
  문서에만 기록된 일회성 테스트 값이며 커밋 이력에 남지 않는다).
- 알림 파이프라인(Prometheus 쿼리 평가 -> Grafana Alerting 상태 전이 -> HTTP
  API 조회)이 실제 라이브 데이터로 정상 동작함을 확인했으므로, 실제 디스크가
  15% 미만으로 떨어지는 상황이 오면 이 규칙이 정상적으로 발동할 것으로 신뢰할
  수 있다.
- 물리적으로 디스크를 15%까지 채우는 재현은 이 워크스테이션 환경에서는
  실익보다 리스크(수백 GB 소모, 다른 프로세스/OS 영향)가 커서 수행하지
  않았다 - 이 판단과 근거를 명시적으로 문서화해 둔다.
