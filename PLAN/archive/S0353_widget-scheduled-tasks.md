# Стратегическая спецификация: S0353 - Виджет задач по расписанию

**Ticket:** S0353
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-04
**Tier:** 3 - Moderate (ad-hoc)
**Parent ticket:** S0348 (home-widget-icon-refresh) - выделено как суб-спецификация по решению владельца 2026-06-04.
**Tactical plan:** `PLAN/S0353_widget-scheduled-tasks/INDEX.md`

<!-- auto-approved by /spec-all - 2026-06-04 -->

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы и архитектурный контекст. Имена классов в §4/§6 даны как факты существующей подсистемы, не как тактический дизайн.

---

## 0. Approval Gate (owner input)

- **Origin:** выделено из S0348 §5.1 (пункт 2.5) и критерия §11.17.
- **Approval signal:** owner запустил `/spec-all S0353` 2026-06-04 - это и есть явный approve суб-спеки; pipeline автоматически промоутит Draft -> Approved.
- **Autonomy:** действует autonomy-rule родителя S0348 §3.3 - агент решает тактические детали с явными допущениями и спрашивает владельца только если реализация иначе станет небезопасной или противоречивой.

---

## 1. Проблема

Запланированные файловые операции (COPY / MOVE / DELETE по интервалу) уже работают как фоновая подсистема, но у пользователя нет быстрого мониторинга и запуска с home screen.

Чтобы узнать статус последнего прогона или запустить задачи вручную, нужно открыть приложение, зайти в Настройки и развернуть секцию «Scheduled». Это медленно для повторяемого ежедневного контроля.

---

## 2. Цели

1. Добавить status/control-виджет `2x1` / `2x2` для запланированных файловых операций.
2. В `2x1`: статус последней завершённой операции (успех / ошибка), время её завершения, число активных (включённых) задач, кнопки «Run All» и «Pause/Resume All».
3. В `2x2`: то же плюс мини-список ближайших задач (время следующего запуска + тип операции).
4. Клик по статусной области ведёт в секцию «Scheduled» настроек операций.
5. Виджет регистрируется в существующем in-app picker (`HomeWidgetCatalog`) и закрепляется через существующий `HomeWidgetPinner`.

**Non-goals:**

- Не мониторить интерактивные (foreground) операции копирования/перемещения из Browse - у них нет durable cross-process snapshot, это отдельная задача (S0348 §6.4, defer).
- Не выполнять деструктивные операции без подтверждения (S0348 §6.3 - reject destructive cleanup actions on widget).
- Не дублировать полный журнал операций (`scheduled_operations_log.txt`) внутри виджета - только последний статус и ближайший список.
- Не строить новую подсистему планирования - она уже существует (см. §4).

---

## 3. Ограничения

- **Scope = только запланированные операции.** Источник состояния - Room-таблица `scheduled_operations`, а не живой прогресс интерактивных операций.
- **Update contract:** обновление виджета по событиям прогона операций / WorkManager-колбэкам, не по `updatePeriodMillis` (период системы ≥ 30 минут не годится для «свежего» статуса).
- **Safety:** «Run All» и «Pause/Resume All» - недеструктивные управляющие действия; определения задач сохраняются, удаление файлов без подтверждения запрещено.
- **Run All cost:** массовый запуск не должен порождать неконтролируемое число одновременных foreground-сервисов (каждая операция исполняется как `dataSync` foreground worker).
- **Pause durability:** «paused»-состояние должно переживать смерть процесса и refresh launcher, иначе кнопка на виджете бессмысленна.
- **Локализация:** EN/RU/UK для всех новых labels, descriptions, accessibility text, picker-preview.
- **Доступность и ввод:** кнопки виджета следуют Rule 17 (TalkBack-имена, touch target); виджет читается в portrait и landscape, текст внутри safe bounds.
- **Flavor isolation:** Rule 15; флаг `ENABLE_SCHEDULED_OPERATIONS` истинен во всех flavors, gating виджета - через манифест (`tools:node="remove"`), не через `BuildConfig` в коде.

---

## 4. Контекст текущей архитектуры

Подсистема запланированных операций существует целиком в `src/main` и поставляется во всех flavors. Deferral-обоснование S0348 §6.3/§6.4 («нет подсистемы планирования, нет durable store, нет журнала») устарело для запланированных операций.

