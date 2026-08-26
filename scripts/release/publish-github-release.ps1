#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Publish the full FastMediaSorter release spectrum to GitHub Releases so
    that GitHub Store (OpenHub-Store/GitHub-Store) and IzzyOnDroid can index it.

.DESCRIPTION
    Spec: S0214 - github-store-publication; extended to the full spectrum by S0394.

    Operator flow (invoke from the release worktree, FastMediaSorter_release,
    checked out to main):
      1. .\scripts\release\build-release-spectrum.ps1   # build all release flavors + wear at one version
      2. .\scripts\release\publish-github-release.ps1   # publish all assets under one tag

    The script:
      - Reads versionName from app_v2/build.gradle.kts
      - Resolves each spectrum release APK (standard, vr, lite, photos, legacy,
        noLegal, wear) through scripts/utils/find-build-artifact.ps1, which refuses
        to guess rather than picking the newest file (S1972)
      - Stages each in a temp dir as FastMediaSorter-<flavor>-<version>.apk
        (versioned names - IzzyOnDroid globs the pattern)
      - Verifies signing fingerprint matches the single pinned value (shared key)
      - Extracts release notes for the version from docs/WHATS_NEW.md
      - Creates a GitHub Release tag v{version} from main with the notes
      - Uploads every staged APK as a release asset and verifies all are present

    Author hooks: -DryRun for safe parsing / discovery, -Force to allow
    republishing the same version (skips "tag already exists" guard).

.PARAMETER DryRun
    Resolve everything, verify guards, print the publish plan, but skip
    every credential / git / API mutation.

.PARAMETER Force
    Allow publishing when a release with the same tag already exists.
    Default: false.

.PARAMETER Owner
    GitHub repo owner. Default: SerZhyAle.

.PARAMETER Repo
    GitHub repo name. Default: FastMediaSorter_mob_v2.

.PARAMETER Flavors
    Subset of the spectrum to publish. Accepts any of:
      standard, lite, photos, legacy, vr, wear, noLegal
    plus the alias 'all' (== 'full' == 'spectrum'). Case-insensitive,
    order-independent, de-duplicated. Omitted / empty => the full spectrum
    (backward-compatible default). Must match the flavors actually built by
    build-release-spectrum.ps1; missing APKs abort the publish. The
    /skill-release flow passes 'standard' by default.

.EXAMPLE
    pwsh -File scripts/release/publish-github-release.ps1 -DryRun

.EXAMPLE
    pwsh -File scripts/release/publish-github-release.ps1 -Flavors standard
#>

[CmdletBinding()]
param(
    [switch]   $DryRun,
    [switch]   $Force,
    [string]   $Owner = "SerZhyAle",
    [string]   $Repo  = "FastMediaSorter_mob_v2",
    [string[]] $Flavors
)

$ErrorActionPreference = "Stop"

# Ensure the GitHub CLI is resolvable even when PATH (e.g. under -NoProfile)
# omits the standard install directory. Without this, gh detection below
# falls through to the unwired REST branch and publication aborts.
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    foreach ($ghDir in @(
        (Join-Path ${env:ProgramFiles} "GitHub CLI"),
        (Join-Path ${env:ProgramW6432} "GitHub CLI"),
        (Join-Path ${env:LOCALAPPDATA} "Microsoft\WinGet\Links")
    )) {
        if ($ghDir -and (Test-Path -LiteralPath (Join-Path $ghDir "gh.exe"))) {
            $env:PATH = "$ghDir;$env:PATH"
            break
        }
    }
}

$repoRoot      = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$buildGradle   = Join-Path $repoRoot "app_v2/build.gradle.kts"
$whatsNewPath  = Join-Path $repoRoot "docs/WHATS_NEW.md"

# Full release spectrum (S0394): flavor -> release APK output dir (relative to repoRoot).
# All app_v2 flavors share the one release key; wear lives in its own module tree and
# is signed with the same key (S0394 Phase 01). noLegal is published as an asset but
# the website links it only from nolegal*.html, never the main page.
$spectrum = [ordered]@{
    standard = "app_v2/build/outputs/apk/standard/release"
    vr       = "app_v2/build/outputs/apk/vr/release"
    lite     = "app_v2/build/outputs/apk/lite/release"
    photos   = "app_v2/build/outputs/apk/photos/release"
    legacy   = "app_v2/build/outputs/apk/legacy/release"
    noLegal  = "app_v2/build/outputs/apk/noLegal/release"
    wear     = "wear/build/outputs/apk/release"
}

