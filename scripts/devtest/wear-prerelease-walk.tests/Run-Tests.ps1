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
$shapeLib = Join-Path $repoRoot 'scripts/devtest/lib/clip-shape-outcome.ps1'
$positionLib = Join-Path $repoRoot 'scripts/devtest/lib/wear-walk-position.ps1'
$foreignLib = Join-Path $repoRoot 'scripts/devtest/lib/wear-foreign-window.ps1'
$batteryLib = Join-Path $repoRoot 'scripts/devtest/lib/wear-battery.ps1'
$walk = Join-Path $repoRoot 'scripts/devtest/wear-prerelease-walk.ps1'
$fixtures = Join-Path $PSScriptRoot 'fixtures'

if (-not (Test-Path -LiteralPath $lib)) {
    Write-Host "FAIL | library not found: $lib" -ForegroundColor Red
    exit 1
}
. $lib

if (-not (Test-Path -LiteralPath $shapeLib)) {
    Write-Host "FAIL | library not found: $shapeLib" -ForegroundColor Red
    exit 1
}
. $shapeLib

if (-not (Test-Path -LiteralPath $positionLib)) {
    Write-Host "FAIL | library not found: $positionLib" -ForegroundColor Red
    exit 1
}
. $positionLib

if (-not (Test-Path -LiteralPath $foreignLib)) {
    Write-Host "FAIL | library not found: $foreignLib" -ForegroundColor Red
    exit 1
}
. $foreignLib

if (-not (Test-Path -LiteralPath $batteryLib)) {
    Write-Host "FAIL | library not found: $batteryLib" -ForegroundColor Red
    exit 1
}
. $batteryLib

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

# --- S3362 follow-up: the tap is whole-value, and backAfter can name a flavor --------------------
#
# Both defects were measured on the small-round emulator 2026-09-22, standard build: the reach for
# the settings-screen label 'Screen' substring-matched the screen-off rim control's 'Screen off'
# and dimmed the display mid-walk, and the Apps block ended on the Apps list because one number
# cannot give apps-stopwatch a depth of one in noLegal (mid-block) and two in standard (block end).

Assert-Equal -Label 'the reach taps a label by whole-value match, never a prefix' `
    -Expected $true -Actual ($walkText -match '''tap-label'', ''-Label'', \$Label, ''-Exact''')

Assert-Equal -Label 'backAfter resolves through a function, so a flavor map is possible' `
    -Expected $true -Actual ($walkText -match 'function Resolve-WalkBackAfter')

Assert-Equal -Label 'the entry loop asks the resolver for its BACK count' `
    -Expected $true -Actual ($walkText -match '\$backAfter = Resolve-WalkBackAfter -Screen \$screen')

# S3394: the water flashlight refuses a single back on purpose, which is what a plain
# `input keyevent BACK` is - without the burst the walk would stay inside it for the rest of the run.
Assert-Equal -Label 'a screen declaring backBurst gets its first BACK sent as that many presses' `
    -Expected $true -Actual ($walkText -match '\$b -eq 0 -and \$screen\.backBurst\) \{ \$backArgs \+= @\(''-Repeat''')

$declaredScreens = (Get-Content -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) 'wear-prerelease-screens.json') -Raw | ConvertFrom-Json).screens
$waterFlashlight = @($declaredScreens | Where-Object { $_.id -eq 'apps-water-flashlight' })[0]
Assert-Equal -Label 'the water flashlight leaves by a burst of three, then one key for the Apps list' `
    -Expected '3/2' -Actual "$($waterFlashlight.backBurst)/$($waterFlashlight.backAfter)"

Assert-Equal -Label 'no declared entry still carries the retired backLongPress field' `
    -Expected 0 -Actual @($declaredScreens | Where-Object { $_.PSObject.Properties['backLongPress'] }).Count

Assert-Equal -Label 'a flavor map without the installed flavor stops the run, never guesses' `
    -Expected $true -Actual ($walkText -match "Stop-Run 2 .*backAfter as a flavor map with no")

Assert-Equal -Label 'the declared list carries the measured stopwatch depth per flavor' `
    -Expected $true -Actual ((Get-Content -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) 'wear-prerelease-screens.json') -Raw) -match '"backAfter": \{ "noLegal": 1, "standard": 2 \}')

