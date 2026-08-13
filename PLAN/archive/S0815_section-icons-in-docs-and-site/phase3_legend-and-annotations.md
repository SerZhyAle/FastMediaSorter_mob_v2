# Phase 3 - Trilingual legend page (meanings sourced from app strings)

**Goal:** publish `docs/ICON_LEGEND.md` / `_RU.md` / `_UK.md` - a single source-of-truth page pairing every public icon with its on-screen meaning in EN/RU/UK, rendered deterministically from the inventory + the app's OWN trilingual strings (no hand-authored per-icon translations).

## Design correction (vs original plan)

The inventory's `feature` field is the entry name of the app string resource that labels the icon (verified: `cast_to_chromecast` -> "Cast to.."/"Трансляция..", `menu_crop` -> "Crop"/"Вырезать"). So the icon's meaning IS its live UI label. The renderer resolves `feature` -> the real string in `values/`, `values-ru/`, `values-uk/strings.xml`. This gives accurate, already-translated, drift-free meanings and satisfies ADR-1 (derived from the app, not a second source of truth). A hand-authored sidecar is needed ONLY for: surface headings, and the handful of `feature` values that are not string keys (resource-type enum names).

## Preconditions / references

- Consumes `docs/icons/icon-inventory.json` (phase 1) + `docs/icons/svg/` (phase 2, 58 SVGs).
- App strings: `app_v2/src/main/res/values{,-ru,-uk}/strings.xml`.
- Sidecar (hand-authored, small): `docs/icons/icon-annotations.json` - surface headings + non-string-key fallbacks.
- Precedent: `scripts/docs/render-settings-reference.ps1`; frontmatter injector `scripts/utils/update_docs_frontmatter.ps1`.
- Asset facts (phase 2 handoff): 58 icons render as `<img src="icons/svg/<drawable>.svg">`; 8 framework icons have no asset (render name + note); `ic_vr_headset` is excluded (public=false).

## Steps

1. [x] Author `docs/icons/icon-annotations.json` (I own this - correctness-critical RU/UK). Contains: `surfaces` = per-surface `{ en, ru, uk }` heading; `resourceTypes` = `{ en, ru, uk }` for the 7 resource-type enum-name features (LOCAL/SMB/SFTP/FTP/CLOUD/HTTP_STREAM/RTSP_STREAM); `overrides` = per-key `{ en, ru, uk }` map for any entry whose live string reads poorly out of context (start empty).
   - Verification: JSON parses; 5 surface headings present in all 3 locales; 7 resource-type labels present.
2. [ ] Create `scripts/docs/render-icon-legend.ps1`. For each locale L in {en, ru, uk}: build key->string maps from the matching `strings.xml`. Group public inventory entries by `surface` (heading from sidecar). For each entry, meaning(L) = `overrides[key][L]` if present, else `resourceTypes[feature][L]` if `feature` is a resource-type name, else `strings[L][feature]` if resolvable, else a humanized `drawable` name + a logged WARNING.
   - Row rendering: `vector` -> `<img src="icons/svg/<drawable>.svg" alt="<meaning>" width="24" height="24"> <drawable> - <meaning>`; `framework` -> `<drawable> (system icon) - <meaning>` (no img); never icon-only (accessibility, §3.2).
   - Emit EN -> `docs/ICON_LEGEND.md`, RU -> `docs/ICON_LEGEND_RU.md`, UK -> `docs/ICON_LEGEND_UK.md`. Deterministic (stable order = inventory order; single trailing newline) for the phase-4 byte-diff gate.
   - Report any entry that fell through to the humanized fallback (coverage gap to close via `overrides`).
   - Verification: three files generated; each has the same public-entry row count (117); byte-identical on re-run; every `<img>` src resolves to an existing `docs/icons/svg/*.svg`.
3. [ ] Add Jekyll frontmatter to the three pages (layout/title/permalink), via the renderer or `update_docs_frontmatter.ps1`.
   - Verification: each file starts with `---`; renders under the theme.
4. [ ] Link the legend from `docs/DOCS_MAP.md` (EN/RU/UK entries) as a user-facing page.
   - Verification: Grep confirms `ICON_LEGEND` reference for all three locales.
5. [ ] Generate committed `docs/ICON_LEGEND*.md`.
   - Verification (auto-build - PASS): `pwsh -NoProfile -File scripts/docs/render-icon-legend.ps1` exits 0; three consistent pages; zero unresolved-fallback warnings (or each remaining one added to `overrides`).

## Notes

- If a `feature` string exists in EN but is missing in RU/UK, the renderer uses EN + logs it (should not happen given the string-parity gate, but be robust).
- Style in authored strings: `..` not `...`, plain hyphen, Ё where grammatical (docs/UI prose rule).
