#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Agent-driven device walks: stage a device, act, capture one frame per checkpoint, and check what
    the app did against the files on the device. The agent judges the frames; nothing here goes green.

.DESCRIPTION
    A Maestro flow answers "did the selectors resolve". A walk answers "did a person get what they
    asked for, and did it look right" - so the verdict is the agent's, taken from screenshots, and this
    script only removes the bookkeeping around that: one call per checkpoint instead of four, and the
    device's own filesystem as the ground truth the screen cannot give (a copy that reports Completed
    and wrote nothing, a delete that only hid the row).

    Scenarios are scripts/devtest/walks/<id>.md (format: scripts/devtest/walks/README.md). The agent
    reads one, then drives it with these verbs:

      list    scenario ids with their titles.
      begin   -Scenario a,b,c  stage the device and open a run. Generates the fixture corpus (eight
              files whose pixels name themselves, one Cyrillic text file, one clip when ffmpeg exists),
              pushes it to <root>/src with an empty <root>/dst beside it, hands it to MediaStore,
              grants the storage permissions, marks the first-run wizard complete, opens the device
              state journal, clears logcat and launches the app. -Fresh wipes app data first.
      act     one device action, no capture: -Tap <id> | -TapLabel <text> | -Field <id> -Text <v> |
              -Key <name> | -Scroll up|down  (-LongPress for the taps).
      step    -Name <slug> [action]  the action if given, a settle, then: node tree, screenshot, a
              digest of what is on screen (labels, the ids a tap can use, off-screen and undersized
              targets), the app's new warnings and errors, and any new crash file. Frames are numbered in run order.
              -System marks a checkpoint that is expected to leave the app (the folder picker, a
              permission dialog); without it a foreign foreground window is flagged.
      fs      -Path src|dst|<device dir>  list the files there with size and md5, each compared to the
              fixture. -Expect a,b  must be present. -Absent a,b  must be gone. -Count N  exact total.
              -Recurse descends.
      end     close the run: restore the journalled device state, list crash files, write report.md.
              -CleanDevice also removes the device root.

    The run (device serial, folder, counters) is remembered per device, so only `begin` needs a device
    or a scenario; with one run open the other verbs need no -DeviceId even when another emulator has
    come online, and with several open they ask for -DeviceId or -RunDir. A step prints its digest to the success stream and writes the same text, in UTF-8,
    beside the frame as NN_<name>.txt - read that file when the console shows Cyrillic as squares.

    Every device call goes through scripts/devtest/adb.ps1: a raw adb in a POSIX shell rewrites /sdcard
    paths. Run this script from the PowerShell tool for the same reason. Identify the device against
    docs/DEVICE_FLEET.md before `begin`; -Fresh is a one-way wipe of the app's data and needs -Yes.

.PARAMETER Verb
    list | begin | act | step | fs | end | help. Default help.

.PARAMETER Scenario
    begin: one id or a comma list, walked in that order in one run.

.PARAMETER DeviceId
    Target serial. begin resolves it when omitted and exactly one device is attached; later verbs read
    it from the run.

.PARAMETER Package
    App package. Default com.sza.fastmediasorter.debug.

.PARAMETER Root
    Device directory that holds src and dst. Default /sdcard/Download/FastMediaSorter_Walk - a fourth
    root beside the store-shot, manual and UI-sweep ones, so a walk never touches personal material.

.PARAMETER RunDir
    Run directory to use instead of the device's current run.

.PARAMETER Name
    step: checkpoint slug, used in the frame file names.

.PARAMETER Tap
    Resource id (short or full) to tap before the capture.

.PARAMETER TapLabel
    Text or content-description to tap before the capture. Prefer -Tap: a label is translated.

.PARAMETER Field
    Resource id of an input field; -Text is written into it and read back.

.PARAMETER Text
    Value for -Field. ASCII only (the same limit as adb.ps1 set-text).

.PARAMETER Key
    Key name for adb.ps1 key, e.g. BACK, HOME, ENTER.

.PARAMETER Scroll
    up | down | left | right: one swipe through the middle of the screen. down reveals what is below;
    left is the finger moving left, which pages a viewer to the next file.

.PARAMETER LongPress
    Make -Tap or -TapLabel a long press.

.PARAMETER Exact
    Match -Tap or -TapLabel as a whole value instead of a substring.

.PARAMETER Index
    Which match to tap when several nodes answer, counted from 1.

.PARAMETER Settle
    Milliseconds to wait between the action and the capture. Default 900.

.PARAMETER LabelCap
    step: how many labels the digest lists. Default 45; a longer screen says how many it left out.

.PARAMETER Bottom
    step: list the last labels of the screen instead of the first. It reads the screen as it is and
    does not scroll; pair it with -Scroll down to see the end of a list.

.PARAMETER Quick
    step: photograph first and read the tree after, for a message (a toast, an undo bar) that is gone
    before a settled tree read finishes. The tree may then describe a slightly later moment than the frame.

.PARAMETER Note
    step: free text recorded with the checkpoint in steps.jsonl.

.PARAMETER System
    step: the foreground is expected to be a system screen.

.PARAMETER Path
    fs: src, dst or an absolute device directory.

.PARAMETER Expect
    fs: comma list of names that must be present.

.PARAMETER Absent
    fs: comma list of names that must not be present.

.PARAMETER Count
    fs: the exact number of files expected.

.PARAMETER Recurse
    fs: include subdirectories.

.PARAMETER Fresh
    begin: wipe the app's data first. One-way; needs -Yes.

.PARAMETER Yes
    Confirms -Fresh.

.PARAMETER NoSeedApp
    begin: leave grants and the first-run wizard alone, for a scenario that walks them.

.PARAMETER NoVideo
    begin: do not generate the clip even when ffmpeg exists.

.PARAMETER Regenerate
    begin: rebuild the fixture corpus instead of reusing temp/scratch/walk_fixture.

.PARAMETER CleanDevice
    end: remove the device root.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/walk.ps1 begin -Scenario add-resources,copy-files -Fresh -Yes

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/walk.ps1 step -Name add.type-picker -Tap btnAddResource

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/walk.ps1 fs -Path dst -Expect walk_a01.jpg,walk_a02.jpg -Count 2

.NOTES
    Exit codes:
      0 - done; for step and fs, nothing flagged.
      1 - bad invocation: unknown verb or scenario, conflicting actions, -Fresh without -Yes.
      2 - could not proceed: no adb, no device or an ambiguous one, the app not installed, no open
          run for the device, or a required tool failed.
      3 - step captured its evidence and flagged it: the foreground is not the app (and -System was
          not given), a crash or not-responding dialog is up, or a new crash file appeared. The frame
          is still written - read it before anything else.
      4 - fs found a difference: a missing, unexpected or altered file, or a wrong count.
      5 - the action did not apply: no node answered to the id or label, or the field read back
          differently. The frame shows what was on screen instead.
      7 - a device command failed outright.
