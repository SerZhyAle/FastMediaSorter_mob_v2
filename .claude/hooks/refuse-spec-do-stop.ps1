<#
refuse-spec-do-stop.ps1 - Claude Code Stop hook (project).

Refuses to let a session end its turn while a /spec-do endless marker is armed for it.

Why this exists: /spec-do's contract is that only the operator ends it - not a budget, not a clock,
not an empty queue. That contract was prose, and prose is the 1-8% tier: a loop ended itself at a
round boundary and asked the operator to compact and say "continue", which is a stop wearing a
report's clothes. The Stop event is the only place where "the model decided it was finished" is
mechanically observable, so it is the only place the contract can be enforced rather than asked for.

Arming and disarming are the operator-facing half - scripts/utils/spec-do-marker.ps1. This hook
never arms anything; it only reads what a loop armed, and it fails open on anything it cannot read,
because a session that can never end its turn because a hook threw is worse than an ungated rule.

Three things make it safe to hold a session open indefinitely:
  - The block is scoped to the session that claimed the marker, so an unrelated session in the same
    working tree is untouched, and a marker left behind by a killed session expires (-StaleHours).
  - An idle wait in flight ALLOWS the stop: temp/SPEC-DO.WORK-*.json with outcome 'waiting' means a
    background waiter is running and its completion is the wake signal. Blocking there would force
    the loop to spin instead of waiting, which is the opposite of what the waiter exists for.
  - A rapid-fire streak of blocks escalates the instruction rather than surrendering: the loop is
    told to go to Stage 6i and launch the waiter, which turns a spin back into a wait.

Contract:
  stdin  - the Stop hook payload (session_id, stop_hook_active, ..).
  stdout - {"decision":"block","reason":".."} to refuse the stop; nothing at all to allow it.

stop_hook_active is deliberately ignored. It exists so an ordinary hook does not loop forever;
looping forever is this hook's entire purpose, and the operator's disarm is the exit.

Exit codes (CLAUDE.md Rule 7):
  0  always - the verdict travels in stdout, and a session must never fail to end because this
     hook errored.

Registered per-project in .claude/settings.json. Inventory: docs/AGENT_HOOKS.md.
#>

[CmdletBinding()]
param(
    # Overridable so the contract tests can drive a fixture tree instead of the live repository.
    [string]$RepoRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)),

    # A marker whose session is gone leaves the repository poisoned for every later session, so an
    # untouched marker expires. Long enough to outlive an overnight run, short enough to self-heal.
    [int]$StaleHours = 24,

    # A 'waiting' work marker older than this is treated as a dead waiter, not an idle wait.
    [int]$WaitStaleMinutes = 15
)

function Approve-Stop { exit 0 }

try {
    $raw = [Console]::In.ReadToEnd()
    $payload = if ([string]::IsNullOrWhiteSpace($raw)) { $null } else { $raw | ConvertFrom-Json }
}
catch { Approve-Stop }

$sessionId = if ($payload -and $payload.session_id) { [string]$payload.session_id } else { '' }

$tempDir = Join-Path $RepoRoot 'temp'
if (-not (Test-Path -LiteralPath $tempDir)) { Approve-Stop }

try {
    $markerFiles = @(Get-ChildItem -LiteralPath $tempDir -Filter 'SPEC-DO.ACTIVE-*.json' -File -ErrorAction Stop |
        Sort-Object LastWriteTime -Descending)
}
catch { Approve-Stop }

if ($markerFiles.Count -eq 0) { Approve-Stop }

function Read-Json([string]$Path) {
    try { return (Get-Content -LiteralPath $Path -Raw -ErrorAction Stop | ConvertFrom-Json) }
    catch { return $null }
}

$now = Get-Date
$mine = $null
$minePath = ''
$unclaimed = $null
$unclaimedPath = ''

foreach ($f in $markerFiles) {
    $m = Read-Json $f.FullName
    if ($null -eq $m) { continue }

    $armedAt = $now
    try { if ($m.armedAt) { $armedAt = [datetime]::Parse($m.armedAt) } } catch { }

    $ownedByMe = ($m.sessionId -and $sessionId -and ($m.sessionId -eq $sessionId))

    if (-not $ownedByMe -and ($now - $armedAt).TotalHours -ge $StaleHours) {
        # Belongs to a session that is gone. Purge rather than inherit: inheriting would hold a
        # brand-new session open for work it never started.
        try { Remove-Item -LiteralPath $f.FullName -Force } catch { }
        continue
    }

    if ($ownedByMe) { $mine = $m; $minePath = $f.FullName; break }
    if (-not $m.sessionId -and $null -eq $unclaimed) { $unclaimed = $m; $unclaimedPath = $f.FullName }
}

