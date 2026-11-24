#!/usr/bin/env python
"""Generate every rendition of the product mark and the identity manifest (PRODUCT-IDENTITY 0.2, S4693, S4784).

The product master is the FastMediaSorter family master plus the Android signifier (section 3.3 rule 17), composed by
identity_family.py from the byte copy of the family folder in assets/identity/family/; this run writes it to
assets/identity/master/ together with its dark and mono forms, so the master is an output and never edited by hand.
Plates and tones come from the family tokens: the light set on surfaces that sit on wallpaper and white pages, the
dark set on the watch and other dark surfaces (rule 28, ADR-3 of S4784). Renditions of 16 to 32 px render the hinted
small form of their size 1:1 (rule 21, ADR-2); larger ones rasterize the full form through PyMuPDF at SUPERSAMPLE
times their size and downsample. Lockups set the name in Manrope ExtraBold outlines (identity_lockup.py, rule 27).

Output is deterministic for one PyMuPDF version: PNG without metadata at a fixed compression level, so a rerun on
unchanged inputs changes no byte (conformance rung 3). --check renders in memory and compares with the files on disk,
then runs the family check of conformance rung 8: the local copy against its recorded hashes, the manifest's
family.sha256 against the local family-mark.json, and that file against the catalog's when the catalog is reachable.

Usage:
    py -3 scripts/identity/build_identity.py [--check]

Exit codes:
    0   every rendition written (or, with --check, every file equals its rendition and the family check matched).
    1   --check found a rendition missing or different, or the family copy or family.sha256 disagrees.
    2   the run could not happen (an input or PyMuPDF/fontTools missing), or --check passed everything except the
        catalog half of the family check, which could not run on this machine.
"""
import hashlib
import io
import json
import os
import re
import sys

from PIL import Image

try:
    import pymupdf
except ImportError:
    pymupdf = None

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from identity_family import FAMILY_DIR, HINTED, Family, insert, paths, view  # noqa: E402

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
IDENTITY = 'assets/identity'
MASTER = f'{IDENTITY}/master/mark.svg'
MASTER_DARK = f'{IDENTITY}/master/mark-dark.svg'
MASTER_MONO = f'{IDENTITY}/master/mark-mono.svg'
MANIFEST = f'{IDENTITY}/identity-manifest.json'
WEB_MANIFEST = 'site.webmanifest'
GENERATOR = 'pwsh -NoProfile -File scripts/identity/build-identity.ps1'
PRODUCT = 'Fast Media Sorter'

SUPERSAMPLE = 4
CANVAS = 432
# The family mark fits the 240-unit square from 96 to 336 of the 432 canvas (construction.txt).
SPAN = 240
ORIGIN = 96
CENTRE = 216

DENSITIES = [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]
# noLegalDebug carries the same layers as main on purpose: the variant source set outranks the debug
# build type, whose badged layers would otherwise replace the noLegal icon in noLegal debug builds.
MIPMAP_SETS = ('main', 'noLegalDebug')
MIPMAP = 'app_v2/src/{s}/res/mipmap-{d}/{name}.png'
WEAR_MIPMAP = 'wear/src/{s}/res/mipmap-{d}/{name}.png'

# Rule 23 and section 6 item 3 of S4784: one badge per build, in the family's upper leading box - the debug build takes
# the family debug badge, an edition a disc of the same geometry in its colour. The lower trailing box belongs to the
# signifier, which a badge never replaces.
BADGE_CX = 146
BADGE_R = 16
EDITIONS = (('lite', '#1976D2'), ('photos', '#7B1FA2'), ('legacy', '#455A64'), ('foss', '#388E3C'))
DEBUG = 'debug'
BADGE_MONO = '{m}/src/main/res/drawable/ic_launcher_badge_monochrome.xml'


