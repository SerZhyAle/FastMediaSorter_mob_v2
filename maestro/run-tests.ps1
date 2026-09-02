<#
.SYNOPSIS
  Maestro capability suite runner (S0551) - off-context, single-line verdict, stable exit codes.

.DESCRIPTION
  Runs a selected set of Maestro flows against a device, writes each flow's full per-step
  trace to an off-context log under temp/, and emits only a compact verdict. The agent reads
  the one-line summary (or, with -Json, a single object) - never the full Maestro output.

  Binary discovery and the infra-vs-assertion exit classification mirror
  scripts/devtest/maestro-run.ps1 (the S0420 single-flow engine). This runner wraps that
  contract over a discovered flow set and aggregates per-flow results into one suite verdict.

  Selection (-Suite):
    all       - emulator-default *.yaml under smoke/, critical/, features/
                (excludes _shared/ fragments, device-only file-operation flows, the inline-audio
                 player flows that need a full player not present on emulator - S0666, and the
                 features/resource flows that need All-Files access)
    smoke      | critical | features      - that category, recursively
    features\browse  (or features/files)  - a single category subpath
    smoke\app_launch.yaml  | a full path  - a single flow file

  Exit codes (suite-level):
    0 - all selected flows passed
    1 - bad arguments / no flow matched the selection
    2 - Maestro CLI not found
    3 - one or more flows failed (step / assertion)
    4 - execution error (no device / runtime error - a flow never completed)

.PARAMETER Suite
  Selection token (see above). Default 'all'.

.PARAMETER DeviceId
  Specific adb device id, forwarded to Maestro as the global --device flag. Omit when exactly
  one device is online.

.PARAMETER Json
  Emit a single JSON object { pass, total, failed, reason, launcherMode, flows:[{flow,pass,log}] }
  instead of human-readable lines. launcherMode is on|off|unknown - the environment state that
  changes where a back press out of MainActivity lands (S1673).

.EXAMPLE
  pwsh -NoProfile -File maestro/run-tests.ps1 -Suite smoke -Json
.EXAMPLE
  pwsh -NoProfile -File maestro/run-tests.ps1 -Suite features\files -DeviceId emulator-5554
#>
[CmdletBinding()]
param(
    [string]$Suite = 'all',
    [string]$DeviceId,
    [switch]$Json,
    # Retained for backward compatibility with existing callers
    # (scripts/utils/run-maestro-smoke.ps1, scripts/utils/run-stress.ps1) which pass -DebugMode.
    # When set, the off-context trace of any failing flow is echoed to the console for local triage.
    [switch]$DebugMode
)

$ErrorActionPreference = 'Stop'

$MaestroDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $MaestroDir
$TempDir     = Join-Path $ProjectRoot 'temp'

# Probed once in the preflight; reported in both output modes so -Json callers see it too (S1673).
$script:LauncherMode = 'unknown'

function Write-Line {
    param([string]$Text, [string]$Color = 'White')
    if (-not $Json) { Write-Host $Text -ForegroundColor $Color }
}

function Exit-Suite {
    param([int]$Code, [bool]$Pass, [int]$Total, [int]$Failed, [array]$Flows, [string]$Reason)
    if ($Json) {
        ([ordered]@{ pass = $Pass; total = $Total; failed = $Failed; reason = $Reason
                     launcherMode = $script:LauncherMode; flows = $Flows } |
            ConvertTo-Json -Depth 5 -Compress)
    } else {
        $verdict = if ($Pass) { 'PASS' } else { 'FAIL' }
        $color   = if ($Pass) { 'Green' } else { 'Red' }
        if ($Reason) { Write-Host "SUITE $verdict ($Code) - $Reason" -ForegroundColor $color }
        else { Write-Host ("SUITE {0} - {1}/{2} flows passed" -f $verdict, ($Total - $Failed), $Total) -ForegroundColor $color }
    }
    exit $Code
}

