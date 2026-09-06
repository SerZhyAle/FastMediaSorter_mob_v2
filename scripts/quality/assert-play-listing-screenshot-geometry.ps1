#requires -Version 7.0
<#
.SYNOPSIS
    Gate: no composed Play listing screenshot buries its own frame under a caption band, and the
    images inside one carousel all have one shape (S2602).

.DESCRIPTION
    Google states it plainly - "Taglines should not take up more than 20% of the image"
    (support.google.com/googleplay/android-developer/answer/9866151). Measured 2026-09-06, all 24
    tenInchScreenshots in the live listing breached it, at 22.2-23.5% of image height, with the
    caption band painted over the top of every tablet frame including its app bar. It reached
    production because nothing ever compared the composed output against that number: the composer
    validated Play's hard limits on size and aspect, which the set passed, and the tagline share was
    a recommendation nobody had turned into a check.

    THREE FINDINGS, NAMED APART BECAUSE THEY CALL FOR DIFFERENT REPAIRS:

      1. BAND    - a caption band over the ceiling share of the image height. Repair: recapture and
                   recompose that set with the current composer, which sizes the band off canvas
                   HEIGHT (S2573). A file composed before that change cannot be repaired in place;
                   its band is baked into the pixels.
      2. MIXED   - images inside one locale's one screenshot type that do not share a single shape.
                   Repair: run compose-play-screenshots.py as a FULL run, never --only. Publication
                   deletes every image of a type before uploading, so a carousel assembled from two
                   partial runs shows both shapes side by side.
      3. BOUNDS  - a side outside Play's per-side bounds, or a long-to-short ratio past its limit,
                   both read from the composer rather than restated here. Repair: recompose from a
                   raw shot the composer can fit; this is a hard Play limit and an upload carrying
                   it is rejected outright.

    BOUNDS IS APPLIED ONLY TO THE TYPES THE COMPOSER ACTUALLY WRITES, and the report says which
    types it therefore left unbounded. The tree carries a third screenshot type, wearScreenshots,
    produced by another tool entirely; the constants above come from a line the composer itself
    labels the Play PHONE-screenshot bounds, and Play judges a watch frame by a stricter rule of its
    own. Measuring a wear frame against phone bounds would let one that breaks Play's wear rule pass
    - a false PASS, which is the one direction a release gate must never fail in. BAND and MIXED
    still apply to every type, because neither depends on the form factor: a caption band burying a
    frame is wrong wherever it appears, and one carousel showing two shapes is wrong wherever it is
    published.

    SHAPE, NOT PIXEL SIZE, IS WHAT MIXED JUDGES - and that distinction is load-bearing rather than
    pedantic. The live phone set is composed from two different capture resolutions, 1200x2400 and
    1422x2844, because the composer normalizes any raw taller than 2:1 onto a 2:1 canvas; both are
    the same 2:1 and a carousel shows them as one shape. A gate comparing pixel sizes would report
    all 24 phone frames as defective on a set that is correct, which is the fastest way to have a
    gate switched off. The tablet set has the opposite property and it is why this ticket exists:
    its raw passes through unpadded, so the set inherits its aspect straight from the AVD
    resolution and two capture profiles really would produce two shapes (strategic S2602 §0).

    Shape is width over height rather than long over short, so a portrait frame and a landscape one
    are not called identical - they share a long-to-short ratio and look nothing alike in a row.

    THE MEASUREMENT LIVES IN listing_screenshot_geometry.py AND THE POLICY LIVES HERE. That script
    reports sizes, ratios and band shares and applies no threshold; this one owns the 20% ceiling
    and Play's bounds, and reads the band color and those bounds out of the composer rather than
    restating them. So a rebrand of the band cannot silently zero the measurement, and changing the
    ceiling is an edit to one line of policy in one file.

    Scope class (CLAUDE.md Rule 33): RELEASE, not per ticket. All four criteria hold. A listing
    image reaches a user only when the owner publishes, which is owner-gated and rare; its subject
    is the whole published asset tree, which no changed file created and no sibling session can
    repair; every finding prints its own path and its own measured number; and recomposing a set is
    one run whenever it is done. Wiring it into post-change.ps1 would redden every session that
    closed a ticket until this ticket's recapture finished - the class S1939 measured at 68 of 191
    red lines. It is EXPECTED TO FAIL until S2602 Phase 03 and Phase 04 recompose both sets.

