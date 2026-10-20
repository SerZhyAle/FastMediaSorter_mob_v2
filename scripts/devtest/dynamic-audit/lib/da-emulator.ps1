#requires -Version 7.0
<#
.SYNOPSIS
    Emulator-only target guard and emulator lifecycle of the S4166 dynamic-audit stand.

.DESCRIPTION
    Dot-source after lib/da-common.ps1. Functions only; nothing is assigned at script scope.

      Assert-DaEmulatorTarget  THE ONLY PLACE A SERIAL IS ACCEPTED. Refuses (code 2) unless the serial
                               matches ^emulator-\d+$, is online, and getprop ro.kernel.qemu returns 1.
                               Every other function receives a serial that already passed it.
      Start-DaEmulator         boots a named AVD headless with the SDK's emulator.exe, records the
                               started process id in the state directory, waits for sys.boot_completed
      Stop-DaEmulator          stops only the process id this stand recorded (checked against its
                               recorded start time, so a recycled pid is never killed)
      Get-DaSubject            package version, version code, install time and the SHA-256 of the
                               installed APK, matched against the APKs under app_v2/build/outputs/apk

    WHY THE GUARD IS STRICT. Authorization is per device (CLAUDE.md Rule 35, docs/DEVICE_FLEET.md): the
    owner's phone, watch and headset carry irreplaceable content, and the stand wipes and restores app
    data. A serial pattern alone is not proof (an mDNS name could be anything a user typed), so the
    kernel property that only the emulator kernel sets is read as well.
#>

. (Join-Path $PSScriptRoot '..\..\lib\target-device.ps1')

function Assert-DaEmulatorTarget {
    <#
    .SYNOPSIS
        Emulator-only guard. Returns {Code; Serial; Reason}; Code 0 means the serial may be used.
    .PARAMETER Serial
        Serial to check; when empty, the single online emulator-* serial is taken.
    #>
    param([string]$Serial)
    $codes = Get-DaExitCode
    $fleet = 'docs/DEVICE_FLEET.md'
    $refuse = {
        param($s, $why)
        $msg = "da guard: REFUSED - $why. The dynamic-audit stand acts only on emulators; read $fleet before targeting any device."
        Write-Warning $msg
        return [pscustomobject]@{ Code = $codes.Refused; Serial = $s; Reason = $msg }
    }
    if ([string]::IsNullOrWhiteSpace($Serial)) {
        $adb = Find-Adb
        if (-not $adb) { return (& $refuse '' 'adb not found, no target can be identified') }
        $online = @(Get-OnlineAdbDevices -Adb $adb | Where-Object { $_ -match '^emulator-\d+$' })
        if ($online.Count -ne 1) { return (& $refuse '' "expected exactly one online emulator, found $($online.Count); pass -Serial") }
        $Serial = $online[0]
    }
    if ($Serial -notmatch '^emulator-\d+$') { return (& $refuse $Serial "serial '$Serial' is not an emulator serial (^emulator-\d+$)") }
    $state = Invoke-DaAdb -Serial $Serial -Arguments @('get-state') -TimeoutMs 15000
    if ($state.ExitCode -ne 0 -or ($state.Lines -join '').Trim() -ne 'device') {
        return (& $refuse $Serial "serial '$Serial' is not online ($((($state.Stderr) -replace '\s+', ' ').Trim()))")
    }
    $qemu = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'getprop', 'ro.kernel.qemu') -TimeoutMs 15000
    if (($qemu.Lines -join '').Trim() -ne '1') {
        return (& $refuse $Serial "serial '$Serial' does not report ro.kernel.qemu=1")
    }
    return [pscustomobject]@{ Code = $codes.Ok; Serial = $Serial; Reason = 'emulator' }
}

function Get-DaSdkRoot {
    <#
    .SYNOPSIS
        Android SDK root: ANDROID_HOME, ANDROID_SDK_ROOT, then the parent of the adb platform-tools folder.
    #>
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($root -and (Test-Path -LiteralPath (Join-Path $root 'emulator'))) { return $root }
    }
    $adb = Find-Adb
    if ($adb) { return (Split-Path -Parent (Split-Path -Parent $adb)) }
    throw 'Android SDK root not found.'
}

