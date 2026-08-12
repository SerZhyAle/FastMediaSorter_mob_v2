# spec-next-session.ps1 - Persistent round state for /spec-next and /spec-do (S1339)
#
# Problem this solves:
#   /spec-next accumulates context across many rounds. `processed`, the
#   running session tally, `deviceOnline` and `selectedDevice` used to live
#   only in the agent's memory, so a context-threshold reset (/clear) lost
#   them and every resume had to re-derive state from scratch. This script
#   persists that state to disk so a reset is just a resume.
#
# Ownership (S1396, revised by S1437):
#   The state path used to be fixed, so two concurrent /spec-next sessions shared one file and
#   the second session's -Verb Init silently wiped the first one's processed set, tally and
#   device facts (observed 2026-08-05). S1396 answered that by stamping an owner and refusing
#   the second session; S1437 answers it properly by giving each session its own file, so both
#   run and neither refuses. Duplicate ticket pickup - the reason the refusal existed - is now
#   prevented one layer up by the ticket lease (scripts/spec_catalog/ticket-lease.ps1).
#
#   The owner is NOT an OS process - each invocation is a separate short-lived pwsh.exe and
#   nothing survives between them - so BUILD.LOCK's pid-liveness model does not apply.
#   Ownership is keyed on the agent session id, and the liveness VERDICT comes from
#   Get-AgentTicketLiveness in scripts/utils/agent-lock.ps1 rather than from a second copy of
#   the rule here. No session id in the environment -> ownership is undefined and the checks
#   below are no-ops.
#
# Storage: temp/spec-next-session.<sessionId>.json (one per session)
# Schema (one session record):
#   {
#     "round": 1,
#     "startedAt": "2026-08-01T11:40:00",
#     "threshold": 300000,
#     "deviceOnline": false,
#     "selectedDevice": null,
#     "owner": { "sessionId": "<uuid>", "host": "PC", "pid": 1234,
#                "stampedAt": "2026-08-01T11:40:00", "lastSeenAt": "2026-08-01T11:55:00" },
#     "handoffAt": null,   // set by -Verb Handoff = "parked, waiting to be resumed" (S1437);
#                          // cleared by -Verb Resume. The one signal that separates a round
#                          // stopped for a context reset from a sibling working right now.
#     "processed": [
#       { "id": "S1339", "outcome": "verified", "note": "", "firstAt": "2026-08-01T11:50:00",
#         "at": "2026-08-01T11:55:00" }
#     ],
#     "tally": { "processed": 0, "verified": 0, "blocked": 0 }
#   }
#
# Verbs:
#   -Verb Init          - start a fresh session (overwrites prior state - round memory is
#                          session-scoped, per /spec-next's own hard rule - but refuses when a
#                          live foreign owner holds the file; -Force takes it over anyway).
#   -Verb Record         - record a processed ticket, keyed by id: a repeat id updates that
#                          entry in place and the tally is recomputed, never incremented.
#   -Verb Device          - persist device-probe facts (Stage 0).
#   -Verb CheckContext    - mechanical threshold check against the live transcript.
#   -Verb Resume           - continue a prior session: bump round, adopt ownership, return
#                            -Exclude CSV + device facts + the displaced owner.
#   -Verb Report            - end-of-session summary, reconstructable after a reset.
#   -Verb Handoff             - one-screen stop block: what happened, why, what's next, recommended commands.
#
# Usage:
#   spec-next-session.ps1 -Verb Init [-Threshold 300000] [-Force] [-StaleMinutes 45]
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
#   (4 was "refused: a live foreign owner holds the state file". Retired in S1437 - the state
#    file is per session now, so there is no foreign owner to refuse. Not reused, so an old
#    caller still testing for 4 reads a code this script can no longer return.)

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

    [int]$Threshold = 300000,

    # Deprecated since S1437, accepted as a no-op: the state file is per session, so there is
    # nothing to take over. Kept so a stored command line carrying it does not fail on an
    # unknown parameter.
    [switch]$Force,

    # How long an owner's last sign of life may be before it counts as gone. Deliberately
    # generous: a session waiting on a release build writes nothing to its transcript for
    # tens of minutes, and a false "dead" verdict re-creates the very wipe this guards.
    [int]$StaleMinutes = 45
)