Assert-Equal -Label 'walk.json is written after the verdict stamps exitCode and ok' `
    -Expected $true `
    -Actual ($walkText.IndexOf('$result.exitCode = $verdict') -lt $walkText.IndexOf("Set-Content -LiteralPath `$walkPath"))

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

# --- S2782: a shape finding and a shape nobody could check are different answers -----------------
#
# The walk counted EVERY non-zero clip-check exit as a WO-V16 violation, so a dropped adb connection
# printed as OFF-GLASS and routed the operator to rebuild the app. And the verdict read no shape at
# all: a walk carrying `shapeExit 9` came back `VERDICT PASS - walk=True`, exit 0, because an
# OFF-GLASS screen's outcome is `observed` and the walk half was built from `outcome` alone. That
# reached a release report on package 36, two days after Play rejected it for content behind the
# round glass - the one thing WO-V16 exists to catch before submission.

Assert-Equal -Label 'a clean shape check is clean' `
    -Expected 'clean' -Actual (Get-ClipShapeClass 0)

Assert-Equal -Label 'exit 9 is a finding - OFF-GLASS, no scroll position saves it' `
    -Expected 'finding' -Actual (Get-ClipShapeClass 9)

Assert-Equal -Label 'exit 10 is a finding - -Strict CLIPPED, the criterion Play applied' `
    -Expected 'finding' -Actual (Get-ClipShapeClass 10)

Assert-Equal -Label 'exit 7 is unchecked - adb failed, the glass was never judged' `
    -Expected 'unchecked' -Actual (Get-ClipShapeClass 7)

Assert-Equal -Label 'exit 2 is unchecked, not a shape verdict' `
    -Expected 'unchecked' -Actual (Get-ClipShapeClass 2)

Assert-Equal -Label 'a screen that never ran clip-check is clean, not unchecked' `
    -Expected 'clean' -Actual (Get-ClipShapeClass $null)

Assert-Equal -Label 'a non-numeric exit code is never read as passing' `
    -Expected 'unchecked' -Actual (Get-ClipShapeClass 'not-a-code')

# --- S3189: a shape finding accepted for one flavor only -----------------------------------------

Assert-Equal -Label 'a -NoLegal versionName is the noLegal flavor' `
    -Expected 'noLegal' -Actual (Get-WearFlavorFromVersionName '2.60.9162.000-NoLegal-DEBUG')

Assert-Equal -Label 'a plain versionName is the standard flavor' `
    -Expected 'standard' -Actual (Get-WearFlavorFromVersionName '2.60.9162.000-DEBUG')

Assert-Equal -Label 'an unreadable versionName is standard, so a failed probe grants nothing' `
    -Expected 'standard' -Actual (Get-WearFlavorFromVersionName $null)

$acceptingEntry = '{"id":"apps-game","acceptedOffGlass":{"flavors":["noLegal"],"reason":"r"}}' | ConvertFrom-Json
$plainEntry = '{"id":"apps-calculator"}' | ConvertFrom-Json

Assert-Equal -Label 'an entry accepts a finding on a flavor it lists' `
    -Expected $true -Actual (Test-WalkShapeAccepted -Screen $acceptingEntry -Flavor 'noLegal')

Assert-Equal -Label 'the same entry does not accept it on standard' `
    -Expected $false -Actual (Test-WalkShapeAccepted -Screen $acceptingEntry -Flavor 'standard')

Assert-Equal -Label 'an entry without acceptedOffGlass accepts nothing' `
    -Expected $false -Actual (Test-WalkShapeAccepted -Screen $plainEntry -Flavor 'noLegal')

Assert-Equal -Label 'an accepted finding row classifies as accepted' `
    -Expected 'accepted' -Actual (Get-WalkRowShapeClass ([pscustomobject]@{ shapeExit = 9; shapeAccepted = 'r' }))

Assert-Equal -Label 'a finding row without acceptance is still a finding' `
    -Expected 'finding' -Actual (Get-WalkRowShapeClass ([pscustomobject]@{ shapeExit = 9 }))

Assert-Equal -Label 'acceptance never turns an unchecked shape into a pass' `
    -Expected 'unchecked' -Actual (Get-WalkRowShapeClass ([pscustomobject]@{ shapeExit = 7; shapeAccepted = 'r' }))

