#!/usr/bin/env python
"""Generate a store-safe synthetic media corpus for Play listing screenshots (S2602).

Store screenshots are shown to an outside audience, so they may not be staged with
`c:\\Common\\test_media`: that corpus is real personal material, including a signed consent form
naming a real person and a commercial book whose title the reader renders from the file's own
contents rather than from its name (S1991). Everything this script writes is generated from
arithmetic - no photograph, no recording, no third-party work - so any frame of it may be published.

Output lands under temp/store-shot-media/ in four folders, one per media kind the eight listing
slots display:

    Photos/   images for the image viewer and the image-folder tiles
    Videos/   one H.264/AAC MP4 for the video player
    Music/    MP3 files carrying ID3 title/artist/album and embedded cover art
    Books/    one PDF and one EPUB for the reader

The artwork is the product's own signature visual - drifting wave fields with particles - rather
than a test pattern, because these frames end up in a store listing where a colour-bar chart would
read as an unfinished build.

ffmpeg is resolved through an ordered candidate list the way compose-play-screenshots.py resolves
its font. It is not a developer tool on this machine; it currently exists only inside an unrelated
application's install directory, so hard-coding one path would break on the next machine.

The PDF and the EPUB are written by hand from the standard library because neither reportlab nor a
packaging library is installed, and adding a dependency to publish two fixture files is not worth it.

Usage:
    python store_shot_media.py [--out <dir>] [--clean]

Exit codes: 0 corpus written; 1 a generation step failed; 2 no usable ffmpeg found.
"""
import math
import os
import shutil
import subprocess
import sys
import wave
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..'))
DEFAULT_OUT = os.path.join(REPO_ROOT, 'temp', 'store-shot-media')

# Every random draw is seeded, so a rerun reproduces the same corpus and a recapture of one slot
# matches the frames captured around it.
SEED = 20260906

FFMPEG_CANDIDATES = [
    os.environ.get('FMS_FFMPEG'),
    'ffmpeg',
    r'C:\Program Files\Virtual Desktop Streamer\ffmpeg.exe',
    r'C:\Program Files\ffmpeg\bin\ffmpeg.exe',
    r'C:\ProgramData\chocolatey\bin\ffmpeg.exe',
    '/usr/bin/ffmpeg',
]

FONT_CANDIDATES = [
    r'C:\Windows\Fonts\segoeuib.ttf',
    r'C:\Windows\Fonts\arialbd.ttf',
    r'C:\Windows\Fonts\arial.ttf',
]

# Palettes are named for what they render, so a file name describes its own picture honestly.
PALETTES = [
    ('Coral drift', ((14, 18, 30), (196, 62, 84), (250, 176, 116))),
    ('Deep harbour', ((8, 14, 28), (18, 88, 158), (92, 210, 226))),
    ('Meadow dusk', ((12, 22, 18), (46, 132, 92), (214, 226, 128))),
    ('Amber field', ((26, 16, 10), (168, 96, 24), (248, 206, 122))),
    ('Violet tide', ((16, 12, 30), (96, 52, 168), (208, 152, 244))),
    ('Slate bloom', ((14, 16, 22), (72, 96, 128), (188, 212, 236))),
    ('Ember ridge', ((22, 10, 12), (152, 40, 52), (244, 148, 96))),
    ('Cyan shelf', ((8, 20, 26), (16, 122, 132), (146, 232, 224))),
    ('Rose quartz', ((24, 14, 20), (170, 70, 118), (246, 186, 208))),
    ('Northern ice', ((10, 18, 30), (44, 104, 176), (198, 232, 248))),
]

TRACKS = [
    ('Morning Signal', 'Wavelength', 'Open Frequencies', 220.0),
    ('Quiet Harbour', 'Wavelength', 'Open Frequencies', 196.0),
    ('Paper Lanterns', 'Field Notes', 'Slow Rooms', 174.6),
    ('Long Shadows', 'Field Notes', 'Slow Rooms', 146.8),
]


def fail(message, code=1):
    print(f"ERROR: {message}")
    sys.exit(code)


def resolve_ffmpeg():
    """First candidate that exists and answers -version, or exit 2 naming every path tried."""
    tried = []
    for candidate in FFMPEG_CANDIDATES:
        if not candidate:
            continue
        path = shutil.which(candidate) if os.sep not in candidate else candidate
        tried.append(candidate)
        if path and os.path.exists(path):
            try:
                subprocess.run([path, '-version'], capture_output=True, check=True)
                return path
            except (subprocess.CalledProcessError, OSError):
                continue
    print("ERROR: no usable ffmpeg found. Tried: " + "; ".join(tried))
    print("Set FMS_FFMPEG to an ffmpeg binary, or install one on PATH.")
    sys.exit(2)


