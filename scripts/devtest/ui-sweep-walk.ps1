#!/usr/bin/env pwsh
<#
.SYNOPSIS
    S2380 - walk the declared phone screen catalog across the run matrix, capture a frame plus a
    node tree for every screen in every combination, and journal an explicit outcome for each row.

.DESCRIPTION
    Three data files drive this script and none of the matrix lives in it:

      ui-sweep-matrix.json     the four dimensions - profile, language, theme, orientation - their
                               values, and the named subsets of a shortened run (step 04.1)
      ui-sweep-profiles.json   the bench each profile value is asserted against (Phase 01)
      ui-sweep-screens.json    the screen catalog: reach fields, expected tokens, expand nodes,
                               setup and teardown (Phase 03)

    Per combination the walker first BRINGS THE DEVICE TO THE DECLARED STATE and refuses to walk
    with a state it could not confirm (step 04.2): the bench geometry is asserted through the
    Phase 01 script, the theme is set through the Phase 02 debug broadcast and the APPLIED
    ACKNOWLEDGEMENT CODE is compared - `am broadcast` exits 0 whether or not a receiver ran, so
    only the result code distinguishes "applied" from "this build has no hook" - the language is
    set per-app before the catalog pass begins (a locale switch recreates the activity, so it
    never happens inside the per-screen loop), and the orientation is a settings write that is
    read back. An unconfirmable step records its own refusal outcome for the whole combination
    instead of photographing frames labelled with settings they do not have.

    Before the first combination of a bench, the walker runs the catalog's setup once: it reaches
    the secure-sensitive-screens toggle IN GENERAL SETTINGS BY RESOURCE ID and reads its checked
    state from the node tree before tapping, so a switch already in the target position is never
    flipped past it; it enables the Streams sub-program the same way; and it configures one local
    resource over the seeded root through the system folder picker. If the secure toggle cannot be
    reached, the sweep does not fail - the four secure surfaces are recorded with their own refusal
    outcome and everything else is walked. Teardown runs on every exit path, including an aborted
    run: the toggle is put back, the locale and theme are restored, and the device state journal is
    closed (step 04.3).

    Each screen is reached by resource id or by resolved label - never by a remembered coordinate -
    with a scroll hunt for controls below the fold; the expected token is asserted from the node
    tree; a screenshot and the tree dump are stored under a name that carries BOTH the combination
    key and the screen id (step 04.4); then the declared expand nodes are opened one by one and
    each expanded state is captured in turn (collapsed again before the next).

    Five outcomes come from the wear walker's taxonomy plus the run-level refusals this walk adds
    (step 04.5):

      observed       the expected token is in the tree
      failed         the screen opened and the token is absent - a product defect
      unreachable    the control was never found, so the screen was never opened or judged
      manual         nothing could be decided: the dump failed, a foreign window is on top, or the
                     screen is state-dependent and its absence proves nothing
      skipped        an `optional` or `stateDependent` entry whose control is absent - the state it
                     belongs to is not this run's state
      refused-state  run-level: the combination's device state could not be confirmed (bench
                     mismatch, theme broadcast not acknowledged, language not confirmed, rotation
                     not confirmed), so no frame was captured
      refused-secure run-level: the secure-sensitive-screens toggle could not be switched off, so
                     the four FLAG_SECURE surfaces are not photographed

    A lost position is recovered the only way the phone allows - no internal screen can be launched
    directly, so the recovery force-stops the app, launches the entry point and replays the stack
    of tapped levels, reporting how many were actually restored. It fires when the app leaves the
    foreground or when consecutive unreachable outcomes accumulate.

    The journal lands beside the raw corpus: one row per screen per combination, carrying the
    outcome, the artifact paths and the reason where there is one (step 04.6). The run asserts the
    row-count identity - rows equal screens times combinations - before it scores itself.

.PARAMETER DeviceId
    Serial used for every profile. For a matrix covering several profiles pass -DeviceMap instead;
    one attached device can only ever assert one bench.

.PARAMETER DeviceMap
    Per-profile serials, 'profile=serial' pairs separated by ';' or ','.

.PARAMETER Subset
    Name of a subset declared in the matrix. Omit for the full matrix.

.PARAMETER Only
    Walk a single screen id - the narrow run that proves capture mechanics. Skips setup.

.PARAMETER OutDir
    Corpus root: screenshots, tree dumps and the journal land here.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/ui-sweep-walk.ps1 -Subset quick -DeviceId emulator-5554

.NOTES
    Exit codes:
      0 - every declared combination walked: every row observed or legitimately skipped
      1 - at least one product defect observed - a screen opened and its expected token was absent
      2 - could not verify: at least one row is unreachable, manual or a run-level refusal
          (unconfirmed combination state, unreachable control, unreadable tree, foreign window,
          secure surfaces left unphotographed), or the row-count identity failed, or the matrix
          itself could not be read, or no device could be asserted for a profile. Distinct from 1
          on purpose: "the app is broken here" and "the walk never got to judge" call for opposite
          reactions.
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [string]$DeviceMap,
    [string]$Subset,
    [string]$Only,
    [string]$OutDir = 'temp/S2380/sweep',
    [string]$Matrix,
    [string]$Profiles,
    [string]$Screens,
    [int]$SettleMs = 1200,
    [int]$MaxScrolls = 12,
    [int]$RehomeAfterUnreachable = 2,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adbWrapper = Join-Path $repoRoot 'scripts/devtest/adb.ps1'
$benchScript = Join-Path $PSScriptRoot 'ui-sweep-bench.ps1'
if (-not $Matrix) { $Matrix = Join-Path $PSScriptRoot 'ui-sweep-matrix.json' }
if (-not $Profiles) { $Profiles = Join-Path $PSScriptRoot 'ui-sweep-profiles.json' }
if (-not $Screens) { $Screens = Join-Path $PSScriptRoot 'ui-sweep-screens.json' }

$APP_PACKAGE = 'com.sza.fastmediasorter'
# The acknowledgement codes ThemeTestHooks answers with. Compared against the broadcast's result
# code - never against the process exit code, which is 0 whether or not a receiver ran.
$THEME_ACK_APPLIED = 2380
$THEME_ACK_REJECTED = 2381

$result = [ordered]@{
    ok        = $false
    exitCode  = 2
    subset    = $Subset
    devices   = $null
    outDir    = $null
    combinations = @()
    counts    = $null
    reason    = $null
}

function Stop-Run {
    param([int]$Code, [string]$Reason)
    $result.exitCode = $Code
    $result.ok = ($Code -eq 0)
    $result.reason = $Reason
    if ($Json) { [pscustomobject]$result | ConvertTo-Json -Depth 8 -Compress }
    else { Write-Error "ui-sweep-walk: $Reason" -ErrorAction Continue }
    exit $Code
}

foreach ($required in @($adbWrapper, $benchScript, $Matrix, $Profiles, $Screens)) {
    if (-not (Test-Path -LiteralPath $required)) { Stop-Run 2 "required file not found: $required" }
}