# ----------------------------------------------------------------------
# Narrow the spectrum to the requested flavors (default: publish all).
# Must mirror the set built by build-release-spectrum.ps1 - a requested
# flavor without a built APK aborts in the staging loop below.
# ----------------------------------------------------------------------
if ($Flavors -and $Flavors.Count -gt 0) {
    $picked = @()
    foreach ($f in $Flavors) {
        $t = "$f".Trim()
        if ($t -eq '') { continue }
        if ($t -in @('all', 'full', 'spectrum')) { $picked = @($spectrum.Keys); break }
        $key = @($spectrum.Keys) | Where-Object { $_ -ieq $t }
        if (-not $key) {
            throw "Unknown flavor '$t'. Valid: $(@($spectrum.Keys) -join ', '), or 'all'."
        }
        $picked += $key
    }
    $filtered = [ordered]@{}
    foreach ($k in $spectrum.Keys) {
        if ($k -in $picked) { $filtered[$k] = $spectrum[$k] }
    }
    if ($filtered.Count -eq 0) { throw "No valid flavors selected." }
    $spectrum = $filtered
}
Write-Host "Publishing flavors: $(@($spectrum.Keys) -join ', ')" -ForegroundColor Green

# ----------------------------------------------------------------------
# Branch guard (publishing is allowed only from main)
# ----------------------------------------------------------------------
Push-Location $repoRoot
try {
    $currentBranch = (& git branch --show-current).Trim()
} finally {
    Pop-Location
}
Write-Host "Current branch: $currentBranch" -ForegroundColor DarkGray
if (-not $DryRun -and $currentBranch -ne "main") {
    throw "Refusing to publish from '$currentBranch'. Release publication is allowed only from 'main' (see CLAUDE.md Git Branching Model)."
}
if ($DryRun -and $currentBranch -ne "main") {
    Write-Host "Branch is not 'main' - would abort outside of -DryRun." -ForegroundColor Yellow
}

# ----------------------------------------------------------------------
# Extract versionName from app_v2/build.gradle.kts
# ----------------------------------------------------------------------
if (-not (Test-Path -LiteralPath $buildGradle)) {
    throw "build.gradle.kts not found at $buildGradle"
}
$buildContent = Get-Content -LiteralPath $buildGradle -Raw
# (?i): version lives in `defaultAppVersionName` (capital V); match case-insensitively like the writer.
$versionMatch = [regex]::Match($buildContent, '(?i)versionName\s*=\s*"([^"]+)"')
if (-not $versionMatch.Success) {
    throw "Could not parse versionName from $buildGradle"
}
$version = $versionMatch.Groups[1].Value
Write-Host "Version: $version" -ForegroundColor Green

# ----------------------------------------------------------------------
# APK discovery - delegated to the shared resolver (S1972). The two throws
# below stay here rather than moving into it, because the resolver answers
# "nothing was built" with $null for callers that have their own guard, and
# this one's guard is a message naming the builder to run.
# ----------------------------------------------------------------------
. "$PSScriptRoot/../utils/find-build-artifact.ps1"

# ----------------------------------------------------------------------
# Discover every spectrum APK, apply the staleness guard, and stage each
# with a deterministic versioned name:
#   FastMediaSorter-<flavor>-<version>.apk
# Versioned names are required - IzzyOnDroid (S0215) globs the pattern.
# ----------------------------------------------------------------------
$gradleMtime = (Get-Item -LiteralPath $buildGradle).LastWriteTimeUtc
$staleCutoff = $gradleMtime.AddHours(-24)

$stagingDir = Join-Path $repoRoot "temp/release/$version"
if (Test-Path -LiteralPath $stagingDir) {
    Remove-Item -LiteralPath $stagingDir -Recurse -Force
}
$null = New-Item -ItemType Directory -Path $stagingDir -Force

