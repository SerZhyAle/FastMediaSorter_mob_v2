# Phase 01 - Prewarm core

**Strategic spec:** [`../S0334_translation-model-prewarm.md`](../S0334_translation-model-prewarm.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Introduce a screen-independent role that, given a target settings language code, ensures the required ML Kit translation models are present on device (target plus the English pivot), is idempotent for already-downloaded models, downloads without a Wi-Fi-only restriction, and publishes an observable status. No settings or UI wiring yet.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved (all are).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/TranslationModelPrewarmStatus.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/PrewarmTranslationModelUseCase.kt` | New | ≤ 180 |

> Use case is `@Singleton` with an `@Inject` constructor - no new Hilt `@Module` required (constructor-injectable). `RemoteModelManager.getInstance()` is obtained internally.

---

## Steps

### Step 01.1 - Add prewarm status model

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/TranslationModelPrewarmStatus.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a sealed interface (or enum-with-payload) `TranslationModelPrewarmStatus` modelling the prewarm lifecycle: `Idle`, `Downloading` (carrying the target language settings code), `Ready` (carrying the target code), `Failed` (carrying the target code). Keep it pure Kotlin in the domain layer - no Android or ML Kit imports.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/TranslationModelPrewarmStatus.kt` exists.
- `Grep` - `TranslationModelPrewarmStatus` matches in that file.
- `Grep` - all four states `Idle`, `Downloading`, `Ready`, `Failed` present.
- `Grep -n "import com.google.mlkit"` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 4/4 PASS. Files: domain/model/TranslationModelPrewarmStatus.kt (New, +23 LOC). Sealed interface with Idle/Downloading/Ready/Failed, no ML Kit imports.

---

### Step 01.2 - Add PrewarmTranslationModelUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/PrewarmTranslationModelUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `@Singleton class PrewarmTranslationModelUseCase @Inject constructor(...)`. Expose a `StateFlow<TranslationModelPrewarmStatus>` for observers and a `suspend fun prewarm(targetSettingsCode: String)`. Inside `prewarm`: map the settings code to an ML Kit code reusing `TranslationManager.languageCodeToMLKit`; compute the required model set as `{en}` when target is English, otherwise `{en, target}` (English is always the pivot for both direct and two-step translation); skip models already present via `RemoteModelManager.getInstance().isModelDownloaded(...)`; download each missing model via `RemoteModelManager.getInstance().download(TranslateRemoteModel, DownloadConditions.Builder().build())` (no Wi-Fi restriction); emit `Downloading` before, `Ready` on success, `Failed` on any error. Make repeated calls for the same code idempotent (return `Ready` immediately if all required models already downloaded). All ML Kit calls on a background dispatcher.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/PrewarmTranslationModelUseCase.kt` exists.
- `Grep` - `class PrewarmTranslationModelUseCase` matches exactly once.
- `Grep` - `suspend fun prewarm(` present.
- `Grep` - `StateFlow<TranslationModelPrewarmStatus>` present.
- `Grep` - `DownloadConditions.Builder().build()` present (no Wi-Fi restriction).
- `Grep` - `languageCodeToMLKit` referenced.
- `Grep -n "Log\.d\("` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 7/7 PASS. Files: domain/usecase/PrewarmTranslationModelUseCase.kt (New, +73 LOC). Singleton @Inject use case, idempotent prewarm, en-pivot model set, no Wi-Fi restriction, Timber only. Note: reuses ui-layer TranslationManager.languageCodeToMLKit (adjacent debt - mapping ideally belongs in domain).

---

### Step 01.3 - Add debug verification tag at prewarm entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/PrewarmTranslationModelUseCase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> At the start of `prewarm(...)`, add `Timber.d("S0334: prewarm requested for <code>")` (interpolate the target code). This is the BlockNeedUserTest probe; it stays until the ticket leaves that status. Do not embed `S0334` in any other non-`Timber.d` log line.

**Verification:**

- `Grep` - `Timber.d("S0334:` present exactly once in the use case file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 1/1 PASS. Files: domain/usecase/PrewarmTranslationModelUseCase.kt (+1 LOC). BlockNeedUserTest probe at prewarm entry.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `build-debug.PS1` BUILD SUCCESSFUL (1m 24s) after one transient R.jar-lock retry.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public class).

---

## Handoff Notes to Next Phase

`PrewarmTranslationModelUseCase` is injectable and exposes a status `StateFlow`. Phase 02 injects it into the settings layer and drives `prewarm(...)` from settings changes; Phase 03 renders the status flow.

---

## Rollback Plan

Revert phase commit(s) - two new files only, no data migration or user-facing surface changed.
