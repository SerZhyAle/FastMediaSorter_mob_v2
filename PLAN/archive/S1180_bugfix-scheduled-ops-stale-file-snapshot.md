# Спецификация (compact bugfix): S1180 - Параллельные scheduled ops с пересекающимися источниками падают на устаревшем снимке файлов

**Ticket:** S1180
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-24
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-24

**Текст:**

Найдено при анализе лога `logs/fastmediasorter_20260723_180744.log` (сборка 2.60.7230.329-NoLegal-DEBUG, SM-S731B, Android 16/API 36).

Две запланированные операции (op=3 и op=4) стартуют одновременно (разница 13 мс) и обе перечисляют пересекающиеся исходные папки (`/storage/emulated/0/Download`, `/storage/emulated/0/DCIM/Camera`). op=3 успешно перемещает 50 файлов на SMB и удаляет локальные оригиналы через MediaStore. op=4 продолжает работать по снимку списка, снятому ДО удаления, и получает 41 ошибку `Local file does not exist`. Пользователь видит текст ошибки «Не получилось переместить выбранные файлы.» для каждого файла.

Лог-эвиденс (verbatim):

```
[1557] 2026-07-23 23:58:00.630 D/App: ScheduledOperationsWorker: starting op=3
[1558] 2026-07-23 23:58:00.643 D/App: ScheduledOperationsWorker: starting op=4
[1766] 2026-07-23 23:58:12.032 D/App: ScheduledOp[3] MOVE OK 751842439_18417423862151553_7885578127358881872_n.webp
[3684] 2026-07-23 23:58:43.947 I/App: ScheduledOperationsWorker: op=3 done - 50 files, errors=0
[3737] 2026-07-23 23:58:45.778 W/App: uploadToSmb: Local file does not exist: /storage/emulated/0/Download/751842439_18417423862151553_7885578127358881872_n.webp
[3738] 2026-07-23 23:58:45.779 I/App: SMB executeMove: Completed loop - successCount=0, errors=1, movedPaths=0
[3739] 2026-07-23 23:58:45.783 E/SLog: [6f71b117|file-operation-sync] FAILURE {error=Не получилось переместить выбранные файлы.
[5659] 2026-07-23 23:58:56.214 I/App: ScheduledOperationsWorker: op=4 done - 59 files, errors=41
```

Ожидаемое поведение: исчезнувший исходный файл - не ошибка перемещения, а пропуск (или перечисление источника должно быть эксклюзивным между одновременно идущими ops).

---

## 1. Проблема / симптом

Одновременный запуск двух scheduled-операций с пересекающимися исходными папками даёт 41 ложную ошибку перемещения за один прогон: второй воркер работает по устаревшему списку файлов, уже перемещённых и удалённых первым. Ошибки видны пользователю как «Не получилось переместить выбранные файлы.» и попадают в счётчик `errors` истории операций.

---

## 2. Корневая причина

Пропавший локальный исходник во время SMB-перемещения трактуется как ошибка, а не как пропуск.

Каждый файл scheduled-операции проходит через `SmbFileOperationHandler.executeMove` (ветка «назначение - SMB»). Для локального источника вызывается `moveLocalToSmb` -> `uploadToSmb`. Если к моменту загрузки файл уже удалён (его переместила и удалила через MediaStore параллельная операция с пересекающейся папкой), `uploadToSmb` находит `!localFile.exists()` и возвращает `SmbResult.Error`. В цикле `executeMove` это становится `MoveOutcome.Failure` и попадает в список `errors`.

Ниже по стеку инфраструктура пропусков уже есть и работает:

- `BaseFileOperationHandler.buildMoveResult` принимает `skippedCount`/`skippedPaths` и считает `totalProcessed = successCount + skippedCount`; при `totalProcessed == sources.size` возвращает `FileOperationResult.Success`, а не `Failure`.
- `ExecuteScheduledOperationUseCase` на per-file `Success` со `skippedCount > 0` пишет `SKIP` и не добавляет ошибку; `errors.isEmpty()` даёт статус `OK`.

Базовый `executeMove` (локальное назначение) этой инфраструктурой пользуется только для `FileExistsException`. SMB-override не пользуется ею вовсе: он зовёт `buildMoveResult(successCount, operation, movedPaths, errors)` без `skippedCount`, поэтому исчезнувший источник у него всегда ошибка. Отсюда 41 ложная ошибка у op=4.

S1138 уже понизил уровень лога этого случая до `warn` («expected race, not an app fault»), но оставил возврат `SmbResult.Error` - то есть починил шум в логе, но не сам ложный подсчёт ошибки и не сообщение пользователю.

---

## 3. Исправление

Провести исчезнувший локальный источник SMB-перемещения через уже существующий механизм пропуска вместо ветки ошибки. Целиком в `SmbFileOperationHandler.kt`:

**3.1** Добавить вариант `MoveOutcome.Skipped(fileName)` в приватный sealed-интерфейс `MoveOutcome`.

