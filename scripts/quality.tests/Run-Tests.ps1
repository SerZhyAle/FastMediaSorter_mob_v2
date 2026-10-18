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
      0  every test file passed or was deliberately disabled.
      1  at least one test file failed.
      2  no test file failed, but a file could not verify, queued, or discovery found no test files.
      3  no file failed or could not verify, but at least one reported an advisory.
#>
# Subject: scripts/utils/set-android-string.ps1, scripts/utils/locale-set.ps1, scripts/utils/seed-locale-tranche.ps1, scripts/utils/locale-bulk-export.ps1
#
# Path arithmetic gives this folder `scripts/quality`, which covers four of the five files. The fifth,
# set-android-string-remove.Tests.ps1, tests a tool in scripts/utils and reaches locale-set.ps1
# through it - so without this line the per-ticket closure never runs that test when its own subject
# is edited, and the file would be "reachable" in the release sweep only. That is the S2126 defect at
# one level in, which is why it is declared here rather than left to the folder name.

[CmdletBinding()]
param([string]$TestRoot = $PSScriptRoot)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
trap {
    Write-Host "quality.tests: COULD NOT VERIFY ($($_.Exception.Message -replace '\s+', ' '))"
    exit 2
}

$testsRoot = $TestRoot
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$testFiles = @(Get-ChildItem -LiteralPath $testsRoot -Filter '*.Tests.ps1' -File | Sort-Object Name)
if ($testFiles.Count -eq 0) {
    Write-Host 'quality.tests: COULD NOT VERIFY (0 file(s) discovered; no tests ran).' -ForegroundColor Yellow
    exit 2
}

$failed = 0
$cannotVerify = 0
$advisories = 0
$notApplicable = 0
$deciders = [System.Collections.Generic.List[string]]::new()

foreach ($file in $testFiles) {
    $childOutput = @(& $pwshExe -NoProfile -NonInteractive -File $file.FullName 2>&1)
    $code = [int]$LASTEXITCODE
    foreach ($line in $childOutput) { Write-Host "$line" }
    $childWord = ''
    if ($childOutput.Count -gt 0 -and [string]$childOutput[-1] -cmatch '^[^:\r\n]+: (PASS WITH ADVISORIES|COULD NOT VERIFY|NOT APPLICABLE|PASS|FAIL)(?:\s|\(|$)') {
        $childWord = $Matches[1]
    }

    $verdict = switch ($code) {
        0 {
            if ($childWord) { $childWord } else { 'COULD NOT VERIFY' }
        }
        2 { 'COULD NOT VERIFY' }
        3 { 'PASS WITH ADVISORIES' }
        4 { 'COULD NOT VERIFY' }
        default { 'FAIL' }
    }
    $colour = switch ($verdict) {
        'PASS' { 'Green' }
        'COULD NOT VERIFY' { 'Yellow' }
        'PASS WITH ADVISORIES' { 'Yellow' }
        'NOT APPLICABLE' { 'DarkGray' }
        default { 'Red' }
    }

    if ($verdict -eq 'FAIL') { $failed++ }
    elseif ($verdict -eq 'COULD NOT VERIFY') { $cannotVerify++ }
    elseif ($verdict -eq 'PASS WITH ADVISORIES') { $advisories++ }
    elseif ($verdict -eq 'NOT APPLICABLE') { $notApplicable++ }
    if ($verdict -in @('FAIL', 'COULD NOT VERIFY', 'PASS WITH ADVISORIES')) {
        $deciders.Add("$($file.Name)=$verdict")
    }

    Write-Host ("SUITE | {0,-13} | {1} | exit {2}" -f $verdict, $file.Name, $code) -ForegroundColor $colour
}

$word = if ($failed -gt 0) { 'FAIL' }
    elseif ($cannotVerify -gt 0) { 'COULD NOT VERIFY' }
    elseif ($advisories -gt 0) { 'PASS WITH ADVISORIES' }
    elseif ($notApplicable -eq $testFiles.Count) { 'NOT APPLICABLE' }
    else { 'PASS' }
Write-Host "quality.tests: $word ($($testFiles.Count) child checks; failed=$failed; unverified=$cannotVerify; advisory=$advisories; disabled=$notApplicable; decisive=[$($deciders -join ', ')])" -ForegroundColor Cyan

if ($failed -gt 0) { exit 1 }
if ($cannotVerify -gt 0) { exit 2 }
if ($advisories -gt 0) { exit 3 }
exit 0