# Rule 21 and 26: the mono silhouette where one colour is forced, as Android vectors. A family path is M/L/Z only, so its
# d string is valid pathData unchanged; every source path gets the same fill, so overlapping tone paths form the
# silhouette. Value: (surface, source, size dp, fill, tint, the invariant the file carries).
VECTORS = {
    'ic_launcher_monochrome': ('app.launcher', 'mono-full', 108, '#FFFFFFFF', None,
                               'The themed-icon layer (Android 13+): the launcher reads it as an alpha mask. The drawing already '
                               'sits inside the 264-unit live area of the 432 canvas, so it needs no inset wrapper.'),
    'ic_notification_app_logo': ('app.notification', 'small-24', 24, '#FFFFFFFF', None,
                                 'The status-bar icon of every notification. Never add a tint: a theme attribute cannot resolve '
                                 'outside the app theme, and startForeground then throws '
                                 'CannotPostForegroundServiceNotificationException (S0405, S0416). The system renders it as an '
                                 'alpha silhouette, so the white fill is the shape rather than the colour.'),
    'ic_app_logo': ('other.glyph', 'app-glyph', 24, '@color/white', '?attr/colorControlNormal',
                    'The product glyph for a row or button that names the product, tinted like the other ic_* glyphs.'),
    'ic_product_mark_mono': ('other.wear-glyph', 'small-24', 24, '#FFFFFFFF', None,
                             'The icon of every watch tile and complication provider that names the product (rule 25): the '
                             'watch face tints it, so the white fill is the shape rather than the colour.'),
}
# ICON-SET 0.44 section 32 A: the app.logo glyph is one contour pinned by SHA-256 in the contract. No family master
# carries that form (the master's two sub-paths overlap on the horizontal arrow), so the drawable is cut from this file.
APP_LOGO_GLYPH = f'{IDENTITY}/glyph/app-logo.svg'
APP_LOGO_GLYPH_SHA256 = '085883252d760a4cf2ae77b43f90cda94c2e825e9b30d3df72d91af9a8b1a439'
# Which vectors each module carries, and the surface id a module-wide row takes (None keeps the vector's own).
# PRODUCT-IDENTITY 0.5 rule 36: the notification icon of the phone and of the watch are two rows under one id, so the
# watch copy keeps its own surface instead of folding into the module-wide app.wear row.
OWN_SURFACE_VECTORS = ('other.wear-glyph', 'app.notification')
MODULE_VECTORS = (
    ('app_v2', ('ic_launcher_monochrome', 'ic_notification_app_logo', 'ic_app_logo'), None),
    ('wear', ('ic_launcher_monochrome', 'ic_notification_app_logo', 'ic_product_mark_mono'), 'app.wear'),
)

