#requires -Version 7.0
<#
.SYNOPSIS
    Load and growth cycle scenes of the S4166 dynamic-audit stand: C05, C06, C12, C14, C16 and the cycle runner.

.DESCRIPTION
    Dot-source after lib/da-scenes.ps1, which holds the scene registry these functions are named in, the
    scene context, Invoke-DaSceneAction and the tree invariants. Functions only; nothing is assigned at script
    scope. Kept apart from da-scenes.ps1 so neither file passes the 2000-line ceiling.

    QUIESCENCE. Every cycle ends at a defined quiescence point before its resources are read: the main
    screen with no dialog and 3 s without input (C05, C06, C12, C14), the end of the idle window itself with
    the list open or behind the launcher (C16), the watch app in front on a watch. One sample per point:
    Get-DaSampleRow with dumpsys meminfo and the run-as descriptor count; no garbage collection is forced.

    TIMINGS. A screen change is observed through UI-tree reads (adb.ps1 uidump, about 1 to 2 s each), so
    open, sort, filter and search times are upper bounds at that granularity; the tap itself is one input
    call on a node read a moment before, never a remembered coordinate. A playing video refuses a tree dump,
    so C06 observes playback through the focus, the media session state, frame statistics and the sampler.
#>

# ------------------------------------------------------------------- load and growth: shared cycle helpers

function Get-DaLaunchComponent {
    <#
    .SYNOPSIS
        Launcher component of the installed package (phone or watch build), listed on the device; falls back to the phone main activity.
    .DESCRIPTION
        The debug build carries a second launcher entry (LeakCanary), so resolving the launcher intent opens the
        system chooser (run 261013-113343-e942 cold-started into ResolverActivity); the package's own entries are
        listed instead and the LeakCanary one is skipped.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package)
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'cmd', 'package', 'query-activities', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER', $Package)
    $line = @(@($r.Lines) | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_.StartsWith("$Package/") -and $_ -notmatch 'leakcanary' } | Select-Object -First 1)
    if ($line) { return [string]$line[0] }
    return Get-DaMainComponent -Package $Package
}

function Invoke-DaTapNode {
    <#
    .SYNOPSIS
        Taps the centre of a node read from a tree a moment ago with one input call, so a timing can start right before the tap; returns the host monotonic time of the call.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Node
        A node of Get-DaUiTree (x1, y1, x2, y2).
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)]$Node)
    $x = [int](([int]$Node.x1 + [int]$Node.x2) / 2); $y = [int](([int]$Node.y1 + [int]$Node.y2) / 2)
    $t0 = Get-DaMonotonicMs
    $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'input', 'tap', [string]$x, [string]$y)
    return $t0
}

function ConvertFrom-DaResourceHeader {
    <#
    .SYNOPSIS
        File count of a browse header such as 'dst (1 000 files) - content://..'; $null when the header carries none. Pure.
    .PARAMETER Text
        tvResourceInfo label.
    #>
    param([string]$Text)
    if ($Text -match '\((\d[\d\s  ,.]*)\s+files?\)') { return [int](($Matches[1]) -replace '\D', '') }
    return $null
}

function Get-DaExpectedVisible {
    <#
    .SYNOPSIS
        Splits a fixture manifest into the files the browse list should show and the ones the default size filter hides (images below 1 KiB, videos below 100 KiB). Pure.
    .DESCRIPTION
        The size filter is an accepted product setting (AppSettings imageSizeMin 1024, videoSizeMin 102400), so a
        hidden fixture file is expected, not a finding; anything else missing from the count is.
    .PARAMETER Manifest
        Fixture manifest (items with path and size).
    .PARAMETER ImageMin
        Smallest listed image in bytes.
    .PARAMETER VideoMin
        Smallest listed video in bytes.
    #>
    param([Parameter(Mandatory)]$Manifest, [long]$ImageMin = 1024, [long]$VideoMin = 102400)
    $visible = [System.Collections.Generic.List[string]]::new()
    $hidden = [System.Collections.Generic.List[string]]::new()
    foreach ($it in @($Manifest.items)) {
        $ext = [System.IO.Path]::GetExtension([string]$it.path).ToLowerInvariant()
        $min = if ($ext -in '.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp') { $ImageMin } elseif ($ext -in '.mp4', '.mkv', '.webm', '.3gp', '.mov') { $VideoMin } else { 0 }
        if ([long]$it.size -lt $min) { $hidden.Add("$($it.path) ($($it.size) B < $min B)") } else { $visible.Add([string]$it.path) }
    }
    return [pscustomobject]@{ Visible = $visible.ToArray(); Hidden = $hidden.ToArray() }
}

function Measure-DaResourceOpen {
    <#
    .SYNOPSIS
        Opens a resource from the main list and times, from the tap, the browse list and the header carrying the final file count; returns {ok; listMs; countMs; count; header; reads; reason}.
    .DESCRIPTION
        Resolution is one UI-tree read through adb.ps1 (about one to two seconds), so the times are upper bounds
        with that granularity; reads is the number of tree reads it took. The count is final when no progress
        panel is shown.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER ResourceName
        Resource name as listed on the main screen.
    .PARAMETER TimeoutMs
        Limit from the tap.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$ResourceName, [int]$TimeoutMs = 180000)
    $fail = { param($why) [ordered]@{ ok = $false; listMs = $null; countMs = $null; count = $null; header = $null; reads = 0; reason = $why } }
    if (-not (Open-DaAppMain -Serial $Serial -Package $Package)) { return (& $fail 'main screen not reached') }
    $tree = Find-DaUiNodeScrolling -Serial $Serial -Id 'tvResourceName' -Label $ResourceName -Exact -ContainerId 'rvResources'
    if (-not $tree) { return (& $fail "resource '$ResourceName' not listed") }
    $node = @(Find-DaUiNode -Tree $tree -Id 'tvResourceName' -Label $ResourceName -Exact) | Select-Object -First 1
    $t0 = Invoke-DaTapNode -Serial $Serial -Node $node
    $listMs = $null; $reads = 0
    while ((Get-DaMonotonicMs) - $t0 -lt $TimeoutMs) {
        $t = Get-DaUiTree -Serial $Serial
        $reads++
        $now = Get-DaMonotonicMs
        if (-not $t.Ok) { Start-Sleep -Milliseconds 300; continue }
        if (@(Find-DaUiNode -Tree $t -Id 'alertTitle').Count -and @(Find-DaUiNode -Tree $t -Id 'button2').Count -and -not @(Find-DaUiNode -Tree $t -Id 'rvMediaFiles').Count) { $null = Invoke-DaUiTap -Serial $Serial -Id 'button2'; continue }
        $list = @(Find-DaUiNode -Tree $t -Id 'rvMediaFiles').Count -gt 0
        $empty = @(Find-DaUiNode -Tree $t -Id 'tvEmptyStateMessage').Count -gt 0
        if (($list -or $empty) -and $null -eq $listMs) { $listMs = $now - $t0 }
        $header = [string](@(Find-DaUiNode -Tree $t -Id 'tvResourceInfo') | Select-Object -First 1).label
        $count = ConvertFrom-DaResourceHeader -Text $header
        $busy = @(Find-DaUiNode -Tree $t -Id 'layoutProgress').Count -gt 0 -or @(Find-DaUiNode -Tree $t -Id 'progressBar').Count -gt 0
        if (($list -or $empty) -and $null -ne $count -and -not $busy) {
            return [ordered]@{ ok = $true; listMs = $listMs; countMs = $now - $t0; count = $count; header = ($header -replace ' .*content://.*$', ''); reads = $reads; reason = $null }
        }
    }
    return [ordered]@{ ok = $false; listMs = $listMs; countMs = $null; count = $null; header = $null; reads = $reads; reason = "no final count within $TimeoutMs ms" }
}

