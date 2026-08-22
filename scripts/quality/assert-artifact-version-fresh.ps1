#requires -Version 7.0
<#
.SYNOPSIS
    Gate: a packaged artifact must carry the version of its own build, not a historical constant.

.DESCRIPTION
    This is the only check in the repository that judges a produced artifact rather than source
    text (S1873). Every other version check reads build.gradle.kts, and build.gradle.kts is exactly
    what a stale artifact agrees with - which is how a wear debug APK built on 2026-08-21 came to
    report 2.60.8151.612 and downgrade the watch it was installed on.

    Two independent findings, either of which fails the gate:

      - DRIFT. The versionName decodes to an instant more than -ToleranceMinutes away from the
        artifact's own last-write time. This catches staleness without knowing anything about what
        the checked-in constants happen to say today.
      - CONSTANT. The versionCode equals the checked-in `val defaultAppVersionCode` of the module
        the artifact belongs to. A packaging path that passed no version property lands here
        exactly, and the message names the path so the caller knows which script to fix.

    "Cannot verify" is deliberately a third answer, not a pass: absent metadata, unreadable JSON or
    a versionName that does not parse all exit 2. A caller that treats 2 as clean is reporting
    "did not look" as "looked and found nothing", which is the failure mode this gate exists to
    end.

.PARAMETER Path
    An .apk, an .aab, or a directory containing output-metadata.json. A directory is the usual
    form - AGP writes the metadata beside the artifact it describes.

.PARAMETER ToleranceMinutes
    How far the encoded build time may sit from the artifact's write time. Default 90: a large
    release build plus a slow disk, and still two orders of magnitude below the days-old drift the
    gate is looking for.

.PARAMETER Quiet
    Print only the verdict line.

.NOTES
    Run from anywhere; paths resolve relative to the repository root.

    Exit codes:
      0 - the artifact's version matches its own build
      1 - stale: version drift beyond tolerance, or the checked-in constant was shipped
      2 - cannot verify: metadata missing or unreadable, versionName unparsable, module unknown
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Path,
    [ValidateRange(1, 10080)] [int] $ToleranceMinutes = 90,
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $repoRoot 'scripts/utils/build-version-stamp.ps1')

function Write-Verdict {
    param([string] $Text, [string] $Color = 'Green')
    Write-Host $Text -ForegroundColor $Color
}

if (-not (Test-Path -LiteralPath $Path)) {
    Write-Host "assert-artifact-version-fresh: cannot verify - path not found: $Path" -ForegroundColor Yellow
    exit 2
}

$resolved = (Resolve-Path -LiteralPath $Path).Path
$item = Get-Item -LiteralPath $resolved
$searchDir = if ($item.PSIsContainer) { $resolved } else { Split-Path -Parent $resolved }
$metadataPath = Join-Path $searchDir 'output-metadata.json'

if (-not (Test-Path -LiteralPath $metadataPath)) {
    Write-Host "assert-artifact-version-fresh: cannot verify - no output-metadata.json beside the artifact." -ForegroundColor Yellow
    Write-Host "  expected: $metadataPath"
    exit 2
}

try {
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
}
catch {
    Write-Host "assert-artifact-version-fresh: cannot verify - output-metadata.json is unreadable." -ForegroundColor Yellow
    Write-Host "  file: $metadataPath"
    Write-Host "  error: $($_.Exception.Message)"
    exit 2
}

$element = @($metadata.elements)[0]
if (-not $element) {
    Write-Host "assert-artifact-version-fresh: cannot verify - output-metadata.json declares no elements." -ForegroundColor Yellow
    Write-Host "  file: $metadataPath"
    exit 2
}

$versionName = [string]$element.versionName
$versionCode = [int]$element.versionCode
$artifactName = [string]$element.outputFile
$artifactPath = Join-Path $searchDir $artifactName
$writeTime = if (Test-Path -LiteralPath $artifactPath) {
    (Get-Item -LiteralPath $artifactPath).LastWriteTime
}
else {
    (Get-Item -LiteralPath $metadataPath).LastWriteTime
}

# The module decides which checked-in constant is the stale one to compare against. AGP writes the
# metadata under <module>/build/outputs/..., so the module is the first segment of the path
# relative to the repo root.
$relative = $searchDir.Substring($repoRoot.Length).TrimStart('\', '/')
$module = ($relative -split '[\\/]')[0]
$moduleGradle = Join-Path $repoRoot (Join-Path $module 'build.gradle.kts')
if (-not (Test-Path -LiteralPath $moduleGradle)) {
    Write-Host "assert-artifact-version-fresh: cannot verify - cannot tell which module produced this artifact." -ForegroundColor Yellow
    Write-Host "  artifact dir: $searchDir"
    Write-Host "  expected a build file at: $moduleGradle"
    exit 2
}

$defaultCode = $null
$match = [regex]::Match((Get-Content -LiteralPath $moduleGradle -Raw), '(?m)^\s*val\s+defaultAppVersionCode\s*=\s*(?<value>\d+)\s*$')
if ($match.Success) { $defaultCode = [int]$match.Groups['value'].Value }

$encoded = ConvertFrom-BuildVersionName $versionName
if ($null -eq $encoded) {
    Write-Host "assert-artifact-version-fresh: cannot verify - versionName does not encode a build time." -ForegroundColor Yellow
    Write-Host "  versionName: '$versionName'   expected shape: Y.YM.MDDH.Hmm with an optional variant suffix"
    exit 2
}

$driftMinutes = [math]::Abs(($writeTime - $encoded).TotalMinutes)
$failures = New-Object System.Collections.Generic.List[string]

if ($driftMinutes -gt $ToleranceMinutes) {
    $failures.Add(("version drift: versionName '{0}' encodes {1:yyyy-MM-dd HH:mm} but the artifact was written {2:yyyy-MM-dd HH:mm} - {3:N0} min apart, tolerance {4} min" -f `
        $versionName, $encoded, $writeTime, $driftMinutes, $ToleranceMinutes))
}

if ($null -ne $defaultCode -and $versionCode -eq $defaultCode) {
    $failures.Add(("checked-in constant shipped: versionCode {0} equals defaultAppVersionCode in {1}/build.gradle.kts - the packaging path passed no -Pfms.versionCode" -f `
        $versionCode, $module))
}

if ($failures.Count -gt 0) {
    Write-Verdict "assert-artifact-version-fresh: FAIL ($module)" 'Red'
    foreach ($f in $failures) { Write-Host "  $f" }
    Write-Host "  artifact: $artifactPath"
    exit 1
}

if (-not $Quiet) {
    Write-Host ("  versionName {0} -> {1:yyyy-MM-dd HH:mm}; artifact written {2:yyyy-MM-dd HH:mm}; drift {3:N0} min" -f `
        $versionName, $encoded, $writeTime, $driftMinutes)
}
Write-Verdict "assert-artifact-version-fresh: PASS ($module, versionCode $versionCode)"
exit 0