Assert-Equal -Label 'a row with no shape check is clean' `
    -Expected 'clean' -Actual (Get-WalkRowShapeClass ([pscustomobject]@{ id = 'x' }))

Assert-Equal -Label 'the walk marks an accepted finding on the row' `
    -Expected $true -Actual ($walkText -match 'Test-WalkShapeAccepted -Screen \$screen -Flavor \$result\.flavor')

# --- S2782: the walk wires the two classes to two different exit codes ---------------------------

Assert-Equal -Label 'the walk sources the shape classifier instead of testing 9 and 10 itself' `
    -Expected $true -Actual ($walkText -match 'clip-shape-outcome\.ps1')

Assert-Equal -Label 'the shape-failure count is classified (S3189: through the row classifier), not a bare -ne 0 test' `
    -Expected $true -Actual ($walkText -match "Get-WalkRowShapeClass \`$_\) -eq 'finding'")

Assert-Equal -Label 'an unchecked shape is counted in its own right' `
    -Expected $true -Actual ($walkText -match "Get-WalkRowShapeClass \`$_\) -eq 'unchecked'")

Assert-Equal -Label 'a shape FINDING still loses the walk its exit 0' `
    -Expected $true -Actual ($walkText -match '(?s)\$verdict = if \(.*?\$shapeFailuresCount -gt 0')

Assert-Equal -Label 'an UNCHECKED shape scores could-not-verify, not a content failure' `
    -Expected $true -Actual ($walkText -match 'elseif \(\$shapeUncheckedCount -gt 0')

Assert-Equal -Label 'the summary line reports both counts' `
    -Expected $true -Actual ($walkText -match 'shapeFailures \$shapeFailuresCount, shapeUnchecked \$shapeUncheckedCount')

# --- S2782: and the verdict reads them, end to end on synthetic walks ----------------------------

function Invoke-VerdictOnScreens {
    # One verdict run against a walk.json built from the given screen rows. Returns the parsed JSON,
    # or $null when the verdict emitted none.
    param([object[]]$Screens)

    $walkFile = Join-Path ([System.IO.Path]::GetTempPath()) ("s2782-walk-" + [guid]::NewGuid().ToString('N') + '.json')
    $logFile  = Join-Path ([System.IO.Path]::GetTempPath()) ("s2782-log-" + [guid]::NewGuid().ToString('N') + '.log')
    try {
        @{ screens = $Screens; coverage = @{ walked = $Screens.Count; excluded = 0; entries = $Screens.Count } } |
            ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $walkFile -Encoding UTF8
        Set-Content -LiteralPath $logFile -Value 'no findings in this capture' -Encoding UTF8

        $out = & pwsh -NoProfile -File $verdictScript -LogFile $logFile -WalkResults $walkFile -Json 2>&1
        try { return ($out | Where-Object { $_ -match '^\s*\{' } | Select-Object -First 1) | ConvertFrom-Json }
        catch { return $null }
    }
    finally {
        Remove-Item -LiteralPath $walkFile -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $logFile -Force -ErrorAction SilentlyContinue
    }
}

# An OFF-GLASS screen: `observed` by outcome, and a WO-V16 violation by shape. This is the exact
# walk.json shape that returned PASS before S2782.
$offGlassVerdict = Invoke-VerdictOnScreens @(
    @{ id = 'home'; outcome = 'observed'; detail = $null; shapeExit = 0 },
    @{ id = 'apps-game'; outcome = 'observed'; detail = $null; shapeExit = 9 }
)

Assert-Equal -Label 'the verdict parses a walk carrying an OFF-GLASS screen' `
    -Expected $true -Actual ($null -ne $offGlassVerdict)

Assert-Equal -Label 'one OFF-GLASS screen is enough to lose the PASS' `
    -Expected $false -Actual ([bool]$offGlassVerdict.pass)

Assert-Equal -Label 'an OFF-GLASS screen is a failure, not a blocked observation' `
    -Expected $false -Actual ([bool]$offGlassVerdict.blocked)

Assert-Equal -Label 'the verdict counts the OFF-GLASS screen' `
    -Expected 1 -Actual $offGlassVerdict.breakdown.walk.offGlass

