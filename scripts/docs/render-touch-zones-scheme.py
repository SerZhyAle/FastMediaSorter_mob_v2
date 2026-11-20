#!/usr/bin/env python
"""Render the touch zones scheme of the playback settings from this source (PRODUCT-IDENTITY rule 12, S4693).

The picture is the 3x3 grid of full-screen touch zones numbered 1..9, shown beside the
"Touch Zones Scheme" setting. It used to be a hand-made 256 px raster with no source; this script is
its source now and the PNG is a render target. Output is deterministic (no PNG metadata, fixed
compression), so --check can compare it with the file on disk.

Usage:
    py -3 scripts/docs/render-touch-zones-scheme.py [--check]

Exit codes:
    0   written, or with --check the file on disk equals the render.
    1   --check found the file missing or different.
    2   the font is missing.
"""
import io
import os
import sys

from PIL import Image, ImageDraw, ImageFont

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(REPO, 'app_v2', 'src', 'main', 'res', 'drawable', 'touch_zones_scheme.png')
FONT = 'C:/Windows/Fonts/segoeui.ttf'
SIZE = 256
GRID = (96, 96, 96)
TEXT = (17, 17, 17)
# One hue per zone, the colours the setting has always shown, so users who learned the zones keep them.
ZONES = [
    (196, 104, 96), (100, 158, 156), (190, 170, 92),
    (100, 158, 156), (150, 150, 150), (100, 158, 156),
    (148, 116, 182), (196, 104, 96), (204, 140, 92),
]


def render():
    if not os.path.exists(FONT):
        raise FileNotFoundError(FONT)
    img = Image.new('RGB', (SIZE, SIZE), GRID)
    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(FONT, 52)
    cell = SIZE / 3
    for i, colour in enumerate(ZONES):
        col, row = i % 3, i // 3
        box = (round(col * cell) + 1, round(row * cell) + 1, round((col + 1) * cell) - 2, round((row + 1) * cell) - 2)
        draw.rectangle(box, fill=colour)
        label = str(i + 1)
        tb = draw.textbbox((0, 0), label, font=font)
        cx = (box[0] + box[2]) / 2 - (tb[2] - tb[0]) / 2 - tb[0]
        cy = (box[1] + box[3]) / 2 - (tb[3] - tb[1]) / 2 - tb[1]
        draw.text((round(cx), round(cy)), label, font=font, fill=TEXT)
    buf = io.BytesIO()
    img.save(buf, format='PNG', optimize=False, compress_level=9)
    return buf.getvalue()


def main():
    try:
        data = render()
    except FileNotFoundError as e:
        print(f'render-touch-zones-scheme: COULD NOT VERIFY (missing font {e})')
        return 2
    if '--check' in sys.argv:
        same = os.path.exists(OUT) and open(OUT, 'rb').read() == data
        print('render-touch-zones-scheme: ' + ('PASS (matches)' if same else 'FAIL (differs from its source; rerun)'))
        return 0 if same else 1
    with open(OUT, 'wb') as f:
        f.write(data)
    print('render-touch-zones-scheme: PASS (written)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