function Wait-DaQuiescence {
    <#
    .SYNOPSIS
        Brings the app to a scene's quiescence point, waits with no input and reads one cycle-point sample; returns {point; reached; quietMs; alive; pid; startTicks; sample}.
    .DESCRIPTION
        Points: main (the main screen in front with no dialog), browse (the open resource's list in front),
        background (the app not in front after HOME), watch (the watch app in front). The sample is one
        Get-DaSampleRow with dumpsys meminfo and the run-as descriptor count. No garbage collection is forced.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Point
        main, browse, background or watch.
    .PARAMETER QuietMs
        No-input wait before the sample.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][ValidateSet('main', 'browse', 'background', 'watch')][string]$Point, [int]$QuietMs = 3000)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $reached = switch ($Point) {
        'main' { Open-DaAppMain -Serial $s -Package $p }
        'browse' { ((Get-DaForeground -Serial $s -Package $p) -match 'BrowseActivity') -or (Open-DaResource -Serial $s -Package $p -Name $Ctx.Resources.Src) }
        'background' {
            if ((Get-DaForeground -Serial $s -Package $p) -like "$p/*") { $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_HOME'); Start-Sleep -Milliseconds 800 }
            -not ((Get-DaForeground -Serial $s -Package $p) -like "$p/*")
        }
        'watch' {
            if (-not ((Get-DaForeground -Serial $s -Package $p) -like "$p/*")) {
                $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
                $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'am', 'start', '-W', '-n', (Get-DaLaunchComponent -Serial $s -Package $p)) -TimeoutMs 30000
                Start-Sleep -Milliseconds 800
            }
            (Get-DaForeground -Serial $s -Package $p) -like "$p/*"
        }
    }
    if ($QuietMs -gt 0) { Start-Sleep -Milliseconds $QuietMs }
    $id = Get-DaProcessIdentity -Serial $s -Package $p
    if (-not $id) { return [ordered]@{ point = $Point; reached = [bool]$reached; quietMs = $QuietMs; alive = $false; pid = $null; startTicks = $null; sample = $null } }
    $row = Get-DaSampleRow -Serial $s -ProcessId $id.pid -StartTicks $id.startTicks -Package $p -RunAs -WithMeminfo
    return [ordered]@{ point = $Point; reached = [bool]$reached; quietMs = $QuietMs; alive = $true; pid = $id.pid; startTicks = $id.startTicks; sample = $row }
}

function Get-DaCyclePhase {
    <#
    .SYNOPSIS
        Phase of a 1-based cycle index: warm-up for the first WarmupCycles, measured after. Pure.
    .PARAMETER Index
        1-based cycle index.
    .PARAMETER WarmupCycles
        Length of the explicit warm-up part.
    #>
    param([Parameter(Mandatory)][int]$Index, [int]$WarmupCycles = 0)
    if ($Index -le $WarmupCycles) { return 'warm-up' }
    return 'measured'
}

function Get-DaCycleSegment {
    <#
    .SYNOPSIS
        Segment of a cycle for scenes whose window has two halves (C14 n1000 then n10000, C16 foreground then background); 'main' otherwise. Pure.
    .PARAMETER Scene
        Scene id.
    .PARAMETER Index
        1-based cycle index.
    .PARAMETER Cycles
        Cycle count of the run.
    .PARAMETER Watch
        The subject is the watch build.
    #>
    param([Parameter(Mandatory)][string]$Scene, [Parameter(Mandatory)][int]$Index, [Parameter(Mandatory)][int]$Cycles, [switch]$Watch)
    $half = [int][math]::Ceiling($Cycles / 2.0)
    if ($Scene -eq 'C14') { if ($Index -le $half) { return 'n1000' } else { return 'n10000' } }
    if ($Scene -eq 'C16' -and -not $Watch) { if ($Index -le $half) { return 'foreground' } else { return 'background' } }
    return 'main'
}

function Add-DaRemoteSet {
    <#
    .SYNOPSIS
        Pushes a generated fixture set's files into one side (src or dst) of the run directory; returns {Side; Set; Count; PushMs}.
    .PARAMETER Ctx
        Scene context with a published fixture.
    .PARAMETER Set
        n1000 or n10000.
    .PARAMETER Side
        src or dst.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][ValidateSet('n1000', 'n10000')][string]$Set, [ValidateSet('src', 'dst')][string]$Side = 'dst')
    $manifest = New-DaFixtureSet -Name $Set
    $target = "$($Ctx.Fixture.runRemoteDir)/$Side"
    if (-not (Test-DaPathInside -Root $Ctx.Fixture.runRemoteDir -Path $target)) { throw "refused: $target is outside the run directory" }
    $local = Join-Path (Join-Path (Join-Path (Get-DaPaths).Fixtures $Set) 'files') '.'
    $push = Invoke-DaAdb -Serial $Ctx.Serial -Arguments @('push', $local, $target) -TimeoutMs 1800000
    if ($push.ExitCode -ne 0) { throw "adb push of $Set into $Side failed: $($push.Stderr)" }
    return [ordered]@{ Side = $Side; Set = $Set; Count = [int]$manifest.count; PushMs = $push.ElapsedMs }
}

function Add-DaPlaybackClip {
    <#
    .SYNOPSIS
        Encodes (once, on the host) a deterministic 10 s 640x360 clip above the 100 KiB video size filter and pushes it into the run's src; returns {Path; Size; Listed}.
    .DESCRIPTION
        The small set's clips are 2 to 70 KiB and the default video size filter hides everything below 100 KiB,
        so the playback scene brings its own clip instead of changing a setting it would then have to restore.
    .PARAMETER Ctx
        Scene context with a published fixture.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    $dir = Join-Path (Get-DaPaths).Fixtures 'playback'
    [void][System.IO.Directory]::CreateDirectory($dir)
    $local = Join-Path $dir 'clip_play_10s.mp4'
    if (-not (Test-Path -LiteralPath $local)) {
        $ff = Get-DaFfmpegPath
        if (-not $ff) { throw 'ffmpeg not found; the playback clip cannot be encoded' }
        if (-not (New-DaFixtureVideo -Ffmpeg $ff -Path $local -Source 'testsrc2=size=640x360:rate=25:duration=10')) { throw 'ffmpeg failed to encode the playback clip' }
    }
    $size = (Get-Item -LiteralPath $local).Length
    $remote = "$($Ctx.Fixture.src)/clip_play_10s.mp4"
    if (-not (Test-DaPathInside -Root $Ctx.Fixture.runRemoteDir -Path $remote)) { throw "refused: $remote is outside the run directory" }
    $push = Invoke-DaAdb -Serial $Ctx.Serial -Arguments @('push', $local, $remote) -TimeoutMs 300000
    if ($push.ExitCode -ne 0) { throw "adb push of the playback clip failed: $($push.Stderr)" }
    return [ordered]@{ Path = 'src/clip_play_10s.mp4'; Size = $size; Listed = ($size -ge 102400) }
}

function Get-DaMediaSessionState {
    <#
    .SYNOPSIS
        Playback state of the package's media session from dumpsys media_session (playing, paused, stopped ..), or $null when it publishes none.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package)
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'dumpsys', 'media_session') -TimeoutMs 15000
    $text = @($r.Lines) -join "`n"
    $i = $text.IndexOf("package=$Package")
    if ($i -lt 0) { return $null }
    $tail = $text.Substring($i, [math]::Min(4000, $text.Length - $i))
    if ($tail -match 'state=PlaybackState \{state=(\w+)') { return $Matches[1] }
    if ($tail -match 'state=PlaybackState \{state=(\d+)') { return $Matches[1] }
    return $null
}

