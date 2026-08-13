# Стратегическая спецификация: S1103 - Лаунчер: все функции приложения как ярлыки стола + распознавание app-shortcuts

**Ticket:** S1103
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-18
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-18 (по тестированию)
**Tactical plan:** `PLAN/S1103_launcher-cell-actions-and-app-shortcuts/INDEX.md`

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-18

**Захвачено во время:**  тестирования (контекст «по тестированию»); связанный тикет - S0404

**Текст:**

все функции которые мы задумали для панели быстрого доступа и жестов все программы и сценарии, а также опраци по расписанию должны быть реализованы как возможные ярлыки рабочего стола лаунчера, которые может задать пользователь кроме, пожалуй, скриншота. саму панель тоже можно запустить оттуда
В современных андроид у многих программ реализованы варианты запуска. Нажимаешь на календарь например - а там и отурыть календарь и "добавить новую запись". хотелось бы чтобы программа распознавала тыаките опции и при выборе календаря в качестве программы для ярлыка - предлагала сразу с каким параметром он будет тут запускаться - например этот ярлык всегда про "добавить в календарь" - нужен короткий ресерч

**Заметка по дедупликации (не часть захвата):** вторая часть идеи (распознавание вариантов запуска сторонних программ, «добавить в календарь») пересекается с существующим тикетом **S0427** (third-party-app-shortcuts) - дочерним research-спеком эпика S0404. Не дублировать: при проработке опереться на S0427. Уникальная часть S1103 - первая: все внутренние функции (панель быстрого доступа, жесты, программы/сценарии, операции по расписанию) как задаваемые пользователем ярлыки стола, плюс запуск самой панели из ячейки.

---

## 1. Проблема

На рабочем столе лаунчера пользователь может задать ячейки-ярлыки, но набор внутренних функций, доступных как ярлык, неполон. Часть функций панели быстрого доступа/жестов/программ уже выведена как feature-роуты, но операций по расписанию и запуска самой панели быстрого доступа из ячейки нет. Пользователь ожидает, что любую задуманную нами функцию (кроме скриншота) можно повесить на ячейку стола. Отдельно - при выборе стороннего приложения для ячейки не предлагаются его варианты запуска (app-shortcuts, напр. «добавить запись в календарь»).

Область: launcher (домашний стол, `domain/model/launcher` + `core/panel` route-каталог), слой представления пикера ячейки.

---

## 2. Цели

1. Любая внутренняя функция, задуманная для панели быстрого доступа/жестов/программ и сценариев, а также операций по расписанию, доступна как задаваемая пользователем ячейка-ярлык стола лаунчера (кроме скриншота).
2. Сама панель быстрого доступа запускается из ячейки стола.
3. При выборе стороннего приложения для ячейки пользователю предлагаются его варианты запуска (app-shortcuts), и он может закрепить ячейку сразу на нужный вариант (напр. «добавить в календарь») - опираясь на S0427.

**Non-goals:**

