# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S0299_office-document-viewing-legal-routing.md`](../S0299_office-document-viewing-legal-routing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** final audit
**Steps done:** 4 / 4
**Started:** 2026-05-28
**Completed:** 2026-05-28

---

## Objective

Close user-visible documentation, catalog, build, and spec status for S0299.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 340 |
| `docs/FEATURES_RU.md` | Modified | ≤ 340 |
| `docs/FEATURES_UK.md` | Modified | ≤ 340 |
| `PLAN/S0299_office-document-viewing-legal-routing.md` | Modified | ≤ 340 |
| `PLAN/S0299_office-document-viewing-legal-routing/INDEX.md` | Modified | ≤ 140 |
| `PLAN/S0299_office-document-viewing-legal-routing/PHASE_01__classification.md` | Modified | ≤ 260 |
| `PLAN/S0299_office-document-viewing-legal-routing/PHASE_02__external-handoff.md` | Modified | ≤ 230 |
| `PLAN/S0299_office-document-viewing-legal-routing/PHASE_03__docs-catalog-cleanup.md` | Modified | ≤ 180 |

---

## Steps

### Step 03.1 - Update public feature inventory

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Phase 02

**Prompt for developer:**

> Add the public `standard` feature entry for Office document handoff in EN/RU/UK. Keep wording honest: FMS opens Office documents with an installed document viewer, not with an embedded renderer.

**Verification:**

- `Grep` - `Office document handoff` exists in `docs/FEATURES.md`.
- `Grep` - `Office` exists in `docs/FEATURES_RU.md`.
- `Grep` - `Office` exists in `docs/FEATURES_UK.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 3/3 PASS. Files: `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`. Public Office document handoff entry added.

### Step 03.2 - Run catalog and localization checks

**Files:** generated catalog, string resources
**Depends on:** Step 03.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` and `pwsh -NoProfile -File scripts/check_strings_localized.ps1`. Record exact exit codes in this phase log.

**Verification:**

- `Command` - catalog sync exits 0.
- `Command` - string localization check exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 2/2 PASS. `scripts/catalog_sync.ps1 -Module app_v2` exit 0. `check_strings_localized.ps1 -KeyPrefix no_app_to_open` and `-KeyPrefix error_opening_file_simple` exit 0; no new Android string key was added.

### Step 03.3 - Build standard debug

**Files:** app build outputs
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `/build standard debug` using the repo build script. If it fails, inspect with `.\a.ps1 bf`, patch only S0299-related errors, and rerun.

**Verification:**

- `Command` - `.\build-debug.PS1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 1/1 PASS. `.\build-debug.PS1` completed with `BUILD SUCCESSFUL`; output APK `app_v2/build/outputs/apk/standard/debug/FastMediaSorter_standard_debug_v2.60.5290.025-DEBUG.apk`.

### Step 03.4 - Close tactical state and audit

**Files:** strategic spec, INDEX, phase files
**Depends on:** Step 03.3

**Prompt for developer:**

> Mark all completed steps and phases, set strategic status to `Implemented`, run `/spec-check S0299`, and apply mechanical `/spec-fix` findings if any.

**Verification:**

- `Grep` - `**Status:** Implemented` or `**Status:** Verified` exists in strategic spec after implementation.
- `Grep` - `## Last Audit` exists in strategic spec after `/spec-check`.
- `Grep` - no `Timber.d("S0299:` hits under `app_v2/src/main/java`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-28 - Verification 3/3 PASS. Strategic status advanced through `Implemented` to `Verified`; `## Last Audit` added; `rg 'Timber\.d\("S0299:' app_v2/src/main/java` returned zero hits.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\build-debug.PS1` completed with `BUILD SUCCESSFUL`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `/spec-check S0299` completed.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase edits and feature docs; no data migration is introduced.