#>
param(
    [Parameter(Position = 0)]
    [string]$Verb = 'help',
    [string]$Scenario,
    [string]$DeviceId,
    [string]$Package = 'com.sza.fastmediasorter.debug',
    [string]$Root = '/sdcard/Download/FastMediaSorter_Walk',
    [string]$RunDir,
    [string]$Name,
    [string]$Tap,
    [string]$TapLabel,
    [string]$Field,
    [string]$Text,
    [string]$Key,
    [ValidateSet('up', 'down', 'left', 'right')]
    [string]$Scroll,
    [switch]$LongPress,
    [switch]$Exact,
    [int]$Index = 1,
    [int]$Settle = 900,
    [int]$LabelCap = 0,
    [switch]$Bottom,
    [switch]$Quick,
    [string]$Note,
    [switch]$System,
    [string]$Path,
    [string]$Expect,
    [string]$Absent,
    [int]$Count = -1,
    [switch]$Recurse,
    [switch]$Fresh,
    [switch]$Yes,
    [switch]$NoSeedApp,
    [switch]$NoVideo,
    [switch]$Regenerate,
    [switch]$CleanDevice
)

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$ErrorActionPreference = 'Stop'

$repoRoot   = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$adbScript  = Join-Path $repoRoot 'scripts/devtest/adb.ps1'
$walksDir   = Join-Path $repoRoot 'scripts/devtest/walks'
$scratch    = Join-Path $repoRoot 'temp/scratch'
$fixtureDir = Join-Path $scratch 'walk_fixture'

$MEDIA_PERMISSIONS_33 = @('android.permission.READ_MEDIA_IMAGES', 'android.permission.READ_MEDIA_VIDEO', 'android.permission.READ_MEDIA_AUDIO')
$MEDIA_PERMISSIONS_OLD = @('android.permission.READ_EXTERNAL_STORAGE', 'android.permission.WRITE_EXTERNAL_STORAGE')
# A walk is judged by a reader, so a long label list is noise past the first screenful.
$DIGEST_LABEL_CAP = 45
$MIN_TARGET_DP = 44
$CRASH_TEXT = 'DEBUG CRASH|isn.t responding|keeps stopping|has stopped|не отвечает|остановлен'

$script:Bound = $PSBoundParameters
$script:DevArgs = @()
if ($DeviceId) { $script:DevArgs = @('-DeviceId', $DeviceId) }
$script:AdbExit = 0

function Stop-Walk {
    param([int]$Code, [string]$Message)
    Write-Host $Message -ForegroundColor Red
    exit $Code
}

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $devArgs = $script:DevArgs
    $out = & pwsh -NoProfile -File $adbScript @devArgs @AdbArgs 2>&1
    $script:AdbExit = $LASTEXITCODE
    return @($out | ForEach-Object { $_.ToString() })
}

function Invoke-AdbJson {
    param([string[]]$AdbArgs)
    $lines = Invoke-Adb (@($AdbArgs) + '-Json')
    $json = $lines | Where-Object { $_ -match '^\{.*\}$' } | Select-Object -Last 1
    if (-not $json) { return $null }
    try { return ($json | ConvertFrom-Json) } catch { return $null }
}

function Invoke-DeviceShell {
    param([string]$Command)
    return (Invoke-Adb @('shell', '-Cmd', $Command))
}

function Get-SafeName {
    param([string]$Value)
    return ($Value -replace '[^A-Za-z0-9_.-]', '_')
}

function Get-PointerPath {
    param([string]$Serial)
    return (Join-Path $scratch ("walk_current_{0}.txt" -f (Get-SafeName $Serial)))
}

function Get-ScenarioIds {
    return @(Get-ChildItem -LiteralPath $walksDir -Filter '*.md' -File |
        Where-Object { $_.BaseName -ne 'README' } | Sort-Object Name | ForEach-Object { $_.BaseName })
}

function Get-ScenarioTitle {
    param([string]$Id)
    $first = Get-Content -LiteralPath (Join-Path $walksDir "$Id.md") -Encoding UTF8 | Where-Object { $_ -match '^# ' } | Select-Object -First 1
    if ($first) { return ($first -replace '^#\s+', '') }
    return $Id
}

function Read-ScenarioRequires {
    param([string]$Id)
    $line = Get-Content -LiteralPath (Join-Path $walksDir "$Id.md") -Encoding UTF8 | Where-Object { $_ -match '^- requires:\s*' } | Select-Object -First 1
    if (-not $line) { return @() }
    return @(($line -replace '^- requires:\s*', '') -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -ne 'none' })
}

function Open-Run {
    # Returns the run object for the device in -DeviceId, or the only device attached; exits 2 when
    # there is none. Sets the device arguments so later calls need no -DeviceId.
    $runDir = $RunDir
    if (-not $runDir) {
        # The run, not the attached devices, says which device this is: a second emulator that comes
        # online mid-walk (another session's) must not turn every later call into "no device resolved".
        $pointer = $null
        if ($DeviceId) { $pointer = Get-PointerPath $DeviceId }
        else {
            $open = @(Get-ChildItem -LiteralPath $scratch -Filter 'walk_current_*.txt' -File -ErrorAction SilentlyContinue)
            if ($open.Count -eq 1) { $pointer = $open[0].FullName }
            elseif ($open.Count -gt 1) {
                $names = ($open | ForEach-Object { $_.BaseName -replace '^walk_current_', '' }) -join ', '
                Stop-Walk 2 "several runs are open ($names) - pass -DeviceId <serial> or -RunDir <folder>"
            }
            else { Stop-Walk 2 'no open run - start one with: walk.ps1 begin -Scenario <id>' }
        }
        if (-not (Test-Path -LiteralPath $pointer)) { Stop-Walk 2 "no open run for $DeviceId - start one with: walk.ps1 begin -Scenario <id>" }
        $runDir = (Get-Content -LiteralPath $pointer -Raw -Encoding UTF8).Trim()
    }
    $runFile = Join-Path $runDir 'run.json'
    if (-not (Test-Path -LiteralPath $runFile)) { Stop-Walk 2 "run directory has no run.json: $runDir" }
    $obj = Get-Content -LiteralPath $runFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $script:DevArgs = @('-DeviceId', [string]$obj.device)
    return [pscustomobject]@{ Dir = $runDir; Data = $obj; File = $runFile }
}

function Save-Run {
    param($RunInfo)
    $RunInfo.Data | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $RunInfo.File -Encoding UTF8
}

function Get-CrashFiles {
    param([string]$Pkg)
    $out = Invoke-DeviceShell "run-as $Pkg ls files/logs"
    return @($out | Where-Object { $_ -match '^fastmediasorter_crash_.*\.log$' } | ForEach-Object { $_.Trim() })
}

function Get-WarnErrorLines {
    param([string]$Pkg)
    $out = Invoke-Adb @('log', '-Package', $Pkg, '-Tail', '4000', '-Grep', ' [WEF] ')
    return @($out | Where-Object { $_ -match '^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+\d+\s+\d+\s+[WEF]\s' })
}

# ---------------------------------------------------------------------------------------------------
# fixture corpus
# ---------------------------------------------------------------------------------------------------

