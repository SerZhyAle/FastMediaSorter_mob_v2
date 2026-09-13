#requires -Version 7.0
<#
.SYNOPSIS
    Contract tests for scripts/devtest/lib/device-store-paths.ps1 (S3036).

.DESCRIPTION
    E1 is the only place either directory name is asserted literally. Everywhere else in the tree
    the name is now derived, which is the point of the ticket - so this suite is where a rename
    stops being free.

    E7 is the case that makes the derivation durable rather than momentary. S3030 protected both
    names in scripts/utils/temp-root-inventory.ps1 by hand, and that hand-written row is what
    S3036 replaced with a call into this declaration; if a later rename reaches the declaration but
    not the inventory, the archiver's age rule regains its reach over DEVICE.REGISTRY/ - the durable
    device roster Rule 35 reads, which is idle for a week in normal use and therefore looks exactly
    like abandoned scratch. E7 fails in that case, which is the only automatic warning that exists.

    E6 pins the invariant the two read-only consumers depend on: no function here creates a
    directory (S2855 - the registry's read verbs, the readiness probe and the monitor writer must
    leave a missing store missing). The fixture root is the proof surface: every function is called
    against it and it must stay empty.

    The fixture lives under this ticket's own scratch directory (CLAUDE.md Rule 10) and is removed
    by the suite. It never judges the shared temp/ root - a suite whose verdict depends on what
    other sessions are writing is the S3025 defect.

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$lib = Join-Path $PSScriptRoot '..\device-store-paths.ps1'
if (-not (Test-Path -LiteralPath $lib)) {
    Write-Error "declaration not found: $lib" -ErrorAction Continue
    exit 1
}
. $lib

# Four levels up: device-store-paths.tests -> lib -> devtest -> scripts -> repo root.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$inventoryLib = Join-Path $repoRoot 'scripts/utils/temp-root-inventory.ps1'
$fixture = Join-Path $repoRoot 'temp/S3036/store-fixture'
$failures = 0

function Reset-Fixture {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
    New-Item -ItemType Directory -Path $fixture -Force | Out-Null
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
    Assert-Case 'E1 the two leaf names are exactly what the tree carries' {
        $lease = Get-DeviceStoreDirName -Store Lease
        $registry = Get-DeviceStoreDirName -Store Registry
        if ($lease -ne 'DEVICE.LEASES') { return "expected: DEVICE.LEASES | actual: $lease" }
        if ($registry -ne 'DEVICE.REGISTRY') { return "expected: DEVICE.REGISTRY | actual: $registry" }
        return $null
    }

    Assert-Case 'E2 a store directory is <root>/temp/<leaf>' {
        foreach ($store in @('Lease', 'Registry')) {
            $expected = Join-Path (Join-Path $fixture 'temp') (Get-DeviceStoreDirName -Store $store)
            $actual = Get-DeviceStoreDir -RepoRoot $fixture -Store $store
            if ($actual -ne $expected) { return "$store - expected: $expected | actual: $actual" }
        }
        return $null
    }

    Assert-Case 'E3 a tcpip serial is encoded and a plain one is not' {
        $encoded = ConvertTo-DeviceSerialFileName -Serial '192.168.1.5:5555'
        if ($encoded -ne '192.168.1.5_5555') { return "expected: 192.168.1.5_5555 | actual: $encoded" }
        $plain = ConvertTo-DeviceSerialFileName -Serial 'emulator-5554'
        if ($plain -ne 'emulator-5554') { return "expected: emulator-5554 | actual: $plain" }
        return $null
    }

    Assert-Case 'E4 a record path is the directory plus the encoded serial plus .json' {
        $expected = Join-Path (Get-DeviceStoreDir -RepoRoot $fixture -Store Registry) '192.168.1.5_5555.json'
        $actual = Get-DeviceStoreRecordPath -RepoRoot $fixture -Store Registry -Serial '192.168.1.5:5555'
        if ($actual -ne $expected) { return "expected: $expected | actual: $actual" }
        return $null
    }

    Assert-Case 'E5 an unknown store is refused rather than answered' {
        foreach ($call in @(
                { Get-DeviceStoreDirName -Store 'Lock' },
                { Get-DeviceStoreDir -RepoRoot $fixture -Store 'Lock' },
                { Get-DeviceStoreRecordPath -RepoRoot $fixture -Store 'Lock' -Serial 'emulator-5554' }
            )) {
            $threw = $false
            try { $null = & $call } catch { $threw = $true }
            if (-not $threw) { return 'expected: a bind-time refusal | actual: a path was returned for an unknown store' }
        }
        return $null
    }

    Assert-Case 'E6 no function creates anything' {
        Reset-Fixture
        $null = Get-DeviceStoreDirName -Store Lease
        $null = Get-DeviceStoreDir -RepoRoot $fixture -Store Lease
        $null = Get-DeviceStoreDir -RepoRoot $fixture -Store Registry
        $null = ConvertTo-DeviceSerialFileName -Serial '192.168.1.5:5555'
        $null = Get-DeviceStoreRecordPath -RepoRoot $fixture -Store Registry -Serial '192.168.1.5:5555'
        $left = @(Get-ChildItem -LiteralPath $fixture -Force -ErrorAction SilentlyContinue)
        if ($left.Count -ne 0) { return "expected: 0 entries under the fixture | actual: $($left.Count) ($($left.Name -join ', '))" }
        return $null
    }

    Assert-Case 'E7 the temp-root inventory still derives both leaves' {
        if (-not (Test-Path -LiteralPath $inventoryLib)) { return "inventory declaration not found: $inventoryLib" }
        # Dot-sourced inside this scriptblock, not at suite scope: the inventory library is itself
        # dot-sourced by its consumers and S2441 is about exactly that reach into a caller's scope.
        . $inventoryLib
        $inventory = Get-TempRootInventory -RepoRoot $repoRoot
        foreach ($store in @('Lease', 'Registry')) {
            $leaf = Get-DeviceStoreDirName -Store $store
            if ($inventory.FixedDirs -notcontains $leaf) {
                return "$leaf is declared by the store lib but not protected by the temp-root inventory - the archiver's age rule can reach it"
            }
        }
        return $null
    }
}
finally {
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue }
}

if ($failures -gt 0) {
    Write-Error "device-store-paths.tests: expected: 0 | actual: $failures failing case(s)." -ErrorAction Continue
    exit 1
}
Write-Host 'device-store-paths.tests: PASS - 7 case(s).' -ForegroundColor Green
exit 0
