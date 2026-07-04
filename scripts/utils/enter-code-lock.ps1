<#
.SYNOPSIS
    Acquire temp/CODE.LOCK before starting a multi-file source (Kotlin/XML/build-file) edit.

.DESCRIPTION
    Tier-2 (convention-only) lock - see scripts/utils/agent-lock.ps1 for the enforcement-model
    rationale. This does NOT block on a live BUILD.LOCK; it only reports one, so the calling
    skill can decide to wait or switch to non-code work (per CLAUDE.md's coordination rule)
    before it starts editing.

    Release is automatic via scripts/post-change.ps1 (its finally block calls
    Exit-AgentLock -Name Code). Skills that skip post-change.ps1 (e.g. /skill-fix) must call
    scripts/utils/exit-code-lock.ps1 explicitly when the edit is done.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S0900: refactor BrowseViewModel"
#>
param(
    [Parameter(Mandatory)][string]$Reason
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\agent-lock.ps1"

$buildStatus = Get-AgentLockStatus -Name Build
if ($buildStatus.Exists -and -not $buildStatus.Stale) {
    Write-Host "Notice: BUILD.LOCK is live (PID $($buildStatus.Pid), age $([int]$buildStatus.AgeSeconds)s, reason: '$($buildStatus.Reason)')." -ForegroundColor Yellow
    Write-Host "  A gradle build is running elsewhere - consider non-code work or waiting before editing." -ForegroundColor Yellow
}

$result = Enter-AgentLock -Name Code -Reason $Reason
if (-not $result.Acquired) {
    $s = $result.Status
    Write-Host "CODE.LOCK already held (age $([int]$s.AgeSeconds)s, reason: '$($s.Reason)') - another agent may be mid-edit." -ForegroundColor Yellow
    Write-Host "  Advisory only (Tier 2, no hard enforcement) - proceeding is allowed, but coordinate first if practical." -ForegroundColor Yellow
    # Exit 0 deliberately: this is an advisory signal, not a gate. A non-zero exit here must
    # never be mistaken by a skill's step-verification logic for a hard failure.
    exit 0
}

Write-Host "CODE.LOCK acquired (reason: '$Reason')." -ForegroundColor Green
exit 0