function Get-DaDisplaySize {
    <#
    .SYNOPSIS
        Physical display size from wm size; returns {Width; Height}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    #>
    param([Parameter(Mandatory)][string]$Serial)
    $text = @((Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'wm', 'size')).Lines) -join ' '
    $m = [regex]::Matches($text, '(\d+)x(\d+)')
    if (-not $m.Count) { return [pscustomobject]@{ Width = $null; Height = $null } }
    $last = $m[$m.Count - 1]
    return [pscustomobject]@{ Width = [int]$last.Groups[1].Value; Height = [int]$last.Groups[2].Value }
}

function Open-DaBrowseFile {
    <#
    .SYNOPSIS
        Opens a file of the open browse list in the player by tapping its name row; returns {ok; openMs; foreground; reason}.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER FileName
        File name as listed.
    .PARAMETER UiAfter
        Read the player's tree after the open (images and documents only: a playing video refuses a tree dump).
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$FileName, [switch]$UiAfter)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $tree = Find-DaUiNodeScrolling -Serial $s -Id 'tvFileName' -Label $FileName -Exact -ContainerId 'rvMediaFiles' -MaxSwipes 10
    if (-not $tree) { return [ordered]@{ ok = $false; openMs = $null; foreground = $null; reason = "'$FileName' is not in the list" } }
    $node = @(Find-DaUiNode -Tree $tree -Id 'tvFileName' -Label $FileName -Exact) | Select-Object -First 1
    $t0 = Invoke-DaTapNode -Serial $s -Node $node
    $ms = Wait-DaForegroundMatch -Serial $s -Package $p -Pattern 'PlayerActivity' -FromMonoMs $t0 -TimeoutMs 15000
    $fg = Get-DaForeground -Serial $s -Package $p
    $overlay = $null
    if ($UiAfter -and $ms) {
        $t = Wait-DaUiNode -Serial $s -Id 'tvFileNameOverlay' -TimeoutMs 8000
        if ($t) { $overlay = [string](@(Find-DaUiNode -Tree $t -Id 'tvFileNameOverlay') | Select-Object -First 1).label }
    }
    return [ordered]@{ ok = ($null -ne $ms); openMs = $ms; foreground = $fg; overlay = $overlay; reason = if ($ms) { $null } else { "the player did not take the focus; foreground $fg" } }
}

function Wait-DaForegroundMatch {
    <#
    .SYNOPSIS
        Milliseconds until the focused window matches a pattern, or $null at the limit.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER Pattern
        Regex on package/activity.
    .PARAMETER FromMonoMs
        Start of the measurement.
    .PARAMETER TimeoutMs
        Limit.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$Pattern, [long]$FromMonoMs, [int]$TimeoutMs = 10000)
    if ($FromMonoMs -le 0) { $FromMonoMs = Get-DaMonotonicMs }
    while ((Get-DaMonotonicMs) - $FromMonoMs -lt $TimeoutMs) {
        if ((Get-DaForeground -Serial $Serial -Package $Package) -match $Pattern) { return (Get-DaMonotonicMs) - $FromMonoMs }
        Start-Sleep -Milliseconds 150
    }
    return $null
}

# ------------------------------------------------------------------------------------------------- C05

function Initialize-DaSceneC05 {
    <#
    .SYNOPSIS
        C05 setup: the small set in src, the n1000 set in dst (the large folder), both registered.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    if (-not (Initialize-DaOpsScene -Ctx $Ctx)) { return $false }
    $Ctx.LargeSet = Invoke-DaSceneAction -Ctx $Ctx -Name 'push-n1000-into-dst' -Atomic 'C05.open-large-folder' -Body { Add-DaRemoteSet -Ctx $Ctx -Set n1000 -Side dst }
    return [bool]$Ctx.LargeSet
}

function Invoke-DaSortStep {
    <#
    .SYNOPSIS
        Chooses one sort order from the sort popup and times, from the tap, the first change of the list's first name; returns {ok; order; sortMs; firstBefore; firstAfter; options}.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Order
        Popup entry label.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][string]$Order)
    $s = $Ctx.Serial
    $before = Get-DaUiTree -Serial $s
    $first = [string]@(Get-DaUiLabels -Tree $before -Id 'tvFileName')[0]
    $null = Invoke-DaUiTap -Serial $s -Id 'btnSort'
    $menu = Wait-DaUiNode -Serial $s -Id 'title' -Label $Order -Exact -TimeoutMs 8000
    if (-not $menu) { return [ordered]@{ ok = $false; order = $Order; sortMs = $null; firstBefore = $first; firstAfter = $null; options = @(); reason = 'sort popup or entry not shown' } }
    $options = @(Get-DaUiLabels -Tree $menu -Id 'title')
    $node = @(Find-DaUiNode -Tree $menu -Id 'title' -Label $Order -Exact) | Select-Object -First 1
    $t0 = Invoke-DaTapNode -Serial $s -Node $node
    $after = $first; $ms = $null
    while ((Get-DaMonotonicMs) - $t0 -lt 20000) {
        $t = Get-DaUiTree -Serial $s
        if ($t.Ok) {
            $names = @(Get-DaUiLabels -Tree $t -Id 'tvFileName')
            if ($names.Count -and [string]$names[0] -ne $first) { $after = [string]$names[0]; $ms = (Get-DaMonotonicMs) - $t0; break }
        }
        Start-Sleep -Milliseconds 200
    }
    return [ordered]@{ ok = ($null -ne $ms); order = $Order; sortMs = $ms; firstBefore = $first; firstAfter = $after; options = $options; reason = if ($ms) { $null } else { 'the first name did not change within 20 s' } }
}

