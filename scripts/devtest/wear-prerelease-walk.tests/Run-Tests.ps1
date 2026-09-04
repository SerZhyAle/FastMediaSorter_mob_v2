#requires -Version 7.0
<#
.SYNOPSIS
    S2547 regression suite for the watch walk's wakefulness precondition.

# Subject: scripts/devtest/wear-prerelease-walk.ps1, scripts/devtest/lib/wear-wakefulness.ps1

.DESCRIPTION
    Hermetic: dot-sources scripts/devtest/lib/wear-wakefulness.ps1 and drives it against recorded
    `dumpsys power` captures. No adb call, no device, no network, no writes outside this folder.

    The case that matters is the dozing one. It replays the 2026-09-04 run that returned
    `observed 0, failed 16` with a clean log audit: the watch had slipped into ambient mode, the watch
    face was in front, and all sixteen "screen failures" were readings of the launcher. The walk's
    existing app-in-front guard could not catch it, because relaunching the app under a sleeping
    display satisfies that guard while every reading after it stays wrong.

    The missing-field case pins the other half: a capture that never reported wakefulness is a
    question, and the suite goes red the moment anyone makes it answer "probably fine". That is the
    difference between exit 2 and exit 1, which is the whole point of the precondition.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/wear-prerelease-walk.tests/Run-Tests.ps1

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$lib = Join-Path $repoRoot 'scripts/devtest/lib/wear-wakefulness.ps1'
$walk = Join-Path $repoRoot 'scripts/devtest/wear-prerelease-walk.ps1'
$fixtures = Join-Path $PSScriptRoot 'fixtures'

if (-not (Test-Path -LiteralPath $lib)) {
    Write-Host "FAIL | library not found: $lib" -ForegroundColor Red
    exit 1
}
. $lib

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
        return
    }
    Write-Host "FAIL | $Label" -ForegroundColor Red
    Write-Host "     | expected: $Expected | actual: $Actual" -ForegroundColor Red
    $script:failed++
}

function Read-Fixture {
    param([string]$Name)
    return (Get-Content -LiteralPath (Join-Path $fixtures $Name) -Raw)
}

# --- the parser reads what is there, and nothing that is not ------------------------------------

Assert-Equal -Label 'awake capture parses as Awake' `
    -Expected 'Awake' -Actual (Get-WearWakefulness (Read-Fixture 'power-awake.txt'))

Assert-Equal -Label 'dozing capture parses as Dozing' `
    -Expected 'Dozing' -Actual (Get-WearWakefulness (Read-Fixture 'power-dozing.txt'))

Assert-Equal -Label 'asleep capture parses as Asleep' `
    -Expected 'Asleep' -Actual (Get-WearWakefulness (Read-Fixture 'power-asleep.txt'))

Assert-Equal -Label 'capture without the field parses as nothing, never a guess' `
    -Expected '' -Actual (Get-WearWakefulness (Read-Fixture 'power-no-field.txt'))

Assert-Equal -Label 'empty input parses as nothing' `
    -Expected '' -Actual (Get-WearWakefulness '')

# --- only Awake may be walked -------------------------------------------------------------------

Assert-Equal -Label 'Awake is usable' `
    -Expected $true -Actual (Test-WearDisplayUsable 'Awake')

Assert-Equal -Label 'Dozing is NOT usable - this is the 2026-09-04 sixteen-failure run' `
    -Expected $false -Actual (Test-WearDisplayUsable 'Dozing')

Assert-Equal -Label 'Asleep is NOT usable' `
    -Expected $false -Actual (Test-WearDisplayUsable 'Asleep')

Assert-Equal -Label 'Dreaming is NOT usable' `
    -Expected $false -Actual (Test-WearDisplayUsable 'Dreaming')

Assert-Equal -Label 'an unknown value is NOT usable' `
    -Expected $false -Actual (Test-WearDisplayUsable 'SomethingNew')

Assert-Equal -Label 'an absent value is NOT usable' `
    -Expected $false -Actual (Test-WearDisplayUsable $null)

# --- the walk wires the precondition to exit 2, not to a list of failed screens ------------------

$walkText = Get-Content -LiteralPath $walk -Raw

Assert-Equal -Label 'the walk sources the wakefulness library' `
    -Expected $true -Actual ($walkText -match 'wear-wakefulness\.ps1')

Assert-Equal -Label 'the walk stops with code 2 when it cannot wake the display' `
    -Expected $true -Actual ($walkText -match 'Stop-Run 2 .*display is not awake')

Assert-Equal -Label 'the walk restores the ambient setting it changed' `
    -Expected $true -Actual ($walkText -match 'function Restore-AmbientSetting')

Assert-Equal -Label 'the wakefulness check runs before the app-in-front guard' `
    -Expected $true `
    -Actual ($walkText.IndexOf('Is the display still awake?') -lt $walkText.IndexOf('Is the app still in front?'))

Write-Host ""
Write-Host "wear-prerelease-walk.tests: $script:passed passed, $script:failed failed."
if ($script:failed -gt 0) { exit 1 }
exit 0
