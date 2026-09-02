<#
.SYNOPSIS
  S0484 pre-release sweep - resource + settings configuration (adb-scriptable parts).

.DESCRIPTION
  Owns the parts of /spec-prerelease configuration that adb can drive without UI:
    1. Credential-free clean-emulator resource fixture declaration.
    2. Theme + language settings via SharedPreferences / cmd locale (step 02.4).

  S1666 removed bundled owner resources and credentials from every APK. The clean setup's seeded
  standard Downloads resource is therefore the automated browsing fixture; owner-only network
  resources are reported as unavailable rather than read from a private checkout file. DataStore-
  backed setting toggles remain UI-driven and belong to the skill scenario (Phase 05).

  Setting channels come from prerelease.config.psd1. No endpoint or credential is carried by this
  runner, so it stays reproducible on every clean checkout.

  Exit codes:
    0  - configuration applied (reachable resources + required adb settings OK)
    1  - bad arguments / config unreadable
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

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$RepoRoot      = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$DebugPackage  = 'com.sza.fastmediasorter.debug'
$ConfigPath    = Join-Path $PSScriptRoot 'prerelease.config.psd1'

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

if (-not (Test-Path $ConfigPath)) { Add-Stage 'load-config' 'FAIL' "config not found: $ConfigPath"; Complete-Run 1 }
$config = Import-PowerShellDataFile $ConfigPath
$script:result.resources += [ordered]@{
    name = 'owner-network-fixtures'
    type = 'OWNER_ONLY'
    reachability = 'not-shipped'
    status = 'SKIP'
}
Add-Stage 'resources' 'SKIP' 'owner-only network fixtures are not shipped; seeded Downloads covers clean-emulator browsing'

# ---------- adb resolution (shared by settings stages) ----------
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

Complete-Run 0
