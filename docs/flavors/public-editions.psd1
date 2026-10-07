# SITE-REPRESENTATION 0.1 rule 4 declaration (S4100), read by scripts/docs/lib/site-facts.ps1.
#
# This file declares a DECISION: which build variants a visitor can obtain, and the one name each is
# shown under on every page. The variants themselves stay owned by docs/flavors/flavor-matrix.json
# (generated from productFlavors); a variant named here that the matrix lacks, or a matrix variant
# named in neither list, is refused by scripts/quality/assert-site-facts.ps1 (dimension `declaration`).
#
# Publishing a variant: move its record from NotPublic to Editions with a display name, then fix every
# page the gate names. Withdrawing one is the same move in the other direction.
@{
    Editions  = @(
        @{ Flavor = 'standard'; Name = 'Standard' }
        @{ Flavor = 'noLegal'; Name = 'noLegal' }
        @{ Flavor = 'lite'; Name = 'Lite' }
        @{ Flavor = 'photos'; Name = 'Photos' }
        @{ Flavor = 'legacy'; Name = 'Legacy' }
        @{ Flavor = 'vr'; Name = 'VR' }
        @{ Flavor = 'foss'; Name = 'FOSS' }
    )

    NotPublic = @(
        @{
            Flavor = 'xr'
            Reason = 'Built for the Google Play Android XR track; the site names no such edition and carries no download for it.'
        }
    )

    # The edition whose minSdk the site quotes as "the minimum Android version" of the product.
    Mainline  = 'standard'

    # Download targets that are not editions of the phone app; their label is not judged as an edition name.
    Companions = @('wear', 'watchface')

    # The name a companion is shown under where a function page's availability names it (S4107).
    CompanionNames = @{ wear = 'FastMedia Wear' }

    # Where the gate looks for a claim about the editions. Site-facing text only: developer documents
    # state the matrix's own count and are held by assert-flavor-count-prose.ps1. A pattern is a path
    # relative to the repository root; `**` crosses directories, `*` does not.
    Surfaces  = @(
        'index*.html'
        'nolegal*.html'
        'documentation/**/*.html'
        'docs/content/**/*.md'
        'README.md'
        'docs/README*.md'
        'docs/QUICK_START*.md'
        'docs/DOCUMENTATION_VOICE_GUIDE.md'
    )

    # Pages that quote the product's minimum Android version; every "Android N+" on them is judged.
    Landing   = @('index*.html')

    # The script that labels a download button with an edition name.
    LabelScript = 'assets/site-ui.js'
}
