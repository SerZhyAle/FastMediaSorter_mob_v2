#requires -Version 7.0
<#
.SYNOPSIS
    S2600 contract suite for the form-factor half of scripts/devtest/device-ready.ps1.

.DESCRIPTION
    Subject: scripts/devtest/device-ready.ps1

    Why it exists. The probe is the gate `/spec-all`, `/spec-dev`, `/spec-sweep` and `/verify` all
    read before deciding whether a device verdict may be recorded, and on 2026-09-05 it answered
    `ready:true` naming a WATCH emulator for a phone-only ticket, from another session's 23-minute-old
    finding, with its own `devices` list empty. Nothing caught it because the probe had no suite at
    all: the answer went straight into a spec as a verdict. Every case below is one sentence of that
    failure.

    Hermetic: no device, no network, no real adb, no shared chat store. The suite drops a stub named
    `adb` on PATH, blanks ANDROID_HOME and ANDROID_SDK_ROOT so `Find-Adb` picks it up, points
    FMS_AGENT_CHAT_ROOT at a throwaway root, and runs the real script as a child process exactly the
    way an agent runs it - parameter binding, selection, serialization and exit code included.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/device-ready.tests/Run-Tests.ps1

    Exit codes:
      0 - every case passed
      1 - at least one case failed
      2 - the suite could not run (stub call the table does not answer)
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$suiteDir = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $suiteDir '..\..\..')).Path
$readyScript = Join-Path $repoRoot 'scripts/devtest/device-ready.ps1'
$chatCli = Join-Path $repoRoot 'scripts/utils/agent-chat.ps1'

$WATCH = 'emulator-5554'
$PHONE = 'emulator-5556'
$BOTH = "$WATCH,$PHONE"

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
    } else {
        Write-Host "FAIL | $Label -> expected: $Expected | actual: $Actual"
        $script:failed++
    }
}

# ---------- the run directory ----------
# Rule 10.1: scratch under temp/. One directory per run holding the stub, the throwaway chat root and
# the miss report, so nothing this suite does can reach a real working file.
$runDir = Join-Path $repoRoot ("temp/scratch/device-ready-suite-{0}" -f $PID)
Get-ChildItem -Path (Join-Path $repoRoot 'temp/scratch') -Filter 'device-ready-suite-*' -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
Copy-Item -Path (Join-Path $suiteDir 'stub') -Destination (Join-Path $runDir 'stub') -Recurse -Force
$missFile = Join-Path $runDir 'stub-misses.txt'
$chatRoot = Join-Path $runDir 'chat'

# ---------- the seam ----------
# Find-Adb reads ANDROID_HOME and ANDROID_SDK_ROOT BEFORE PATH, so both must be blanked or the child
# finds this machine's real adb.exe and the suite stops being hermetic.
#
# S2712: TWO resolvers run in this probe and their orders differ, so sealing PATH alone seals one of
# them. device-ready.ps1's own Find-Adb reads ANDROID_HOME -> ANDROID_SDK_ROOT -> PATH ->
# %LOCALAPPDATA%\Android\Sdk, but the canon harness's Find-AgentChatAdb (chat/agent-chat-store.ps1,
# reached from Test-AgentChatFindingAlive when a finding names a device) reads
# ANDROID_HOME -> ANDROID_SDK_ROOT -> %LOCALAPPDATA%\Android\Sdk -> PATH, with PATH LAST. So the
# liveness check walked past the seam to the machine's real adb, which lists no emulator-5554, and
# the seeded finding died as `device not listed` - the three reuse cases below then measured a
# multiple-devices refusal instead of a reuse. Pointing LOCALAPPDATA at the run directory, which is
# real and writable and has no Android\Sdk under it, makes that resolver fall through to PATH and
# reach the same stub the probe uses.
$env:ANDROID_HOME        = ''
$env:ANDROID_SDK_ROOT    = ''
$env:LOCALAPPDATA        = $runDir
$env:PATH                = (Join-Path $runDir 'stub') + [System.IO.Path]::PathSeparator + $env:PATH
$env:FMS_STUB_HOME       = $runDir
$env:FMS_AGENT_CHAT_ROOT = $chatRoot

