#!/usr/bin/env python3
"""
Generate 10 primitive PNG images (solid colours) for testing.

Each image is 256×256 px and saved into the existing
`test_media` directory of the FastMediaSorter project.
"""

from pathlib import Path
from PIL import Image

# ----------------------------------------------------------------------
# Configuration
# ----------------------------------------------------------------------
# Destination folder - relative to this script
DEST_DIR = Path(__file__).resolve().parent.parent / "test_media"

# Image size (width, height)
IMG_SIZE = (512, 512)

# Colours to use – feel free to edit / extend.
COLOURS = [
    ("red",      (255,   0,   0)),
    ("green",    (  0, 255,   0)),
    ("blue",     (  0,   0, 255)),
    ("orange",   (255, 165,   0)),
    ("purple",   (128,   0, 128)),
    ("teal",     (  0, 128, 128)),
    ("yellow",   (255, 255,   0)),
    ("pink",     (255, 105, 180)),
    ("gray",     (128, 128, 128)),
    ("black",    (  0,   0,   0)),
]

# ----------------------------------------------------------------------
def main() -> None:
    # Ensure the destination exists
    DEST_DIR.mkdir(parents=True, exist_ok=True)

    for idx, (name, rgb) in enumerate(COLOURS, start=1):
        img = Image.new("RGB", IMG_SIZE, rgb)
        filename = DEST_DIR / f"Зображення_{idx:02d}_{name}.png"
        img.save(filename, format="PNG")
        print(f"Created {filename}")

if __name__ == "__main__":
    main()
