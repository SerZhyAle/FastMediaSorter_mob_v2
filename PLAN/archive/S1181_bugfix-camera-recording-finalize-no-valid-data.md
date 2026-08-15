# Спецификация (compact bugfix): S1181 - Запись видео завершается ошибкой при выходе из экрана камеры

**Ticket:** S1181
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-24
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-24

**Текст:**

Найдено при анализе лога `logs/fastmediasorter_20260724_033924.log` (сборка 2.60.7240.310-NoLegal-DEBUG, SM-S731B, Android 16/API 36).

При уничтожении `CameraCaptureActivity` во время активной видеозаписи финализация записи завершается кодом ошибки 8 (CameraX `ERROR_NO_VALID_DATA`) и пишется в лог на уровне E. Файл записи, судя по всему, теряется; пользователю ничего не сообщается.

Лог-эвиденс (verbatim):

```
2026-07-24 12:15:09.221 D/App: onDestroy: CameraCaptureActivity
2026-07-24 12:15:09.254 E/App: CameraCaptureSessionManager: recording finalize error 8
```

Вопросы к расследованию: корректно ли останавливается запись в `onPause`/`onStop` до уничтожения; нужно ли трактовать `NO_VALID_DATA` (запись короче одного валидного кадра) как ошибку уровня E или как ожидаемую отмену на уровне W/I; удаляется ли пустой выходной файл.

---

## 1. Проблема / симптом

Выход с экрана камеры во время видеозаписи приводит к финализации записи с ошибкой `NO_VALID_DATA`: результат записи теряется, ошибка логируется на уровне E без пользовательской обратной связи и без явного пути отмены.

---

## 2. Корневая причина

Запись нигде не останавливается до того, как CameraX отбирает у неё источник кадров.

Камера привязывается через `provider.bindToLifecycle(lifecycleOwner, selector, useCases.group)`, где `lifecycleOwner` - сама `CameraCaptureActivity`. Значит use case'ы отвязывает сам CameraX по событию `ON_STOP`, и делает это, ничего не зная про активную запись.

Цепочка при уходе с экрана во время записи:

1. `onPause()` останавливает только `orientationManager` и обратный отсчёт - про `activeRecording` там нет ни слова.
2. `onStop` наступает, CameraX отвязывает `VideoCapture`, видео-источник становится неактивным.
3. `VideoRecordEvent.Finalize` приходит с ошибкой - в наблюдаемом логе это код 8, `ERROR_NO_VALID_DATA`.
4. `CameraCaptureSessionManager` пишет это через `Timber.e`, хотя произошло ровно то, что пользователь и попросил - уход с экрана.
5. Колбэк в `CameraCaptureActivity.startRecording` отсеян охраной `if (isFinishing || isDestroyed) return@startRecording`, поэтому обратной связи нет и уборка не выполняется.

`onDestroy()` действительно зовёт `sessionManager.unbind()`, а тот - `activeRecording?.stop()`, но это уже после `onStop`: источник к этому моменту мёртв, останавливать нечего. Порядок в самом `unbind()` тоже неверен - `stop()` стоит вплотную перед `cameraProvider?.unbindAll()`, а `stop()` асинхронный, так что даже при вызове вовремя финализация не успела бы записать хвост.

Итого три отдельных дефекта в одной цепочке: потеря данных, ложный уровень лога, осиротевший файл.

---

## 3. Исправление

**3.1 Останавливать запись в `onPause()`.** Если `sessionManager.isRecording()`, звать `stopRecording()` до вызова `super.onPause()`. Тогда CameraX финализирует запись, пока камера ещё привязана, и на диск ложится валидный файл с тем, что успели снять, - ровно как при обычном нажатии на кнопку остановки.

Выбран `onPause`, а не `onStop`: порядок между собственным `onStop` активности и наблюдателем `LifecycleCameraRepository` не гарантирован, а `onPause` заведомо раньше любой рассылки `ON_STOP`. Диалог настроек камеры - `DialogFragment`, активность он не паузит, так что ложных остановок не будет.