- **Durable state:** Room-таблица `scheduled_operations` (`AppDatabase` v32) хранит на каждую задачу `isEnabled`, `intervalHours/Minutes`, `startTimeHour/Minute`, `lastRunAt`, `lastRunStatus` (`null | "OK" | "ERROR: .."`), `nextRunAt`. Доступна реактивно как `Flow<List<ScheduledOperation>>` через `ScheduledOperationRepository.getAll()`.
- **Планировщик:** `WorkManagerScheduler` (`@Singleton`) - центр; `scheduleOperation/runNow(id)/toggleEnabled/cancelOperation/rescheduleAll/observeAndReschedule`. Self-rescheduling: `ScheduledOperationsWorker` (OneTime, `@HiltWorker`) после прогона пишет `lastRunAt/lastRunStatus/nextRunAt` и переenqueue-ит следующий запуск.
- **Boot/startup survival:** `ScheduledOperationsBootReceiver` на `BOOT_COMPLETED` и `FastMediaSorterApp.onCreate` вызывают `rescheduleAll()`.
- **Журнал:** `scheduled_operations_log.txt` (`filesDir`, ≤ 1 MB / 500 строк) через `AppendToScheduledLogUseCase` / `GetScheduledOperationsLogUseCase`; показывается в `ScheduledLogDialog`.
- **Настройки:** секция «Scheduled» в `OperationsSettingsFragment` (host `SettingsActivity`), gated `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` + `settings.enableScheduledOperations`; ViewModel `ScheduledOperationsViewModel` отдаёт `operations: StateFlow<List<ScheduledOperation>>`.
- **Widget foundation (S0348):** `HomeWidgetCatalog` (реестр+гейтинг), `HomeWidgetEntry` (`settingGate`), `HomeWidgetPinner` (`requestPinAppWidget`, legacy-safe). Богатейший шаблон RemoteViews+список - `FavoritesWidgetProvider` + `FavoritesWidgetService` (`RemoteViewsService` читает Room через Hilt `@EntryPoint`).
- **WorkManager+Hilt:** `FastMediaSorterApp` - `Configuration.Provider` с `HiltWorkerFactory`; новый воркер не нужен (используем существующий через агрегатные команды).

---

## 5. Предлагаемый подход

Виджет - read-mostly snapshot существующей Room-таблицы плюс три управляющих действия. Никакой новой модели данных для отображения не требуется.

### 5.1 Основные столпы / модули

**Status surface (`2x1`)**

- Читает из `scheduled_operations`: число `isEnabled == true`, последний `lastRunAt`/`lastRunStatus` среди всех задач.
- Иконка/цвет отражают OK vs ERROR последней операции; пустое расписание - явный empty-state, а не пустота.
- Кнопки «Run All» и «Pause/Resume All» как `PendingIntent` на provider (broadcast), не как переход в приложение.

**Upcoming list (`2x2`)**

- Переиспользует паттерн `FavoritesWidgetService`: `RemoteViewsService` + Room через Hilt `@EntryPoint`, выборка включённых задач, сортировка по `nextRunAt`, лимит 2-3 строки.
- Каждая строка: тип операции (COPY/MOVE/DELETE) + время следующего запуска.

**Aggregate commands (новое в планировщике)**

- «Run All» - недеструктивный запуск всех включённых задач; исполнение сериализовано/очередью, чтобы не плодить параллельные foreground-сервисы.
- «Pause/Resume All» - durable переключаемое состояние; на paused новые прогоны не стартуют, определения задач сохраняются; состояние переживает смерть процесса.

**Widget refresh hook**

- После каждого прогона (`ScheduledOperationsWorker` / `observeAndReschedule`) и после агрегатных команд виджет получает push-обновление (`notifyAppWidgetViewDataChanged` / `updateAppWidget`), а не ждёт `updatePeriodMillis`.

**Navigation**

- Клик по статусу открывает `SettingsActivity` с маршрутом в `OperationsSettingsFragment` и разворотом секции «Scheduled» (расширить существующий extra-механизм фрагмента).

**Registry + flavor**

- Добавить `HomeWidgetEntry` с `settingGate = { it.enableScheduledOperations }` в `HomeWidgetCatalog`.
- Виджет доступен во всех flavors (флаг истинен везде); при необходимости скрытия - `tools:node="remove"` во flavor-манифесте, без `BuildConfig` в коде виджета.

### 5.2 Точки расширяемости

- Агрегатные команды (`runAllNow`, pause/resume) живут в `WorkManagerScheduler` и переиспользуемы из настроек, не только из виджета.
- Durable «paused»-флаг - кандидат для будущего глобального «scheduler paused» UI в настройках.

---

## 6. Research findings (resolved §4 открытые вопросы)

