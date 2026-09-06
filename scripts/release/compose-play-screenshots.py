#!/usr/bin/env python
"""Compose localized caption overlays onto raw store screenshots (S0497 Phase 03).

For each locale folder in play/listing/ and each slot in play/listing/captions.json, load the raw
shot temp/play-shots/<locale>/<slot-id>.png (falling back to temp/play-shots/<slot-id>.png when the
locale has no own capture), pad it onto a brand-color canvas constrained to a 2:1 aspect bound, add
the localized caption on a band ABOVE the frame, and write the result to
play/listing/<locale-folder>/images/phoneScreenshots/<NN>.png (NN ordered over present slots).

The band occupies canvas the composer adds, never the screenshot's own pixels, so a composed file is
taller than the frame it carries. Its height follows the canvas height rather than the short edge:
Play allows a tagline at most 20% of the image, and the short-edge rule spent 23% of a landscape
tablet frame against 11% of a portrait phone one, burying the app bar of every tablet screenshot
(S2573). A recomposed slot is therefore not the shape of a slot composed before that change, which
is what the --only geometry warning below reports.

Play asset constraints enforced: PNG output, each side in [320, 3840], aspect ratio <= 2:1.
Slots whose raw shot is absent are skipped with a warning (manual-pending), not silently swallowed.

--tablet reads the separate temp/play-shots-tablet/ tree and writes tenInchScreenshots instead, so a
tablet run can never overwrite or shadow a phone raw shot through resolve_shot()'s flat fallback.

--only <slot-id> composes that slot alone and writes it at its 1-based ordinal in captions.json,
leaving every other file in the output directory alone. A full run numbers its output over the raw
shots that happen to be present, and temp/ is a gitignored scratch area, so a run made after one
fresh capture writes 01.png and leaves a one-file set - which publish-play-listing.py then uploads
over the whole live set, because it deletes all images of a type before uploading (S2398). Such a
run also compares what it wrote against the siblings it left alone and warns when their shapes
disagree, because publishing replaces every image of a type at once and the carousel then shows both.

Usage:
    python compose-play-screenshots.py [--tablet] [--only <slot-id>]

Exit codes: 0 composed; 1 nothing composed, an unknown slot id, or a missing caption/captions file.
"""
import json
import math
import os
import sys
from PIL import Image, ImageDraw, ImageFont

if '--help' in sys.argv or '-h' in sys.argv:
    print(__doc__)
    sys.exit(0)

# Sibling scripts in this folder read sys.argv directly rather than pulling in argparse.
TABLET = '--tablet' in sys.argv


def parse_only(argv):
    """Return the slot id given to --only, or None for a full run."""
    if '--only' not in argv:
        return None
    value_index = argv.index('--only') + 1
    if value_index >= len(argv):
        print("ERROR: --only requires a slot id")
        sys.exit(1)
    return argv[value_index]


ONLY = parse_only(sys.argv)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..'))
LISTING_ROOT = os.path.join(REPO_ROOT, 'play', 'listing')
CAPTIONS = os.path.join(LISTING_ROOT, 'captions.json')
SHOTS_DIR = os.path.join(REPO_ROOT, 'temp',
                         'play-shots-tablet' if TABLET else 'play-shots')
OUT_SUBDIR = 'tenInchScreenshots' if TABLET else 'phoneScreenshots'

# Play phone-screenshot bounds.
MIN_EDGE, MAX_EDGE, MAX_ASPECT = 320, 3840, 2.0

# Brand band + canvas color (dark slate) and caption text color.
BG_COLOR = (17, 21, 28)
BAND_COLOR = (13, 71, 161)
TEXT_COLOR = (255, 255, 255)

FONT_CANDIDATES = [
    r'C:\Windows\Fonts\segoeuib.ttf',
    r'C:\Windows\Fonts\arialbd.ttf',
    r'C:\Windows\Fonts\arial.ttf',
]


def resolve_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    raise FileNotFoundError(
        "No Cyrillic-capable TTF found. Tried: " + "; ".join(FONT_CANDIDATES))


def resolve_shot(locale, slot_id):
    """Locale-specific raw shot wins; the flat path stays as a shared fallback."""
    for candidate in (os.path.join(SHOTS_DIR, locale, f"{slot_id}.png"),
                      os.path.join(SHOTS_DIR, f"{slot_id}.png")):
        if os.path.exists(candidate):
            return candidate
    return None