def resolve_font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def run(ffmpeg, args, label):
    result = subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-y'] + args,
                            capture_output=True, text=True)
    if result.returncode != 0:
        fail(f"{label}: ffmpeg exited {result.returncode}\n{result.stderr.strip()}")


def wave_field(width, height, palette, phase, rng):
    """Render the product's signature visual: summed travelling waves, then particles over them.

    Done in numpy over the whole array rather than per pixel because a 2400x1600 still takes
    minutes pixel by pixel and under a second vectorised, and this runs once per generated frame.
    """
    low, mid, high = (np.array(c, dtype=np.float64) for c in palette)
    yy, xx = np.mgrid[0:height, 0:width]
    u = xx / width
    v = yy / height

    field = np.zeros((height, width), dtype=np.float64)
    for harmonic in range(1, 5):
        angle = phase * (0.6 + 0.27 * harmonic)
        field += np.sin((u * 5.5 * harmonic + v * 2.1 * harmonic + angle)) / harmonic
    field += 0.7 * np.sin((u - v) * 7.3 + phase * 1.4)
    # np.ptp(), not field.ptp(): NumPy 2.0 removed the ndarray method and this venv carries 2.5.
    field = (field - field.min()) / max(float(np.ptp(field)), 1e-6)

    # Two-stop ramp: the darker half of the range goes low->mid, the brighter half mid->high, which
    # keeps a deep background instead of washing the whole frame toward the accent colour.
    t = field[..., None]
    lower = low + (mid - low) * np.clip(t * 2.0, 0.0, 1.0)
    upper = mid + (high - mid) * np.clip(t * 2.0 - 1.0, 0.0, 1.0)
    rgb = np.where(t < 0.5, lower, upper)

    # A vertical falloff so the top of the frame stays dark enough for the caption band to sit on.
    rgb *= (0.55 + 0.45 * v)[..., None]

    canvas = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), 'RGB')
    draw = ImageDraw.Draw(canvas, 'RGBA')
    for _ in range(int(width * height / 26000)):
        px = rng.integers(0, width)
        py = rng.integers(0, height)
        radius = int(rng.integers(2, max(3, width // 260)))
        alpha = int(rng.integers(60, 190))
        draw.ellipse([px - radius, py - radius, px + radius, py + radius],
                     fill=(255, 255, 255, alpha))
    return canvas


def write_photos(out_dir, rng):
    os.makedirs(out_dir, exist_ok=True)
    written = []
    for index, (name, palette) in enumerate(PALETTES):
        image = wave_field(2400, 1600, palette, phase=index * 0.83, rng=rng)
        path = os.path.join(out_dir, f"{name}.jpg")
        image.save(path, 'JPEG', quality=92)
        written.append(path)
    return written


def write_cover(path, title, artist, palette, rng):
    cover = wave_field(1000, 1000, palette, phase=1.7, rng=rng)
    draw = ImageDraw.Draw(cover)
    draw.text((70, 780), title, font=resolve_font(76), fill=(255, 255, 255))
    draw.text((70, 880), artist, font=resolve_font(46), fill=(226, 226, 232))
    cover.save(path, 'JPEG', quality=92)
    return path


def write_tone_wav(path, root_hz, seconds=18, rate=44100):
    """A slow major-seventh pad, in stereo. Synthesised, so nothing here is anyone's work.

    Stereo rather than mono for two independent reasons. The material one: everything this corpus
    stands in for - an album track, the soundtrack of a clip - is stereo in the world, and a store
    frame photographs a player whose channel controls have something to act on. The measured one:
    the app cannot currently play a one-channel stream at all (S2638, found while capturing this
    ticket's slots), so a mono corpus photographs an error toast instead of a player.

    The two channels are detuned against each other rather than duplicated, so the file is stereo in
    content and not only in its header.
    """
    t = np.linspace(0.0, seconds, int(rate * seconds), endpoint=False)
    voices = [1.0, 1.25, 1.5, 1.875]

    channels = []
    for side, spread in enumerate((-1.0, 1.0)):
        signal = np.zeros_like(t)
        for index, ratio in enumerate(voices):
            drift = 1.0 + 0.0016 * math.sin(0.21 * (index + 1)) + 0.0009 * spread * (index + 1)
            signal += np.sin(2 * math.pi * root_hz * ratio * drift * t) / (index + 1.6)
        signal += 0.18 * np.sin(2 * math.pi * root_hz * 0.5 * t + 0.4 * spread)

        envelope = np.clip(np.minimum(t / 2.5, (seconds - t) / 3.0), 0.0, 1.0)
        signal *= envelope * (0.62 + 0.38 * np.sin(2 * math.pi * 0.08 * t + 0.6 * side))
        channels.append(signal)

    stereo = np.stack(channels, axis=1)
    stereo /= max(np.abs(stereo).max(), 1e-6)

    pcm = (stereo * 0.82 * 32767).astype(np.int16)
    with wave.open(path, 'wb') as handle:
        handle.setnchannels(2)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        handle.writeframes(pcm.tobytes())
    return path


def write_music(out_dir, work_dir, ffmpeg, rng):
    os.makedirs(out_dir, exist_ok=True)
    written = []
    for index, (title, artist, album, root_hz) in enumerate(TRACKS):
        palette = PALETTES[(index * 3 + 1) % len(PALETTES)][1]
        cover = write_cover(os.path.join(work_dir, f"cover_{index}.jpg"), album, artist, palette, rng)
        source = write_tone_wav(os.path.join(work_dir, f"tone_{index}.wav"), root_hz)
        path = os.path.join(out_dir, f"{index + 1:02d} {title}.mp3")
        run(ffmpeg, [
            '-i', source, '-i', cover,
            '-map', '0:a', '-map', '1:v',
            '-c:a', 'libmp3lame', '-b:a', '192k',
            '-c:v', 'copy', '-disposition:v', 'attached_pic', '-id3v2_version', '3',
            '-metadata', f'title={title}',
            '-metadata', f'artist={artist}',
            '-metadata', f'album={album}',
            '-metadata', f'track={index + 1}',
            path,
        ], f"encode {title}")
        written.append(path)
    return written


def write_video(out_dir, work_dir, ffmpeg, rng):
    os.makedirs(out_dir, exist_ok=True)
    frames_dir = os.path.join(work_dir, 'frames')
    os.makedirs(frames_dir, exist_ok=True)
    fps, seconds = 24, 6
    for frame in range(fps * seconds):
        image = wave_field(1280, 720, PALETTES[1][1], phase=frame * 0.085, rng=rng)
        image.save(os.path.join(frames_dir, f"f{frame:04d}.png"), 'PNG')

    tone = os.path.join(work_dir, 'video_tone.wav')
    write_tone_wav(tone, 164.8, seconds=seconds)

    path = os.path.join(out_dir, 'Harbour Lights.mp4')
    run(ffmpeg, [
        '-framerate', str(fps), '-i', os.path.join(frames_dir, 'f%04d.png'),
        '-i', tone,
        '-c:v', 'libopenh264', '-b:v', '5M', '-pix_fmt', 'yuv420p',
        '-c:a', 'aac', '-b:a', '160k', '-shortest',
        '-metadata', 'title=Harbour Lights',
        path,
    ], "encode video")
    return [path]


def write_pdf(path):
    """A one-page PDF built by hand: object bodies first, then the xref built from their offsets."""
    lines = [
        (34, "FastMediaSorter"),
        (24, "Reading, without leaving the app"),
        (13, "PDF and EPUB open in the same viewer as your photos and video,"),
        (13, "so a document is one more file in the folder you are already in."),
        (13, ""),
        (13, "Page navigation, zoom, text selection and night mode all work the"),
        (13, "same way they do everywhere else in the app."),
        (13, ""),
        (11, "This page is generated sample content."),
    ]
    stream_parts = ["BT"]
    y = 720
    for size, text in lines:
        if text:
            escaped = text.replace('\\', r'\\').replace('(', r'\(').replace(')', r'\)')
            stream_parts.append(f"/F1 {size} Tf 1 0 0 1 72 {y} Tm ({escaped}) Tj")
        y -= int(size * 1.9)
    stream_parts.append("ET")
    stream = "\n".join(stream_parts).encode('ascii')

    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
        b"/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        b"<< /Length " + str(len(stream)).encode('ascii') + b" >>\nstream\n" + stream + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]

    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n".encode('ascii') + body + b"\nendobj\n"

    xref_at = len(out)
    out += f"xref\n0 {len(objects) + 1}\n".encode('ascii')
    out += b"0000000000 65535 f \n"
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode('ascii')
    out += (f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
            f"startxref\n{xref_at}\n%%EOF\n").encode('ascii')

    with open(path, 'wb') as handle:
        handle.write(out)
    return path


def write_epub(path):
    # Long enough to fill a landscape tablet page. A two-paragraph chapter reflowed onto a 2560px
    # viewport leaves most of the frame white, which is what the reader slot photographed the first
    # time this corpus was used for a store capture (S2602 Phase 03).
    chapters = [
        ("The Long Shelf", [
            "Every folder on the shelf holds something different, and the app does not ask you to "
            "care which. A picture, a song, a film and a book all open from the same list, in the "
            "same place, with the same gesture.",
            "That is the whole idea. There is no photo section to leave before you can reach the "
            "music, and no separate reader to install before a document will open. The folder you "
            "are standing in is the folder you work in, whatever happens to be inside it.",
            "Sorting works the same way. Two panels sit at the foot of the screen - one to copy, "
            "one to move - and each remembers the destinations you actually use. A file goes where "
            "it belongs in a single tap, and the next file is already on screen.",
            "Nothing here is a mode you have to enter. The viewer, the player and the reader are "
            "the same window wearing different controls, so the muscle memory you build on your "
            "holiday photographs carries over to the album you are tidying and the manual you are "
            "halfway through.",
        ]),
        ("Rooms and Doors", [
            "A network share is a room down the hall. A cloud folder is a room in another building. "
            "Both are drawn on the same shelf, next to the folder that lives on the phone itself.",
            "Once a room is on the shelf it stops being a special case. You browse it with the same "
            "list, you play from it with the same player, and you copy out of it into any other "
            "room with the same two panels. Where a file physically sits becomes a detail rather "
            "than a decision.",
            "Connections are described once and then left alone. The app reconnects quietly when a "
            "share comes back, keeps its place in a long folder while you are away, and tells you "
            "plainly when a room is genuinely unreachable instead of pretending it is empty.",
            "The shelf grows the way a real one does - slowly, by adding what you already use. "
            "Nothing is imported, nothing is duplicated into a private library, and nothing is "
            "moved unless you move it yourself.",
        ]),
    ]

    def page(title, paragraphs):
        body = "".join(f"    <p>{text}</p>\n" for text in paragraphs)
        return ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<html xmlns="http://www.w3.org/1999/xhtml">\n'
                f'  <head><title>{title}</title></head>\n'
                f'  <body>\n    <h1>{title}</h1>\n{body}  </body>\n</html>\n')

    manifest = "".join(
        f'    <item id="c{i}" href="c{i}.xhtml" media-type="application/xhtml+xml"/>\n'
        for i in range(len(chapters)))
    spine = "".join(f'    <itemref idref="c{i}"/>\n' for i in range(len(chapters)))
    opf = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">\n'
           '  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">\n'
           '    <dc:identifier id="bookid">urn:uuid:fms-store-sample-0001</dc:identifier>\n'
           '    <dc:title>The Long Shelf</dc:title>\n'
           '    <dc:creator>FastMediaSorter</dc:creator>\n'
           '    <dc:language>en</dc:language>\n'
           '  </metadata>\n'
           f'  <manifest>\n{manifest}  </manifest>\n'
           f'  <spine>\n{spine}  </spine>\n'
           '</package>\n')

    container = ('<?xml version="1.0" encoding="utf-8"?>\n'
                 '<container version="1.0" '
                 'xmlns="urn:oasis:names:tc:opendocument:xmlns:container">\n'
                 '  <rootfiles>\n'
                 '    <rootfile full-path="OEBPS/content.opf" '
                 'media-type="application/oebps-package+xml"/>\n'
                 '  </rootfiles>\n</container>\n')

    with zipfile.ZipFile(path, 'w') as book:
        # The mimetype entry must be first and stored uncompressed, or a reader rejects the file.
        book.writestr(zipfile.ZipInfo('mimetype'), 'application/epub+zip',
                      compress_type=zipfile.ZIP_STORED)
        book.writestr('META-INF/container.xml', container)
        book.writestr('OEBPS/content.opf', opf)
        for index, (title, paragraphs) in enumerate(chapters):
            book.writestr(f'OEBPS/c{index}.xhtml', page(title, paragraphs))
    return path


def write_books(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    return [write_pdf(os.path.join(out_dir, 'Reading in FastMediaSorter.pdf')),
            write_epub(os.path.join(out_dir, 'The Long Shelf.epub'))]


def main():
    out_root = DEFAULT_OUT
    if '--out' in sys.argv:
        out_root = sys.argv[sys.argv.index('--out') + 1]
    if '--clean' in sys.argv and os.path.isdir(out_root):
        shutil.rmtree(out_root)

    ffmpeg = resolve_ffmpeg()
    print(f"ffmpeg: {ffmpeg}")

    work_dir = os.path.join(out_root, '.work')
    os.makedirs(work_dir, exist_ok=True)
    rng = np.random.default_rng(SEED)

    written = []
    written += write_photos(os.path.join(out_root, 'Photos'), rng)
    written += write_video(os.path.join(out_root, 'Videos'), work_dir, ffmpeg, rng)
    written += write_music(os.path.join(out_root, 'Music'), work_dir, ffmpeg, rng)
    written += write_books(os.path.join(out_root, 'Books'))

    shutil.rmtree(work_dir, ignore_errors=True)

    for path in written:
        print(f"  {os.path.relpath(path, out_root)} ({os.path.getsize(path)} bytes)")
    print(f"\nDONE: {len(written)} file(s) under {out_root}")


if __name__ == '__main__':
    main()
