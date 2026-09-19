#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for scripts/devtest/lib/device-state-journal.ps1 (S3201).

.DESCRIPTION
    Hermetic: no device, no adb. Judges the parts of the journal that decide what is put back -
    the parsers, the first-original-wins rule, drift detection and the restore command list - since
    a wrong answer from any of them restores the wrong value on the owner's watch while reporting
    success.

    J1 is the incident itself: the watch printed "Physical density: 340 / Override density: 380",
    and the value to restore is "no override", which is `wm density reset`, not `wm density 340`.

    The fixture lives under temp/S3201/ (Rule 10) and is removed by the suite.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$lib = Join-Path $PSScriptRoot '..\device-state-journal.ps1'
if (-not (Test-Path -LiteralPath $lib)) {
    Write-Error "library not found: $lib" -ErrorAction Continue
    exit 1
}
. $lib

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$fixture = Join-Path $repoRoot 'temp/S3201/journal-fixture'
$failures = 0
$cases = 0

function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
    $script:cases++
    try {
        $message = & $Body
        if ($message) {
            Write-Host "FAIL  $Name - $message" -ForegroundColor Red
            $script:failures++
        }
        else {
            Write-Host "PASS  $Name" -ForegroundColor Green
        }
    }
    catch {
        Write-Host "FAIL  $Name - threw: $($_.Exception.Message)" -ForegroundColor Red
        $script:failures++
    }
}

