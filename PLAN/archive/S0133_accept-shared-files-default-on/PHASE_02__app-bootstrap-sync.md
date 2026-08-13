# Phase 02 — Application Bootstrap Component-State Sync

**Strategic spec:** [`../S0133_accept-shared-files-default-on.md`](../S0133_accept-shared-files-default-on.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-09
**Completed:** 2026-05-10

---

## Objective

Add an idempotent bootstrap step in `FastMediaSorterApp.onCreate` that reads `acceptSharedFiles` and `isPrimaryMediaPlayer` from DataStore and reconciles the corresponding manifest activity-aliases via `DefaultPlayerManager`. After the first process start on a fresh install with the new default, the app appears in the OS share-sheet for MIME types supported by the current flavor without any user interaction.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [x] Strategic §6 research items Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/DefaultPlayerStateBootstrapper.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 580 (existing 529) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/DefaultPlayerManager.kt` | unchanged | — |

> No backup required — `FastMediaSorterApp.kt` stays well below the 1500 LOC ceiling and below 500 → no backup gate.
> The new `DefaultPlayerStateBootstrapper.kt` lives in `core/init/` next to the existing `AppStartupInitializer` import already present in `FastMediaSorterApp`.

---

## Steps

### Step 02.1 — Create `DefaultPlayerStateBootstrapper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/init/DefaultPlayerStateBootstrapper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new class `DefaultPlayerStateBootstrapper` (object or `@Singleton class`, choose the variant matching the surrounding `core/init/` style — see `AppStartupInitializer.kt`). Expose a single suspending function `suspend fun apply(context: Context, settingsRepository: SettingsRepository)` that:
>
> 1. Reads the latest `AppSettings` from `settingsRepository.getSettings().first()`.
> 2. Calls `DefaultPlayerManager.applyShareReceiverState(context, settings.acceptSharedFiles)`.
> 3. Calls `DefaultPlayerManager.applyPrimaryPlayerState(context, settings.isPrimaryMediaPlayer)`.
> 4. Logs `Timber.d("S0133: bootstrap applied — share=${settings.acceptSharedFiles}, primary=${settings.isPrimaryMediaPlayer}")` once at the end.
>
> The function must be safe to call repeatedly: each underlying `setComponentEnabledSetting` call with the current value is a no-op at the OS level. No "first run" flag is required.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/init/DefaultPlayerStateBootstrapper.kt` exists.
- `Grep -n "class DefaultPlayerStateBootstrapper|object DefaultPlayerStateBootstrapper"` matches exactly once in that file.
- `Grep -n "suspend fun apply\("` matches exactly once in that file.
- `Grep -n "DefaultPlayerManager.applyShareReceiverState"` and `Grep -n "DefaultPlayerManager.applyPrimaryPlayerState"` each match at least once.
- `Grep -n "Timber.d\\(\"S0133:"` matches exactly once.
- `Grep -n "Log\.d\("` returns zero hits (Timber-only rule).

**Status:** `[x]` done

**Step Log:**

- 2026-05-09 — Verification 5/5 PASS. Files: `DefaultPlayerStateBootstrapper.kt` (new, 31 LOC). Dev log recorded.

---

### Step 02.2 — Wire bootstrap into `FastMediaSorterApp.onCreate`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `FastMediaSorterApp.onCreate`, after Hilt has injected `settingsRepository` and `applicationScope` is available (both already present), schedule a single bootstrap call:
>
> ```kotlin
> // S0133: reconcile system component state (share-sheet aliases, primary-player aliases) with DataStore.
> // Idempotent — safe on every process start; restores registration after reinstall / clear-data.
> applicationScope.launch {
>     runCatching {
>         DefaultPlayerStateBootstrapper.apply(applicationContext, settingsRepository)
>     }.onFailure { Timber.e(it, "S0133: bootstrap failed") }
> }
> ```
>
> Place the block in a section of `onCreate` that runs AFTER injected fields are guaranteed available — i.e. somewhere alongside the other `applicationScope.launch { .. }` initializers, NOT before `super.onCreate()`. Wrap with `runCatching` so a DataStore IOException never crashes the process.

**Verification:**

- `Grep -n "DefaultPlayerStateBootstrapper.apply"` matches exactly once in `FastMediaSorterApp.kt`.
- `Grep -n "S0133:"` matches at least once in `FastMediaSorterApp.kt`.
- `Grep -n "applicationScope.launch"` count increased by exactly one compared to the previous file revision (sanity check: the new block uses the existing scope rather than creating a new one).
- `Grep -n "Log\.d\("` returns zero hits in this file (Timber-only rule).

**Status:** `[x]` done

**Step Log:**

- 2026-05-09 — Verification 4/4 PASS. `DefaultPlayerStateBootstrapper.apply` call resolves across 2 lines (Kotlin chained call). `applicationScope.launch` 6 → 7. `S0133:` matches 2 (comment + error tag). Dev log recorded.

---

### Step 02.3 — Build verification

**Files:** —
**Depends on:** Step 02.2

**Prompt for developer:**

> Run a debug build for the `standard` flavor via `/build`. Resolve any compile errors / lint warnings introduced by Steps 02.1–02.2 in the touched files. Do not invoke gradle directly.

**Verification:**

- Build artefact `app_v2/build/outputs/apk/standardDebug/*.apk` exists (Glob).
- No new lint warnings reported in `FastMediaSorterApp.kt` or `DefaultPlayerStateBootstrapper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-09 — Build FAILED. Cause: pre-existing uncommitted edits in `app_v2/src/main/res/values/strings.xml` (~25 strings rewritten with `&apos;` decoding to unescaped `'` — aapt rejects with misleading "Invalid unicode escape sequence" error). NOT a regression from S0133 changes (`AppSettings.kt`, `SettingsRepositoryImpl.kt`, `DefaultPlayerStateBootstrapper.kt`, `FastMediaSorterApp.kt` all compile-clean — kotlinc passed; failure is in `mergeStandardDebugResources` task, before Kotlin compilation). User must resolve the strings.xml issue (escape apostrophes as `\'` or wrap strings in `"..."`) before this step can pass.
- 2026-05-10 — Verification 2/2 PASS. Apostrophes in `strings.xml` now properly escaped as `\'`. `BUILD SUCCESSFUL in 32s`. APK at `app_v2/build/outputs/apk/standard/debug/FastMediaSorter_standard_debug_v2.60.5100.226-DEBUG.apk`. No new lint warnings in S0133 files.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `BUILD SUCCESSFUL` (verified 2026-05-10).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry to be added in Phase 03 step 03.3 (consolidated bulk run).
- [x] Public API changed (new bootstrap class) → catalog regen happens in Phase 03 alongside docs.

---

## Handoff Notes to Next Phase

Bootstrap now reconciles `acceptSharedFiles` and `isPrimaryMediaPlayer` with system component state on every process start. Combined with Phase 01's default flip, a freshly installed app on `standard` / `photos` / `legacy` flavors registers in the OS share-sheet for supported MIME types after the first `onCreate`. Phase 03 documents the user-visible behavior change and refreshes the class catalog.

---

## Rollback Plan

- Revert the `applicationScope.launch { .. }` insertion in `FastMediaSorterApp.onCreate` and delete `DefaultPlayerStateBootstrapper.kt`.
- No data migration, no schema bump, no user-visible UI change to undo. The default flip in Phase 01 remains harmless without bootstrap (manifest aliases stay disabled until the user toggles the switch in Settings — pre-S0133 behavior).