function Invoke-DaSceneCycleC05 {
    <#
    .SYNOPSIS
        One C05 cycle: open the 1000-item folder, change the sort order, apply and clear a type filter, search by name, switch to the other resource.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Index
        1-based cycle index.
    .PARAMETER Segment
        Cycle segment.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][int]$Index, [string]$Segment)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $timings = [ordered]@{}
    $open = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-open-large-folder" -Atomic 'C05.open-large-folder' -Body { Measure-DaResourceOpen -Serial $s -Package $p -ResourceName $Ctx.Resources.Dst }
    if ($open) { $timings.openLargeListMs = $open.listMs; $timings.openLargeCountMs = $open.countMs }
    if ($Index -eq 1 -and $open) { Add-DaInvariant -Ctx $Ctx -Id 'large-folder-lists-every-item' -Atomic 'C05.open-large-folder' -Holds ([int]$open.count -eq [int]$Ctx.LargeSet.Count) -Detail "header count $($open.count) of $($Ctx.LargeSet.Count) pushed; list after $($open.listMs) ms, final count after $($open.countMs) ms ($($open.reads) tree reads)" }
    if (-not $open -or -not $open.ok) { $Ctx.Notes.Add("cycle ${Index}: the large folder did not open: $(if ($open) { $open.reason })"); return [ordered]@{ timings = $timings; ok = $false } }
    $orders = @('Name (Z-A)', 'Size (Large first)', 'Date (New first)', 'Name (A-Z)')
    $sort = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-sort" -Atomic 'C05.sort' -Body { Invoke-DaSortStep -Ctx $Ctx -Order $orders[($Index - 1) % $orders.Count] }
    if ($sort) { $timings.sortMs = $sort.sortMs }
    $filter = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-filter" -Atomic 'C05.filter' -Body {
        $null = Invoke-DaUiTap -Serial $s -Id 'btnFilter'
        $dlg = Wait-DaUiNode -Serial $s -Id 'cbFilterImage' -TimeoutMs 8000
        if (-not $dlg) { throw 'filter dialog not shown' }
        $null = Invoke-DaUiTap -Serial $s -Id 'cbFilterImage'
        $apply = @(Find-DaUiNode -Tree (Get-DaUiTree -Serial $s) -Id 'btnApplyFilter') | Select-Object -First 1
        $t0 = Invoke-DaTapNode -Serial $s -Node $apply
        $hideMs = $null
        while ((Get-DaMonotonicMs) - $t0 -lt 20000) {
            $t = Get-DaUiTree -Serial $s
            if ($t.Ok -and (@(Find-DaUiNode -Tree $t -Id 'tvEmptyStateMessage').Count -or -not @(Get-DaUiLabels -Tree $t -Id 'tvFileName').Count)) { $hideMs = (Get-DaMonotonicMs) - $t0; break }
            Start-Sleep -Milliseconds 200
        }
        $null = Invoke-DaUiTap -Serial $s -Id 'btnFilter'
        $dlg2 = Wait-DaUiNode -Serial $s -Id 'btnClearFilter' -TimeoutMs 8000
        if (-not $dlg2) { throw 'filter dialog did not reopen' }
        $clear = @(Find-DaUiNode -Tree $dlg2 -Id 'btnClearFilter') | Select-Object -First 1
        $t1 = Invoke-DaTapNode -Serial $s -Node $clear
        Start-Sleep -Milliseconds 600
        $still = Get-DaUiTree -Serial $s
        if ($still.Ok -and @(Find-DaUiNode -Tree $still -Id 'btnApplyFilter').Count) { $null = Invoke-DaUiTap -Serial $s -Id 'btnApplyFilter' }
        $showMs = $null; $count = $null
        while ((Get-DaMonotonicMs) - $t1 -lt 20000) {
            $t = Get-DaUiTree -Serial $s
            if ($t.Ok -and @(Get-DaUiLabels -Tree $t -Id 'tvFileName').Count) { $showMs = (Get-DaMonotonicMs) - $t1; $count = ConvertFrom-DaResourceHeader -Text ([string](@(Find-DaUiNode -Tree $t -Id 'tvResourceInfo') | Select-Object -First 1).label); break }
            Start-Sleep -Milliseconds 200
        }
        [ordered]@{ hideMs = $hideMs; showMs = $showMs; countAfterClear = $count }
    }
    if ($filter) {
        $timings.filterApplyMs = $filter.hideMs; $timings.filterClearMs = $filter.showMs
        if ($Index -eq 1) { Add-DaInvariant -Ctx $Ctx -Id 'cleared-filter-restores-the-list' -Atomic 'C05.filter' -Holds ($null -ne $filter.showMs -and ($null -eq $filter.countAfterClear -or [int]$filter.countAfterClear -eq [int]$Ctx.LargeSet.Count)) -Detail "images hidden after $($filter.hideMs) ms; list back after $($filter.showMs) ms with count $($filter.countAfterClear)" }
    }
    $search = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-search" -Atomic 'C05.search' -Body {
        $null = Invoke-DaUiTap -Serial $s -Id 'btnSearch'
        if (-not (Wait-DaUiNode -Serial $s -Id 'etSearchQuery' -TimeoutMs 8000)) { throw 'search field not shown' }
        $t0 = Get-DaMonotonicMs
        $st = Set-DaUiText -Serial $s -Id 'etSearchQuery' -Text 'tiny_0050'
        $ms = $null; $names = @()
        while ((Get-DaMonotonicMs) - $t0 -lt 20000) {
            $t = Get-DaUiTree -Serial $s
            $names = if ($t.Ok) { @(Get-DaUiLabels -Tree $t -Id 'tvFileName') } else { @() }
            if ($names.Count -and -not @($names | Where-Object { $_ -notlike '*tiny_0050*' }).Count) { $ms = (Get-DaMonotonicMs) - $t0; break }
            Start-Sleep -Milliseconds 200
        }
        $null = Invoke-DaUiTap -Serial $s -Id 'btnSearchClose'
        Start-Sleep -Milliseconds 600
        [ordered]@{ typed = $st.Ok; searchMs = $ms; shown = $names }
    }
    if ($search) {
        $timings.searchMs = $search.searchMs
        if ($Index -eq 1) { Add-DaInvariant -Ctx $Ctx -Id 'search-narrows-to-matching-names' -Atomic 'C05.search' -Holds ($null -ne $search.searchMs) -Detail "'tiny_0050' typed (read back $($search.typed)); visible after $($search.searchMs) ms (includes the set-text call): $(@($search.shown) -join ', ')" }
    }
    $null = Invoke-DaUiTap -Serial $s -Id 'btnBack'
    $switch = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-change-source" -Atomic 'C05.change-source' -Body { Measure-DaResourceOpen -Serial $s -Package $p -ResourceName $Ctx.Resources.Src }
    if ($switch) { $timings.changeSourceCountMs = $switch.countMs }
    return [ordered]@{ timings = $timings; ok = [bool]($switch -and $switch.ok) }
}

# ------------------------------------------------------------------------------------------------- C06

function Initialize-DaSceneC06 {
    <#
    .SYNOPSIS
        C06 setup: the small set in src plus a playback clip above the video size filter, registered.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    if (-not (Initialize-DaOpsScene -Ctx $Ctx)) { return $false }
    $Ctx.Clip = Invoke-DaSceneAction -Ctx $Ctx -Name 'push-playback-clip' -Atomic 'C06.play-video' -Body { Add-DaPlaybackClip -Ctx $Ctx }
    $Ctx.LastTree = Get-DaRunTree -Serial $Ctx.Serial -RunRemoteDir $Ctx.Fixture.runRemoteDir
    return $true
}

