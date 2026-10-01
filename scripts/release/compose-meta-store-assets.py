"""S0555: Meta Horizon Store listing images - covers, logo, icon and screenshots - into meta/listing/images/.

Covers are rendered from the WAVE-PARTICLES backdrop (GREEN palette).

The backdrop follows the catalog contract WAVE-PARTICLES sections 3-4 (lines + particles over an
accumulating wash); it is simulated for a fixed number of frames with a fixed seed so every cover
shares one look. Sizes and content rules: developers.meta.com/horizon/resources/asset-guidelines/.
"""
import colorsys
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "meta" / "listing" / "images"
# Raw tablet captures shared with the Play composer (compose-play-screenshots.py --tablet).
SHOTS = ROOT / "temp" / "play-shots-tablet"
TITLE = sys.argv[1] if len(sys.argv) > 1 else "Fast Media Sorter"
FONT = "C:/Windows/Fonts/segoeuib.ttf"
FORE = ROOT / "app_v2/src/main/res/mipmap-xxxhdpi/ic_launcher_adaptive_fore.png"
ICON = ROOT / "play/listing/en-US/images/icon.png"

WASH = (10, 10, 10)
WASH_ALPHA = 38 / 255
FRAMES = 90
SS = 2  # supersampling factor for smooth strokes


def hsla(h, s, l, a):
    r, g, b = colorsys.hls_to_rgb((h % 360) / 360, l, s)
    return int(r * 255), int(g * 255), int(b * 255), int(a * 255)


def backdrop(w, h, seed):
    rnd = random.Random(seed)
    u = lambda a, b: a + rnd.random() * (b - a)
    W, H = w * SS, h * SS
    # Stroke/radius/step in the contract are screen pixels on a ~1080p surface; scale to the canvas.
    px = min(W, H) / 1080
    theta = math.radians(u(8, 22))  # a gentle diagonal reads better on a cover than a random angle
    dx, dy, nx, ny = math.cos(theta), math.sin(theta), -math.sin(theta), math.cos(theta)
    lines = rnd.randint(5, 7)
    step = 20 * u(0.8, 1.2) * px
    stroke = max(1, int(u(3, 6) * px))
    amp_frac = u(0.28, 0.32)
    line_base, line_step = u(95, 130), u(2, 6)
    m = u(0.8, 1.2)
    parts = []
    for _ in range(int(55 * (W * H) / (1920 * 1080 * SS * SS) ** 1 + 30)):
        speed = (0.12 + u(0, 0.42)) * m * px * 2
        if rnd.random() < 0.18:
            speed *= -0.35
        parts.append([u(0, W), u(0, H), u(1, 6) * px, dx * speed + (rnd.random() - 0.5) * 0.28 * m * px,
                      dy * speed + (rnd.random() - 0.5) * 0.28 * m * px, 115 + (rnd.random() - 0.5) * 30])
    img = Image.new("RGB", (W, H), WASH)
    wash = Image.new("RGB", (W, H), WASH)
    t = u(0, 20)
    for f in range(1, FRAMES + 1):
        t += 0.002 * 2
        p = min(f, 36) / 36
        g = 0.35 + 0.65 * p * (2 - p)
        img = Image.blend(img, wash, WASH_ALPHA)
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        small = min(W, H)
        drift = math.sin(0.45 * t) * 0.02 * small
        cx, cy = W / 2 + dx * drift, H / 2 + dy * drift
        lane = 0.038 * small
        span = math.hypot(W, H) + 6 * step
        la = (0.28 + 0.16 * g) * 0.70
        for j in range(lines):
            band = (j - (lines - 1) / 2) * lane
            env = 0.40 + 0.60 * abs(math.sin(0.4 * t + 0.2 * j))
            amp = H * amp_frac * g * env
            pts = []
            s = -span / 2
            while s <= span / 2:
                off = band + math.sin(0.0105 / px * s + t + 0.8 * j) * amp
                pts.append((cx + dx * s + nx * off, cy + dy * s + ny * off))
                s += step
            d.line(pts, fill=hsla(line_base + j * line_step, 0.80, 0.65, la), width=stroke, joint="curve")
        pa = (0.38 + 0.32 * g) * 0.70
        for q in parts:
            q[0] += q[3]
            q[1] += q[4]
            if q[0] < 0 or q[0] > W:
                q[3] = -q[3]
                q[0] = min(max(q[0], 0), W)
            if q[1] < 0 or q[1] > H:
                q[4] = -q[4]
                q[1] = min(max(q[1], 0), H)
            r = q[2]
            d.ellipse((q[0] - r, q[1] - r, q[0] + r, q[1] + r), fill=hsla(q[5], 0.90, 0.70, pa))
        img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    return img.resize((w, h), Image.LANCZOS)


def arrows(size):
    fore = Image.open(FORE).convert("RGBA")
    fore = fore.crop(fore.getbbox())
    return fore.resize((size, size), Image.LANCZOS)


def vignette(img, strength=0.85):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).ellipse((w * 0.18, h * 0.22, w * 0.82, h * 0.78), fill=int(255 * strength))
    mask = mask.filter(ImageFilter.GaussianBlur(min(w, h) * 0.12))
    return Image.composite(Image.new("RGB", (w, h), WASH), img, mask)


