# Phase 05 - Docs Catalog Cleanup

**Strategic spec:** [../S0117_url-media-downloader-nolegal-flavor.md](../S0117_url-media-downloader-nolegal-flavor.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Finish the mechanical closure work: dev log coverage, string audit, catalog regeneration, focused build validation, and spec status synchronization.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Every source/config edit already has a matching dev log entry.
- [x] Kotlin/API changes are ready for catalog regeneration.
- [x] Final verification commands are known.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | auto |
| `dev/CATALOG/app_v2.md` | Modified | auto |
| `PLAN/S0117_url-media-downloader-nolegal-flavor.md` | Modified | <= 250 |
| `PLAN/S0117_url-media-downloader-nolegal-flavor/INDEX.md` | Modified | <= 250 |

---

## Steps

### Step 05.1 - Run the localization audit for S0117 strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run the project string parity script for the `s0117_` prefix and fix any missing EN/RU/UK keys before closing the spec.

**Verification:**

- `Command` - `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix s0117_` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. `check_strings_localized.ps1 -KeyPrefix s0117_` returned OK for all EN/RU/UK keys.

---

### Step 05.2 - Regenerate the app_v2 catalog after Kotlin changes

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Refresh the `app_v2` code catalog after the new `noLegal` Kotlin classes and API changes land. Preserve manual role/status metadata.

**Verification:**

- `Command` - `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` exits 0.
- `Command` - `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. `dev/CATALOG/scripts/scan.ps1` and `render.ps1` regenerated the `app_v2` catalog after the S0117 Kotlin changes.

---

### Step 05.3 - Run final focused validation and sync spec state

**Files:** `PLAN/S0117_url-media-downloader-nolegal-flavor.md`, `PLAN/S0117_url-media-downloader-nolegal-flavor/INDEX.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run the final focused build/tests for the touched URL-download slice, update the tactical index counters, and advance the strategic spec to `Implemented` when all tactical phases are complete. Do not update public FEATURES docs because strategic §8 explicitly keeps `noLegal` out of those files.

**Verification:**

- `Command` - `./gradlew.bat :app_v2:compileNoLegalDebugKotlin :app_v2:compileStandardDebugKotlin` exits 0.
- `Command` - standard runtime dependency check returns no `TeamNewPipe|NewPipeExtractor|org.schabi` hits.
- `Grep` - `Status: Implemented` present in `PLAN/S0117_url-media-downloader-nolegal-flavor.md` when all phases are complete.
- `Grep` - `Status: Done` present in `PLAN/S0117_url-media-downloader-nolegal-flavor/INDEX.md` when all phases are complete.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 - Verification PASS. `noLegal` and `standard` compile paths are green; `standardDebugRuntimeClasspath` shows no NewPipe coordinates.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Final focused validation command(s) exit 0.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] Strategic spec is ready for `/spec-check`.
- [x] Public FEATURES docs remain intentionally unchanged per strategic §8.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate.