# Run-Tests.ps1 (S2917) - regression suite for the Play vitals watch: reader, verdict, records, filing.
#
# Subjects: scripts/release/read-play-vitals.py, scripts/release/read-play-vitals.ps1,
#           scripts/release/lib/play-vitals-verdict.ps1, scripts/release/watch-play-vitals.ps1,
#           scripts/release/lib/marked-region.ps1, scripts/release/lib/play-vitals-filing.ps1
#
# Why this suite exists at all: the Reporting API is disabled on the service account's project
# (PLAN/S2917_play-vitals-monitor/research/02), so no part of the loop can be rehearsed against live data.
# Recorded responses under fixtures/ are pushed through the SHIPPED code - the Python module is loaded
# with importlib because its file name carries hyphens - never through a copy of its logic.
#
# What is asserted, because a suite that only goes green proves nothing:
#   * the window spans exactly 28 DAILY dates in America/Los_Angeles, ending at the freshest date the
#     metric set reports (latestEndTime is exclusive, so the last data date is the day before it),
#   * phone and watch rows stay apart by versionCode - both publish under one applicationId,
#   * metric values equal the fixture decimals unscaled - the unit is not stated by Google (research 6),
#     so no layer below the verdict may interpret it,
#   * the console link of an error issue survives normalisation,
#   * the recorded SERVICE_DISABLED body yields the activation URL, and a PERMISSION_DENIED names the
#     Play Console permission - the two things the owner has to do,
#   * a refusal prints nothing on stdout and the wrapper returns 2, not 1,
#   * the verdict colours each band as Google publishes it, keeps anomalies red with no arithmetic of
#     its own, refuses a rate that cannot be a fraction, and pins every finding key the filer dedups on,
#   * the records move no byte outside their markers, -Check writes nothing, a failed read writes
#     nothing, a missing marker writes neither document,
#   * one red run files one Draft through a SANDBOX catalog, the next day appends one evidence line,
#     the same day appends nothing - and the real PLAN/spec-catalog.jsonl is never written.
#
# Usage:  pwsh -NoProfile -File scripts/release/watch-play-vitals.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   the suite could not run (a subject is missing, or the project virtual environment is absent).

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pyScript = Join-Path $repoRoot 'scripts/release/read-play-vitals.py'
$readerPs1 = Join-Path $repoRoot 'scripts/release/read-play-vitals.ps1'
$venvPython = Join-Path $repoRoot '.venv/Scripts/python.exe'
$fixtures = Join-Path $PSScriptRoot 'fixtures'
$rawGreen = Join-Path $fixtures 'raw-green.json'
$rawDisabled = Join-Path $fixtures 'raw-service-disabled.json'

foreach ($required in @($pyScript, $readerPs1, $venvPython, $rawGreen, $rawDisabled)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "watch-play-vitals.tests: CANNOT RUN - not found: $required"
        exit 2
    }
}

$script:pass = 0
$script:fail = 0
$pwshExe = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        $script:pass++
        Write-Host ("  PASS  {0}" -f $name)
    } else {
        $script:fail++
        Write-Host ("  FAIL  {0} - {1}" -f $name, $detail)
    }
}

