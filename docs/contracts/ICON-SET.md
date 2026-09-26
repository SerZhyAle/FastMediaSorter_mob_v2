# Pointer - `ICON-SET`

| | |
| --- | --- |
| **Id** | `ICON-SET` |
| **Version** | 0.13, draft. Owner: this product |
| **Home** | `iconography/README.md` section 2 in the shared contracts catalog |
| **Role here** | owner and reference implementation - phone, launcher, watch, documentation and the website |

## What this repository must do to stay conformant

- A control that stands for a meaning in the vocabulary draws that meaning's glyph and carries its
  canonical EN/RU/UK name; a label may add the object, never another meaning's word.
- A new meaning enters the vocabulary before the code that shows it ships.
- The documentation and the site show the meaning's glyph beside its name - never an emoji, never a
  picture chosen to decorate the page.
- The glyphs, looks, `palette.json`, `CATALOG.md` and `gallery.html` are regenerated from this
  repository and never edited in the catalog by hand.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- Export (rung 1): `scripts/docs/export-icon-contract.ps1`, byte-identical on a rerun.
- Product declaration against the vocabulary: `docs/icons/icon-contract-map.json`.
- Rungs 2, 3 and 5: `scripts/quality/assert-icon-contract.ps1` - unmapped glyphs, labels against
  glyphs, name substitutions, docs pictures - with its baseline `scripts/quality/icon-contract-baseline.txt`.
- Documentation and site icons: `docs/icons/doc-icon-map.json`, held by
  `scripts/quality/assert-doc-icons-sync.ps1`.
- Legends: `docs/ICON_LEGEND*.md` for the phone, `docs/wear/ICON_LEGEND*.md` for the watch
  (`scripts/docs/render-wear-icon-legend.ps1`).