function Get-DaEmulatorRecordPath {
    <#
    .SYNOPSIS
        Path of the record of the emulator process this stand started.
    #>
    return (Join-Path (Get-DaPaths).State 'emulator.json')
}

function Start-DaEmulator {
    <#
    .SYNOPSIS
        Boots an AVD headless, records the started process, waits for sys.boot_completed; returns {Serial; Pid}.
    .PARAMETER Avd
        AVD name (emulator -list-avds).
    .PARAMETER ExtraArgs
        Extra emulator arguments.
    .PARAMETER BootTimeoutSec
        Seconds to wait for the device to appear and finish booting.
    #>
    param(
        [Parameter(Mandatory)][string]$Avd,
        [string[]]$ExtraArgs = @(),
        [int]$BootTimeoutSec = 420
    )
    $adb = Get-DaAdbPath
    $emulatorExe = Join-Path (Join-Path (Get-DaSdkRoot) 'emulator') 'emulator.exe'
    if (-not (Test-Path -LiteralPath $emulatorExe)) { throw "emulator.exe not found at $emulatorExe" }
    $paths = Get-DaPaths
    New-Item -ItemType Directory -Path $paths.State -Force | Out-Null
    $before = @(Get-OnlineAdbDevices -Adb $adb)
    $argList = @('-avd', $Avd, '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot-save', '-gpu', 'swiftshader_indirect') + $ExtraArgs
    $logOut = Join-Path $paths.State "emulator-$Avd.out.log"
    $logErr = Join-Path $paths.State "emulator-$Avd.err.log"
    $proc = Start-Process -FilePath $emulatorExe -ArgumentList $argList -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $logOut -RedirectStandardError $logErr
    $record = [ordered]@{
        schemaVersion  = 1
        avd            = $Avd
        pid            = $proc.Id
        processStart   = $proc.StartTime.ToUniversalTime().ToString('o')
        serial         = $null
        startedMonoMs  = Get-DaMonotonicMs
    }
    Write-DaJson -Path (Get-DaEmulatorRecordPath) -InputObject $record -Durable

    $deadline = (Get-DaMonotonicMs) + ($BootTimeoutSec * 1000L)
    $serial = $null
    while (-not $serial -and (Get-DaMonotonicMs) -lt $deadline) {
        if ($proc.HasExited) { throw "emulator exited early with code $($proc.ExitCode); see $logErr" }
        Start-Sleep -Milliseconds 1500
        $raw = & $adb devices 2>$null
        foreach ($line in $raw) {
            if ($line -match '^(emulator-\d+)\s') {
                $cand = $Matches[1]
                if ($before -contains $cand) { continue }
                $name = (& $adb -s $cand emu avd name 2>$null | Select-Object -First 1)
                if ("$name".Trim() -eq $Avd) { $serial = $cand; break }
            }
        }
    }
    if (-not $serial) { throw "AVD '$Avd' did not appear in adb devices within $BootTimeoutSec s" }
    $record.serial = $serial
    Write-DaJson -Path (Get-DaEmulatorRecordPath) -InputObject $record -Durable

    while ((Get-DaMonotonicMs) -lt $deadline) {
        $boot = Invoke-DaAdb -Serial $serial -Arguments @('shell', 'getprop', 'sys.boot_completed') -TimeoutMs 15000
        if (($boot.Lines -join '').Trim() -eq '1') {
            $record.bootedMonoMs = Get-DaMonotonicMs
            $record.bootMs = $record.bootedMonoMs - $record.startedMonoMs
            Write-DaJson -Path (Get-DaEmulatorRecordPath) -InputObject $record -Durable
            return [pscustomobject]@{ Serial = $serial; Pid = $proc.Id; BootMs = $record.bootMs }
        }
        Start-Sleep -Seconds 2
    }
    throw "AVD '$Avd' ($serial) did not finish booting within $BootTimeoutSec s"
}

