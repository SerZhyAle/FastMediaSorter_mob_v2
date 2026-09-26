# Pointer - `PAGE-CONTENT`

| | |
| --- | --- |
| **Id** | `PAGE-CONTENT` |
| **Version** | 1.1, active. Owner: the sza.od.ua hub |
| **Home** | `product-web-pages/PAGE-CONTENT.md` in the shared contracts catalog |
| **Role here** | consumer - the product site's landing and sideload pages |

## What this repository must do to stay conformant

- Keep the mandatory page order for apps: the hero begins the explanation above the fold.
- Keep the non-commercial position and the copy rules - no emoji in place of a glyph.
- Take every stated fact from its source (release link fetched, never hardcoded).
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- Landing pages: `index.html` and its `-ru` / `-uk` translations at the repository root.
- Sideload pages: `nolegal.html` and its `-ru` / `-uk` translations.
- Out of scope: `broadcast-import.html`, the noindex fallback of the live-broadcast link, which hands a payload to the app and is not a page a visitor reads.

## Gate

- `scripts/quality/assert-page-content.ps1` (release scope): no emoji in markup or inline scripts, escaped forms included, and the order sticky header with a `#get` link, H1 with tagline and lead, `#get`, family footer.