**3.2 Разделить уровни лога по классу ошибки.** `ERROR_NO_VALID_DATA` и `ERROR_SOURCE_INACTIVE` - это отмена, а не сбой: логировать на `Timber.i`. Остальные коды (нехватка места, ошибка кодирования, ошибка рекордера, неверные параметры вывода) остаются на `Timber.e`. Контракт колбэка `onFinalized(hasError = true)` не меняется - меняется только уровень записи в лог.

**3.3 Убирать непригодный файл.** При финализации с ошибкой удалять `outputFile`, если он не содержит пригодных данных: код `ERROR_NO_VALID_DATA` (CameraX документирует, что валидных данных в файле нет) либо нулевая длина при любом коде ошибки. Коды, при которых CameraX отдаёт валидный усечённый файл (лимит размера, лимит длительности, нехватка места), файл не трогают.

### 3.1 Вне области

Не меняем поведение результата: что делать с частичной записью, если пользователь ушёл с экрана, - отдельный вопрос UX, и тикет его не ставит. После правки файл просто остаётся валидным вместо битого.

Не трогаем порядок внутри `unbind()`: после 3.1 активной записи там уже не будет, а перестановка `stop()` относительно `unbindAll()` без ожидания финализации всё равно ничего не гарантирует.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0545, S0566, S1066

---

## 4. Проверка

- `a.ps1 fk` - BUILD SUCCESSFUL.
- `CameraCaptureActivity.onPause()` содержит остановку записи под проверкой `sessionManager.isRecording()`.
- В `CameraCaptureSessionManager` ветка `event.hasError()` разводит уровни: `Timber.i` для отмены, `Timber.e` для настоящих сбоев.
- На устройстве: начать видеозапись, выйти с экрана камеры кнопкой «назад» - в логе нет строки уровня E про finalize, файл записи валиден и воспроизводится.
- На устройстве: начать и мгновенно выйти (запись короче кадра) - finalize приходит с кодом 8 на уровне I, непригодный файл на диске не остаётся.

---

## Last Audit

**Date:** 2026-07-24
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 4 · EXEMPT 2

### Static checks

- Build: `a.ps1 dav` - `BUILD SUCCESSFUL`, APK `2.60.7241.829-DEBUG`.
- `CameraCaptureActivity.onPause()` stops an active recording under an `isRecording()` guard, before `super.onPause()`.
- `handleFinalizeError` splits levels: `Timber.i` for `ERROR_NO_VALID_DATA` / `ERROR_SOURCE_INACTIVE`, `Timber.e` for every other code; and deletes the output only when it holds nothing playable.
- Fast static gates (`post-change`): ticket-log, neuroslop, public-mutable-flow, FGS-notification, deprecated-pm, listener-symmetry, flavor-flag - all PASS.
- Debug-tag invariant: 2 `Timber.d("S1181:` probes present while status was `BlockNeedUserTest`; removed on this flip.
- Dev log and catalog sync: present.
- FEATURES trilingual: EXEMPT - no §8 in this compact bugfix spec; `quick-capture.in-app-camera-controls-and-video` already covers the capability in `ALL_FEATURES`, and this is a fix to it, not a new one.
- detekt (diff-scoped): **EXEMPT** - `CameraCaptureSessionManager.kt` carries 5 unbaselined findings that this change does not touch, tracked as `S1185`. Proof the change is detekt-neutral: with the new helper as a class method the report read `TooManyFunctions 41/40`; with it moved to file level it reads `40/40`, i.e. exactly the pre-change count. The other four (`MultiLineIfElse`, 3x `ReturnCount`) sit in `applyExposureCompensationForNight`, `applyHdr`, `setManualSensor`, `setAspectRatioAndResolution` - none edited here.

### Parked out-of-scope findings

- `S1184` - `assert-detekt.ps1` reports a false PASS when `-ChangedFiles` holds several paths: adding a second file made the first file's findings disappear from the verdict.
- `S1185` - the detekt baseline for `CameraCaptureSessionManager.kt` lists 4 signatures that no longer exist while 5 current findings are unbaselined, so the scoped gate fails for anyone touching the file.

