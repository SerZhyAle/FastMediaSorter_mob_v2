#requires -Version 7.0
<#
.SYNOPSIS
    UI driver, accessibility-event capture, app navigation and SAF resource registration of the S4166
    dynamic-audit stand; used by the scenes of lib/da-scenes.ps1.

.DESCRIPTION
    Dot-source after lib/da-common.ps1, da-emulator.ps1, da-fixture.ps1, da-sampler.ps1, da-probe.ps1 and
    da-fault.ps1. Functions only; nothing is assigned at script scope.

    Every tap, key, text entry, swipe, screenshot and tree read goes through scripts/devtest/adb.ps1
    (uidump -Ids, tap-id, tap-label, set-text, key, swipe, shot) in its own pwsh process with -Json.
    tap-id is tried first and tap-label second; no coordinate is ever remembered. The one place a raw
    `input tap` is sent is Invoke-DaUiTriggerTap, and its point is the centre of the node found in a
    tree read in the same call a moment earlier: `uiautomator dump` and `uiautomator events` cannot
    share the accessibility connection (measured 2026-10-11 on emulator-5554: a dump started while the
    event stream ran was killed, exit 137), so the tap that starts an operation is resolved first, then
    the event stream is opened, then the tap is sent. The event stream is what captures the raw text of
    a toast, which never appears in a tree.

    Resources for the file-operation scenes are registered the way a user does it on the standard
    flavor: Add > Local Folder > Add Manually opens the system folder picker (a SAF tree grant), which
    Select-DaSafFolder walks by breadcrumbs and folder titles; the destination folder is marked
    "Mark for Quick Sort" so the copy and move dialogs offer it.
#>
# ---------------------------------------------------------------------------------------------- UI driver

function Get-DaAdbScriptPath {
    <#
    .SYNOPSIS
        Path of scripts/devtest/adb.ps1.
    #>
    return (Join-Path (Get-DaPaths).RepoRoot 'scripts/devtest/adb.ps1')
}

function Invoke-DaUiVerb {
    <#
    .SYNOPSIS
        Runs one adb.ps1 verb with -Json in its own pwsh process; returns {ExitCode; Ok; Data; Reason; ElapsedMs}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Arguments
        Verb and its parameters, for example @('tap-id', '-ResourceId', 'btnNext').
    .PARAMETER TimeoutMs
        Hard limit of the call.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string[]]$Arguments, [int]$TimeoutMs = 90000)
    $pwsh = (Get-Process -Id $PID).Path
    $argv = @('-NoProfile', '-File', (Get-DaAdbScriptPath)) + $Arguments + @('-DeviceId', $Serial, '-Json')
    $r = Invoke-DaProcess -FilePath $pwsh -ArgumentList $argv -TimeoutMs $TimeoutMs
    $json = $null
    foreach ($l in @($r.Lines)) {
        if ($l.TrimStart().StartsWith('{')) { try { $json = $l | ConvertFrom-Json -Depth 32 } catch { $json = $null } }
    }
    $reason = if ($json -and $json.reason) { [string]$json.reason } elseif ($r.TimedOut) { "adb.ps1 $($Arguments[0]) timed out" } else { (($r.Stderr + ' ' + (@($r.Lines) -join ' ')) -replace '\s+', ' ').Trim() }
    return [pscustomobject]@{
        ExitCode  = $r.ExitCode
        Ok        = ($r.ExitCode -eq 0)
        Data      = if ($json) { $json.data } else { $null }
        Reason    = if ($r.ExitCode -eq 0) { $null } else { $reason }
        ElapsedMs = $r.ElapsedMs
    }
}

function Get-DaUiTree {
    <#
    .SYNOPSIS
        Reads the UI tree through adb.ps1 uidump -Ids; returns {Ok; Nodes; File; Checked; Reason}.
    .DESCRIPTION
        Checked holds, per resource id, the checked attribute of every node carrying it in document
        order, read from the saved XML because the uidump listing does not carry it.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    #>
    param([Parameter(Mandatory)][string]$Serial)
    $r = Invoke-DaUiVerb -Serial $Serial -Arguments @('uidump', '-Ids')
    if (-not $r.Ok -or -not $r.Data) { return [pscustomobject]@{ Ok = $false; Nodes = @(); File = $null; Checked = @{}; Reason = "uidump exit $($r.ExitCode): $($r.Reason)" } }
    $checked = @{}
    $file = [string]$r.Data.file
    if ($file -and (Test-Path -LiteralPath $file)) {
        try {
            $xml = [xml][System.IO.File]::ReadAllText($file, [System.Text.UTF8Encoding]::new($false))
            foreach ($n in $xml.SelectNodes('//node[@resource-id]')) {
                $rid = [string]$n.'resource-id'
                if (-not $rid) { continue }
                $short = $rid -replace '^.*:id/', ''
                if (-not $checked.ContainsKey($short)) { $checked[$short] = [System.Collections.Generic.List[bool]]::new() }
                $checked[$short].Add([string]$n.checked -eq 'true')
            }
        }
        catch { $checked = @{} }
    }
    return [pscustomobject]@{ Ok = $true; Nodes = @($r.Data.nodes); File = $file; Checked = $checked; Reason = $null }
}

function Find-DaUiNode {
    <#
    .SYNOPSIS
        Nodes of a tree matching a resource id or a label (text or content-description). Pure.
    .PARAMETER Tree
        Get-DaUiTree output.
    .PARAMETER Id
        Short or full resource id.
    .PARAMETER Label
        Label to match; substring unless -Exact.
    .PARAMETER Exact
        Whole-value label match.
    #>
    param([Parameter(Mandatory)]$Tree, [string]$Id, [string]$Label, [switch]$Exact)
    $hits = foreach ($n in @($Tree.Nodes)) {
        $okId = $true; $okLabel = $true
        if ($Id) { $okId = ([string]$n.resIdShort -eq $Id -or [string]$n.resId -eq $Id) }
        if ($Label) {
            $vals = @([string]$n.text, [string]$n.desc, [string]$n.label)
            $okLabel = if ($Exact) { $vals -contains $Label } else { [bool]@($vals | Where-Object { $_ -and $_.Contains($Label) }).Count }
        }
        if ($okId -and $okLabel) { $n }
    }
    return @($hits)
}