Assert-Equal -Label 'and does not inflate the failed count with it' `
    -Expected 0 -Actual $offGlassVerdict.breakdown.walk.failed

# A screen whose clip-check could not run at all. Nothing was decided about the glass there, which is
# the `manual` class - it refuses the PASS without claiming the app is broken.
$acceptedVerdict = Invoke-VerdictOnScreens -Screens @(
    [pscustomobject]@{ id = 'apps-game'; outcome = 'observed'; shapeExit = 9; shapeAccepted = 'r' })
Assert-Equal -Label 'the verdict does not count an accepted finding as offGlass' `
    -Expected 0 -Actual $(if ($acceptedVerdict) { $acceptedVerdict.breakdown.walk.offGlass } else { 'no-json' })

$uncheckedVerdict = Invoke-VerdictOnScreens @(
    @{ id = 'home'; outcome = 'observed'; detail = $null; shapeExit = 0 },
    @{ id = 'apps-game'; outcome = 'observed'; detail = $null; shapeExit = 7 }
)

Assert-Equal -Label 'an unchecked shape refuses the PASS' `
    -Expected $false -Actual ([bool]$uncheckedVerdict.pass)

Assert-Equal -Label 'an unchecked shape BLOCKS rather than fails - nothing was decided' `
    -Expected $true -Actual ([bool]$uncheckedVerdict.blocked)

Assert-Equal -Label 'the verdict counts the unchecked shape' `
    -Expected 1 -Actual $uncheckedVerdict.breakdown.walk.shapeUnchecked

Assert-Equal -Label 'an unchecked shape is never reported as an OFF-GLASS finding' `
    -Expected 0 -Actual $uncheckedVerdict.breakdown.walk.offGlass

# The control: a walk with clean shapes still passes, so the two cases above are not passing on a
# verdict that refuses everything.
$cleanVerdict = Invoke-VerdictOnScreens @(
    @{ id = 'home'; outcome = 'observed'; detail = $null; shapeExit = 0 }
)

Assert-Equal -Label 'a walk with clean shapes still passes' `
    -Expected $true -Actual ([bool]$cleanVerdict.pass)

Assert-Equal -Label 'a clean walk reports no OFF-GLASS screen' `
    -Expected 0 -Actual $cleanVerdict.breakdown.walk.offGlass

# A pre-S2782 walk.json carries no `shapeExit` on any row. It must read as clean rather than as a
# tree of unchecked screens, or every re-read of an archived run turns into a blocked verdict.
$legacyVerdict = Invoke-VerdictOnScreens @(
    @{ id = 'home'; outcome = 'observed'; detail = $null }
)

Assert-Equal -Label 'a walk.json written before S2782 still passes' `
    -Expected $true -Actual ([bool]$legacyVerdict.pass)

Assert-Equal -Label 'and reports no unchecked shape it has no evidence for' `
    -Expected 0 -Actual $legacyVerdict.breakdown.walk.shapeUnchecked

# --- S2779: the walk knows where it is standing, and recovers when it stops knowing -------------
#
# The 2026-09-09 run took 35 minutes for 25 entries and produced 13 observations. Eleven consecutive
# entries after `apps-water-flashlight` were recorded `unreachable`, which is not eleven independent
# absences - it is one lost position, paid for once per entry. The position model below is what makes
# that state nameable, and the cascade threshold is what makes it recoverable.

function New-WalkEntry {
    # One screen-list entry, carrying only the fields it declares - which is the point: an entry with
    # no control has no `label` property at all, and this suite runs under Set-StrictMode.
    param([string]$Id, [string]$Label, [string]$ResourceId)
    $entry = @{ id = $Id }
    if ($Label) { $entry['label'] = $Label }
    if ($ResourceId) { $entry['resourceId'] = $ResourceId }
    return [pscustomobject]$entry
}

function Get-PositionNames {
    param([object[]]$Position)
    return (@($Position | ForEach-Object { $_.name }) -join ',')
}

$apps = Push-WearWalkPosition -Position @() -Entry (New-WalkEntry -Id 'apps' -Label 'Apps')