1. **Источник durable snapshot.**
   - **Вывод:** Room-таблица `scheduled_operations` (per-op `lastRunAt/nextRunAt/lastRunStatus/isEnabled`) + текстовый журнал `scheduled_operations_log.txt`.
   - **Статус:** Resolved - читать из Room через тот же Hilt `@EntryPoint`-паттерн, что и Favorites widget.

2. **Семантика «активных задач» и «ближайших запланированных».**
   - **Вывод:** активные = `isEnabled == true`; ближайшие = включённые задачи, отсортированные по `nextRunAt` (ascending), лимит 2-3.
   - **Подводный камень:** `nextRunAt` пишется только `ScheduledOperationsWorker` после первого прогона; до него он `null`. Новая задача стартует немедленно (delay 0), и до первого прогона «следующий запуск» неизвестен.
   - **Статус:** Resolved - в scope входит инициализация `nextRunAt` из `startTimeHour/Minute` при upsert; для ещё не запущенных без явного времени виджет показывает «soon», а не пусто.

3. **Поведение при пустом расписании.**
   - **Вывод:** явный empty-state по образцу `FavoritesWidgetProvider.setEmptyView`; клик ведёт в секцию «Scheduled», где задачу можно создать.
   - **Статус:** Resolved - do.

4. **Run All - параллельно или последовательно.**
   - **Вывод:** последовательно/очередью, потому что каждая операция - foreground `dataSync` worker; массовый параллельный старт - плохой ресурсный профиль.
   - **Статус:** Resolved - serialized aggregate run.

5. **Pause/Resume - механизм.**
   - **Вывод:** durable состояние, переживающее процесс. Конкретный механизм (глобальный «scheduler paused» флаг против bulk-`isEnabled`) - тактическое решение; стратегически требуется только durability + reversibility + сохранение определений.
   - **Статус:** Resolved at strategic level - детали в `/spec-tech`.

6. **Триггер обновления виджета.**
   - **Вывод:** push после каждого прогона и после агрегатных команд; `updatePeriodMillis` (≥ 30 мин) недостаточен.
   - **Статус:** Resolved - добавить refresh-hook.

7. **Flavor exposure.**
   - **Вывод:** во всех flavors (`ENABLE_SCHEDULED_OPERATIONS` истинен везде); gating - через манифест при необходимости.
   - **Статус:** Resolved - do, all flavors.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| `nextRunAt` пуст до первого прогона | Высокая | `2x2` показывает «next run» неизвестным | Инициализировать `nextRunAt` при upsert из `startTimeHour/Minute`; иначе рендерить «soon» |
| Run All плодит параллельные foreground-сервисы | Средняя | Скачок нагрузки, перегрев, возможный системный троттлинг | Сериализовать прогон очередью |
| «Paused» не переживает смерть процесса | Средняя | Кнопка на виджете бессмысленна после refresh launcher | Durable-флаг в БД/настройках, читается воркером перед стартом |
| Виджет не обновляется сразу после прогона | Средняя | Пользователь видит устаревший статус | Push-refresh hook в воркере и агрегатных командах |
| `RemoteViewsFactory` блокирует binder-поток на Room | Низкая | ANR в процессе сервиса при большой выборке | Лимитировать выборку (2-3 строки), как Favorites |
| Launcher по-разному рисует `2x1`/`2x2` | Средняя | Обрезка текста/кнопок | Проверить sizing на стандартном launcher и устройстве владельца; держать текст в safe bounds |
| Виджет показан во flavor без нужного media-type | Низкая | `fileTypeMask` ничего не матчит, «пустые» прогоны | Виджет показывает реальный статус задач; type-filter - ответственность самой задачи |

---

## 8. Влияние на пользователя (docs/FEATURES)

После реализации обновить `docs/FEATURES.md` + `_RU` + `_UK`: Smart Widgets получают виджет «Задачи по расписанию» (`2x1`/`2x2`) со статусом последней операции, числом активных задач, списком ближайших и кнопками запуска/паузы.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Виджет читает существующий Room-store, новой модели отображения нет**

- **Решение:** источник истины - таблица `scheduled_operations`; виджет - её snapshot.
- **Альтернативы:** отдельный widget-snapshot store; парсинг текстового журнала.
- **Почему:** durable per-op состояние уже есть и реактивно; дублирование добавит рассинхрон.

**ADR-2: Scope - только запланированные операции**

- **Решение:** виджет покрывает scheduled COPY/MOVE/DELETE, не интерактивные Browse-операции.
- **Альтернативы:** включить foreground-прогресс копирования/перемещения.
- **Почему:** для интерактивных операций нет durable cross-process snapshot (S0348 §6.4); это отдельная крупная задача.

