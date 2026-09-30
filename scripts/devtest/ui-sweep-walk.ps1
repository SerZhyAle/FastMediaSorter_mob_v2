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

# An unhandled terminating error must not leave the caller reading silence as success: this whole
# script is consumed by its exit code, and the one crash it has had - an assignment to $PID, which
# PowerShell holds read-only - produced a stack trace and no verdict at all. 2, not 1: a crash is
# "the walk never got to judge", never "the app is broken here".
trap {
    Write-Error "ui-sweep-walk: the run stopped on an unhandled error: $($_.Exception.Message)" -ErrorAction Continue
    exit 2
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
    $walkScreens = @($screensAll | Where-Object { $_.id -eq $Only })
    if ($walkScreens.Count -eq 0) { Stop-Run 2 "-Only '$Only' names no screen in the catalog" }
}
else { $walkScreens = $screensAll }
# Named $walkScreens and NOT $screens on purpose: PowerShell names are case-insensitive, so a local
# $screens IS the -Screens parameter, which is [string]. Assigning the 43-record array into it
# collapsed the whole catalog to one string, and the run swept a single nameless screen and still
# passed its own row-count identity, because both sides of that check read the same collapsed value
# (measured emulator-5554, 2026-09-20: rows 2/2, screens 1, every row's screen id null).

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
                $profileId = if ($p -is [string]) { $p } else { [string]$p.id }
                $lid = if ($l -is [string]) { $l } else { [string]$l.id }
                $tid = if ($t -is [string]) { $t } else { [string]$t.id }
                $oid = if ($o -is [string]) { $o } else { [string]$o.id }
                $combinations += ,@{
                    key         = "${profileId}_${lid}_${tid}_${oid}"
                    profile     = $profileId
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
    $profileId = if ($p -is [string]) { $p } else { [string]$p.id }
    if ($DeviceId) { $deviceForProfile[$profileId] = $DeviceId }
}
if ($DeviceMap) {
    foreach ($pair in ($DeviceMap -split '[;,]')) {
        if ($pair -notmatch '^\s*([^=]+)=(.+)\s*$') { Stop-Run 2 "-DeviceMap entry '$pair' is not profile=serial" }
        $deviceForProfile[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}
foreach ($p in $profileValues) {
    $profileId = if ($p -is [string]) { $p } else { [string]$p.id }
    if (-not $deviceForProfile.ContainsKey($profileId)) {
        Stop-Run 2 "no device for profile '$profileId' - pass -DeviceId (single bench) or -DeviceMap ${profileId}=<serial>"
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
    # EVERY strings file in the folder, not strings.xml alone: app_v2 splits its resources across
    # about thirty strings_*.xml files, and the settings tab labels - which the setup and teardown
    # entries reach by - live in strings_settings.xml. Reading one file resolved those labels to
    # nothing, and the walk reported it as "the app never reached its start screen": a marker that
    # cannot be resolved is indistinguishable from a marker that is not on screen (measured
    # emulator-5554, 2026-09-20, where it refused the whole matrix before the first frame).
    $resDir = Join-Path $repoRoot "app_v2/src/main/res/$Folder"
    if (-not (Test-Path -LiteralPath $resDir)) { $script:resCache[$cacheKey] = $null; return $null }
    $value = $null
    foreach ($resFile in (Get-ChildItem -LiteralPath $resDir -Filter 'strings*.xml' -File -ErrorAction SilentlyContinue | Sort-Object Name)) {
        try {
            $xml = [xml](Get-Content -LiteralPath $resFile.FullName -Raw -Encoding UTF8)
            $node = $xml.SelectNodes("//string[@name='$Name']") | Select-Object -First 1
            if ($node) { $value = $node.InnerText; break }
        }
        catch { continue }
    }
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
    # An `expectId` wins over both text forms where the catalog declares one: a resource-id is not
    # translated and does not collapse with the screen width, which a label does on both counts. Main
    # is why the field exists - its declared marker was the add-resource button's LABEL, which is
    # drawn only while the button has room for it; at 360 dp the button is icon-only, so the walk
    # refused the whole matrix reporting that the app never reached its start screen, on a device
    # that was sitting on the start screen (measured emulator-5554, 2026-09-20). Matching it is
    # Get-Haystack's job, and it reads the raw tree for ids precisely because a container id never
    # reaches the parsed node list.
    if ($Record.expectId) { $markers += [string]$Record.expectId }
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
    # Two sources, because they are not the same set. The parsed node list carries only nodes that
    # draw text or a description, so a CONTAINER - a tab strip, a section header - is in the raw tree
    # and in none of those nodes; the raw XML is therefore read for its resource-ids as well. An
    # `expectId` matched against the parsed list alone made a screen that was open read as never
    # reached: measured emulator-5554 2026-09-20, `tabResourceTypes` is in the start screen's tree
    # and in none of its 45 parsed nodes, so the walk refused the whole matrix from the start screen.
    param($Dump)
    $parts = @($Dump.nodes | ForEach-Object { "$($_.label) $($_.desc) $($_.resId)" })
    if ($Dump.file -and (Test-Path -LiteralPath $Dump.file)) {
        try {
            $raw = Get-Content -LiteralPath $Dump.file -Raw -Encoding UTF8
            $parts += @([regex]::Matches($raw, 'resource-id="([^"]+)"') |
                ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
        } catch {
            # The parsed nodes stay the haystack - which is exactly what the walk matched against
            # before this second source existed, so an unreadable dump file loses no ground.
        }
    }
    return ($parts -join "`n")
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
    # checkable="true", not merely [@checked]: uiautomator writes checked="false" on EVERY node, so the
    # first descendant - the row's title text - answered for the switch and a row that was ON read OFF.
    $checkedNode = @($row.SelectNodes(".//*[@checkable='true']")) | Select-Object -First 1
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
    # The CURRENT size, rotation applied. `wm size` reports the natural size, so in landscape a
    # 720x1280 bench kept swiping from y=960 on a 720-high screen - below the edge, scrolling nothing -
    # and every hunt on a long settings page gave up (emulator-5562, 2026-09-25, headerAudio).
    $displays = Invoke-Shell 'dumpsys window displays'
    if ($displays.Exit -eq 0) {
        $cur = [regex]::Match([string]($displays.Output -join ' '), '\bcur=(\d+)x(\d+)')
        if ($cur.Success) {
            $script:screenW = [int]$cur.Groups[1].Value
            $script:screenH = [int]$cur.Groups[2].Value
            return
        }
    }
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
    $before = Read-UiDump
    for ($i = 0; $i -lt $MaxScrolls; $i++) {
        Invoke-ScrollUp
        Start-Sleep -Milliseconds $SettleMs
        $after = Read-UiDump
        if ($before -and $after -and (Get-Haystack $before) -eq (Get-Haystack $after)) { break }
        $before = $after
    }
}

function Find-OnScreenOrHunt {
    # The current screen first, the top-down hunt only when the control is not on it. An unconditional
    # reset before every section read made one expand node cost six full-page hunts, and a 14-section
    # settings screen took 40 minutes (measured emulator-5562, 2026-09-25).
    param([Parameter(Mandatory)][string]$ResourceId)
    $dump = Invoke-ScrollToVisible -ResourceId $ResourceId -Cap 0
    if ($dump) { return $dump }
    Reset-ListToTop
    return (Invoke-ScrollToVisible -ResourceId $ResourceId -Cap $MaxScrolls)
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
    elseif ($Record.viaLabel) {
        # A per-row opener: the resource card's overflow button carries no distinguishing id, only a
        # description naming its row, and the first overflow on Main belongs to a virtual resource
        # whose menu has no Edit item.
        $via = Invoke-AdbVerb -Arguments @('tap-label', '-Label', (Resolve-StepLabel -Label $Record.viaLabel), '-Exact')
        if ($via.Exit -ne 0) { return $via }
        Start-Sleep -Milliseconds $SettleMs
    }
    if ($Record.itemId) {
        # `tap-first-item`: the first ROW of a list, by the id its rows share. Tapping the list's own
        # id lands on its centre, which is whatever row or gap happens to sit there - on 2026-09-25 it
        # ticked a select checkbox and the player was scored failed on a browse screen.
        return (Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', [string]$Record.itemId, '-Exact', '-Index', '1'))
    }
    if ($Record.resourceId) {
        return (Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', [string]$Record.resourceId, '-Exact'))
    }
    $label = Resolve-StepLabel -Label $Record.label
    if (-not $label) {
        return [pscustomobject]@{ Exit = 8; Output = "label '$($Record.label)' resolved to nothing in '$($script:lang.id)'" }
    }
    # `exactLabel` is for a screen where one string is a substring of another: the system grant
    # dialog's own title contains the word the ALLOW button carries, and a substring match tapped the
    # TITLE, leaving the dialog open and the walk stalled three steps later on a control the dialog
    # was covering (measured emulator-5554, 2026-09-20).
    $tapArgs = @('tap-label', '-Label', $label)
    if ($Record.exactLabel) { $tapArgs += '-Exact' }
    return (Invoke-AdbVerb -Arguments $tapArgs)
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
            # The parsed list carries only nodes that draw text or a description, and a toggle ROW is
            # a container - its title and its switch are the labelled nodes, not the row. Read the raw
            # tree as well, or a row that is on screen reads as never reached; the caller then scrolls
            # a settings page to its end and reports a present control missing (measured
            # emulator-5554, 2026-09-20, rowSecureSensitiveScreens).
            if ($hit.Count -eq 0 -and $dump.file -and (Test-Path -LiteralPath $dump.file)) {
                $rawTree = Get-Content -LiteralPath $dump.file -Raw -Encoding UTF8
                if ($rawTree -match ('resource-id="[^"]*/' + [regex]::Escape($ResourceId) + '"')) { return $dump }
            }
            if ($hit.Count -gt 0) { return $dump }
        }
        if ($try -eq $Cap) { return $null }
        Invoke-ScrollDown
        Start-Sleep -Milliseconds $SettleMs
    }
    return $null
}

function Test-SettingsTabSelected {
    # True when the node drawing $Label, or one of its three nearest ancestors (the TabView that owns
    # the custom tab text), carries selected="true" in the raw tree.
    param([string]$DumpFile, [string]$Label)
    try { $xml = [xml](Get-Content -LiteralPath $DumpFile -Raw -Encoding UTF8) } catch { return $false }
    foreach ($node in @($xml.SelectNodes('//node') | Where-Object { $_.GetAttribute('text') -eq $Label })) {
        $cursor = $node
        for ($depth = 0; $depth -le 3 -and $cursor -is [System.Xml.XmlElement]; $depth++) {
            if ($cursor.GetAttribute('selected') -eq 'true') { return $true }
            $cursor = $cursor.ParentNode
        }
    }
    return $false
}

function Invoke-SelectSettingsTab {
    # Settings reopens on the tab it last remembered, so a tab tap is judged by the tab strip's own
    # selected state, never assumed. The label is matched exactly: a substring match can land on a
    # row of the current page that merely contains the tab's word. Returns $null or a reason.
    param($Record)
    $label = Resolve-StepLabel -Label $Record.label
    if (-not $label) { return "tab label '$($Record.label)' resolved to nothing in '$($script:lang.id)'" }
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        $tap = Invoke-AdbVerb -Arguments @('tap-label', '-Label', $label, '-Exact')
        if ($tap.Exit -ne 0) { return "the '$label' settings tab could not be tapped: $($tap.Output)" }
        Start-Sleep -Milliseconds $SettleMs
        $dump = Read-UiDump
        if ($dump -and $dump.file -and (Test-SettingsTabSelected -DumpFile $dump.file -Label $label)) {
            Reset-ListToTop
            return $null
        }
    }
    return "the '$label' settings tab was tapped twice and never read back as selected"
}

function Invoke-ReachRecord {
    # A catalog record whose parent is `settings` is a tab, reached and CONFIRMED through the tab
    # strip; every other record is a control hunted on the current screen. Same result shape either way.
    param($Record, [int]$Cap)
    if ($Record.section) {
        # The control lives inside a collapsible section, and a collapsed section's rows are GONE:
        # no amount of scrolling reveals them (2026-09-25, eight Management-tab dialogs unreachable).
        $sectionReason = Set-SectionState -HeaderId ([string]$Record.section) -ContainerId ([string]$Record.sectionContainer) -Open $true
        if ($sectionReason) { return [pscustomobject]@{ Exit = 8; Output = $sectionReason } }
    }
    if (Test-SectionRecord $Record) {
        $openReason = Set-SectionState -HeaderId ([string]$Record.resourceId) -ContainerId ([string]$Record.containerId) -Open $true
        if ($openReason) { return [pscustomobject]@{ Exit = 8; Output = $openReason } }
        # Back to the header, so the marker hunt below starts where the section's content begins.
        $null = Find-OnScreenOrHunt -ResourceId ([string]$Record.resourceId)
        return [pscustomobject]@{ Exit = 0; Output = 'section opened' }
    }
    if ($Record.from -ne 'settings') { return (Invoke-ReachControl -Record $Record -Cap $Cap) }
    $reason = Invoke-SelectSettingsTab -Record $Record
    if ($reason) { return [pscustomobject]@{ Exit = 8; Output = $reason } }
    return [pscustomobject]@{ Exit = 0; Output = 'tab selected' }
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
        $tabTap = Invoke-ReachRecord -Record $fromRec -Cap $MaxScrolls
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
        $null = Invoke-ReachRecord -Record $anc -Cap $MaxScrolls
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
        $step = Invoke-ReachRecord -Record $level.rec -Cap $MaxScrolls
        if ($step.Exit -ne 0) { break }
        Start-Sleep -Milliseconds $SettleFor
        $restored++
    }
    return $restored
}

$script:mainRefusalSeq = 0
$script:mainRefusalNote = ''

function Save-MainScreenRefusal {
    # What the walk SAW at the moment it gave up: the tree, the window that actually held focus, and
    # the markers it was looking for, written beside the corpus. None of that survives the run
    # otherwise, and three wrong causes were already talked into S2380 by reasoning from the refusal
    # TEXT instead of from the screen. Returns a short note naming the artifact for the reason string.
    param($Dump, [string[]]$Markers)
    $script:mainRefusalSeq++
    $stamp = '{0:d2}' -f $script:mainRefusalSeq
    $treePath = $null
    if ($Dump -and $Dump.file -and (Test-Path -LiteralPath $Dump.file)) {
        $treePath = Join-Path $outPath "refusal-main-screen-$stamp.xml"
        Copy-Item -LiteralPath $Dump.file -Destination $treePath -Force
    }
    $current = Invoke-AdbVerb -Arguments @('current')
    $focus = Get-ForeignWindowPackage
    $nodeInfo = if ($Dump) { "$(@($Dump.nodes).Count)" } else { 'none - the dump verb returned no tree' }
    $treeInfo = if ($treePath) { $treePath } else { 'not captured' }
    $focusInfo = if ($focus) { $focus } else { 'none - the app held focus, or the window dump was unreadable' }
    $notePath = Join-Path $outPath "refusal-main-screen-$stamp.txt"
    @(
        'refusal:  the app never reached its start screen',
        "attempt:  $stamp",
        "at:       $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))",
        "markers:  $($Markers -join ' | ')",
        "nodes:    $nodeInfo",
        "tree:     $treeInfo",
        "current:  $($current.Output)",
        "focus:    $focusInfo"
    ) -join [Environment]::NewLine | Set-Content -LiteralPath $notePath -Encoding UTF8
    return "diagnostic: $notePath"
}

function Wait-ForMainScreen {
    # The one position the walk can verify it reached: the entry point's own marker, in the current
    # combination's language. The wizard's title shares the marker string with the button label on
    # Main, so a wizard still open - a setup that did not finish by itself - is backed out of rather
    # than accepted as the start screen.
    param([int]$Attempts = 10)
    $main = $screenById['main']
    $markers = Get-MarkerTexts -Record $main
    $lastDump = $null
    for ($i = 0; $i -lt $Attempts; $i++) {
        Start-Sleep -Milliseconds 1000
        $dump = Read-UiDump
        if ($dump) {
            $lastDump = $dump
            $h = Get-Haystack $dump
            if ($h -match 'cardLocalFolder') {
                Invoke-AdbVerb -Arguments @('key', '-Key', 'BACK') | Out-Null
                continue
            }
            foreach ($mk in $markers) { if (Test-HaystackHasToken $h $mk) { return $true } }
        }
    }
    $script:mainRefusalNote = Save-MainScreenRefusal -Dump $lastDump -Markers $markers
    return $false
}

# --- evidence ------------------------------------------------------------------------------------

function Save-Shot {
    # One screenshot, moved under the combination-and-screen name. A frame under FLAG_SECURE comes
    # back black BY DESIGN; the tree the shot verb pulls beside it is the evidence then, and the
    # caller scores `manual`, never `failed`.
    param([Parameter(Mandatory)][string]$Name)
    # Re-assert the rotation immediately before the capture. Every tree read opens a UiAutomation
    # connection, and that connection restores the rotation state it cached when it closes - so a
    # combination that rotated correctly at its start drifts back to portrait somewhere inside a long
    # screen list. Measured emulator-5554 2026-09-20: a two-screen run rotated, and the 43-screen run
    # right after it produced 44 landscape-labelled frames of which 0 were wide.
    if ($null -ne $script:userRotation) {
        Invoke-Shell "settings put system user_rotation $($script:userRotation)" | Out-Null
        Start-Sleep -Milliseconds $SettleMs
    }
    $shot = Invoke-AdbJson -Arguments @('shot', '-Json')
    if (-not $shot -or -not $shot.file) { return $null }
    $dest = Join-Path $outPath "$Name.png"
    Move-Item -LiteralPath $shot.file -Destination $dest -Force
    $out = [ordered]@{ file = $dest; secure = [bool]$shot.secureWindow; treeFile = $null; rotationMismatch = $false }
    # The frame's own pixels decide, not the setting's read-back: a mislabelled frame is the one
    # artifact this sweep cannot recover from, because every later stage trusts the name.
    if ($null -ne $script:userRotation) {
        try {
            Add-Type -AssemblyName System.Drawing
            $probe = [System.Drawing.Bitmap]::FromFile($dest)
            $isWide = $probe.Width -gt $probe.Height
            $probe.Dispose()
            $wantWide = ($script:userRotation -ne 0)
            if ($isWide -ne $wantWide) { $out.rotationMismatch = $true }
        } catch {
            # Unreadable here means the compression stage will classify it too; leaving the flag
            # false keeps that one verdict in one place rather than splitting it across two stages.
        }
    }
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
    # --include-stopped-packages: a force-stopped package is excluded from implicit delivery by
    # default, so after a cold `stop` the broadcast answered result=0 and the combination was refused
    # as "no hook" on a build that carries one (emulator-5554, 2026-09-20). The flag starts the
    # process for the receiver; the stored value is read by the next Activity onCreate.
    $call = Invoke-Shell "am broadcast --include-stopped-packages -a com.sza.fastmediasorter.debug.THEME_TEST_SET --es theme $Theme -p $pkg"
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
    $script:userRotation = $UserRotation
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

function Invoke-ExpandSection {
    # Read the state, then act on it - the same rule the toggle rows already follow, and for the same
    # reason. A collapsible settings section PERSISTS its expanded state across app restarts, so a
    # blind tap on the header closes a section a previous run left open, and the row underneath then
    # reports "never became visible" on a screen that was showing it one run earlier. Measured
    # emulator-5554 2026-09-20 on headerStreams and headerAuthorization both, in consecutive runs.
    # The container is the readable signal, not the row: an expanded row can still be below the fold
    # and absent from the tree, while the container sits next to its own header.
    param([Parameter(Mandatory)][string]$HeaderId, [string]$ContainerId)
    return (Set-SectionState -HeaderId $HeaderId -ContainerId $ContainerId -Open $true)
}

function Get-NodeBottom {
    # The bottom edge in pixels of the first node carrying $Id, read from the raw tree; $null if absent.
    param([string]$DumpFile, [string]$Id)
    if (-not $DumpFile -or -not (Test-Path -LiteralPath $DumpFile)) { return $null }
    $raw = Get-Content -LiteralPath $DumpFile -Raw -Encoding UTF8
    $m = [regex]::Match($raw, 'resource-id="[^"]*/' + [regex]::Escape($Id) + '"[^>]*bounds="\[\d+,\d+\]\[\d+,(\d+)\]"')
    if (-not $m.Success) { return $null }
    return [int]$m.Groups[1].Value
}

function Get-SectionOpen {
    # Whether a collapsible section is open, judged by its container. The header draws its state only
    # as an accessibility stateDescription, which the tree dump does not carry. Returns $true, $false,
    # or $null when the header itself was never found. A container is GONE while collapsed, so its
    # absence is the collapsed signal - but only once the screen has room below the header: a header
    # on the bottom edge hides an OPEN container too, so the page is moved up one step before the
    # absence is believed.
    param([string]$HeaderId, [string]$Target)
    $dump = Find-OnScreenOrHunt -ResourceId $HeaderId
    if (-not $dump) { return $null }
    if (Test-HaystackHasToken (Get-Haystack $dump) $Target) { return $true }
    $bottom = Get-NodeBottom -DumpFile $dump.file -Id $HeaderId
    if ($null -ne $bottom -and $bottom -gt ($script:screenH * 0.6)) {
        Invoke-ScrollDown
        Start-Sleep -Milliseconds $SettleMs
        $again = Read-UiDump
        if ($again -and (Test-HaystackHasToken (Get-Haystack $again) $Target)) { return $true }
    }
    return $false
}

function Set-SectionState {
    # Read, then tap only when the section is not already where it is wanted. A section header is a
    # toggle whose state persists across restarts, and every blind tap in this walk has at some point
    # closed a section it meant to open: on 2026-09-25 five Media sub-sections scored `failed` because
    # a run before had left them open. Returns $null on success, a reason otherwise.
    param([Parameter(Mandatory)][string]$HeaderId, [string]$ContainerId, [bool]$Open)
    $target = if ($ContainerId) { $ContainerId } else { $HeaderId -replace '^header', 'container' }
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        $state = Get-SectionOpen -HeaderId $HeaderId -Target $target
        if ($null -eq $state) { return "the section header '$HeaderId' never became visible" }
        if ($state -eq $Open) { return $null }
        # Get-SectionOpen may have moved the page up one step, so the header is found again, not assumed.
        $tap = Invoke-ReachControl -Record @{ resourceId = $HeaderId } -Cap $MaxScrolls
        if ($tap.Exit -ne 0) { return "tapping the section header '$HeaderId' failed: $($tap.Output)" }
        Start-Sleep -Milliseconds $SettleMs
    }
    $final = Get-SectionOpen -HeaderId $HeaderId -Target $target
    if ($final -eq $Open) { return $null }
    return "'$HeaderId' was tapped twice and '$target' is still $(if ($Open) { 'absent' } else { 'present' }) - the section did not $(if ($Open) { 'open' } else { 'close' })"
}

function Test-SectionRecord {
    # A catalog record whose control is a collapsible section header rather than a screen opener.
    param($Record)
    return ([string]$Record.resourceId -match '^header' -and $Record.from -ne 'settings')
}

function Invoke-CatalogEntrySteps {
    # The interpreter for the catalog's setup and teardown arrays. The entries are data - reach
    # fields, toggle actions, the folder pick - and this walks them in the declared order.
    param($Entry)
    if ($Entry.skipWhenSeedOnMain) {
        # Adding the seeded folder is not idempotent: every run added one more card over the same
        # root (three on emulator-5562 by 2026-09-25), and the extra rows push later Main controls
        # below the fold. A card already drawing the folder name means the resource exists.
        $mainDump = Read-UiDump
        if ($mainDump -and (Test-HaystackHasToken (Get-Haystack $mainDump) $seedFolderName)) {
            Write-Host "setup: $($Entry.id) skipped - Main already lists '$seedFolderName'"
            return $null
        }
    }
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
        if ($step.action -eq 'select-tab') {
            $r = Invoke-SelectSettingsTab -Record $step
            if ($r) { return $r }
            continue
        }
        if ($step.action -eq 'expand') {
            $r = Invoke-ExpandSection -HeaderId ([string]$step.resourceId) -ContainerId ([string]$step.containerId)
            if ($r) { return $r }
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
        # The wizard opens on the resource-TYPE chooser, not on the local-folder page: measured
        # emulator-5554 2026-09-20, where btnAddManually was absent and the tree carried six
        # cardXxx choices instead. Optional, because a build offering a single type would land on
        # the page directly and a hard tap here would then refuse a wizard that is working.
        @{ name = 'resource-type-local'; id = 'cardLocalFolder'; optional = $true },
        @{ name = 'add-manually'; id = 'btnAddManually' },
        # btnRoot is the second door: a build whose manifest cannot obtain all-files access draws no
        # SAF button, and the quick-root button then launches the same picker. It lost the grant
        # until S3354 made it dismiss the dialog first, so a build older than that fix lists nothing
        # after the pick - the resource-list screens then fail as observed defects, not silently.
        @{ name = 'browse-with-saf'; ids = @('btnBrowseWithSAF', 'btnRoot') },
        # Optional: the picker opens on the device root already listing Download, DCIM and the rest,
        # so the roots drawer is needed only when it opens somewhere else - Recent, or a folder a
        # previous grant left it in. Refusing here would refuse a picker showing the destination.
        # The picker reopens where the last grant left it, so both navigation steps are skipped when
        # the breadcrumb already ends in the seed folder (emulator-5560, 2026-09-24: it opened inside
        # FastMediaSorter_UiSweep and the walk refused, looking for Download).
        @{ name = 'show-roots'; labels = @('Show roots'); optional = $true; skipWhenAt = $true },
        # Both spellings: the roots drawer says 'Downloads', the file list says 'Download', and which
        # of the two the walk meets depends on the step above it having been needed at all.
        @{ name = 'downloads'; labels = @('Download', 'Downloads'); skipWhenAt = $true },
        @{ name = 'seed-folder'; labels = @($FolderName); skipWhenAt = $true },
        @{ name = 'use-this-folder'; labels = @('USE THIS FOLDER', 'SELECT FOLDER') },
        # The grant dialog the system raises after the folder is chosen - 'Allow <app> to access
        # files in <folder>?'. Optional, because a tree already granted is handed back without it,
        # so a second run on the same emulator never sees this dialog at all.
        @{ name = 'allow-access'; labels = @('ALLOW', 'Allow'); optional = $true; exact = $true }
    )
    # Every step writes down what it tapped and where the screen went. The picker crosses two
    # processes and a system grant dialog, so a step that "succeeded" on the wrong screen looks
    # exactly like one that worked, and the failure then surfaces three steps later on a control
    # nobody expected to be missing (measured emulator-5554, 2026-09-20).
    $trace = [System.Collections.Generic.List[string]]::new()
    $tracePath = Join-Path $outPath 'saf-folder-pick.log'
    $atSeed = $false
    foreach ($step in $steps) {
        if ($step.skipWhenAt -and $atSeed) {
            $trace.Add("$($step.name): skipped - the picker already stands in $FolderName")
            continue
        }
        $tap = $null
        $ids = @(if ($step.ids) { $step.ids } elseif ($step.id) { @($step.id) } else { @() })
        if ($ids.Count -gt 0) {
            # Reached, not merely tapped: a control can sit below the fold on a 360 dp screen, and a
            # bare tap-id exits 8 on a screen that is working. Several ids are alternatives, tried in
            # order - the first one the screen actually carries wins.
            foreach ($id in $ids) {
                $tap = Invoke-ReachControl -Record @{ resourceId = $id } -Cap $MaxScrolls
                if ($tap.Exit -eq 0) { break }
            }
        }
        else {
            foreach ($label in $step.labels) {
                $tap = Invoke-ReachControl -Record @{ label = $label; exactLabel = $step.exact } -Cap $MaxScrolls
                if ($tap.Exit -eq 0) { break }
            }
        }
        $verdict = if (-not $tap) { 'no candidate tried' } elseif ($tap.Exit -eq 0) { 'tapped' } else { "exit $($tap.Exit)" }
        $trace.Add("$($step.name): $verdict :: $(($tap.Output -replace '\s+', ' '))")
        if (-not $tap -or $tap.Exit -ne 0) {
            if ($step.optional) { continue }
            $trace -join [Environment]::NewLine | Set-Content -LiteralPath $tracePath -Encoding UTF8
            return "the folder picker stalled at '$($step.name)' (trace: $tracePath): $($tap.Output)"
        }
        Start-Sleep -Milliseconds (2 * $SettleMs)
        $after = Read-UiDump
        if ($after) {
            $where = @($after.nodes | Where-Object { $_.resIdShort -in @('header_title', 'breadcrumb_text', 'alertTitle') } |
                ForEach-Object { $_.label }) -join ' | '
            $trace.Add("  -> $where")
            $crumbs = @($after.nodes | Where-Object { $_.resIdShort -eq 'breadcrumb_text' } | ForEach-Object { $_.label })
            $titles = @($after.nodes | Where-Object { $_.resIdShort -eq 'header_title' } | ForEach-Object { [string]$_.label })
            $atSeed = (($crumbs.Count -gt 0 -and $crumbs[-1] -eq $FolderName) -or
                @($titles | Where-Object { $_.EndsWith(" $FolderName") }).Count -gt 0)
        }
    }
    $trace -join [Environment]::NewLine | Set-Content -LiteralPath $tracePath -Encoding UTF8
    return $null
}

function Invoke-Setup {
    # Once per bench, before its first combination: secure surfaces photographable, Streams present,
    # one resource configured. A setup step that fails names its refusal; only the secure toggle's
    # failure changes how the catalog is walked (its four surfaces get refused-secure), the rest
    # surface later as honest screen outcomes.
    param([string]$Serial)
    $script:dev = $Serial
    # Before the first hunt: the swipe geometry defaults to 1080x2400 until read, and on a 720x1280
    # bench that swipe starts below the screen, scrolls nothing, and every setup row reads as absent
    # (emulator-5560, 2026-09-24, rowSecureSensitiveScreens and headerStreams both).
    Read-ScreenSize
    # The system picker runs in its own task, so stopping the app leaves a picker an aborted run
    # opened standing on top, and the relaunch never reaches Main (emulator-5560, 2026-09-24).
    Invoke-Shell 'am force-stop com.google.android.documentsui' | Out-Null
    Invoke-AdbVerb -Arguments @('stop') | Out-Null
    Start-Sleep -Milliseconds $SettleMs
    Invoke-AdbVerb -Arguments @('launch') | Out-Null
    if (-not (Wait-ForMainScreen)) { return "the app never reached its start screen after launch ($script:mainRefusalNote)" }

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
    if (-not (Wait-ForMainScreen)) { return "the walk is not on the start screen after setup ($script:mainRefusalNote)" }
    return $null
}

function Invoke-Teardown {
    # Runs on every exit path. The read-then-toggle form is what makes this a restore: a switch
    # already back in place is left alone, so teardown is safe to re-run and repairs what an
    # aborted run left behind the next time anything runs.
    param([string]$Serial)
    $script:dev = $Serial
    try {
        Read-ScreenSize
        # Always from a cold Main: an aborted setup leaves the app in front but inside Settings, with
        # no tracked stack for Pop-UntilMain to unwind, and the restore then cannot find btnSettings.
        Invoke-Shell 'am force-stop com.google.android.documentsui' | Out-Null
        Invoke-AdbVerb -Arguments @('stop') | Out-Null
        Start-Sleep -Milliseconds $SettleMs
        Invoke-AdbVerb -Arguments @('launch') | Out-Null
        $null = Wait-ForMainScreen
        $script:pos.Clear()
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
        $tap = Invoke-ReachRecord -Record $Screen -Cap $scrolls
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

    # The frame name carries the screen id as well as the combination: without it every screen in a
    # combination writes the same file and only the last one survives, which is also the link the
    # compressed corpus joins an observation back to (strategic 11 criterion 4).
    $frameName = "${Key}__$($Screen.id)"
    $row.tree = Save-Tree -DumpFile $dump.file -Name $frameName
    $shot = Save-Shot -Name $frameName
    if ($shot) {
        $row.shot = $shot.file
        if ($shot.secure) {
            $row.outcome = 'manual'
            $row.detail = 'the frame came back under FLAG_SECURE; the tree pulled beside it is the evidence'
            if ($shot.treeFile) { $row.tree = $shot.treeFile }
        }
        elseif ($shot.rotationMismatch -and $Screen.ownsOrientation) {
            # Declared in the catalog: the surface ignores a forced rotation by design, so the
            # portrait frame is the correct behaviour and not a display that failed to hold.
            $row.outcome = 'skipped'
            $row.detail = "the surface owns its orientation: $($Screen.ownsOrientation)"
        }
        elseif ($shot.rotationMismatch) {
            # Scored, not silently kept: a frame whose pixels contradict the orientation in its own
            # name would be read by the review as evidence about a layout it never photographed.
            $row.outcome = 'manual'
            $row.detail = 'the frame does not carry the orientation its combination declares - the display did not hold the rotation'
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
        $isSectionNode = ([string]$exp.resourceId -match '^header')
        if ($isSectionNode) {
            $openReason = Set-SectionState -HeaderId ([string]$exp.resourceId) -ContainerId ([string]$exp.containerId) -Open $true
            $expTap = if ($openReason) { [pscustomobject]@{ Exit = 8; Output = $openReason } } else { [pscustomobject]@{ Exit = 0; Output = 'section opened' } }
            if (-not $openReason) { $null = Find-OnScreenOrHunt -ResourceId ([string]$exp.resourceId) }
        }
        else { $expTap = Invoke-ReachControl -Record $exp -Cap $MaxScrolls }
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
        $expName = "${Key}__$($Screen.id)__x$('{0:d2}' -f $expandNo)"
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
        if ($isSectionNode) {
            $null = Set-SectionState -HeaderId ([string]$exp.resourceId) -ContainerId ([string]$exp.containerId) -Open $false
        }
        else {
            $null = Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', [string]$exp.resourceId, '-Exact')
            Start-Sleep -Milliseconds $settleMs
        }
    }
    $row.expanded = @($expandResults)

    Add-Row $row

    # A section opened as a screen is closed again: left open, every later section on the page sits
    # one section lower, and the hunt for it runs out of scrolls on a 640 dp screen.
    if (Test-SectionRecord $Screen) {
        $null = Set-SectionState -HeaderId ([string]$Screen.resourceId) -ContainerId ([string]$Screen.containerId) -Open $false
    }

    # Climb out by the declared count, popping one real level per BACK.
    $backAfter = if ($null -ne $Screen.backAfter) { [int]$Screen.backAfter } else { 1 }
    if ($backAfter -gt 0) { Pop-RealLevels -Count $backAfter }
}

# --- run -----------------------------------------------------------------------------------------

$runStarted = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')

# Two walks on one device share the remote tree path, so each deletes the other's dump between dump and
# pull and both read "the pull produced no file" (emulator-5560, 2026-09-24: an orphaned walk from a
# dead session ran under a fresh one for twenty minutes and every symptom pointed at the device).
$otherWalks = @(Get-CimInstance Win32_Process -Filter "Name='pwsh.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.ProcessId -ne $PID -and $_.CommandLine -match 'ui-sweep-walk\.ps1' })
if ($otherWalks.Count -gt 0) {
    Stop-Run 2 ("another ui-sweep-walk is already running (PID $(@($otherWalks.ProcessId) -join ', ')) - two walks " +
        "on one bench corrupt each other's tree reads; wait for it to exit")
}

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
                foreach ($s in $walkScreens) {
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
                foreach ($s in $walkScreens) {
                    Add-Row ([ordered]@{
                        combination = $key; screen = $s.id; name = $s.name
                        outcome = 'refused-state'; detail = 'the app never reached its start screen after the combination launch'
                        shot = $null; tree = $null; expanded = @(); rehomed = $false
                    })
                }
                if (-not $Json) { Write-Host "walk: combination $key -> refused-state (no start screen)" -ForegroundColor Yellow }
                continue
            }

            foreach ($s in $walkScreens) {
                Invoke-WalkScreen -Screen $s -Combo $combo -Key $key
            }

            # Whatever the declared `backAfter` chain left open, the next combination starts from a
            # force-stop anyway - but the journal is written per run, so leave the device tidy.
            Pop-UntilMain
            if (-not $Json) {
                $soFar = @($script:rows | Where-Object { $_.combination -eq $key })
                $observed = @($soFar | Where-Object { $_.outcome -eq 'observed' }).Count
                Write-Host "walk: combination $key done - $observed/$($walkScreens.Count) observed" -ForegroundColor Cyan
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

$expectedRows = $walkScreens.Count * $combinations.Count
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
    screens      = $walkScreens.Count
    expectedRows = $expectedRows
    rowCount     = $rowCount
    counts       = $counts
    rows         = $script:rows
}
$journalPath = Join-Path $outPath 'sweep-journal.json'
$journal | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $journalPath -Encoding UTF8

if ($rowCount -ne $expectedRows) {
    Stop-Run 2 "the row-count identity failed: $rowCount rows for $($walkScreens.Count) screens x $($combinations.Count) combinations = $expectedRows; journal: $journalPath"
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
