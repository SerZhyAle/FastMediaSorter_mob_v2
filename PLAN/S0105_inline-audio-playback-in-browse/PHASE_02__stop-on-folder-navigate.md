# Phase 02 — Stop on Folder Navigate

**Strategic spec:** [`../S0105_inline-audio-playback-in-browse.md`](../S0105_inline-audio-playback-in-browse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Stop inline playback when the user navigates into a subfolder or back within any Browse resource that is not in audio-only mode. Audio-Library resources retain their existing continuous-playback behavior.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 908 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 499 |

> `BrowseManagerInitializer.kt` is 908 lines — backup required before edits (Step 02.1).

---

## Steps

### Step 02.1 — Backup BrowseManagerInitializer.kt

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup:
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt" `
>           "temp/BrowseManagerInitializer_$ts.kt"
> ```

**Verification:**

- `Glob` — `temp/BrowseManagerInitializer_*.kt` matches at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 1/1 PASS. Files: temp/BrowseManagerInitializer_20260506_154126.kt. Dev log N/A (backup only).

---

### Step 02.2 — Stop playback on subfolder entry and up-navigation (BrowseManagerInitializer)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Make two changes in `BrowseManagerInitializer.kt`:
>
> **Change 1 — `onFolderClick` callback** (around line 216):
> Before `viewModel.navigateToFolder(folder)`, add a conditional stop:
> ```kotlin
> onFolderClick = { folder ->
>     UserActionLogger.logItemClick(folder.name, context = "Folder click")
>     if (!isAudioOnlyResource()) viewModel.inlineStop()
>     viewModel.navigateToFolder(folder)
> },
> ```
>
> **Change 2 — `navigateUp()` override** (around line 308):
> Before `viewModel.navigateUp()`, add the same conditional stop:
> ```kotlin
> override fun navigateUp() {
>     if (!isAudioOnlyResource()) viewModel.inlineStop()
>     viewModel.navigateUp()
> }
> ```
>
> `isAudioOnlyResource()` is already defined in this class (line 578): `override fun isAudioOnlyResource() = viewModel.state.value.resource?.isAudioOnly() == true`. No new method needed.

**Verification:**

- `Grep` — `viewModel.state.value.resource?.isAudioOnly() != true.*viewModel.inlineStop` appears exactly twice in `BrowseManagerInitializer.kt` (once before `navigateToFolder`, once before `navigateUp`). Note: `isAudioOnlyResource()` was defined inside an anonymous object and not accessible at these call sites; the inline expression is semantically identical.
- `Grep` — `Log.d(` returns zero hits in `BrowseManagerInitializer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: BrowseManagerInitializer.kt (2 inlineStop guards added using inline isAudioOnly expression; isAudioOnlyResource() was inaccessible from those call sites). Dev log deferred to phase end.

---

### Step 02.3 — Stop playback on back-press navigation (BrowseActivity)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> `BrowseActivity` has two back-navigation entry points that call `viewModel.navigateUp()`: the `OnBackPressedCallback` and the `btnBack` click listener. In both cases, guard the stop call so it fires only when `canNavigateUp()` is true (i.e., navigation actually happens within Browse, not when exiting Browse — `onStop()` already handles the exit case).
>
> **Change 1 — `OnBackPressedCallback`** (around line 237):
> Replace:
> ```kotlin
> override fun handleOnBackPressed() {
>     if (viewModel.canNavigateUp() && viewModel.navigateUp()) {
>         Timber.d("Navigated back to parent folder")
>     } else {
>         viewModel.clearResumeState()
>         isEnabled = false
>         onBackPressedDispatcher.onBackPressed()
>     }
> }
> ```
> With:
> ```kotlin
> override fun handleOnBackPressed() {
>     if (viewModel.canNavigateUp()) {
>         if (viewModel.state.value.resource?.isAudioOnly() != true) viewModel.inlineStop()
>         if (viewModel.navigateUp()) {
>             Timber.d("Navigated back to parent folder")
>             return
>         }
>     }
>     viewModel.clearResumeState()
>     isEnabled = false
>     onBackPressedDispatcher.onBackPressed()
> }
> ```
>
> **Change 2 — `btnBack` click listener** (around line 248):
> Replace:
> ```kotlin
> binding.btnBack.setOnClickListener {
>     UserActionLogger.logButtonClick("Back", "BrowseActivity")
>     if (!viewModel.canNavigateUp() || !viewModel.navigateUp()) {
>         viewModel.clearResumeState()
>         finish()
>         @Suppress("DEPRECATION")
>         overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
>     }
> }
> ```
> With:
> ```kotlin
> binding.btnBack.setOnClickListener {
>     UserActionLogger.logButtonClick("Back", "BrowseActivity")
>     if (viewModel.canNavigateUp()) {
>         if (viewModel.state.value.resource?.isAudioOnly() != true) viewModel.inlineStop()
>         if (viewModel.navigateUp()) return@setOnClickListener
>     }
>     viewModel.clearResumeState()
>     finish()
>     @Suppress("DEPRECATION")
>     overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
> }
> ```

**Verification:**

- `Grep` — `viewModel.state.value.resource?.isAudioOnly() != true` appears exactly twice in `BrowseActivity.kt`.
- `Grep` — `viewModel.inlineStop()` appears at least twice in `BrowseActivity.kt` (the two new calls plus the existing `onStop()` call).
- `Grep` — `Log.d(` returns zero hits in `BrowseActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 3/3 PASS. Files: BrowseActivity.kt (2 back-press handlers updated). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `BrowseManagerInitializer.kt` and `BrowseActivity.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Subfolder navigation and back-navigation in non-audio-only resources now stop inline playback.
- Audio Library resources retain continuous playback through folder navigation (guarded by `isAudioOnly()`).
- `BrowseActivity.onStop()` still handles playback stop when Browse is backgrounded or closed.

---

## Rollback Plan

Restore `temp/BrowseManagerInitializer_<timestamp>.kt`. Revert `BrowseActivity.kt` changes. Revert phase commit(s).
