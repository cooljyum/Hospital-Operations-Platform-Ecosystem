# 로컬 개발용 MySQL(Docker 미사용) 기동 스크립트.
# Docker Desktop/WSL2가 준비되기 전까지, winget으로 설치한 MySQL 8.4를
# Windows 서비스 대신 일반 프로세스로 띄운다(서비스 등록은 관리자 권한 필요).
# 자세한 배경은 docs/local-dev-mysql.md 참조.

$ErrorActionPreference = "Stop"

$mysqlBin = "C:\Program Files\MySQL\MySQL Server 8.4\bin"
$iniPath = "C:\ProgramData\MySQL\MySQL Server 8.4\my.ini"

if (-not (Test-Path $mysqlBin)) {
    throw "MySQL이 설치되어 있지 않습니다: $mysqlBin (winget install --id Oracle.MySQL)"
}

$existing = Get-NetTCPConnection -LocalPort 3306 -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "MySQL이 이미 3306 포트에서 실행 중입니다."
    exit 0
}

Start-Process -FilePath "$mysqlBin\mysqld.exe" -ArgumentList "--defaults-file=`"$iniPath`"" -WindowStyle Hidden
Write-Host "MySQL을 백그라운드 프로세스로 기동했습니다. 잠시 후 'mysql -u hospital_ops -pchangeme hospital_ops'로 접속을 확인하세요."
