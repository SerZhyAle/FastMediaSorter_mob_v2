#!/usr/bin/env python3
"""Compare a camera viewfinder screenshot against the photo the shutter produced.

S1920: the in-app camera promises WYSIWYG - the saved file must show the same field of
view the viewfinder showed at the shutter press. This tool measures whether it does, so the
invariant is checked by a machine instead of by eye.

Method. The screenshot region covering the PreviewView is compared against the captured
photo over a 2-D grid of centre-crop fractions (fx, fy). The pair is scored by zero-mean
normalised cross-correlation on a small grayscale raster, which is invariant to the exposure
and white-balance differences between a screen capture and a JPEG. The best (fx, fy) states
how much of the viewfinder region the photo actually kept.

Both sides are reduced to a central BAND before matching. The camera screen draws its zoom
row, labels and action bar ON TOP of the preview, so a full-region comparison correlates
photographed scene against app chrome and lands on a wrong crop with a low score - measured
at keep_fx=0.740 / correlation=0.25 for a pair that is actually a perfect match (0.98) once
the chrome is excluded.

A weak match is refused rather than reported. Below --min-correlation the geometry is not
determined by the data, and a number printed anyway would read exactly like a measurement.

The crop fractions are searched on BOTH sides, because the defect can point either way: a
photo narrower than the viewfinder, or a viewfinder narrower than the photo.

Known limitation - the verdict may be read as a verdict, the per-axis split may not. When the
photo's SHAPE differs from the viewfinder's, --content-from-photo derives the compared band from
the photo itself, which is the very quantity under test. The pair is still refused - a crop
injected on one axis alone was correctly reported FAIL - but the axis attribution was wrong,
naming 0.740 on both axes for a crop applied only vertically. Read such a result as "these two
disagree", never as "the file lost this much height".

Exit codes:
  0 - comparison completed (and, when --expect-* given, within tolerance)
  1 - comparison completed but outside the expected tolerance
  2 - could not compare (unreadable input, flat scene, or a match too weak to trust)
"""

import argparse
import json
import sys

import numpy as np
from PIL import Image, ImageOps

RASTER = 128
MIN_FRACTION = 0.30
MAX_FRACTION = 1.00
FRACTION_STEP = 0.02
# Below this standard deviation the scene carries no structure to match (lens cap, blank
# wall, pitch dark) and any correlation score is noise.
MIN_CONTRAST = 0.02


def load_oriented(path):
    """Load an image and apply its EXIF rotation."""
    try:
        img = Image.open(path)
    except Exception as exc:  # noqa: BLE001 - surfaced to the caller as exit 2
        raise SystemExit(f"camera_fov_compare: cannot open {path}: {exc}")
    return ImageOps.exif_transpose(img).convert("L")


def match_orientation(img, reference):
    """Rotate `img` a quarter turn when its long axis disagrees with `reference`'s.

    The sensor JPEG is stored landscape while the viewfinder region is portrait (or the other
    way round on a rotated host). Comparing across that mismatch measures nothing.
    """
    img_portrait = img.size[1] >= img.size[0]
    ref_portrait = reference.size[1] >= reference.size[0]
    if img_portrait != ref_portrait:
        return img.rotate(-90, expand=True)
    return img


