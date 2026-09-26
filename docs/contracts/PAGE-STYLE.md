# Pointer - `PAGE-STYLE`

| | |
| --- | --- |
| **Id** | `PAGE-STYLE` |
| **Version** | 1.1, active. Owner: the sza.od.ua hub |
| **Home** | `product-web-pages/PAGE-STYLE.md` in the shared contracts catalog |
| **Role here** | consumer - the product site's landing and sideload pages |

## What this repository must do to stay conformant

- The kit's sticky header, the RU / EN / UA language switcher with no flags, and the theme toggle.
- The pre-paint theme and language resolver, so neither flashes on load.
- The kit's fonts and tokens, 44 px touch targets, `prefers-reduced-motion`.
- Monochrome `ICON-SET` glyphs beside labels, never emoji.
- Walk the per-site acceptance checklist (section 11) before publishing a page change.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- `index.html`, `nolegal.html` and their `-ru` / `-uk` translations at the repository root.

## Gate

- `scripts/quality/assert-page-style.ps1` (release scope): the UA switcher label, the theme pre-paint before the first stylesheet, and the reference kit byte-identical or its dated exception open in the registry.
