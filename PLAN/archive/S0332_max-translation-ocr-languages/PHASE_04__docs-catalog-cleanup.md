# Phase 04 - docs-catalog-cleanup

**Strategic spec:** [`../S0332_max-translation-ocr-languages.md`](../S0332_max-translation-ocr-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none - final cleanup phase
**Steps done:** 1 / 1
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Final documentation updates, catalog synchronization, and codebase cleanup.

---

## Prerequisites

- [ ] Phase 03 integration is ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 500 |
| `docs/FEATURES_RU.md` | Modified | ≤ 500 |
| `docs/FEATURES_UK.md` | Modified | ≤ 500 |

---

## Steps

### Step 04.1 - Update Features Documentation and Run Catalog Sync

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> 1. Add description of the searchable language picker UI, flags, native names, capability labels, and the expanded translation/OCR language list (including Czech) to `docs/FEATURES.md` (EN), `docs/FEATURES_RU.md` (RU), and `docs/FEATURES_UK.md` (UK) using the existing list styles.
> 2. Run catalog sync using `scripts/catalog_sync.ps1`.
> 3. Register the files touched in the dev progress log.

**Verification:**

- `Grep` - Search for "Czech" or "searchable" in `docs/FEATURES.md`.
- `Grep` - Search for "Чешский" or "поиск" in `docs/FEATURES_RU.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 2/2 PASS. Expected: `Czech|searchable` in `docs/FEATURES.md`, `Чешский|поиск` in `docs/FEATURES_RU.md`. Actual: EN line 114 contains `searchable`, `Czech`, and capability labels; RU line 114 contains `поиском`, `чешский`, and capability labels. Dev log recorded; catalog sync PASS.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\gradlew.bat :app_v2:assembleStandardDebug "-Pchaquopy.enabled=false"` exit 0; `.\gradlew.bat :app_v2:assembleNoLegalDebug "-Pchaquopy.enabled=true" --no-configuration-cache` exit 0.
- [x] Catalog sync completed via `catalog_sync.ps1`.
- [x] Dev log entries registered.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert doc changes - no functional code changes.
