# Phase 01 - Recognize/Translate Facade Split

**Strategic spec:** [`../S0386_ondemand-ocr-translation-delivery.md`](../S0386_ondemand-ocr-translation-delivery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-06-09
**Completed:** 2026-06-09

> **Revised 2026-06-09:** `TranslationManager` is a `new`-constructed instance carrying a per-call `TranslationCallback` (not a Hilt singleton), so the facades are instance-scoped - the coordinator constructs the two backends directly. No Hilt `@Module` binding (the earlier 01.4 approach was wrong).

---

## Objective

Split the `TranslationManager` monolith into a recognize facade (OCR) and a translate facade (translation) behind `src/main` interfaces, with the coordinator delegating to two instance-scoped backends, zero behavior change and no delivery logic yet - so Set A (translation) and Set B (OCR) become independently addressable later.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §5.4 Phase 0 read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextRecognizationFacade.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextTranslationFacade.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/RecognitionBackend.kt` | New | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationBackend.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageOcrManager.kt` | Modified | ≤ 250 |

> Backup step required: `TranslationManager.kt` is 981 LOC (>500) - timestamped copy to `temp/` before editing.
>
> No Hilt module is added: the backends are plain classes constructed by the coordinator with the per-call `callback`; app-scoped collaborators (`OfflineOcrEngineProvider`) are obtained via the existing `EntryPointAccessors` pattern already used in `TranslationManager`.

---

## Steps

### Step 01.1 - Back up the monolith

**Files:** `temp/TranslationManager_<timestamp>.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TranslationManager.kt` to `temp/` with a timestamp suffix before any edit (file exceeds the 500-LOC backup threshold).

**Verification:**

- `Glob` - `temp/TranslationManager_*.kt` exists.

**Status:** `[x]` done

---

### Step 01.2 - Define the two facades

**Files:** `ui/player/helpers/TextRecognizationFacade.kt`, `ui/player/helpers/TextTranslationFacade.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `interface TextRecognizationFacade` exposing the pure-OCR surface currently on `TranslationManager`: `extractTextOnly`, `recognizeText`, `recognizeTextBlocksForSelection`, plus `release`. Create `interface TextTranslationFacade` exposing the translation surface: `translate`, `detectLanguage`, `getTargetLanguageCode`, plus `release`. Match the existing method signatures (suspend, nullable returns, `TranslationManager.TranslatedTextBlock` for selection). Place both in `ui/player/helpers` (co-located with the backends) because the selection method returns the UI-layer `TranslationManager.TranslatedTextBlock` - a domain interface must not depend on a UI type. Do not move the static language-name/list/conversion helpers - those stay in the `TranslationManager` companion (call-sites reference them as `TranslationManager.x`). No ML Kit imports in the interface files.

**Verification:**

- `Grep` - `interface TextRecognizationFacade` matches once in `TextRecognizationFacade.kt`.
- `Grep` - `interface TextTranslationFacade` matches once in `TextTranslationFacade.kt`.
- `Grep` - `com.google.mlkit` returns zero hits in both interface files.

**Status:** `[x]` done

---

### Step 01.3 - Extract the two instance backends

**Files:** `ui/player/helpers/RecognitionBackend.kt`, `ui/player/helpers/TranslationBackend.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Move ML Kit `nl.translate` + `nl.languageid` logic into `class TranslationBackend(context, callback, settingsRepository) : TextTranslationFacade` - it owns `translate`, `translateDirect`, `detectLanguage`, `getTargetLanguageCode`, the `Translator`/`RemoteModelManager`/`languageIdentifier` lifecycle, `isDirectTranslationSupported`, `awaitModelDownloadConfirmation`, and the model-download prompt via `callback`. Move the ML Kit `vision.text` recognition logic plus the `OfflineOcrEngineProvider` delegation into `class RecognitionBackend(context, callback, settingsRepository, offlineOcrEngineProvider, translation: TextTranslationFacade) : TextRecognizationFacade` - it owns `recognizeText`, `extractTextOnly`, `recognizeTextBlocksForSelection`, the `TextRecognizer` lifecycle, OCR-text cleaning, and Latin→Cyrillic post-processing; it calls `translation.detectLanguage(..)` for the auto-detect Cyrillic-conversion branch (language-id lives on the translation side). Both are plain classes (no `@Inject`, no Hilt singleton) constructed by the coordinator. These are the only `src/main` classes importing `com.google.mlkit.vision.text` / `com.google.mlkit.nl.*` after this phase (besides the existing prewarm/mapper helpers). No behavior change.

**Verification:**

- `Grep` - `class RecognitionBackend` and `: TextRecognizationFacade` both present in `RecognitionBackend.kt`.
- `Grep` - `class TranslationBackend` and `: TextTranslationFacade` both present in `TranslationBackend.kt`.
- `Grep -n "Log\.d\("` - zero hits in both new files (Timber only).

**Status:** `[x]` done

---

### Step 01.4 - Reduce TranslationManager to a coordinator exposing both facades

**Files:** `ui/player/helpers/TranslationManager.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Reduce `TranslationManager` to a coordinator that constructs a `RecognitionBackend` and a `TranslationBackend` (passing its `context`, `callback`, `settingsRepository`, and the lazily-resolved `offlineOcrEngineProvider`) and delegates to them. Expose them as public read-only properties `val recognition: TextRecognizationFacade` and `val translation: TextTranslationFacade`. Keep every current public method (`extractTextOnly`, `recognizeText`, `translate`, `recognizeAndTranslate`, `recognizeAndTranslateBlocks`, `recognizeTextBlocksForSelection`, `detectLanguage`, `getTargetLanguageCode`, `applyFontSettings`, `release`) delegating to the backends so call-sites compile unchanged; `recognizeAndTranslate*` compose `recognition` + `translation`. `release()` releases both. Keep the companion helpers in place. No Hilt module.

**Verification:**

- `Grep` - `val recognition: TextRecognizationFacade` and `val translation: TextTranslationFacade` both present in `TranslationManager.kt`.
- `Grep` - `RecognitionBackend(` and `TranslationBackend(` both constructed in `TranslationManager.kt`.
- `Grep` - `com.google.mlkit.vision.text` and `com.google.mlkit.nl.translate` return zero hits in `TranslationManager.kt` (moved to backends).

**Status:** `[x]` done

---

### Step 01.5 - Repoint the pure-OCR call-site to the recognize facade

**Files:** `ui/player/helpers/ImageOcrManager.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> In `ImageOcrManager`, obtain OCR through the recognize facade (`translationManager.recognition.extractTextOnly(...)`) instead of the combined `translationManager.extractTextOnly(...)`, so the OCR-only path depends on `TextRecognizationFacade` rather than the translation surface. Do not change runtime behavior; do not add any ML Kit import.

**Verification:**

- `Grep` - `recognition` (the facade property) referenced in `ImageOcrManager.kt`.
- `Grep` - no new `com.google.mlkit` import added to `ImageOcrManager.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `com.google.mlkit.vision.text` / `com.google.mlkit.nl.` in `src/main` returns hits only in `RecognitionBackend.kt`, `TranslationBackend.kt`, and pre-existing prewarm/mapper helpers - never in the interface files or `TranslationManager.kt`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public interfaces).

---

## Handoff Notes to Next Phase

Recognize and translate are now independent instance-scoped facades exposed as `TranslationManager.recognition` / `.translation`, behavior unchanged. Phase 02 wraps each behind a deliverable-capability gate (the backends read `DeliverableCapabilityRepository` via the same `EntryPointAccessors` pattern). Phase 05 then removes the ML Kit Text-Recognition path from `RecognitionBackend` entirely (Tesseract/Paddle cover OCR per strategic §5.4 B4) and moves ML Kit Translate to a Play dynamic-feature on store flavors while keeping it bundled on sideload/VR (2026-06-09 decision).

---

## Rollback Plan

Revert phase commit(s). Pure refactor behind facades - no data migration or user-facing surface changed; restore `temp/TranslationManager_<timestamp>.kt` if needed.

---

## Step Log

- 2026-06-09 - Steps 01.1-01.5 all PASS (grep predicates green). Backup `temp/TranslationManager_20260609_015640.kt`. New: `TextRecognizationFacade.kt`, `TextTranslationFacade.kt`, `RecognitionBackend.kt`, `TranslationBackend.kt`. Modified: `TranslationManager.kt` (981→~430 LOC, ML Kit moved out), `ImageOcrManager.kt` (OCR via `.recognition`). Design notes: facades co-located in `ui/player/helpers` (selection method returns UI-layer `TranslatedTextBlock`); `RecognitionBackend` holds a `TextTranslationFacade` ref for `detectLanguage`; combined `recognizeAndTranslateBlocks` kept in `RecognitionBackend` (offline→ML-Kit fallback entangled with per-block translation). Build: `assembleStandardDebug` BUILD SUCCESSFUL (v2.60.6090.210), compileStandardDebugKotlin zero errors. Dev log + catalog sync recorded.
