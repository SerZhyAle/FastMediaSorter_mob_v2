# Спецификация (compact bugfix): S1457 - Снятое фото не совпадает с тем, что показывал видоискатель

**Ticket:** S1457
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1447

**Текст:**

баг! результат фото не совпадает с тем. что пользователь выбрал в видеоискателе и настроил перед кадром. и камера не та и зум и пропорции и ориентирование кадра

---

## 1. Проблема / симптом

Со слов владельца расходятся сразу четыре свойства кадра, то есть речь не об одной настройке, а о том, что снимок берётся не из той конфигурации, которую показывает превью:

- выбранная камера (объектив) - снимает не тот, что в видоискателе;
- зум - в снимке не тот, что был выставлен перед кадром;
- пропорции кадра (aspect ratio) - результат не совпадает с превью;
- ориентация кадра.

---

## 2. Корневая причина

Единой причины нет: геометрия кадра решается в трёх местах, и ни одно из них не сверяется с остальными. `CameraUseCaseFactory` задаёт объектив, пропорции и поворот на момент привязки; `CameraCaptureSessionManager` отдельно решает, как обрезать уже снятый файл; `CameraCaptureFlowManager` отдельно зеркалит «живые» значения в UI. Расследование нашло четыре независимых дефекта, каждый из которых объясняет один из названных симптомов.

### 2.1 Зум теряется на каждой перепривязке (симптом «зум»)

`CameraCaptureSessionManager.bindToLifecycle()` после успешного `bindToLifecycle` выполняет `camera = boundCamera` и `resetDigitalZoom()`, но не восстанавливает оптический зум. Новый объект `Camera` всегда стартует на зуме объектива по умолчанию. В той же функции соседний блок явно возвращает экспокоррекцию с комментарием «A rebind resets exposure compensation» - для зума такой строки нет.

Перепривязка происходит на любое применение настроек: смена пропорций и разрешения, переключение режима фото/видео, включение HDR, ночного режима, боке, макро и профилей съёмки. Пользователь выставляет зум, что-то применяет, снимает - и получает кадр на 1x.

### 2.2 Геометрия снимка читается в момент сохранения, а не в момент спуска (симптомы «зум», «пропорции»)

В `CameraCaptureSessionManager.capture()` значения `digitalZoomFactor`, `selectedAspectRatio` и `videoMode` читаются внутри колбэка `onImageSaved`, то есть уже после того, как JPEG записан на диск. Между спуском затвора и этим колбэком проходят сотни миллисекунд, в течение которых зум и пропорции остаются полностью управляемыми: активна кнопка спуска, но не слайдер зума и не пинч. Щипок в этом окне обрезает уже снятый кадр по новому коэффициенту, а не по тому, что был в видоискателе на спуске.

### 2.3 Диалог предлагает разрешения, которые конвейер молча выбрасывает (симптом «пропорции»)

`CameraSettingsDialogFragment` показывает `capabilities.photoResolutions` целиком, без фильтрации. `CameraUseCaseFactory.buildResolutionSelector()` применяет выбранное разрешение только через `selectedResolution?.takeIf { resolutionMatchesAspect(it, aspect) }`, а `effectiveAspectRatioInt()` в фоторежиме всегда возвращает `RATIO_4_3`. Любое выбранное 16:9-разрешение отбрасывается без единого следа в UI: пользователь видит в списке «3840x2160», а получает кадр совсем другого размера и пропорций.

Тот же список даёт вторую рассинхронизацию: выпадающий список выбирает позицию через `indexOf(draft.resolution).coerceAtLeast(0)`, поэтому при `resolution == null` (разрешение нигде не сохраняется, у свежей сессии оно всегда null) подсвечивается первый пункт, хотя в сессию не применено ничего.

### 2.4 На устройстве без датчика ориентации поворот кадра залипает на нуле (симптом «ориентация»)

