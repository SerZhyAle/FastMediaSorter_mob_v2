<#
.SYNOPSIS
  Pre-flight readiness check for on-device testing skills (/spec-test-device, /verify).

.DESCRIPTION
  Single point of truth that answers: can the agent actually run a UI scenario right now?
  Verifies, in order:
    1. ADB executable is reachable.
    2. At least one device is online (DeviceId narrows the selection).
    3. If -Package is given, the package is installed on the selected device.
    4. If -ExpectedVersion is given, the installed package's versionName matches.
    5. If -CheckMcp is set, the mobile-mcp launcher (npx + @mobilenext/mobile-mcp) is resolvable.

  This is a STATUS QUERY (S1338 phase 09). "No device attached" is a normal answer to it,
  not a failure of the query, so the readiness verdict travels in the payload and the
  process exits 0 whenever the state could be determined. Read `ready` (bool) and `state`
  (string); `statusCode` carries the legacy numeric code for anything still keyed on it.
  Pass -StrictExit to restore the old behaviour where the numeric code IS the exit code.

  Exit codes:
    0 - state determined and reported: ready, or not-ready with a `state`/`reason`
    2 - the probe itself could not run

  With -StrictExit the not-ready states exit with their legacy code instead:
    1 - ADB executable not found          (state: no-adb)
    2 - no online device                  (state: no-device)
    3 - multiple online devices, no -DeviceId (state: multiple-devices)
    4 - target package not installed      (state: package-not-installed)
    5 - installed versionName mismatch    (state: version-mismatch)
    6 - mobile-mcp launcher not resolvable (state: mcp-unavailable)
    7 - every online device is leased by another session (state: all-devices-leased)
        or the named -DeviceId is                        (state: device-leased)
        Reachable only under -ClaimFree. Deliberately distinct from no-device and from
        multiple-devices (S1926): "there is nothing to test on" ends the device stage, while
        "somebody else is on all of them" means try again later.

  Human output:  one line per check + final verdict line.
  Machine output (with -Json): single JSON object on stdout, all human noise suppressed.

.PARAMETER DeviceId
  Specific adb device id (the "serial" from `adb devices`). Required when multiple devices are online,
  unless -ClaimFree is passed.

.PARAMETER ClaimFree
  Take a device lease (S1926) instead of refusing when several devices are online: walk the online
  devices and keep the first one this session can claim. Opt-in on purpose - without it the probe
  answers exactly as it did before the lease existed. With a -DeviceId it claims that device and
  reports state `device-leased` if a sibling holds it.

.PARAMETER Package
  Target package name to verify is installed (e.g. com.sza.fastmediasorter, com.sza.fastmediasorter.debug).

.PARAMETER ExpectedVersion
  Expected versionName the installed Package must report. Comparison is exact string match.

.PARAMETER CheckMcp
  Best-effort check that the mobile-mcp launcher is resolvable (npx + the @mobilenext/mobile-mcp package).
  Does not start the server - only confirms the entry point would be runnable.

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

.PARAMETER StrictExit
  Legacy fail-fast mode: a not-ready state exits with its numeric code (1..6) instead of 0.
  Only for a caller that cannot read the payload and must branch on $LASTEXITCODE.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/device-ready.ps1
  Quick sanity: ADB up, one device online.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -ExpectedVersion 2.62.0501.151
  Full pre-flight for a /spec-test-device run.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/device-ready.ps1 -CheckMcp -Json
  Machine-readable readiness probe including mobile-mcp resolvability.
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [string]$Package,
    [string]$ExpectedVersion,
    [switch]$CheckMcp,
    [switch]$Json,
    [switch]$StrictExit,
    # S1926. Opt-in, and opt-in on purpose (ADR-2): sibling sessions are running right now, and
    # silently changing a shared probe's answer mid-run is the same class of surprise the device
    # lease exists to remove. Without this switch the probe behaves exactly as it always has,
    # `multiple-devices` included.
    [switch]$ClaimFree
)

$ErrorActionPreference = 'Stop'

# ---------- helpers ----------

$script:result = [ordered]@{
    ready           = $false
    state           = 'unknown'
    statusCode      = 0
    exitCode        = 0   # retained for callers that already read this field; mirrors statusCode
    adbPath         = $null
    devices         = @()
    selectedDevice  = $null
    package         = $Package
    installed       = $null
    versionName     = $null
    expectedVersion = $ExpectedVersion
    versionMatch    = $null
    mcpResolvable   = $null
    reason          = $null
}

function Write-Line {
    param([string]$Text, [string]$Color = 'White')
    if (-not $Json) { Write-Host $Text -ForegroundColor $Color }
}

function Stop-NotReady {
    # Reports a determined not-ready state. The query succeeded - it is the device that is
    # not ready - so this exits 0 unless the caller asked for the legacy fail-fast codes.
    param([int]$Code, [string]$State, [string]$Reason)
    $script:result.state      = $State
    $script:result.statusCode = $Code
    $script:result.exitCode   = $Code
    $script:result.reason     = $Reason
    if ($Json) {
        $script:result | ConvertTo-Json -Compress
    } else {
        Write-Host "NOT READY ($State) - $Reason" -ForegroundColor Yellow
    }
    if ($StrictExit) { exit $Code }
    exit 0
}

