<#
.SYNOPSIS
    Hermetic regression suite for the module SBOM builder and licence configuration.
.DESCRIPTION
    Uses a fake Gradle wrapper and lock adapter in a disposable checkout. No Android
    SDK, real Gradle invocation, build output or workstation lock is touched.
.NOTES
    Exit codes: 0 - all cases passed; 1 - a regression or test failure was found.
.EXAMPLE
    pwsh -NoProfile -File scripts/builders/build-sbom.tests/Run-Tests.ps1
#>
# Subject: .github/workflows/dependency-scan.yml, scripts/quality/dependency-license-allowlist.json
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$fixture = Join-Path ([IO.Path]::GetTempPath()) "sbom-$([guid]::NewGuid().ToString('n'))"
$pwsh = (Get-Process -Id $PID).Path
$passed = 0
$failed = 0
function Assert-Case([string]$Name, [bool]$Condition) {
    if ($Condition) { Write-Host "PASS $Name"; $script:passed++ }
    else { Write-Host "FAIL $Name"; $script:failed++ }
}
function Invoke-Case([string]$Mode, [string]$Module = 'app_v2', [string]$OutDir = 'artifacts/sbom') {
    $env:SBOM_TEST_MODE = $Mode
    foreach ($f in @('acquired.txt', 'released.txt', 'args.txt')) {
        Remove-Item -LiteralPath (Join-Path $fixture $f) -Force -ErrorAction SilentlyContinue
    }
    $result = & $pwsh -NoProfile -NonInteractive -File (Join-Path $fixture 'scripts/builders/build-sbom.ps1') -Module $Module -OutDir $OutDir 2>&1
    $code = $LASTEXITCODE
    Assert-Case "$Mode/$Module exit code" ($code -eq $(if ($Mode -eq 'success') { 0 } else { 1 }))
    Assert-Case "$Mode/$Module released its lock" (Test-Path (Join-Path $fixture 'released.txt'))
    return $result
}
$oldMode = $env:SBOM_TEST_MODE
try {
    $null = New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts/builders'), (Join-Path $fixture 'scripts/utils') -Force
    Copy-Item (Join-Path $repo 'scripts/builders/build-sbom.ps1') (Join-Path $fixture 'scripts/builders/build-sbom.ps1')
    Set-Content (Join-Path $fixture 'scripts/utils/agent-lock.ps1') @'
function Enter-BuildLockOrExit {
    param($Reason, $Domain)
    Set-Content (Join-Path $PSScriptRoot '../../acquired.txt') $Domain
}
function Exit-AgentLock {
    param($Name, $Domains)
    Set-Content (Join-Path $PSScriptRoot '../../released.txt') ($Domains -join ',')
}
'@
    Set-Content (Join-Path $fixture 'fake-gradle.ps1') @'
Set-Content 'args.txt' ($args -join ' ')
Set-Content 'gradle-home.txt' $env:GRADLE_USER_HOME
if ($env:SBOM_TEST_MODE -eq 'failure') { exit 17 }
if ($env:SBOM_TEST_MODE -eq 'empty') { exit 0 }
$module = ($args[0] -split ':')[1]
$dir = "$module/build/reports/cyclonedx-direct"
$null = New-Item -ItemType Directory -Path $dir -Force
Set-Content "$dir/bom.json" '{"bomFormat":"CycloneDX","specVersion":"1.6"}'
exit 0
'@
    if ($IsWindows) {
        Set-Content (Join-Path $fixture 'gradlew.bat') "@echo off`r`n`"$pwsh`" -NoProfile -File `"%~dp0fake-gradle.ps1`" %*`r`nexit /b %errorlevel%"
    } else {
        Set-Content (Join-Path $fixture 'gradlew') "#!/bin/sh`nexec '$pwsh' -NoProfile -File ./fake-gradle.ps1 `"`$@`"" -Encoding utf8NoBOM
        & chmod +x (Join-Path $fixture 'gradlew')
        if ($LASTEXITCODE -ne 0) { throw 'Cannot make fake Gradle executable.' }
    }
    foreach ($module in @('app_v2', 'wear')) {
        $null = Invoke-Case 'success' $module
        Assert-Case "$module used the direct task" ((Get-Content (Join-Path $fixture 'args.txt') -Raw).Trim() -eq ":${module}:cyclonedxDirectBom --configuration-cache")
        Assert-Case "$module acquired the correct domain" ((Get-Content (Join-Path $fixture 'acquired.txt') -Raw).Trim() -eq $(if ($module -eq 'wear') { 'Build.Wear' } else { 'Build.Phone' }))
        Assert-Case "$module copied the report relative to project root" (Test-Path (Join-Path $fixture "artifacts/sbom/sbom-$module.json"))
    }
    $null = Invoke-Case 'failure'
    $report = Join-Path $fixture 'app_v2/build/reports/cyclonedx-direct/bom.json'
    $null = New-Item -ItemType Directory -Path (Split-Path $report) -Force
    Set-Content $report '{"stale":true}'
    $null = Invoke-Case 'empty'
    Assert-Case 'stale report cannot turn an empty producer green' (-not (Test-Path $report))
    $null = Invoke-Case 'success' 'wear' (Join-Path $fixture 'absolute-output')
    Assert-Case 'Gradle home uses the configured or portable default' ((Get-Content (Join-Path $fixture 'gradle-home.txt') -Raw).Trim() -eq $(if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $HOME '.gradle' }))
    Assert-Case 'absolute output directory is supported' (Test-Path (Join-Path $fixture 'absolute-output/sbom-wear.json'))
    Remove-Item (Join-Path $fixture $(if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' }))
    $result = & $pwsh -NoProfile -NonInteractive -File (Join-Path $fixture 'scripts/builders/build-sbom.ps1') 2>&1
    Assert-Case 'missing wrapper returns tooling exit 2' ($LASTEXITCODE -eq 2)
    Assert-Case 'missing wrapper prints a reason' (($result -join "`n") -match 'not found')
    $allowlist = Get-Content (Join-Path $repo 'scripts/quality/dependency-license-allowlist.json') -Raw | ConvertFrom-Json
    $packages = @($allowlist.licenses | Where-Object { -not $_.spdx } | ForEach-Object { $_.packages })
    Assert-Case 'licence exceptions are nonempty Maven package URLs' ($packages.Count -gt 0 -and @($packages | Where-Object { $_ -notmatch '^pkg:maven/[^/:\s]+/[^/:\s]+$' }).Count -eq 0)
    $workflow = Get-Content (Join-Path $repo '.github/workflows/dependency-scan.yml') -Raw
    $sbomJob = ($workflow -split '(?m)^  sbom:', 2)[1]
    Assert-Case 'SBOM CI installs the required harness' ($sbomJob -match 'SZA_HARNESS_ROOT=')
    Assert-Case 'each module failure stops the workflow step' ([regex]::Matches($sbomJob, 'if \(\$LASTEXITCODE -ne 0\) \{ exit \$LASTEXITCODE \}').Count -eq 2)
} catch {
    Write-Host "FAIL suite: $($_.Exception.Message)"
    $failed++
} finally {
    $env:SBOM_TEST_MODE = $oldMode
    Remove-Item -LiteralPath $fixture -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host "build-sbom tests: $passed passed, $failed failed"
if ($failed -gt 0) { Write-Host 'SBOM regression suite failed.'; exit 1 }
exit 0
