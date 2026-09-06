#!/usr/bin/env python
"""Measure the geometry of every composed Play listing screenshot (S2602 Phase 02).

Walks play/listing/<locale>/images/<type>/*.png and reports, per file: its pixel size, its aspect
ratio as long edge over short edge, and the height of the caption band at its top edge as a share
of image height. Emits JSON on stdout for assert-play-listing-screenshot-geometry.ps1 to judge.

MEASURES ONLY, DECIDES NOTHING. No threshold lives here - not the 20% tagline ceiling, not the Play
size bounds. The gate applies those, so a change of policy edits one file and a change of technique
edits the other, and neither can quietly reinterpret the other's numbers.

HOW THE BAND IS FOUND, AND WHY NOT WITH A COLUMN PROBE. compose-play-screenshots.py paints a flat
BAND_COLOR rectangle across the full width above the frame, then draws the caption in white across
its middle. A probe that walks one column downwards stops at the first glyph it meets and reports
the distance to the text rather than the height of the band: measured on
en-US/phoneScreenshots/01.png it returned 3.4% for a band that is really 11.8% (strategic S2602 §0).
So each ROW is judged as a whole and a row counts as band while most of its pixels are band-colored;
the caption is a short centered string of thin strokes and never comes close to half a row.

THE COMPOSER'S CONSTANTS ARE READ OUT OF THE COMPOSER, NEVER RESTATED HERE. A second copy of
BAND_COLOR would go stale on the first rebrand, and the failure would be silent in the worst
direction: this script would find no band at all, report 0%, and the gate would turn green on a
listing whose captions had never been measured. Same for the Play bounds. This is the S2340 lesson
that assert-play-listing-graphics.ps1 records - a declaration duplicated is a declaration that
drifts - so an unparseable composer is a refusal to measure, not a default.

WHY THE ROW FRACTION IS 0.5 AND NOT A CLEVERER TEST. The obvious refinement - call a row band when
almost every pixel lies on the segment between BAND_COLOR and TEXT_COLOR, which would retire the
constant below - is worse, because pure white sits at that segment's far end: any screenshot whose
own top row is near-white, a light app bar or a white background, would be counted as caption band.
That trades a false positive in a common case for a false negative in a hypothetical one. The
measured margins say the simple test has room on both sides: on the live corpus the worst row INSIDE
a band scores 0.758 on the phone and 0.849 on the tablet, while the best row BELOW it scores 0.000
and so does the whole rest of the frame. The exposed side is a caption dense enough to fill half a
row with glyphs, which would stop the scan early and under-report the band; bandMinRowShare is
emitted per image so that approach shows up as a shrinking number rather than as a silently green
listing.

Usage:
    python listing_screenshot_geometry.py [--listing-root <dir>] [--composer <path>]

Exit codes:
    0 - measured; JSON on stdout, one record per PNG found (an empty set is still a measurement).
    2 - could not measure: the listing root is absent, the composer's constants would not parse, or
        a file with a .png name would not decode. Never 1 - this script finds no defects, so the
        gate above it can treat every non-zero code as "could not verify" without ambiguity.
"""
import argparse
import json
import os
import re
import sys

try:
    import numpy as np
    from PIL import Image
except ImportError as exc:  # pragma: no cover - environment failure, not a listing defect
    print(f"ERROR: cannot measure without Pillow and numpy: {exc}", file=sys.stderr)
    sys.exit(2)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..'))
DEFAULT_LISTING_ROOT = os.path.join(REPO_ROOT, 'play', 'listing')
DEFAULT_COMPOSER = os.path.join(REPO_ROOT, 'scripts', 'release', 'compose-play-screenshots.py')

# A band row is one whose pixels are mostly band-colored. The caption's white strokes and their
# antialiased edges are a small minority of any row they cross, so the two constants below are not
# a tuning surface - halfway between "a few glyphs" and "the frame has begun" is the whole range.
BAND_ROW_FRACTION = 0.5
COLOR_TOLERANCE = 8


def read_composer_constants(composer_path):
    """Return (band_color, min_edge, max_edge, max_aspect) parsed from the composer's source."""
    if not os.path.exists(composer_path):
        print(f"ERROR: composer not found: {composer_path}", file=sys.stderr)
        sys.exit(2)
    with open(composer_path, 'r', encoding='utf-8') as handle:
        source = handle.read()

    band = re.search(r'^BAND_COLOR\s*=\s*\((\d+),\s*(\d+),\s*(\d+)\)', source, re.MULTILINE)
    bounds = re.search(
        r'^MIN_EDGE,\s*MAX_EDGE,\s*MAX_ASPECT\s*=\s*(\d+),\s*(\d+),\s*([\d.]+)',
        source, re.MULTILINE)
    # Which output directories this composer can write. Those are the only types whose files it
    # made, and so the only ones the bounds above legitimately describe - the composer's own line
    # calls them the Play PHONE-screenshot bounds, and a wear frame answers to a different Play
    # rule entirely. Parsed rather than listed here for the same reason as the constants.
    produced = re.search(
        r"^OUT_SUBDIR\s*=\s*'([^']+)'\s+if\s+TABLET\s+else\s+'([^']+)'", source, re.MULTILINE)
    if not band or not bounds or not produced:
        missing = ('BAND_COLOR' if not band
                   else 'MIN_EDGE/MAX_EDGE/MAX_ASPECT' if not bounds else 'OUT_SUBDIR')
        print(f"ERROR: {missing} not parseable from {composer_path}; refusing to assume a default",
              file=sys.stderr)
        sys.exit(2)

    return (
        tuple(int(band.group(i)) for i in (1, 2, 3)),
        int(bounds.group(1)),
        int(bounds.group(2)),
        float(bounds.group(3)),
        sorted(produced.groups()),
    )