$claimedAt = $null
if ($null -eq $mine) {
    # Claim the newest unclaimed marker: the loop that armed it is the session now ending a turn.
    if ($null -eq $unclaimed -or [string]::IsNullOrWhiteSpace($sessionId)) { Approve-Stop }
    $mine = $unclaimed
    $minePath = $unclaimedPath
    $claimedAt = $now.ToString('s')
}

# --- an idle wait in flight allows the stop ------------------------------------------------------

try {
    foreach ($w in @(Get-ChildItem -LiteralPath $tempDir -Filter 'SPEC-DO.WORK-*.json' -File -ErrorAction Stop)) {
        $work = Read-Json $w.FullName
        if ($null -eq $work -or $work.outcome -ne 'waiting') { continue }
        $checked = $w.LastWriteTime
        try { if ($work.checkedAt) { $checked = [datetime]::Parse($work.checkedAt) } } catch { }
        if (($now - $checked).TotalMinutes -lt $WaitStaleMinutes) { Approve-Stop }
    }
}
catch { }

# --- refuse ---------------------------------------------------------------------------------------

# Read every field defensively and write a whole object back: a marker written by an older version,
# or by hand, is missing properties, and assigning to an absent property throws - which would put a
# PowerShell error in front of the model on a turn that is supposed to carry one instruction.
function Get-Field($Obj, [string]$Name, $Fallback) {
    $p = $Obj.PSObject.Properties[$Name]
    if ($null -eq $p -or $null -eq $p.Value) { return $Fallback }
    return $p.Value
}

# ConvertFrom-Json turns an ISO timestamp into a [datetime], and stringifying that back writes the
# machine's locale format - so a marker rewritten here would come back in a shape the next read has
# to guess at. Normalize every timestamp to 's' on the way out.
function Format-Stamp($Value, [string]$Fallback) {
    if ($null -eq $Value -or "$Value" -eq '') { return $Fallback }
    if ($Value -is [datetime]) { return $Value.ToString('s') }
    try { return ([datetime]::Parse([string]$Value)).ToString('s') } catch { return [string]$Value }
}

$blocks = 0
try { $blocks = [int](Get-Field $mine 'blocks' 0) } catch { }
$rapid = 0
try { $rapid = [int](Get-Field $mine 'rapidBlocks' 0) } catch { }

$lastBlockAt = [string](Get-Field $mine 'lastBlockAt' '')
$sinceLast = [double]::MaxValue
try { if ($lastBlockAt) { $sinceLast = ($now - [datetime]::Parse($lastBlockAt)).TotalSeconds } } catch { }

$rapid = if ($sinceLast -lt 20) { $rapid + 1 } else { 0 }

if (-not $claimedAt) { $claimedAt = Format-Stamp (Get-Field $mine 'claimedAt' $null) $now.ToString('s') }
$token = [string](Get-Field $mine 'token' 'unknown')

$updated = [ordered]@{
    token       = $token
    reason      = [string](Get-Field $mine 'reason' '')
    armedAt     = Format-Stamp (Get-Field $mine 'armedAt' $null) $now.ToString('s')
    sessionId   = $sessionId
    claimedAt   = $claimedAt
    blocks      = $blocks + 1
    rapidBlocks = $rapid
    lastBlockAt = $now.ToString('s')
}

try { $updated | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $minePath -Encoding UTF8 } catch { }

$disarm = "pwsh -NoProfile -File scripts/utils/spec-do-marker.ps1 -Action disarm -Token $token"

if ($rapid -ge 5) {
    # Bouncing off this hook without doing work is the one failure mode that costs tokens for
    # nothing. The answer is still not a stop - it is the wait the loop skipped.
    $reason = @(
        "/spec-do is armed (token $token) and you have ended $($rapid + 1) turns in a row with almost no work between them.",
        'Stop re-answering and go to Stage 6i step 4: launch scripts/utils/wait-for-ticket-work.ps1 as a BACKGROUND task,',
        'then end the turn - a live waiter marker allows the stop, and its completion is your wake signal.',
        "If the operator asked you to stop, disarm first: $disarm"
    ) -join ' '
}
else {
    $reason = @(
        "/spec-do is armed (token $token): only the operator ends this loop.",
        'Do not report a budget, a context size, an elapsed time or a round boundary, and do not ask for a compaction,',
        'a /clear or a "continue" message - none of those is a stop condition here.',
        'Resume the loop now: next Stage 1 call, or Stage 6i if the queue was empty (device drain, tally, re-admit, then',
        'launch the background waiter and end the turn - that is the sanctioned way to be idle).',
        "If the operator has explicitly asked you to stop, disarm the loop and then finish: $disarm"
    ) -join ' '
}

$verdict = [ordered]@{ decision = 'block'; reason = $reason }
[Console]::Out.Write(($verdict | ConvertTo-Json -Compress -Depth 3))

exit 0
