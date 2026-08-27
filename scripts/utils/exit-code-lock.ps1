<#
.SYNOPSIS
    Release temp/CODE.LOCK. Safe no-op if unheld or held by a different process.

.DESCRIPTION
    scripts/post-change.ps1 already calls this automatically from its own finally block, so
    most skills never need to call it directly. Skills that skip post-change.ps1 (e.g.
    /skill-fix) must call this explicitly once their edit is done.

    S1432: the release is owner-checked. A lock held by ANOTHER live session is left in place
    and reported, because with a queue behind the lock, releasing someone else's lock hands the
    turn to the wrong session. A lock with no session id (written before S1432) or one whose
    owner has gone quiet is still released, so nothing can get permanently stuck.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1
#>
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\agent-lock.ps1"

Exit-AgentLock -Name Code
# Report what actually happened: an owner-checked release can legitimately leave the file in
# place, and saying "released" then would be a false completion claim.
$after = Get-AgentLockStatus -Name Code
if ($after.Exists -and -not $after.Stale) {
    # S2109: say WHOSE it is. This line used to assert "another live session" without checking,
    # so a lock this session still held after a failed release was reported as somebody else's -
    # which is the one reading that stops the holder from investigating its own leak.
    $owner = [string]$after.SessionId
    if ($owner -and $owner -eq $env:CLAUDE_CODE_SESSION_ID) {
        Write-Host "CODE.LOCK still held by THIS session after the release attempt ($($after.Path)) - investigate, nothing is waiting on a sibling." -ForegroundColor Red
    }
    else {
        Write-Host "CODE.LOCK left in place - it belongs to another live session ($owner)." -ForegroundColor Yellow
    }
}
else {
    Write-Host "CODE.LOCK released (or was already free)." -ForegroundColor Green
}
exit 0
