# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0377_resource-menu-open-launch-reorder.md`](../S0377_resource-menu-open-launch-reorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Finalize: regenerate the class catalog, update trilingual FEATURES docs, and confirm the dev log covers every modified file.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ +2 |
| `docs/FEATURES_RU.md` | Modified | ≤ +2 |
| `docs/FEATURES_UK.md` | Modified | ≤ +2 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 04.1 - Update FEATURES trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one concise bullet (EN/RU/UK mirrors) under the resource-management feature area describing the resource dropdown menu actions: open, launch player for media libraries, move to very top / very bottom, plus the visible tappable-icon frame on media-library tiles. Use the `/doc-update` skill so the three mirrors stay in lockstep. Apply `..` and `ё` style rules.

**Verification:**

- `Grep` - the new bullet present in all three FEATURES files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification PASS: new bullet present in EN/RU/UK FEATURES (1 each). Tag `[Standard / Lite / Photos / Legacy / VR]` (resource list + menu shared across all flavors). Author style applied (no `...`, ё where correct). Dev log recorded for all 3 mirrors.

---

### Step 04.2 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan and re-render the catalog after the Kotlin changes in `ResourceOrderManager`, `MainViewModel`, `ResourceAdapter`, `MainActivity`.

**Verification:**

- Command exits 0.
- `Grep` - `moveResourceToTop` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 2/2 PASS: catalog_sync exit 0 (1656 records), `moveResourceToTop` = 2 hits in app_v2.jsonl (ResourceOrderManager + MainViewModel). Catalog is gitignored - regenerated, not committed.

---

### Step 04.3 - Confirm dev log coverage

**Files:** (verification only)
**Depends on:** Step 04.2

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry for every file modified across Phases 01-03 (ResourceOrderManager, MainViewModel, resource_item_actions.xml, the three strings.xml, ResourceAdapter, MainActivity, bg_icon_media_storage_frame.xml). Add any missing entries via `.\scripts\add_to_dev_log.ps1`. Append one functionality-log line: `.\scripts\add_to_functionality_log.ps1 -Id S0377 -Op ADD -Description "Resource dropdown: Open/Launch, move to top/bottom, media-library icon frame"`.

**Verification:**

- `Grep` - `S0377` or the touched filenames present in `dev/CHANGELOG.md`.
- `Grep` - the functionality-log line present in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-07 - Verification 2/2 PASS: dev log covers all 9 touched source files (Phase 01-03) plus 3 FEATURES mirrors; functionality-log line `[S0377] [ADD]` present in dev/FUNCTIONALITY.log (timestamp 04:18).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] All four phases show ✅ Done in INDEX.md.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After this, the spec moves to `BlockNeedUserTest` for on-device verification (menu items, launch behaviour, move-to-edge persistence, icon frame in grid and list).

---

## Rollback Plan

Docs and catalog only - revert the FEATURES edits; the catalog regenerates from source.
