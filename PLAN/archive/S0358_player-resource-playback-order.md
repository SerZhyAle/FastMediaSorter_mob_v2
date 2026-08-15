# Стратегическая спецификация: S0358 - Player resource playback order

**Ticket:** S0358
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-04
**Tier:** 1 - Quick Win

## Problem

Плеер при запуске из Browse через `Play random` не показывает режим `Shuffle` на кнопке порядка проигрывания. Из-за этого в машине пользователь каждый раз вручную прокликивает режимы до `Random`, хотя запуск уже был случайным.

## Approach

- `BrowseManagerInitializer.kt` передаёт в плеер явный стартовый режим `SHUFFLE` для сценария `Play random`.
- `PlaybackControlPreferences.kt` получает ключи для per-resource playback order с fallback на старые глобальные audio/video prefs.
- `PlayerActivity.kt` восстанавливает playback order для текущего ресурса, сохраняет изменения обратно в per-resource prefs и сразу применяет режим к активному плееру.

## Done criteria

- `BrowseManagerInitializer.kt`: `Play random` открывает плеер с явным `SHUFFLE` override.
- `PlaybackControlPreferences.kt`: есть resource-scoped ключи и helper для разделения audio/visual режима.
- `PlayerActivity.kt`: кнопка порядка проигрывания сразу показывает и применяет сохранённый для ресурса режим, а `Play random` переводит её в `Shuffle` без ручного прокликивания.
