# Спецификация (compact bugfix): S1174 - быстрый снимок через trampoline теряет результат

**Ticket:** S1174
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-24
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (verbatim)

Обнаружено при device-тесте S1163 на emulator-5554 (sdk_gphone64_x86_64, Android 15 / SDK 35), 2026-07-24.

Сообщение исполнителя:

> the quick-capture **widget** half UNVERIFIED (no widget placeable on the AVD; the equivalent
> Quick-launch route to the same `noHistory` `CameraQuickCaptureActivity` trampoline lost the result
> entirely - pre-existing, untouched by S1163)

Артефакты прогона: `temp/S1163/`.

---

## 1. Проблема

Снимок, сделанный через прозрачный trampoline `CameraQuickCaptureActivity`, не доходит до сохранения: кадр снят, файл лежит во временной папке, но подтверждения нет, диалог имени не появляется, и в целевую папку виджета ничего не попадает.

Затронуты обе точки входа, ведущие в этот trampoline:

- виджет «Быстрый снимок» на домашнем экране (`CameraQuickCaptureWidgetProvider`);
- плитка быстрого снимка в панели запуска (`AppLaunchPanelRouteIntents.quickCamera`, `PANEL_APP_WIDGET_ID`).

---

## 2. Корневая причина

`CameraQuickCaptureActivity` объявлена с `android:noHistory="true"`. Активность с этим флагом система завершает, как только она перестаёт быть видимой. Trampoline прозрачный, а `CameraCaptureActivity` - непрозрачный полноэкранный хост, поэтому при переходе в камеру trampoline уходит в `onStop` и уничтожается. К моменту возврата `RESULT_OK` получателя уже нет: колбэк `captureLauncher` не вызывается, и вся ветка `onCaptureResult` -> `save()` не выполняется никогда.

Этот же дефект уже был диагностирован и починен на соседнем trampoline: `PhotoCaptureLaunchActivity` (S0790-S0794) несёт в манифесте явный комментарий, что `noHistory` уничтожал её на середине съёмки и снимок терялся, и потому флаг там снят. Лечение известно и проверено в этом же проекте.

`CameraLaunchActivity` (S0568, виджет «Камера» и жест «начать видеозапись») построена по той же схеме - `noHistory` плюс `registerForActivityResult` на тот же непрозрачный `CameraCaptureActivity` - и потому несёт ровно тот же дефект.

---

## 3. Исправление

Снять `android:noHistory="true"` с обоих trampoline'ов, которые ждут результат от непрозрачной активности:

- `.widget.CameraQuickCaptureActivity`;
- `.widget.CameraLaunchActivity`.

Оба уже несут `excludeFromRecents="true"` и `taskAffinity=""`, поэтому в списке недавних задач не появятся. Оба менеджера (`CameraQuickCaptureLaunchManager`, `CameraLaunchWidgetManager`) вызывают `finish()` на каждой терминальной ветке - отмена, отказ в разрешении, отсутствие камеры, ошибка временного файла, ошибка сохранения, успех, отмена диалога имени, - поэтому без `noHistory` пустая прозрачная активность на экране не задержится.

Комментарии в манифесте и KDoc обоих классов сейчас утверждают, что `noHistory` - часть рецепта; их надо привести в соответствие, иначе следующий автор вернёт флаг обратно.

### 3.1 Вне области

Не трогаем trampoline'ы, которые ждут только системный диалог разрешения и не запускают непрозрачную активность за результатом: `QuickAudioRecorderActivity`, `ScreenRecordingLaunchActivity`, `LinkDownloadLaunchActivity`, а также активности завершения авторизации Google Drive.

Комментарий S0790 утверждает, что там trampoline убивал именно диалог разрешения. **Проверено на устройстве (check 3, API 35) - не воспроизводится:** `GrantPermissionsActivity` запускается в задачу самого trampoline'а как translucent, и `dumpsys` показывает trampoline в состоянии `PAUSED mVisible=true nowVisible=true` - до `onStop` дело не доходит, поэтому `noHistory` не срабатывает. Перечисленным выше активностям это лечение не нужно; отдельный тикет заводить не на чем.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1163, S0369, S0568, S0790

---

## 4. Проверка

- `check-standard-fast.ps1 -Mode CodeAndResources` - BUILD SUCCESSFUL.
- В `app_v2/src/main/AndroidManifest.xml` у `.widget.CameraQuickCaptureActivity` и `.widget.CameraLaunchActivity` нет `android:noHistory`.
- На устройстве: плитка быстрого снимка в панели запуска - снять кадр, дождаться диалога имени (или тоста о сохранении при включённом «пропускать имя файла») и убедиться, что файл лёг в привязанную папку.
- На устройстве: виджет «Камера» - снять кадр и убедиться, что появился тост о сохранении и файл лёг в публичную папку.
- После возврата из камеры прозрачная активность не остаётся на экране и не появляется в списке недавних.

---

## Last Audit

**Date:** 2026-07-24
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 5 · EXEMPT 1

### Static checks

- Build: `a.ps1 dav` - `BUILD SUCCESSFUL in 54s`, APK `2.60.7241.749-DEBUG`.
- `AndroidManifest.xml` L507 / L522: `android:noHistory` absent on `.widget.CameraQuickCaptureActivity` and `.widget.CameraLaunchActivity`.
- Fast static gates: `a.ps1 fg` all green, including `assert-orientation-implied-feature` (no device-reach regression from the manifest edit).
- detekt, diff-scoped: PASS for each touched `.kt` run **separately**. The combined two-file run also reported PASS, but that form of the gate is unreliable (`S1184` - a multi-file `-ChangedFiles` list silently matches nothing and reports a false PASS), so the per-file runs are the evidence here.
- Debug-tag invariant: 2 `Timber.d("S1174:` probes present while status was `BlockNeedUserTest`; removed on this flip.
- Dev log and catalog sync: present.
- FEATURES trilingual: EXEMPT - no §8 in this compact bugfix spec, and `widgets.quick-capture-widget` already exists in `ALL_FEATURES` (this is a fix of a recorded capability, not a new one).

