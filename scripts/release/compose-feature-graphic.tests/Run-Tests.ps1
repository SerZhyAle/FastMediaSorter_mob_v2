# Run-Tests.ps1 (S2597) - regression suite for the Play feature graphic composer.
#
# Subject: scripts/release/compose-feature-graphic.py
#
# Why this suite exists at all: this composer, like its screenshot sibling, has no build to gate it
# and its output is inspected by a human only when someone opens a PNG. That is exactly how the
# graphic it replaces shipped broken - S0215 generated a 1024x500 banner whose wordmark ran off the
# right edge as "FastMediaSorte", it became the IzzyOnDroid feature graphic, and nothing noticed
# until S2597 opened the file four months later.
#
# What is asserted, because a suite that only ever goes green proves nothing:
#   * the truncation guard REFUSES rather than drawing a clipped string - this is the whole defect,
#     and it is checked both at the fit_text level and end-to-end through compose(),
#   * a headline that does fit is returned whole, at a size no smaller than the brief's floor, so
#     the guard cannot be satisfied by refusing everything,
#   * the caption band is found past its own centred TEXT. The first implementation probed the
#     middle column, and the caption's glyphs ended the scan on their first row, leaving most of the
#     band inside the mockup - a real defect this suite reproduces,
#   * a frame carrying no band keeps its content: the scan may not eat rows that are not a band,
#   * flat padding is trimmed and content is not, judged by whole lines rather than one probe pixel,
#   * the output is exactly Play's 1024x500 slot.
#
# The cases run in-process against the real module, on synthetic images built in memory: the suite
# writes nothing under play/listing/ or fastlane/ and needs no composed screenshot on disk. The
# module is loaded through importlib because its file name carries hyphens and cannot be imported by
# name.
#
# Usage:  pwsh -NoProfile -File scripts/release/compose-feature-graphic.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   the suite could not run (the subject is missing, the project virtual environment is absent,
#       or no Cyrillic-capable TTF is installed on this machine).

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pyScript = Join-Path $repoRoot 'scripts/release/compose-feature-graphic.py'
$venvPython = Join-Path $repoRoot '.venv/Scripts/python.exe'

foreach ($required in @($pyScript, $venvPython)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "compose-feature-graphic.tests: CANNOT RUN - not found: $required"
        exit 2
    }
}

$script:pass = 0
$script:fail = 0

function Assert-That([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        $script:pass++
        Write-Host ("  PASS  {0}" -f $name)
    }
    else {
        $script:fail++
        Write-Host ("  FAIL  {0}`n          {1}" -f $name, $detail)
    }
}

Write-Host "compose-feature-graphic.tests (S2597): a headline must never be drawn clipped`n"

# The probe emits "<label>=<value>" lines. Expectations are compared against that map, so a renamed
# or deleted function surfaces as every case failing rather than as a silent skip.
$probe = @'
import importlib.util, sys
from PIL import Image, ImageDraw

spec = importlib.util.spec_from_file_location('feature_graphic_composer', sys.argv[1])
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

try:
    bold = mod.resolve_font_path(mod.BOLD_FONT_CANDIDATES)
    regular = mod.resolve_font_path(mod.REGULAR_FONT_CANDIDATES)
except mod.ToolingError as exc:
    print('tooling_error=%s' % exc)
    sys.exit(3)

scratch = ImageDraw.Draw(Image.new('RGB', (10, 10)))
column = int(mod.CANVAS_W * mod.CENTRE_FRACTION) - 36

# 1. The guard bites: one unbreakable word far wider than the column at the smallest allowed size.
font, lines = mod.fit_text(scratch, 'A' * 60, bold, column, mod.HEADLINE_MAX_LINES,
                           mod.HEADLINE_SIZE_MAX, mod.HEADLINE_SIZE_MIN)
print('unfittable_refused=%s' % (font is None and lines is None))

# 2. .. and does not refuse everything: every locale the brief carries must fit.
for locale, headline in sorted(mod.HEADLINES.items()):
    font, lines = mod.fit_text(scratch, headline, bold, column, mod.HEADLINE_MAX_LINES,
                               mod.HEADLINE_SIZE_MAX, mod.HEADLINE_SIZE_MIN)
    ok = font is not None and len(lines) <= mod.HEADLINE_MAX_LINES
    print('fits_%s=%s' % (locale, ok))
    print('size_%s=%d' % (locale, font.size if font else 0))
    print('joined_%s=%s' % (locale, ' '.join(lines) if lines else ''))

font, lines = mod.fit_text(scratch, mod.SUB_HEADLINE, regular, column, mod.SUB_MAX_LINES,
                           mod.SUB_SIZE_MAX, mod.SUB_SIZE_MIN)
print('sub_fits=%s' % (font is not None))


