<sub class="doc-stamp">26.11.20 20:19</sub>

# Pointer - `ICON-SET`

| | |
| --- | --- |
| **Id** | `ICON-SET` |
| **Version** | 0.45, draft. Owner: this product |
| **Home** | `iconography/README.md` section 2 in the shared contracts catalog |
| **Role here** | owner and reference implementation - phone, launcher, watch, documentation and the website |

## What this repository must do to stay conformant

- A control that stands for a meaning in the vocabulary draws that meaning's glyph and carries its
  canonical EN/RU/UK name; a label may add the object, never another meaning's word.
- A new meaning enters the vocabulary before the code that shows it ships - or, since 0.30 (section 15),
  the product draws it to the catalog's style, ships it under its proposed id and files
  `PROPOSAL-<date>-<topic>.md` in the same change, marked provisional in its own glyph list until the
  owner folds it in. A meaning that exists is drawn with its own glyph, never a second one.
- A language beyond en/ru/uk has one wording per meaning: this product's own string where it has one
  (rule 3, 0.28); the import was made by S4131 and the diverging translations it refused were reconciled by S4132.
- `tool.brush` (a brush) and `feature.drawing` (the drawing editor) are two meanings: the brush tool draws
  the brush and the drawing editor draws the free-hand loop (S4125).
- The exchange screens draw the glyphs of section 17 (0.32, S4221): `source.exchange-server`,
  `feature.server-relay`, `action.connect-device`, `action.send-clipboard`, `action.receive-clipboard`,
  `content.invitation` and `content.guest`, each under the canonical name of `DEVICE-EXCHANGE` 25 item CW. The
  path "via Google Drive" shows `brand.google-drive`, and a one-tone surface shows the words, never a drawn
  stand-in. The drawables are drawn into the product by S4237.
- A retry control draws `action.retry` (0.33, S4255, section 18) and reads "Повторить попытку" /
  "Повторити спробу" in ru/uk; the bare "Повторить" / "Повторити" stays the name of `action.redo`.
- The help action draws `app.help` and reads its normative name in every shipped locale (0.34).
- Six meanings enter as `proposed` records with this product's own drawings, for its sibling-set migration under
  `ICON-RENDER` section 25 (0.40, section 26, S4570): `view.compact`, `app.animations`, `app.units`,
  `media.visualizer`, `view.full-resolution` and `view.halo`. Each turns `active` on the first shipped report
  (rule 6), and no existing record's picture, name or `distinct` set changes.
- Open (S4804): 0.42 enters twelve `proposed` meanings for the settings destinations of the Share Manager, the Exchange
  Server window and the remote administration window (Windows surfaces), 0.43 replaces the cog drawings of
  `system.service` and `feature.remote-admin`, and 0.44 (breaking) redraws `app.logo` from the FastMediaSorter family
  mark of `PRODUCT-IDENTITY` 0.2 section 3.3; the exporter run and the `app.logo` carrier are owed by this product.
- The 0.45 catalog amendment redraws `action.rename`. The product carrier is tracked separately.
- The documentation and the site show the meaning's glyph beside its name - never an emoji, never a
  picture chosen to decorate the page.
- The glyphs, looks, `palette.json`, `CATALOG.md` and `gallery.html` are regenerated from this
  repository and never edited in the catalog by hand.
- The deviations currently open are recorded as exceptions in the catalog's registry.

## Where it lives here

- Export (rung 1): `scripts/docs/export-icon-contract.ps1`, byte-identical on a rerun; its `-Check` mode (0.39)
  compares every generated file with what a run would write, names each that is stale, missing or extraneous, and
  writes nothing.
- Product declaration against the vocabulary: `docs/icons/icon-contract-map.json`.
- Rungs 2, 3 and 5: `scripts/quality/assert-icon-contract.ps1` - unmapped glyphs, labels against
  glyphs, name substitutions, docs pictures - with its baseline `scripts/quality/icon-contract-baseline.txt`.
- Documentation and site icons: `docs/icons/doc-icon-map.json`, held by
  `scripts/quality/assert-doc-icons-sync.ps1`.
- Legends: `docs/ICON_LEGEND*.md` for the phone, `docs/wear/ICON_LEGEND*.md` for the watch
  (`scripts/docs/render-wear-icon-legend.ps1`).

