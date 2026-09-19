#requires -Version 7.0
<#
.SYNOPSIS
  Summarises development throughput over a window from the journals that are already written.

.DESCRIPTION
  S3308. Three journals already record everything the process audits keep re-deriving by hand:
  the queue runner writes one row per child run, every gate writes one row per execution, and
  each fast check writes its own log. Nobody sweeps them, so each audit re-mines the same corpus
  and the numbers it produces die with the conversation. This script is the sweep.

  Reported for the requested window:
    - status moves: runs whose ticket changed status.
    - idle run share: runs that moved nothing, which is what paying for more concurrency buys
      when the queue has nothing left to move.
    - cheap model share: runs on any model other than the strong one.
    - gate seconds: the wall clock every gate execution summed to, SKIP rows excluded because a
      skipped gate costs nothing and folding it in makes the wall look calmer the more gates opt out.
    - fast check lag: how far the phone code fast check ran past the duration
      docs/BUILD_TEST_FAST_PATH.md documents for it, which is where concurrency is actually paid for.

  Two conventions worth knowing before a number here is quoted.

  A fast check does not write its own duration, so the run is measured the way
  measure-build-lock-wait.ps1 already measures a hold: the 'Date:' header is written right after the
  build domain is acquired and the file's last write is the run's last line. That under-reports
  exactly the run that hangs after its last line, so the lag is never over-stated.

  The documented figure is READ out of the datasheet rather than copied here, the same rule
  scripts/quality/gate-timing-claims.json follows: a copy would be a second source of truth and
  would go stale in silence, which is the defect that made the fg row 7x wrong for a month.

  Manual tool: run by hand before changing how much work runs at once, or when an audit wants the
  window's numbers. It reads temp/ and docs/, and it writes nothing.

.PARAMETER Since
  Ignore anything recorded before this local moment. Default: 24 hours back.

.PARAMETER Until
  Ignore anything recorded after this local moment. Default: no upper bound.

.PARAMETER StrongModel
  The model that is NOT cheap. The runner's model rule ships from the canon harness and names no
  model in this repository's profile, so the strong one is a parameter here rather than a guess
  built into the body. Default: opus.

.PARAMETER Root
  Repository root to read from. Default: the root this script lives in. Tests point it at a fixture.

.PARAMETER Json
  Emit the values as one JSON object instead of the text block.

.NOTES
  Exit codes:
    0 - the window was summarised
    2 - could not verify: an input journal or the datasheet is absent or unreadable
#>
[CmdletBinding()]
param(
    [datetime]$Since = (Get-Date).AddDays(-1),
    [datetime]$Until = [datetime]::MaxValue,
    [string]$StrongModel = 'opus',
    [string]$Root,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

if (-not $Root) { $Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }

$queueDir = Join-Path $Root 'temp/spec-queue'
$gateJournal = Join-Path $Root 'temp/metrics/gate-executions.jsonl'
$fastLogDir = Join-Path $Root 'temp'
$datasheet = Join-Path $Root 'docs/BUILD_TEST_FAST_PATH.md'

function Stop-Unverifiable {
    param([string] $Message)
    Write-Host "measure-process-throughput: could not verify - $Message" -ForegroundColor Yellow
    exit 2
}

foreach ($required in @($queueDir, $gateJournal, $fastLogDir, $datasheet)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Stop-Unverifiable "missing input: $required"
    }
}

function Test-InWindow {
    param([datetime] $Moment)
    return ($Moment -ge $Since -and $Moment -le $Until)
}

