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

# --- S2767: a scroll stops at the end of the list, never at the end of the budget ----------------
#
# The defect these guard was not a wrong number. Reset-ListToTop spent its whole budget in blind
# back-to-back swipes, and measured on emulator-5556 2026-09-09 four of those on an already-at-top
# list OPEN the first row - the last-used shortcut on Home - and start playback, after which every
# later entry is judged against the audio player. Raising the budget made it worse, which is why the
# assertions below are about the SHAPE of the loop and not about the size of the cap.

Assert-Equal -Label 'the reset reads the tree between swipes instead of swiping blind' `
    -Expected $true -Actual ($walkText -match 'function Invoke-ScrollUntilSettled')

Assert-Equal -Label 'the reset goes through the settling scroll, not through a bare for loop' `
    -Expected $true `
    -Actual ($walkText -match '(?s)function Reset-ListToTop.*?Invoke-ScrollUntilSettled.*?\r?\n\}')

Assert-Equal -Label 'the settling scroll stops when two consecutive reads agree' `
    -Expected $true -Actual ($walkText -match '\$current -eq \$previous')

Assert-Equal -Label 'an unreadable dump is not read as "the list stopped"' `
    -Expected $true -Actual ($walkText -match '(?s)if \(\$null -eq \$current\) \{ continue \}')

Assert-Equal -Label 'the upward expect-hunt no longer spends twice the budget unconditionally' `
    -Expected $false -Actual ($walkText -match '\$MaxScrolls \* 2')

Assert-Equal -Label 'the default scroll cap covers the longest measured list (Home, 6 swipes)' `
    -Expected $true -Actual ($walkText -match '\$MaxScrolls = 12')

# --- S2767: "never opened" is its own outcome, and it still blocks the PASS ----------------------

Assert-Equal -Label 'a control that was never found scores unreachable, not failed' `
    -Expected $true -Actual ($walkText -match "\`$row\.outcome = 'unreachable'")

Assert-Equal -Label 'unreachable is counted in its own right' `
    -Expected $true -Actual ($walkText -match 'unreachable\s+= @\(\$rows \| Where-Object')

Assert-Equal -Label 'an unreachable screen loses the walk its exit 0' `
    -Expected $true -Actual ($walkText -match '\$result\.counts\.unreachable -gt 0')

$verdictScript = Join-Path (Split-Path -Parent $PSScriptRoot) 'prerelease-verdict.ps1'
Assert-Equal -Label 'the verdict script is where the tests expect it' `
    -Expected $true -Actual (Test-Path -LiteralPath $verdictScript)

# The one case run end to end rather than matched: a walk whose every screen passed except one that
# was never reached must not report PASS. Read as a product failure it sent two rebuilds after a
# defect that did not exist; read as a pass it ships an unjudged screen - it is neither.
$verdictTemp = Join-Path ([System.IO.Path]::GetTempPath()) ("s2767-walk-" + [guid]::NewGuid().ToString('N') + '.json')
@{
    screens = @(
        @{ id = 'home'; outcome = 'observed'; detail = $null },
        @{ id = 'broadcast'; outcome = 'unreachable'; detail = 'could not reach the screen' }
    )
    coverage = @{ walked = 2; excluded = 0; entries = 2 }
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $verdictTemp -Encoding UTF8

$verdictLog = Join-Path ([System.IO.Path]::GetTempPath()) ("s2767-log-" + [guid]::NewGuid().ToString('N') + '.log')
Set-Content -LiteralPath $verdictLog -Value 'no findings in this capture' -Encoding UTF8

try {
    # -LogFile is mandatory: the verdict never answers about a walk alone, so the walk half is
    # exercised beside a log that carries nothing rather than on its own.
    $verdictOut = & pwsh -NoProfile -File $verdictScript -LogFile $verdictLog -WalkResults $verdictTemp -Json 2>&1
    $verdictJson = $null
    try { $verdictJson = ($verdictOut | Where-Object { $_ -match '^\s*\{' } | Select-Object -First 1) | ConvertFrom-Json } catch { $verdictJson = $null }

    Assert-Equal -Label 'the verdict parses a walk carrying an unreachable screen' `
        -Expected $true -Actual ($null -ne $verdictJson)

    Assert-Equal -Label 'one unreachable screen is enough to lose the PASS' `
        -Expected $false -Actual ([bool]$verdictJson.pass)

    Assert-Equal -Label 'the verdict reports the unreachable count apart from the failed one' `
        -Expected 1 -Actual $verdictJson.breakdown.walk.unreachable

    Assert-Equal -Label 'and does not inflate the failed count with it' `
        -Expected 0 -Actual $verdictJson.breakdown.walk.failed
}
finally {
    Remove-Item -LiteralPath $verdictTemp -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $verdictLog -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "wear-prerelease-walk.tests: $script:passed passed, $script:failed failed."
if ($script:failed -gt 0) { exit 1 }
exit 0