def _surfaces():
    """Rows of (surface, path, kind, size, badge, dark): badge is None, DEBUG or an edition colour."""
    rows = []
    for src in MIPMAP_SETS:
        for d, k in DENSITIES:
            rows.append(('app.launcher', MIPMAP.format(s=src, d=d, name='ic_launcher_adaptive_fore'), 'fore', int(108 * k), None, False))
            rows.append(('app.launcher', MIPMAP.format(s=src, d=d, name='ic_launcher_adaptive_back'), 'plate', int(108 * k), None, False))
            rows.append(('app.launcher', MIPMAP.format(s=src, d=d, name='ic_launcher'), 'rounded', int(48 * k), None, False))
    # The plate comes from main, so an edition carries only the layers its badge changes; the round legacy
    # bitmap stays because the legacy flavor still installs below API 26, where no adaptive icon exists.
    for src, badge in EDITIONS + (('debug', DEBUG),):
        names = ('ic_launcher',) if src == 'debug' else ('ic_launcher', 'ic_launcher_round')
        for d, k in DENSITIES:
            rows.append(('app.launcher', MIPMAP.format(s=src, d=d, name='ic_launcher_adaptive_fore'), 'fore', int(108 * k), badge, False))
            for name in names:
                rows.append(('app.launcher', MIPMAP.format(s=src, d=d, name=name), 'rounded', int(48 * k), badge, False))
    for d, k in DENSITIES:
        # ADR-3: the watch takes the dark set; its legacy bitmap is a round plate, the mark inside the 66 % safe circle.
        rows.append(('app.wear', WEAR_MIPMAP.format(s='main', d=d, name='ic_launcher_adaptive_fore'), 'fore', int(108 * k), None, True))
        rows.append(('app.wear', WEAR_MIPMAP.format(s='main', d=d, name='ic_launcher_adaptive_back'), 'plate', int(108 * k), None, True))
        rows.append(('app.wear', WEAR_MIPMAP.format(s='main', d=d, name='ic_launcher'), 'circle', int(48 * k), None, True))
        rows.append(('app.wear', WEAR_MIPMAP.format(s='debug', d=d, name='ic_launcher_adaptive_fore'), 'fore', int(108 * k), DEBUG, True))
        rows.append(('app.wear', WEAR_MIPMAP.format(s='debug', d=d, name='ic_launcher'), 'circle', int(48 * k), DEBUG, True))
    rows.append(('app.launcher', BADGE_MONO.format(m='app_v2'), 'badge-mono', None, None, False))
    rows.append(('app.wear', BADGE_MONO.format(m='wear'), 'badge-mono', None, None, False))
    for path in ('store_assets/icon_512.png', 'play/listing/en-US/images/icon.png',
                 'fastlane/metadata/android/en-US/images/icon.png'):
        rows.append(('play.icon', path, 'square', 512, None, False))
    rows += [
        ('web.favicon-ico', 'favicon.ico', 'ico', 48),
        ('web.favicon-svg', 'favicon.svg', 'svg', None),
        ('other.favicon-png', 'favicon-16x16.png', 'none', 16),
        ('other.favicon-png', 'favicon-32x32.png', 'none', 32),
        ('web.apple-touch', 'apple-touch-icon.png', 'square', 180),
        ('site.identity', 'icon.png', 'rounded', 192),
        ('web.manifest-192', f'{IDENTITY}/out/web/icon-192.png', 'rounded', 192),
        ('web.manifest-512', f'{IDENTITY}/out/web/icon-512.png', 'rounded', 512),
        ('web.maskable-512', f'{IDENTITY}/out/web/maskable-512.png', 'maskable', 512),
        ('web.og-image', 'assets/og/og-image.png', 'card', (1200, 630)),
        ('github.social-preview', f'{IDENTITY}/out/github-social-preview.png', 'card', (1280, 640)),
        ('readme.head', f'{IDENTITY}/out/readme-head-128.png', 'none', 128),
        ('hub.card', f'{IDENTITY}/out/hub-card-256.png', 'rounded', 256),
        ('help.head', 'app_v2/src/main/assets/help/img/product-mark.png', 'none', 96),
    ]
    for qualifier, dark in (('values', False), ('values-night', True)):
        rows.append(('other.identity-colors', f'app_v2/src/main/res/{qualifier}/identity_colors.xml', 'colors', None, None, dark))
    for d, k in DENSITIES:
        rows.append(('app.tv-banner', f'app_v2/src/main/res/drawable-{d}/tv_banner.png', 'card', (int(160 * k), int(90 * k))))
    for module, names, surface in MODULE_VECTORS:
        for name in names:
            own = VECTORS[name][0]
            rows.append((own if own in OWN_SURFACE_VECTORS else surface or own,
                         f'{module}/src/main/res/drawable/{name}.xml', 'vector', name))
    return [r if len(r) == 6 else r + (None, False) for r in rows]


# Rule 16: pictures that are not renditions of the mark, on record with their origin and licence. The
# generator does not draw them; it records their current bytes so a silent replacement is caught.
RECORDED = [
    ('other.provider-dropbox', 'app_v2/src/main/res/drawable/ic_provider_dropbox.png',
     'Dropbox brand mark (third party, ICON-EXTERNAL)', 'trademark of Dropbox, Inc.; shown only to name the service'),
    ('other.provider-google-drive', 'app_v2/src/main/res/drawable/ic_provider_google_drive.png',
     'Google Drive brand mark (third party, ICON-EXTERNAL)', 'trademark of Google LLC; shown only to name the service'),
    ('other.provider-onedrive', 'app_v2/src/main/res/drawable/ic_provider_onedrive.png',
     'Microsoft OneDrive brand mark (third party, ICON-EXTERNAL)', 'trademark of Microsoft Corporation; shown only to name the service'),
    ('other.author-logo', 'app_v2/src/main/res/drawable/ic_logo.png',
     "the author's SZ mark, platform splash branding slot", 'own work'),
    ('other.touch-zones-scheme', 'app_v2/src/main/res/drawable/touch_zones_scheme.png',
     'generated by scripts/docs/render-touch-zones-scheme.py', 'own work'),
    ('other.hero-supported-devices', 'store_assets/hero-supported-devices.png',
     'unknown - promotion picture, no source in the repository', 'unknown'),
]


