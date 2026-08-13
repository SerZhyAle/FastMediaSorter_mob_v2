# Спецификация (compact bugfix): S1130 - Eviction превью стримов по размеру диска, а не по числу файлов

**Ticket:** S1130
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-20
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-20

**Захвачено во время:** аудит внешнего документа `stream-playback-recommendations.md` (StreamsPlayer), режим «аудит + разложить в тикеты».

**Текст:**

Источник (§6.3): дисковый стор превью должен вытеснять по бюджету суммарного размера диска (в StreamsPlayer - 150 MB), НЕ по фиксированному числу файлов. Документ прямо предупреждает: ранний лимит в 64 ФАЙЛА тихо прятал превью для каталога на 2 300 каналов.

Находка аудита FMS - ровно тот же анти-паттерн, тот же «магический» 64:

- `StreamFramePersistentStore.enforceCap()` удаляет старейшие файлы сверх `MAX_FILES = 64` (`StreamFramePersistentStore.kt:69-76,95`).
- FMS шипит массовый импорт каталогов (`ImportStreamCatalogUseCase`, `ImportStreamPlaylistUseCase`) - каталоги заведомо больше 64 каналов.
- Следствие: на каталоге > 64 захватываемых каналов старейшие превью тихо исчезают независимо от реального занятого места; при следующем показе канал откатывается на favicon/букву.

Для сравнения: memory-LRU (`StreamFrameCache`, `MAX_ENTRIES=64`) - это ок как runtime-кэш; проблема именно в дисковом сторе. Размер кадра сейчас 640×360 @ JPEG q75 (док рекомендует 480×270 @ q70 ≈ 22 KB/плитка) - размер-осознанная политика вытеснения также даёт повод пересмотреть right-sizing.

Не покрыт открытыми тикетами (каталог: frame cache eviction / stream frame - нет записей).

**Вложения:**
- Исходный документ рекомендаций (StreamsPlayer, §6.3) - `PLAN/S1130_bugfix-stream-thumbnail-disk-eviction-size-budget/attachments/stream-playback-recommendations.md`

---

## 1. Проблема / симптом

На каталоге больше 64 захватываемых каналов дисковый стор превью (`StreamFramePersistentStore`) вытесняет старейшие файлы по фиксированному лимиту `MAX_FILES = 64` независимо от реально занятого места - превью тихо исчезают и канал откатывается на favicon. Эвиденс: `StreamFramePersistentStore.kt:69-76,95`.

---

## 2. Корневая причина

`StreamFramePersistentStore.enforceCap()` ограничивал дисковый стор превью фиксированным числом файлов (`MAX_FILES = 64`), выравненным на ёмкость memory-LRU `StreamFrameCache.MAX_ENTRIES`. Для runtime-кэша это уместно, но у дискового холодного слоя иная роль - переживать перезапуск и хранить превью для всего импортированного каталога. На каталоге > 64 захваченных каналов `enforceCap` удалял старейшие по mtime файлы независимо от реально занятого места (несколько сотен КБ против доступных сотен МБ), и канал при следующем показе откатывался на favicon/букву.

---

## 3. Исправление

- `enforceCap` теперь делегирует в `evictToBudget(dir, maxBytes)`: вытеснение старейших по mtime файлов, пока суммарный размер `.jpg` в директории не уложится в бюджет. Число файлов не ограничено.
- Новый бюджет `MAX_DISK_BYTES = 150 MB` - зеркалит дисковый бюджет StreamsPlayer из `stream-playback-recommendations.md` (§6.3). При ~40-50 КБ/плитку это тысячи каналов вместо 64.
- `evictToBudget` вынесен `internal @VisibleForTesting` для управляемого по размеру unit-теста; продакшн зовёт его с `MAX_DISK_BYTES`.
- Right-sizing кадра (640×360 q75 -> рекомендованные 480×270 q70) не входит в этот фикс: разрешение задаётся на стороне захвата (`StreamFrameSnapshotManager`), это отдельная ортогональная оптимизация. Здесь QUALITY=75 без изменений.
- Файл: `StreamFramePersistentStore.kt`. Затронут handoff-док `dev/handoff/streams-source-spec/04_favicon_atlas.md` (описание eviction).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1129 (player-ingest - без размерного бюджета новые превью будут вытеснять старые ещё быстрее)

---

## 4. Проверка

- Unit (`StreamFramePersistentStoreTest`, pure-JVM, mockk Context):
  - 70 плиток (> старого 64-лимита) при малом суммарном размере - не вытеснено ни одной;
  - при превышении бюджета вытесняются старейшие по mtime, пока сумма не уложится; новейшие сохранены;
  - учитываются/удаляются только `.jpg` (посторонний `.tmp` не считается и не удаляется).
- Build: `standard debug` компилируется; `testStandardDebugUnitTest --tests *StreamFramePersistentStoreTest` - PASS.

---

## Last Audit

**Date:** 2026-07-20
**Verdict:** Verified
**Method:** code review + unit test + compile (device-free; data-layer logic fix).

- `StreamFramePersistentStore.enforceCap` delegates to `evictToBudget(dir, MAX_DISK_BYTES = 150 MB)`; eviction is oldest-by-mtime until the total `.jpg` footprint fits the budget. The fixed `MAX_FILES = 64` cap is gone.
- Unit test `StreamFramePersistentStoreTest` (pure-JVM, mockk Context) covers: 70 tiles (> old 64-cap) kept when under budget; oldest-first eviction to budget with the newest retained; non-`.jpg` files ignored.
- Build: `:app_v2:testStandardDebugUnitTest --tests *StreamFramePersistentStoreTest` -> BUILD SUCCESSFUL (1m07s, exit 0). Evidence: `temp/S1130/test.log`.
- Handoff doc `dev/handoff/streams-source-spec/04_favicon_atlas.md` §9.2 updated to the size-budget policy.
- No `Timber.d("S1130:` probes (spec never entered BlockNeedUserTest; verified device-free).

**Residual:** none. Frame right-sizing (480×270 q70) is orthogonal capture-side work, intentionally out of scope.
