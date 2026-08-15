# Спецификация (fix): S0729 - Гонки на разделяемых кэшах (MediaFiles/Translation)

**Ticket:** S0729
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0716 (Layer 2, P2 races)
**Umbrella:** S0714

> **Scope:** Защита двух разделяемых изменяемых кэшей от гонок чтения/мутации. Найдено статически (S0716).

---

## 0. Источник

Две P2-находки аудита S0716 (`PLAN/S0716_concurrency-correctness-audit/AUDIT_FINDINGS.md`) измерения «data races»: незащищённое общее изменяемое состояние, читаемое и мутируемое с разных диспетчеров.

## 1. Находки и правки

1. **`core/cache/MediaFilesCacheManager.kt:73` `updateFile`/`removeFile`/`addFile`.** Значения LruCache - plain `ArrayList<MediaFile>`, мутируются in-place (`set`/`removeAll`/`add`) на Main, тогда как `RandomPhotoFrameWidgetRefresher.refresh()` зовёт `getCachedList()` (итерация `.toList()`) внутри `runBlocking(IO)` (WorkManager + widget broadcasts) - off-Main. Итерация на IO гонится со структурной мутацией того же списка на Main → `ConcurrentModificationException`/torn read. **Fix:** хранить `CopyOnWriteArrayList` как значение LruCache, либо защитить каждую мутацию значения + `toList()`-снимок одним `synchronized`/`Mutex`.
2. **`core/cache/TranslationCacheManager.kt:17` `cache`/`lensCache`.** Object-singleton с plain `mutableMapOf` (без `@Volatile`/`Mutex`/`synchronized`/`ConcurrentHashMap`). `putTranslation` на Main; `putLensTranslation`, `getTranslation`, `clearAll` - на IO. Запись на Main при одновременном чтении/очистке на IO без синхронизации → CME/потеря записей/resize-spin/visibility; плюс неатомарный `getOrPut`. **Fix:** заменить оба на `ConcurrentHashMap<String, ConcurrentHashMap<Int, ..>>`, либо защитить все аксессоры одним `Mutex`/`synchronized`.

## 2. Критерии приёмки

- [x] Обе структуры безопасны при конкурентном чтении/мутации с Main и IO; нет CME/torn read. (`TranslationCacheManager` - `ConcurrentHashMap` на обоих уровнях + атомарный `putIfAbsent`; `MediaFilesCacheManager` - единый `lock` на все мутации содержимого списка + snapshot-чтение `getCachedList`.)
- [x] Поведение кэша сохранено; `.\a.ps1 fc` зелёный. (Публичные сигнатуры не тронуты; BUILD SUCCESSFUL in 38s.)

## 3. Связанные тикеты

- S0716 (аудит-источник), S0714 (зонтик).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0716, S0714
- **Data:** защита двух in-memory кэшей от гонок; формат данных, поведение и публичные сигнатуры не меняются (атомарность вместо CME/torn read). Без UI/flavor/schema/API эффекта.

## Last Audit

**Date:** 2026-06-26
**Mode:** full (compact fix)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 2 · WARN 0 · FAIL 0

Обе находки §1 закрыты:

- `TranslationCacheManager` - `cache`/`lensCache` теперь `ConcurrentHashMap<String, ConcurrentHashMap<..>>`; `putTranslation`/`putLensTranslation` создают per-file карту через атомарный `putIfAbsent` (API-23-safe, без `computeIfAbsent`). Чтение/`clearAll`/`getCacheStats` безопасны на CHM (weakly-consistent итераторы, без CME).
- `MediaFilesCacheManager` - единый `private val lock`; `getCachedList` (snapshot на IO), `setCachedList`, `updateFile`, `removeFile`, `addFile`, `getCacheSize`, `fixCloudPaths` синхронизированы. In-place мутация списка на Main больше не гонится с `toList()`-итерацией на IO (`RandomPhotoFrameWidgetRefresher`). Порядок захвата всегда `lock` → LruCache-монитор, реверса нет - deadlock невозможен.

`.\a.ps1 fc` - BUILD SUCCESSFUL in 38s. Публичные сигнатуры обоих синглтонов неизменны - test source set не затронут.