$stubDefaults = @{
    FMS_STUB_DEVICES  = $WATCH
    FMS_STUB_WATCHES  = $WATCH
    FMS_STUB_PACKAGES = 'com.sza.fastmediasorter.debug'
    FMS_STUB_VERSION  = '9.9.9'
}

function Invoke-Probe {
    param(
        [string[]]$ProbeArgs = @(),
        [hashtable]$Stub = @{}
    )
    foreach ($k in $stubDefaults.Keys) {
        $v = if ($Stub.ContainsKey($k)) { $Stub[$k] } else { $stubDefaults[$k] }
        Set-Item -Path "Env:$k" -Value $v
    }
    $stdout = & pwsh @(@('-NoProfile', '-File', $readyScript) + $ProbeArgs + @('-Json')) 2>$null
    $code = $LASTEXITCODE
    $text = ($stdout | Out-String).Trim()
    $obj = $null
    try { $obj = $text | ConvertFrom-Json } catch { $obj = $null }
    return [pscustomobject]@{ exit = $code; json = $obj; stdout = $text }
}

try {
    Write-Host '--- form factor is reported unconditionally ---'
    # The half that needs no -Module: a reader must never have to infer the form factor from the
    # package name, which cannot carry it - both modules ship under one applicationId (S1681).
    $r = Invoke-Probe
    Assert-Equal 'ready' $r.json.state 'single watch online -> ready'
    Assert-Equal 'watch' $r.json.formFactor 'single watch online -> formFactor watch'
    $r = Invoke-Probe -Stub @{ FMS_STUB_DEVICES = $PHONE; FMS_STUB_WATCHES = '' }
    Assert-Equal 'phone' $r.json.formFactor 'single phone online -> formFactor phone'

    Write-Host '--- -Module selects by form factor instead of refusing ---'
    # The S2600 scenario itself: a watch and a phone both online, and a phone ticket asking.
    $r = Invoke-Probe -ProbeArgs @('-Module', 'app_v2') -Stub @{ FMS_STUB_DEVICES = $BOTH }
    Assert-Equal 'ready' $r.json.state '-Module app_v2 with both online -> ready'
    Assert-Equal $PHONE $r.json.selectedDevice '-Module app_v2 with both online -> picks the phone'
    Assert-Equal 'phone' $r.json.formFactor '-Module app_v2 with both online -> formFactor phone'
    $r = Invoke-Probe -ProbeArgs @('-Module', 'wear') -Stub @{ FMS_STUB_DEVICES = $BOTH }
    Assert-Equal $WATCH $r.json.selectedDevice '-Module wear with both online -> picks the watch'
    Assert-Equal 'watch' $r.json.formFactor '-Module wear with both online -> formFactor watch'

    Write-Host '--- no -Module leaves every existing caller untouched ---'
    # The compatibility promise in the strategic spec Non-goals. If this goes red, the fix started
    # answering differently for the callers that never asked for it.
    $r = Invoke-Probe -Stub @{ FMS_STUB_DEVICES = $BOTH }
    Assert-Equal 'multiple-devices' $r.json.state 'no -Module with both online -> multiple-devices'
    Assert-Equal 3 $r.json.statusCode 'no -Module with both online -> statusCode 3'
    Assert-Equal 0 $r.exit 'status query still exits 0 without -StrictExit'

    Write-Host '--- form-factor-mismatch is its own answer, not no-device ---'
    $r = Invoke-Probe -ProbeArgs @('-Module', 'app_v2')
    Assert-Equal 'form-factor-mismatch' $r.json.state 'only a watch online, app_v2 asked -> form-factor-mismatch'
    Assert-Equal 8 $r.json.statusCode 'only a watch online, app_v2 asked -> statusCode 8'
    Assert-Equal $WATCH ($r.json.devices -join ',') 'refusal still reports the devices it saw'
    $r = Invoke-Probe -ProbeArgs @('-Module', 'app_v2', '-StrictExit')
    Assert-Equal 8 $r.exit '-StrictExit turns the mismatch into process exit 8'
    # A device the caller named BY SERIAL is online; saying 'not online' about it would be the same
    # class of lie this ticket exists for.
    $r = Invoke-Probe -ProbeArgs @('-DeviceId', $WATCH, '-Module', 'app_v2') -Stub @{ FMS_STUB_DEVICES = $BOTH }
    Assert-Equal 'form-factor-mismatch' $r.json.state 'named watch under -Module app_v2 -> mismatch, not no-device'

    Write-Host '--- a reused finding no longer chooses the device ---'
    # Every case here reseeds a bare chat root first. A probe that reaches `ready` posts a finding of
    # its own, so without the reset the cases above leave a live PHONE finding behind and each case
    # would be judging the previous case's exhaust instead of the record it seeded.
    . $readyScript
    $req = Get-CanonicalReadyRequest -DeviceId '' -Package '' -ExpectedVersion '' -CheckMcp $false

    function Reset-SeededFinding {
        # The exact shape that caused the incident: a live finding about the WATCH, written by
        # another agent, matching the canonical request a no-DeviceId call builds.
        param([Parameter(Mandatory)][string]$Serial)
        Remove-Item -LiteralPath $chatRoot -Recurse -Force -ErrorAction SilentlyContinue
        $env:FMS_AGENT_ID = 'suite-other-agent'
        try {
            & pwsh -NoProfile -File $chatCli -Verb Post -Finding -Kind device -Topic "device:$Serial" `
                -Device $Serial -TtlMinutes 60 -Note 'READY' -EvidenceCommand $req -EvidenceExit 0 *> $null
        } finally { Remove-Item Env:FMS_AGENT_ID -ErrorAction SilentlyContinue }
    }

    Reset-SeededFinding -Serial $WATCH
    $r = Invoke-Probe -ProbeArgs @('-ReuseFinding', '-Module', 'app_v2') -Stub @{ FMS_STUB_DEVICES = $BOTH }
    Assert-Equal $PHONE $r.json.selectedDevice 'watch finding + -Module app_v2 -> the phone, not the finding'
    Assert-Equal 'phone' $r.json.formFactor 'watch finding + -Module app_v2 -> form factor read fresh'
    Assert-Equal $false $r.json.reused 'an ineligible finding is not reported as reuse'

    Reset-SeededFinding -Serial $WATCH
    $r = Invoke-Probe -ProbeArgs @('-ReuseFinding') -Stub @{ FMS_STUB_DEVICES = $BOTH }
    Assert-Equal $true $r.json.reused 'an eligible finding is still reused'
    Assert-Equal $WATCH $r.json.selectedDevice 'the reused answer names the finding device'
    Assert-Equal $BOTH ($r.json.devices -join ',') 'a reused answer no longer reports an empty device list'
    Assert-Equal 'watch' $r.json.formFactor 'a reused answer carries a freshly measured form factor'

    # The finding names a device that is not online any more. Falling through to the full probe is
    # the point: the old code answered ready about it regardless.
    Reset-SeededFinding -Serial $WATCH
    $r = Invoke-Probe -ProbeArgs @('-ReuseFinding') -Stub @{ FMS_STUB_DEVICES = $PHONE; FMS_STUB_WATCHES = '' }
    Assert-Equal $false $r.json.reused 'a finding about an absent device is ignored'
    Assert-Equal $PHONE $r.json.selectedDevice 'the ignored finding falls through to a fresh probe'

    Write-Host '--- the package and version checks still work ---'
    $r = Invoke-Probe -ProbeArgs @('-Package', 'com.sza.fastmediasorter.debug', '-ExpectedVersion', '9.9.9')
    Assert-Equal 'ready' $r.json.state 'package + version still reach ready'
    Assert-Equal $true $r.json.installed 'installed still reported'
    $r = Invoke-Probe -ProbeArgs @('-Package', 'com.sza.absent')
    Assert-Equal 'package-not-installed' $r.json.state 'absent package still reported'
}
finally {
    if (Test-Path -LiteralPath $missFile) {
        Write-Host ''
        Write-Host 'Stub calls the table does not answer:' -ForegroundColor Red
        Get-Content -LiteralPath $missFile | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
        Write-Host 'A miss means the probe changed how it talks to adb - extend the stub, do not ignore it.' -ForegroundColor Red
        $script:failed = -1
    }
    Remove-Item -LiteralPath $runDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host "device-ready.tests: $script:passed passed, $([Math]::Max($script:failed, 0)) failed"
if ($script:failed -lt 0) { exit 2 }
if ($script:failed -gt 0) { exit 1 }
exit 0
