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
    launcher_home_smoke.yaml              - a bare file name, found anywhere under maestro/

  A '.yaml' selection that no root resolves falls back to its FILE NAME, searched under maestro/.
  That is deliberate: the category a flow lives in is not something the operator re-checking one
  failed flow should have to remember, and both forms the owner reached for on 2026-09-02 named
  the wrong directory with the right file (S2396).

  -ListFlows resolves the selection, prints it and exits - no Maestro binary, no device. It is the
  only way to verify a selector form without an emulator.

  Exit codes (suite-level):
    0 - all selected flows passed
    1 - bad arguments / no flow matched the selection
    2 - Maestro CLI not found
    3 - one or more flows failed (step / assertion)
    4 - execution error (no device / runtime error - a flow never completed)
    5 - the 'wear' suite was aimed at a device that does not report a watch, so NOTHING was run
        (S2548). Both modules publish under one applicationId, so a watch flow pointed at a phone
        launches the phone app, passes its opening steps and returns a verdict about the wrong
        platform - the serial is the only thing that distinguishes them

  With -ListFlows only 0 (the selection resolved to at least one flow) and 1 (it resolved to none)
  are reachable - nothing is run, so no verdict beyond the selection exists.

.PARAMETER Suite
  Selection token (see above). Default 'all'.

.PARAMETER DeviceId
  Specific adb device id, forwarded to Maestro as the global --device flag. Omit when exactly
  one device is online.

.PARAMETER Json
  Emit a single JSON object
  { pass, total, failed, skipped, reason, launcherMode, flows:[{flow,pass,status,skipReason,log}] }
  instead of human-readable lines. launcherMode is on|off|unknown - the environment state that
  changes where a back press out of MainActivity lands (S1673). status is pass|fail|execError|skip.
  execError is a transport failure between Maestro and the device and says nothing about the app,
  so a consumer aggregating a release verdict must not count it as a defect (S2396). skip means the
  flow was never run because its declared precondition could not be established here (S2720) - also
  not a defect, and skipReason carries the sentence explaining it.

.PARAMETER AllowHomeRoleGrant
  Permit flows marked '# maestro-requires: home-role' to take the ROLE_HOME system role on a
  PHYSICAL device. Emulators are allowed without it. Off by default because whether a given handset
  may be handed a system role is a per-device authorization recorded in docs/DEVICE_FLEET.md, and
  this script must point at that file rather than carry a copy of it. Without the switch such flows
  are reported as skip, never as fail. The previous role holder is restored either way.

.PARAMETER ListFlows
  Resolve -Suite, print the flow set and exit (0 = at least one flow, 1 = none). Runs before
  binary discovery and before any device call, so a selector form can be verified with no
  emulator attached.

.EXAMPLE
  pwsh -NoProfile -File maestro/run-tests.ps1 -Suite smoke -Json
.EXAMPLE
  pwsh -NoProfile -File maestro/run-tests.ps1 -Suite features\files -DeviceId emulator-5554
.EXAMPLE
  pwsh -NoProfile -File maestro/run-tests.ps1 -Suite launcher_home_smoke.yaml -ListFlows
