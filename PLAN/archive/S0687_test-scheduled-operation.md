# Спецификация: S0687 - Проверка запланированных операций (device verification)

**Ticket:** S0687
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-25
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-25

> **Scope:** VERIFICATION. Тикет не несёт изменений кода - это on-device проверка существующей фичи запланированных операций. Захваченная идея: "test the schedulled operation".

---

## 1. Цель

Подтвердить, что фича запланированных операций (Scheduled operations) работает end-to-end на устройстве: создание операции в UI, постановка в WorkManager, форсированный запуск (Run now), исполнение worker'ом через `ExecuteScheduledOperationUseCase`, реальная файловая операция, обновление статуса в БД и журнале операций.

Фича существует и не дорабатывалась в рамках тикета - проверяется только её фактическая работоспособность.

## 2. Что проверено

Сборка `standard debug` v2.60.6241.447, эмулятор `emulator-5556` (Android), пакет `com.sza.fastmediasorter.debug`.

Сценарий (DELETE выбран как наименее зависящий от настройки: нужен только source, без destination):

1. Settings -> Management -> "Scheduled operations by schedule" - секция доступна.
2. Тоггл "Use scheduled operations" включается; появляются действия Add / Log / Clear all.
3. Диалог "Add" рисует все поля: Source, операция, Destination (автозаполнение единственным destination "Downloads"), Conditions (свёрнуто), Starting at, Schedule, превью "Next run at", Suppress notifications.
4. Превью следующего запуска вычисляется корректно: при старте 13:56 (уже прошёл) и интервале 24ч показано "Next run at: 26-06-26 13:56" (якорь следующего дня).
5. Условный UI: при выборе операции Delete поле Destination скрывается.
6. Операция сохраняется и рисуется карточкой: "Downloads · At 13:56 · Every 24h · All files" с кнопками Run now / Edit / Delete.
7. Run now запускает worker немедленно.

Подготовка данных: в `/sdcard/Download/` (ресурс "Downloads") и его подпапку `sched_src/` положены по 3 одинаковых jpg.

## 3. Доказательства

Logcat (`ScheduledOperationsWorker` + `ExecuteScheduledOperationUseCase`):

- `ScheduledOperationsWorker: starting op=1`
- `ExecuteScheduledOperationUseCase: ScheduledOp[1] fired: DELETE`
- по `ScheduledOp[1] DELETE OK <file>.jpg` на каждый удалённый файл
- `ScheduledOperationsWorker: op=1 done - 6 files, errors=0`
- `WM-WorkerWrapper: Worker result SUCCESS for Work [.. sched_op_1]`

Файловая система (`adb ls`): все jpg в корне `Download` и в `sched_src/` удалены (реальная операция, не no-op).

Журнал операций (UI, кнопка Log): 6 строк `DELETE | Downloads [LOCAL] -> - | OK: <file>` плюс итог `OK (6 files)`.

## 4. Наблюдение: "6 files" при 3 в корне

Worker удалил 6 файлов, хотя в корне `Download` лежало 3. Подтверждено `adb ls`: удаление рекурсивное - снялись 3 файла из корня и 3 из подпапки `sched_src/` (одинаковые имена -> каждое имя в логе дважды). `errors=0`, поведение корректное, не дефект.

## 5. Вердикт

PASS. Фича запланированных операций работает end-to-end на эмуляторе: UI -> сохранение -> WorkManager -> worker -> use case -> реальная файловая операция -> журнал. Дефектов не выявлено.

Тестовая операция и тестовые файлы удалены после прогона.

## 6. Связи

Связей нет.
