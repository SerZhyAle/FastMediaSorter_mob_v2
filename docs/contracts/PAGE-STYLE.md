<sub class="doc-stamp">26.10.02 23:35</sub>

# Pointer - `PAGE-STYLE`

| | |
| --- | --- |
| **Id** | `PAGE-STYLE` |
| **Version** | 1.2, active. Owner: the sza.od.ua hub |
| **Home** | `product-web-pages/PAGE-STYLE.md` in the shared contracts catalog |
| **Role here** | consumer - the product site's landing and sideload pages, and the documentation portal (per-language documentation pages, section 6) |

## What this repository must do to stay conformant

- The kit's sticky header, the RU / EN / UA language switcher with no flags, and the theme toggle.
- The section 7 pre-paint resolver for theme and language, so neither flashes on load: `sza-lang` holds
  only `ru`, `en` or `ua`, `sza-theme` only `dark` or `light`, and a stored `uk` reads as `ua`.
- Locales beyond RU / EN / UA form a secondary text-only row below the header and above the H1, and are
  never written to `sza-lang` (section 4.2).
- A vendored kit file is marked `-text` and stays byte-identical; the page stylesheet is linked after
  it (sections 0 and 4.7).
- The kit's fonts and tokens, 44 px touch targets, `prefers-reduced-motion`.
- Monochrome `ICON-SET` glyphs beside labels, never emoji.
- Walk the per-site acceptance checklist (section 11) before publishing a page change.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- `index.html`, `nolegal.html` and their `-ru` / `-uk` translations at the repository root.
- The landing in each further locale, `index-<slug>.html`, written by `scripts/site/generate-landing-pages.ps1`, which also renders the further-locale row under the header.
- The documentation portal, `documentation/**/*.html`, written by `scripts/docs/generate-docs-pages.ps1`, `generate-glossary.ps1` and `generate-subject-index.ps1`, and the `_layouts/doc.html` Jekyll layout. Its pages carry the section 7 resolver and the `sza-lang` writer; its 13-locale picker is not yet the section 4.2 secondary row.

## Gate

- `scripts/quality/assert-page-style.ps1` (release scope, PAGE-STYLE 1.2): the UA switcher label, the pre-paint before the first stylesheet validating `sza-theme` and reading `sza-lang` with `uk` mapped to `ua`, a script writing `sza-lang`, `data-lang` limited to ru / en / ua, every further-locale link above the H1 and without `data-lang`, and the reference kit byte-identical or its dated exception open in the registry.
- `scripts/quality/assert-docs-portal-ui-ux.ps1` (per ticket, the documentation portal): the section 7 pre-paint validating `sza-theme` and reading `sza-lang` with `uk` mapped to `ua`, and a `sza-lang` writer on every page that offers `data-lang` links.
