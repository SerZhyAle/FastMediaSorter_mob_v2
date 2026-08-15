# Phase 04 - Docs Catalog Cleanup

**Strategic spec:** [`../S0351_widget-audio-now-playing.md`](../S0351_widget-audio-now-playing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Close documentation, catalog, build, and final audit hygiene for S0351.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | <= 170 |
| `docs/FEATURES_RU.md` | Modified | <= 170 |
| `docs/FEATURES_UK.md` | Modified | <= 170 |
| `dev/CATALOG/app_v2.jsonl` | Generated | n/a |
| `dev/CATALOG/app_v2.md` | Generated | n/a |
| `PLAN/S0351_widget-audio-now-playing.md` | Modified | <= 140 |
| `PLAN/S0351_widget-audio-now-playing/INDEX.md` | Modified | <= 80 |

---

## Steps

### Step 04.1 - Update feature docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`

**Prompt for developer:**

> Add a Smart Widgets bullet for Audio Now Playing in EN/RU/UK and update the document dates to 2026-06-04.

**Verification:**

- `Grep` - Audio Now Playing entry appears in all three feature docs.
- `Grep` - date is `2026-06-04` in all three feature docs.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Audio Now Playing entries and `2026-06-04` dates are present in EN/RU/UK feature docs.

### Step 04.2 - Regenerate catalog and run builds

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`

**Prompt for developer:**

> Run catalog sync and build standard debug. Also build lite/photos debug or otherwise verify merged manifests because this phase changes those flavor overlays.

**Verification:**

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- Standard debug build exits 0.
- Lite/photos merged manifests do not contain `AudioNowPlayingWidgetProvider`; standard merged manifest contains it.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Catalog sync exited 0; standard, lite, and photos debug builds passed; merged manifests contain the provider only for standard.

### Step 04.3 - Mark implementation ready for audit

**Files:** `PLAN/S0351_widget-audio-now-playing.md`, `PLAN/S0351_widget-audio-now-playing/INDEX.md`

**Prompt for developer:**

> Set the strategic spec status to `Implemented` after phases complete, then run `/spec-check S0351`.

**Verification:**

- `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0351 -Status Implemented` exits 0.
- `Grep` - first `**Status:**` line is `Implemented` before audit.

**Status:** `[x]` done

**Step Log:**

- 2026-06-04 - Verification PASS. Strategic spec is ready for `Implemented` status and final `/spec-check S0351` audit.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `/spec-check S0351` records final audit in the strategic spec.

---

## Rollback Plan

Reopen the spec to `Tactical` and fix the failing step.
