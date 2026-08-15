# Спецификация (compact bugfix): S1573 - Сохранение снятого кадра выполняется в главном потоке

**Ticket:** S1573
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-11
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-11

**Текст:**

The camera capture save chain runs on the main thread. Evidence from a real-device run (SM-G996U1, Android 15, standard debug v2.60.8082.309, 2026-08-11): three shutter presses in one multi-capture session produced a StrictMode android.os.strictmode.DiskWriteViolation, ~duration 3 ms, whose stack is com.sza.fastmediasorter.data.capture.CameraCaptureSaver.save(CameraCaptureSaver.kt:134) -> CameraCaptureSaver$save$1.invokeSuspend -> kotlinx.coroutines.DispatchedTask.run -> android.os.Handler.handleCallback -> android.os.Looper.loop -> android.app.ActivityThread.main. Logged pid/tid are both 10066, i.e. the main thread. Line 134 is `tempFile.delete()` in the `finally` block of `suspend fun save(..)`.

Call chain: CameraCaptureResultManager.persistMultiCapture(CameraCaptureResultManager.kt:73-75) does `lifecycleScope.launch { saveCapturedMedia(file, isVideo) }`. `lifecycleScope.launch` defaults to Dispatchers.Main.immediate. SaveCapturedMediaUseCase.invoke (SaveCapturedMediaUseCase.kt:26-28) has no `withContext`. CameraCaptureSaver.save (CameraCaptureSaver.kt:70) has no `withContext` either - it relies entirely on the caller's context. So the whole save body runs on Main: maybeCopyToClipboard, saveToDcim / saveLocal / upload, and the temp-file delete.

StrictMode de-duplicates identical stacks per process, so one logged instance covers all three shots. Only the delete was reported, but the same dispatcher carries the JPEG write and the clipboard copy on the same path; a network/cloud target routes `upload` through it too.

Raw log: temp/scratch/adb_log_20260811_151412.log

Distinct from S1480, whose acceptance criterion is scoped to violations "whose stack passes through CameraCaptureResultManager.showGalleryThumbnail" - that path was clean across all three shots in the same run. This is the saver path, not the thumbnail path.

**Захвачено во время:** device-sweep камеры, круг 1 (проверка S1480 / S1457 / S1418)

---

## 1. Проблема / симптом

Весь путь сохранения снятого кадра исполняется в главном потоке. Устройство SM-G996U1, Android 15 (SDK 35), flavor standard, debug-сборка v2.60.8082.309.

Цепочка: менеджер результата съёмки запускает сохранение через `lifecycleScope.launch`, то есть на `Dispatchers.Main.immediate`; ни use case, ни сам saver не переключают контекст. Поэтому в главном потоке оказываются запись файла, копирование в буфер обмена и удаление временного файла, а для сетевой цели - ещё и выгрузка.

Наблюдаемое доказательство - одно нарушение StrictMode `DiskWriteViolation` на удалении временного файла. Оно короткое (3 мс), но это самая дешёвая операция цепочки: остальные тяжелее и находятся в том же потоке.

Для сетевого/облачного назначения последствие тяжелее локального: выгрузка идёт по тому же диспетчеру.

Не дубликат: S1480 закрывает путь миниатюры (`showGalleryThumbnail`), и в этом же прогоне он чист.

---

## 2. Корневая причина

`CameraCaptureSaver.save` (строка 70) - `suspend fun` без собственного `withContext`. Suspend-функция исполняется в том контексте, который дал вызывающий, поэтому диспетчер здесь не свойство saver'а, а свойство места вызова. Все три входа приходят с `Dispatchers.Main.immediate`:

- `CameraCaptureResultManager.persistMultiCapture` -> `lifecycleScope.launch { .. }`;
- `SaveCapturedMediaUseCase.invoke` (строки 26-28) контекст не переключает;
- `BrowseCameraCaptureManager.save` вызывает `cameraCaptureSaver.save` (строка 431) из уже запущенной на Main корутины.

KDoc saver'а обещает «Activity-free реализацию», владеющую маршрутизацией, но владения диспетчером в коде нет.

Почему дефект дожил до сегодня: `withContext(Dispatchers.IO)` внутри цепочки всё-таки есть, но только в листьях - `writeToDevice` (строки 204-205), `LocalCaptureDestinationWriter.write` (строка 23), `ImageClipboardWriter.copyImageFile`. Тяжёлая запись JPEG действительно уходит с Main, поэтому долгих зависаний не видно. На Main остаётся всё, что между этими островами:

- чтение настроек `settingsRepository.getSettings().first()` в `maybeCopyToClipboard` (строка 160);
- построение путей и `context.sendBroadcast` в `saveToDcim` (строки 182-189);
- вызов `upload` для сетевой цели (строка 107) - целиком, вместе с сетевым вводом-выводом;
- `tempFile.delete()` в `finally` (строка 134).

Именно последний, самый дешёвый из них, и попал в StrictMode - не потому, что он худший, а потому, что он единственный, кто трогает диск напрямую, без промежуточного `withContext`. Частичная защита листьев маскировала общую проблему: симптом был на порядок легче причины.