`CameraOrientationManager.enable()` включает `OrientationEventListener` только при `canDetectOrientation()`, но `dispatch()` вызывает в любом случае. Если датчика нет или он недоступен - автомобильная магнитола, часть эмуляторов и ТВ-устройств - слушатель не включается никогда, `currentRotation` навсегда остаётся `Surface.ROTATION_0`, и в `ImageCapture.targetRotation` уходит нуль независимо от того, как реально повёрнут экран. Экранного запасного источника поворота нет, хотя `Display.getRotation()` доступен всегда.

### 2.5 Что проверено и дефектом не является

- Превью не обрезано: `previewViewCamera` использует `scaleType="fitCenter"`, то есть кадр показывается целиком с полями, а не заполняет экран кропом.
- Пропорции 16:9 в фоторежиме реализованы намеренно (S1066): сенсор всегда отдаёт 4:3, поверх превью рисуется рамка результата, а файл обрезается после съёмки. Схема внутренне согласована.
- Поворот на основном пути обновляется на уже привязанных use case, а не только при сборке, и `capture()` дополнительно переприсваивает `targetRotation` перед спуском.
- `physicalCameraId` применяется к превью и к съёмке симметрично из одного экземпляра фабрики.

---

## 3. Исправление

Четыре независимые правки, по одной на дефект. Каждая ограничена одним файлом плюс, где нужно, его тестом.

### Шаг 1 - Восстанавливать зум после перепривязки

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** - начало

**Prompt for developer:**

> Запомнить оптический зум, действовавший до перепривязки, и вернуть его на новый `Camera` сразу после `bindToLifecycle`, рядом с восстановлением экспокоррекции. Зум объектива, которого больше нет в диапазоне нового объектива, зажать в его границы, а не отбрасывать. Смена объектива через `switchCamera()` обязана по-прежнему стартовать с зума нового объектива - восстановление относится к перепривязке того же объектива.

**Why:**

Без этого любое применение настроек молча возвращает кадр на 1x, из-за чего снимок не совпадает с зумом, выставленным в видоискателе перед кадром (§2.1).

**Verification:**

- `Grep` - в `bindToLifecycle` есть восстановление зума после присваивания `camera = boundCamera`.
- `Grep` - `resetDigitalZoom()` в этой же ветке сохранён (цифровой зум обязан сбрасываться, оптический - восстанавливаться).
- `.\a.ps1 fk` - exit 0.

**Status:** `[x] done`

---

### Шаг 2 - Снимать геометрию на спуске затвора

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Шаг 1

**Prompt for developer:**

> В `capture()` считать `digitalZoomFactor`, `selectedAspectRatio` и `videoMode` в локальные переменные до вызова `takePicture`, а в `onImageSaved` использовать только их. Колбэк не должен читать ни одно изменяемое поле сессии.

**Why:**

Пока значения читаются в `onImageSaved`, любое движение зума в окне между спуском и записью файла обрезает готовый кадр по коэффициенту, которого в видоискателе на спуске не было (§2.2).

**Verification:**

- `Grep` - внутри `onImageSaved` нет обращений к `digitalZoomFactor`, `selectedAspectRatio`, `videoMode`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x] done`

---

### Шаг 3 - Не предлагать разрешения, которые будут отброшены

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt`
**Depends on:** - независим

**Prompt for developer:**

> Отфильтровать список разрешений по действующим для фоторежима пропорциям тем же критерием, которым `CameraUseCaseFactory` решает, применять разрешение или нет. Когда в черновике разрешение не выбрано, показывать строку выбора неактивной подсказкой вместо подсвеченного первого пункта: `indexOf` возвращает -1, и приведение к нулю выдаёт выбор, которого не делали.

**Why:**

Сейчас пользователь выбирает 16:9-разрешение, конвейер его молча выбрасывает, и снятый кадр приходит с другими пропорциями и размером - ровно тот симптом «пропорции», о котором сообщил владелец (§2.3).

