#!/usr/bin/env python
"""Build one listing-tree fixture for the geometry gate's regression suite (S2764).

Every fixture is derived from a REAL wear screenshot in play/listing rather than from a drawn
square, so the clean case is the live corpus and each defective case differs from it in exactly the
one property under test. A hand-drawn fixture would let the gate pass on the drawing while still
failing on a real screenshot, which is the failure a regression suite exists to prevent.

The frame fixture shrinks the content into a smaller circle and fills the rim with a flat bezel -
which is what a device frame is: a band around the round content that carries no UI at all. Its
bezel is deliberately BRIGHTER than the dim content in one variant, because that is the case the
brightness-step criterion got wrong and the width criterion gets right (S2764 3.2).

Usage:
    python make-fixture.py <kind> <out-dir>

    kind: clean | framed | framed-narrow | transparent

Exit codes:
    0 - the fixture was written; its directory holds <locale>/images/wearScreenshots/*.png.
    2 - could not build it: Pillow absent, the source screenshot missing, or an unknown kind.
"""
import os
import shutil
import sys

try:
    from PIL import Image, ImageDraw
except ImportError as exc:  # pragma: no cover - environment failure, not a fixture defect
    print(f"ERROR: cannot build a fixture without Pillow: {exc}", file=sys.stderr)
    sys.exit(2)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..', '..'))
SOURCE_DIR = os.path.join(REPO_ROOT, 'play', 'listing', 'en-US', 'images', 'wearScreenshots')


def target_dir(out_root):
    path = os.path.join(out_root, 'en-US', 'images', 'wearScreenshots')
    os.makedirs(path, exist_ok=True)
    return path


def source(name):
    path = os.path.join(SOURCE_DIR, name)
    if not os.path.exists(path):
        print(f"ERROR: source screenshot not found: {path}", file=sys.stderr)
        sys.exit(2)
    return Image.open(path)


def write_clean(out_root):
    """Every live wear frame, copied byte for byte - the gate must pass on the real corpus."""
    dest = target_dir(out_root)
    for name in sorted(os.listdir(SOURCE_DIR)):
        if name.lower().endswith('.png'):
            shutil.copy2(os.path.join(SOURCE_DIR, name), os.path.join(dest, name))


def write_framed(out_root, scale, bezel):
    src = source('04.png').convert('RGB')
    width, height = src.size
    small = src.resize((int(width * scale), int(height * scale)))
    box = [(width - small.width) // 2, (height - small.height) // 2,
           (width + small.width) // 2, (height + small.height) // 2]
    inner = Image.new('RGB', (width, height), bezel)
    inner.paste(small, (box[0], box[1]))
    mask = Image.new('L', (width, height), 0)
    ImageDraw.Draw(mask).ellipse(box, fill=255)
    framed = Image.composite(inner, Image.new('RGB', (width, height), bezel), mask)
    framed.save(os.path.join(target_dir(out_root), '01.png'))


def write_transparent(out_root):
    """A frame with both fully and partially transparent pixels - Play forbids either."""
    src = source('02.png').convert('RGBA')
    pixels = src.load()
    for y in range(0, 40):
        for x in range(0, 40):
            r, g, b, _ = pixels[x, y]
            pixels[x, y] = (r, g, b, 0)
    for y in range(40, 60):
        for x in range(0, 40):
            r, g, b, _ = pixels[x, y]
            pixels[x, y] = (r, g, b, 128)
    src.save(os.path.join(target_dir(out_root), '02.png'))


def main():
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
    kind, out_root = sys.argv[1], sys.argv[2]
    if kind == 'clean':
        write_clean(out_root)
    elif kind == 'framed':
        # A bezel brighter than the dim content: the case that inverts a brightness-step criterion.
        write_framed(out_root, 0.78, (35, 35, 35))
    elif kind == 'framed-narrow':
        write_framed(out_root, 0.97, (16, 16, 16))
    elif kind == 'transparent':
        write_clean(out_root)
        write_transparent(out_root)
    else:
        print(f"ERROR: unknown fixture kind: {kind}", file=sys.stderr)
        sys.exit(2)
    print(f"fixture '{kind}' written under {out_root}")


if __name__ == '__main__':
    main()