$ErrorActionPreference = 'Stop'

# Get-AgentTicketLiveness: the one home for "is that session still alive" (S1437).
. (Join-Path $PSScriptRoot '..\utils\agent-lock.ps1')

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$tempDir = Join-Path $root 'temp'
if (-not (Test-Path $tempDir)) {
    New-Item -ItemType Directory -Path $tempDir | Out-Null
}
# S1437: the round state is per SESSION, so two pickers can run at once. Before this it was one
# file for the whole repo, and the second session was refused at Init - a stub placed against
# ticket duplication, which the ticket lease now prevents properly.
# No session id in the environment -> same pid- fallback agent-lock.ps1 uses, so the path always
# resolves. The legacy single-file state is adopted on first Init/Resume, never orphaned.
$sessionKey = if ([string]::IsNullOrWhiteSpace($env:CLAUDE_CODE_SESSION_ID)) { "pid-$PID" } else { $env:CLAUDE_CODE_SESSION_ID }
$statePath = Join-Path $tempDir "spec-next-session.$sessionKey.json"
$legacyStatePath = Join-Path $tempDir 'spec-next-session.json'

function Import-ResumableSessionState {
    <#
        Adopt an existing round into THIS session's state file.

        Two cases, one mechanism:
          - the pre-S1437 single file (temp/spec-next-session.json), for a round in flight across
            the upgrade;
          - another session's per-session file whose owner is gone - which is the NORMAL resume
            path, because /clear gives the resuming agent a NEW session id, so the round it is
            resuming is always filed under the OLD one. Without this, /spec-next --resume after a
            threshold stop would find nothing and lose the round the state file exists to save.

        A file owned by a session that is still ALIVE is never taken: that is a sibling working
        its own round, not an orphan. Newest adoptable file wins when several qualify.
        Only ever runs when this session has no state of its own, so it cannot clobber live state.
    #>
    if (Test-Path -LiteralPath $statePath) { return $false }

    $candidates = @()
    if (Test-Path -LiteralPath $legacyStatePath) { $candidates += (Get-Item -LiteralPath $legacyStatePath) }
    $candidates += @(Get-ChildItem -LiteralPath $tempDir -Filter 'spec-next-session.*.json' -ErrorAction SilentlyContinue)

    $adoptable = @()
    foreach ($c in $candidates) {
        if ($c.FullName -eq $statePath) { continue }
        $peek = $null
        try { $peek = Get-Content -LiteralPath $c.FullName -Raw -ErrorAction Stop | ConvertFrom-Json } catch { continue }
        $parked = [bool]$peek.handoffAt
        if (-not $parked -and (Get-OwnerCheck $peek).status -eq 'foreign-live') { continue }
        $adoptable += $c
    }
    if ($adoptable.Count -eq 0) { return $false }

    $winner = ($adoptable | Sort-Object LastWriteTime -Descending)[0]
    try {
        Move-Item -LiteralPath $winner.FullName -Destination $statePath -Force
        return $true
    }
    catch {
        # Non-fatal: a fresh round is a worse outcome than a failed move, but not a broken one.
        return $false
    }
}

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