# Runs a Python snippet with the reader module preloaded as `m`; the snippet prints one JSON value.
function Invoke-ReaderPython([string]$body) {
    $prelude = @"
import importlib.util, json, sys, types
spec = importlib.util.spec_from_file_location('read_play_vitals', r'$pyScript')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
"@
    $tmp = Join-Path ([IO.Path]::GetTempPath()) ("s2917-py-{0}.py" -f [guid]::NewGuid().ToString('N'))
    Set-Content -LiteralPath $tmp -Value ($prelude + "`n" + $body) -Encoding utf8NoBOM
    try {
        $out = & $venvPython $tmp 2>&1
        $code = $LASTEXITCODE
    } finally {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
    if ($code -ne 0) { throw "python snippet failed ($code): $($out -join ' | ')" }
    return ($out -join "`n") | ConvertFrom-Json
}

Write-Host 'watch-play-vitals.tests - reader'

# --- R1..R4: normalisation of the green fixture through the shipped normalize() ---
$snap = Invoke-ReaderPython @"
raw = json.load(open(r'$rawGreen', encoding='utf-8'))
args = m.parse_args(['--fixture', r'$rawGreen'])
import datetime as dt
print(json.dumps(m.normalize(raw, args, dt.datetime(2026, 9, 11, 8, 0, tzinfo=dt.timezone.utc))))
"@
$dates = @($snap.sets.crashRate.overall | ForEach-Object { $_.date } | Sort-Object -Unique)
Assert-That 'R1 window is 28 LA dates ending the day before latestEndTime' `
    ($snap.window.days -eq 28 -and $snap.window.timeZone -eq 'America/Los_Angeles' -and
     $snap.window.startDate -eq '2026-08-14' -and $snap.window.endDate -eq '2026-09-10' -and $dates.Count -eq 28 -and
     $dates[-1] -eq '2026-09-10') `
    "window=$($snap.window | ConvertTo-Json -Compress) dates=$($dates.Count)"

$codes = @($snap.sets.crashRate.byVersionCode | ForEach-Object { $_.dims.versionCode } | Sort-Object -Unique)
Assert-That 'R2 phone and watch rows stay apart by versionCode' `
    ($codes.Count -eq 2 -and $codes -contains '260902195' -and $codes -contains '26090914' -and
     $snap.sets.lmkRate.byVersionCode.Count -eq 56) `
    "codes=$($codes -join ',') lmkRows=$($snap.sets.lmkRate.byVersionCode.Count)"

$firstRow = @($snap.sets.crashRate.overall)[0]
Assert-That 'R3 metric values equal the fixture decimals, unscaled' `
    ($firstRow.metrics.userPerceivedCrashRate -eq 0.002 -and $firstRow.metrics.crashRate -eq 0.0028 -and
     $firstRow.metrics.distinctUsers -eq 1200) `
    "metrics=$($firstRow.metrics | ConvertTo-Json -Compress)"

Assert-That 'R4 the error issue keeps its console link and last versionCode' `
    (@($snap.errorIssues).Count -eq 1 -and $snap.errorIssues[0].issueUri -like 'https://play.google.com/console/*' -and
     $snap.errorIssues[0].lastAppVersionCode -eq '260902195' -and $snap.source -eq 'fixture') `
    "issues=$(@($snap.errorIssues).Count)"

# --- R5..R6: refusal classification through the shipped describe_http_error() ---
$lines = Invoke-ReaderPython @"
raw = json.load(open(r'$rawDisabled', encoding='utf-8'))['_httpError']
denied = {'error': {'code': 403, 'status': 'PERMISSION_DENIED', 'message': 'The caller does not have permission'}}
print(json.dumps([m.describe_http_error(raw['status'], json.dumps(raw['content']).encode('utf-8')),
                  m.describe_http_error(403, denied)]))
"@
Assert-That 'R5 SERVICE_DISABLED names the activation URL' `
    ($lines[0] -like '*SERVICE_DISABLED*' -and
     $lines[0] -like '*playdeveloperreporting.googleapis.com/overview?project=764216752430*') `
    $lines[0]
Assert-That 'R6 PERMISSION_DENIED names the Play Console permission' `
    ($lines[1] -like '*PERMISSION_DENIED*' -and $lines[1] -like '*View app information and download bulk reports*') `
    $lines[1]

# --- R7..R8: the process contract - nothing on stdout from a refusal, and 2 through the wrapper ---
$stdout = & $venvPython $pyScript --fixture $rawDisabled 2>$null
$pyCode = $LASTEXITCODE
Assert-That 'R7 a refused read prints nothing on stdout and exits 2' (($pyCode -eq 2) -and -not $stdout) "exit=$pyCode stdout=$($stdout -join ' ')"

$null = & $pwshExe -NoProfile -File $readerPs1 -Json -Fixture $rawDisabled 2>&1
$psCode = $LASTEXITCODE
Assert-That 'R8 the wrapper propagates 2 rather than collapsing it into 1' ($psCode -eq 2) "exit=$psCode"

$json = & $pwshExe -NoProfile -File $readerPs1 -Json -Fixture $rawGreen 2>$null
$okCode = $LASTEXITCODE
$parsed = $null
try { $parsed = ($json -join "`n") | ConvertFrom-Json } catch { $parsed = $null }
Assert-That 'R9 the wrapper passes the snapshot through with -Json' ($okCode -eq 0 -and $null -ne $parsed -and $parsed.schema -eq 1) "exit=$okCode"

Write-Host 'watch-play-vitals.tests - verdict'

# Every verdict case starts from the snapshot the shipped reader makes of raw-green.json and mutates
# one thing, so a case fails for its own reason and never for a drifting hand-written snapshot.
. (Join-Path $repoRoot 'scripts/release/lib/play-vitals-verdict.ps1')
$config = Import-PowerShellDataFile (Join-Path $repoRoot 'scripts/devtest/prerelease.config.psd1')
$baseJson = ($json -join "`n")

function New-CaseSnapshot { return ($baseJson | ConvertFrom-Json) }

function New-CaseBands([hashtable] $Override = @{}) {
    $bands = @{}
    foreach ($k in $config.PlayVitals.Keys) { $bands[$k] = $config.PlayVitals[$k] }
    $bands.Red = @{} + $config.PlayVitals.Red
    foreach ($k in $Override.Keys) { $bands[$k] = $Override[$k] }
    return $bands
}

# Sets one metric on the freshest row of a breakdown; -Match narrows to one dimension value.
function Set-FreshMetric($Snap, [string] $Set, [string] $Breakdown, [string] $Metric, [double] $Value, [string] $Match = '') {
    $rows = @($Snap.sets.$Set.$Breakdown)
    if ($Match) { $rows = @($rows | Where-Object { @($_.dims.PSObject.Properties.Value) -contains $Match }) }
    $last = ($rows | ForEach-Object { $_.date } | Sort-Object)[-1]
    foreach ($row in $rows | Where-Object { $_.date -eq $last }) { $row.metrics.$Metric = $Value }
}

function Invoke-Verdict($Snap, [hashtable] $Bands = (New-CaseBands)) {
    return Get-PlayVitalsVerdict -Snapshot $Snap -Bands $Bands -Memory $config.PlayMemory
}

function Get-FindingColor($Verdict, [string] $Key) {
    $hit = @($Verdict.Findings | Where-Object { $_.Key -eq $Key })
    if ($hit.Count -ne 1) { return "<$($hit.Count) findings>" }
    return $hit[0].Color
}

$crashKey = 'play-vitals:userPerceivedCrashRate:overall'
$anrKey = 'play-vitals:userPerceivedAnrRate:overall'

$v = Invoke-Verdict (New-CaseSnapshot)
Assert-That 'V1 green snapshot is green on both rates' `
    ($v.Verdict -eq 'green' -and (Get-FindingColor $v $crashKey) -eq 'green' -and (Get-FindingColor $v $anrKey) -eq 'green') `
    "verdict=$($v.Verdict) crash=$(Get-FindingColor $v $crashKey) anr=$(Get-FindingColor $v $anrKey)"

$s = New-CaseSnapshot
Set-FreshMetric $s 'crashRate' 'overall' 'userPerceivedCrashRate28dUserWeighted' 0.006
$v = Invoke-Verdict $s
Assert-That 'V2 a 28-day crash rate of 0.6 % is yellow' `
    ($v.Verdict -eq 'yellow' -and (Get-FindingColor $v $crashKey) -eq 'yellow') "verdict=$($v.Verdict)"

$s = New-CaseSnapshot
Set-FreshMetric $s 'crashRate' 'overall' 'userPerceivedCrashRate28dUserWeighted' 0.012
$v = Invoke-Verdict $s
Assert-That 'V3 a 28-day crash rate of 1.2 % is red' `
    ($v.Verdict -eq 'red' -and (Get-FindingColor $v $crashKey) -eq 'red') "verdict=$($v.Verdict)"

$deviceKey = 'play-vitals:userPerceivedAnrRate:device:xiaomi/lisa'
$s = New-CaseSnapshot
Set-FreshMetric $s 'anrRate' 'byDeviceModel' 'userPerceivedAnrRate28dUserWeighted' 0.09 'xiaomi/lisa'
$v = Invoke-Verdict $s
Assert-That 'V4 9 % ANR on one model with enough users is red on that model only' `
    ($v.Verdict -eq 'red' -and (Get-FindingColor $v $deviceKey) -eq 'red' -and (Get-FindingColor $v $anrKey) -eq 'green') `
    "verdict=$($v.Verdict) device=$(Get-FindingColor $v $deviceKey)"

Set-FreshMetric $s 'anrRate' 'byDeviceModel' 'distinctUsers' 40 'xiaomi/lisa'
$v = Invoke-Verdict $s
Assert-That 'V5 the same model below MinDistinctUsersPerDevice stays out' `
    ($v.Verdict -eq 'green' -and (Get-FindingColor $v $deviceKey) -eq '<0 findings>') "verdict=$($v.Verdict)"

$s = New-CaseSnapshot
Set-FreshMetric $s 'crashRate' 'overall' 'distinctUsers' 40
Set-FreshMetric $s 'anrRate' 'overall' 'distinctUsers' 40
$v = Invoke-Verdict $s
Assert-That 'V6 40 distinct users is insufficient data, not green and not red' `
    ($v.Verdict -eq 'insufficient-data' -and (Get-FindingColor $v $crashKey) -eq 'insufficient-data') "verdict=$($v.Verdict)"

$s = New-CaseSnapshot
$s.anomalies = @([pscustomobject] @{
    name = 'apps/com.sza.fastmediasorter/anomalies/synthetic42'; metricSet = 'apps/com.sza.fastmediasorter/crashRateMetricSet'
    metric = 'userPerceivedCrashRate'; value = 0.004; dimensions = [pscustomobject] @{ versionCode = '260902195' }
    startDate = '2026-09-08'; endDate = '2026-09-10'
})
$v = Invoke-Verdict $s
Assert-That 'V7 a Google anomaly is red with no arithmetic of our own' `
    ($v.Verdict -eq 'red' -and (Get-FindingColor $v 'play-vitals:anomaly:synthetic42') -eq 'red') "verdict=$($v.Verdict)"

$s = New-CaseSnapshot
Set-FreshMetric $s 'crashRate' 'overall' 'userPerceivedCrashRate' 0.007
$v = Invoke-Verdict $s
Assert-That 'V8 a daily value above 3 x the 28-day value is a yellow spike' `
    ($v.Verdict -eq 'yellow' -and (Get-FindingColor $v 'play-vitals:userPerceivedCrashRate:spike') -eq 'yellow' -and
     (Get-FindingColor $v $crashKey) -eq 'green') "verdict=$($v.Verdict)"

$s = New-CaseSnapshot
Set-FreshMetric $s 'crashRate' 'overall' 'userPerceivedCrashRate28dUserWeighted' 1.5
$threw = $null
try { $null = Invoke-Verdict $s } catch { $threw = $_.Exception.Message }
Assert-That 'V9 a rate of 1.5 under RateUnit fraction refuses instead of reading 150 %' `
    ($null -ne $threw -and $threw -like '*rate unit mismatch*') "threw=$threw"

$v = Invoke-Verdict (New-CaseSnapshot)
$threwMem = $null
try { $null = Invoke-Verdict (New-CaseSnapshot) (New-CaseBands @{ MemoryBandsEnabled = $true }) } catch { $threwMem = $_.Exception.Message }
Assert-That 'V10 memory stays unit-unconfirmed while the flag is off, and on without a unit refuses' `
    ((Get-FindingColor $v 'play-vitals:anonRssAndSwapMemoryUsageP90:memory') -eq 'unit-unconfirmed' -and
     (Get-FindingColor $v 'play-vitals:bitmapMemoryUsageP90:memory') -eq 'unit-unconfirmed' -and
     $threwMem -like '*MemoryUnit*') "threw=$threwMem"

$v = Invoke-Verdict (New-CaseSnapshot) (New-CaseBands @{ MemoryBandsEnabled = $true; MemoryUnit = 'kB' })
Assert-That 'V11 memory on in kB compares P90 with PlayMemory; bitmap foreground is not judged' `
    ((Get-FindingColor $v 'play-vitals:anonRssAndSwapMemoryUsageP90:ram-8192:foreground') -eq 'green' -and
     (Get-FindingColor $v 'play-vitals:anonRssAndSwapMemoryUsageP90:ram-4096:foreground') -eq 'green' -and
     (Get-FindingColor $v 'play-vitals:bitmapMemoryUsageP90:ram-8192:foreground') -eq 'not-judged') `
    "keys=$(@($v.Findings | ForEach-Object { $_.Key + '=' + $_.Color }) -join '; ')"

Write-Host 'watch-play-vitals.tests - records'

# The orchestrator runs against COPIES of the two documents (-DocRoot), never the real ones.
$watchPs1 = Join-Path $repoRoot 'scripts/release/watch-play-vitals.ps1'
$sandboxRoot = Join-Path $repoRoot ("temp/scratch/watch-play-vitals-tests/{0}" -f [guid]::NewGuid().ToString('N'))
$stateRel = 'docs/PLAY_PUBLISHING_STATE.md'
$thresholdRel = 'dev/PLAY_QUALITY_THRESHOLDS_2027.md'
$stateMarkers = @('<!-- s2272:measured:vitals:begin -->', '<!-- s2272:measured:vitals:end -->')
$thresholdMarkers = @('<!-- s2917:measured:play-vitals:begin -->', '<!-- s2917:measured:play-vitals:end -->')

function New-DocRoot([string] $Name) {
    $root = Join-Path $sandboxRoot $Name
    foreach ($rel in @($stateRel, $thresholdRel)) {
        $target = Join-Path $root $rel
        $null = New-Item -ItemType Directory -Force -Path (Split-Path $target)
        Copy-Item -LiteralPath (Join-Path $repoRoot $rel) -Destination $target
    }
    return $root
}

function Get-Outside([string] $Text, [string[]] $Markers) {
    $b = $Text.IndexOf($Markers[0]); $e = $Text.IndexOf($Markers[1])
    return $Text.Substring(0, $b) + '|' + $Text.Substring($e)
}

function Get-Inside([string] $Text, [string[]] $Markers) {
    $b = $Text.IndexOf($Markers[0]) + $Markers[0].Length; $e = $Text.IndexOf($Markers[1])
    return $Text.Substring($b, $e - $b)
}

function Invoke-Watch([string[]] $WatchArgs) {
    $null = & $pwshExe -NoProfile -File $watchPs1 @WatchArgs 2>&1
    return $LASTEXITCODE
}

$greenSnap = Join-Path $sandboxRoot 'snapshot-green.json'
$null = New-Item -ItemType Directory -Force -Path $sandboxRoot
Set-Content -LiteralPath $greenSnap -Value $baseJson -Encoding utf8NoBOM
try {
    $root = New-DocRoot 'w1'
    $before = @{}; foreach ($rel in @($stateRel, $thresholdRel)) { $before[$rel] = Get-Content -LiteralPath (Join-Path $root $rel) -Raw }
    $code = Invoke-Watch @('-DocRoot', $root, '-SnapshotPath', $greenSnap, '-NoFile')
    $stateAfter = Get-Content -LiteralPath (Join-Path $root $stateRel) -Raw
    $thresholdAfter = Get-Content -LiteralPath (Join-Path $root $thresholdRel) -Raw
    Assert-That 'W1 a green snapshot fills both blocks and moves no byte outside the markers' `
        ($code -eq 0 -and
         (Get-Outside $stateAfter $stateMarkers) -eq (Get-Outside $before[$stateRel] $stateMarkers) -and
         (Get-Outside $thresholdAfter $thresholdMarkers) -eq (Get-Outside $before[$thresholdRel] $thresholdMarkers) -and
         # .Contains, not -like: a backtick is the wildcard escape character, so -like cannot match one.
         (Get-Inside $stateAfter $stateMarkers).Contains('**Verdict:** `green`') -and
         (Get-Inside $thresholdAfter $thresholdMarkers).Contains('App verdict: `green`')) "exit=$code"

    Assert-That 'W1b the measured date is ISO in both blocks, whatever the session culture' `
        ((Get-Inside $stateAfter $stateMarkers) -match 'measured \d{4}-\d{2}-\d{2} \(UTC\)' -and
         (Get-Inside $thresholdAfter $thresholdMarkers) -match 'Measured \d{4}-\d{2}-\d{2} \(UTC\)') 'date not ISO'

    $inside = Get-Inside $thresholdAfter $thresholdMarkers
    Assert-That 'W2 the play-vitals block holds separate phone and watch rows' `
        ($inside -match '\| `260902195` \|' -and $inside -match '\| `26090914` \|') 'rows missing'

    $code = Invoke-Watch @('-DocRoot', $root, '-SnapshotPath', $greenSnap, '-Check')
    Assert-That 'W3 an identical second run under -Check is current (exit 0)' ($code -eq 0) "exit=$code"

    $changed = $baseJson | ConvertFrom-Json
    Set-FreshMetric $changed 'crashRate' 'overall' 'userPerceivedCrashRate28dUserWeighted' 0.006
    $changedSnap = Join-Path $sandboxRoot 'snapshot-changed.json'
    Set-Content -LiteralPath $changedSnap -Value ($changed | ConvertTo-Json -Depth 20) -Encoding utf8NoBOM
    $hashBefore = (Get-FileHash -LiteralPath (Join-Path $root $stateRel)).Hash
    $code = Invoke-Watch @('-DocRoot', $root, '-SnapshotPath', $changedSnap, '-Check')
    Assert-That 'W4 a changed snapshot under -Check exits 1 and writes nothing' `
        ($code -eq 1 -and (Get-FileHash -LiteralPath (Join-Path $root $stateRel)).Hash -eq $hashBefore) "exit=$code"

    $root = New-DocRoot 'w5'
    $hashes = @{}; foreach ($rel in @($stateRel, $thresholdRel)) { $hashes[$rel] = (Get-FileHash -LiteralPath (Join-Path $root $rel)).Hash }
    $code = Invoke-Watch @('-DocRoot', $root, '-Fixture', $rawDisabled, '-NoFile')
    Assert-That 'W5 a failed read exits 2 and leaves both documents byte-identical' `
        ($code -eq 2 -and (Get-FileHash -LiteralPath (Join-Path $root $stateRel)).Hash -eq $hashes[$stateRel] -and
         (Get-FileHash -LiteralPath (Join-Path $root $thresholdRel)).Hash -eq $hashes[$thresholdRel]) "exit=$code"

    $root = New-DocRoot 'w6'
    $crlfPath = Join-Path $root $thresholdRel
    $lf = (Get-Content -LiteralPath $crlfPath -Raw) -replace "`r`n", "`n"
    [IO.File]::WriteAllText($crlfPath, ($lf -replace "`n", "`r`n"), [Text.UTF8Encoding]::new($false))
    $code = Invoke-Watch @('-DocRoot', $root, '-SnapshotPath', $greenSnap, '-NoFile')
    $crlfText = [IO.File]::ReadAllText($crlfPath)
    Assert-That 'W6 a CRLF document keeps CRLF throughout' `
        ($code -eq 0 -and ($crlfText -replace "`r`n", '') -notmatch "`n" -and $crlfText -like '*App verdict*') "exit=$code"

    $root = New-DocRoot 'w7'
    $statePath = Join-Path $root $stateRel
    $broken = (Get-Content -LiteralPath $statePath -Raw).Replace($stateMarkers[1], '')
    [IO.File]::WriteAllText($statePath, $broken, [Text.UTF8Encoding]::new($false))
    $thresholdHash = (Get-FileHash -LiteralPath (Join-Path $root $thresholdRel)).Hash
    $code = Invoke-Watch @('-DocRoot', $root, '-SnapshotPath', $greenSnap, '-NoFile')
    Assert-That 'W7 a document missing its end marker exits 2 and the other document is not written either' `
        ($code -eq 2 -and (Get-FileHash -LiteralPath (Join-Path $root $thresholdRel)).Hash -eq $thresholdHash) "exit=$code"

    $root = New-DocRoot 'w8'
    $logDir = Join-Path $sandboxRoot 'logs'
    $code = Invoke-Watch @('-DocRoot', $root, '-SnapshotPath', $greenSnap, '-NoFile', '-LogDir', $logDir)
    $marker = if (Test-Path -LiteralPath (Join-Path $logDir 'last-exit.json')) { Get-Content -LiteralPath (Join-Path $logDir 'last-exit.json') -Raw | ConvertFrom-Json } else { $null }
    Assert-That 'W8 -LogDir leaves a transcript and last-exit.json with the exit code and verdict' `
        ($code -eq 0 -and $null -ne $marker -and $marker.exitCode -eq 0 -and $marker.verdict -eq 'green' -and
         @(Get-ChildItem -LiteralPath $logDir -Filter 'run-*.log').Count -eq 1) "exit=$code marker=$($marker | ConvertTo-Json -Compress)"
} finally {
    Remove-Item -LiteralPath $sandboxRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host 'watch-play-vitals.tests - filing'

. (Join-Path $repoRoot 'scripts/release/lib/play-vitals-filing.ps1')
$realCatalog = Join-Path $repoRoot 'PLAN/spec-catalog.jsonl'
$realCatalogLines = @(Get-Content -LiteralPath $realCatalog).Count

function New-RedFinding([string] $Key) {
    return [pscustomobject] @{ Key = $Key; Color = 'red'; Metric = 'm'; Scope = 's'; Value = 0.02; Threshold = 0.0109
        Window = 'w'; VersionCodes = @('260902195'); DistinctUsers = 1200; Detail = '' }
}
$crashFinding = New-RedFinding 'play-vitals:userPerceivedCrashRate:overall'
$crashName = Get-PlayVitalsTicketName -Key $crashFinding.Key

$plan = @(Get-PlayVitalsFilingPlan -Findings @($crashFinding) -Records @())
Assert-That 'F1 no record -> file' ($plan.Count -eq 1 -and $plan[0].Action -eq 'file' -and $plan[0].Name -eq 'play-vitals-user-perceived-crash-rate-overall') "plan=$($plan | ConvertTo-Json -Compress -Depth 2)"

$plan = @(Get-PlayVitalsFilingPlan -Findings @($crashFinding) -Records @([pscustomobject] @{ id = 'S9001'; name = $crashName; status = 'Draft'; file = 'PLAN/S9001_x.md' }))
Assert-That 'F2 an open Draft with the same name -> append' ($plan[0].Action -eq 'append' -and $plan[0].RecordId -eq 'S9001') "action=$($plan[0].Action)"

$plan = @(Get-PlayVitalsFilingPlan -Findings @($crashFinding) -Records @([pscustomobject] @{ id = 'S9002'; name = $crashName; status = 'BlockNeedUserTest'; file = 'PLAN/S9002_x.md' }))
Assert-That 'F3 a BlockNeedUserTest ticket is left alone -> skip' ($plan[0].Action -eq 'skip' -and $plan[0].Reason -like '*BlockNeedUserTest*') "action=$($plan[0].Action)"

$plan = @(Get-PlayVitalsFilingPlan -Findings @($crashFinding) -Records @([pscustomobject] @{ id = 'S9003'; name = $crashName; status = 'Archived'; file = 'PLAN/archive/S9003_x.md' }))
Assert-That 'F4 an Archived ticket does not count -> file' ($plan[0].Action -eq 'file') "action=$($plan[0].Action)"

$plan = @(Get-PlayVitalsFilingPlan -Findings @($crashFinding, (New-RedFinding $crashFinding.Key), ([pscustomobject] @{ Key = 'k'; Color = 'yellow' })) -Records @())
Assert-That 'F5 two findings with one key make one action, and yellow is never planned' ($plan.Count -eq 1) "count=$($plan.Count)"

$longKey = 'play-vitals:userPerceivedAnrRate:device:' + ('vendor/' + ('m' * 83))
$n1 = Get-PlayVitalsTicketName -Key $longKey
$n2 = Get-PlayVitalsTicketName -Key $longKey
Assert-That 'F6 a 90-character device model yields a stable name of at most 60 characters' `
    ($n1 -eq $n2 -and $n1.Length -le 60 -and $n1 -match '^play-vitals-[a-z0-9-]+-[0-9a-f]{8}$') "name=$n1 ($($n1.Length))"

# Execution runs against a sandbox project: its own profile, catalog forwarders, template and PLAN/.
# The forwarders pin SZA_PROJECT_ROOT to their own ..\.., so the sandbox copy writes only the sandbox.
$sb = Join-Path $repoRoot ("temp/scratch/watch-play-vitals-tests/catalog-{0}" -f [guid]::NewGuid().ToString('N'))
$skipReason = $null
try {
    foreach ($dir in @('scripts/spec_catalog', 'scripts/utils', 'PLAN', '.claude/templates', 'docs', 'dev')) {
        $null = New-Item -ItemType Directory -Force -Path (Join-Path $sb $dir)
    }
    Copy-Item -LiteralPath (Join-Path $repoRoot '.sza-profile.json') -Destination $sb
    foreach ($cli in @('next-id', 'insert', 'select', 'search')) {
        Copy-Item -LiteralPath (Join-Path $repoRoot "scripts/spec_catalog/$cli.ps1") -Destination (Join-Path $sb 'scripts/spec_catalog')
    }
    Copy-Item -LiteralPath (Join-Path $repoRoot 'scripts/utils/project-paths.ps1') -Destination (Join-Path $sb 'scripts/utils')
    Copy-Item -LiteralPath (Join-Path $repoRoot '.claude/templates/strategic-spec.md') -Destination (Join-Path $sb '.claude/templates')
    Copy-Item -LiteralPath (Join-Path $repoRoot $stateRel) -Destination (Join-Path $sb 'docs')
    Copy-Item -LiteralPath (Join-Path $repoRoot $thresholdRel) -Destination (Join-Path $sb 'dev')
    Set-Content -LiteralPath (Join-Path $sb 'PLAN/spec-catalog.jsonl') -Value $null -NoNewline
    foreach ($plan in @('RELEASE_QUEUE.md', 'RELEASE_READY.md')) {
        $header = @(Get-Content -LiteralPath (Join-Path $repoRoot "PLAN/$plan") -TotalCount 8)
        Set-Content -LiteralPath (Join-Path $sb "PLAN/$plan") -Value $header -Encoding utf8NoBOM
    }
    $probe = & $pwshExe -NoProfile -File (Join-Path $sb 'scripts/spec_catalog/next-id.ps1') 2>&1
    if ($LASTEXITCODE -ne 0 -or ([string] ($probe | Select-Object -Last 1)).Trim() -notmatch '^S\d{4}$') {
        $skipReason = "the catalog forwarder refused the sandbox root ($($probe -join ' | '))"
    }
} catch {
    $skipReason = "the sandbox could not be built ($($_.Exception.Message))"
}

try {
    if ($skipReason) {
        Write-Host "  SKIP  F7 one red run files exactly one Draft - $skipReason" -ForegroundColor Yellow
        Write-Host "  SKIP  F8 the next day's run files nothing and appends one evidence line - $skipReason" -ForegroundColor Yellow
        Write-Host "  SKIP  F9 a second run on the same day appends nothing - $skipReason" -ForegroundColor Yellow
    } else {
        $sbCatalog = Join-Path $sb 'PLAN/spec-catalog.jsonl'
        $redBands = @{ Red = @{ UserPerceivedCrashRate = 0.0015 } }
        $day1 = $baseJson | ConvertFrom-Json -DateKind String
        $day1.measuredUtc = '2026-09-11T08:00:00Z'
        $day1Path = Join-Path $sb 'snapshot-day1.json'
        Set-Content -LiteralPath $day1Path -Value ($day1 | ConvertTo-Json -Depth 20) -Encoding utf8NoBOM
        $null = & $watchPs1 -DocRoot $sb -CatalogRoot $sb -SnapshotPath $day1Path -BandsOverride $redBands 6>&1 2>&1
        $code = $LASTEXITCODE
        $rows = @(Get-Content -LiteralPath $sbCatalog | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
        $specPath = if ($rows.Count -eq 1) { Join-Path $sb $rows[0].file } else { '' }
        $spec = if ($specPath -and (Test-Path -LiteralPath $specPath)) { Get-Content -LiteralPath $specPath -Raw } else { '' }
        Assert-That 'F7 one red run files exactly one Draft carrying the key and the evidence' `
            ($code -eq 0 -and $rows.Count -eq 1 -and $rows[0].status -eq 'Draft' -and $rows[0].name -eq $crashName -and
             $spec.Contains('**Finding key:** `play-vitals:userPerceivedCrashRate:overall`') -and
             $spec.Contains('[console](https://play.google.com/console/') -and $spec.Contains('**Status:** Draft') -and
             -not $spec.Contains('<Sxxxx>') -and -not $spec.Contains('Template consumed by')) `
            "exit=$code rows=$($rows.Count)"

        $day2 = $baseJson | ConvertFrom-Json -DateKind String
        $day2.measuredUtc = '2026-09-12T08:00:00Z'
        $day2Path = Join-Path $sb 'snapshot-day2.json'
        Set-Content -LiteralPath $day2Path -Value ($day2 | ConvertTo-Json -Depth 20) -Encoding utf8NoBOM
        $null = & $watchPs1 -DocRoot $sb -CatalogRoot $sb -SnapshotPath $day2Path -BandsOverride $redBands 6>&1 2>&1
        $code = $LASTEXITCODE
        $rows = @(Get-Content -LiteralPath $sbCatalog | Where-Object { $_.Trim() })
        $spec2 = if ($specPath) { Get-Content -LiteralPath $specPath -Raw } else { '' }
        Assert-That "F8 the next day's run files nothing and appends one evidence line" `
            ($code -eq 0 -and $rows.Count -eq 1 -and ([regex]::Matches($spec2, '(?m)^- 2026-09-1[12] - value ')).Count -eq 2) `
            "exit=$code rows=$($rows.Count)"

        $null = & $watchPs1 -DocRoot $sb -CatalogRoot $sb -SnapshotPath $day2Path -BandsOverride $redBands 6>&1 2>&1
        $code = $LASTEXITCODE
        $spec3 = if ($specPath) { Get-Content -LiteralPath $specPath -Raw } else { '' }
        Assert-That 'F9 a second run on the same day appends nothing' `
            ($code -eq 0 -and $spec3 -eq $spec2 -and @(Get-Content -LiteralPath $sbCatalog | Where-Object { $_.Trim() }).Count -eq 1) "exit=$code"
    }
} finally {
    Remove-Item -LiteralPath $sb -Recurse -Force -ErrorAction SilentlyContinue
}
Assert-That 'F10 the real catalog was not written by the suite' (@(Get-Content -LiteralPath $realCatalog).Count -eq $realCatalogLines) "before=$realCatalogLines"

