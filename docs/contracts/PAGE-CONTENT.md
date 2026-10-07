<sub class="doc-stamp">26.10.07 04:59</sub>

# Pointer - `PAGE-CONTENT`

| | |
| --- | --- |
| **Id** | `PAGE-CONTENT` |
| **Version** | 1.4, active. Owner: the sza.od.ua hub |
| **Home** | `product-web-pages/PAGE-CONTENT.md` in the shared contracts catalog |
| **Role here** | consumer - the product site's landing and sideload pages |

## What this repository must do to stay conformant

- Keep the mandatory page order for apps: the hero begins the explanation above the fold.
- Carry the seven things of "The landing always carries" (1.3): the application icon and the full name in display type, the language switchers, a link for every way to get the product, links to the portal and the documentation, the author's links and the other projects in the footer, and the full width of the screen. Gaps are exceptions in the registry (S4106).
- Keep the non-commercial position and the copy rules - no emoji in place of a glyph.
- Take every stated fact from its source (release link fetched, never hardcoded).
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- Landing pages: `index.html` and its `-ru` / `-uk` translations at the repository root.
- Sideload pages: `nolegal.html` and its `-ru` / `-uk` translations.
- Full width also reaches the documentation portal (`documentation/`) and the `docs/` reference pages, whose theme column `assets/css/style.scss` uncaps.
- Out of scope: `broadcast-import.html`, the noindex fallback of the live-broadcast link, which hands a payload to the app and is not a page a visitor reads.

## Gate

- `scripts/quality/assert-page-content.ps1` (release scope): no emoji in markup or inline scripts, escaped forms included, and the order sticky header with a `#get` link, H1 with tagline and lead, `#get`, family footer.
- `scripts/quality/assert-page-style.ps1` (release scope), finding WIDTH: no `max-width` cap on a page wrapper in `styles.css`, `documentation/assets/docs.css` or `assets/css/style.scss` (the `docs/` theme override), and no inline cap on `main` (S4106).
