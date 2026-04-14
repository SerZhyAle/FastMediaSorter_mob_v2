# SPEC: Исправление мини-панели NowPlaying в PlayerActivity

**Создано**: 2026-04-14  
**Статус**: ОЖИДАЕТ РЕАЛИЗАЦИИ  
**Флейворы**: `standard`, `legacy` (затронут `ENABLE_PERSISTENT_AUDIO_PLAYBACK`)  
**API level**: minSdk 26..35  
**Автор**: Copilot  

---

## 1. Описание проблемы

`NowPlayingManager` управляет мини-полоской фонового аудио-плеера (`miniNowPlayingBar`) внутри `PlayerActivity`.  
Полоска появляется при просмотре **изображений и видео** (видео — отдельный фикс), показывает пустое состояние и не реагирует на нажатия.

### Воспроизведение

1. Пользователь слушает аудио (фоновый `AudioPlaybackService` запущен).
2. Выходит из аудио-библиотеки (нажимает «Всегда продолжать»).
3. Открывает «Все фото» → тапает на фотографию → новый `PlayerActivity`.
4. **Результат**: снизу полоска с треугольником (Play-кнопка), тап на полоску → `NowPlayingBottomSheetFragment` с пустым заголовком «Сейчас играет», пустой очередью и нерабочими кнопками.
5. Музыка при этом **продолжает играть** в фоне.

Настройка «Всегда остановить»: аудио останавливается, но сервис живёт ещё ~10 секунд (`AUTO_STOP_DELAY_MS`) → полоска также появляется в этом окне.

---

## 2. Корневые причины багов (AS-IS анализ)

### Bug A — `audioServiceController.player` всегда `null` в новом PlayerActivity  
**Файл**: `AudioServiceController.kt`  
**Суть**: `mediaController` (и, соответственно, свойство `player`) устанавливается только внутри `connect()`, который вызывается **исключительно** из методов `playAudio*()`. При открытии нового `PlayerActivity` для фото/изображений `playAudio*()` не вызывается → `audioServiceController.player == null`.  
**Следствие**: вся логика `NowPlayingManager.updateBarVisibility()`, которая читает `audioServiceController.player`, получает `null` → заголовок не заполняется, иконка play/pause не обновляется.

```kotlin
// NowPlayingManager.init {}
miniBar.miniPlayPause.setOnClickListener {
    audioServiceController.player?.let { player ->  // ← null → тихой no-op
        if (player.isPlaying) player.pause() else player.play()
        updateMiniPlayPauseIcon(player.isPlaying.not())
    }
}
```

### Bug B — `NowPlayingBottomSheetFragment` показывает пустое состояние при открытии  
**Файл**: `NowPlayingViewModel.kt`, `NowPlayingBottomSheetFragment.kt`  
**Суть**: `NowPlayingViewModel.connect()` создаёт **независимое** подключение к сервису, которое асинхронно. До завершения `Future` (обычно 50..300 мс) UI показывает дефолтный `NowPlayingState`:
```kotlin
data class NowPlayingState(
    val title: String = "",           // → "Сейчас играет"
    val queueItems: List<QueueItem> = emptyList()  // → «Очередь (1)» пустая
    // ...
)
```
Кнопки работают (ViewModel делает `mediaController?.play()`), но `mediaController == null` до завершения `Future` → первый тап тоже no-op.

### Bug C — «Всегда остановить»: полоска показывается пока сервис умирает (10 сек)  
**Файл**: `AudioPlaybackService.kt`, `NowPlayingManager.kt`  
**Суть**: при `player.stop()` → `STATE_ENDED` → `autoStopRunnable` выполняется через 10 секунд → `stopSelf()` → `onDestroy()` → `isRunning = false`.  
В течение этих 10 секунд `AudioPlaybackService.isRunning == true`, но плеер ничего не воспроизводит. Если пользователь успел открыть другой `PlayerActivity`, полоска показывается с пустым состоянием.

**Диаграмма состояний сервиса:**
```
playAudio() → STATE_READY → player.stop() → STATE_ENDED → [10s wait] → stopSelf() → onDestroy() → isRunning=false
                                                 ↑ isRunning=true во всём этом при переходе к фото
```

### Bug D — Обновление иконки play/pause возможно только при подключённом контроллере  
**Файл**: `NowPlayingManager.kt`  
**Суть**: `updateMiniPlayPauseIcon(player.isPlaying)` вызывается только внутри блока `if (player != null)`, который никогда не выполняется (Bug A). Иконка остаётся в дефолтном состоянии (из XML/ресурса) независимо от реального состояния плеера.

---

## 3. Компоненты и файлы, затронутые исправлением

