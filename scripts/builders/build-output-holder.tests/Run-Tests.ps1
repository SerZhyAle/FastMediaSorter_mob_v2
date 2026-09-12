# Run-Tests.ps1 (S2584) - regression suite for scripts/builders/build-output-holder.ps1.
#
# The defect being guarded: a hung Gradle test worker holds R.jar open, every later resource task for
# that variant dies with "Couldn't delete .. R.jar", and the reader attributes it to their own change.
# The whole verdict rests on ONE operation - opening the file with FileShare::None - so this suite
# asserts both directions of it, because a probe that only ever says "free" would look healthy on a
# green tree forever and fail exactly once, during the next incident, in the safe direction.
#
# Also asserted: the variant-directory naming (a flavorless module has no variant segment) and the
# path lookup, because a probe pointed at a path that does not exist returns "free" while observing
# nothing at all - the false green this helper exists to prevent.
#
# Hermetic: no Gradle is invoked and no real build directory is touched. The lock is taken by this
# same process against a sandbox file, which is exactly the condition the probe must detect.
#
# Usage:  pwsh -NoProfile -File scripts/builders/build-output-holder.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
. (Join-Path $repoRoot 'scripts/builders/build-output-holder.ps1')

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        Write-Host "  PASS  $name" -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host "  FAIL  $name -> $detail" -ForegroundColor Red
        $script:fail++
    }
}

$sandbox = Join-Path ([System.IO.Path]::GetTempPath()) "s2584-$([guid]::NewGuid().ToString('n'))"
$null = New-Item -ItemType Directory -Path $sandbox -Force

try {
    # --- Test-BuildOutputLocked -------------------------------------------------------------------

    $free = Join-Path $sandbox 'free.jar'
    Set-Content -LiteralPath $free -Value 'x' -Encoding ascii
    Assert-That 'an unlocked file reads as free' `
    (-not (Test-BuildOutputLocked -Path $free)) 'expected $false'

    $held = Join-Path $sandbox 'held.jar'
    Set-Content -LiteralPath $held -Value 'x' -Encoding ascii
    $handle = [System.IO.File]::Open(
        $held,
        [System.IO.FileMode]::Open,
        [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None)
    try {
        Assert-That 'a file held with FileShare::None reads as locked' `
        (Test-BuildOutputLocked -Path $held) 'expected $true while the handle is open'
    } finally {
        $handle.Dispose()
    }

    Assert-That 'the same file reads as free once the handle is released' `
    (-not (Test-BuildOutputLocked -Path $held)) 'expected $false after Dispose'

    Assert-That 'an absent file is not reported as locked' `
    (-not (Test-BuildOutputLocked -Path (Join-Path $sandbox 'nope.jar'))) 'expected $false'

    # --- Get-BuildOutputVariantDir ----------------------------------------------------------------

    Assert-That 'a flavored variant lower-cases only its first letter' `
    ((Get-BuildOutputVariantDir -Variant 'Standard' -BuildType 'Debug') -ceq 'standardDebug') `
        "got '$(Get-BuildOutputVariantDir -Variant 'Standard' -BuildType 'Debug')'"

    Assert-That 'noLegal keeps its inner capital' `
    ((Get-BuildOutputVariantDir -Variant 'NoLegal' -BuildType 'Debug') -ceq 'noLegalDebug') `
        "got '$(Get-BuildOutputVariantDir -Variant 'NoLegal' -BuildType 'Debug')'"

    # S2121: watchface declares no flavor dimension, so its directory carries no variant segment.
    Assert-That 'a flavorless module falls back to the bare build type' `
    ((Get-BuildOutputVariantDir -Variant '' -BuildType 'Debug') -ceq 'debug') `
        "got '$(Get-BuildOutputVariantDir -Variant '' -BuildType 'Debug')'"

    # --- Get-BuildOutputRJarPath ------------------------------------------------------------------

    $jarRoot = Join-Path $sandbox 'mod\build\intermediates\compile_and_runtime_r_class_jar'
    $stdDir = Join-Path $jarRoot 'standardDebug\processStandardDebugResources'
    $nlDir = Join-Path $jarRoot 'noLegalDebug\processNoLegalDebugResources'
    $null = New-Item -ItemType Directory -Path $stdDir -Force
    $null = New-Item -ItemType Directory -Path $nlDir -Force
    Set-Content -LiteralPath (Join-Path $stdDir 'R.jar') -Value 'x' -Encoding ascii
    Set-Content -LiteralPath (Join-Path $nlDir 'R.jar') -Value 'x' -Encoding ascii

    $std = @(Get-BuildOutputRJarPath -ProjectRoot $sandbox -Module 'mod' -VariantDir 'standardDebug')
    Assert-That 'the lookup finds exactly this run variant jar' `
    ($std.Count -eq 1 -and $std[0] -match 'standardDebug') "got $($std.Count): $($std -join ', ')"

    $all = @(Get-BuildOutputRJarPath -ProjectRoot $sandbox -Module 'mod' -VariantDir '')
    Assert-That 'an empty variant returns every jar under the module' `
    ($all.Count -eq 2) "got $($all.Count)"

    $none = @(Get-BuildOutputRJarPath -ProjectRoot $sandbox -Module 'mod' -VariantDir 'vrRelease')
    Assert-That 'a variant that was never built returns nothing to probe' `
    ($none.Count -eq 0) "got $($none.Count)"

    $absent = @(Get-BuildOutputRJarPath -ProjectRoot $sandbox -Module 'never-built' -VariantDir 'standardDebug')
    Assert-That 'a module with no build directory returns nothing to probe' `
    ($absent.Count -eq 0) "got $($absent.Count)"

    # --- Get-BuildOutputHolder --------------------------------------------------------------------

    # Identity is best-effort and must never throw: it decides nothing, and a diagnosis that crashes
    # would replace an unreadable failure with a worse one.
    $holders = @(Get-BuildOutputHolder -Module 'mod')
    Assert-That 'holder lookup returns an array and does not throw' `
    ($null -ne $holders) 'expected an array'
} finally {
    Remove-Item -LiteralPath $sandbox -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "passed=$script:pass failed=$script:fail" -ForegroundColor $(if ($script:fail) { 'Red' } else { 'Green' })
if ($script:fail -gt 0) { exit 1 }
exit 0