Write-Host 'watch-play-vitals.tests - scheduler'

# -WhatIf only: registering a real task is a workstation change the suite must never make.
$registrar = Join-Path $repoRoot 'scripts/release/register-play-vitals-task.ps1'
$probeName = 'FastMediaSorter-PlayVitalsWatch-SuiteProbe'
$printed = & $pwshExe -NoProfile -File $registrar -Action Register -TaskName $probeName -WhatIf 2>&1
$whatIfCode = $LASTEXITCODE
$argLine = @($printed | Where-Object { "$_" -like '*arguments*' }) -join ''
Assert-That 'S1 -WhatIf prints a definition that runs the orchestrator with its log in temp\play-vitals, and registers nothing' `
    ($whatIfCode -eq 0 -and $argLine -like '*watch-play-vitals.ps1*' -and $argLine -like '*-LogDir*temp\play-vitals*' -and
     $null -eq (Get-ScheduledTask -TaskName $probeName -ErrorAction SilentlyContinue)) "exit=$whatIfCode line=$argLine"
$null = & $pwshExe -NoProfile -File $registrar -Action Status -TaskName $probeName 2>&1
Assert-That 'S2 Status of an absent task exits 1' ($LASTEXITCODE -eq 1) "exit=$LASTEXITCODE"

Write-Host ''
Write-Host ("watch-play-vitals.tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