function Get-DaUiLabels {
    <#
    .SYNOPSIS
        Labels of the nodes carrying a resource id, in document order. Pure.
    .PARAMETER Tree
        Get-DaUiTree output.
    .PARAMETER Id
        Short resource id.
    #>
    param([Parameter(Mandatory)]$Tree, [Parameter(Mandatory)][string]$Id)
    return @(@($Tree.Nodes) | Where-Object { [string]$_.resIdShort -eq $Id } | ForEach-Object { [string]$_.label })
}

function Invoke-DaUiTap {
    <#
    .SYNOPSIS
        Taps by resource id first and by label second, through adb.ps1; returns {Ok; How; ExitCode; Reason}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Id
        Resource id (tap-id).
    .PARAMETER Label
        Label (tap-label), used when no id is given or the id is not on screen.
    .PARAMETER Exact
        Whole-value match.
    .PARAMETER Index
        1-based match index.
    #>
    param([Parameter(Mandatory)][string]$Serial, [string]$Id, [string]$Label, [switch]$Exact, [int]$Index = 0)
    $tail = @()
    if ($Exact) { $tail += '-Exact' }
    if ($Index -gt 0) { $tail += @('-Index', [string]$Index) }
    if ($Id) {
        $r = Invoke-DaUiVerb -Serial $Serial -Arguments (@('tap-id', '-ResourceId', $Id) + $tail)
        if ($r.Ok) { return [pscustomobject]@{ Ok = $true; How = "tap-id $Id"; ExitCode = 0; Reason = $null } }
        if (-not $Label) { return [pscustomobject]@{ Ok = $false; How = "tap-id $Id"; ExitCode = $r.ExitCode; Reason = $r.Reason } }
    }
    $r = Invoke-DaUiVerb -Serial $Serial -Arguments (@('tap-label', '-Label', $Label) + $tail)
    return [pscustomobject]@{ Ok = $r.Ok; How = "tap-label '$Label'"; ExitCode = $r.ExitCode; Reason = $r.Reason }
}

function Invoke-DaUiKey {
    <#
    .SYNOPSIS
        Sends one key through adb.ps1 key; returns $true on success.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Key
        Key name or code (BACK, HOME, KEYCODE_SLEEP ..).
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Key)
    return (Invoke-DaUiVerb -Serial $Serial -Arguments @('key', '-Key', $Key)).Ok
}

function Set-DaUiText {
    <#
    .SYNOPSIS
        Replaces a field's text through adb.ps1 set-text (ASCII only, read back); returns {Ok; Reason}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Id
        Resource id of the field.
    .PARAMETER Text
        New value.
    .PARAMETER Index
        1-based match index when several fields carry the id.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Id, [Parameter(Mandatory)][string]$Text, [int]$Index = 0)
    $argv = @('set-text', '-ResourceId', $Id, '-Text', $Text)
    if ($Index -gt 0) { $argv += @('-Index', [string]$Index) }
    $r = Invoke-DaUiVerb -Serial $Serial -Arguments $argv
    return [pscustomobject]@{ Ok = $r.Ok; Reason = $r.Reason }
}

function Save-DaUiShot {
    <#
    .SYNOPSIS
        Screenshot through adb.ps1 shot into a run directory; returns the file name or $null.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER OutDir
        Host directory.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$OutDir)
    $r = Invoke-DaUiVerb -Serial $Serial -Arguments @('shot', '-OutDir', $OutDir)
    if ($r.Ok -and $r.Data -and $r.Data.file) { return (Split-Path -Leaf ([string]$r.Data.file)) }
    $latest = Get-ChildItem -LiteralPath $OutDir -Filter '*.png' -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($r.Ok -and $latest) { return $latest.Name }
    return $null
}

function Wait-DaUiNode {
    <#
    .SYNOPSIS
        Polls the tree until a node with the id or label is visible; returns the tree or $null on timeout.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Id
        Resource id.
    .PARAMETER Label
        Label.
    .PARAMETER Exact
        Whole-value label match.
    .PARAMETER TimeoutMs
        Limit.
    #>
    param([Parameter(Mandatory)][string]$Serial, [string]$Id, [string]$Label, [switch]$Exact, [int]$TimeoutMs = 15000)
    $deadline = (Get-DaMonotonicMs) + $TimeoutMs
    do {
        $t = Get-DaUiTree -Serial $Serial
        if ($t.Ok -and @(Find-DaUiNode -Tree $t -Id $Id -Label $Label -Exact:$Exact).Count) { return $t }
        Start-Sleep -Milliseconds 700
    } while ((Get-DaMonotonicMs) -lt $deadline)
    return $null
}

