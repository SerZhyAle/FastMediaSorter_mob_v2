#requires -Version 7.0
<#
.SYNOPSIS
    S2126: entry point for the quality.tests suite - runs each sibling *.Tests.ps1 in its own process.

.DESCRIPTION
    Every test file in this folder carries its own harness, its own $script:pass/$script:fail pair,
    and ends in `exit 0` / `exit 1`. That last property is why this dispatcher spawns a child process
    per file instead of dot-sourcing them the way scripts/doc-drift.tests/Run-Tests.ps1 does: a
    dot-sourced file's `exit` terminates the DISPATCHER, so the first green file would end the run
    with code 0 while reporting a whole folder it never finished. A refusal that reports success is
    worse than no runner at all, which is the defect this ticket exists to remove
    (dev/REFUTED_APPROACHES.md).

    Spawning also preserves the way these files are debugged today - each stays runnable on its own
    with `pwsh -NoProfile -File <name>.Tests.ps1`, unchanged by this file's existence.

    Discovery is by glob, not by a list: a new *.Tests.ps1 dropped in this folder runs by the fact of
    being placed, with no edit here. The folder is reached in turn by scripts/quality/run-script-suites.ps1,
    which discovers `*.tests/Run-Tests.ps1` and classifies the exit code returned below.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every test file passed, or the folder holds no test file.
      1  at least one test file failed.
      2  no test file failed, but at least one could not verify its environment.
#>
# Subject: scripts/utils/set-android-string.ps1, scripts/utils/locale-set.ps1, scripts/utils/seed-locale-tranche.ps1, scripts/utils/locale-bulk-export.ps1
#
# Path arithmetic gives this folder `scripts/quality`, which covers four of the five files. The fifth,
# set-android-string-remove.Tests.ps1, tests a tool in scripts/utils and reaches locale-set.ps1
# through it - so without this line the per-ticket closure never runs that test when its own subject
# is edited, and the file would be "reachable" in the release sweep only. That is the S2126 defect at
# one level in, which is why it is declared here rather than left to the folder name.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$testsRoot = $PSScriptRoot
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$testFiles = @(Get-ChildItem -LiteralPath $testsRoot -Filter '*.Tests.ps1' -File | Sort-Object Name)
if ($testFiles.Count -eq 0) {
    Write-Host 'quality.tests: PASS - no test file in this folder.' -ForegroundColor Green
    exit 0
}

$failed = 0
$cannotVerify = 0

foreach ($file in $testFiles) {
    & $pwshExe -NoProfile -File $file.FullName
    $code = [int]$LASTEXITCODE

    $verdict = switch ($code) {
        0 { 'PASS' }
        2 { 'CANNOT-VERIFY' }
        default { 'FAIL' }
    }
    $colour = switch ($verdict) {
        'PASS' { 'Green' }
        'CANNOT-VERIFY' { 'Yellow' }
        default { 'Red' }
    }

    if ($verdict -eq 'FAIL') { $failed++ }
    elseif ($verdict -eq 'CANNOT-VERIFY') { $cannotVerify++ }

    Write-Host ("SUITE | {0,-13} | {1} | exit {2}" -f $verdict, $file.Name, $code) -ForegroundColor $colour
}

Write-Host ("quality.tests: {0} file(s) | failed: {1} | could not verify: {2}" -f $testFiles.Count, $failed, $cannotVerify) -ForegroundColor Cyan

if ($failed -gt 0) { exit 1 }
if ($cannotVerify -gt 0) { exit 2 }
exit 0
