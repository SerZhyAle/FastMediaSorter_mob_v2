# Спецификация (compact bugfix): S1005 - Видеоплеер открывается без элементов управления воспроизведением

**Ticket:** S1005
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-12
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-12

**Текст:**

Symptom: opening a video file (video_sample.mp4, 44-file "Загрузки"/Downloads LOCAL resource) does not expose any play/pause control (`btnPlaybackControl`) - the video appears frozen on its first frame with no way to start/control playback via touch. Found during /spec-prerelease sweep 2026-07-12.

First surfaced via Maestro: maestro/features/player/player_video.yaml opens video_sample.mp4, asserts `playerView` visible (passes), taps `playerView` to reveal the Media3 controller overlay, then asserts `btnPlaybackControl` visible - this assertion FAILED (log: temp/player_video_maestro_20260712_010036.log; evidence dir C:\Users\serzh\.maestro\tests\2026-07-12_010038\, failure screenshot screenshot-❌-1783810923127-(player_video.yaml).png).

Manually reproduced live via mobile-mcp on the same emulator (emulator-5554, Pixel_4, API 37) immediately after: opened Загрузки → video_sample.mp4 (576x1024, 00:39, 2.60 MB). The screen that opens shows:
- `topCommandPanel` with btnBack/btnCastCmd/btnRenameCmd/btnEditCmd/btnSaveFrameCmd/btnOverflowMenu/btnDeleteCmd/btnFavorite/btnInfoCmd/btnFullscreenCmd/btnSlideshowCmd/btnPreviousCmd/btnNextCmd (navigation index "video_sample.mp4 (37/44)" shown via tvFileNameOverlay)
- `playerView`/`mediaContentArea`/`exo_controller`/`exo_content_frame` region (the actual video frame area is narrow, letterboxed for the 576x1024 portrait video) - `exo_controller` is present in the layout but its accessibility tree is EMPTY, no play/pause/seek children ever appear, before or after tapping directly on the video content area.
- `bottomPanelsContainer` permanently docked below the video area with the "Копировать в.." / "Переместить в.." quick-sort destination panels (buttons 0-9/letters for mark/media/test_media/down/_e/p26-2/SFTP/FTP/..) - this panel is NOT a toggle-revealed overlay, it is always present.

Tapping directly inside the video content area (multiple times, including the exact `playerView` center) never produced any play/pause/seek control. The video does not appear to auto-play either (frame stays static, no progress). No crash, no error toast, no log FATAL/ANR - the screen simply has no reachable playback control.

`btnPlaybackControl` as an id only exists in app_v2/src/main/res/layout/activity_standalone_photo_video.xml (and layout-land variant) and app_v2/src/main/res/layout/custom_player_controls*.xml - i.e. it belongs to the STANDALONE player activity, not the in-app/unified player screen that actually opened when tapping the file row in Downloads. The screen that DID open (topCommandPanel + quick-sort bottomPanelsContainer) does not match activity_standalone_photo_video.xml's structure, suggesting either (a) the file-open routing landed on the wrong/older "unified" player screen for this entry point, or (b) that unified player screen is correct-by-design but is missing its own play/pause control for video content specifically.

Evidence: temp/player_video_maestro_20260712_010036.log, C:\Users\serzh\.maestro\tests\2026-07-12_010038\ (screenshots + commands json), full sweep run log temp/S0484/run_20260712_003339.log, Maestro suite JSON temp/S0484/maestro_suite_20260712_003339.json.

---

## 1. Проблема / симптом

- Открытие видеофайла в in-app unified-плеере (`PlayerActivity`, layout `activity_player_unified.xml`) не даёт ни одного достижимого транспорт-контрола: `exo_play_pause`, seekbar (`exo_progress`), `btnPlaybackControl` не появляются ни на открытии, ни по тапу в область видео.
- Экран, который открывается, - именно unified `PlayerActivity` (виден `topCommandPanel` + закреплённый quick-sort `bottomPanelsContainer`), т.е. роутинг верный. Standalone-плеер (`activity_standalone_photo_video.xml`) здесь ни при чём.
- Найдено `/spec-prerelease` 2026-07-12: Maestro `maestro/features/player/player_video.yaml` открывает `video_sample.mp4`, тапает `playerView`, ассертит `btnPlaybackControl` visible → FAIL. Эвиденс в §0.
- Симптом «видео не автостартует» вторичен и не является предметом фикса (кадр 0 рендерится - плеер прикреплён и подготовлен; `playWhenReady` следует состоянию паузы).

---

## 2. Корневая причина