function Find-DaUiNodeScrolling {
    <#
    .SYNOPSIS
        Brings a node into view by swiping inside a scrollable container (bounds read from the same tree); returns the tree or $null.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Id
        Resource id of the target.
    .PARAMETER Label
        Label of the target.
    .PARAMETER Exact
        Whole-value label match.
    .PARAMETER ContainerId
        Resource id of the scrollable container.
    .PARAMETER MaxSwipes
        Swipes per direction.
    #>
    param([Parameter(Mandatory)][string]$Serial, [string]$Id, [string]$Label, [switch]$Exact, [Parameter(Mandatory)][string]$ContainerId, [int]$MaxSwipes = 8)
    foreach ($dir in @('down', 'up')) {
        $lastSig = $null
        for ($i = 0; $i -le $MaxSwipes; $i++) {
            $t = Get-DaUiTree -Serial $Serial
            if (-not $t.Ok) { Start-Sleep -Milliseconds 800; continue }
            if (@(Find-DaUiNode -Tree $t -Id $Id -Label $Label -Exact:$Exact).Count) { return $t }
            $sig = (@($t.Nodes | ForEach-Object { $_.label }) -join '|')
            if ($sig -eq $lastSig) { break }
            $lastSig = $sig
            $c = @(Find-DaUiNode -Tree $t -Id $ContainerId) | Select-Object -First 1
            if (-not $c) { return $null }
            $x = [int](($c.x1 + $c.x2) / 2)
            $top = [int]($c.y1 + ($c.y2 - $c.y1) * 0.25); $bottom = [int]($c.y1 + ($c.y2 - $c.y1) * 0.75)
            $from = if ($dir -eq 'down') { $bottom } else { $top }
            $to = if ($dir -eq 'down') { $top } else { $bottom }
            $null = Invoke-DaUiVerb -Serial $Serial -Arguments @('swipe', '-X', [string]$x, '-Y', [string]$from, '-X2', [string]$x, '-Y2', [string]$to, '-Duration', '350')
            Start-Sleep -Milliseconds 700
        }
    }
    return $null
}

# ------------------------------------------------------------------------------------- accessibility events

function Start-DaUiEventWatch {
    <#
    .SYNOPSIS
        Starts `uiautomator events` on the device for a bounded time; returns the handle Stop-DaUiEventWatch takes.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Seconds
        Device-side limit of the stream (toybox timeout).
    #>
    param([Parameter(Mandatory)][string]$Serial, [ValidateRange(1, 300)][int]$Seconds = 8)
    $psi = [System.Diagnostics.ProcessStartInfo]::new((Get-DaAdbPath))
    foreach ($a in @('-s', $Serial, 'shell', "timeout $Seconds uiautomator events")) { $psi.ArgumentList.Add($a) }
    $psi.UseShellExecute = $false; $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
    $psi.StandardOutputEncoding = [System.Text.UTF8Encoding]::new($false)
    $proc = [System.Diagnostics.Process]::Start($psi)
    return [pscustomobject]@{ Process = $proc; Out = $proc.StandardOutput.ReadToEndAsync(); Err = $proc.StandardError.ReadToEndAsync(); Seconds = $Seconds; StartedMonoMs = Get-DaMonotonicMs }
}

function Stop-DaUiEventWatch {
    <#
    .SYNOPSIS
        Waits for the event stream to end (or ends it) and returns its raw lines.
    .PARAMETER Watch
        Handle from Start-DaUiEventWatch.
    #>
    param([Parameter(Mandatory)]$Watch)
    $p = $Watch.Process
    if (-not $p.WaitForExit(($Watch.Seconds + 6) * 1000)) { try { $p.Kill($true) } catch { $null = $_ } ; $null = $p.WaitForExit(5000) }
    $text = ''
    try { $text = $Watch.Out.Result } catch { $text = '' }
    $p.Dispose()
    return @(($text -replace "`r`n", "`n") -split "`n" | Where-Object { $_.Trim() })
}

function ConvertFrom-DaUiEventLines {
    <#
    .SYNOPSIS
        Parses `uiautomator events` lines into {time; type; package; text; className}; text is the raw bracket content. Pure.
    .PARAMETER Lines
        Raw event lines.
    #>
    param([string[]]$Lines)
    $out = foreach ($l in @($Lines)) {
        if ($l -notmatch 'EventType: (\w+);') { continue }
        $type = $Matches[1]
        $pkg = if ($l -match 'PackageName: ([^;]+);') { $Matches[1].Trim() } else { $null }
        $cls = if ($l -match 'ClassName: ([^;]+);') { $Matches[1].Trim() } else { $null }
        $txt = if ($l -match 'Text: \[(.*?)\]; ContentDescription') { $Matches[1] } else { '' }
        $time = if ($l -match '^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)') { $Matches[1] } else { $null }
        [pscustomobject]@{ time = $time; type = $type; package = $pkg; className = $cls; text = $txt }
    }
    return @($out)
}

function Get-DaUiOutcomeChannels {
    <#
    .SYNOPSIS
        Splits parsed events into toast or notification texts and dialog texts of one package. Pure.
    .PARAMETER Events
        ConvertFrom-DaUiEventLines output.
    .PARAMETER Package
        Package whose events count.
    .PARAMETER AppLabel
        Application label the platform appends to a toast's text list; removed from the message.
    #>
    param([object[]]$Events, [Parameter(Mandatory)][string]$Package, [string]$AppLabel = 'Fast Media Sorter & Organizer')
    $toasts = [System.Collections.Generic.List[string]]::new()
    $dialogs = [System.Collections.Generic.List[string]]::new()
    foreach ($e in @($Events)) {
        if ($e.package -ne $Package -or [string]::IsNullOrWhiteSpace($e.text)) { continue }
        $suffix = [regex]::Escape(", $AppLabel") + '$'
        $t = ([string]$e.text -replace $suffix, '').Trim()
        if (-not $t -or $t -eq $AppLabel) { continue }
        if ($e.type -eq 'TYPE_NOTIFICATION_STATE_CHANGED') { if (-not $toasts.Contains($t)) { $toasts.Add($t) } }
        elseif ($e.type -eq 'TYPE_WINDOW_STATE_CHANGED') { if (-not $dialogs.Contains($t)) { $dialogs.Add($t) } }
    }
    return [pscustomobject]@{ Toasts = $toasts.ToArray(); Dialogs = $dialogs.ToArray() }
}

