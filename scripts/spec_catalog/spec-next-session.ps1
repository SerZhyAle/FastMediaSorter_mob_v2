# spec-next-session.ps1 - Persistent round state for /spec-next and /spec-do (S1339)
#
# Problem this solves:
#   /spec-next accumulates context across many rounds. `processed`, the
#   running session tally, `deviceOnline` and `selectedDevice` used to live
#   only in the agent's memory, so a context-threshold reset (/clear) lost
#   them and every resume had to re-derive state from scratch. This script
#   persists that state to disk so a reset is just a resume.
#
# Storage: temp/spec-next-session.json
# Schema (one session record):
#   {
#     "round": 1,
#     "startedAt": "2026-08-01T11:40:00",
#     "threshold": 300000,
#     "deviceOnline": false,
#     "selectedDevice": null,
#     "processed": [
#       { "id": "S1339", "outcome": "verified", "note": "", "at": "2026-08-01T11:55:00" }
#     ],
#     "tally": { "processed": 0, "verified": 0, "blocked": 0 }
#   }
#
# Verbs:
#   -Verb Init          - start a fresh session (overwrites any prior state - round memory
#                          is session-scoped, per /spec-next's own hard rule).
#   -Verb Record         - append a processed ticket + update the tally.
#   -Verb Device          - persist device-probe facts (Stage 0).
#   -Verb CheckContext    - mechanical threshold check against the live transcript.
#   -Verb Resume           - continue a prior session: bump round, return -Exclude CSV + device facts.
#   -Verb Report            - end-of-session summary, reconstructable after a reset.
#   -Verb Handoff             - one-screen stop block: what happened, why, what's next, recommended commands.
#
# Usage:
#   spec-next-session.ps1 -Verb Init [-Threshold 300000]
#   spec-next-session.ps1 -Verb Record -Id Sxxxx -Outcome <advanced|verified|blocked|skipped> [-Note "text"]
#   spec-next-session.ps1 -Verb Device -Online <true|false> [-SelectedDevice <id>]
#   spec-next-session.ps1 -Verb CheckContext [-Threshold N]
#   spec-next-session.ps1 -Verb Resume
#   spec-next-session.ps1 -Verb Report
#   spec-next-session.ps1 -Verb Handoff
#
# Exit codes (S1070 contract):
#   0 - ok.
#   1 - error (missing state file where one is required, write failure).
#   2 - cannot verify (bad -Id on Record; CheckContext could not determine tokens).
#   3 - threshold crossed (CheckContext only).

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Init', 'Record', 'Device', 'CheckContext', 'Resume', 'Report', 'Handoff')]
    [string]$Verb,

    [string]$Id,

    [ValidateSet('advanced', 'verified', 'blocked', 'skipped')]
    [string]$Outcome,

    [string]$Note = '',

    [string]$Online,

    [string]$SelectedDevice,

    [int]$Threshold = 300000
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$tempDir = Join-Path $root 'temp'
if (-not (Test-Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir | Out-Null
}
$statePath = Join-Path $tempDir 'spec-next-session.json'

function Read-State {
    if (-not (Test-Path $statePath)) { return $null }
    try {
        $raw = Get-Content -Path $statePath -Raw
        if (-not $raw -or $raw.Trim() -eq '') { return $null }
        return $raw | ConvertFrom-Json
    }
    catch {
        Write-Warning "spec-next-session: state file malformed, treating as absent: $_"
        return $null
    }
}

function Write-State($state) {
    $json = $state | ConvertTo-Json -Depth 6
    Set-Content -Path $statePath -Value $json -Encoding utf8NoBOM
}

function Get-ContextCheck([int]$ThresholdOverride, [bool]$ThresholdExplicit) {
    # Mechanical threshold check (strategic S1339 §3): the newest assistant record's
    # cache_read_input_tokens in the LIVE session transcript is the current carried
    # context. Never sums across records (a single API response repeats the same
    # usage object across several JSONL lines - only the latest value matters here,
    # not a total), so no requestId dedup is needed, unlike cost-mining aggregation.
    $sessionId = $env:CLAUDE_CODE_SESSION_ID
    if (-not $sessionId) {
        return [PSCustomObject]@{ ok = $false; reason = 'no session id in environment (CLAUDE_CODE_SESSION_ID)' }
    }
    $projectsRoot = Join-Path $env:USERPROFILE '.claude\projects'
    $transcript = Get-ChildItem -Path $projectsRoot -Recurse -Filter "$sessionId.jsonl" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $transcript) {
        return [PSCustomObject]@{ ok = $false; reason = "no transcript file for session $sessionId" }
    }
    $tokens = $null
    foreach ($line in [System.IO.File]::ReadLines($transcript.FullName)) {
        try {
            $obj = $line | ConvertFrom-Json -ErrorAction Stop
        }
        catch {
            continue
        }
        if ($obj.type -eq 'assistant' -and $null -ne $obj.message.usage.cache_read_input_tokens) {
            $tokens = [int64]$obj.message.usage.cache_read_input_tokens
        }
    }
    if ($null -eq $tokens) {
        return [PSCustomObject]@{ ok = $false; reason = 'no assistant usage record yet - session just started' }
    }
    $effectiveThreshold = $ThresholdOverride
    if (-not $ThresholdExplicit) {
        $state = Read-State
        if ($state -and $state.threshold) { $effectiveThreshold = [int]$state.threshold }
    }
    $crossed = $tokens -ge $effectiveThreshold
    return [PSCustomObject]@{ ok = $true; tokens = $tokens; threshold = $effectiveThreshold; crossed = $crossed }
}

