<#
.SYNOPSIS
    Synthea 합성 환자 데이터를 소량 생성한다 (Phase 0 Step 0.5).

.DESCRIPTION
    이 스크립트는 Synthea(https://github.com/synthetichealth/synthea, Apache License 2.0)의
    "with-dependencies" 실행 가능 jar를 사용해 완전 합성(가상) 환자 데이터를 생성한다.
    실환자 데이터는 이 프로젝트에서 사용하지 않는다는 원칙(README.md 참조)에 따라,
    Phase 1 이후 레거시 HIS 스키마 적재에 사용할 데이터 소스로 Synthea를 채택했다.

    동작:
      1. scripts/vendor/synthea-with-dependencies.jar 가 없으면 GitHub Releases에서 다운로드한다.
         (이 jar는 대용량이라 git에 커밋하지 않는다 — .gitignore의 /scripts/vendor/ 규칙 참조.
          재현 가능성은 이 스크립트의 다운로드 로직으로 보장한다.)
      2. java -jar 로 Synthea를 실행해 population 명(기본 10명)만큼 지정 State/City의
         합성 환자 데이터를 생성한다.
      3. 산출물은 저장소 루트 기준 /data/synthetic/ (기본 FHIR R4 Bundle JSON) 에 쓰인다.
         -IncludeCsv 스위치를 주면 CSV 익스포트도 함께 생성한다.
         산출물 자체도 git에 커밋하지 않는다(.gitignore의 /data/ 규칙 참조) — 대량이기 때문.
         acceptance criteria 증빙용 샘플은 /docs/synthea-notes.md 및 /docs/samples/ 에 별도 보관한다.

.PARAMETER PopulationSize
    생성할 합성 환자 수. 기본 10 (소량 시연 목적).

.PARAMETER State
    Synthea가 지리 데이터를 참조할 미국 주(state) 이름. 기본 "Massachusetts"
    (Synthea 기본 지리 모듈이 가장 잘 검증된 지역).

.PARAMETER City
    선택적 도시 이름. 지정하지 않으면 State 전체에서 무작위 도시를 사용한다.

.PARAMETER Seed
    선택적 난수 시드. 지정하면 동일 시드로 재현 가능한 동일 인구 집단을 생성한다.

.PARAMETER IncludeCsv
    지정하면 FHIR Bundle JSON 외에 CSV 익스포트도 함께 생성한다
    (--exporter.csv.export=true).

.PARAMETER OutputDir
    산출물 출력 디렉터리. 기본값은 저장소 루트의 /data/synthetic (스크립트 위치 기준 상대 경로로 계산).

.PARAMETER JarVersion
    다운로드할 Synthea 릴리스 태그. 기본 "v4.0.0"(2026-03-05 기준 최신 정식 릴리스,
    master-branch-latest 롤링 빌드 대신 고정 태그를 사용해 재현성을 보장한다).

.PARAMETER ForceDownload
    이미 jar가 있어도 강제로 다시 다운로드한다.

.EXAMPLE
    ./scripts/gen-synthetic-data.ps1
    기본값(10명, Massachusetts, FHIR Bundle JSON만)으로 생성.

.EXAMPLE
    ./scripts/gen-synthetic-data.ps1 -PopulationSize 20 -State Massachusetts -City Bedford -IncludeCsv -Seed 12345
    Massachusetts주 Bedford시 20명을 시드 고정하여 생성하고 CSV도 함께 출력.
#>
[CmdletBinding()]
param(
    [int]$PopulationSize = 10,
    [string]$State = "Massachusetts",
    [string]$City = "",
    [int]$Seed = 0,
    [switch]$IncludeCsv,
    [string]$OutputDir = "",
    [string]$JarVersion = "v4.0.0",
    [switch]$ForceDownload
)

# PowerShell의 [Nullable[int]] 파라미터는 커맨드라인 바인딩 시 .HasValue/.Value가
# 신뢨성 있게 채워지지 않는 것으로 확인됨(테스트 결과 항상 비어 있음).
# 대신 $PSBoundParameters로 "-Seed가 실제로 지정됐는지"를 판별한다.
$SeedSpecified = $PSBoundParameters.ContainsKey('Seed')

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# --- 경로 계산 (저장소 루트 = 이 스크립트의 부모의 부모 디렉터리) ---
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$vendorDir = Join-Path $scriptDir "vendor"
$jarPath = Join-Path $vendorDir "synthea-with-dependencies.jar"

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $repoRoot "data\synthetic"
}