**Verification:**

- `Grep` - `photoResolutions` в диалоге проходит через фильтр по пропорциям.
- `Grep` - `indexOf(draft.resolution).coerceAtLeast(0)` больше не встречается.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x] done`

---

### Шаг 4 - Запасной источник поворота, когда датчика нет

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraOrientationManager.kt`
**Depends on:** - независим

**Prompt for developer:**

> Инициализировать `currentRotation` поворотом дисплея, а не константой `Surface.ROTATION_0`, и при `canDetectOrientation() == false` брать поворот дисплея как единственный источник вместо залипания на нуле.

**Why:**

На устройстве без датчика ориентации в `targetRotation` навсегда уходит нуль, и снимок сохраняется так, будто устройство стоит в естественном портрете, независимо от реального поворота экрана (§2.4).

**Verification:**

- `Grep` - `canDetectOrientation()` имеет ветку с поворотом дисплея.
- `Grep` - `currentRotation` инициализируется не литералом `Surface.ROTATION_0`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x] done`

---

### Шаг 5 - Переносить зум по запрошенному значению, а не по состоянию сессии

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Шаг 1

**Prompt for developer:**

> Запоминать оптический зум, который пользователь запросил на текущем объективе (`setZoomRatio`), и
> переносить через перепривязку именно его, откатываясь на `zoomState` только когда запроса не было
> (слайдер задаёт намерение в линейных единицах). Сбрасывать запомненное значение при смене объектива,
> чтобы новый объектив по-прежнему стартовал со своего значения по умолчанию.

**Why:**

Перенос через `currentZoomRatio()` читает состояние сессии, а в видеорежиме этот аппарат отвечает 1.0,
хотя его собственное превью реально увеличено, - поэтому связка фото -> видео -> фото возвращала
пользователя на 1x при показаниях 3x (device-тест 2026-08-11, §4.2 Last Audit).

**Verification:**

- `Grep` - `requestedZoomRatio` задаётся в `setZoomRatio` и обнуляется при смене объектива.
- `Grep` - перенос читает `requestedZoomRatio ?: currentZoomRatio()`.
- `.\a.ps1 fk` - exit 0 (проверено 2026-08-11).
- Device: 3x в фото -> видео -> фото; проба `S1457: zoom carry source=requested value=3.0` и показания 3x.

**Status:** `[x] done`

---

### 3.1 Вынесено за рамки

- Безэкранная съёмка (`HeadlessPhotoCapturer`) не задаёт `targetRotation`, не выбирает объектив, не применяет пропорции и зум. Видоискателя там нет по построению, поэтому расхождением «превью против результата» это быть не может. Запарковано отдельным тикетом.
- В видеорежиме `physicalCameraId` не применяется, а `activeCameraIndex` не сбрасывается, поэтому подпись объектива продолжает обещать субобъектив, которого в потоке нет. Отдельный тикет: симптом «камера не та» относится к видео, а не к фото.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1189 - перечисление возможностей зума и объективов; S1260 - округление подписей зума; S1360 - headless-съёмка при закрытой камере; S1066 - рамка результата и пост-кроп 16:9. Ни один из них не описывает расхождение результата с превью.
- **UI scope:** список разрешений в диалоге настроек камеры сужается до значений, которые конвейер действительно применяет; новых элементов интерфейса не добавляется, размещение существующих не меняется.

---

## 4. Проверка

Устройство: эмулятор для сборки и компиляции, реальный аппарат для итоговой приёмки - зум, объективы и датчик ориентации на эмуляторе не воспроизводятся.

1. Зум переживает применение настроек: выставить зум отличным от 1x, открыть настройки камеры, применить любое значение, снять кадр. Ожидается: превью и файл на выставленном зуме.
2. Зум переживает переключение режима: выставить зум, переключиться в видео и обратно в фото, снять кадр.
3. Геометрия фиксируется на спуске: нажать спуск и сразу подвинуть зум. Ожидается: файл обрезан по зуму на момент спуска.
4. Разрешения согласованы с пропорциями: открыть настройки камеры в фоторежиме. Ожидается: в списке только разрешения, которые действительно применяются; при неприменённом разрешении первый пункт не показан выбранным.
5. Ориентация без датчика: на аппарате без датчика ориентации или при отключённом автоповороте снять кадр в альбомной ориентации. Ожидается: сохранённый файл ориентирован по экрану, а не по естественному портрету.

### 4.1 Что уже закрыто на эмуляторе

Прогон 2026-08-07 на emulator-5554 (Android 15, standard debug 2.60.8071.632). Полный сценарий и лог:
`temp/S1457/mobile_test_scenario_20260807_1635.md`.

- Пункт 4 закрыт целиком. `S1457: resolution list filtered 5 -> 3` доказывает, что два из пяти
  предлагавшихся разрешений конвейер действительно молча выбрасывал; после правки список содержит только
  4:3, строка пуста до применения и показывает выбранное значение после.
- Пункт 3 закрыт наполовину: проба выходит за 1 мс до `takePictureInternal`, то есть геометрия снимается
  на спуске, а не в колбэке сохранения. Совпадение самих пикселей эмулятор подтвердить не может.
- Пункты 1 и 2 подтверждены только как проводка: восстановление срабатывает на перепривязке того же
  объектива и не срабатывает на смене объектива (подпись Wide -> Front, строки восстановления нет).
  Значение переносится нулевое, потому что эмулятор отдаёт `maxZoomRatio` 1.0.
- Пункт 5 не выполнялся: у эмулятора есть акселерометр, ветка недостижима.

Итого на реальном аппарате остаются пункты 1, 2, 3 (пиксели) и 5.

---

## Last Audit

### Manual (device retest 2026-08-11 18:12, after step 5)

Device: RFCR110NBQJ, build `v2.60.8111.809-DEBUG`. Closes the one leg that failed in the run below.

**Spec §4.2 - zoom survives photo -> video -> photo: PASS.**

- Set 3x in photo, switched to video and back. Readout back in photo reads `3x`, where before the fix it
  read `1x`.
- Both rebinds of the round trip restored the same value: `S1457: zoom carry restores 3.0` at 18:12:27.582
  (into video) and 18:12:30.594 (back to photo). Before the fix the second one carried 1.0.
- Pixels, not just the readout - the readout was the thing that lied in the previous run. Shot at 3x right
  after the round trip, then a 1x reference of the same scene: `fov.py match verify_1x.jpg verify_3x.jpg`
  measures k=3.00 at corr 0.998, against corr 0.634 at k=1.00. Files in `temp/S1457/run20260811/`.
- Both frames were fetched with the new `adb.ps1 pull` verb (S1578), which is what made the measurement a
  two-command step rather than a raw `adb.exe` call.

The other four legs are unchanged by step 5 and keep the verdicts recorded below. The §4.5 fallback branch
stays unexercisable on this handset for the reason given there - it needs a display that is itself landscape.

### Manual (device test 2026-08-11)

Device: RFCR110NBQJ - Samsung Galaxy S21+ (SM-G996U1), Android 15 (SDK 35), 1080x2400 @450dpi. Build
`v2.60.8082.309-DEBUG` (standard debug, S1457 probes present). The device was held in landscape for the
whole run: the overlay counter-rotates by -90 deg, so `CameraOrientationManager` sat on
`Surface.ROTATION_270` throughout. Artifacts - photos, screenshots, UI dumps and the two measurement
scripts - in `temp/S1457/run20260811/`.

Real optics, which is what the AVD could not offer:
`S1189: presets=[1.0, 3.0, 5.0, 8.0, 10.0, 20.0, 30.0] native=1.0..8.0`. Presets up to 8 are pure optics
(`digitalZoomFactor` stays 1.0); 10, 20 and 30 clamp the optics at 8 and add a digital crop of 1.25, 2.5
and 3.75. That split is what makes criterion 3 testable at all.

**How a saved file was measured.** Every claim about a photo comes from the pulled JPEG, never from the
gallery thumbnail or the preview. `fov.py` scans crop factors k and reports the k whose centred crop of
the reference frame best matches the test frame, correlated on a zero-mean unit-variance 128x128 grey
thumbnail, so exposure and white-balance differences between two shots cannot move the peak - the winning
k IS the measured field-of-view ratio. `frame_vs_preview.py` runs the same correlation against the
letterboxed PreviewView area of a screenshot, searching rotation x width kept x height kept, which is what
settles orientation.

**Spec §4.1 - zoom survives a settings apply: PASS.**
- Expected: preview and file both stay at the zoom set before the apply. Actual: both did.
- Zoom set to 3x, resolution 4032x3024 applied in the settings dialog. Probe:
  `S1457: zoom restored 3.0 -> 3.0` at 17:26:42.476, on the rebind the apply triggered.
- The live readout still showed `3x` after the dialog closed, so the optics themselves held the ratio.
- File `photos/c1_settings_apply_3x.jpg` against the same-resolution 1x reference `photos/ref2_1x.jpg`:
  measured k=3.00 at corr 0.997, while corr at k=1.00 is only 0.155. The file is a 3x frame.
- Side result on real optics: `S1457: resolution list filtered 6 -> 1` at 17:25:34.093. Five of the six
  offered resolutions would have been silently discarded by the pipeline here, against two of five on the
  AVD - the defect §2.3 describes is markedly worse on real hardware than the emulator suggested.

**Spec §4.2 - zoom survives photo -> video -> photo: FAIL.**
- Expected: the same lens keeps the ratio across the round trip. Actual: it returns at 1x.
- Readout sequence: `3x` in photo, `3x` in video, `1x` back in photo. Probes:
  `S1457: zoom restored 3.0 -> 3.0` at 17:30:01.167 (into video) then
  `S1457: zoom restored 1.0 -> 1.0` at 17:30:10.257 (back into photo).
- Reproduced twice - 17:29:16.584 / 17:29:21.400 and the run above.
- The lens label stayed `Macro` on both legs, so the same-lens guard passed and the carry did happen. The
  value carried is simply 1.0: `currentZoomRatio()` already reads 1.0 from the video session when
  `applyMode(false)` samples it.
- File-level proof: the shot taken right after the round trip measured k=1.00 at corr 0.979 against the 1x
  reference (`photos/c2_after_video_roundtrip.jpg`). The user set 3x and received a 1x photo.
- The divergence is inside video mode, not in the restore. While in video the readout claims `3x` and the
  video preview is genuinely zoomed (preview field of view roughly 4x against the photo 1x preview -
  indicative only, the video letterbox differs), yet the `zoomState` that same readout mirrors reports 1.0
  to the next rebind. Screens `c2a_photo_1x.png`, `c2b_photo_3x.png`, `c2c_video_claims3x.png`.
- Consequence for §3 step 1: the restore is correct for a settings-apply rebind but does not cover the
  mode-switch rebind that §2.1 explicitly lists among its triggers.

**Spec §4 lens change - zoom starts at the new lens default: PASS.**
- Expected: no carry across a lens change. Actual: none.
- 3x on the `Macro` lens, tapped Switch camera: label became `Front` and the readout became `1x`.
- No `S1457: zoom restored` line was emitted for that transition - the last one is 17:30:10, the switch
  happened around 17:31:2x. The same-lens guard suppresses the carry exactly as step 1 requires.

**Spec §4.3 - capture geometry pinned at shutter press: PASS.**
- Expected: the file matches the zoom held at the press, not the value the slider moved to afterwards.
- Method: pills 10 and 30 both clamp the optics to 8x and differ only in the digital crop, 1.25 against
  3.75. The sensor frame is therefore identical for both and only the post-save crop can differ, which
  isolates the fix from any hardware race on the optics.
- Pressed the shutter at 10x, then moved to 30x immediately after; the readout read `30x` once the move
  landed. Probe: `S1457: capture geometry pinned zoom=1.25 aspect16x9=false` at 17:36:19.376.
- File `photos/c4_pressed10_moved30.jpg` against `photos/ref_8x.jpg`: measured k=1.25 at corr 0.994 -
  identical to the stable 10x control (`photos/stable_10x.jpg`, k=1.25 at corr 0.994) and not the 3.75 the
  slider held while the JPEG was being written.
- The mirror direction samples correctly too: pressed at 30x then moved to 10x logged
  `S1457: capture geometry pinned zoom=3.75` at 17:34:33.125. Its pixels cannot be measured - a 3.75 crop
  of this scene is featureless surface that correlates at 0.20 against anything, including a second 30x
  shot of the same frame - which is why the 10 -> 30 direction is the decisive one.

**Spec §4.5 - orientation: PASS on the observable half, fallback branch NOT EXERCISABLE.**
- Expected: a shot taken with the device held in landscape is not pinned to natural portrait. Actual: it
  is not.
- File `photos/ref2_1x.jpg` is 4032x3024 landscape, EXIF orientation 1, and the world in it is upright -
  the clock in the scene reads level.
- Against the viewfinder area of the screenshot taken at the same zoom (`screens/02_viewfinder_1x_fullres.png`)
  the best match is rotate=270 (90 deg clockwise) at width kept 1.000 and height kept 1.000, corr 0.927.
  The competing hypotheses score 0.254 at rotate=0 - which is what a rotation pinned to natural portrait
  would have produced - 0.157 at 180 and 0.002 at 90. So the saved file covers exactly the region the
  viewfinder showed, full frame with no crop, and is stored for the device pose.
- `S1457: display rotation fallback` never fired and cannot fire on this handset: the S21+ always reports
  an accelerometer, so `canDetectOrientation()` stays true. `dumpsys sensorservice restrict` did not change
  that - after the restriction and a resume the overlay was still counter-rotated, proving the listener was
  still live. The branch stays unverified on hardware, for the same reason as on the AVD.
- The portrait-locked host also makes the branch undiscriminating here even if it could be reached:
  `displayRotation()` returns 0 while the camera activity is on top, which is the value the removed literal
  had. Only a device whose display itself is landscape - the car head unit §2.4 names - can tell the two
  apart.

**Verdict.** Three of the four fixes are confirmed on real optics: geometry pinned at the shutter press,
the filtered resolution list, and the suppressed carry on a lens change. The zoom restore is confirmed for
a settings-apply rebind and refuted for the photo -> video -> photo round trip, so §3 step 1 is not
complete. The ticket cannot go to `Verified` on this run; status and probe tags left untouched.

---

## Revision History

- **2026-08-11** - by `/spec-test-device` (SM-G996U1, device: RFCR110NBQJ, Android 15, real optics)
  - Artifacts: `temp/S1457/run20260811/` · criteria PASS/FAIL 4/1 · errors in log 0
  - Criterion §4.2 (zoom across photo -> video -> photo) FAILS: the video session reports zoom 1.0 to the
    next rebind while its own readout claims 3x.
  - Status left `BlockNeedUserTest`: one of the four fixes is incomplete.

- **2026-08-07** - by `/spec-test-device` (sdk_gphone64_x86_64, device: emulator-5554, Android 15)
  - Сценарий: `temp/S1457/mobile_test_scenario_20260807_1635.md` · PASS/FAIL/SKIPPED 6/0/0 · ошибок в логе 0
  - Статус оставлен `BlockNeedUserTest`: три из четырёх правок эмулятор подтвердить не может.
