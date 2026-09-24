<#
.SYNOPSIS
    Drift gate: docs/SCRIPT_CHEATSHEET.md must equal `help.ps1 -Generate` output.

.DESCRIPTION
    Fails when a script's param() block (or synopsis) changed but the committed
    cheatsheet was not regenerated. Delegates the rebuild + byte-compare to
    scripts/utils/help.ps1 -Check so the generator and the gate share one code
    path and can never disagree on format. Run with -Gate from post-change.ps1.

    -Repair (S3515) regenerates a stale cheatsheet instead of failing. The closure
    passes it because the only remedy this gate ever printed was that one command,
    and an agent ran it and then re-ran the whole closure - 15-50 s per ticket for
    a step no judgement went into. The fast-gate batch keeps the pure check.

.PARAMETER Repair
    On a stale cheatsheet run `help.ps1 -Generate` and pass when it succeeds. A
    generator refusal (exit 4 - Code.Scripts held by another session - or any
    other code) keeps the FAIL verdict and prints the generator's output.

.NOTES
    Exit codes:
      0  in sync, or regenerated under -Repair.
      1  stale - regenerate with `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate`.
      2  the gate itself cannot run (scripts/utils/help.ps1 missing).
#>
param(
    [switch] $Gate,
    [switch] $Quiet,
    [switch] $Repair,
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

# Captured, not streamed: under -Repair the checker's "is stale - run: .." line is a remedy this
# script is about to apply, and printing it would read as a failure the verdict then contradicts.
$checkOutput = @(& $pwshExe -NoProfile -File $help -Check -Root $RepoRoot 2>&1)
$checkExit = $LASTEXITCODE
if ($checkExit -eq 0) {
    if (-not $Quiet) {
        Write-Host 'script-cheatsheet-sync: OK - docs/SCRIPT_CHEATSHEET.md matches every script param block.' -ForegroundColor Green
    }
    exit 0
}

if ($Repair) {
    $generateOutput = @(& $pwshExe -NoProfile -File $help -Generate -Root $RepoRoot 2>&1)
    if ($LASTEXITCODE -eq 0) {
        Write-Host 'script-cheatsheet-sync: REPAIRED - docs/SCRIPT_CHEATSHEET.md was stale and has been regenerated.' -ForegroundColor Green
        exit 0
    }
    $generateOutput | ForEach-Object { Write-Host "  $_" }
}
else {
    $checkOutput | ForEach-Object { Write-Host "  $_" }
}

Write-Error 'script-cheatsheet-sync: FAIL - docs/SCRIPT_CHEATSHEET.md is stale. Run: pwsh -NoProfile -File scripts/utils/help.ps1 -Generate' -ErrorAction Continue
exit 1
