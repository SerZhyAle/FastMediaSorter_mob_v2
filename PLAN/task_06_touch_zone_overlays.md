# Task 6: Context-Aware Touch Zone Help Overlays

## Objective
Показывать пользователю графическую подсказку тач-зон, точно соответствующую текущему режиму Player. Тракировать статус показа **отдельно для каждого типа** оверлея.

---

## Критическое ограничение: ZERO FUNCTIONAL CHANGES

> **ВОЗБРАНИТЬ**: Тач-зоны — очень хрупкая подсистема. Изменять можно ТОЛЬКО логику показа подсказочного оверлея. Размещение в View-иерархии, interaction listeners, Z-порядок, `TouchZoneGestureManager`, `TouchZoneDetector` — **не трогать**.

---

## Context & Current Behavior

### Текущая система тач-зон (`TouchZoneGestureManager`)

Функциональное определение зон (comment в `PlayerActivity.setupTouchZones()`):
- **Fullscreen mode** (`showCommandPanel == false`): **9-зонная сетка 3×3** — `handleTouchZone()`
- **Command panel mode** (`showCommandPanel == true`): **3-зонная** (20% левая, 60% центр, 20% правая) — `handleCommandPanelTouchZones()`
- **Аудио/видео playback**: нижняя часть экрана зарезервирована под ExoPlayer controls (75% / 66% верхняя часть)

### Текущая система первого запуска
- `KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN = booleanPreferencesKey("show_player_hint_on_first_run")` — **один флаг** для всех режимов.
- `hasShownFirstRunHint: Boolean` — сессионный флаг в `PlayerActivity`.
- `showFirstRunHintOverlay()` (стр. ~1889) — отображает **одну** имеющуюся картинку (9-зонная сетка) без различия.
- `binding.btnTouchZonesHelp` — кнопка в настройках, вызывающая `showFirstRunHintOverlay()`.
- Layout оверлея: `player_first_run_hint_overlay_content.xml` (или аналогичный view id `firstRunHintOverlay`).
- Оверлей содержит `iv` для показа картинки тач-зон (`ivTouchZonesScheme` в `WelcomePagerAdapter`).

---

## Affected Files

| File | Role |
|------|------|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | `showFirstRunHintOverlay()`, `hasShownFirstRunHint`, `binding.btnTouchZonesHelp` |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | DataStore: добавить три новых ключа |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Добавить три новых поля |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/preferences/SettingsManager.kt` | Аналогично содержит DataStore ключи |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerUiStateCoordinator.kt` | `hasShownFirstRunHint()` interface |
| `app_v2/src/main/res/layout/player_first_run_hint_overlay_content.xml` | Оверлей подсказки |
| `app_v2/src/main/res/drawable/` | Новые картинки для 3-зонного и видео/аудио режимов |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/` | Кнопка сброса |

---

## Requirements

### R1 — Определить типы оверлеев

```kotlin
enum class TouchZoneHintType {
    FULLSCREEN_9ZONE,      // fullscreen mode, images — 9-сегментная сетка
    COMMAND_PANEL_3ZONE,   // command panel mode — 3 зоны
    MEDIA_BOTTOM_RESERVED  // video/audio fullscreen — нижнее пространство занято ExoPlayer
}
```

### R2 — DataStore: отдельные ключи для каждого типа

Добавить в `SettingsRepositoryImpl` + `SettingsManager` + `AppSettings`:

```kotlin
// SettingsRepositoryImpl.kt
private val KEY_HINT_SHOWN_9ZONE     = booleanPreferencesKey("hint_shown_9zone")
private val KEY_HINT_SHOWN_3ZONE     = booleanPreferencesKey("hint_shown_3zone")
private val KEY_HINT_SHOWN_MEDIA     = booleanPreferencesKey("hint_shown_media_bottom")

