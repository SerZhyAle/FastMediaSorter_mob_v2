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

  Exit codes are stable; skills use them to decide whether to abort or proceed.

  Exit codes:
    0 - device ready (also writes "OK ..." human line; with -Json writes machine JSON)
    1 - ADB executable not found
    2 - no online device
    3 - multiple online devices and -DeviceId not supplied
    4 - target package not installed
    5 - installed versionName mismatch
    6 - mobile-mcp launcher not resolvable

  Human output:  one line per check + final verdict line.
  Machine output (with -Json): single JSON object on stdout, all human noise suppressed.

.PARAMETER DeviceId
  Specific adb device id (the "serial" from `adb devices`). Required when multiple devices are online.

.PARAMETER Package
  Target package name to verify is installed (e.g. com.sza.fastmediasorter, com.sza.fastmediasorter.debug).

.PARAMETER ExpectedVersion
  Expected versionName the installed Package must report. Comparison is exact string match.

.PARAMETER CheckMcp
  Best-effort check that the mobile-mcp launcher is resolvable (npx + the @mobilenext/mobile-mcp package).
  Does not start the server - only confirms the entry point would be runnable.

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

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
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

# ---------- helpers ----------

$script:result = [ordered]@{
    ready           = $false
    exitCode        = 0
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

function Fail {
    param([int]$Code, [string]$Reason)
    $script:result.exitCode = $Code
    $script:result.reason   = $Reason
    if ($Json) {
        $script:result | ConvertTo-Json -Compress
    } else {
        Write-Host "FAIL ($Code) - $Reason" -ForegroundColor Red
    }
    exit $Code
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
    Fail 1 "adb.exe not found (checked ANDROID_HOME, ANDROID_SDK_ROOT, PATH, %LOCALAPPDATA%\Android\Sdk\platform-tools)"
}
$script:result.adbPath = $adb
Write-Line "OK adb: $adb" 'Green'

# ---------- step 2: devices ----------

$raw = & $adb devices 2>$null
if ($LASTEXITCODE -ne 0) { Fail 1 "adb devices returned exit $LASTEXITCODE" }

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
    Fail 2 "no online device (boot an emulator or connect a phone, then re-run)"
}

$selected = $null
if ($DeviceId) {
    $selected = $devices | Where-Object { $_.id -eq $DeviceId } | Select-Object -First 1
    if (-not $selected) { Fail 2 "device '$DeviceId' is not online (online: $($devices.id -join ', '))" }
} elseif ($devices.Count -gt 1) {
    Fail 3 "multiple online devices ($($devices.id -join ', ')); pass -DeviceId"
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
        Fail 4 "package '$Package' not installed on $($selected.id)"
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
            Fail 5 "versionName mismatch: installed='$current' expected='$ExpectedVersion'"
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
        Fail 6 "npx not found (PATH, %ProgramFiles%\nodejs, %APPDATA%\npm) - install Node.js to enable mobile-mcp"
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
        Fail 6 "@mobilenext/mobile-mcp not resolvable via npm (offline or unknown package)"
    }
    $script:result.mcpResolvable = $true
    Write-Line "OK mobile-mcp launcher: $npxPath" 'Green'
}

# ---------- verdict ----------

$script:result.ready = $true
if ($Json) {
    $script:result | ConvertTo-Json -Compress
} else {
    Write-Host "READY - device=$($selected.id)$(if($Package){" pkg=$Package"})$(if($ExpectedVersion){" v=$ExpectedVersion"})" -ForegroundColor Cyan
}
exit 0
