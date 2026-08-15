# Спецификация (compact bugfix): S1360 - Headless-съёмка фото по жесту падает с "Camera is closed"

**Ticket:** S1360
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-02
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-02

**Текст:**

Автозахват при анализе удалённого лог-бандла (`/newlog`), сессия `logs/fastmediasorter_20260801_183450.log`, устройство SM-S731B, Android 16 / API 36, сборка `2.60.7302.058-NoLegal-DEBUG`.

Жест краевой полосы "вверх" (`S1210: lift zone=LEFT_BOTTOM selection=UP`) четыре раза подряд не сделал фото. Каждая попытка - две ошибки подряд и один и тот же стек CameraX. Соседний жест "вниз" (снимок экрана) в том же окне отработал успешно, то есть сам жестовый слой жив, ломается именно ветка съёмки камерой.

Лог-строки:

```
[  649] 21:28:23  D  S1242: gesture hint removed before capture
[  650] 21:28:23  D  App moved to FOREGROUND
[  652] 21:28:24  D  S1188: relayout axis=VERTICAL bands=4
[  653] 21:28:24  E  HeadlessPhotoCapturer: capture failed
[  699] 21:28:24  E  PhotoCaptureLaunchManager: headless capture failed
[  745] 21:28:25  E  HeadlessPhotoCapturer: capture failed
[  791] 21:28:25  E  PhotoCaptureLaunchManager: headless capture failed
[  847] 21:28:37  E  HeadlessPhotoCapturer: capture failed
[  893] 21:28:37  E  PhotoCaptureLaunchManager: headless capture failed
[  939] 21:28:38  E  HeadlessPhotoCapturer: capture failed
[  985] 21:28:38  E  PhotoCaptureLaunchManager: headless capture failed
```

Стек (одинаковый во всех четырёх блоках):

```
androidx.camera.core.ImageCaptureException: Camera is closed.
	at androidx.camera.core.imagecapture.TakePictureManagerImpl.abortRequests(TakePictureManagerImpl.java:164)
	at androidx.camera.core.ImageCapture.abortImageCaptureRequests(ImageCapture.java:1208)
	at androidx.camera.core.ImageCapture.onSessionStop(ImageCapture.java:1197)
	at androidx.camera.camera2.internal.Camera2CameraImpl.notifySessionStoppedToUseCases(Camera2CameraImpl.java:1112)
	at androidx.camera.camera2.internal.Camera2CameraImpl.detachUseCases(Camera2CameraImpl.java:1131)
	at androidx.camera.core.impl.AdapterCameraInternal.detachUseCases(AdapterCameraInternal.java:98)
	at androidx.camera.core.internal.CameraUseCaseAdapter.detachUseCases(CameraUseCaseAdapter.java:983)
	at androidx.camera.lifecycle.LifecycleCamera.onStop(LifecycleCamera.java:115)
	at androidx.camera.lifecycle.LifecycleCamera.suspend(LifecycleCamera.java:158)
	at androidx.camera.lifecycle.LifecycleCameraRepository.suspendUseCases(LifecycleCameraRepository.java:591)
	at androidx.camera.lifecycle.LifecycleCameraRepository.setInactive(LifecycleCameraRepository.java:536)
	at androidx.camera.lifecycle.LifecycleCameraRepository$LifecycleCameraRepositoryObserver.onStop(LifecycleCameraRepository.java:663)
```

---

## 1. Проблема / симптом

Съёмка фото по краевому жесту не работает: 4 попытки из 4 в одной сессии завершились `ImageCaptureException: Camera is closed`, файл не создан, пользователь получает только ошибку.

Наблюдалось на flavor `noLegal`, debug-сборка `2.60.7302.058`, SM-S731B / Android 16 (API 36), портрет и ландшафт (в окне отказа фиксируется `S1188: relayout axis=VERTICAL`, то есть смена ориентации бэндов).

Стек показывает, что отмена приходит из `LifecycleCameraRepositoryObserver.onStop` - то есть владелец жизненного цикла, к которому привязан `ImageCapture`, уходит в `STOPPED` раньше, чем `takePicture` успевает вернуть результат. Сразу после каждого отказа в логе идёт `App moved to BACKGROUND - optimizing resources`, что согласуется с этой версией.

