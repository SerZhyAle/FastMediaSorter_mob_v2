# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [../S0376_file-manager-predefined-resource.md](../S0376_file-manager-predefined-resource.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

## Objective

Document the shipped All Files predefined resource and close the mechanical repo rituals for Kotlin and string changes.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.

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
**Depends on:** 02.3
**Prompt for developer:** Update the existing file-manager / all-files feature bullets so they mention the predefined `All Files` resource, the profile-triggered creation on first-run, and the manual Settings CTA.
**Verification:** All three feature inventory files mention the predefined resource and keep EN/RU/UK parity.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Updated EN/RU/UK feature inventory bullets for the predefined All Files resource and profile-triggered creation flow.

### Step 03.2 - Run closure validation

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** 03.1
**Prompt for developer:** Run localization audit for the new key prefix, regenerate the app_v2 catalog, and run a focused debug build validation before handing the spec to `/spec-check`.
**Verification:** `check_strings_localized.ps1` passes for the new key prefix, `scripts/catalog_sync.ps1 -Module app_v2` succeeds, and the target debug build returns exit code 0.
**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - PASS. Localization audit passed for the new `settings_all_files*` keys, `scripts/catalog_sync.ps1 -Module app_v2` succeeded, and `build-debug.PS1` returned exit code 0.

---

## Phase Done Criteria

- [x] Feature inventory reflects the shipped predefined All Files resource in EN/RU/UK.
- [x] Localization audit passes.
- [x] Catalog sync succeeds.
- [x] Target debug build passes.
