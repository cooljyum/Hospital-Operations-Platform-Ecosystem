# 로컬 개발용 MySQL (Docker 미사용 임시 경로)

Phase 0.4에서 만든 `docker/docker-compose.yml`이 이 프로젝트의 정식 실행 방식이다. 하지만 이
저장소가 작업된 개발 머신에는 Docker Desktop 실행에 필요한 WSL2가 아직 설치되어 있지 않고(관리자
권한 필요), Phase 1부터는 실제 MySQL이 있어야 Flyway 마이그레이션·JPA 작업을 진행할 수 있다.
그래서 Docker/WSL2가 준비되기 전까지 임시로 **MySQL 8.4를 이 머신에 직접 설치**해 로컬 개발에
쓴다.

## 설치 방식

- `winget install --id Oracle.MySQL` (MySQL Server 8.4.9, Community Edition).
- 관리자 권한이 없어 **Windows 서비스로 등록하지 못했다** (`mysqld --install`이 "Install/Remove
  of the Service Denied" 로 실패). 대신 `mysqld.exe`를 일반 프로세스로 직접 실행한다
  (`scripts/start-local-mysql.ps1`). 이 프로세스는 세션/재부팅 시 죽으므로 그때마다
  스크립트로 다시 띄워야 한다.
- 데이터 디렉터리: `C:\ProgramData\MySQL\MySQL Server 8.4\Data` (저장소 밖, 머신 전역).
- 설정 파일: `C:\ProgramData\MySQL\MySQL Server 8.4\my.ini` (`basedir`/`datadir`/`port=3306`만
  지정).

## 계정 정보 (로컬 전용, 실제 비밀값 아님)

`docker/.env.example`의 네이밍과 동일하게 맞췄다 — `app/src/main/resources/application.yml`의
기본값(`DB_NAME=hospital_ops`, `DB_USERNAME=hospital_ops`, `DB_PASSWORD=changeme`)과 정확히
일치하므로, 환경변수를 따로 안 잡아도 로컬에서 바로 붙는다.

| 계정 | 용도 | 비밀번호 |
|---|---|---|
| `root`@`localhost` | 관리용 | `LocalDevRoot!2026` |
| `hospital_ops`@`localhost` | 앱 접속용 | `changeme` (application.yml 기본값과 동일) |

DB: `hospital_ops` (utf8mb4 / utf8mb4_0900_ai_ci).

## 재기동

```powershell
.\scripts\start-local-mysql.ps1
```

이미 3306 포트에서 떠 있으면 아무 것도 하지 않는다.

## Docker/WSL2 준비되면

WSL2·Docker Desktop이 준비되면 이 로컬 MySQL은 정리하고(`Stop-Process -Name mysqld`,
필요시 `C:\Program Files\MySQL` 제거) `docker/docker-compose.yml`의 컨테이너 MySQL로 전환한다.
그 시점에 Phase 0.4·Phase 1 acceptance criteria(컨테이너 기동·헬스체크)를 재검증해야 한다.
