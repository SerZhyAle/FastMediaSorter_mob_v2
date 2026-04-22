#!/usr/bin/env python3
"""
Synthesize VR test media covering every StereoMode variant.

Output goes to c:/Common/test_media/3dvr/<variant>/ — each subfolder contains
files that exercise a specific StereoMode enum value. The generated patterns
use two distinct color fields (red L-eye, green R-eye) so the stereo effect is
immediately visible: if a viewer shows blended/yellow, it is not splitting the
eyes; if the left eye sees red and the right sees green, routing is correct.

Requires Pillow. No FFmpeg dependency.

Covered StereoMode values:
  SBS_FULL, SBS_HALF, OU, MONO,
  EQUIRECT_360_MONO, EQUIRECT_360_SBS, EQUIRECT_360_OU,
  EQUIRECT_180_MONO, EQUIRECT_180_SBS,
  VR180_FISHEYE_SBS, CYLINDER_180
"""
from __future__ import annotations

import math
import os
from pathlib import Path
from typing import Callable, Tuple

from PIL import Image, ImageDraw, ImageFont

ROOT = Path("c:/Common/test_media/3dvr")

# Distinct per-eye fields so a stereo-capable viewer shows red in left eye and
# green in right eye — an incorrect (monoscopic) path shows identical halves.
LEFT_COLOR = (220, 40, 40)
RIGHT_COLOR = (40, 180, 80)
MONO_COLOR = (60, 110, 180)
BG_DARK = (18, 18, 22)