switch ($Verb) {
    'Init' {
        $state = [PSCustomObject]@{
            round          = 1
            startedAt      = (Get-Date).ToString('s')
            threshold      = $Threshold
            deviceOnline   = $false
            selectedDevice = $null
            processed      = @()
            tally          = [PSCustomObject]@{ processed = 0; verified = 0; blocked = 0 }
        }
        try {
            Write-State $state
        }
        catch {
            Write-Error "spec-next-session: failed to write state file: $_" -ErrorAction Continue
            exit 1
        }
        Write-Output "spec-next-session: initialized ($statePath)"
        exit 0
    }
    'Resume' {
        $state = Read-State
        if (-not $state) {
            Write-Error "spec-next-session: no session state to resume - run -Verb Init first" -ErrorAction Continue
            exit 1
        }
        $state.round = [int]$state.round + 1
        Write-State $state
        $processedIds = @($state.processed | ForEach-Object { $_.id })
        $out = [PSCustomObject]@{
            excludeCsv     = ($processedIds -join ',')
            deviceOnline   = $state.deviceOnline
            selectedDevice = $state.selectedDevice
            round          = $state.round
        }
        $out | ConvertTo-Json -Compress
        exit 0
    }
    'Device' {
        # -Online is a string, not [bool]: this runs across both the PowerShell tool
        # ($true/$false get stringified to "True"/"False" crossing the pwsh.exe -File
        # process boundary) and Bash (`-Online true`) - a typed [bool] parameter fails
        # that cross-process conversion inconsistently, so parse it manually instead.
        if ($null -eq $Online -or $Online -eq '') {
            Write-Error "spec-next-session: -Online is required for -Verb Device (true|false)" -ErrorAction Continue
            exit 2
        }
        $state = Read-State
        if (-not $state) {
            Write-Error "spec-next-session: no session state - run -Verb Init or -Verb Resume first" -ErrorAction Continue
            exit 1
        }
        $onlineBool = [bool]($Online -match '^(?i:true|1)$')
        $state.deviceOnline = $onlineBool
        if ($SelectedDevice) { $state.selectedDevice = $SelectedDevice }
        Write-State $state
        Write-Output "spec-next-session: device online=$onlineBool selected=$SelectedDevice"
        exit 0
    }
    'CheckContext' {
        $thresholdExplicit = $PSBoundParameters.ContainsKey('Threshold')
        $result = Get-ContextCheck -ThresholdOverride $Threshold -ThresholdExplicit $thresholdExplicit
        if (-not $result.ok) {
            Write-Error "spec-next-session: cannot verify context - $($result.reason)" -ErrorAction Continue
            exit 2
        }
        [PSCustomObject]@{
            tokens    = $result.tokens
            threshold = $result.threshold
            crossed   = $result.crossed
        } | ConvertTo-Json -Compress
        if ($result.crossed) { exit 3 } else { exit 0 }
    }
    'Record' {
        if (-not $Id -or $Id -notmatch '^S\d{4}$') {
            Write-Error "spec-next-session: -Id must match S#### (got '$Id')" -ErrorAction Continue
            exit 2
        }
        if (-not $Outcome) {
            Write-Error "spec-next-session: -Outcome is required for -Verb Record" -ErrorAction Continue
            exit 2
        }
        $state = Read-State
        if (-not $state) {
            Write-Error "spec-next-session: no session state - run -Verb Init or -Verb Resume first" -ErrorAction Continue
            exit 1
        }
        $entry = [PSCustomObject]@{
            id      = $Id
            outcome = $Outcome
            note    = $Note
            at      = (Get-Date).ToString('s')
        }
        $state.processed = @($state.processed) + $entry
        $state.tally.processed = [int]$state.tally.processed + 1
        if ($Outcome -eq 'verified') { $state.tally.verified = [int]$state.tally.verified + 1 }
        if ($Outcome -eq 'blocked') { $state.tally.blocked = [int]$state.tally.blocked + 1 }
        Write-State $state
        Write-Output "spec-next-session: recorded $Id ($Outcome)"
        exit 0
    }
    'Handoff' {
        $state = Read-State
        if (-not $state) {
            Write-Error "spec-next-session: no session state - run -Verb Init first" -ErrorAction Continue
            exit 1
        }
        $pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
            "$env:ProgramFiles\PowerShell\7\pwsh.exe"
        }
        else {
            'pwsh'
        }
        $processed = @($state.processed)
        $processedIds = @($processed | ForEach-Object { $_.id })

        Write-Output 'spec-next: context-threshold stop'
        Write-Output ''
        # A session can accumulate many single-ticket rounds before one threshold
        # stop (one /spec-all delegation per round, not per batch) - listing every
        # processed ticket would blow past "one screen" (strategic §4.4). Show only
        # the most recent few; the tally line still covers the whole session.
        $recentCap = 5
        $recent = @($processed | Select-Object -Last $recentCap)
        $olderCount = $processed.Count - $recent.Count
        Write-Output "What just happened (last $($recent.Count) of $($processed.Count)):"
        foreach ($p in $recent) {
            Write-Output "  $($p.id) - $($p.outcome)"
        }
        if ($olderCount -gt 0) {
            Write-Output "  .. and $olderCount more earlier this session"
        }
        Write-Output "  tally: processed $($state.tally.processed), verified $($state.tally.verified), blocked $($state.tally.blocked)"
        Write-Output ''

        # Never a percentage (S1338 package B finding) - absolute tokens only.
        $ctx = Get-ContextCheck -ThresholdOverride $state.threshold -ThresholdExplicit $true
        Write-Output 'Why it stopped:'
        if ($ctx.ok) {
            Write-Output "  context $($ctx.tokens) tokens >= threshold $($ctx.threshold) tokens"
        }
        else {
            Write-Output "  context: unavailable ($($ctx.reason))"
        }
        Write-Output ''

        Write-Output 'What is next in the queue:'
        try {
            $preflightPath = Join-Path $PSScriptRoot 'spec-next-preflight.ps1'
            $preflightRaw = & $pwshExe -NoProfile -File $preflightPath -Exclude $processedIds -Format json 2>$null
            $preflight = $preflightRaw | ConvertFrom-Json -ErrorAction Stop
            if ($preflight.selected) {
                Write-Output "  $($preflight.selected.id) - $($preflight.selected.name)"
            }
            else {
                Write-Output '  backlog exhausted'
            }
        }
        catch {
            Write-Output '  could not determine'
        }
        Write-Output ''

        Write-Output 'Recommended commands, in order:'
        Write-Output '  1. /clear - all state is on disk (temp/spec-next-session.json); a /compact summary would only re-carry what the files already hold.'
        Write-Output '  2. /spec-next --resume - continue bounded.'
        Write-Output '  3. /spec-do --resume - continue unbounded (deliberate escape hatch, spends tokens on purpose).'
        Write-Output ''

        $blockedCount = @($processed | Where-Object { $_.outcome -eq 'blocked' }).Count
        $needTestCount = 0
        try {
            $searchPath = Join-Path $PSScriptRoot 'search.ps1'
            $searchRaw = & $pwshExe -NoProfile -File $searchPath -Status BlockNeedUserTest -Format json 2>$null
            $needTest = $searchRaw | ConvertFrom-Json -ErrorAction Stop
            $needTestCount = @($needTest).Count
        }
        catch {
            $needTestCount = -1
        }
        Write-Output 'What needs the human:'
        if ($blockedCount -eq 0 -and $needTestCount -le 0) {
            Write-Output '  nothing waiting on you this round'
        }
        else {
            Write-Output "  blocked this round: $blockedCount"
            if ($needTestCount -ge 0) { Write-Output "  BlockNeedUserTest backlog: $needTestCount" }
        }
        exit 0
    }
    'Report' {
        $state = Read-State
        if (-not $state) {
            Write-Error "spec-next-session: no session state - run -Verb Init first" -ErrorAction Continue
            exit 1
        }
        Write-Output "spec-next: session state (round $($state.round))"
        Write-Output ''
        Write-Output 'Processed this run:'
        foreach ($p in @($state.processed)) {
            Write-Output "  $($p.id) - $($p.outcome)"
        }
        Write-Output ''
        Write-Output "tally: processed: $($state.tally.processed), verified: $($state.tally.verified), blocked: $($state.tally.blocked)"
        exit 0
    }
    default {
        Write-Error "spec-next-session: verb '$Verb' not yet implemented" -ErrorAction Continue
        exit 1
    }
}
