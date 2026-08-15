# Phase 2 - SVG asset export (VectorDrawable -> currentColor SVG)

**Goal:** produce lightweight web SVGs for every `vector` icon in the inventory, under `docs/icons/svg/`, colour-neutral so they render correctly against the site's light/dark themes.

## Preconditions / references

- Consumes `docs/icons/icon-inventory.json` (phase 1).
- Source drawables: `app_v2/src/main/res/drawable/ic_*.xml` (VectorDrawable; ~214 vectors).
- Tint problem: sources are "Tinted at usage site" with placeholder `fillColor="@android:color/white"` (`ic_add.xml:2,9`) - must NOT bake white into the SVG.

## Steps

1. [ ] Create `scripts/docs/export-icon-svgs.ps1`. Input: `docs/icons/icon-inventory.json`; for each entry with `assetFormat=vector`, read `app_v2/src/main/res/drawable/<drawable>.xml`.
   - Verification: script enumerates only inventory `vector` drawables (not all 214), resolves each source XML path, errors clearly if a source is missing.
2. [ ] Convert VectorDrawable -> SVG: map root `android:viewportWidth/Height` -> SVG `viewBox`; map each `<path android:pathData="..">` -> `<path d="..">`. Replace every `android:fillColor` (placeholder white or literal) with `fill="currentColor"`; carry `android:fillAlpha` -> `fill-opacity`, `android:fillType` -> `fill-rule`. Drop Android-only attrs.
   - Verification: a spot-check icon (e.g. `ic_cast`) produces valid SVG that renders in a browser and inherits text colour (no hard-coded fill).
3. [ ] Handle non-vector inventory entries:
   - `assetFormat=raster` (3 cloud PNGs): copy `res/drawable/<name>.png` -> `docs/icons/svg/<name>.png` verbatim (brand logos).
   - `assetFormat=framework` (8 PlayerCommands): skip - no asset emitted (legend renders name + note).
   - Verification: no framework icon produces a file; each raster icon is copied.
4. [ ] Emit SVGs to `docs/icons/svg/<drawable>.svg`, deterministic (stable attribute order, trailing newline) so the drift gate can byte-diff.
   - Verification: re-running the script with no source change produces a byte-identical tree (git diff empty).
5. [ ] Keep assets lightweight (§3.2): no embedded raster in SVG, no metadata bloat; each SVG is path-only.
   - Verification (auto-build - PASS): `pwsh -NoProfile -File scripts/docs/export-icon-svgs.ps1` exits 0; `docs/icons/svg/` populated; sample SVG opens and is < a few KB.

## Notes

- `android:pathData` and SVG `d` share the same command grammar (M/L/C/Q/A/Z, relative variants) - a direct string copy works for the vast majority; only colour/alpha/fillType need translation. Do not attempt gradient/clip-path translation - if any inventory icon uses `<gradient>`/`<clip-path>`, log it and skip with a warning (none expected among the simple `ic_*` set; verify).
- currentColor rationale in `research/03` D2.