function Invoke-DaUiTriggerTap {
    <#
    .SYNOPSIS
        Taps the node that starts an operation while the accessibility event stream records its outcome; returns {Ok; Reason; TapMonoMs; Node; Toasts; Dialogs; RawLines}.
    .DESCRIPTION
        The node is read from a tree taken in this call; the stream is opened only after that read
        (dump and stream cannot share the connection), then the node's centre is tapped. -During runs on
        the host while the stream is still open (fault injection at a moment inside the operation).
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Package whose events count.
    .PARAMETER Id
        Resource id of the node.
    .PARAMETER Label
        Label of the node.
    .PARAMETER Exact
        Whole-value label match.
    .PARAMETER WatchSec
        Length of the event stream.
    .PARAMETER During
        Scriptblock run after the tap while the stream is open; receives the tap's monotonic time.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Package,
        [string]$Id,
        [string]$Label,
        [switch]$Exact,
        [int]$WatchSec = 7,
        [scriptblock]$During
    )
    $tree = Get-DaUiTree -Serial $Serial
    $node = if ($tree.Ok) { @(Find-DaUiNode -Tree $tree -Id $Id -Label $Label -Exact:$Exact) | Select-Object -First 1 } else { $null }
    if (-not $node) { return [pscustomobject]@{ Ok = $false; Reason = "trigger node id='$Id' label='$Label' not on screen ($($tree.Reason))"; TapMonoMs = $null; Node = $null; Toasts = @(); Dialogs = @(); RawLines = @(); DuringResult = $null } }
    $watch = Start-DaUiEventWatch -Serial $Serial -Seconds $WatchSec
    Start-Sleep -Milliseconds 900
    $tapAt = Get-DaMonotonicMs
    $tap = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'input', 'tap', [string]$node.tapX, [string]$node.tapY) -TimeoutMs 15000
    $duringResult = $null
    if ($During) { try { $duringResult = & $During $tapAt } catch { $duringResult = "during-step failed: $($_.Exception.Message)" } }
    $lines = Stop-DaUiEventWatch -Watch $watch
    $ch = Get-DaUiOutcomeChannels -Events (ConvertFrom-DaUiEventLines -Lines $lines) -Package $Package
    return [pscustomobject]@{
        Ok = ($tap.ExitCode -eq 0); Reason = if ($tap.ExitCode -ne 0) { "input tap failed: $($tap.Stderr)" } else { $null }
        TapMonoMs = $tapAt; Node = [string]$node.label; Toasts = $ch.Toasts; Dialogs = $ch.Dialogs; RawLines = $lines; DuringResult = $duringResult
    }
}

function Test-DaMessageText {
    <#
    .SYNOPSIS
        Readability issues of one user-visible message (encoded path, number agreement, raw exception text). Pure.
    .PARAMETER Text
        Raw message text.
    #>
    param([AllowEmptyString()][string]$Text)
    $issues = [System.Collections.Generic.List[string]]::new()
    if ([string]::IsNullOrWhiteSpace($Text)) { $issues.Add('empty'); return $issues.ToArray() }
    if ($Text -match '%2F|%3A|content://|primary:') { $issues.Add('encoded-path') }
    if ($Text -match '(?<![\d.,])1 (files|items|folders)\b|\b\d+ file\(s\)') { $issues.Add('number-agreement') }
    if ($Text -match '(Exception|java\.|kotlin\.|android\.os\.|\bnull\b|errno|EACCES|ENOSPC)') { $issues.Add('raw-technical-text') }
    return $issues.ToArray()
}


# ------------------------------------------------------------------------------------------- app helpers

function Get-DaMainComponent {
    <#
    .SYNOPSIS
        Launcher component of the phone app.
    .PARAMETER Package
        Application id.
    #>
    param([Parameter(Mandatory)][string]$Package)
    return "$Package/com.sza.fastmediasorter.ui.main.MainActivity"
}

function Get-DaForeground {
    <#
    .SYNOPSIS
        Focused window component (package/activity) or $null.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id (for the process part of the probe context).
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package)
    return [string](Get-DaProbeContext -Serial $Serial -Package $Package).foreground
}

function Open-DaAppMain {
    <#
    .SYNOPSIS
        Brings the app's main screen to the front (am start, then back out of anything above it); returns $true when MainActivity has focus.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package)
    for ($i = 0; $i -lt 8; $i++) {
        $fg = Get-DaForeground -Serial $Serial -Package $Package
        if ($fg -match 'ui\.main\.MainActivity') {
            # noLegal raises "Permissions required" (all-files access) over the main screen; the user's
            # "Continue anyway" keeps the SAF path the scenes exercise.
            $t = Get-DaUiTree -Serial $Serial
            if ($t.Ok -and @(Find-DaUiNode -Tree $t -Id 'alertTitle').Count -and @(Find-DaUiNode -Tree $t -Id 'button2').Count) {
                $null = Invoke-DaUiTap -Serial $Serial -Id 'button2'
                Start-Sleep -Milliseconds 1200
                continue
            }
            return $true
        }
        # A system picker or permission screen left in the app's task by an interrupted run sits on top of
        # it after a restore; am start would only bring it back, so it is closed with Back.
        if ($fg -match 'documentsui|permissioncontroller') { $null = Invoke-DaUiKey -Serial $Serial -Key 'BACK' }
        elseif ($fg -and $fg.StartsWith("$Package/") -and $fg -notmatch 'MainActivity') { $null = Invoke-DaUiKey -Serial $Serial -Key 'BACK' }
        else { $null = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'am', 'start', '-W', '-n', (Get-DaMainComponent -Package $Package)) -TimeoutMs 30000 }
        Start-Sleep -Milliseconds 1500
    }
    return ((Get-DaForeground -Serial $Serial -Package $Package) -match 'ui\.main\.MainActivity')
}

