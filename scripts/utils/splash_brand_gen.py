"""Generate the splash brand drawable (product mark + wordmark + slogan) for every locale.

The mark is the product master assets/identity/master/mark.svg, written by the identity generator
(scripts/identity/build-identity.ps1, S4784); its tone paths are copied unchanged and coloured through
@color/identity_mark_*, which the night qualifier switches to the family's dark tones (rule 28). The
wordmark is drawn from Manrope ExtraBold outlines, the family lockup typeface.

The Android splash icon slot is a 288-unit asset whose visible area is a 192-unit
circle, so everything drawn must sit inside that circle. The slogan's point size is
therefore chosen per locale by measuring the real outline bounds: cap height alone is
not enough, because descenders reach below the baseline and the circle narrows fast
towards the bottom of the asset.

Driven by generate-splash-brand.ps1 - do not call directly except when debugging.
"""
import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

from fontTools.ttLib import TTFont
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

VIEWPORT = 288.0
CIRCLE_R = 96.0
CENTRE = VIEWPORT / 2.0
FIT_MARGIN = 0.96

SLOGAN_FONT = os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts", "georgiai.ttf")
SLOGAN_BASELINE = 207.0
SLOGAN_SIZES = [18, 17, 16, 15, 14, 13, 12]

# The master's 240-unit mark square (96..336 of 432) lands on the 91.2-unit box the mark has always had
# above the wordmark, so the animation pivot and the text layout keep their places.
MARK_SPAN = 240.0
MARK_ORIGIN = 96.0
MARK_BOX = 91.2
MARK_X = CENTRE - MARK_BOX / 2.0
MARK_Y = 58.0
MARK_SCALE = MARK_BOX / MARK_SPAN
MARK_PIVOT_Y = MARK_Y + MARK_BOX / 2.0
# Source tone of a master path -> the generated colour resource that switches it by theme.
TONE_COLORS = (("family.blue.lit", "identity_mark_lit"), ("family.blue.base", "identity_mark_base"),
               ("family.blue.shade", "identity_mark_shade"))

WORDMARK_TEXT = "Fast Media Sorter"
WORDMARK_BASELINE = 197.0
WORDMARK_SIZES = [21, 20, 19, 18, 17, 16]
WORDMARK_TRANSLATE_Y = -14.0

STRING_KEY = "splash_slogan"


def render_line(font_path, text, size, baseline_y):
    """Render one centred line; return (path data, bounds, width) or None."""
    font = TTFont(font_path)
    upm = font["head"].unitsPerEm
    cmap = font.getBestCmap()
    glyphs = font.getGlyphSet()
    hmtx = font["hmtx"]
    scale = size / upm

    width = 0.0
    for ch in text:
        gname = cmap.get(ord(ch))
        if gname is None:
            print(f"  no glyph for {ch!r} in {os.path.basename(font_path)}", file=sys.stderr)
            continue
        width += hmtx[gname][0] * scale

    pen_x = CENTRE - width / 2.0
    parts = []
    box = [float("inf"), float("inf"), float("-inf"), float("-inf")]
    for ch in text:
        gname = cmap.get(ord(ch))
        if gname is None:
            continue
        # y is negated: font units grow upward, drawable coordinates grow downward
        transform = (scale, 0, 0, -scale, pen_x, baseline_y)
        svg = SVGPathPen(glyphs, ntos=lambda v: f"{v:.1f}")
        glyphs[gname].draw(TransformPen(svg, transform))
        drawn = svg.getCommands()
        if drawn.strip():
            parts.append(drawn)
            bounds = BoundsPen(glyphs)
            glyphs[gname].draw(TransformPen(bounds, transform))
            if bounds.bounds:
                x0, y0, x1, y1 = bounds.bounds
                box = [min(box[0], x0), min(box[1], y0), max(box[2], x1), max(box[3], y1)]
        pen_x += hmtx[gname][0] * scale

    if not parts:
        return None
    return " ".join(parts), tuple(box), width


def inside_circle(bounds, dy=0.0):
    """A rectangle lies inside a circle exactly when all four of its corners do."""
    x0, y0, x1, y1 = bounds
    y0, y1 = y0 + dy, y1 + dy
    limit = CIRCLE_R * FIT_MARGIN
    for x in (x0, x1):
        for y in (y0, y1):
            if ((x - CENTRE) ** 2 + (y - CENTRE) ** 2) ** 0.5 > limit:
                return False
    return True


def fit_line(text, baseline_y=SLOGAN_BASELINE):
    """Largest candidate size whose outline fits the mask circle, or (None, None, w)."""
    widest = 0.0
    for size in SLOGAN_SIZES:
        rendered = render_line(SLOGAN_FONT, text, size, baseline_y)
        if rendered is None:
            return None, None, 0.0
        path_d, bounds, width = rendered
        widest = width
        if inside_circle(bounds):
            return size, path_d, width
    return None, None, widest


def fit_wordmark(font_path):
    """The wordmark at the largest size that fits the mask circle where the group translate puts it."""
    for size in WORDMARK_SIZES:
        rendered = render_line(font_path, WORDMARK_TEXT, size, WORDMARK_BASELINE)
        if rendered and inside_circle(rendered[1], WORDMARK_TRANSLATE_Y):
            return rendered[0]
    return None


