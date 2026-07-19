#requires -Version 7.0
<#
.SYNOPSIS
    Gate wrapper (S1075): documentation-vs-Gradle pin drift.

.DESCRIPTION
    Delegates to scripts/check-doc-vs-gradle.ps1 (S0271), which compares the
    hand-maintained pin mentions in dev/TECH_REQUIREMENTS.md against the canonical
    versions read from the Gradle build files. Any FAIL / INCONSISTENT / MISSING makes
    this gate fail, so drift is caught at change time instead of accumulating unseen
    until the next pre-release sweep.

    Scope note: CLAUDE.md and docs/TECH_STACK.md pins are OWNED by
    generate-toolchain-pins.ps1 (managed generated block) and verified by the separate
    doc-pins-sync gate - they are intentionally not required by the drift checker.

    Exit codes (S1070):
      0 - no drift (doc pins match Gradle).
      1 - drift found (FAIL / INCONSISTENT / MISSING), or the underlying checker could
          not run.

.PARAMETER Gate
    Accepted for parity with the other assert-*.ps1 fast gates. The underlying checker
    is already fail-closed (exit 1 on any drift), so this switch is a no-op marker.

.PARAMETER Quiet
    Print only the checker's SUMMARY line, not every record.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-doc-pin-drift.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$checker = Join-Path $repoRoot 'scripts/check-doc-vs-gradle.ps1'

if (-not (Test-Path -LiteralPath $checker)) {
    Write-Error "doc-vs-gradle checker not found: $checker" -ErrorAction Continue
    exit 1
}

# Run the checker in a SEPARATE process: this wrapper is under Set-StrictMode -Version
# Latest, and `& $checker` in-process would leak that into the checker + its dot-sourced
# modules (which are not strict-clean) and break them on legitimate scalar/$null .Count.
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
}
else {
    'pwsh'
}
$output = & $pwshExe -NoProfile -File $checker
$checkerExit = $LASTEXITCODE

if ($Quiet) {
    $output | Where-Object { $_ -match '^SUMMARY' } | ForEach-Object { Write-Host $_ }
}
else {
    $output | ForEach-Object { Write-Host $_ }
}

if ($checkerExit -ne 0) {
    Write-Host 'assert-doc-pin-drift: FAIL - dev/TECH_REQUIREMENTS.md is out of sync with Gradle pins (run: pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1).' -ForegroundColor Red
    exit 1
}

Write-Host 'assert-doc-pin-drift: PASS (doc pins match Gradle).' -ForegroundColor Green
exit 0