function Wait-DaProcessGone {
    <#
    .SYNOPSIS
        Milliseconds until the package has no process, or $null when it is still alive at the limit.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER TimeoutMs
        Limit.
    .PARAMETER FromMonoMs
        Start of the measurement.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [int]$TimeoutMs = 10000, [long]$FromMonoMs = 0)
    if ($FromMonoMs -le 0) { $FromMonoMs = Get-DaMonotonicMs }
    while ((Get-DaMonotonicMs) - $FromMonoMs -lt $TimeoutMs) {
        if (-not (Get-DaProcessIdentity -Serial $Serial -Package $Package)) { return (Get-DaMonotonicMs) - $FromMonoMs }
        Start-Sleep -Milliseconds 200
    }
    return $null
}

function Get-DaPowerState {
    <#
    .SYNOPSIS
        Wakefulness and keyguard state in one adb call; returns {wakefulness; keyguard}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    #>
    param([Parameter(Mandatory)][string]$Serial)
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "dumpsys power | grep -E 'mWakefulness='; dumpsys window | grep -E 'mDreamingLockscreen=|isKeyguardShowing|mShowingLockscreen=|KeyguardShowing=' | head -3") -TimeoutMs 15000
    $text = @($r.Lines) -join "`n"
    $wake = if ($text -match 'mWakefulness=(\w+)') { $Matches[1] } else { $null }
    $kg = ($text -match '(mShowingLockscreen|mDreamingLockscreen|KeyguardShowing|isKeyguardShowing)\s*[=:]\s*true')
    return [pscustomobject]@{ wakefulness = $wake; keyguard = [bool]$kg }
}

function Wait-DaPowerState {
    <#
    .SYNOPSIS
        Milliseconds until wakefulness equals a value, or $null at the limit.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Wakefulness
        Awake, Asleep or Dozing.
    .PARAMETER FromMonoMs
        Start of the measurement.
    .PARAMETER TimeoutMs
        Limit.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Wakefulness, [long]$FromMonoMs, [int]$TimeoutMs = 10000)
    while ((Get-DaMonotonicMs) - $FromMonoMs -lt $TimeoutMs) {
        if ((Get-DaPowerState -Serial $Serial).wakefulness -eq $Wakefulness) { return (Get-DaMonotonicMs) - $FromMonoMs }
        Start-Sleep -Milliseconds 150
    }
    return $null
}

function Wait-DaAppFocus {
    <#
    .SYNOPSIS
        Milliseconds until a window of the package has focus, or $null at the limit.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER FromMonoMs
        Start of the measurement.
    .PARAMETER TimeoutMs
        Limit.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [long]$FromMonoMs, [int]$TimeoutMs = 10000)
    while ((Get-DaMonotonicMs) - $FromMonoMs -lt $TimeoutMs) {
        $fg = Get-DaForeground -Serial $Serial -Package $Package
        if ($fg -and $fg.StartsWith("$Package/")) { return (Get-DaMonotonicMs) - $FromMonoMs }
        Start-Sleep -Milliseconds 150
    }
    return $null
}

# ---------------------------------------------------------------------------------- fixture and resources

function Initialize-DaOpsFixture {
    <#
    .SYNOPSIS
        Publishes the small set into <stand root>/<runId>/src beside an empty dst and checks it against the set manifest; returns the fixture record.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER RunId
        Run id naming the run directory.
    .PARAMETER Set
        Fixture set name.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$RunId, [ValidateSet('small', 'n1000', 'n10000')][string]$Set = 'small')
    $manifest = New-DaFixtureSet -Name $Set
    $run = "$(Get-DaFixtureRemoteRoot)/$RunId"
    $exists = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "test -e '$run' && echo yes || echo no")
    if ((@($exists.Lines) -join '').Trim() -eq 'yes') { throw "run fixture directory already exists: $run" }
    $mk = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "mkdir -p '$run/dst'")
    if ($mk.ExitCode -ne 0) { throw "mkdir $run failed: $($mk.Stderr)" }
    $local = Join-Path (Join-Path (Join-Path (Get-DaPaths).Fixtures $Set) 'files') '.'
    $push = Invoke-DaAdb -Serial $Serial -Arguments @('push', $local, "$run/src") -TimeoutMs 1800000
    if ($push.ExitCode -ne 0) { throw "adb push of fixture $Set failed: $($push.Stderr)" }
    $tree = Get-DaTreeHash -Serial $Serial -RemoteDir "$run/src"
    $diff = Compare-DaTreeHash -Reference @($manifest.items) -Difference $tree
    if ($diff.Count) { throw "published fixture differs from its manifest: $($diff.Summary)" }
    return [ordered]@{ set = $Set; runRemoteDir = $run; src = "$run/src"; dst = "$run/dst"; count = $tree.Count; treeSha256 = $tree.TreeSha256; manifestTreeSha256 = $manifest.treeSha256; pushMs = $push.ElapsedMs }
}

function Add-DaBigSource {
    <#
    .SYNOPSIS
        Creates a large deterministic file (zeros) in the run's src so an operation lasts long enough to be interrupted.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER RunRemoteDir
        The run's fixture directory.
    .PARAMETER Name
        File name in src.
    .PARAMETER SizeMb
        Size in MiB.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$RunRemoteDir, [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$Name, [int]$SizeMb = 256)
    $path = "$RunRemoteDir/src/$Name"
    if (-not (Test-DaPathInside -Root $RunRemoteDir -Path $path)) { throw "refused: $path is outside $RunRemoteDir" }
    $r = Invoke-DaAdb -Serial $Serial -Arguments @('shell', "head -c $([long]$SizeMb * 1048576) /dev/zero > '$path' && stat -c %s '$path'") -TimeoutMs 300000
    if ($r.ExitCode -ne 0) { throw "creating $path failed: $($r.Stderr)" }
    return "src/$Name"
}

