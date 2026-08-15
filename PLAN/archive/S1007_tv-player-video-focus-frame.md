# Спецификация (compact bugfix): S1007 - TV-фокус-рамка на видео-поверхности плеера

**Ticket:** S1007
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-12
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-12

**Текст:**

android-tv в плеере само видео в проигрывателе обведено рамкой (даже в полном экране. Эта та рамка которая показывает текущий фокус. Она нужна на элементах, разумеется, но не в этом случае

---

## 1. Проблема / симптом

На Android TV видео-поверхность плеера (`androidx.media3.ui.PlayerView`) сама получает системную D-pad-фокус-рамку - декорацию S0943 (`FocusDecorationController` в `core/ui/focus/`), которая обводит accent-рамкой любой сфокусированный вне touch-режима View без собственного `foreground`. Рамка появляется даже в полноэкранном воспроизведении, где нет ни одного видимого интерактивного элемента - выглядит так, будто "сфокусировано" само видео, а не элемент управления. Затронуты все хосты плеера: `PlayerActivity` (`activity_player_unified.xml`), `PhotoVideoStandaloneActivity` (`activity_standalone_photo_video.xml`), `AudioStandaloneActivity` (`activity_standalone_audio.xml`) - и легаси-фолбэк `StandalonePlayerActivity`, переиспользующий тот же layout, что и `PlayerActivity`.

---

## 2. Корневая причина

Ни один `PlayerView` в проекте не задаёт `android:focusable` явно. Media3 держит `useController=true` по умолчанию, из-за чего конструктор `PlayerView` вызывает `setClickable(true)`; на платформенном `FOCUSABLE_AUTO` (Android O+, действует независимо от `minSdk` флейвора - фактическое поведение определяется `targetSdk` 35) любой кликабельный View автоматически становится фокусируемым для D-pad/клавиатуры. Поэтому `FocusDecorationController.shouldDecorate()` (`view.isFocusable == true` и `view.foreground == null`) декорирует именно `playerView`, когда D-pad-навигация переводит на него системный фокус. Проверено по `media3-ui-1.2.1-sources.jar`: `PlayerView.java` нигде программно не вызывает `setFocusable()`, так что явный XML-атрибут не будет перезаписан рантаймом.

D-pad play/pause на это не завязан: `PlayerActivity.dispatchKeyEvent` пересылает клавиши в `PlayerKeyboardHandler` независимо от текущего Android-фокуса, так что фокусируемость `playerView` не требуется для работы пульта - её можно безопасно снять.

---

## 3. Исправление

Добавить `android:focusable="false"` на элемент `<androidx.media3.ui.PlayerView android:id="@+id/playerView">` в каждом хосте плеера (плюс `-land`-пара, CLAUDE.md Rule 11). `clickable` и жест-обработчики (`performClick()` на `ACTION_UP`) не трогаются - тач и TalkBack-доступность (ориентируется на `clickable`, не на D-pad-`focusable`) без регрессии. Фикс останавливает `FocusDecorationController` на первой же проверке (`view.isFocusable`) точечно на видео-поверхности, не трогая сам S0943-контроллер и не заводя новый opt-out-механизм; реальные фокусируемые элементы (кнопки command-бара) не затронуты - у них уже есть собственный `foreground`.

Файлы (8, только `res/layout*`, Kotlin не тронут):

1. `layout/activity_player_unified.xml` (`playerView`) + `layout-land/activity_player_unified.xml` - покрывает и `PlayerActivity`, и легаси `StandalonePlayerActivity` (общий layout).
2. `layout/activity_standalone_photo_video.xml` (`playerView`) + `layout-land/activity_standalone_photo_video.xml`.
3. `layout/activity_standalone_audio.xml` (`playerView`) + `layout-land/activity_standalone_audio.xml`.
4. `layout/activity_standalone_document.xml` (`playerView`, `visibility="gone"`, id-паритет) + `layout-land/activity_standalone_document.xml` - для консистентности; элемент и так невидим и недостижим фокусом.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** видео-поверхность (`PlayerView`) перестаёт быть D-pad/клавиатурной точкой фокуса во всех хостах плеера; фокус-рамка (S0943) остаётся только на реальных интерактивных элементах (кнопки command-бара, встроенные controls - если видимы).
- **Accessibility:** `clickable` не меняется, жест-обработчики (`performClick()` в `PlayerGestureSetupManager` / `PhotoVideoStandaloneActivity`) не трогаются - touch и TalkBack-доступность без регрессии; меняется только D-pad/клавиатурная фокусируемость.
- **Validation level:** `standard debug` build gate (чисто ресурсный XML-атрибут) + ручная on-device TV/D-pad проверка (эмулятор Android TV либо устройство со стрелочной клавиатурой) - фокус-поведение не покрыто unit-тестами.
- **Owner sign-off:** auto-approved by /spec-all - 2026-07-12 (Tier 2 ad-hoc, механический однопричинный XML-фикс).
- **Related tickets:** none

---

## 4. Проверка

- XML well-formed check всех 8 изменённых файлов (PowerShell XML parser) - PASS.
- `.\a.ps1 dq` (assembleStandardDebug) - должен пройти без ошибок, изменение чисто ресурсное.
- Ручная on-device проверка (Android TV / D-pad, либо обычное устройство со стрелочной клавиатурой): открыть плеер (фото/видео/аудио), D-pad-навигацией дойти до видео-поверхности - фокус-рамка на самом видео не должна появляться; рамка на реальных кнопках command-бара продолжает работать штатно.

---

## Last Audit

### Manual (device) - 2026-07-20

- **Device:** emulator-5554, Android 15 (SDK 35), build `com.sza.fastmediasorter.debug` v2.60.7182.317-DEBUG.
- **Method:** D-pad keyevents (DPAD_DOWN/RIGHT 19-23) after opening each host; `uiautomator dump` to read the `playerView` focusable/focused state and the currently-focused node; screenshot to confirm the visible accent ring.
- **Verdict: PASS** (all three reachable surfaces).

Surface-by-surface:

- **In-app player (`PlayerActivity`, `activity_player_unified.xml`)** - PASS. Expected: video surface never focusable, no accent ring; controls keep their ring. Actual: `playerView` dump = `focusable="false" focused="false" clickable="true"`; D-pad focus traversed `exo_progress` (SeekBar) then `btnForward30`; accent ring visible on the fast-forward button, none around the video. Evidence: `temp/S1007/04_player_ui.xml`, `temp/S1007/05_player_btn_focus.png` + `05_player_btn_ui.xml`.
- **Standalone photo/video host (`PhotoVideoStandaloneActivity`, `activity_standalone_photo_video.xml`)** - PASS. Expected: same. Actual: `playerView` dump = `focusable="false" focused="false"` (bounds `[0,252][1080,2028]`); focused node = `btnBack`; accent ring on the top-left close/back control, none around the rendered video frame. Evidence: `temp/S1007/16_video_ui.xml`, `temp/S1007/16_standalone_video_focus.png`.
- **Standalone audio host (`AudioStandaloneActivity`, `activity_standalone_audio.xml`)** - PASS. Expected: artwork surface never focusable, no accent ring. Actual: `playerView` (artwork) dump = `focusable="false" focused="false"` (bounds `[0,252][1080,1965]`); focused node = `btnBack`; accent ring on the close/back control, none around the music-note artwork. Evidence: `temp/S1007/17_audio_ui.xml`, `temp/S1007/17_standalone_audio.png`.

Notes:

- Standalone hosts required the "System media handler" toggle (Settings -> Operating system interaction -> Set as default) to be ON so the exported ACTION_VIEW aliases (`.StandaloneVideoPlayer` / `.StandaloneAudioPlayer`) become launchable; confirmed via logcat `DefaultPlayerManager: primary player ENABLED`. Left ON afterwards - emulator swipe-injection wedged, could not scroll back to the toggle to revert (test scaffolding only, no effect on the focus behavior under test).
- No `S1007:` logcat markers exist - the fix is resource-only (`android:focusable="false"`, no Kotlin/Timber probe), so their absence is expected, not a gap.
- `DocumentStandaloneActivity` (`playerView` kept `visibility="gone"`, id-parity only) not exercised - the surface is never visible or focus-reachable by design.
