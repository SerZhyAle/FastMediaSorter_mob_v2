# Phase 07 - Programs scenario: menu + in-app card + MainActivity wiring

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** -
**Steps done:** 5 / 5
**Started:** 2026-06-29
**Completed:** 2026-06-29

> **Phase Step Log (2026-06-29):** `MainScreenRecordingMenuManager` (id 16, icon ic_video) + `MainScreenRecordingManager` (RECORD_AUDIO + POST_NOTIFICATIONS host launchers -> controller.launch; collectOnLifecycle card with timer + Stop, dismissed on background); coordinator registers order 4 + remove path; MainActivity injects controllers + state, 2 permission launchers, settings gate `isScreenRecordingEnabled` + panel-input term. Two `Timber.d("S0774:` tags inserted (in-app + service). `.\a.ps1 d` BUILD SUCCESSFUL; MainActivity 1329 LOC (< 1500); neuroslop no regression; no flavor guard. ticket-log gate fails until status -> BlockNeedUserTest (Phase 08).

---

## Objective

Surface the "Screen video recording" scenario in the programs menu and panel (gated by the toggle + capability), drive start via the consent flow, and show the in-app floating recording card (timer + Stop) mirroring `MainVoiceCaptureManager`. All scenario wiring lands in `MainProgramsMenuCoordinator` / new helpers, keeping `MainActivity` under the limit.

---

## Prerequisites

- [ ] Phase 01 done (`MainProgramsMenuCoordinator`).
- [ ] Phase 02 done (`AppSettings.screenRecordingEnabled` for the visibility gate).
- [ ] Phase 03 done (scenario/card strings).
- [ ] Phase 04 done (`ScreenRecordingStateController`, `ScreenVideoRecordingController`).
- [ ] Phase 05 done (engine + non-empty controller set on standard/noLegal).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainScreenRecordingMenuManager.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainScreenRecordingManager.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainProgramsMenuCoordinator.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | < 1500 |

> Reuse `ic_screen_capture` (or the existing screen-capture drawable) for the menu icon - confirm the exact name at impl time; do not invent a new asset unless none fits.

---

## Steps

### Step 07.1 - MainScreenRecordingMenuManager

**Files:** `MainScreenRecordingMenuManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Mirror `MainScreenRecordingMenuManager` on `MainQuickCaptureMenuManager`: constructor `onScreenRecording: () -> Unit`; `itemCount(enabled): Int`; `populate(popup, enabled, order): Int` adding `MENU_ITEM_SCREEN_RECORDING` with `R.string.screen_recording_menu_label` and the screen-capture icon when `enabled`; `handleMenuItem(itemId)` dispatching to `onScreenRecording`. Companion `const val MENU_ITEM_SCREEN_RECORDING = 16` (free id; current ids: 1,2,9,10,12,13,14,15).

**Verification:**

- `Glob` - file exists.
- `Grep` - `MENU_ITEM_SCREEN_RECORDING = 16` once; `class MainScreenRecordingMenuManager` once.

**Status:** `[x] done`

---

### Step 07.2 - MainScreenRecordingManager (start flow + floating card)

**Files:** `MainScreenRecordingManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Host-neutral manager mirroring `MainVoiceCaptureManager`'s dialog + timer mechanics, but driving the foreground service rather than a local `MediaRecorder`:
> - Constructor: `activity: FragmentActivity`, `coroutineScope`, `controller: ScreenVideoRecordingController?` (`= screenVideoRecordingControllers.firstOrNull()`), `stateController: ScreenRecordingStateController`, `requestRecordAudioPermission: () -> Unit`, `requestPostNotificationsPermission: () -> Unit`.
> - `start()`: if `controller == null` return; check `RECORD_AUDIO` (request via host launcher if missing); on API 33+ check `POST_NOTIFICATIONS` similarly; when both granted → `controller.launch(activity)`. Provide `onRecordAudioResult(granted)` / `onPostNotificationsResult(granted)` host callbacks that continue or show `R.string.screen_recording_permission_denied`.
> - Observe `stateController.isRecording` with `collectOnLifecycle` (lifecycle-safe): on `true` show the recording card - a non-cancelable `MaterialAlertDialogBuilder` with title `R.string.screen_recording_card_title`, message = live `mm:ss` timer driven from `stateController.startedAtElapsedRealtimeMs` via a `Handler` tick (mirror `MainVoiceCaptureManager.timerTick`), positive `R.string.screen_recording_stop` → `controller.requestStop(activity)`; on `false` dismiss + stop the tick.
> - No business logic beyond UI + delegation; no `MediaProjection`/`MediaRecorder` here (that lives in the service).

