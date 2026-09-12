#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Read Android vitals from the Play Developer Reporting API - read-only, safe to run at any time (S2917).

.DESCRIPTION
    Wraps scripts/release/read-play-vitals.py in the project virtual environment. The Python half makes
    only get/query/search/list calls on the read-only Reporting service, with the same service-account
    key the release scripts use, and prints one snapshot (schema 1) of crash, ANR and LMK rates, both
    February-2027 memory sets, the top grouped error issues and Google's own anomalies over a DAILY
    window in America/Los_Angeles.

    The Reporting API is a separate Google service from the publishing API: it has to be enabled on the
    service account's Cloud project, and the account needs the Play Console permission "View app
    information and download bulk reports (read-only)". When either is missing the reader exits 2 and
    names the one thing to do - the activation URL, or the permission.

    Consumed by scripts/release/watch-play-vitals.ps1, which turns the snapshot into a verdict, the
    measured records and, on a red band, a Draft ticket.

.PARAMETER Json
    Emit the snapshot JSON unchanged, for a caller that parses it. Default output is a short table.

.PARAMETER Package
    Application id to read. Defaults to com.sza.fastmediasorter.

.PARAMETER Days
    Length of the DAILY window in days, ending at the freshest date the API reports. Defaults to 28,
    the window Google judges bad behaviour over.

.PARAMETER Fixture
    Read the raw API responses from this JSON file instead of calling the API. Used by the tests.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/read-play-vitals.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/release/read-play-vitals.ps1 -Json

.NOTES
    Exit codes:
      0 - every call succeeded and the snapshot was printed
      2 - could not verify: no virtual environment, no key, the service disabled or refused, a fixture
          unreadable, or output that is not JSON. Nothing is printed on stdout in that case.
#>
[CmdletBinding()]
param(
    [switch] $Json,
    [string] $Package = 'com.sza.fastmediasorter',
    [int] $Days = 28,
    [string] $Fixture
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$venvPython = Join-Path $repoRoot '.venv\Scripts\python.exe'
$pyScript = Join-Path $PSScriptRoot 'read-play-vitals.py'

if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Error "read-play-vitals: virtual environment not found at $venvPython." -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $pyScript)) {
    Write-Error "read-play-vitals: reader not found at $pyScript." -ErrorAction Continue
    exit 2
}

$pyArgs = @($pyScript, '--package', $Package, '--days', $Days)
if ($Fixture) {
    $pyArgs += @('--fixture', (Resolve-Path -LiteralPath $Fixture).Path)
}

Push-Location $repoRoot
try {
    $raw = & $venvPython @pyArgs
    $pyExit = $LASTEXITCODE
}
finally {
    Pop-Location
}

if ($pyExit -ne 0) {
    Write-Error 'read-play-vitals: could not read Android vitals - see the message above.' -ErrorAction Continue
    exit 2
}

try {
    $snapshot = ($raw -join [Environment]::NewLine) | ConvertFrom-Json
}
catch {
    Write-Error "read-play-vitals: reader returned output that is not JSON - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

if ($Json) {
    $raw | ForEach-Object { Write-Output $_ }
    exit 0
}

function Get-FreshestRow {
    param($Rows)
    return @($Rows) | Sort-Object -Property date | Select-Object -Last 1
}

$window = $snapshot.window
Write-Host "Android vitals for $($snapshot.package): $($window.startDate) .. $($window.endDate) ($($window.timeZone), $($window.days) days)" -ForegroundColor Cyan
foreach ($pair in @(@('crashRate', 'userPerceivedCrashRate28dUserWeighted'), @('anrRate', 'userPerceivedAnrRate28dUserWeighted'))) {
    $row = Get-FreshestRow -Rows $snapshot.sets.($pair[0]).overall
    if ($null -eq $row) {
        Write-Host ("  {0,-12} no rows" -f $pair[0])
        continue
    }
    Write-Host ("  {0,-12} {1} = {2}  distinctUsers = {3}  (as of {4})" -f $pair[0], $pair[1], $row.metrics.($pair[1]), $row.metrics.distinctUsers, $row.date)
}
Write-Host ("  error issues: {0}   anomalies: {1}" -f @($snapshot.errorIssues).Count, @($snapshot.anomalies).Count)
Write-Host '  Values are printed as the API returns them; their unit is not stated by Google (S2917 research 6).' -ForegroundColor DarkGray
exit 0
