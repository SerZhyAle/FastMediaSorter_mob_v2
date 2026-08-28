#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Read the live Google Play track state - read-only, safe to run at any time.

.DESCRIPTION
    Wraps scripts/release/read-play-tracks.py in the project virtual environment. The Play
    Developer API has no read path that does not open an edit, so the Python half opens one and
    deletes it in a finally block; nothing here uploads, updates a track or commits.

    Two callers need this. The wear release campaign (/skill-release-wear) reads the phone's
    published versionName - the string the watch bundle must carry - and the versionCode the
    Wear track already holds. An operator reads the same state to answer "what is actually in
    the store", which otherwise means opening the Console.

    -RequireWearCodeBelow turns the second read into an assertion. The wear campaign derives its
    versionCode from the clock as yyMMddHH, which is unique against every phone code by digit
    count and against every prior wear code by monotonicity - except for two watch releases
    inside one hour, which is the one case a rule cannot exclude and a check can (S2081).

.PARAMETER Json
    Emit the Python half's JSON object unchanged, for a caller that parses it. Default output is
    a human-readable table.

.PARAMETER RequireWearCodeBelow
    Exit 1 unless the live wear:production versionCode is strictly below this candidate.

.PARAMETER Package
    Application id to read. Defaults to com.sza.fastmediasorter.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/read-play-tracks.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/release/read-play-tracks.ps1 -Json

.EXAMPLE
    pwsh -NoProfile -File scripts/release/read-play-tracks.ps1 -RequireWearCodeBelow 26082718

.NOTES
    Exit codes:
      0 - state read (and the -RequireWearCodeBelow assertion held, when given)
      1 - the -RequireWearCodeBelow assertion failed: the Wear track already holds that code or higher
      2 - could not verify: no virtual environment, no service-account key, or the API call failed
#>
[CmdletBinding()]
param(
    [switch] $Json,
    [int] $RequireWearCodeBelow,
    [string] $Package = 'com.sza.fastmediasorter'
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$venvPython = Join-Path $repoRoot '.venv\Scripts\python.exe'
$pyScript = Join-Path $PSScriptRoot 'read-play-tracks.py'

if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Error "read-play-tracks: virtual environment not found at $venvPython." -ErrorAction Continue
    exit 2
}
if (-not (Test-Path -LiteralPath $pyScript)) {
    Write-Error "read-play-tracks: reader not found at $pyScript." -ErrorAction Continue
    exit 2
}

Push-Location $repoRoot
try {
    $raw = & $venvPython $pyScript --package $Package
    $pyExit = $LASTEXITCODE
}
finally {
    Pop-Location
}

if ($pyExit -ne 0) {
    Write-Error 'read-play-tracks: could not read Play track state - see the message above.' -ErrorAction Continue
    exit 2
}

try {
    $state = ($raw -join [Environment]::NewLine) | ConvertFrom-Json
}
catch {
    Write-Error "read-play-tracks: reader returned output that is not JSON - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

if ($Json) {
    $raw | ForEach-Object { Write-Output $_ }
}
else {
    Write-Host "Play track state for $($state.package):" -ForegroundColor Cyan
    foreach ($track in $state.tracks) {
        $releases = @($track.releases)
        if ($releases.Count -eq 0) {
            Write-Host ("  {0,-20} (no releases)" -f $track.track)
            continue
        }
        foreach ($release in $releases) {
            $codes = if ($release.versionCodes) { ($release.versionCodes -join ',') } else { '-' }
            Write-Host ("  {0,-20} {1,-10} vc={2,-12} name={3}" -f $track.track, $release.status, $codes, $release.name)
        }
    }
}

# The assertion is evaluated after the state is printed, so a failing run still shows why.
if ($PSBoundParameters.ContainsKey('RequireWearCodeBelow')) {
    if (-not $state.wear_production) {
        Write-Host "read-play-tracks: wear:production holds no completed release - candidate $RequireWearCodeBelow is free." -ForegroundColor Green
        exit 0
    }
    $live = [int]$state.wear_production.version_code
    if ($live -ge $RequireWearCodeBelow) {
        Write-Error "read-play-tracks: wear:production already holds versionCode $live, so candidate $RequireWearCodeBelow is not strictly greater. A watch release already went out this hour - wait for the next one." -ErrorAction Continue
        exit 1
    }
    Write-Host "read-play-tracks: candidate $RequireWearCodeBelow is above the live wear:production code $live." -ForegroundColor Green
}

exit 0