---

## 2. Корневая причина

Расследовано по коду 2026-08-02. Гипотеза захвата подтверждена в части «привязка к чужому
lifecycle», но её вторая половина - «останавливается сам, сразу после `bindToLifecycle`» - **не**
подтвердилась: останов приходит извне.

Доказанное по дереву:

- `HeadlessPhotoCapturer` (118 строк, `ui/cameracapture/helpers`) принимает `lifecycleOwner`
  конструктором и делает `provider.bindToLifecycle(lifecycleOwner, selector, imageCapture)` - то
  есть время жизни съёмки целиком определяется чужим объектом, которым капчурер не владеет.
- `PhotoCaptureLaunchManager` (216 строк, `widget`) зовёт `finish()` только в терминальных ветках -
  после сохранения, после ошибки, после открытия вьюера. Своим кодом трамплин себя во время съёмки
  не закрывает.
- `PhotoCaptureLaunchActivity` в манифесте (строка 529) объявлен `excludeFromRecents` +
  `taskAffinity=""` и **без** `android:noHistory` - его сняли ещё в S0790 ровно из-за потери
  результата. Соседние трамплины (`ScreenRecordingLaunchActivity`, `LinkDownloadLaunchActivity`)
  `noHistory` сохранили, но к этой ветке отношения не имеют.

Отсюда следует переформулировка: activity не убивает себя и не помечена на снос манифестом, значит
`STOPPED` ей назначает система. Это согласуется со стеком - отмена приходит из
`LifecycleCameraRepositoryObserver.onStop`, - и с логом, где сразу за каждым отказом идёт `App moved
to BACKGROUND`. Кандидат, требующий подтверждения на устройстве: краевой жест срабатывает, когда
приложение не на переднем плане, а Android 16 (API 36) режет фоновый запуск activity - трамплин
стартует, но до RESUMED не доходит и уходит в STOPPED, унося с собой use case.

Ключевой вывод для §3 не зависит от того, какая именно внешняя причина останавливает хост: **съёмка
не должна зависеть от чужого lifecycle вообще**. Пока зависимость есть, любой внешний останов -
фоновый запуск, смена ориентации (`S1188: relayout` виден в том же окне), системное решение о
приоритете - воспроизводит ровно этот стек.

---

## 3. Исправление

Направление: дать съёмке собственный lifecycle вместо чужого. `HeadlessPhotoCapturer` заводит
внутренний `LifecycleRegistry`, переводит его в `RESUMED` перед `bindToLifecycle`, отдаёт его как
владельца, и гасит в `DESTROYED` внутри `release()` - которая уже вызывается на обеих терминальных
ветках и идемпотентна. Внешний останов хоста после этого физически не может отменить кадр: CameraX
слушает регистр, которым владеет сам капчурер.

Что это меняет по существу:

- Убирает весь класс отказов `Camera is closed` по стеку `LifecycleCameraRepositoryObserver.onStop`,
  а не конкретный сценарий фонового жеста.
- Не требует ни foreground-сервиса, ни правки манифеста, ни изменения трамплина - изменение
  локализовано в одном файле на 118 строк.
- Переносит ответственность за освобождение камеры целиком на `release()`, что и так уже её
  контракт («safe to call more than once»).

Что проверить при реализации, а не решать здесь:

- Ловушка утечки: если `release()` не вызовется (например, `takePicture` не вернёт ни одну ветку),
  камера останется занятой дольше, чем при привязке к activity. Нужен таймаут или привязка к
  `onDestroy` хоста как страховка - но именно как страховка, а не как основной механизм.
- Осталась ли причина в фоновом запуске: если да, вторым шагом стоит проверить, доходит ли до
  пользователя понятное сообщение, когда система вообще не даёт стартовать трамплину.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1246 (camera-capture-saver-reports-success-on-failed-upload, Verified) - соседняя область, тот же путь сохранения; S1262 (camera-photo-profile-menu, BlockNeedUserTest).

---

## 4. Проверка

Тикет - багфикс, поэтому до флипа в `Implemented` в спеку пишется запись «до/после»: отказ с его
доказательством и то же наблюдение после правки. Отказ уже задокументирован в §0 - четыре блока
`ImageCaptureException: Camera is closed` из сессии `fastmediasorter_20260801_183450.log`, этого
достаточно как «до».

