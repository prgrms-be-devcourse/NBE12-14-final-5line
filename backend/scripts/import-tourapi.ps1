param(
    [ValidateSet('importTourPlaces', 'importPlaceOperatingInfo', 'comparePlaceOperatingInfo')][string]$Task = 'importTourPlaces',
    [string]$ReportPath,
    [ValidateRange(1, 500)][int]$Limit = 20,
    [ValidateRange(0, [long]::MaxValue)][long]$AfterPlaceId = 0,
    [switch]$SaveCandidates
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw '프로젝트 루트에 .env 파일을 만들어 주세요.' }

# 필요한 값만 현재 프로세스로 읽는다. 키와 비밀번호를 화면/명령 인자에 출력하지 않는다.
$allowed = @('TOUR_API_KEY', 'MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_DATABASE', 'MYSQL_USER', 'MYSQL_PASSWORD')
foreach ($line in [IO.File]::ReadAllLines($envFile)) {
    if ($line -match '^\s*([A-Z_]+)\s*=(.*)$' -and $allowed -contains $Matches[1]) {
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}
foreach ($name in @('TOUR_API_KEY', 'MYSQL_DATABASE', 'MYSQL_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw ".env에 $name 값을 설정해 주세요."
    }
}
if ($Task -eq 'comparePlaceOperatingInfo') {
    if ([string]::IsNullOrWhiteSpace($ReportPath)) { throw '새 보고서 파일 경로를 -ReportPath로 지정해 주세요.' }
    $env:TOUR_OPERATING_REPORT_PATH = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ReportPath)
    $env:TOUR_OPERATING_REPORT_LIMIT = "$Limit"
    $env:TOUR_OPERATING_REPORT_AFTER_ID = "$AfterPlaceId"
    $env:TOUR_OPERATING_REPORT_SAVE_CANDIDATES = "$($SaveCandidates.IsPresent)"
}
& (Join-Path $projectRoot 'backend/gradlew.bat') -p (Join-Path $projectRoot 'backend') $Task --no-daemon
exit $LASTEXITCODE
