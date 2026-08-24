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

Rotation is measured, not repaired. S1986: the tool used to rotate the photo a quarter turn
whenever its long axis disagreed with the region's, which corrected a wrongly rotated file before
measuring it and reported a confident field-of-view number with no mention of the turn - one of the
two reasons a sweep could report PASS on every cell while the owner was looking at a wrong photo.
Every quarter turn is now scored, the winner is reported as `rotation_deg`, and 180 degrees is among
the candidates because an upside-down frame has the identical shape and no long-axis rule can see it.

The two sides are matched as a uniform scale, never as a stretch (S1986). Two rectangular crops of
one sensor image differ by a scale and a shift, so only the height fraction is searched and the width
follows from the shape. The old free per-axis search is still reported, as `free_keep_*`, because it
is a useful hint that something is wrong - but it must not decide, since a stretch can absorb a shape
error and name a plausible number for it.

Known limitation - the verdict may be read as a verdict, the per-axis split may not. When the
photo's SHAPE differs from the viewfinder's, --content-from-photo derives the compared band from
the photo itself, which is the very quantity under test: a file cropped top and bottom then defines
its own expectation and agrees with it. Pass --content-from-aspect instead, which derives the band
from the shape the UI was set to. With the circular option the pair is still refused - a crop
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
EXIF_TAG_ORIENTATION = 274
QUARTER_TURNS = (0, 90, 180, 270)
# Coarse grid used only to pick the quarter turn. Fine enough to rank four rotations apart, cheap
# enough that trying all four costs a fraction of one fine search.
COARSE_FRACTIONS = (0.5, 0.7, 0.85, 1.0)
# Below this standard deviation the scene carries no structure to match (lens cap, blank
# wall, pitch dark) and any correlation score is noise.
MIN_CONTRAST = 0.02


def load_oriented(path):
    """Load an image as a viewer shows it - EXIF rotation applied."""
    try:
        img = Image.open(path)
    except Exception as exc:  # noqa: BLE001 - surfaced to the caller as exit 2
        raise SystemExit(f"camera_fov_compare: cannot open {path}: {exc}")
    return ImageOps.exif_transpose(img).convert("L")


def exif_orientation_of(path):
    """The raw TAG_ORIENTATION of a JPEG, or None when it carries none.

    Reported beside the measured rotation because the two answer different questions: the tag says
    what the file ASKS a viewer to do, the measurement says what the pixels actually show once the
    viewer obeyed. A capture pipeline can be wrong in either half alone.
    """
    try:
        with Image.open(path) as img:
            exif = img.getexif()
        return int(exif.get(EXIF_TAG_ORIENTATION)) if exif and EXIF_TAG_ORIENTATION in exif else None
    except Exception:  # noqa: BLE001 - a missing tag is data, not a failure
        return None


def rotated(img, degrees):
    """Rotate clockwise by a multiple of 90, expanding the canvas."""
    if degrees % 360 == 0:
        return img
    # PIL rotates counter-clockwise, so a clockwise request is its negation.
    return img.rotate(-(degrees % 360), expand=True)


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


def content_rect_from_aspect(view_size, stream_long_over_short):
    """Where a fitCenter preview of a stream of `stream_long_over_short` sits inside the view.

    The non-circular sibling of `content_rect_from_photo`. The band is derived from the shape the
    UI was SET to, which is an input to the capture, instead of from the shape the file came out
    with, which is the quantity under test. Deriving it from the photo makes a file cropped top and
    bottom define its own expectation and agree with it - the very reading that let a sweep report
    PASS on every cell while the owner was looking at a cropped frame.

    A stream at least as wide as the view leaves no letterbox at all (the full-screen selection
    fills the view by cropping the stream), so the band is the whole region.
    """
    vw, vh = view_size
    if vw <= 0 or vh <= 0 or stream_long_over_short <= 0:
        return (0, vh)
    # Inside a portrait view the stream's short edge is the width, so the drawn height is the width
    # times the ratio; inside a landscape view the letterbox falls on the sides and never on rows.
    drawn_h = vw * stream_long_over_short if vh >= vw else vh
    if drawn_h >= vh:
        return (0, vh)
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


def search_similar(container, contained_size, fixed_vec, fractions):
    """Best sub-rectangle of `container` whose SHAPE is that of `contained_size`.

    S1986: the honest model. Two rectangular crops of the same sensor image are related by a uniform
    scale and a shift - never by a stretch, because the pixels are square in both. Searching the two
    axes independently, as `search` does, grants a stretch the physics does not, and the winner is
    then whichever squash happens to line the scene up: measured on one pair as (1.0, 0.8) where the
    truth was a uniform 0.8, and on another as (1.0, 0.56) for a photo whose shape had been turned.
    Here one free parameter is searched - the height fraction - and the width follows from the shape,
    so a reported pair can be read as "this much of the viewfinder survived".

    Returns (score, fx, fy). A shape that cannot fit at any fraction returns a score of -2.
    """
    cw, ch = container.size
    pw, ph = contained_size
    if cw <= 0 or ch <= 0 or pw <= 0 or ph <= 0:
        return (-2.0, 1.0, 1.0)
    best = (-2.0, 1.0, 1.0)
    for fy in fractions:
        rect_h = ch * fy
        rect_w = rect_h * (pw / ph)
        if rect_w > cw:
            continue
        fx = rect_w / cw
        vec = raster(crop_fraction(container, fx, fy))
        if vec is None:
            continue
        score = float(np.dot(vec, fixed_vec))
        if score > best[0]:
            best = (score, float(fx), float(fy))
    return best


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