def _png_bytes(img):
    buf = io.BytesIO()
    img.save(buf, format='PNG', optimize=False, compress_level=9)
    return buf.getvalue()


def _raster(svg, px_w, px_h=None, supersample=SUPERSAMPLE):
    """Rasterize an SVG to px_w x px_h through PyMuPDF, supersampled and downsampled with LANCZOS."""
    px_h = px_h or px_w
    doc = pymupdf.open(stream=svg.encode('utf-8'), filetype='svg')
    page = doc[0]
    zoom = px_w * supersample / page.rect.width
    pix = page.get_pixmap(matrix=pymupdf.Matrix(zoom, zoom), alpha=True)
    img = Image.open(io.BytesIO(pix.tobytes('png'))).convert('RGBA')
    doc.close()
    if img.size != (px_w, px_h):
        img = img.resize((px_w, px_h), Image.LANCZOS)
    return img


def _cut(master, size, share):
    """The mark alone, its 240-unit square filling `share` of a size x size box."""
    side = SPAN / share
    x0 = CENTRE - side / 2
    return _raster(view(master, x0, x0, side, side, size, size), size)


def _plated(master, size, plate, shape, inset, share):
    """A plate of shape square/rounded/circle, inset from the canvas edge, with the mark at `share` of the plate side."""
    side = size * (1 - 2 * inset)
    off = size * inset
    unit = side * share / SPAN
    if shape == 'circle':
        backdrop = f'<circle cx="{size / 2:g}" cy="{size / 2:g}" r="{side / 2:g}" fill="{plate}"/>'
    else:
        radius = side * 0.22 if shape == 'rounded' else 0
        backdrop = f'<rect x="{off:g}" y="{off:g}" width="{side:g}" height="{side:g}" rx="{radius:g}" fill="{plate}"/>'
    shift = size / 2 - CENTRE * unit
    body = ''.join(f'<path fill="{fill}" d="{d}"/>' for fill, d in paths(master))
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">'
           f'{backdrop}<g transform="translate({shift:.4f} {shift:.4f}) scale({unit:.6f})">{body}</g></svg>')
    return _raster(svg, size)


def _none(family, master, size):
    """No plate (rule 24): the hinted small form 1:1 up to 32 px, else the full form filling the box less 6 %."""
    if size in HINTED:
        return _raster(family.hinted(size), size, supersample=1)
    return _cut(master, size, 0.88)


def _ico(family, master):
    frames = [_none(family, master, s) for s in (16, 32, 48)]
    buf = io.BytesIO()
    frames[-1].save(buf, format='ICO', sizes=[(16, 16), (32, 32), (48, 48)], append_images=frames[:-1])
    return buf.getvalue()


def _favicon_svg(family):
    """The small form with a prefers-color-scheme rule switching to the dark tones (rule 25, rule 37)."""
    light, dark = paths(family.small()), paths(family.small(dark=True))
    pad = SPAN * 0.03
    rules = ''.join(f'.t{i}{{fill:{fill}}}' for i, (fill, _) in enumerate(dark))
    body = ''.join(f'<path class="t{i}" fill="{fill}" d="{d}"/>' for i, (fill, d) in enumerate(light))
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{ORIGIN - pad:g} {ORIGIN - pad:g} {SPAN + 2 * pad:g} '
            f'{SPAN + 2 * pad:g}">\n<!-- Generated by {GENERATOR} (S4784); never edit by hand. -->\n'
            f'<style>@media (prefers-color-scheme: dark){{{rules}}}</style>\n{body}\n</svg>\n').encode('utf-8')