- Скриншот как тип ячейки-ярлыка (владелец явно исключил: «кроме, пожалуй, скриншота»).
- Дублирование механики распознавания сторонних app-shortcuts - она принадлежит S0427; здесь только потребление её результата в пикере ячейки.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** сборки с launcher-поверхностью (standard, noLegal; source set `launcherEnabled`).
- **API level:** feature-роуты без API-специфики; чтение сторонних app-shortcuts (часть 2) - `ShortcutManager`/`LauncherApps` API 25+ (детали в S0427).
- **Wear OS:** не затрагивается.
- **Совместимость данных:** без миграции Room - `LauncherCellCommand` кодируется в один TEXT-столбец через namespace-префикс (`fn:`/`app:`/..), новый вид ярлыка не требует schema change.
- **Локализация:** EN/RU/UK - новые метки роутов обязательны во всех трёх (существующие роуты уже локализованы).
- **Доступность:** пикер ячейки и стол - клавиатура/D-pad/mouse, touch target (лаунчер часто на ТВ).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0404 (родительский эпик лаунчера); **S0427** (third-party-app-shortcuts - покрывает распознавание вариантов запуска сторонних программ; на него опереться, часть 2 идеи); доработки лаунчера S1087-S1102.
- **Граница S1103/S0427 (решено):** S1103 = только часть 1 (внутренние функции как ячейки стола + запуск панели из ячейки); часть 2 (распознавание сторонних app-shortcuts) полностью делегирована S0427. S0427 сейчас `BlockByOtherTask`, поэтому критерий 3 (варианты запуска приложения в пикере) закрывается позже, когда S0427 отгрузит контракт.
- **Перечень внутренних функций (решено):** большинство уже есть как feature-роуты (калькулятор, OCR, стримы, избранное, быстрые камера/голос, запись экрана, download-by-link, камера/видео-жесты). Добавляются две недостающие: (a) **операции по расписанию** - ячейка триггерит конкретную сохранённую операцию напрямую, (b) **запуск панели быстрого доступа** - ячейка открывает overlay панели. Скриншот исключён.
- **Поведение sched-op ячейки (решено владельцем 2026-07-22):** тап по ячейке операции - короткий диалог подтверждения (операция может копировать/перемещать/удалять файлы), затем запуск в фоне + тост с результатом. Прямой триггер без подтверждения отклонён из-за деструктивных операций (DELETE/MOVE).
- **Greenlight (решено):** явный go на тактику/реализацию S1103 дан владельцем (batch-запрос 2026-07-21).

### Quiz decisions (2026-07-18)
- Граница с S0427: S1103 = только часть 1 (внутренние функции как ячейки стола); распознавание вариантов запуска сторонних приложений делегировано S0427, здесь только потребление результата (S1103-1: A).
- Семантика (владелец выбрал B, отлично от рекомендации): (a) ячейка «операции по расписанию» триггерит операцию напрямую, не открывает экран расписаний; (b) ячейка «панель быстрого доступа» открывает overlay панели (S1103-2: B).
  - Impl-нота для тактики: прямой триггер требует привязки конкретной операции к ячейке в момент её создания (какую именно операцию запускать) - зафиксировать в пикере ячейки.
- Greenlight на тактику/реализацию дан (Q0: A).

---

## Last Audit

### Manual (device) - 2026-07-29, emulator-5554 (standard-debug v2.60.7262.102-DEBUG, API 37, 1600x2560 @320dpi)

Verdict: PASS on all three Part-1 criteria. The precondition that blocked the 2026-07-24 run (no saved scheduled operation) was resolved by creating one through the app's own UI, so the confirm-then-run path was exercised end to end this time.

APK provenance (the fast debug target pins the version string, so `versionName` proves nothing): pulled the installed `base.apk`, unzipped `classes*.dex` and counted symbols in the decompressed dex - `LauncherScheduledOpPickerDialogFragment` 55 hits, `executeScheduledOp` 10, the probe literal `S1103: executeScheduledOp` 1, against pre-existing controls `ExecuteLauncherCommandUseCase` 25 and `LauncherHomeViewModel` 191. A grep over the `.apk` itself is not a valid test - dex entries are compressed.

Criterion 1 - Feature category includes "Quick-access panel", and the cell opens the panel overlay:

- expected: "Choose a feature" lists "Quick-access panel" | actual: PASS - listed as the first entry, ahead of Calculator, Mini-game, Photo OCR translate and Streams.
- expected: the assigned cell opens the panel overlay | actual: PASS - tapping it opened the "Quick launch" overlay with its tiles and its own "Edit" affordance. Persisted as `fn:app_launch_panel` in `launcher_cells`, and `launcher_journal` recorded the launch.

Criterion 2 - "Scheduled operation" category, assign, confirm, background run, toasts:

