"""The wide-rectangle lockup of the family mark: the mark at the leading third, the name after it (S4784).

PRODUCT-IDENTITY 0.2 rule 27: one typeface for the family, Manrope ExtraBold, embedded as outlines, so the output needs
no installed font and does not depend on the machine. The glyphs are turned into SVG path data with fontTools and the
whole lockup is one SVG that PyMuPDF rasterizes - no gradient, which MuPDF renders as black (rule 20).
"""
import os

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

from identity_family import paths

FONT = 'assets/identity/font/Manrope-ExtraBold.ttf'
NAME = 'Fast Media Sorter'
# The 240-unit square the family mark fits, and one arrow-head length of the full form (construction.txt: L=64),
# which rule 27 makes the clear space around a lockup.
MARK_SPAN = 240
MARK_ORIGIN = 96
HEAD = 64


class Lockup:
    def __init__(self, repo):
        self.font = TTFont(os.path.join(repo, FONT.replace('/', os.sep)))
        self.glyphs = self.font.getGlyphSet()
        self.cmap = self.font.getBestCmap()
        self.upm = self.font['head'].unitsPerEm
        self.cap = self.font['OS/2'].sCapHeight or round(self.upm * 0.7)

    def text_width(self, text, size):
        hmtx = self.font['hmtx']
        return sum(hmtx[self.cmap[ord(ch)]][0] for ch in text) * size / self.upm

    def text_path(self, text, size, x, baseline):
        """SVG path data of text set at size px with its baseline at y = baseline."""
        pen = SVGPathPen(self.glyphs)
        scale = size / self.upm
        hmtx = self.font['hmtx']
        for ch in text:
            name = self.cmap[ord(ch)]
            self.glyphs[name].draw(TransformPen(pen, (scale, 0, 0, -scale, x, baseline)))
            x += hmtx[name][0] * scale
        return pen.getCommands()

    def card(self, master_svg, width, height, plate, ink):
        """A wide rectangle: plate, the mark on the leading side, the name after it, centred as one group."""
        mark = round(height * 0.5)
        unit = mark / MARK_SPAN
        gap = HEAD * unit
        size = height * 0.16
        room = width - 2 * gap - mark - gap
        if self.text_width(NAME, size) > room:
            size = size * room / self.text_width(NAME, size)
        text_w = self.text_width(NAME, size)
        left = (width - (mark + gap + text_w)) / 2
        top = (height - mark) / 2
        body = [f'<rect x="0" y="0" width="{width}" height="{height}" fill="{plate}"/>',
                f'<g transform="translate({left - MARK_ORIGIN * unit:.4f} {top - MARK_ORIGIN * unit:.4f}) scale({unit:.6f})">']
        body += [f'<path fill="{fill}" d="{d}"/>' for fill, d in paths(master_svg)]
        body.append('</g>')
        baseline = height / 2 + self.cap * size / self.upm / 2
        body.append(f'<path fill="{ink}" d="{self.text_path(NAME, size, left + mark + gap, baseline)}"/>')
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
                f'viewBox="0 0 {width} {height}">\n' + '\n'.join(body) + '\n</svg>\n')