function Select-DaSafFolder {
    <#
    .SYNOPSIS
        Navigates the system folder picker to a folder by its path segments (breadcrumbs first, then folder titles); returns {Ok; Reason; Steps}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Segments
        Folder names from the storage root, for example Download, FastMediaSorter_DynAudit, <runId>, src.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string[]]$Segments)
    $steps = [System.Collections.Generic.List[string]]::new()
    for ($i = 0; $i -lt 14; $i++) {
        $t = Get-DaUiTree -Serial $Serial
        if (-not $t.Ok) { Start-Sleep -Milliseconds 800; continue }
        $crumbs = @(Get-DaUiLabels -Tree $t -Id 'breadcrumb_text')
        if (-not $crumbs.Count) { return [pscustomobject]@{ Ok = $false; Reason = 'the system folder picker is not in front (no breadcrumb)'; Steps = $steps.ToArray() } }
        $current = $crumbs[-1]
        $depth = [array]::LastIndexOf($Segments, $current)
        if ($depth -eq $Segments.Count - 1 -and ($crumbs.Count -lt 2 -or $crumbs.Count -lt $Segments.Count -or $crumbs[-2] -eq $Segments[-2])) {
            return [pscustomobject]@{ Ok = $true; Reason = "picker at $($crumbs -join ' > ')"; Steps = $steps.ToArray() }
        }
        if ($depth -ge 0) {
            $next = $Segments[$depth + 1]
            $found = Find-DaUiNodeScrolling -Serial $Serial -Label $next -Exact -ContainerId 'dir_list' -MaxSwipes 6
            if (-not $found) { return [pscustomobject]@{ Ok = $false; Reason = "folder '$next' not listed under '$current'"; Steps = $steps.ToArray() } }
            $tap = Invoke-DaUiTap -Serial $Serial -Label $next -Exact
            $steps.Add("open $next ($($tap.How), ok $($tap.Ok))")
        }
        else {
            $known = -1
            for ($k = 0; $k -lt $crumbs.Count; $k++) { if ($Segments -contains $crumbs[$k]) { $known = $k } }
            $target = if ($known -ge 0) { $known + 1 } else { 1 }
            if ($crumbs.Count -eq 1) {
                $tap = Invoke-DaUiTap -Serial $Serial -Label $Segments[0] -Exact
                $steps.Add("open $($Segments[0]) from the storage root ($($tap.How), ok $($tap.Ok))")
            }
            else {
                $tap = Invoke-DaUiTap -Serial $Serial -Id 'breadcrumb_text' -Index $target
                $steps.Add("breadcrumb #$target '$($crumbs[$target - 1])' ($($tap.How), ok $($tap.Ok))")
            }
        }
        Start-Sleep -Milliseconds 1500
    }
    return [pscustomobject]@{ Ok = $false; Reason = 'picker navigation did not converge in 14 steps'; Steps = $steps.ToArray() }
}

