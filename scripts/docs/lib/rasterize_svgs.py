#!/usr/bin/env python3
"""S0889 - batch-rasterize currentColor SVGs to PNG for the docs/site icons.

Reads a jobs JSON (path as argv[1]) of the form:
    [{"svg": "<path>", "png": "<path>", "width": 96, "color": "#24292e"}, ...]

For each job it substitutes the SVG's `currentColor` paint with a concrete colour
(markdown <img> icons need a baked colour; the landing keeps currentColor SVGs inline
and is NOT rasterized here) and renders a square PNG. One process renders the whole
batch. Deterministic: same inputs -> same PNG bytes.

Backend: resvg-py (S1964). It ships pip wheels with the Rust renderer compiled in, so
the only dependency is the one this venv declares. The previous backend, cairosvg, has
no native code of its own - cairocffi dlopens a SYSTEM libcairo that arrives on Windows
with GTK or another application, so the pipeline silently depended on the machine and
stopped working on a machine that never had it. Provisioning: scripts/docs/lib/requirements.txt.
"""
import json
import sys

import resvg_py


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
        png = resvg_py.svg_to_bytes(svg_string=svg, width=width, height=width)
        with open(job["png"], "wb") as fh:
            fh.write(bytes(png))
        rendered += 1
    print(f"rasterized {rendered} png(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