| Файл | Роль | Нужное изменение |
|---|---|---|
| `AudioServiceController.kt` | Управляет `MediaController` подключением | + метод `connectForStatus(onResult)` |
| `NowPlayingManager.kt` | Управляет мини-баром | Вызов `connectForStatus()` в `updateBarVisibility()`, callback refreshes UI |
| `NowPlayingViewModel.kt` | Данные для BottomSheet | + `isLoading: Boolean` в state, показывать loading до подключения |
| `NowPlayingBottomSheetFragment.kt` | UI нижнего листа | Отображение loading-спиннера пока `isLoading=true` |
| `AudioPlaybackService.kt` | Фоновый сервис | + `isActivelyPlaying` флаг (опционально, см. вариант 2 ниже) |

---

## 4. Дизайн решения

### Принцип

Вместо того чтобы `NowPlayingManager` читал `audioServiceController.player` (который null), он должен **инициировать подключение** при обнаружении `AudioPlaybackService.isRunning == true` и **обновлять UI в callback'е**.

### 4.1 `AudioServiceController` — новый метод `connectForStatus()`

```kotlin
/**
 * Connect to AudioPlaybackService to read state without starting playback.
 * Safe to call multiple times — no-op if already connected.
 *
 * @param onResult Invoked on main thread with (isConnected, player).
 *   player is null if service disconnected/not responding.
 */
fun connectForStatus(onResult: (player: Player?) -> Unit) {
    if (isConnected) {
        onResult(mediaController)
        return
    }
    // Reuse connect() machinery, but do NOT start playback
    val sessionToken = SessionToken(context,
        ComponentName(context, AudioPlaybackService::class.java))
    val future = MediaController.Builder(context, sessionToken).buildAsync()
    controllerFuture = future
    future.addListener({
        try {
            val controller = future.get()
            mediaController = controller
            Timber.d("AudioServiceController: connectForStatus — connected")
            onResult(controller)
        } catch (e: Exception) {
            Timber.w(e, "AudioServiceController: connectForStatus — failed (service may have died)")
            onResult(null)
        }
    }, MoreExecutors.directExecutor())
}
```

**Примечание**: callback работает на `directExecutor()` — тот же поток что Media3 использует для `Future`. Убедиться, что UI-обновления обёрнуты в `Handler(Looper.getMainLooper()).post { }` или использовать `MainExecutor`.

### 4.2 `NowPlayingManager` — `updateBarVisibility()` с async подключением

```kotlin
fun updateBarVisibility(currentMediaType: MediaType? = null) {
    if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK || miniBar == null) return

    // Видео — всегда скрыть. Исправлено в предыдущем шаге.
    if (currentMediaType == MediaType.VIDEO) {
        miniBar.root.isVisible = false
        return
    }

    val serviceRunning = AudioPlaybackService.isRunning
    if (!serviceRunning) {
        miniBar.root.isVisible = false
        return
    }

    // Сервис запущен, но контроллер может быть не подключён.
    // Подключаемся async, затем проверяем реальное состояние плеера.
    audioServiceController.connectForStatus { player ->
        Handler(Looper.getMainLooper()).post {
            if (player == null) {
                // Сервис умер пока мы подключались
                miniBar.root.isVisible = false
                return@post
            }
            val activelyPlaying = player.playbackState == Player.STATE_READY
                    || player.playbackState == Player.STATE_BUFFERING
            if (!activelyPlaying) {
                // STATE_ENDED / IDLE — сервис умирает, прячем бар.
                miniBar.root.isVisible = false
                return@post
            }
            // Сервис жив и воспроизводит → показываем и заполняем.
            miniBar.root.isVisible = true
            val meta = player.mediaMetadata
            miniBar.miniTitle.text = meta.title?.toString()
                ?: activityBinding.root.context.getString(R.string.now_playing_label)
            val artworkUri = meta.artworkUri
            if (artworkUri != null) {
                Glide.with(activityBinding.root.context)
                    .load(artworkUri)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .into(miniBar.miniArtwork)
            } else {
                miniBar.miniArtwork.setImageResource(R.drawable.ic_music_note)
            }
            updateMiniPlayPauseIcon(player.isPlaying)
        }
    }
}
```

**Важно**: кнопка play/pause в `init {}` использует `audioServiceController.player?.let { ... }`. После `connectForStatus()` `audioServiceController.player` будет не null, поэтому кнопка заработает без дополнительных изменений в `init`. Но вызов `connectForStatus()` должен произойти ДО того, как пользователь нажмёт кнопку — это гарантируется `onResume()`.

### 4.3 `NowPlayingViewModel` — индикатор загрузки

Добавить поле `isLoading: Boolean = true` в `NowPlayingState`:

```kotlin
data class NowPlayingState(
    val isLoading: Boolean = true,  // true пока MediaController не подключился
    val isPlaying: Boolean = false,
    val title: String = "",
    // ...
)
```

В `connect()`:

```kotlin
fun connect() {
    if (mediaController?.isConnected == true) return
    // isLoading уже true (по умолчанию)
    // ...
    future.addListener({
        try {
            val controller = future.get()
            // ... существующая логика ...
            _state.value = state.copy(isLoading = false)  // ← добавить
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, serviceRunning = false)
        }
    }, MoreExecutors.directExecutor())
}
```