- expected: the add-cell category picker lists "Scheduled operation" | actual: PASS - present in "Put on the desktop"; reachable by scrolling and by typing "sched" in the picker's search field.
- expected: the category lists saved operations | actual: PASS - with zero operations it shows "No scheduled operations yet" (`scheduled_operations` count confirmed 0 in the database, so the empty state was truthful); after one was created it listed it as "COPY: All Images -> Downloads", matching `launcher_cell_scheduled_op_label`.
- expected: assigning one writes a per-cell command | actual: PASS - persisted as `op:1` in `launcher_cells`, the `op:` namespace prefix with the operation id, no schema change.
- expected: tapping the cell shows a CONFIRM dialog warning about destructive effects | actual: PASS - title "Run this operation?", message "It may copy, move or delete files as configured.". The message names copy, move and delete explicitly rather than a bare "are you sure".
- expected: confirming runs it in the background with a start toast and a result toast | actual: PASS - probe `S1103: executeScheduledOp 1` fired, `ScheduledOp[1] fired: COPY` followed by 46 `COPY OK <file>` lines with zero failures, and two distinct toast windows were created (`Toast#5661` at the start, `Toast#5668` 19 ms after the last copy). The file work itself ran on background threads, not the main thread.

Criterion 3 - own-app, resource, OS and stream cells unchanged:

- expected: pre-existing cell commands untouched | actual: PASS - `launcher_cells` still holds `res:N:BROWSE`, `fn:streams`, `fn:quick_camera`, `fn:quick_voice`, `fn:calculator`, `fn:ocr`, `fn:favorites`, `os:settings`, `app:com.sza.fastmediasorter.debug` and the clock `GADGET`, in both the PORTRAIT and LANDSCAPE sets.
- expected: each kind still launches | actual: PASS - resource `res:6:BROWSE` opened `BrowseActivity`, stream `fn:streams` opened `StreamsActivity`, OS `os:settings` opened `com.android.settings/.homepage.SettingsHomepageActivity`, own-app opened `MainActivity`.

Out of scope and untested here: strategic criterion 3 (third-party app shortcuts), which stays deferred to S0427.

