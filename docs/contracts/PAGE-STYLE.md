<sub class="doc-stamp">26.10.29 05:03</sub>

# Pointer - `PAGE-STYLE`

| | |
| --- | --- |
| **Id** | `PAGE-STYLE` |
| **Version** | 3.5, active. Owner: the sza.od.ua hub |
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
- Fill the screen (1.3, section 5 "Width"): `--wide` is `100%`, no page wrapper carries a `max-width` or a centred `margin: auto`; the hero opens with the identity block (section 4.1b) and the header brand is text only. The 1920 / 2560 px measurement is in the catalog's `product-web-pages/README.md` section 2 (S4106).
- Buttons follow `BUTTONS`, disclosure groups `COLLAPSIBLE-GROUP` and target sizes `UI-SIZING` (2.0 to 3.1); the
  vendored kit alone is a provenance check, not conformance, so a page links an identified adapter after it
  (section 4.6) and claims no rendered conformance without evidence.
- Monochrome `ICON-SET` glyphs beside labels, never emoji.
- The 3.3 kit revision (the kit draws the unified controls and is itself the adapter) is not vendored here: the
  product stylesheet carries the kit's tokens and components in its own layer, which is the kit exception open in
  the catalog's registry. The exchange server's picture is drawn unaltered with the `alt` of `EXCHANGE-MARK`
  rule 10 and never as an action glyph (3.4, section 9); the help layer of a help page stays beside the kit (3.2).
- Walk the per-site acceptance checklist (section 11) before publishing a page change.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- `index.html`, `nolegal.html` and their `-ru` / `-uk` translations at the repository root.
- The landing in each further locale, `index-<slug>.html`, written by `scripts/site/generate-landing-pages.ps1`, which also renders the further-locale row under the header.
- The documentation portal, `documentation/**/*.html`, written by `scripts/docs/generate-docs-pages.ps1`, `generate-glossary.ps1` and `generate-subject-index.ps1`. Its pages carry the section 7 resolver and the `sza-lang` writer; its 13-locale picker is not yet the section 4.2 secondary row.
- The `docs/` reference pages render in the portal shell (owner decision, S4104; every group moved by S4117): `_layouts/doc.html` is the Jekyll copy of the portal chrome. Its header menu offers the page's real RU / EN / UA siblings, and a page with a further-locale sibling gets the section 4.2 text-only row under the header, built from `_data/languages.yml`. Only `docs/` files with no front matter (the developer notes) still render through the third-party `jekyll-theme-cayman` (`_config.yml`).

## Gate

- `scripts/quality/assert-page-style.ps1` (release scope, PAGE-STYLE 1.2): the UA switcher label, the pre-paint before the first stylesheet validating `sza-theme` and reading `sza-lang` with `uk` mapped to `ua`, a script writing `sza-lang`, `data-lang` limited to ru / en / ua, every further-locale link above the H1 and without `data-lang`, and the reference kit byte-identical or its dated exception open in the registry; `_layouts/doc.html` is judged as one of its pages.
- `scripts/quality/assert-docs-portal-ui-ux.ps1` (per ticket, the documentation portal and `_layouts/doc.html`): the section 7 pre-paint validating `sza-theme` and reading `sza-lang` with `uk` mapped to `ua`, and a `sza-lang` writer on every page that offers `data-lang` links.
