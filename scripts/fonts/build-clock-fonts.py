"""Build the launcher clock / watch face typeface subsets (S4562).

The one declaration of the clock typeface set shared by the phone launcher clock
(`ClockDialTypeface`), the watch app (`WearClockTypeface`) and the watch face
(`watchface.xml` time branches). The order is frozen: the index is packed into the
watch face style code, so a new typeface is appended, never inserted.

Usage:
    python build-clock-fonts.py <source-dir> [--repo <repo-root>] [--write-watch-legacy]

<source-dir> holds the google/fonts source files named in TYPEFACES. Requires fontTools.

Writes:
- watchface/src/main/res/font/fms_clock_<wire>.ttf - digits-and-colon subset; the four
  typefaces the face already shipped are left untouched unless --write-watch-legacy.
- app_v2/src/launcherEnabled/res/font/fms_dial_<wire>.ttf - digits, colon, space and the
  AM/PM letters the 12-hour phone clock draws; missing glyphs fall back to the system font.

Every written subset is a Modified Version under the SIL OFL, which forbids it to carry a
Reserved Font Name, so its naming records are rewritten to a neutral "FMS Clock NN" (NN = the
typeface index); the copyright and licence records are kept.

Prints the face size per typeface: the largest px size, capped at 142, at which the widest
reading fits the 437 px the 440 px time group leaves (the S3799 mono measurement).

Exit codes: 0 written, 1 a source file is missing, 2 bad arguments or fontTools absent.
"""

import argparse
import os
import sys

try:
    from fontTools import subset
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer
except ImportError:
    sys.stderr.write("build-clock-fonts: fontTools is not installed\n")
    sys.exit(2)

# (wire name, source file, variable-axis instance or None). Index = list position.
TYPEFACES = [
    ("default", None, None),
    ("condensed", "RobotoCondensed[wght].ttf", {"wght": 700}),
    ("serif", "NotoSerif[wdth,wght].ttf", {"wght": 700, "wdth": 100}),
    ("monospace", "RobotoMono[wght].ttf", {"wght": 700}),
    ("casual", "ComingSoon-Regular.ttf", None),
    ("bebas", "BebasNeue-Regular.ttf", None),
    ("audiowide", "Audiowide-Regular.ttf", None),
    ("lobster", "Lobster-Regular.ttf", None),
    ("pacifico", "Pacifico-Regular.ttf", None),
    ("abril", "AbrilFatface-Regular.ttf", None),
    ("righteous", "Righteous-Regular.ttf", None),
    ("pixel", "PressStart2P-Regular.ttf", None),
    ("sharetech", "ShareTechMono-Regular.ttf", None),
    ("stencil", "BlackOpsOne-Regular.ttf", None),
    ("monoton", "Monoton-Regular.ttf", None),
    ("bungee", "Bungee-Regular.ttf", None),
    ("terminal", "VT323-Regular.ttf", None),
    ("blackletter", "UnifrakturMaguntia-Book.ttf", None),
    ("bangers", "Bangers-Regular.ttf", None),
    ("rubikmono", "RubikMonoOne-Regular.ttf", None),
]

# The face already ships these files under its own legacy names (S3557).
WATCH_LEGACY_NAMES = {"condensed": "condensed", "serif": "serif", "monospace": "mono", "casual": "casual"}

WATCH_TEXT = "0123456789:"
PHONE_TEXT = "0123456789: AMPamp"
MAX_FACE_SIZE = 142
FACE_WIDTH_PX = 437


def load(path, axes):
    font = TTFont(path)
    if axes and "fvar" in font:
        font = instancer.instantiateVariableFont(font, axes)
    return font


def neutralize_names(font, index):
    neutral = "FMS Clock %02d" % index
    name = font["name"]
    name.names = [record for record in name.names if record.nameID not in (16, 17, 21, 25)]
    for record in name.names:
        if record.nameID == 2:
            continue
        if record.nameID in (1, 3, 4):
            record.string = neutral
        elif record.nameID == 6:
            record.string = neutral.replace(" ", "")


def write_subset(font_path, axes, text, out_path, index):
    font = load(font_path, axes)
    options = subset.Options()
    options.layout_features = ["kern", "tnum", "lnum"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(text=text)
    subsetter.subset(font)
    neutralize_names(font, index)
    font.save(out_path)


def widest_em(font, pattern):
    cmap = font.getBestCmap()
    hmtx = font["hmtx"]
    upem = font["head"].unitsPerEm
    digit = max(hmtx[cmap[ord(c)]][0] for c in "0123456789")
    colon = hmtx[cmap[ord(":")]][0]
    digits = sum(1 for c in pattern if c != ":")
    colons = len(pattern) - digits
    return (digit * digits + colon * colons) / upem


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source")
    parser.add_argument("--repo", default=os.path.join(os.path.dirname(__file__), "..", ".."))
    parser.add_argument("--write-watch-legacy", action="store_true")
    args = parser.parse_args()
    watch_dir = os.path.join(args.repo, "watchface", "src", "main", "res", "font")
    phone_dir = os.path.join(args.repo, "app_v2", "src", "launcherEnabled", "res", "font")
    os.makedirs(phone_dir, exist_ok=True)
    missing = [src for _, src, _ in TYPEFACES if src and not os.path.isfile(os.path.join(args.source, src))]
    if missing:
        sys.stderr.write("build-clock-fonts: missing sources: %s\n" % ", ".join(missing))
        return 1
    for index, (wire, src, axes) in enumerate(TYPEFACES):
        if src is None:
            continue
        path = os.path.join(args.source, src)
        watch_name = WATCH_LEGACY_NAMES.get(wire, wire)
        if wire not in WATCH_LEGACY_NAMES or args.write_watch_legacy:
            write_subset(path, axes, WATCH_TEXT, os.path.join(watch_dir, "fms_clock_%s.ttf" % watch_name), index)
        write_subset(path, axes, PHONE_TEXT, os.path.join(phone_dir, "fms_dial_%s.ttf" % wire), index)
        font = load(path, axes)
        sizes = [min(MAX_FACE_SIZE, int(FACE_WIDTH_PX / widest_em(font, p))) for p in ("00:00", "00:00:00")]
        print("%2d %-12s face fms_clock_%-12s hh:mm %3d  hh:mm:ss %3d" % (index, wire, watch_name, sizes[0], sizes[1]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
