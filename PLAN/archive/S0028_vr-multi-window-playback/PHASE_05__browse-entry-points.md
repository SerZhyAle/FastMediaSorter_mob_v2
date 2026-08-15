# Phase 05 — Browse Entry Points (Resource Card + Top Menu Tear-Off)

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Wire entry points 1 and 2 from strategic §2 Goal 3:
- **Entry point 1:** icon button on the resource card → opens Browse for that resource in a new window; current window stays on the home screen.
- **Entry point 2:** "In separate window" item in the Browse top menu → tears off current Browse (resource + file + scroll) to a new window; current window goes back to home screen.

Also adds all trilingual strings for S0028 (replacing the placeholder from Phase 01 Step 01.4).

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseActionBarManager.kt` | Modified | ≤ 400 |

> Files likely >500 lines — create timestamped backups in `temp/` before editing.

---

## Steps

### Step 05.1 — Add all S0028 trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following string resources to all three locale files. Use `..` (two dots) for Russian/Ukrainian where natural; English uses no trailing punctuation.
>
> | Key | EN value | RU value | UK value |
> |-----|----------|----------|----------|
> | `action_open_in_separate_window` | `In separate window` | `В отдельном окне` | `В окремому вікні` |
> | `setting_allow_separate_window` | `Allow opening in a separate window` | `Разрешить запуск в отдельном окне` | `Дозволити запуск в окремому вікні` |
>
> After adding, find the Settings UI file modified in Phase 01 Step 01.4. Remove the `// TODO(phase-01)` placeholder string literal and replace it with `R.string.setting_allow_separate_window`.

**Verification:**

- `Grep` — `action_open_in_separate_window` matches in all three `strings.xml` files.
- `Grep` — `setting_allow_separate_window` matches in all three `strings.xml` files.
- `Grep` — `В отдельном окне` matches in `values-ru/strings.xml`.
- `Grep` — `TODO(phase-01)` returns zero hits in `app_v2/src/`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Added action_open_in_separate_window + setting_allow_separate_window to EN/RU/UK strings.xml. Replaced TODO(phase-05) placeholder in layout/layout-land fragment_settings_video.xml with @string/setting_allow_separate_window. Dev log recorded.

---

### Step 05.2 — Add `openBrowseInNewWindow()` to `BrowseEventHandler`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create a timestamped backup of `BrowseEventHandler.kt` in `temp/` if the file exceeds 500 lines.
>
> Add a method `fun openBrowseInNewWindow(resourceId: Long)` to `BrowseEventHandler`. The method must:
>
> 1. Build a `BrowseActivity` intent.
> 2. Put `resourceId` via `putExtra(BrowseActivity.EXTRA_RESOURCE_ID, resourceId)`.
> 3. Generate a unique window ID: `val windowId = java.util.UUID.randomUUID().toString()`.
> 4. Put `windowId` via `putExtra(BrowseActivity.EXTRA_WINDOW_ID, windowId)`.
> 5. Add flags: `Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK`.
> 6. Call `activity.startActivity(intent)`.
>
> Do not finish or navigate the current activity — the main window stays on the home screen.

**Verification:**

- `Grep` — `openBrowseInNewWindow` matches in `BrowseEventHandler.kt`.
- `Grep` — `FLAG_ACTIVITY_MULTIPLE_TASK` matches in `BrowseEventHandler.kt`.
- `Grep` — `BrowseActivity.EXTRA_RESOURCE_ID` matches in `BrowseEventHandler.kt`.
- `Grep` — `UUID.randomUUID` matches in `BrowseEventHandler.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseEventHandler.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 5/5 PASS. Added openBrowseInNewWindow(resourceId) + tearOffBrowse(resourceId, filePath, scrollPos) to BrowseEventHandler; both use UUID.randomUUID for windowId and FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_MULTIPLE_TASK. Files: BrowseEventHandler.kt (+24 LOC). Dev log recorded.

---

### Step 05.3 — Add resource card "In separate window" icon to `ResourceOpsMenuManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Create a timestamped backup of `ResourceOpsMenuManager.kt` in `temp/` if the file exceeds 500 lines.
>
> In `ResourceOpsMenuManager`, locate the method that builds resource card actions or popup menu options. Add an "In separate window" action guarded by both the setting and VR:
>
> ```kotlin
> if (BuildConfig.SUPPORT_VR_PLAYER && allowSeparateWindow) {
>     // add action: R.string.action_open_in_separate_window
>     // on click: browseEventHandler.openBrowseInNewWindow(resource.id)
> }
> ```
>
> The `allowSeparateWindow` value must be read from the settings flow already observed by the owning ViewModel (do not add a new DataStore read here — thread it in through an existing parameter or constructor if needed). Position the item after standard "Open" / "Browse" actions and before destructive actions.