def best_score_at(photo_band, shot_band, fractions):
    """Best correlation between the two bands over both crop directions."""
    photo_vec = raster(photo_band)
    shot_vec = raster(shot_band)
    if photo_vec is None or shot_vec is None:
        return -2.0
    a_score, _, _ = search_similar(shot_band, photo_band.size, photo_vec, fractions)
    b_score, _, _ = search_similar(photo_band, shot_band.size, shot_vec, fractions)
    return max(a_score, b_score)


def best_quarter_turn(photo, shot_band, band):
    """Which clockwise quarter turn of `photo` lines up with what the viewfinder showed.

    Measured, never assumed. The old code rotated the photo whenever its long axis disagreed with
    the region's, which silently repaired exactly the defect the owner reports - a file saved at the
    wrong rotation arrived at the comparator already corrected, and no field of the result mentioned
    that a turn had been applied. Here the turn is the answer, and 180 degrees - invisible to any
    long-axis rule, because an upside-down frame has the identical shape - is one of the candidates.
    """
    best = (-2.0, 0)
    for turn in QUARTER_TURNS:
        candidate = central_band(rotated(photo, turn), band)
        score = best_score_at(candidate, shot_band, COARSE_FRACTIONS)
        if score > best[0]:
            best = (score, turn)
    return best[1], best[0]


def main():
    ap = argparse.ArgumentParser(description="Measure viewfinder-vs-photo field-of-view agreement.")
    ap.add_argument("--screenshot", required=True, help="Full-screen PNG captured while the viewfinder was live.")
    ap.add_argument("--photo", required=True, help="The JPEG the shutter produced.")
    ap.add_argument("--region", help="PreviewView CONTENT bounds inside the screenshot: left,top,right,bottom.")
    ap.add_argument("--content-from-photo", action="store_true",
                    help="Derive the letterboxed preview band inside --region from the photo's aspect "
                         "(circular - the photo's shape is under test; prefer --content-from-aspect).")
    ap.add_argument("--content-from-aspect", type=float, metavar="LONG_OVER_SHORT",
                    help="Derive the preview band from the stream aspect the UI was SET to "
                         "(e.g. 1.7778 for 16:9, 1.3333 for 4:3, 0 for a view-filling selection).")
    ap.add_argument("--expect-rotation", type=int, choices=list(QUARTER_TURNS),
                    help="Clockwise turn the photo must need to line up with the viewfinder. "
                         "Any other measured turn is a FAIL.")
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
    if args.content_from_aspect is not None:
        detected = content_rect_from_aspect(shot.size, args.content_from_aspect)
        shot = shot.crop((0, detected[0], shot.size[0], detected[1]))
    elif args.content_from_photo:
        detected = content_rect_from_photo(shot.size, photo.size)
        shot = shot.crop((0, detected[0], shot.size[0], detected[1]))
    elif args.detect_content:
        detected = detect_content_rows(shot)
        if detected is None:
            print("camera_fov_compare: could not find a live preview band in the region", file=sys.stderr)
            return 2
        shot = shot.crop((0, detected[0], shot.size[0], detected[1]))

    shot_band = central_band(shot, args.band)
    turn, turn_score = best_quarter_turn(photo, shot_band, args.band)
    photo = rotated(photo, turn)
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
    a_score, a_fx, a_fy = search_similar(shot_band, photo_band.size, photo_vec, fractions)
    # Direction B: the viewfinder showed only part of what the photo kept.
    b_score, b_fx, b_fy = search_similar(photo_band, shot_band.size, shot_vec, fractions)
    # The unconstrained search, reported but never used for the verdict. It is what an eye does when
    # it stretches one picture onto another, and it scores higher on a pair that genuinely disagrees -
    # which is exactly why it must not decide: it can hide a shape error inside a stretch.
    free_score, free_fx, free_fy = search(shot_band, photo_vec, fractions)

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
        "rotation_deg": turn,
        "rotation_correlation": round(turn_score, 4),
        "exif_orientation": exif_orientation_of(args.photo),
        "keep_fx": round(fx, 3),
        "keep_fy": round(fy, 3),
        "correlation": round(score, 4),
        "band": args.band,
        "free_keep_fx": round(free_fx, 3),
        "free_keep_fy": round(free_fy, 3),
        "free_correlation": round(free_score, 4),
        "screenshot": args.screenshot,
        "photo": args.photo,
    }
    if detected is not None:
        result["detected_content_rows"] = [int(detected[0]), int(detected[1])]

    status = 0
    failures = []
    if args.expect_rotation is not None:
        result["expect_rotation"] = args.expect_rotation
        if turn != args.expect_rotation:
            failures.append(f"rotation {turn} != expected {args.expect_rotation}")
    if args.expect_fx is not None and args.expect_fy is not None:
        dx = abs(fx - args.expect_fx)
        dy = abs(fy - args.expect_fy)
        result["expect_fx"] = args.expect_fx
        result["expect_fy"] = args.expect_fy
        result["delta_fx"] = round(dx, 3)
        result["delta_fy"] = round(dy, 3)
        if dx > args.tolerance or dy > args.tolerance:
            failures.append(f"keep ({fx:.2f},{fy:.2f}) != expected ({args.expect_fx},{args.expect_fy})")
    if args.expect_rotation is not None or args.expect_fx is not None:
        result["verdict"] = "PASS" if not failures else "FAIL"
        if failures:
            result["failures"] = failures
        status = 0 if not failures else 1

    if args.json:
        print(json.dumps(result))
    else:
        print(
            f"{args.label or 'compare'}: direction={direction} rotation={turn} "
            f"keep_fx={fx:.3f} keep_fy={fy:.3f} correlation={score:.4f}"
            + (f" verdict={result['verdict']}" if "verdict" in result else "")
        )
    return status


if __name__ == "__main__":
    sys.exit(main())
