# Specification: Ad hoc — Player Filename Overlay Auto-Hide

**Status:** Draft  
**Date:** 2026-04-17  
**Tier:** Untracked — ad hoc player UX improvement (4–8h, medium risk)  
**Roadmap entry:** Not present in `PLAN/IMPROVEMENT_ROADMAP.md`; user request: auto-hide the top-left filename overlay by file type and re-show it on file switch, pause, and image zoom.

---

## 1. Problem Statement

The player already renders the top-left filename overlay via `tvFileNameOverlay` in `activity_player_unified.xml`, fills its text in `PlayerUiStateCoordinator.kt`, and toggles its visibility in `PlayerDialogAndUiStateManager.kt`. Today that overlay remains visible whenever the command panel is visible, which is useful for sorting but obstructs the first lines of TXT/PDF/EPUB content and parts of images. The current architecture has no dedicated timer, no lifecycle-aware hide/resume behavior, and no interaction-driven re-show path for pause or zoom.

---

## 2. Goals

1. Add automatic hide timing for `tvFileNameOverlay` based on the currently opened media type.
2. Re-show the overlay whenever the user navigates to another file and restart the correct timeout for the new file type.
3. Re-show or extend the overlay timeout on non-fullscreen pause interactions for video, audio, and animated image playback.
4. Re-show or extend the overlay timeout on non-fullscreen image zoom interactions using the existing `PhotoView` gesture pipeline.
5. Keep the solution inside the player UI/helper layer without pushing ephemeral overlay timer state into persistence or unrelated domain layers.

Non-goals for this spec: changing fullscreen-only behavior, adding a user setting for custom timeout values, redesigning the overlay layout, changing the toolbar title format, or modifying StandalonePlayer behavior.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Full scope: TXT, EPUB, PDF, video, image/GIF, audio. |
| `lite`     | ✅ | Partial scope only: video, image/GIF, audio. Document branches are unreachable because `BuildConfig.SUPPORT_DOCUMENTS=false`. |
| `photos`   | ✅ | Image/GIF-only branch. No video/audio/document pause rules apply because `BuildConfig.SUPPORT_VIDEO=false` and `BuildConfig.SUPPORT_AUDIO=false`. |
| `legacy`   | ✅ | Same behavioral scope as `standard`, but must keep API 23-compatible timer/animation code paths. |

Existing `BuildConfig` gating is sufficient: `SUPPORT_VIDEO`, `SUPPORT_AUDIO`, `SUPPORT_IMAGES`, and `SUPPORT_DOCUMENTS` already define which player branches are reachable in each flavor. No new `BuildConfig` flag is required in `app_v2/build.gradle.kts`.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | Use `Handler(Looper.getMainLooper())` plus standard `ViewPropertyAnimator`; do not rely on newer lifecycle animation helpers. |
| 26+ (standard minSdk) | Default implementation path; same timer/animation logic as legacy, but without special compatibility workarounds. |

No Android 10+/11+/14+ storage, permission, predictive-back, or package-visibility forks are expected because this feature is purely in-player UI behavior.

### 3.3 Wear OS Impact

No Wear OS changes required.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `tvFileNameOverlay` | `app_v2/src/main/res/layout/activity_player_unified.xml` and `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Top-left filename/counter badge shown over player content. |
| `PlayerUiStateCoordinator` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt` | Formats overlay text from `currentFile`, `currentIndex`, and `files.size`. |
| `PlayerDialogAndUiStateManager` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt` | Shows/hides the overlay together with command-panel/fullscreen state. |
| `PlayerViewModel` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Holds current file, current index, pause state, and slideshow state. |
| `PlayerLifecycleManager` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Coordinates `onResume()` / `onPause()` / `onDestroy()` cleanup, but has no overlay-timer logic. |
| `ImageLoadingManager` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` | Configures `PhotoView`, including `setOnScaleChangeListener`, currently used only for debug logging. |
| `PlayerControlsSetupManager` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerControlsSetupManager.kt` | Toggles pause/play from the visible playback controls. |
| `PlayerNavigationManager` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationManager.kt` | Advances to next/previous file and synchronizes slideshow pause state. |

