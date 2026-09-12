#!/usr/bin/env pwsh
#requires -Version 7.0
<#
.SYNOPSIS
    Read Android vitals, judge them against Google's published bands and rewrite the two measured records (S2917).

.DESCRIPTION
    The deterministic Play vitals watch: read -> verdict -> record -> file. No model takes part in any
    step; an agent meets the result only as a Draft ticket in the queue.

      1. read    - scripts/release/read-play-vitals.ps1 -Json (read-only Reporting API), or -SnapshotPath.
      2. verdict - scripts/release/lib/play-vitals-verdict.ps1 against the PlayVitals and PlayMemory
                   blocks of scripts/devtest/prerelease.config.psd1.
      3. record  - block 4 of docs/PLAY_PUBLISHING_STATE.md (marker s2272:measured:vitals) and section
                   3.2 of dev/PLAY_QUALITY_THRESHOLDS_2027.md (marker s2917:measured:play-vitals), through
                   the same Set-MarkedRegion the S2272 refresher uses. Nothing outside the markers moves.
      4. file    - on a red finding, one Draft ticket per finding through the catalog CLI, deduplicated
                   by the finding key (skipped under -NoFile and -Check).

    A read that fails, or a verdict that refuses (a rate unit that cannot be right), ends the run with
    exit 2 before anything is written: a date in a record is always the date of data that was read.

    Starts: `.\a.ps1 pv` on demand, the release campaign after publication, and the daily scheduled
    task registered by scripts/release/register-play-vitals-task.ps1 (not registered by default).
    Takes no lock and posts nothing to the agent chat - it is a reader, not an agent.

.PARAMETER Check
    Compute both blocks and write nothing. Exit 1 if either would change. Files no ticket.

.PARAMETER DocRoot
    Root the two documents are resolved under. Defaults to the repository root; the tests point it at a
    temporary copy.