**Verification:**

- `Glob` - file exists.
- `Grep` - `class MainScreenRecordingManager`; `requestStop`, `collectOnLifecycle`, `isRecording`, `screen_recording_card_title` referenced.
- `Grep -n "lifecycleScope.launch {[^}]*collect"` → zero bare view-bound collects (must use `collectOnLifecycle`).

**Status:** `[x] done`

---

### Step 07.3 - Register the scenario in MainProgramsMenuCoordinator

**Files:** `MainProgramsMenuCoordinator.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Add `MENU_ORDER_SCREEN_RECORDING = 4` (free slot) and route the new item through the coordinator: include `MainScreenRecordingMenuManager.populate(..)` in `populateMainWindowDropdownMenu`, add its `itemCount` term to `getMainWindowDropdownMenuItemCount`, delegate `MENU_ITEM_SCREEN_RECORDING` in the dispatch, return `null` from `programNewWindowActionFor` (foreground service, not a window), and in `programRemoveActionFor` map it to a remove action that disables the toggle (`it.copy(screenRecordingEnabled = false)`) with the `R.string.screen_recording_menu_label` confirm - parallel to the `MENU_ITEM_QUICK_VOICE` case.

**Verification:**

- `Grep` - `MENU_ORDER_SCREEN_RECORDING = 4` and `MENU_ITEM_SCREEN_RECORDING` handled in the coordinator.
- `Grep` - `screenRecordingEnabled = false` present in the remove path.

**Status:** `[x] done`

---

### Step 07.4 - Wire MainActivity (managers, permission launchers, settings gate)

**Files:** `MainActivity.kt`
**Depends on:** Step 07.1, Step 07.2, Step 07.3

**Prompt for developer:**

> - `@Inject` the `Set<ScreenVideoRecordingController>` and `ScreenRecordingStateController`.
> - Register two `ActivityResultLauncher`s (RECORD_AUDIO, POST_NOTIFICATIONS) before STARTED; route results into `MainScreenRecordingManager.onRecordAudioResult/onPostNotificationsResult`.
> - Construct `MainScreenRecordingManager` and `MainScreenRecordingMenuManager(onScreenRecording = { screenRecordingManager.start() })`; hand the menu manager to `MainProgramsMenuCoordinator`.
> - In the settings collector, compute `isScreenRecordingEnabled = settings.screenRecordingEnabled && screenVideoRecordingControllers.isNotEmpty()`, add the `screenRecordingEnabledChanged` term to the panel-inputs change check, and pass the gate into the coordinator's populate path.
> - Release nothing extra on pause (the recording is a foreground service and must continue); the card is re-shown on return via the observed state.
> - Confirm `MainActivity.kt` stays < 1500 LOC; if close, move any incidental block into a helper.

**Verification:**

- `Grep` - `screenVideoRecordingControllers`, `ScreenRecordingStateController`, `screenRecordingEnabledChanged`, `isScreenRecordingEnabled` present in `MainActivity.kt`.
- `wc -l MainActivity.kt` → < 1500.
- `Grep` - no `BuildConfig.IS_*` flavor guard added.

**Status:** `[x] done`

---

### Step 07.5 - Build + accessibility pass

**Files:** (all of phase 07)
**Depends on:** Steps 07.1-07.4

**Prompt for developer:**

> Build standard debug. Confirm the menu/panel button carries a `contentDescription` (panel buttons set `contentDescription = item.title` via `item_main_program.xml` - inherited) and the card Stop button is focusable with a readable label/timer for TalkBack (strategic §3.2 accessibility). The recording state is conveyed by text ("Recording screen" + timer), not color alone.

**Verification:**

- `/build` (standard debug) green - or `.\a.ps1 fc`.
- `Grep` - card title + Stop label use string resources (no hardcoded English literals).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] All five steps `[x]`.
- [ ] `MainActivity.kt` < 1500 LOC.
- [ ] Standard debug build green.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] Dev log entry added for all touched files.

---

## Handoff Notes to Next Phase

Feature is functionally complete end-to-end: toggle → scenario button → consent → recording → notification/in-app stop → save. Phase 08 finalizes docs, catalog, and the capability inventory.

---

## Rollback Plan

Revert the phase commit - new helpers + additive coordinator/MainActivity wiring; the engine (Phase 05) and contracts (Phase 04) remain but are simply unreachable from the UI.