- На реальном устройстве выполнить краевой жест «снять фото» 5 раз подряд: 5 файлов в целевой папке,
  ни одной строки `HeadlessPhotoCapturer: capture failed` в логе.
- Повторить тот же жест, когда приложение не на переднем плане - это исходный сценарий отказа, а не
  дополнительный.
- Повернуть экран сразу после жеста: `S1188: relayout` в окне съёмки не должен ничего отменять.
- Проверить, что камера освобождается: после серии снимков системный индикатор «камера
  используется» гаснет, повторный жест работает без перезапуска приложения.
- Эмулятор недостаточен: отказ завязан на решение системы о фоновом запуске и на реальный
  камера-стек, поэтому гейт - `BlockNeedUserTest` на устройстве, а не AVD-прогон.

---

## Last Audit

### Manual device test - 2026-08-11 (RFCR110NBQJ, SM-G996U1, Android 15 / SDK 35, standard debug)

**Сборка:** `2.60.8111.809-DEBUG`, versionCode `260811180`. Процесс pid `23038` до перезапуска приложения в середине прогона и pid `31551` после; перебазирование отмечено там, где оно влияет на чтение лога.

**Триггер - настоящий краевой жест, а не прямой запуск трамплина.** Прямой `am start` на этом устройстве невозможен: Samsung отказывает shell в старте неэкспортированной activity - `SecurityException: Permission Denial .. not exported from uid 10371`, `result code=0`, файл не создаётся. Пять первых «выстрелов» так и ушли в пустоту и отброшены. Оверлей в этой сборке есть (`SYSTEM_ALERT_WINDOW` объявлен в установленном APK, то есть собрано с `-Pfms.edgeGestureOverlay=on`), поэтому на время прогона включён «Gesture overlay» и слот Left top / Down временно переназначен с «Silent screenshot» на «Take a photo». Обе настройки восстановлены и перечитаны с устройства.

**Цепочка проб на каждый снимок:** `S1210: lift zone=LEFT_TOP selection=DOWN` -> `S1478: headless lens=0 rotation=0 aspect=<n>` -> `S1360: bound to own capture lifecycle, host stop cannot abort the frame`.

**Разбор ложной сигнатуры.** В логе на каждый снимок есть `I/RequestInjectorService( 1575): Camera is closed. cameraId(0), state(3)`. Это `system_server` и HAL, штатно закрывающий устройство после кадра. Сигнатура §0 - другая: `E/HeadlessPhotoCapturer` плюс `androidx.camera.core.ImageCaptureException: Camera is closed` в процессе приложения. Ниже «отказ» означает только вторую.

#### Сценарий 1 - пять снимков подряд: PASS

- Пять жестов дали пять файлов в `DCIM/Camera`, счётчик `35 -> 40`.
- Пять строк `S1360: bound to own capture lifecycle`, по одной на снимок.
- Ни одной строки `HeadlessPhotoCapturer: capture failed`.

#### Сценарий 2 - жест, когда приложения нет на переднем плане: PASS

- Передний план до и после жестов - `com.sec.android.app.launcher/.activities.LauncherActivity`, то есть приложение действительно в фоне.
- Два жеста дали два файла, счётчик `40 -> 42`, пробы на месте, отказов нет.
- Это исходный сценарий §0. На Android 15 трамплин стартует из фона беспрепятственно: у приложения работает оверлейный foreground-сервис, который снимает ограничение на фоновый старт activity.

#### Сценарий 3 - поворот сразу после жеста: FAIL, отказ §0 воспроизведён

Отказ ловится сменой ориентации через ~350 мс после жеста, когда кадр ещё в полёте. Воспроизведён два раза из двух, файл не создан ни разу (счётчик `47 -> 47`):