**ADR-3: Управляющие действия недеструктивны и сериализованы**

- **Решение:** «Run All» исполняется очередью; «Pause/Resume All» сохраняет определения и durable.
- **Альтернативы:** массовый параллельный запуск; пауза через cancel-workers без сохранения состояния.
- **Почему:** параллельные foreground-сервисы - плохой ресурсный профиль; cancel-only «пауза» не переживает процесс.

**ADR-4: Обновление по событиям, не по периоду**

- **Решение:** push-refresh после прогонов и команд.
- **Альтернативы:** полагаться на `updatePeriodMillis`.
- **Почему:** системный период ≥ 30 мин не даёт «свежего» статуса.

---

## 10. Связи с другими спеками

- **S0348** - parent; widget foundation (`HomeWidgetCatalog`, `HomeWidgetEntry`, `HomeWidgetPinner`), picker, pinning, deferral-rationale (§6.3/§6.4), который эта спека пересматривает для запланированных операций.
- **S0349 / S0350 / S0351 / S0352** - sibling суб-спеки новых виджетов из той же волны S0348.

---

## 11. Критерии готовности (strategic-level)

1. Виджет «Задачи по расписанию» добавляется в размерах `2x1` и `2x2` и виден в in-app picker, когда `enableScheduledOperations` включён.
2. `2x1` показывает число активных задач, статус и время последней операции, кнопки «Run All» и «Pause/Resume All».
3. `2x2` дополнительно показывает мини-список ближайших задач (тип + время следующего запуска), лимит 2-3.
4. «Run All» запускает все включённые задачи недеструктивно и сериализованно.
5. «Pause/Resume All» переключает durable-состояние, переживающее смерть процесса, сохраняя определения задач.
6. Виджет обновляется сразу после прогона задачи и после агрегатных команд, а не только по периоду.
7. Клик по статусу открывает секцию «Scheduled» настроек операций.
8. Пустое расписание показывает явный empty-state и ведёт в настройки для создания задачи.
9. Новые строки локализованы EN/RU/UK; кнопки доступны для touch/keyboard/D-pad/mouse и читаются в portrait/landscape внутри safe bounds.

---

### 3.3 Owner inputs (Approval gate)

- **UI placement:** виджет `2x1` (status+controls) и `2x2` (то же + upcoming list); клик по статусу ведёт в секцию «Scheduled» настроек; кнопки «Run All» и «Pause/Resume All» с TalkBack-именами - согласовано из целей §2 владельца.
- **Data/durability:** виджет читает Room-таблицу `scheduled_operations`; «paused» хранится durable; `nextRunAt` инициализируется при upsert - решено в рамках autonomy-rule, безопасно и обратимо.
- **Flavor exposure:** виджет доступен во всех flavors (`ENABLE_SCHEDULED_OPERATIONS` истинен везде); скрытие при необходимости через манифест, без `BuildConfig` в коде виджета.
- **Autonomy:** действует autonomy-rule S0348 §3.3 - тактические детали решаются с явными допущениями.
- **Related tickets:** S0348, S0349, S0350, S0351, S0352.

## Last Audit

### Manual / on-device verification - 2026-06-17

- **Device:** emulator-5554 (Pixel 4, Android 17 / SDK 37), flavor `standard`, package `com.sza.fastmediasorter.debug`.
- **Method:** `/spec-test-device` via mobile-mcp drive + app file-log harvest. Pre-seeded one scheduled Copy operation (All Files -> Downloads, every 24h) through Management > Scheduled operations.
- **Verdict:** PASS (core flows).
- Evidence: `temp/S0353_devtest/probes.log`, `temp/S0353_devtest/07_deeplink_scheduled_unfolded.png`.

