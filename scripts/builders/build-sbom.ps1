<#
.SYNOPSIS
    Produce the CycloneDX SBOM for one module (S3371).
.DESCRIPTION
    Uses the shared build-domain lock locally and in CI. CycloneDX 3.x has a direct
    task per module; its aggregate task is not the contract for a module SBOM.
    A fresh report is required, so a stale bom.json cannot certify a failed producer.
.PARAMETER Module
    The phone or Wear OS module to resolve.
.PARAMETER OutDir
    Optional destination directory, relative to the project root unless absolute.
.NOTES
    Exit codes:
      0 - a fresh SBOM was written; its path is printed.
      1 - Gradle failed, the report is absent, or report publication failed.
      2 - the Gradle wrapper or required SZA harness is unavailable.
      4 - the module's build domain is queued (Enter-BuildLockOrExit).
.EXAMPLE
    pwsh -NoProfile -File scripts/builders/build-sbom.ps1 -Module wear -OutDir artifacts/sbom
#>
[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module = 'app_v2',
    [string]$OutDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$domain = if ($Module -eq 'wear') { 'Build.Wear' } else { 'Build.Phone' }
$wrapperName = if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' }
$gradlew = Join-Path $projectRoot $wrapperName
if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) {
    Write-Host "build-sbom: $wrapperName not found at $gradlew" -ForegroundColor Red
    exit 2
}

# Gradle defaults to the current user's .gradle directory on both platforms. The
# shared lock's daemon probe needs this explicit on Linux, where USERPROFILE is absent.
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $HOME '.gradle' }
. (Join-Path $PSScriptRoot '../utils/agent-lock.ps1')
Enter-BuildLockOrExit -Reason "build-sbom.ps1 ($Module)" -Domain $domain
Push-Location $projectRoot
try {
    $report = Join-Path $projectRoot "$Module/build/reports/cyclonedx-direct/bom.json"
    # Remove only this task's report, after acquiring its domain. Gradle will rerun the
    # producer when its declared output is missing; other module reports stay untouched.
    if (Test-Path -LiteralPath $report) { Remove-Item -LiteralPath $report -Force }
    Write-Host "Producing the CycloneDX SBOM for $Module .." -ForegroundColor Cyan
    & $gradlew ":${Module}:cyclonedxDirectBom" --configuration-cache
    if ($LASTEXITCODE -ne 0) {
        Write-Host "build-sbom: cyclonedxDirectBom failed (exit $LASTEXITCODE)." -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path -LiteralPath $report -PathType Leaf)) {
        Write-Host 'build-sbom: the task reported success but no direct bom.json was written.' -ForegroundColor Red
        exit 1
    }
    $destination = $report
    if ($OutDir) {
        $null = New-Item -ItemType Directory -Path $OutDir -Force
        $destination = Join-Path $OutDir "sbom-$Module.json"
        Copy-Item -LiteralPath $report -Destination $destination -Force
    }
    Write-Host "SBOM: $destination" -ForegroundColor Green
    exit 0
}
catch {
    Write-Host "build-sbom: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
finally {
    Pop-Location
    Exit-AgentLock -Name 'Build' -Domains @($domain)
}
