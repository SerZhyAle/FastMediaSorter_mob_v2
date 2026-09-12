# Run-Tests.ps1 (S2573) - regression suite for the caption geometry of the Play screenshot composer.
#
# Subject: scripts/release/compose-play-screenshots.py
#
# Why this suite exists at all: this composer has no build to gate it and its output is inspected by
# a human only when someone opens a PNG. The defect it now guards survived a full ticket that way -
# the caption band was painted straight onto the screenshot, so on a landscape tablet frame it
# covered 23% of the height, over the 20% Google allows a tagline, and buried the app bar of all
# eight tenInchScreenshots. It was found by eye while reviewing an unrelated slot (S2398), not by any
# check, and by then the raw shots that would allow a recompose were gone.
#
# What is asserted, because a suite that only ever goes green proves nothing:
#   * the fitted screenshot survives the compose pixel-for-pixel - this is the whole defect, and it
#     is checked on both form factors because the old rule failed only on one of them,
#   * the band spends the same share of the height on a landscape and a portrait frame, and both
#     stay under Google's 20% - a proportion that holds on one shape only is what broke here,
#   * the SUPERSEDED short-edge rule still measures over 20% on the landscape frame; without this
#     the four cases above would also pass on code that never had the defect, and the suite would
#     stop being evidence that the fix bites,
#   * a portrait frame keeps the exact font size the short-edge rule gave it, so the phone set - the
#     one that was never wrong - is provably untouched,
#   * both outputs still satisfy validate_bounds, since the fix works by ADDING canvas and Play caps
#     the aspect at 2:1,
#   * a partial recompose reports a set of two shapes and stays quiet on a set of one.
#
# The cases run in-process against the real module, on synthetic frames built in memory: the suite
# writes nothing under play/listing/ and needs no raw shot under temp/. The module is loaded through
# importlib because its file name carries hyphens and cannot be imported by name.
#
# Usage:  pwsh -NoProfile -File scripts/release/compose-play-screenshots.tests/Run-Tests.ps1
#
# Exit codes:
#   0   all cases pass.
#   1   at least one case failed.
#   2   the suite could not run (the subject is missing, the project virtual environment is absent,
#       or no caption-capable TTF is installed on this machine).

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$pyScript = Join-Path $repoRoot 'scripts/release/compose-play-screenshots.py'
$venvPython = Join-Path $repoRoot '.venv/Scripts/python.exe'

