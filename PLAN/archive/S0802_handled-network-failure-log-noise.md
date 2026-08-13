# Стратегическая спецификация: S0802 - Нормализовать шум от ожидаемых сетевых отказов в логах

**Ticket:** S0802
**Status:** Archived
**Priority:** 70
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29
<!-- auto-approved by /spec-all - 2026-06-29 -->
**Tactical spec:** `PLAN/S0802_handled-network-failure-log-noise/`
**Tactical plan:** `PLAN/S0802_handled-network-failure-log-noise/INDEX.md`
**Implemented date:** 2026-06-29

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec
- **Goal / expected outcome:** Delegated by user - выделить отдельным тикетом нормализацию повторяющегося лог-шума, который маскирует реальные падения в приложенных сессиях
- **Local anchor:** Provided by user - `fastmediasorter_20260627_092657.log`, `fastmediasorter_20260628_033957.log`, `fastmediasorter_20260629_005853.log`; повторяющиеся handled-failure блоки в интервалах `2026-06-27 19:48:50`, `2026-06-28 03:42:39` / `21:15:33`, `2026-06-29 01:05:43` .. `02:04:56`
- **Scope boundaries / forbidden areas:** Delegated by user - нормализация диагностики для уже обработанных stream preview fallback и Wi-Fi-gated SMB background checks; без изменения каталога стримов, сетевых политик доступа и без попытки "починить интернет"
- **Done / success signal:** Delegated by user - ожидаемые внешние отказы и отмены больше не порождают exception-блоки и E-level шум, а реальные транспортные дефекты остаются отчётливо видимыми
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions
- **UI decisions / delegation:** N/A, если правка останется внутри логирования и служебной классификации; при изменении пользовательских сообщений решение выносится в tactic

`Approved` is blocked while any mandatory line in this section contains `MISSING - requires owner input`.

---

## 1. Проблема

Предоставленные сессии переполнены длинными exception-блоками от уже обработанных внешних сбоёв: недоступные live-stream URL, таймауты первых кадров, отмены задач на переработанных ячейках и SMB-проверки, заранее отклонённые Wi-Fi-политикой. Эти события в текущем виде визуально неотличимы от настоящих дефектов приложения и размывают расследование реального крэша, который находится в тех же логах.

---

## 2. Цели

1. Отделить ожидаемые control-flow fallback и policy-skip события от настоящих транспортных ошибок приложения.
2. Убрать stacktrace-шум из сценариев, где UI уже умеет корректно показать favicon, пропуск или понятное сетевое ограничение.
3. Сохранить диагностический сигнал для неожиданных transport/runtime failures и для реальных деградаций, требующих исправления.

**Non-goals:**

- Не исправлять неработающие внешние stream URL и не обещать доступность сторонних серверов.
- Не ослаблять существующее требование Wi-Fi для ограниченных сетевых операций.
- Не менять бизнес-логику импорта, сортировки или воспроизведения стримов вне области диагностики и классификации исходов.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Экспортированный debug-log должен оставаться пригодным для быстрого поиска реальных регрессий и крэшей.
2. Поведение fallback для стримов и фоновых SMB-задач должно оставаться понятным пользователю даже после снижения лог-шума.

### 3.2 Жёсткие ограничения

- **Flavor:** flavor-ветки с интернет-стримами и SMB/network operations; подтверждённые логи получены в `noLegal` debug, решение не должно ломать остальные streaming/network-capable flavors
- **API level:** без API-специфики, кроме уже существующих transport/media ограничений
- **Wear OS:** не затрагивается
- **Производительность:** уменьшение объёма логирования не должно сопровождаться более тяжёлой диагностикой или дополнительными сетевыми попытками
- **Совместимость данных:** миграций данных нет
- **Локализация:** если будут затронуты пользовательские сообщения, обязателен обычный EN/RU/UK поток и соответствие `docs/COMMUNICATION_POLICY.md`
- **Доступность:** пользовательские fallback-состояния должны сохранить нынешнюю понятность без опоры только на цвет или скрытый лог

### 3.3 Owner inputs (Approval gate)

- **Validation level:** статическая проверка + compile proof достаточно для шага нормализации логирования; отдельный device-gate нужен только если реализация внезапно затронет пользовательские тексты или runtime-поведение beyond logging
- **Owner sign-off:** 2026-06-29 (delegated by user via `/spec-next`)
- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

Подсистема интернет-стримов уже умеет деградировать gracefully: если предварительный кадр не получен, список возвращается к favicon или placeholder и продолжает работать. Аналогично фоновые сетевые операции заранее знают про отсутствие Wi-Fi и способны объяснить ограничение без бессмысленной socket-попытки.

Проблема в том, что эти уже обработанные исходы всё ещё протекают в единый error-канал логирования с throwable-стеками и E/W-сигналами, как будто это внутренний дефект приложения. В результате контрольный поток "мы корректно откатились" и настоящий сбой "у нас сломалась логика" смешиваются в одну телеметрию.

---

## 5. Предлагаемый подход