function Invoke-DaSceneCycleC06 {
    <#
    .SYNOPSIS
        One C06 cycle: open an image, page with swipes and the Next button, back; play the clip with pause, play, seek forward and back, stop, back. The playing player refuses a tree dump, so playback is observed through the focus, the media session state, the frame statistics and the sampler.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Index
        1-based cycle index.
    .PARAMETER Segment
        Cycle segment.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][int]$Index, [string]$Segment)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $timings = [ordered]@{}
    if (-not (Open-DaResource -Serial $s -Package $p -Name $Ctx.Resources.Src)) { $Ctx.Notes.Add("cycle ${Index}: src did not open"); return [ordered]@{ timings = $timings; ok = $false } }
    $img = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-view-image" -Atomic 'C06.view-image' -Body { Open-DaBrowseFile -Ctx $Ctx -FileName 'IMG_0006.jpg' -UiAfter }
    if ($img) { $timings.openImageMs = $img.openMs }
    if ($img -and $img.ok) {
        $page = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-page-swipe" -Atomic 'C06.page-swipe' -Body {
            $t = Get-DaUiTree -Serial $s
            $view = @(Find-DaUiNode -Tree $t -Id 'photoView') + @(Find-DaUiNode -Tree $t -Id 'mediaContentArea') | Select-Object -First 1
            if (-not $view) { throw 'no photo view in the player tree' }
            $y = [int](([int]$view.y1 + [int]$view.y2) / 2)
            $xa = [int]([int]$view.x1 + ([int]$view.x2 - [int]$view.x1) * 0.8); $xb = [int]([int]$view.x1 + ([int]$view.x2 - [int]$view.x1) * 0.2)
            # The overlay hides itself a moment after a page change, so each read is one tree dump, not a wait.
            $readPos = {
                $tt = Get-DaUiTree -Serial $s
                $lbl = if ($tt.Ok) { [string](@(Find-DaUiNode -Tree $tt -Id 'tvFileNameOverlay') | Select-Object -First 1).label } else { '' }
                [pscustomobject]@{ label = $lbl; pos = if ($lbl -match '\((\d+)/\d+\)') { [int]$Matches[1] } else { $null } }
            }
            $start = if ([string]$img.overlay -match '\((\d+)/\d+\)') { [int]$Matches[1] } else { $null }
            $seen = [System.Collections.Generic.List[string]]::new()
            $seen.Add([string]$img.overlay)
            foreach ($k in 1..2) {
                $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'swipe', [string]$xa, [string]$y, [string]$xb, [string]$y, '250')
                Start-Sleep -Milliseconds 1200
                $seen.Add((& $readPos).label)
            }
            $null = Invoke-DaUiTap -Serial $s -Id 'btnNextCmd'
            Start-Sleep -Milliseconds 1200
            $after = & $readPos
            $seen.Add($after.label)
            [ordered]@{ overlays = $seen.ToArray(); startPos = $start; endPos = $after.pos }
        }
        if ($Index -eq 1 -and $page) {
            $moved = if ($null -ne $page.startPos -and $null -ne $page.endPos) { $page.endPos - $page.startPos } else { $null }
            Add-DaInvariant -Ctx $Ctx -Id 'next-button-pages-forward' -Atomic 'C06.page-swipe' -Holds ($null -ne $moved -and $moved -ge 1) -Detail "position $($page.startPos) -> $($page.endPos) after two swipes and one Next; overlays: $(@($page.overlays) -join ' -> ')"
            Add-DaInvariant -Ctx $Ctx -Id 'two-swipes-page-two-items' -Atomic 'C06.page-swipe' -Holds ($null -ne $moved -and $moved -ge 3) -Detail "position moved by $moved for two right-to-left swipes across the photo plus one Next (expected 3); an empty overlay means it was hidden at the read"
        }
        $t0 = Get-DaMonotonicMs
        $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        $timings.backToListMs = Wait-DaForegroundMatch -Serial $s -Package $p -Pattern 'BrowseActivity' -FromMonoMs $t0 -TimeoutMs 10000
    }
    $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'dumpsys', 'gfxinfo', $p, 'reset')
    $play = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-play-video" -Atomic 'C06.play-video' -Body {
        $m0 = Get-DaMonotonicMs
        $o = Open-DaBrowseFile -Ctx $Ctx -FileName 'clip_play_10s.mp4'
        if (-not $o.ok) { throw $o.reason }
        $findAndOpenMs = (Get-DaMonotonicMs) - $m0
        Start-Sleep -Milliseconds 2000
        $states = [ordered]@{ playing = Get-DaMediaSessionState -Serial $s -Package $p }
        foreach ($step in @(@('pause', 'KEYCODE_MEDIA_PAUSE'), @('play', 'KEYCODE_MEDIA_PLAY'), @('fast-forward', 'KEYCODE_MEDIA_FAST_FORWARD'), @('rewind', 'KEYCODE_MEDIA_REWIND'), @('stop', 'KEYCODE_MEDIA_STOP'))) {
            $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', $step[1])
            Start-Sleep -Milliseconds 1000
            $states[$step[0]] = Get-DaMediaSessionState -Serial $s -Package $p
        }
        $frames = Get-DaFrameStats -Serial $s -Package $p -Reset
        $t1 = Get-DaMonotonicMs
        $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        $back = Wait-DaForegroundMatch -Serial $s -Package $p -Pattern 'BrowseActivity' -FromMonoMs $t1 -TimeoutMs 10000
        [ordered]@{ openMs = $o.openMs; findAndOpenMs = $findAndOpenMs; transportMs = $t1 - $m0 - $findAndOpenMs; sessionStates = $states; frames = [ordered]@{ total = $frames.totalFrames; janky = $frames.jankyFrames; p95Ms = $frames.p95Ms; over100Ms = $frames.framesOver100Ms }; backToListMs = $back }
    }
    if ($play) {
        $timings.openVideoMs = $play.openMs; $timings.videoBackMs = $play.backToListMs
        if ($Index -eq 1) {
            Add-DaInvariant -Ctx $Ctx -Id 'video-opens-and-returns' -Atomic 'C06.play-video' -Holds ($null -ne $play.openMs -and $null -ne $play.backToListMs) -Detail "player focus $($play.openMs) ms after the tap; list back $($play.backToListMs) ms after Back; frames $($play.frames.total) ($($play.frames.janky) janky, $($play.frames.over100Ms) over 100 ms)"
            $st = $play.sessionStates
            if ($null -eq $st.playing) { Add-DaOpen -Ctx $Ctx -Atomic 'C06.transport-controls' -Reason 'the player publishes no media session, so pause, seek and stop cannot be observed from outside the process without a tree dump' -Observed 'dumpsys media_session lists no session of the package' }
            else { Add-DaInvariant -Ctx $Ctx -Id 'media-keys-change-the-playback-state' -Atomic 'C06.transport-controls' -Holds ([string]$st.pause -ne [string]$st.playing -and [string]$st.play -eq [string]$st.playing) -Detail (@($st.Keys | ForEach-Object { "$_=$($st[$_])" }) -join ', ') }
        }
    }
    foreach ($a in 'C06.play-gif', 'C06.open-document', 'C06.edit-text', 'C06.play-audio', 'C06.play-stream', 'C06.slideshow', 'C06.draw-annotate', 'C06.view-vr') {
        if ($Index -eq 1) { Add-DaOpen -Ctx $Ctx -Atomic $a -Reason 'not driven by the first-pass cycle scene (image paging and video transport only); the fixture carries no GIF, PDF, audio or stream' -Observed '' }
    }
    return [ordered]@{ timings = $timings; ok = [bool]($play) }
}

# ------------------------------------------------------------------------------------------------- C12

