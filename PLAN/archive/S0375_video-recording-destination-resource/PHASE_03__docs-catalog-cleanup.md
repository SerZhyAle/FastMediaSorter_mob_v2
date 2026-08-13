# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [../S0375_video-recording-destination-resource.md](../S0375_video-recording-destination-resource.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Align user-facing documentation and mechanical closure with the shipped video destination behavior.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 260 |
| `docs/FEATURES_RU.md` | Modified | ≤ 260 |
| `docs/FEATURES_UK.md` | Modified | ≤ 260 |

---

## Steps

### Step 03.1 - Update feature inventory copy

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** 02.2
**Prompt for developer:** Update the existing video-recording feature bullets so they explicitly mention the new playback setting for the destination resource, preserve the current-resource primary behavior, and document the `Movies` fallback when neither current nor selected destination is usable.
**Verification:** All three feature inventory files mention the new destination setting and keep EN/RU/UK parity.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Updated EN/RU/UK feature inventory bullets for the video destination contract.

### Step 03.2 - Run closure validation

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** 03.1
**Prompt for developer:** Run localization audit for the new keys, regenerate the app_v2 catalog, and run a focused build validation for the touched slice before handing the spec to `/spec-check`.
**Verification:** `check_strings_localized.ps1` passes for the new key prefix, `scripts/catalog_sync.ps1 -Module app_v2` succeeds, and the target debug build returns exit code 0.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Ran localization audit, catalog sync, and `build-debug.PS1` successfully.

---

## Phase Done Criteria

- [x] Feature inventory reflects the shipped destination behavior in EN/RU/UK.
- [x] Localization audit passes.
- [x] Catalog sync succeeds.
- [x] Target debug build passes.