# ---------- Maestro binary discovery (mirror scripts/devtest/maestro-run.ps1) ----------
function Find-Maestro {
    foreach ($name in 'maestro', 'maestro.bat', 'maestro.cmd', 'maestro.ps1') {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    $roots = @()
    if ($env:MAESTRO_HOME) { $roots += $env:MAESTRO_HOME }
    if ($env:USERPROFILE)  { $roots += (Join-Path $env:USERPROFILE '.maestro') }
    if ($env:HOME)         { $roots += (Join-Path $env:HOME '.maestro') }
    foreach ($root in $roots) {
        foreach ($leaf in 'bin\maestro.bat', 'bin\maestro.cmd', 'bin\maestro') {
            $candidate = Join-Path $root $leaf
            if (Test-Path -Path $candidate -PathType Leaf) { return $candidate }
        }
    }
    return $null
}

# ---------- Android SDK discovery (Maestro shells out to adb to detect devices) ----------
# Maestro needs ANDROID_HOME to locate adb; without it dadb reports "0 devices connected".
# Discover the SDK without hardcoding a user path: honour an existing env var, else the
# standard per-user install location, else the parent of adb on PATH.
function Set-AndroidHome {
    if ($env:ANDROID_HOME -and (Test-Path -Path $env:ANDROID_HOME -PathType Container)) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT -and (Test-Path -Path $env:ANDROID_SDK_ROOT -PathType Container)) {
        $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT; return $env:ANDROID_HOME
    }
    $standard = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -Path $standard -PathType Container) {
        $env:ANDROID_HOME = $standard; $env:ANDROID_SDK_ROOT = $standard; return $standard
    }
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) {
        $sdk = Split-Path -Parent (Split-Path -Parent $adb.Source)  # platform-tools\adb.exe -> SDK root
        if ($sdk -and (Test-Path -Path $sdk -PathType Container)) {
            $env:ANDROID_HOME = $sdk; $env:ANDROID_SDK_ROOT = $sdk; return $sdk
        }
    }
    return $null
}

# ---------- adb discovery + deterministic device input state ----------
function Find-Adb {
    param([string]$Sdk)
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($Sdk) {
        $candidate = Join-Path $Sdk 'platform-tools\adb.exe'
        if (Test-Path -Path $candidate -PathType Leaf) { return $candidate }
    }
    return $null
}

# S1673: launcher mode is invisible in a flow trace but changes where a back press out of
# MainActivity lands (the app's own desktop, not the system home). _shared/go_home.yaml escapes it
# with an explicit relaunch, so this probe never blocks the run - it only names the state in the
# header, so a red that follows a launcher-mode device test is readable as such at a glance.
#
# The mode flag IS the enabled state of the HOME component (LauncherRoleManager.isModeEnabled), so
# read that and nothing else: launcher_role_prefs.xml holds only onboarding bookkeeping and is
# routinely empty while the mode is on. Scope the match to the enabledComponents block - the
# activity resolver table lists the component from the parsed manifest whether it is enabled or not.
function Get-LauncherModeState {
    param([string]$Sdk, [string]$Device)
    $adb = Find-Adb -Sdk $Sdk
    if (-not $adb) { return 'unknown' }
    $target = if ($Device) { @('-s', $Device) } else { @() }
    $dump = $null
    try { $dump = & $adb @target shell dumpsys package com.sza.fastmediasorter.debug 2>$null } catch { return 'unknown' }
    if (-not $dump) { return 'unknown' }
    $inEnabledBlock = $false
    foreach ($line in @($dump)) {
        if ($line -match '^\s*enabledComponents:\s*$') { $inEnabledBlock = $true; continue }
        if (-not $inEnabledBlock) { continue }
        if ($line -notmatch '^\s+\S') { $inEnabledBlock = $false; continue }
        if ($line -match 'LauncherHomeActivity') { return 'on' }
    }
    return 'off'
}

# Stylus handwriting turns text-field taps into a handwriting panel on API 34 tablet images,
# so flows that type into a field (browse filter) never land their text. Force it off for a
# deterministic input state. Non-fatal: a probe failure never blocks the suite.
function Set-DeviceInputDeterminism {
    param([string]$Sdk, [string]$Device)
    $adb = Find-Adb -Sdk $Sdk
    if (-not $adb) { return }
    $target = if ($Device) { @('-s', $Device) } else { @() }
    try {
        & $adb @target shell settings put secure stylus_handwriting_enabled 0 *> $null
    } catch { }
}

