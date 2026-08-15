# Спецификация (compact bugfix): S1138 - Плановая SMB-выгрузка: массовые EIO/ENOENT при чтении локальных файлов

**Ticket:** S1138
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-21
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-21

**Текст:**

Автозахват при анализе логов (/log-reader). Плановая операция синхронизации
(ScheduledOp / file-operation-sync, MOVE на SMB) выгружает пачку локальных файлов
`/storage/emulated/0/Download/readchan/hr/jpg/*.jpg` на `smb://192.168.1.112/down`.
Высокая доля неудач: значительная часть файлов не читается - `close failed: EIO
(I/O error)`, `open failed: ENOENT`, либо `Local file does not exist`.

**Метрики (лог `logs/fastmediasorter_20260717_234437.log`):**

- uploadToSmb SUCCESS: 194
- close failed: EIO (I/O error): 196
- Local file does not exist: 65
- Failed to read local file: 31
- ENOENT (No such file or directory): 21
- SLog `file-operation-sync` FAILURE: 97 (против 165 успешных) - ~37% провалов

**Эвиденс:**

```
19:15:15 E/App: uploadToSmb: Local file does not exist: /storage/emulated/0/Download/readchan/hr/jpg/1767918779140261.jpg
19:15:15 E/App: uploadToSmb: Failed to read local file: close failed: EIO (I/O error)
19:15:15 E/App: uploadToSmb: Failed to read local file: /storage/emulated/0/Download/readchan/hr/jpg/1780874757013833.jpg: open failed: ENOENT (No such file or directory)
19:15:15 E/SLog: [a51a46d4|file-operation-sync] FAILURE {error=<не удалось прочитать временный локальный файл; mojibake в логе>}
```

Тот же паттерн в `logs/fastmediasorter_20260720_032131.log` (03:21:43): три
uploadToSmb-ошибки (Local file does not exist + EIO close failed).

Замечания:
- Смесь `Local file does not exist`/`ENOENT` намекает: батч перечисляет файлы,
  но к моменту выгрузки часть уже удалена (внешним приложением-загрузчиком
  readchan или предыдущим MOVE). Приложение должно это переживать без 97 FAILURE.
- `close failed: EIO` на локальном файле (FUSE-путь /storage/emulated/0) -
  вероятна конкурентная модификация/удаление во время чтения либо сбой mount.
- SLog FAILURE-строки в логе в неверной кодировке (mojibake) - отдельный мелкий
  дефект логирования, при желании вынести отдельным тикетом.

---

## 1. Проблема / симптом

Плановая SMB-синхронизация (MOVE) даёт ~37% провалов при чтении исходных
локальных файлов: `close failed: EIO`, `open failed: ENOENT`, `Local file does
not exist`. Файлы, судя по всему, удаляются/меняются внешне между перечислением и
выгрузкой. Операция не обрабатывает пропавшие файлы аккуратно и засоряет лог
десятками FAILURE. Флейвор noLegal, Android 16 / API 36, SM-S731B.

---

## 2. Корневая причина

Два независимых источника «провалов» в `SmbFileOperationHandler.uploadToSmb`
(`data/network/SmbFileOperationHandler.kt`):

1. **Главный (кластер EIO, ~196 строк).** Загрузка шла как
   `inputStream.use { stream -> uploadFile(..) }`. `use` закрывает локальный поток
   **после** возврата блока. Если `uploadFile` уже вернул `Success`, но `close()`
   FUSE-источника (`/storage/emulated/0/..`) бросает `EIO` (файл конкурентно
   удалён/усечён внешним приложением-загрузчиком), исключение из `close()`
   вытесняет уже успешный результат и уходит в `catch` -> `SmbResult.Error`
   («Failed to read local file: close failed: EIO»). Итог: **успешная выгрузка
   помечается FAILURE**. Это раздувает счётчик ошибок плановой синхронизации
   (в логе одновременно «SUCCESS - uploaded» и следом «close failed: EIO»).

2. **Вторичный (`Local file does not exist` / `ENOENT`).** Между перечислением
   батча и выгрузкой внешний производитель удаляет часть источников. Это ожидаемая
   гонка, но логировалась на уровне `Timber.e`, создавая шум ошибок.

---

## 3. Исправление

1. Захватывать результат `uploadFile` и закрывать локальный источник в `finally`,
   который **глотает** `IOException` из `close()` (логирует на `warn`), не давая
   отказу закрытия перекрыть успешную выгрузку. Вынесено в top-level
   `closeLocalSourceQuietly(Closeable)` (тестируемо). Чтение-ошибка в самом
   `uploadFile` по-прежнему пробрасывается в `catch` и даёт `Error` - поведение
   реальных сбоев передачи не меняется.
2. Даунгрейд лога «Local file does not exist» с `Timber.e` на `Timber.w`
   (ожидаемая внешняя гонка, не дефект приложения).

Охват: `uploadToSmb` - общий путь для всех SMB-выгрузок (плановые операции и
ручной перенос). Видео/аудио воспроизведение не затрагивается.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1009 (Approved, scheduled-ops-local-folder-picker) - смежная область плановых операций
- **UI/поведение:** без изменений UI; успешные выгрузки перестают ложно считаться проваленными, лог-шум снижается.
- **Flavor:** src/main - все флейворы.

---

## 4. Проверка

- Unit (device-free): `SmbFileOperationHandlerCloseTest` -
  `testStandardDebugUnitTest` -> 2/2 PASS. Проверяет, что `close()` с `IOException`
  не пробрасывается (не превращает успех в FAILURE) и что нормальный поток
  закрывается.
- Compile: `testStandardDebugUnitTest` (компилирует main+test) - BUILD SUCCESSFUL.

---

## Last Audit

**Date:** 2026-07-21 (via /spec-all)
**Verdict:** Verified

- Root cause confirmed in `SmbFileOperationHandler.uploadToSmb`: `inputStream.use { }` closed the local source after a successful `uploadFile`, and a `close()` EIO propagated and overrode the success -> spurious FAILURE.
- Fix implemented: upload result captured, local source closed via `closeLocalSourceQuietly` in a `finally` that swallows `IOException` (warn-logged). Read failures inside `uploadFile` still propagate to the `catch` -> `Error` (real transfer failures unchanged). Missing-source log downgraded `Timber.e` -> `Timber.w`.
- Evidence:
  - Unit: `SmbFileOperationHandlerCloseTest` (`testStandardDebugUnitTest`) -> tests=2 failures=0 errors=0. Locks the invariant that a `close()` `IOException` is swallowed, not propagated.
  - Compile: `a.ps1 fk` (standard) -> BUILD SUCCESSFUL.
  - detekt: scoped gate PASS, 0 findings in the changed file (also cleared pre-existing `LongParameterList`/`ArgumentListWrapping`/`ImportOrdering` surfaced in the touched file, per Rule 7).
- Scope note: the genuine `Local file does not exist` / `ENOENT` cases (source deleted externally between enumeration and upload) remain legitimate `Error` returns - the app cannot upload a vanished file - but no longer log at error level. Only the spurious EIO-close-overriding-success failures are eliminated.