# The stack follows what HAPPENED, not what was declared: an entry with no control opens nothing.
Assert-Equal -Label 'an entry with no control adds no level - `home` is reached by the launch itself' `
    -Expected 0 -Actual (@(Push-WearWalkPosition -Position @() -Entry (New-WalkEntry -Id 'home')).Count)

Assert-Equal -Label 'an entry that opened pushes the level it opened' `
    -Expected 'Apps' -Actual (Get-PositionNames $apps)

Assert-Equal -Label 'a nested entry stacks on its section' `
    -Expected 'Apps,Calculator' `
    -Actual (Get-PositionNames (Push-WearWalkPosition -Position $apps -Entry (New-WalkEntry -Id 'apps-calculator' -Label 'Calculator')))

# A level records HOW to re-enter it, and an id-driven entry is re-entered by id. Storing the label
# alone would drop such an entry from the stack entirely, and a replay would restore too shallow a
# position while reporting it as restored in full.
$byId = Push-WearWalkPosition -Position @() -Entry (New-WalkEntry -Id 'settings' -ResourceId 'settings_chip')

Assert-Equal -Label 'an entry naming a resource-id is on the stack, not dropped from it' `
    -Expected 1 -Actual (@($byId).Count)

Assert-Equal -Label 'and the level keeps the id, which is what a replay must tap' `
    -Expected 'settings_chip' -Actual ([string]$byId[0].resourceId)

Assert-Equal -Label 'backAfter 0 keeps the walk inside the section it opened' `
    -Expected 'Apps' -Actual (Get-PositionNames (Pop-WearWalkPosition -Position $apps -BackAfter 0))

$appsCalc = Push-WearWalkPosition -Position $apps -Entry (New-WalkEntry -Id 'apps-calculator' -Label 'Calculator')

Assert-Equal -Label 'backAfter 1 climbs one level' `
    -Expected 'Apps' -Actual (Get-PositionNames (Pop-WearWalkPosition -Position $appsCalc -BackAfter 1))

Assert-Equal -Label 'backAfter 2 leaves the section - the shape apps-system-info declares' `
    -Expected 0 -Actual (@(Pop-WearWalkPosition -Position $appsCalc -BackAfter 2).Count)

Assert-Equal -Label 'a backAfter past the bottom answers Home, never a negative depth' `
    -Expected 0 -Actual (@(Pop-WearWalkPosition -Position $apps -BackAfter 5).Count)

# The cascade decision. One failure is the ordinary case an optional or state-dependent entry gives
# on a clean device; two neighbours failing together is already unlike two independent absences.
Assert-Equal -Label 'one unreachable entry is not a cascade' `
    -Expected $false -Actual (Test-WearWalkCascade -ConsecutiveUnreachable 1 -Threshold 2)

Assert-Equal -Label 'two in a row is the default cascade' `
    -Expected $true -Actual (Test-WearWalkCascade -ConsecutiveUnreachable 2 -Threshold 2)

Assert-Equal -Label 'the 2026-09-09 run - eleven in a row - recovers instead of running to the end' `
    -Expected $true -Actual (Test-WearWalkCascade -ConsecutiveUnreachable 11 -Threshold 2)

Assert-Equal -Label 'threshold 0 disables the recovery, reproducing a pre-S2779 run' `
    -Expected $false -Actual (Test-WearWalkCascade -ConsecutiveUnreachable 11 -Threshold 0)

# The library is pure. A position model that reached for the device could not be driven from here,
# and the three fixes this script already carries are each pinned by a case that touches no watch.
$positionText = Get-Content -LiteralPath $positionLib -Raw

Assert-Equal -Label 'the position library calls no adb verb' `
    -Expected $false -Actual ($positionText -match 'Invoke-AdbVerb')

Assert-Equal -Label 'the position library sleeps for nothing' `
    -Expected $false -Actual ($positionText -match 'Start-Sleep')

# --- S2779: and the walk is wired to it ----------------------------------------------------------

Assert-Equal -Label 'the walk sources the position library' `
    -Expected $true -Actual ($walkText -match 'wear-walk-position\.ps1')

Assert-Equal -Label 'the cascade threshold is a parameter, defaulting to two' `
    -Expected $true -Actual ($walkText -match '\$RehomeAfterUnreachable = 2')

