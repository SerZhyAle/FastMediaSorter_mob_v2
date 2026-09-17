<#
.SYNOPSIS
    S1984 - walk the declared watch screens, capture evidence for each, then audit the process log.

.DESCRIPTION
    The first hand-run watch sweep drove the screens by hand, which is why its PASS meant something
    different from one day to the next (strategic S1984 section 7). This script walks a list held as
    data, so two runs visit the same screens in the same order and disagree only about the device.

    It reaches a screen by resource-id where one exists and by label otherwise, because most of the
    watch UI is Compose and carries no id. It never taps a coordinate: a list that scrolled between
    the dump and the tap sends a coordinate into the neighbouring row, which is exactly how two taps
    in one earlier watch sweep hit the wrong control (CLAUDE.md section 9).

    Five outcomes per screen, and the difference between them matters more than the count:
      observed    - the expected token was in the UI dump.
      failed      - the screen opened and the expected token was not on it. A product defect.
      unreachable - the control that opens the screen was never found, so the screen was never
                    opened and nothing about it was judged (S2767). Blocks the PASS exactly as
                    `failed` does - a screen nobody reached satisfies no Play requirement - but it
                    is reported apart from it, because "the walk could not get there" and "the app
                    is broken" call for opposite reactions and both used to print `failed (tap)`.
                    S2779 splits the detail line one level further: reached for from a position this
                    run restored in full, the control is genuinely not where the screen list places
                    it; reached for from a position that could not be restored, the walk may still
                    be standing somewhere else.
      manual   - nothing could be decided: the dump itself failed, or the screen is state-dependent
                 and its absence on a clean install proves nothing. A human still has to look.
      skipped  - an entry declared `optional` whose control is not on screen in this run. The first
                 launch of a fresh install shows a permission gate that a second run does not, and
                 an entry that is absent by design is neither a failure nor a question for a human.

    A destination is never recognised by its own title alone. Most watch screens repeat the label of
    the chip that opened them - the Home chip "Apps" opens a screen titled "Apps" - so a title match
    would also match the screen the walk was standing on. Each entry names a token that belongs to
    the destination and not to its parent.

.PARAMETER DeviceId
    Serial of the watch. Omitted: the wrapper picks, and refuses when the choice is ambiguous.

.PARAMETER OutDir
    Where the screenshots, the dumps, the log and walk.json land.

.PARAMETER ScreenList
    The declared walk. Default: the list shipped beside this script.

.PARAMETER RehomeAfterUnreachable
    How many consecutive `unreachable` entries mean the walk has lost its position rather than met
    that many absent controls. On reaching the count it relaunches the app and taps back down to the
    position it was tracking, then carries on. 0 disables the recovery.

.PARAMETER SkipLogAudit
    Walk the screens but do not harvest or audit the log. Recorded in the output.

.PARAMETER SkipShapeCheck
    Walk the screens but do not run clip-check per screen. Recorded in the output.

.PARAMETER Json
    Emit the result object instead of the human lines.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/wear-prerelease-walk.ps1 -DeviceId 192.168.1.166:46551

.NOTES
    Exit codes:
      0  every declared screen was observed, no OFF-GLASS finding (unless SkipShapeCheck), and log audit found nothing
      1  at least one screen failed or was unreachable, an OFF-GLASS finding was recorded, or the
         log audit reported a finding. A finding on an entry whose `acceptedOffGlass.flavors` names
         the installed flavor is recorded `shapeAccepted` and does not set this code (S3189)
      2  could not verify: the watch display could not be woken (S2547 - every reading under a
         sleeping display describes the watch face, not this app), the battery is below -MinBatteryPct
         (S2794 - a dying battery triggers a system panel that takes the display, and every screen
         reading under it describes the panel, not this app), the screen list is missing or
         unreadable, no device, a called script is absent, or clip-check could not run on at least
         one screen (S2782 - `shapeUnchecked`, a wrapper failure rather than a shape verdict; the
         glass was never judged there, which is not the same answer as judging it clean). A screen
         recorded `manual` does not by itself set this code - it is reported and carried into the
         verdict, which refuses the PASS.
#>
[CmdletBinding()]
param(
    [string]$DeviceId,

    [string]$OutDir = 'temp/scratch/wear-prerelease',

    [string]$ScreenList,

    # Milliseconds to let a screen settle before its tree is read. A per-entry `settleMs` overrides it.
    [int]$SettleMs = 1200,

    # Safety cap on the swipes any one scroll may spend, NOT the budget it is expected to use: since
    # S2767 every scroll here stops the moment the UI tree stops changing, which is the end of the
    # list, so this number is only what stops a list that never settles. Measured on emulator-5556
    # (384x384, one last-used shortcut on Home): Home takes 6 swipes top to bottom, Apps 5, Settings
    # 4 - so the old default of 4 was below the longest declared list and the walk reported reachable
    # screens as failures. 12 leaves room for one more screenful of shortcuts, and it has to leave
    # room rather than match a count: Home draws one row per last-used resource with no limit, so its
    # length is a property of the user's history, not of the app. A per-entry `maxScrolls` overrides it.
    [int]$MaxScrolls = 12,

    # S2779 - how many entries in a row may be recorded `unreachable` before the walk stops believing
    # the controls are absent and starts believing it is standing somewhere it should not be. On that
    # count it relaunches the app and taps its way back to the position it was tracking. 0 disables
    # the recovery, which reproduces a pre-S2779 run.
    [int]$RehomeAfterUnreachable = 2,

    # S2794 - refuse to walk a watch whose battery is too low to survive the run. A dying watch
    # triggers Samsung's low-battery panel, which takes the display and turns every screen reading
    # into a reading of the panel - fifteen false verdicts in one 2026-09-09 run. Below this
    # threshold the walk exits 2 (could-not-verify), the same code S2547 uses for a sleeping display.
    # 0 disables the check, reproducing a pre-S2794 run.
    [int]$MinBatteryPct = 20,

    [switch]$SkipLogAudit,

    [switch]$SkipShapeCheck,

    [switch]$Json
)