def shadowed_text(img, xy, text, font):
    layer = Image.new("L", img.size, 0)
    ImageDraw.Draw(layer).text(xy, text, font=font, fill=255)
    glow = layer.filter(ImageFilter.GaussianBlur(max(4, font.size // 10)))
    img.paste(Image.new("RGB", img.size, (0, 0, 0)), (0, 0), glow.point(lambda v: min(255, v * 2)))
    ImageDraw.Draw(img).text(xy, text, font=font, fill=(255, 255, 255))


def fit_font(text, max_w, max_h):
    size = int(max_h)
    while size > 10:
        font = ImageFont.truetype(FONT, size)
        l, t, r, b = font.getbbox(text)
        if r - l <= max_w and b - t <= max_h:
            return font
        size -= 2
    return ImageFont.truetype(FONT, 10)


def cover(name, w, h, seed, layout):
    img = vignette(backdrop(w, h, seed))
    d = ImageDraw.Draw(img)
    if layout == "stack":
        # Title and mark kept inside the vertical 20-80% band: no text in the top or bottom 20%.
        mark = int(min(w, h) * 0.20)
        font = fit_font(TITLE, w * 0.82, h * 0.11)
        l, t, r, b = font.getbbox(TITLE)
        gap = int(h * 0.04)
        total = mark + gap + (b - t)
        y0 = (h - total) // 2
        img.paste(arrows(mark), ((w - mark) // 2, y0), arrows(mark))
        shadowed_text(img, ((w - (r - l)) // 2 - l, y0 + mark + gap - t), TITLE, font)
    else:
        mark = int(h * 0.34)
        font = fit_font(TITLE, w * 0.62, h * 0.22)
        l, t, r, b = font.getbbox(TITLE)
        gap = int(h * 0.06)
        total = mark + gap + (r - l)
        x0 = (w - total) // 2
        img.paste(arrows(mark), (x0, (h - mark) // 2), arrows(mark))
        shadowed_text(img, (x0 + mark + gap - l, (h - (b - t)) // 2 - t), TITLE, font)
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / name, "PNG")
    print(name, img.size, img.mode)


def logo():
    h = 480
    font = ImageFont.truetype(FONT, int(h * 0.5))
    l, t, r, b = font.getbbox(TITLE)
    mark = int(h * 0.8)
    gap = int(h * 0.12)
    w = mark + gap + (r - l) + 40
    OUT.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    img.paste(arrows(mark), (20, (h - mark) // 2), arrows(mark))
    ImageDraw.Draw(img).text((20 + mark + gap - l, (h - (b - t)) // 2 - t), TITLE, font=font, fill=(255, 255, 255, 255))
    img.save(OUT / "logo_transparent.png", "PNG")
    print("logo_transparent.png", img.size, img.mode)


def icon():
    src = Image.open(ICON).convert("RGBA")
    flat = Image.new("RGB", src.size, (0, 0, 0))
    flat.paste(src, (0, 0), src)
    flat.resize((512, 512), Image.LANCZOS).save(OUT / "icon_512.png", "PNG")
    print("icon_512.png", flat.size, flat.mode)


# Meta takes exactly five 2560x1440 frames with no text. Slots name only screens the vr flavor has:
# the launcher and the paired-watch row are absent there (docs/FLAVOR_MATRIX.md).
SCREENSHOT_SLOTS = [
    ("01_browse", "en-US/browse.png"),
    ("02_video_player", "video-player.png"),
    ("03_image_viewer", "image-viewer.png"),
    ("04_music_player", "music-player.png"),
    ("05_streams", "en-US/streams.png"),
]


def screenshots():
    out = OUT / "screenshots"
    out.mkdir(parents=True, exist_ok=True)
    for name, rel in SCREENSHOT_SLOTS:
        # Drop the tablet status bar (cellular icons name non-Quest hardware) and the gesture strip.
        im = Image.open(SHOTS / rel).convert("RGB").crop((0, 52, 2560, 1540))
        h = 1440
        w = round(im.width * h / im.height)
        im = im.resize((w, h), Image.LANCZOS)
        pad = (2560 - w) // 2
        canvas = Image.new("RGB", (2560, 1440))
        canvas.paste(im.crop((0, 0, 1, h)).resize((pad, h)), (0, 0))
        canvas.paste(im.crop((w - 1, 0, w, h)).resize((2560 - pad - w, h)), (pad + w, 0))
        canvas.paste(im, (pad, 0))
        canvas.save(out / f"{name}_2560x1440.png", "PNG")
        print(f"screenshots/{name}_2560x1440.png", canvas.size)


if __name__ == "__main__":
    cover("cover_landscape_2560x1440.png", 2560, 1440, 7, "stack")
    cover("cover_square_1440x1440.png", 1440, 1440, 7, "stack")
    cover("cover_portrait_1008x1440.png", 1008, 1440, 7, "stack")
    cover("hero_3000x900.png", 3000, 900, 7, "row")
    cover("mini_landscape_1080x360.png", 1080, 360, 7, "row")
    logo()
    icon()
    screenshots()
