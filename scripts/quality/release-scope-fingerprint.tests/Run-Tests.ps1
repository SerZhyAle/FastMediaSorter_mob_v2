#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/quality/release-scope-fingerprint.ps1 (S3010).

.DESCRIPTION
    Every case runs against a fixture tree that FMS_REPO_ROOT points the script at, so no case reads
    or writes the repository's own temp/RELEASE-FREEZE-FINGERPRINTS.json - which a live sweep may be
    holding at the time.

    The direction that matters most is the false NEGATIVE: a fingerprint that reports "stable" over a
    tree that moved would let a sweep certify a gate nobody re-ran, which is the exact failure the
    whole ticket exists to close. Hence a case per kind of movement - content, addition, deletion -
    and a case pinning that an unrecorded group counts as moved rather than as absent.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$script = Join-Path (Split-Path -Parent $PSScriptRoot) 'release-scope-fingerprint.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Error "script not found: $script" -ErrorAction Continue
    exit 1
}

$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") { "$env:ProgramFiles\PowerShell\7\pwsh.exe" } else { 'pwsh' }

$failures = 0
$root = Join-Path ([System.IO.Path]::GetTempPath()) ("fingerprint-tests-" + [guid]::NewGuid().ToString('n').Substring(0, 8))
foreach ($dir in @('temp', 'docs', 'app_v2\src', 'wear\src', 'play', 'scripts', 'PLAN\archive')) {
    New-Item -ItemType Directory -Path (Join-Path $root $dir) -Force | Out-Null
}
Set-Content -LiteralPath (Join-Path $root 'docs\one.md') -Value 'one' -Encoding UTF8
Set-Content -LiteralPath (Join-Path $root 'app_v2\src\Main.kt') -Value 'class Main' -Encoding UTF8

$recordPath = Join-Path $root 'temp\RELEASE-FREEZE-FINGERPRINTS.json'

$previousRoot = $env:FMS_REPO_ROOT
$env:FMS_REPO_ROOT = $root

function Invoke-Fingerprint {
    param([string[]] $FingerprintArgs)
    & $pwshExe -NoProfile -File $script @FingerprintArgs *> $null
    return $LASTEXITCODE
}

function Get-CompareJson {
    $out = & $pwshExe -NoProfile -File $script -Verb Compare -Json 2>$null
    if (-not $out) { return $null }
    return ($out | Out-String).Trim() | ConvertFrom-Json
}

function Assert-Case {
    param([string] $Name, [scriptblock] $Body)
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
    Assert-Case 'E1 Compare with no record exits 2' {
        Remove-Item -LiteralPath $recordPath -Force -ErrorAction SilentlyContinue
        $code = Invoke-Fingerprint @('-Verb', 'Compare')
        if ($code -ne 2) { return "expected exit 2, got $code" }
        return $null
    }

    Assert-Case 'E2 Record writes every group' {
        $code = Invoke-Fingerprint @('-Verb', 'Record')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        if (-not (Test-Path -LiteralPath $recordPath)) { return 'no record written' }
        $record = Get-Content -LiteralPath $recordPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $names = @($record.groups.PSObject.Properties.Name)
        foreach ($expected in @('phone-src', 'wear-src', 'play-listing', 'docs', 'scripts', 'specs-archive')) {
            if ($names -notcontains $expected) { return "group '$expected' missing from the record" }
        }
        return $null
    }

    Assert-Case 'E3 an unchanged tree compares stable' {
        $code = Invoke-Fingerprint @('-Verb', 'Compare')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        $state = Get-CompareJson
        if ($state.status -ne 'stable') { return "expected status stable, got '$($state.status)'" }
        return $null
    }

    Assert-Case 'E4 a changed file moves its group and only its group' {
        Set-Content -LiteralPath (Join-Path $root 'docs\one.md') -Value 'one changed' -Encoding UTF8
        $code = Invoke-Fingerprint @('-Verb', 'Compare')
        if ($code -ne 3) { return "expected exit 3, got $code" }
        $state = Get-CompareJson
        if (@($state.moved) -notcontains 'docs') { return "expected docs among moved, got '$($state.moved -join ',')'" }
        if (@($state.moved) -contains 'phone-src') { return 'phone-src reported moved although nothing under it changed' }
        return $null
    }

    Assert-Case 'E5 a new file moves its group' {
        [void](Invoke-Fingerprint @('-Verb', 'Record'))
        Set-Content -LiteralPath (Join-Path $root 'wear\src\Watch.kt') -Value 'class Watch' -Encoding UTF8
        $code = Invoke-Fingerprint @('-Verb', 'Compare')
        if ($code -ne 3) { return "expected exit 3, got $code" }
        $state = Get-CompareJson
        if (@($state.moved) -notcontains 'wear-src') { return "expected wear-src among moved, got '$($state.moved -join ',')'" }
        return $null
    }

    Assert-Case 'E6 a deleted file moves its group' {
        [void](Invoke-Fingerprint @('-Verb', 'Record'))
        Remove-Item -LiteralPath (Join-Path $root 'wear\src\Watch.kt') -Force
        $code = Invoke-Fingerprint @('-Verb', 'Compare')
        if ($code -ne 3) { return "expected exit 3, got $code" }
        $state = Get-CompareJson
        if (@($state.moved) -notcontains 'wear-src') { return "expected wear-src among moved, got '$($state.moved -join ',')'" }
        return $null
    }

    # An older record carries fewer groups than the script now declares. Reporting the new one as
    # unchanged would silently exempt a whole class of gate from the second pass.
    Assert-Case 'E7 a group the record does not carry counts as moved' {
        [void](Invoke-Fingerprint @('-Verb', 'Record'))
        $record = Get-Content -LiteralPath $recordPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $record.groups.PSObject.Properties.Remove('docs')
        Set-Content -LiteralPath $recordPath -Value ($record | ConvertTo-Json -Depth 6) -Encoding UTF8
        $code = Invoke-Fingerprint @('-Verb', 'Compare')
        if ($code -ne 3) { return "expected exit 3, got $code" }
        $state = Get-CompareJson
        if (@($state.moved) -notcontains 'docs') { return "expected docs among moved, got '$($state.moved -join ',')'" }
        return $null
    }

    Assert-Case 'E8 an unreadable record exits 2' {
        Set-Content -LiteralPath $recordPath -Value 'not json' -Encoding UTF8
        $code = Invoke-Fingerprint @('-Verb', 'Compare')
        if ($code -ne 2) { return "expected exit 2, got $code" }
        return $null
    }

    Assert-Case 'E9 a missing group directory is not an error' {
        Remove-Item -LiteralPath (Join-Path $root 'play') -Recurse -Force
        $code = Invoke-Fingerprint @('-Verb', 'Record')
        if ($code -ne 0) { return "expected exit 0, got $code" }
        return $null
    }
}
finally {
    if ($null -eq $previousRoot) { Remove-Item Env:\FMS_REPO_ROOT -ErrorAction SilentlyContinue }
    else { $env:FMS_REPO_ROOT = $previousRoot }
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

if ($failures -gt 0) {
    Write-Host "release-scope-fingerprint tests: $failures case(s) failed." -ForegroundColor Red
    exit 1
}
Write-Host 'release-scope-fingerprint tests: all cases passed.' -ForegroundColor Green
exit 0
