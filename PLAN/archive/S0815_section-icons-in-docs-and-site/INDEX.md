# S0815 - Tactical plan: Section icons in docs & site

**Ticket:** S0815
**Status:** Tactical
**Strategic spec:** `PLAN/S0815_section-icons-in-docs-and-site.md`
**Complexity:** Full (4 phases; touches app test-sources + new docs-tooling + trilingual docs + a new quality gate)

## Goal (RU)

Собрать единый инвентарь узнаваемых иконок интерфейса из уже существующих реестров приложения, экспортировать его в лёгкие SVG-ассеты и опубликовать трилингвальную страницу-легенду (EN/RU/UK) рядом с сайтом, плюс drift-гейт, не дающий доке разойтись с экраном. Итерация 1 - легенда + инвентарь + ассеты + гейт; инлайн-встраивание иконок в лендинг/howto отложено на итерацию 2 (явно задокументировано).

## Design decisions (from research/03)

- Inventory = live-scan generator -> `docs/icons/icon-inventory.json` (mirror settings-manifest system). [D1]
- Assets = SVG `fill="currentColor"` under `docs/icons/svg/`. [D2]
- Embedding iteration 1 = central trilingual legend page; inline deferred. [D3/D5]
- Meaning prose = hand-authored trilingual `docs/icons/icon-annotations.json`. [D4]
- Convention "icons instead of emoji" in `docs/icons/README.md`; no ban-gate yet. [D6]

## Phases

1. `phase1_inventory-manifest.md` - registry accessors + Robolectric export test -> `icon-inventory.json`.
2. `phase2_svg-asset-export.md` - VectorDrawable->SVG (`currentColor`) exporter -> `docs/icons/svg/`.
3. `phase3_legend-and-annotations.md` - trilingual annotations sidecar + legend renderer -> `docs/ICON_LEGEND*.md`.
4. `phase4_drift-gate-and-convention.md` - `assert-icon-inventory-sync.ps1` + `post-change` wiring + convention/scope docs.

## Ordering / dependencies

Phase 1 -> 2 -> 3 are strictly sequential (each consumes the prior output). Phase 4 (gate) depends on 1-3 existing. Phases 1-2 are the autonomous core; phase 3 is the trilingual content; phase 4 locks it against drift.

## Out of scope (iteration 2 / deferred)

- Emoji -> icon swap in `index*.html` cards, `docs/howto/index.md`, `docs/DOCS_MAP.md` headers.
- Inline per-row icons inside generated `docs/SETTINGS_REFERENCE*.md`.
- Mechanical emoji-ban gate.
- noLegal-only icon legend variant (VR) - excluded from the public inventory.

## Scan-scope guards (from research/01)

- Exclude noLegal-only icons (VR: `ic_vr_headset`) via `public=false` tag.
- 8 framework-icon PlayerCommands: `assetFormat=framework`, no SVG.
- 3 cloud-provider PNGs: `assetFormat=raster`.
- `ResourceIconRegistry` (cosmetic palette) and `ConnectionBadgeMapper` excluded.
- Send-to package-backed receivers: document the stable fallback glyph; note runtime substitution.