def _badge_mono():
    """The badge disc alone, in 108-unit space, for the themed-icon layer the launcher reads as an alpha mask."""
    c, r = BADGE_CX / 4, BADGE_R / 4
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by {GENERATOR} (S4695, S4784); never edit by hand. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n'
            '    android:height="108dp"\n'
            '    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n'
            '    <path\n'
            '        android:fillColor="#FFFFFFFF"\n'
            f'        android:pathData="M{c - r:g},{c:g} A{r:g},{r:g} 0 1,0 {c + r:g},{c:g} A{r:g},{r:g} 0 1,0 {c - r:g},{c:g} Z" />\n'
            '</vector>\n').encode('utf-8')


def _pinned_glyph():
    """(origin, port, pairs, even_odd) of the app.logo glyph; refuses a file that is not the pinned contract text."""
    with open(_abs(APP_LOGO_GLYPH), 'rb') as fh:
        raw = fh.read()
    if _sha(raw) != APP_LOGO_GLYPH_SHA256:
        raise RuntimeError(f'{APP_LOGO_GLYPH} is not the glyph pinned by ICON-SET 0.44 section 32 A')
    text = raw.decode('utf-8')
    return (APP_LOGO_GLYPH, 24, [(None, d) for d in re.findall(r'<path d="([^"]+)"', text)],
            'fill-rule="evenodd"' in text)


def _vector(family, name):
    _, source, dp, fill, tint, note = VECTORS[name]
    # The contract export reads the fill rule back from android:fillType, so the pinned glyph's evenodd must survive.
    even_odd = False
    if source == 'app-glyph':
        origin, port, pairs, even_odd = _pinned_glyph()
    else:
        port = 432 if source == 'mono-full' else int(source.split('-')[1])
        svg = family.mono(full=True) if source == 'mono-full' else family.hinted(port)
        origin, pairs = FAMILY_DIR, paths(svg)
    nl = '\n'
    tint_attr = f'{nl}    android:tint="{tint}"' if tint else ''
    fill_type = f'{nl}        android:fillType="evenOdd"' if even_odd else ''
    body = ''.join(f'    <path{nl}        android:fillColor="{fill}"{fill_type}{nl}        android:pathData="{d}" />{nl}'
                   for _, d in pairs)
    return ('<?xml version="1.0" encoding="utf-8"?>' + nl
            + f'<!--{nl}    Generated by {GENERATOR} (S4784) from {origin}; never edit by hand.{nl}    {note}{nl}-->{nl}'
            + '<vector xmlns:android="http://schemas.android.com/apk/res/android"' + nl
            + f'    android:width="{dp}dp"{nl}    android:height="{dp}dp"{nl}'
            + f'    android:viewportWidth="{port}"{nl}    android:viewportHeight="{port}"{tint_attr}>{nl}'
            + body + '</vector>' + nl).encode('utf-8')

# Rule 28: a surface that follows the system theme (the splash) takes its tones from a resource the night qualifier
# switches, never from literals. Value: the family token each colour resolves to.
IDENTITY_COLORS = (('identity_mark_lit', 'family.blue.lit'), ('identity_mark_base', 'family.blue.base'),
                   ('identity_mark_shade', 'family.blue.shade'), ('identity_plate', 'family.background'))


def _colors(family, dark):
    tones = family.dark if dark else family.light
    nl = '\n'
    body = ''.join(f'    <color name="{name}">{tones[token]}</color>{nl}' for name, token in IDENTITY_COLORS)
    return ('<?xml version="1.0" encoding="utf-8"?>' + nl
            + f'<!-- Generated by {GENERATOR} (S4784) from {FAMILY_DIR}/tokens.json, '
            + f'{"dark" if dark else "light"} tones; never edit by hand. -->{nl}'
            + '<resources>' + nl + body + '</resources>' + nl).encode('utf-8')


