#requires -Version 7.0
<#
.SYNOPSIS
    Refuse a foreground-service type in the standard release that the previous release did not
    ship and the registry does not allow for standard (S4413).

.DESCRIPTION
    Release 46 was refused at the Play commit (HTTP 403) because the standard bundle carried the
    camera foreground-service type without a Play Console declaration, and the discovery came
    after the sweep and the build - a second AAB was the price. This check runs at the release
    campaign's assessment step instead, before the device half.

    The subject is the MERGED standard-release manifest: every android:foregroundServiceType token
    of a service the merge kept (a tools:node="remove" service does not count). The baseline is
    the previous release's type set, read from the newest fgs-types.json that
    retain-deobfuscation.ps1 writes beside each retained release; with no snapshot yet, the
    baseline is the registry's foregroundServiceType rows whose flavors include standard.

    A type is a finding when it is not in the baseline. Against a snapshot a registry row does not
    excuse it: the standard store build never gains a foreground-service type, and a capability that
    needs one stays in noLegal (release 47 was refused with HTTP 403 over a registered dataSync).

    Without -Build the script never runs gradle and judges whatever merged manifest is on disk.

.PARAMETER Manifest
    The merged manifest to judge. Default: the newest AndroidManifest.xml under
    app_v2/build/intermediates/merged_manifests/standardRelease/ of this tree.

.PARAMETER Snapshot
    A previous release's fgs-types.json. Default: the newest one under -ArchiveRoot.

.PARAMETER ArchiveRoot
    The deobfuscation archive root that holds <versionCode>/fgs-types.json. Default: the resolved
    Deobfuscation artifact sink - the root retain-deobfuscation.ps1 writes to.

.PARAMETER RegistryPath
    The manifest risk registry. Default: docs/manifest-risk-registry.jsonl.

.PARAMETER Build
    Run :app_v2:processStandardReleaseManifest first, under the Build.Phone domain lock, so the
    judged manifest is the current tree's.

.PARAMETER Help
    Print this help and exit 0.

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-fgs-types-vs-previous.ps1 -Build

.EXAMPLE
    pwsh -NoProfile -File scripts/release/assert-fgs-types-vs-previous.ps1 -Manifest temp/S4413/AndroidManifest.xml -ArchiveRoot temp/S4413/archive

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - pass: every foreground-service type is in the baseline; the type set is printed.
      1 - fail: a type is new in the standard store build - strip it from standard before the
          campaign continues.
      2 - could not verify: the merged manifest is absent or not well-formed XML, the snapshot or
          the registry cannot be read, or the -Build gradle task failed.
      4 - -Build only: the Build.Phone domain is queued (Enter-BuildLockOrExit exits on its own).
#>
[CmdletBinding()]
param(
    [string] $Manifest,
    [string] $Snapshot,
    [string] $ArchiveRoot,
    [string] $RegistryPath,
    [switch] $Build,
    [switch] $Help
)

if ($Help) {
    Get-Help -Full $MyInvocation.MyCommand.Path
    exit 0
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$mergedRoot = Join-Path $projectRoot 'app_v2/build/intermediates/merged_manifests/standardRelease'

if ($Build) {
    $wrapperName = if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' }
    $gradlew = Join-Path $projectRoot $wrapperName
    if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) {
        Write-Host "assert-fgs-types-vs-previous: COULD NOT VERIFY ($wrapperName not found at $gradlew)" -ForegroundColor Red
        exit 2
    }
    . (Join-Path $PSScriptRoot '../utils/agent-lock.ps1')
    Enter-BuildLockOrExit -Reason 'assert-fgs-types-vs-previous.ps1' -Domain Build.Phone
    $gradleExit = 0
    Push-Location $projectRoot
    try {
        Write-Host 'Running: gradlew :app_v2:processStandardReleaseManifest' -ForegroundColor Yellow
        & $gradlew ':app_v2:processStandardReleaseManifest' '-Pchaquopy.enabled=false' --configuration-cache
        $gradleExit = $LASTEXITCODE
    }
    finally {
        Pop-Location
        Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
    }
    if ($gradleExit -ne 0) {
        Write-Host "assert-fgs-types-vs-previous: COULD NOT VERIFY (processStandardReleaseManifest failed, exit $gradleExit)" -ForegroundColor Red
        exit 2
    }
}

# --- The merged manifest ---------------------------------------------------------------------
if (-not $Manifest -and (Test-Path -LiteralPath $mergedRoot)) {
    $found = Get-ChildItem -LiteralPath $mergedRoot -Recurse -File -Filter 'AndroidManifest.xml' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($found) { $Manifest = $found.FullName }
}
if (-not $Manifest -or -not (Test-Path -LiteralPath $Manifest -PathType Leaf)) {
    $shown = if ($Manifest) { $Manifest } else { $mergedRoot }
    Write-Host "assert-fgs-types-vs-previous: COULD NOT VERIFY (merged standard-release manifest not found: $shown; pass -Build)" -ForegroundColor Red
    exit 2
}

