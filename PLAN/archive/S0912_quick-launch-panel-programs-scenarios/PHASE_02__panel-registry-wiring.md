# Phase 02 - Panel Registry Wiring

**Strategic spec:** [`../S0912_quick-launch-panel-programs-scenarios.md`](../S0912_quick-launch-panel-programs-scenarios.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Register the four missing Programs-and-Scenarios routes (quick camera, quick voice, screen recording, link download) in the app-launch panel's existing registry/intents/availability triad, so they appear in the panel's "add feature" picker and launch correctly with no picker-UI change.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`CameraQuickCaptureLaunchManager.PANEL_APP_WIDGET_ID` and `LinkDownloadLaunchActivity` exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/InternalRouteCatalog.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/AppLaunchPanelRouteIntents.kt` | Modified | ≤ 75 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt` | Modified | ≤ 100 |

---

## Steps

### Step 02.1 - Register the four routes in the catalog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/InternalRouteCatalog.kt`
**Depends on:** - start of phase (Phase 01 already done)

**Prompt for developer:**

> Add four `KEY_*` constants - `KEY_QUICK_CAMERA = "quick_camera"`, `KEY_QUICK_VOICE = "quick_voice"`, `KEY_SCREEN_RECORDING = "screen_recording"`, `KEY_LINK_DOWNLOAD = "link_download"` - and four matching `Route(...)` entries appended to the `routes` list. Reuse the exact label/icon resources the main-window Programs-and-Scenarios menu already uses for the same feature, so the panel picker and the main menu never drift in wording (strategic §3.3 "UI placement contract"): quick camera -> `R.string.quick_camera_menu_label` / `R.drawable.ic_camera_capture`; quick voice -> `R.string.quick_voice_menu_label` / `R.drawable.ic_microphone`; screen recording -> `R.string.screen_recording_menu_label` / `R.drawable.ic_display`; link download -> `R.string.download_by_link_menu_label` / `R.drawable.ic_cloud_download`. None of the four need a `settingsIntent` - like three of the five existing routes (calculator, OCR, streams, favorites all lack one; only `game` has one), a compiled-but-disabled tile simply does not launch, which is the already-accepted majority behavior, not a new gap. Do not touch `KEY_FAVORITES` - out of scope (strategic §2 non-goals).

**Verification:**

- `Grep` - `KEY_QUICK_CAMERA`, `KEY_QUICK_VOICE`, `KEY_SCREEN_RECORDING`, `KEY_LINK_DOWNLOAD` each match at least twice in the file (const + use in a `Route(...)`).
- `Grep` - `        Route($` (indented instantiation, excludes the `data class Route(` declaration) matches exactly 9 times (5 existing + 4 new).
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS (4 keys x2 occurrences each; 9 indented `Route(` instantiations; 0 `Log.d(` hits). Files: core/panel/InternalRouteCatalog.kt (+26 LOC). Dev log recorded.

---

### Step 02.2 - Add intent builders for the four routes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/AppLaunchPanelRouteIntents.kt`
**Depends on:** Step 02.1 (route keys exist), Phase 01 (`PANEL_APP_WIDGET_ID`, `LinkDownloadLaunchActivity`)

**Prompt for developer:**

> Add four functions, each returning `.withPanelFlags()` like the existing ones:
> - `quickCamera(context)` -> `Intent(context, CameraQuickCaptureActivity::class.java).apply { action = CameraQuickCaptureActivity.ACTION_CAPTURE; putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, CameraQuickCaptureLaunchManager.PANEL_APP_WIDGET_ID) }` - matches how `CameraQuickCaptureWidgetProvider` itself builds this intent, minus the real widget id.
> - `quickVoice(context)` -> `Intent(context, QuickAudioRecorderActivity::class.java).apply { action = QuickAudioRecorderActivity.ACTION_TOGGLE }`.
> - `screenRecording(context)` -> `Intent(context, ScreenRecordingLaunchActivity::class.java)`.
> - `linkDownload(context)` -> `Intent(context, LinkDownloadLaunchActivity::class.java)`.
>
> Add the four needed imports (`com.sza.fastmediasorter.widget.CameraQuickCaptureActivity`, `.CameraQuickCaptureLaunchManager`, `.QuickAudioRecorderActivity`, `.ScreenRecordingLaunchActivity`, `.LinkDownloadLaunchActivity`, and `android.appwidget.AppWidgetManager`). Every builder still ends with `.withPanelFlags()` exactly like the five existing ones - do not introduce a second flag-adding helper.

**Verification:**

- `Grep` - `fun quickCamera(context: Context)`, `fun quickVoice(context: Context)`, `fun screenRecording(context: Context)`, `fun linkDownload(context: Context)` each match exactly once.
- `Grep` - `withPanelFlags()` matches 11 times in the file (6 existing call sites incl. `resource()` + 4 new call sites + 1 declaration).
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 3/3 PASS (4 new functions each match exactly once; `withPanelFlags()` x11; 0 `Log.d(` hits). Files: core/panel/AppLaunchPanelRouteIntents.kt (+21 LOC). Dev log recorded.

---

### Step 02.3 - Extend availability resolution for the four routes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt`
**Depends on:** Step 02.1 (route keys exist)

**Prompt for developer:**

> Inject two more dependencies into the existing `@Inject constructor`: `private val mediaCapabilities: MediaCapabilities` (`com.sza.fastmediasorter.core.capability.MediaCapabilities` - already Hilt-provided per flavor, no new module needed) and `private val screenVideoRecordingControllers: Set<@JvmSuppressWildcards ScreenVideoRecordingController>` (`com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController` - already a Hilt multibound set, empty on flavors without the capture engine, exactly as `MainActivity` already injects it). Refactor `invoke()` and `all()` to fetch `val settings = settingsRepository.getSettings().first()` once and pass it into `resolve(routeKey, settings)`, replacing the current two-boolean-parameter shape (`gameEnabled`, `favoritesEnabled`) - read `settings.embeddedGameEnabled` / `settings.enableFavorites` directly inside `resolve()` from the passed `settings` instead. Add four branches to the `when (routeKey)` in `resolve()`:
> - `KEY_QUICK_CAMERA` -> `Availability(availableInBuild = mediaCapabilities.supportsImages, enabledAtRuntime = !settings.disableCameraCapture)`. **Photo only, not video**: per Phase 01 Step 01.1, the panel tile always resolves to `CameraQuickCaptureWidgetProvider`'s default capture mode (`CAPTURE_MODE_PHOTO`) because it has no per-instance config step (strategic §2 non-goals) - gate on the photo capability/toggle pair only, not the video one, so the tile's declared availability matches what it actually does.
> - `KEY_QUICK_VOICE` -> `Availability(availableInBuild = mediaCapabilities.supportsMicRecording, enabledAtRuntime = settings.micRecordingEnabled)`.
> - `KEY_SCREEN_RECORDING` -> `Availability(availableInBuild = screenVideoRecordingControllers.isNotEmpty(), enabledAtRuntime = settings.screenRecordingEnabled)`.
> - `KEY_LINK_DOWNLOAD` -> `Availability(availableInBuild = true, enabledAtRuntime = settings.linkAutoDownloadEnabled)`.
>
> This addresses strategic §5.3/§9 (ADR none, but §7 risk row 2): availability now lives entirely inside this one `resolve()` function per route, with no separate toggle elsewhere to forget when a sixth route is added later.

**Verification:**

- `Grep` - `mediaCapabilities: MediaCapabilities` and `screenVideoRecordingControllers: Set` both present in the constructor.
- `Grep` - `KEY_QUICK_CAMERA ->`, `KEY_QUICK_VOICE ->`, `KEY_SCREEN_RECORDING ->`, `KEY_LINK_DOWNLOAD ->` each match exactly once inside `resolve()`.
- `Grep` - `settingsRepository.getSettings().first()` matches exactly twice (`invoke()` and `all()`), each assigned to a local `settings` value passed into `resolve(`.
- `Grep -n "Log\.d\("` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 5/5 PASS. Files: domain/usecase/panel/ResolvePanelRouteAvailabilityUseCase.kt (+30 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL, 2026-07-03 (Hilt graph resolved with the two new injected dependencies).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (via `post-change.ps1`'s `catalog-sync` step, run after each of this phase's 3 edits).

---

## Handoff Notes to Next Phase

All four routes are now registered, launchable, and correctly gated. Phase 03 only needs catalog regen, dev log, and the feature-inventory record - no further source changes.

---

## Rollback Plan

Low-risk: revert this phase's commit(s). No Room schema, no Hilt scope/qualifier, no data migration - `ResolvePanelRouteAvailabilityUseCase`'s two new constructor parameters resolve from already-existing Hilt bindings.