function Register-DaSafResources {
    <#
    .SYNOPSIS
        Registers the run's src and dst folders as local resources through Add > Local Folder > Add Manually and the system picker; dst is marked as a destination. Returns {Ok; Reason; Src; Dst; Steps}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER RunId
        Run id (the folder under the stand root).
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$RunId)
    $steps = [System.Collections.Generic.List[string]]::new()
    $fail = { param($why) [pscustomobject]@{ Ok = $false; Reason = $why; Src = $null; Dst = $null; Steps = $steps.ToArray() } }
    if (-not (Open-DaAppMain -Serial $Serial -Package $Package)) { return (& $fail 'main screen not reached') }
    foreach ($id in 'btnAddResource', 'cardLocalFolder') {
        $tap = Invoke-DaUiTap -Serial $Serial -Id $id
        $steps.Add("$($tap.How) ok $($tap.Ok)")
        if (-not $tap.Ok) { return (& $fail "$id not tappable: $($tap.Reason)") }
        Start-Sleep -Milliseconds 1500
    }
    foreach ($leaf in 'src', 'dst') {
        if (-not (Wait-DaUiNode -Serial $Serial -Id 'btnAddManually' -TimeoutMs 10000)) { return (& $fail 'Add Manually not shown') }
        foreach ($id in 'btnAddManually', 'btnDownload') {
            if ($id -eq 'btnDownload') {
                # noLegal asks for all-files access first ("Permissions required"); "Continue (Limited)"
                # (button3) keeps the system picker path, which is the one both flavors share.
                $pt = Get-DaUiTree -Serial $Serial
                $alert = if ($pt.Ok) { @(Find-DaUiNode -Tree $pt -Id 'alertTitle') | Select-Object -First 1 } else { $null }
                if ($alert -and -not @(Find-DaUiNode -Tree $pt -Id 'btnDownload').Count) {
                    $answer = if (@(Find-DaUiNode -Tree $pt -Id 'button3').Count) { 'button3' } else { 'button2' }
                    $label = (@(Find-DaUiNode -Tree $pt -Id $answer) | Select-Object -First 1).label
                    $null = Invoke-DaUiTap -Serial $Serial -Id $answer
                    $steps.Add("dialog '$($alert.label)' answered '$label'")
                    Start-Sleep -Milliseconds 1500
                }
                if ((Get-DaForeground -Serial $Serial -Package $Package) -match 'documentsui') { $steps.Add('the system picker opened directly'); break }
            }
            $tap = Invoke-DaUiTap -Serial $Serial -Id $id
            $steps.Add("$($tap.How) ok $($tap.Ok)")
            if (-not $tap.Ok) { return (& $fail "$id not tappable: $($tap.Reason)") }
            Start-Sleep -Milliseconds 1800
        }
        $nav = Select-DaSafFolder -Serial $Serial -Segments @('Download', 'FastMediaSorter_DynAudit', $RunId, $leaf)
        foreach ($s in $nav.Steps) { $steps.Add("picker: $s") }
        if (-not $nav.Ok) { return (& $fail "picker for ${leaf}: $($nav.Reason)") }
        $use = Invoke-DaUiTap -Serial $Serial -Id 'button1'
        $steps.Add("use this folder: $($use.How) ok $($use.Ok)")
        Start-Sleep -Milliseconds 1500
        $allowTree = Wait-DaUiNode -Serial $Serial -Id 'button1' -TimeoutMs 8000
        if ($allowTree) {
            $msg = @(Find-DaUiNode -Tree $allowTree -Id 'alertTitle') | Select-Object -First 1
            $allow = Invoke-DaUiTap -Serial $Serial -Id 'button1'
            $steps.Add("allow '$($msg.label)': $($allow.How) ok $($allow.Ok)")
        }
        Start-Sleep -Milliseconds 2000
    }
    $t = Wait-DaUiNode -Serial $Serial -Id 'btnAddToResources' -TimeoutMs 10000
    if (-not $t) { return (& $fail 'the resources-to-add list did not appear') }
    # The default name is not stable (seen: "src", and "<parent>/src" when a resource of that name existed),
    # so each row's name is read by its folder path. The names are not retyped: typing into this field
    # scrambled the characters (run 261011-231041-cc50: '<runId>-src' read back as 'ccc5-261-21').
    $paths = @(Get-DaUiLabels -Tree $t -Id 'tvPath')
    $names = @(Get-DaUiLabels -Tree $t -Id 'etName')
    $srcName = $null; $dstName = $null; $dstIndex = 0
    for ($k = 1; $k -le [math]::Min($paths.Count, $names.Count); $k++) {
        if ($paths[$k - 1] -match "$([regex]::Escape($RunId))%2Fsrc$") { $srcName = $names[$k - 1] }
        elseif ($paths[$k - 1] -match "$([regex]::Escape($RunId))%2Fdst$") { $dstName = $names[$k - 1]; $dstIndex = $k }
    }
    if (-not $srcName -or -not $dstName -or $srcName -eq $dstName) { return (& $fail "resource rows not recognised: names $($names -join ', '); paths $($paths -join ', ')") }
    $steps.Add("rows: src as '$srcName', dst as '$dstName' (row $dstIndex)")
    $isChecked = { param($tree, $i) $c = $tree.Checked['cbDestination']; ($c -and $c.Count -ge $i -and $c[$i - 1]) }
    if (-not (& $isChecked $t $dstIndex)) {
        $tap = Invoke-DaUiTap -Serial $Serial -Id 'cbDestination' -Index $dstIndex
        $steps.Add("mark $dstName for quick sort: $($tap.How) ok $($tap.Ok)")
        $t = Get-DaUiTree -Serial $Serial
        if (-not (& $isChecked $t $dstIndex)) { return (& $fail "destination checkbox of $dstName did not turn on") }
    }
    $add = Invoke-DaUiTap -Serial $Serial -Id 'btnAddToResources'
    $steps.Add("$($add.How) ok $($add.Ok)")
    Start-Sleep -Milliseconds 2500
    $ok = (Get-DaForeground -Serial $Serial -Package $Package) -match 'MainActivity'
    return [pscustomobject]@{ Ok = [bool]$ok; Reason = if ($ok) { 'registered' } else { 'main screen not back after adding' }; Src = $srcName; Dst = $dstName; Steps = $steps.ToArray() }
}

function Open-DaResource {
    <#
    .SYNOPSIS
        Opens a resource by its name from the main list (scrolling the list into view); returns $true when the browse list is shown.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER Name
        Resource name as listed.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][string]$Name)
    # Two attempts: noLegal raises its all-files dialog over the main screen a moment after it resumes,
    # which hides the list from the first search.
    $found = $null
    for ($attempt = 0; $attempt -lt 2 -and -not $found; $attempt++) {
        if (-not (Open-DaAppMain -Serial $Serial -Package $Package)) { return $false }
        $found = Find-DaUiNodeScrolling -Serial $Serial -Id 'tvResourceName' -Label $Name -Exact -ContainerId 'rvResources'
        if (-not $found) { Start-Sleep -Milliseconds 1500 }
    }
    if (-not $found) { return $false }
    $tap = Invoke-DaUiTap -Serial $Serial -Label $Name -Exact
    if (-not $tap.Ok) { return $false }
    # noLegal opens the browse screen under its all-files dialog again; Cancel keeps the browse screen.
    $deadline = (Get-DaMonotonicMs) + 20000
    while ((Get-DaMonotonicMs) -lt $deadline) {
        $bt = Get-DaUiTree -Serial $Serial
        if ($bt.Ok -and @(Find-DaUiNode -Tree $bt -Id 'rvMediaFiles').Count) { return $true }
        if ($bt.Ok -and @(Find-DaUiNode -Tree $bt -Id 'alertTitle').Count -and @(Find-DaUiNode -Tree $bt -Id 'button2').Count) { $null = Invoke-DaUiTap -Serial $Serial -Id 'button2' }
        Start-Sleep -Milliseconds 800
    }
    return $false
}