.PARAMETER CatalogRoot
    Project root whose catalog CLI (scripts/spec_catalog/*.ps1), template and PLAN/ receive filed
    tickets. Defaults to the repository root; the tests point it at a sandbox project.

.PARAMETER SnapshotPath
    Read the schema-1 snapshot from this file instead of running the reader.

.PARAMETER Fixture
    Raw API responses passed through to the reader's -Fixture (tests).

.PARAMETER NoFile
    Write the records but file no ticket.

.PARAMETER BandsOverride
    Hashtable merged over the PlayVitals block, for synthetic breach runs from a PowerShell prompt and
    for the tests. A nested Red hashtable is merged key by key.

.PARAMETER LogDir
    Also write run-<utc stamp>.log (a transcript) and last-exit.json ({exitCode, verdict, measuredUtc,
    finishedUtc}) into this directory. The scheduled task passes temp\play-vitals.

.PARAMETER Package
    Application id to read. Defaults to com.sza.fastmediasorter.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/watch-play-vitals.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/release/watch-play-vitals.ps1 -Check

.NOTES
    Exit codes:
      0 - read, judged and recorded (whatever the verdict colour); tickets filed where a band was red
      1 - -Check found at least one block out of date; nothing was written
      2 - could not verify: the read failed, the verdict refused, a document or its marker pair is
          missing, or filing failed after the records were written
#>
[CmdletBinding()]
param(
    [switch] $Check,
    [string] $DocRoot,
    [string] $CatalogRoot,
    [string] $SnapshotPath,
    [string] $Fixture,
    [switch] $NoFile,
    [hashtable] $BandsOverride,
    [string] $LogDir,
    [string] $Package = 'com.sza.fastmediasorter'
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $DocRoot) { $DocRoot = $repoRoot }
if (-not $CatalogRoot) { $CatalogRoot = $repoRoot }
$pwshExe = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
$inv = [System.Globalization.CultureInfo]::InvariantCulture

. (Join-Path $PSScriptRoot 'lib\marked-region.ps1')
. (Join-Path $PSScriptRoot 'lib\play-vitals-verdict.ps1')
. (Join-Path $PSScriptRoot 'lib\play-vitals-filing.ps1')

$script:runVerdict = $null
$script:runMeasured = $null
$script:transcribing = $false
if ($LogDir) {
    $null = New-Item -ItemType Directory -Force -Path $LogDir
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
    Start-Transcript -Path (Join-Path $LogDir "run-$stamp.log") -Force | Out-Null
    $script:transcribing = $true
}

# Every exit goes through here, so the scheduled run always leaves its marker beside its log.
function Complete-Run([int] $Code) {
    if ($LogDir) {
        $marker = [ordered] @{
            exitCode    = $Code
            verdict     = $script:runVerdict
            measuredUtc = $script:runMeasured
            finishedUtc = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
        }
        [System.IO.File]::WriteAllText((Join-Path $LogDir 'last-exit.json'), ($marker | ConvertTo-Json),
            [System.Text.UTF8Encoding]::new($false))
    }
    if ($script:transcribing) { Stop-Transcript | Out-Null }
    exit $Code
}

function Stop-Run([int] $Code, [string] $Message) {
    Write-Error "watch-play-vitals: $Message" -ErrorAction Continue
    Complete-Run $Code
}

$stateDoc = Join-Path $DocRoot 'docs\PLAY_PUBLISHING_STATE.md'
$thresholdsDoc = Join-Path $DocRoot 'dev\PLAY_QUALITY_THRESHOLDS_2027.md'
$configPath = Join-Path $repoRoot 'scripts\devtest\prerelease.config.psd1'
foreach ($required in @($stateDoc, $thresholdsDoc, $configPath)) {
    if (-not (Test-Path -LiteralPath $required)) { Stop-Run 2 "required file not found - $required" }
}

# --- 1. read ---------------------------------------------------------------------------------------
if ($SnapshotPath) {
    if (-not (Test-Path -LiteralPath $SnapshotPath)) { Stop-Run 2 "snapshot not found - $SnapshotPath" }
    $snapshotText = Get-Content -LiteralPath $SnapshotPath -Raw
} else {
    $readerArgs = @('-NoProfile', '-File', (Join-Path $PSScriptRoot 'read-play-vitals.ps1'), '-Json', '-Package', $Package)
    if ($Fixture) { $readerArgs += @('-Fixture', $Fixture) }
    $output = & $pwshExe @readerArgs 2>&1
    $readerExit = $LASTEXITCODE
    $errLines = @($output | Where-Object { $_ -is [System.Management.Automation.ErrorRecord] } | ForEach-Object { $_.ToString() })
    $outLines = @($output | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] })
    if ($readerExit -ne 0) {
        foreach ($line in $errLines) { Write-Host $line }
        Stop-Run 2 "the vitals read failed (reader exit $readerExit) - nothing written, no ticket filed."
    }
    $snapshotText = $outLines -join "`n"
}
try {
    $snapshot = $snapshotText | ConvertFrom-Json
} catch {
    Stop-Run 2 "the snapshot is not JSON - $($_.Exception.Message)"
}
# ConvertFrom-Json turns an ISO timestamp into a DateTime, and [string] of a DateTime follows the
# session culture ("09/11/2026"), so every stamp is formatted explicitly - the block must not depend
# on who ran it, or -Check would call a record stale on another machine's locale.
function ConvertTo-UtcText($Value, [string] $Format) {
    if ($null -eq $Value -or "$Value" -eq '') { return $null }
    if ($Value -is [datetime]) { return $Value.ToUniversalTime().ToString($Format, $inv) }
    $parsed = [datetime]::MinValue
    if ([datetime]::TryParse([string] $Value, $inv, [System.Globalization.DateTimeStyles]::AdjustToUniversal, [ref] $parsed)) {
        return $parsed.ToString($Format, $inv)
    }
    return [string] $Value
}
$script:runMeasured = ConvertTo-UtcText $snapshot.measuredUtc 'yyyy-MM-ddTHH:mm:ssZ'

# --- 2. verdict ------------------------------------------------------------------------------------
$config = Import-PowerShellDataFile -LiteralPath $configPath
$bands = @{}
foreach ($key in $config.PlayVitals.Keys) { $bands[$key] = $config.PlayVitals[$key] }
$bands.Red = @{} + $config.PlayVitals.Red
if ($BandsOverride) {
    foreach ($key in $BandsOverride.Keys) {
        if ($key -eq 'Red' -and $BandsOverride.Red -is [hashtable]) {
            foreach ($redKey in $BandsOverride.Red.Keys) { $bands.Red[$redKey] = $BandsOverride.Red[$redKey] }
        } else {
            $bands[$key] = $BandsOverride[$key]
        }
    }
}
try {
    $verdict = Get-PlayVitalsVerdict -Snapshot $snapshot -Bands $bands -Memory $config.PlayMemory
} catch {
    Stop-Run 2 "the verdict refused - $($_.Exception.Message)"
}
$script:runVerdict = $verdict.Verdict

# --- 3. record -------------------------------------------------------------------------------------
function Format-Num($Value) {
    if ($null -eq $Value -or "$Value" -eq '') { return '-' }
    if ($Value -is [string]) { return $Value }
    return ([double] $Value).ToString('0.######', $inv)
}

function Get-FreshRow($Rows, [string] $Dimension, [string] $Value) {
    return @($Rows | Where-Object { $null -ne $_ -and [string] $_.dims.$Dimension -eq $Value }) |
        Sort-Object -Property date | Select-Object -Last 1
}

$measuredDate = ConvertTo-UtcText $snapshot.measuredUtc 'yyyy-MM-dd'
if (-not $measuredDate) { $measuredDate = '-' }
$windowText = "$($snapshot.window.startDate)..$($snapshot.window.endDate) $($snapshot.window.timeZone)"
$rateNote = "Rates as the API returns them, read as $($bands.RateUnit) (S2917 research 6)."

$vitalsBody = [System.Collections.Generic.List[string]]::new()
$vitalsBody.Add("**Verdict:** ``$($verdict.Verdict)`` - measured $measuredDate (UTC), window $windowText, source $($snapshot.source). $rateNote")
$vitalsBody.Add('')
$vitalsBody.Add('| Finding | Scope | Value | Band | Colour | Distinct users |')
$vitalsBody.Add('|---------|-------|-------|------|--------|----------------|')
foreach ($finding in $verdict.Findings) {
    $vitalsBody.Add(('| `{0}` | {1} | {2} | {3} | {4} | {5} |' -f $finding.Metric, $finding.Scope, (Format-Num $finding.Value),
        (Format-Num $finding.Threshold), $finding.Color, (Format-Num $finding.DistinctUsers)))
}
$vitalsBody.Add('')
$anomalies = @($snapshot.anomalies | Where-Object { $null -ne $_ })
if ($anomalies.Count -eq 0) {
    $vitalsBody.Add('Google anomalies in the window: none.')
} else {
    $vitalsBody.Add("Google anomalies in the window: $($anomalies.Count).")
    foreach ($anomaly in $anomalies) {
        $vitalsBody.Add(('- `{0}` on `{1}`, value {2}, {3}..{4}' -f $anomaly.metric, $anomaly.metricSet, (Format-Num $anomaly.value),
            $anomaly.startDate, $anomaly.endDate))
    }
}
$issues = @($snapshot.errorIssues | Where-Object { $null -ne $_ })
$vitalsBody.Add('')
if ($issues.Count -eq 0) {
    $vitalsBody.Add('Top error issues: none reported in the window.')
} else {
    $vitalsBody.Add("Top error issues by distinct users ($($issues.Count)):")
    foreach ($issue in $issues) {
        $vitalsBody.Add(('- `{0}` {1} at `{2}` - {3} users, {4} reports, last versionCode {5} - [console]({6})' -f $issue.type,
            $issue.cause, $issue.location, $issue.distinctUsers, $issue.errorReportCount, $issue.lastAppVersionCode, $issue.issueUri))
    }
}

$sets = $snapshot.sets
$minUsers = [double] $bands.MinDistinctUsers
$memoryText = if ([bool] $bands.MemoryBandsEnabled) {
    $memColors = @($verdict.Findings | Where-Object { $_.Metric -like '*MemoryUsageP90' } | ForEach-Object { $_.Color } | Sort-Object -Unique)
    if ($memColors.Count -gt 0) { $memColors -join ', ' } else { 'no rows' }
} else { 'unit unconfirmed' }
$codes = @($verdict.VersionCodes | Sort-Object -Property { [long] $_ } -Descending)
$thresholdBody = [System.Collections.Generic.List[string]]::new()
$thresholdBody.Add("Measured $measuredDate (UTC) over $windowText. App verdict: ``$($verdict.Verdict)``. $rateNote")
$thresholdBody.Add('')
$thresholdBody.Add('| Freshest day | versionCode | Crash, user-perceived 28d | ANR, user-perceived 28d | LMK, user-perceived 28d | Distinct users | Memory | Anomalies | Band colour |')
$thresholdBody.Add('|---|---|---|---|---|---|---|---|---|')
foreach ($code in $codes) {
    $crash = Get-FreshRow $sets.crashRate.byVersionCode 'versionCode' $code
    $anr = Get-FreshRow $sets.anrRate.byVersionCode 'versionCode' $code
    $lmk = Get-FreshRow $sets.lmkRate.byVersionCode 'versionCode' $code
    $crash28 = if ($crash) { $crash.metrics.userPerceivedCrashRate28dUserWeighted } else { $null }
    $anr28 = if ($anr) { $anr.metrics.userPerceivedAnrRate28dUserWeighted } else { $null }
    $lmk28 = if ($lmk) { $lmk.metrics.userPerceivedLmkRate28dUserWeighted } else { $null }
    $users = if ($crash) { $crash.metrics.distinctUsers } else { $null }
    $codeAnomalies = @($anomalies | Where-Object { [string] $_.dimensions.versionCode -eq $code }).Count
    $colour = if ($null -eq $users -or [double] $users -lt $minUsers -or $null -eq $crash28 -or $null -eq $anr28) {
        'insufficient data'
    } else {
        $pair = @(
            (Get-VitalsColor -Value (ConvertTo-VitalsFraction ([double] $crash28) $bands.RateUnit) -Red $bands.Red.UserPerceivedCrashRate -YellowShare $bands.YellowShareOfRed),
            (Get-VitalsColor -Value (ConvertTo-VitalsFraction ([double] $anr28) $bands.RateUnit) -Red $bands.Red.UserPerceivedAnrRate -YellowShare $bands.YellowShareOfRed)
        )
        if ($pair -contains 'red') { 'red' } elseif ($pair -contains 'yellow') { 'yellow' } else { 'green' }
    }
    $day = if ($crash) { $crash.date } else { '-' }
    $thresholdBody.Add(('| {0} | `{1}` | {2} | {3} | {4} | {5} | {6} | {7} | {8} |' -f $day, $code, (Format-Num $crash28), (Format-Num $anr28),
        (Format-Num $lmk28), (Format-Num $users), $memoryText, $codeAnomalies, $colour))
}
if ($codes.Count -eq 0) {
    $thresholdBody.Add('| - | no versionCode carried users in the window | - | - | - | - | - | - | insufficient data |')
}

function Get-DocUpdate([string] $Path, [string] $Begin, [string] $End, [System.Collections.Generic.List[string]] $Body) {
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    $text = [System.Text.UTF8Encoding]::new($false).GetString($bytes, ($(if ($hasBom) { 3 } else { 0 })), $bytes.Length - ($(if ($hasBom) { 3 } else { 0 })))
    $eol = if ($text -match "`r`n") { "`r`n" } else { "`n" }
    $next = Set-MarkedRegion -Text $text -Begin $Begin -End $End -Body $Body.ToArray() -Eol $eol
    return [pscustomobject] @{ Path = $Path; Old = $text; New = $next; Bom = $hasBom; Begin = $Begin }
}

$updates = @(
    (Get-DocUpdate $stateDoc '<!-- s2272:measured:vitals:begin -->' '<!-- s2272:measured:vitals:end -->' $vitalsBody),
    (Get-DocUpdate $thresholdsDoc '<!-- s2917:measured:play-vitals:begin -->' '<!-- s2917:measured:play-vitals:end -->' $thresholdBody)
)
foreach ($update in $updates) {
    if ($null -eq $update.New) {
        Stop-Run 2 "the marker pair $($update.Begin) is missing, reversed or duplicated in $($update.Path) - nothing written."
    }
}
$stale = @($updates | Where-Object { $_.New -ne $_.Old })

Write-Host "watch-play-vitals: verdict $($verdict.Verdict) over $windowText (source $($snapshot.source))."
foreach ($finding in $verdict.Findings | Where-Object { $_.Color -in @('red', 'yellow') }) {
    Write-Host ("  {0,-6} {1} - {2} = {3} (band {4})" -f $finding.Color, $finding.Key, $finding.Metric, (Format-Num $finding.Value), (Format-Num $finding.Threshold))
}

if ($Check) {
    if ($stale.Count -gt 0) {
        Stop-Run 1 "-Check found $($stale.Count) measured block(s) out of date ($(@($stale | ForEach-Object { Split-Path $_.Path -Leaf }) -join ', ')). Run without -Check to rewrite them."
    }
    Write-Host 'watch-play-vitals: -Check - both measured blocks are current.'
    Complete-Run 0
}

foreach ($update in $stale) {
    $encoding = [System.Text.UTF8Encoding]::new($update.Bom)
    [System.IO.File]::WriteAllText($update.Path, $update.New, $encoding)
    Write-Host "watch-play-vitals: rewrote the measured block in $(Split-Path $update.Path -Leaf)."
}
if ($stale.Count -eq 0) { Write-Host 'watch-play-vitals: both measured blocks already current, nothing written.' }

# --- 4. file ---------------------------------------------------------------------------------------
# Only red is filed; yellow and insufficient data live in the records and nowhere else.
$red = @($verdict.Findings | Where-Object { $_.Color -eq 'red' })
if (-not $NoFile -and -not $Check -and $red.Count -gt 0) {
    try {
        $recordsJson = Invoke-PlayVitalsCatalogCli -ProjectRoot $CatalogRoot -Script 'search.ps1' -Arguments @('-Query', 'play-vitals-', '-Format', 'json')
        $recordsText = ($recordsJson -join "`n").Trim()
        # Assigned in two steps: an empty array returned from an if-expression arrives as $null.
        $records = @()
        if ($recordsText) { $records = @($recordsText | ConvertFrom-Json) }
        $plan = Get-PlayVitalsFilingPlan -Findings $red -Records $records
        $results = Invoke-PlayVitalsFiling -Plan $plan -ProjectRoot $CatalogRoot -Snapshot $snapshot `
            -TopIssues ([int] $bands.TopIssuesInTicket) -MeasuredDate $measuredDate
    } catch {
        Stop-Run 2 "both records are current, but the red finding(s) were NOT filed - $($_.Exception.Message)"
    }
    foreach ($result in $results) {
        switch ($result.Action) {
            'filed' { Write-Host "watch-play-vitals: filed $($result.Id) $($result.Name)" }
            'appended' { Write-Host "watch-play-vitals: appended $($result.Id) ($($result.Name))" }
            default { Write-Host "watch-play-vitals: skipped $($result.Id) ($($result.Note))" }
        }
    }
}

Complete-Run 0