def central_band(img, fraction):
    """Keep the middle `fraction` of both axes - the part no UI chrome is drawn over."""
    if fraction >= 1.0:
        return img
    w, h = img.size
    bw = max(1, int(w * fraction))
    bh = max(1, int(h * fraction))
    return img.crop(((w - bw) // 2, (h - bh) // 2, (w - bw) // 2 + bw, (h - bh) // 2 + bh))


def content_rect_from_photo(view_size, photo_size):
    """Where a fitCenter preview of `photo_size`'s shape sits inside a view of `view_size`.

    Derived rather than detected. Looking for the letterbox in the pixels assumes the preview is
    textured everywhere and the bars are not, which a flat subject breaks: zoomed onto a plain
    surface the detector reported a 527-pixel band inside a 1440-pixel preview and every result
    keyed off it moved. The stream's shape is already known exactly - the captured file has it.
    """
    vw, vh = view_size
    pw, ph = photo_size
    if pw <= 0 or ph <= 0:
        return (0, vh)
    # The sensor file is landscape while a portrait host shows it upright, so compare like for like.
    if (vh >= vw) != (ph >= pw):
        pw, ph = ph, pw
    scale = min(vw / pw, vh / ph)
    drawn_h = ph * scale
    top = int(round((vh - drawn_h) / 2))
    return (top, int(round(top + drawn_h)))


def detect_content_rows(img, min_row_std=0.04):
    """Return (top, bottom) of the live preview inside a letterboxed view.

    A viewfinder shown with scaleType=fitCenter is letterboxed, and the letterbox is not a
    fixed fraction: it follows whichever aspect the user selected. Measuring against the whole
    view would therefore compare photographed scene against black bars and shift every result.
    The preview is the tallest run of rows that actually carry variance.
    """
    arr = np.asarray(img, dtype=np.float64) / 255.0
    row_std = arr.std(axis=1)
    live = row_std > min_row_std
    best_len, best = 0, None
    start = None
    for i, alive in enumerate(live):
        if alive and start is None:
            start = i
        elif not alive and start is not None:
            if i - start > best_len:
                best_len, best = i - start, (start, i)
            start = None
    if start is not None and len(live) - start > best_len:
        best_len, best = len(live) - start, (start, len(live))
    return best


def crop_fraction(img, fx, fy):
    """Centre-crop `img` keeping fraction fx of its width and fy of its height."""
    w, h = img.size
    cw = max(1, int(round(w * fx)))
    ch = max(1, int(round(h * fy)))
    return img.crop(((w - cw) // 2, (h - ch) // 2, (w - cw) // 2 + cw, (h - ch) // 2 + ch))


def raster(img):
    """Normalise to a fixed-size zero-mean unit-norm vector for correlation."""
    arr = np.asarray(img.resize((RASTER, RASTER), Image.BILINEAR), dtype=np.float64)
    arr -= arr.mean()
    norm = np.linalg.norm(arr)
    if norm < 1e-9:
        return None
    return (arr / norm).ravel()


def contrast_of(img):
    arr = np.asarray(img.resize((RASTER, RASTER), Image.BILINEAR), dtype=np.float64) / 255.0
    return float(arr.std())


def search(moving, fixed_vec, fractions):
    """Find the (fx, fy) centre-crop of `moving` best matching `fixed_vec`."""
    best = (-2.0, 1.0, 1.0)
    for fx in fractions:
        for fy in fractions:
            vec = raster(crop_fraction(moving, fx, fy))
            if vec is None:
                continue
            score = float(np.dot(vec, fixed_vec))
            if score > best[0]:
                best = (score, float(fx), float(fy))
    return best


def main():
    ap = argparse.ArgumentParser(description="Measure viewfinder-vs-photo field-of-view agreement.")
    ap.add_argument("--screenshot", required=True, help="Full-screen PNG captured while the viewfinder was live.")
    ap.add_argument("--photo", required=True, help="The JPEG the shutter produced.")
    ap.add_argument("--region", help="PreviewView CONTENT bounds inside the screenshot: left,top,right,bottom.")
    ap.add_argument("--content-from-photo", action="store_true",
                    help="Derive the letterboxed preview band inside --region from the photo's aspect (preferred).")
    ap.add_argument("--detect-content", action="store_true",
                    help="Find the preview band by looking for texture; fails on a flat subject.")
    ap.add_argument("--band", type=float, default=0.6,
                    help="Central fraction of each side actually compared, excluding UI chrome (default 0.6).")
    ap.add_argument("--min-correlation", type=float, default=0.5,
                    help="Refuse to report a verdict below this correlation (default 0.5).")
    ap.add_argument("--expect-fx", type=float, help="Expected horizontal keep-fraction for a pass.")
    ap.add_argument("--expect-fy", type=float, help="Expected vertical keep-fraction for a pass.")
    ap.add_argument("--tolerance", type=float, default=0.05, help="Allowed absolute deviation (default 0.05).")
    ap.add_argument("--label", default="", help="Free-text label echoed into the result.")
    ap.add_argument("--json", action="store_true", help="Emit the result as a single JSON object.")
    args = ap.parse_args()

    shot = load_oriented(args.screenshot)
    photo = load_oriented(args.photo)

    if args.region:
        try:
            left, top, right, bottom = (int(v) for v in args.region.split(","))
        except ValueError:
            raise SystemExit("camera_fov_compare: --region must be left,top,right,bottom")
        shot = shot.crop((left, top, right, bottom))

    detected = None
    if args.content_from_photo:
        detected = content_rect_from_photo(shot.size, photo.size)
        shot = shot.crop((0, detected[0], shot.size[0], detected[1]))
    elif args.detect_content:
        detected = detect_content_rows(shot)
        if detected is None:
            print("camera_fov_compare: could not find a live preview band in the region", file=sys.stderr)
            return 2
        shot = shot.crop((0, detected[0], shot.size[0], detected[1]))

    photo = match_orientation(photo, shot)

    shot_band = central_band(shot, args.band)
    photo_band = central_band(photo, args.band)

    if contrast_of(shot_band) < MIN_CONTRAST or contrast_of(photo_band) < MIN_CONTRAST:
        # Refusing beats reporting a confident number derived from noise.
        print("camera_fov_compare: scene has too little contrast to compare", file=sys.stderr)
        return 2

    fractions = np.arange(MIN_FRACTION, MAX_FRACTION + 1e-9, FRACTION_STEP)
    photo_vec = raster(photo_band)
    shot_vec = raster(shot_band)
    if photo_vec is None or shot_vec is None:
        print("camera_fov_compare: degenerate image", file=sys.stderr)
        return 2

    # Direction A: the photo kept only part of what the viewfinder showed.
    a_score, a_fx, a_fy = search(shot_band, photo_vec, fractions)
    # Direction B: the viewfinder showed only part of what the photo kept.
    b_score, b_fx, b_fy = search(photo_band, shot_vec, fractions)

    if a_score >= b_score:
        direction, score, fx, fy = "photo-inside-viewfinder", a_score, a_fx, a_fy
    else:
        direction, score, fx, fy = "viewfinder-inside-photo", b_score, b_fx, b_fy

    if score < args.min_correlation:
        print(
            f"camera_fov_compare: best match only correlates {score:.3f} "
            f"(below {args.min_correlation}) - geometry not determined, refusing a verdict",
            file=sys.stderr,
        )
        return 2

    result = {
        "label": args.label,
        "direction": direction,
        "keep_fx": round(fx, 3),
        "keep_fy": round(fy, 3),
        "correlation": round(score, 4),
        "band": args.band,
        "screenshot": args.screenshot,
        "photo": args.photo,
    }
    if detected is not None:
        result["detected_content_rows"] = [int(detected[0]), int(detected[1])]

    status = 0
    if args.expect_fx is not None and args.expect_fy is not None:
        dx = abs(fx - args.expect_fx)
        dy = abs(fy - args.expect_fy)
        result["expect_fx"] = args.expect_fx
        result["expect_fy"] = args.expect_fy
        result["delta_fx"] = round(dx, 3)
        result["delta_fy"] = round(dy, 3)
        within = dx <= args.tolerance and dy <= args.tolerance
        result["verdict"] = "PASS" if within else "FAIL"
        status = 0 if within else 1

    if args.json:
        print(json.dumps(result))
    else:
        print(
            f"{args.label or 'compare'}: direction={direction} "
            f"keep_fx={fx:.3f} keep_fy={fy:.3f} correlation={score:.4f}"
            + (f" verdict={result['verdict']}" if "verdict" in result else "")
        )
    return status


if __name__ == "__main__":
    sys.exit(main())