def read_mark(master_path, tokens_path):
    """The master's (colour resource, path data) pairs, in paint order."""
    with open(tokens_path, encoding="utf-8") as fh:
        tokens = json.load(fh)["tokens"]
    by_fill = {tokens[token]["light"].upper(): name for token, name in TONE_COLORS}
    with open(master_path, encoding="utf-8") as fh:
        svg = fh.read()
    pairs = []
    for fill, d in re.findall(r'<path fill="(#[0-9A-Fa-f]{6})" d="([^"]+)"', svg):
        if fill.upper() not in by_fill:
            raise KeyError(f"master fill {fill} is not a family tone")
        pairs.append((by_fill[fill.upper()], d))
    return pairs


def read_slogan(strings_path):
    try:
        root = ET.parse(strings_path).getroot()
    except ET.ParseError as exc:
        print(f"  cannot parse {strings_path}: {exc}", file=sys.stderr)
        return None
    for node in root.findall("string"):
        if node.get("name") == STRING_KEY:
            text = "".join(node.itertext()).strip()
            return text.strip('"').replace("\\'", "'")
    return None


def build_xml(slogan_path, wordmark_path, mark):
    slogan_block = ""
    if slogan_path:
        slogan_block = f"""
        <group android:name="slogan">
            <path
                android:name="sloganPath"
                android:fillColor="@color/splash_slogan"
                android:fillAlpha="1"
                android:pathData="{slogan_path}" />
        </group>"""
    mark_paths = "".join(f"""
                <path
                    android:name="arrowsPath{i}"
                    android:fillColor="@color/{color}"
                    android:fillAlpha="1"
                    android:pathData="{d}" />""" for i, (color, d) in enumerate(mark))
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!--
    GENERATED by scripts/utils/generate-splash-brand.ps1 (S1706, S4784) - do not hand-edit.
    Regenerate after changing the `{STRING_KEY}` string, the product master or the generator's layout constants.
    Splash-screen centre content: the product master assets/identity/master/mark.svg in the family
    tones (@color/identity_mark_*, switched by the night qualifier), the "Fast Media Sorter" wordmark
    in Manrope ExtraBold, and the slogan. Sized for the API-31 splash icon slot: a 288dp asset whose
    content stays inside the 192dp mask circle.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="288dp"
    android:height="288dp"
    android:viewportWidth="288"
    android:viewportHeight="288">
    <group android:name="splash">
        <group
            android:name="arrows"
            android:pivotX="{CENTRE:g}"
            android:pivotY="{MARK_PIVOT_Y:.1f}">
            <group
                android:scaleX="{MARK_SCALE:g}"
                android:scaleY="{MARK_SCALE:g}"
                android:translateX="{MARK_X - MARK_ORIGIN * MARK_SCALE:.2f}"
                android:translateY="{MARK_Y - MARK_ORIGIN * MARK_SCALE:.2f}">{mark_paths}
            </group>
        </group>
        <group android:name="wordmark" android:translateY="{WORDMARK_TRANSLATE_Y}">
            <path
                android:name="wordmarkPath"
                android:fillColor="@color/splash_content"
                android:fillAlpha="1"
                android:pathData="{wordmark_path}" />
        </group>{slogan_block}
    </group>
</vector>
"""


def locale_of(values_dir):
    name = os.path.basename(values_dir)
    return "" if name == "values" else name[len("values-"):]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--res-root", required=True)
    ap.add_argument("--font", required=True)
    ap.add_argument("--master", required=True)
    ap.add_argument("--tokens", required=True)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    wordmark_path = fit_wordmark(args.font)
    if wordmark_path is None:
        print(f"FAIL the wordmark does not fit the mask circle at any size down to {WORDMARK_SIZES[-1]}",
              file=sys.stderr)
        return 1
    mark = read_mark(args.master, args.tokens)
    values_dirs = sorted(
        os.path.join(args.res_root, d)
        for d in os.listdir(args.res_root)
        if d == "values" or d.startswith("values-")
    )

    changed, failures = [], []
    for values_dir in values_dirs:
        strings_path = os.path.join(values_dir, "strings.xml")
        if not os.path.isfile(strings_path):
            continue
        slogan = read_slogan(strings_path)
        locale = locale_of(values_dir)
        if slogan is None:
            continue
        size, slogan_path, width = fit_line(slogan)
        if size is None:
            failures.append(
                f"{locale or 'default'}: {slogan!r} does not fit the mask circle at any "
                f"size down to {SLOGAN_SIZES[-1]} (widest run {width:.0f} units). "
                f"Shorten the translation."
            )
            continue
        target_dir = os.path.join(args.res_root, "drawable" if not locale else f"drawable-{locale}")
        target = os.path.join(target_dir, "ic_splash_app_brand.xml")
        xml = build_xml(slogan_path, wordmark_path, mark)
        current = open(target, encoding="utf-8").read() if os.path.isfile(target) else None
        if current == xml:
            continue
        changed.append(f"{locale or 'default'} (slogan size {size})")
        if not args.check:
            os.makedirs(target_dir, exist_ok=True)
            with open(target, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(xml)

    for line in failures:
        print(f"FAIL {line}", file=sys.stderr)
    for line in changed:
        print(f"{'would write' if args.check else 'wrote'} {line}")
    if failures:
        return 1
    if args.check and changed:
        return 1
    if not changed:
        print("splash brand drawables are in sync")
    return 0


if __name__ == "__main__":
    sys.exit(main())
