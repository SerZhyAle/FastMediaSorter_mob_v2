# Phase 01 - Surface Extraction

**Strategic spec:** [`../S0543_features-inventory-docs-audit.md`](../S0543_features-inventory-docs-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4

---

## Objective

Produce a repeatable, READ-ONLY scan that pairs the inventory with the real code surface, tagged by area/module, as seed worksheets for the Phase 02 audit. Reuse the class catalog rather than building a noisy string/menu parser.

---

## Design note

Catalog `role`/function-`description` fields are largely empty, so a monolithic "signal emitter" over strings/menus would be noisy. Leaner, higher-signal choice: `scan_surface.ps1` joins `docs/ALL_FEATURES.jsonl` (inventory by area) with `dev/CATALOG/app_v2.jsonl` (classes by module = layer/feature), emitting per-area worksheets + a coverage seed. The per-area agents (Phase 02) do the deep code scan with `query.ps1`/Grep/Read - the scan tool just focuses them.

---

## Steps

### Step 01.1 - Inventory existing signal sources

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Sources confirmed: catalog `dev/CATALOG/app_v2.jsonl` (1896 records, fields path/class/layer/loc/role/functions; role mostly empty), inventory `docs/ALL_FEATURES.jsonl` (344/25 areas), settings manifest `docs/settings/settings-manifest.json` (S0440, 165 entries), site `index*.html`+`nolegal*.html` at repo root. BuildConfig `FEATURE_*` grep empty - flavor gating is not via simple buildConfigField flags; deferred to per-agent flavor verification.

---

### Step 01.2 - Write the surface-scan script

**Files:** `scripts/all_features/scan_surface.ps1` (new)

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Wrote read-only `scan_surface.ps1`. Emits `temp/s0543/{inventory_by_area.json, catalog_modules.json, coverage_seed.txt}`. Re-runnable, deterministic.

---

### Step 01.3 - Derive per-flavor availability

**Status:** `[x]` done (delegated)

**Step Log:**

- 2026-06-19 - Mechanical flavor derivation deferred to per-agent verification: inventory records already carry `flavors`; agents confirm against `src/<flavor>` source sets + gating. Avoids guessing where BuildConfig gating is indirect.

---

### Step 01.4 - Baseline scan + coverage summary

**Status:** `[x]` done

**Step Log:**

- 2026-06-19 - Ran scan: inventory=344 areas=25 classes=1896 modules=121 idHygiene=7. Coverage seed written. Notable signals: `ui/player`=317 classes (whole viewer family), `domain/game`+`ui/game`=42 classes with NO inventory area (Wave 2 investigate), 7 spec-prefixed ids (General/Quick Capture/File Saving).

---

## Phase Done Criteria

- [x] Steps 01.1-01.4 are `[x]`.
- [x] `scan_surface.ps1` committed and re-runnable.
- [x] Dev log entry added for `scan_surface.ps1`.

---

## Handoff Notes

`temp/s0543/inventory_by_area.json` + `catalog_modules.json` are the join inputs for Phase 02. Signals are HINTS - Phase 02 verifies against code before any inventory write. id-hygiene defects (7) and the `game` module are explicit Phase 02 inputs.
