<#
.SYNOPSIS
  S0484 pre-release sweep - resource + settings configuration (adb-scriptable parts).

.DESCRIPTION
  Owns the parts of /spec-prerelease configuration that adb can drive without UI:
    1. Endpoint reachability pre-check (probe-and-list vs register-only SKIP).
    2. Theme + language settings via SharedPreferences / cmd locale (step 02.4).

  Resource import is NOT adb-scriptable (S0492): the importer reads the APK-bundled
  res/xml/sza_resources.xml directly and is triggered only by committing the OWNER_TRIGGER
  value into the Settings "Default User" field. On minSdk 26 a raw file:// Uri handed to
  ResourceImportActivity by an external caller is not openable (FileProvider isolation), so the
  former intent-push import stage always failed and was removed. The import, the DataStore-backed
  setting toggles, and per-resource listing verification are UI-driven and belong to the skill
  scenario (Phase 05).

  Resource picks, reachability classes and setting channels come from prerelease.config.psd1.
  Endpoints/credentials are resolved by predefined-resource NAME from res/xml/sza_resources.xml.

  Exit codes:
    0  - configuration applied (reachable resources + required adb settings OK)
    1  - bad arguments / config or resources XML unreadable
    10 - a required configuration stage failed

.PARAMETER DeviceId
  Specific adb device id. Required when multiple devices are online.

.PARAMETER Json
  Emit a single JSON object instead of human-readable lines.

.EXAMPLE
  pwsh -NoProfile -File scripts/devtest/prerelease-configure.ps1 -Json
#>
[CmdletBinding()]
param(
    [string]$DeviceId,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'

$RepoRoot      = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$DebugPackage  = 'com.sza.fastmediasorter.debug'
$ConfigPath    = Join-Path $PSScriptRoot 'prerelease.config.psd1'
$ResourcesXml  = Join-Path $RepoRoot 'app_v2/src/main/res/xml/sza_resources.xml'

$result = [ordered]@{
    ok        = $false
    exitCode  = 0
    resources = @()
    settings  = @()
    stages    = @()
}

function Add-Stage {
    param([string]$Name, [string]$Status, [string]$Detail)
    $script:result.stages += [ordered]@{ name = $Name; status = $Status; detail = $Detail }
    if (-not $Json) { Write-Host ("[{0}] {1} - {2}" -f $Status, $Name, $Detail) }
}

function Complete-Run {
    param([int]$Code)
    $script:result.exitCode = $Code
    $script:result.ok       = ($Code -eq 0)
    if ($Json) { $script:result | ConvertTo-Json -Depth 6 -Compress }
    else { Write-Host ("CONFIGURE {0} - exit={1}" -f $(if ($Code -eq 0) { 'OK' } else { 'FAIL' }), $Code) }
    exit $Code
}

if (-not (Test-Path $ConfigPath))   { Add-Stage 'load-config' 'FAIL' "config not found: $ConfigPath"; Complete-Run 1 }
if (-not (Test-Path $ResourcesXml)) { Add-Stage 'load-config' 'FAIL' "resources XML not found: $ResourcesXml"; Complete-Run 1 }
$config = Import-PowerShellDataFile $ConfigPath
[xml]$resXml = Get-Content -Raw -Path $ResourcesXml

# Resolve a predefined resource path (e.g. sftp://host:port/..) by its name.
function Resolve-Endpoint {
    param([string]$Name)
    $node = $resXml.SelectNodes('//resource') | Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if ($node) { return $node.path }
    return $null
}

# TCP reachability probe (host proxy for emulator NAT: public endpoints share the host's route).
function Test-Endpoint {
    param([string]$DhHost, [int]$Port)
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $iar = $client.BeginConnect($DhHost, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(4000)
        if ($ok -and $client.Connected) { $client.Close(); return $true }
        $client.Close(); return $false
    } catch { return $false }
}

# ---------- stage 1: reachability pre-check ----------
foreach ($key in $config.Resources.Keys) {
    $res   = $config.Resources[$key]
    $entry = [ordered]@{ name = $res.Name; type = $res.Type; reachability = $res.Reachability; status = $null }

    if ($res.Reachability -eq 'register-only') {
        # LAN endpoints are unreachable from the emulator NAT; register the row, never probe/list.
        $entry.status = 'SKIP'
        Add-Stage "reach:$($res.Name)" 'SKIP' "register-only ($($res.Type)) - LAN unreachable from emulator NAT"
    } elseif ($res.Type -eq 'LOCAL') {
        $entry.status = 'reachable'
        Add-Stage "reach:$($res.Name)" 'OK' 'LOCAL - on-device path'
    } else {
        $path = Resolve-Endpoint -Name $res.Name
        $m = [regex]::Match("$path", '^[a-z]+://([^/:]+):(\d+)')
        if (-not $m.Success) {
            $entry.status = 'SKIP'
            Add-Stage "reach:$($res.Name)" 'SKIP' "could not parse endpoint from '$path'"
        } elseif (Test-Endpoint -DhHost $m.Groups[1].Value -Port ([int]$m.Groups[2].Value)) {
            $entry.status = 'reachable'
            Add-Stage "reach:$($res.Name)" 'OK' "reachable $($m.Groups[1].Value):$($m.Groups[2].Value)"
        } else {
            $entry.status = 'SKIP'
            Add-Stage "reach:$($res.Name)" 'SKIP' "unreachable $($m.Groups[1].Value):$($m.Groups[2].Value)"
        }
    }
    $script:result.resources += $entry
}

# ---------- adb resolution (shared by settings + import stages) ----------
# Resolve adb (not on PATH on the dev machine), mirroring device-ready.ps1.
function Get-Adb {
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root) { $c = Join-Path $root 'platform-tools\adb.exe'; if (Test-Path $c) { return $c } }
    }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $known = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $known) { return $known }
    return $null
}
$adb = Get-Adb
if (-not $adb) { Add-Stage 'configure' 'FAIL' 'adb not found'; Complete-Run 10 }
$adbTarget = @(); if ($DeviceId) { $adbTarget = @('-s', $DeviceId) }

