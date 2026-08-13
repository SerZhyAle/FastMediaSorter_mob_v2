# Phase 01 - Foundations: enums + settings model + store

**Strategic spec:** [`../S0425_screenshot-gesture-actions.md`](../S0425_screenshot-gesture-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 0 / 5
**Started:** -
**Completed:** 2026-06-16

---

## Objective

Introduce the `ScreenshotGestureDirection` and `ScreenshotGestureAction` enums, replace the dead `screenshotGestureDownEnabled` boolean with three per-direction action fields on `AppSettings`, and persist them via `ScreenshotSettingsStore`. No capture, dispatch, or UI behaviour yet - settings round-trip only.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScreenshotGestureDirection.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScreenshotGestureAction.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/ScreenshotSettingsStore.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 1500 |

> Removing the dead boolean ripples to `SettingsRepositoryImpl`, `SettingsViewModel`, and the existing `rowScreenshotGestureDown` switch in `OperationsSettingsFragment` - all must be updated in this phase to keep the build green. The full picker UI lands in Phase 06.

---

## Steps

### Step 01.1 - Create `ScreenshotGestureDirection` enum

**Files:** `domain/model/ScreenshotGestureDirection.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `enum class ScreenshotGestureDirection { DOWN, RIGHT, UP }` in package `com.sza.fastmediasorter.domain.model`. This is the directional gesture identity threaded from the overlay manager to the dispatcher.

**Verification:**

- `Glob` - `domain/model/ScreenshotGestureDirection.kt` exists.
- `Grep` - `enum class ScreenshotGestureDirection` matches once.
- `Grep` - all three constants `DOWN`, `RIGHT`, `UP` present.

**Status:** `[ ]` not done

---

### Step 01.2 - Create `ScreenshotGestureAction` enum

**Files:** `domain/model/ScreenshotGestureAction.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `enum class ScreenshotGestureAction { SILENT_SCREENSHOT, OPEN_IN_PLAYER, OPEN_IN_DRAW, OCR_TRANSLATE, SHARE, DO_NOT_USE }` in `com.sza.fastmediasorter.domain.model`. Add a companion `fun fromName(name: String?): ScreenshotGestureAction` returning the matching constant or a passed-in default - used for DataStore string parsing tolerant of unknown values.

**Verification:**

- `Glob` - `domain/model/ScreenshotGestureAction.kt` exists.
- `Grep` - `enum class ScreenshotGestureAction` matches once.
- `Grep` - all six constants present.
- `Grep` - `fun fromName` present.

**Status:** `[ ]` not done

---

### Step 01.3 - Replace dead field on `AppSettings`

**Files:** `domain/model/AppSettings.kt`
**Depends on:** Step 01.1, 01.2

**Prompt for developer:**

> Remove `val screenshotGestureDownEnabled: Boolean = true`. Add three fields: `val screenshotGestureActionDown: ScreenshotGestureAction = ScreenshotGestureAction.SILENT_SCREENSHOT`, `val screenshotGestureActionRight: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE`, `val screenshotGestureActionUp: ScreenshotGestureAction = ScreenshotGestureAction.DO_NOT_USE`. Defaults preserve the current silent-down behaviour and leave the two new directions inert.

**Verification:**

- `Grep` - `screenshotGestureDownEnabled` returns zero hits in `AppSettings.kt`.
- `Grep` - `screenshotGestureActionDown`, `screenshotGestureActionRight`, `screenshotGestureActionUp` each present once.

**Status:** `[ ]` not done

---

### Step 01.4 - Persist mapping in `ScreenshotSettingsStore`

**Files:** `data/repository/settings/ScreenshotSettingsStore.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Remove `KEY_SCREENSHOT_GESTURE_DOWN_ENABLED` and its read/write. Add three `stringPreferencesKey` entries: `screenshot_gesture_action_down`, `screenshot_gesture_action_right`, `screenshot_gesture_action_up`. In `Values`, replace `screenshotGestureDownEnabled` with three `ScreenshotGestureAction` fields. In `read`, parse each via `ScreenshotGestureAction.fromName(preferences[KEY], default)` using the same defaults as `AppSettings`. In `write`, store `settings.screenshotGestureActionX.name`. The legacy boolean key is dropped (it was never consumed at runtime - no behavioural migration needed).

**Verification:**

- `Grep` - `screenshot_gesture_down_enabled` returns zero hits in this file.
- `Grep` - `screenshot_gesture_action_down`, `..._right`, `..._up` each present.
- `Grep` - `ScreenshotGestureAction.fromName` present in `read`.

**Status:** `[ ]` not done

---

### Step 01.5 - Update mapping/default call sites

**Files:** `data/repository/SettingsRepositoryImpl.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> In `SettingsRepositoryImpl` (~L397) map the three new store fields into `AppSettings` instead of `screenshotGestureDownEnabled`. In `SettingsViewModel` (~L392) replace the `screenshotGestureDownEnabled = defaults.screenshotGestureDownEnabled` reset line with the three new fields. In `OperationsSettingsFragment` remove the now-orphaned `rowScreenshotGestureDown` read/sync (~L633-634) and its `setOnCheckedChangeListener` (~L989-991) - the full picker UI is added in Phase 06; for this phase the row binding is left unbound (Phase 06 replaces the layout row). Do not add picker logic here.

**Verification:**

- `Grep` - `screenshotGestureDownEnabled` returns zero hits across `app_v2/src/main` (`Grep -r`).
- `.\a.ps1 fk` - Kotlin compiles (the layout still declares `rowScreenshotGestureDown`; binding field stays generated until Phase 06 - no reference to it remains in Kotlin).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk` sufficient for this compile-only phase).
- [ ] `Grep` for `screenshotGestureDownEnabled` across `app_v2/src` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`AppSettings` now carries three `ScreenshotGestureAction` fields with silent-down default. The enums exist in `domain/model`. The dead boolean and its DataStore key are gone. `OperationsSettingsFragment` no longer references the old switch (its layout row is replaced in Phase 06).

---

## Rollback Plan

Revert phase commit(s). No Room or user-data migration - DataStore simply ignores the removed key and falls back to enum defaults on next read.