function Select-DaBrowseFiles {
    <#
    .SYNOPSIS
        Clears the selection and ticks the named files in the open browse list; returns {Ok; Reason; Selected}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Names
        File names as listed.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string[]]$Names)
    $t = Get-DaUiTree -Serial $Serial
    $stale = $false
    $info = if ($t.Ok) { [string](@(Find-DaUiNode -Tree $t -Id 'tvResourceInfo') | Select-Object -First 1).label } else { '' }
    if ($info -match '\d+ selected') { $null = Invoke-DaUiTap -Serial $Serial -Id 'btnDeselectAll'; Start-Sleep -Milliseconds 600; $stale = $true }
    if ($t.Ok -and @(Find-DaUiNode -Tree $t -Id 'btnSearchClose').Count) { $null = Invoke-DaUiTap -Serial $Serial -Id 'btnSearchClose'; Start-Sleep -Milliseconds 800; $stale = $true }
    $done = [System.Collections.Generic.List[string]]::new()
    foreach ($n in $Names) {
        $label = "File $n, select"
        $visible = (-not $stale -and $t.Ok -and @(Find-DaUiNode -Tree $t -Label $label -Exact).Count)
        if (-not $visible -and -not (Find-DaUiNodeScrolling -Serial $Serial -Label $label -Exact -ContainerId 'rvMediaFiles' -MaxSwipes 10)) {
            return [pscustomobject]@{ Ok = $false; Reason = "file '$n' not found in the list"; Selected = $done.ToArray() }
        }
        $stale = $true
        $tap = Invoke-DaUiTap -Serial $Serial -Label $label -Exact
        if (-not $tap.Ok) { return [pscustomobject]@{ Ok = $false; Reason = "selecting '$n' failed: $($tap.Reason)"; Selected = $done.ToArray() } }
        $done.Add($n)
        Start-Sleep -Milliseconds 400
    }
    return [pscustomobject]@{ Ok = $true; Reason = "$($done.Count) selected"; Selected = $done.ToArray() }
}

function Invoke-DaBrowseTransfer {
    <#
    .SYNOPSIS
        Copy or move of the selected files to a destination resource, with the outcome captured; returns {Ok; Reason; Dialog; Trigger}.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    .PARAMETER Kind
        Copy or Move.
    .PARAMETER Destination
        Destination resource name (a button of the destination dialog).
    .PARAMETER WatchSec
        Length of the outcome event stream.
    .PARAMETER During
        Scriptblock run while the stream is open (fault injection inside the operation).
    .PARAMETER BeforeConfirm
        Scriptblock run after the destination dialog is shown and before the destination is tapped.
    #>
    param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Package, [Parameter(Mandatory)][ValidateSet('Copy', 'Move')][string]$Kind,
        [Parameter(Mandatory)][string]$Destination, [int]$WatchSec = 8, [scriptblock]$During, [scriptblock]$BeforeConfirm)
    $tap = Invoke-DaUiTap -Serial $Serial -Id "actionBrowse$Kind"
    if (-not $tap.Ok) { return [pscustomobject]@{ Ok = $false; Reason = "$Kind action not available: $($tap.Reason)"; Dialog = $null; Trigger = $null; BeforeConfirm = $null } }
    $dlg = Wait-DaUiNode -Serial $Serial -Label $Destination -Exact -TimeoutMs 10000
    if (-not $dlg) { return [pscustomobject]@{ Ok = $false; Reason = "destination '$Destination' not offered"; Dialog = $null; Trigger = $null; BeforeConfirm = $null } }
    $dialogText = (@(Find-DaUiNode -Tree $dlg -Id 'tvFileCount') | Select-Object -First 1).label
    $pre = $null
    if ($BeforeConfirm) { $pre = & $BeforeConfirm }
    $trig = Invoke-DaUiTriggerTap -Serial $Serial -Package $Package -Label $Destination -Exact -WatchSec $WatchSec -During $During
    return [pscustomobject]@{ Ok = $trig.Ok; Reason = $trig.Reason; Dialog = $dialogText; Trigger = $trig; BeforeConfirm = $pre }
}

function Resolve-DaPendingDialog {
    <#
    .SYNOPSIS
        Records and answers a dialog left on screen after an operation (conflict, error); prefers a named answer, otherwise the negative button. Returns the texts seen or $null.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Prefer
        Regex of the answer to tap when present (for example Overwrite|Replace).
    .PARAMETER Tree
        A tree read a moment ago; read anew when omitted.
    #>
    param([Parameter(Mandatory)][string]$Serial, [string]$Prefer, $Tree)
    $t = if ($Tree) { $Tree } else { Get-DaUiTree -Serial $Serial }
    if (-not $t.Ok) { return $null }
    $title = @(Find-DaUiNode -Tree $t -Id 'alertTitle') + @(Find-DaUiNode -Tree $t -Id 'tvDialogTitle') | Select-Object -First 1
    if (-not $title) { return $null }
    $texts = @($t.Nodes | Where-Object { $_.labelled } | ForEach-Object { [string]$_.label } | Where-Object { $_ }) | Select-Object -First 12
    $pick = if ($Prefer) { @($t.Nodes | Where-Object { $_.class -match 'Button' -and [string]$_.label -match $Prefer }) | Select-Object -First 1 } else { $null }
    if ($pick) { $null = Invoke-DaUiTap -Serial $Serial -Label ([string]$pick.label) -Exact }
    elseif (@(Find-DaUiNode -Tree $t -Id 'button2').Count) { $null = Invoke-DaUiTap -Serial $Serial -Id 'button2' }
    elseif (@(Find-DaUiNode -Tree $t -Id 'btnCancel').Count) { $null = Invoke-DaUiTap -Serial $Serial -Id 'btnCancel' }
    else { $null = Invoke-DaUiKey -Serial $Serial -Key 'BACK' }
    Start-Sleep -Milliseconds 1200
    return [pscustomobject]@{ Title = [string]$title.label; Texts = $texts; Answer = if ($pick) { [string]$pick.label } else { 'negative' } }
}