function Stop-DaEmulator {
    <#
    .SYNOPSIS
        Stops the emulator process this stand recorded, and nothing else. Returns a one-line outcome.
    .PARAMETER TimeoutSec
        Seconds to wait for a graceful exit before the recorded process tree is killed.
    #>
    param([int]$TimeoutSec = 60)
    $recordPath = Get-DaEmulatorRecordPath
    $record = Read-DaJson -Path $recordPath
    if (-not $record) { return 'no emulator recorded by this stand; nothing stopped' }
    $proc = Get-Process -Id $record.pid -ErrorAction SilentlyContinue
    $sameStart = $false
    if ($proc) {
        $sameStart = ($proc.StartTime.ToUniversalTime().ToString('o') -eq [string]$record.processStart)
    }
    if (-not $proc -or -not $sameStart) {
        Remove-Item -LiteralPath $recordPath -Force
        return "recorded pid $($record.pid) is gone or was recycled; nothing stopped"
    }
    if ($record.serial) {
        $null = Invoke-DaAdb -Serial ([string]$record.serial) -Arguments @('emu', 'kill') -TimeoutMs 15000
    }
    if (-not $proc.WaitForExit($TimeoutSec * 1000)) {
        $proc.Kill($true)
        $proc.WaitForExit()
    }
    Remove-Item -LiteralPath $recordPath -Force
    return "stopped emulator pid $($record.pid) ($($record.avd))"
}

function Get-DaSubject {
    <#
    .SYNOPSIS
        Describes the binary under test: package version, version code, install times, installed APK SHA-256 and its host artifact.
    .PARAMETER Serial
        A serial that already passed Assert-DaEmulatorTarget.
    .PARAMETER Package
        Application id.
    #>
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Package
    )
    $dump = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'dumpsys', 'package', $Package)
    $text = $dump.Lines -join "`n"
    if ($text -notmatch "Package \[$([regex]::Escape($Package))\]") {
        return [pscustomobject]@{ package = $Package; installed = $false }
    }
    $pick = { param($rx) if ($text -match $rx) { $Matches[1].Trim() } else { $null } }
    $pathOut = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'pm', 'path', $Package)
    $basePath = ($pathOut.Lines | Where-Object { $_ -match '^package:.*base\.apk$' } | Select-Object -First 1) -replace '^package:', ''
    $deviceSha = $null
    if ($basePath) {
        $sum = Invoke-DaAdb -Serial $Serial -Arguments @('shell', 'sha256sum', $basePath) -TimeoutMs 120000
        $deviceSha = (($sum.Lines | Select-Object -First 1) -split '\s+')[0]
    }
    $hostMatch = $null
    # The watch build shares the debug application id, so its artifact is looked up beside the phone's.
    $apkRoots = @(@('app_v2/build/outputs/apk', 'wear/build/outputs/apk') | ForEach-Object { Join-Path (Get-DaPaths).RepoRoot $_ } | Where-Object { Test-Path -LiteralPath $_ })
    if ($deviceSha -and $apkRoots.Count) {
        foreach ($apk in (Get-ChildItem -LiteralPath $apkRoots -Recurse -Filter '*.apk' -File)) {
            if ((Get-DaFileSha256 -Path $apk.FullName) -eq $deviceSha) {
                $hostMatch = [System.IO.Path]::GetRelativePath((Get-DaPaths).RepoRoot, $apk.FullName) -replace '\\', '/'
                break
            }
        }
    }
    return [pscustomobject]@{
        package          = $Package
        installed        = $true
        versionName      = & $pick 'versionName=(\S+)'
        versionCode      = & $pick 'versionCode=(\d+)'
        firstInstallTime = & $pick 'firstInstallTime=([^\n]+)'
        lastUpdateTime   = & $pick 'lastUpdateTime=([^\n]+)'
        apkSha256        = $deviceSha
        hostApk          = $hostMatch
        formFactor       = Get-DeviceFormFactor -Adb (Get-DaAdbPath) -Serial $Serial
    }
}