- В unified-плеере ни один видео-тап не вызывает `PlayerView.showController()`, поэтому кастомный контроллер (`custom_player_controls.xml`, где и живут `exo_play_pause` / `btnPlaybackControl`) никогда не раскрывается.
- `VideoTouchDelegate` (его `onSingleTapConfirmed` по центру звал `togglePlayerController()` → `showController()`) жёстко отключён: `if (false)` в `PlayerGestureSetupManager.setupPlayerViewTouchListener` - мёртвая ветка, оставленная при переходе на 9-зонную модель.
- Видео-тапы идут в 9-зонный / command-panel обработчик (`TouchZoneGestureManager.handleTouchZone` / `handleCommandPanelTouchZones`), где центр видео → `TouchZoneAction.PAUSE_RESUME` → `PlayerTouchZoneCallbackImpl.onPauseResume()`. Этот колбэк только тоглит play/pause и `showController()` не зовёт.
- Видео-ветка `PlayerMediaLoaderManager.configurePlayerViewForMediaType` не форсит показ контролов на открытии (в отличие от аудио-ветки, которая зовёт `showController()`), а единственный видео-`showController()` при входе в command-panel (`PlayerDialogAndUiStateManager`) авто-скрывается через `controllerShowTimeoutMs = 15s` и больше не переоткрывается.
- Standalone-плеер работает именно потому, что его `StandaloneVideoTouchDelegate.toggleController()` НЕ отключён - это эталон намеренного поведения «тап по видео → контролы».

---

## 3. Исправление

Восстановить достижимость контроллера для видео в unified-плеере, не ломая 9-зонную модель файловых операций. Точечно, две правки:

- `PlayerTouchZoneCallbackImpl.onPauseResume()`: после тогла play/pause вызвать `activity.activityBinding.playerView.showController()` - центр-тап по видео/аудио снова раскрывает транспорт-контролы (переоткрытие после авто-скрытия). Колбэк вызывается только из центр-зоны видео/аудио, поэтому побочных эффектов на изображения нет; для аудио контролы и так показаны - повторный вызов безвреден.
- `PlayerMediaLoaderManager.configurePlayerViewForMediaType` (видео-ветка): вызвать `binding.playerView.showController()` при загрузке видео - контролы видны сразу на открытии (обнаруживаемость), как у аудио-ветки. Далее авто-скрытие через 15s, повторный показ - центр-тапом (правка выше).

Оба вызова идемпотентны и совпадают по контракту с аудио-веткой и standalone-эталоном. Кнопки контроллера - дочерние вью `PlayerView`, поэтому по достижении видимости они тапаются штатно (touch-listener на `playerView` не перехватывает тапы по clickable-детям).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Проверка

- `.\a.ps1 fk` (standard) - компиляция символов.
- `.\a.ps1 db` - сборка debug APK.
- On-device (BlockNeedUserTest / Maestro `player_video.yaml`): открыть `video_sample.mp4` в Downloads → на открытии видны транспорт-контролы; тап по центру видео раскрывает контроллер (`exo_play_pause` + `btnPlaybackControl` + seekbar); контролы тапаются.

---

## Last Audit

**Date:** 2026-07-15
**Mode:** strategic (compact bugfix)
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0 · MANUAL 0

Device-verified on emulator-5554 (Android 17 / API 37, standard-debug v2.60.7151.516). Both fixes confirmed at runtime; `S1005:` probe removed on Verified flip.

### Manual device test - 2026-07-15 (emulator-5554, Android 17 / API 37, standard-debug v2.60.7151.516-DEBUG)

**Verdict: PASS**

Fixture: `test_video.mp4` (c:\Common\test_media) pushed to `/sdcard/Download/video_sample.mp4` (576x1024, 00:39, 2.60 MB - matches §0 fixture); opened via the "All Videos" virtual resource (`virtual://all_video`), which is the same unified `PlayerActivity` entry point as the §0 repro.

- Controls visible on open:
  - expected: `exo_play_pause` + `btnPlaybackControl` + seekbar (`exo_progress`) visible immediately on open.
  - actual: PASS - all three present and visible in the a11y tree under a populated `exo_controller` (was EMPTY in §0). `exo_play_pause` @ (370,2076), `btnPlaybackControl` (label "Control") @ (909,2093), `exo_progress` SeekBar @ (146,1939). Evidence: `01_on_open.png` (one-time "Media Playback Mode" info overlay + bottom transport bar), `02_after_dismiss_overlay.png`.
  - probe: `S1005: video controls shown on open` (PlayerMediaLoaderManager) fired on open - see `logcat_S1005.txt`.
- Auto-hide + center-tap re-reveal:
  - expected: controls auto-hide after ~15s of playback, and a center-tap re-reveals them.
  - actual: PASS - after ~16s of playback the controller auto-hid (clean fullscreen, `03_after_autohide.png`); a single center-tap (540,1100) re-revealed the full transport bar (`04_after_center_tap.png`, confirmed via a11y tree: `exo_play_pause` / `btnPlaybackControl` / `exo_progress` all back).

Notes:
- Only one `Timber.d("S1005: ..")` probe exists (PlayerMediaLoaderManager:1035); the `PlayerTouchZoneCallbackImpl` S1005 reference is a comment, not a probe. Invariant intact.
- Evidence dir: `temp/S1005/` (4 screenshots + `logcat_S1005.txt`).