# ---------- Maestro local session cleanup ----------
function Clear-MaestroSessions {
    $roots = @()
    if ($env:USERPROFILE) { $roots += (Join-Path $env:USERPROFILE '.maestro') }
    if ($env:HOME) { $roots += (Join-Path $env:HOME '.maestro') }
    foreach ($root in $roots) {
        $sessions = Join-Path $root 'sessions'
        if (-not (Test-Path -Path $sessions -PathType Container)) { continue }
        $resolvedRoot = Resolve-Path -LiteralPath $root -ErrorAction SilentlyContinue
        $resolvedSessions = Resolve-Path -LiteralPath $sessions -ErrorAction SilentlyContinue
        if ($resolvedRoot -and $resolvedSessions -and
            $resolvedSessions.Path.StartsWith($resolvedRoot.Path, [System.StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedSessions.Path -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

# Force-stop the app between a failed flow and its retry so a prior flow's deep screen (an open
# player, a dialog) does not leak into the retry and re-fail it. Non-fatal best effort.
function Reset-App {
    param([string]$Sdk, [string]$Device)
    $adb = Find-Adb -Sdk $Sdk
    if (-not $adb) { return }
    $target = if ($Device) { @('-s', $Device) } else { @() }
    try { & $adb @target shell am force-stop com.sza.fastmediasorter.debug *> $null } catch { }
    Start-Sleep -Milliseconds 800
}

# ---------- flow-set resolution from -Suite ----------
function Get-FlowSet {
    param([string]$Selection)

    # A single flow file. This branch claims every '.yaml' selection, so whatever it cannot resolve
    # is refused outright - the later category branch never sees it. Until 2026-09-02 it resolved
    # only against the repo root, which meant the two forms this script's own help offers
    # ('smoke\app_launch.yaml', a category-relative path) both answered "no flow matched": they live
    # under maestro/, not under the repo root. Measured while re-running one failed flow instead of
    # the whole 25-flow suite. Try each root in turn, then fall back to a bare file name.
    if ($Selection -match '\.ya?ml$') {
        $norm = $Selection -replace '/', '\'
        foreach ($candidate in @($norm, (Join-Path $ProjectRoot $norm), (Join-Path $MaestroDir $norm))) {
            if (Test-Path -Path $candidate -PathType Leaf) { return @(Get-Item -Path $candidate) }
        }
        if ($norm -notmatch '[\\/]') {
            return @(Get-ChildItem -Path $MaestroDir -Recurse -Filter $norm -File)
        }
        return @()
    }

    if ($Selection.ToLower() -eq 'all') {
        return @(Get-ChildItem -Path $MaestroDir -Recurse -Filter '*.yaml' -File |
            Where-Object {
                $_.FullName -match '[\\/](smoke|critical|features)[\\/]' -and
                $_.FullName -notmatch '[\\/]features[\\/]files[\\/]' -and
                $_.FullName -notmatch '[\\/]critical[\\/]file_operations\.yaml$' -and
                # Resource create/edit/delete flows need All-Files access (MANAGE_EXTERNAL_STORAGE)
                # granted for the debug package; excluded from the bare-emulator default, run via
                # -Suite features\resource once storage access is set up.
                $_.FullName -notmatch '[\\/]features[\\/]resource[\\/]' -and
                # Audio flows assume a full-screen audio player (playerView / btnPlaybackControl), but
                # audio plays INLINE in the browse list (the row's btnPlayInline; the activity stays
                # BrowseActivity) and the inline state is not introspectable by UiAutomator on the
                # emulator, so these cannot be reliably driven here. Excluded from the emulator default;
                # redesign for the inline model is tracked in S0666.
                $_.FullName -notmatch '[\\/]features[\\/]player[\\/]player_(audio_lyrics|info_dialog)\.yaml$'
            })
    }

    # A category (smoke|critical|features) or a subpath (features\browse).
    $norm = $Selection -replace '/', '\'
    $dir  = Join-Path $MaestroDir $norm
    if (Test-Path -Path $dir -PathType Container) {
        return @(Get-ChildItem -Path $dir -Recurse -Filter '*.yaml' -File)
    }

    return @()
}

# ---------- run one flow, capture trace off-context ----------
function Invoke-Flow {
    param([string]$Maestro, [System.IO.FileInfo]$FlowFile)

    # Maestro 2.x can leave a local session lock after stopApp/relaunch-heavy flows; clearing the
    # per-user session cache before each isolated CLI invocation keeps the suite deterministic.
    Clear-MaestroSessions

    $stamp   = (Get-Date).ToString('yyyyMMdd_HHmmss')
    $logFile = Join-Path $TempDir ("{0}_maestro_{1}.log" -f $FlowFile.BaseName, $stamp)

    $argList = @()
    if ($DeviceId) { $argList += @('--device', $DeviceId) }
    $argList += @('test', $FlowFile.FullName)

    & $Maestro @argList *> $logFile
    $maestroExit = $LASTEXITCODE

    $status = 'pass'
    if ($maestroExit -ne 0) {
        $logText = ''
        if (Test-Path -Path $logFile -PathType Leaf) {
            $logText = (Get-Content -Path $logFile -Raw -ErrorAction SilentlyContinue)
        }
        $infra = 'no devices|no connected device|not enough devices|Missing \d+ device|it is not connected|Unable to launch|Unable to find|connection refused|locked a portion of the file|MAESTRO_CLI|java\.lang|ADB'
        if ($logText -match $infra -and $logText -notmatch 'Assertion|Element .*not|not visible|did not') {
            $status = 'execError'
        } else {
            $status = 'fail'
        }
    }
    return [ordered]@{ flow = $FlowFile.Name; status = $status; pass = ($status -eq 'pass'); log = $logFile }
}

# ---------- main ----------
if (-not (Test-Path -Path $TempDir -PathType Container)) {
    New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
}

$maestro = Find-Maestro
if (-not $maestro) {
    Exit-Suite -Code 2 -Pass $false -Total 0 -Failed 0 -Flows @() `
        -Reason 'Maestro CLI not found (checked PATH, MAESTRO_HOME, %USERPROFILE%\.maestro\bin) - see maestro/INSTALLATION_WINDOWS.md'
}
Write-Line "OK maestro: $maestro" 'Green'

$sdk = Set-AndroidHome
Write-Line "OK android-sdk: $(if ($sdk) { $sdk } else { '(not found - Maestro may not detect devices)' })" $(if ($sdk) { 'Green' } else { 'Yellow' })

# Deterministic device input state (stylus handwriting off) so text-entry flows are reliable.
Set-DeviceInputDeterminism -Sdk $sdk -Device $DeviceId

# Environment state that changes flow behaviour without appearing in any flow trace (S1673).
$script:LauncherMode = Get-LauncherModeState -Sdk $sdk -Device $DeviceId
switch ($script:LauncherMode) {
    'on' {
        Write-Line ('WARN launcher-mode: ON - the app is a home-screen candidate, so leaving MainActivity ' +
            'lands on its own desktop; _shared/go_home.yaml relaunches to escape it (S1673). ' +
            'Turn it off in Settings > General if a flow still fails on the desktop.') 'Yellow'
    }
    'off'   { Write-Line 'OK launcher-mode: off' 'Green' }
    default { Write-Line 'OK launcher-mode: (not probed - adb unavailable)' 'Yellow' }
}

$flows = Get-FlowSet -Selection $Suite
if ($flows.Count -eq 0) {
    Exit-Suite -Code 1 -Pass $false -Total 0 -Failed 0 -Flows @() `
        -Reason "no flow matched selection '$Suite' (try: all | smoke | critical | features | features\\<category> | <flow>.yaml)"
}

Write-Line ("RUN suite '{0}': {1} flow(s) on device {2}" -f $Suite, $flows.Count, $(if ($DeviceId) { $DeviceId } else { '(auto)' })) 'Cyan'

$results  = @()
$anyExec  = $false
$anyFail  = $false
foreach ($flow in $flows) {
    $r = Invoke-Flow -Maestro $maestro -FlowFile $flow
    # Single retry for a transient assertion failure. Emulator UI suites flake run-to-run (state
    # contamination between flows, scroll determinism); an isolated re-run almost always passes, and a
    # failing flow can leave the app on a deep screen that cascades into the next flow's go_home.
    # Force-stop first so the retry starts clean. Execution errors (infra) are not retried - exit 4.
    if ($r.status -eq 'fail') {
        Write-Line ("  RETRY    {0}  (first attempt failed - transient flake guard)" -f $r.flow) 'Yellow'
        Reset-App -Sdk $sdk -Device $DeviceId
        $r2 = Invoke-Flow -Maestro $maestro -FlowFile $flow
        if ($r2.pass) { $r = $r2 }
    }
    $results += $r
    if ($r.status -eq 'execError') { $anyExec = $true }
    if ($r.status -eq 'fail')      { $anyFail = $true }
    $mark = switch ($r.status) { 'pass' { 'PASS' } 'fail' { 'FAIL' } default { 'EXEC-ERR' } }
    $col  = if ($r.pass) { 'Green' } else { 'Red' }
    Write-Line ("  {0,-8} {1}  (log: {2})" -f $mark, $r.flow, $r.log) $col

    # -DebugMode: surface the failing flow's trace tail for local triage without breaking the
    # off-context contract (full trace still lives in the log file; only a tail is echoed).
    if ($DebugMode -and -not $r.pass -and -not $Json -and (Test-Path -Path $r.log -PathType Leaf)) {
        Write-Host "    --- $($r.flow) trace tail ---" -ForegroundColor DarkGray
        Get-Content -Path $r.log -Tail 25 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
    }
}

$failedCount = @($results | Where-Object { -not $_.pass }).Count
$flowsOut    = @($results | ForEach-Object { [ordered]@{ flow = $_.flow; pass = $_.pass; log = $_.log } })

# Precedence: execution error (4) outranks assertion failure (3); both outrank pass.
if ($anyExec) {
    Exit-Suite -Code 4 -Pass $false -Total $flows.Count -Failed $failedCount -Flows $flowsOut -Reason 'execution error (no device / runtime) in one or more flows'
}
if ($anyFail) {
    Exit-Suite -Code 3 -Pass $false -Total $flows.Count -Failed $failedCount -Flows $flowsOut -Reason $null
}
Exit-Suite -Code 0 -Pass $true -Total $flows.Count -Failed 0 -Flows $flowsOut -Reason $null