Нужно ввести явную таксономию исходов для сетевых preview/background сценариев: успешное действие, ожидаемый fallback, ожидаемый policy-skip и настоящий дефект. Обработанные fallback/policy-skip случаи должны логироваться кратко и однообразно, без stacktrace-шума, а расширенный throwable-сигнал нужно оставить только для неожиданных или ещё не классифицированных отказов.

### 5.1 Основные столпы / модули

1. **Outcome taxonomy for handled network paths**
   - Отдельные статусы для preview timeout, recycled cancel, fallback-to-favicon, Wi-Fi policy skip и unexpected transport failure.
2. **Severity normalization**
   - Ожидаемые ветки остаются видимыми, но не поднимаются до E-level и не засоряют экспорт full stacktrace блоками.
3. **Preserved defect signal**
   - Непредвидённые transport/runtime проблемы по-прежнему сохраняют достаточную детализацию для дебага и triage.

### 5.2 Потоки данных и событий

`stream preview request` -> `short-lived probe` -> `frame received` или `handled fallback` -> `compact diagnostic outcome`

`scheduled remote check` -> `network policy preflight` -> `expected skip or allowed transport attempt` -> `policy result or real transport error`

`unexpected transport/runtime failure` -> `explicit error path` -> `full diagnostic signal for investigation`

### 5.3 Точки расширяемости

- Единая классификация должна быть пригодна и для других сетевых протоколов, где бизнес-логика заранее знает про допустимый fallback.
- Формат исходов должен позволять позже собрать counters/summary по причинам отказов без повторной переработки логов.

---

## 6. Открытые вопросы / Research items

1. **Граница между warning и info для handled fallback**
   - **Вопрос:** какие причины стоит оставлять в `W`, а какие лучше переводить в `I/D`, чтобы не потерять полезный operational signal
   - **Решение:** гибрид по типу причины. Предварительные policy/preflight skips без socket-attempt (`WifiRequiredException` и тот же класс будущих gate-отказов) переводятся в `I`; handled preview degradation (`timeout`, `null-frame`, `stale-share`, protocol transient с fallback/placeholder) остаётся в `W`, но строго без throwable; `E` сохраняется только для unexpected/unclassified transport/runtime defects.
   - **Почему:** thumbnail path уже показал рабочий паттерн compact warning без stacktrace, а главный remaining gap сидит в worker/use-case catch-all, где policy-gated outcomes всё ещё идут как `Timber.e(e, ..)`.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0802_handled-network-failure-log-noise/research/01__handled-outcome-severity-boundary.md`

2. **Нужен ли сводный счётчик причин вместо повторяемых одиночных строк**
   - **Вопрос:** достаточно ли просто понизить severity, или стоит дополнительно агрегировать повторяющиеся preview/network outcomes
   - **Решение:** в рамках S0802 оставить одиночные structured one-line outcomes и не вводить summary counters.
   - **Почему:** текущая боль вызвана не отсутствием агрегации, а тем, что handled paths пишут throwable/E-level шум. После нормализации severity уже существующий `scope=thumbnail` / `failureClass=..` формат остаётся достаточно grep-friendly, а counters потребуют новый shared-state и flush contract.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0802_handled-network-failure-log-noise/research/02__single-line-vs-summary-counters.md`

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Слишком агрессивное понижение severity скроет реальную сетевую регрессию | Средняя | Настоящий transport bug будет сложнее заметить | Для неожиданных или неклассифицированных причин оставить полный throwable-сигнал и явный error-path |
| Разные подсистемы по-разному трактуют "ожидаемый отказ" | Средняя | Логи останутся непоследовательными, а triage - дорогим | Задать общую outcome-taxonomy и применить её одинаково в preview- и background-сценариях |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES`. Это улучшение наблюдаемости и качества деградации существующих сетевых сценариев, а не новая функция.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Обработанный fallback и policy-skip не считаются дефектом приложения**

- **Решение:** ожидаемые внешние отказы и заранее известные сетевые ограничения логируются как нормализованные outcomes без полного exception-шума
- **Альтернативы:** оставить текущее логирование; понижать severity точечно без общей классификации
- **Почему:** локальные ad-hoc правки не решают корневую путаницу между control-flow и defect-flow

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. Логи screen-preview и background SMB сценариев больше не генерируют повторяющиеся throwable-блоки для уже обработанных fallback/policy outcomes.
2. Ожидаемое отсутствие Wi-Fi в фоновой SMB-задаче не выглядит как полноценный transport error в exported logs.
3. Реальный крэш или неожиданный transport/runtime defect после изменений всё ещё легко отделяется от внешнего сетевого шума в одной и той же сессии.
4. Пользовательский fallback-путь для стримов и сетевых ограничений остаётся корректным и понятным после нормализации логирования.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S0802_handled-network-failure-log-noise/INDEX.md`

Фазы 01-03 завершены 2026-06-29; тикет готов к финальному audit verdict.

## Last Audit

**Date:** 2026-06-29
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 13 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

- [x] Logging-only normalization remains static-audit eligible per strategic §3.3; no device gate required.
