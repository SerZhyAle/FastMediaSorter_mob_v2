#!/usr/bin/env python3
"""S3433 - measure the drawing style of glyph SVGs (ICON-RENDER 0.10 section 10, item A).

usage: measure_glyph_style.py <jobs.json> <out.json>

Reads a jobs JSON of the form
    [{"name": "ic_play", "svg": "<path>", "viewportW": 24, "viewportH": 24}, ...]
renders each SVG with resvg-py at 10 px per unit of a 24-unit-wide grid (currentColor baked to black),
decodes the PNG with the standard library, and writes a JSON array, sorted by name, of
    {"name", "inkBox": [x0, y0, x1, y1], "boxCentre": [dx, dy], "massCentre": [dx, dy],
     "weight", "coverage", "grid": [w, h]}
in grid units; the centres are offsets from the grid centre. weight is 2 x inked area / ink perimeter,
an estimate of line width; coverage is the inked share of the grid in percent. A glyph with no ink
gets "inkBox": null.

Deterministic: same inputs -> same bytes. Only dependency: resvg-py (scripts/docs/lib/requirements.txt);
the PNG is decoded here so Pillow is not needed.

Exit codes:
  0 - every job was measured and the output written.
  2 - bad arguments, an unreadable jobs file, or an SVG resvg cannot render (named on stderr).
"""
import json
import struct
import sys
import zlib

PX = 10
ALPHA_INK = 128


def decode_rgba(png: bytes):
    """Decode an 8-bit RGBA, non-interlaced PNG into (width, height, alpha rows)."""
    if png[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    pos, width, height, idat = 8, 0, 0, []
    while pos < len(png):
        length = struct.unpack(">I", png[pos:pos + 4])[0]
        kind = png[pos + 4:pos + 8]
        data = png[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, depth, colour, _, _, interlace = struct.unpack(">IIBBBBB", data)
            if depth != 8 or colour != 6 or interlace != 0:
                raise ValueError("expected 8-bit RGBA non-interlaced")
        elif kind == b"IDAT":
            idat.append(data)
        elif kind == b"IEND":
            break
    raw = zlib.decompress(b"".join(idat))
    stride, bpp = width * 4, 4
    prev = bytearray(stride)
    alpha = []
    for y in range(height):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 0xFF
            elif f == 2:
                line[i] = (line[i] + b) & 0xFF
            elif f == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 0xFF
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        alpha.append(bytes(line[3::4]))
        prev = line
    return width, height, alpha


def measure(job):
    import resvg_py

    vw = float(job["viewportW"])
    vh = float(job["viewportH"])
    grid_h = 24.0 * vh / vw
    with open(job["svg"], encoding="utf-8") as fh:
        svg = fh.read().replace("currentColor", "#000000")
    w, h = int(round(24 * PX)), int(round(grid_h * PX))
    width, height, alpha = decode_rgba(bytes(resvg_py.svg_to_bytes(svg_string=svg, width=w, height=h)))
    area = 0.0
    sx = sy = 0.0
    x0, y0, x1, y1 = width, height, -1, -1
    ink = [[v >= ALPHA_INK for v in row] for row in alpha]
    perim = 0
    for y in range(height):
        row = alpha[y]
        for x in range(width):
            v = row[x]
            if v:
                wgt = v / 255.0
                area += wgt
                sx += wgt * (x + 0.5)
                sy += wgt * (y + 0.5)
            if ink[y][x]:
                x0, y0, x1, y1 = min(x0, x), min(y0, y), max(x1, x), max(y1, y)
                if (x == 0 or not ink[y][x - 1] or x == width - 1 or not ink[y][x + 1]
                        or y == 0 or not ink[y - 1][x] or y == height - 1 or not ink[y + 1][x]):
                    perim += 1
    result = {"name": job["name"], "grid": [24.0, round(grid_h, 1)]}
    if x1 < 0:
        result.update({"inkBox": None, "boxCentre": None, "massCentre": None, "weight": 0.0, "coverage": 0.0})
        return result
    box = [x0 / PX, y0 / PX, (x1 + 1) / PX, (y1 + 1) / PX]
    area_u = area / (PX * PX)
    result.update({
        "inkBox": [round(v, 1) for v in box],
        "boxCentre": [round((box[0] + box[2]) / 2 - 12, 1), round((box[1] + box[3]) / 2 - grid_h / 2, 1)],
        "massCentre": [round(sx / area / PX - 12, 1), round(sy / area / PX - grid_h / 2, 1)],
        "weight": round(2 * area_u / (perim / PX), 2) if perim else 0.0,
        "coverage": round(100 * area_u / (24 * grid_h), 1),
    })
    return result


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: measure_glyph_style.py <jobs.json> <out.json>", file=sys.stderr)
        return 2
    try:
        with open(sys.argv[1], encoding="utf-8") as fh:
            jobs = sorted(json.load(fh), key=lambda j: j["name"])
    except (OSError, ValueError, KeyError) as exc:
        print(f"measure_glyph_style: cannot read jobs: {exc}", file=sys.stderr)
        return 2
    out = []
    for job in jobs:
        try:
            out.append(measure(job))
        # resvg-py raises its own error types; any job that cannot be measured makes the run unverifiable.
        except Exception as exc:  # noqa: BLE001
            print(f"measure_glyph_style: {job.get('name')}: {exc}", file=sys.stderr)
            return 2
    with open(sys.argv[2], "w", encoding="utf-8", newline="\n") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
