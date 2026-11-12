#requires -Version 7.0
<#
.SYNOPSIS
    The compile-only command an unattended queue pass runs once: `commands.compileCheck` in
    .sza-profile.json (S4541).

.DESCRIPTION
    Canon `rules/TESTING_AND_QA.md` section 2 lets a headless pass that edited compiled source run ONE
    declared compile-only command before it sets `Implemented` or `BlockNeedUserTest`, and nothing else
    above the 5 s line. Measured across the portfolio's unattended passes, 45 of 89 closing passes with
    source edits handed over code nobody had compiled; here the owner's own builds failed twice on robot
    code (S4492 a missing `timber` import, S4536 an undefined `KEY_MESSAGE`).

    The command is a script rather than a profile string so that both Kotlin modules are covered by the
    one declared command: a pass that only touched the watch would otherwise write "compiled" about a
    module that never built, and a pass may not invent a second command.

    It runs the Kotlin compile of `app_v2` (Standard) and then of `wear`, through
    check-standard-fast.ps1 -Mode Code, and stops at the first failing module. `a.ps1 fk` is not used
    on purpose: in an agent shell a.ps1 refuses it with exit 6 (CLAUDE.md Rule 37), and this command is
    the one named exception. -BlockThrough makes a busy build domain a wait instead of exit 4, because
    a headless pass cannot wait in the background; the caller sets the foreground timeout to its maximum.

    Packages nothing, runs no test, takes no device. A pass that reports the result says "compiled, not
    tested".

.PARAMETER DryRun
    Print the commands that would run and run neither.

.EXAMPLE
    pwsh -NoProfile -File scripts/builders/check-unattended-compile.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/builders/check-unattended-compile.ps1 -DryRun

Exit codes (CLAUDE.md Rule 7):
  0  every module compiled, or -DryRun listed the commands.
  1  a module has a compile error; the modules after it were not run.
  2  a module could not be checked to a verdict (check-standard-fast.ps1's own exit 2).
  otherwise  check-standard-fast.ps1's own exit code for the first module that did not return 0.
#>

[CmdletBinding()]
param(
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'

. "$PSScriptRoot\..\quality\lib\check-subject.ps1"

$check = Join-Path $PSScriptRoot 'check-standard-fast.ps1'
$pwshExe = (Get-Process -Id $PID).Path

$modules = @(
    [pscustomobject]@{ Name = 'app_v2'; Args = @('-Mode', 'Code', '-BlockThrough') },
    [pscustomobject]@{ Name = 'wear'; Args = @('-Mode', 'Code', '-Module', 'wear', '-BlockThrough') }
)

Write-CheckSubject -Axes ([ordered]@{ module = (($modules | ForEach-Object { $_.Name }) -join ','); mode = 'Code'; scope = 'compile-only' })

foreach ($module in $modules) {
    Write-Host "check-unattended-compile: [$($module.Name)] pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 $($module.Args -join ' ')"
    if ($DryRun) { continue }

    & $pwshExe -NoProfile -File $check @($module.Args)
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        Write-Host "check-unattended-compile: FAIL - module $($module.Name) exited $code; the modules after it were not checked." -ForegroundColor Red
        exit $code
    }
}

if ($DryRun) {
    Write-Host 'check-unattended-compile: -DryRun - nothing ran.' -ForegroundColor Yellow
}
else {
    Write-Host "check-unattended-compile: PASS - $(($modules | ForEach-Object { $_.Name }) -join ', ') compiled, not tested." -ForegroundColor Green
}
exit 0
