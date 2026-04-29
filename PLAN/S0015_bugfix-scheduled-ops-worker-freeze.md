# Стратегическая спецификация: S0015 — Заморозка приложения после выполнения фоновых плановых операций

**Ticket:** S0015
**Status:** Verified
<!-- auto-approved by /spec-all — 2026-04-28 -->
**Date:** 2026-04-28
**Tier:** 1 — Quick Win (ad-hoc)
**Roadmap entry:** Ad-hoc — обнаружено в логах 2026-04-27
**Tactical spec:** `PLAN/S0015_bugfix-scheduled-ops-worker-freeze/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Плановый фоновый воркер файловых операций пытается перейти в foreground-режим и получает `SecurityException: WAKE_LOCK` — несмотря на то что разрешение объявлено в манифесте. Воркер перехватывает ошибку как non-fatal и продолжает работу, однако внутренняя машина состояний WorkManager остаётся в нестабильном положении: Processor удерживает блокировку в состоянии RUNNING, пока новый запрос с политикой `REPLACE` пытается отменить предыдущую работу и задействовать внутренний wake lock. Это приводит к тому, что после завершения воркера приложение зависает и перестаёт реагировать на действия пользователя.

---

## 2. Цели

1. Плановый фоновый воркер корректно запускается, выполняется и завершается без `SecurityException`, независимо от версии Android или окружения (эмулятор / реальное устройство, API 27–35).
2. После завершения воркера и перепланирования следующего запуска приложение остаётся отзывчивым.
3. Политика повторного планирования (`REPLACE`) не вызывает дедлока во внутренних механизмах WorkManager.
4. Foreground-уведомление воркера отображается корректно там, где это технически возможно (API 29+); на API 27–28 допускается запуск без foreground-продвижения.

**Non-goals:**
- Изменение логики самих операций (копирование, перемещение файлов) — это отдельная область.
- Миграция механизма планирования на AlarmManager или другой бекенд.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Воркер должен журналировать предупреждение (не ошибку) если foreground-продвижение недоступно, не прерывая выполнение.
2. Повторное планирование следующего запуска желательно выполнять после того, как воркер завершил работу и снял все внутренние блокировки, а не изнутри `doWork`.

### 3.2 Жёсткие ограничения

- **Flavor:** standard, lite, legacy, photos — все варианты сборки.
- **API level:** minSdk 26; особое внимание API 27–28 (эмулятор API 27 — основной воспроизводимый стенд).
- **Wear OS:** не затрагивается.
- **Производительность:** воркер запускается с задержкой и не должен держать основной поток.
- **Совместимость данных:** Room-схема не меняется. Состояние планировщика в БД сохраняется.
- **Локализация:** строки уведомления — EN/RU/UK; технические изменения не требуют новых строк.
- **Доступность:** не затрагивается.

---

## 4. Контекст текущей архитектуры

Фоновый воркер запускается через WorkManager с политикой `REPLACE` (один экземпляр на операцию). В начале `doWork` воркер пытается перейти в foreground-режим через системный механизм `setForeground`. Если переход не удаётся, исключение перехватывается и воркер продолжает работу в background-режиме. По окончании вся информация о результате записывается в Room, после чего воркер сам запрашивает перепланирование следующего запуска — то есть вызывает `WorkManager.enqueueUniqueWork` с `REPLACE` изнутри `doWork`. Этот вызов происходит до того, как WorkManager успевает корректно завершить текущий WorkSpec (foreground-блокировка Processor ещё не снята), что порождает состояние гонки при переходе RUNNING → SUCCEEDED.

---

## 5. Предлагаемый подход

Исправление состоит из двух независимых частей: устранение причины `SecurityException` и разрыв гонки при перепланировании.

### 5.1 Устранение SecurityException при foreground-переходе

Выяснить точную причину: разрешение объявлено в манифесте, но ОС его не признаёт. Варианты: неверно указан тип foreground-сервиса для данной версии API, промежуточная версия WorkManager требует дополнительного разрешения `FOREGROUND_SERVICE_DATA_SYNC` отдельно от `WAKE_LOCK`, или ОС API 27 не признаёт разрешение без явного `<service>`-элемента в манифесте с флагом `android:foregroundServiceType`. После установления причины — либо добавить недостающее объявление, либо сделать попытку foreground-перехода условной по API-уровню.

### 5.2 Разрыв гонки при перепланировании

Перепланирование следующего запуска вынести из `doWork` и перевести на механизм, который срабатывает после того, как WorkManager полностью завершил текущий WorkSpec. Подходящий путь — `WorkManager.getWorkInfoByIdFlow` с ожиданием состояния `SUCCEEDED`, запущенный из ViewModel или Application-уровня, либо использование `PeriodicWorkRequest` вместо ручного перепланирования `OneTimeWorkRequest` (если интервалы позволяют).

### 5.3 Потоки данных и событий

```
[Trigger: scheduled time elapsed]
    → WorkManager enqueues ScheduledOperationsWorker
    → Worker: setForeground (non-blocking, catches error)
    → Worker: executeScheduledOperationUseCase
    → Worker: persist result to DB
    → Worker: Result.success()
    → WorkManager: WorkSpec → SUCCEEDED
    → [Rescheduler listens for SUCCEEDED] → enqueueUniqueWork (REPLACE)
    → Room DB Flow → ScheduledOperationsViewModel → UI refresh
