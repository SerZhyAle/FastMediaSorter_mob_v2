# Phase 03 - Settings Menu Action

**Strategic spec:** [`../S0559_split-screencapture-menu-standard.md`](../S0559_split-screencapture-menu-standard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Surface a single "Take screenshot" action in the Operations settings tab, shown only when `Set<MenuScreenshotLauncher>` is non-empty (standard + noLegal), wired to start the confirmable capture. Trilingual label, full keyboard/D-pad/mouse focusability. Reuses the existing screenshot destination setting - no new destination UI.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`Set<MenuScreenshotLauncher>` injectable).
- [ ] UI placement: a single action row in the existing Operations destinations layout near the screenshot-gesture group, following the existing `btnSelectCameraPhotosDest` one-shot button pattern. No new card, so no separate `/ui-clarify` pass required (strategic §6.1).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ≤ 900 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> **Landscape parity (MANDATORY):** `fragment_settings_destinations.xml` has a `layout-land/` counterpart - the new action row id must be added to BOTH files with identical id, or `ViewBinding` makes the field nullable in one orientation.

---

## Steps

### Step 03.1 - Add the trilingual action label

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one string key `settings_take_screenshot_now` across EN/RU/UK in lockstep using `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Module app_v2 -Key settings_take_screenshot_now -En "Take screenshot" -Ru "Сделать скриншот" -Uk "Зробити знімок екрана"`. The label is an explicit user action (button), so it follows the imperative action wording in `docs/COMMUNICATION_POLICY.md` §2; verify against the §6 tone checklist (no raw exception text, no "are you sure", concise imperative).

**Verification:**

- `Grep` - `settings_take_screenshot_now` present in all three of `values/`, `values-ru/`, `values-uk/` `strings.xml`.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_take_screenshot_now"` exits 0.

**Status:** `[ ]` not done

---

### Step 03.2 - Add the action row to both layouts

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`, `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> In BOTH layout files add a NEW standalone `MaterialCardView` `@+id/groupMenuScreenshot` as a sibling immediately before `groupScreenGestures` (NOT inside it - `groupScreenGestures` is hidden entirely on standard when the gesture controller set is empty, which would hide the menu action too). The card holds a single `@+id/btnTakeScreenshotNow` `MaterialButton` using the same `style="@style/Widget.FastMediaSorter.SettingsButton.Outlined"` as the neighbouring `btnOpenAccessibilitySettings` (theme attrs only, no hardcoded hex), `android:text="@string/settings_take_screenshot_now"`, `android:focusable="true"`, `android:clickable="true"`. Neighbouring buttons set no explicit `nextFocus*` (default traversal order), so match that. The card + button ids and attributes must be identical in portrait and landscape (land counterpart `groupScreenGestures` is at line ~1027).

**Verification:**

- `Grep` - `btnTakeScreenshotNow` present in `layout/fragment_settings_destinations.xml`.
- `Grep` - `btnTakeScreenshotNow` present in `layout-land/fragment_settings_destinations.xml`.
- `Grep` - `@string/settings_take_screenshot_now` referenced in both files.
- `Grep` - no `="#` hardcoded hex on the new button in either file.

**Status:** `[ ]` not done

---

### Step 03.3 - Wire show/hide + click in the capture manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `fun setupScreenshotAction(launchers: Set<MenuScreenshotLauncher>, activity: Activity)` to `OperationsCaptureManager` that sets `binding.groupMenuScreenshot` visibility to `View.VISIBLE` when the set is non-empty and `View.GONE` when empty (mirroring how `OperationsGesturesManager` hides `groupScreenGestures` on empty controllers), and wires `binding.btnTakeScreenshotNow` click to `launchers.first().launch(activity)`. Keep all business logic in the manager - the fragment only forwards the injected set and activity. No logging needed (an empty set just hides the card, no click is possible).

**Verification:**

- `Grep` - `btnTakeScreenshotNow` referenced in `OperationsCaptureManager.kt`.
- `Grep` - `MenuScreenshotLauncher` referenced in `OperationsCaptureManager.kt`.
- `Grep -n "Log\.d\("` returns zero hits in `OperationsCaptureManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.4 - Inject the set and forward to the manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `OperationsSettingsFragment` add `@Inject lateinit var menuScreenshotLaunchers: Set<@JvmSuppressWildcards MenuScreenshotLauncher>` (mirror the existing `screenGestureControllers` injection), and in the same place the fragment already calls `OperationsCaptureManager` setup, forward `menuScreenshotLaunchers` and the host activity to the new manager method from Step 03.3. The fragment must contain no capture/visibility logic itself - it only injects and forwards.

**Verification:**

- `Grep` - `menuScreenshotLaunchers` present in `OperationsSettingsFragment.kt`.
- `Grep` - `Set<@JvmSuppressWildcards MenuScreenshotLauncher>` present.
- `Grep` - the fragment forwards the set to the `OperationsCaptureManager` method (the Step 03.3 method name appears in the fragment).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `assembleStandardDebug` succeeds. Run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_take_screenshot_now"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- The standard flavor now has a complete menu-triggered confirmable screenshot path. Phase 04 regenerates the catalog and records the capability; on-device verification tags are inserted by `/spec-dev` before the final build.

---

## Rollback Plan

Revert the phase commit(s): remove the action row from both layouts, the manager wiring, the fragment injection, and the three string keys (`set-android-string.ps1 -Action remove`). No data migration; the launcher seam from Phase 02 stays dormant.