function Invoke-DaSceneCycleC12 {
    <#
    .SYNOPSIS
        One C12 cycle on the phone: a held-key burst, repeated Refresh taps, rapid view switching and rapid open and close of one file, each sent in one shell call without waiting, then a key probe of the screen's reaction. On the watch: a swipe and key storm, then the probe.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Index
        1-based cycle index.
    .PARAMETER Segment
        Cycle segment.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][int]$Index, [string]$Segment)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $timings = [ordered]@{}
    $pidBefore = (Get-DaProcessIdentity -Serial $s -Package $p)
    if ($Ctx.Watch) {
        $storm = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-watch-key-storm" -Atomic 'C12.watch-key-storm' -Body {
            $d = Get-DaDisplaySize -Serial $s
            $x = [int]($d.Width / 2); $ya = [int]($d.Height * 0.75); $yb = [int]($d.Height * 0.25)
            $cmd = ((1..10 | ForEach-Object { "input swipe $x $ya $x $yb 80; input swipe $x $yb $x $ya 80" }) + @('input keyevent ' + ((1..20 | ForEach-Object { '20' }) -join ' '))) -join '; '
            $t0 = Get-DaMonotonicMs
            $null = Invoke-DaAdb -Serial $s -Arguments @('shell', $cmd) -TimeoutMs 120000
            [ordered]@{ stormMs = (Get-DaMonotonicMs) - $t0; foreground = Get-DaForeground -Serial $s -Package $p }
        }
        if ($storm) { $timings.stormMs = $storm.stormMs }
    }
    else {
        if (-not ((Get-DaForeground -Serial $s -Package $p) -match 'BrowseActivity') -and -not (Open-DaResource -Serial $s -Package $p -Name $Ctx.Resources.Src)) { $Ctx.Notes.Add("cycle ${Index}: src did not open"); return [ordered]@{ timings = $timings; ok = $false } }
        $held = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-held-key" -Atomic 'C12.held-key' -Body {
            $t0 = Get-DaMonotonicMs
            $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input keyevent ' + ((1..40 | ForEach-Object { '20' }) -join ' ') + '; input keyevent ' + ((1..40 | ForEach-Object { '19' }) -join ' ')) -TimeoutMs 120000
            [ordered]@{ burstMs = (Get-DaMonotonicMs) - $t0; foreground = Get-DaForeground -Serial $s -Package $p }
        }
        if ($held) { $timings.heldKeyBurstMs = $held.burstMs }
        $t = Get-DaUiTree -Serial $s
        $centre = { param($id, $label) $n = @(Find-DaUiNode -Tree $t -Id $id -Label $label -Exact:([bool]$label)) | Select-Object -First 1; if ($n) { '{0} {1}' -f [int](([int]$n.x1 + [int]$n.x2) / 2), [int](([int]$n.y1 + [int]$n.y2) / 2) } else { $null } }
        $refresh = & $centre 'btnRefresh' $null
        $toggle = & $centre 'btnToggleView' $null
        $file = & $centre 'tvFileName' 'IMG_0001.jpg'
        $rep = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-repeated-command" -Atomic 'C12.repeated-command' -Body {
            if (-not $refresh) { throw 'Refresh not on screen' }
            $t0 = Get-DaMonotonicMs
            $null = Invoke-DaAdb -Serial $s -Arguments @('shell', ((1..8 | ForEach-Object { "input tap $refresh" }) -join '; ')) -TimeoutMs 120000
            $sent = (Get-DaMonotonicMs) - $t0
            $settle = Wait-DaUiNode -Serial $s -Id 'tvFileName' -TimeoutMs 20000
            [ordered]@{ sentMs = $sent; listBackMs = if ($settle) { (Get-DaMonotonicMs) - $t0 } else { $null } }
        }
        if ($rep) { $timings.repeatedRefreshSettleMs = $rep.listBackMs }
        $sw = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-rapid-switching" -Atomic 'C12.rapid-switching' -Body {
            if (-not $toggle) { throw 'view toggle not on screen' }
            $t0 = Get-DaMonotonicMs
            $null = Invoke-DaAdb -Serial $s -Arguments @('shell', ((1..6 | ForEach-Object { "input tap $toggle" }) -join '; ')) -TimeoutMs 120000
            $settle = Wait-DaUiNode -Serial $s -Id 'rvMediaFiles' -TimeoutMs 20000
            [ordered]@{ listBackMs = if ($settle) { (Get-DaMonotonicMs) - $t0 } else { $null } }
        }
        if ($sw) { $timings.rapidSwitchSettleMs = $sw.listBackMs }
        $oc = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-rapid-open-close" -Atomic 'C12.rapid-open-close' -Body {
            if (-not $file) { throw 'IMG_0001.jpg not on screen' }
            # Back goes out the moment the player has the focus, polled on the device: a fixed 0.4 s pause sent
            # Back before the player opened and walked out of the app (run 261013-121122-1048).
            $focus = { param($act) "n=0; while [ `$n -lt 40 ] && ! dumpsys window displays | grep -q 'mCurrentFocus=.*$act'; do n=`$((n+1)); sleep 0.1; done" }
            $pair = "input tap $file; $(& $focus 'PlayerActivity'); input keyevent 4; $(& $focus 'BrowseActivity')"
            $t0 = Get-DaMonotonicMs
            $null = Invoke-DaAdb -Serial $s -Arguments @('shell', ((1..5 | ForEach-Object { $pair }) -join '; ')) -TimeoutMs 120000
            $back = Wait-DaForegroundMatch -Serial $s -Package $p -Pattern 'BrowseActivity' -FromMonoMs $t0 -TimeoutMs 20000
            [ordered]@{ fiveRoundTripsMs = (Get-DaMonotonicMs) - $t0; browseInFront = ($null -ne $back); foreground = Get-DaForeground -Serial $s -Package $p }
        }
        if ($oc) {
            $timings.rapidOpenCloseFiveMs = $oc.fiveRoundTripsMs
            if (-not $oc.browseInFront) {
                $Ctx.Notes.Add("cycle ${Index}: after five open and Back pairs the foreground is $($oc.foreground); the list was reopened")
                $null = Open-DaResource -Serial $s -Package $p -Name $Ctx.Resources.Src
            }
        }
    }
    $probe = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-reaction-after-storm" -Atomic $(if ($Ctx.Watch) { 'C12.watch-key-storm' } else { 'C12.held-key' }) -Body { Invoke-DaKeyProbe -Serial $s -Package $p -KeyCode 20 -ReactionTimeoutMs 1000 }
    if ($probe) { $timings.reactionAfterStormMs = $probe.hostReactionMs }
    $pidAfter = Get-DaProcessIdentity -Serial $s -Package $p
    $same = ($pidBefore -and $pidAfter -and $pidBefore.pid -eq $pidAfter.pid -and $pidBefore.startTicks -eq $pidAfter.startTicks)
    if (-not $same) { Add-DaInvariant -Ctx $Ctx -Id "process-survives-storm-c$Index" -Atomic $(if ($Ctx.Watch) { 'C12.watch-key-storm' } else { 'C12.repeated-command' }) -Holds $false -Detail "pid $($pidBefore.pid) -> $($pidAfter.pid)" }
    elseif ($Index -eq 1) { Add-DaInvariant -Ctx $Ctx -Id 'process-survives-storm' -Atomic $(if ($Ctx.Watch) { 'C12.watch-key-storm' } else { 'C12.repeated-command' }) -Holds $true -Detail "pid $($pidAfter.pid) unchanged; key probe after the storm: $($probe.outcome) in $($probe.hostReactionMs) ms" }
    return [ordered]@{ timings = $timings; ok = [bool]$same; probeOutcome = if ($probe) { $probe.outcome } else { $null } }
}

# ------------------------------------------------------------------------------------------------- C14

function Initialize-DaSceneC14 {
    <#
    .SYNOPSIS
        C14 setup: the small set (long, Unicode and RTL names, corrupt media) in src, the n1000 set in dst, both registered; n10000 replaces it at the segment change.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    if (-not (Initialize-DaOpsScene -Ctx $Ctx)) { return $false }
    $Ctx.LargeSet = Invoke-DaSceneAction -Ctx $Ctx -Name 'push-n1000-into-dst' -Atomic 'C14.items-1k' -Body { Add-DaRemoteSet -Ctx $Ctx -Set n1000 -Side dst }
    $Ctx.LargeSegment = 'n1000'
    return [bool]$Ctx.LargeSet
}