```

### 5.4 Точки расширяемости

- Foreground-переход должен быть заменяемым (null-объект, если недоступен).
- Источник перепланирования должен быть единственным (не дублироваться в воркере и во ViewModel).

---

## 6. Открытые вопросы / Research items

1. **Почему WAKE_LOCK SecurityException, если разрешение в манифесте?**
   - **Статус:** Resolved (code inspection 2026-04-28)
   - **Вывод:** Манифест содержит `<service android:name="androidx.work.impl.foreground.SystemForegroundService" android:foregroundServiceType="mediaPlayback|dataSync">` и все разрешения (`WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`). API-guard в `createForegroundInfo()` корректен (API 29+ → DATA_SYNC type, иначе — без типа). SecurityException вызывается не нехваткой разрешений, а гонкой: `enqueueUniqueWork(REPLACE, "sched_op_$id")` внутри `doWork()` заставляет WorkManager 2.9.0 отменить текущую работу (самого себя). В момент отмены `SystemForegroundService` удерживает wake lock через foreground notification; Processor пытается освободить его для отменяемой работы, а одновременно открывает новую — конфликт состояний wake lock и выброс SecurityException. Устранение гонки (выносом перепланирования за doWork) устраняет и SecurityException.

2. **Гонка doWork ↔ reschedule: правда ли она есть?**
   - **Статус:** Resolved (code inspection 2026-04-28)
   - **Вывод:** Гонка подтверждена. `ScheduledOperationsWorker.doWork()` строки 67–68: `scheduledOperationRepository.update(updated)` → `workManagerScheduler.scheduleOperation(updated)` → `enqueueUniqueWork("sched_op_$id", REPLACE, ...)` → `Result.success()` строка 72. WorkManager 2.9.0 с политикой `REPLACE` при получении нового запроса с тем же именем отменяет текущую работу (в т.ч. RUNNING). Отмена выполняется до того, как Processor зафиксировал SUCCEEDED, поэтому foreground-блокировка Processor не снимается штатно: работа переходит в CANCELLED вместо SUCCEEDED, а UI-поток блокируется на ожидании синхронизации внутреннего состояния WorkManager.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Переход на `PeriodicWorkRequest` ломает логику кастомного интервала (intervalHours/Minutes из БД) | Средняя | Операции запускаются не в нужное время | Остаться на `OneTimeWorkRequest`, только вынести перепланирование |
| Ручная проверка API-уровня для `ForegroundInfo` пропускает промежуточные версии API | Низкая | foreground-переход молча падает на новых API | Покрыть unit-тестом с моком |
| Исправление WAKE_LOCK не устраняет заморозку (другая причина) | Средняя | Заморозка воспроизводится после фикса | Провести трассировку `ScheduledOperationsWorker` на эмуляторе с Android Studio Profiler |

---

## 8. Влияние на пользователя (docs/FEATURES)

Функция планирования фоновых файловых операций уже описана в `docs/FEATURES.md`. После исправления добавится заметка о надёжности: плановые операции завершаются без зависания приложения на всех поддерживаемых версиях Android. Без изменений в docs/FEATURES — это bugfix существующей функции.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Перепланирование вынести за пределы doWork**
- **Решение:** следующий запуск планируется после того, как WorkManager подтвердил состояние SUCCEEDED (через Flow или BootReceiver-уровень), а не из тела `doWork`.
- **Альтернативы:** оставить перепланирование в `doWork` и добавить задержку; переключиться на `PeriodicWorkRequest`.
- **Почему:** `PeriodicWorkRequest` не поддерживает переменный интервал из БД. Задержка — ненадёжный костыль. Вынесение — единственный способ гарантировать отсутствие гонки.

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. На эмуляторе API 27 плановая операция завершается без `SecurityException` в logcat.
2. После завершения операции приложение остаётся отзывчивым: касания обрабатываются, навигация работает.
3. Следующий запуск корректно планируется с нужной задержкой (проверяется по логу WorkManager).
4. Тест на эмуляторе API 27 и реальном устройстве API 33+ — оба проходят без ANR.

---

## 12. Implementation Phases

### Phase 1 — Remove self-rescheduling from ScheduledOperationsWorker

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/ScheduledOperationsWorker.kt`