```
20:39:46.057 D/ScreenGestureOverlayManager: S1210: lift zone=LEFT_TOP selection=DOWN
20:39:46.162 D/HeadlessPhotoCapturer: S1360: bound to own capture lifecycle, host stop cannot abort the frame
20:39:46.862 D/ScreenGestureOverlayManager: S1188: relayout axis=HORIZONTAL bands=1
20:39:47.026 E/HeadlessPhotoCapturer$takePicture: HeadlessPhotoCapturer: capture failed
20:39:47.026 E/HeadlessPhotoCapturer$takePicture: androidx.camera.core.ImageCaptureException: Camera is closed.
20:39:47.038 E/PhotoCaptureLaunchManager: PhotoCaptureLaunchManager: headless capture failed
```

Причина видна в дереве и не требует догадок. `PhotoCaptureLaunchActivity` не объявляет `android:configChanges`, поэтому смена ориентации её **уничтожает и пересоздаёт**. `onDestroy()` зовёт `capturer.release()` (то же делает и `hostObserver`), а `release()` зовёт `captureLifecycle.destroy()` - ровно то, что сообщает CameraX о конце use case. Страховка из §3, объявленная «не основным механизмом», срабатывает во время **пересоздания**, а не сноса, и убивает кадр. Пересозданная activity тут же запускает вторую съёмку, и её убивает `provider.unbindAll()` первой - отсюда две пары отказов на один жест.

Что этот прогон доказывает и чего не доказывает: менялся поворот **дисплея**, а не положение корпуса. Для этого сценария подмена честная - активность пересоздаёт смена конфигурации, и физический поворот при включённом автоповороте даёт ровно ту же смену конфигурации. Не проверено руками: поворот корпуса при **выключенном** автоповороте, когда конфигурация не меняется и пересоздания быть не должно.

Контрольный опыт, отделяющий починенное от непочиненного: тот же жест, но вместо поворота через 300 мс нажат HOME - хост уходит в `STOPPED`, не в `DESTROYED`. Кадр **дошёл**, файл сохранён (`47 -> 48`), отказов нет. То есть заявленный механизм §3 работает: внешний останов хоста кадр не отменяет. Дыра ровно одна - уничтожение хоста сменой конфигурации.

#### Сценарий 4 - освобождение камеры: PASS

- Серия из трёх жестов дала три файла, счётчик `48 -> 51`.
- Сразу после серии `dumpsys media.camera` показывает `Active Camera Clients: []`, то есть устройство отпущено и системный индикатор «камера используется» погашен. `appops get .. CAMERA` показывает закрытое окно использования (`duration=+718ms`), а не текущее.
- pid `31551` до и после серии один и тот же - перезапуск приложения не потребовался.
- Эта серия шла **после** двух отказов сценария 3, то есть заодно доказывает восстановление: занятая упавшим кадром камера не остаётся заблокированной.

**Изменённые настройки, все восстановлены и перечитаны с устройства:**

- `gesture_overlay_enabled`: `false` -> `true` -> восстановлено `false`.
- Left top / Down: `SILENT_SCREENSHOT` -> `TAKE_PHOTO` -> восстановлено `SILENT_SCREENSHOT`.
- `accelerometer_rotation`: `1` -> `0` -> восстановлено `1`.
- `user_rotation`: `0` -> `1` -> восстановлено `0`.
- `SYSTEM_ALERT_WINDOW` оставлен в `allow`, как и был.

**Артефакты:** `temp/S1478_S1360/` - логи `raw_s1360_sc1.txt`, `raw_s1360_sc2.txt`, `raw_sc3a.txt`, `raw_sc3a2.txt`, `raw_sc3b.txt`, `raw_s1360_sc4_burst.txt`.

### Follow-up repair - 2026-08-11

- The configuration-change reproduction destroyed `PhotoCaptureLaunchActivity`, which invoked
  `capturer.release()` while its owned CameraX lifecycle still had an active frame.
- The activity now declares `android:configChanges="orientation|screenSize"`; rotation keeps the
  existing trampoline and its capture lifecycle alive until the normal terminal callback releases it.
- Static manifest/resource processing passed. Standard debug build/install and launch smoke passed on
  RFCR110NBQJ; no app FATAL, Exception, or ANR was found in the post-launch scan.
- [ ] Re-run the edge-gesture photo capture while rotating the device mid-frame. Expect one saved
  file and no app-side `HeadlessPhotoCapturer: capture failed` or `ImageCaptureException: Camera is closed`.