Assert-Equal -Label 'the entry loop asks the library whether it has lost its position' `
    -Expected $true -Actual ($walkText -match 'Test-WearWalkCascade -ConsecutiveUnreachable')

Assert-Equal -Label 'the walk pushes a level only for an entry that OPENED' `
    -Expected $true -Actual ($walkText -match 'Push-WearWalkPosition -Position \$position -Entry \$screen')

Assert-Equal -Label 'and pops the levels its backAfter presses climbed' `
    -Expected $true -Actual ($walkText -match 'Pop-WearWalkPosition -Position \$position -BackAfter \$backAfter')

Assert-Equal -Label 'a recovery replays the position instead of stopping at the relaunch' `
    -Expected $true -Actual ($walkText -match '(?s)function Restore-WalkPosition.*?tap-label.*?return \$restored')

# Measured on emulator-5554 2026-09-09: a bare tap restored 0 of 2 levels from a fresh launch,
# because `Apps` is the fourth row of Home on a 384x384 round face and a tap verb sees only what is
# rendered. The replay reaches a control the same way an entry does, or it cannot restore a position
# any real walk reaches.
# The launch verb is `am start -n <pkg>/<activity>`, which RESUMES a live task where it was left. A
# recovery that only launched returned to the screen the walk was stuck on: measured on emulator-5554
# 2026-09-09, 0 of 2 levels restored twice in a row, and 2 of 2 with the force-stop in front.
Assert-Equal -Label 'a re-home force-stops before it launches, or it recovers nothing' `
    -Expected $true -Actual ($walkText -match "(?s)function Restore-WalkPosition.*?'stop'.*?'launch'")

Assert-Equal -Label 'the replay reaches a level through the same hunt an entry uses' `
    -Expected $true -Actual ($walkText -match '(?s)function Restore-WalkPosition.*?Invoke-ReachControl -ResourceId \$level\.resourceId')

Assert-Equal -Label 'and the entry loop reaches through that one function too, not a second copy' `
    -Expected $true -Actual ($walkText -match 'Invoke-ReachControl -ResourceId \$screen\.resourceId')

Assert-Equal -Label 'the shared reach taps by id where there is one, never by a translated label' `
    -Expected $true -Actual ($walkText -match '(?s)function Invoke-ReachControl.*?if \(\$ResourceId\).*?tap-id')

# Measured on emulator-5554 2026-09-09: a cascade fired from Home, where nothing had been pushed, and
# the empty [object[]] bound as $null - `@($null)` is a one-element array holding nothing, so the
# replay sent `tap-label -Label ''` and the run died mid-walk on a parameter-binding error.
Assert-Equal -Label 'a replay of an EMPTY position taps nothing rather than tapping an empty label' `
    -Expected $true -Actual ($walkText -match '\$Path \| Where-Object \{ \$null -ne \$_ \}')

Assert-Equal -Label 'the app-in-front guard shares that recovery rather than relaunching alone' `
    -Expected $true -Actual ($walkText -match 'if \(\$appLeft -or \$cascade\)')

Assert-Equal -Label 'an unreachable entry judged from a restored position says so' `
    -Expected $true -Actual ($walkText -match 'reached for from a position restored in full')

Assert-Equal -Label 'every row carries the position it was reached from' `
    -Expected $true -Actual ($walkText -match 'position = @\(\$position \| ForEach-Object \{ \$_\.name \}\)')

Assert-Equal -Label 'the run counts its recoveries' `
    -Expected $true -Actual ($walkText -match 'rehomes\s+= \$rehomeCount')

Assert-Equal -Label 'and the summary line reports them' `
    -Expected $true -Actual ($walkText -match 'rehomes \$rehomeCount')

# A recovery is the sweep working, not the app failing: it must not enter the exit-code arithmetic.
Assert-Equal -Label 'a re-home never costs the walk its exit 0' `
    -Expected $false -Actual ($walkText -match '\$rehomeCount -gt 0')

# --- S2794: a foreign system window is `manual`, never `failed` or `unreachable` -----------------
#
# The 2026-09-09 run on SM-L310: the watch drained to 16%, Samsung's low-battery panel took the
# display, and the walk reported apps-calculator as `failed` and the next fourteen screens as
# `unreachable` - fifteen false verdicts in one run. The app-in-front guard reads `ResumedActivity`
# from `dumpsys activity activities`, but a system window is a window, not an activity, so the app
# remains the resumed activity and the guard sees it as "in front". `mCurrentFocus` from `dumpsys
# window` is the signal that sees the panel, because it names the window actually drawn on top.