### 4.4 `NowPlayingBottomSheetFragment` — loading-состояние

В `bottom_sheet_now_playing.xml` добавить `ProgressBar` (центр панели).
В `observeState()`:

```kotlin
private fun updateNowPlayingPanel(state: NowPlayingViewModel.NowPlayingState) {
    progressLoading.isVisible = state.isLoading
    panelContent.isVisible = !state.isLoading
    // ... остальная логика только если !isLoading
}
```

---

## 5. Граничные случаи и ADR

### ADR-1: Почему `connectForStatus()` а не изменение `connect()`?

`connect()` уже имеет семантику инициализации воспроизведения. Отдельный метод сохраняет SRP и не ломает существующие вызовы `playAudio*()`.

### ADR-2: Что делать если сервис умер пока открывается BottomSheet?

`NowPlayingViewModel` слушает `onPlaybackStateChanged`. При `STATE_IDLE/ENDED` → `serviceRunning = false` → UI должен показывать «Ничего не играет» и предложить закрыть лист. Сейчас этой логики нет. В рамках данного спека: при `serviceRunning = false` показать placeholder-текст «Воспроизведение остановлено» и кнопку «Закрыть».

### ADR-3: Гонка между `connectForStatus()` и разрушением Activity

Если `PlayerActivity.onDestroy()` вызывается до завершения `Future`:
- `audioServiceController.release()` вызывается в `PlayerLifecycleManager.releaseResources()`
- `connectForStatus()` callback вызовет `Handler.post { }` на уничтоженную Activity
- Защита: проверить `activity.isDestroyed` в начале `Handler.post { }` блока

### ADR-4: Почему не использовать `isRunning && isActivelyPlaying` статические флаги?

Статический `@Volatile` флаг в `AudioPlaybackService` — простой способ, но:
- Подвержен гонке (флаг ставится в `onPlaybackStateChanged`, который приходит асинхронно)
- Требует дополнительного флага и синхронизации
- `connectForStatus()` + проверка `player.playbackState` надёжнее и не добавляет глобального состояния

---

## 6. Тест-план

### Сценарий 1 — Основной (MUST PASS)
1. Открыть аудио, начать воспроизведение.
2. Выйти с «Всегда продолжать».
3. Открыть «Все фото» → открыть фото.
4. **Ожидаемо**: мини-бар показывается с названием трека и рабочей иконкой play/pause.
5. Нажать треугольник → музыка паузируется/возобновляется.
6. Нажать на полоску → BottomSheet открывается с правильным названием и очередью.

### Сценарий 2 — «Всегда остановить» (MUST PASS)
1. Открыть аудио, начать воспроизведение.
2. Выйти с «Всегда остановить».
3. Немедленно открыть фото.
4. **Ожидаемо**: мини-бар скрыт (или показывается и сразу скрывается при обнаружении STATE_ENDED).

### Сценарий 3 — Видео (MUST PASS, уже исправлено)
- Открыть видеофайл → мини-бар отсутствует.

### Сценарий 4 — BottomSheet loading (MUST PASS)
- Открыть BottomSheet при медленном устройстве → виден спиннер → затем появляется трек.

### Сценарий 5 — Сервис умер пока было открыто фото (MUST PASS)
1. Слушать аудио, открыть фото, мини-бар показался.
2. Принудительно остановить сервис (ADB или авто-стоп).
3. **Ожидаемо**: мини-бар скрывается (при следующем `onResume` или через Player.Listener).

### Сценарий 6 — Нет подключения к сервису (EDGE CASE)
- `AudioPlaybackService.isRunning == true`, но `connectForStatus()` падает.
- **Ожидаемо**: мини-бар скрыт, ошибка логируется в Timber.w.

---

## 7. Ограничения вне скоупа данного спека

- Показ мини-бара в **BrowseActivity** — это независимая фича, не затронута.
- Полная живая синхронизация мини-бара (обновление позиции в реальном времени) — отдельная задача.
- Обновление иконки мини-бара при переходе к следующему треку через уведомление — отдельная задача.

---

## 8. Файлы к изменению (summary)

| Файл | Тип изменения | Объём |
|---|---|---|
| `ui/player/helpers/AudioServiceController.kt` | + метод `connectForStatus()` | ~25 строк |
| `ui/player/helpers/NowPlayingManager.kt` | Переработка `updateBarVisibility()` | ~30 строк замены |
| `ui/player/NowPlayingViewModel.kt` | + поле `isLoading`, обновление `connect()` | ~10 строк |
| `ui/player/NowPlayingBottomSheetFragment.kt` | Loading state в `updateNowPlayingPanel()` | ~5 строк |
| `res/layout/bottom_sheet_now_playing.xml` | + ProgressBar | ~5 строк XML |

Итого: небольшое изменение, без архитектурного рефакторинга.  
**Максимальный риск**: гонка `connectForStatus()` vs `onDestroy()` — закрыта через `isDestroyed` проверку.
