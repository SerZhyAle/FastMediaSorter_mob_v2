#requires -Version 7.0
<#
.SYNOPSIS
    S2872 - the closure check a runtime runs before it says "done".

.DESCRIPTION
    Claude Code refuses a whole class of mistakes at the tool call. Outside it nothing refuses
    anything, so this script asks once, at the end, what state the session is about to leave
    behind. The imperatives themselves live in docs/NON_CLAUDE_RUNTIME_RULES.md; this is their
    backstop, and it deliberately catches late rather than not at all.

    STATE ONLY, never command history. No non-Claude runtime writes its own commands anywhere
    this repository can read - Claude Code keeps a transcript and is exactly not the audience -
    so there is nothing to parse and a "did you background a gate" check cannot exist. What DOES
    survive the call that wrote it is the coordination state, and every check below reads it:

      1. FMS_AGENT_ID is set. Without it the identity chain falls through to a pid that lives for
         one command, and one session wrote itself as 46 different agents in an hour with its
         lease dead from the first minute (2026-09-02).
      1b. No FMS_AGENT_* variable is persisted in the User or Machine scope. A persisted value is
         inherited by every later session on the machine, whatever its runtime (S3149).
      2. This identity posted a kind=session start line. It is the one fact separating "died
         mid-phase" from "finished and left", and no lock, queue or lease records it.
      3. No lock domain is still held by this identity. A leaked lock refuses every sibling.
      4. No lock queue still carries this identity's ticket. An abandoned intent holds a place
         nobody will ever take, and Rule 23 makes withdrawing it the asker's job.
      5. No ticket lease is still claimed by this identity. A lease outliving its work makes the
         ticket unpickable in PLAN/RELEASE_QUEUE.md.

    It judges nothing about the working TREE. That is scripts/post-change.ps1 for a change and
    .\a.ps1 fg for the fast static gates; re-implementing either here would produce a third copy
    of the gate set with its own drift. The verdict names both instead.

    It mutates nothing - it releases no lock, withdraws no ticket and posts no chat line. A
    preflight that silently repairs what it found teaches the caller nothing and hides the leak
    from the next run.

.PARAMETER Json
    Emit a machine-readable summary on stdout instead of the human report.

.PARAMETER Quiet
    Print only failing and advisory checks, plus the verdict line.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/preflight-checks.ps1

.NOTES
    Exit codes:
      0 - every state check passed. Advisories may still have printed.
      1 - at least one state check failed. Each failure names its own remedy.
      2 - could not verify: the coordination harness could not be loaded.
#>
[CmdletBinding()]
param(
    [switch]$Json,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path

try {
    . (Join-Path $repoRoot 'scripts/utils/agent-lock.ps1')
    . (Join-Path $repoRoot 'scripts/utils/agent-lock-domains.ps1')
}
catch {
    Write-Error "preflight: could not load the coordination harness - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Expected,
        [Parameter(Mandatory)][string]$Actual,
        [Parameter(Mandatory)][ValidateSet('PASS', 'FAIL', 'ADVISORY')][string]$Result,
        [string]$Remedy = ''
    )
    $checks.Add([pscustomobject]@{
            name     = $Name
            expected = $Expected
            actual   = $Actual
            result   = $Result
            remedy   = $Remedy
        })
}