// AppSettings.kt
val hintShown9Zone: Boolean = false
val hintShown3Zone: Boolean = false  
val hintShownMedia: Boolean = false
```

Старый ключ `KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN` **остаётся** — его сброс сбрасывает все три новых (обратная совместимость).

### R3 — Определить активный тип перед показом

```kotlin
fun determineTouchZoneHintType(
    showCommandPanel: Boolean,
    currentFile: MediaFile?
): TouchZoneHintType {
    if (showCommandPanel) return TouchZoneHintType.COMMAND_PANEL_3ZONE
    val isMedia = currentFile?.type in listOf(MediaType.VIDEO, MediaType.AUDIO)
    return if (isMedia) TouchZoneHintType.MEDIA_BOTTOM_RESERVED
           else TouchZoneHintType.FULLSCREEN_9ZONE
}
```

### R4 — Логика показа оверлея

В `showFirstRunHintOverlay()` / `PlayerUiStateCoordinator`:

```kotlin
fun maybeShowTouchZoneHint() {
    val hintType = determineTouchZoneHintType(state.showCommandPanel, state.currentFile)
    val alreadyShown = when (hintType) {
        FULLSCREEN_9ZONE -> settings.hintShown9Zone
        COMMAND_PANEL_3ZONE -> settings.hintShown3Zone
        MEDIA_BOTTOM_RESERVED -> settings.hintShownMedia
    }
    if (alreadyShown) return
    val imageRes = when (hintType) {
        FULLSCREEN_9ZONE -> R.drawable.hint_touch_zones_9
        COMMAND_PANEL_3ZONE -> R.drawable.hint_touch_zones_3
        MEDIA_BOTTOM_RESERVED -> R.drawable.hint_touch_zones_media
    }
    showHintOverlayWithImage(imageRes) {
        markHintShown(hintType)  // сохраняет в DataStore
    }
}
```

### R5 — Graphics: три рисунка

| Drawable | Описание |
|---------|----------|
| `hint_touch_zones_9.png` | Существующая картинка 9-зон full-экран |
| `hint_touch_zones_3.png` | Новая: 3 колонки с подписями Previous / Center / Next |
| `hint_touch_zones_media.png` | Новая: 9-зонная сетка с тёмной полосой внизу (занята controls) |

Если дизайнер графики недоступен — генерировать программно через Canvas (аналогично `TouchZoneOverlayView`).

### R6 — Сброс через Settings ("See controls again")

- Существующая кнопка в Settings, которая сбрасывала `KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN`, теперь дополнительно сбрасывает все три новых ключа.
- При открытии Player в любом режиме пользователь увидит соответствующую подсказку.

### R7 — `btnTouchZonesHelp` — показ по запросу

При нажатии `btnTouchZonesHelp` — вызывать `maybeShowTouchZoneHint()` без проверки (force-show для текущего типа).

---

## Implementation Plan

1. **`AppSettings.kt` + `SettingsRepositoryImpl.kt` + `SettingsManager.kt`**: Добавить три новых DataStore-ключа и поля `AppSettings`.

2. **`TouchZoneHintType` enum**: Создать в `domain/model/` или внутри `ui/player/`.

3. **`PlayerActivity.showFirstRunHintOverlay()`** / новый `maybeShowTouchZoneHint()`:
   - Вычислить `hintType` из текущего состояния.
   - Проверить DataStore-флаг.
   - Загрузить нужный Drawable, показать оверлей.
   - По тапу: скрыть и сохранить флаг в DataStore.

4. **`PlayerUiStateCoordinator.Callback`**: Обновить интерфейс: вместо `hasShownFirstRunHint()`/`markFirstRunHintShown()` — передавать текущий `TouchZoneHintType` и статус показа.

5. **Settings reset**: `PlaybackSettingsFragment` — при сбросе `show_player_hint_on_first_run` — дополнительно сбрасывать `hint_shown_9zone`, `hint_shown_3zone`, `hint_shown_media_bottom`.

6. **Drawable assets**: Создать `hint_touch_zones_3.png` и `hint_touch_zones_media.png`. Если нет финальных артефактов — генерировать через Canvas аналогично `TouchZoneOverlayView`.

---

## Edge Cases & Risks

- **Смена режима** (fullscreen ↔ command panel) **во время сессии**: `hasShownFirstRunHint` — сессионный флаг. При переключении в другой режим — хинт для нового типа может быть показан. Обождём сессионный флаг на `Set<TouchZoneHintType>`.
- **Раннее сохранённые данные**: у пользователей, которые глядели обычное приложение, `KEY_SHOW_PLAYER_HINT_ON_FIRST_RUN = false` — все три новых ключа при миграции записываются `false` (не показвать). Миграция: если старый ключ = false, новые — default false (hint уже показан).
- **Первый запуск Player** на новом устройстве: все три hint = true (not shown), показать первый актуальный тип.
- **Документы (PDF, EPUB)**: тач-зоны отключены (overlay hidden). Не показывать подсказку для документов.
- **CRITICAL: Don't touch z-order!** Охранный оверлей должен представлять собой визуальный элемент, отображающийся поверх всего с полупрозрачным фоном, без взаимодействия с touch-events под ним.

