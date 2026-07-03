#!/usr/bin/env python3
"""S0889 - batch-rasterize currentColor SVGs to PNG for the docs/site icons.

Reads a jobs JSON (path as argv[1]) of the form:
    [{"svg": "<path>", "png": "<path>", "width": 96, "color": "#24292e"}, ...]

For each job it substitutes the SVG's `currentColor` paint with a concrete colour
(markdown <img> icons need a baked colour; the landing keeps currentColor SVGs inline
and is NOT rasterized here) and renders a square PNG with cairosvg. One process renders
the whole batch. Deterministic: same inputs -> same PNG bytes.
"""
import json
import sys

import cairosvg


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: rasterize_svgs.py <jobs.json>", file=sys.stderr)
        return 2
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        jobs = json.load(fh)
    rendered = 0
    for job in jobs:
        with open(job["svg"], "r", encoding="utf-8") as fh:
            svg = fh.read()
        # currentColor has no intrinsic colour; bake the requested one so the PNG is visible
        # on the (light) doc background. fill="none" stroke-only paths are left untouched.
        svg = svg.replace("currentColor", job["color"])
        width = int(job["width"])
        cairosvg.svg2png(
            bytestring=svg.encode("utf-8"),
            write_to=job["png"],
            output_width=width,
            output_height=width,
        )
        rendered += 1
    print(f"rasterized {rendered} png(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