def _badge_svg(family, badge):
    if badge == DEBUG:
        return family.badge('debug')
    c, r = BADGE_CX, BADGE_R
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 432 432">\n'
            '<!-- edition badge -->\n'
            f'<path fill="{badge}" d="M{c - r} {c}A{r} {r} 0 1 0 {c + r} {c}A{r} {r} 0 1 0 {c - r} {c}Z"/>\n</svg>\n')


class Renderer:
    def __init__(self, family):
        self.family = family
        self.lockup = None

    def master(self, badge, dark):
        return self.family.product_master(dark=dark, badge=_badge_svg(self.family, badge) if badge else None)

    def plate(self, dark):
        return (self.family.dark if dark else self.family.light)['family.background']

    def render(self, kind, size, badge, dark):
        master = self.master(badge, dark)
        plate = self.plate(dark)
        if kind == 'svg':
            return _favicon_svg(self.family), None, None
        if kind == 'colors':
            return _colors(self.family, dark), None, None
        if kind == 'vector':
            return _vector(self.family, size), None, None
        if kind == 'badge-mono':
            return _badge_mono(), None, None
        if kind == 'card':
            if self.lockup is None:
                from identity_lockup import Lockup
                self.lockup = Lockup(REPO)
            fam = self.family.dark
            svg = self.lockup.card(self.family.product_master(dark=True), size[0], size[1],
                                   fam['family.background'], fam['family.mono.light'])
            return _png_bytes(_raster(svg, size[0], size[1]).convert('RGB')), size[0], size[1]
        if kind == 'ico':
            return _ico(self.family, master), 48, 48
        if kind == 'fore':
            img = _raster(master, size)
        elif kind == 'plate':
            img = Image.new('RGB', (size, size), plate)
        elif kind == 'rounded':
            img = _plated(master, size, plate, 'rounded', 0.0625, 0.6)
        elif kind == 'circle':
            img = _plated(master, size, plate, 'circle', 0.0625, 0.467)
        elif kind == 'square':
            img = _plated(master, size, plate, 'square', 0, 0.6)
        elif kind == 'maskable':
            # Inside the 80 % safe circle a maskable icon promises: the square's diagonal fits 0.8 of the side.
            img = _plated(master, size, plate, 'square', 0, 0.56)
        elif kind == 'none':
            img = _none(self.family, master, size)
        else:
            raise ValueError(kind)
        return _png_bytes(img), size, size


def _web_manifest(renditions, background):
    """The site's web app manifest; icon paths are relative to the site root, where it is served."""
    purposes = {'web.manifest-192': 'any', 'web.manifest-512': 'any', 'web.maskable-512': 'maskable'}
    icons = [{'src': r['path'], 'sizes': f"{r['width']}x{r['height']}", 'type': 'image/png', 'purpose': purposes[r['surface']]}
             for r in renditions if r['surface'] in purposes]
    doc = {'name': PRODUCT, 'short_name': PRODUCT, 'icons': icons,
           'theme_color': '#0a0f0a', 'background_color': background, 'display': 'browser',
           'start_url': '.'}
    return (json.dumps(doc, indent=2) + '\n').encode('utf-8')


def _abs(rel):
    return os.path.join(REPO, rel.replace('/', os.sep))


def _sha(data):
    return hashlib.sha256(data).hexdigest()


def _stamp(svg, label):
    head, rest = svg.split('\n', 1)
    return f'{head}\n<!-- {label}; generated by {GENERATOR} (S4784) from {FAMILY_DIR}, never edit by hand -->\n{rest}'


def _renditions(family):
    renderer = Renderer(family)
    outputs, renditions = [], []
    for surface, rel, kind, size, badge, dark in _surfaces():
        data, w, h = renderer.render(kind, size, badge, dark)
        entry = {'surface': surface, 'path': rel, 'format': os.path.splitext(rel)[1].lstrip('.').lower()}
        if w:
            entry['width'] = w
            entry['height'] = h
        entry['sha256'] = _sha(data)
        renditions.append(entry)
        outputs.append((rel, data))
    for surface, rel, source, licence in RECORDED:
        with open(_abs(rel), 'rb') as f:
            data = f.read()
        w, h = Image.open(io.BytesIO(data)).size
        renditions.append({'surface': surface, 'path': rel, 'format': 'png', 'width': w, 'height': h,
                           'sha256': _sha(data), 'source': source, 'licence': licence})
    return outputs, renditions


