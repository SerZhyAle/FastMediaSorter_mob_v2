# BUG: Неправильный режим запуска и тач-зоны для ресурса "Все видео"

## Статус: Требует исправления

---

## 1. Описание проблемы

При открытии плеера для ресурса **"Все видео"** возникают **два независимых дефекта**:

### Дефект A — Неправильный режим запуска (fullscreen вместо command panel)
Плеер открывается в **полноэкранном режиме** (без командной панели), хотя в глобальных настройках установлено **"панель кнопок по умолчанию"** (`defaultShowCommandPanel = true`). Поведение некорректно: пользователь не может вернуться в Browse и не видит кнопок управления.

### Дефект B — Несоответствие тач-зон и UI-режима (КРИТИЧНЫЙ)
Когда UI отображается в **полноэкранном режиме**, применяется **трёхзонная схема** (REG_375 — для режима с командной панелью), а не **девятизонная** (REG_975 — для полноэкранного видео). В результате работают только 3 зоны (вперёд/назад/пауза), но позиции тапов не соответствуют реальному расположению.

---

## 2. Воспроизведение

1. В настройках: **"Панель кнопок → По умолчанию"** (`defaultShowCommandPanel = true`)
2. Открыть ресурс **"Все видео"** → перейти в плеер
3. **Ожидаемо**: плеер открывается с командной панелью + тач-зоны REG_375 (3-зонные, 75% высоты)
4. **Фактически**: плеер открывается в полноэкранном режиме + применяются тач-зоны как в режиме command panel (3 зоны)

---

## 3. Корневые причины

### Дефект A — Логика `showCommandPanel` в `PlayerViewModel`

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`

В методе `loadMediaFiles()` (строки ~382–414) применяется специальная логика:

```kotlin
val showCommandPanel = when {
    resource.showCommandPanel == null -> currentSettings.defaultShowCommandPanel
    resource.showCommandPanel == false && currentSettings.defaultShowCommandPanel == true -> currentSettings.defaultShowCommandPanel
    else -> resource.showCommandPanel  // ← если resource.showCommandPanel == false явно, берётся false
}
```

Для ресурса "Все видео" поле `resource.showCommandPanel` может быть явно выставлено в `false` в БД (например, из старого пресета или профиля `VIDEO_LIBRARY`). В этом случае третья ветка `else -> resource.showCommandPanel` вернёт `false`, игнорируя глобальный дефолт.

**Дополнительно**: в `loadSettings()` тоже есть своя логика определения `showCommandPanel`:

```kotlin
val showCommandPanel = when {
    resource?.showCommandPanel != null -> resource.showCommandPanel  // ← берёт false если явно задано
    state.value.files.isNotEmpty() -> state.value.showCommandPanel
    else -> settings.defaultShowCommandPanel
}
```

Эти два пути выполняются **параллельно** и могут перезаписывать состояние друг друга в зависимости от порядка завершения корутин.

### Дефект B — Хардкод `isFullscreen = false` в `TouchZoneGestureManager`

**Файл**: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TouchZoneGestureManager.kt`, строка **196**

```kotlin
// ← ВСЕГДА передаёт false, независимо от реального UI-режима
val zoneMap = TouchZoneConfig.getZoneMapForMediaType(currentFile?.type, isFullscreen = false)
```

Этот код выполняется в ветке `!isInFullscreenMode` (command panel mode) и поэтому правильно использует `false` для IMAGE-файлов. Однако для VIDEO-файлов аналогичная логика определения зон работает через другой механизм — ExoPlayer controls. Нужно проверить, как именно определяется зонная схема для видео при полноэкранном режиме.

---

## 4. Требуемое исправление

### Фикс A — Нормализация `showCommandPanel` для ресурса

**Место**: `PlayerViewModel.kt`, метод `loadMediaFiles()`, логика определения `showCommandPanel`.

**Правило**: если `resource.showCommandPanel == null` **ИЛИ** глобальный дефолт явно включён (`defaultShowCommandPanel == true`), **всегда** применять глобальный дефолт. Явный `false` в ресурсе должен означать только "этот ресурс всегда открывается без панели" — и только тогда можно игнорировать глобал.

**Важно**: не трогать логику тач-зон, не менять `TouchZoneGestureManager`. Изменить только условие выбора `showCommandPanel`.

### Фикс B — Синхронизация тач-зон с UI-режимом

**Место**: Найти, где для VIDEO в полноэкранном режиме вычисляется тач-зонная схема.

**Правило**:
- `showCommandPanel == true` → REG_375 (3 зоны, 75% высоты)
- `showCommandPanel == false` (fullscreen) → REG_975 (9 зон, 75% высоты)

Проверить, что `getZoneMapForMediaType(MediaType.VIDEO, isFullscreen = state.showCommandPanel.not())` вызывается с корректным значением `isFullscreen`.

---

## 5. Что НЕ трогать

- Логику самих тач-зон (`TouchZoneConfig`, `TouchZoneGestureManager`) — только параметр `isFullscreen`
- Поведение для IMAGE/GIF — там тач-зоны работают правильно
- Логику переключения режима кнопкой (toggle command panel) — это работает корректно
- Ресурсы типа AUDIO — у них своя логика ExoPlayer UI

---

## 6. Проверка после фикса

| Сценарий | Ожидаемый UI | Ожидаемые тач-зоны |
|---|---|---|
| "Все видео" + глобал = command panel | Командная панель видна | REG_375 (3 зоны) |
| "Все видео" + глобал = fullscreen | Полный экран | REG_975 (9 зон) |
| Ресурс с явным `showCommandPanel = false` | Полный экран | REG_975 (9 зон) |
| Ресурс с явным `showCommandPanel = true` | Командная панель видна | REG_375 (3 зоны) |
| Toggle кнопкой во время сессии | Переключается | Тач-зоны переключаются вместе |

---

## 7. Затронутые файлы

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` — исправить логику `showCommandPanel` в `loadMediaFiles()` и `loadSettings()`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TouchZoneGestureManager.kt` — проверить `isFullscreen` при вычислении зон для VIDEO
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt` — только чтение, изменения не планируются
