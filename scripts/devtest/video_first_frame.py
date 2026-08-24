#!/usr/bin/env python3
"""Extract one frame from a recorded clip so video capture can be measured like a photo.

S1986: the owner's report covers video as well as stills, and video fails differently - a clip is
rotated by a matrix in the container rather than by an EXIF tag, so a player shows it upright while
the frames themselves are not. Both halves are reported here: `rotation_metadata` is what the file
ASKS a player to do, and the written PNG is what a player actually shows once it obeyed.

The frame is taken a little way in, never at timestamp zero: the first frames of a recording carry
the auto-exposure ramp, and a comparator matching a black or blown-out frame against a viewfinder
screenshot refuses the pair for want of contrast.

Exit codes:
  0 - a frame was written
  2 - could not read the clip, or it holds no decodable video frame
"""

import argparse
import json
import sys

import av

DEFAULT_OFFSET_SECONDS = 0.5


def rotation_of(stream):
    """Container rotation in degrees clockwise, or 0 when the clip declares none."""
    raw = None
    if stream.metadata:
        raw = stream.metadata.get("rotate")
    if raw is None:
        side = getattr(stream, "side_data", None)
        # PyAV exposes the display matrix as side data on newer builds; older ones only carry the tag.
        if side:
            for entry in side:
                if "DISPLAYMATRIX" in str(entry).upper():
                    raw = str(entry).split()[-1]
                    break
    try:
        return int(round(float(raw))) % 360 if raw is not None else 0
    except (TypeError, ValueError):
        return 0


def main():
    ap = argparse.ArgumentParser(description="Write one frame of a clip as a PNG.")
    ap.add_argument("--video", required=True, help="The recorded clip.")
    ap.add_argument("--out", required=True, help="PNG to write.")
    ap.add_argument("--offset", type=float, default=DEFAULT_OFFSET_SECONDS,
                    help=f"Seconds into the clip (default {DEFAULT_OFFSET_SECONDS}).")
    ap.add_argument("--json", action="store_true", help="Emit the result as a single JSON object.")
    args = ap.parse_args()

    try:
        container = av.open(args.video)
    except Exception as exc:  # noqa: BLE001 - surfaced to the caller as exit 2
        print(f"video_first_frame: cannot open {args.video}: {exc}", file=sys.stderr)
        return 2

    with container:
        streams = container.streams.video
        if not streams:
            print(f"video_first_frame: {args.video} carries no video stream", file=sys.stderr)
            return 2
        stream = streams[0]
        rotation = rotation_of(stream)
        chosen = None
        for frame in container.decode(stream):
            chosen = frame
            # PyAV already applies the display matrix when converting to an image, so the written PNG
            # is what a player shows - the same thing a viewfinder screenshot shows.
            if frame.time is not None and frame.time >= args.offset:
                break
        if chosen is None:
            print(f"video_first_frame: {args.video} holds no decodable frame", file=sys.stderr)
            return 2
        image = chosen.to_image()
        image.save(args.out)
        result = {
            "video": args.video,
            "frame": args.out,
            "rotation_metadata": rotation,
            "frame_width": image.size[0],
            "frame_height": image.size[1],
            "time": round(float(chosen.time or 0.0), 3),
        }

    if args.json:
        print(json.dumps(result))
    else:
        print(f"video_first_frame: {args.out} at {result['time']}s, "
              f"{result['frame_width']}x{result['frame_height']}, rotation={rotation}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