# ConvertFrom-Json turns an ISO-8601 stamp into a DateTime before this script sees it, and stringifying
# that object drops the UTC marker - the round trip reads a UTC moment back as a local one and every
# duration built from it is wrong by the offset. The Kind is therefore honoured instead of re-parsed,
# and a stamp that carries no zone at all is left alone rather than assumed to be UTC.
function ConvertTo-LocalMoment {
    param($Value)
    [datetime]$moment = [datetime]::MinValue
    if ($Value -is [datetime]) { $moment = $Value }
    elseif (-not [datetime]::TryParse([string]$Value, [cultureinfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::RoundtripKind, [ref]$moment)) {
        return $null
    }
    if ($moment.Kind -eq [System.DateTimeKind]::Utc) { return $moment.ToLocalTime() }
    return $moment
}

# --- runner journal: status moves, idle share, cheap model share -----------------------------

$runRows = @()
# Kept for every parsable row, window or not: a policy with no runs IN the window is only readable
# as starved rather than unused if the instance carrying it can be dated outside the window too.
$lastRunByInstance = @{}
foreach ($file in @(Get-ChildItem -LiteralPath $queueDir -Filter 'runs-*.jsonl' -File -ErrorAction SilentlyContinue)) {
    $instance = ($file.BaseName -replace '^runs-', '')
    foreach ($line in (Get-Content -LiteralPath $file.FullName)) {
        if (-not $line.Trim()) { continue }
        try { $row = $line | ConvertFrom-Json } catch { continue }
        if (-not $row.finishedAt) { continue }
        $finished = ConvertTo-LocalMoment $row.finishedAt
        if ($null -eq $finished) { continue }
        if (-not $lastRunByInstance.ContainsKey($instance) -or $finished -gt $lastRunByInstance[$instance]) {
            $lastRunByInstance[$instance] = $finished
        }
        if (-not (Test-InWindow $finished)) { continue }
        $runRows += [pscustomobject]@{
            Instance = $instance
            Model    = [string]$row.model
            Policy   = if ($row.PSObject.Properties.Name -contains 'policy' -and $row.policy) { [string]$row.policy } else { 'unknown' }
            Moved    = [bool]$row.moved
        }
    }
}

$runCount = $runRows.Count
$statusMoves = @($runRows | Where-Object { $_.Moved }).Count
$idleRuns = $runCount - $statusMoves
$modelled = @($runRows | Where-Object { $_.Model })
$cheapRuns = @($modelled | Where-Object { $_.Model -ne $StrongModel }).Count

# --- which rule produced which model, per instance --------------------------------------------

$policyRows = @()
foreach ($group in @($runRows | Group-Object -Property Policy, Instance)) {
    $models = @($group.Group | Group-Object -Property Model | Sort-Object -Property Name |
        ForEach-Object { '{0} {1}' -f $_.Count, ($(if ($_.Name) { $_.Name } else { 'unrecorded' })) })
    $policyRows += [pscustomobject]@{
        Policy   = $group.Group[0].Policy
        Instance = $group.Group[0].Instance
        Runs     = $group.Count
        Models   = ($models -join ', ')
    }
}
$policyRows = @($policyRows | Sort-Object -Property Policy, Instance)

# A policy that is declared but never ran is not an inconclusive measurement - it is an absent one,
# and the two read identically in any report that only sums the rows it finds.
$policyWarnings = @()
$profileJson = $null
$profilePath = Join-Path $Root '.sza-profile.json'
if (Test-Path -LiteralPath $profilePath) {
    try { $profileJson = Get-Content -LiteralPath $profilePath -Raw | ConvertFrom-Json } catch { $profileJson = $null }
    $instances = $profileJson.runner.instances
    if ($instances) {
        $observed = @($runRows | ForEach-Object { $_.Policy } | Sort-Object -Unique)
        foreach ($name in @($instances.PSObject.Properties.Name)) {
            $declared = [string]$instances.$name.modelPolicy
            if (-not $declared) { continue }
            if ($observed -contains $declared) { continue }
            $lastSeen = if ($lastRunByInstance.ContainsKey($name)) { '{0:yyyy-MM-dd}' -f $lastRunByInstance[$name] } else { 'never' }
            $policyWarnings += "policy '$declared' declared by instance '$name' has zero runs in the window (last run of that instance: $lastSeen)"
        }
    }
}

# --- domain holds that outlived their edit window ---------------------------------------------

# Rule 23 releases a domain at the last file a step writes, so a hold past that is a defect and
# nothing reports it today. One hold of the script domain ran at least 41 minutes against 4 to 418
# seconds for every other hold of the same domain that day, and cost a neighbouring session 747 s.
$holdThreshold = $null
if ($profileJson -and $profileJson.concurrency -and $profileJson.concurrency.holdSecondsMax) {
    $holdThreshold = [double]$profileJson.concurrency.holdSecondsMax
}

$holdRows = @()
if ($null -ne $holdThreshold) {
    $lockEvents = @()
    $progressDir = Join-Path $Root 'temp/AGENT-CHAT/progress'
    foreach ($file in @(Get-ChildItem -LiteralPath $progressDir -Filter '*_lock_*.json' -File -ErrorAction SilentlyContinue)) {
        try { $record = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json } catch { continue }
        $at = ConvertTo-LocalMoment $record.at
        if ($null -eq $at) { continue }
        $verb = if ([string]$record.note -match '^(acquired|released)') { $Matches[1] } else { $null }
        if (-not $verb) { continue }
        foreach ($domain in @($record.domains)) {
            $lockEvents += [pscustomobject]@{
                At     = $at
                Verb   = $verb
                Domain = [string]$domain
                Holder = [string]$record.agent.name
                Agent  = [string]$record.agent.id
            }
        }
    }

    $waits = @()
    foreach ($file in @(Get-ChildItem -LiteralPath (Join-Path $Root 'temp/LOCK-HANDOFF') -Filter 'HANDOFF-*.json' -File -ErrorAction SilentlyContinue)) {
        try { $record = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json } catch { continue }
        foreach ($domain in @($record.tickets.PSObject.Properties.Name)) {
            $waits += [pscustomobject]@{
                Domain    = [string]$domain
                CreatedAt = [datetimeoffset]::FromUnixTimeMilliseconds([long]$record.createdAt).LocalDateTime
            }
        }
    }

    $now = Get-Date
    foreach ($group in @($lockEvents | Group-Object -Property Domain, Agent)) {
        $ordered = @($group.Group | Sort-Object -Property At)
        for ($i = 0; $i -lt $ordered.Count; $i++) {
            if ($ordered[$i].Verb -ne 'acquired') { continue }
            $start = $ordered[$i].At
            if (-not (Test-InWindow $start)) { continue }
            $release = $null
            for ($j = $i + 1; $j -lt $ordered.Count; $j++) {
                if ($ordered[$j].Verb -eq 'released') { $release = $ordered[$j].At; break }
                if ($ordered[$j].Verb -eq 'acquired') { break }
            }
            # An acquire with no release is the case this report exists for: it is the shape a dropped
            # hold takes, so it is carried as still held rather than dropped for having no end stamp.
            $end = if ($null -ne $release) { $release } else { $now }
            $seconds = [math]::Round(($end - $start).TotalSeconds)
            if ($seconds -lt $holdThreshold) { continue }
            # A lower bound on the queue behind it: a waiter that took a ticket during the hold waited
            # at least until the hold ended. The grant itself is not journalled, so this never overstates.
            $behind = @($waits | Where-Object { $_.Domain -eq $ordered[$i].Domain -and $_.CreatedAt -ge $start -and $_.CreatedAt -le $end } |
                ForEach-Object { [math]::Round(($end - $_.CreatedAt).TotalSeconds) })
            $holdRows += [pscustomobject]@{
                Domain       = $ordered[$i].Domain
                Holder       = $ordered[$i].Holder
                Seconds      = $seconds
                StillHeld    = ($null -eq $release)
                LongestWait  = if ($behind.Count -gt 0) { ($behind | Measure-Object -Maximum).Maximum } else { 0 }
            }
        }
    }
    $holdRows = @($holdRows | Sort-Object -Property Seconds -Descending)
}

# --- gate journal: total gate seconds --------------------------------------------------------

$gateMs = 0.0
$gateRuns = 0
foreach ($line in (Get-Content -LiteralPath $gateJournal)) {
    if (-not $line.Trim()) { continue }
    try { $row = $line | ConvertFrom-Json } catch { continue }
    if ([string]$row.status -eq 'SKIP') { continue }
    if (-not $row.timestampUtc) { continue }
    $stamp = ConvertTo-LocalMoment $row.timestampUtc
    if ($null -eq $stamp) { continue }
    if (-not (Test-InWindow $stamp)) { continue }
    $gateMs += [double]$row.elapsedMs
    $gateRuns++
}

# --- fast check logs: lag against the documented figure --------------------------------------

$documentedSeconds = $null
$datasheetRow = @(Get-Content -LiteralPath $datasheet | Where-Object { $_ -match '^\|\s*`a\.ps1 fk`\s*\|\s*([0-9.]+)\s*s\s*\|' })
if ($datasheetRow.Count -eq 1 -and $datasheetRow[0] -match '^\|\s*`a\.ps1 fk`\s*\|\s*([0-9.]+)\s*s\s*\|') {
    $documentedSeconds = [double]$Matches[1]
}

$fastDurations = @()
foreach ($log in @(Get-ChildItem -LiteralPath $fastLogDir -Filter 'check_fast_app_v2_Code_*.log' -File -ErrorAction SilentlyContinue)) {
    $head = Get-Content -LiteralPath $log.FullName -TotalCount 12 -ErrorAction SilentlyContinue
    $dateLine = $head | Where-Object { $_.StartsWith('Date: ') } | Select-Object -First 1
    # A run refused for queue reasons writes `Queued:` where an acquired run writes `Date:`; it held
    # the domain for no time at all, so it is not a duration and is dropped rather than counted as 0.
    if (-not $dateLine) { continue }
    $acquired = ConvertTo-LocalMoment $dateLine.Substring(6).Trim()
    if ($null -eq $acquired) { continue }
    if (-not (Test-InWindow $acquired)) { continue }
    $seconds = ($log.LastWriteTime - $acquired).TotalSeconds
    if ($seconds -lt 0) { continue }
    $fastDurations += $seconds
}

function Get-Median {
    param([double[]] $Values)
    if (-not $Values -or $Values.Count -eq 0) { return $null }
    $sorted = @($Values | Sort-Object)
    $middle = [int][math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return $sorted[$middle] }
    return (($sorted[$middle - 1] + $sorted[$middle]) / 2)
}

$fastMedian = Get-Median -Values $fastDurations
$fastLag = $null
if ($null -ne $fastMedian -and $null -ne $documentedSeconds) {
    $fastLag = [math]::Round($fastMedian - $documentedSeconds, 1)
}

# --- assemble ---------------------------------------------------------------------------------

$idleShare = if ($runCount -gt 0) { [math]::Round(100.0 * $idleRuns / $runCount, 1) } else { $null }
$cheapShare = if ($modelled.Count -gt 0) { [math]::Round(100.0 * $cheapRuns / $modelled.Count, 1) } else { $null }
$gateSeconds = if ($gateRuns -gt 0) { [math]::Round($gateMs / 1000.0, 1) } else { $null }
$movesValue = if ($runCount -gt 0) { $statusMoves } else { $null }

$windowText = '{0:yyyy-MM-dd HH:mm} .. {1}' -f $Since, ($(if ($Until -eq [datetime]::MaxValue) { 'now' } else { '{0:yyyy-MM-dd HH:mm}' -f $Until }))

if ($Json) {
    [ordered]@{
        window          = $windowText
        statusMoves     = $movesValue
        idleRunShare    = $idleShare
        cheapModelShare = $cheapShare
        gateSeconds     = $gateSeconds
        fastCheckLag    = $fastLag
        runCount        = $runCount
        gateExecutions  = $gateRuns
        fastCheckRuns   = $fastDurations.Count
        documented      = $documentedSeconds
        strongModel     = $StrongModel
        policies        = $policyRows
        policyWarnings  = $policyWarnings
        holdThreshold   = $holdThreshold
        holds           = $holdRows
    } | ConvertTo-Json -Depth 4
    exit 0
}

function Format-Value {
    param($Value, [string] $Suffix = '')
    if ($null -eq $Value) { return 'n/a' }
    return "$Value$Suffix"
}

$movesSuffix = if ($runCount -gt 0) { " of $runCount run(s)" } else { '' }

Write-Host "process throughput $windowText"
Write-Host ("  status moves      : {0}{1}" -f (Format-Value $movesValue), $movesSuffix)
Write-Host ("  idle run share    : {0}" -f (Format-Value $idleShare ' %'))
Write-Host ("  cheap model share : {0}" -f (Format-Value $cheapShare ' %'))
Write-Host ("  gate seconds      : {0}" -f (Format-Value $gateSeconds ' s'))
Write-Host ("  fast check lag    : {0}" -f (Format-Value $fastLag ' s'))

$holdSummary = 'n/a'
if ($null -ne $holdThreshold) {
    $holdSummary = if ($holdRows.Count -gt 0) { "$($holdRows.Count) past $holdThreshold s" } else { "none past $holdThreshold s" }
}
Write-Host ("  long domain holds : {0}" -f $holdSummary)
foreach ($hold in $holdRows) {
    $state = if ($hold.StillHeld) { 'still held' } else { 'released' }
    Write-Host ("      {0} {1} s ({2}) held by {3}, longest wait behind it {4} s" -f
        $hold.Domain, $hold.Seconds, $state, $hold.Holder, $hold.LongestWait)
}

if ($policyRows.Count -gt 0) {
    Write-Host '  model by policy and instance:'
    foreach ($row in $policyRows) {
        Write-Host ("      {0} / {1} : {2} run(s) - {3}" -f $row.Policy, $row.Instance, $row.Runs, $row.Models)
    }
}
foreach ($warning in $policyWarnings) {
    Write-Host "  WARN zero runs: $warning" -ForegroundColor Yellow
}
exit 0
