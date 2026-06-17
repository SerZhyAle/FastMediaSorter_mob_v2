<#
.SYNOPSIS
  S0484 pre-release sweep - resource + settings configuration (adb-scriptable parts).

.DESCRIPTION
  Owns the parts of /spec-prerelease configuration that adb can drive without UI:
    1. Endpoint reachability pre-check (probe-and-list vs register-only SKIP).
    2. Resource import trigger via intent-push to ResourceImportActivity (step 02.3).
    3. Theme + language settings via SharedPreferences / cmd locale (step 02.4).

  The import confirm-dialog tap, the DataStore-backed setting toggles, and per-resource
  listing verification are UI-driven and belong to the skill scenario (Phase 05).

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
# Code package has no .debug suffix (only applicationId does); component names must use the FQCN.
$CodePackage   = 'com.sza.fastmediasorter'
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
        & $adb @adbTarget shell cmd locale set-app-locales $DebugPackage --locales $s.Locale *> $null
        $loc = & $adb @adbTarget shell cmd locale get-app-locales $DebugPackage 2>$null
        $okLoc = ("$loc" -match [regex]::Escape($s.Locale))
        $script:result.settings += [ordered]@{ name = $name; channel = 'adb'; status = $(if ($okLoc) { 'OK' } else { 'FAIL' }) }
        if (-not $okLoc) { Add-Stage "set:$name" 'FAIL' "locale not applied (got '$loc')"; Complete-Run 10 }
        Add-Stage "set:$name" 'OK' "locale=$($s.Locale)"
    } else {
        $script:result.settings += [ordered]@{ name = $name; channel = 'adb'; status = 'noop' }
        Add-Stage "set:$name" 'SKIP' 'no adb apply method for this key'
    }
}

# ---------- stage 3: import trigger (step 02.3) ----------
# Push the predefined-resources XML (root <media-resources>) and fire ACTION_VIEW at the
# exported ResourceImportActivity (mime application/vnd.fms.resources+xml). This opens the
# import preview + confirm dialog; the confirm tap is delegated to the skill scenario.
# NOTE: file:// is the only adb-mintable scheme here; on scoped-storage devices the app's
# read of /sdcard via file:// must be validated in Phase 05 (fallback: OWNER_TRIGGER UI path).
$devXml = '/sdcard/sza_resources.xml'
& $adb @adbTarget push "$ResourcesXml" $devXml *> $null
if ($LASTEXITCODE -ne 0) { Add-Stage 'import-push' 'FAIL' "adb push exit $LASTEXITCODE"; Complete-Run 10 }
Add-Stage 'import-push' 'OK' "pushed resources XML to $devXml"

$amOut = & $adb @adbTarget shell am start -a android.intent.action.VIEW -d "file://$devXml" `
    -t 'application/vnd.fms.resources+xml' `
    -n "$DebugPackage/$CodePackage.ui.resourceimport.ResourceImportActivity" 2>&1
if ($LASTEXITCODE -ne 0 -or "$amOut" -match 'Error:') {
    Add-Stage 'import-launch' 'FAIL' ("am start failed: " + ("$amOut" -replace '\s+', ' '))
    Complete-Run 10
}
Add-Stage 'import-launch' 'OK' 'ResourceImportActivity launched (confirm tap delegated to skill)'

Complete-Run 0