# Device API level. Per-app locale via `cmd locale set-app-locales` is API 33+ (Android 13);
# below that the framework `locale` service does not exist ("Can't find service: locale") and
# per-app language is in-app only (AppCompat storage, not adb-drivable). The locale step is
# therefore skipped - not failed - on such devices so an emulator's API level never aborts the sweep.
$deviceSdk = 0
$sdkRaw = "$(& $adb @adbTarget shell getprop ro.build.version.sdk 2>$null)".Trim()
if ($sdkRaw -match '^\d+$') { $deviceSdk = [int]$sdkRaw }

# ---------- stage 2: adb-scriptable settings (step 02.4) ----------
# Apply only Channel='adb' settings (language via cmd locale). Channel='ui' entries
# (theme + DataStore toggles) are delegated to the skill UI scenario (Phase 05).
# Runs before the import trigger so the locale-driven restart cannot interrupt the dialog.
foreach ($name in $config.Settings.Keys) {
    $s = $config.Settings[$name]
    if ($s.Channel -ne 'adb') {
        $script:result.settings += [ordered]@{ name = $name; channel = $s.Channel; status = 'delegated-ui' }
        Add-Stage "set:$name" 'SKIP' 'ui channel - delegated to skill scenario'
        continue
    }
    if ($s.Locale) {
        if ($deviceSdk -gt 0 -and $deviceSdk -lt 33) {
            $script:result.settings += [ordered]@{ name = $name; channel = 'adb'; status = 'SKIP' }
            Add-Stage "set:$name" 'SKIP' "per-app locale needs API 33+ (device API $deviceSdk); language is in-app only below that"
            continue
        }
        # Per-app locale, the API 33+ supported path. --user current targets USER_CURRENT (user -2):
        # without it set/get-app-locales operate on user 0, which reads back empty on a freshly
        # installed app and previously failed the sweep (exit 10) for a locale that was in fact
        # applied (S0626). Set and verify against the same user so a real apply is never misread.
        & $adb @adbTarget shell cmd locale set-app-locales $DebugPackage --user current --locales $s.Locale *> $null
        $loc = & $adb @adbTarget shell cmd locale get-app-locales $DebugPackage --user current 2>$null
        $okLoc = ("$loc" -match [regex]::Escape($s.Locale))
        $script:result.settings += [ordered]@{ name = $name; channel = 'adb'; status = $(if ($okLoc) { 'OK' } else { 'FAIL' }) }
        if (-not $okLoc) { Add-Stage "set:$name" 'FAIL' "locale not applied (got '$loc')"; Complete-Run 10 }
        # Relaunch so the running process picks up the new per-app locale. Explicit -n MainActivity
        # avoids the debug build's LeakCanary LAUNCHER trap.
        & $adb @adbTarget shell am force-stop $DebugPackage *> $null
        & $adb @adbTarget shell am start -n "$DebugPackage/com.sza.fastmediasorter.ui.main.MainActivity" *> $null
        Add-Stage "set:$name" 'OK' "locale=$($s.Locale) (per-app, relaunched)"
    } else {
        $script:result.settings += [ordered]@{ name = $name; channel = 'adb'; status = 'noop' }
        Add-Stage "set:$name" 'SKIP' 'no adb apply method for this key'
    }
}

# ---------- stage 3: import delegation (step 02.3) ----------
# Resource import cannot be driven by adb (S0492): the importer reads the APK-bundled
# res/xml/sza_resources.xml and is triggered only by the OWNER_TRIGGER Settings-field commit,
# a UI action. The previous intent-push to ResourceImportActivity always failed because a raw
# file:// Uri from an external caller is not openable on minSdk 26. The skill scenario performs
# the import via mobile-mcp (Phase 05); record the delegation so the JSON contract stays explicit.
$script:result.settings += [ordered]@{ name = 'resource-import'; channel = 'ui'; status = 'delegated-ui' }
Add-Stage 'import' 'SKIP' 'not adb-scriptable - OWNER_TRIGGER UI path delegated to skill scenario'

Complete-Run 0
