# Phase 01 - foundations

**Strategic spec:** [`../S0332_max-translation-ocr-languages.md`](../S0332_max-translation-ocr-languages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Introduce the unified language catalogue `TranslationLanguageCatalog`, capability metadata, and unit tests.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationLanguageCatalog.kt` | New | ≤ 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/TranslationLanguageCatalogTest.kt` | New | ≤ 150 |

---

## Steps

### Step 01.1 - Create TranslationLanguageCatalog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationLanguageCatalog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the `TranslationLanguageCatalog.kt` file. It should define:
> 1. `LanguageItem` data class with fields: `code` (String), `countryCode` (String?), `localizedName` (String), `nativeName` (String), `flagEmoji` (String).
> 2. `TranslationLanguageCatalog` object containing a whitelist of supported BCP-47 language codes from ML Kit Translation, mapped to country codes.
> 3. Dynamic name construction using `Locale(code).getDisplayName(displayLocale)` and native `Locale(code).getDisplayName(Locale(code))`.
> 4. Dynamic country flag emoji generation using Unicode regional indicator symbols code points.
> 5. Methods to build source language list (ordered: Auto-detect, UI language, English, others alphabetically) and target language list (ordered: UI language, English, others alphabetically).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationLanguageCatalog.kt` exists.
- `Grep` - `class TranslationLanguageCatalog` or `object TranslationLanguageCatalog` matches exactly once.
- `Grep` - `fun buildSourceLanguageList` present.
- `Grep` - `fun buildTargetLanguageList` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationLanguageCatalog.kt` (+244 LOC). Includes translation/basic OCR/quality OCR/noLegal OCR capability metadata. Dev log recorded; catalog sync PASS.

---

### Step 01.2 - Create Unit Tests for TranslationLanguageCatalog

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/TranslationLanguageCatalogTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `TranslationLanguageCatalogTest.kt` to verify that `TranslationLanguageCatalog` behaves correctly:
> 1. `getFlagEmoji` returns correct emoji (e.g. "CZ" -> "🇨🇿").
> 2. Language lists are built successfully and names format correctly.
> 3. List ordering invariants (Auto first for source, UI language second, English third) are satisfied.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/TranslationLanguageCatalogTest.kt` exists.
- `Grep` - `class TranslationLanguageCatalogTest` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-06-03 - Verification 2/2 PASS. Files: `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/TranslationLanguageCatalogTest.kt` (+63 LOC). Includes Czech basic OCR and noLegal capability regression coverage. Dev log recorded; catalog sync PASS.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\gradlew.bat :app_v2:assembleStandardDebug "-Pchaquopy.enabled=false"` exit 0.
- [x] Unit tests pass - `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalogTest" "-Pchaquopy.enabled=false"` exit 0.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Catalog sync completed via `catalog_sync.ps1`.

---

## Handoff Notes to Next Phase

Unified language catalog, capability matrix and mappings are implemented and verified via unit tests. Ready for UI picker development.

---

## Rollback Plan

Revert phase commit(s) - no database migration or user-facing interface changed.