function Invoke-DaSpecialNamesCheck {
    <#
    .SYNOPSIS
        C14 once per run: the src list against the fixture manifest - every file listed or hidden by the documented size filter, the long, Unicode and RTL names present, and a corrupt image opened without losing the process.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $manifest = New-DaFixtureSet -Name small
    $exp = Get-DaExpectedVisible -Manifest $manifest
    $open = Invoke-DaSceneAction -Ctx $Ctx -Name 'open-special-names' -Atomic 'C14.unicode-names' -Body { Measure-DaResourceOpen -Serial $s -Package $p -ResourceName $Ctx.Resources.Src }
    Add-DaInvariant -Ctx $Ctx -Id 'listed-count-equals-unfiltered-fixture' -Atomic 'C14.corrupt-media' -Holds ($open -and [int]$open.count -eq $exp.Visible.Count) -Detail "header $($open.count); expected $($exp.Visible.Count) after the default size filter hides: $(@($exp.Hidden) -join '; ')"
    $groups = [ordered]@{
        'C14.long-names'    = @($manifest.items | Where-Object { $_.kind -eq 'jpeg-long-name' } | ForEach-Object { Split-Path -Leaf $_.path })
        'C14.unicode-names' = @($manifest.items | Where-Object { $_.kind -eq 'jpeg-unicode-name' -and [string]$_.path -notmatch '[֐-׿؀-ۿ]' } | ForEach-Object { Split-Path -Leaf $_.path })
        'C14.rtl-names'     = @($manifest.items | Where-Object { $_.kind -eq 'jpeg-unicode-name' -and [string]$_.path -match '[֐-׿؀-ۿ]' } | ForEach-Object { Split-Path -Leaf $_.path })
    }
    foreach ($g in $groups.Keys) {
        $names = @($groups[$g])
        $found = Invoke-DaSceneAction -Ctx $Ctx -Name "find-$($g.Split('.')[1])" -Atomic $g -Body {
            @(foreach ($n in $names) { [ordered]@{ name = $n; listed = [bool](Find-DaUiNodeScrolling -Serial $s -Id 'tvFileName' -Label $n -Exact -ContainerId 'rvMediaFiles' -MaxSwipes 10) } })
        }
        $missing = @(@($found) | Where-Object { -not $_.listed } | ForEach-Object { $_.name })
        Add-DaInvariant -Ctx $Ctx -Id "$($g.Split('.')[1])-listed-by-full-name" -Atomic $g -Holds ($names.Count -gt 0 -and $missing.Count -eq 0) -Detail "$($names.Count) names searched as exact list labels; missing: $(if ($missing.Count) { $missing -join ', ' } else { 'none' })"
    }
    $corrupt = Invoke-DaSceneAction -Ctx $Ctx -Name 'open-corrupt-image' -Atomic 'C14.corrupt-media' -Body {
        $before = Get-DaProcessIdentity -Serial $s -Package $p
        $o = Open-DaBrowseFile -Ctx $Ctx -FileName 'corrupt_garbage.png' -UiAfter
        Start-Sleep -Milliseconds 1500
        $shot = Save-DaUiShot -Serial $s -OutDir $Ctx.RunDir
        $after = Get-DaProcessIdentity -Serial $s -Package $p
        $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        Start-Sleep -Milliseconds 1200
        [ordered]@{ open = $o; shot = $shot; samePid = ($before -and $after -and $before.pid -eq $after.pid) }
    }
    if ($corrupt) { Add-DaInvariant -Ctx $Ctx -Id 'corrupt-image-open-keeps-the-process' -Atomic 'C14.corrupt-media' -Holds ([bool]$corrupt.samePid) -Detail "player focus: $($corrupt.open.ok) ($($corrupt.open.foreground)); screenshot $($corrupt.shot)" }
    Add-DaOpen -Ctx $Ctx -Atomic 'C14.items-100k' -Reason 'the first pass stops at the 10k set (strategic 3.3: one run, 20 cycles); a 100k set is not generated' -Observed ''
    Add-DaOpen -Ctx $Ctx -Atomic 'C14.large-media' -Reason 'no very large valid media file is generated; the zero-filled transfer sources are not media' -Observed ''
}

function Invoke-DaSceneCycleC14 {
    <#
    .SYNOPSIS
        One C14 cycle: open the large dst folder (1000 items in the first half, 10000 in the second), jump to its end and back to the main screen; the special-names check runs in the first cycle.
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Index
        1-based cycle index.
    .PARAMETER Segment
        n1000 or n10000.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][int]$Index, [string]$Segment)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $timings = [ordered]@{}
    if ($Index -eq 1) { Invoke-DaSpecialNamesCheck -Ctx $Ctx }
    $atomic = if ($Segment -eq 'n10000') { 'C14.items-10k' } else { 'C14.items-1k' }
    $first = $false
    if ($Segment -ne $Ctx.LargeSegment) {
        $Ctx.LargeSet = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-push-n10000-into-dst" -Atomic 'C14.items-10k' -Body { Add-DaRemoteSet -Ctx $Ctx -Set n10000 -Side dst }
        $Ctx.LargeSegment = $Segment
        $first = $true
    }
    $open = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-open-$Segment" -Atomic $atomic -Body { Measure-DaResourceOpen -Serial $s -Package $p -ResourceName $Ctx.Resources.Dst -TimeoutMs 600000 }
    if ($open) { $timings.openListMs = $open.listMs; $timings.openCountMs = $open.countMs }
    if (($Index -eq 1 -or $first) -and $open) { Add-DaInvariant -Ctx $Ctx -Id "$Segment-lists-every-item" -Atomic $atomic -Holds ([int]$open.count -eq [int]$Ctx.LargeSet.Count) -Detail "header count $($open.count) of $($Ctx.LargeSet.Count); list after $($open.listMs) ms, final count after $($open.countMs) ms ($($open.reads) tree reads)" }
    if (-not $open -or -not $open.ok) { return [ordered]@{ timings = $timings; ok = $false } }
    $end = Invoke-DaSceneAction -Ctx $Ctx -Name "c$Index-scroll-to-end" -Atomic 'C14.large-lists' -Body {
        $t = Get-DaUiTree -Serial $s
        $btn = @(Find-DaUiNode -Tree $t -Id 'actionBrowseScrollToBottom') | Select-Object -First 1
        if (-not $btn) { throw 'Scroll to bottom is not offered' }
        $last = '{0}.jpg' -f ('tiny_{0:D5}' -f [int]$Ctx.LargeSet.Count)
        $t0 = Invoke-DaTapNode -Serial $s -Node $btn
        $ms = $null
        while ((Get-DaMonotonicMs) - $t0 -lt 30000) {
            $tt = Get-DaUiTree -Serial $s
            if ($tt.Ok -and (@(Get-DaUiLabels -Tree $tt -Id 'tvFileName') -contains $last)) { $ms = (Get-DaMonotonicMs) - $t0; break }
            Start-Sleep -Milliseconds 200
        }
        [ordered]@{ lastName = $last; endMs = $ms }
    }
    if ($end) { $timings.scrollToEndMs = $end.endMs }
    return [ordered]@{ timings = $timings; ok = $true }
}

# ------------------------------------------------------------------------------------------------- C16

function Initialize-DaSceneC16 {
    <#
    .SYNOPSIS
        C16 setup: on the phone the small set in src, registered and opened (the idle window shows a real list); on the watch the app is brought to the front.
    .PARAMETER Ctx
        Scene context.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    if ($Ctx.Watch) { return [bool](Wait-DaQuiescence -Ctx $Ctx -Point watch -QuietMs 0).reached }
    if (-not (Initialize-DaOpsScene -Ctx $Ctx)) { return $false }
    return (Open-DaResource -Serial $Ctx.Serial -Package $Ctx.Package -Name $Ctx.Resources.Src)
}

function Invoke-DaSceneCycleC16 {
    <#
    .SYNOPSIS
        One C16 cycle: IdleSec seconds without input, in front with the list open (foreground segment, or the watch app) or behind the launcher (background segment).
    .PARAMETER Ctx
        Scene context.
    .PARAMETER Index
        1-based cycle index.
    .PARAMETER Segment
        foreground, background or main (watch).
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx, [Parameter(Mandatory)][int]$Index, [string]$Segment)
    $s = $Ctx.Serial; $p = $Ctx.Package
    $fg = Get-DaForeground -Serial $s -Package $p
    if ($Segment -eq 'background' -and $fg -like "$p/*") { $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_HOME'); Start-Sleep -Milliseconds 800 }
    if ($Segment -eq 'foreground' -and $fg -notmatch 'BrowseActivity') { $Ctx.Notes.Add("cycle ${Index}: the browse list was not in front ($fg); reopened"); $null = Open-DaResource -Serial $s -Package $p -Name $Ctx.Resources.Src }
    $cpuA = Get-DaProcessIdentity -Serial $s -Package $p
    $ticksA = if ($cpuA) { (Get-DaDeviceCpuSnapshot -Serial $s -ProcessId $cpuA.pid).procTicks } else { $null }
    Start-Sleep -Seconds ([int]$Ctx.CyclePlan.IdleSec)
    $ticksB = if ($cpuA) { (Get-DaDeviceCpuSnapshot -Serial $s -ProcessId $cpuA.pid).procTicks } else { $null }
    $power = Get-DaPowerState -Serial $s
    return [ordered]@{ timings = [ordered]@{}; ok = $true; idleSec = [int]$Ctx.CyclePlan.IdleSec; cpuTicksIdle = if ($null -ne $ticksA -and $null -ne $ticksB) { $ticksB - $ticksA } else { $null }; foregroundAfter = Get-DaForeground -Serial $s -Package $p; wakefulness = $power.wakefulness }
}