#>
[CmdletBinding()]
param(
    [string]$Suite = 'all',
    [string]$DeviceId,
    [switch]$Json,
    [switch]$ListFlows,
    [switch]$AllowHomeRoleGrant,
    # S2548: asserts the stand carries the media a '# maestro-requires: seeded-content' flow needs.
    # A switch rather than a probe: what counts as seeded differs per flow, and a wrong guess here
    # turns a skip into a red flow about the stand rather than about the app.
    [switch]$WithSeededContent,
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
    param([int]$Code, [bool]$Pass, [int]$Total, [int]$Failed, [int]$Skipped = 0, [array]$Flows, [string]$Reason)
    if ($Json) {
        ([ordered]@{ pass = $Pass; total = $Total; failed = $Failed; skipped = $Skipped; reason = $Reason
                     launcherMode = $script:LauncherMode; flows = $Flows } |
            ConvertTo-Json -Depth 5 -Compress)
    } else {
        $verdict = if ($Pass) { 'PASS' } else { 'FAIL' }
        $color   = if ($Pass) { 'Green' } else { 'Red' }
        # A skipped flow is neither passed nor failed, so it is named on its own rather than folded
        # into either count - a summary reading "23/25 passed" with no third number is how a
        # precondition that never held reads as coverage that did (S2720).
        $skipNote = if ($Skipped -gt 0) { ", $Skipped skipped" } else { '' }
        if ($Reason) { Write-Host "SUITE $verdict ($Code) - $Reason$skipNote" -ForegroundColor $color }
        else { Write-Host ("SUITE {0} - {1}/{2} flows passed{3}" -f $verdict, ($Total - $Failed - $Skipped), $Total, $skipNote) -ForegroundColor $color }
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

# The serial this run actually targets. -DeviceId wins; otherwise the sole online device is read
# from adb, which is the same device Maestro picks when no --device is passed. Returns $null when
# adb is unavailable or the count is not exactly one - an ambiguous target must not be guessed at
# when the answer decides whether a system role may be granted.
function Resolve-TargetDevice {
    param([string]$Sdk, [string]$Device)
    if ($Device) { return $Device }
    $adb = Find-Adb -Sdk $Sdk
    if (-not $adb) { return $null }
    try { $out = & $adb devices 2>$null } catch { return $null }
    $online = @($out) | Where-Object { $_ -match '^(\S+)\s+device\s*$' } | ForEach-Object { $Matches[1] }
    if (@($online).Count -eq 1) { return $online[0] }
    return $null
}

# ---------- ROLE_HOME precondition for launcher flows (S2720) ----------
# A flow declares the requirement in its own header, `# maestro-requires: home-role`, because the
# alternative is a list of file names inside this script that drifts from the flows it names.
function Get-FlowRequirements {
    param([System.IO.FileInfo]$FlowFile)
    $head = Get-Content -Path $FlowFile.FullName -TotalCount 20 -ErrorAction SilentlyContinue
    $tokens = @()
    foreach ($line in @($head)) {
        $m = [regex]::Match($line, '^\s*#\s*maestro-requires:\s*([a-z0-9-]+)\s*$')
        if ($m.Success) { $tokens += $m.Groups[1].Value }
    }
    return , $tokens
}

function Test-FlowRequiresHomeRole {
    param([System.IO.FileInfo]$FlowFile)
    return (Get-FlowRequirements -FlowFile $FlowFile) -contains 'home-role'
}

# S2548: the watch reports its form factor, and content the stand does not carry cannot be conjured,
# so a flow that needs either says so in its own header and is SKIPPED rather than failed. The token
# spellings are the excluded[] reason codes of scripts/devtest/wear-prerelease-screens.json - a second
# vocabulary for the same idea is how the walk and the suite come to disagree about why a path is out
# of reach.
function Get-UnmetRequirement {
    param([System.IO.FileInfo]$FlowFile, [string]$Sdk, [string]$Device)
    foreach ($token in (Get-FlowRequirements -FlowFile $FlowFile)) {
        switch ($token) {
            'watch' {
                if (-not (Test-WatchTarget -Sdk $Sdk -Device $Device)) {
                    return 'requires a watch and the target does not report one in ro.build.characteristics'
                }
            }
            'seeded-content' {
                if (-not $WithSeededContent) {
                    return 'requires seeded media on the device; pass -WithSeededContent once the stand carries it'
                }
            }
        }
    }
    return $null
}

# The form-factor read, shared by the suite guard and the per-flow 'watch' precondition so the two
# cannot answer differently about the same device.
function Test-WatchTarget {
    param([string]$Sdk, [string]$Device)
    $adb = Find-Adb -Sdk $Sdk
    if (-not $adb) { return $false }
    $target = if ($Device) { @('-s', $Device) } else { @() }
    try {
        $chars = (& $adb @target shell getprop ro.build.characteristics 2>$null) -join ''
    } catch { return $false }
    return ($chars -match 'watch')
}

# The single package currently holding the home role, or $null when it cannot be read. `cmd role`
# prints one holder per line; ROLE_HOME is exclusive, so the first line is the answer.
function Get-HomeRoleHolder {
    param([string]$Sdk, [string]$Device)
    $adb = Find-Adb -Sdk $Sdk
    if (-not $adb) { return $null }
    $target = if ($Device) { @('-s', $Device) } else { @() }
    try {
        $out = & $adb @target shell cmd role get-role-holders android.app.role.HOME 2>$null
    } catch { return $null }
    $first = @($out) | Where-Object { $_ -and $_.Trim() } | Select-Object -First 1
    if (-not $first) { return $null }
    return $first.Trim()
}

# Hands the home role to $Package and confirms the change by reading it back. Returns $true only on
# a verified change: `cmd role add-role-holder` reports failure through an exception trace on stdout
# rather than a non-zero exit, so the read-back IS the check.
function Set-HomeRoleHolder {
    param([string]$Sdk, [string]$Device, [string]$Package)
    $adb = Find-Adb -Sdk $Sdk
    if (-not $adb -or -not $Package) { return $false }
    $target = if ($Device) { @('-s', $Device) } else { @() }
    try {
        & $adb @target shell cmd role add-role-holder android.app.role.HOME $Package *> $null
    } catch { return $false }
    return ((Get-HomeRoleHolder -Sdk $Sdk -Device $Device) -eq $Package)
}

# Whether taking a system role on this target is authorized. Emulators are created and destroyed
# freely, so they need no opt-in; a physical handset needs -AllowHomeRoleGrant, because which serial
# may be handed a system role is recorded in docs/DEVICE_FLEET.md and restating a per-device
# permission inside a script is how such a copy goes stale unnoticed (CLAUDE.md Rule 35).
function Test-HomeRoleGrantAllowed {
    param([string]$Device)
    if ($AllowHomeRoleGrant) { return $true }
    return ($Device -match '^emulator-\d+$')
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
function Get-RelativeFlowPath {
    param([System.IO.FileInfo]$File)
    $full = $File.FullName
    if ($full.StartsWith($ProjectRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $full.Substring($ProjectRoot.Length).TrimStart('\', '/') -replace '\\', '/'
    }
    return $full
}

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
        # Last resort: the file NAME, searched under maestro/. Until 2026-09-02 this ran only for a
        # selector with no separator, which left the two forms the owner actually typed unresolved -
        # both named a real flow under the wrong directory (launcher_home_smoke.yaml lives in
        # features/launcher, not smoke/; settings_dropdown_select.yaml in smoke/, not
        # features/settings). Flow file names are unique across the tree, so the directory carries no
        # information the name does not; refusing on it only costs a half-hour full-suite re-run (S2396).
        # S2548: refuse an ambiguous name rather than returning every match. Once the watch owns a root
        # of its own, one file name can resolve on two platforms, and silently widening a single-flow
        # re-run into two runs on two devices is the failure this fallback would otherwise introduce.
        $byName = @(Get-ChildItem -Path $MaestroDir -Recurse -Filter (Split-Path -Leaf $norm) -File)
        if ($byName.Count -gt 1) {
            $named = ($byName | ForEach-Object { Get-RelativeFlowPath -File $_ }) -join ', '
            Exit-Suite -Code 1 -Pass $false -Total 0 -Failed 0 -Flows @() `
                -Reason ("selection '{0}' matches {1} flows - name one of them by its path: {2}" -f $Selection, $byName.Count, $named)
        }
        return $byName
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
        # S2548: a suite root may own a 'config.yaml' - Maestro's per-root configuration, which
        # declares appId and timeouts and carries no commands. Handing it to the CLI as a flow makes
        # every watch run report one permanent FAIL beside its passing flows, so a suite that did
        # exactly what it was written to do never returns exit 0 and can never become a gate.
        return @(Get-ChildItem -Path $dir -Recurse -Filter '*.yaml' -File |
            Where-Object { $_.Name -ne 'config.yaml' })
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
        # Two tiers, because the single tier that preceded them misread the one case it existed for
        # (S2396). A transport crash - Maestro's own ADB client throwing out of a flow - is named by
        # its stack frame and nothing else can produce it, so it wins outright. The weak tier keeps
        # the old broad wording and its assertion-text veto: those phrases also occur in ordinary
        # traces, so they may only classify a trace that says nothing about an assertion. Measured
        # 2026-09-02: a maestro.android.AdbSocket.connect crash in settings_dropdown_select.yaml was
        # vetoed by the assertion text a half-run trace always carries, and reached the release
        # verdict as an app defect.
        $infraDefinitive = 'maestro\.android\.AdbSocket|dadb\.|AdbShellStream|no devices|no connected device|not enough devices|Missing \d+ device|it is not connected|Unable to launch|connection refused'
        $infraWeak       = 'Unable to find|locked a portion of the file|MAESTRO_CLI|java\.lang|ADB'
        if ($logText -match $infraDefinitive) {
            $status = 'execError'
        } elseif ($logText -match $infraWeak -and $logText -notmatch 'Assertion|Element .*not|not visible|did not') {
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

# -ListFlows answers the selection question alone. It runs before binary discovery and before any
# adb call on purpose: every other path here needs a device, so without it no selector form can be
# verified except by starting a suite (S2396).
if ($ListFlows) {
    $listed = Get-FlowSet -Selection $Suite
    foreach ($f in $listed) { Write-Host (Get-RelativeFlowPath $f) }
    Write-Host ("selection '{0}': {1} flow(s)" -f $Suite, $listed.Count)
    exit $(if ($listed.Count -gt 0) { 0 } else { 1 })
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

# S2548: the watch suite refuses a target that is not a watch instead of running the phone app under
# a watch flow's name. Checked before the first flow so nothing is executed on the wrong platform,
# and applied whether the serial was given or resolved implicitly - taking "the single online device"
# is exactly how the wrong one gets picked.
if (@($flows | Where-Object { $_.FullName -match '[\\/]wear[\\/]' }).Count -gt 0) {
    $wearTarget = Resolve-TargetDevice -Sdk $sdk -Device $DeviceId
    if (-not (Test-WatchTarget -Sdk $sdk -Device $wearTarget)) {
        Exit-Suite -Code 5 -Pass $false -Total 0 -Failed 0 -Flows @() `
            -Reason ("suite '{0}' selects watch flows, but device '{1}' does not report a watch in ro.build.characteristics - nothing was run. Aim it with -DeviceId <watch serial>" -f $Suite, $(if ($wearTarget) { $wearTarget } else { '(unresolved)' }))
    }
}

Write-Line ("RUN suite '{0}': {1} flow(s) on device {2}" -f $Suite, $flows.Count, $(if ($DeviceId) { $DeviceId } else { '(auto)' })) 'Cyan'

$results  = @()
$anyExec  = $false
$anyFail  = $false
$targetDevice = Resolve-TargetDevice -Sdk $sdk -Device $DeviceId
foreach ($flow in $flows) {
    # S2720: a flow that needs the home role gets it taken and given back around its own run, and is
    # skipped rather than failed when that cannot be arranged here. Restoring is not housekeeping:
    # while this app holds the role, the NEXT flow's _shared/go_home.yaml would reach a stopApp on
    # the device's home app and hang Maestro, so an unrestored role turns two red flows into a stuck
    # suite. Both the previous holder and the read-back live here rather than in the flow because a
    # flow has neither adb nor the serial.
    # S2548: an environment precondition the stand cannot meet is a skip with its token as the reason,
    # never a failure - a red flow about a missing file reads as a defect in the app.
    $unmet = Get-UnmetRequirement -FlowFile $flow -Sdk $sdk -Device $targetDevice
    if ($unmet) {
        $skippedEnv = [ordered]@{ flow = $flow.Name; status = 'skip'; pass = $false; skipReason = $unmet; log = $null }
        $results += $skippedEnv
        Write-Line ("  {0,-8} {1}  ({2})" -f 'SKIP', $skippedEnv.flow, $unmet) 'Yellow'
        continue
    }

    $needsHomeRole    = Test-FlowRequiresHomeRole -FlowFile $flow
    $previousRoleHolder = $null
    if ($needsHomeRole) {
        $skipReason = $null
        if (-not (Find-Adb -Sdk $sdk)) {
            $skipReason = 'requires the ROLE_HOME system role and adb is unavailable, so the role cannot be restored afterwards'
        } elseif (-not $targetDevice) {
            $skipReason = 'requires the ROLE_HOME system role and the target device could not be resolved (pass -DeviceId when more than one is online)'
        } elseif (-not (Test-HomeRoleGrantAllowed -Device $targetDevice)) {
            $skipReason = ("requires the ROLE_HOME system role on physical device '{0}'; pass -AllowHomeRoleGrant only after checking that serial in docs/DEVICE_FLEET.md" -f $targetDevice)
        } else {
            $previousRoleHolder = Get-HomeRoleHolder -Sdk $sdk -Device $targetDevice
            if (-not $previousRoleHolder) {
                $skipReason = 'requires the ROLE_HOME system role and the current holder could not be read, so it could not be restored afterwards'
            }
        }
        if ($skipReason) {
            $skipped = [ordered]@{ flow = $flow.Name; status = 'skip'; pass = $false; skipReason = $skipReason; log = $null }
            $results += $skipped
            Write-Line ("  {0,-8} {1}  ({2})" -f 'SKIP', $skipped.flow, $skipReason) 'Yellow'
            continue
        }
    }

    $r = Invoke-Flow -Maestro $maestro -FlowFile $flow
    # Single retry for a transient assertion failure. Emulator UI suites flake run-to-run (state
    # contamination between flows, scroll determinism); an isolated re-run almost always passes, and a
    # failing flow can leave the app on a deep screen that cascades into the next flow's go_home.
    # Force-stop first so the retry starts clean. An execution error is retried on the same budget
    # (S2396): a dropped ADB socket is transient in exactly the way an assertion flake is, and the
    # 2026-09-02 run that lost settings_dropdown_select.yaml to one would have passed on a re-run.
    # One attempt only, so a device that is genuinely gone still ends the flow instead of looping.
    if ($r.status -eq 'fail' -or $r.status -eq 'execError') {
        Write-Line ("  RETRY    {0}  (first attempt {1} - transient flake guard)" -f $r.flow, $r.status) 'Yellow'
        Reset-App -Sdk $sdk -Device $DeviceId
        $r2 = Invoke-Flow -Maestro $maestro -FlowFile $flow
        if ($r2.pass) { $r = $r2 }
    }

    # Give the role back before the next flow starts, whatever this one did.
    if ($previousRoleHolder) {
        $restored = Set-HomeRoleHolder -Sdk $sdk -Device $targetDevice -Package $previousRoleHolder
        if ($restored) {
            Write-Line ("  {0,-8} {1}  (home role restored to {2})" -f 'ROLE', $r.flow, $previousRoleHolder) 'DarkGray'
        } else {
            Write-Line ("  {0,-8} {1}  (FAILED to restore the home role to {2} - later flows may hang in go_home; restore it by hand)" -f 'ROLE', $r.flow, $previousRoleHolder) 'Red'
        }
        $script:LauncherMode = Get-LauncherModeState -Sdk $sdk -Device $targetDevice
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

$skippedCount = @($results | Where-Object { $_.status -eq 'skip' }).Count
$failedCount  = @($results | Where-Object { -not $_.pass -and $_.status -ne 'skip' }).Count
# status rides into the JSON so a consumer can tell a transport failure from an app defect; without
# it prerelease-verdict.ps1 saw only pass=false and counted an ADB crash against the release (S2396).
# skipReason rides along for the same reason one step further: a skip carries no log to read.
$flowsOut     = @($results | ForEach-Object {
    [ordered]@{ flow = $_.flow; pass = $_.pass; status = $_.status
                skipReason = $(if ($_.Contains('skipReason')) { $_.skipReason } else { $null }); log = $_.log }
})

# Precedence: execution error (4) outranks assertion failure (3); both outrank pass. A skip enters
# neither branch - it is a flow that was never judged, not a flow that was judged badly.
if ($anyExec) {
    Exit-Suite -Code 4 -Pass $false -Total $flows.Count -Failed $failedCount -Skipped $skippedCount -Flows $flowsOut -Reason 'execution error (no device / runtime) in one or more flows'
}
if ($anyFail) {
    Exit-Suite -Code 3 -Pass $false -Total $flows.Count -Failed $failedCount -Skipped $skippedCount -Flows $flowsOut -Reason $null
}
Exit-Suite -Code 0 -Pass $true -Total $flows.Count -Failed 0 -Skipped $skippedCount -Flows $flowsOut -Reason $null