$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$APP_PACKAGE = 'com.sza.fastmediasorter'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adbWrapper = Join-Path $repoRoot 'scripts/devtest/adb.ps1'
$logAudit = Join-Path $repoRoot 'scripts/devtest/prerelease-log-audit.ps1'
. (Join-Path $PSScriptRoot 'lib/wear-wakefulness.ps1')
. (Join-Path $PSScriptRoot 'lib/clip-shape-outcome.ps1')
. (Join-Path $PSScriptRoot 'lib/wear-walk-position.ps1')
. (Join-Path $PSScriptRoot 'lib/wear-foreign-window.ps1')
. (Join-Path $PSScriptRoot 'lib/wear-battery.ps1')
if (-not $ScreenList) { $ScreenList = Join-Path $PSScriptRoot 'wear-prerelease-screens.json' }

$result = [ordered]@{
    ok            = $false
    exitCode      = 2
    device        = $null
    outDir        = $null
    screenSize    = $null
    flavor        = $null
    batteryPct    = $null
    screens       = @()
    counts        = $null
    coverage      = $null
    logFile       = $null
    logAuditExit  = $null
    skipLogAudit  = [bool]$SkipLogAudit
    skipShapeCheck= [bool]$SkipShapeCheck
    reason        = $null
}

function Stop-Run {
    param([int]$Code, [string]$Reason)
    Close-DeviceStateJournal
    $result.exitCode = $Code
    $result.ok = ($Code -eq 0)
    $result.reason = $Reason
    if ($Json) { [pscustomobject]$result | ConvertTo-Json -Depth 8 -Compress }
    else { Write-Error "wear-prerelease-walk: $Reason" -ErrorAction Continue }
    exit $Code
}

if (-not (Test-Path -LiteralPath $adbWrapper)) { Stop-Run 2 "required script not found: $adbWrapper" }
if (-not (Test-Path -LiteralPath $ScreenList)) { Stop-Run 2 "screen list not found: $ScreenList" }

try { $screens = @((Get-Content -LiteralPath $ScreenList -Raw | ConvertFrom-Json).screens) }
catch { Stop-Run 2 "screen list is not readable JSON: $ScreenList" }
if ($screens.Count -eq 0) { Stop-Run 2 "screen list declares no screen: $ScreenList" }

$outPath = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $repoRoot $OutDir }
New-Item -ItemType Directory -Path $outPath -Force | Out-Null
$result.outDir = $outPath

function Invoke-AdbVerb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $callArgs = @($Arguments)
    if ($DeviceId) { $callArgs += @('-DeviceId', $DeviceId) }
    $output = & pwsh -NoProfile -File $adbWrapper @callArgs 2>&1
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = ($output -join "`n") }
}

# --- Device state journal (S3201) ---------------------------------------------------------------
# The walk opens the journal before its first change to the watch and closes it on every exit path,
# so whatever the run changed - the ambient setting below included - is put back mechanically, and a
# restore is reported in walk.json rather than left to whoever reads the recipe.

$script:stateJournalOpen = $false

function Open-DeviceStateJournal {
    $begin = Invoke-AdbVerb -Arguments @('state-begin')
    $script:stateJournalOpen = ($begin.Exit -eq 0)
    $result.stateBegin = $begin.Output
}

function Close-DeviceStateJournal {
    if (-not $script:stateJournalOpen) { return }
    $script:stateJournalOpen = $false
    $check = Invoke-AdbVerb -Arguments @('state-check')
    $result.stateCheckExit = $check.Exit
    $result.stateRestored = @($check.Output -split "`r?`n" | Where-Object { $_ -like 'RESTORED *' })
}

Open-DeviceStateJournal

# --- Wakefulness (S2547) -------------------------------------------------------------------------

$ambientOriginal = $null

function Get-AmbientSetting {
    $probe = Invoke-AdbVerb -Arguments @('shell', '-Cmd', 'settings get global ambient_enabled')
    if ($probe.Exit -ne 0) { return $null }
    $value = ($probe.Output -split "`r?`n" | Where-Object { $_ -match '^\s*(0|1|null)\s*$' } | Select-Object -First 1)
    if ($null -eq $value) { return $null }
    return $value.Trim()
}

function Test-WalkDisplayAwake {
    # $true when the display is usable now. Never wakes anything - the caller decides whether it may.
    $dump = Invoke-AdbVerb -Arguments @('shell', '-Cmd', 'dumpsys power')
    if ($dump.Exit -ne 0) { return $false }
    return (Test-WearDisplayUsable (Get-WearWakefulness $dump.Output))
}

function Assert-WalkDisplayAwake {
    # The watch dozes on its own schedule, so this runs before the first screen AND between screens.
    # Ambient mode is turned off for the duration rather than poked awake once: a single KEYCODE_WAKEUP
    # buys a few seconds, and the walk is minutes long.
    param([int]$Attempts = 3, [int]$SettleFor = 1200)

    for ($try = 1; $try -le $Attempts; $try++) {
        if (Test-WalkDisplayAwake) { return $true }
        if ($null -eq $script:ambientOriginal) {
            $script:ambientOriginal = Get-AmbientSetting
        }
        Invoke-AdbVerb -Arguments @('shell', '-Cmd', 'settings put global ambient_enabled 0') | Out-Null
        Invoke-AdbVerb -Arguments @('key', '-Key', 'KEYCODE_WAKEUP') | Out-Null
        Start-Sleep -Milliseconds $SettleFor
    }
    return (Test-WalkDisplayAwake)
}