.PARAMETER ListingRoot
    Directory holding <locale>/images/<type>/. Defaults to play/listing. Point it at a candidate
    tree to judge a set before it replaces the published one.

.PARAMETER MaxBandShare
    Caption-band ceiling as a share of image height. Defaults to 0.20, Google's stated figure.

.PARAMETER Gate
    Accepted for the release-scope runner's uniform child invocation; this gate is always fatal on
    a finding, so it changes nothing here.

.PARAMETER Quiet
    Print only the verdict line, not the per-carousel summary.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-play-listing-screenshot-geometry.ps1

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-play-listing-screenshot-geometry.ps1 -ListingRoot temp/candidate-listing

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - every composed screenshot is inside the band ceiling, its carousel's shape and Play bounds.
      1 - at least one BAND, MIXED or BOUNDS finding. The output names each file and its number.
      2 - cannot verify: the venv python, the measurement script or the listing root is absent, the
          measurement did not return readable JSON or was missing a field this gate reads, or the
          tree held no PNG at all. Distinct from 1 on purpose - "did not look" and "found a defect"
          call for opposite reactions, and a gate that reports a drifted field name as a listing
          defect sends an operator off to recompose two dozen correct screenshots.
#>
[CmdletBinding()]
param(
    [string]$ListingRoot,
    [double]$MaxBandShare = 0.20,
    [switch]$Gate,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

# Matches the tolerance compose-play-screenshots.py uses in odd_shaped_siblings, so the composer's
# own mixed-shape warning and this gate cannot reach opposite verdicts about one set.
$aspectTolerance = 0.01

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$venvPython = Join-Path $repoRoot '.venv/Scripts/python.exe'
$measurer = Join-Path $PSScriptRoot 'listing_screenshot_geometry.py'
if (-not $ListingRoot) { $ListingRoot = Join-Path $repoRoot 'play/listing' }

foreach ($required in @(
        @{ Path = $venvPython; What = 'venv python' },
        @{ Path = $measurer; What = 'measurement script' },
        @{ Path = $ListingRoot; What = 'listing root' })) {
    if (-not (Test-Path -LiteralPath $required.Path)) {
        Write-Host "assert-play-listing-screenshot-geometry: CANNOT VERIFY - $($required.What) not found: $($required.Path)" -ForegroundColor Yellow
        exit 2
    }
}

$raw = & $venvPython $measurer --listing-root $ListingRoot 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "assert-play-listing-screenshot-geometry: CANNOT VERIFY - measurement exited $LASTEXITCODE" -ForegroundColor Yellow
    $raw | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    exit 2
}

try {
    $measured = ($raw -join "`n") | ConvertFrom-Json
}
catch {
    Write-Host "assert-play-listing-screenshot-geometry: CANNOT VERIFY - measurement output was not JSON: $($_.Exception.Message)" -ForegroundColor Yellow
    exit 2
}

# The measurement is produced by another file in another language, so its shape is checked before
# it is trusted. Under Set-StrictMode a renamed field would throw, and an uncaught throw leaves
# pwsh -File exiting 1 - the code this gate reserves for a real listing defect.
foreach ($field in 'images', 'minEdge', 'maxEdge', 'maxAspect', 'composedTypes') {
    if (-not $measured.PSObject.Properties[$field]) {
        Write-Host "assert-play-listing-screenshot-geometry: CANNOT VERIFY - measurement JSON has no '$field'" -ForegroundColor Yellow
        exit 2
    }
}

$images = @($measured.images)
if ($images.Count -eq 0) {
    Write-Host "assert-play-listing-screenshot-geometry: CANNOT VERIFY - no PNG found under $ListingRoot" -ForegroundColor Yellow
    exit 2
}

$composedTypes = @($measured.composedTypes)
$findings = @()

