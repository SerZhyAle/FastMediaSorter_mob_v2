<#
.SYNOPSIS
    Monochrome ICON-SET glyphs for the documentation portal, as inline SVG.
.DESCRIPTION
    Dot-sourced library used by the documentation generators; it returns HTML and never exits.
    SITE-EXPERIENCE rule 6: a portal page names a control with its meaning's glyph, drawn as
    monochrome inline SVG, and carries no emoji. The path data is copied from the glyphs of the
    ICON-SET vocabulary (contracts catalog, iconography, vocabulary 0.17), keyed by meaning id, so a
    page never picks a picture outside the vocabulary. Size and colour come from the .doc-glyph
    class in documentation/assets/docs.css, never from a style attribute.
#>

$script:PortalGlyphVocabulary = 'ICON-SET 0.17'

$script:PortalGlyphPaths = @{
    'action.search'     = '<path d="M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>'
    'nav.contents'      = '<path d="M3 7h2v2H3v-2zm0 4h2v2H3v-2zm0 4h2v2H3v-2zm4-8h14v2H7V7zm0 4h14v2H7v-2zm0 4h14v2H7v-2z"/>'
    'status.ok'         = '<path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>'
    'status.warning'    = '<path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/>'
    'app.theme'         = '<path d="M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10zm1-17.93c3.94.49 7 3.85 7 7.93s-3.05 7.44-7 7.93V4.07z"/>'
    'app.info'          ='<path d="M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/>'
    'content.document'  = '<path d="M14,2H6C4.9,2 4,2.9 4,4v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8L14,2zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z"/>'
    'device.phone'      = '<path d="M17,1H7c-1.1,0 -2,0.9 -2,2v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3c0,-1.1 -0.9,-2 -2,-2zM17,19H7V5h10v14z"/>'
    'device.tv'         = '<path d="M21,3L3,3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 2,-0.9 2,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17L3,17L3,5h18v12z"/>'
    'device.vr-headset' = '<path d="M20.74,6H3.26C2.57,6 2,6.57 2,7.26v9.49C2,17.43 2.57,18 3.26,18h4.85c0.45,0 0.86,-0.26 1.06,-0.67l0.99,-1.99c0.33,-0.67 1.01,-1.09 1.75,-1.09h0.18c0.74,0 1.42,0.42 1.75,1.09l0.99,1.99c0.2,0.41 0.61,0.67 1.06,0.67h4.85c0.69,0 1.26,-0.57 1.26,-1.26V7.26C22,6.57 21.43,6 20.74,6zM7.5,14C5.57,14 4,12.43 4,10.5S5.57,7 7.5,7S11,8.57 11,10.5S9.43,14 7.5,14zM16.5,14c-1.93,0 -3.5,-1.57 -3.5,-3.5S14.57,7 16.5,7S20,8.57 20,10.5S18.43,14 16.5,14z"/><path d="M7.5,9a1.5,1.5 0,1 0,0 3,1.5 1.5,0 0,0 0,-3z"/><path d="M16.5,9a1.5,1.5 0,1 0,0 3,1.5 1.5,0 0,0 0,-3z"/>'
    'source.watch'      = '<g transform="translate(12 12) scale(0.917 0.917) translate(-12 -12)"><path d="M20,12c0,-2.54 -1.19,-4.81 -3.04,-6.27L16,0L8,0l-0.96,5.73C5.19,7.19 4,9.46 4,12s1.19,4.81 3.04,6.27L8,24h8l0.96,-5.73C18.81,16.81 20,14.54 20,12zM6,12c0,-3.31 2.69,-6 6,-6s6,2.69 6,6 -2.69,6 -6,6 -6,-2.69 -6,-6z"/><path d="M12.5,12L12.5,8.5L11.5,8.5L11.5,12.5L15,12.5L15,11.5z"/></g>'
    'system.internet'   = '<path d="M12,2a10,10 0,1 0,0 20a10,10 0,0 0,0 -20zM4.1,11a8.1,8.1 0,0 1,1.7,-4.1h2.8A14.7,14.7 0,0 0,8,11H4.1zM4.1,13H8a14.7,14.7 0,0 0,0.6 4.1H5.8A8.1,8.1 0,0 1,4.1 13zM12,19.9a12.7,12.7 0,0 1,-1.4,-4.9h2.8A12.7,12.7 0,0 1,12 19.9zM13.4,13h-2.8a12.7,12.7 0,0 1,0,-2h2.8a12.7,12.7 0,0 1,0 2zM12,4.1a12.7,12.7 0,0 1,1.4,4.9h-2.8A12.7,12.7 0,0 1,12 4.1zM15.4,17.1a14.7,14.7 0,0 0,0.6,-4.1h3.9a8.1,8.1 0,0 1,-1.7 4.1h-2.8zM16,11a14.7,14.7 0,0 0,-0.6,-4.1h2.8a8.1,8.1 0,0 1,1.7 4.1H16z"/>'
    'status.locked'     = '<path d="M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"/>'
}

function Get-PortalGlyph {
    # Inline SVG for one ICON-SET meaning; decorative, because the visible text beside it names it.
    param([Parameter(Mandatory)][string]$Meaning)
    if (-not $script:PortalGlyphPaths.ContainsKey($Meaning)) {
        throw "portal-glyphs: '$Meaning' is not a meaning of the portal glyph set ($script:PortalGlyphVocabulary); add its catalog glyph first"
    }
    return '<svg class="doc-glyph" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' + $script:PortalGlyphPaths[$Meaning] + '</svg>'
}
