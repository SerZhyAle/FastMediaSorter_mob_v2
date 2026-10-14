#requires -Version 7.0
<#
.SYNOPSIS
    S4249 contract suite for the watch half's decision function and its device refusal.

# Subject: scripts/devtest/prerelease-wear-half.ps1, scripts/devtest/lib/device-half-plan.ps1

.DESCRIPTION
    Hermetic: dot-sources scripts/devtest/lib/device-half-plan.ps1 and runs
    scripts/devtest/prerelease-wear-half.ps1 only with ids it refuses before any gate or device is
    touched. No adb call, no device. Pins strategic S4249 section 7: a missing branch is a refusal,
    never a silent pass.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "scripts/devtest/prerelease-wear-half.ps1"

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$lib = Join-Path $repoRoot 'scripts/devtest/lib/device-half-plan.ps1'
$half = Join-Path $repoRoot 'scripts/devtest/prerelease-wear-half.ps1'
foreach ($required in @($lib, $half)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "FAIL | not found: $required" -ForegroundColor Red
        exit 1
    }
}
. $lib

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    if ("$Expected" -eq "$Actual") {
        Write-Host "PASS | $Label"
        $script:passed++
        return
    }
    Write-Host "FAIL | $Label" -ForegroundColor Red
    Write-Host "     | expected: $Expected | actual: $Actual" -ForegroundColor Red
    $script:failed++
}

function New-Stages {
    param([hashtable]$Override = @{})
    $base = [ordered]@{ gate1 = @('gate', 0); gate2 = @('gate', 0); build = @('setup', 0); migration = @('migration', 0)
                        install = @('setup', 0); benchS = @('bench', 0); benchL = @('bench', 0); walkS = @('walk', 0)
                        walkL = @('walk', 0); verdictS = @('verdict', 0); verdictL = @('verdict', 0) }
    foreach ($k in $Override.Keys) { $base[$k] = $Override[$k] }
    return @($base.Values | ForEach-Object { [pscustomobject]@{ kind = $_[0]; exit = $_[1] } })
}

$cleanVerdict = [pscustomobject]@{ pass = $true; blocked = $false }
$blockedVerdict = [pscustomobject]@{ pass = $false; blocked = $true }
$failedVerdict = [pscustomobject]@{ pass = $false; blocked = $false }

$scratch = Join-Path ([System.IO.Path]::GetTempPath()) ('s4249-wear-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch -Force | Out-Null

try {
    Assert-Equal -Label 'fold: a clean run exits 0' -Expected 0 `
        -Actual (Get-WearHalfExitCode -Stages (New-Stages) -Verdicts @($cleanVerdict, $cleanVerdict))
    Assert-Equal -Label 'fold: a blocked verdict exits 1' -Expected 1 `
        -Actual (Get-WearHalfExitCode -Stages (New-Stages @{ verdictL = @('verdict', 1) }) -Verdicts @($cleanVerdict, $blockedVerdict))
    Assert-Equal -Label 'fold: a failed verdict exits 1' -Expected 1 `
        -Actual (Get-WearHalfExitCode -Stages (New-Stages @{ verdictS = @('verdict', 1) }) -Verdicts @($failedVerdict, $cleanVerdict))
    Assert-Equal -Label 'fold: a failed migration run exits 1' -Expected 1 `
        -Actual (Get-WearHalfExitCode -Stages (New-Stages @{ migration = @('migration', 1) }) -Verdicts @($cleanVerdict, $cleanVerdict))
    Assert-Equal -Label 'fold: a migration run that could not run exits 2' -Expected 2 `
        -Actual (Get-WearHalfExitCode -Stages (New-Stages @{ migration = @('migration', 2) }) -Verdicts @($cleanVerdict, $cleanVerdict))
    Assert-Equal -Label 'fold: a walk exit 2 exits 2' -Expected 2 `
        -Actual (Get-WearHalfExitCode -Stages (New-Stages @{ walkS = @('walk', 2) }) -Verdicts @($cleanVerdict, $cleanVerdict))
    Assert-Equal -Label 'fold: a missing verdict artifact exits 2' -Expected 2 `
        -Actual (Get-WearHalfExitCode -Stages (New-Stages) -Verdicts @($cleanVerdict, $null))
    Assert-Equal -Label 'fold: a failed setup stage exits 2' -Expected 2 `
        -Actual (Get-WearHalfExitCode -Stages (New-Stages @{ install = @('setup', 1) }) -Verdicts @($cleanVerdict, $cleanVerdict))
    Assert-Equal -Label 'fold: an early stop on a content gate keeps 1' -Expected 1 `
        -Actual (Get-WearHalfExitCode -Stages @([pscustomobject]@{ kind = 'gate'; exit = 1 }, [pscustomobject]@{ kind = 'gate'; exit = 0 }) -Verdicts @() -ExpectedVerdicts 0)
    Assert-Equal -Label 'fold: no stage at all exits 2' -Expected 2 `
        -Actual (Get-WearHalfExitCode -Stages @() -Verdicts @($cleanVerdict, $cleanVerdict))

    $null = & pwsh -NoProfile -File $half -SmallDeviceId 'RFGL1148CRZ' -LargeDeviceId 'emulator-5562' -OutRoot $scratch -Json 2>&1
    Assert-Equal -Label 'script: a physical watch serial is refused with exit 2' -Expected 2 -Actual $LASTEXITCODE
    $null = & pwsh -NoProfile -File $half -SmallDeviceId 'emulator-5560' -LargeDeviceId 'emulator-5560' -OutRoot $scratch -Json 2>&1
    Assert-Equal -Label 'script: one emulator for both shapes is refused with exit 2' -Expected 2 -Actual $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "prerelease-wear-half.tests: $script:passed passed, $script:failed failed."
if ($script:failed -gt 0) { exit 1 }
exit 0