**3.2** В начале `moveLocalToSmb`, для не-SAF пути (`!sourcePath.startsWith("content:/")`), если `!File(sourcePath).exists()` - вернуть `MoveOutcome.Skipped(fileName)` до вызова `uploadToSmb`. Это ровно гонка параллельных ops: файл уже уехал в назначение силами первой операции, перемещать нечего.

**3.3** В цикле `executeMove` (ветка SMB-назначения) завести `skippedCount`, обработать `MoveOutcome.Skipped` (инкремент, без добавления в `errors`), и передать его в `buildMoveResult(successCount, operation, movedPaths, errors, skippedCount)`. `skippedPaths` не заводим: use-case (`ExecuteScheduledOperationUseCase`) читает только `skippedCount`, а исчезнувший источник восстанавливать нечем - он уже в назначении силами первой операции.

Тогда per-file исход исчезнувшего источника - `Success(skippedCount=1)`, use-case пишет `SKIP`, ошибка пользователю не показывается, счётчик `errors` операции не растёт.

### 3.1 Вне области

- Не сериализуем сами scheduled-операции и не делаем перечисление источников эксклюзивным между параллельными ops: это крупная правка воркера с рисками для пропускной способности, а пропуск исчезнувшего источника снимает пользовательский симптом полностью и корректно покрывает любого внешнего продюсера, удалившего источник в середине батча (та же мотивация, что у S1138).
- Не трогаем SAF-источники (`content://`): проверка существования там - запрос к `contentResolver`, а воспроизведённый случай - локальные пути `/storage/emulated/0/..`.
- Не трогаем базовый (локальное назначение) `executeMove` и мосты `sftp/ftp -> smb`: у них исчезнувший локальный источник в этот путь не приходит; при появлении такого же симптома - отдельный тикет.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1138

---

## 4. Проверка

- `a.ps1 fk` - BUILD SUCCESSFUL.
- Юнит-тест `SmbFileOperationHandlerMoveSkipTest`: `executeMove` с одним несуществующим локальным источником и SMB-назначением возвращает `FileOperationResult.Success` со `skippedCount == 1`, `errors` пуст, и `smbClient.uploadFile` не вызывается. Полный end-to-end repro (две пересекающиеся scheduled-операции на реальный SMB) на эмуляторе невоспроизводим - SMB-сервера нет; юнит-тест проверяет само решение о пропуске device-независимо.
- `moveLocalToSmb` содержит guard `!sourcePath.startsWith("content:/") && !File(sourcePath).exists()` до `uploadToSmb`, возвращающий `MoveOutcome.Skipped`.
- Цикл SMB-`executeMove` передаёт `skippedCount` в `buildMoveResult`.

---

## Last Audit

**Date:** 2026-07-24
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Static checks

- Build/compile: `:app_v2:testStandardDebugUnitTest --rerun-tasks` - `BUILD SUCCESSFUL`, main standard-debug compiled clean.
- Unit test `SmbFileOperationHandlerMoveSkipTest`: `executeMove` with one non-existent local source and an SMB destination returns `FileOperationResult.Success` with `skippedCount == 1`, `processedCount == 0`, and `smbClient.uploadFile` never called - `BUILD SUCCESSFUL` on `--rerun-tasks`.
- `moveLocalToSmb`: guard `!sourcePath.startsWith("content:/") && !File(sourcePath).exists()` returns `MoveOutcome.Skipped` before any upload; the method keeps 2 returns (upload-fail folded into the `try`).
- `executeMove` SMB branch: `skippedCount` accumulated on `MoveOutcome.Skipped`, passed to `buildMoveResult` (skippedPaths defaulted - use case reads only the count).
- End-to-end trace: `buildMoveResult` treats `success + skipped == sources.size` as `Success`; `ExecuteScheduledOperationUseCase` logs `SKIP` and adds no error for a per-file `Success` with `skippedCount > 0`, so the worker's `errors` count stays 0.
- detekt (diff-scoped, fresh `:app_v2:detekt --rerun-tasks`): `SmbFileOperationHandler.kt` has **zero** findings - the three my first cut introduced (`LongMethod 80/80`, `CyclomaticComplexMethod 21/20`, `MaxLineLength`, `ReturnCount`) were all resolved by keeping the skip inside `moveLocalToSmb` and shortening the completion log.
- Fast static gates (`post-change`): ticket-log, neuroslop, flavor-flag, public-mutable-flow, deprecated-pm, listener-symmetry, fgs-notification - all PASS.
- Debug-tag invariant: no `Timber.d("S1180:` probes - verified by unit test, never entered `BlockNeedUserTest`.

### FEATURES / inventory

- EXEMPT - no new capability: this is a fix to the existing scheduled-operations SMB-move behavior (fewer false "move failed" errors), not a new shippable feature. No `docs/ALL_FEATURES.jsonl` record added.

### Manual / on-device

- [ ] Full end-to-end repro (two overlapping scheduled ops moving to a live SMB share, first deleting originals mid-flight) is not reproducible on the emulator - no SMB server. The skip decision itself is proven device-independently by the unit test; a real-setup spot-check remains optional, not blocking.