try {
    Assert-Case 'J1 an override density is read, and its absence is null' {
        $watch = "Physical density: 340`nOverride density: 380"
        $v = ConvertFrom-WmOverrideOutput -Text $watch
        if ($v -ne '380') { return "expected: 380 | actual: $v" }
        $plain = ConvertFrom-WmOverrideOutput -Text 'Physical density: 340'
        if ($null -ne $plain) { return "expected: null | actual: $plain" }
        return $null
    }

    Assert-Case 'J2 a size override is read' {
        $v = ConvertFrom-WmOverrideOutput -Text "Physical size: 480x480`r`nOverride size: 400x400"
        if ($v -ne '400x400') { return "expected: 400x400 | actual: $v" }
        return $null
    }

    Assert-Case 'J3 a never-written setting is null' {
        foreach ($t in @('null', '', '  null  ')) {
            $v = ConvertFrom-SettingsValue -Text $t
            if ($null -ne $v) { return "expected: null for '$t' | actual: $v" }
        }
        if ((ConvertFrom-SettingsValue -Text "1.15`n") -ne '1.15') { return 'expected: 1.15 kept' }
        return $null
    }

    Assert-Case 'J4 shell mutations map to keys, reads map to nothing' {
        $map = @{
            'wm density 380'                 = 'wm.density'
            'wm density reset'               = 'wm.density'
            'wm size 400x400'                = 'wm.size'
            'settings put system font_scale 1.3' = 'settings.system.font_scale'
            'settings delete secure foo'     = 'settings.secure.foo'
        }
        foreach ($cmd in $map.Keys) {
            $r = ConvertFrom-ShellMutation -Command $cmd
            if ($null -eq $r -or $r.Key -ne $map[$cmd]) { return "expected: $($map[$cmd]) for '$cmd' | actual: $($r.Key)" }
        }
        foreach ($read in @('wm density', 'settings get system font_scale', 'logcat -d')) {
            $r = ConvertFrom-ShellMutation -Command $read
            if ($null -ne $r) { return "expected: null for read '$read' | actual: $($r.Key)" }
        }
        return $null
    }

    Assert-Case 'J5 the first original wins' {
        $j = New-DeviceStateJournal -Serial 'RFGL1148CRZ'
        $first = Add-DeviceStateOriginal -Journal $j -Key 'wm.density' -Original $null
        $second = Add-DeviceStateOriginal -Journal $j -Key 'wm.density' -Original '380'
        if (-not $first -or $second) { return "expected: added then refused | actual: $first, $second" }
        if ($null -ne $j.entries['wm.density'].original) { return "expected: null original kept | actual: $($j.entries['wm.density'].original)" }
        return $null
    }

    Assert-Case 'J6 restore commands: reset for null, write for a value' {
        $a = (Get-DeviceStateRestoreCommand -Key 'wm.density' -Original $null) -join ' '
        if ($a -ne 'shell wm density reset') { return "expected: shell wm density reset | actual: $a" }
        $b = (Get-DeviceStateRestoreCommand -Key 'wm.size' -Original '400x400') -join ' '
        if ($b -ne 'shell wm size 400x400') { return "actual: $b" }
        $c = (Get-DeviceStateRestoreCommand -Key 'settings.system.font_scale' -Original $null) -join ' '
        if ($c -ne 'shell settings delete system font_scale') { return "actual: $c" }
        $d = (Get-DeviceStateRestoreCommand -Key 'settings.system.font_scale' -Original '1.3') -join ' '
        if ($d -ne 'shell settings put system font_scale 1.3') { return "actual: $d" }
        return $null
    }

    Assert-Case 'J7 DataStore drift: changed, vanished and new files' {
        $rec = [ordered]@{ 'wear_settings.preferences_pb' = 'aa'; 'wear_now_playing.preferences_pb' = 'bb' }
        $cur = [ordered]@{ 'wear_settings.preferences_pb' = 'cc'; 'wear_tiles.preferences_pb' = 'dd' }
        $d = @(Compare-DeviceStateFiles -Recorded $rec -Current $cur)
        $got = (@($d | ForEach-Object { "$($_.Action):$($_.Name)" }) | Sort-Object) -join ','
        $want = 'remove:wear_tiles.preferences_pb,restore:wear_now_playing.preferences_pb,restore:wear_settings.preferences_pb'
        if ($got -ne $want) { return "expected: $want | actual: $got" }
        $none = @(Compare-DeviceStateFiles -Recorded $rec -Current $rec)
        if (@($none).Count -ne 0) { return "expected: no drift | actual: $(@($none).Count)" }
        return $null
    }

    Assert-Case 'J8 a journal survives a write and a read with a null original' {
        if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
        $path = Join-Path $fixture 'RFGL1148CRZ.json'
        $j = New-DeviceStateJournal -Serial 'RFGL1148CRZ'
        Add-DeviceStateOriginal -Journal $j -Key 'wm.density' -Original $null | Out-Null
        Add-DeviceStateOriginal -Journal $j -Key 'settings.system.font_scale' -Original '1.0' | Out-Null
        Write-DeviceStateJournal -Path $path -Journal $j
        $back = Read-DeviceStateJournal -Path $path
        if (-not $back.entries.Contains('wm.density')) { return 'expected: wm.density entry present after read' }
        if ($null -ne $back.entries['wm.density'].original) { return "expected: null | actual: $($back.entries['wm.density'].original)" }
        if ($back.entries['settings.system.font_scale'].original -ne '1.0') { return 'expected: 1.0 kept' }
        return $null
    }

    Assert-Case 'J9 a changed scalar is drift, an equal one is not' {
        if (-not (Compare-DeviceStateScalar -Original $null -Current '380')) { return 'expected: drift null -> 380' }
        if (Compare-DeviceStateScalar -Original $null -Current $null) { return 'expected: no drift null -> null' }
        if (Compare-DeviceStateScalar -Original '1.0' -Current '1.0') { return 'expected: no drift 1.0 -> 1.0' }
        return $null
    }
}
finally {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue }
}

if ($failures -gt 0) {
    Write-Error "device-state-journal.tests: expected: 0 | actual: $failures failing case(s)." -ErrorAction Continue
    exit 1
}
Write-Host "device-state-journal.tests: PASS - $cases case(s)." -ForegroundColor Green
exit 0