Assert-Equal -Label 'the app on top parses as the app package' `
    -Expected 'com.sza.fastmediasorter' `
    -Actual (Get-TopWindowPackage (Read-Fixture 'window-app-on-top.txt'))

Assert-Equal -Label 'a system window on top parses as the system package' `
    -Expected 'com.android.systemui' `
    -Actual (Get-TopWindowPackage (Read-Fixture 'window-foreign-on-top.txt'))

Assert-Equal -Label 'a Samsung system window with a uid parses as the Samsung package' `
    -Expected 'com.samsung.android.systemui' `
    -Actual (Get-TopWindowPackage (Read-Fixture 'window-foreign-with-uid.txt'))

# S3181: a debug install's own window was read as foreign and three screens went `manual`.
Assert-Equal -Label 'the debug install window is the app, not a foreign window' `
    -Expected $true -Actual (Test-IsAppWindowPackage -Package 'com.sza.fastmediasorter.debug' -AppPackage 'com.sza.fastmediasorter')

Assert-Equal -Label 'the release install window is the app' `
    -Expected $true -Actual (Test-IsAppWindowPackage -Package 'com.sza.fastmediasorter' -AppPackage 'com.sza.fastmediasorter')

Assert-Equal -Label 'a system package is not the app' `
    -Expected $false -Actual (Test-IsAppWindowPackage -Package 'com.android.systemui' -AppPackage 'com.sza.fastmediasorter')

Assert-Equal -Label 'a capture without mCurrentFocus parses as nothing, never a guess' `
    -Expected '' -Actual (Get-TopWindowPackage (Read-Fixture 'window-no-focus.txt'))

Assert-Equal -Label 'empty input parses as nothing' `
    -Expected '' -Actual (Get-TopWindowPackage '')

# --- S2794: the battery precondition reads the level and refuses below a threshold ----------------
#
# The walk never reported the device's charge, so a run on a dying watch looked like a defect report.
# `dumpsys battery` prints `  level: 42` on its own line; an absent field is a question, not "probably
# fine" - the same discipline the wakefulness parser follows.

Assert-Equal -Label 'a normal battery level parses as the integer' `
    -Expected 42 -Actual (Get-BatteryLevel (Read-Fixture 'battery-normal.txt'))

Assert-Equal -Label 'a low battery level parses as the integer' `
    -Expected 16 -Actual (Get-BatteryLevel (Read-Fixture 'battery-low.txt'))

Assert-Equal -Label 'a capture without the level field parses as nothing, never a guess' `
    -Expected '' -Actual (Get-BatteryLevel (Read-Fixture 'battery-no-level.txt'))

Assert-Equal -Label 'empty battery input parses as nothing' `
    -Expected '' -Actual (Get-BatteryLevel '')

# --- S2794: the walk is wired to both preconditions ------------------------------------------------

# Re-read the walk text because the earlier assertions loaded it before S2794 touched the script.
$walkText = Get-Content -LiteralPath $walk -Raw

Assert-Equal -Label 'the walk sources the foreign-window library' `
    -Expected $true -Actual ($walkText -match 'wear-foreign-window\.ps1')

Assert-Equal -Label 'the walk sources the battery library' `
    -Expected $true -Actual ($walkText -match 'wear-battery\.ps1')

Assert-Equal -Label 'the walk declares a MinBatteryPct parameter defaulting to 20' `
    -Expected $true -Actual ($walkText -match '\$MinBatteryPct = 20')

Assert-Equal -Label 'the walk reads the battery before the screen loop' `
    -Expected $true `
    -Actual ($walkText.IndexOf('Get-BatteryLevel') -lt $walkText.IndexOf('foreach ($screen in $screens)'))

Assert-Equal -Label 'the walk exits 2 when the battery is below the threshold' `
    -Expected $true -Actual ($walkText -match 'Stop-Run 2 .*battery.*below')

Assert-Equal -Label 'the walk reports the battery level in the result object' `
    -Expected $true -Actual ($walkText -match '\$result\.batteryPct')

