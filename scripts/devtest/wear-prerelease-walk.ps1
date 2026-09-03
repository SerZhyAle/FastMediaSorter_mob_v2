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

    Four outcomes per screen, and the difference between them matters more than the count:
      observed - the expected token was in the UI dump.
      failed   - the tap errored, or the dump succeeded and the token was not there.
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
      1  at least one screen failed, an OFF-GLASS finding was recorded, or the log audit reported a finding
      2  could not verify: the screen list is missing or unreadable, no device, or a called script
         is absent. A screen recorded `manual` does not by itself set this code - it is reported and
         carried into the verdict, which is what refuses the PASS.
#>
[CmdletBinding()]
param(
    [string]$DeviceId,

    [string]$OutDir = 'temp/scratch/wear-prerelease',

    [string]$ScreenList,

    # Milliseconds to let a screen settle before its tree is read. A per-entry `settleMs` overrides it.
    [int]$SettleMs = 1200,

    # How many times a control may be scrolled toward before the walk calls it unreachable. A per-entry
    # `maxScrolls` overrides it.
    [int]$MaxScrolls = 4,

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
if (-not $ScreenList) { $ScreenList = Join-Path $PSScriptRoot 'wear-prerelease-screens.json' }

$result = [ordered]@{
    ok            = $false
    exitCode      = 2
    device        = $null
    outDir        = $null
    screenSize    = $null
    screens       = @()
    counts        = $null
    logFile       = $null
    logAuditExit  = $null
    skipLogAudit  = [bool]$SkipLogAudit
    skipShapeCheck= [bool]$SkipShapeCheck
    reason        = $null
}

function Stop-Run {
    param([int]$Code, [string]$Reason)
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

# Scroll geometry, read from the device rather than assumed: a round watch, a square one and an
# emulator do not share a screen size, and a swipe sized for one of them misses on the others.
$sizeProbe = Invoke-AdbVerb -Arguments @('shell', '-Cmd', 'wm size')
$screenW = 480
$screenH = 480
if ($sizeProbe.Exit -eq 0 -and $sizeProbe.Output -match '(\d+)x(\d+)') {
    $screenW = [int]$Matches[1]
    $screenH = [int]$Matches[2]
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
    return @($nodes | ForEach-Object { "$($_.label) $($_.resId)" }) -join "`n"
}

function Reset-ListToTop {
    # Put the list back where the previous entry found it. Every entry then starts from one known
    # position instead of from wherever its predecessor happened to stop, which is what makes two runs
    # of this walk comparable at all - the whole point of a declared screen list.
    param([int]$Times, [int]$SettleFor)
    for ($u = 0; $u -lt $Times; $u++) {
        Invoke-AdbVerb -Arguments @('swipe', '-X', $swipeUp.X1, '-Y', $swipeUp.Y1, '-X2', $swipeUp.X2, '-Y2', $swipeUp.Y2, '-Duration', '400') | Out-Null
    }
    if ($Times -gt 0) { Start-Sleep -Milliseconds $SettleFor }
}

$rows = @()

foreach ($screen in $screens) {
    $row = [ordered]@{
        id       = $screen.id
        name     = $screen.name
        outcome  = 'manual'
        detail   = $null
        shot     = $null
        rehomed  = $false
    }

    # Settle before reaching for the control too: the previous entry's BACK is still animating when
    # this iteration starts, and a tap verb re-reads the tree itself, so it would search the screen
    # that is on its way out.
    $entrySettleMs = if ($null -ne $screen.settleMs) { [int]$screen.settleMs } else { $SettleMs }
    Start-Sleep -Milliseconds $entrySettleMs

    # Is the app still in front? One BACK too many leaves it, and everything after that is measured
    # against the watch launcher while still being reported as this app's screens - nine failures in a
    # row on 2026-08-26, of which one was real. Re-entering costs a launch; not re-entering costs the
    # rest of the run, so the walk re-homes and says it did rather than carrying on blind.
    $current = Invoke-AdbVerb -Arguments @('current')
    if ($current.Exit -eq 0 -and $current.Output -notmatch [regex]::Escape($APP_PACKAGE)) {
        $relaunch = Invoke-AdbVerb -Arguments @('launch', '-Module', 'wear', '-Release')
        Start-Sleep -Milliseconds $entrySettleMs
        $row.rehomed = $true
        if (-not $Json) { Write-Host "walk: $($screen.id) - app was not in front, relaunched (exit $($relaunch.Exit))" -ForegroundColor Yellow }
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
        # Start from the top of whatever list this is. The hunt below travels one way only, so without
        # this an entry sitting above the previous entry's stopping point is unreachable no matter how
        # many times it scrolls - and which entries those are depends on where the last one stopped,
        # which is precisely the run-to-run divergence this list exists to remove.
        Reset-ListToTop -Times $scrolls -SettleFor $entrySettleMs
        for ($try = 0; $try -le $scrolls; $try++) {
            $tap = if ($screen.resourceId) {
                Invoke-AdbVerb -Arguments @('tap-id', '-ResourceId', $screen.resourceId)
            } else {
                Invoke-AdbVerb -Arguments @('tap-label', '-Label', $screen.label)
            }
            if ($tap.Exit -eq 0) { break }
            if ($try -lt $scrolls) {
                Invoke-AdbVerb -Arguments @('swipe', '-X', $swipe.X1, '-Y', $swipe.Y1, '-X2', $swipe.X2, '-Y2', $swipe.Y2, '-Duration', '400') | Out-Null
                Start-Sleep -Milliseconds $entrySettleMs
            }
        }
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
        $row.outcome = 'failed'
        $row.detail = "could not reach the screen: $($tap.Output)"
        $rows += [pscustomobject]$row
        if (-not $Json) { Write-Host "walk: $($screen.id) -> failed (tap)" -ForegroundColor Red }
        continue
    }

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
    # bottom of a scrolling keypad, `FastMedia Wear` is the home list's header ABOVE its resting
    # position, and both were reported missing from screens that were plainly showing (S1984).
    # Downwards first, because that is where most of a list is; then back to the top for a marker the
    # list had already scrolled past.
    if (-not $present -and $null -ne $tree) {
        for ($hunt = 0; $hunt -lt $MaxScrolls; $hunt++) {
            Invoke-AdbVerb -Arguments @('swipe', '-X', $swipe.X1, '-Y', $swipe.Y1, '-X2', $swipe.X2, '-Y2', $swipe.Y2, '-Duration', '400') | Out-Null
            Start-Sleep -Milliseconds $settleMs
            $haystack = Read-UiNodes
            if ($null -ne $haystack -and $haystack -match [regex]::Escape([string]$screen.expect)) { $present = $true; break }
        }
    }
    if (-not $present -and $null -ne $tree) {
        for ($hunt = 0; $hunt -lt ($MaxScrolls * 2); $hunt++) {
            Invoke-AdbVerb -Arguments @('swipe', '-X', $swipeUp.X1, '-Y', $swipeUp.Y1, '-X2', $swipeUp.X2, '-Y2', $swipeUp.Y2, '-Duration', '400') | Out-Null
            Start-Sleep -Milliseconds $settleMs
            $haystack = Read-UiNodes
            if ($null -ne $haystack -and $haystack -match [regex]::Escape([string]$screen.expect)) { $present = $true; break }
        }
    }

    if (-not $tree) {
        $row.detail = 'the UI tree could not be read or carried no node on any attempt'
        $rows += [pscustomobject]$row
        if (-not $Json) { Write-Host "walk: $($screen.id) -> manual (no usable dump)" -ForegroundColor Yellow }
        continue
    }

    $shapeFailed = $false
    if ($present) {
        $row.outcome = 'observed'
        if (-not $SkipShapeCheck) {
            $clip = Invoke-AdbVerb -Arguments @('clip-check')
            $row['shapeExit'] = $clip.Exit
            if ($clip.Exit -ne 0) {
                $row['shapeDetail'] = $clip.Output
                $shapeFailed = $true
            }
        }
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

    $shot = Invoke-AdbVerb -Arguments @('shot', '-OutDir', $outPath)
    if ($shot.Exit -eq 0) {
        $shotLine = ($shot.Output -split "`r?`n" | Where-Object { $_ -match '\.png' } | Select-Object -First 1)
        $row.shot = $shotLine
    }

    $rows += [pscustomobject]$row
    if (-not $Json) {
        $colour = switch ($row.outcome) { 'observed' { if ($shapeFailed) { 'Red' } else { 'Green' } } 'failed' { 'Red' } default { 'Yellow' } }
        $shapeNote = if ($shapeFailed) { " (OFF-GLASS)" } else { "" }
        Write-Host "walk: $($screen.id) -> $($row.outcome)$shapeNote" -ForegroundColor $colour
    }

    # How many levels this entry sits above the next one. A nested block - the settings pages, the
    # mini-programs - declares 0 on the section it opens and 1 on each page inside it, so the walk
    # comes back out by the same number of steps it went in by.
    $backAfter = if ($null -ne $screen.backAfter) { [int]$screen.backAfter } else { 1 }
    for ($b = 0; $b -lt $backAfter; $b++) { Invoke-AdbVerb -Arguments @('key', '-Key', 'BACK') | Out-Null }
}

$result.screens = $rows
$shapeFailuresCount = @($rows | Where-Object { $_.shapeExit -and $_.shapeExit -ne 0 }).Count
$result.counts = [ordered]@{
    observed      = @($rows | Where-Object { $_.outcome -eq 'observed' }).Count
    failed        = @($rows | Where-Object { $_.outcome -eq 'failed' }).Count
    manual        = @($rows | Where-Object { $_.outcome -eq 'manual' }).Count
    shapeFailures = $shapeFailuresCount
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

$walkPath = Join-Path $outPath 'walk.json'
[pscustomobject]$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $walkPath -Encoding UTF8

$verdict = if ($result.counts.failed -gt 0 -or $shapeFailuresCount -gt 0 -or $result.logAuditExit -eq 1) { 1 }
           elseif ($result.logAuditExit -eq 2) { 2 }
           else { 0 }

$result.exitCode = $verdict
$result.ok = ($verdict -eq 0)

if ($Json) { [pscustomobject]$result | ConvertTo-Json -Depth 8 -Compress }
else {
    Write-Host ("wear-prerelease-walk: observed $($result.counts.observed), failed $($result.counts.failed), manual $($result.counts.manual), shapeFailures $shapeFailuresCount; log audit $($result.logAuditExit); walk $walkPath") -ForegroundColor Cyan
}
exit $verdict
