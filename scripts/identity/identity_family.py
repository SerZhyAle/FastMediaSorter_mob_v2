"""The FastMediaSorter family master folder as the identity generator's input (PRODUCT-IDENTITY 0.2 section 3.3, S4784).

Reads the byte-for-byte copy in assets/identity/family/ that scripts/identity/sync-family-mark.ps1 writes; this module
never writes there. A product master is composed the way the catalog's reference build_family_mark.py composes its
review sheet: the signifier's and the badge's <path> elements are inserted before </svg> of a family master, so no
shape is redrawn here (rule 17, section 0 item 5 of S4784).
"""
import hashlib
import json
import os
import re

FAMILY_DIR = 'assets/identity/family'
FAMILY_MANIFEST = f'{FAMILY_DIR}/family-mark.json'
HINTED = (16, 20, 24, 32)
CATALOG_SUBDIR = ('product-identity', 'family', 'fastmediasorter', 'family-mark.json')

_PATH_RE = re.compile(r'<path fill="(#[0-9A-Fa-f]{6})" d="([^"]+)"/>')


class Family:
    def __init__(self, repo):
        self.repo = repo
        self.manifest_bytes = self._read(FAMILY_MANIFEST)
        self.manifest = json.loads(self.manifest_bytes.decode('utf-8'))
        tokens = json.loads(self.read_text('tokens.json'))['tokens']
        self.light = {k: v['light'] for k, v in tokens.items()}
        self.dark = {k: v['dark'] for k, v in tokens.items()}

    def _read(self, rel):
        with open(os.path.join(self.repo, rel.replace('/', os.sep)), 'rb') as f:
            return f.read()

    def read_text(self, rel):
        return self._read(f'{FAMILY_DIR}/{rel}').decode('utf-8')

    def copy_findings(self):
        """Every local file whose SHA-256 differs from the one family-mark.json records."""
        out = []
        for rel, sha in sorted(self.manifest['files'].items()):
            try:
                data = self._read(f'{FAMILY_DIR}/{rel}')
            except FileNotFoundError:
                out.append(f'missing {FAMILY_DIR}/{rel}')
                continue
            if hashlib.sha256(data).hexdigest() != sha:
                out.append(f'differs from its recorded hash: {FAMILY_DIR}/{rel}')
        return out

    def family_sha256(self):
        return hashlib.sha256(self.manifest_bytes).hexdigest()

    def catalog_check(self):
        """'match', 'differs' or 'could-not-verify' - the local family-mark.json against the catalog's (rung 8)."""
        root = os.environ.get('FMS_CONTRACTS_ROOT') or _user_env('FMS_CONTRACTS_ROOT')
        if not root:
            return 'could-not-verify'
        path = os.path.join(root, *CATALOG_SUBDIR)
        if not os.path.exists(path):
            return 'could-not-verify'
        with open(path, 'rb') as f:
            return 'match' if f.read() == self.manifest_bytes else 'differs'

    def product_master(self, dark=False, badge=None):
        """The full form plus the Android signifier, and a badge SVG text when given.

        The dark master swaps the signifier's light shade for the dark one (ADR-7 of S4784): rule 22 draws the
        signifier in the shade tone, and the light shade reads 1.76:1 on the dark plate.
        """
        svg = self.read_text('masters/mark-full-dark.svg' if dark else 'masters/mark-full.svg')
        signifier = self.read_text('signifiers/android.svg')
        if dark:
            signifier = signifier.replace(self.light['family.blue.shade'], self.dark['family.blue.shade'])
        svg = insert(svg, signifier)
        if badge:
            svg = insert(svg, badge)
        return svg

    def badge(self, name):
        return self.read_text(f'badges/{name}.svg')

    def hinted(self, px):
        return self.read_text(f'masters/mark-small-{px}.svg')

    def small(self, dark=False):
        return self.read_text('masters/mark-small-dark.svg' if dark else 'masters/mark-small.svg')

    def mono(self, full=False):
        return self.read_text('masters/mark-mono-full.svg' if full else 'masters/mark-mono.svg')


def insert(base_svg, over_svg):
    """Append the drawable elements of over_svg (its third line on) to base_svg before </svg>."""
    body = over_svg.split('\n', 2)[2].rsplit('</svg>', 1)[0]
    return base_svg.replace('</svg>', body + '</svg>')


def paths(svg):
    """(fill, d) of every flat path of a family SVG, in document order."""
    return _PATH_RE.findall(svg)


def view(svg, x, y, w, h, width, height):
    """The same drawing with another viewBox and pixel size - how a plated or padded cut is made."""
    svg = re.sub(r'viewBox="[^"]*"', f'viewBox="{x:g} {y:g} {w:g} {h:g}"', svg, count=1)
    svg = re.sub(r'width="[^"]*" height="[^"]*"', f'width="{width}" height="{height}"', svg, count=1)
    return svg


def _user_env(name):
    """A user-scope variable set after this process's parent started (Windows only)."""
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, 'Environment') as key:
            return winreg.QueryValueEx(key, name)[0]
    except (ImportError, OSError):
        return None