function New-WalkImage {
    # Each image names itself in large type and carries a coloured square in every corner (red top-left,
    # green top-right, blue bottom-left, yellow bottom-right), so a viewer that crops, mirrors or
    # rotates the file shows it, and a copy that landed as another file shows the wrong name.
    param([string]$OutFile, [int]$Width, [int]$Height, [string]$Label, [string]$Sub, [int]$Hue, [string]$Format)
    Add-Type -AssemblyName System.Drawing
    $bmp = New-Object System.Drawing.Bitmap $Width, $Height
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    try {
        $g.SmoothingMode = 'AntiAlias'
        $g.TextRenderingHint = 'AntiAliasGridFit'
        $c1 = [System.Drawing.ColorTranslator]::FromHtml(('#{0:X2}{1:X2}{2:X2}' -f (60 + ($Hue * 37) % 120), (80 + ($Hue * 71) % 120), (110 + ($Hue * 53) % 120)))
        $c2 = [System.Drawing.ColorTranslator]::FromHtml(('#{0:X2}{1:X2}{2:X2}' -f (20 + ($Hue * 53) % 80), (30 + ($Hue * 37) % 80), (40 + ($Hue * 71) % 80)))
        $rect = New-Object System.Drawing.Rectangle 0, 0, $Width, $Height
        $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $c1, $c2, 45.0
        $g.FillRectangle($brush, $rect)
        $short = [Math]::Min($Width, $Height)
        $mark = [int]($short / 8)
        $g.FillRectangle([System.Drawing.Brushes]::Red, 0, 0, $mark, $mark)
        $g.FillRectangle([System.Drawing.Brushes]::Lime, $Width - $mark, 0, $mark, $mark)
        $g.FillRectangle([System.Drawing.Brushes]::Blue, 0, $Height - $mark, $mark, $mark)
        $g.FillRectangle([System.Drawing.Brushes]::Yellow, $Width - $mark, $Height - $mark, $mark, $mark)
        $fmt = New-Object System.Drawing.StringFormat
        $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
        $big = New-Object System.Drawing.Font 'Arial', ([single]($short / 6)), ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
        $small = New-Object System.Drawing.Font 'Arial', ([single]($short / 14)), ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
        $mid = [single]($Height / 2)
        $g.DrawString($Label, $big, [System.Drawing.Brushes]::Black, [single]($Width / 2 + 3), ($mid - $short / 12 + 3), $fmt)
        $g.DrawString($Label, $big, [System.Drawing.Brushes]::White, [single]($Width / 2), ($mid - $short / 12), $fmt)
        $g.DrawString($Sub, $small, [System.Drawing.Brushes]::White, [single]($Width / 2), ($mid + $short / 10), $fmt)
        $imgFormat = if ($Format -eq 'png') { [System.Drawing.Imaging.ImageFormat]::Png } else { [System.Drawing.Imaging.ImageFormat]::Jpeg }
        $bmp.Save($OutFile, $imgFormat)
    }
    finally {
        $g.Dispose(); $bmp.Dispose()
    }
}

function Find-Ffmpeg {
    if ($env:FMS_FFMPEG -and (Test-Path -LiteralPath $env:FMS_FFMPEG)) { return $env:FMS_FFMPEG }
    $cmd = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function New-WalkClip {
    param([string]$OutFile, [string]$Ffmpeg)
    $listing = (& $Ffmpeg -hide_banner -encoders 2>&1) -join "`n"
    $encoder = 'mpeg4'
    foreach ($candidate in @('libx264', 'libopenh264')) {
        if ($listing -match " $candidate ") { $encoder = $candidate; break }
    }
    & $Ffmpeg -y -loglevel error -f lavfi -i 'testsrc=duration=4:size=640x360:rate=30' -f lavfi -i 'sine=frequency=440:duration=4' `
        -c:v $encoder -b:v 1000k -pix_fmt yuv420p -c:a aac -shortest $OutFile
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $OutFile)) { return $false }
    # The default video size filter would otherwise hide the playback fixture.
    if ((Get-Item -LiteralPath $OutFile).Length -lt 102400) {
        throw 'Generated clip is smaller than the default video size minimum.'
    }
    return $true
}

function New-FixtureCorpus {
    param([bool]$WithVideo)
    $src = Join-Path $fixtureDir 'src'
    if (Test-Path -LiteralPath $fixtureDir) { Remove-Item -LiteralPath $fixtureDir -Recurse -Force }
    New-Item -ItemType Directory -Path $src -Force | Out-Null
    foreach ($i in 1..4) {
        New-WalkImage -OutFile (Join-Path $src ("walk_a0$i.jpg")) -Width 640 -Height 480 -Label "A0$i" -Sub "walk_a0$i.jpg  landscape 640x480" -Hue $i -Format jpg
    }
    foreach ($i in 1..2) {
        New-WalkImage -OutFile (Join-Path $src ("walk_b0$i.png")) -Width 480 -Height 640 -Label "B0$i" -Sub "walk_b0$i.png  portrait 480x640" -Hue (4 + $i) -Format png
    }
    New-WalkImage -OutFile (Join-Path $src 'walk_big.jpg') -Width 3000 -Height 2000 -Label 'BIG' -Sub 'walk_big.jpg  3000x2000' -Hue 9 -Format jpg
    $notes = "Walk notes`nLine two: Привет, мир`nLine three: the end`n"
    [System.IO.File]::WriteAllText((Join-Path $src 'walk_notes.txt'), $notes, [System.Text.UTF8Encoding]::new($false))
    $videoNote = 'no clip (-NoVideo)'
    if ($WithVideo) {
        $ffmpeg = Find-Ffmpeg
        if (-not $ffmpeg) { $videoNote = 'no clip (ffmpeg not found; set FMS_FFMPEG)' }
        elseif (New-WalkClip -OutFile (Join-Path $src 'walk_clip.mp4') -Ffmpeg $ffmpeg) { $videoNote = 'clip written' }
        else { $videoNote = 'no clip (ffmpeg failed)' }
    }
    $manifest = @(Get-ChildItem -LiteralPath $src -File | Sort-Object Name | ForEach-Object {
            [ordered]@{ name = $_.Name; bytes = $_.Length; md5 = (Get-FileHash -LiteralPath $_.FullName -Algorithm MD5).Hash.ToLowerInvariant() }
        })
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $fixtureDir 'fixture.json') -Encoding UTF8
    return $videoNote
}

function Read-Fixture {
    $file = Join-Path $fixtureDir 'fixture.json'
    if (-not (Test-Path -LiteralPath $file)) { return @() }
    return @(Get-Content -LiteralPath $file -Raw -Encoding UTF8 | ConvertFrom-Json)
}

# ---------------------------------------------------------------------------------------------------
# on-screen digest
# ---------------------------------------------------------------------------------------------------