Assert-Equal -Label 'the walk defines the Get-ForeignWindowPackage wrapper' `
    -Expected $true -Actual ($walkText -match 'function Get-ForeignWindowPackage')

Assert-Equal -Label 'the foreign-window check runs before the unreachable outcome' `
    -Expected $true `
    -Actual ($walkText.IndexOf('Get-ForeignWindowPackage') -lt $walkText.IndexOf('$row.outcome = ''unreachable'''))

Assert-Equal -Label 'the foreign-window check runs in the not-observed branch' `
    -Expected $true `
    -Actual ($walkText -match "(?s)else \{.*?Get-ForeignWindowPackage.*?\`$row\.outcome = 'failed'")

Assert-Equal -Label 'a foreign-window manual names the intruding package in the detail' `
    -Expected $true -Actual ($walkText -match 'foreign system window is on top \(\$foreignPkg\)')

Assert-Equal -Label 'a foreign-window manual in the unreachable branch does not increment the cascade counter' `
    -Expected $true `
    -Actual ($walkText -match "(?s)Get-ForeignWindowPackage.*?if \(\`$null -ne \`$foreignPkg\).*?continue.*?consecutiveUnreachable\+\+")

# --- S3358: an entry declared for one flavor only ------------------------------------------------

$bothFlavors = '{"id":"apps-calculator"}' | ConvertFrom-Json
$sideloadOnly = '{"id":"resources","flavors":["noLegal"]}' | ConvertFrom-Json
$emptyScope = '{"id":"broken","flavors":[]}' | ConvertFrom-Json

Assert-Equal -Label 'an entry declaring no flavors is walkable in the store build' `
    -Expected $true -Actual (Test-WalkEntryInFlavor -Screen $bothFlavors -Flavor 'standard')

Assert-Equal -Label 'an entry declaring no flavors is walkable in the sideload build too' `
    -Expected $true -Actual (Test-WalkEntryInFlavor -Screen $bothFlavors -Flavor 'noLegal')

Assert-Equal -Label 'a noLegal-only entry is walkable there' `
    -Expected $true -Actual (Test-WalkEntryInFlavor -Screen $sideloadOnly -Flavor 'noLegal')

Assert-Equal -Label 'the same entry is not walkable on standard, which draws no such row' `
    -Expected $false -Actual (Test-WalkEntryInFlavor -Screen $sideloadOnly -Flavor 'standard')

Assert-Equal -Label 'an empty flavors list silences nothing - it is a declaration defect, not a scope' `
    -Expected $true -Actual (Test-WalkEntryInFlavor -Screen $emptyScope -Flavor 'standard')

Assert-Equal -Label 'a missing flavor argument never silences an entry' `
    -Expected $true -Actual (Test-WalkEntryInFlavor -Screen $sideloadOnly -Flavor '')

Assert-Equal -Label 'the walk asks the flavor-scope predicate about each entry' `
    -Expected $true -Actual ($walkText -match 'Test-WalkEntryInFlavor -Screen \$screen -Flavor \$result\.flavor')

# The skip must stay ahead of the control hunt. Below it the walk would scroll a list looking for a
# row the artifact does not carry, leave the screen somewhere else and fail the entry after it -
# which is exactly the cascade measured on 2026-09-20 and the reason this ticket exists.
Assert-Equal -Label 'the flavor skip runs before the walk reaches for any control' `
    -Expected $true `
    -Actual ($walkText.IndexOf('Test-WalkEntryInFlavor') -lt $walkText.IndexOf('$tap = Invoke-ReachControl'))

Assert-Equal -Label 'an out-of-flavor entry is counted apart from every other outcome' `
    -Expected $true -Actual ($walkText -match "outOfFlavor\s+= @\(\`$rows \| Where-Object \{ \`$_\.outcome -eq 'outOfFlavor' \}\)\.Count")

Assert-Equal -Label 'the walk verdict does not score an out-of-flavor row' `
    -Expected $false -Actual ($walkText -match '\$verdict = if \([^\r\n]*outOfFlavor')

Write-Host ""
Write-Host "wear-prerelease-walk.tests: $script:passed passed, $script:failed failed."
if ($script:failed -gt 0) { exit 1 }
exit 0
