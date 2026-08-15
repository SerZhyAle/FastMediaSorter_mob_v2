# Phase 05 - Docs & catalog cleanup

**Strategic spec:** [`../S0410_standalone-image-action-parity.md`](../S0410_standalone-image-action-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-13
**Completed:** 2026-06-13

**Step Log:**

- 2026-06-13 - 05.1 catalog regenerated (catalog_sync + render); roles/status=new set for MaterializeUriToFileUseCase, TranslationSettingsDialog, StandaloneDrawSaveHelper. 05.2 FEATURES: Без изменений (extension of documented image actions to the external viewer) - rationale in dev log, no FEATURES edit. 05.3 dev-log coverage: per-file entries recorded by per-step post-change.ps1 + spec status entry via close-and-log. Final full build (`a.ps1 d`) SUCCESSFUL incl. Hilt graph + 4 S0410 tags.

---

## Objective

Regenerate the class catalog, record dev-log coverage, and decide the FEATURES sentence.

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | n/a |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |
| `docs/FEATURES.md` / `_RU.md` / `_UK.md` | Modified (conditional) | ≤ 10 |

---

## Steps

### Step 05.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so the new `MaterializeUriToFileUseCase` and `TranslationSettingsDialog` appear in the catalog. Set `role` + `status` for both new classes via `set.ps1`.

**Verification:**

- `Grep` - `MaterializeUriToFileUseCase` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `TranslationSettingsDialog` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 05.2 - Decide and apply the FEATURES sentence

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Per strategic §8: if the owner confirms this is a new user-facing capability (draw / settings / crop-to-file on externally-opened images), add one trilingual sentence describing the extended action set of the external image viewer, in lockstep EN/RU/UK. Otherwise record "Без изменений" rationale in the dev log and skip the FEATURES edit. If strings are added, they pass `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- Either: `Grep` - the new sentence present in all three FEATURES files; or a dev-log entry stating "FEATURES: Без изменений (распространение уже описанных возможностей)".

**Status:** `[ ]` not done

---

### Step 05.3 - Dev-log coverage sweep

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has an entry for every file modified across Phases 01-04 (`MaterializeUriToFileUseCase.kt`, `TranslationSettingsDialog.kt`, `TranslationButtonManager.kt`, `PhotoVideoStandaloneActivity.kt`, `StandalonePlayerViewModel.kt`, `overflow_menu_standalone_player.xml`). Add any missing entries via `.\scripts\add_to_dev_log.ps1`.

**Verification:**

- `Grep` - each of the six file names appears at least once in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] All Completion-Gate boxes in `INDEX.md` satisfiable by `/spec-check`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0410`.

---

## Rollback Plan

Catalog/doc-only phase - revert generated catalog and any FEATURES sentence; no code or data impact.