function Read-TreeNodes {
    param([string]$TreeFile)
    $xml = [xml](Get-Content -LiteralPath $TreeFile -Raw -Encoding UTF8)
    $nodes = New-Object System.Collections.Generic.List[object]
    foreach ($n in $xml.SelectNodes('//node')) {
        $b = [regex]::Match($n.GetAttribute('bounds'), '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
        if (-not $b.Success) { continue }
        $text = $n.GetAttribute('text')
        $desc = $n.GetAttribute('content-desc')
        $resId = $n.GetAttribute('resource-id')
        $idMatch = [regex]::Match($resId, ':id/(.+)$')
        $short = if ($idMatch.Success) { $idMatch.Groups[1].Value } else { $resId }
        $nodes.Add([pscustomobject]@{
                Label = if ($text) { $text } else { $desc }
                Id = $short
                X1 = [int]$b.Groups[1].Value; Y1 = [int]$b.Groups[2].Value; X2 = [int]$b.Groups[3].Value; Y2 = [int]$b.Groups[4].Value
                Clickable = ($n.GetAttribute('clickable') -eq 'true')
                Enabled = ($n.GetAttribute('enabled') -ne 'false')
                Checked = ($n.GetAttribute('checked') -eq 'true')
                Selected = ($n.GetAttribute('selected') -eq 'true')
                Focused = ($n.GetAttribute('focused') -eq 'true')
            })
    }
    return $nodes
}

function Format-Digest {
    param([object[]]$Nodes, [int]$Density, [string]$Header, [int]$Cap = 0, [switch]$Bottom)
    if ($Cap -le 0) { $Cap = $DIGEST_LABEL_CAP }
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add($Header)
    # The first node is the window root, so its bounds are the screen. Taking the widest node instead
    # would make a node that overruns the edge define the edge, and nothing could ever be off it.
    $screenW = 0; $screenH = 0
    if ($Nodes.Count -gt 0) { $screenW = $Nodes[0].X2; $screenH = $Nodes[0].Y2 }
    # A text view and the tab or button that wraps it both carry the label; the inner one adds nothing.
    $labelled = New-Object System.Collections.Generic.List[object]
    $prev = $null
    foreach ($n in $Nodes) {
        if (-not $n.Label) { continue }
        if ($prev -and $prev.Label -eq $n.Label -and $n.X1 -ge $prev.X1 -and $n.X2 -le $prev.X2 -and $n.Y1 -ge $prev.Y1 -and $n.Y2 -le $prev.Y2) { continue }
        $labelled.Add($n)
        $prev = $n
    }
    $lines.Add("labels ($($labelled.Count) of $($Nodes.Count) nodes):")
    $hidden = [Math]::Max(0, $labelled.Count - $Cap)
    if ($Bottom -and $hidden -gt 0) { $lines.Add("  .. $hidden earlier label(s) not shown (-LabelCap $($labelled.Count) shows all)") }
    $shown = if ($Bottom) { $labelled | Select-Object -Last $Cap } else { $labelled | Select-Object -First $Cap }
    foreach ($n in $shown) {
        $mark = ''
        if ($n.Checked) { $mark += ' [checked]' }
        if ($n.Selected) { $mark += ' [selected]' }
        if ($n.Focused) { $mark += ' [focused]' }
        if (-not $n.Enabled) { $mark += ' [disabled]' }
        $label = ($n.Label -replace '\s+', ' ')
        if ($label.Length -gt 60) { $label = $label.Substring(0, 57) + '...' }
        $idPart = if ($n.Id) { "  <$($n.Id)>" } else { '' }
        $lines.Add(("  {0}{1}  {2},{3}-{4},{5}{6}" -f $label, $idPart, $n.X1, $n.Y1, $n.X2, $n.Y2, $mark))
    }
    if (-not $Bottom -and $hidden -gt 0) { $lines.Add("  .. +$hidden more below (-Bottom lists the end of the screen, -LabelCap $($labelled.Count) all of it)") }
    # Containers that carry an id but no text of their own (a card, a row) are tapped by id, and the
    # label list cannot show them.
    $targets = @($Nodes | Where-Object { $_.Clickable -and $_.Enabled -and $_.Id })
    if ($targets.Count -gt 0) {
        $lines.Add('tap by id:')
        $byId = @($targets | Group-Object Id)
        $listed = 0
        foreach ($g in $byId) {
            if ($listed -ge 30) { break }
            $first = $g.Group[0]
            $short = { param($s) $s = ($s -replace '\s+', ' '); if ($s.Length -gt 40) { $s.Substring(0, 37) + '...' } else { $s } }
            if ($g.Count -ge 2 -and $g.Count -le 20) {
                # A repeated id is a row or a cell per file, and the digit that -Index needs is the
                # position among every node with that id, so each instance is printed with it and with
                # the nearest label (the file name that sits beside a thumbnail).
                $everyone = @($Nodes | Where-Object { $_.Id -eq $first.Id -and ($_.X2 - $_.X1) -gt 0 -and ($_.Y2 - $_.Y1) -gt 0 })
                foreach ($t in $g.Group) {
                    $k = 1 + [array]::IndexOf($everyone, $t)
                    $cx = ($t.X1 + $t.X2) / 2; $cy = ($t.Y1 + $t.Y2) / 2
                    # A file name is what a row's buttons and thumbnail belong to, so a label that looks like one wins
                    # over a nearer button caption.
                    $candidates = @($labelled | Where-Object { $_.Id -ne $t.Id })
                    $named = @($candidates | Where-Object { $_.Label -match '[\w\-\(\)]+\.[A-Za-z0-9]{2,4}\b' })
                    if ($named.Count -gt 0) { $candidates = $named }
                    $near = $candidates |
                        Sort-Object { [Math]::Abs((($_.X1 + $_.X2) / 2) - $cx) + [Math]::Abs((($_.Y1 + $_.Y2) / 2) - $cy) } | Select-Object -First 1
                    $nameMatch = if ($near) { [regex]::Match($near.Label, '[\w\-\(\)]+\.[A-Za-z0-9]{2,4}\b').Value } else { '' }
                    $own = if ($nameMatch) { $nameMatch } elseif ($near) { $near.Label } elseif ($t.Label) { $t.Label } else { '' }
                    $ownPart = if ($own) { "  near '" + (& $short $own) + "'" } else { '' }
                    $lines.Add(("  {0} -Index {1}{2}  {3},{4}-{5},{6}" -f $t.Id, $k, $ownPart, $t.X1, $t.Y1, $t.X2, $t.Y2))
                }
                $listed++
                continue
            }
            $inside = $labelled | Where-Object { $_.X1 -ge $first.X1 -and $_.X2 -le $first.X2 -and $_.Y1 -ge $first.Y1 -and $_.Y2 -le $first.Y2 } | Select-Object -First 1
            $times = if ($g.Count -gt 1) { " x$($g.Count)" } else { '' }
            $what = if ($first.Label) { $first.Label } elseif ($inside) { $inside.Label } else { '' }
            $whatPart = if ($what) { "  '" + (& $short $what) + "'" } else { '' }
            $lines.Add(("  {0}{1}{2}  {3},{4}-{5},{6}" -f $first.Id, $times, $whatPart, $first.X1, $first.Y1, $first.X2, $first.Y2))
            $listed++
        }
        if ($byId.Count -gt $listed) { $lines.Add("  .. +$($byId.Count - $listed) more ids") }
    }
    $flags = New-Object System.Collections.Generic.List[string]
    if ($screenW -gt 0) {
        foreach ($n in ($labelled | Where-Object { $_.X1 -lt 0 -or $_.X2 -gt $screenW -or $_.Y1 -lt 0 -or $_.Y2 -gt $screenH })) {
            $flags.Add("off-screen: '$($n.Label)' <$($n.Id)>")
        }
    }
    if ($Density -gt 0) {
        $minPx = [int]($MIN_TARGET_DP * $Density / 160)
        # Under 16 dp on one side is a row sliced by a scroll edge, not a button someone drew that small.
        $sliverPx = [int](16 * $Density / 160)
        $small = @($Nodes | Where-Object {
                $_.Clickable -and $_.Enabled -and ($_.Label -or $_.Id) -and
                ([Math]::Min($_.X2 - $_.X1, $_.Y2 - $_.Y1) -ge $sliverPx) -and
                ([Math]::Min($_.X2 - $_.X1, $_.Y2 - $_.Y1) -lt $minPx)
            })
        # One line per id and size: a row of controls repeats an id and carries a different label each time.
        $groups = @($small | Group-Object {
                '{0}|{1}x{2}' -f $(if ($_.Id) { $_.Id } else { $_.Label }), [int](($_.X2 - $_.X1) * 160 / $Density), [int](($_.Y2 - $_.Y1) * 160 / $Density)
            })
        foreach ($g in ($groups | Select-Object -First 6)) {
            $n = $g.Group[0]
            $w = [int](($n.X2 - $n.X1) * 160 / $Density); $h = [int](($n.Y2 - $n.Y1) * 160 / $Density)
            $times = if ($g.Count -gt 1) { " x$($g.Count)" } else { '' }
            $flags.Add("small-target: '$($n.Label)' <$($n.Id)> ${w}x${h}dp$times")
        }
        if ($groups.Count -gt 6) { $flags.Add("small-target: .. +$($groups.Count - 6) more kinds") }
    }
    if (@($labelled | Where-Object { $_.Label -match $CRASH_TEXT }).Count -gt 0) { $flags.Add('crash-text: a crash or not-responding label is on screen') }
    if ($Nodes.Count -lt 3) { $flags.Add('thin-tree: fewer than 3 nodes - a secure window or a screen that is still drawing; trust the frame') }
    foreach ($f in $flags) { $lines.Add("FLAG $f") }
    return @{ Lines = $lines; Flags = $flags }
}

# ---------------------------------------------------------------------------------------------------
# actions
# ---------------------------------------------------------------------------------------------------

function Get-ActionArgs {
    $given = @()
    if ($Tap) { $given += 'Tap' }
    if ($TapLabel) { $given += 'TapLabel' }
    if ($Field) { $given += 'Field' }
    if ($Key) { $given += 'Key' }
    if ($Scroll) { $given += 'Scroll' }
    if ($given.Count -gt 1) { Stop-Walk 1 "one action per call, got: $($given -join ', ') - use act for the first and step for the second" }
    return $given
}

function Invoke-WalkAction {
    # Returns $null when there was no action, 'ok' or 'failed:<reason>'.
    $given = @(Get-ActionArgs)
    if ($given.Count -eq 0) { return $null }
    $press = @(); if ($LongPress) { $press += '-LongPress' }
    if ($Exact) { $press += '-Exact' }
    if ($script:Bound.ContainsKey('Index')) { $press += @('-Index', "$Index") }
    if ($Tap) { $out = Invoke-Adb (@('tap-id', '-ResourceId', $Tap) + $press) }
    elseif ($TapLabel) { $out = Invoke-Adb (@('tap-label', '-Label', $TapLabel) + $press) }
    elseif ($Field) {
        if (-not $script:Bound.ContainsKey('Text')) { Stop-Walk 1 '-Field needs -Text' }
        $out = Invoke-Adb @('set-text', '-ResourceId', $Field, '-Text', $Text)
    }
    elseif ($Key) { $out = Invoke-Adb @('key', '-Key', $Key) }
    else {
        $size = (Invoke-DeviceShell 'wm size') -join ' '
        if ($size -notmatch '(\d+)x(\d+)') { return 'failed:could not read the screen size' }
        $w = [int]$Matches[1]; $h = [int]$Matches[2]
        $x = [int]($w / 2); $y = [int]($h / 2)
        $x1 = $x; $y1 = $y; $x2 = $x; $y2 = $y
        # A page turn is a flick: a slow horizontal drag is below a pager's fling velocity and moves nothing.
        $dur = if ($Scroll -in @('left', 'right')) { 120 } else { 350 }
        switch ($Scroll) {
            'down' { $y1 = [int]($h * 0.72); $y2 = [int]($h * 0.28) }
            'up' { $y1 = [int]($h * 0.28); $y2 = [int]($h * 0.72) }
            'left' { $x1 = [int]($w * 0.78); $x2 = [int]($w * 0.22) }
            'right' { $x1 = [int]($w * 0.22); $x2 = [int]($w * 0.78) }
        }
        $out = Invoke-Adb @('swipe', '-X', "$x1", '-Y', "$y1", '-X2', "$x2", '-Y2', "$y2", '-Duration', "$dur")
    }
    if ($script:AdbExit -eq 0) { return 'ok' }
    $why = ($out | Where-Object { $_ -match '\S' } | Select-Object -Last 1)
    return "failed:exit $($script:AdbExit) $why"
}

# ---------------------------------------------------------------------------------------------------
# verbs
# ---------------------------------------------------------------------------------------------------

switch ($Verb) {
    'help' {
        Get-Help $PSCommandPath -Detailed | Out-String | Write-Output
        exit 0
    }

    'list' {
        foreach ($id in Get-ScenarioIds) { Write-Output ("{0,-22} {1}" -f $id, (Get-ScenarioTitle $id)) }
        exit 0
    }

    'begin' {
        if (-not $Scenario) { Stop-Walk 1 'begin needs -Scenario <id[,id..]> (walk.ps1 list shows them)' }
        $ids = @($Scenario -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        $known = Get-ScenarioIds
        foreach ($id in $ids) { if ($id -notin $known) { Stop-Walk 1 "unknown scenario '$id' - known: $($known -join ', ')" } }
        if ($Fresh -and -not $Yes) { Stop-Walk 1 '-Fresh wipes the app data on the device and is one-way: add -Yes after reading docs/DEVICE_FLEET.md for this device' }
        $seen = @()
        foreach ($id in $ids) {
            foreach ($need in (Read-ScenarioRequires $id)) {
                if ($need -notin $seen) { Write-Host "NOTE $id requires '$need' earlier in this run - its state is not there unless you walk it first" -ForegroundColor Yellow }
            }
            $seen += $id
        }

        $props = Invoke-AdbJson @('props')
        if (-not $props -or -not $props.device) { Stop-Walk 2 'no device resolved - pass -DeviceId, or attach exactly one device' }
        $serial = [string]$props.device
        $script:DevArgs = @('-DeviceId', $serial)

        $sdkRaw = (Invoke-DeviceShell 'getprop ro.build.version.sdk') -join ' '
        if ($sdkRaw -notmatch '(\d+)') { Stop-Walk 2 "device did not report an API level: $sdkRaw" }
        $sdk = [int]$Matches[1]
        $model = ((Invoke-DeviceShell 'getprop ro.product.model') -join ' ').Trim()
        $installed = (Invoke-DeviceShell "pm list packages $Package") -join "`n"
        if ($installed -notmatch [regex]::Escape("package:$Package")) {
            Stop-Walk 2 "$Package is not installed on $serial - install it first: adb.ps1 install -Apk <path> (the x86_64 debug APK for an emulator)"
        }

        $journal = Invoke-Adb @('state-begin')
        if ($script:AdbExit -ne 0) { Stop-Walk 7 "state-begin failed: $($journal -join ' ')" }
        foreach ($line in ($journal | Where-Object { $_ -match 'RESTORED' })) { Write-Host $line -ForegroundColor Yellow }

        if ($Fresh) {
            $null = Invoke-Adb @('wipe-data', '-Yes')
            if ($script:AdbExit -ne 0) { Stop-Walk 7 'wipe-data failed' }
        }

        $needCorpus = $Regenerate -or -not (Test-Path -LiteralPath (Join-Path $fixtureDir 'fixture.json'))
        $videoNote = 'corpus reused'
        if ($needCorpus) { $videoNote = New-FixtureCorpus -WithVideo (-not $NoVideo) }
        $fixture = Read-Fixture
        if ($fixture.Count -eq 0) { Stop-Walk 2 'the fixture corpus is empty' }

        $null = Invoke-DeviceShell "rm -rf '$Root' && mkdir -p '$Root/dst'"
        if ($script:AdbExit -ne 0) { Stop-Walk 7 "could not prepare $Root on the device" }
        $pushOut = Invoke-Adb @('push', '-Local', (Join-Path $fixtureDir 'src'), '-Remote', $Root)
        if ($script:AdbExit -ne 0) { Stop-Walk 7 "push failed: $($pushOut -join ' ')" }
        foreach ($f in $fixture) {
            $null = Invoke-DeviceShell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$Root/src/$($f.name)'"
        }

        if (-not $NoSeedApp) {
            $null = Invoke-DeviceShell "am force-stop $Package"
            $perms = if ($sdk -ge 33) { $MEDIA_PERMISSIONS_33 } else { $MEDIA_PERMISSIONS_OLD }
            foreach ($p in $perms) { $null = Invoke-DeviceShell "pm grant $Package $p" }
            $welcome = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>`n<map>`n    <boolean name=`"welcome_completed`" value=`"true`" />`n    <boolean name=`"first_run_after_welcome`" value=`"false`" />`n</map>`n"
            $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($welcome))
            $null = Invoke-DeviceShell "run-as $Package sh -c 'mkdir -p shared_prefs && echo $encoded | base64 -d > shared_prefs/welcome_prefs.xml'"
            $readBack = (Invoke-DeviceShell "run-as $Package cat shared_prefs/welcome_prefs.xml") -join ''
            if ($readBack -notmatch 'welcome_completed') { Stop-Walk 7 "could not mark the first-run wizard complete - is $Package a debuggable build?" }
        }

        $densityRaw = (Invoke-DeviceShell 'wm density') -join ' '
        $density = 0
        if ($densityRaw -match '(?:Override|Physical) density:\s*(\d+)') { $density = [int]$Matches[1] }
        $sizeRaw = (Invoke-DeviceShell 'wm size') -join ' '
        $size = if ($sizeRaw -match '(\d+x\d+)\s*$') { $Matches[1] } elseif ($sizeRaw -match '(\d+x\d+)') { $Matches[1] } else { '' }
        $versionRaw = (Invoke-DeviceShell "dumpsys package $Package | grep -m1 versionName") -join ' '
        $version = if ($versionRaw -match 'versionName=(\S+)') { $Matches[1] } else { 'unknown' }

        $stamp = Get-Date -Format 'yyyyMMdd_HHmm'
        $runDir = Join-Path $scratch "walk_$stamp"
        if (Test-Path -LiteralPath $runDir) { $runDir = Join-Path $scratch ("walk_{0}_{1}" -f $stamp, (Get-Date -Format 'ss')) }
        New-Item -ItemType Directory -Path $runDir -Force | Out-Null
        $runObj = [ordered]@{
            scenarios = $ids; device = $serial; model = $model; sdk = $sdk; density = $density; size = $size
            package = $Package; app = $version; root = $Root; started = (Get-Date).ToUniversalTime().ToString('o')
            steps = 0; logSeen = 0; crashBaseline = @(Get-CrashFiles $Package); fresh = [bool]$Fresh
        }
        $runInfo = [pscustomobject]@{ Dir = $runDir; Data = ([pscustomobject]$runObj); File = (Join-Path $runDir 'run.json') }
        Save-Run $runInfo
        Copy-Item -LiteralPath (Join-Path $fixtureDir 'fixture.json') -Destination (Join-Path $runDir 'fixture.json') -Force
        New-Item -ItemType Directory -Path $scratch -Force | Out-Null
        Set-Content -LiteralPath (Get-PointerPath $serial) -Value $runDir -Encoding UTF8

        $null = Invoke-Adb @('logcat-clear')
        $null = Invoke-Adb @('launch', '-Package', $Package)
        Start-Sleep -Milliseconds 2500

        Write-Host ("WALK BEGIN {0}  device {1} ({2}, API {3}, {4} @ {5}dpi)  app {6}" -f ($ids -join ' > '), $serial, $model, $sdk, $size, $density, $version) -ForegroundColor Green
        Write-Output "run: $runDir"
        Write-Output "device root: $Root  (src: $($fixture.Count) file(s), dst: empty; $videoNote)"
        Write-Output 'next: walk.ps1 step -Name <scenario>.<checkpoint>   (the app is already launched)'
        exit 0
    }

    'act' {
        $runInfo = Open-Run
        if (@(Get-ActionArgs).Count -eq 0) { Stop-Walk 1 'act needs an action: -Tap, -TapLabel, -Field, -Key or -Scroll' }
        $result = Invoke-WalkAction
        if ($result -ne 'ok') { Write-Host "ACT $result" -ForegroundColor Red; exit 5 }
        Write-Host 'ACT ok' -ForegroundColor Green
        exit 0
    }

    'step' {
        if (-not $Name) { Stop-Walk 1 'step needs -Name <slug>' }
        $runInfo = Open-Run
        $run = $runInfo.Data
        $pkg = [string]$run.package
        $actionResult = Invoke-WalkAction
        Start-Sleep -Milliseconds $Settle

        $n = [int]$run.steps + 1
        $run.steps = $n
        $slug = (Get-SafeName $Name)
        $base = ('{0:D2}_{1}' -f $n, $slug)
        $frame = Join-Path $runInfo.Dir "$base.png"
        $treeOut = Join-Path $runInfo.Dir "$base.tree.xml"
        $digestFile = Join-Path $runInfo.Dir "$base.txt"

        $exit = 0
        # The tree first, then the frame: the dump waits until two reads agree, so the screen has
        # settled by the time it is photographed. The other order let a card that arrived late sit in
        # the tree and be absent from the frame, and the digest described a screen the frame did not show.
        $window = ''
        $shot = $null
        if ($Quick) { $shot = Invoke-AdbJson @('shot', '-OutDir', $runInfo.Dir) }
        $dump = Invoke-AdbJson @('uidump', '-OutDir', $runInfo.Dir)
        if ($dump -and $dump.data.file -and (Test-Path -LiteralPath $dump.data.file)) {
            Move-Item -LiteralPath $dump.data.file -Destination $treeOut -Force
            $window = [string]$dump.data.window
        }
        if (-not $Quick) { $shot = Invoke-AdbJson @('shot', '-OutDir', $runInfo.Dir) }
        $secure = $false
        if ($shot -and $shot.data.file -and (Test-Path -LiteralPath $shot.data.file)) {
            Move-Item -LiteralPath $shot.data.file -Destination $frame -Force
            $secure = [bool]$shot.data.secureWindow
            if (-not (Test-Path -LiteralPath $treeOut) -and $shot.data.treeFile -and (Test-Path -LiteralPath $shot.data.treeFile)) {
                Move-Item -LiteralPath $shot.data.treeFile -Destination $treeOut -Force
            }
            if ([string]$shot.data.window) { $window = [string]$shot.data.window }
        }
        else { $frame = $null }
        $nodes = @()
        if (Test-Path -LiteralPath $treeOut) { $nodes = @(Read-TreeNodes $treeOut) }

        $header = "STEP $('{0:D2}' -f $n) $Name  window=$window  frame=$(if ($frame) { Split-Path -Leaf $frame } else { 'MISSING' })"
        $digest = Format-Digest -Nodes $nodes -Density ([int]$run.density) -Header $header -Cap $LabelCap -Bottom:$Bottom
        $lines = $digest.Lines
        $flags = New-Object System.Collections.Generic.List[string]
        foreach ($f in $digest.Flags) { $flags.Add($f) }

        if ($actionResult) {
            $lines.Insert(1, "action: $actionResult")
            if ($actionResult -ne 'ok') { $exit = 5; $lines.Add("FLAG action: $actionResult") }
        }
        if (-not $frame) { $lines.Insert(1, 'frame: the screenshot failed - the tree is the only evidence'); $exit = [Math]::Max($exit, 7) }
        if ($secure) { $lines.Insert(1, 'secure-window: FLAG_SECURE is up, the frame is expected to be black; the tree is the evidence') }
        # A popup menu is the app's own window but is named without the package.
        if ($window -and $window -notmatch [regex]::Escape($pkg) -and $window -notmatch '^PopupWindow' -and -not $System) {
            $lines.Add("FLAG foreground: '$window' is not $pkg (pass -System when a system screen is the expected checkpoint)")
            if ($exit -eq 0) { $exit = 3 }
        }
        if ($flags | Where-Object { $_ -like 'crash-text:*' }) { if ($exit -eq 0) { $exit = 3 } }

        $crashNow = @(Get-CrashFiles $pkg)
        $newCrash = @($crashNow | Where-Object { $_ -notin @($run.crashBaseline) })
        if ($newCrash.Count -gt 0) {
            $lines.Add("FLAG crash-file: $($newCrash -join ', ') under files/logs (pull with: run-as $pkg cat files/logs/<name>)")
            if ($exit -eq 0) { $exit = 3 }
        }

        # What is new is judged by the line's own timestamp, not by how many lines were seen before: the
        # log window is a tail, so a count slides as old lines fall out and re-reported old errors as new.
        $allLog = @(Get-WarnErrorLines $pkg)
        foreach ($extra in @('logLastTs', 'logLastLines')) {
            if (-not $run.PSObject.Properties[$extra]) { $run | Add-Member -NotePropertyName $extra -NotePropertyValue $(if ($extra -eq 'logLastTs') { '' } else { @() }) }
        }
        $lastTs = [string]$run.logLastTs
        $lastLines = @($run.logLastLines)
        $newLog = @($allLog | Where-Object {
                $ts = [regex]::Match($_, '^\d\d-\d\d \d\d:\d\d:\d\d\.\d+').Value
                $ts -and ((-not $lastTs) -or ($ts -gt $lastTs) -or (($ts -eq $lastTs) -and ($_ -notin $lastLines)))
            })
        if ($allLog.Count -gt 0) {
            $tsAll = @($allLog | ForEach-Object { [regex]::Match($_, '^\d\d-\d\d \d\d:\d\d:\d\d\.\d+').Value } | Where-Object { $_ })
            if ($tsAll.Count -gt 0) {
                $maxTs = ($tsAll | Sort-Object | Select-Object -Last 1)
                $run.logLastTs = $maxTs
                $run.logLastLines = @($allLog | Where-Object { $_.StartsWith($maxTs) })
            }
        }
        $bad = @($newLog | Where-Object { $_ -match '^\S+\s+\S+\s+\d+\s+\d+\s+[EF]\s' })
        $warn = @($newLog | Where-Object { $_ -match '^\S+\s+\S+\s+\d+\s+\d+\s+W\s' })
        if ($newLog.Count -gt 0) { Add-Content -LiteralPath (Join-Path $runInfo.Dir 'warnings.log') -Value (@("== step $n $Name") + $newLog) -Encoding UTF8 }
        $lines.Add("log: $($bad.Count) new error(s), $($warn.Count) new warning(s)" + $(if ($newLog.Count -gt 0) { '  (warnings.log)' } else { '' }))
        foreach ($l in ($bad | Select-Object -First 8)) {
            $short = if ($l.Length -gt 200) { $l.Substring(0, 197) + '...' } else { $l }
            $lines.Add("  $short")
        }
        if ($warn.Count -gt 0) {
            $tags = @($warn | ForEach-Object {
                    $m = [regex]::Match($_, '^\S+\s+\S+\s+\d+\s+\d+\s+W\s+(.+?)\s*:')
                    if ($m.Success) { $m.Groups[1].Value } else { '?' }
                } | Group-Object | Sort-Object Count -Descending | Select-Object -First 5)
            $lines.Add('  warnings by tag: ' + (($tags | ForEach-Object { "$($_.Name) x$($_.Count)" }) -join ', '))
        }

        ($lines -join "`n") | Set-Content -LiteralPath $digestFile -Encoding UTF8
        $record = [ordered]@{
            n = $n; name = $Name; utc = (Get-Date).ToUniversalTime().ToString('o'); action = $actionResult; window = $window
            nodes = $nodes.Count; flags = @($lines | Where-Object { $_ -like 'FLAG *' } | ForEach-Object { $_.Substring(5) }); frame = $(if ($frame) { Split-Path -Leaf $frame } else { $null })
            errors = $bad.Count; warnings = $warn.Count; note = $Note; exit = $exit
        }
        Add-Content -LiteralPath (Join-Path $runInfo.Dir 'steps.jsonl') -Value ($record | ConvertTo-Json -Compress -Depth 4) -Encoding UTF8
        Save-Run $runInfo

        foreach ($l in $lines) { Write-Output $l }
        exit $exit
    }

    'fs' {
        if (-not $Path) { Stop-Walk 1 'fs needs -Path src|dst|<device dir>' }
        $runInfo = Open-Run
        $deviceRoot = [string]$runInfo.Data.root
        $dir = switch ($Path) { 'src' { "$deviceRoot/src" } 'dst' { "$deviceRoot/dst" } default { $Path } }
        $depth = if ($Recurse) { '' } else { '-maxdepth 1 ' }
        $sizes = Invoke-DeviceShell "find '$dir' $depth-type f -exec stat -c '%s|%n' {} +"
        if (($sizes -join ' ') -match 'No such file') {
            Write-Output "FS ${dir}: directory does not exist"
            if ($Expect -or $Count -ge 0) { exit 4 }
            exit 0
        }
        $sums = Invoke-DeviceShell "find '$dir' $depth-type f -exec md5sum {} +"
        $sizeByPath = @{}
        foreach ($l in $sizes) { if ($l -match '^(\d+)\|(.+)$') { $sizeByPath[$Matches[2].Trim()] = [int64]$Matches[1] } }
        $md5ByPath = @{}
        foreach ($l in $sums) { if ($l -match '^([0-9a-f]{32})\s+(.+)$') { $md5ByPath[$Matches[2].Trim()] = $Matches[1] } }
        $fixture = @(Get-Content -LiteralPath (Join-Path $runInfo.Dir 'fixture.json') -Raw -Encoding UTF8 | ConvertFrom-Json)
        $byName = @{}; $byMd5 = @{}
        foreach ($f in $fixture) { $byName[[string]$f.name] = $f; $byMd5[[string]$f.md5] = $f }

        $problem = 0
        $names = @()
        Write-Output "FS ${dir}: $($sizeByPath.Count) file(s)"
        foreach ($p in ($sizeByPath.Keys | Sort-Object)) {
            $leaf = $p.Substring($p.LastIndexOf('/') + 1)
            $names += $leaf
            $md5 = if ($md5ByPath.ContainsKey($p)) { $md5ByPath[$p] } else { '?' }
            $verdict = if ($byName.ContainsKey($leaf)) {
                $fx = $byName[$leaf]
                if ($md5 -eq [string]$fx.md5) { 'same-as-fixture' }
                elseif ($sizeByPath[$p] -ne [int64]$fx.bytes) { $problem++; 'SIZE-DIFFERS' }
                else { $problem++; 'BYTES-DIFFER' }
            }
            elseif ($byMd5.ContainsKey($md5)) { "same bytes as $($byMd5[$md5].name) (renamed)" }
            else { 'not-in-fixture' }
            $shown = $p.Substring($dir.TrimEnd('/').Length).TrimStart('/')
            Write-Output ("  {0,-34} {1,10}  {2}  {3}" -f $shown, $sizeByPath[$p], $md5.Substring(0, [Math]::Min(8, $md5.Length)), $verdict)
        }
        if ($Expect) {
            foreach ($e in ($Expect -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
                if ($e -notin $names) { Write-Output "  MISSING $e"; $problem++ }
            }
        }
        if ($Absent) {
            foreach ($a in ($Absent -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
                if ($a -in $names) { Write-Output "  STILL-PRESENT $a"; $problem++ }
            }
        }
        if ($Count -ge 0 -and $names.Count -ne $Count) { Write-Output "  COUNT expected $Count, found $($names.Count)"; $problem++ }
        if ($problem -gt 0) { Write-Host "FS $problem problem(s)" -ForegroundColor Red; exit 4 }
        Write-Host 'FS ok' -ForegroundColor Green
        exit 0
    }

    'end' {
        $runInfo = Open-Run
        $run = $runInfo.Data
        $pkg = [string]$run.package
        $stateOut = Invoke-Adb @('state-check')
        $stateExit = $script:AdbExit
        $crashNow = @(Get-CrashFiles $pkg)
        $newCrash = @($crashNow | Where-Object { $_ -notin @($run.crashBaseline) })
        $stepsFile = Join-Path $runInfo.Dir 'steps.jsonl'
        $steps = @()
        if (Test-Path -LiteralPath $stepsFile) { $steps = @(Get-Content -LiteralPath $stepsFile -Encoding UTF8 | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json }) }
        # Only a hard signal counts as flagged: an undersized-target note is on nearly every screen and would drown the rest.
        $flagged = @($steps | Where-Object { $_.errors -gt 0 -or $_.exit -ne 0 })
        $report = New-Object System.Collections.Generic.List[string]
        $report.Add("# Walk report - $(@($run.scenarios) -join ' > ')")
        $report.Add('')
        $report.Add("- device: $($run.device) ($($run.model), API $($run.sdk), $($run.size) @ $($run.density)dpi)")
        $report.Add("- app: $($run.package) $($run.app)")
        $report.Add("- run folder: $($runInfo.Dir)")
        $report.Add("- state check: $(if ($stateExit -eq 0) { 'clean' } else { "exit $stateExit - " + ($stateOut -join ' ') })")
        $report.Add("- crash files from this run: $(if ($newCrash.Count -gt 0) { $newCrash -join ', ' } else { 'none' })")
        $report.Add('')
        $report.Add('## Frames')
        foreach ($s in $steps) {
            $mark = if ($s.errors -gt 0 -or $s.exit -ne 0) { ' - FLAGGED' } else { '' }
            $report.Add(("- {0:D2} {1}{2}: {3}" -f $s.n, $s.name, $mark, $s.frame))
            foreach ($f in @($s.flags)) { $report.Add("  - $f") }
            if ($s.errors -gt 0) { $report.Add("  - $($s.errors) logged error(s)") }
        }
        $report.Add('')
        $report.Add('## Verdict')
        $report.Add('- (one line per scenario: OK, or FINDINGS with the count)')
        $report.Add('')
        $report.Add('## Findings')
        $report.Add('- (per finding: scenario and step, what was seen, what was expected, frame file; then the ticket id it was parked as)')
        $reportFile = Join-Path $runInfo.Dir 'report.md'
        ($report -join "`n") | Set-Content -LiteralPath $reportFile -Encoding UTF8
        if ($CleanDevice) { $null = Invoke-DeviceShell "rm -rf '$($run.root)'" }
        Remove-Item -LiteralPath (Get-PointerPath ([string]$run.device)) -Force -ErrorAction SilentlyContinue
        Write-Host ("WALK END {0} frame(s), {1} flagged, {2} new crash file(s), state {3}" -f $steps.Count, $flagged.Count, $newCrash.Count, $(if ($stateExit -eq 0) { 'clean' } else { "exit $stateExit" })) -ForegroundColor Green
        Write-Output "report: $reportFile"
        if ($stateExit -ne 0) { exit 7 }
        exit 0
    }

    default {
        Stop-Walk 1 "unknown verb '$Verb' (list, begin, act, step, fs, end, help)"
    }
}
