#!/usr/bin/env python
"""Compose localized caption overlays onto raw store screenshots (S0497 Phase 03).

For each locale folder in play/listing/ and each slot in play/listing/captions.json, load the raw
shot temp/play-shots/<slot-id>.png, pad it onto a brand-color canvas constrained to a 2:1 aspect
bound, draw the localized caption as a top band, and write the result to
play/listing/<locale-folder>/images/phoneScreenshots/<NN>.png (NN ordered over present slots).

Play asset constraints enforced: PNG output, each side in [320, 3840], aspect ratio <= 2:1.
Slots whose raw shot is absent are skipped with a warning (manual-pending), not silently swallowed.

Usage:
    python compose-play-screenshots.py
"""
import json
import os
import sys
from PIL import Image, ImageDraw, ImageFont

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..'))
LISTING_ROOT = os.path.join(REPO_ROOT, 'play', 'listing')
CAPTIONS = os.path.join(LISTING_ROOT, 'captions.json')
SHOTS_DIR = os.path.join(REPO_ROOT, 'temp', 'play-shots')

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


def draw_caption(img, text):
    """Draw the caption inside a top band sized to the wrapped text."""
    w, h = img.size
    draw = ImageDraw.Draw(img)
    font = resolve_font(max(28, w // 18))
    lines = text.split('\n')
    spacing = max(6, font.size // 5)
    bbox = draw.multiline_textbbox((0, 0), text, font=font, spacing=spacing, align='center')
    text_w, text_h = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pad_y = font.size
    band_h = text_h + 2 * pad_y
    draw.rectangle([0, 0, w, band_h], fill=BAND_COLOR)
    tx = (w - text_w) // 2 - bbox[0]
    ty = pad_y - bbox[1]
    draw.multiline_text((tx, ty), text, font=font, fill=TEXT_COLOR,
                        spacing=spacing, align='center')
    return img


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

    locales = sorted(slots[0]['captions'].keys())
    total_written = 0
    missing = []

    for locale in locales:
        out_dir = os.path.join(LISTING_ROOT, locale, 'images', 'phoneScreenshots')
        os.makedirs(out_dir, exist_ok=True)
        index = 0
        for slot in slots:
            slot_id = slot['id']
            caption = slot['captions'].get(locale)
            if caption is None:
                print(f"ERROR: {locale}/{slot_id}: missing caption")
                sys.exit(1)
            shot = os.path.join(SHOTS_DIR, f"{slot_id}.png")
            if not os.path.exists(shot):
                if locale == locales[0]:
                    missing.append(slot_id)
                continue
            index += 1
            with Image.open(shot) as raw:
                composed = fit_to_aspect(raw)
                composed = draw_caption(composed, caption)
            label = f"{locale}/{index:02d} ({slot_id})"
            validate_bounds(composed, label)
            out_path = os.path.join(out_dir, f"{index:02d}.png")
            composed.save(out_path, 'PNG')
            print(f"  {label} -> {out_path} ({composed.size[0]}x{composed.size[1]})")
            total_written += 1

    if missing:
        print(f"WARNING: {len(missing)} slot(s) without a raw shot, skipped (manual-pending): "
              + ", ".join(missing))
    if total_written == 0:
        print("ERROR: no screenshots composed (no raw shots under temp/play-shots/).")
        sys.exit(1)
    print(f"\nDONE: composed {total_written} screenshot(s) across {len(locales)} locale(s).")


if __name__ == '__main__':
    main()