def leading_band_rows(image, band_color):
    """Return (rows, thinnest_row_share) for the caption band at the top edge.

    The second value is how band-colored the LEAST band-colored row inside the detected band was.
    It is the detection's own margin above BAND_ROW_FRACTION - see the module docstring - and it is
    reported rather than acted on, so a caption that starts crowding the threshold is visible.
    """
    pixels = np.asarray(image.convert('RGB'), dtype=np.int16)
    close_enough = (np.abs(pixels - np.array(band_color, dtype=np.int16)) <= COLOR_TOLERANCE)
    band_share_per_row = close_enough.all(axis=2).mean(axis=1)
    first_non_band = np.flatnonzero(band_share_per_row < BAND_ROW_FRACTION)
    rows = int(first_non_band[0]) if first_non_band.size else int(image.height)
    thinnest = float(band_share_per_row[:rows].min()) if rows else 1.0
    return rows, round(thinnest, 4)


def measure_tree(listing_root, band_color):
    """One record per PNG under <locale>/images/<type>/, sorted for a stable report."""
    records = []
    for locale in sorted(os.listdir(listing_root)):
        images_dir = os.path.join(listing_root, locale, 'images')
        if not os.path.isdir(images_dir):
            continue
        # Only the subdirectories: the single images (feature graphic, icon) sit directly in
        # images/ and carry no caption band, so they are not this measurement's subject.
        for shot_type in sorted(os.listdir(images_dir)):
            type_dir = os.path.join(images_dir, shot_type)
            if not os.path.isdir(type_dir):
                continue
            for name in sorted(os.listdir(type_dir)):
                if not name.lower().endswith('.png'):
                    continue
                path = os.path.join(type_dir, name)
                # A file named .png that will not decode is a broken input, not a listing defect:
                # let it raise and CPython exits 1, which is the code the gate above reserves for
                # "found a defect" and would send an operator off to recompose 24 screenshots.
                try:
                    with Image.open(path) as image:
                        width, height = image.size
                        band_rows, band_min_row_share = leading_band_rows(image, band_color)
                except OSError as exc:
                    print(f"ERROR: cannot measure {path}: {exc}", file=sys.stderr)
                    sys.exit(2)
                records.append({
                    'locale': locale,
                    'type': shot_type,
                    'name': name,
                    'path': os.path.relpath(path, REPO_ROOT).replace('\\', '/'),
                    'width': width,
                    'height': height,
                    # Two ratios, because the two questions asked of them differ. Play's own bound
                    # is on the long edge over the short one and does not care which way up the
                    # image is; whether a carousel shows one shape or two very much does, and a
                    # portrait 1200x2400 beside a landscape 2400x1200 shares 'aspect' 2.0 while
                    # looking nothing alike. 'shape' is width over height for that second question,
                    # the same quantity compose-play-screenshots.py compares in odd_shaped_siblings.
                    'aspect': round(max(width, height) / min(width, height), 4),
                    'shape': round(width / height, 4),
                    'bandRows': band_rows,
                    'bandShare': round(band_rows / height, 4),
                    'bandMinRowShare': band_min_row_share,
                })
    return records


def main():
    parser = argparse.ArgumentParser(description='Measure Play listing screenshot geometry.')
    parser.add_argument('--listing-root', default=DEFAULT_LISTING_ROOT,
                        help='directory holding <locale>/images/<type>/ (default: play/listing)')
    parser.add_argument('--composer', default=DEFAULT_COMPOSER,
                        help='composer whose BAND_COLOR and Play bounds are read')
    args = parser.parse_args()

    if not os.path.isdir(args.listing_root):
        print(f"ERROR: listing root not found: {args.listing_root}", file=sys.stderr)
        sys.exit(2)

    band_color, min_edge, max_edge, max_aspect, composed_types = read_composer_constants(
        args.composer)
    records = measure_tree(args.listing_root, band_color)

    json.dump({
        'listingRoot': args.listing_root.replace('\\', '/'),
        'bandColor': list(band_color),
        'minEdge': min_edge,
        'maxEdge': max_edge,
        'maxAspect': max_aspect,
        'composedTypes': composed_types,
        'images': records,
    }, sys.stdout, indent=2)
    print()


if __name__ == '__main__':
    main()