# --------------------------------------------------------------------------------------------- cycle runner

function Invoke-DaCycleScene {
    <#
    .SYNOPSIS
        Runs a registered cycle scene: a measured cold start, the scene's setup, Cycles cycles each closed at the scene's quiescence point with one cycle-point sample, then a warm start; writes runs/<runId>/cycles.jsonl.
    .DESCRIPTION
        The first WarmupCycles cycles are the explicit warm-up part (phase warm-up); Analyze excludes them from
        the slope. Every row carries its cycle index, phase and segment and the monotonic interval it covered,
        so the 1 Hz sampler rows map onto cycles by time. Cold and warm starts are separate rows. No garbage
        collection is forced; -HeapSnapshot (plan key) takes a diagnostic heap dump only after the window.
    .PARAMETER Ctx
        Scene context with CyclePlan (Cycles, WarmupCycles, IdleSec, HeapSnapshot) and Watch.
    #>
    param([Parameter(Mandatory)][hashtable]$Ctx)
    $entry = (Get-DaSceneRegistry)[$Ctx.Scene]
    $plan = $Ctx.CyclePlan
    $file = Join-Path $Ctx.RunDir 'cycles.jsonl'
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $emit = { param($row) [System.IO.File]::AppendAllText($file, (($row | ConvertTo-Json -Compress -Depth 16) + "`n"), $utf8) }
    $s = $Ctx.Serial; $p = $Ctx.Package
    $point = if ($Ctx.Watch) { 'watch' } else { [string]$entry.quiescence }
    $launch = Get-DaLaunchComponent -Serial $s -Package $p
    $cold = Invoke-DaSceneAction -Ctx $Ctx -Name 'cold-start' -Atomic $null -Body { Measure-DaStart -Serial $s -Package $p -Component $launch -Mode cold -TimeoutMs 30000 }
    & $emit ([ordered]@{ type = 'start'; scene = $Ctx.Scene; phase = 'cold'; index = 0; start = $cold })
    $ready = Invoke-DaSceneAction -Ctx $Ctx -Name 'setup' -Atomic $null -Body { & $entry.setup -Ctx $Ctx }
    if (-not $ready) {
        foreach ($a in @($entry.atomic)) { Add-DaOpen -Ctx $Ctx -Atomic $a -Reason 'the cycle scene setup failed' -Observed (@($Ctx.Notes) -join '; ') }
        return
    }
    $firstPid = $null; $restarts = [System.Collections.Generic.List[string]]::new()
    for ($i = 1; $i -le [int]$plan.Cycles; $i++) {
        $phase = Get-DaCyclePhase -Index $i -WarmupCycles ([int]$plan.WarmupCycles)
        $segment = Get-DaCycleSegment -Scene $Ctx.Scene -Index $i -Cycles ([int]$plan.Cycles) -Watch:([bool]$Ctx.Watch)
        $q = if ($segment -eq 'background') { 'background' } elseif ($Ctx.Scene -eq 'C16' -and -not $Ctx.Watch) { 'browse' } else { $point }
        $t0 = Get-DaMonotonicMs
        $res = Invoke-DaSceneAction -Ctx $Ctx -Name "cycle-$i" -Atomic $null -Body { & $entry.cycle -Ctx $Ctx -Index $i -Segment $segment }
        $t1 = Get-DaMonotonicMs
        $quiet = if ($Ctx.Scene -eq 'C16') { 0 } else { 3000 }
        $qs = Wait-DaQuiescence -Ctx $Ctx -Point $q -QuietMs $quiet
        if ($qs.alive) {
            if ($null -eq $firstPid) { $firstPid = "$($qs.pid)/$($qs.startTicks)" }
            elseif ("$($qs.pid)/$($qs.startTicks)" -ne $firstPid) { $restarts.Add("cycle ${i}: $firstPid -> $($qs.pid)/$($qs.startTicks)"); $firstPid = "$($qs.pid)/$($qs.startTicks)" }
        }
        else { $restarts.Add("cycle ${i}: no process at the quiescence point") }
        & $emit ([ordered]@{
                type = 'cycle'; scene = $Ctx.Scene; index = $i; phase = $phase; segment = $segment; startMonoMs = $t0; endMonoMs = $t1; durationMs = $t1 - $t0
                ok = [bool]($res -and $res.ok); result = $res; quiescence = [ordered]@{ point = $qs.point; reached = $qs.reached; quietMs = $qs.quietMs; alive = $qs.alive; pid = $qs.pid; startTicks = $qs.startTicks }
                pointMonoMs = if ($qs.sample) { $qs.sample.hostMonoMs } else { (Get-DaMonotonicMs) }; point = $qs.sample; forcedGc = $false
            })
    }
    Add-DaInvariant -Ctx $Ctx -Id 'process-survives-every-cycle' -Atomic @($entry.atomic)[0] -Holds ($restarts.Count -eq 0) -Detail $(if ($restarts.Count) { $restarts -join '; ' } else { "one process $firstPid through $($plan.Cycles) cycles" })
    if ($Ctx.Watch) { $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') }
    $warm = Invoke-DaSceneAction -Ctx $Ctx -Name 'warm-start' -Atomic $null -Body { Measure-DaStart -Serial $s -Package $p -Component $launch -Mode warm }
    & $emit ([ordered]@{ type = 'start'; scene = $Ctx.Scene; phase = 'warm'; index = [int]$plan.Cycles + 1; start = $warm })
    if ($plan.HeapSnapshot) {
        $heap = Invoke-DaSceneAction -Ctx $Ctx -Name 'diagnostic-heap-snapshot' -Atomic $null -Body {
            $id = Get-DaProcessIdentity -Serial $s -Package $p
            if (-not $id) { throw 'no process for the heap snapshot' }
            $dev = "/data/local/tmp/da-$($Ctx.RunId).hprof"
            $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'am', 'dumpheap', [string]$id.pid, $dev) -TimeoutMs 120000
            Start-Sleep -Seconds 5
            $privDir = (Get-DaPaths).Private
            Initialize-DaPrivateDir -Path $privDir
            $priv = Join-Path $privDir "heap-$($Ctx.RunId).hprof"
            $null = Invoke-DaAdb -Serial $s -Arguments @('pull', $dev, $priv) -TimeoutMs 300000
            $null = Invoke-DaAdb -Serial $s -Arguments @('shell', 'rm', '-f', $dev)
            [ordered]@{ privateFile = Split-Path -Leaf $priv; bytes = if (Test-Path -LiteralPath $priv) { (Get-Item -LiteralPath $priv).Length } else { $null }; note = 'raw heap dump kept in the private directory, never exported (section 5.9)' }
        }
        & $emit ([ordered]@{ type = 'heap'; scene = $Ctx.Scene; afterWindow = $true; heap = $heap })
    }
}

