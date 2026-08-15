# Спецификация (draft): S0740 - BrowseActivity: лишний cold-collect getSettings()

**Ticket:** S0740
**Status:** Archived
**Priority:** 30
**Date:** 2026-06-26
**Tier:** 1 - Bugfix (efficiency)
**Umbrella:** S0714 / S0716 (аудит Flow/корутин)

> **Scope:** DRAFT - инбокс находки. Без проектирования.

---

## 0. Источник (verbatim)

Находка аудита S0730 (агент проверки, 2026-06-26): `BrowseActivity.kt:426` держит отдельный mic-recording cold `collectOnLifecycle(settingsRepository.getSettings())`, gated по `supportsMicRecording` - отличный от четырёх обзёрверов `BrowseObserverManager`, которые S0730 уже перевёл на единый разделяемый `BrowseViewModel.settings` StateFlow.

## 1. Проблема

`getSettings()` холодный: каждая отдельная подписка пересобирает ~150-полевой `AppSettings` (включая side-effect записи в glidePrefs) на Main. S0730 устранил мультипликацию в `BrowseObserverManager`, но этот mic-gated коллектор в `BrowseActivity` остался отдельным cold-collect-ом - та же неэффективность, другой класс.

## 2. Возможное направление (не утверждено)

Переиспользовать общий `BrowseViewModel.settings` StateFlow (введён S0730) и в `BrowseActivity`: `viewModel.settings.map { it.<micField> }.distinctUntilChanged()` через `collectOnLifecycle`. Проверить доступность `viewModel.settings` из Activity и сохранить gating `supportsMicRecording`.

## 3. Связанные тикеты

- S0730 (исходный фикс той же гигиены), S0716 (аудит-источник), S0714 (зонтик).