The key limitation is that overlay content and overlay visibility are split across helper classes, but there is no single owner for timed visibility. Because of that, the current overlay is either simply visible or hidden based on panel mode, with no notion of remaining timeout, interaction extension, or lifecycle pause/resume.

---

## 5. Proposed Architecture

### 5.1 UI-layer timed overlay manager

Introduce a dedicated UI helper named `FilenameOverlayAutoHideManager` under `ui/player/helpers/`. It will own timeout computation, hide scheduling, remaining-time bookkeeping, fade-in/fade-out animation, and pause/resume semantics for the existing `tvFileNameOverlay` view.

This manager should stay in the UI layer because the behavior is ephemeral presentation state, not domain state. The `PlayerViewModel` should continue to expose only stable player state such as current file, index, and paused status.

```kotlin
class FilenameOverlayAutoHideManager(
        private val overlayView: TextView,
        private val mainHandler: Handler,
        private val isCommandPanelVisible: () -> Boolean,
        private val isFullscreen: () -> Boolean
) {
        fun onFileShown(mediaType: MediaType)
        fun onPauseInteraction(mediaType: MediaType)
        fun onZoomInteraction(mediaType: MediaType)
        fun onHostPause()
        fun onHostResume(currentType: MediaType?)
        fun cancel()
}
```

Behavioral rules:

- `TXT` → 5000 ms.
- `PDF` / `EPUB` / `TEXT`-style document viewers → 10000 ms.
- `VIDEO` / `IMAGE` / `GIF` / `AUDIO` → 15000 ms.
- Re-show on file switch always resets the deadline from scratch.
- If the overlay is already hidden and the user pauses or zooms, re-show it and start a fresh timeout.
- If the overlay is still visible and the user pauses or zooms, extend the deadline by `currentTypeTimeoutMs` instead of merely resetting it.
- Fullscreen remains out of scope: if the relevant branch is in fullscreen mode, the manager ignores re-show requests.

### 5.2 New classes / files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `FilenameOverlayAutoHideManager.kt` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/` | ≤ 220 |
| `FilenameOverlayAutoHideManagerTest.kt` | `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/helpers/` | ≤ 220 |

The manager must remain small and single-purpose. If interaction branching expands beyond the current pause/zoom/file-switch scope, extract timeout-policy mapping into a second helper instead of letting the manager grow past ~250 lines.

### 5.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | Timer logic lives in `FilenameOverlayAutoHideManager`, not in `PlayerActivity`. |
| New classes follow naming (`VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`) | ✅ | `FilenameOverlayAutoHideManager` follows the existing helper-manager convention in `ui/player/helpers/`. |
| Data flow strictly `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | This change is purely presentational UI behavior; no new domain/data path is introduced. |
| No `Log.d()` — Timber only | ✅ | Any new diagnostics use `Timber`. |
| Room schema version incremented (if DB changes) | N/A | No database changes. |
| `StateFlow` for state, `SharedFlow` for one-shot events | N/A | No new persistent or shared player state is needed; overlay timer stays local to the UI manager. |
| Hilt DI: new bindings declared in module file | N/A | The helper can be constructed inside existing player helper wiring; no new injected binding is required. |

---

## 6. Data Flow

```text
PlayerViewModel.state.currentFile/currentIndex
        → PlayerUiStateCoordinator.updateUI()
        → set tvFileNameOverlay text
        → FilenameOverlayAutoHideManager.onFileShown(currentFile.type)

PlayerControlsSetupManager / PlayerNavigationManager / gesture callbacks
        → PlayerViewModel.togglePause() / setPaused()
        ←—— PlayerUiStateCoordinator observes updated state
        → FilenameOverlayAutoHideManager.onPauseInteraction(currentFile.type)

PhotoView pinch / zoom callback in ImageLoadingManager
        → FilenameOverlayAutoHideManager.onZoomInteraction(currentFile.type)

PlayerLifecycleManager.onPause()/onResume()/onDestroy()
        → FilenameOverlayAutoHideManager.onHostPause()/onHostResume()/cancel()