Finding to resolve before this ticket is considered done - main-thread disk I/O on the new path (P1 by the audit protocol's taxonomy): the tactical plan's Step 03.2 assumed "the use case runs its own IO work; do not add a dispatcher here", but it does not. `executeScheduledOp` runs in `viewModelScope` on the main dispatcher, and StrictMode flagged `DiskReadViolation` on the main thread from `ExecuteScheduledOperationUseCase.checkTargetReachability` (line 257, via `invoke` line 114) and from `logOp` (line 373) through `AppendToScheduledLogUseCase`. The heavy copying is correctly off-main, so this did not stall the run visibly, but the orchestration and logging touch disk on the UI thread. Fix by wrapping the call in `Dispatchers.IO` or by moving the boundary into the use case.

Environment notes for whoever repeats this run:

- The device-test itself was obstructed by an unrelated defect - `SettingsActivity` renders 99.9% black on this emulator while its view hierarchy stays intact. Parked as S1284; the workaround was to drive the screen by `uiautomator dump` bounds instead of by sight.
- Preconditions created for the test: "Use scheduled operations" was off and had to be enabled (it also requests a battery-optimization exemption), and one COPY operation (All Images -> Downloads) was created through Settings > Management. The run copied 46 image files into Downloads on the emulator.
- A long-press on the desktop enters edit mode, but only inside `launcherDesktop` bounds and not on the clock gadget - long-pressing the gadget opens Google Calendar, which cascades into a Google account flow that steals focus.

Evidence: temp/S1103/apk_proof.txt, temp/S1103/EV_crit1_feature_list.png, temp/S1103/EV_crit1_panel_overlay.png, temp/S1103/EV_crit2_empty_before.png, temp/S1103/EV_crit2_picker_lists_op.png, temp/S1103/EV_crit2_confirm_dialog.png, temp/S1103/logcat_run.txt.

---

## 4. Контекст текущей архитектуры

Лаунчер (эпик S0404) уже держит модель ячейки стола: команда-ярлык кодируется в один TEXT-столбец через namespace-префикс (приложение / внутренняя функция / ресурс / стрим / OS-цель), декодируется толерантно, исполняется отдельным use-case. Внутренние функции описаны статическим route-каталогом, который переиспользуется панелью быстрого доступа; часть функций там уже есть. Проблему §1 нельзя закрыть точечно: отсутствуют роуты операций по расписанию и запуска панели, а вторая часть (варианты запуска сторонних программ) требует отдельного механизма чтения системных app-shortcuts, живущего в research-спеке S0427.

---

## 5. Предлагаемый подход

Часть 1 переиспользует существующий вид команды-ярлыка «внутренняя функция» (namespace-префикс) - расширить route-каталог недостающими функциями и убедиться, что пикер ячейки предлагает весь каталог с учётом доступности в текущей сборке. Часть 2 потребляет результат S0427 (перечень вариантов запуска выбранного приложения) в том же пикере, не дублируя механику.

### 5.1 Основные столпы / модули

- Route-каталог внутренних функций: пополнить операциями по расписанию и запуском панели; каждая запись = стабильный ключ + метка/иконка + точка входа + (при наличии) переход в настройки, если функция выключена.
- Пикер ячейки стола: показывать полный каталог функций + (для приложения) варианты его запуска из S0427; фильтрация по доступности в сборке.
- Исполнение: существующий слой применения команды-ярлыка (новые ключи функций - через тот же путь; вариант приложения - целевой intent из S0427).

### 5.2 Потоки данных и событий

Пользователь в пикере ячейки -> выбор «функция» из каталога или «приложение + вариант запуска» (из S0427) -> команда кодируется в TEXT-поле ячейки -> при тапе по ячейке слой применения декодирует и исполняет.

### 5.3 Точки расширяемости

Namespace-кодирование команды оставляет добавление новых видов ярлыка без миграции БД; каталог функций растёт добавлением записи. Граница с S0427 держит распознавание сторонних shortcuts отдельно переиспользуемым.

---

## 6. Открытые вопросы / Research items

- **Короткий ресерч (из захвата):** как распознавать варианты запуска сторонних программ (Android app shortcuts / `ShortcutManager`, статические/динамические shortcuts, deep-link intents вроде «добавить запись в календарь») и предлагать их пользователю при выборе программы для ячейки. Опереться на research S0427.

---

## 7. Риски

- Размывание границы с S0427 (средняя): дубль механики распознавания сторонних shortcuts -> строго закрепить часть 2 за S0427, здесь только потребление (owner decision 1).
- Неоднозначный entry-point «операций по расписанию» (средняя): ярлык ведёт не туда, куда ждёт пользователь -> зафиксировать семантику на Approval gate (owner decision 2) до тактики.
- Мёртвый ярлык выключенной функции (низкая): тап по ячейке ничего не делает -> переиспользовать существующий приём «открыть настройку функции», если она скомпилирована, но выключена.

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая возможность (launcher): любые внутренние функции и операции по расписанию как ярлыки рабочего стола + запуск панели быстрого доступа из ячейки. Одно предложение в FEATURES/_RU/_UK при релизе (только сборки с launcher-поверхностью). Точная формулировка - после подтверждения перечня (owner decision 2).

---

## 9. Архитектурные решения (ADR)

ADR: переиспользовать существующее namespace-кодирование команды-ярлыка и route-каталог внутренних функций вместо нового типа ячейки. Обоснование - без миграции БД, единый путь исполнения, нулевое дублирование с панелью быстрого доступа.

---

## 10. Связи с другими спеками

- S0404 - родительский эпик лаунчера (owner-gated; ячеечная модель и route-каталог из него).
- S0427 - third-party-app-shortcuts: владелец распознавания вариантов запуска сторонних программ (часть 2). S1103 потребляет его результат.
- S1087-S1102 - серия доработок лаунчера (соседние ячейки/стол).

---

## 11. Критерии готовности (strategic-level)

1. В пикере ячейки стола доступны все внутренние функции (панель быстрого доступа, жесты, программы и сценарии, операции по расписанию), кроме скриншота.
2. Панель быстрого доступа запускается из назначенной ей ячейки.
3. При выборе стороннего приложения пользователю предлагаются его варианты запуска (через S0427) и ячейку можно закрепить на конкретный вариант.
4. Ярлык выключенной функции ведёт в её настройку, а не «умирает».
5. Сборка с launcher-поверхностью зелёная; новые метки локализованы EN/RU/UK.