try {
    foreach ($image in $images) {
        if ($image.bandShare -gt $MaxBandShare) {
            $findings += ("BAND    {0} -> caption band is {1:P1} of image height, over the {2:P0} ceiling. " -f
                $image.path, $image.bandShare, $MaxBandShare) +
            'Repair: recompose this set with compose-play-screenshots.py, which sizes the band off canvas height (S2573).'
        }

        # See .DESCRIPTION: these bounds describe the composer's own output and nothing else.
        if ($composedTypes -notcontains $image.type) { continue }

        $shortEdge = [Math]::Min($image.width, $image.height)
        $longEdge = [Math]::Max($image.width, $image.height)
        if ($shortEdge -lt $measured.minEdge -or $longEdge -gt $measured.maxEdge) {
            $findings += "BOUNDS  $($image.path) -> size $($image.width)x$($image.height) outside Play's [$($measured.minEdge), $($measured.maxEdge)] per side. Repair: recompose from a raw shot the composer can fit."
        }
        if ($image.aspect -gt ($measured.maxAspect + 1e-6)) {
            $findings += ("BOUNDS  {0} -> aspect {1:N3} exceeds Play's {2}:1 limit. Repair: recompose; an upload carrying this is rejected." -f
                $image.path, $image.aspect, $measured.maxAspect)
        }
    }

    # @() because Group-Object returns a bare GroupInfo for a single group, and GroupInfo carries a
    # Count of its own members - so a one-carousel tree reported "5 image(s) across 5 carousel(s)".
    $carousels = @($images | Group-Object -Property { "$($_.locale)/$($_.type)" })
    foreach ($carousel in $carousels) {
        $shapes = @($carousel.Group.shape | Sort-Object -Unique)
        $spread = $shapes[-1] - $shapes[0]
        if ($spread -gt $aspectTolerance) {
            $detail = ($carousel.Group | ForEach-Object { "$($_.name) $($_.width)x$($_.height)" }) -join ', '
            $findings += ("MIXED   {0} -> shapes span {1:N3}, past the {2:N2} tolerance: {3}. " -f
                $carousel.Name, $spread, $aspectTolerance, $detail) +
            'Repair: run compose-play-screenshots.py as a FULL run, never --only - publishing replaces every image of a type at once.'
        }
    }
}
catch {
    Write-Host "assert-play-listing-screenshot-geometry: CANNOT VERIFY - measurement record missing a field this gate reads: $($_.Exception.Message)" -ForegroundColor Yellow
    exit 2
}

if ($findings.Count -gt 0) {
    Write-Host "assert-play-listing-screenshot-geometry: FAIL ($($findings.Count) finding(s) over $($images.Count) image(s))" -ForegroundColor Red
    $findings | Sort-Object | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

if (-not $Quiet) {
    foreach ($carousel in ($carousels | Sort-Object Name)) {
        $worst = ($carousel.Group | Sort-Object bandShare -Descending | Select-Object -First 1)
        $bounded = if ($composedTypes -contains $carousel.Group[0].type) { '' } else { ', bounds not judged' }
        Write-Host ("  {0,-28} {1} image(s), shape {2:N3}, widest band {3:P1}{4}" -f
            $carousel.Name, $carousel.Count, $worst.shape, $worst.bandShare, $bounded) -ForegroundColor DarkGray
    }
    # The detection's own margin over the whole set. See the measurement's docstring: the exposed
    # side is a caption dense enough to fill half a row, and this number is how close it came.
    $thinnest = ($images | Sort-Object bandMinRowShare | Select-Object -First 1)
    Write-Host ("  band detection margin: thinnest row {0:P1} against the {1:P0} threshold ({2})" -f
        $thinnest.bandMinRowShare, 0.5, $thinnest.path) -ForegroundColor DarkGray
}
$unbounded = @($images | Where-Object { $composedTypes -notcontains $_.type } |
    ForEach-Object { $_.type } | Sort-Object -Unique)
$note = if ($unbounded.Count -gt 0) { "; Play bounds not judged for $($unbounded -join ', ') - not composed here" } else { '' }
Write-Host ("assert-play-listing-screenshot-geometry: PASS ($($images.Count) image(s) across $($carousels.Count) carousel(s); every band under {0:P0}{1})" -f $MaxBandShare, $note) -ForegroundColor Green
exit 0