# --- who is asking ------------------------------------------------------------
# Resolved through the same chain every lock, queue and lease record uses, so this
# script's view of "mine" cannot disagree with theirs.
try { $sessionId = Get-AgentSessionId }
catch {
    Write-Error "preflight: could not resolve the agent identity - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

# --- 1. identity --------------------------------------------------------------

$explicitId = [string]$env:FMS_AGENT_ID
if ([string]::IsNullOrWhiteSpace($explicitId)) {
    Add-Check -Name 'agent identity' -Expected 'FMS_AGENT_ID set' -Actual 'unset' -Result 'FAIL' `
        -Remedy 'Set FMS_AGENT_ID once for the whole session before anything else, then re-run. AGENTS.md section 9.1.'
}
else {
    Add-Check -Name 'agent identity' -Expected 'FMS_AGENT_ID set' -Actual $explicitId -Result 'PASS'
}

# --- 1b. persisted identity ---------------------------------------------------
# A runtime once obeyed "set FMS_AGENT_ID first" with a User-scope write. Every later process on
# the machine inherited it, Claude Code included, and signed its chat lines and leases as that one
# Codex session - check 1 above PASSed throughout, because the variable WAS set. The identity is
# per session by contract, so any persisted FMS_AGENT_* value is a leak, whoever wrote it.
function Get-PersistedAgentVariable {
    $override = [string]$env:FMS_PREFLIGHT_PERSISTED_ENV
    if (-not [string]::IsNullOrWhiteSpace($override)) {
        # Contract-suite seam: 'Scope:NAME=value;..' stands in for the real scopes, so no test
        # ever writes the registry a live session reads.
        return @($override -split ';' | ForEach-Object {
                if ($_ -match '^(User|Machine):(FMS_AGENT_\w+)=') { "$($Matches[1]):$($Matches[2])" }
            })
    }
    $found = @()
    foreach ($scope in 'User', 'Machine') {
        # Empty on a platform without persisted scopes, which is the correct answer there.
        $vars = [Environment]::GetEnvironmentVariables([EnvironmentVariableTarget]$scope)
        foreach ($name in @($vars.Keys)) {
            # An empty value is inherited as nothing, so it is not an identity and not a finding.
            if ([string]$name -like 'FMS_AGENT_*' -and -not [string]::IsNullOrWhiteSpace([string]$vars[$name])) {
                $found += "${scope}:$name"
            }
        }
    }
    return $found
}

$persisted = @(Get-PersistedAgentVariable)
if ($persisted.Count -gt 0) {
    # Not SetEnvironmentVariable(.., $null, ..): PowerShell hands .NET an empty string for $null,
    # which writes an empty value instead of deleting the name (measured 2026-09-15).
    Add-Check -Name 'persisted identity' -Expected 'no FMS_AGENT_* in the User or Machine scope' -Actual ($persisted -join ', ') -Result 'FAIL' `
        -Remedy "Remove-ItemProperty -Path HKCU:\Environment -Name <NAME> for a User value (Machine: 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Environment', elevated), then restart the host that inherited it; set the identity in the process only (`$env:FMS_AGENT_ID = '..'). AGENTS.md section 9.1."
}
else {
    Add-Check -Name 'persisted identity' -Expected 'no FMS_AGENT_* in the User or Machine scope' -Actual 'none' -Result 'PASS'
}

# --- 2. session start line ----------------------------------------------------

$sessionLineFound = $false
$chatError = $null
try {
    $chatDir = Join-Path (Get-SzaPath 'agentChatDir') 'progress'
    if (Test-Path -LiteralPath $chatDir) {
        # Filename carries the session id, so the scan never opens a file belonging to a sibling.
        foreach ($file in @(Get-ChildItem -LiteralPath $chatDir -Filter "*_session_*.json" -File -ErrorAction SilentlyContinue)) {
            if ($file.Name -notlike "*$sessionId*") { continue }
            $sessionLineFound = $true
            break
        }
    }
}
catch { $chatError = $_.Exception.Message }

if ($chatError) {
    Add-Check -Name 'session start line' -Expected 'one kind=session line for this identity' -Actual "unreadable: $chatError" -Result 'ADVISORY' `
        -Remedy 'The agent chat store could not be read. Nothing depends on it, so this is advisory.'
}
elseif ($sessionLineFound) {
    Add-Check -Name 'session start line' -Expected 'one kind=session line for this identity' -Actual 'present' -Result 'PASS'
}
else {
    Add-Check -Name 'session start line' -Expected 'one kind=session line for this identity' -Actual 'absent' -Result 'FAIL' `
        -Remedy 'pwsh -NoProfile -File scripts/utils/agent-chat.ps1 -Verb Post -Kind session -Note "session started (<runtime>)" - and post the matching "session ended (<reason>)" before you leave.'
}

# --- 3 and 4. held locks and queued tickets -----------------------------------

$heldDomains = @()
$queuedDomains = @()
$lockError = $null

try {
    foreach ($domain in @(Get-AgentLockDomainTable | ForEach-Object { $_.Domain })) {
        $status = Get-AgentLockStatus -Name $domain
        if ($status.Exists -and -not $status.Stale -and [string]$status.SessionId -eq $sessionId) {
            $heldDomains += $domain
        }
        foreach ($ticket in @(Get-AgentLockQueue -Name $domain)) {
            if ([string]$ticket.sessionId -eq $sessionId) { $queuedDomains += $domain; break }
        }
    }
}
catch { $lockError = $_.Exception.Message }

if ($lockError) {
    Add-Check -Name 'held locks' -Expected 'no domain held by this identity' -Actual "unreadable: $lockError" -Result 'FAIL' `
        -Remedy 'The lock store could not be read, so a leaked lock cannot be ruled out. Run scripts/utils/lock-status.ps1 -Name Code.Scripts -Queue by hand.'
}
else {
    if ($heldDomains.Count -gt 0) {
        Add-Check -Name 'held locks' -Expected 'no domain held by this identity' -Actual ($heldDomains -join ', ') -Result 'FAIL' `
            -Remedy 'pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1 - the window is the edit and nothing else (CLAUDE.md Rule 23).'
    }
    else {
        Add-Check -Name 'held locks' -Expected 'no domain held by this identity' -Actual 'none' -Result 'PASS'
    }

    if ($queuedDomains.Count -gt 0) {
        Add-Check -Name 'queued lock tickets' -Expected 'no queue ticket for this identity' -Actual ($queuedDomains -join ', ') -Result 'FAIL' `
            -Remedy 'pwsh -NoProfile -File scripts/utils/withdraw-lock-ticket.ps1 -Name <domain> for each, unless you are still waiting for that turn.'
    }
    else {
        Add-Check -Name 'queued lock tickets' -Expected 'no queue ticket for this identity' -Actual 'none' -Result 'PASS'
    }
}

# --- 5. ticket leases ---------------------------------------------------------

$heldLeases = @()
$leaseError = $null

# Read the store directly rather than shelling out to ticket-lease.ps1 -Verb Status: that verb
# sweeps as it reports, and a preflight must observe the state, not change it.
try {
    $leaseDir = Get-SzaPath 'leasesDir'
    if (Test-Path -LiteralPath $leaseDir) {
        foreach ($file in @(Get-ChildItem -LiteralPath $leaseDir -Filter '*.json' -File -ErrorAction SilentlyContinue)) {
            $lease = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
            if ([string]$lease.sessionId -eq $sessionId) { $heldLeases += [string]$lease.id }
        }
    }
}
catch { $leaseError = $_.Exception.Message }

if ($leaseError) {
    Add-Check -Name 'ticket leases' -Expected 'no lease claimed by this identity' -Actual "unreadable: $leaseError" -Result 'FAIL' `
        -Remedy 'The lease store could not be read, so an outlived lease cannot be ruled out. Run scripts/spec_catalog/ticket-lease.ps1 -Verb Status by hand.'
}
elseif ($heldLeases.Count -gt 0) {
    Add-Check -Name 'ticket leases' -Expected 'no lease claimed by this identity' -Actual ($heldLeases -join ', ') -Result 'FAIL' `
        -Remedy 'pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Release -Id <Sxxxx> for each - a lease outliving its work refuses a sibling for nothing.'
}
else {
    Add-Check -Name 'ticket leases' -Expected 'no lease claimed by this identity' -Actual 'none' -Result 'PASS'
}

# --- the check that is deliberately NOT here ----------------------------------
# Rule 7 of docs/NON_CLAUDE_RUNTIME_RULES.md ("query the class catalogue before grepping .kt")
# was planned as an advisory here, keyed on temp/catalog-touch.marker being absent while the
# tree carries a modified .kt. Measured on this tree 2026-09-10: 38 modified .kt with no marker,
# and that is the STANDING state - the working tree is permanently dirty across sessions, so the
# advisory would fire on nearly every run. A check that always fires teaches the reader to skip
# the verdict, which costs more than the rule it was guarding. There is no narrower signal: the
# marker proves a query happened and its absence proves only that none did, never that a
# tree-wide grep was run instead. Rule 7 stays a rule the model follows, with no backstop.

# --- report -------------------------------------------------------------------

$failures = @($checks | Where-Object { $_.result -eq 'FAIL' })
$advisories = @($checks | Where-Object { $_.result -eq 'ADVISORY' })

if ($Json) {
    [pscustomobject]@{
        sessionId  = $sessionId
        checks     = @($checks)
        failed     = $failures.Count
        advisories = $advisories.Count
        verdict    = if ($failures.Count -gt 0) { 'FAIL' } else { 'PASS' }
    } | ConvertTo-Json -Depth 4
}
else {
    foreach ($c in $checks) {
        if ($Quiet -and $c.result -eq 'PASS') { continue }
        Write-Host ("preflight: {0,-8} {1} - expected: {2} | actual: {3}" -f $c.result, $c.name, $c.expected, $c.actual)
        if ($c.remedy -and $c.result -ne 'PASS') { Write-Host "           -> $($c.remedy)" }
    }
    Write-Host 'preflight: the working tree is judged elsewhere - scripts/post-change.ps1 -Files "a,b" -ScopeToFile for a change, ./a.ps1 fg for the fast static gates.'
}

if ($failures.Count -gt 0) {
    Write-Error ("preflight: FAIL - {0} state check(s) failed, {1} advisory. Fix each remedy above before declaring the work done." -f $failures.Count, $advisories.Count) -ErrorAction Continue
    exit 1
}

if (-not $Json) {
    Write-Host ("preflight: PASS ({0} check(s), {1} advisory)" -f $checks.Count, $advisories.Count)
}
exit 0