1. Remove `workManagerScheduler: WorkManagerScheduler` from the `@AssistedInject` constructor parameters.
2. Delete the `workManagerScheduler.scheduleOperation(updated)` call inside `doWork()` (currently line 68). Keep `scheduledOperationRepository.update(updated)` — DB state must still be written before returning.
3. Remove the now-unused `WorkManagerScheduler` import.

**Verification:**

- `ScheduledOperationsWorker.kt` contains no reference to `WorkManagerScheduler`.
- `doWork()` still calls `scheduledOperationRepository.update(updated)` before `Result.success()`.

---

### Phase 2 — Add post-SUCCEEDED observer in WorkManagerScheduler

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt`

1. Add class-level field: `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
2. Extract private helper `observeAndReschedule(requestId: UUID, operationId: Long)` that:
   - Observes `WorkManager.getInstance(context).getWorkInfoByIdFlow(requestId)` until `state.isFinished`.
   - On `SUCCEEDED`: fetches fresh `ScheduledOperation` from repository by `operationId`; if non-null and enabled, calls `scheduleOperation(it)`.
   - On any other terminal state: logs `Timber.w("... finished with state=... — skipping reschedule")`.
   - Wraps in try/catch → `Timber.e(...)` on exception.
3. Call `observeAndReschedule(request.id, operation.id)` at the end of `scheduleOperation()`, after `enqueueUniqueWork()`.
4. Call `observeAndReschedule(request.id, operationId)` at the end of `runNow()`, after `enqueueUniqueWork()`.
5. Add required imports: `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.SupervisorJob`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.flow.filter`, `kotlinx.coroutines.flow.first`, `androidx.work.WorkInfo`, `java.util.UUID`.

**Verification:**

- `WorkManagerScheduler.kt` declares `private val scope`.
- `scheduleOperation()` and `runNow()` both call `observeAndReschedule(...)`.
- `observeAndReschedule` is a private fun, not exposed in the public API.
- No reference to `WorkManagerScheduler` remains in `ScheduledOperationsWorker`.
