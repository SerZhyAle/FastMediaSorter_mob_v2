<#
.SYNOPSIS
    Release temp/CODE.LOCK. Safe no-op if unheld or held by a different process.

.DESCRIPTION
    scripts/post-change.ps1 already calls this automatically from its own finally block, so
    most skills never need to call it directly. Skills that skip post-change.ps1 (e.g.
    /skill-fix) must call this explicitly once their edit is done.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1
#>
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\agent-lock.ps1"

Exit-AgentLock -Name Code
Write-Host "CODE.LOCK released (or was already free)." -ForegroundColor Green
exit 0
