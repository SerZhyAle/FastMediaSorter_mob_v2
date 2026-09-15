#requires -Version 7.0
<#
.SYNOPSIS
    Contract suite for Get-ReleaseMetadataCandidate / Get-ReleaseArtifactVersion in
    scripts/utils/build-version-stamp.ps1 (S3029).

.DESCRIPTION
    The acceptance criterion behind these functions asks for a release run with a second flavor,
    which no repository session can perform. Each candidate location is proved separately instead,
    against a sandbox project root seeded with an AGP-shaped output-metadata.json - and that keeps
    proving it after the next AGP move, which a single release run would not.

    Cases:
      - the candidate list is ordered: bundle outputs, then the bundle_ide_model intermediates,
        then the APK outputs;
      - the version is recovered from each of the three locations on its own;
      - the earlier candidate wins when two locations disagree;
      - a tree with no metadata anywhere answers $null, not a wrong version.

.NOTES
    Exit codes:
      0 - every case passed.
      1 - at least one case failed.
      2 - the fixture could not be prepared.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $repoRoot 'scripts/utils/build-version-stamp.ps1')

$script:pass = 0
$script:fail = 0

function Assert-That {
    param([string] $Name, [bool] $Condition, [string] $Detail)
    if ($Condition) {
        $script:pass++
        Write-Host "  PASS  $Name" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "  FAIL  $Name" -ForegroundColor Red
        if ($Detail) { Write-Host "        $Detail" -ForegroundColor DarkGray }
    }
}

$run = Get-Date -Format 'yyyyMMdd-HHmmss'
$sandboxRoot = Join-Path $repoRoot "temp/S3029/release-version-tests/$run"
try { New-Item -ItemType Directory -Path $sandboxRoot -Force | Out-Null }
catch { Write-Host "release-artifact-version tests: fixture could not be prepared - $_" -ForegroundColor Red; exit 2 }

function New-Sandbox {
    param([string] $Name)
    $path = Join-Path $sandboxRoot $Name
    New-Item -ItemType Directory -Path $path -Force | Out-Null
    return $path
}

# The shape AGP writes: one element per ABI slice, all carrying the same version (S1972 6.2).
function Write-Metadata {
    param(
        [Parameter(Mandatory)][string] $Dir,
        [Parameter(Mandatory)][string] $VersionName,
        [Parameter(Mandatory)][int] $VersionCode
    )
    New-Item -ItemType Directory -Path $Dir -Force | Out-Null
    $json = @{
        version      = 3
        artifactType = @{ type = 'BUNDLE'; kind = 'Directory' }
        elements     = @(@{ type = 'SINGLE'; versionCode = $VersionCode; versionName = $VersionName; outputFile = 'app.aab' })
    } | ConvertTo-Json -Depth 6
    Set-Content -LiteralPath (Join-Path $Dir 'output-metadata.json') -Value $json -Encoding utf8
}

try {
    Write-Host 'Candidate list'
    $probe = New-Sandbox 'order'
    $candidates = @(Get-ReleaseMetadataCandidate -ProjectRoot $probe)
    Assert-That 'three candidates are offered' ($candidates.Count -eq 3) "count=$($candidates.Count)"
    Assert-That 'the bundle output directory is tried first' ($candidates[0] -match 'outputs.bundle.standardRelease$') $candidates[0]
    Assert-That 'the intermediates listing is tried second' ($candidates[1] -match 'bundle_ide_model') $candidates[1]
    Assert-That 'the APK output directory is tried last' ($candidates[2] -match 'outputs.apk.standard.release$') $candidates[2]

    Write-Host 'Each location answers on its own'
    $i = 0
    foreach ($label in @('bundle outputs', 'bundle_ide_model intermediates', 'apk outputs')) {
        $root = New-Sandbox ("only-$i")
        $dir = (Get-ReleaseMetadataCandidate -ProjectRoot $root)[$i]
        Write-Metadata -Dir $dir -VersionName '2.60.9121.346' -VersionCode 260912134
        $found = Get-ReleaseArtifactVersion -ProjectRoot $root
        Assert-That "the version is recovered from the $label alone" (
            $null -ne $found -and $found.VersionName -eq '2.60.9121.346' -and $found.VersionCode -eq 260912134
        ) "found=$($found | ConvertTo-Json -Compress)"
        $i++
    }

    Write-Host 'Order decides when two locations disagree'
    $both = New-Sandbox 'both'
    $dirs = Get-ReleaseMetadataCandidate -ProjectRoot $both
    Write-Metadata -Dir $dirs[0] -VersionName '2.60.9121.346' -VersionCode 260912134
    Write-Metadata -Dir $dirs[2] -VersionName '1.00.0000.000' -VersionCode 100000000
    $winner = Get-ReleaseArtifactVersion -ProjectRoot $both
    Assert-That 'the first candidate wins' ($winner.VersionCode -eq 260912134) "got=$($winner.VersionCode)"

    Write-Host 'Nothing anywhere is not a wrong answer'
    $empty = New-Sandbox 'empty'
    Assert-That 'an unbuilt tree answers null' ($null -eq (Get-ReleaseArtifactVersion -ProjectRoot $empty)) 'expected $null'
}
finally {
    Remove-Item -LiteralPath (Split-Path -Parent $sandboxRoot) -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host ("release-artifact-version tests: {0} passed, {1} failed" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { 'Green' } else { 'Red' })
if ($script:fail -gt 0) { exit 1 }
exit 0
