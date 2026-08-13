# Phase 01 - grouped-picker-infra

**Goal:** Rework the flat action picker into a grouped list with per-option explanations, using the EXISTING 18 actions (no new actions yet, so the rework is independently verifiable).

## Steps

- [ ] **1.1** Add action metadata: a group id + explanation to each `ScreenshotGestureAction`. Model it as a separate catalog map/companion (e.g. `ScreenshotGestureActionCatalog`) mapping action -> (groupRes, labelRes, explanationRes) rather than bloating the enum. Define the group enum (CAPTURE, CAMERA, LAUNCH, DEVICE, SYSTEM, UTILITY, DISABLED) with title string resources. Verify: catalog covers all 18 current actions; compiles.
- [ ] **1.2** Build a grouped picker: a dedicated sealed `GesturePickerRow { Header(groupTitle); Entry(action, label, explanation, enabled) }` + RecyclerView adapter with 2 view types, mirroring `ui/settings/fragments/PermissionRowAdapter.kt` (header + per-entry description). Rows focusable (D-pad/keyboard). Verify: adapter builds sectioned list; `a.ps1 fk` compiles.
- [ ] **1.3** Replace the flat `ListSelectionDialog<ScreenshotGestureAction>` call in `ScreenshotGestureActionPickerManager.showPicker` with the grouped picker. Keep the existing capability filtering (`availableActions()` / `screenRecordingAvailable`) - now applied per group, hiding empty groups. `onPicked(action)` callback unchanged so `OperationsGesturesManager` wiring is untouched. Verify: picker opens grouped; selecting an action still persists via existing path.
- [ ] **1.4** Strings: group titles + explanation for each existing action, EN/RU/UK via `set-android-string.ps1 -Action add`. Verify: `check_strings_localized.ps1 -KeyPrefix "gesture_action_group_"` and `..._explain_` exit 0.

## Done criteria
- Grouped picker replaces the flat list; all 18 existing actions selectable with explanations; standard debug builds green.