$stagedAssets = @()
foreach ($flavor in $spectrum.Keys) {
    $dir = Join-Path $repoRoot $spectrum[$flavor]
    if (-not (Test-Path -LiteralPath $dir)) {
        throw "$flavor APK directory missing: $dir. Run the appropriate builder first (a.ps1 r / a.ps1 vr)."
    }
    # No -Abi: the release path stays universal until strategic §6.1 is answered, and asking for
    # nothing is what makes a split release directory throw here rather than publish one slice under
    # the architecture-free asset name IzzyOnDroid globs (S0215).
    $apk = Find-BuildArtifact -Dir $dir
    if (-not $apk) {
        throw "$flavor APK not found in $dir. Run the appropriate builder first."
    }

    # Staleness guard: every APK must be newer than build.gradle.kts minus 24h
    # (i.e. built within ~24h of the version bump that put the current
    # versionName in place).
    if ($apk.LastWriteTimeUtc -lt $staleCutoff) {
        $hours = [int]($gradleMtime - $apk.LastWriteTimeUtc).TotalHours
        throw "$($apk.Name) ($flavor) is older than the current build.gradle.kts version by ${hours}h. Rebuild the spectrum (scripts/release/build-release-spectrum.ps1) before publishing."
    }

    $staged = Join-Path $stagingDir ("FastMediaSorter-$flavor-$version.apk")
    Copy-Item -LiteralPath $apk.FullName -Destination $staged
    $stagedAssets += $staged
    Write-Host ("  {0,-9} {1} -> {2}" -f $flavor, $apk.Name, [System.IO.Path]::GetFileName($staged)) -ForegroundColor DarkGray
}

Write-Host "Discovery + staging complete: $($stagedAssets.Count) assets staged for v$version." -ForegroundColor Cyan

# ----------------------------------------------------------------------
# Phase 04 - Assert signing fingerprint matches the pinned value.
# Runs after staging and before release-create, dry-run or not.
# ----------------------------------------------------------------------
function Resolve-Apksigner {
    $cmd = Get-Command apksigner -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    # Fall back: scan local Android SDK build-tools for apksigner.bat (Windows).
    $sdkRoot = $env:ANDROID_HOME
    if (-not $sdkRoot) { $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
    if (-not (Test-Path -LiteralPath $sdkRoot)) { return $null }
    $btDir = Join-Path $sdkRoot "build-tools"
    if (-not (Test-Path -LiteralPath $btDir)) { return $null }
    $latest = Get-ChildItem -LiteralPath $btDir -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if (-not $latest) { return $null }
    $exe = Join-Path $latest.FullName "apksigner.bat"
    if (Test-Path -LiteralPath $exe) { return $exe }
    return $null
}

function Assert-ExpectedFingerprint {
    param(
        [Parameter(Mandatory)] [string[]] $ApkPaths,
        [Parameter(Mandatory)] [string]   $PinFile
    )
    if (-not (Test-Path -LiteralPath $PinFile)) {
        throw "Pin file missing: $PinFile (run Phase 04 Step 04.1 to populate)"
    }

    $expected = (Get-Content -LiteralPath $PinFile |
        Where-Object { $_ -and ($_ -notmatch '^\s*#') } |
        Select-Object -First 1).Trim().ToUpperInvariant()
    if (-not $expected) { throw "Pin file is empty: $PinFile" }

    $apksigner = Resolve-Apksigner
    if (-not $apksigner) {
        throw "apksigner not found on PATH and no Android SDK build-tools detected. Install Android SDK or set `$env:ANDROID_HOME."
    }

    foreach ($apk in $ApkPaths) {
        $output = & $apksigner verify --print-certs $apk 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "apksigner verify failed for $apk : $output"
        }
        $sha256Line = $output | Where-Object { $_ -match 'SHA-256 digest:\s+([0-9A-Fa-f]+)' } | Select-Object -First 1
        if (-not $sha256Line) {
            throw "Could not parse SHA-256 from apksigner output for $apk"
        }
        $null = ($sha256Line -match 'SHA-256 digest:\s+([0-9A-Fa-f]+)')
        $hexNoColons = $Matches[1].ToUpperInvariant()
        # Insert colons every two chars to match pin format.
        $actual = (($hexNoColons.ToCharArray() | ForEach-Object -Begin {$i=0; $buf=@()} -Process {
            $buf += $_
            if ($buf.Count -eq 2) {
                ,(($buf -join ''))
                $buf = @()
            }
        } -End {}) -join ':').ToUpperInvariant()

        if ($actual -ne $expected) {
            throw @"
Fingerprint mismatch for $apk :
  expected: $expected
  actual  : $actual
See docs/DEV_OPS.md "Release Signing Fingerprint (GitHub Store)" for the rotation procedure.
"@
        }
        Write-Host "Fingerprint OK: $([System.IO.Path]::GetFileName($apk))" -ForegroundColor Green
    }
}

$pinFile = Join-Path $PSScriptRoot "expected-signing-fingerprint.txt"
# All spectrum flavors + wear share the one release key (S0394 Phase 01), so a single
# pinned fingerprint covers every staged asset.
Assert-ExpectedFingerprint -ApkPaths $stagedAssets -PinFile $pinFile

# ----------------------------------------------------------------------
# Extract release notes for $version via the Phase 03 helper.
# ----------------------------------------------------------------------
$extractScript = Join-Path $PSScriptRoot "extract-release-notes.ps1"
if (-not (Test-Path -LiteralPath $extractScript)) {
    throw "Missing helper: $extractScript"
}

