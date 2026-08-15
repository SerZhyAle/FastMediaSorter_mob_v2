# Спецификация (compact bugfix): S1203 - ANR при открытии пикера действия жеста

**Ticket:** S1203
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-26
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-26

**Текст:**

Symptom: opening the gesture-action picker dialog (EdgeGestureConfigManager.openActionPicker -> ScreenshotGestureActionPickerManager.showPicker, MaterialAlertDialogBuilder + list_selection_recycler / ListSelectionDialog) inside Settings > Management > Edge screen gestures > Configure gestures froze the app's main thread solid on emulator-5554 (standard debug, v2.60.7262.102-DEBUG, Android 15/SDK35, sdk_gphone64_x86_64, RAM 2G). System showed repeated "Fast Media Sorter & Organizer isn't responding" ANR dialogs; tapping Wait repeatedly (over ~40s cumulative) never recovered, only "Close app" did. TracerPid was 0 (not a debugger freeze). Reproduced twice in the same session: first freeze happened on the very first tap that opened the Up-direction picker for zone LEFT_TOP (before any scroll); second freeze happened again after reopening and attempting to scroll the same list. adb logcat captured zero ANR/ActivityManager lines around the event (worth checking why the ANR trace isn't landing in logcat, or if the capture tool cleared the buffer). Discovered incidentally while device-testing S1167 (lock-screen gesture strip visibility) - completely unrelated subsystem, so parking rather than investigating now.

**Захвачено во время:** S1167

---

## 1. Проблема / симптом

Открытие пикера действия жеста намертво вешает главный поток. Воспроизведено дважды на emulator-5554,
standard **debug** `2.60.7262.102-DEBUG`. `TracerPid=0` — это не остановка отладчиком. В logcat нет ни одной
строки ANR/ActivityManager вокруг события.

---

## 2. Корневая причина

Исходная версия (`PackageManager` на главном потоке при построении иконок строк) **опровергнута**: иконки строк —
статические `@DrawableRes` из `ScreenshotGestureActionCatalog`, ни `queryIntentActivities`, ни `loadIcon()` на пути
пикера нет. Опровергнута и привязка тикета к `ListSelectionDialog` — после S1038 пикер использует
`GesturePickerDialog`, который переиспользует только разметку `dialog_list_selection.xml`.

Единственная операция на пути открытия пикера, трогающая диск, — `Timber.d("S1166: …")` в
`ScreenshotGestureActionPickerManager`. Она попадает в `FileLoggingTree.log`, который пишет файл **синхронно на
вызывающем потоке** внутри `synchronized(this)` (`LoggingHelper.kt:273-334`; `StrictModeHelper.allowDiskIO` лишь
подменяет политику StrictMode и не уводит работу с потока). Тот же монитор захватывает фоновое зеркало
`flushDebugMirrorDelta()`, копирующее файл раз в 10 секунд.

### 2.1 Почему это не дефект релизной сборки

Обе половины механизма отключены вне debug:

- `FileLoggingTree.log` первой же строкой делает `if (priority < minPriority) return` (`LoggingHelper.kt:273`), а в
  release дерево сажается с `minPriority = WARN` (`LoggingHelper.kt:168`). `Timber.d` возвращается до любого I/O.
- `startDebugMirrorScheduler()` вызывается только внутри `if (BuildConfig.DEBUG)` (`LoggingHelper.kt:234-236`),
  то есть фоновое зеркало в релизе не запускается вовсе.

Вдобавок `S1166:` — временный probe-маркер, живущий лишь пока S1166 в `BlockNeedUserTest`.

**Следствие:** зависание не является дефектом, доступным пользователям, и не блокирует выпуск.

### 2.2 Что остаётся настоящим дефектом

Синхронная запись на диск на вызывающем потоке в `FileLoggingTree.log` присутствует и в release — для WARN/ERROR.
По протоколу проекта это P1 (I/O на главном потоке), но дефект не новый и переписывание логирования внутри
хотфикса рискованнее устраняемого.

Не установлено статически: состояние главного потока в момент зависания (ожидание монитора против непрерываемого
I/O) и причина отсутствия строки ANR в logcat. Нужен дамп потоков живого зависания.

---

## 3. Исправление

Оба конца конфликта сходятся на одном мониторе `synchronized(this)`: `log()` берёт его на **вызывающем**
потоке ради короткой дописи строки, а `flushDebugMirrorDelta()` держит его на **всё** копирование файла
(`RandomAccessFile` -> `FileOutputStream`, цикл по буферу) раз в 10 секунд на потоке `fms-log-mirror`.
Главный поток встаёт на мониторе на время копирования — это и есть зависание.

Правка ровно в двух шагах, без переписывания логирования (риск из 2.2):

1. **Одна очередь вместо двух потоков.** Дописью в файл и фоновым зеркалом владеет один и тот же
   однопоточный executor (сегодняшний `debugMirrorScheduler`, переименованный в `logIoExecutor`).
   Обе операции сериализуются очередью, а не монитором, поэтому конкурировать за него больше некому.