function Restore-AmbientSetting {
    # The sweep judges the watch; it does not reconfigure it. Only a value this run actually changed
    # is written back, and `null` means the setting was unset, which is restored as unset.
    if ($null -eq $script:ambientOriginal) { return }
    if ($script:ambientOriginal -eq 'null') {
        Invoke-AdbVerb -Arguments @('shell', '-Cmd', 'settings delete global ambient_enabled') | Out-Null
    }
    else {
        Invoke-AdbVerb -Arguments @('shell', '-Cmd', "settings put global ambient_enabled $script:ambientOriginal") | Out-Null
    }
    $script:ambientOriginal = $null
}

if (-not (Assert-WalkDisplayAwake -SettleFor $SettleMs)) {
    Restore-AmbientSetting
    Stop-Run 2 'the watch display is not awake (mWakefulness is not Awake, or dumpsys power could not be read). Every screen reading under a sleeping display describes the watch face, not this app.'
}

# S2794 - battery precondition. A dying watch triggers Samsung's low-battery panel, which takes the
# display and turns every screen reading into a reading of the panel. The walk never reported the
# charge, so a run on a dying watch looked like a defect report. Read it once at start; below the
# threshold, exit 2 (could-not-verify) the same way S2547 exits 2 for a sleeping display. An
# unreadable level is not a refusal - it is a question, and answering it with "probably fine" is the
# same error that made the 2026-09-04 run report sixteen confident readings of the wrong app.
if ($MinBatteryPct -gt 0) {
    $batteryProbe = Invoke-AdbVerb -Arguments @('shell', '-Cmd', 'dumpsys battery')
    $batteryPct = $null
    if ($batteryProbe.Exit -eq 0) { $batteryPct = Get-BatteryLevel $batteryProbe.Output }
    $result.batteryPct = $batteryPct
    if ($null -ne $batteryPct -and $batteryPct -lt $MinBatteryPct) {
        Restore-AmbientSetting
        Stop-Run 2 "the watch battery is at $batteryPct% (below the $MinBatteryPct% minimum). A dying battery triggers a system panel that takes the display, and every screen reading under it describes the panel, not this app."
    }
}

# Scroll geometry, read from the device rather than assumed: a round watch, a square one and an
# emulator do not share a screen size, and a swipe sized for one of them misses on the others.
$sizeProbe = Invoke-AdbVerb -Arguments @('shell', '-Cmd', 'wm size')
$screenW = 480
$screenH = 480
# `wm size` prints 'Physical size' first and an 'Override size' line after it when one is set, and the
# override is what the app is laid out against. Taking the first match sized every swipe for the
# physical panel: measured on the Galaxy Watch 7 (480x480 physical) put into the 384x384 small-round
# geometry 2026-09-12, the swipe started at y=360 - below the 271 px scroll viewport WearStateBlock
# centres on that glass - so no gesture ever reached it, nine consecutive trees were identical, and the
# Resources empty state scored failed for a Sync from Phone chip one scroll away. At 454 the same
# y=360 still fell inside the viewport, which is why only the smaller reviewed shape broke.
if ($sizeProbe.Exit -eq 0) {
    $sizeText = [string]($sizeProbe.Output -join ' ')
    $sizeMatch = [regex]::Match($sizeText, 'Override size:\s*(\d+)x(\d+)')
    if (-not $sizeMatch.Success) { $sizeMatch = [regex]::Match($sizeText, '(\d+)x(\d+)') }
    if ($sizeMatch.Success) {
        $screenW = [int]$sizeMatch.Groups[1].Value
        $screenH = [int]$sizeMatch.Groups[2].Value
    }
}
# A scroll, not a fling: the list must stop where it was put, or the next tree read describes a
# screen that is still moving.
$swipe = @{
    X1 = [int]($screenW / 2)
    Y1 = [int]($screenH * 0.75)
    X2 = [int]($screenW / 2)
    Y2 = [int]($screenH * 0.35)
}
$result.screenSize = "${screenW}x${screenH}"

# S3189 - which flavor is installed, read once. Both flavors share the application id, so only the
# versionName suffix separates them, and an entry may accept a shape finding for one flavor alone.
$versionProbe = Invoke-AdbVerb -Arguments @('shell', '-Cmd', "dumpsys package $APP_PACKAGE")
$versionName = $null
if ($versionProbe.Exit -eq 0) {
    $versionMatch = [regex]::Match([string]$versionProbe.Output, 'versionName=(\S+)')
    if ($versionMatch.Success) { $versionName = $versionMatch.Groups[1].Value }
}
$result.flavor = Get-WearFlavorFromVersionName $versionName

# The reverse swipe. A list keeps its scroll position while the walk is away inside one of its rows,
# so an entry that had to scroll down to be reached leaves every entry ABOVE it out of view for good -
# and the hunt below only ever scrolled downwards. That is not a flaky tap: it is one direction of
# travel on a list with two, and it turned one unrecognised screen into six unreachable ones on the
# 2026-08-26 run (S1984).
$swipeUp = @{
    X1 = [int]($screenW / 2)
    Y1 = [int]($screenH * 0.35)
    X2 = [int]($screenW / 2)
    Y2 = [int]($screenH * 0.75)
}

function Read-UiNodes {
    # One tree read, flattened to the text the walk matches against. Returns $null when the dump
    # could not be read at all, which the caller must tell apart from "read fine, token absent".
    $dump = Invoke-AdbVerb -Arguments @('uidump', '-Json')
    if ($dump.Exit -ne 0) { return $null }
    $tree = $null
    try { $tree = $dump.Output | ConvertFrom-Json } catch { return $null }
    if (-not $tree) { return $null }
    # The wrapper puts a verb's payload under `data`, not at the top level.
    $nodes = @(if ($null -ne $tree.data -and $null -ne $tree.data.nodes) { $tree.data.nodes } else { $tree.nodes })
    if ($nodes.Count -eq 0) { return $null }
    # The haystack carries desc as well as label, because the two are not alternatives. `adb.ps1`
    # fills `label` from a node's TEXT when it has any and falls back to its content-description only
    # when it has none, so a node carrying both arrived here as the text alone. Every Wear control
    # that captions a bare number is such a node: the mini-game's score reads `text="0"` with
    # `desc="Score: 0"`, so the marker `Score` could not match, and the walk scrolled twenty-four
    # times past a screen that was plainly showing before calling it a failure (S2555, measured on
    # emulator-5554 2026-09-05). That made every marker phrased as an accessibility description
    # unmatchable, which is why repairing the markers one at a time kept finding more of them.
    return @($nodes | ForEach-Object { "$($_.label) $($_.desc) $($_.resId)" }) -join "`n"
}

