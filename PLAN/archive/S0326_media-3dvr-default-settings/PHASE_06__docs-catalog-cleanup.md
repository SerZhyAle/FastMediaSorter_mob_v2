# Phase 06 - Docs & catalog cleanup

**Strategic spec:** [`../S0326_media-3dvr-default-settings.md`](../S0326_media-3dvr-default-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-01
**Completed:** 2026-06-01

> Done 2026-06-01: catalog regenerated (1556 records); FEATURES EN/RU/UK gained a `[VR Only]` "3D/VR default settings" bullet; FUNCTIONALITY.log ADD line written; dev logs recorded for all touched files. The VR settings classes are physically isolated in `src/vr` (no `set.ps1 -NoFlavors` needed - no new main-catalog class).

---

## Objective

Finalize the feature: regenerate the class catalog with flavor hints for VR-only classes, record the new user-visible capability in FEATURES (trilingual), and close dev-log/functionality-log entries.

---

## Prerequisites

- [ ] Phases 01–05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |

---

## Steps

### Step 06.1 - Regenerate catalog and set flavor hints

**Files:** catalog index (generated)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. For the VR-only classes introduced in Phase 05, set `-NoFlavors "standard,lite,photos,legacy"` via `set.ps1` so the catalog records that they exist only in `vr`/`noLegal`. Fill `role` + `status` for the new shared classes.

**Verification:**

- `dev/CATALOG/app_v2.jsonl` regenerated (mtime updated).
- `Grep` - the VR-only class entries carry the `NoFlavors` hint.

**Status:** `[ ]` not done

---

### Step 06.2 - Update FEATURES trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add one flavor-independent sentence per strategic §8: the user sets default 3D/VR behavior (format auto-detection, default mode, immersive behavior) in media settings, while per-file override still wins for individual files. Do not list VR-only specifics in the public files. Use `/doc-update` to keep EN/RU/UK mirrors consistent.

**Verification:**

- `Grep` - the new sentence (or its key phrase) exists in all three FEATURES files.
- No VR-only capability leaked into the public FEATURES files.

**Status:** `[ ]` not done

---

### Step 06.3 - Close logs

**Files:** dev log, functionality log (append-only)
**Depends on:** Step 06.2

**Prompt for developer:**

> Ensure a `dev/CHANGELOG.md` entry exists for every modified file across phases (via `add_to_dev_log.ps1`). Append one functionality-log line: `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Id S0326 -Op ADD -Description "Global 3D/VR default settings screen in media settings"`.

**Verification:**

- `Grep` - `dev/FUNCTIONALITY.log` contains an `S0326` ADD line.
- `dev/CHANGELOG.md` has entries covering all modified files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Catalog regenerated; FEATURES updated trilingual; functionality log appended.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action: `/spec-check S0326`.

---

## Rollback Plan

Docs/catalog only - revert the FEATURES edits; regenerate the catalog from source.