def load_font(size: int) -> ImageFont.ImageFont:
    # Fall back to PIL default bitmap font if TTF not found. Labels are large
    # so readability is fine either way on Quest.
    for name in ("arial.ttf", "DejaVuSans-Bold.ttf", "seguisb.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def draw_eye_panel(
    width: int,
    height: int,
    fill: Tuple[int, int, int],
    label: str,
) -> Image.Image:
    img = Image.new("RGB", (width, height), fill)
    draw = ImageDraw.Draw(img)

    # Grid reference lines so disparity/warping is easy to spot visually.
    grid_step = max(width, height) // 16
    for x in range(0, width, grid_step):
        draw.line([(x, 0), (x, height)], fill=BG_DARK, width=2)
    for y in range(0, height, grid_step):
        draw.line([(0, y), (width, y)], fill=BG_DARK, width=2)

    # Centered crosshair for IPD/alignment checks.
    draw.line([(0, height // 2), (width, height // 2)], fill=(255, 255, 255), width=3)
    draw.line([(width // 2, 0), (width // 2, height)], fill=(255, 255, 255), width=3)
    r = min(width, height) // 12
    cx, cy = width // 2, height // 2
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 255, 255), width=4)

    # Eye label (huge, centered).
    font = load_font(max(48, height // 10))
    tbbox = draw.textbbox((0, 0), label, font=font)
    tw, th = tbbox[2] - tbbox[0], tbbox[3] - tbbox[1]
    draw.text(
        ((width - tw) // 2, (height - th) // 2 - height // 3),
        label,
        fill=(255, 255, 255),
        font=font,
        stroke_width=3,
        stroke_fill=(0, 0, 0),
    )
    return img


def compose_sbs(width: int, height: int) -> Image.Image:
    half_w = width // 2
    left = draw_eye_panel(half_w, height, LEFT_COLOR, "L")
    right = draw_eye_panel(half_w, height, RIGHT_COLOR, "R")
    canvas = Image.new("RGB", (width, height))
    canvas.paste(left, (0, 0))
    canvas.paste(right, (half_w, 0))
    return canvas


def compose_ou(width: int, height: int) -> Image.Image:
    half_h = height // 2
    top = draw_eye_panel(width, half_h, LEFT_COLOR, "L")
    bot = draw_eye_panel(width, half_h, RIGHT_COLOR, "R")
    canvas = Image.new("RGB", (width, height))
    canvas.paste(top, (0, 0))
    canvas.paste(bot, (0, half_h))
    return canvas


def compose_mono(width: int, height: int, label: str = "MONO") -> Image.Image:
    return draw_eye_panel(width, height, MONO_COLOR, label)


def compose_equirect_360_mono(width: int = 4096, height: int = 2048) -> Image.Image:
    """Full-sphere mono: longitude/latitude grid + checkerboard poles."""
    img = Image.new("RGB", (width, height), MONO_COLOR)
    draw = ImageDraw.Draw(img)

    # Latitude bands (every 30°) – color shifts so top pole vs bottom is obvious.
    for deg in range(0, 181, 30):
        y = int(deg / 180 * height)
        draw.line([(0, y), (width, y)], fill=(240, 240, 240), width=3)
    # Longitude meridians (every 30°).
    for deg in range(0, 361, 30):
        x = int(deg / 360 * width)
        draw.line([(x, 0), (x, height)], fill=(240, 240, 240), width=3)

    font = load_font(height // 20)
    for deg in range(0, 361, 90):
        x = int(deg / 360 * width)
        draw.text((x + 10, height // 2 - 40), f"{deg}°", fill=(255, 255, 255), font=font)

    title_font = load_font(height // 12)
    draw.text(
        (width // 2 - 300, height // 2 + 60),
        "EQUIRECT 360° MONO",
        fill=(255, 255, 255),
        font=title_font,
        stroke_width=3,
        stroke_fill=(0, 0, 0),
    )
    return img


def compose_equirect_360_sbs(width: int = 4096, height: int = 1024) -> Image.Image:
    """4:1 AR — left half = left eye full sphere, right half = right eye."""
    half = width // 2
    left = compose_equirect_360_mono(half, height)
    right = compose_equirect_360_mono(half, height)

    draw_l = ImageDraw.Draw(left)
    draw_r = ImageDraw.Draw(right)
    # Tint panels to make L/R obvious.
    tint_l = Image.new("RGB", (half, height), LEFT_COLOR)
    tint_r = Image.new("RGB", (half, height), RIGHT_COLOR)
    left = Image.blend(left, tint_l, 0.45)
    right = Image.blend(right, tint_r, 0.45)

    f = load_font(height // 8)
    ImageDraw.Draw(left).text((half // 2 - 50, 40), "L", fill=(255, 255, 255), font=f, stroke_width=4, stroke_fill=(0, 0, 0))
    ImageDraw.Draw(right).text((half // 2 - 50, 40), "R", fill=(255, 255, 255), font=f, stroke_width=4, stroke_fill=(0, 0, 0))

    canvas = Image.new("RGB", (width, height))
    canvas.paste(left, (0, 0))
    canvas.paste(right, (half, 0))
    return canvas


def compose_equirect_360_ou(width: int = 3840, height: int = 3840) -> Image.Image:
    """1:1 AR — top half = left eye full sphere, bottom half = right eye."""
    half = height // 2
    top = compose_equirect_360_mono(width, half)
    bot = compose_equirect_360_mono(width, half)
    top = Image.blend(top, Image.new("RGB", (width, half), LEFT_COLOR), 0.45)
    bot = Image.blend(bot, Image.new("RGB", (width, half), RIGHT_COLOR), 0.45)

    f = load_font(half // 8)
    ImageDraw.Draw(top).text((60, 40), "L", fill=(255, 255, 255), font=f, stroke_width=4, stroke_fill=(0, 0, 0))
    ImageDraw.Draw(bot).text((60, 40), "R", fill=(255, 255, 255), font=f, stroke_width=4, stroke_fill=(0, 0, 0))

    canvas = Image.new("RGB", (width, height))
    canvas.paste(top, (0, 0))
    canvas.paste(bot, (0, half))
    return canvas


def compose_equirect_180_mono(width: int = 2048, height: int = 2048) -> Image.Image:
    """Front hemisphere only, 1:1 AR."""
    img = compose_equirect_360_mono(width, height)
    draw = ImageDraw.Draw(img)
    f = load_font(height // 12)
    draw.text(
        (width // 2 - 280, height // 4),
        "EQUIRECT 180° MONO",
        fill=(255, 255, 255),
        font=f,
        stroke_width=3,
        stroke_fill=(0, 0, 0),
    )
    return img


def compose_equirect_180_sbs(width: int = 4096, height: int = 2048) -> Image.Image:
    """2:1 AR — each eye covers the front hemisphere."""
    half = width // 2
    left = compose_equirect_180_mono(half, height)
    right = compose_equirect_180_mono(half, height)
    left = Image.blend(left, Image.new("RGB", (half, height), LEFT_COLOR), 0.45)
    right = Image.blend(right, Image.new("RGB", (half, height), RIGHT_COLOR), 0.45)

    f = load_font(height // 8)
    ImageDraw.Draw(left).text((60, 60), "L-180", fill=(255, 255, 255), font=f, stroke_width=4, stroke_fill=(0, 0, 0))
    ImageDraw.Draw(right).text((60, 60), "R-180", fill=(255, 255, 255), font=f, stroke_width=4, stroke_fill=(0, 0, 0))

    canvas = Image.new("RGB", (width, height))
    canvas.paste(left, (0, 0))
    canvas.paste(right, (half, 0))
    return canvas


def compose_vr180_fisheye_sbs(width: int = 4096, height: int = 2048) -> Image.Image:
    """Two circular fisheye projections side-by-side (Google VR180 convention)."""
    half = width // 2
    canvas = Image.new("RGB", (width, height), BG_DARK)
    radius = min(half, height) // 2 - 8
    for eye_index, (ox, color, label) in enumerate(
        [(0, LEFT_COLOR, "L"), (half, RIGHT_COLOR, "R")],
    ):
        cx, cy = ox + half // 2, height // 2
        draw = ImageDraw.Draw(canvas)
        draw.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)
        # Radial grid inside fisheye circle.
        for angle_deg in range(0, 360, 15):
            a = math.radians(angle_deg)
            x2 = cx + int(radius * math.cos(a))
            y2 = cy + int(radius * math.sin(a))
            draw.line([(cx, cy), (x2, y2)], fill=BG_DARK, width=2)
        for ring in (radius // 3, radius * 2 // 3, radius - 4):
            draw.ellipse([cx - ring, cy - ring, cx + ring, cy + ring], outline=BG_DARK, width=3)
        f = load_font(radius // 5)
        draw.text(
            (cx - 40, cy - radius // 6),
            label,
            fill=(255, 255, 255),
            font=f,
            stroke_width=4,
            stroke_fill=(0, 0, 0),
        )
    return canvas


def compose_cylinder_180(width: int = 4096, height: int = 2048) -> Image.Image:
    """Cylindrical 180° panorama — vertical bands + subtle horizon line."""
    img = Image.new("RGB", (width, height), MONO_COLOR)
    draw = ImageDraw.Draw(img)
    band_count = 12
    for i in range(band_count):
        x0 = i * width // band_count
        x1 = (i + 1) * width // band_count
        shade = 160 if i % 2 == 0 else 210
        draw.rectangle([x0, 0, x1, height], fill=(shade, shade - 20, shade - 40))
    draw.line([(0, height // 2), (width, height // 2)], fill=(255, 255, 255), width=4)
    f = load_font(height // 12)
    draw.text(
        (width // 2 - 260, height // 2 + 40),
        "CYLINDER 180°",
        fill=(255, 255, 255),
        font=f,
        stroke_width=3,
        stroke_fill=(0, 0, 0),
    )
    return img


# ──────────────────────────────────────────────────────────────────────────
# Render plan: every entry is (subfolder, filename, factory)
# ──────────────────────────────────────────────────────────────────────────
PLAN: list[tuple[str, str, Callable[[], Image.Image]]] = [
    ("sbs", "synth_SBS_FULL_3840x1080.jpg", lambda: compose_sbs(3840, 1080)),
    ("sbs", "synth_SBS_HALF_1920x1800.jpg", lambda: compose_sbs(1920, 1800)),
    ("ou", "synth_OU_1920x2160.jpg", lambda: compose_ou(1920, 2160)),
    ("mono", "synth_MONO_1920x1080.jpg", lambda: compose_mono(1920, 1080, "MONO 2D")),
    ("360_mono", "synth_EQUIRECT_360_MONO_4096x2048.jpg", lambda: compose_equirect_360_mono(4096, 2048)),
    ("360_sbs", "synth_EQUIRECT_360_SBS_4096x1024.jpg", lambda: compose_equirect_360_sbs(4096, 1024)),
    ("360_ou", "synth_EQUIRECT_360_OU_3840x3840.jpg", lambda: compose_equirect_360_ou(3840, 3840)),
    ("180_mono", "synth_EQUIRECT_180_MONO_2048x2048.jpg", lambda: compose_equirect_180_mono(2048, 2048)),
    ("180_sbs", "synth_EQUIRECT_180_SBS_4096x2048.jpg", lambda: compose_equirect_180_sbs(4096, 2048)),
    ("vr180_fisheye", "synth_VR180_FISHEYE_SBS_4096x2048.jpg", lambda: compose_vr180_fisheye_sbs(4096, 2048)),
    ("cylinder", "synth_CYLINDER_180_4096x2048.jpg", lambda: compose_cylinder_180(4096, 2048)),
]


def main() -> int:
    ROOT.mkdir(parents=True, exist_ok=True)
    written: list[tuple[str, int]] = []
    for sub, name, factory in PLAN:
        folder = ROOT / sub
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / name
        img = factory()
        img.save(path, "JPEG", quality=88, optimize=True)
        written.append((str(path.relative_to(ROOT)), path.stat().st_size))
    for rel, size in written:
        print(f"{rel:60s}  {size/1024:8.1f} KB")
    print(f"\nTotal: {len(written)} files under {ROOT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