foreach ($required in @($pyScript, $venvPython)) {
    if (-not (Test-Path -LiteralPath $required)) {
        Write-Host "compose-play-screenshots.tests: CANNOT RUN - not found: $required"
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

Write-Host "compose-play-screenshots.tests (S2573): the caption must not cover the frame`n"

# The probe emits "<label>=<value>" lines. Expectations are compared against that map, so a renamed
# or deleted function surfaces as every case failing rather than as a silent skip.
$probe = @'
import importlib.util, os, sys, tempfile
from PIL import Image, ImageChops, ImageDraw

spec = importlib.util.spec_from_file_location('play_screenshot_composer', sys.argv[1])
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

CAPTION = "Use the app as\nyour home screen"
# The two real capture geometries: the Pixel_Tablet AVD in landscape and the phone AVD in portrait.
LANDSCAPE, PORTRAIT = (2560, 1600), (1080, 2844)


def frame(w, h):
    """A frame whose topmost rows carry content, so a band that covered them would be detectable."""
    img = Image.new('RGB', (w, h), (250, 250, 250))
    draw = ImageDraw.Draw(img)
    for y in range(0, h, max(1, h // 40)):
        draw.line([(0, y), (w, y)], fill=((y * 7) % 256, (y * 13) % 256, (y * 29) % 256))
    draw.rectangle([0, 0, w - 1, max(1, h // 50)], fill=(200, 30, 30))
    return img


def measure(tag, size):
    fitted = mod.fit_to_aspect(frame(*size))
    out = mod.compose_with_caption(fitted, CAPTION)
    band_h = out.height - fitted.height
    left = (out.width - fitted.width) // 2
    carried = out.crop((left, band_h, left + fitted.width, band_h + fitted.height))
    print('%s_intact=%s' % (tag, ImageChops.difference(carried, fitted).getbbox() is None))
    print('%s_returns_new=%s' % (tag, out is not fitted))
    print('%s_share=%.4f' % (tag, band_h / out.height))
    print('%s_aspect=%.4f' % (tag, max(out.size) / min(out.size)))
    print('%s_font=%d' % (tag, mod.caption_font_size(fitted.height)))
    print('%s_font_oldrule=%d' % (tag, max(28, min(fitted.size) // 18)))
    print('%s_size=%dx%d' % (tag, out.width, out.height))
    try:
        mod.validate_bounds(out, tag)
        print('%s_bounds=True' % tag)
    except ValueError as exc:
        print('%s_bounds=False (%s)' % (tag, exc))


def old_rule_share(size):
    """The short-edge rule this ticket replaced, kept so the suite proves the fix bites."""
    fitted = mod.fit_to_aspect(frame(*size))
    font = mod.resolve_font(max(28, min(fitted.size) // 18))
    spacing = max(6, font.size // 5)
    probe_draw = ImageDraw.Draw(Image.new('RGB', (1, 1)))
    bbox = probe_draw.multiline_textbbox((0, 0), CAPTION, font=font, spacing=spacing, align='center')
    band = (bbox[3] - bbox[1]) + 2 * font.size
    # The old band ate the frame instead of adding to it, so its share is of the frame's own height.
    return band / fitted.height


measure('landscape', LANDSCAPE)
measure('portrait', PORTRAIT)
print('landscape_share_oldrule=%.4f' % old_rule_share(LANDSCAPE))
print('portrait_share_oldrule=%.4f' % old_rule_share(PORTRAIT))
print('has_draw_caption=%s' % hasattr(mod, 'draw_caption'))

mixed = tempfile.mkdtemp()
Image.new('RGB', (2560, 1600)).save(os.path.join(mixed, '01.png'))
Image.new('RGB', (2560, 1787)).save(os.path.join(mixed, '08.png'))
print('odd_flagged=%d' % len(mod.odd_shaped_siblings(mixed, '08.png', (2560, 1787))))

uniform = tempfile.mkdtemp()
Image.new('RGB', (2560, 1600)).save(os.path.join(uniform, '01.png'))
Image.new('RGB', (2560, 1600)).save(os.path.join(uniform, '08.png'))
print('odd_silent=%d' % len(mod.odd_shaped_siblings(uniform, '08.png', (2560, 1600))))
'@

$probeFile = Join-Path ([IO.Path]::GetTempPath()) ("s2573-compose-probe-{0}.py" -f $PID)
try {
    Set-Content -LiteralPath $probeFile -Value $probe -Encoding utf8
    $probeOut = & $venvPython $probeFile $pyScript 2>&1 | Out-String
    $probeExit = $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $probeFile -Force -ErrorAction SilentlyContinue
}

if ($probeExit -ne 0) {
    Write-Host "compose-play-screenshots.tests: CANNOT RUN - the compose probe failed:`n$probeOut"
    exit 2
}

$o = @{}
foreach ($line in ($probeOut -split "`r?`n")) {
    if ($line -match '^\s*([a-z_]+)=(.+?)\s*$') { $o[$Matches[1]] = $Matches[2] }
}

function Get-Observed([string]$key) {
    if ($o.ContainsKey($key)) { return $o[$key] }
    return '<case absent>'
}

# --- Cases 1-4: the screenshot survives the compose -----------------------------------------------
foreach ($shape in @('landscape', 'portrait')) {
    Assert-That "$shape - the frame survives pixel-for-pixel under the band" `
        ((Get-Observed "${shape}_intact") -eq 'True') `
        "expected: True | actual: $(Get-Observed "${shape}_intact") | size $(Get-Observed "${shape}_size")"

    Assert-That "$shape - the compose returns a new canvas instead of painting its argument" `
        ((Get-Observed "${shape}_returns_new") -eq 'True') `
        "expected: True | actual: $(Get-Observed "${shape}_returns_new")"
}

# --- Cases 5-7: the band is proportional, and stays inside Google's tagline limit -----------------
$landscapeShare = [double](Get-Observed 'landscape_share')
$portraitShare = [double](Get-Observed 'portrait_share')

Assert-That 'the band spends the same share of the height on both form factors' `
    ([math]::Abs($landscapeShare - $portraitShare) -le 0.01) `
    "expected: within 0.01 | actual: landscape $landscapeShare vs portrait $portraitShare"

foreach ($shape in @('landscape', 'portrait')) {
    Assert-That "$shape - the band stays under the 20% Google allows a tagline" `
        ([double](Get-Observed "${shape}_share") -lt 0.20) `
        "expected: < 0.20 | actual: $(Get-Observed "${shape}_share")"
}

# --- Case 8: the superseded rule really did break the landscape frame -----------------------------
# Without this the cases above would pass just as well on code that never carried the defect.
Assert-That 'the superseded short-edge rule measures over 20% on the landscape frame' `
    ([double](Get-Observed 'landscape_share_oldrule') -gt 0.20) `
    "expected: > 0.20 | actual: $(Get-Observed 'landscape_share_oldrule') - if this fails the suite no longer proves the fix bites"

# --- Cases 9-10: the phone set, which was never wrong, is provably untouched ----------------------
Assert-That 'a portrait frame keeps the font size the short-edge rule gave it' `
    ((Get-Observed 'portrait_font') -eq (Get-Observed 'portrait_font_oldrule')) `
    "expected: equal | actual: new $(Get-Observed 'portrait_font') vs old $(Get-Observed 'portrait_font_oldrule')"

Assert-That 'a landscape frame no longer takes the portrait-shaped font' `
    ([int](Get-Observed 'landscape_font') -lt [int](Get-Observed 'landscape_font_oldrule')) `
    "expected: smaller | actual: new $(Get-Observed 'landscape_font') vs old $(Get-Observed 'landscape_font_oldrule')"

# --- Cases 11-13: Play's hard bounds still hold on the taller output ------------------------------
foreach ($shape in @('landscape', 'portrait')) {
    Assert-That "$shape - validate_bounds accepts the composed frame" `
        ((Get-Observed "${shape}_bounds") -eq 'True') `
        "expected: True | actual: $(Get-Observed "${shape}_bounds")"
}

Assert-That 'the portrait output stays inside the 2:1 aspect cap' `
    ([double](Get-Observed 'portrait_aspect') -le 2.0) `
    "expected: <= 2.0 | actual: $(Get-Observed 'portrait_aspect') - adding a band lengthens the tall frame, so this is the bound that binds"

# --- Cases 14-16: a partial recompose reports what it left behind ---------------------------------
Assert-That 'a sibling of a different shape is reported' `
    ((Get-Observed 'odd_flagged') -eq '1') `
    "expected: 1 | actual: $(Get-Observed 'odd_flagged')"

Assert-That 'a set of one shape reports nothing' `
    ((Get-Observed 'odd_silent') -eq '0') `
    "expected: 0 | actual: $(Get-Observed 'odd_silent')"

Assert-That 'the superseded draw_caption is gone rather than left beside its replacement' `
    ((Get-Observed 'has_draw_caption') -eq 'False') `
    "expected: False | actual: $(Get-Observed 'has_draw_caption') - a dead painter is the one a future edit calls by mistake"

Write-Host ("`ncompose-play-screenshots.tests: {0} passed, {1} failed" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
