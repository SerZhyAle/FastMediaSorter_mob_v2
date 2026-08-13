# S0458 - Standalone image player: gate Google Lens on setting

**Ticket:** S0458
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-16
**Tier:** 1 - Quick Win

## Problem

В standalone-плеере изображений пункт overflow-меню «Google Lens» показывается всегда, когда изображение локально-редактируемое, и игнорирует настройку `enableGoogleLens`. Во встроенном плеере та же команда скрыта, пока пользователь не включит её в настройках. Поведение должно совпадать: пункт виден только при включённой настройке.

## Approach

- `PhotoVideoStandaloneActivity.kt` - кэшировать значение `enableGoogleLens` из `settingsRepository.getSettings()` в `observeData()` и гейтить видимость `menu_google_lens` на `editable && <настройка>` (сейчас только `editable`).

## Done criteria

- При выключенной настройке Google Lens пункт «Google Lens» в overflow-меню standalone-плеера изображений отсутствует.
- При включённой настройке и локально-редактируемом изображении пункт присутствует и открывает Google Lens (как раньше).