**Verification:**

- `Grep` — `SUPPORT_VR_PLAYER` matches in `ResourceOpsMenuManager.kt`.
- `Grep` — `allowSeparateWindow` matches in `ResourceOpsMenuManager.kt`.
- `Grep` — `action_open_in_separate_window` matches in `ResourceOpsMenuManager.kt`.
- `Grep` — `openBrowseInNewWindow` matches in `ResourceOpsMenuManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `ResourceOpsMenuManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 5/5 PASS. Added action_open_in_separate_window to menu_resource_ops.xml; added allowSeparateWindow + openBrowseInNewWindow params to showMenu(); item shown only when BuildConfig.SUPPORT_VR_PLAYER && allowSeparateWindow; click delegates to openBrowseInNewWindow lambda. Files: ResourceOpsMenuManager.kt (+12 LOC), menu_resource_ops.xml (+4 lines). Dev log recorded.

---

### Step 05.4 — Add Browse top menu "In separate window" tear-off to `BrowseActionBarManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseActionBarManager.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Create a timestamped backup of `BrowseActionBarManager.kt` in `temp/` if the file exceeds 500 lines.
>
> Add a method `fun tearOffBrowse(resourceId: Long, currentFilePath: String?, scrollPosition: Int)` to `BrowseEventHandler` first:
>
> ```kotlin
> fun tearOffBrowse(resourceId: Long, currentFilePath: String?, scrollPosition: Int) {
>     val windowId = java.util.UUID.randomUUID().toString()
>     val intent = Intent(activity, BrowseActivity::class.java).apply {
>         putExtra(BrowseActivity.EXTRA_WINDOW_ID, windowId)
>         putExtra(BrowseActivity.EXTRA_RESOURCE_ID, resourceId)
>         currentFilePath?.let { putExtra(BrowseActivity.EXTRA_INITIAL_FILE_PATH, it) }
>         putExtra(BrowseActivity.EXTRA_SCROLL_POSITION, scrollPosition)
>         addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
>     }
>     activity.startActivity(intent)
>     activity.finish()   // tear-off: current window returns to its back stack (home screen)
> }
> ```
>
> Then in `BrowseActionBarManager`, add an "In separate window" menu item next to "Delete duplicates" / "Automate.." commands. Guard it with `BuildConfig.SUPPORT_VR_PLAYER && allowSeparateWindow`. On click: gather `currentResourceId`, `currentFilePath`, and `currentScrollPosition` from the owning ViewModel and call `browseEventHandler.tearOffBrowse(...)`.

**Verification:**

- `Grep` — `tearOffBrowse` matches in `BrowseEventHandler.kt`.
- `Grep` — `activity.finish()` matches inside the `tearOffBrowse` method in `BrowseEventHandler.kt`.
- `Grep` — `tearOffBrowse` matches in `BrowseActionBarManager.kt`.
- `Grep` — `SUPPORT_VR_PLAYER` matches in `BrowseActionBarManager.kt`.
- `Grep` — `allowSeparateWindow` matches in `BrowseActionBarManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseActionBarManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 6/6 PASS. Added handleSeparateWindowAction() to BrowseActionBarManager that guards with BuildConfig.SUPPORT_VR_PLAYER && allowSeparateWindow and calls browseEventHandler.tearOffBrowse(); tearOffBrowse and activity.finish() already added to BrowseEventHandler in Step 05.2. Files: BrowseActionBarManager.kt (+15 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — run `/build` for standard flavor (entry points absent) and VR flavor (entry points present, gated by setting).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `Grep` for `TODO(phase-01)` returns zero hits (placeholder replaced in Step 05.1).
- [x] Dev log entries added for all 6 files in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Entry points 1 and 2 are live. Phase 06 adds entry point 3 (player overflow tear-off). Phase 07 is final docs and catalog.

---

## Rollback Plan

Revert phase commit(s). Menu items disappear; string resources cleaned up; `tearOffBrowse` and `openBrowseInNewWindow` methods removed. No persistent state affected.