- **#1 Picker + pin (PASS).** "Scheduled Tasks" appears in the in-app picker when `enableScheduledOperations` is on. Selecting it raised the Pixel launcher pin dialog ("Scheduled Tasks", 3x1, "Status and controls for scheduled file operations"); "Add to home screen" pinned widget id 4. Render probe fired on placement: `S0353: scheduled-tasks widget rendered` at 11:16:15.146 right after `AppWidgetServiceImpl: Bound widget 4`.
- **#2 Status row (PASS).** 3x1 widget shows "1 active" (matches the one enabled op). Before any run the last-status read "-"; after a run it updated to "Last: OK 11:17 AM".
- **#3 Run All (PASS).** Expected: tap logs `S0353: widget Run All tapped` + widget refreshes. Actual: probe at 11:17:15.947, two re-renders, `ScheduledOperationsWorker: S0353: scheduled-tasks widget refreshed after run`, `WM-WorkerWrapper: Worker result SUCCESS [..sched_op_run_all]`.
- **#4/#5 Pause/Resume + durability (PASS).** Pause logged `S0353: widget Pause/Resume tapped` + `WorkManagerScheduler: pauseAll - scheduler paused, workers cancelled`; toggle label changed to "Resume All". A subsequent tap read `paused==true` from the persisted store and called `resumeAll - scheduler resumed, operations rescheduled`, proving durability. DataStore key `scheduled_operations_paused` present in `settings.preferences_pb`.
- **#6 Event-driven refresh (PASS).** Status went "-" -> "Last: OK 11:17 AM" immediately after the run, driven by the worker refresh hook, not a period tick.
- **#7 Deep-link (PASS).** Tapping the status area opened `SettingsActivity` on the Management tab with the "Scheduled operations by schedule" section unfolded; probe `OperationsSettingsFragment: S0353: settings opened to scheduled section from widget` at 11:21:23.694.

- **Not exercised on this launcher (INCONCLUSIVE, no failure observed):**
  - **#3/#5 2x2 upcoming list + empty-state rows.** The pin-confirmation path only offers the 3x1 default; resizing to 2x2 needs a launcher drag/resize gesture that is not automatable via mobile-mcp here. The 2x2 list service (`ScheduledTasksWidgetService`) was instantiated on every placement/refresh, so the adapter is wired, but the 3-row upcoming render and its "-" empty-state were not visually confirmed.
  - **#7 Portrait/landscape + TalkBack.** Verified portrait only; landscape safe-bounds and TalkBack label readout were not driven.

### Static corroboration of un-exercised items - 2026-06-17 (`/spec-check`)

The two INCONCLUSIVE items above are statically confirmed against the source; no runtime gesture is needed to close them.

- **2x2 upcoming list + adapter (criteria #3).** `res/layout/widget_scheduled_tasks.xml` carries `ListView@widget_scheduled_list` (`layout_weight=1`, grows to fill the tall 2x2 cell, degrades at 2x1) plus the `widget_scheduled_tasks_item.xml` row template. `ScheduledTasksWidgetProvider.updateAppWidget` wires it: `setRemoteAdapter`, `setEmptyView`, `setPendingIntentTemplate`, and `notifyAppWidgetViewDataChanged`. `ScheduledTasksRemoteViewsFactory.loadUpcoming` reads `getUpcomingEnabled().take(3)` - the spec's max-3-rows limit. `widget_scheduled_tasks_info.xml` declares `resizeMode="horizontal|vertical"` (2x1 -> 2x2 resize allowed).
- **Empty-state "-" (criteria #3/#8).** `setEmptyView` points the empty `ListView` at `widget_scheduled_empty` (`@string/widget_scheduled_empty`); per-row null `nextRunAt` renders the em-dash fallback in `getViewAt`.
- **contentDescription / TalkBack (criteria #9).** Both control buttons in `widget_scheduled_tasks.xml` carry `contentDescription` (`widget_scheduled_run_all`, `widget_scheduled_pause_all`) plus `focusable`/`clickable`; buttons also expose visible text labels. Status TextViews are read by TalkBack via their dynamic text. All 10 `widget_scheduled_*` strings present in EN/RU/UK (`check_strings_localized.ps1` PASS).
- **Landscape-safe (criteria #9).** Single resilient RemoteViews layout: `match_parent`/`wrap_content` with weighted rows, no fixed-pixel dimensions, no orientation-specific clipping; widgets reflow in the launcher regardless of device orientation, so no `layout-land/` mirror is required for RemoteViews. Colors via `@color/white` (no hardcoded hex).

- **Verdict: Verified.** All 9 strategic criteria are either drivable-PASS on device or statically corroborated. No failures observed; nothing left neither exercised nor corroborable. `BlockNeedUserTest` debug probes removed from `ScheduledTasksWidgetProvider.kt` (3 lines).

## Revision History

- **2026-06-04** - by `/spec-all` (F1: strategic refinement + auto-approve)
  - Incorporated read-only architecture research: scheduling subsystem, Room store, journal, widget foundation all already exist; S0348 §6.3/§6.4 deferral superseded for scheduled operations.
  - Resolved §4 open questions into §6 (durable source, active/upcoming semantics, empty state, Run All serialization, Pause durability, refresh trigger, flavor exposure).
  - Added §5 approach, §7 risks, §9 ADRs, §11 strategic criteria, §3.3 Owner inputs.
  - Status Draft -> Approved.
