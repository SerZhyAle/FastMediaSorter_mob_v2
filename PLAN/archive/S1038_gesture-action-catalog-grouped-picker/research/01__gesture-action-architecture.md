# S1038 research 01 - gesture-action architecture + integration map

**Date:** 2026-07-14
**Scope:** app_v2. Gesture catalog compiles only on `standard` (`fms.screenCapture=on`) + `noLegal`; `lite`/`photos`/`legacy` resolve empty multibindings - out of scope.

## Current catalog + key classes

| Concern | File | Notes |
|---|---|---|
| Action enum (18 today) | domain/model/ScreenshotGestureAction.kt | assignable actions; no group/explanation/permission metadata |
| Zones / directions (4x3=12 slots) | domain/model/ScreenshotGestureZone.kt, ScreenshotGestureDirection.kt | |
| Picker | ui/settings/helpers/ScreenshotGestureActionPickerManager.kt (84) | flat `ListSelectionDialog<ScreenshotGestureAction>`; `availableActions()` filters by `capabilityAvailability` + `screenRecordingAvailable` boolean |
| Dispatcher | core/screencapture/ScreenshotGestureActionDispatcher.kt (317) | `handlePreCaptureAction` + `runPostSave` exhaustive `when` (no else); trampoline-Activity launches with FLAG_ACTIVITY_NEW_TASK; ACTION_VIEW already used (openInViewer :246-271) |
| Per-slot storage | data/repository/settings/ScreenshotSettingsStore.kt (151) | 12 slots stored as `stringPreferencesKey` of enum `.name`; NO per-slot string payload exists |
| AppSettings gesture fields | domain/model/AppSettings.kt:196-218, 385-424 | 12 action fields + `screenshotGestureAction(zone,dir)` when |
| UI host | ui/settings/helpers/OperationsGesturesManager.kt | row click -> picker -> `updateSettings(setAction(..))` |
| Accessibility (noLegal) | src/noLegal/.../screencapture/ScreenshotAccessibilityService.kt (272) | only `takeScreenshot`; NO `performGlobalAction` anywhere; runtime liveness via ScreenshotAccessibilityServiceHolder.instance |
| Flavor seam | multibinding (`screenVideoRecordingControllers.isNotEmpty()`), NOT BuildConfig | ScreenGestureOverlayModule `@Multibinds` (main, empty) + noLegal `@IntoSet` |

## Reusable precedents

- **Grouped picker template:** `ui/settings/fragments/PermissionRowAdapter.kt` (sealed Header/Entry, per-entry description `tv_perm_entry_desc`) and `ui/keybinding/KeybindingListAdapter.kt` (sealed Header/Row, DiffUtil, 2 view types). Use the PermissionRowAdapter shape.
- **Volume:** AudioManager `getStreamVolume`/`setStreamVolume(STREAM_MUSIC)` in ui/player/helpers/StandaloneVideoTouchDelegate.kt / VideoTouchDelegate.kt.
- **URL open:** ACTION_VIEW + `runCatching { startActivity }.onFailure { Timber.w }` (dispatcher openInViewer).
- **Trampoline:** every launch action starts a transparent same-app Activity (CameraLaunchActivity etc.) because the dispatcher runs in a Service with no task.

## Net-new (no precedent)

- System brightness (`Settings.System.SCREEN_BRIGHTNESS`) + `WRITE_SETTINGS` request (`ACTION_MANAGE_WRITE_SETTINGS`, `Settings.System.canWrite`) - zero references in src/main. Needs a FragmentActivity trampoline for the grant flow.
- `performGlobalAction` accessibility global actions (shade/quick-settings/lock/split-screen/recents) - zero references. `GLOBAL_ACTION_LOCK_SCREEN` API 28+, `TOGGLE_SPLIT_SCREEN` API 24+ (narrowed in newer OS).
- Package-visibility `<queries>` for Keep/Gemini/assistant/alarm/calendar targets - check AndroidManifest during impl.
- Media keys: use AudioManager `dispatchMediaKeyEvent(KeyEvent)` (play-pause/next/prev) - no existing helper.

## Resolved forks (author open questions -> decisions for the plan)

1. Grouped-picker component: **dedicated sealed Header/Entry adapter** mirroring PermissionRowAdapter (gesture-specific, renders explanation per item). Do NOT generify ListSelectionDialog.
2. Accessibility group gating: **compile-time flavor** (the accessibility action group only exists in the noLegal path). Within noLegal, if the service is not runtime-enabled (holder.instance == null), the action degrades safely (no-op + Timber.w). Matches spec §3.2 "видны только где сервис доступен" + "явная деградация".
3. Dispatcher size: **split per-class up front** (DeviceActionHandler / MediaActionHandler / LaunchActionHandler / AccessibilityActionHandler) per ADR-2 - keeps the 317-LOC dispatcher from crossing 1500 and gives uniform add points.

## §6 owner-acknowledged forks (recommendations, non-blocking)

- Volume/media: separate items (up/down/mute; play-pause/next/prev) - one gesture = one action.
- Keep: try create-note intent -> fallback launch Keep -> safe no-op.
- Gemini vs assistant: keep both (assistant = ACTION_ASSIST system default; Gemini = the Gemini app with degradation).
- URL input: per-slot payload (§5.5) + a simple input dialog next to the slot.

## Risks

- No test coverage on any of the 5 touched classes (High) - add targeted unit tests where pure (settings round-trip, action metadata mapping).
- `runPostSave` exhaustive `when` (no else) - every new enum value forces a compile break here (good, but a large mechanical diff) - group no-op actions into an explicit branch.
- Dispatcher is a Service context - Activity-launching / permission-grant actions need trampolines.
- Per-slot payload must be introduced once, shared with S1036 (ADR-3) - avoid divergent storage.
