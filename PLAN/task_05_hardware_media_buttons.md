# Task 5: Hardware Media Buttons Support

## Objective
Обеспечить реакцию на аппаратные кнопки управления медиа (Next / Previous / Play-Pause) на устройстве или подключённых периферийных устройствах.

---

## Context & Current Behavior

### Что есть сейчас

- `PlayerActivity.onKeyDown()` (стр. ~2981) → `keyboardHandler.handleKeyDown(keyCode, event)`.
- `PlayerKeyboardHandler.handleKeyDown()` (стр. ~53): обрабатывает Page Up/Down, стрелки, Delete. **`KEYCODE_MEDIA_*` не обрабатываются.**
- `AudioPlaybackService : MediaSessionService` — `MediaSession` создаётся в `onCreate()`, но **не назначается** `MediaSession.Callback` для своих команд.

### Как работают Android hardware buttons

Android отправляет события аппаратных кнопок через **два** канала (работающие независимо):
1. **`onKeyDown()` в Activity** — когда Activity на переднем плане (в `onResume`).
2. **`MediaSession` / `MediaButtonReceiver`** — даже когда экран выключен, если зарегистрирован в AndroidManifest и `MediaSession.isActive = true`.

---

## Affected Files

| File | Role |
|------|------|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt` | Добавить `KEYCODE_MEDIA_*` кейсы |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | `onKeyDown()` — убедиться, что не интерцептирует кнопки до KeyboardHandler |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioPlaybackService.kt` | `MediaSession` — добавить `Callback` |
| `app_v2/src/main/AndroidManifest.xml` | Регистрация `MediaButtonReceiver` |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | `nextFile()`, `previousFile()`, `togglePlayPause()` |

---

## Requirements

### R1 — Обработка кнопок через `PlayerKeyboardHandler`

Добавить обработку в `handleKeyDown()`:

```kotlin
KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
KeyEvent.KEYCODE_HEADSETHOOK -> {
    viewModel.togglePlayPause()
    true
}
KeyEvent.KEYCODE_MEDIA_NEXT -> {
    viewModel.nextFile()
    true
}
KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
    viewModel.previousFile()
    true
}
KeyEvent.KEYCODE_MEDIA_PLAY -> {
    viewModel.play()
    true
}
KeyEvent.KEYCODE_MEDIA_PAUSE -> {
    viewModel.pause()
    true
}
```

**Важно:** `KEYCODE_HEADSETHOOK` — аппаратные наушники отправляют его вместо `MEDIA_PLAY_PAUSE`.

### R2 — Обработка через `MediaSession.Callback` (экран выключен / Bluetooth)

Создать `object MediaButtonCallback : MediaSession.Callback` в `AudioPlaybackService`:

```kotlin
override fun onPlay(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>): MediaSession.MediaItemsWithStartPosition {
    // пример — интеграция зависит от текущей архитектуры Service
}
```

MediaSession3 (androidx.media3) использует `Player.Listener` вместо прямых callback для большинства команд. Изучить текущую версию Media3 в `gradle/libs.versions.toml` (`androidx.media3`). Для команд next/previous в Media3 — использовать `CommandButton` или `Player.seekToNext()` / `Player.seekToPrevious()`.

**Альтернатива (BroadcastReceiver):** если `MediaSession.Callback` Media3 не покрывает next/prev через hardware buttons — добавить `MediaButtonReceiver` в Manifest и обработать `Intent.ACTION_MEDIA_BUTTON` в Service.

### R3 — `MediaSession.isActive = true`

Убедиться, что `mediaSession.isActive = true` выставляется при начале воспроизведения. Без это система не маршрутизирует hardware events в приложение.

---

## Implementation Plan

1. **`PlayerKeyboardHandler.handleKeyDown()`**: Добавить ветви `when` для media keycodes. Маппинг — на существующие методы ViewModel.   

2. **`AudioPlaybackService`**: Проверить, назначен ли `MediaSession.Callback`. Добавить callback через `MediaSession.Builder(...).setCallback(...)`. Relay-методы callback — через `PendingIntent` или broadcast в `PlayerActivity`.

3. **`AndroidManifest.xml`**: Проверить наличие `<receiver android:name="androidx.media.session.MediaButtonReceiver">` с фильтром `ACTION_MEDIA_BUTTON`.

4. **Тестирование на устройстве:** ADB-подача events:
   ```
   adb shell input keyevent KEYCODE_MEDIA_NEXT
   adb shell input keyevent KEYCODE_MEDIA_PREVIOUS
   adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE
   ```

---

## Edge Cases & Risks

- **Activity не на переднем плане:** аппаратные кнопки должны работать через `MediaSession` (Bluetooth наушники, блокировка экрана). Оба механизма необходимы.
- **Double-trigger:** если оба `onKeyDown` и `MediaSession` срабатывают одновременно — `nextFile()` вызывается дважды. Обязательно: при debounce или отдать приоритет одному механизму.
- **Media3 vs Media2 API:** приложение использует `AudioPlaybackService : MediaSessionService` (Media3), не `MediaBrowserServiceCompat`. API callback-ов отличается. Перед разработкой — читать документацию Media3 `MediaSession`.
- **Long press KEYCODE_MEDIA_NEXT:** у большинства устройств долгое нажатие = fast forward. Скорее всего в нашем случае достаточно обработать қак `onKeyDown` с `repeatCount == 0` (игнорировать повторы).