function Invoke-ScrollUntilSettled {
    # Swipe one way until the list stops moving, reading the tree after every swipe. Returns the last
    # readable tree, or $null when no dump could be read at all.
    #
    # S2767 - the two halves of this are what the fix is. It stops AT the end of the list instead of
    # swiping a fixed number of times past it, and it puts a tree read between consecutive swipes.
    # Both were needed: measured on emulator-5556 2026-09-09, one overscroll swipe on an
    # already-at-top list is harmless and changes nothing, while four of them back to back OPEN the
    # first row of the list - the last-used shortcut on Home - and start playback. Every reading after
    # that describes the audio player, and the app-in-front guard cannot see it because the player is
    # the same package. That is the whole of why four screens were called unreachable with the budget
    # raised to ten: the bigger budget meant MORE overscroll, not more reach.
    #
    # The caller's cap is a backstop against a list that never settles, not the expected spend.
    param(
        [Parameter(Mandatory)][hashtable]$Swipe,
        [int]$Cap,
        [int]$SettleFor,
        [scriptblock]$StopWhen
    )
    $previous = Read-UiNodes
    for ($step = 0; $step -lt $Cap; $step++) {
        if ($StopWhen -and (& $StopWhen $previous)) { return $previous }
        Invoke-AdbVerb -Arguments @('swipe', '-X', $Swipe.X1, '-Y', $Swipe.Y1, '-X2', $Swipe.X2, '-Y2', $Swipe.Y2, '-Duration', '400') | Out-Null
        Start-Sleep -Milliseconds $SettleFor
        $current = Read-UiNodes
        # An unreadable dump is not "the list stopped": it is no information at all, so the loop keeps
        # its previous reading and spends another swipe rather than concluding from a failure.
        if ($null -eq $current) { continue }
        if ($current -eq $previous) { return $current }
        $previous = $current
    }
    return $previous
}

function Reset-ListToTop {
    # Put the list back where the previous entry found it. Every entry then starts from one known
    # position instead of from wherever its predecessor happened to stop, which is what makes two runs
    # of this walk comparable at all - the whole point of a declared screen list.
    param([int]$Cap, [int]$SettleFor)
    if ($Cap -le 0) { return }
    Invoke-ScrollUntilSettled -Swipe $swipeUp -Cap $Cap -SettleFor $SettleFor | Out-Null
}

$rows = @()

# S2779 - the walk's standing position, as a stack of the labels tapped to get there. It is grown and
# shrunk by the entry loop through `lib/wear-walk-position.ps1` and read only by the recovery below.
$position = @()
$consecutiveUnreachable = 0
$rehomeCount = 0