---

## 3. Исправление

- Обернуть тело `CameraCaptureSaver.save` в `withContext(Dispatchers.IO)`. Один пункт, закрывающий всех трёх вызывающих сразу.
- Чинить на уровне saver'а, а не вызывающих. Правка трёх `lifecycleScope.launch` убрала бы симптом, но оставила бы контракт saver'а зависящим от вызывающего - следующий вход с Main вернул бы дефект. Диспетчер ввода-вывода принадлежит слою данных.
- Вложенные `withContext(Dispatchers.IO)` в листьях остаются как есть: с тем же диспетчером это дешёвый no-op, а листья вызываются и по другим путям.
- Возврат из `withContext` происходит в контекст вызывающего, поэтому весь код после `save(..)` - снекбары, открытие редактора, обновление списка - остаётся на Main без правок.

### 3.1 Регрессионный тест

Юнит-тест в существующем `CameraCaptureSaverTest`: вызвать `save` из корутины, привязанной к одному именованному потоку, и записать `Thread.currentThread().name` внутри лямбды `upload` - она исполняется во внешнем теле, ровно там, где жил дефект. Имя должно отличаться от имени вызывающего потока. До правки тест падает, после - проходит. Дешевле и надёжнее, чем ловить StrictMode на устройстве.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1480 (соседний путь миниатюры камеры, тот же экран), S1569 (главный поток на пути миниатюр browse) - соседи, зависимости нет

---

## 4. Проверка

- Новый тест `CameraCaptureSaverTest` падает на текущем коде и проходит после правки - это и есть доказательство, что тест ловит именно дефект, а не что-то ещё. `expected: FAIL до, PASS после`.
- Остальные тесты `CameraCaptureSaverTest` (маршрутизация по трём назначениям, локальный откат, удаление temp-файла) остаются зелёными - правка контекста не должна менять маршрутизацию.
- `.\a.ps1 fk` - компиляция Kotlin, `expected: exit 0`.
- `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` - все гейты зелёные, включая detekt по затронутым файлам.
- На устройстве (перенесено, не гейт): три нажатия затвора в мультисъёмке не дают `DiskWriteViolation` со стеком через `CameraCaptureSaver.save`. Требует сборки и установки debug-APK; юнит-тест доказывает тот же инвариант дешевле.

---

## Last Audit

**Date:** 2026-08-11
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Подтверждение на устройстве: три нажатия затвора в мультисъёмке без `DiskWriteViolation` со стеком через `CameraCaptureSaver.save`. Не гейт: тот же инвариант доказан юнит-тестом, а проверка на устройстве требует сборки и установки debug-APK.

### Evidence

- **Тест сначала упал на текущем коде** - `CameraCaptureSaverTest > save body leaves the caller thread FAILED, java.lang.AssertionError at CameraCaptureSaverTest.kt:233`, `8 tests completed, 1 failed`. Это и есть доказательство, что тест ловит именно этот дефект, а не что-то соседнее.
- **После правки все 8 зелёные** - `BUILD SUCCESSFUL`, `Fast check passed`, exit 0. Маршрутизация по трём назначениям, локальный откат S0522 и удаление temp-файла не изменились.
- Компиляция Kotlin доказана тем же прогоном: `compileStandardDebugUnitTestKotlin` собирает и main, и тестовый source set.
- `scripts/quality/assert-detekt.ps1 -ChangedFiles ..` -> `PASS [scoped] - none among changed files`, exit 0.
- `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile` -> `post-change: PASS (Kotlin)`, exit 0.
- Правка в одной точке слоя данных закрывает все три входа (`CameraCaptureResultManager`, `SaveCapturedMediaUseCase`, `BrowseCameraCaptureManager`) - ни один из них не менялся.
- Инвариант отладочных меток: `Timber.d("S1573:` - 0 вхождений. `Timber.d("S1354: ..)` в этом же файле оставлена намеренно: S1354 сейчас `BlockNeedUserTest`, то есть её метка обязана существовать.
- FEATURES trilingual - EXEMPT: поведение для пользователя не меняется, чинится поток исполнения.

### Отклонение от плана

- Первая попытка вынесла тело в приватную `saveOnIoDispatcher`, а обёртку оставила публичной `save`. Компилируется и проходит тесты, но воскрешает уже забаселайненный `NestedBlockDepth`: его идентификатор в `config/detekt/baseline-app_v2.xml` привязан к сигнатуре `suspend fun save(..)`, и перенос тела в функцию с другой сигнатурой читается detekt'ом как новая находка. Итоговая форма - `withContext` прямо в `save`, сигнатура не тронута, тело не переехало: baseline продолжает совпадать, никакой правки baseline не потребовалось.
- Побочно выяснилось, что дополнительный отступ телу не нужен: лямбда `= withContext(..) { .. }` стоит на том же уровне, что и заменённое ею тело функции. Это же сохраняет совпадение забаселайненных `MaxLineLength`/`ArgumentListWrapping`, чьи идентификаторы привязаны к тексту элемента.
