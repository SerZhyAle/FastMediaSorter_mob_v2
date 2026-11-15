<#
.SYNOPSIS
    Captures the in-app help screenshots with numbered markers, per locale and theme (CONTEXT-HELP rule 14).

.DESCRIPTION
    Manual tool: drives a device the caller chose from docs/DEVICE_FLEET.md, so no gate or closure may invoke it.

    For each `page` row of app_v2/src/main/assets/help/help-map.json (and each settings tab it lists), each
    locale of -Locales and each theme of -Themes:
    - seeds demo content once through scripts/devtest/ui-sweep-seed.ps1 (skip with -SkipSeed);
    - sets the app locale (`cmd locale set-app-locales`) and the system night mode (`cmd uimode night`), which
      the app follows while its colour theme is AUTO, the default;
    - reaches the surface through scripts/devtest/adb.ps1 by component or resource id, never by coordinate; the
      browser and the player are reached from the main screen by tapping the seeded "All Videos" resource by its
      label in the captured locale, then, for the player, the seeded video by its file name and a pause; a screen
      that is not exported (`am start` is refused for it) is reached the way a person reaches it - a button of
      the main screen, a settings row, an entry of the three-dots programs menu by its label - and the recipe's
      `Expect` names the activity that must be in the foreground before the frame is trusted;
    - takes a screenshot and a uidump, reads the bounds of every view id the page source
      docs/content/help/<help-id>.json lists as an element, and draws a numbered circular marker at each one;
      the number is the element's position in that list, the same number the generated page prints;
    - shoots portrait, or landscape for a page whose recipe says so (the main screen's command bar);
    - downscales and writes app_v2/src/main/assets/help/img/<help-id>-<locale>-<theme>.webp.

    A page records which of its elements can be absent from a single frame. The main screen shows every
    element but two (the search field exists in the table view only, the type tabs only while a remote source
    is on); a settings tab and the welcome wizard scroll or page, so one frame holds only the first rows and
    the rest are allowed to be missing. Any other absent element fails that page with its id named.

    Device state touched - app locale, night mode, rotation lock, System UI demo mode - is read before the
    first capture and written back at the end, also after a failure.

    Pages are not regenerated here: run scripts/docs/help/generate-help-pages.ps1 afterwards so each page
    embeds its image.

.PARAMETER DeviceId
    adb serial. Required when more than one device is attached. Check docs/DEVICE_FLEET.md first.

.PARAMETER Package
    Application id to drive. Default: the standard debug build.

.PARAMETER Help
    Capture only these help ids (comma-separated). Default: every page the help map lists.

.PARAMETER Locales
    Locales to capture. Default: en, ru, uk.

.PARAMETER Themes
    Themes to capture. Default: light, dark.

.PARAMETER Width
    Published width of a page shot in portrait. Default: 540.

.PARAMETER LandscapeWidth
    Published width of a page shot in landscape. Default: 960.

.PARAMETER Quality
    WebP quality, 0-100. Default: 80.

.PARAMETER OutRoot
    Folder receiving the images. Default: app_v2/src/main/assets/help/img.

.PARAMETER SkipSeed
    Do not run the demo-content seed (the device already carries the content to show).

.PARAMETER SettleSeconds
    Wait after reaching a surface before the frame is taken. Default: 3.

.PARAMETER F1Proof
    Take no screenshot. Reach each surface, send the F1 key and require the help viewer
    (HelpPageActivity) in the foreground afterwards: the proof that the screen's keyboard help entry opens its
    page inside the app. Needs no seeded content for the screens reached by component or settings row.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/help/capture-help-screenshots.ps1 -DeviceId emulator-5554

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/help/capture-help-screenshots.ps1 -DeviceId RFCR110NBQJ -Help main -Locales en -Themes light -SkipSeed

.NOTES
    Exit codes:
      0 - every requested page was captured
      1 - a required element was missing from a frame, or a capture step failed; the other pages were still captured
      2 - no device, no adb.ps1, no ffmpeg with libwebp, System.Drawing unavailable, or the help map / a page source is missing
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [string]$Package = 'com.sza.fastmediasorter.debug',
    [string]$Help = '',
    [string[]]$Locales = @('en', 'ru', 'uk'),
    [ValidateSet('light', 'dark')]
    [string[]]$Themes = @('light', 'dark'),
    [int]$Width = 540,
    [int]$LandscapeWidth = 960,
    [ValidateRange(0, 100)]
    [int]$Quality = 80,
    [string]$OutRoot = '',
    [switch]$SkipSeed,
    [int]$SettleSeconds = 3,
    [switch]$F1Proof
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$adbScript = Join-Path $repoRoot 'scripts/devtest/adb.ps1'
$mapPath = Join-Path $repoRoot 'app_v2/src/main/assets/help/help-map.json'
$sourceRoot = Join-Path $repoRoot 'docs/content/help'
if ([string]::IsNullOrWhiteSpace($OutRoot)) { $OutRoot = Join-Path $repoRoot 'app_v2/src/main/assets/help/img' }
elseif (-not [System.IO.Path]::IsPathRooted($OutRoot)) { $OutRoot = Join-Path $repoRoot $OutRoot }
$workDir = Join-Path $repoRoot 'temp/scratch/help-screenshots'

$settingsComponent = 'com.sza.fastmediasorter.ui.settings.SettingsActivity'
$mainComponent = 'com.sza.fastmediasorter.ui.main.MainActivity'
$streamsComponent = 'com.sza.fastmediasorter.ui.streams.StreamsActivity'

# How each page is reached, in which orientation, and which of its elements one frame may lack. A tab
# index is SettingsActivity.TAB_*; the welcome wizard is not exported, so it is opened from its settings
# row. The main screen is shot in landscape: its portrait command bar scrolls and hides its last buttons.
$recipes = @{
    'main'                = @{ Component = $mainComponent; Landscape = $true; Optional = @('search', 'type-tabs') }
    'settings-general'    = @{ Component = $settingsComponent; Tab = 0; FirstFrameOnly = $true }
    'settings-media'      = @{ Component = $settingsComponent; Tab = 1; FirstFrameOnly = $true }
    'settings-playback'   = @{ Component = $settingsComponent; Tab = 2; FirstFrameOnly = $true }
    'settings-operations' = @{ Component = $settingsComponent; Tab = 3; FirstFrameOnly = $true }
    'welcome'             = @{ Component = $settingsComponent; Tab = 0; TapIdScrolling = 'btnOpenWelcome'; FirstFrameOnly = $true }
    # The browser and the player are reached through the main screen: the seeded virtual "All Videos"
    # resource row is tapped by its label (a string key, resolved per locale), and the player by the name of
    # a two-minute loop of the seeded video, which the run publishes first (the seeded clip itself lasts six
    # seconds, so a slow start ends it before it can be paused). The first row of the list is not a safe pick,
    # since the device may hold a clip this build cannot decode. The video is paused so the seek bar stops
    # moving, which is what lets uiautomator dump the tree at all. A virtual resource is read-only and has no subfolder, so the
    # create buttons and the path button are absent, and the selection bar exists only while files are
    # selected; the player frame lacks the controls gated by file state. The portrait bar of the browser pushes
    # its two lowest-priority commands, play and play-random, into the menu - the shape a phone user sees.
    'browse'              = @{ Component = $mainComponent; OpenResourceLabelKey = 'virtual_all_video'
                               Optional = @('path', 'create-folder', 'create-text-note', 'create-drawing', 'mic-record',
                                            'play-random', 'play', 'copy', 'move', 'rename', 'delete', 'undo', 'share', 'archive') }
    'player'              = @{ Component = $mainComponent; OpenResourceLabelKey = 'virtual_all_video'
                               LongClip = 'Harbour Lights long.mp4'; OpenFileLabel = 'Harbour Lights long.mp4'; PauseAfterOpen = $true
                               Optional = @('random', 'share', 'info', 'undo') }
}

# Slice 3 (S4293). Almost none of these screens is exported, so `am start` is refused for them: each is reached
# the way a person reaches it - a button of the main screen, a row of a settings tab, an entry of the three-dots
# programs menu (by its label in the captured locale). `Steps` run in order after the first screen is up;
# `Expect` names the activity that must be in the foreground before the frame is trusted. These screens scroll or
# fill with data, so one frame holds the first rows and the rest are allowed to be missing (`FirstFrameOnly`).
# The programs menu lists a program only while its settings switch is on (the calculator, the stopwatch, the
# network monitor, the tourist dashboard, the mini-game, both flashlights and SOS are off by default; their
# rows sit in the collapsed "Additional programs" section of the operations tab), so those recipes expect the
# switches on.
# No recipe, so the page is published without a screenshot: the stopwatch and the tourist dashboard redraw
# continuously, which keeps uiautomator from ever reaching an idle tree (adb.ps1 exit 7, even with the animation
# scales at zero); the network monitor crashes on open on the emulator (S4331); the resource editor, the
# duplicates scan, the statistics dashboard, the tourist counter and both camera screens need a resource, scan
# result, opt-in switch, dashboard tile or camera frame the script does not set up; the watch companion is a
# Compose island with no view ids to read; the front flashlight has no entry in the programs menu to tap; "My
# devices" lists the exchange servers of the account the device is signed in to, which a published frame must
# never show.
$programsMenu = @(@{ TapId = 'btnMainDropdownMenu' })
$slice3 = @{
    'add-resource'         = @{ Component = $mainComponent; Steps = @(@{ TapId = 'btnAddResource' }); Expect = 'AddResourceActivity' }
    'streams'              = @{ Component = $streamsComponent; Expect = 'StreamsActivity' }
    'auth-sessions'        = @{ Component = $settingsComponent; Tab = 0; Expect = 'AuthSessionsActivity'
                                Steps = @(@{ ExpandSection = 'headerAuthorization'; Reveals = 'row_saved_authorizations' }, @{ TapIdScrolling = 'row_saved_authorizations' }) }
    'keybinding-remap'     = @{ Component = $settingsComponent; Tab = 3; TapIdScrolling = 'rowControlsKeybindings'; Expect = 'KeybindingRemapActivity' }
    'scheduled-operations' = @{ Component = $mainComponent; Steps = $programsMenu + @(@{ TapLabelKey = 'widget_scheduled_tasks_label' }); Expect = 'ScheduledOperationsActivity' }
    'system-info'          = @{ Component = $settingsComponent; Tab = 0; TapIdScrolling = 'btnSystemInfo'; Expect = 'SystemInfoActivity' }
    'launch-panel'         = @{ Component = $mainComponent; Steps = $programsMenu + @(@{ TapLabelKey = 'app_launch_panel_title' }); Expect = 'AppLaunchPanelActivity' }
    'launch-panel-edit'    = @{ Component = $mainComponent; Steps = $programsMenu + @(@{ TapLabelKey = 'app_launch_panel_title' }, @{ TapId = 'btnEditPanel' }); Expect = 'EditAppLaunchPanelActivity' }
    'mirror'               = @{ Component = $mainComponent; Steps = $programsMenu + @(@{ TapLabelKey = 'mirror_title' }); Expect = 'MirrorActivity' }
    'calculator'           = @{ Component = $mainComponent; Steps = $programsMenu + @(@{ TapLabelKey = 'app_launch_panel_route_calculator' }); Expect = 'CalculatorActivity' }
    'game'                 = @{ Component = $mainComponent; Steps = $programsMenu + @(@{ TapLabelKey = 'app_launch_panel_route_game' }); Expect = 'GameActivity' }
    'water-flashlight'     = @{ Component = $mainComponent; Steps = $programsMenu + @(@{ TapLabelKey = 'water_flashlight_title' }); Expect = 'WaterFlashlightActivity' }
    'sos'                  = @{ Component = $mainComponent; Steps = $programsMenu + @(@{ TapLabelKey = 'sos_title' }); Expect = 'SosActivity' }
}
foreach ($key in $slice3.Keys) {
    $slice3[$key].FirstFrameOnly = $true
    $recipes[$key] = $slice3[$key]
}

# Slice 4 (S4294). The broadcast control screen is not exported and the programs menu lists "Live Broadcast" only
# while live broadcasting is switched on (Settings, Media tab, group Broadcast; off by default), so the recipe
# expects that switch on, like the slice 3 program recipes. The frame is the idle state: the live controls, the
# share block, the camera picture and the listener count exist only on air or with a camera mode, so one frame
# is allowed to lack them (`FirstFrameOnly`) and their markers are simply not drawn.
$recipes['broadcast'] = @{ Component = $mainComponent; Steps = $programsMenu + @(@{ TapLabelKey = 'broadcast_menu_label' }); Expect = 'BroadcastControlActivity'; FirstFrameOnly = $true }

function Write-Fail([string]$Message) { Write-Host "capture-help-screenshots: $Message" -ForegroundColor Red }

function Invoke-AdbVerb {
    param([string[]]$VerbArgs, [switch]$AllowFail)
    $deviceArgs = if ($DeviceId) { @('-DeviceId', $DeviceId) } else { @() }
    # A tree verb answers 7 while the window still emits accessibility events (the main screen's resource
    # list and a playing video do), so it is retried; every other verb gets one attempt.
    $attempts = if (@('uidump', 'tap-label', 'tap-id') -contains $VerbArgs[0]) { 4 } else { 1 }
    for ($attempt = 1; $attempt -le $attempts; $attempt++) {
        $out = & pwsh -NoProfile -File $adbScript @VerbArgs @deviceArgs -Package $Package -OutDir $workDir -Json 2>$null
        $code = $LASTEXITCODE
        if ($code -ne 7) { break }
        Start-Sleep -Seconds 2
    }
    $parsed = $null
    $text = ($out | Out-String).Trim()
    $jsonLine = @($text -split "`r?`n" | Where-Object { $_.TrimStart().StartsWith('{') }) | Select-Object -Last 1
    if ($jsonLine) { $parsed = $jsonLine | ConvertFrom-Json }
    if ($code -ne 0 -and -not $AllowFail) { throw "adb.ps1 $($VerbArgs -join ' ') exited $code" }
    return $parsed
}

function Invoke-DeviceShell([string]$Command) {
    $r = Invoke-AdbVerb @('shell', '-Cmd', $Command) -AllowFail
    if ($r -and $r.data -and $r.data.out) { return ([string]$r.data.out).Trim() }
    return ''
}

function Resolve-Ffmpeg {
    $cmd = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    # A winget install reaches PATH only in shells started after it.
    $candidate = Join-Path $env:LOCALAPPDATA 'Microsoft/WinGet/Links/ffmpeg.exe'
    if (Test-Path $candidate) { return $candidate }
    return $null
}

# The UI string the app shows for a key in the locale being captured, English when that locale has no value.
# A label read from the same resources as the interface cannot drift from what the device shows.
function Get-UiString([string]$Key, [string]$Locale) {
    $resRoot = Join-Path $repoRoot 'app_v2/src/main/res'
    $folders = @("values-$Locale", 'values')
    foreach ($folder in $folders) {
        $dir = Join-Path $resRoot $folder
        if (-not (Test-Path $dir)) { continue }
        foreach ($file in Get-ChildItem $dir -Filter 'strings*.xml') {
            $m = [regex]::Match((Get-Content -Raw -Encoding UTF8 $file.FullName), "<string name=`"$([regex]::Escape($Key))`"[^>]*>([^<]*)</string>")
            if ($m.Success) { return [System.Net.WebUtility]::HtmlDecode($m.Groups[1].Value) }
        }
    }
    throw "UI string '$Key' not found for locale '$Locale'"
}

# Publishes the long clip a recipe names: the seeded video looped by stream copy, pushed next to the seeded
# corpus and handed to MediaStore, which is what the virtual "All Videos" resource lists.
function Publish-LongClip([string]$Name) {
    $source = Join-Path $repoRoot 'temp/S2380/media/Videos/Harbour Lights.mp4'
    if (-not (Test-Path $source)) { throw "seeded clip missing: $source (run scripts/devtest/ui-sweep-seed.ps1 without -SkipGenerate)" }
    $local = Join-Path $workDir $Name
    & $ffmpeg -loglevel error -y -stream_loop 19 -i $source -c copy $local
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $local)) { throw 'ffmpeg could not loop the seeded clip' }
    $remoteDir = '/sdcard/Download/FastMediaSorter_UiSweep/Videos'
    Invoke-AdbVerb @('push', '-Local', $local, '-Remote', "$remoteDir/$Name") | Out-Null
    Invoke-DeviceShell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$remoteDir/$Name'" | Out-Null
}

function Show-PlayerControls([string[]]$ViewIds) {
    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        $present = @(Get-FrameNodes | Where-Object { $ViewIds -contains $_.resIdShort } | ForEach-Object { $_.resIdShort } | Select-Object -Unique)
        if ($present.Count -ge $ViewIds.Count) { return }
        Invoke-AdbVerb @('tap-id', '-ResourceId', 'playerView', '-Exact') | Out-Null
        Start-Sleep -Seconds 1
    }
}

function Get-PageIds($Map) {
    $ids = [System.Collections.Generic.List[string]]::new()
    foreach ($row in $Map.surfaces) {
        if ($row.form -ne 'page') { continue }
        if ($row.help -and -not $ids.Contains([string]$row.help)) { $ids.Add([string]$row.help) }
        foreach ($sub in @($row.pages)) {
            if ($sub -and $sub.help -and -not $ids.Contains([string]$sub.help)) { $ids.Add([string]$sub.help) }
        }
    }
    return $ids
}

function Get-FrameNodes {
    $r = Invoke-AdbVerb @('uidump', '-Ids')
    return @($r.data.nodes)
}

# Taps a control by id, scrolling the current screen up to thirty times until it is on the tree: the general
# settings tab is long enough that its authorizations row lies past the twelfth page.
function Find-IdScrolling([string]$ViewId) {
    for ($i = 0; $i -lt 30; $i++) {
        if (@(Get-FrameNodes | Where-Object { $_.resIdShort -eq $ViewId }).Count -gt 0) { return $true }
        Invoke-DeviceShell 'input swipe 540 1800 540 700 300' | Out-Null
        Start-Sleep -Milliseconds 700
    }
    return $false
}

function Tap-IdScrolling([string]$HelpId, [string]$ViewId) {
    if (-not (Find-IdScrolling $ViewId)) { throw "help '$HelpId': '$ViewId' never scrolled into view" }
    Invoke-AdbVerb @('tap-id', '-ResourceId', $ViewId, '-Exact') | Out-Null
    Start-Sleep -Seconds $SettleSeconds
}

# Opens a collapsed settings section by its header when the control the recipe needs is not on the tree yet;
# an already open section is left alone, since a second tap on the header would close it.
function Expand-Section([string]$HelpId, [string]$HeaderId, [string]$RevealsId) {
    if (-not (Find-IdScrolling $HeaderId)) { throw "help '$HelpId': section header '$HeaderId' never scrolled into view" }
    if (@(Get-FrameNodes | Where-Object { $_.resIdShort -eq $RevealsId }).Count -eq 0) {
        Invoke-AdbVerb @('tap-id', '-ResourceId', $HeaderId, '-Exact') | Out-Null
        Start-Sleep -Seconds 1
    }
}

function Open-Surface([string]$HelpId, $Recipe, [string]$Locale) {
    Invoke-AdbVerb @('stop') -AllowFail | Out-Null
    Invoke-DeviceShell "settings put system user_rotation $(if ($Recipe.Landscape) { 1 } else { 0 })" | Out-Null
    $start = "am start -n $Package/$($Recipe.Component)"
    if ($null -ne $Recipe.Tab) { $start += " --ei extra_initial_tab $($Recipe.Tab)" }
    Invoke-DeviceShell $start | Out-Null
    Start-Sleep -Seconds $SettleSeconds
    if ($Recipe.OpenResourceLabelKey) {
        Invoke-AdbVerb @('tap-label', '-Label', (Get-UiString $Recipe.OpenResourceLabelKey $Locale)) | Out-Null
        Start-Sleep -Seconds $SettleSeconds
    }
    if ($Recipe.OpenFileLabel) {
        # The name text spans most of the row and the favorite star lies inside its bounds, so the centre of
        # the node would toggle the star: tap the left edge of the name, which is still the row.
        $row = @(Get-FrameNodes | Where-Object { $_.resIdShort -eq 'tvFileName' -and $_.text -eq $Recipe.OpenFileLabel }) | Select-Object -First 1
        if (-not $row) { throw "help '$HelpId': file '$($Recipe.OpenFileLabel)' is not on the list" }
        Invoke-AdbVerb @('tap', '-X', [string]([int]$row.x1 + 40), '-Y', [string][int]$row.tapY) | Out-Null
        Start-Sleep -Seconds 2
        if ((Invoke-DeviceShell 'dumpsys activity activities | grep -m1 topResumedActivity') -notmatch 'PlayerActivity') {
            throw "help '$HelpId': tapping '$($Recipe.OpenFileLabel)' did not open the player"
        }
        if ($Recipe.PauseAfterOpen) {
            # A playing seek bar keeps uiautomator from dumping the tree at all, so the clip is paused by key
            # event, which needs no tree. It is sent once, after the controller has hidden itself: by then the
            # player is surely playing, and a pause sent before it is ready would do nothing. A second press
            # hides the controller again, so there is no retry.
            Start-Sleep -Seconds 6
            Invoke-DeviceShell 'input keyevent 127' | Out-Null
            Start-Sleep -Seconds 1
            # The command panel and the transport controls hide themselves while the clip plays, a pause brings
            # back only the transport controls, and the rewind can hide them again. A tap on the picture toggles
            # them, so it is repeated until the wanted ones are on the frame, looking first because a tap on
            # visible controls would hide them. The rewind needs the transport controls; the frame needs both.
            Show-PlayerControls @('exo_progress')
            # The rewind puts every capture on the first frame of the clip.
            Invoke-AdbVerb @('tap-id', '-ResourceId', 'btnRewind10', '-Exact') -AllowFail | Out-Null
            Start-Sleep -Seconds 1
            Show-PlayerControls @('btnBack', 'exo_progress')
        } else {
            Start-Sleep -Seconds $SettleSeconds
        }
    }
    if ($Recipe.TapIdScrolling) { Tap-IdScrolling $HelpId $Recipe.TapIdScrolling }
    foreach ($step in @($Recipe.Steps | Where-Object { $_ })) {
        if ($step.TapId) {
            Invoke-AdbVerb @('tap-id', '-ResourceId', [string]$step.TapId, '-Exact') | Out-Null
            Start-Sleep -Seconds $SettleSeconds
        } elseif ($step.ExpandSection) {
            Expand-Section $HelpId ([string]$step.ExpandSection) ([string]$step.Reveals)
        } elseif ($step.TapIdScrolling) {
            Tap-IdScrolling $HelpId ([string]$step.TapIdScrolling)
        } elseif ($step.TapLabelKey) {
            Invoke-AdbVerb @('tap-label', '-Label', (Get-UiString ([string]$step.TapLabelKey) $Locale)) | Out-Null
            Start-Sleep -Seconds $SettleSeconds
        } else {
            throw "help '$HelpId': unknown recipe step"
        }
    }
    if ($Recipe.Expect) {
        # An id such as `toolbar` exists on many screens, so a wrong screen would still draw a marker: the
        # foreground activity is checked by name before the frame is trusted.
        $top = Invoke-DeviceShell 'dumpsys activity activities | grep -m1 topResumedActivity'
        if ($top -notmatch [regex]::Escape([string]$Recipe.Expect)) {
            throw "help '$HelpId': expected $($Recipe.Expect) in the foreground, found: $top"
        }
    }
}

function Add-Markers([string]$RawPng, [string]$MarkedPng, $Markers, [int]$PublishWidth) {
    $bmp = [System.Drawing.Bitmap]::FromFile($RawPng)
    try {
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
        # Sized against the published width, so a marker keeps one size on every page after the downscale;
        # centred near the element's top-left corner, so it covers at most a corner of an icon.
        $radius = [Math]::Max(12, [int](12 * $bmp.Width / [Math]::Max(1, $PublishWidth)))
        $fill = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(235, 211, 47, 47))
        $ring = [System.Drawing.Pen]::new([System.Drawing.Color]::White, [single]([Math]::Max(3, $radius / 6)))
        $font = [System.Drawing.Font]::new('Segoe UI', [single]($radius * 1.05), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
        $format = [System.Drawing.StringFormat]::new()
        $format.Alignment = [System.Drawing.StringAlignment]::Center
        $format.LineAlignment = [System.Drawing.StringAlignment]::Center
        foreach ($m in $Markers) {
            $cx = [Math]::Min([Math]::Max($m.X1 + $radius / 3, $radius), $bmp.Width - $radius)
            $cy = [Math]::Min([Math]::Max($m.Y1 + $radius / 3, $radius), $bmp.Height - $radius)
            $rect = [System.Drawing.RectangleF]::new($cx - $radius, $cy - $radius, 2 * $radius, 2 * $radius)
            $g.FillEllipse($fill, $rect)
            $g.DrawEllipse($ring, $rect)
            $g.DrawString([string]$m.Number, $font, [System.Drawing.Brushes]::White, $rect, $format)
        }
        $g.Dispose()
        $bmp.Save($MarkedPng, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bmp.Dispose()
    }
}

# --- preconditions --------------------------------------------------------------------------------
if (-not (Test-Path $adbScript)) { Write-Fail "adb.ps1 not found at $adbScript"; exit 2 }
if (-not (Test-Path $mapPath)) { Write-Fail "help map not found at $mapPath"; exit 2 }
$ffmpeg = Resolve-Ffmpeg
if (-not $ffmpeg) { Write-Fail 'ffmpeg not found'; exit 2 }
if (-not ((& $ffmpeg -hide_banner -encoders 2>$null | Out-String) -match 'libwebp')) { Write-Fail 'ffmpeg has no libwebp encoder'; exit 2 }
try { Add-Type -AssemblyName System.Drawing } catch { Write-Fail "System.Drawing unavailable: $($_.Exception.Message)"; exit 2 }

$devices = Invoke-AdbVerb @('devices') -AllowFail
if (-not $devices -or -not $devices.ok -or @($devices.data | Where-Object { $_.state -eq 'ok' }).Count -eq 0) { Write-Fail 'no device online (adb.ps1 devices)'; exit 2 }

$map = Get-Content -Raw $mapPath | ConvertFrom-Json
$pageIds = @(Get-PageIds $map)
if ($Help) {
    $wanted = @($Help -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $unknown = @($wanted | Where-Object { $pageIds -notcontains $_ })
    if ($unknown.Count -gt 0) { Write-Fail "not a page of the help map: $($unknown -join ', ')"; exit 2 }
    $pageIds = $wanted
} else {
    # A full run captures what it can reach: a page whose screen needs an account, live data or a state the
    # script cannot set up has no recipe, and its help page is published without a screenshot section.
    $noRecipe = @($pageIds | Where-Object { -not $recipes.ContainsKey($_) })
    if ($noRecipe.Count -gt 0) { Write-Host "capture-help-screenshots: no recipe, left without a screenshot: $($noRecipe -join ', ')" -ForegroundColor Yellow }
    $pageIds = @($pageIds | Where-Object { $recipes.ContainsKey($_) })
}
$sources = @{}
foreach ($id in $pageIds) {
    $path = Join-Path $sourceRoot "$id.json"
    if (-not (Test-Path $path)) { Write-Fail "page source missing: $path"; exit 2 }
    if (-not $recipes.ContainsKey($id)) { Write-Fail "help '$id' has no capture recipe in this script"; exit 2 }
    $sources[$id] = Get-Content -Raw $path | ConvertFrom-Json
}

New-Item -ItemType Directory -Force $workDir, $OutRoot | Out-Null

# --- device state, read once and restored in finally ----------------------------------------------
$saved = @{
    Locales  = (Invoke-DeviceShell "cmd locale get-app-locales $Package") -replace '^.*are \[(.*)\]\s*$', '$1'
    Night    = (Invoke-DeviceShell 'cmd uimode night') -replace '^Night mode:\s*', ''
    AutoRot  = Invoke-DeviceShell 'settings get system accelerometer_rotation'
    UserRot  = Invoke-DeviceShell 'settings get system user_rotation'
}
# A screen that keeps animating never reaches the idle state the tree dump waits for (adb.ps1 exit 7), so the
# animation scales are zeroed for the run. 'null' means the key was never set and is deleted on the way back.
$animationKeys = @('animator_duration_scale', 'transition_animation_scale', 'window_animation_scale')
foreach ($key in $animationKeys) { $saved["Anim:$key"] = Invoke-DeviceShell "settings get global $key" }

$failures = [System.Collections.Generic.List[string]]::new()
$written = 0
try {
    if (-not $SkipSeed) {
        $seedArgs = if ($DeviceId) { @('-DeviceId', $DeviceId) } else { @() }
        & pwsh -NoProfile -File (Join-Path $repoRoot 'scripts/devtest/ui-sweep-seed.ps1') @seedArgs -Package $Package
        if ($LASTEXITCODE -ne 0) { throw "demo-content seed exited $LASTEXITCODE" }
    }

    foreach ($id in $pageIds) {
        if ($recipes[$id].LongClip) { Publish-LongClip $recipes[$id].LongClip }
    }

    Invoke-DeviceShell 'settings put system accelerometer_rotation 0' | Out-Null
    foreach ($key in $animationKeys) { Invoke-DeviceShell "settings put global $key 0" | Out-Null }
    Invoke-DeviceShell 'settings put global sysui_demo_allowed 1' | Out-Null
    $demo = 'am broadcast -a com.android.systemui.demo -e command'
    foreach ($c in @('enter', 'clock -e hhmm 1200', 'battery -e level 100 -e plugged false', 'notifications -e visible false')) {
        Invoke-DeviceShell "$demo $c" | Out-Null
    }

    foreach ($theme in $Themes) {
        Invoke-DeviceShell "cmd uimode night $(if ($theme -eq 'dark') { 'yes' } else { 'no' })" | Out-Null
        foreach ($locale in $Locales) {
            Invoke-DeviceShell "cmd locale set-app-locales $Package --locales $locale" | Out-Null
            foreach ($id in $pageIds) {
                $label = "$id-$locale-$theme"
                try {
                    $recipe = $recipes[$id]
                    Open-Surface $id $recipe $locale
                    if ($F1Proof) {
                        Invoke-DeviceShell 'input keyevent 131' | Out-Null
                        Start-Sleep -Seconds $SettleSeconds
                        $top = Invoke-DeviceShell 'dumpsys activity activities | grep -m1 topResumedActivity'
                        if ($top -notmatch 'HelpPageActivity') { throw "F1 did not open the help viewer, foreground: $top" }
                        Write-Host "F1 OK $label" -ForegroundColor Green
                        $written++
                        continue
                    }
                    $nodes = Get-FrameNodes
                    $shot = Invoke-AdbVerb @('shot')
                    $raw = [string]$shot.data.file
                    if (-not $raw -or -not (Test-Path $raw)) { throw "screenshot not written" }

                    $markers = [System.Collections.Generic.List[object]]::new()
                    $missing = [System.Collections.Generic.List[string]]::new()
                    $number = 0
                    foreach ($el in @($sources[$id].elements)) {
                        $number++
                        # An element with no view id (a row of a list, a Compose control, a dialog button) takes
                        # its number on the page but has no view on the tree to point at.
                        if (-not $el.viewId) { continue }
                        $node = @($nodes | Where-Object { $_.resIdShort -eq $el.viewId -and $_.x2 -gt $_.x1 -and $_.y2 -gt $_.y1 }) | Select-Object -First 1
                        if ($node) {
                            $markers.Add([pscustomobject]@{ Number = $number; X1 = [int]$node.x1; Y1 = [int]$node.y1 })
                        } elseif (-not $recipe.FirstFrameOnly -and @($recipe.Optional) -notcontains $el.id) {
                            $missing.Add("$($el.id) ($($el.viewId))")
                        }
                    }
                    if ($missing.Count -gt 0) {
                        # The frame that failed is kept: the list of missing ids alone does not say which screen was shot.
                        Copy-Item $raw (Join-Path $workDir "FAILED-$label.png") -Force
                        throw "element(s) missing from the frame: $($missing -join ', ')"
                    }
                    if ($markers.Count -eq 0) { throw 'no listed element is on the frame' }

                    $marked = Join-Path $workDir "$label.png"
                    $publishWidth = if ($recipe.Landscape) { $LandscapeWidth } else { $Width }
                    Add-Markers $raw $marked $markers $publishWidth
                    $target = Join-Path $OutRoot "$label.webp"
                    & $ffmpeg -loglevel error -y -i $marked -vf "scale=${publishWidth}:-2:flags=lanczos" -c:v libwebp -quality $Quality $target
                    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $target)) { throw 'ffmpeg webp encode failed' }
                    $kb = [int]((Get-Item $target).Length / 1KB)
                    Write-Host "CAPTURED $label ($($markers.Count) marker(s), $kb KB)" -ForegroundColor Green
                    $written++
                } catch {
                    $failures.Add("help '$id' [$locale/$theme]: $($_.Exception.Message)")
                    Write-Fail $failures[-1]
                }
            }
        }
    }
} catch {
    $failures.Add($_.Exception.Message)
    Write-Fail $_.Exception.Message
} finally {
    $restoreLocales = if ($saved.Locales) { $saved.Locales } else { '""' }
    Invoke-DeviceShell "cmd locale set-app-locales $Package --locales $restoreLocales" | Out-Null
    if ($saved.Night) { Invoke-DeviceShell "cmd uimode night $($saved.Night)" | Out-Null }
    if ($saved.UserRot) { Invoke-DeviceShell "settings put system user_rotation $($saved.UserRot)" | Out-Null }
    if ($saved.AutoRot) { Invoke-DeviceShell "settings put system accelerometer_rotation $($saved.AutoRot)" | Out-Null }
    foreach ($key in $animationKeys) {
        $was = $saved["Anim:$key"]
        if ($was -and $was -ne 'null') { Invoke-DeviceShell "settings put global $key $was" | Out-Null }
        else { Invoke-DeviceShell "settings delete global $key" | Out-Null }
    }
    Invoke-DeviceShell 'am broadcast -a com.android.systemui.demo -e command exit' | Out-Null
}

if ($F1Proof) {
    Write-Host "capture-help-screenshots: $written screen(s) answered F1 with the help viewer, $($failures.Count) failure(s)"
} else {
    Write-Host "capture-help-screenshots: $written image(s) written to $OutRoot, $($failures.Count) failure(s)"
}
if ($failures.Count -gt 0) { exit 1 }
exit 0