function Invoke-ReachControl {
    # Tap one control, scrolling to it when it is not on screen yet. A watch list shows three or four
    # entries at a time, so most of a section's chips start below the fold and a tap verb only sees
    # what is currently rendered. Without the hunt the walk reports a working screen as unreachable,
    # which is what the second live run did to every entry after the fourth.
    #
    # S2779 made this a function so the position replay below reaches a control the same way an entry
    # does. It was a bare tap first, and measured on emulator-5554 2026-09-09 that restored 0 of 2
    # levels from a fresh launch: `Apps` is the fourth row of Home on a 384x384 round face, below the
    # fold, so the replay missed a chip the entry loop reaches every run.
    param([string]$ResourceId, [string]$Label, [int]$Cap, [int]$SettleFor)

    $reach = {
        if ($ResourceId) { Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', $ResourceId) }
        else { Invoke-AdbVerb -Arguments @('tap-label', '-Label', $Label) }
    }

    # Reach for the control where the walk is standing BEFORE moving the list (S2767). Most entries
    # return to their parent list exactly where their control still shows, so the common case costs
    # one tap and no scrolling at all - and since the settling reset below reads the tree after every
    # swipe, skipping it when it is not needed is most of this walk's running time. It is also the
    # safer order: the fewer swipes a run spends, the fewer chances a swipe has to land on something.
    $tap = & $reach
    if ($tap.Exit -ne 0) {
        # Start from the top of whatever list this is. The hunt below travels one way only, so without
        # this a control sitting above the previous entry's stopping point is unreachable no matter how
        # many times it scrolls - and which ones those are depends on where the last one stopped, which
        # is precisely the run-to-run divergence the declared list exists to remove.
        Reset-ListToTop -Cap $Cap -SettleFor $SettleFor
    }
    # Then hunt downwards from the top, one swipe and one reach at a time. An `optional` entry passes
    # 0 here and so is reached for exactly once, where it stands, and never hunted.
    $lastSeen = $null
    for ($try = 0; $try -lt $Cap -and $tap.Exit -ne 0; $try++) {
        Invoke-AdbVerb -Arguments @('swipe', '-X', $swipe.X1, '-Y', $swipe.Y1, '-X2', $swipe.X2, '-Y2', $swipe.Y2, '-Duration', '400') | Out-Null
        Start-Sleep -Milliseconds $SettleFor
        $tap = & $reach
        if ($tap.Exit -eq 0) { break }
        # S2767: stop at the end of the list rather than at the end of the budget. Past the last row
        # every further swipe is an overscroll, and a run of those is what opens the row under the
        # finger - so spending a leftover budget here is not merely wasted, it moves the walk to a
        # screen the next entry will be judged on.
        $current = Read-UiNodes
        if ($null -ne $current -and $current -eq $lastSeen) { break }
        if ($null -ne $current) { $lastSeen = $current }
    }
    return $tap
}

function Restore-WalkPosition {
    # S2779 - relaunch the app and tap back down to $Path. Returns how many of its labels were
    # re-entered, so the caller can tell "restored, and the control is still not there" from
    # "the replay itself came up short" - the first is a screen-list defect, the second is a walk that
    # is still lost, and reporting them alike is what left the 2026-09-09 run with eleven identical
    # `unreachable (control not found)` lines and no diagnosis.
    #
    # The relaunch is the recovery's whole point: Home is the one position this script can reach
    # without knowing where it currently is. A BACK chain cannot do it - the depth is exactly the
    # unknown - and a gesture would be device-specific.
    param([object[]]$Path, [int]$SettleFor, [int]$Cap)

    # Force-stop BEFORE the launch, or the recovery recovers nothing. `adb.ps1 launch` is
    # `am start -n <pkg>/<activity>`, which RESUMES a live task at whatever screen it was left on -
    # so relaunching an app that is stuck inside the Calculator returns to the Calculator. Measured on
    # emulator-5554 2026-09-09: the replay restored 0 of 2 levels twice in a row for this reason, and
    # the first level it tried to re-enter was already on screen behind it. The app-in-front guard has
    # carried the same flaw since S1984 - it relaunches an app that LEFT the foreground, and a task
    # left alive resumes where it was rather than at the start destination.
    Invoke-AdbVerb -Arguments @('stop', '-Module', 'wear', '-Release') | Out-Null
    Start-Sleep -Milliseconds $SettleFor
    Invoke-AdbVerb -Arguments @('launch', '-Module', 'wear', '-Release') | Out-Null
    Start-Sleep -Milliseconds $SettleFor

    $restored = 0
    # Filtered, not merely wrapped: an empty [object[]] parameter binds as $null, and `@($null)` is a
    # one-element array holding nothing - which sent a `tap-label -Label ''` at the device the first
    # time a cascade fired from Home, on a fixture whose first three entries pushed no level at all.
    foreach ($level in @($Path | Where-Object { $null -ne $_ })) {
        # Through the shared reach, so a level is re-entered exactly as its entry entered it: hunted
        # down the list when it is below the fold, and by id rather than by a translated label where
        # the level has one (CLAUDE.md section 9).
        $step = Invoke-ReachControl -ResourceId $level.resourceId -Label $level.label -Cap $Cap -SettleFor $SettleFor
        if ($step.Exit -ne 0) { break }
        Start-Sleep -Milliseconds $SettleFor
        $restored++
    }
    return $restored
}

# --- Foreign-window check (S2794) ----------------------------------------------------------------

function Get-ForeignWindowPackage {
    # Read the top visible window and return its package when it does NOT belong to the app. Returns
    # $null when the app's window is on top, when `dumpsys window` is unreadable, or when no focused
    # window can be parsed - $null means "not foreign", so the caller proceeds normally.
    #
    # The app-in-front guard reads `ResumedActivity` from `dumpsys activity activities`, but a system
    # window (Samsung's low-battery panel, a permission dialog) is a window, not an activity, so the
    # app remains the resumed activity and the guard sees the app as "in front". `mCurrentFocus` from
    # `dumpsys window` names the window actually drawn on top, which is the signal the guard cannot
    # read. Measured 2026-09-09 on SM-L310: fifteen false verdicts in one run because the panel took
    # the display and the tree returned its content, not the app's.
    $dump = Invoke-AdbVerb -Arguments @('shell', '-Cmd', 'dumpsys window')
    if ($dump.Exit -ne 0) { return $null }
    $pkg = Get-TopWindowPackage $dump.Output
    if ($null -eq $pkg -or (Test-IsAppWindowPackage -Package $pkg -AppPackage $APP_PACKAGE)) { return $null }
    return $pkg
}

foreach ($screen in $screens) {
    $row = [ordered]@{
        id       = $screen.id
        name     = $screen.name
        outcome  = 'manual'
        detail   = $null
        shot     = $null
        rehomed  = $false
        # The names rather than the level objects: walk.json is read by an operator diagnosing a run,
        # and `Apps > Calculator` is the whole answer to "where was this reached from".
        position = @($position | ForEach-Object { $_.name })
    }

    # Settle before reaching for the control too: the previous entry's BACK is still animating when
    # this iteration starts, and a tap verb re-reads the tree itself, so it would search the screen
    # that is on its way out.
    $entrySettleMs = if ($null -ne $screen.settleMs) { [int]$screen.settleMs } else { $SettleMs }
    Start-Sleep -Milliseconds $entrySettleMs

    # Is the display still awake? Checked BEFORE the app-in-front guard below, because that guard
    # relaunches the app and a relaunch under a dozing display satisfies it while every reading that
    # follows still describes the watch face (S2547).
    if (-not (Assert-WalkDisplayAwake -SettleFor $entrySettleMs)) {
        Restore-AmbientSetting
        Stop-Run 2 "the watch display fell asleep before '$($screen.id)' and could not be woken; the screens after it were never observed."
    }

    # Two reasons to go back to a known position, and one recovery for both.
    #
    # Is the app still in front? One BACK too many leaves it, and everything after that is measured
    # against the watch launcher while still being reported as this app's screens - nine failures in a
    # row on 2026-08-26, of which one was real. Re-entering costs a launch; not re-entering costs the
    # rest of the run, so the walk re-homes and says it did rather than carrying on blind.
    #
    # S2779 - or the walk is still inside its own package and lost anyway. A screen that swallowed a
    # BACK, or a `backAfter` that no longer matches its section, leaves the app in front and the walk
    # standing somewhere no later entry's control can be: on 2026-09-09 that made eleven consecutive
    # entries `unreachable` and spent 23 of the run's 35 minutes hunting for controls that were never
    # on screen. A run of unreachable outcomes is the symptom the guard above cannot see, so the
    # recovery is keyed on it as well.
    #
    # Both branches now REPLAY the tracked position rather than merely relaunching. The relaunch alone
    # lands on Home, and an entry declared inside a section - every settings page, every Apps page - is
    # unreachable from Home by construction, so a recovery that stopped at the launch would turn one
    # lost position into a cascade of its own.
    $current = Invoke-AdbVerb -Arguments @('current')
    $appLeft = ($current.Exit -eq 0 -and $current.Output -notmatch [regex]::Escape($APP_PACKAGE))
    $cascade = Test-WearWalkCascade -ConsecutiveUnreachable $consecutiveUnreachable -Threshold $RehomeAfterUnreachable
    $positionRestored = $false
    if ($appLeft -or $cascade) {
        $wanted = @($position).Count
        $restored = Restore-WalkPosition -Path $position -SettleFor $entrySettleMs -Cap $MaxScrolls
        $positionRestored = ($restored -eq $wanted)
        $rehomeCount++
        $consecutiveUnreachable = 0
        $row.rehomed = $true
        $row['rehomeReason'] = if ($appLeft) { 'the app was not in front' }
                               else { "$RehomeAfterUnreachable consecutive unreachable entries" }
        $row['rehomeRestored'] = "$restored/$wanted"
        if (-not $Json) {
            Write-Host "walk: $($screen.id) - re-homed ($($row.rehomeReason)), position restored $restored/$wanted" -ForegroundColor Yellow
        }
    }

    # Reach the control, scrolling when it is not on screen yet. A watch list shows three or four
    # entries at a time, so most of a section's chips start below the fold - and a tap verb only sees
    # what is currently rendered. Without this the walk reports a working screen as unreachable, which
    # is what the second live run did to every entry after the fourth.
    $tap = $null
    # An optional entry does not get hunted for. Its absence is the expected case, and scrolling the
    # list four times looking for a control that was never going to be there leaves the screen
    # somewhere else entirely - which then fails the NEXT entry, the one that was fine.
    $scrolls = if ($null -ne $screen.maxScrolls) { [int]$screen.maxScrolls }
               elseif ($screen.optional) { 0 }
               else { $MaxScrolls }
    if ($screen.resourceId -or $screen.label) {
        $tap = Invoke-ReachControl -ResourceId $screen.resourceId -Label $screen.label -Cap $scrolls -SettleFor $entrySettleMs
    }

    if ($tap -and $tap.Exit -ne 0) {
        if ($screen.optional) {
            # An entry that is present only in some starting states - the permission gate of a first
            # launch is the case this exists for. Absent means the state it belongs to is not this
            # run's state, which is neither a failure nor something a human needs to look at.
            $row.outcome = 'skipped'
            $row.detail = 'optional entry: its control is not on screen in this run'
            $rows += [pscustomobject]$row
            if (-not $Json) { Write-Host "walk: $($screen.id) -> skipped (optional)" -ForegroundColor Gray }
            continue
        }
        # S2794 - before declaring the screen unreachable, check whether a foreign system window took
        # the display. The control was not found because the tree returned the intruding window, not
        # the app - which is not a walk that lost its position and not an app that is broken. A
        # foreign-window `manual` does not increment `consecutiveUnreachable` (the position is not
        # lost) and does not fire the cascade recovery (which would relaunch into the same panel).
        $foreignPkg = Get-ForeignWindowPackage
        if ($null -ne $foreignPkg) {
            $row.outcome = 'manual'
            $row.detail = "a foreign system window is on top ($foreignPkg); the control was not found because the app is behind it, not broken"
            $rows += [pscustomobject]$row
            if (-not $Json) { Write-Host "walk: $($screen.id) -> manual (foreign window: $foreignPkg)" -ForegroundColor Yellow }
            continue
        }
        # S2767: never opened, so nothing about this screen was judged - which is a different report
        # from "opened and wrong", not a milder one. Both block the PASS; only this one means the walk
        # itself came up short, and printing both as `failed (tap)` is what made an unreached screen
        # read as a product regression and cost two rebuilds on the 2026-09-09 run.
        #
        # No BACK here, deliberately. `backAfter` says how many levels to climb out of a screen that
        # OPENED; the tap never fired, so the walk is still standing on the parent list, and climbing
        # out of that would leave the section its neighbouring entries are still walking. The cascade
        # this branch used to start is removed where it began - the reset above no longer navigates.
        #
        # S2779 - and say which of the two questions this answer settles. An entry reached for from a
        # position this iteration restored in full was judged from a known place, so its control is
        # genuinely not where the screen list says it is; one reached for from a position the replay
        # could not restore, or from no recovery at all, may be either that or a walk still standing
        # somewhere else. The two call for opposite reactions - edit the screen list, or fix the
        # walk's navigation - and printing both as `control not found` is what left the 2026-09-09 run
        # with eleven identical lines and no way to tell which kind it had.
        $row.outcome = 'unreachable'
        $row.detail = if ($positionRestored) {
            "the control is not on the screen the list places it on: reached for from a position restored in full ($($row.rehomeRestored)) - $($tap.Output)"
        }
        else {
            "could not reach the screen, so it was never opened or judged: $($tap.Output)"
        }
        $rows += [pscustomobject]$row
        $consecutiveUnreachable++
        if (-not $Json) {
            $why = if ($positionRestored) { 'control absent from a restored position' } else { 'control not found' }
            Write-Host "walk: $($screen.id) -> unreachable ($why)" -ForegroundColor Red
        }
        continue
    }

    # The control answered, so this entry OPENED and the walk is one level deeper than it was. Pushed
    # here rather than at the end of the iteration because every `continue` above leaves without
    # opening anything, and an entry that never opened moves nothing (S2779).
    $position = Push-WearWalkPosition -Position $position -Entry $screen
    $consecutiveUnreachable = 0

    # Settle, then look, then look once more. A watch screen is still animating when the tap returns,
    # and a tree read mid-transition shows the screen being left rather than the one being entered -
    # `adb.ps1`'s own tap verbs say as much when they find nothing. Deciding on that first tree is how
    # a working screen gets recorded as a failure, which is what the first live run of this script did
    # to sixteen screens out of eighteen.
    $settleMs = if ($null -ne $screen.settleMs) { [int]$screen.settleMs } else { $SettleMs }
    # $tree holds the flattened text of the last readable dump. It stays $null when no dump could be
    # read at all, which is the `manual` case below - told apart from "read fine, token absent".
    $tree = $null
    $present = $false

    for ($attempt = 1; $attempt -le 2; $attempt++) {
        Start-Sleep -Milliseconds $settleMs
        $haystack = Read-UiNodes
        if ($null -eq $haystack) { continue }
        $tree = $haystack
        if ($haystack -match [regex]::Escape([string]$screen.expect)) { $present = $true; break }
    }

    # Still not found: hunt for it the same way the tap above hunts for a control, instead of judging
    # the screen by the slice of it that happens to be in view. A marker is chosen because it belongs
    # to the destination, not because it fits on 480 px - `Clear` is the calculator's C key at the
    # bottom of a scrolling keypad, and `Favourites` is the last Home section, below the fold on a
    # small round face; both were reported missing from screens that were plainly showing (S1984).
    # Downwards first, because that is where most of a list is; then back to the top for a marker the
    # list had already scrolled past.
    #
    # S2555 replaced the second example. It used to call the app name the home list's header, sitting
    # ABOVE the list's resting position, and there is no such header: HomeScreen draws shortcuts, then
    # the HomeSectionCatalog sections, then the command bar, and `app_name` is named only by the
    # manifest, so no composable in the module ever renders it.
    # The upward hunt is still right - it is why a marker the list scrolled past is still found - but
    # it was being justified by a screen element that does not exist, which is the same wrong belief
    # that put four unmatchable markers in wear-prerelease-screens.json.
    #
    # S2767: both hunts stop at the end of the list, not at the end of the budget. The upward one used
    # to spend twice the budget unconditionally - 24 swipes at the current default - and every swipe
    # past the top row is an overscroll, which in a run is what opens the row under the finger.
    $expectToken = [string]$screen.expect
    $stopOnExpect = { param($seen) $null -ne $seen -and $seen -match [regex]::Escape($expectToken) }
    if (-not $present -and $null -ne $tree) {
        $seen = Invoke-ScrollUntilSettled -Swipe $swipe -Cap $MaxScrolls -SettleFor $settleMs -StopWhen $stopOnExpect
        if (& $stopOnExpect $seen) { $present = $true }
    }
    if (-not $present -and $null -ne $tree) {
        $seen = Invoke-ScrollUntilSettled -Swipe $swipeUp -Cap $MaxScrolls -SettleFor $settleMs -StopWhen $stopOnExpect
        if (& $stopOnExpect $seen) { $present = $true }
    }

    if (-not $tree) {
        $row.detail = 'the UI tree could not be read or carried no node on any attempt'
        $rows += [pscustomobject]$row
        if (-not $Json) { Write-Host "walk: $($screen.id) -> manual (no usable dump)" -ForegroundColor Yellow }
        continue
    }

    # S2782: 'clean' | 'finding' | 'unchecked'. The last one is clip-check's own wrapper failing, not
    # a statement about the glass, and it must not be printed as a WO-V16 violation.
    $shapeClass = 'clean'
    if ($present) {
        $row.outcome = 'observed'
        if (-not $SkipShapeCheck) {
            $clip = Invoke-AdbVerb -Arguments @('clip-check')
            $row['shapeExit'] = $clip.Exit
            $shapeClass = Get-ClipShapeClass $clip.Exit
            if ($shapeClass -ne 'clean') { $row['shapeDetail'] = $clip.Output }
            if ($shapeClass -eq 'finding' -and (Test-WalkShapeAccepted -Screen $screen -Flavor $result.flavor)) {
                $row['shapeAccepted'] = [string]$screen.acceptedOffGlass.reason
                $shapeClass = 'accepted'
            }
        }
    }
    else {
        # S2794 - the expected token was not found. Before declaring a product defect or a
        # state-dependent absence, check whether a foreign system window took the display: the tree
        # read may have returned the intruding window's content, not the app's. The control was tapped
        # (the screen opened), so the walk still owes its `backAfter` BACK presses below - this is why
        # the foreign-window case does not `continue` like the unreachable one does.
        $foreignPkg = Get-ForeignWindowPackage
        if ($null -ne $foreignPkg) {
            $row.outcome = 'manual'
            $row.detail = "a foreign system window is on top ($foreignPkg); expected '$($screen.expect)' was not read because the app is behind it, not broken"
        }
        elseif ($screen.stateDependent) {
            # Absence proves nothing here: the screen only exists once the user has created the state it
            # lists, so a clean install is expected to lack it and a human decides whether that is right.
            $row.detail = "expected '$($screen.expect)' absent, and this screen is state-dependent - a human must judge it"
        }
        else {
            $row.outcome = 'failed'
            $row.detail = "expected '$($screen.expect)' is not on the screen"
        }
    }

    $shot = Invoke-AdbVerb -Arguments @('shot', '-OutDir', $outPath)
    if ($shot.Exit -eq 0) {
        $shotLine = ($shot.Output -split "`r?`n" | Where-Object { $_ -match '\.png' } | Select-Object -First 1)
        $row.shot = $shotLine
    }

    $rows += [pscustomobject]$row
    if (-not $Json) {
        $shapeColour = switch ($shapeClass) { 'finding' { 'Red' } 'unchecked' { 'Yellow' } 'accepted' { 'Yellow' } default { 'Green' } }
        $colour = switch ($row.outcome) { 'observed' { $shapeColour } 'failed' { 'Red' } default { 'Yellow' } }
        $shapeNote = switch ($shapeClass) { 'finding' { ' (OFF-GLASS)' } 'unchecked' { ' (shape unchecked)' } 'accepted' { " (OFF-GLASS accepted on $($result.flavor))" } default { '' } }
        Write-Host "walk: $($screen.id) -> $($row.outcome)$shapeNote" -ForegroundColor $colour
    }

    # How many levels this entry sits above the next one. A nested block - the settings pages, the
    # mini-programs - declares 0 on the section it opens and 1 on each page inside it, so the walk
    # comes back out by the same number of steps it went in by.
    $backAfter = if ($null -ne $screen.backAfter) { [int]$screen.backAfter } else { 1 }
    for ($b = 0; $b -lt $backAfter; $b++) { Invoke-AdbVerb -Arguments @('key', '-Key', 'BACK') | Out-Null }
    $position = Pop-WearWalkPosition -Position $position -BackAfter $backAfter
}

$result.screens = $rows

# Coverage is a statement of scope, not a failure condition (S2547): it never changes the arithmetic
# below. It exists because a PASS used to say nothing about how much of the app it had opened, and
# clip-check - which decides WO-V16 - only runs on a screen the walk actually reached.
$excludedDeclared = @()
try { $excludedDeclared = @((Get-Content -LiteralPath $ScreenList -Raw | ConvertFrom-Json).excluded) } catch { $excludedDeclared = @() }
$result['coverage'] = [ordered]@{
    walked   = @($screens | ForEach-Object { $_.screen } | Where-Object { $_ } | Select-Object -Unique).Count
    excluded = $excludedDeclared.Count
    entries  = $screens.Count
}

# S2782: both counts come from Get-ClipShapeClass, the same classifier the verdict reads the rows
# with, so the two halves of the sweep cannot disagree about what a given exit code meant.
# S3189: through Get-WalkRowShapeClass, so a finding the entry accepts for this flavor is not a failure.
$shapeFailuresCount  = @($rows | Where-Object { (Get-WalkRowShapeClass $_) -eq 'finding' }).Count
$shapeUncheckedCount = @($rows | Where-Object { (Get-WalkRowShapeClass $_) -eq 'unchecked' }).Count
$shapeAcceptedCount  = @($rows | Where-Object { (Get-WalkRowShapeClass $_) -eq 'accepted' }).Count
$result.counts = [ordered]@{
    observed       = @($rows | Where-Object { $_.outcome -eq 'observed' }).Count
    failed         = @($rows | Where-Object { $_.outcome -eq 'failed' }).Count
    unreachable    = @($rows | Where-Object { $_.outcome -eq 'unreachable' }).Count
    manual         = @($rows | Where-Object { $_.outcome -eq 'manual' }).Count
    shapeFailures  = $shapeFailuresCount
    shapeUnchecked = $shapeUncheckedCount
    shapeAccepted  = $shapeAcceptedCount
    # S2779: reported, never scored. A re-home says the walk recovered a position it had lost, which
    # is the sweep working rather than the app failing - the screens it recovered are judged by their
    # own outcomes above, and adding a count of recoveries to the arithmetic would fail a run for
    # having handled its own navigation.
    rehomes        = $rehomeCount
}

# --- Log harvest and audit ----------------------------------------------------------------------

if (-not $SkipLogAudit) {
    $logPath = Join-Path $outPath 'wear_session.log'
    $log = Invoke-AdbVerb -Arguments @('log', '-Tail', '4000')
    if ($log.Exit -ne 0) {
        $result.logAuditExit = 2
    }
    else {
        Set-Content -LiteralPath $logPath -Value $log.Output -Encoding UTF8
        $result.logFile = $logPath

        if (-not (Test-Path -LiteralPath $logAudit)) {
            $result.logAuditExit = 2
        }
        else {
            # The audit already defaults to the shared application id, which the watch publishes
            # under, so it attributes the log to the watch process without any change of its own.
            $auditOutput = & pwsh -NoProfile -File $logAudit -LogFile $logPath -Package $APP_PACKAGE 2>&1
            $result.logAuditExit = $LASTEXITCODE
            if ($result.logAuditExit -ne 0 -and -not $Json) { Write-Host ($auditOutput -join "`n") }
        }
    }
}

Restore-AmbientSetting
Close-DeviceStateJournal

$walkPath = Join-Path $outPath 'walk.json'
[pscustomobject]$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $walkPath -Encoding UTF8

# S2767: an unreachable screen counts against the run exactly as a failed one does. It satisfied no
# Play requirement, and letting it pass would be the green verdict about the unseen that this walk
# exists to prevent.
#
# S2782: a shape nobody could check is the OTHER answer, and it takes the could-not-verify code. The
# walk used to spend exit 1 on it, which reads as "the app breaks WO-V16" and routes the operator to
# rebuild - so an adb hiccup cost the same as a real violation and looked identical in the report.
$verdict = if ($result.counts.failed -gt 0 -or $result.counts.unreachable -gt 0 -or $shapeFailuresCount -gt 0 -or $result.logAuditExit -eq 1) { 1 }
           elseif ($shapeUncheckedCount -gt 0 -or $result.logAuditExit -eq 2) { 2 }
           else { 0 }

$result.exitCode = $verdict
$result.ok = ($verdict -eq 0)

if ($Json) { [pscustomobject]$result | ConvertTo-Json -Depth 8 -Compress }
else {
    $batteryNote = if ($null -ne $result.batteryPct) { "battery $($result.batteryPct)%; " } else { '' }
    Write-Host ("wear-prerelease-walk: ${batteryNote}observed $($result.counts.observed), failed $($result.counts.failed), unreachable $($result.counts.unreachable), manual $($result.counts.manual), shapeFailures $shapeFailuresCount, shapeUnchecked $shapeUncheckedCount, shapeAccepted $shapeAcceptedCount ($($result.flavor)), rehomes $rehomeCount; coverage $($result.coverage.walked) walked + $($result.coverage.excluded) excluded; log audit $($result.logAuditExit); walk $walkPath") -ForegroundColor Cyan
}
exit $verdict