$notesOutput = & $extractScript -Version $version 2>&1
$notesExit   = $LASTEXITCODE
$notesAvailable = ($notesExit -eq 0)
if (-not $notesAvailable -and -not $DryRun) {
    throw "WHATS_NEW.md is missing a section for $version (extract-release-notes exit $notesExit). Add the section and rerun."
}
if (-not $notesAvailable -and $DryRun) {
    Write-Host "Dry-run: WHATS_NEW.md has no section for $version yet - would abort outside of -DryRun. Plan output continues with a placeholder body." -ForegroundColor Yellow
    $releaseNotes = "<release notes for $version go here>"
} else {
    $releaseNotes = $notesOutput -join "`n"
}

$tag      = "v$version"
$title    = "FastMediaSorter $version"
$ghOnPath = Get-Command gh -ErrorAction SilentlyContinue

# ----------------------------------------------------------------------
# Compose the create-release command (gh-first, REST fallback).
# ----------------------------------------------------------------------
if ($ghOnPath) {
    # Stage notes into a temp file so multi-line content survives the CLI boundary.
    $notesFile = Join-Path $stagingDir "release-notes.md"
    Set-Content -LiteralPath $notesFile -Value $releaseNotes -NoNewline

    $createCmd = @(
        "gh", "release", "create", $tag,
        "--repo", "$Owner/$Repo",
        "--target", "main",
        "--title", $title,
        "--notes-file", $notesFile
        # No --prerelease (publishing stable only per DECISIONS.md Pre-release).
    )
    Write-Host "Plan: $($createCmd -join ' ')" -ForegroundColor Cyan
} else {
    # REST fallback (POST /repos/{owner}/{repo}/releases).
    Write-Host "Plan: POST /repos/$Owner/$Repo/releases with tag $tag (REST fallback, gh not on PATH)" -ForegroundColor Cyan
}

if ($DryRun) {
    Write-Host "Dry-run requested - no release create, no asset upload." -ForegroundColor Yellow
    exit 0
}

# ----------------------------------------------------------------------
# Real release create.
# ----------------------------------------------------------------------
if ($ghOnPath) {
    & gh release create $tag `
        --repo "$Owner/$Repo" `
        --target "main" `
        --title $title `
        --notes-file $notesFile
    if ($LASTEXITCODE -ne 0) {
        throw "gh release create failed with exit $LASTEXITCODE"
    }
} else {
    # REST: requires credential resolver; ported from apply-github-store-metadata
    throw "REST release-create fallback intentionally left for Step 03.5 (asset upload step wires the credential resolver shared with Phase 02). Install gh CLI to publish without REST glue."
}

Write-Host "Release $tag created." -ForegroundColor Green

# ----------------------------------------------------------------------
# Step 03.5 - Asset upload + post-publish verification
# ----------------------------------------------------------------------
$expectedAssetNames = @($stagedAssets | ForEach-Object { [System.IO.Path]::GetFileName($_) })

if ($ghOnPath) {
    foreach ($asset in $stagedAssets) {
        Write-Host "Uploading $asset .." -ForegroundColor Cyan
        & gh release upload $tag $asset --repo "$Owner/$Repo" --clobber
        if ($LASTEXITCODE -ne 0) {
            throw "gh release upload failed for $asset (exit $LASTEXITCODE)"
        }
    }
} else {
    # REST fallback: POST /repos/{owner}/{repo}/releases/{release_id}/assets
    # with header Content-Type: application/vnd.android.package-archive.
    throw "REST asset-upload fallback intentionally not wired in this iteration. Install gh CLI to publish without REST glue."
}

# Verify the release lists both deterministic asset names.
$verify = & gh api "repos/$Owner/$Repo/releases/tags/$tag" --jq '.assets[].name'
if ($LASTEXITCODE -ne 0) {
    throw "Failed to read back release $tag for verification"
}
$actualAssetNames = $verify -split "`n" | Where-Object { $_ -and $_.Trim() -ne '' }

$missing = @()
foreach ($expected in $expectedAssetNames) {
    if (-not ($actualAssetNames -contains $expected)) {
        $missing += $expected
    }
}

if ($missing.Count -gt 0) {
    throw "Release $tag is missing expected assets: $($missing -join ', '). Found: $($actualAssetNames -join ', ')"
}

Write-Host "OK - release $tag has both expected assets: $($actualAssetNames -join ', ')" -ForegroundColor Green
