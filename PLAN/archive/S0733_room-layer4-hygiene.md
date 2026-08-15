# Спецификация (fix): S0733 - Room Layer-4 гигиена: N+1 в legacy-миграции + дедуп Favorites-Flow

**Ticket:** S0733
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0717 (Layer 4, P3)
**Umbrella:** S0714

> **Scope:** Две тривиально-безопасные правки. Найдено статически (S0717).

---

## 0. Источник

Две P3-находки аудита S0717 (`PLAN/S0717_room-database-audit/AUDIT_FINDINGS.md`, #11/#12).

## 1. Находки и правки

1. **`data/repository/ThumbnailCacheRepositoryImpl.kt:252` `migrateLegacyCache`.** `thumbnailCacheDao.getAllThumbnails()` (`SELECT * FROM thumbnail_cache`, без LIMIT) вызывался внутри `oldFiles.forEach` → O(legacyFiles × cacheRows), квадратично по размеру библиотеки. Off-main (DeferredStartupWorker), одноразово при апгрейде. **Fix:** вынести `getAllThumbnails()` за цикл один раз в `Map` (`associateBy { File(it.thumbnailPath) }`), искать по `oldFile` внутри forEach.
2. **`data/repository/FavoritesRepositoryImpl.kt:18,22` `getAllFavorites`/`isFavorite`.** Room инвалидирует по гранулярности таблицы → любая запись в favorites ре-эмитит даже при неизменных строках. Один живой коллектор `getAllFavorites` (`BrowseStateSyncManager` - полный rebuild + перезапись кэша на каждый toggle). **Fix:** `.distinctUntilChanged()` на обоих Flow на границе репозитория.

## 2. Статус

Реализовано в этом тикете (тривиально-безопасно, политика inline). `compileStandardDebugKotlin` - зелёный.

## 3. Критерии приёмки

- [x] `migrateLegacyCache` грузит таблицу один раз (Map-lookup), не на каждый файл.
- [x] Оба Favorites-Flow обёрнуты `.distinctUntilChanged()`.
- [x] Компиляция зелёная; поведение сохранено (та же семантика поиска по `File(thumbnailPath)`).

## 4. Связанные тикеты

- S0717 (аудит-источник), S0714 (зонтик).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0

Обе P3-правки S0717 (Layer 4) подтверждены в коде:

- #1 `ThumbnailCacheRepositoryImpl.migrateLegacyCache` (`:249`) - `getAllThumbnails().associateBy { File(it.thumbnailPath) }` вынесен за `forEach`; O(legacyFiles × cacheRows) -> один проход + Map-lookup.
- #2 `FavoritesRepositoryImpl` (`:21`/`:25`) - `.distinctUntilChanged()` на `getAllFavorites`/`isFavorite` (import `:7`); table-granularity ре-эмиссии Room отсекаются на границе репозитория.

`compileStandardDebugKotlin` зелёный (main собран в проходе S0732). Заголовок файла был stale (`Draft` при catalog=`Implemented`) - синхронизирован на `Verified`.
