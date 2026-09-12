#requires -Version 7.0
<#
.SYNOPSIS
    Gate: every single image the Play listing publisher declares has a source, and a listing image
    that lives in more than one tree is one artwork (S2597).

.DESCRIPTION
    publish-play-listing.py declares two one-per-locale images in SINGLE_IMAGES - the feature
    graphic and the store icon - and reads them from play/listing/<locale>/images/. Measured
    2026-09-05: neither file existed in any locale. The publisher skips an image whose file is
    absent and exits 0, so "the banner was not uploaded" and "there is no banner to upload" were
    the same answer, and the live Play feature graphic survived only as a hand upload that no run
    reproduced and no check compared against anything.

    Nothing could have found that. The only observer was publication itself, which is owner-gated
    and rare, and its silence on a missing file is by design - a phone-only refresh must not wipe
    the tablet screenshots. This gate is the observer that runs on a shorter schedule, and it is
    release-scope by CLAUDE.md Rule 33: its subject is the listing as a whole, not one ticket's
    changed set.

    TWO FINDINGS, NAMED APART BECAUSE THEY CALL FOR DIFFERENT REPAIRS:

      1. MISSING   - a file named in SINGLE_IMAGES that the default-language folder does not carry.
                     Repair: produce it. The feature graphic comes from
                     scripts/release/compose-feature-graphic.py; the icon is the 512x512 store icon.
      2. DIVERGED  - one artwork whose copies in different publication trees no longer hash alike.
                     Repair: re-run the composer, which writes both copies of the feature graphic in
                     one pass, or re-copy the icon.

    THE DECLARED LIST IS READ FROM THE PUBLISHER, NEVER RESTATED HERE. A second copy of
    SINGLE_IMAGES is exactly the drift this gate exists to catch - the same failure S2340 recorded
    when the listing's locale set was declared twice and one copy sat at three languages while the
    other grew to thirteen. Adding an image type to the publisher therefore arms this gate for it
    automatically, and the gate fails until a source appears.

    WHY THE MIRROR SETS ARE SPELLED OUT AND THE DECLARED LIST IS NOT. Which trees publish an image
    is a decision of this repository, not a fact stated anywhere a script can read: the Play tree
    and fastlane/metadata/android/ are deliberately separate publication sources, and S2597 resolved
    that the split is about TEXT only - IzzyOnDroid needs an "Anti-Features" block that is dead
    weight on Play - so the two storefronts show one artwork. That resolution has to live somewhere,
    and here it is enforced rather than merely written down.

    The default language is en-US: the ten locales with no images/ folder inherit its graphics on
    Play (play/listing/README.md), so a missing file there is missing for the whole listing, and a
    per-locale override is a bonus rather than a requirement.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-play-listing-graphics.ps1

.NOTES
    Exit codes:
      0 - every declared single image has a source and every mirrored artwork agrees.
      1 - at least one MISSING or DIVERGED finding. The output names each one.
      2 - the check could not happen: the publisher is unreadable or declares no SINGLE_IMAGES map.
#>

[CmdletBinding()]
param(
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$publisher = Join-Path $repoRoot 'scripts/release/publish-play-listing.py'
$defaultLocaleImages = Join-Path $repoRoot 'play/listing/en-US/images'

if (-not (Test-Path -LiteralPath $publisher)) {
    Write-Host "assert-play-listing-graphics: CANNOT VERIFY - publisher not found: $publisher" -ForegroundColor Yellow
    exit 2
}

$declaration = (Get-Content -LiteralPath $publisher -Raw) -split "`n" |
    Where-Object { $_ -match '^\s*SINGLE_IMAGES\s*=' } |
    Select-Object -First 1
if (-not $declaration) {
    Write-Host "assert-play-listing-graphics: CANNOT VERIFY - no SINGLE_IMAGES map in $publisher" -ForegroundColor Yellow
    exit 2
}

$declared = [ordered]@{}
foreach ($pair in [regex]::Matches($declaration, "'(?<type>[^']+)'\s*:\s*'(?<file>[^']+)'")) {
    $declared[$pair.Groups['type'].Value] = $pair.Groups['file'].Value
}
if ($declared.Count -eq 0) {
    Write-Host "assert-play-listing-graphics: CANNOT VERIFY - SINGLE_IMAGES parsed to nothing" -ForegroundColor Yellow
    exit 2
}

# One artwork, several publication trees. See .DESCRIPTION for why this table is spelled out while
# the declared list above is read from the publisher.
$mirrorSets = @(
    @{
        Artwork = 'featureGraphic'
        Paths   = @(
            'play/listing/en-US/images/featureGraphic.png',
            'fastlane/metadata/android/en-US/images/featureGraphic.png'
        )
        Repair  = 'python scripts/release/compose-feature-graphic.py writes both copies in one pass'
    },
    @{
        Artwork = 'icon'
        Paths   = @(
            'play/listing/en-US/images/icon.png',
            'fastlane/metadata/android/en-US/images/icon.png',
            'store_assets/icon_512.png'
        )
        Repair  = 'copy store_assets/icon_512.png over the other two'
    }
)

$findings = @()

foreach ($type in $declared.Keys) {
    $file = $declared[$type]
    $path = Join-Path $defaultLocaleImages $file
    if (-not (Test-Path -LiteralPath $path)) {
        $findings += "MISSING   $type -> play/listing/en-US/images/$file does not exist; the publisher declares it and would skip it in silence"
    }
}

foreach ($set in $mirrorSets) {
    $present = @()
    foreach ($relative in $set.Paths) {
        $full = Join-Path $repoRoot $relative
        if (Test-Path -LiteralPath $full) {
            $present += [pscustomobject]@{
                Path = $relative
                Hash = (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash
            }
        }
    }
    if ($present.Count -lt 2) { continue }
    # @() because Sort-Object -Unique returns a bare string for a single value, which has no .Count
    # under Set-StrictMode.
    $distinct = @($present.Hash | Sort-Object -Unique)
    if ($distinct.Count -gt 1) {
        $detail = ($present | ForEach-Object { "$($_.Path)=$($_.Hash.Substring(0,12))" }) -join ' | '
        $findings += "DIVERGED  $($set.Artwork) -> copies disagree: $detail. Repair: $($set.Repair)"
    }
}

if ($findings.Count -gt 0) {
    Write-Host "assert-play-listing-graphics: FAIL ($($findings.Count) finding(s))" -ForegroundColor Red
    $findings | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

if (-not $Quiet) {
    $names = ($declared.Keys | ForEach-Object { $declared[$_] }) -join ', '
    Write-Host "assert-play-listing-graphics: PASS ($($declared.Count) declared single image(s) sourced: $names; $($mirrorSets.Count) mirrored artwork(s) agree)" -ForegroundColor Green
}
exit 0