try {
    $matrixJson = Get-Content -LiteralPath $Matrix -Raw -Encoding UTF8 | ConvertFrom-Json
    $screensDoc = Get-Content -LiteralPath $Screens -Raw -Encoding UTF8 | ConvertFrom-Json
    $profilesJson = Get-Content -LiteralPath $Profiles -Raw -Encoding UTF8 | ConvertFrom-Json
}
catch { Stop-Run 2 "a declared data file is not readable JSON: $($_.Exception.Message)" }

$dimensions = @($matrixJson.dimensions)
if ($dimensions.Count -lt 4) { Stop-Run 2 "the matrix declares fewer than the four dimensions" }
$pkg = $matrixJson.package
$seedRoot = $matrixJson.seedRoot
$seedFolderName = Split-Path -Leaf ($seedRoot -replace '/$', '')

$screensAll = @($screensDoc.screens)
if ($screensAll.Count -eq 0) { Stop-Run 2 "the screen catalog declares no screen" }
$screenById = @{}
foreach ($s in $screensAll) { $screenById[$s.id] = $s }
if ($Only) {
    $screens = @($screensAll | Where-Object { $_.id -eq $Only })
    if ($screens.Count -eq 0) { Stop-Run 2 "-Only '$Only' names no screen in the catalog" }
}
else { $screens = $screensAll }

# --- expand the matrix into combinations ---------------------------------------------------------

function Get-DimensionValues {
    param($Dim, $Filter)
    $ids = [System.Collections.Generic.List[object]]::new()
    foreach ($v in @($Dim.values)) {
        $id = if ($v -is [string]) { $v } else { [string]$v.id }
        if (-not $Filter -or @($Filter) -contains $id) { $ids.Add($v) }
    }
    return $ids
}

$subsetFilter = $null
if ($Subset) {
    $subsetFilter = $matrixJson.subsets.$Subset
    if (-not $subsetFilter) { Stop-Run 2 "unknown subset '$Subset'. Declared: $((($matrixJson.subsets.PSObject.Properties.Name)))" }
}
$dimByName = @{}
foreach ($d in $dimensions) { $dimByName[$d.id] = $d }
foreach ($required in @('profile', 'language', 'theme', 'orientation')) {
    if (-not $dimByName.ContainsKey($required)) { Stop-Run 2 "the matrix declares no '$required' dimension" }
}
$profileValues = Get-DimensionValues $dimByName['profile'] $subsetFilter.profile
$languageValues = Get-DimensionValues $dimByName['language'] $subsetFilter.language
$themeValues = Get-DimensionValues $dimByName['theme'] $subsetFilter.theme
$orientationValues = Get-DimensionValues $dimByName['orientation'] $subsetFilter.orientation

$combinations = @()
foreach ($p in $profileValues) {
    foreach ($l in $languageValues) {
        foreach ($t in $themeValues) {
            foreach ($o in $orientationValues) {
                $pid = if ($p -is [string]) { $p } else { [string]$p.id }
                $lid = if ($l -is [string]) { $l } else { [string]$l.id }
                $tid = if ($t -is [string]) { $t } else { [string]$t.id }
                $oid = if ($o -is [string]) { $o } else { [string]$o.id }
                $combinations += ,@{
                    key         = "${pid}_${lid}_${tid}_${oid}"
                    profile     = $pid
                    language    = $l
                    theme       = $tid
                    orientation = $o
                }
            }
        }
    }
}
if ($combinations.Count -eq 0) { Stop-Run 2 "the matrix expanded to zero combinations" }
$result.combinations = @($combinations | ForEach-Object { $_.key })

# --- device map ----------------------------------------------------------------------------------