def fit_to_aspect(img):
    """Pad onto a BG canvas so the result respects MAX_ASPECT (tall->wider canvas)."""
    w, h = img.size
    long_edge, short_edge = max(w, h), min(w, h)
    if long_edge / short_edge <= MAX_ASPECT:
        return img.convert('RGB')
    # Tall screenshot: widen the canvas to long_edge / MAX_ASPECT.
    if h >= w:
        canvas_w, canvas_h = int(round(h / MAX_ASPECT)), h
    else:
        canvas_w, canvas_h = w, int(round(w / MAX_ASPECT))
    canvas = Image.new('RGB', (canvas_w, canvas_h), BG_COLOR)
    canvas.paste(img.convert('RGB'), ((canvas_w - w) // 2, (canvas_h - h) // 2))
    return canvas


def caption_font_size(canvas_h):
    """Font size for a caption on a canvas of this height.

    Off the HEIGHT, not the short edge. A store frame is shown whole, so what a reader perceives is
    the caption's share of the height, and holding that share constant is what makes the tablet and
    the phone caption look alike. The divisor is twice the old one because these frames are about
    twice as tall as they are wide, which leaves the portrait output byte-identical to the short-edge
    rule it replaces while halving the landscape band that rule doubled.
    """
    return max(28, canvas_h // 36)


def compose_with_caption(img, text):
    """Return a new canvas carrying the caption on a band ADDED above the frame.

    The band never overlaps `img`: its height is added to the canvas, so whatever the screenshot
    carries at its top edge survives the compose. Adding height can push a portrait frame past
    MAX_ASPECT, so the canvas widens rather than the band shrinking.
    """
    w, h = img.size
    font = resolve_font(caption_font_size(h))
    spacing = max(6, font.size // 5)
    probe = ImageDraw.Draw(Image.new('RGB', (1, 1)))
    bbox = probe.multiline_textbbox((0, 0), text, font=font, spacing=spacing, align='center')
    text_w, text_h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pad_y = font.size
    band_h = text_h + 2 * pad_y
    out_h = h + band_h
    out_w = max(w, int(math.ceil(out_h / MAX_ASPECT)))

    canvas = Image.new('RGB', (out_w, out_h), BG_COLOR)
    draw = ImageDraw.Draw(canvas)
    draw.rectangle([0, 0, out_w, band_h], fill=BAND_COLOR)
    canvas.paste(img, ((out_w - w) // 2, band_h))
    draw.multiline_text(((out_w - text_w) // 2 - bbox[0], pad_y - bbox[1]), text,
                        font=font, fill=TEXT_COLOR, spacing=spacing, align='center')
    return canvas


def odd_shaped_siblings(out_dir, written_name, written_size):
    """Siblings in out_dir whose aspect differs from the file just written."""
    ratio = written_size[0] / written_size[1]
    odd = []
    for name in sorted(os.listdir(out_dir)):
        if name == written_name or not name.lower().endswith('.png'):
            continue
        with Image.open(os.path.join(out_dir, name)) as sibling:
            if abs(sibling.size[0] / sibling.size[1] - ratio) > 0.01:
                odd.append((name, sibling.size))
    return odd


def validate_bounds(img, label):
    w, h = img.size
    short_edge, long_edge = min(w, h), max(w, h)
    if short_edge < MIN_EDGE or long_edge > MAX_EDGE:
        raise ValueError(f"{label}: size {w}x{h} out of Play bounds [{MIN_EDGE},{MAX_EDGE}]")
    if long_edge / short_edge > MAX_ASPECT + 1e-6:
        raise ValueError(f"{label}: aspect {long_edge / short_edge:.3f} exceeds {MAX_ASPECT}:1")


def main():
    if not os.path.exists(CAPTIONS):
        print(f"ERROR: captions file not found: {CAPTIONS}")
        sys.exit(1)
    with open(CAPTIONS, 'r', encoding='utf-8') as f:
        slots = json.load(f)['slots']

    known_ids = [slot['id'] for slot in slots]
    if ONLY is not None and ONLY not in known_ids:
        print(f"ERROR: unknown slot '{ONLY}'. Known: " + ", ".join(known_ids))
        sys.exit(1)

    locales = sorted(slots[0]['captions'].keys())
    total_written = 0
    missing = []
    mixed = []

    for locale in locales:
        out_dir = os.path.join(LISTING_ROOT, locale, 'images', OUT_SUBDIR)
        os.makedirs(out_dir, exist_ok=True)
        index = 0
        for ordinal, slot in enumerate(slots, start=1):
            slot_id = slot['id']
            if ONLY is not None and slot_id != ONLY:
                continue
            caption = slot['captions'].get(locale)
            if caption is None:
                print(f"ERROR: {locale}/{slot_id}: missing caption")
                sys.exit(1)
            shot = resolve_shot(locale, slot_id)
            if shot is None:
                missing.append(f"{locale}/{slot_id}")
                continue
            index += 1
            # A single-slot run keeps the slot's registry position, so the seven files it does not
            # touch stay addressable; a full run still numbers over the shots actually present.
            out_index = ordinal if ONLY is not None else index
            with Image.open(shot) as raw:
                composed = compose_with_caption(fit_to_aspect(raw), caption)
            label = f"{locale}/{out_index:02d} ({slot_id})"
            validate_bounds(composed, label)
            out_name = f"{out_index:02d}.png"
            out_path = os.path.join(out_dir, out_name)
            composed.save(out_path, 'PNG')
            print(f"  {label} -> {out_path} ({composed.size[0]}x{composed.size[1]})")
            total_written += 1
            # Only a partial run can disagree with the set around it; a full run writes every file.
            if ONLY is not None:
                odd = odd_shaped_siblings(out_dir, out_name, composed.size)
                if odd:
                    mixed.append((out_path, composed.size, odd))

    if missing:
        print(f"WARNING: {len(missing)} slot(s) without a raw shot, skipped (manual-pending): "
              + ", ".join(missing))
    if mixed:
        print("\nWARNING: the set now holds more than one frame shape. Publishing deletes every "
              "image of a type before uploading, so the store carousel will show both.")
        for path, size, odd in mixed:
            siblings = ", ".join(f"{name} {s[0]}x{s[1]}" for name, s in odd)
            print(f"  {path} {size[0]}x{size[1]} against {siblings}")
    if total_written == 0:
        print(f"ERROR: no screenshots composed (no raw shots under {SHOTS_DIR}).")
        sys.exit(1)
    print(f"\nDONE: composed {total_written} screenshot(s) across {len(locales)} locale(s).")


if __name__ == '__main__':
    main()
