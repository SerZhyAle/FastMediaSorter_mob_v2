<#
.SYNOPSIS
    Drift gate: docs/SCRIPT_CHEATSHEET.md must equal `help.ps1 -Generate` output.

.DESCRIPTION
    Fails when a script's param() block (or synopsis) changed but the committed
    cheatsheet was not regenerated. Delegates the rebuild + byte-compare to
    scripts/utils/help.ps1 -Check so the generator and the gate share one code
    path and can never disagree on format. Run with -Gate from post-change.ps1.

.NOTES
    Exit codes:
      0  in sync.
      1  stale - regenerate with `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate`.
      2  the gate itself cannot run (scripts/utils/help.ps1 missing).
#>
param(
    [switch] $Gate,
    [switch] $Quiet,
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
)

$ErrorActionPreference = 'Stop'

$help = Join-Path $RepoRoot 'scripts/utils/help.ps1'
if (-not (Test-Path $help)) {
    Write-Error 'script-cheatsheet-sync: cannot run - scripts/utils/help.ps1 missing.' -ErrorAction Continue
    exit 2
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else { 'pwsh' }

& $pwshExe -NoProfile -File $help -Check -Root $RepoRoot
if ($LASTEXITCODE -eq 0) {
    if (-not $Quiet) {
        Write-Host 'script-cheatsheet-sync: OK - docs/SCRIPT_CHEATSHEET.md matches every script param block.' -ForegroundColor Green
    }
    exit 0
}

Write-Error 'script-cheatsheet-sync: FAIL - docs/SCRIPT_CHEATSHEET.md is stale. Run: pwsh -NoProfile -File scripts/utils/help.ps1 -Generate' -ErrorAction Continue
exit 1