2. **Вызывающий поток только ставит задачу в очередь.** `log()` снимает метку времени (чтобы порядок
   записей соответствовал порядку вызовов) и отдаёт форматирование, санитизацию и запись в executor.
   Ни один `Timber.*` больше не касается диска на потоке вызова.

Две страховки, без которых правка вносит собственные дефекты:

- **Потолок очереди.** Снятие backpressure делает очередь неограниченной. Счётчик незавершённых задач
  с потолком: сверх потолка запись отбрасывается, число отброшенных копится и выводится одной строкой,
  когда очередь разгрузится. Молчаливая потеря строк для диагностического лога недопустима.
- **Слив перед крахом.** `writeCrashSynchronously` вызывается из `UncaughtExceptionHandler`, и очередь
  на этот момент может содержать ещё не записанные строки. Перед записью крэш-файла executor
  прокачивается пустой задачей с ограниченным ожиданием — однопоточный FIFO гарантирует, что после неё
  всё поставленное ранее уже на диске.

`writeCrashSynchronously` остаётся синхронным: обработчик краха обязан дописать файл до смерти процесса.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1038, S1166 - both block on this. Their acceptance runs *through* the frozen picker
  (S1038 verifies the grouped sections and per-item explanations, S1166 verifies every row's icon), so neither can
  be device-verified until this ANR is fixed. Noted during the 2026-07-26 sweep, which had S1038 queued next and
  had already deferred S1166 for the wrong reason (assumed a screenshot-capture limitation, actually unreachable UI).

---

## 4. Проверка

Проверяется на эмуляторе - симптом воспроизводился именно там (emulator-5554, standard debug), и обе
половины механизма живут только в debug (см. 2.1), так что release-сборка для проверки бесполезна.

- **Головной сценарий.** Settings > Management > Edge screen gestures > Configure gestures > тап по
  направлению Up зоны LEFT_TOP. Пикер открывается без зависания; список прокручивается. Повторить
  открытие/прокрутку не менее двух раз - исходный дефект воспроизводился со второго захода.
- **Момент зеркала.** Зеркало срабатывает раз в 10 секунд, поэтому подержать экран пикера открытым
  ~30 секунд, продолжая листать: раньше именно совпадение с копированием и вешало поток.
- **Порядок записей не нарушен.** В `fastmediasorter_*.log` строки одного потока идут в порядке
  вызовов, а не в порядке разгрузки очереди. Межпоточная монотонность меток не проверяется: метка и
  раньше снималась до входа в `synchronized`, так что перестановка записей разных потоков возможна
  ровно как прежде - это не регресс правки.
- **Асинхронный путь жив.** Строка `S1203:`, поставленная в очередь при инициализации логирования,
  присутствует в файле `fastmediasorter_*.log`: она проходит ровно через новый путь целиком.
- **Крах дописывается.** Спровоцировать краш нечем в штатном сценарии, поэтому проверяется косвенно:
  при наличии `fastmediasorter_crash_*.log` от прошлых сессий последние строки сессионного лога перед
  крэш-маркером на месте (слив очереди отработал).

Не проверяется на устройстве и остаётся статикой: `2.2` (синхронная запись WARN/ERROR в release) -
отдельный дефект, этим тикетом не закрывается.

## Last Audit

**Дата:** 2026-07-28. **Вердикт:** Verified (эмулятор-свип, emulator-5586, API 35, standard debug, нативная геометрия).

- Головной сценарий: пикер Up-жеста зоны LEFT_TOP открыт и пролистан до конца ДВАЖДЫ
  (второй заход - именно тот, где дефект воспроизводился), плюс обратная прокрутка; UI
  отзывчив, ни одного маркера `ANR in`/`Input dispatching timed out` в logcat за оба прогона.
- Момент зеркала: пикер держался открытым ~30-40 секунд каждого прогона с прокруткой - 10-секундное
  зеркало срабатывало под ним минимум трижды за прогон, зависания нет.
- Асинхронный путь: строка `S1203: file logging planted..` присутствует в
  `fastmediasorter_20260728_164048.log` (прошла через очередь целиком).
- Порядок записей: 0 инверсий таймстемпов на 444 строки файла.
- Крах-слив: не проверялся (crash-логов прошлых сессий на этом эмуляторе нет; «при наличии» -
  условие §4 не выполнимо здесь, косвенная проверка недоступна, риска не несёт).
- Probe-тег строки 184 удалён при переводе из BlockNeedUserTest (S1203-комментарии - постоянные
  WHY-референсы, остаются).
- Попутно: сам пикер показал сгруппированные секции (SCREEN CAPTURE / MEDIA / UTILITIES / LAUNCH /
  DISABLED) с пояснениями на каждом пункте - основная часть S1038-проверки наблюдалась мимоходом;
  S1038 остаётся заблокирован своей полнотой (устройственные экшены/noLegal-группа).