function Get-SessionTranscript([string]$SessionId) {
    if (-not $SessionId) { return $null }
    $projectsRoot = Join-Path $env:USERPROFILE '.claude\projects'
    return Get-ChildItem -Path $projectsRoot -Recurse -Filter "$SessionId.jsonl" -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

function New-OwnerStamp {
    $now = (Get-Date).ToString('s')
    return [PSCustomObject]@{
        sessionId  = $env:CLAUDE_CODE_SESSION_ID
        host       = $env:COMPUTERNAME
        pid        = $PID
        stampedAt  = $now
        lastSeenAt = $now
    }
}

function Set-OwnerStamp($state) {
    $owner = New-OwnerStamp
    if ($state.PSObject.Properties['owner']) { $state.owner = $owner }
    else { $state | Add-Member -NotePropertyName owner -NotePropertyValue $owner }
    return $state
}

function Get-OwnerCheck($state) {
    # Verdict on who owns $state relative to this session:
    #   none          - no stamp (absent file, or one written before S1396)
    #   self          - this session
    #   foreign-live  - another session, still showing signs of life
    #   foreign-stale - another session, gone
    #   undetermined  - we have no session id, so "mine" and "theirs" are indistinguishable
    $owner = if ($state) { $state.owner } else { $null }
    # 'none' stays local: it describes an ABSENT state file, which is not a statement about any
    # session's liveness and so is not something the shared helper models.
    if (-not $owner -or -not $owner.sessionId) {
        return [PSCustomObject]@{ status = 'none'; owner = $null; ageMinutes = $null }
    }

    # S1437: the verdict comes from agent-lock.ps1's Get-AgentTicketLiveness - the single home
    # for "what counts as alive". A second copy of that rule here drifted from it by definition.
    $transcript = Get-SessionTranscript -SessionId $owner.sessionId
    $shim = [pscustomobject]@{
        sessionId      = $owner.sessionId
        transcriptPath = $(if ($transcript) { $transcript.FullName } else { $null })
        enqueuedAt     = $(
            if ($owner.lastSeenAt) {
                try { [DateTimeOffset]::new([datetime]::Parse($owner.lastSeenAt)).ToUnixTimeMilliseconds() } catch { $null }
            } else { $null }
        )
    }
    $status = Get-AgentTicketLiveness -Ticket $shim -StaleMinutes $StaleMinutes

    # ageMinutes is display-only (Format-Owner), so it is derived here rather than asked of the
    # helper, which returns a verdict and not a measurement.
    $ageMinutes = $null
    if ($status -eq 'self') { $ageMinutes = 0 }
    else {
        $lastSeen = if ($transcript) { $transcript.LastWriteTime } else { $null }
        if ($null -eq $lastSeen -and $owner.lastSeenAt) {
            try { $lastSeen = [datetime]::Parse($owner.lastSeenAt) } catch { $lastSeen = $null }
        }
        if ($lastSeen) { $ageMinutes = [math]::Round(((Get-Date) - $lastSeen).TotalMinutes, 1) }
    }
    return [PSCustomObject]@{ status = $status; owner = $owner; ageMinutes = $ageMinutes }
}

function Format-Owner($check) {
    $o = $check.owner
    $age = if ($null -eq $check.ageMinutes) { 'unknown' } else { "$($check.ageMinutes) min ago" }
    return "session $($o.sessionId) on $($o.host) (pid $($o.pid), last seen $age)"
}

function Update-OwnerOnWrite($state, $check) {
    # Mutating verbs other than Init never refuse (soft CODE.LOCK model): refusing mid-round
    # would drop the record of a ticket the loop just finished, which costs more than the
    # warning. A live foreign owner keeps its stamp so the file still names who started it.
    switch ($check.status) {
        'self' { $state.owner.lastSeenAt = (Get-Date).ToString('s'); return $state }
        'foreign-live' {
            Write-Warning "spec-next-session: state file belongs to another session - $(Format-Owner $check); writing into it anyway, its tally will include this row"
            return $state
        }
        'undetermined' { return $state }
        default { return (Set-OwnerStamp $state) }
    }
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
    $transcript = Get-SessionTranscript -SessionId $sessionId
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
        # S1437: no refusal here any more. The state file is this session's own, so there is no
        # foreign owner to collide with; duplicate ticket pickup is prevented by the lease
        # (scripts/spec_catalog/ticket-lease.ps1), which is the thing the old refusal stood in for.
        # No adoption here on purpose: Init means a FRESH round and overwrites state anyway.
        # Continuing an existing round is -Verb Resume's job, and that is where adoption lives.
        $state = [PSCustomObject]@{
            round          = 1
            startedAt      = (Get-Date).ToString('s')
            threshold      = $Threshold
            deviceOnline   = $false
            selectedDevice = $null
            owner          = (New-OwnerStamp)
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
        # Reported as a JSON field, never as a console line: this verb's stdout is a contract the
        # driver parses, and a prose line in front of it turns a working resume into a parse error.
        $adoptedLegacy = [bool](Import-ResumableSessionState)
        $state = Read-State
        if (-not $state) {
            Write-Error "spec-next-session: no session state to resume - run -Verb Init first" -ErrorAction Continue
            exit 1
        }
        # Resume adopts ownership unconditionally and never refuses: after the /clear this verb
        # exists to survive, the resuming session carries a NEW session id while the previous
        # owner's transcript was written seconds ago, so any liveness test would read it as a
        # live foreigner and refuse the one path the state file was built for. Naming the
        # displaced owner is the honest half - a genuine sibling steal stays visible.
        $check = Get-OwnerCheck $state
        $previousOwner = if ($check.status -in @('foreign-live', 'foreign-stale')) { $check.owner.sessionId } else { $null }
        $state.round = [int]$state.round + 1
        # The round is picked up, so it is no longer parked - a stale marker would let a third
        # session adopt it out from under this one.
        if ($state.PSObject.Properties['handoffAt']) { $state.handoffAt = $null }
        $state = Set-OwnerStamp $state
        Write-State $state
        $processedIds = @($state.processed | ForEach-Object { $_.id })
        $out = [PSCustomObject]@{
            excludeCsv     = ($processedIds -join ',')
            deviceOnline   = $state.deviceOnline
            selectedDevice = $state.selectedDevice
            round          = $state.round
            previousOwner  = $previousOwner
            adoptedLegacy  = $adoptedLegacy
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
        $state = Update-OwnerOnWrite $state (Get-OwnerCheck $state)
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
        $state = Update-OwnerOnWrite $state (Get-OwnerCheck $state)
        $now = (Get-Date).ToString('s')
        $entries = @($state.processed)
        $existing = $entries | Where-Object { $_.id -eq $Id } | Select-Object -First 1
        if ($existing) {
            # A ticket changing status twice in one session is the ordinary pipeline shape
            # (advanced when /spec-dev lands it, verified once /spec-check passes). Appending a
            # second row counted it twice, and that inflated count is exactly what the final
            # report and the handoff print - so the entry is keyed by id and updated in place.
            if (-not $existing.PSObject.Properties['firstAt']) {
                $existing | Add-Member -NotePropertyName firstAt -NotePropertyValue $existing.at
            }
            $existing.outcome = $Outcome
            $existing.note = $Note
            $existing.at = $now
            $recordAction = 'updated'
        }
        else {
            $entries += [PSCustomObject]@{
                id      = $Id
                outcome = $Outcome
                note    = $Note
                firstAt = $now
                at      = $now
            }
            $recordAction = 'recorded'
        }
        $state.processed = $entries
        # Recomputed, never incremented: an in-place update must not move the counters, and a
        # recount from the collapsed list is the only form that stays right for both branches.
        $state.tally.processed = $entries.Count
        $state.tally.verified = @($entries | Where-Object { $_.outcome -eq 'verified' }).Count
        $state.tally.blocked = @($entries | Where-Object { $_.outcome -eq 'blocked' }).Count
        Write-State $state
        Write-Output "spec-next-session: $recordAction $Id ($Outcome)"
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

        # S1437: mark the round PARKED for pickup. After a context reset the resuming agent
        # carries a new session id, and this session's transcript is seconds old - so liveness
        # alone cannot tell "just stopped, waiting to be resumed" from "a sibling working right
        # now". This stamp is the difference, and -Verb Resume adopts only on it (or on an owner
        # that has genuinely gone stale). Without it, resume either loses the round or steals a
        # sibling's - there is no third answer available from liveness.
        $handoffAt = (Get-Date).ToString('s')
        if ($state.PSObject.Properties['handoffAt']) { $state.handoffAt = $handoffAt }
        else { $state | Add-Member -NotePropertyName handoffAt -NotePropertyValue $handoffAt }
        Write-State $state

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
        Write-Output "  1. /clear - all state is on disk ($statePath); a /compact summary would only re-carry what the files already hold."
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