$deviceForProfile = @{}
foreach ($p in $profileValues) {
    $pid = if ($p -is [string]) { $p } else { [string]$p.id }
    if ($DeviceId) { $deviceForProfile[$pid] = $DeviceId }
}
if ($DeviceMap) {
    foreach ($pair in ($DeviceMap -split '[;,]')) {
        if ($pair -notmatch '^\s*([^=]+)=(.+)\s*$') { Stop-Run 2 "-DeviceMap entry '$pair' is not profile=serial" }
        $deviceForProfile[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}
foreach ($p in $profileValues) {
    $pid = if ($p -is [string]) { $p } else { [string]$p.id }
    if (-not $deviceForProfile.ContainsKey($pid)) {
        Stop-Run 2 "no device for profile '$pid' - pass -DeviceId (single bench) or -DeviceMap ${pid}=<serial>"
    }
}
$result.devices = [ordered]@{}
foreach ($k in $deviceForProfile.Keys) { $result.devices[$k] = $deviceForProfile[$k] }

# --- output --------------------------------------------------------------------------------------

$outPath = if ([System.IO.Path]::IsPathRooted($OutDir)) { $OutDir } else { Join-Path $repoRoot $OutDir }
New-Item -ItemType Directory -Path $outPath -Force | Out-Null
$result.outDir = $outPath

# --- device plumbing -----------------------------------------------------------------------------

$script:dev = $null

function Invoke-AdbVerb {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $callArgs = @($Arguments)
    if ($script:dev) { $callArgs += @('-DeviceId', $script:dev) }
    $output = & pwsh -NoProfile -File $adbWrapper @callArgs 2>&1
    return [pscustomobject]@{ Exit = $LASTEXITCODE; Output = (($output | ForEach-Object { $_.ToString() }) -join "`n") }
}

function Invoke-AdbJson {
    # One -Json verb call, parsed. Returns $null when the verb failed or printed nothing parseable.
    param([Parameter(Mandatory)][string[]]$Arguments)
    $call = Invoke-AdbVerb -Arguments $Arguments
    if ($call.Exit -ne 0) { return $null }
    $line = ($call.Output -split "`r?`n" | Where-Object { $_.StartsWith('{') } | Select-Object -First 1)
    if (-not $line) { return $null }
    try { $obj = $line | ConvertFrom-Json } catch { return $null }
    if ($obj.data) { return $obj.data }
    return $obj
}

function Invoke-Shell {
    param([Parameter(Mandatory)][string]$Command)
    return (Invoke-AdbVerb -Arguments @('shell', '-Cmd', $Command))
}

# --- string resources ----------------------------------------------------------------------------

$script:resCache = @{}

function Get-ResourceValue {
    # The drawn text of one string resource in the CURRENT combination's language. The sweep runs in
    # two languages, so both reach labels and expected tokens are resolved per language from the
    # module's own resource files - never hardcoded, and never taken from the key name.
    param([string]$Folder, [string]$Name)
    if (-not $Name) { return $null }
    $cacheKey = "$Folder/$Name"
    if ($script:resCache.ContainsKey($cacheKey)) { return $script:resCache[$cacheKey] }
    $resFile = Join-Path $repoRoot "app_v2/src/main/res/$Folder/strings.xml"
    if (-not (Test-Path -LiteralPath $resFile)) { $script:resCache[$cacheKey] = $null; return $null }
    $value = $null
    try {
        $xml = [xml](Get-Content -LiteralPath $resFile -Raw -Encoding UTF8)
        $node = $xml.SelectNodes("//string[@name='$Name']") | Select-Object -First 1
        if ($node) { $value = $node.InnerText }
    }
    catch { $value = $null }
    if ($value) {
        # Android escapes in the source what the device draws unescaped.
        $value = $value -replace "\\'", "'" -replace '\\"', '"' -replace '\\n', ' '
    }
    $script:resCache[$cacheKey] = $value
    return $value
}

function Resolve-StepLabel {
    param([string]$Label)
    if (-not $Label) { return $null }
    if ($Label.StartsWith('@string/')) {
        $name = $Label.Substring(8)
        $resolved = Get-ResourceValue -Folder $script:lang.resFolder -Name $name
        if (-not $resolved) { $resolved = Get-ResourceValue -Folder 'values' -Name $name }
        return $resolved
    }
    return $Label
}

function Get-MarkerTexts {
    # The tokens that prove the destination opened. The catalog's `expect` is authored against the
    # English value; in the other language the same expectRes draws its own value, so both are
    # acceptable and the localized one is tried first.
    param($Record)
    $markers = @()
    $localized = Get-ResourceValue -Folder $script:lang.resFolder -Name $Record.expectRes
    if ($localized) { $markers += $localized }
    if ($Record.expect -and -not $markers.Contains([string]$Record.expect)) { $markers += [string]$Record.expect }
    return ,$markers
}

# --- tree reading --------------------------------------------------------------------------------

function Read-UiDump {
    # One tree dump. Returns the parsed verb payload (nodes + the saved XML file) or $null.
    $dump = Invoke-AdbJson -Arguments @('uidump', '-Json')
    if (-not $dump -or -not $dump.nodes -or @($dump.nodes).Count -eq 0) { return $null }
    return $dump
}

function Get-Haystack {
    param($Dump)
    return @($Dump.nodes | ForEach-Object { "$($_.label) $($_.desc) $($_.resId)" }) -join "`n"
}

function Test-HaystackHasToken {
    param([string]$Haystack, [string]$Token)
    return ($null -ne $Haystack -and $Haystack -match [regex]::Escape($Token))
}

function Get-CheckedState {
    # The checked state of the switch inside a toggle row, read from the raw XML dump the tree verb
    # saves: the parsed nodes carry no checked attribute, and the sweep must READ a toggle before
    # tapping it - a blind tap flips a switch that may already be in the target position.
    param([Parameter(Mandatory)][string]$DumpFile, [Parameter(Mandatory)][string]$RowId)
    try { $xml = [xml](Get-Content -LiteralPath $DumpFile -Raw -Encoding UTF8) } catch { return $null }
    $rows = @($xml.SelectNodes("//*[@resource-id]") | Where-Object {
        $_.GetAttribute('resource-id') -match ('/' + [regex]::Escape($RowId) + '$')
    })
    if ($rows.Count -eq 0) { return $null }
    $row = $rows[0]
    $checkedNode = @($row.SelectNodes(".//*[@checked]")) | Select-Object -First 1
    if (-not $checkedNode) {
        if ($row.HasAttribute('checked')) { return ($row.GetAttribute('checked') -eq 'true') }
        return $null
    }
    return ($checkedNode.GetAttribute('checked') -eq 'true')
}

# --- scrolling -----------------------------------------------------------------------------------

$script:screenW = 1080
$script:screenH = 2400

function Read-ScreenSize {
    $sizeProbe = Invoke-Shell 'wm size'
    if ($sizeProbe.Exit -ne 0) { return }
    $text = [string]($sizeProbe.Output -join ' ')
    $m = [regex]::Match($text, 'Override size:\s*(\d+)x(\d+)')
    if (-not $m.Success) { $first = [regex]::Match($text, '(\d+)x(\d+)'); if ($first.Success) { $m = $first } }
    if ($m.Success) {
        $script:screenW = [int]$m.Groups[1].Value
        $script:screenH = [int]$m.Groups[2].Value
    }
}

function Invoke-ScrollDown {
    Invoke-AdbVerb -Arguments @('swipe', '-X', ([int]($script:screenW / 2)), '-Y', ([int]($script:screenH * 0.75)), '-X2', ([int]($script:screenW / 2)), '-Y2', ([int]($script:screenH * 0.35)), '-Duration', '400') | Out-Null
}

function Invoke-ScrollUp {
    Invoke-AdbVerb -Arguments @('swipe', '-X', ([int]($script:screenW / 2)), '-Y', ([int]($script:screenH * 0.35)), '-X2', ([int]($script:screenW / 2)), '-Y2', ([int]($script:screenH * 0.75)), '-Duration', '400') | Out-Null
}

function Reset-ListToTop {
    # One upward settle pass so every hunt starts from a known position: the hunt travels one way
    # only, so a control above the previous entry's stopping point is unreachable without it.
    for ($i = 0; $i -lt $MaxScrolls; $i++) {
        $before = Read-UiDump
        Invoke-ScrollUp
        Start-Sleep -Milliseconds $SettleMs
        $after = Read-UiDump
        if ($before -and $after -and (Get-Haystack $before) -eq (Get-Haystack $after)) { break }
    }
}

# --- reaching controls ---------------------------------------------------------------------------

function Invoke-TapOnce {
    # One reach attempt: the `via` control first (a popup's opener), then the record's own control.
    # Only tap-id and tap-label are ever used - a remembered coordinate is how a tap lands on the
    # neighbouring row of a list that scrolled.
    param($Record)
    if ($Record.via) {
        $via = Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', [string]$Record.via, '-Exact')
        if ($via.Exit -ne 0) { return $via }
        Start-Sleep -Milliseconds $SettleMs
    }
    if ($Record.resourceId) {
        return (Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', [string]$Record.resourceId, '-Exact'))
    }
    $label = Resolve-StepLabel -Label $Record.label
    if (-not $label) {
        return [pscustomobject]@{ Exit = 8; Output = "label '$($Record.label)' resolved to nothing in '$($script:lang.id)'" }
    }
    return (Invoke-AdbVerb -Arguments @('tap-label', '-Label', $label))
}

function Invoke-ReachControl {
    # Tap one control, hunting for it with scroll passes when it is not on screen yet. A settings
    # tab or a popup item can sit below the fold, and a tap verb only sees what is rendered.
    param($Record, [int]$Cap)
    $tap = Invoke-TapOnce -Record $Record
    if ($tap.Exit -eq 0) { return $tap }
    Reset-ListToTop
    $lastSeen = $null
    for ($try = 0; $try -lt $Cap -and $tap.Exit -ne 0; $try++) {
        Invoke-ScrollDown
        Start-Sleep -Milliseconds $SettleMs
        $tap = Invoke-TapOnce -Record $Record
        if ($tap.Exit -eq 0) { break }
        $current = Read-UiDump
        if ($current) {
            $h = Get-Haystack $current
            if ($h -eq $lastSeen) { break }
            $lastSeen = $h
        }
    }
    return $tap
}

function Invoke-ScrollToVisible {
    # Scroll until a control is rendered, WITHOUT tapping it: the prelude to reading a toggle's
    # state, where the tap itself is the action being decided on.
    param([Parameter(Mandatory)][string]$ResourceId, [int]$Cap)
    for ($try = 0; $try -le $Cap; $try++) {
        $dump = Read-UiDump
        if ($dump) {
            $hit = @($dump.nodes | Where-Object { $_.resIdShort -eq $ResourceId })
            if ($hit.Count -gt 0) { return $dump }
        }
        if ($try -eq $Cap) { return $null }
        Invoke-ScrollDown
        Start-Sleep -Milliseconds $SettleMs
    }
    return $null
}

# --- position tracking and recovery --------------------------------------------------------------

# The stack of opened levels. A level is REAL when its surface answers BACK presses (an activity or
# a dialog); a settings tab or an in-page section header only switches what is rendered inside the
# activity, so it is popped silently - one BACK from a settings tab exits the whole activity, not
# one tab.
$script:pos = [System.Collections.Generic.List[object]]::new()
$script:consecutiveUnreachable = 0
$script:rehomeCount = 0

function Test-LevelReal {
    param($Record)
    return ($Record.from -ne 'settings' -and $Record.from -ne 'settings-media')
}

function Push-Level {
    param($Record)
    if ($Record.id -eq 'main') { return }
    $script:pos.Add([pscustomobject]@{ rec = $Record; real = (Test-LevelReal $Record) })
}

function Pop-RealLevels {
    # Press BACK once per real level popped; silent levels ride along without a press.
    param([int]$Count)
    $back = 0
    while ($Count -gt 0 -and $script:pos.Count -gt 0) {
        $top = $script:pos[$script:pos.Count - 1]
        $script:pos.RemoveAt($script:pos.Count - 1)
        if ($top.real) {
            Invoke-AdbVerb -Arguments @('key', '-Key', 'BACK') | Out-Null
            $back++
            $Count--
        }
    }
    if ($back -gt 0) { Start-Sleep -Milliseconds $SettleMs }
}

function Pop-UntilId {
    param([string]$Id)
    while ($script:pos.Count -gt 0 -and $script:pos[$script:pos.Count - 1].rec.id -ne $Id) {
        $top = $script:pos[$script:pos.Count - 1]
        $script:pos.RemoveAt($script:pos.Count - 1)
        if ($top.real) {
            Invoke-AdbVerb -Arguments @('key', '-Key', 'BACK') | Out-Null
            Start-Sleep -Milliseconds $SettleMs
        }
    }
}

function Pop-UntilMain {
    Pop-UntilId '<never-matches>'
}

function Invoke-NavigateTo {
    # Stand where the entry's `from` says the walk should stand, before reaching for its control.
    # The catalog's declared order keeps the walk on the right parent most of the time; the tab
    # switches and the occasional re-climb it does not carry are navigation, and navigation is done
    # HERE rather than scored as an unreachable screen.
    param([string]$FromId)
    if (-not $FromId -or $FromId -eq 'main' -or $FromId -eq $screenById['main'].id) {
        if ($script:pos.Count -gt 0) { Pop-UntilMain }
        return
    }
    $fromRec = $screenById[$FromId]
    if (-not $fromRec) { return }
    if ($script:pos.Count -gt 0 -and $script:pos[$script:pos.Count - 1].rec.id -eq $FromId) { return }
    if ($fromRec.from -eq 'settings') {
        # A settings tab: reachable by tapping its label from inside SettingsActivity, never by BACK.
        Pop-UntilId 'settings'
        if ($script:pos.Count -eq 0 -or $script:pos[$script:pos.Count - 1].rec.id -ne 'settings') {
            $settingsTap = Invoke-ReachControl -Record $screenById['settings'] -Cap $MaxScrolls
            if ($settingsTap.Exit -ne 0) { return }
            Push-Level $screenById['settings']
            Start-Sleep -Milliseconds $SettleMs
        }
        $tabTap = Invoke-ReachControl -Record $fromRec -Cap $MaxScrolls
        if ($tabTap.Exit -ne 0) { return }
        Push-Level $fromRec
        Start-Sleep -Milliseconds $SettleMs
        return
    }
    $inStack = $false
    foreach ($level in $script:pos) { if ($level.rec.id -eq $FromId) { $inStack = $true; break } }
    if ($inStack) { Pop-UntilId $FromId; return }
    # Not in the stack: climb the declared chain from a known position instead of guessing taps.
    Pop-UntilMain
    $chain = [System.Collections.Generic.List[object]]::new()
    $cursor = $fromRec
    while ($cursor -and $cursor.id -ne 'main' -and $cursor.from) {
        $chain.Insert(0, $cursor)
        $cursor = $screenById[$cursor.from]
    }
    foreach ($anc in $chain) {
        $null = Invoke-ReachControl -Record $anc -Cap $MaxScrolls
        Push-Level $anc
        Start-Sleep -Milliseconds $SettleMs
    }
}

function Test-AppInFront {
    $current = Invoke-AdbVerb -Arguments @('current')
    return ($current.Exit -eq 0 -and $current.Output -match [regex]::Escape($APP_PACKAGE))
}

function Get-ForeignWindowPackage {
    # The package drawn on top when it is not the app, from the window that actually holds focus. A
    # system dialog is a window, not an activity, so the app-in-front guard cannot see it - and a
    # control read "not found" behind such a window is not a lost walk and not a broken screen.
    $dump = Invoke-Shell 'dumpsys window'
    if ($dump.Exit -ne 0) { return $null }
    $m = [regex]::Match($dump.Output, 'mCurrentFocus=Window\{[^}]*u0?\s([\w.]+)/')
    if (-not $m.Success) { return $null }
    $pkg = $m.Groups[1].Value
    if ($pkg.StartsWith($APP_PACKAGE)) { return $null }
    return $pkg
}

function Restore-WalkPosition {
    # Force-stop, launch the entry point, replay the tracked stack. The relaunch is the point: a
    # task left alive resumes wherever it was, and no phone screen can be launched directly to skip
    # the stack. Returns how many levels were actually re-entered, which is what tells "the control
    # is not where the list says" from "the walk is still lost".
    param([int]$SettleFor)
    Invoke-AdbVerb -Arguments @('stop') | Out-Null
    Start-Sleep -Milliseconds $SettleFor
    Invoke-AdbVerb -Arguments @('launch') | Out-Null
    Start-Sleep -Milliseconds $SettleFor
    $restored = 0
    foreach ($level in $script:pos) {
        $step = Invoke-ReachControl -Record $level.rec -Cap $MaxScrolls
        if ($step.Exit -ne 0) { break }
        Start-Sleep -Milliseconds $SettleFor
        $restored++
    }
    return $restored
}

function Wait-ForMainScreen {
    # The one position the walk can verify it reached: the entry point's own marker, in the current
    # combination's language. The wizard's title shares the marker string with the button label on
    # Main, so a wizard still open - a setup that did not finish by itself - is backed out of rather
    # than accepted as the start screen.
    param([int]$Attempts = 10)
    $main = $screenById['main']
    $markers = Get-MarkerTexts -Record $main
    for ($i = 0; $i -lt $Attempts; $i++) {
        Start-Sleep -Milliseconds 1000
        $dump = Read-UiDump
        if ($dump) {
            $h = Get-Haystack $dump
            if ($h -match 'cardLocalFolder') {
                Invoke-AdbVerb -Arguments @('key', '-Key', 'BACK') | Out-Null
                continue
            }
            foreach ($mk in $markers) { if (Test-HaystackHasToken $h $mk) { return $true } }
        }
    }
    return $false
}

# --- evidence ------------------------------------------------------------------------------------

function Save-Shot {
    # One screenshot, moved under the combination-and-screen name. A frame under FLAG_SECURE comes
    # back black BY DESIGN; the tree the shot verb pulls beside it is the evidence then, and the
    # caller scores `manual`, never `failed`.
    param([Parameter(Mandatory)][string]$Name)
    $shot = Invoke-AdbJson -Arguments @('shot', '-Json')
    if (-not $shot -or -not $shot.file) { return $null }
    $dest = Join-Path $outPath "$Name.png"
    Move-Item -LiteralPath $shot.file -Destination $dest -Force
    $out = [ordered]@{ file = $dest; secure = [bool]$shot.secureWindow; treeFile = $null }
    if ($shot.secureWindow -and $shot.treeFile -and (Test-Path -LiteralPath $shot.treeFile)) {
        $treeDest = Join-Path $outPath "$Name.flagsecure.xml"
        Move-Item -LiteralPath $shot.treeFile -Destination $treeDest -Force
        $out.treeFile = $treeDest
    }
    return $out
}

function Save-Tree {
    param([Parameter(Mandatory)][string]$DumpFile, [Parameter(Mandatory)][string]$Name)
    if (-not (Test-Path -LiteralPath $DumpFile)) { return $null }
    $dest = Join-Path $outPath "$Name.xml"
    Copy-Item -LiteralPath $DumpFile -Destination $dest -Force
    return $dest
}

# --- combination state (step 04.2) ---------------------------------------------------------------

$script:lang = $languageValues[0]
if ($script:lang -is [string]) { $script:lang = @{ id = $script:lang; resFolder = 'values'; appLocale = $script:lang } }

function Assert-Bench {
    param([string]$ProfileId, [string]$Serial)
    $benchArgs = @('-Assert', '-Profile', $ProfileId, '-DeviceId', $Serial)
    $null = & pwsh -NoProfile -File $benchScript @benchArgs 2>&1
    return ($LASTEXITCODE -eq 0)
}

function Set-ThemeBroadcast {
    # Returns $null on success, a reason string otherwise. The comparison is against the hook's
    # result code - the one channel that distinguishes "applied" from "no receiver" - never against
    # the exit code of the broadcast.
    param([string]$Theme)
    $call = Invoke-Shell "am broadcast -a com.sza.fastmediasorter.debug.THEME_TEST_SET --es theme $Theme -p $pkg"
    if ($call.Exit -ne 0) { return "the theme broadcast failed: $($call.Output)" }
    $m = [regex]::Match($call.Output, 'result=(-?\d+)')
    if (-not $m.Success) { return "the theme broadcast printed no result code: $($call.Output)" }
    $code = [int]$m.Groups[1].Value
    if ($code -eq $THEME_ACK_APPLIED) { return $null }
    if ($code -eq $THEME_ACK_REJECTED) { return "the hook rejected '$Theme' - not one of the nine values" }
    return "no theme hook answered (result=$code) - this build carries no ThemeTestReceiver"
}

function Set-Language {
    # Per-app locale, outside the per-screen loop by construction: this runs once per combination,
    # before any screen is reached, because the switch recreates the activity.
    param([string]$Locale)
    $set = Invoke-Shell "cmd locale set-app-locales $pkg --locales $Locale"
    if ($set.Exit -ne 0) { return "setting the app locale to $Locale failed: $($set.Output)" }
    Start-Sleep -Milliseconds $SettleMs
    $get = Invoke-Shell "cmd locale get-app-locales $pkg"
    if ($get.Exit -ne 0) { return "reading back the app locale failed: $($get.Output)" }
    if ($get.Output -notmatch [regex]::Escape($Locale)) { return "the app locale read back as '$($get.Output.Trim())', not $Locale" }
    return $null
}

function Set-Orientation {
    param([int]$UserRotation)
    $a = Invoke-Shell 'settings put system accelerometer_rotation 0'
    if ($a.Exit -ne 0) { return "switching accelerometer rotation off failed: $($a.Output)" }
    $u = Invoke-Shell "settings put system user_rotation $UserRotation"
    if ($u.Exit -ne 0) { return "writing user_rotation failed: $($u.Output)" }
    Start-Sleep -Milliseconds $SettleMs
    $check = Invoke-Shell 'settings get system user_rotation'
    if ($check.Exit -ne 0) { return "reading back user_rotation failed: $($check.Output)" }
    if ($check.Output.Trim() -ne [string]$UserRotation) { return "user_rotation read back as '$($check.Output.Trim())', not $UserRotation" }
    return $null
}

function Invoke-CombinationState {
    # Bring the device to the declared combination and CONFIRM each step. Returns $null when every
    # step is confirmed, otherwise the reason the walk refuses the combination - a frame labelled
    # with settings it does not have is the one artifact this sweep cannot recover from.
    param($Combo, [string]$Serial)
    if (-not (Assert-Bench -ProfileId $Combo.profile -Serial $Serial)) {
        return "the attached device does not match the '$($Combo.profile)' bench (ui-sweep-bench -Assert failed)"
    }
    $themeReason = Set-ThemeBroadcast -Theme $Combo.theme
    if ($themeReason) { return $themeReason }
    $langReason = Set-Language -Locale $Combo.language.appLocale
    if ($langReason) { return $langReason }
    $rotReason = Set-Orientation -UserRotation ([int]$Combo.orientation.userRotation)
    if ($rotReason) { return $rotReason }
    Read-ScreenSize
    return $null
}

# --- setup / teardown (step 04.3) ----------------------------------------------------------------

$script:secureAvailable = $true

function Invoke-SetToggleRow {
    # Read the switch, tap only when it differs, read again. Returns $null on success, a reason
    # otherwise. A blind tap would flip a switch already in place - and teardown, whose job is to
    # put the protection back, would then switch it OFF.
    param([string]$RowId, [bool]$Want)
    $dump = Invoke-ScrollToVisible -ResourceId $RowId -Cap $MaxScrolls
    if (-not $dump) { return "the '$RowId' row never became visible" }
    $checked = Get-CheckedState -DumpFile $dump.file -RowId $RowId
    if ($null -eq $checked) { return "no readable checked state on '$RowId'" }
    if ($checked -ne $Want) {
        $tap = Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', $RowId, '-Exact')
        if ($tap.Exit -ne 0) { return "tapping '$RowId' failed: $($tap.Output)" }
        Start-Sleep -Milliseconds $SettleMs
        $after = Read-UiDump
        if (-not $after) { return "the tree could not be re-read after tapping '$RowId'" }
        $nowChecked = Get-CheckedState -DumpFile $after.file -RowId $RowId
        if ($null -eq $nowChecked -or $nowChecked -ne $Want) {
            return "'$RowId' read back $(if ($null -eq $nowChecked) { 'unreadable' } else { $nowChecked }) after the tap, wanted $Want"
        }
    }
    return $null
}

function Invoke-CatalogEntrySteps {
    # The interpreter for the catalog's setup and teardown arrays. The entries are data - reach
    # fields, toggle actions, the folder pick - and this walks them in the declared order.
    param($Entry)
    foreach ($step in @($Entry.steps)) {
        if ($step.action -eq 'pick-local-folder') {
            $reason = Invoke-SafFolderPick -FolderName $seedFolderName
            if ($reason) { return $reason }
            continue
        }
        if ($step.action -eq 'confirm') {
            $tap = Invoke-ReachControl -Record @{ resourceId = 'btnAddToResources' } -Cap $MaxScrolls
            if ($tap.Exit -ne 0) { return "the add-resource confirm button never appeared: $($tap.Output)" }
            Start-Sleep -Milliseconds (2 * $SettleMs)
            continue
        }
        if ($step.action -eq 'toggle-off') {
            $r = Invoke-SetToggleRow -RowId $step.resourceId -Want $false
            if ($r) { return $r }
            continue
        }
        if ($step.action -eq 'toggle-on') {
            $r = Invoke-SetToggleRow -RowId $step.resourceId -Want $true
            if ($r) { return $r }
            continue
        }
        if ($step.action -eq 'expand') {
            $tap = Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', [string]$step.resourceId, '-Exact')
            if ($tap.Exit -ne 0) { return "expanding '$($step.resourceId)' failed: $($tap.Output)" }
            Start-Sleep -Milliseconds $SettleMs
            continue
        }
        $tap = Invoke-TapOnce -Record $step
        if ($tap.Exit -ne 0) { return "setup step ($($step.resourceId)$($step.label)) failed: $($tap.Output)" }
        Start-Sleep -Milliseconds $SettleMs
    }
    $backAfter = if ($null -ne $Entry.backAfter) { [int]$Entry.backAfter } else { 1 }
    for ($b = 0; $b -lt $backAfter; $b++) {
        Invoke-AdbVerb -Arguments @('key', '-Key', 'BACK') | Out-Null
        Start-Sleep -Milliseconds $SettleMs
    }
    return $null
}

function Invoke-SafFolderPick {
    # The system folder picker no build setting can route around on a flavor without all-files
    # access. The wizard's own two controls are reached by id; DocumentsUI is driven by its stable
    # labels - the picker speaks the SYSTEM language, so those are literals and are never resolved
    # through the app's resources.
    param([Parameter(Mandatory)][string]$FolderName)
    $steps = @(
        @{ name = 'add-manually'; id = 'btnAddManually' },
        @{ name = 'browse-with-saf'; id = 'btnBrowseWithSAF' },
        @{ name = 'show-roots'; labels = @('Show roots') },
        @{ name = 'downloads'; labels = @('Downloads') },
        @{ name = 'seed-folder'; labels = @($FolderName) },
        @{ name = 'use-this-folder'; labels = @('USE THIS FOLDER', 'SELECT FOLDER') }
    )
    foreach ($step in $steps) {
        $tap = $null
        if ($step.id) {
            $tap = Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', $step.id, '-Exact')
        }
        else {
            foreach ($label in $step.labels) {
                $tap = Invoke-ReachControl -Record @{ label = $label } -Cap $MaxScrolls
                if ($tap.Exit -eq 0) { break }
            }
        }
        if (-not $tap -or $tap.Exit -ne 0) { return "the folder picker stalled at '$($step.name)': $($tap.Output)" }
        Start-Sleep -Milliseconds (2 * $SettleMs)
    }
    return $null
}

function Invoke-Setup {
    # Once per bench, before its first combination: secure surfaces photographable, Streams present,
    # one resource configured. A setup step that fails names its refusal; only the secure toggle's
    # failure changes how the catalog is walked (its four surfaces get refused-secure), the rest
    # surface later as honest screen outcomes.
    param([string]$Serial)
    $script:dev = $Serial
    Invoke-AdbVerb -Arguments @('stop') | Out-Null
    Start-Sleep -Milliseconds $SettleMs
    Invoke-AdbVerb -Arguments @('launch') | Out-Null
    if (-not (Wait-ForMainScreen)) { return "the app never reached its start screen after launch" }

    foreach ($entry in @($screensDoc.setup)) {
        $reason = Invoke-CatalogEntrySteps -Entry $entry
        if ($reason) {
            if ($entry.id -eq 'setup-disable-secure-screens') {
                $script:secureAvailable = $false
                Write-Host "setup: secure toggle unreachable - the four secure surfaces will be refused ($reason)" -ForegroundColor Yellow
                # Back to a known position for the next setup entry: force-stop and relaunch rather
                # than guessing how many BACK presses the failed navigation left outstanding.
                Invoke-AdbVerb -Arguments @('stop') | Out-Null
                Start-Sleep -Milliseconds $SettleMs
                Invoke-AdbVerb -Arguments @('launch') | Out-Null
                $null = Wait-ForMainScreen
                continue
            }
            return $reason
        }
        if ($entry.id -eq 'setup-disable-secure-screens') { $script:secureAvailable = $true }
    }
    if (-not (Wait-ForMainScreen)) { return "the walk is not on the start screen after setup" }
    return $null
}

function Invoke-Teardown {
    # Runs on every exit path. The read-then-toggle form is what makes this a restore: a switch
    # already back in place is left alone, so teardown is safe to re-run and repairs what an
    # aborted run left behind the next time anything runs.
    param([string]$Serial)
    $script:dev = $Serial
    try {
        if (-not (Test-AppInFront)) {
            Invoke-AdbVerb -Arguments @('launch') | Out-Null
            $null = Wait-ForMainScreen
        }
        Pop-UntilMain
        foreach ($entry in @($screensDoc.teardown)) {
            $reason = Invoke-CatalogEntrySteps -Entry $entry
            if ($reason) { Write-Host "teardown: $($entry.id) failed: $reason" -ForegroundColor Yellow }
        }
    }
    catch {
        Write-Host "teardown: interrupted: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# --- the walk ------------------------------------------------------------------------------------

$script:rows = [System.Collections.Generic.List[object]]::new()
$script:localeOriginal = @{}

function Add-Row {
    param($Row)
    $script:rows.Add($Row)
}

function Invoke-WalkScreen {
    # One catalog record in one combination: navigate, reach, assert, capture, expand, back out.
    param($Screen, [hashtable]$Combo, [string]$Key)

    $row = [ordered]@{
        combination = $Combo.key
        screen      = $Screen.id
        name        = $Screen.name
        outcome     = 'manual'
        detail      = $null
        shot        = $null
        tree        = $null
        expanded    = @()
        rehomed     = $false
    }

    $entrySettleMs = if ($null -ne $Screen.settleMs) { [int]$Screen.settleMs } else { $SettleMs }
    Start-Sleep -Milliseconds $entrySettleMs

    if (-not $script:secureAvailable -and $Screen.secure) {
        $row.outcome = 'refused-secure'
        $row.detail = 'the secure-sensitive-screens toggle could not be switched off, so this FLAG_SECURE surface was not photographed'
        Add-Row $row
        return
    }

    # Two reasons to re-home, one recovery for both: the app left the foreground, or consecutive
    # unreachable outcomes say the walk is standing somewhere no declared control can be found.
    $cascade = ($RehomeAfterUnreachable -gt 0 -and $script:consecutiveUnreachable -ge $RehomeAfterUnreachable)
    if (-not (Test-AppInFront) -or $cascade) {
        $wanted = $script:pos.Count
        $restored = Restore-WalkPosition -SettleFor $entrySettleMs
        $script:rehomeCount++
        $script:consecutiveUnreachable = 0
        $row.rehomed = $true
        $row['rehomeRestored'] = "$restored/$wanted"
        if (-not (Test-AppInFront)) {
            $row.outcome = 'manual'
            $row.detail = "the app is not in front and did not come back on relaunch (restored $restored/$wanted)"
            Add-Row $row
            return
        }
    }

    Invoke-NavigateTo -FromId $Screen.from

    $tap = $null
    if ($Screen.resourceId -or $Screen.label) {
        $scrolls = if ($null -ne $Screen.maxScrolls) { [int]$Screen.maxScrolls } else { $MaxScrolls }
        $tap = Invoke-ReachControl -Record $Screen -Cap $scrolls
    }

    if ($tap -and $tap.Exit -ne 0) {
        if ($Screen.optional -or $Screen.stateDependent) {
            $row.outcome = 'skipped'
            $row.detail = 'its control is not on screen in this run, and the entry declares that legitimate'
            Add-Row $row
            return
        }
        $foreign = Get-ForeignWindowPackage
        if ($foreign) {
            $row.outcome = 'manual'
            $row.detail = "a foreign window is on top ($foreign); the control was not found because the app is behind it"
            Add-Row $row
            return
        }
        $row.outcome = 'unreachable'
        $row.detail = "the control was never found, so the screen was never opened or judged: $($tap.Output)"
        # A popup-reached entry may leave its popup open; close it so the next entry is not judged
        # against a menu. The app-in-front guard above self-corrects the case where there was no
        # popup to close and this BACK left the app instead.
        if ($Screen.via) {
            Invoke-AdbVerb -Arguments @('key', '-Key', 'BACK') | Out-Null
            Start-Sleep -Milliseconds $SettleMs
        }
        Add-Row $row
        $script:consecutiveUnreachable = $script:consecutiveUnreachable + 1
        return
    }

    Push-Level $Screen
    $script:consecutiveUnreachable = 0

    # Settle, then read, then read once more: the screen is still animating when the tap returns,
    # and a tree read mid-transition describes the screen being left.
    $settleMs = if ($null -ne $Screen.settleMs) { [int]$Screen.settleMs } else { $SettleMs }
    $dump = $null
    $haystack = $null
    $markers = Get-MarkerTexts -Record $Screen
    $present = $false
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        Start-Sleep -Milliseconds $settleMs
        $dump = Read-UiDump
        if (-not $dump) { continue }
        $haystack = Get-Haystack $dump
        foreach ($mk in $markers) {
            if (Test-HaystackHasToken $haystack $mk) { $present = $true; break }
        }
        if ($present) { break }
    }

    # Hunt for the marker the way the tap hunts for its control: a marker chosen to belong to the
    # destination can sit below the fold, and judging by the visible slice is how a present screen
    # reads as failed.
    if (-not $present -and $dump) {
        $found = $false
        for ($pass = 0; $pass -lt 2 -and -not $found; $pass++) {
            for ($i = 0; $i -lt $MaxScrolls -and -not $found; $i++) {
                if ($pass -eq 0) { Invoke-ScrollDown } else { Invoke-ScrollUp }
                Start-Sleep -Milliseconds $settleMs
                $dump = Read-UiDump
                if (-not $dump) { continue }
                $haystack = Get-Haystack $dump
                foreach ($mk in $markers) { if (Test-HaystackHasToken $haystack $mk) { $present = $true; break } }
                if ($present) { $found = $true }
            }
        }
    }

    if (-not $dump) {
        $row.detail = 'the UI tree could not be read on any attempt'
        Add-Row $row
        # The screen OPENED (the tap answered), so the declared climb-out is still owed even though
        # nothing could be judged on it - skipping it would leave the next entry one level off.
        $backAfter = if ($null -ne $Screen.backAfter) { [int]$Screen.backAfter } else { 1 }
        if ($backAfter -gt 0) { Pop-RealLevels -Count $backAfter }
        return
    }

    if ($present) {
        $row.outcome = 'observed'
    }
    elseif ($Screen.stateDependent) {
        $row.detail = "expected '$($markers[0])' absent, and the entry is state-dependent - a human must judge it"
    }
    else {
        $row.outcome = 'failed'
        $row.detail = "the screen opened but expected '$($markers[0])' is not on it"
    }

    $row.tree = Save-Tree -DumpFile $dump.file -Name $Key
    $shot = Save-Shot -Name $Key
    if ($shot) {
        $row.shot = $shot.file
        if ($shot.secure) {
            $row.outcome = 'manual'
            $row.detail = 'the frame came back under FLAG_SECURE; the tree pulled beside it is the evidence'
            if ($shot.treeFile) { $row.tree = $shot.treeFile }
        }
    }
    elseif (-not $row.detail) {
        $row.detail = 'the screenshot could not be captured'
    }

    # The declared expand nodes, each captured on its own and collapsed again.
    $expandNo = 0
    $expandResults = [System.Collections.Generic.List[object]]::new()
    foreach ($exp in @($Screen.expand)) {
        if (-not $exp) { continue }
        $expandNo++
        $expRow = [ordered]@{ id = $exp.resourceId; outcome = 'manual'; detail = $null; shot = $null; tree = $null }
        $expTap = Invoke-ReachControl -Record $exp -Cap $MaxScrolls
        if ($expTap.Exit -ne 0) {
            if ($exp.stateDependent -or $exp.optional) { $expRow.outcome = 'skipped'; $expRow.detail = 'not present in this build/state' }
            else { $expRow.outcome = 'unreachable'; $expRow.detail = $expTap.Output }
            $expandResults.Add([pscustomobject]$expRow)
            continue
        }
        Start-Sleep -Milliseconds $settleMs
        $expDump = Read-UiDump
        $expPresent = $false
        $expMarkers = Get-MarkerTexts -Record $exp
        if ($expDump) {
            $eh = Get-Haystack $expDump
            foreach ($mk in $expMarkers) { if (Test-HaystackHasToken $eh $mk) { $expPresent = $true; break } }
        }
        $expName = "${Key}__x$('{0:d2}' -f $expandNo)"
        if ($expDump) { $expRow.tree = Save-Tree -DumpFile $expDump.file -Name $expName }
        if (-not $expDump) { $expRow.outcome = 'manual'; $expRow.detail = 'the tree could not be read after expanding' }
        elseif ($expPresent) { $expRow.outcome = 'observed' }
        elseif ($exp.stateDependent -or $exp.optional) { $expRow.outcome = 'skipped'; $expRow.detail = "expanded state not reached - expected '$($expMarkers[0])' absent" }
        else { $expRow.outcome = 'failed'; $expRow.detail = "expanded but expected '$($expMarkers[0])' is not on it" }
        $expShot = Save-Shot -Name $expName
        if ($expShot) {
            $expRow.shot = $expShot.file
            # A secure frame is black by design; it overrides an otherwise clean verdict because the
            # IMAGE is not evidence - the tree pulled beside it is.
            if ($expShot.secure) {
                $expRow.outcome = 'manual'
                $expRow.detail = 'captured under FLAG_SECURE; the tree beside the black frame is the evidence'
                if ($expShot.treeFile) { $expRow.tree = $expShot.treeFile }
            }
        }
        $expandResults.Add([pscustomobject]$expRow)
        # Collapse, so the next expand node is judged against the screen it was declared on.
        $null = Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', [string]$exp.resourceId, '-Exact')
        Start-Sleep -Milliseconds $settleMs
    }
    $row.expanded = @($expandResults)

    Add-Row $row

    # Climb out by the declared count, popping one real level per BACK.
    $backAfter = if ($null -ne $Screen.backAfter) { [int]$Screen.backAfter } else { 1 }
    if ($backAfter -gt 0) { Pop-RealLevels -Count $backAfter }
}

# --- run -----------------------------------------------------------------------------------------

$runStarted = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')

foreach ($p in $profileValues) {
    $profileId = if ($p -is [string]) { $p } else { [string]$p.id }
    $serial = $deviceForProfile[$profileId]
    $script:dev = $serial

    try {
        $null = Invoke-AdbVerb -Arguments @('state-begin')
        $loc = Invoke-Shell "cmd locale get-app-locales $pkg"
        $script:localeOriginal[$serial] = if ($loc.Exit -eq 0) { $loc.Output.Trim() } else { '' }

        if (-not $Only) {
            $setupReason = Invoke-Setup -Serial $serial
            if ($setupReason) { Stop-Run 2 "setup failed on ${profileId}: $setupReason" }
        }

        foreach ($combo in $combinations | Where-Object { $_.profile -eq $profileId }) {
            $script:dev = $serial
            $script:lang = $combo.language
            if ($script:lang -is [string]) { $script:lang = @{ id = $script:lang; resFolder = 'values'; appLocale = $script:lang } }
            $script:pos = [System.Collections.Generic.List[object]]::new()
            $script:consecutiveUnreachable = 0
            $key = $combo.key

            $stateReason = Invoke-CombinationState -Combo $combo -Serial $serial
            if ($stateReason) {
                # The refusal is its own outcome for the whole combination: every screen gets a row,
                # and not one frame is captured under a label that was never confirmed.
                foreach ($s in $screens) {
                    Add-Row ([ordered]@{
                        combination = $key; screen = $s.id; name = $s.name
                        outcome = 'refused-state'; detail = $stateReason
                        shot = $null; tree = $null; expanded = @(); rehomed = $false
                    })
                }
                if (-not $Json) { Write-Host "walk: combination $key -> refused-state ($stateReason)" -ForegroundColor Yellow }
                continue
            }

            Invoke-AdbVerb -Arguments @('stop') | Out-Null
            Start-Sleep -Milliseconds $SettleMs
            Invoke-AdbVerb -Arguments @('launch') | Out-Null
            if (-not (Wait-ForMainScreen)) {
                foreach ($s in $screens) {
                    Add-Row ([ordered]@{
                        combination = $key; screen = $s.id; name = $s.name
                        outcome = 'refused-state'; detail = 'the app never reached its start screen after the combination launch'
                        shot = $null; tree = $null; expanded = @(); rehomed = $false
                    })
                }
                if (-not $Json) { Write-Host "walk: combination $key -> refused-state (no start screen)" -ForegroundColor Yellow }
                continue
            }

            foreach ($s in $screens) {
                Invoke-WalkScreen -Screen $s -Combo $combo -Key $key
            }

            # Whatever the declared `backAfter` chain left open, the next combination starts from a
            # force-stop anyway - but the journal is written per run, so leave the device tidy.
            Pop-UntilMain
            if (-not $Json) {
                $soFar = @($script:rows | Where-Object { $_.combination -eq $key })
                $observed = @($soFar | Where-Object { $_.outcome -eq 'observed' }).Count
                Write-Host "walk: combination $key done - $observed/$($screens.Count) observed" -ForegroundColor Cyan
            }
        }
    }
    finally {
        $script:dev = $serial
        # Put back what the run changed, including on an aborted run: the secure protection, the
        # theme (research 01's residual - a sticky accent would greet the next human tester), the
        # per-app locale, the rotation, and the device state journal.
        Invoke-Teardown -Serial $serial
        $null = Set-ThemeBroadcast -Theme 'AUTO'
        if ($script:localeOriginal.ContainsKey($serial)) {
            $original = $script:localeOriginal[$serial]
            if ($original) {
                $null = Invoke-Shell "cmd locale set-app-locales $pkg --locales $original"
            }
        }
        $null = Invoke-Shell 'settings put system accelerometer_rotation 1'
        $null = Invoke-AdbVerb -Arguments @('state-check')
    }
}

# --- journal and verdict (step 04.6) -------------------------------------------------------------

$expectedRows = $screens.Count * $combinations.Count
$rowCount = $script:rows.Count

$counts = [ordered]@{
    observed       = @($script:rows | Where-Object { $_.outcome -eq 'observed' }).Count
    failed         = @($script:rows | Where-Object { $_.outcome -eq 'failed' }).Count
    unreachable    = @($script:rows | Where-Object { $_.outcome -eq 'unreachable' }).Count
    manual         = @($script:rows | Where-Object { $_.outcome -eq 'manual' }).Count
    skipped        = @($script:rows | Where-Object { $_.outcome -eq 'skipped' }).Count
    refusedState   = @($script:rows | Where-Object { $_.outcome -eq 'refused-state' }).Count
    refusedSecure  = @($script:rows | Where-Object { $_.outcome -eq 'refused-secure' }).Count
    rehomes        = $script:rehomeCount
}
$result.counts = $counts

$journal = [ordered]@{
    ticket       = 'S2380'
    started      = $runStarted
    finished     = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    matrix       = (Split-Path -Leaf $Matrix)
    subset       = $Subset
    only         = $Only
    devices      = $result.devices
    combinations = $result.combinations
    screens      = $screens.Count
    expectedRows = $expectedRows
    rowCount     = $rowCount
    counts       = $counts
    rows         = $script:rows
}
$journalPath = Join-Path $outPath 'sweep-journal.json'
$journal | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $journalPath -Encoding UTF8

if ($rowCount -ne $expectedRows) {
    Stop-Run 2 "the row-count identity failed: $rowCount rows for $($screens.Count) screens x $($combinations.Count) combinations = $expectedRows; journal: $journalPath"
}

$verdict = if ($counts.failed -gt 0) { 1 }
           elseif (($counts.unreachable + $counts.manual + $counts.refusedState + $counts.refusedSecure) -gt 0) { 2 }
           else { 0 }

$result.exitCode = $verdict
$result.ok = ($verdict -eq 0)
$result['journal'] = $journalPath

if ($Json) { [pscustomobject]$result | ConvertTo-Json -Depth 8 -Compress }
else {
    Write-Host ("ui-sweep-walk: observed $($counts.observed), failed $($counts.failed), unreachable $($counts.unreachable), manual $($counts.manual), skipped $($counts.skipped), refusedState $($counts.refusedState), refusedSecure $($counts.refusedSecure), rehomes $script:rehomeCount; rows $rowCount/$expectedRows; journal $journalPath") -ForegroundColor Cyan
}
exit $verdict