FilenameOverlayAutoHideManager
        → fade in/out tvFileNameOverlay
        → schedule/cancel hide runnable on main thread
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt` | Trigger overlay manager on file change and route media-type-specific reset behavior after text formatting. | 285 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt` | Make overlay visibility manager-aware instead of treating `tvFileNameOverlay` as a simple command-panel toggle. | 498 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerLifecycleManager.kt` | Pause/resume/cancel overlay timers during lifecycle transitions. | 470 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` | Convert existing `PhotoView` scale callback from debug-only logging into a zoom interaction source for overlay re-show. | 2145 lines |

If `ImageLoadingManager.kt` is modified, create a timestamped backup in `temp/` first because the file already far exceeds the 500-line safety threshold.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Duplicate hide runnables after rapid next/previous navigation | Med | Centralize scheduling/cancellation in one manager; every new trigger must cancel the previous runnable before arming a new one. |
| Overlay flicker when command-panel state changes near timer expiry | Med | Let only one manager decide final overlay visibility; `PlayerDialogAndUiStateManager` should delegate instead of forcing `isVisible` directly in multiple branches. |
| False zoom triggers from initial PhotoView scale callbacks during image load | Med | Ignore the first scale callback until image content is fully displayed, or gate zoom-triggered re-show behind a real delta from the baseline scale. |
| Overlay remains hidden after app resume or rotation | Low | Preserve remaining timeout on host pause/resume and explicitly re-evaluate current file state on resume. |
| Accessibility regression if hide uses `gone` | Low | Animate `alpha` and keep the view present in layout/accessibility tree when practical. |

---

## 9. Testing Plan

### 9.1 Unit Tests

- `FilenameOverlayAutoHideManagerTest`
    - `onFileShown()` uses 5s for TXT.
    - `onFileShown()` uses 10s for PDF/EPUB/TEXT.
    - `onFileShown()` uses 15s for VIDEO/IMAGE/GIF/AUDIO.
    - `onPauseInteraction()` re-shows when already hidden.
    - `onPauseInteraction()` extends the remaining deadline when still visible.
    - `onZoomInteraction()` is ignored for unsupported media types.
    - `onHostPause()` and `onHostResume()` preserve/cancel time correctly.

### 9.2 Manual Test Cases

1. Open a TXT file in non-fullscreen player mode and verify the top-left overlay hides after 5 seconds.
2. Open a PDF and verify the overlay hides after 10 seconds without shifting the document content downward.
3. Open a video in non-fullscreen mode, wait until the overlay hides, then pause playback and verify the overlay re-appears for 15 seconds.
4. Open a video, pause before timeout expiry, and verify the deadline is extended instead of abruptly restarting a shorter timer.
5. Open an image, wait until the overlay hides, pinch-zoom, and verify the overlay re-appears.
6. Navigate quickly through 5+ files and verify only one overlay timer is active and the filename/counter always matches the current file.
7. Put the app in background while the overlay is visible, return to foreground, and verify the overlay timer resumes or re-evaluates cleanly instead of staying stuck hidden/visible.
8. Error-state check: open an unsupported branch for a given flavor (`photos` has no audio/video, `lite` has no documents) and verify no dead code path tries to trigger unsupported overlay rules.

### 9.3 Maestro E2E (if applicable)

Add `maestro/smoke/player-filename-autohide.yaml` covering at least one TXT timeout path, one file-switch reset path, and one image zoom re-show path. Video pause timing can remain a manual test initially if emulator timing proves flaky.

---

## 10. Accessibility

