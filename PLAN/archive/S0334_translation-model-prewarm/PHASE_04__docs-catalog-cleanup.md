# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0334_translation-model-prewarm.md`](../S0334_translation-model-prewarm.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Close out the spec: regenerate the class catalog, record dev-log entries, and confirm no FEATURES change is required.

---

## Prerequisites

- [x] Phases 01–03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Appended (via script) | - |

---

## Steps

### Step 04.1 - Regenerate catalog and dev log

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan and render the catalog (new `PrewarmTranslationModelUseCase` + status model). Set role/status for the new classes via `set.ps1` if not auto-filled. Ensure `dev/CHANGELOG.md` has an entry for every file modified across phases 01–03 (via `add_to_dev_log.ps1`).

**Verification:**

- `Grep` - `PrewarmTranslationModelUseCase` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `TranslationModelPrewarmStatus` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 2/2 PASS. `scripts/catalog_sync.ps1 -Module app_v2` exit 0. `PrewarmTranslationModelUseCase` and `TranslationModelPrewarmStatus` present in `dev/CATALOG/app_v2.jsonl`; role/status set via `set.ps1` (`status=new`). Dev log recorded for catalog regeneration.
- 2026-06-03 - Post-audit hardening PASS. Added `src/translationEnabled/java` to `dev/CATALOG/scripts/scan.ps1`; rerun scanned 1298 files / 1599 records. Role/status set for `TranslationLanguageCodeMapper`, `TranslationModelPrewarmEnabled`, `TranslationModelPrewarmModule`, `TranslationModelPrewarmAvailabilityModule`, `PrewarmTranslationModelUseCase`, and `TranslationModelPrewarmStatus`.

---

### Step 04.2 - Confirm no FEATURES change

**Files:** -
**Depends on:** Step 04.1

**Prompt for developer:**

> Strategic §8 states "Без изменений в docs/FEATURES" - this is an improvement to existing translation, not a new user-facing feature. Do NOT edit `docs/FEATURES*.md`. Confirm and record the closure decision in the dev log.

**Verification:**

- `Grep` - no new `translation_model_prewarm` mention added to `docs/FEATURES.md` (must return zero hits).

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 1/1 PASS. `rg 'translation_model_prewarm|S0334|PrewarmTranslationModelUseCase|TranslationModelPrewarmStatus' docs/FEATURES*.md` expected: 0 hits | actual: 0 hits. `docs/FEATURES*.md` were already dirty before this run; no S0334 feature-inventory edits were made. Dev log recorded closure decision.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] `dev/CHANGELOG.md` complete for all touched files.
- [x] `docs/FEATURES*.md` untouched by S0334. Note: files were pre-existing dirty before this run; no S0334/prewarm mention added.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-test-device S0334` when an online device and mobile-mcp are available, then `/spec-check S0334`.

Validation snapshot, 2026-06-03:

- PASS - `scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix translation_model_prewarm`.
- PASS - no `BuildConfig.*` in the new common/domain prewarm code.
- PASS - no `Log.d()` in the touched Kotlin files.
- PASS - `build-debug.PS1` / `assembleStandardDebug`.
- PASS - `scripts/builders/build-lite-debug.ps1` / `assembleLiteDebug`.
- PASS - `scripts/builders/build-photos-debug.ps1` / `assemblePhotosDebug`.
- PASS - targeted `TranslationLanguageCatalogTest`.
- PASS - targeted `TranslationLanguageCodeMapperTest`.
- FAIL unrelated - full `testStandardDebugUnitTest` has existing non-S0334 failures in `CanonicalPathNormalizerTest`, `GoogleDriveTokenRefreshTest`, `NetworkErrorMessageMapperTest`, and `BaseFileOperationHandlerExtractFileNameTest`.
- BLOCKED - device preflight `scripts/devtest/device-ready.ps1` returned `FAIL (2) - no online device`; mobile-mcp is unavailable in this Codex toolset, so the strategic spec stays `BlockNeedUserTest`.

---

## Rollback Plan

Catalog is a regenerated local index - no rollback needed. Dev log is append-only history.
