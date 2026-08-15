# Спецификация: S0796 - Действие жеста для запуска аудиозаписи

**Ticket:** S0796
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-29

**Текст:** /spec-draft Новое действие для жеста с левого края - "Начать аудиозапись" (диктофон).

---

## 1. Проблема

В приложении есть быстрый диктофон (виджет Quick Audio Recorder, S0349), но его нельзя запустить edge-жестом - только с домашнего экрана.

## 2. Цели

1. Новое действие жеста `START_AUDIO_RECORDING` запускает быстрый диктофон.
2. Повторный жест во время записи останавливает и сохраняет её (toggle).

**Non-goals:**

- Новый UI диктофона - переиспользуется существующий трамплин/сервис.
- Изменение самого overlay/движка жеста.

## 3. Ограничения

- **Flavor:** enum/dispatcher/picker + диктофон-трамплин в `src/main` (все флейворы); overlay ships standard (`fms.edgeGestureOverlay`) + noLegal.
- **Permissions:** RECORD_AUDIO запрашивается трамплином при первом запуске.
- **Локализация:** EN/RU/UK - добавлена `screenshot_gesture_action_start_audio_recording`.

## 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0349, S0522, S0793.

## 4. Критерии готовности

1. `START_AUDIO_RECORDING` в enum, диспетчере (pre-capture) и picker-е с меткой.
2. Жест запускает диктофон; повторный жест останавливает (toggle через существующий сервис).
3. Проект компилируется (standard + noLegal).

## Реализация (2026-07-01, Simple-путь)

- `ScreenshotGestureAction`: добавлено `START_AUDIO_RECORDING` (pre-capture).
- `ScreenshotGestureActionDispatcher.handlePreCaptureAction`: ветка `START_AUDIO_RECORDING -> launchAudioRecorder(context)` - запускает `QuickAudioRecorderActivity` (S0349 трамплин: гейтит RECORD_AUDIO, тогглит `QuickAudioRecorderService`) с `FLAG_ACTIVITY_NEW_TASK`; добавлено в pre-capture no-op группу `runPostSave`.
- `ScreenshotGestureActionPickerManager.labelResFor`: метка `screenshot_gesture_action_start_audio_recording` (EN "Start audio recording" / RU "Начать аудиозапись" / UK "Почати аудіозапис").
- Валидация: `a.ps1 fk` + `a.ps1 fkn` - BUILD SUCCESSFUL.

**Device-проверка (BlockNeedUserTest):** назначить «Начать аудиозапись»; жест стартует диктофон (при первом запуске - запрос RECORD_AUDIO), повторный жест останавливает и сохраняет. Проверить на standard (`fms.edgeGestureOverlay=on`) или noLegal.

## Last Audit

### Manual (device) - 2026-07-10

**Device:** emulator-5554, Android 13 (SDK 33), x86_64. Build `2.60.7092.225-DEBUG` (standard debug, `fms.edgeGestureOverlay=on` - `OverlayHostService` present and running). Probe `Timber.d("S0796: ..")` present in installed build.

**Verdict: PASS** (all three criteria).

1. **Configure LEFT-edge action -> "Start audio recording"** - PASS. Settings -> Management -> Left-edge screen gestures -> "Right gesture action" (the left-edge inward = RIGHT-direction swipe; `screenshotGestureActionRight`) picker -> "Start audio recording". DataStore confirmed: `screenshot_gesture_action_right = START_AUDIO_RECORDING` (was `OPEN_IN_DRAW`).

2. **Left-edge gesture STARTS recorder** - PASS. Inward swipe from x=0 captured by `screen_gesture_overlay_strip`. Log: `S0796: edge-gesture toggle audio recording` -> START `QuickAudioRecorderActivity` -> `requestAudioFocus() .. QuickAudioRecorderService` -> AAC encoder created + StagefrightRecorder recording -> `S0930: showing floating stop indicator`. RECORD_AUDIO pre-granted (no permission crash).

3. **Repeat gesture STOPS + SAVES (toggle)** - PASS. Second swipe: `S0796: edge-gesture toggle audio recording` -> `abandonAudioFocus()` -> MediaProvider `Moving .pending-..REC_20260710_010827.m4a -> /storage/emulated/0/Download/REC_20260710_010827.m4a` -> `MediaStoreSink.commit: published content://media/external/downloads/1000000272` -> Service `onDestroy`. Saved file verified on disk: `Download/REC_20260710_010827.m4a`, 30261 bytes.

**Note:** the left-edge inward swipe momentarily contends with the system back/edge gesture (`InputDispatcher: edge-swipe .. stealing touch from screen_gesture_overlay_strip`), but the overlay strip still captured the gesture and the dispatcher fired reliably on each attempt.

**Evidence:** `temp/S0796/evidence_logcat.txt` (filtered logcat), `temp/S0796/logcat.txt` (raw).
