# S3430: ICON-RENDER rule 2 for the phone's glyph files - a glyph that paints a literal colour must
# declare android:tint, so a call site that sets no tint of its own still gets the theme's colour.
# Measured 2026-09-24: 146 phone ic_*.xml vectors painted white or black with no tint and relied on
# every call site to recolour them; one forgotten site made the glyph vanish on the opposite theme.
# Dot-sourced by scripts/quality/assert-icon-style.ps1; its contract suite is
# scripts/quality/assert-icon-style.tests/Run-Tests.ps1.

# A paint that is not a literal colour: a theme attribute resolves at draw time, and a fully
# transparent paint (an outline's hollow fill) has no colour to bake.
function Test-IsLiteralPaint([string] $paint) {
    if ([string]::IsNullOrWhiteSpace($paint)) { return $false }
    if ($paint.StartsWith('?')) { return $false }
    if ($paint -match '^@android:color/transparent$|^@color/transparent$') { return $false }
    if ($paint -match '^#00[0-9A-Fa-f]{6}$|^#0[0-9A-Fa-f]{3}$') { return $false }
    return $true
}

# Returns $null when the file holds the rule, else a short description of the baked paints.
# Only a <vector> root is judged; XML comments are ignored, because several glyph files quote an
# android:tint in a comment that explains why they do NOT carry one.
function Test-GlyphTint([string] $path) {
    $text = [System.IO.File]::ReadAllText($path)
    $text = [regex]::Replace($text, '<!--.*?-->', '', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if ($text -notmatch '<vector\b') { return $null }
    if ($text -match '<vector\b[^>]*\bandroid:tint="') { return $null }
    $paints = @([regex]::Matches($text, 'android:(fillColor|strokeColor)="([^"]+)"') |
        ForEach-Object { $_.Groups[2].Value } | Where-Object { Test-IsLiteralPaint $_ } | Sort-Object -Unique)
    if (-not $paints.Count) { return $null }
    return ('literal ' + ($paints -join ' ') + ' with no android:tint')
}