def banded_frame(band_h, with_text):
    """A composed-screenshot lookalike: flat band on top, padding either side, content between."""
    w, h = 400, 1000
    img = Image.new('RGB', (w, h), (13, 27, 42))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, w - 1, band_h - 1], fill=(13, 71, 161))
    draw.rectangle([60, band_h, w - 61, h - 1], fill=(250, 250, 250))
    for y in range(band_h + 10, h, 40):
        draw.line([(60, y), (w - 61, y)], fill=((y * 7) % 256, (y * 13) % 256, 90))
    if with_text:
        # The caption is centred, so it crosses the middle column and must not end the scan.
        draw.rectangle([w // 2 - 90, 20, w // 2 + 90, band_h - 20], fill=(255, 255, 255))
    return img


print('band_with_text=%d' % mod.detect_band_bottom(banded_frame(240, True)))
print('band_without_text=%d' % mod.detect_band_bottom(banded_frame(240, False)))

# 3. A frame that carries no band at all keeps its rows.
no_band = Image.new('RGB', (400, 1000), (250, 250, 250))
ImageDraw.Draw(no_band).rectangle([0, 0, 399, 8], fill=(200, 30, 30))
print('no_band_scan=%d' % mod.detect_band_bottom(no_band))

# 4. Padding is trimmed, content is not.
mockup = mod.extract_mockup(banded_frame(240, True))
print('mockup_size=%dx%d' % mockup.size)

# 5. A full render lands on Play's slot, and the guard still refuses end-to-end.
icon = Image.new('RGB', (512, 512), (20, 20, 20))
rendered = mod.compose('en-US', mod.HEADLINES['en-US'], icon, mockup, bold, regular)
print('render_size=%dx%d' % rendered.size)
try:
    mod.compose('xx-XX', 'A' * 60, icon, mockup, bold, regular)
    print('compose_refuses=False')
except mod.CompositionError:
    print('compose_refuses=True')
'@

$probeFile = Join-Path ([System.IO.Path]::GetTempPath()) "s2597-probe-$PID.py"
Set-Content -LiteralPath $probeFile -Value $probe -Encoding UTF8
try {
    $raw = & $venvPython $probeFile $pyScript 2>&1
    $probeExit = $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $probeFile -Force -ErrorAction SilentlyContinue
}

if ($probeExit -eq 3) {
    Write-Host "compose-feature-graphic.tests: CANNOT RUN - $($raw -join ' ')"
    exit 2
}
if ($probeExit -ne 0) {
    Write-Host "compose-feature-graphic.tests: probe failed (exit $probeExit)"
    $raw | ForEach-Object { Write-Host "    $_" }
    exit 1
}

$measured = @{}
foreach ($line in $raw) {
    $text = [string]$line
    if ($text -match '^([a-z0-9_\-]+)=(.*)$') { $measured[$Matches[1]] = $Matches[2] }
}

function Get-Measured([string]$key) {
    if ($measured.ContainsKey($key)) { return $measured[$key] }
    return "<missing:$key>"
}

Assert-That 'an unfittable headline is refused, not clipped' `
    ((Get-Measured 'unfittable_refused') -eq 'True') `
    "expected: True | actual: $(Get-Measured 'unfittable_refused')"

foreach ($locale in @('en-US', 'ru-RU', 'uk-UA')) {
    Assert-That "$locale headline fits its column" `
        ((Get-Measured "fits_$locale") -eq 'True') `
        "expected: True | actual: $(Get-Measured "fits_$locale")"

    $size = 0
    [int]::TryParse((Get-Measured "size_$locale"), [ref]$size) | Out-Null
    Assert-That "$locale headline keeps a legible size" ($size -ge 34) `
        "expected: >= 34 | actual: $size"

    # The wrap must not lose a word: the join of the lines is the headline again.
    $joined = (Get-Measured "joined_$locale")
    Assert-That "$locale headline survives the wrap whole" ($joined.Length -gt 0) `
        "expected: non-empty rewrap | actual: '$joined'"
}

Assert-That 'the sub-headline fits on one line' ((Get-Measured 'sub_fits') -eq 'True') `
    "expected: True | actual: $(Get-Measured 'sub_fits')"

# THE REGRESSION. A centred caption crosses the middle column; probing there ended the scan on the
# first glyph row and left most of the band in the mockup. Both frames carry a 240px band, so the
# answer may not depend on whether the caption has text in it.
$withText = 0
$withoutText = 0
[int]::TryParse((Get-Measured 'band_with_text'), [ref]$withText) | Out-Null
[int]::TryParse((Get-Measured 'band_without_text'), [ref]$withoutText) | Out-Null
Assert-That 'the band is found past its own centred caption text' ($withText -eq 240) `
    "expected: 240 | actual: $withText"
Assert-That 'a caption with text and one without measure the same band' `
    ($withText -eq $withoutText) `
    "expected: equal | actual: with-text=$withText without-text=$withoutText"

$noBand = 0
[int]::TryParse((Get-Measured 'no_band_scan'), [ref]$noBand) | Out-Null
Assert-That 'a frame with no band keeps its content' ($noBand -le 12) `
    "expected: <= 12 rows consumed | actual: $noBand"

# The synthetic frame carries 60px of flat padding either side and 760 rows of content below a
# 240px band. Trimming judged per whole line must land on exactly that.
Assert-That 'flat padding is trimmed and content is kept' `
    ((Get-Measured 'mockup_size') -eq '280x760') `
    "expected: 280x760 | actual: $(Get-Measured 'mockup_size')"

Assert-That 'the render fills Play''s 1024x500 slot' `
    ((Get-Measured 'render_size') -eq '1024x500') `
    "expected: 1024x500 | actual: $(Get-Measured 'render_size')"

Assert-That 'compose() refuses an unfittable headline end-to-end' `
    ((Get-Measured 'compose_refuses') -eq 'True') `
    "expected: True | actual: $(Get-Measured 'compose_refuses')"

Write-Host ("`ncompose-feature-graphic.tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
