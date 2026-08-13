# Phase 01 - Standalone Launch Surfaces

**Strategic spec:** [`../S0912_quick-launch-panel-programs-scenarios.md`](../S0912_quick-launch-panel-programs-scenarios.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Give the two Programs-and-Scenarios items that do not yet have a Context-only (no host Activity) launch form one - a reserved default-target path for quick camera, and a new trampoline Activity for link download - so Phase 02 can wire all four missing routes into the panel registry through a uniform launch contract. Quick voice and screen recording already have a Context-only form and need no change in this phase.

---

## Prerequisites

- [ ] Strategic spec `Status: Approved` or later.
- [ ] Research artifact `research/01__panel-programs-scenarios-gap.md` read in full.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManager.kt` | Modified | ≤ 290 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLinkDownloadManager.kt` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/LinkDownloadLaunchActivity.kt` | New | ≤ 40 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | n/a (manifest) |

---

## Steps

### Step 01.1 - Add a panel-default-target sentinel to the quick-camera launch manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `CameraQuickCaptureLaunchManager.loadTarget()` (around line 226) resolves a `CameraCaptureTarget` from `SharedPreferences` keyed by a real `appWidgetId` (`CameraQuickCaptureWidgetProvider.keyTargetIsCameraFolder(appWidgetId)` etc.). The app-launch panel has no `appWidgetId` and, per strategic §3.1/§6.1, always captures to the device camera folder with no target-picker step. Add a reserved constant `const val PANEL_APP_WIDGET_ID = -1000` to `CameraQuickCaptureLaunchManager`'s companion object (a value `AppWidgetManager` never assigns - real ids are non-negative - and distinct from `AppWidgetManager.INVALID_APPWIDGET_ID` (0), so the existing `appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID -> finish()` guard in `CameraQuickCaptureActivity.onCreate` still only rejects a truly missing extra). In `loadTarget()`, add one branch before the existing `SharedPreferences` lookup: `if (appWidgetId == PANEL_APP_WIDGET_ID) return CameraCaptureTarget.CameraFolder`. Do not touch `CameraQuickCaptureActivity` or `CameraQuickCaptureWidgetProvider` - `captureMode()` already defaults to `CAPTURE_MODE_PHOTO` for any id with no stored preference, so an unconfigured sentinel id naturally captures a photo, matching the owner's stated default (strategic §3.1 item 2).

**Verification:**

- `Grep` - `PANEL_APP_WIDGET_ID` in `CameraQuickCaptureLaunchManager.kt` matches at least twice (declaration + use in `loadTarget()`).
- `Grep` - `appWidgetId == PANEL_APP_WIDGET_ID` present in the same file.
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: widget/CameraQuickCaptureLaunchManager.kt (+8 LOC). Dev log recorded.

---

### Step 01.2 - Let the link-download dialog signal completion to a non-MainActivity host

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLinkDownloadManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `MainLinkDownloadManager.show()` builds a `MaterialAlertDialogBuilder` and never signals when it closes - the existing call site in `MainActivity` stays open regardless, so no callback exists. The panel's trampoline (step 01.3) has no other work to do once the dialog closes and must call `finish()` on every dismissal path (Ok, Cancel, tap-outside, back button) - not only the positive button. Add an optional constructor parameter `private val onClosed: () -> Unit = {}` to `MainLinkDownloadManager` and attach `.setOnDismissListener { onClosed() }` to the `MaterialAlertDialogBuilder` chain in `show()` before `.show()`. Do not add an `onDismiss`-specific parameter name that shadows `AlertDialog`'s own listener type - keep the class-level callback a plain `() -> Unit`. The existing `MainLinkDownloadManager(this)` call site in `MainActivity` keeps compiling unchanged (default `{}`).

**Verification:**

- `Grep` - `onClosed: () -> Unit = {}` present in `MainLinkDownloadManager.kt` constructor.
- `Grep` - `setOnDismissListener` present in `show()`.
- `Grep` - `MainLinkDownloadManager(this)` in `MainActivity.kt` still compiles with no argument added (build in Step 01.3 covers this transitively).

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS. Files: ui/main/helpers/MainLinkDownloadManager.kt (+7 LOC). Dev log recorded.

---

### Step 01.3 - Add a link-download trampoline Activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/LinkDownloadLaunchActivity.kt` (new), `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `LinkDownloadLaunchActivity` in the `widget` package, mirroring the shape of `ScreenRecordingLaunchActivity` / `QuickAudioRecorderActivity` (Rule 3 - transparent, no business logic, `AppCompatActivity`, not `@AndroidEntryPoint` - `MainLinkDownloadManager` needs no injected dependency). `onCreate` constructs `MainLinkDownloadManager(this) { finish() }` and calls `.show()`. Register it in `AndroidManifest.xml` immediately after the `ScreenRecordingLaunchActivity` entry (around line 475), with the exact same attributes as the other three panel-reachable trampolines: `android:exported="false"`, `android:theme="@style/Theme.FastMediaSorter.Transparent"`, `android:excludeFromRecents="true"`, `android:taskAffinity=""`, `android:noHistory="true"`. Precedent for a transparent trampoline hosting a `MaterialAlertDialogBuilder` dialog already exists in this codebase (`CameraQuickCaptureLaunchManager.showNameDialog()` inside `CameraQuickCaptureActivity`), so no new UI pattern is introduced.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/widget/LinkDownloadLaunchActivity.kt` exists.
- `Grep` - `class LinkDownloadLaunchActivity : AppCompatActivity()` matches exactly once.
- `Grep` - `MainLinkDownloadManager(this)` (with a trailing lambda) present in the new file.
- `Grep` - `.widget.LinkDownloadLaunchActivity` present in `AndroidManifest.xml` with `android:exported="false"` on the same tag.
- `Grep -n "Log\.d\("` returns zero hits in the new file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 5/5 PASS. Files: widget/LinkDownloadLaunchActivity.kt (new, 17 LOC), AndroidManifest.xml (+3 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` (CodeAndResources) BUILD SUCCESSFUL, 2026-07-03.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `CameraQuickCaptureLaunchManager.PANEL_APP_WIDGET_ID` is the constant Phase 02's `AppLaunchPanelRouteIntents.quickCamera()` must pass as the `AppWidgetManager.EXTRA_APPWIDGET_ID` extra.
- `LinkDownloadLaunchActivity` is the class Phase 02's `AppLaunchPanelRouteIntents.linkDownload()` must target.
- Note for `/spec-test-device`: the panel's quick-camera and quick-voice tiles reuse the widget-grade trampolines (`CameraQuickCaptureActivity`, `QuickAudioRecorderActivity`) - a single fixed mode/target, no in-screen photo/video chooser - not the richer `MainCameraCaptureManager` / `MainVoiceCaptureManager` flow the main-window menu uses, because the panel has no Activity host to hang a chooser or permission dialog off of (same Context-only constraint every other panel route already has). The quick-camera tile specifically always captures a **photo** (never video) to the default camera folder - `PANEL_APP_WIDGET_ID` has no stored capture-mode preference, and `CameraQuickCaptureWidgetProvider.captureMode()` defaults an unconfigured id to `CAPTURE_MODE_PHOTO`. This is expected behavior, not a regression to flag.

---

## Rollback Plan

Low-risk: revert this phase's commit(s). No Room schema, no Hilt scope, no data migration - `MainLinkDownloadManager`'s new parameter is additive-default, so the existing `MainActivity` call site is unaffected either way.