### Parked out-of-scope findings

- `S1182` - `CameraLaunchWidgetManager`'s `RESULT_OK` save branch is unreachable: the host is opened with `multiCapture = true` and self-saves, returning `RESULT_CANCELED`.
- `S1183` - `adb.ps1` writes verb output via `Write-Host`, so piping it to a file silently yields an empty file.

### Manual device test - 2026-07-24 (emulator-5554, standard debug)

Device: `sdk_gphone64_x86_64`, Android 15 (SDK 35), 1080x2424, virtual cameras. Build under test `2.60.7241.749-DEBUG` (versionCode `260724174`, package `com.sza.fastmediasorter.debug`), installed from `app_v2/build/outputs/apk/standard/debug/FastMediaSorter_standard_debug_v2.60.7241.749-DEBUG.apk` and confirmed on device via `dumpsys package` before testing - the emulator carried `2.60.7220.314-DEBUG` from another session and was reinstalled. No rebuild. `skipCameraFilenameDialog` is off, so the "Save as" dialog is the expected confirmation on the quick-capture route. Both routes were driven from the app-launch panel (Programs -> Quick launch), with the "Camera" and "Start video recording" tiles added through the panel editor. Evidence: `temp/S1174/`.

- **Check 1 - quick-capture via the panel tile: PASS.** Expected: result delivered, save confirmation, file on disk | Actual: probe `S1174: quick-capture result delivered code=-1` at 18:01:47, "Save as CAP_20260724_180114.jpg" dialog shown, and `/sdcard/DCIM/Camera/CAP_20260724_180114.jpg` (117400 bytes) present after OK. While the opaque host was in front, `dumpsys activity activities` showed task #24 with `sz=2` and both `CameraCaptureActivity` and `CameraQuickCaptureActivity` records alive - the trampoline is no longer destroyed mid-capture. Evidence: `probes.txt`, `check1_quickcapture.log`, `check1_filename_dialog.png`.
- **Check 2 - camera-launch route: PASS.** Reached through the panel's "Start video recording" tile (`AppLaunchPanelRouteIntents.startVideoRecording` -> `CameraLaunchActivity.videoIntent`); no widget placement was needed. Expected: result delivered, video in the public folder | Actual: probe `S1174: camera-launch result delivered code=0` at 18:08:02, and `/sdcard/Movies/CAP_20260724_180443_1.mp4` (1367825 bytes) present. `CameraLaunchActivity` stayed alive under the host (task #25, `sz=2`) for the whole recording. The code is `0` (`RESULT_CANCELED`) rather than `-1` because `CameraLaunchWidgetManager` opens the host through `CameraCaptureContract.createSwitchableIntent`, whose `multiCapture` default is `true`: the host persists each capture itself and returns `RESULT_CANCELED` on close, so the trampoline's own save branch never runs on this route. Delivery - the thing S1174 fixes - is proven; the save toast comes from the host. Evidence: `probes.txt`, `check2_cameralaunch.log`.
- **Check 3 - CAMERA permission first-use: PASS, and the S0790 claim does not reproduce on API 35.** CAMERA revoked, app force-stopped, capture started cold from the panel tile. Expected: dialog appears and the trampoline still completes the capture | Actual: `GrantPermissionsActivity` came up in the trampoline's own task (#28) marked `translucent=true`, and `dumpsys activity a .widget.CameraQuickCaptureActivity` reported the trampoline as `state=PAUSED mVisible=true nowVisible=true` - it never reached `onStop`. After "While using the app" the camera opened, the shot produced probe `code=-1` at 18:11:13, the "Save as" dialog appeared and `/sdcard/DCIM/Camera/CAP_20260724_181057.jpg` (117468 bytes) landed. Consequence for §3.1: a permission dialog alone does not stop a transparent trampoline on this API level, so `noHistory` is not triggered by it - `QuickAudioRecorderActivity`, `ScreenRecordingLaunchActivity` and the Drive auth activities need no equivalent change on the strength of this evidence. Evidence: `check3_trampoline_state_during_dialog.txt`, `check3_permission_dialog.png`, `check3_permission_firstuse.log`.
- **Check 4 - no regression from dropping `noHistory`: PASS.** Expected: no visible leftover, nothing in recents, no lingering instance | Actual: after every completed flow `dumpsys activity activities | grep -E "QuickCapture|CameraLaunch"` returned no match (exit 1), and the recents screen showed only the main FastMediaSorter card. The trampoline's own task survives in `dumpsys activity recents` with `Activities=[]` and the intent flagged `0x10800000` (`FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS`), so it is an empty excluded task, not a user-visible card. Evidence: `check4_recents.png`.
- **Check 5 - cancel path: PASS.** Expected: non-OK result, trampoline finishes, nothing saved | Actual: backing out of the camera without shooting produced probe `S1174: quick-capture result delivered code=0` at 18:09:05, the trampoline disappeared from the stack, `/sdcard/DCIM/Camera` stayed at 14 entries, and no new scratch file was left in `files/Pictures` (the only file there predates this build). Evidence: `check5_cancel.log`.
- **Crashes/ANRs: none.** `logcat -b crash -b main -b events` over the run contains zero `FATAL EXCEPTION` / `ANR in` / `am_crash` / `am_anr` lines. The single dropbox ANR on the device is `com.google.android.gms.persistent` at 14:30, hours before this run. Evidence: `full_run_all_buffers.log`.
