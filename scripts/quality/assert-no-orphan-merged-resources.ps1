#requires -Version 7.0
<#
.SYNOPSIS
    S1825 - fails when a merged resource artifact outlives the source file it was built from.

.DESCRIPTION
    Deleting a file under <module>/src/<set>/res does not remove its compiled artifact from
    <module>/build/intermediates/merged_res/<variant>/merge<Variant>Resources. The merger updates what
    changed and adds what appeared, but leaves behind what vanished, and packaging still picks the
    orphan up. It then competes as a real resource variant: on a device matching its qualifier, Android
    prefers it over the file that is actually in the tree.

    Found 2026-08-20 by the crash it finally caused. layout-w600dp/activity_streams.xml had been deleted
    on this branch, its artifact stayed, and every variant - standardDebug, noLegalDebug, vrDebug,
    legacyDebug, liteDebug, photosDebug and standardRelease - kept packaging a copy from 9 to 11 days
    earlier. While the stale copy was merely old this was invisible; the moment the live layout gained a
    view the old one lacks, every wide-screen device died on open with
    "NullPointerException: Missing required view with ID" out of the generated ViewBinding.

    An artifact is named <qualifier-folder>_<file>.flat. The folder carries an AAPT-added -vNN suffix
    that no source folder has, so it is stripped before the lookup; the split is on the FIRST underscore
    because qualifier folders never contain one while file names routinely do.

    Deliberately not reported, because they have no source by construction:
      values*/*.arsc              per-locale aggregates the merger synthesises
      mipmap-anydpi/ic_launcher*  adaptive-icon wrappers AGP generates

    Reports nothing when nothing has been built - an unbuilt tree cannot ship a stale artifact, and
    failing there would only teach people to skip the gate.

.PARAMETER Module
    Module directory relative to the repo root. Default app_v2.

.PARAMETER Fix
    Delete the orphaned artifacts and the incremental merge state, so the next build re-merges from
    source. Both go together: dropping an artifact while leaving the state that lists it is what turns
    one stale file into an inconsistent merge.

.PARAMETER Quiet
    Print the verdict line only, without naming each orphan.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-no-orphan-merged-resources.ps1

.EXAMPLE
    # Clean up before a release build.
    pwsh -NoProfile -File scripts/quality/assert-no-orphan-merged-resources.ps1 -Fix

.OUTPUTS
    Exit codes:
      0 - no orphaned artifact, or nothing has been built yet.
      1 - at least one artifact has no source; the output names every one.
      2 - cannot verify: the module directory does not exist.
#>
[CmdletBinding()]
param(
    [string]$Module = 'app_v2',
    [switch]$Fix,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$modulePath = Join-Path $repoRoot $Module

if (-not (Test-Path -LiteralPath $modulePath)) {
    Write-Host "assert-no-orphan-merged-resources: CANNOT VERIFY - no module at $modulePath" -ForegroundColor Yellow
    exit 2
}

$mergedRoot = Join-Path $modulePath 'build/intermediates/merged_res'
if (-not (Test-Path -LiteralPath $mergedRoot)) {
    if (-not $Quiet) { Write-Host "assert-no-orphan-merged-resources: PASS - nothing built in $Module." -ForegroundColor Green }
    exit 0
}

$resRoots = @(
    Get-ChildItem -LiteralPath (Join-Path $modulePath 'src') -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'res' } |
        Where-Object { Test-Path -LiteralPath $_ }
)

# A source path is looked up in every source set, because a flavor may own the file the main set lacks.
function Test-HasSource {
    param([string]$Folder, [string]$File)

    foreach ($root in $resRoots) {
        if (Test-Path -LiteralPath (Join-Path $root (Join-Path $Folder $File))) { return $true }
    }
    return $false
}

function Test-IsGenerated {
    param([string]$Folder, [string]$File)

    if ($Folder -like 'values*' -and $File -like '*.arsc') { return $true }
    if ($Folder -eq 'mipmap-anydpi' -and $File -like 'ic_launcher*') { return $true }
    return $false
}

$orphans = [System.Collections.Generic.List[object]]::new()

foreach ($variantDir in (Get-ChildItem -LiteralPath $mergedRoot -Directory)) {
    foreach ($flat in (Get-ChildItem -LiteralPath $variantDir.FullName -Recurse -Filter '*.flat' -ErrorAction SilentlyContinue)) {
        $name = $flat.Name -replace '\.flat$', ''
        $split = $name.IndexOf('_')
        if ($split -lt 1) { continue }

        $folder = ($name.Substring(0, $split)) -replace '-v\d+$', ''
        $file = $name.Substring($split + 1)

        if (Test-IsGenerated -Folder $folder -File $file) { continue }
        if (Test-HasSource -Folder $folder -File $file) { continue }

        $orphans.Add([pscustomobject]@{
            Variant = $variantDir.Name
            Source  = "$folder/$file"
            Age     = $flat.LastWriteTime.ToString('yyyy-MM-dd')
            Path    = $flat.FullName
        })
    }
}

if ($orphans.Count -eq 0) {
    if (-not $Quiet) {
        Write-Host "assert-no-orphan-merged-resources: PASS - every merged artifact in $Module still has a source." -ForegroundColor Green
    }
    exit 0
}

$distinct = @($orphans | Select-Object -ExpandProperty Source -Unique)
Write-Host "assert-no-orphan-merged-resources: FAIL - $($orphans.Count) artifact(s) in $Module have no source ($($distinct.Count) distinct)." -ForegroundColor Red

if (-not $Quiet) {
    foreach ($group in ($orphans | Group-Object Source | Sort-Object Name)) {
        $variants = ($group.Group | Select-Object -ExpandProperty Variant | Sort-Object) -join ', '
        $oldest = ($group.Group | Sort-Object Age | Select-Object -First 1).Age
        Write-Host ("  {0}  [{1}]  since {2}" -f $group.Name, $variants, $oldest)
    }
    Write-Host ''
    Write-Host 'A packaged artifact with no source still wins on a device whose configuration matches it.'
    Write-Host 'Re-run with -Fix to drop the artifacts and the incremental merge state, then rebuild.'
}

if ($Fix) {
    foreach ($orphan in $orphans) {
        Remove-Item -LiteralPath $orphan.Path -Force -ErrorAction SilentlyContinue
    }
    # The merge state lists the artifacts it wrote, so it goes with them - a state that still names a
    # deleted artifact is what makes the next incremental merge inconsistent rather than corrective.
    $incremental = Join-Path $modulePath 'build/intermediates/incremental'
    if (Test-Path -LiteralPath $incremental) {
        Get-ChildItem -LiteralPath $incremental -Directory -Filter 'merge*Resources' -ErrorAction SilentlyContinue |
            ForEach-Object { Remove-Item -LiteralPath $_.FullName -Recurse -Force -ErrorAction SilentlyContinue }
    }
    Write-Host "assert-no-orphan-merged-resources: removed $($orphans.Count) artifact(s) and the incremental merge state - rebuild to re-merge." -ForegroundColor Yellow
}

exit 1