### Manual device test - 2026-07-24 (emulator-5554, standard debug)

Device: `emulator-5554`, sdk_gphone64_x86_64, Android 15 (SDK 35). Package `com.sza.fastmediasorter.debug`, installed `versionName=2.60.7241.829-DEBUG` (`versionCode=260724182`) - verified via `dumpsys package` before testing, matches the APK under test. Camera reached through Quick launch -> «Start video recording» (opens `CameraCaptureActivity` in video mode and auto-starts the recording). Scratch dir `/sdcard/Android/data/com.sza.fastmediasorter.debug/files/Capture/`, confirmed destination `/sdcard/Movies/`. Evidence: `temp/S1181/logcat_run.txt`, `temp/S1181/check1.mp4`, `temp/S1181/check3.mp4`, screenshots `temp/S1181/shot_0*.png`.

**Check 1 - leave mid-recording, footage survives: PASS.**
Recording ran 18:39:04.270 (`Recorder RECORDING`) .. 18:39:32.086, left with the Back key. Expected: onPause probe fires, finalize without error, non-trivial valid file on disk. Actual: `S1181: onPause stops an active recording before CameraX unbinds` at 18:39:32.086; `RECORDING --> STOPPING` at 18:39:32.087, `Sending VideoRecordEvent Finalize` at 18:39:34.095 with **no** error - no `recording finalize error` line and no `S1181: finalize error=` probe. File `/sdcard/Android/data/com.sza.fastmediasorter.debug/files/Capture/CAP_20260724_183903_1.mp4`, 1 049 588 bytes; box scan of the pulled copy: `ftyp` (mp42/isom) + `free` + `mdat` + `moov` (7 619 B @ 1 041 969) - a complete, playable MP4, not a stub. Note (out of scope per §3.1): the surviving footage stays in the scratch dir and is not promoted to `Movies/`, since leaving the screen never confirms the result.

**Check 2 - start then leave instantly, no stub left behind: PASS.**
Reproduced on the second attempt by leaving inside the window between `Recorder RECORDING` and the first `Received video keyframe` (a first attempt left 200 ms after the keyframe and correctly produced a clean 40 242 B file - no error, no deletion). Expected: `S1181: finalize error=8 unusable=true`, level **I** `recording cancelled before any valid data (8)`, no level-E line, output file gone. Actual: `S1181: onPause stops an active recording before CameraX unbinds` 18:42:08.857; `I/CameraCaptureSessionManagerKt: CameraCaptureSessionManager: recording cancelled before any valid data (8)` 18:42:10.744; `S1181: finalize error=8 unusable=true size=0` 18:42:10.744; no `CAP_20260724_1842*.mp4` in the scratch dir afterwards and no `could not delete unusable recording` warning - the zero-length file was created and removed.

**Check 3 - normal recording still works (regression): PASS.**
Recording 18:42:36.261 .. shutter stop 18:42:43.271, staying on the screen. Expected: clean finalize, file saved as before, onPause probe silent. Actual: `Sending VideoRecordEvent Finalize` 18:42:44.891 with no error; `/sdcard/Movies/CAP_20260724_184235_1.mp4`, 274 248 bytes, box scan `ftyp` + `moov` + `free` + `mdat` - valid. Activity stayed resumed (result thumbnail shown, shutter back to idle) and **no** new `S1181: onPause` probe fired after 18:42:08.857, so the onPause hook does not hijack the normal stop path.

**Check 4 - log-level discipline: PASS.**
`recording finalize error` occurrences across the whole 25 244-line run: **0** at any level. The only finalize line from the app is the level-I cancellation above. Zero Timber `E/App` / `E/CameraCapture*` lines; the remaining E-level entries under the app pid are platform/emulator noise (`EGL_emulation eglQueryContext`, `BufferQueueProducer .. BufferQueue has been abandoned`, `MPEG4Writer Stop() called but track is not started`), unrelated to this ticket.

**Crashes:** 0 `FATAL EXCEPTION`, 0 ANR for the package across the run.

Overall: 4 of 4 checks PASS on emulator-5554.