# --- Java 탐색 ---
function Resolve-JavaExe {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $candidates = @(
        "C:\Program Files\Eclipse Adoptium\jdk-17*\bin\java.exe",
        "C:\Program Files\Java\jdk-17*\bin\java.exe"
    )
    foreach ($pattern in $candidates) {
        $found = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }

    throw "java 실행 파일을 찾을 수 없다. Java 17 이상을 설치하거나 JAVA_HOME/PATH를 설정하라."
}

$javaExe = Resolve-JavaExe
Write-Host "[gen-synthetic-data] java: $javaExe"

# --- Synthea jar 확보 (없으면 다운로드) ---
if (-not (Test-Path $vendorDir)) {
    New-Item -ItemType Directory -Force -Path $vendorDir | Out-Null
}

if ($ForceDownload -or -not (Test-Path $jarPath)) {
    $downloadUrl = "https://github.com/synthetichealth/synthea/releases/download/$JarVersion/synthea-with-dependencies.jar"
    Write-Host "[gen-synthetic-data] Synthea jar 다운로드 중: $downloadUrl"
    Invoke-WebRequest -Uri $downloadUrl -OutFile $jarPath
    Write-Host "[gen-synthetic-data] 다운로드 완료: $jarPath ($([math]::Round((Get-Item $jarPath).Length / 1MB, 1)) MB)"
} else {
    Write-Host "[gen-synthetic-data] 기존 jar 사용: $jarPath ($([math]::Round((Get-Item $jarPath).Length / 1MB, 1)) MB)"
}

# --- 출력 디렉터리 준비 ---
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}
$outputDirResolved = (Resolve-Path $OutputDir).Path
Write-Host "[gen-synthetic-data] 출력 디렉터리: $outputDirResolved"

# --- Synthea 실행 인자 구성 ---
# Synthea는 --exporter.baseDirectory 경로를 뒤에 슬래시가 오도록 두는 관례를 따른다(공식 예시 참고).
$baseDirArg = $outputDirResolved.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar

$javaArgs = @("-jar", $jarPath)
$javaArgs += "-p"
$javaArgs += "$PopulationSize"
if ($SeedSpecified) {
    $javaArgs += "-s"
    $javaArgs += "$Seed"
}
$javaArgs += "--exporter.baseDirectory=$baseDirArg"
$javaArgs += "--exporter.fhir.export=true"
if ($IncludeCsv) {
    $javaArgs += "--exporter.csv.export=true"
}
$javaArgs += $State
if (-not [string]::IsNullOrWhiteSpace($City)) {
    $javaArgs += $City
}

Write-Host "[gen-synthetic-data] 실행: java $($javaArgs -join ' ')"
Write-Host "[gen-synthetic-data] (population=$PopulationSize state=$State city=$City csv=$($IncludeCsv.IsPresent))"

& $javaExe @javaArgs
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    throw "Synthea 실행이 종료 코드 $exitCode 로 실패했다."
}

Write-Host ""
Write-Host "[gen-synthetic-data] 완료. 산출물 위치: $outputDirResolved"
Get-ChildItem -Path $outputDirResolved -Recurse -File | ForEach-Object {
    Write-Host ("  {0,10:N0} bytes  {1}" -f $_.Length, $_.FullName.Substring($repoRoot.Length + 1))
}