def _family_findings(family, manifest_on_disk):
    """(failures, catalog verdict) of conformance rung 8."""
    failures = family.copy_findings()
    if manifest_on_disk is not None and manifest_on_disk.get('family.sha256') != family.family_sha256():
        failures.append('family.sha256 in the manifest is not the SHA-256 of the local family-mark.json')
    verdict = family.catalog_check()
    if verdict == 'differs':
        failures.append('the local family-mark.json differs from the catalog; run scripts/identity/sync-family-mark.ps1')
    return failures, verdict


def main():
    check = '--check' in sys.argv
    if pymupdf is None:
        print('build-identity: COULD NOT VERIFY (PyMuPDF missing; py -3 -m pip install pymupdf)')
        return 2
    try:
        family = Family(REPO)
        masters = [(MASTER, _stamp(family.product_master(), 'product master, light tones')),
                   (MASTER_DARK, _stamp(family.product_master(dark=True), 'product master, dark tones')),
                   (MASTER_MONO, family.mono())]
        outputs, renditions = _renditions(family)
    except (FileNotFoundError, KeyError, ImportError) as e:
        print(f'build-identity: COULD NOT VERIFY (missing input: {e})')
        return 2
    master_bytes = masters[0][1].encode('utf-8')
    outputs = [(rel, text.encode('utf-8')) for rel, text in masters] + outputs
    hinted = [{'name': f'small-{px}', 'path': f'{FAMILY_DIR}/masters/mark-small-{px}.svg', 'minPx': px, 'maxPx': px}
              for px in HINTED]
    manifest = {
        'schemaVersion': 1,
        'product': PRODUCT,
        'family': 'fastmediasorter',
        'family.sha256': family.family_sha256(),
        'master': {
            'svg': MASTER,
            'small': f'{FAMILY_DIR}/masters/mark-small.svg',
            'mono': MASTER_MONO,
            'reduced': hinted,
            'background': family.light['family.background'],
            'sha256': _sha(master_bytes),
        },
        'generator': GENERATOR,
        'renditions': renditions,
    }
    outputs.append((MANIFEST, (json.dumps(manifest, indent=2, ensure_ascii=False) + '\n').encode('utf-8')))
    outputs.append((WEB_MANIFEST, _web_manifest(renditions, family.light['family.background'])))

    if check:
        drift = []
        for rel, data in outputs:
            path = _abs(rel)
            if not os.path.exists(path):
                drift.append(f'missing {rel}')
                continue
            with open(path, 'rb') as f:
                if f.read() != data:
                    drift.append(f'differs {rel}')
        with open(_abs(MANIFEST), 'rb') as f:
            on_disk = json.loads(f.read().decode('utf-8'))
        fam_fail, catalog = _family_findings(family, on_disk)
        for d in drift + fam_fail:
            print(f'  FAIL {d}')
        if drift or fam_fail:
            print(f'build-identity: FAIL ({len(drift)} of {len(outputs)} file(s) drifted, {len(fam_fail)} family finding(s))')
            return 1
        if catalog != 'match':
            print(f'build-identity: COULD NOT VERIFY ({len(outputs)} file(s) match and the family copy holds; '
                  'the catalog half of the family check could not run - set FMS_CONTRACTS_ROOT)')
            return 2
        print(f'build-identity: PASS ({len(outputs)} file(s) match; family {family.family_sha256()[:12]} matches the catalog)')
        return 0

    written = 0
    for rel, data in outputs:
        path = _abs(rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if os.path.exists(path):
            with open(path, 'rb') as f:
                if f.read() == data:
                    continue
        with open(path, 'wb') as f:
            f.write(data)
        written += 1
    print(f'build-identity: PASS ({written} of {len(outputs)} file(s) written)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
