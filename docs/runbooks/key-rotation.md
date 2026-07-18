# Runbook: KEK(Key Encryption Key) 회전

> 대상: `com.hospitalops.crypto.EnvelopeCrypto`(Phase 4 Step 4.2, AES-256-GCM 엔벨로프
> 암호화)가 사용하는 KEK. 이 문서는 정기 회전과 긴급(유출 의심) 회전 절차를 모두 다룬다.

## 0. 배경 — 왜 엔벨로프 암호화라 회전이 싸게 끝나는가

`EnvelopeCrypto`는 데이터를 KEK로 직접 암호화하지 않는다. 매 암호화 호출마다:

1. 무작위 DEK(Data Encryption Key, 32바이트)를 새로 생성한다.
2. 그 DEK로 실제 평문을 AES-256-GCM 암호화한다 (`ciphertext` + `dataIv`).
3. DEK 자체를 KEK로 다시 AES-256-GCM 암호화("래핑")한다 (`wrappedDek` + `wrapIv`).
4. `ciphertext`/`dataIv`/`wrappedDek`/`wrapIv` 네 값을 함께 저장한다.

KEK를 회전할 때 **실제 대용량 암호문(`ciphertext`)은 다시 암호화할 필요가 없다** — 각
레코드의 `wrappedDek`만 "옛 KEK로 언래핑 -> 새 KEK로 재래핑"하면 된다. 이것이 엔벨로프
암호화를 쓰는 이유다(대량 복호 재암호화 비용을 DEK 하나 크기로 줄임).

## 1. KEK 공급 방식

| 환경 | 공급 방법 |
|---|---|
| 운영(Docker Compose/추후 오케스트레이션) | Docker secret으로 파일 마운트 후, 엔트리포인트 스크립트가 그 파일 내용을 `ENVELOPE_KEK` 환경변수로 읽어 앱에 전달한다 (`app.crypto.envelope.kek: ${ENVELOPE_KEK:}`, `application.yml`). |
| 로컬 개발 | Docker secret 인프라가 없으므로 `ENVELOPE_KEK` 환경변수를 직접 셸에서 설정하고 기동한다. 예: PowerShell `$env:ENVELOPE_KEK = "<Base64 32바이트>"`. 값을 코드/설정 파일에 커밋하지 않는다. |
| 테스트(`./gradlew test`) | `app/src/test/resources/application.yml`에 테스트 전용 고정 KEK를 하나 박아 둔다 — 운영 키가 아니며, 매 테스트 실행마다 재사용 가능한 값이다. |

KEK는 반드시 **Base64로 인코딩된 32바이트(AES-256) 값**이어야 한다. `ENVELOPE_KEK`가
비어 있거나, Base64가 아니거나, 디코딩 후 32바이트가 아니면 `EnvelopeCrypto` 빈 생성이
즉시 실패하고 애플리케이션 컨텍스트 기동 자체가 중단된다(의도된 fail-fast 동작 — KEK
없이 반쪽으로 뜨는 상태를 허용하지 않는다).

## 2. 정기 회전 절차 (계획된 회전)

권장 주기: 90일(또는 조직 보안 정책에 맞춰 조정). 아래 순서를 지킨다 — **순서를 바꾸면
서비스 중단 또는 기존 암호문 복호 불가로 이어질 수 있다.**

1. **새 KEK 생성**: 32바이트 무작위 값을 생성해 Base64로 인코딩한다.
   ```powershell
   $bytes = New-Object byte[] 32
   (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($bytes)
   [Convert]::ToBase64String($bytes)
   ```
2. **새 KEK를 별도 슬롯에 등록**: Docker secret(운영) 또는 안전한 시크릿 저장소에 새
   KEK를 "차기 KEK"로 등록한다. 이 시점에는 아직 애플리케이션에 적용하지 않는다 —
   **옛 KEK와 새 KEK가 동시에 존재하는 과도기(dual-key window)**를 짧게 둔다.
3. **일괄 재래핑(rewrap) 배치 실행**: 대량 복호 승인 워크플로(Phase 4 Step 4.5,
   `com.hospitalops.approval` 패키지)와 동일한 승인 절차를 거쳐, 저장된 모든
   `wrappedDek`/`wrapIv`를 대상으로:
   - 옛 KEK로 `EnvelopeCrypto.decrypt`에 준하는 방식으로 DEK를 언래핑한다.
   - 새 KEK로 그 DEK를 다시 래핑한다(`wrappedDek`/`wrapIv` 갱신, `ciphertext`/`dataIv`는
     변경 없음).
   - 재래핑은 반드시 감사 로그(`AUDIT_LOG`)에 별도 행위(`KEK_ROTATION_REWRAP` 등)로
     기록한다.
4. **애플리케이션에 새 KEK 적용**: `ENVELOPE_KEK`(또는 Docker secret 마운트 내용)를
   새 KEK로 교체하고 애플리케이션을 재기동한다.
5. **검증**: 재기동 직후 표본 레코드 몇 건을 실제로 복호화해 라운드트립이 성공하는지
   확인한다. 실패 시 즉시 옛 KEK로 롤백(4번 되돌리기)하고 3번 재래핑 배치를 재검토한다.
6. **옛 KEK 폐기**: 검증이 끝나고 그레이스 기간(예: 7일, 롤백 필요성 대비)이 지나면
   옛 KEK를 시크릿 저장소에서 완전히 제거한다. 이 시점 이후로는 옛 KEK로 언래핑이
   불가능해지므로, 3번 재래핑 배치가 **전체 레코드**를 빠짐없이 처리했는지 사전에
   재확인한다(누락된 행이 있으면 이 시점 이후 영구히 복호 불가).

## 3. 긴급 회전 절차 (KEK 유출 의심)

정상 절차의 "그레이스 기간"을 생략하고 즉시 조치한다.

1. 유출 의심 KEK를 **즉시 무효화**할 수 있는 경로가 있다면 발동한다(시크릿 저장소
   접근 로그 확인, 관련 자격증명 강제 로테이션 포함).
2. 새 KEK를 즉시 생성(§2.1과 동일)하고, 재래핑 배치(§2.3)를 최우선 순위로 즉시 실행한다.
   이 구간 동안 신규 암호화 요청은 가능한 한 큐잉하거나 지연시켜, 재래핑 중 새로 생성된
   데이터가 옛 KEK로 래핑되는 창을 최소화한다.
3. 애플리케이션에 새 KEK 적용 후 재기동(§2.4).
4. 유출 의심 KEK는 재래핑 완료를 확인하는 즉시(그레이스 기간 없이) 폐기한다.
5. 사고 대응 로그북(Phase 8.1, `docs/incidents/`)에 발생 시각, 탐지 경위, 조치 시각,
   재래핑 대상 건수, 완료 시각을 기록한다.

## 4. 체크리스트 요약

- [ ] 새 KEK는 32바이트 무작위 값을 Base64 인코딩해 생성했는가?
- [ ] 재래핑 배치가 실행되기 **전**에 애플리케이션의 `ENVELOPE_KEK`를 바꾸지 않았는가?
- [ ] 재래핑이 전체 레코드를 빠짐없이 처리했음을 카운트로 확인했는가?
- [ ] 재래핑 각 건이 AUDIT_LOG에 기록됐는가?
- [ ] 표본 복호화 검증을 재기동 직후 수행했는가?
- [ ] (정기 회전) 그레이스 기간 이후에만 옛 KEK를 폐기했는가?
- [ ] (긴급 회전) 사고 대응 로그북에 기록했는가?
