# Спецификация (fix): S0726 - app_v2: гигиена ресурсов Layer 3 (4 находки S0715)

**Ticket:** S0726
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-26
**Tier:** 2 - Bugfix
**Roadmap entry:** Ad-hoc - находки аудита S0715 (Layer 3)
**Umbrella:** S0714

> **Scope:** Четыре точечных fix-а владения ресурсами в `app_v2`. Каждый найден статически (S0715) и подтверждён sibling-кодом.

---

## 0. Источник

Четыре подтверждённые находки аудита S0715 (`PLAN/S0715_memory-resource-ownership-audit/AUDIT_FINDINGS.md`): две P2, две P3. Независимы, но мелкие - сведены в один fix-тикет (один PR).

## 1. Находки и правки

1. **P2 - `ui/player/helpers/BackgroundMusicManager.kt:531` `release()`.** `release()` отменяет джобы и освобождает ExoPlayer, но не обнуляет `onTrackChangedListener`/`onMusicErrorListener` (лямбды захватывают `PlayerActivity`). Sibling `AudioBackgroundPhotosManager.release()` (`:319-320`), освобождаемый в том же teardown-блоке, обнуляет свои слушатели - асимметрия реальна. **Fix:** добавить `onTrackChangedListener = null` и `onMusicErrorListener = null` в `release()`.
2. **P2 - `core/util/MediaMetadataHelper.kt:337` `extractVideoAudioInfo`.** `val extractor = MediaExtractor()` в `:337`, `extractor.release()` в `:378` внутри тела try; `finally` (`:382-384`) освобождает только `MediaMetadataRetriever`. Любой throw между 337 и 378 (битый `setDataSource`, `getTrackFormat`) утекает нативный `MediaExtractor`. Sibling `SafUriExtractor.extractVideoAudioInfo` оборачивает в try/finally. **Fix:** вынести `extractor` и звать `extractor.release()` в `finally` на всех путях.
3. **P3 - `ui/player/VideoPosterExtractor.kt:149` `tryGlideMemoryCache`.** `FutureTarget` от `.submit()` потребляется inline через `.get(50, ms)` и не освобождается; на `TimeoutException` `Glide.with(context).clear(future)` не зовётся (RequestFutureTarget не отменяет SingleRequest на timeout). Ограничено Activity-scoped RequestManager. **Fix:** захватить target в `val`, звать `Glide.with(context).clear(target)` в `finally`.
4. **P3 - `core/util/AudioMetadataLoader.kt:87` `memoryCache`.** `ConcurrentHashMap<String,AudioMetadata>` без `removeEldestEntry`/cap/`clear` - не вытесняется за время процесса (sibling `failedCache` `:99-106` ограничен FIFO `FAILED_CACHE_MAX_SIZE=5000`). Значения крошечные (без bitmap/Context), рост ограничен числом сетевых аудио за сессию. **Fix:** ограничить кэш (синхронизированный `LinkedHashMap` с `removeEldestEntry`, паритет с `failedCache`); сохранить потокобезопасность (текущий доступ - из нескольких потоков).

## 2. Критерии приёмки

- [ ] Все 4 правки внесены; поведение не меняется кроме корректного освобождения ресурса.
- [ ] `.\a.ps1 fc` зелёный; затронутые unit-тесты (если есть) проходят.
- [ ] Правка №4 сохраняет потокобезопасность доступа к кэшу.

## 3. Связанные тикеты

- S0715 (аудит-источник), S0714 (зонтик).

## Last Audit

**Date:** 2026-06-26
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 · WARN 0 · FAIL 0

Все 4 находки Layer 3 (S0715) подтверждены в коде:

- #1 P2 `BackgroundMusicManager.release()` (`:551-552`) - обнуляет `onTrackChangedListener`/`onMusicErrorListener` (симметрия с sibling `AudioBackgroundPhotosManager`).
- #2 P2 `MediaMetadataHelper.extractVideoAudioInfo` (`:337` hoist + `:379-381` finally) - нативный `MediaExtractor` освобождается на всех путях, включая throws из `setDataSource`/`getTrackFormat`.
- #3 P3 `VideoPosterExtractor.tryGlideMemoryCache` (`:153-155`) - `Glide.with(context).clear(target)` в `finally` (RequestFutureTarget не авто-чистится на timeout).
- #4 P3 `AudioMetadataLoader.memoryCache` (`:88-90`) - `Collections.synchronizedMap(LinkedHashMap .. removeEldestEntry)`, потокобезопасно, паритет с FIFO `failedCache`.

`compileStandardDebugKotlin` зелёный (main собран в проходе S0732). Снимает один из двух блокеров S0715 (второй - S0725, ждёт Wear-устройства).