$androidNs = 'http://schemas.android.com/apk/res/android'
$toolsNs = 'http://schemas.android.com/tools'
$doc = [System.Xml.XmlDocument]::new()
$parsed = $true
try { $doc.Load((Resolve-Path -LiteralPath $Manifest).Path) } catch { $parsed = $false }
if (-not $parsed) {
    Write-Host "assert-fgs-types-vs-previous: COULD NOT VERIFY ($Manifest is not well-formed XML)" -ForegroundColor Red
    exit 2
}
$types = [System.Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
foreach ($service in $doc.SelectNodes('//service')) {
    if ($service.GetAttribute('node', $toolsNs) -eq 'remove') { continue }
    foreach ($raw in ($service.GetAttribute('foregroundServiceType', $androidNs) -split '\|')) {
        $type = $raw.Trim()
        if ($type) { [void]$types.Add($type) }
    }
}

# --- The registry: types allowed for standard -----------------------------------------------
$registryFile = if ($RegistryPath) { $RegistryPath } else { Join-Path $projectRoot 'docs/manifest-risk-registry.jsonl' }
if (-not (Test-Path -LiteralPath $registryFile -PathType Leaf)) {
    Write-Host "assert-fgs-types-vs-previous: COULD NOT VERIFY (registry not found: $registryFile)" -ForegroundColor Red
    exit 2
}
$registered = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$badLine = 0
$lineNo = 0
foreach ($rawLine in [System.IO.File]::ReadAllLines($registryFile)) {
    $lineNo++
    if (-not $rawLine.Trim()) { continue }
    $row = $null
    try { $row = $rawLine | ConvertFrom-Json } catch { $badLine = $lineNo; break }
    if ($row.PSObject.Properties.Name -notcontains 'kind' -or [string]$row.kind -ne 'foregroundServiceType') { continue }
    $flavors = @(if ($row.PSObject.Properties.Name -contains 'flavors') { $row.flavors } else { @() })
    if ($flavors -contains 'standard') { [void]$registered.Add([string]$row.key) }
}
if ($badLine -gt 0) {
    Write-Host "assert-fgs-types-vs-previous: COULD NOT VERIFY (line $badLine of $registryFile is not JSON)" -ForegroundColor Red
    exit 2
}

# --- The baseline: the previous release's snapshot, else the registry -------------------------
if (-not $Snapshot) {
    if (-not $ArchiveRoot) {
        . (Join-Path $PSScriptRoot '../utils/project-paths.ps1')
        $ArchiveRoot = Get-ArtifactSink -Kind Deobfuscation -Quiet
    }
    # retain-deobfuscation.ps1 writes the judged build's own snapshot before the Play commit, so a
    # snapshot at or above the manifest's versionCode is this build (or a later one), never "the previous
    # release": taking it as the baseline turned release 47's refused dataSync into its own excuse.
    $judgedCode = [long]::MaxValue
    $codeAttr = $doc.DocumentElement.GetAttribute('versionCode', 'http://schemas.android.com/apk/res/android')
    if ($codeAttr -match '^\d+$') { $judgedCode = [long]$codeAttr }
    if ($ArchiveRoot -and (Test-Path -LiteralPath $ArchiveRoot -PathType Container)) {
        $newest = Get-ChildItem -LiteralPath $ArchiveRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '^\d+$' -and [long]$_.Name -lt $judgedCode -and (Test-Path -LiteralPath (Join-Path $_.FullName 'fgs-types.json') -PathType Leaf) } |
            Sort-Object { [long]$_.Name } -Descending | Select-Object -First 1
        if ($newest) { $Snapshot = Join-Path $newest.FullName 'fgs-types.json' }
    }
}
$baseline = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$baselineName = ''
if ($Snapshot) {
    $snap = $null
    if (Test-Path -LiteralPath $Snapshot -PathType Leaf) {
        try { $snap = [System.IO.File]::ReadAllText($Snapshot) | ConvertFrom-Json } catch { $snap = $null }
    }
    if (-not $snap -or $snap.PSObject.Properties.Name -notcontains 'types') {
        Write-Host "assert-fgs-types-vs-previous: COULD NOT VERIFY (snapshot unreadable or has no types: $Snapshot)" -ForegroundColor Red
        exit 2
    }
    foreach ($t in @($snap.types)) { if ($t) { [void]$baseline.Add([string]$t) } }
    $snapCode = if ($snap.PSObject.Properties.Name -contains 'versionCode') { $snap.versionCode } else { '?' }
    $baselineName = "snapshot of versionCode $snapCode ($Snapshot)"
}
else {
    foreach ($t in $registered) { [void]$baseline.Add($t) }
    $baselineName = 'registry rows for standard (no fgs-types.json snapshot yet)'
}

# --- Verdict --------------------------------------------------------------------------------
# A registry row is no excuse against a snapshot: release 47 carried dataSync, registered for standard
# with a Console declaration on record, and Play still refused the commit with HTTP 403.
$newTypes = [System.Collections.Generic.List[string]]::new()
foreach ($type in $types) {
    if (-not $baseline.Contains($type)) { $newTypes.Add($type) }
}

$typeList = if ($types.Count -gt 0) { @($types) -join ', ' } else { '(none)' }
Write-Host "fgs-types: manifest $Manifest"
Write-Host "fgs-types: baseline $baselineName"
Write-Host "fgs-types: standard release types: $typeList"
if ($newTypes.Count -gt 0) {
    foreach ($type in $newTypes) {
        Write-Host "  foregroundServiceType '$type' is new in the standard store build: strip it from standard (its capability stays in noLegal) before the campaign continues" -ForegroundColor Red
    }
    Write-Host "assert-fgs-types-vs-previous: FAIL ($($newTypes.Count) new type(s): $($newTypes -join ', '))" -ForegroundColor Red
    exit 1
}
Write-Host "assert-fgs-types-vs-previous: PASS ($($types.Count) type(s): $typeList)" -ForegroundColor Green
exit 0
