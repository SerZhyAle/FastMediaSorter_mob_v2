#requires -Version 7.0
<#
.SYNOPSIS
    S4249 contract suite for scripts/release/assert-sweep-verdicts.ps1 - the release-point read of
    the phone and watch sweep verdicts.

.DESCRIPTION
    Hermetic: fixture verdict files and fingerprint records in a scratch tree under the system temp
    directory, passed through -RepoRoot. File times are set explicitly, so staleness is decided by
    the fixture and not by the clock. Pins strategic S4249 section 11.7: a verdict missing a
    blocking branch is never a pass.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/run-script-suites.ps1 -ChangedFiles "scripts/release/assert-sweep-verdicts.ps1"

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$script = Join-Path $repoRoot 'scripts/release/assert-sweep-verdicts.ps1'
if (-not (Test-Path -LiteralPath $script)) {
    Write-Host "FAIL | not found: $script" -ForegroundColor Red
    exit 1
}

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

$root = Join-Path ([System.IO.Path]::GetTempPath()) ('s4249-verdicts-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $root 'temp') -Force | Out-Null
$recordedAt = [datetime]'2026-10-14T10:00:00'
foreach ($name in @('RELEASE-FREEZE-FINGERPRINTS.json', 'RELEASE-FREEZE-FINGERPRINTS-WEAR.json')) {
    Set-Content -LiteralPath (Join-Path $root "temp\$name") -Encoding utf8NoBOM `
        -Value (@{ schema = 1; recordedAt = $recordedAt.ToString('s'); groups = @{} } | ConvertTo-Json)
}

function New-Verdict {
    param([string]$Name, [bool]$Pass = $true, [bool]$Blocked = $false, [datetime]$Written = $recordedAt.AddMinutes(30))
    $path = Join-Path $root $Name
    Set-Content -LiteralPath $path -Encoding utf8NoBOM -Value (@{ pass = $Pass; blocked = $Blocked; breakdown = @{} } | ConvertTo-Json)
    (Get-Item -LiteralPath $path).LastWriteTime = $Written
    return $path
}

function Invoke-Check {
    param([string[]]$CheckArgs)
    $null = & pwsh -NoProfile -File $script @CheckArgs -RepoRoot $root 2>&1
    return $LASTEXITCODE
}

try {
    $phone = New-Verdict -Name 'phone.json'
    $small = New-Verdict -Name 'small.json'
    $large = New-Verdict -Name 'large.json'

    Assert-Equal -Label 'both pass exits 0' -Expected 0 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$large"))

    $wearFail = New-Verdict -Name 'wear-fail.json' -Pass $false
    Assert-Equal -Label 'a failed watch verdict exits 1' -Expected 1 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$wearFail"))

    $wearBlocked = New-Verdict -Name 'wear-blocked.json' -Pass $false -Blocked $true
    Assert-Equal -Label 'a blocked verdict exits 1' -Expected 1 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$wearBlocked"))

    $phoneStale = New-Verdict -Name 'phone-stale.json' -Written $recordedAt.AddMinutes(-5)
    Assert-Equal -Label 'a verdict file older than its fingerprint exits 1' -Expected 1 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phoneStale, '-WearVerdicts', "$small,$large"))

    Assert-Equal -Label 'a missing watch verdict exits 2' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$(Join-Path $root 'absent.json')"))

    Assert-Equal -Label '-NoWear with a reason and a passing phone verdict exits 0' -Expected 0 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-NoWear', '-NoWearReason', 'phone-only hotfix'))

    Assert-Equal -Label '-NoWear without a reason is refused' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-NoWear'))

    Assert-Equal -Label 'no watch verdict and no -NoWear is refused' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone))

    Remove-Item -LiteralPath (Join-Path $root 'temp\RELEASE-FREEZE-FINGERPRINTS-WEAR.json') -Force
    Assert-Equal -Label 'a scope with no fingerprint record exits 2' -Expected 2 `
        -Actual (Invoke-Check @('-PhoneVerdict', $phone, '-WearVerdicts', "$small,$large"))
}
finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "assert-sweep-verdicts.tests: $script:passed passed, $script:failed failed."
if ($script:failed -gt 0) { exit 1 }
exit 0