This feature changes visibility timing for an existing UI element, not the interaction model. The overlay must remain readable in its existing contrast treatment, must not use `gone` in a way that removes filename access unexpectedly for TalkBack users, and must continue to avoid overlapping interactive controls. Because the overlay itself is not tappable, there are no new touch targets, but the fade behavior must still respect system animation settings and avoid rapid flashing.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN): `- **Smart filename overlay auto-hide**: The top-left filename/counter badge now hides automatically after a media-type-specific delay and re-appears when you switch files, pause playback, or zoom images in non-fullscreen mode.`
- `docs/FEATURES_RU.md` (RU): `- **Умное авто-скрытие плашки имени файла**: Верхняя левая плашка с именем файла и счётчиком автоматически скрывается через разное время для разных типов медиа и снова появляется при переключении файлов, паузе или зуме изображения в не-полноэкранном режиме.`
- `docs/FEATURES_UK.md` (UK): `- **Розумне автоприховування плашки з назвою файла**: Верхня ліва плашка з назвою файла та лічильником автоматично ховається через різний час для різних типів медіа й знову з'являється при перемиканні файлів, паузі або масштабуванні зображення в не-повноекранному режимі.`

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Keep overlay timer state out of `PlayerViewModel`**
- **Decision:** Store timeout bookkeeping in a UI helper manager, not in `PlayerViewModel.PlayerState`.
- **Alternatives considered:** Add `isHeaderVisible` and deadline fields to `PlayerState`; persist overlay visibility in settings.
- **Reason:** The overlay timer is ephemeral presentation state tied to one `TextView` and should not increase the churn or persistence surface of the core player state machine.

**ADR-2: Extend remaining time instead of simple reset for pause/zoom while visible**
- **Decision:** When the overlay is still visible, add the full file-type timeout to the remaining deadline.
- **Alternatives considered:** Restart timeout from zero on every pause/zoom; ignore interactions while visible.
- **Reason:** The user request explicitly asks to preserve the current remaining time and add more time, which better matches the sorting workflow.

**ADR-3: Keep fullscreen out of scope for this change**
- **Decision:** The auto-hide/re-show rules apply only when the player is not in fullscreen mode.
- **Alternatives considered:** Reuse the same rules in fullscreen immediately.
- **Reason:** `PlayerDialogAndUiStateManager` currently treats fullscreen as a separate UI mode with different panel visibility rules; mixing both concerns in one change raises regression risk without user validation.

---

## 13. Implementation Steps

1. Read and confirm all current overlay-related comments in `PlayerUiStateCoordinator.kt`, `PlayerDialogAndUiStateManager.kt`, and `ImageLoadingManager.kt` before changing behavior.
2. Create `FilenameOverlayAutoHideManager.kt` in `ui/player/helpers/` with timeout mapping, show/hide animation, extend/re-show behavior, and lifecycle-aware scheduling.
3. Wire `PlayerDialogAndUiStateManager.kt` to own or delegate to the new manager so overlay visibility has a single source of truth.
4. Update `PlayerUiStateCoordinator.kt` so file changes refresh overlay text first, then notify the manager about the current media type and file-switch reset.
5. Update `PlayerLifecycleManager.kt` so host `onPause()`, `onResume()`, and `onDestroy()` pause/resume/cancel overlay timers correctly.
6. Create a timestamped backup of `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` in `temp/` before editing it.
7. Modify `ImageLoadingManager.kt` to turn the existing `PhotoView` scale callback into a real zoom-interaction signal, while filtering out load-time false positives.
8. Add `FilenameOverlayAutoHideManagerTest.kt` and cover timeout mapping, extend behavior, hidden-to-visible re-show behavior, and lifecycle pause/resume.
9. Add or update `maestro/smoke/player-filename-autohide.yaml` for TXT timeout, file-switch reset, and image zoom re-show.
10. Update `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` with the user-visible behavior summary.
11. Run `./scripts/add_to_dev_log.ps1` for each modified file during implementation.

Mandatory step checklist at the end:
- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated (if user-facing)
- [ ] Room DB migration added + version incremented (if DB schema changes)
- [ ] `./scripts/add_to_dev_log.ps1` run for every modified file

---

## 14. Out of Scope (future items)

- User-configurable timeout values in Settings.
- Separate fullscreen overlay policy.
- StandalonePlayer parity for the same overlay logic.
- Additional re-show triggers such as seek, subtitle-track change, or OCR/translation overlay entry.
- Overlay style changes such as multiline filenames, marquee, or bottom-positioned badge.