function Find-Adb {
    # Priority: env ANDROID_HOME / ANDROID_SDK_ROOT, then PATH, then well-known Windows path.
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) {
            $candidate = Join-Path $root 'platform-tools\adb.exe'
            if (Test-Path -Path $candidate -PathType Leaf) { return $candidate }
        }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $known = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path -Path $known -PathType Leaf) { return $known }

    return $null
}

# ---------- step 1: ADB ----------

$adb = Find-Adb
if (-not $adb) {
    Stop-NotReady 1 'no-adb' "adb.exe not found (checked ANDROID_HOME, ANDROID_SDK_ROOT, PATH, %LOCALAPPDATA%\Android\Sdk\platform-tools)"
}
$script:result.adbPath = $adb
Write-Line "OK adb: $adb" 'Green'

# Generous on purpose: a cold `adb start-server` on a loaded machine takes seconds, and this
# bound exists to stop an indefinite hang, not to police a slow start.
$script:AdbServerStartTimeoutMs = 20000

function Start-AdbServerDetached([string]$AdbPath) {
    # S1633: `adb devices` auto-starts the `adb fork-server` daemon when none is running, and
    # that daemon INHERITS this process's stdout pipe and then never exits. The capture below
    # waits for EOF on a pipe whose write end the daemon holds open for its whole life, so the
    # adb client exits in milliseconds while the caller hangs - observed 2613 s with not one
    # byte of output, which read as "the agent went silent" rather than as an error.
    # Starting the server through Start-Process hands the daemon file handles instead of our
    # pipe, so every later capture sees EOF and returns. Already-running server: a no-op.
    $outFile = [System.IO.Path]::GetTempFileName()
    $errFile = [System.IO.Path]::GetTempFileName()
    try {
        $proc = Start-Process -FilePath $AdbPath -ArgumentList 'start-server' -NoNewWindow -PassThru `
            -RedirectStandardOutput $outFile -RedirectStandardError $errFile
        if (-not $proc.WaitForExit($script:AdbServerStartTimeoutMs)) {
            try { $proc.Kill() } catch { <# already gone between the timeout and the kill #> }
        }
    }
    catch {
        # Deliberately non-fatal: this call only removes a hazard. If it cannot run, the probe
        # below still succeeds whenever a server is already up, which is the common case, and
        # the caller's own timeout covers the rest.
        Write-Line "adb start-server could not be pre-started: $($_.Exception.Message)" 'DarkYellow'
    }
    finally {
        Remove-Item -LiteralPath $outFile, $errFile -Force -ErrorAction SilentlyContinue
    }
}

# ---------- step 2: devices ----------

Start-AdbServerDetached -AdbPath $adb
$raw = & $adb devices 2>$null
if ($LASTEXITCODE -ne 0) { Stop-NotReady 1 'no-adb' "adb devices returned exit $LASTEXITCODE" }

# parse skipping the "List of devices attached" header
$lines = $raw -split "`r?`n" | Where-Object { $_ -and $_ -notmatch '^\s*List of devices' }
$devices = foreach ($line in $lines) {
    $parts = ($line -split "\s+", 2) | Where-Object { $_ }
    if ($parts.Count -ge 2 -and $parts[1] -eq 'device') {
        [pscustomobject]@{ id = $parts[0]; state = $parts[1] }
    }
}
$devices = @($devices)
$script:result.devices = $devices | ForEach-Object { $_.id }

if ($devices.Count -eq 0) {
    Stop-NotReady 2 'no-device' "no online device (boot an emulator or connect a phone, then re-run)"
}

function Get-DevicePreferenceOrder {
    <#
        S1926 section 5.3: the ONE place the choosing order lives. A later preference - favour an
        emulator over the owner's physical phone, or a device that already carries the package -
        belongs here, so it cannot drift apart across the callers that pick a device.
        Today the order is "as adb listed them".
    #>
    param([Parameter(Mandatory)]$Candidates)
    return @($Candidates)
}

function Request-DeviceLease {
    <#
        Returns $true when this session now holds the lease on $Serial. Exit 3 is the normal "a
        sibling got there first" answer, not a fault. A missing lease script is not fatal either:
        the probe still worked before the lease existed, and refusing to answer because an optional
        coordination file is absent would be worse than the conflict it prevents.
    #>
    param([Parameter(Mandatory)][string]$Serial)
    $leaseScript = Join-Path $PSScriptRoot 'device-lease.ps1'
    if (-not (Test-Path -LiteralPath $leaseScript)) { return $true }
    & pwsh -NoProfile -File $leaseScript -Verb Claim -Id $Serial -Reason 'device-ready' *> $null
    return ($LASTEXITCODE -eq 0)
}

$selected = $null
if ($DeviceId) {
    $selected = $devices | Where-Object { $_.id -eq $DeviceId } | Select-Object -First 1
    if (-not $selected) { Stop-NotReady 2 'no-device' "device '$DeviceId' is not online (online: $($devices.id -join ', '))" }
    if ($ClaimFree -and -not (Request-DeviceLease -Serial $selected.id)) {
        Stop-NotReady 7 'device-leased' "device '$($selected.id)' is leased by another session; run device-lease.ps1 -Verb Status to see who"
    }
} elseif ($ClaimFree) {
    # The claim itself arbitrates - there is deliberately no "list the free ones, then take one",
    # because the gap between those two calls is exactly how two sessions take one device. Walk the
    # candidates and keep the first one that lets us claim it.
    $contested = @()
    foreach ($candidate in (Get-DevicePreferenceOrder -Candidates $devices)) {
        if (Request-DeviceLease -Serial $candidate.id) { $selected = $candidate; break }
        $contested += $candidate.id
    }
    if (-not $selected) {
        Stop-NotReady 7 'all-devices-leased' "every online device is leased by another session ($($contested -join ', ')); this is not 'no device' - retry later or run device-lease.ps1 -Verb Status"
    }
} elseif ($devices.Count -gt 1) {
    Stop-NotReady 3 'multiple-devices' "multiple online devices ($($devices.id -join ', ')); pass -DeviceId, or -ClaimFree to take a free one"
} else {
    $selected = $devices[0]
}
$script:result.selectedDevice = $selected.id
Write-Line "OK device: $($selected.id)" 'Green'

# ---------- step 3 + 4: package + version ----------

if ($Package) {
    # `pm list packages` returns lines like "package:com.foo.bar"
    $pmRaw = & $adb -s $selected.id shell pm list packages $Package 2>$null
    $installed = $false
    foreach ($pmLine in ($pmRaw -split "`r?`n")) {
        if ($pmLine.Trim() -eq "package:$Package") { $installed = $true; break }
    }
    $script:result.installed = $installed
    if (-not $installed) {
        Stop-NotReady 4 'package-not-installed' "package '$Package' not installed on $($selected.id)"
    }
    Write-Line "OK package: $Package" 'Green'

    if ($ExpectedVersion) {
        # `dumpsys package <pkg>` includes a "versionName=..." line.
        $dump = & $adb -s $selected.id shell dumpsys package $Package 2>$null
        $vLine = ($dump -split "`r?`n" | Where-Object { $_ -match 'versionName=' } | Select-Object -First 1)
        $current = $null
        if ($vLine) {
            $m = [regex]::Match($vLine, 'versionName=([^\s]+)')
            if ($m.Success) { $current = $m.Groups[1].Value }
        }
        $script:result.versionName  = $current
        $script:result.versionMatch = ($current -eq $ExpectedVersion)
        if ($current -ne $ExpectedVersion) {
            Stop-NotReady 5 'version-mismatch' "versionName mismatch: installed='$current' expected='$ExpectedVersion'"
        }
        Write-Line "OK version: $current" 'Green'
    }
}

# ---------- step 5: mobile-mcp resolvability ----------

function Find-Npx {
    # PATH first, then known Node.js install locations on Windows.
    foreach ($name in 'npx', 'npx.cmd', 'npx.ps1') {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    $candidates = @(
        "$env:ProgramFiles\nodejs\npx.cmd",
        "$env:ProgramFiles\nodejs\npx",
        "${env:ProgramFiles(x86)}\nodejs\npx.cmd",
        "$env:APPDATA\npm\npx.cmd"
    ) | Where-Object { $_ }
    foreach ($c in $candidates) {
        if (Test-Path -Path $c -PathType Leaf) { return $c }
    }
    return $null
}

if ($CheckMcp) {
    $npxPath = Find-Npx
    if (-not $npxPath) {
        Stop-NotReady 6 'mcp-unavailable' "npx not found (PATH, %ProgramFiles%\nodejs, %APPDATA%\npm) - install Node.js to enable mobile-mcp"
    }
    # `npm view` exits 0 if the package can be resolved from registry / cache.
    # Use the npm next to the discovered npx so we don't depend on PATH.
    $npmPath = [System.IO.Path]::ChangeExtension($npxPath, $null) -replace 'npx$', 'npm'
    if (-not (Test-Path -Path $npmPath -PathType Leaf)) {
        # fall back to .cmd sibling
        $npmCmd = (Split-Path -Parent $npxPath) + '\npm.cmd'
        if (Test-Path -Path $npmCmd -PathType Leaf) { $npmPath = $npmCmd }
    }
    $null = & $npmPath view '@mobilenext/mobile-mcp' name 2>$null
    if ($LASTEXITCODE -ne 0) {
        Stop-NotReady 6 'mcp-unavailable' "@mobilenext/mobile-mcp not resolvable via npm (offline or unknown package)"
    }
    $script:result.mcpResolvable = $true
    Write-Line "OK mobile-mcp launcher: $npxPath" 'Green'
}

# ---------- verdict ----------

$script:result.ready = $true
$script:result.state = 'ready'
if ($Json) {
    $script:result | ConvertTo-Json -Compress
} else {
    Write-Host "READY - device=$($selected.id)$(if($Package){" pkg=$Package"})$(if($ExpectedVersion){" v=$ExpectedVersion"})" -ForegroundColor Cyan
}
exit 0
