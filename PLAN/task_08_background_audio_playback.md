# Task 8: Background Audio Playback — Settings Toggle & Feature Completion

## Objective

Реализовать возможность продолжать воспроизведение аудиофайлов при сворачивании приложения
или блокировке экрана — как YouTube Music (платная фича YouTube):
управление через системное медиа-уведомление, переключение треков из уведомления,
работа до конца очереди аудиофайлов или до ручной остановки.

### Чем эта фича НЕ является (разграничение с существующими фичами)

| Фича | Флаг | Что делает | Scope |
|------|------|------------|-------|
| **THIS TASK: Background Playback** | `enablePersistentAudioPlayback` | Аудио играет при свёрнутом приложении / заблокированном экране | **Task 8** |
| Slideshow Background Music | `enableSlideshowBackgroundMusic` | Случайная музыка во время слайдшоу фото (IN-APP, `BackgroundMusicManager`) | Уже реализовано |
| Photos During Audio | `enablePhotosDuringAudio` | Фоновые фото при проигрывании аудио (IN-APP, `AudioSlideshowPhotoModeManager`) | Уже реализовано |

---

## Context & Current State

### Что уже реализовано (AS-IS)

**Инфраструктура сервиса** существует и готова:

| Компонент | Файл | Статус |
|-----------|------|--------|
| `AudioPlaybackService` | `ui/player/AudioPlaybackService.kt` | ✅ Готов (MediaSessionService + ExoPlayer + AudioFocus + WakeMode) |
| `MediaNotificationManager` | `ui/player/MediaNotificationManager.kt` | ✅ Готов (NotificationChannel + DefaultMediaNotificationProvider) |
| `AudioServiceController` | `ui/player/helpers/AudioServiceController.kt` | ✅ Готов (MediaController connection + `playAudio()` + `playAudioPlaylist()` API) |
| DataStore ключ | `SettingsRepositoryImpl.kt` line ~84: `KEY_ENABLE_BACKGROUND_AUDIO` | ✅ Готов (чтение line ~221 и запись line ~334) |
| AppSettings | `domain/model/AppSettings.kt` line ~50: `enablePersistentAudioPlayback: Boolean = false` | ✅ Готов |
| PlayerViewModel state | `PlayerViewModel.kt` line ~67: `enablePersistentAudioPlayback` в `PlayerState` | ✅ Готов (sync from settings at line ~172) |
| Routing в loader | `PlayerMediaLoaderManager.kt` line ~155: `isAudioFile && isPersistentAudioEnabled` → `playAudioViaService()` | ✅ Готов |
| AndroidManifest | `.ui.player.AudioPlaybackService` с `foregroundServiceType="mediaPlayback"`, `exported="true"` | ✅ Готов |
| Permissions | `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + `POST_NOTIFICATIONS` + `WAKE_LOCK` | ✅ Declared |
| Иконка уведомления | `res/drawable/ic_notification_audio.xml` (music note 24dp) | ✅ Существует |
| Строки UI | `strings.xml`: `background_audio_setting_title/summary` + `settings_category_audio` (EN/RU/UK) | ⚠️ RU/UK summary НЕ переведены |
| PlayerActivity init | line ~1484: `audioServiceController = AudioServiceController(this)` | ✅ Готов |
| PlayerActivity destroy | line ~3034-3040: `audioServiceController?.release()` | ✅ Готов |
| PlayerActivity → Loader | line ~1546: `audioServiceController` передан в `PlayerMediaLoaderManager` constructor | ✅ Готов |
| Backup | `BackupMapper.kt` + `BackupData.kt` — `enablePersistentAudioPlayback` mapped | ✅ Готов |

### Что НЕ реализовано (GAP)

1. **Settings UI** — нет Switch-переключателя в `fragment_settings_playback.xml`
2. **PlaybackSettingsFragment** — нет кода биндинга `enablePersistentAudioPlayback` через SettingsViewModel
3. **Filtered audio playlist** — `playAudioViaService()` передаёт только **одинарный** URI; нужно фильтровать аудио из списка и передавать как playlist
4. **Local-only guard** — нет проверки что файл локальный перед отправкой в сервис (сетевые URI не поддерживаются)
5. **Reconnect при возврате** — при возврате в Activity `PlayerView.player` не переподключается к работающему сервису
6. **Back-pressed → stop** — нет остановки сервиса при явном выходе из плеера
7. **Audio→non-audio switch → stop** — нет остановки сервиса при переключении на фото/видео в slideshow
8. **POST_NOTIFICATIONS permission** — нет runtime request на Android 13+ при включении toggle
9. **Lite flavor guard** — toggle и сервис не скрыты во флейворе `lite`
10. **STATE_ENDED + REPEAT_MODE** — сервис не поддерживает `REPEAT_MODE_ALL` для плейлиста и `REPEAT_MODE_ONE` для повтора трека
11. **resetPlaybackSection BUG** — `enablePersistentAudioPlayback` не сбрасывается при reset playback settings
12. **String translations** — `background_audio_setting_summary` в RU/UK не переведён (показывает English)
13. **Battery optimization warning** — нет one-time диалога о Samsung/Xiaomi/Huawei restrictions при opt-in

---

## Affected Files

| Файл | Роль |
|------|------|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Добавить Switch UI + Permission button |
| `ui/settings/fragments/PlaybackSettingsFragment.kt` | Биндинг switch + permission request + lite guard |
| `ui/settings/SettingsViewModel.kt` | Добавить `enablePersistentAudioPlayback` в `resetPlaybackSection()` |
| `ui/player/helpers/PlayerMediaLoaderManager.kt` | Filtered audio playlist + local-only guard + stop on non-audio switch |
| `ui/player/PlayerActivity.kt` | Reconnect при `onResume`; back-pressed → stop service |
| `ui/player/AudioPlaybackService.kt` | REPEAT_MODE_ALL/ONE support, fixed STATE_ENDED listener |
| `app_v2/build.gradle.kts` | Добавить `ENABLE_PERSISTENT_AUDIO_PLAYBACK = false` для `lite` flavor |
| `res/values-ru/strings.xml` | Перевод `background_audio_setting_summary` |
| `res/values-uk/strings.xml` | Перевод `background_audio_setting_summary` |

---

## Requirements

### R1 — Settings UI: переключатель "Background Playback"

Добавить Switch в `fragment_settings_playback.xml` в новую секцию "Audio" **после** секции "Touch Zones" (`headerTouchZones`).

**Landscape layout**: `layout-land/fragment_settings_playback.xml` **не существует** — fragment использует только portrait layout (автоматический fallback). Landscape не нужен.

**Lite flavor guard**: весь блок Background Audio обернуть в `visibility = if (BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) VISIBLE else GONE` (или скрыть программно в Fragment). В lite этот toggle НЕ должен быть виден.

**Структура XML** (аналогично существующим секциям — `switchPlayToEnd` и т.д.):

```xml
<!-- Background audio playback section -->
<TextView
    android:id="@+id/headerBackgroundAudio"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/colorSurfaceVariant"
    android:padding="@dimen/settings_padding_vertical"
    android:text="@string/settings_category_audio"
    android:textSize="@dimen/settings_group_header_text_size"
    android:textStyle="bold" />

<LinearLayout
    android:id="@+id/layoutBackgroundAudioRow"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="@dimen/settings_item_padding">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/background_audio_setting_title"
            android:textSize="@dimen/settings_item_text_size" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/background_audio_setting_summary"
            android:textSize="@dimen/settings_item_summary_text_size"
            android:textColor="@color/text_color_secondary" />
    </LinearLayout>

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchEnablePersistentAudioPlayback"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="@dimen/settings_switch_margin_end"
        android:contentDescription="@string/background_audio_setting_title" />
</LinearLayout>

<!-- Notification permission button (visible only on Android 13+ when permission not granted) -->
<Button
    android:id="@+id/btnNotificationPermission"
    style="@style/Widget.Material3.Button.TextButton"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginStart="@dimen/settings_item_padding"
    android:text="@string/manage_notifications_permission"
    android:visibility="gone" />
```

---

### R2 — PlaybackSettingsFragment: wire switch + permission request

Фрагмент использует **Flow + collect** паттерн с `isUpdatingFromSettings` флагом (НЕ LiveData observe).

```kotlin
// В collectSettingsFlow (существующий observeData() метод):
viewModel.settings.collect { settings ->
    isUpdatingFromSettings = true
    // ... existing bindings ...
    binding.switchEnablePersistentAudioPlayback.isChecked = settings.enablePersistentAudioPlayback
    isUpdatingFromSettings = false
}

// setOnCheckedChangeListener (в setupListeners()):
binding.switchEnablePersistentAudioPlayback.setOnCheckedChangeListener { _, isChecked ->
    if (isUpdatingFromSettings) return@setOnCheckedChangeListener
    if (isChecked) {
        // При включении: проверить POST_NOTIFICATIONS permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && !isNotificationPermissionGranted()) {
            requestNotificationPermission()
            // Не сохранять пока — сохранить в callback разрешения
            return@setOnCheckedChangeListener
        }
        // Показать one-time battery optimization dialog
        showBatteryOptimizationHintIfNeeded()
    }
    val current = viewModel.settings.value
    viewModel.updateSettings(current.copy(enablePersistentAudioPlayback = isChecked))
}
```

**Отдельный метод `saveEnablePersistentAudioPlayback()` НЕ НУЖЕН** — SettingsViewModel уже имеет generic `updateSettings(settings)` который используется для всех switch-ей.

**POST_NOTIFICATIONS Permission Flow:**
1. При opt-in toggle → check `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS)`
2. Если нет → `requestPermissionLauncher.launch(POST_NOTIFICATIONS)`
3. В callback: если granted → сохранить `enablePersistentAudioPlayback = true`; если denied → вернуть switch в off, показать Snackbar "Для фонового воспроизведения нужно разрешение на уведомления"
4. Кнопка `btnNotificationPermission` видима на API 33+ когда permission не granted — открывает App Notification Settings

**Lite flavor guard:**
```kotlin
// В onViewCreated():
if (!BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK) {
    binding.headerBackgroundAudio.visibility = View.GONE
    binding.layoutBackgroundAudioRow.visibility = View.GONE
    binding.btnNotificationPermission.visibility = View.GONE
}
```

---

### R3 — Filtered Audio Playlist + Local-Only Guard

**Текущее состояние**: `playAudioViaService(path)` вызывает `controller.playAudio(uri)` — одиночный файл.

**Требуемое поведение при сворачивании**: если текущий трек закончился — играть следующий аудиофайл из ресурса. Если аудиофайлов не осталось — остановить сервис. При достижении конца списка аудио — начать с начала.

**Решение — Filtered Audio Playlist**: из полного списка файлов ресурса отфильтровать только аудио, передать как playlist в сервис. ExoPlayer с `REPEAT_MODE_ALL` будет автоматически проигрывать все треки по кругу.

**Почему не "single file"**: если передать одиночный файл — при его окончании сервис не знает, какой следующий. Activity может быть убита ОС. Единственный надёжный способ — дать сервису весь список.

```kotlin
private fun playAudioViaService(path: String) {
    val controller = audioServiceController ?: return

    // === LOCAL-ONLY GUARD ===
    // AudioPlaybackService uses standard ExoPlayer without custom DataSource.Factory.
    // Network protocols (smb://, sftp://, ftp://, cloud://) are NOT supported.
    val resourceType = determineResourceType(path, null)
    if (resourceType != ResourceType.LOCAL) {
        Timber.w("playAudioViaService: network audio not supported for background, falling back")
        Toast.makeText(activity,
            R.string.background_audio_local_only_warning,
            Toast.LENGTH_SHORT).show()
        // Fall through to regular VideoPlayerManager path
        return
    }

    // === BUILD FILTERED AUDIO PLAYLIST ===
    val allFiles = viewModel.state.value.files
    val localAudioFiles = allFiles.filter { file ->
        file.type == MediaType.AUDIO
        && determineResourceType(file.path, null) == ResourceType.LOCAL
    }

    if (localAudioFiles.isEmpty()) return

    val startIndex = localAudioFiles.indexOfFirst { it.path == path }.coerceAtLeast(0)

    if (localAudioFiles.size == 1) {
        val uri = buildLocalUri(localAudioFiles[0].path)
        controller.playAudio(uri) { player ->
            activity.runOnUiThread { bindServiceAndPlayerView(player) }
        }
    } else {
        val uris = localAudioFiles.map { buildLocalUri(it.path) }
        controller.playAudioPlaylist(uris, startIndex) { player ->
            activity.runOnUiThread { bindServiceAndPlayerView(player) }
        }
    }
}

private fun buildLocalUri(path: String): Uri {
    val parsed = Uri.parse(path)
    return if (parsed.scheme == null) Uri.fromFile(java.io.File(path)) else parsed
}

private fun bindServiceAndPlayerView(player: Player) {
    bindServicePlaybackListener(player)
    binding.playerView.player = player
    loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
    binding.progressBar.isVisible = false
    Timber.d("PlayerMediaLoaderManager: service player bound to PlayerView")
}
```

**Почему сетевые файлы не работают**: `AudioPlaybackService` использует стандартный `ExoPlayer.Builder(this)` без`setMediaSourceFactory()`. Стандартный ExoPlayer поддерживает только `file://`, `content://`, `http://`, `https://`. Кастомные протоколы (`smb://`, `sftp://`, `ftp://`, `cloud://`) требуют инъекции `SmbDataSourceFactory`, `SftpDataSourceFactory` и т.д., которые живут в `VideoPlayerManager` и требуют credentials + network clients. Передача этой инфраструктуры в сервис через Hilt — значительная переработка, **вне scope Task 8**.

**Можно ли кэшировать сетевые файлы для сервиса?** Теоретически — да, через `UnifiedFileCache` (pre-download в `{cacheDir}/unified_network_cache/`). Для текущего трека — возможно. Для следующих треков — проблематично: foreground service МОЖЕТ делать сетевые операции, но не имеет доступа к credential repository и network clients. **Это задача для будущей версии (v2).**

---

### R4 — Audio→Non-Audio Switch: Stop Service

**Принцип**: сервис работает ТОЛЬКО когда текущий файл — аудио. При переключении на фото/видео — остановить. При следующем аудио — запустить заново.

В `PlayerMediaLoaderManager.playVideo()`, **перед** routing логикой:

```kotlin
fun playVideo(path: String) {
    val currentFile = viewModel.state.value.currentFile
    val isAudioFile = currentFile?.type == MediaType.AUDIO

    // === STOP SERVICE IF SWITCHING AWAY FROM AUDIO ===
    if (!isAudioFile && isServiceAudioActive) {
        Timber.d("playVideo: switching from audio to non-audio, stopping service")
        audioServiceController?.player?.stop()
        unbindServicePlaybackListener()
        binding.playerView.player = null  // detach service player
    }

    // ... existing routing logic ...
}
```

---

### R5 — Reconnect при возврате в PlayerActivity (onResume)

**Проблема**: пользователь сворачивает приложение → аудио продолжает через сервис.
При возврате `PlayerView.player` = `null` (или указывает на старый Activity ExoPlayer).

**Решение**: в `PlayerActivity.onResume()`, если сервис активен, переподключить PlayerView:

```kotlin
override fun onResume() {
    super.onResume()
    // ... existing onResume code ...
    reconnectToServiceIfActive()
}

private fun reconnectToServiceIfActive() {
    val controller = audioServiceController ?: return
    if (!controller.isConnected) return
    val currentPlayer = controller.player ?: return
    if (currentPlayer.isPlaying || currentPlayer.playbackState == Player.STATE_READY) {
        binding.playerView.player = currentPlayer
        mediaLoaderManager?.bindServicePlaybackListener(currentPlayer)
        Timber.d("PlayerActivity.reconnect: rebound PlayerView to service MediaController")
    }
}
```

⚠️ **Pitfall**: `MediaController` — async connection. Если сервис убит ОС,
`controller.isConnected` = `false` → восстановление через обычный `VideoPlayerManager`.

---

### R6 — Back-pressed: остановка сервиса

**Поведение**:
- Home / Recent Apps → сервис **продолжает** работать ✅ (уже работает)
- Back из PlayerActivity → сервис **останавливается** (пользователь явно закрыл плеер)

В `PlayerActivity` уже зарегистрирован `OnBackPressedCallback` (line ~1665). Добавить остановку сервиса **перед** `finish()`:

```kotlin
// В существующем OnBackPressedCallback:
override fun handleOnBackPressed() {
    // ... existing overlay/PDF/EPUB checks ...

    // Stop background audio service when user explicitly exits player
    audioServiceController?.player?.stop()

    // Default back behavior
    isEnabled = false
    onBackPressedDispatcher.onBackPressed()
}
```

**⚠️ Важно**: НЕ вызывать `stopSelf()` на сервисе напрямую из Activity.
`mediaController.stop()` → сервис получает команду через MediaSession → `onPlaybackStateChanged(STATE_IDLE)` → `AudioPlaybackService.onTaskRemoved()` проверяет `!playWhenReady` → `stopSelf()`.

---

### R7 — REPEAT_MODE + STATE_ENDED в AudioPlaybackService

**Требуемое поведение**:
- Playlist (>1 аудио): проигрывать все треки по кругу до ручной остановки → `REPEAT_MODE_ALL`
- Single file: проиграть и остановить → `REPEAT_MODE_OFF`
- Если пользователь включил repeat-one в PlayerView → `REPEAT_MODE_ONE` (играть один трек по кругу)

**Изменения в `AudioPlaybackService`:**

```kotlin
// В onCreate(), после создания exoPlayer:
// Default repeat mode — will be overridden by MediaController commands from UI
exoPlayer.repeatMode = Player.REPEAT_MODE_OFF

exoPlayer.addListener(object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        Timber.d("AudioPlaybackService: playbackState=$playbackState")
        if (playbackState == Player.STATE_ENDED) {
            // With REPEAT_MODE_ALL, STATE_ENDED never fires (ExoPlayer loops).
            // With REPEAT_MODE_ONE, STATE_ENDED never fires (ExoPlayer repeats).
            // STATE_ENDED only fires with REPEAT_MODE_OFF at end of last item.
            Timber.d("AudioPlaybackService: playback ended, stopping service")
            stopSelf()
        }
    }
})
```

**В `playAudioPlaylist()`:**
```kotlin
fun playAudioPlaylist(uris: List<Uri>, startIndex: Int = 0) {
    val currentPlayer = player ?: return
    val mediaItems = uris.map { MediaItem.fromUri(it) }
    currentPlayer.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
    currentPlayer.repeatMode = Player.REPEAT_MODE_ALL  // Loop audio playlist
    currentPlayer.prepare()
    currentPlayer.play()
}
```

**В `playAudio()` (single file):**
```kotlin
fun playAudio(uri: Uri) {
    val currentPlayer = player ?: return
    currentPlayer.setMediaItem(MediaItem.fromUri(uri))
    currentPlayer.repeatMode = Player.REPEAT_MODE_OFF  // Play once and stop
    currentPlayer.prepare()
    currentPlayer.play()
}
```

**Repeat-one from UI**: `MediaController implements Player`, поэтому когда пользователь нажимает repeat в PlayerView контролах, `MediaController.setRepeatMode(REPEAT_MODE_ONE)` автоматически транслируется в сервисный ExoPlayer через MediaSession. Дополнительный код не нужен.

---

### R8 — POST_NOTIFICATIONS Permission Management

**Android 13+ (API 33)**: `POST_NOTIFICATIONS` — runtime permission. Без неё:
- Сервис запустится и будет играть ✅
- Но уведомление **не покажется** ❌ → пользователь не увидит controls
- На lock screen — нет управления

**Реализация в PlaybackSettingsFragment:**

1. **Permission launcher** (в `onCreate()`):
```kotlin
private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        val current = viewModel.settings.value
        viewModel.updateSettings(current.copy(enablePersistentAudioPlayback = true))
        updateNotificationPermissionButtonVisibility()
    } else {
        binding.switchEnablePersistentAudioPlayback.isChecked = false
        Snackbar.make(binding.root,
            R.string.notification_permission_required_for_background,
            Snackbar.LENGTH_LONG).show()
    }
}
```

2. **Permission button** (рядом с другими permission кнопками в settings):
```kotlin
binding.btnNotificationPermission.setOnClickListener {
    // Open system app notification settings
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
    }
    startActivity(intent)
}

private fun updateNotificationPermissionButtonVisibility() {
    binding.btnNotificationPermission.visibility =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && !isNotificationPermissionGranted())
            View.VISIBLE else View.GONE
}
```

---

### R9 — Lite Flavor: Disable Background Audio

В `app_v2/build.gradle.kts`, добавить `ENABLE_PERSISTENT_AUDIO_PLAYBACK` BuildConfig field:

```gradle
// standard flavor:
buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")

// lite flavor:
buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "false")

// photos flavor:
buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "false")  // SUPPORT_AUDIO = false

// legacy flavor:
buildConfigField("boolean", "ENABLE_PERSISTENT_AUDIO_PLAYBACK", "true")
```

**Guard в PlayerMediaLoaderManager.playVideo():**
```kotlin
val isPersistentAudioEnabled = viewModel.state.value.enablePersistentAudioPlayback
    && BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK  // compile-time guard
```

---

### R10 — Battery Optimization One-Time Dialog

При **первом** включении toggle `enablePersistentAudioPlayback` показать информационный диалог:

```
Для стабильной работы фонового воспроизведения:
• Samsung: Настройки → Обслуживание устройства → Батарея → FastMediaSorter → Не ограничивать
• Xiaomi: Настройки → Приложения → FastMediaSorter → Автозапуск
• Huawei: Настройки → Батарея → Запуск приложений → FastMediaSorter → Управление вручную
```

Контролировать через DataStore key `has_shown_battery_hint_background_audio` (one-time).

Также добавить эту информацию в FAQ/docs (TROUBLESHOOTING.md).

---

### R11 — Reset Bug Fix

В `SettingsViewModel.resetPlaybackSection()` добавить `enablePersistentAudioPlayback = false`:

```kotlin
fun resetPlaybackSection() {
    viewModelScope.launch {
        val current = settingsRepository.getSettings()
        settingsRepository.updateSettings(current.copy(
            // ... existing resets ...
            enablePersistentAudioPlayback = false  // ← ADD THIS
        ))
        // ... existing per-type hint resets ...
    }
}
```

---

### R12 — String Translations Fix

**RU** (`values-ru/strings.xml`):
```xml
<string name="background_audio_setting_summary">Продолжить воспроизведение аудио при сворачивании приложения</string>
```

**UK** (`values-uk/strings.xml`):
```xml
<string name="background_audio_setting_summary">Продовжити відтворення аудіо при згортанні додатку</string>
```

**Новые строки (все 3 локали):**
```xml
<!-- EN -->
<string name="background_audio_local_only_warning">Background playback is available only for local files</string>
<string name="notification_permission_required_for_background">Notification permission required for background playback controls</string>
<string name="manage_notifications_permission">Manage notification permission</string>
<string name="battery_optimization_hint_title">Battery optimization</string>
<string name="battery_optimization_hint_message">For stable background playback on some devices, disable battery optimization for FastMediaSorter in system settings.</string>

<!-- RU -->
<string name="background_audio_local_only_warning">Фоновое воспроизведение доступно только для локальных файлов</string>
<string name="notification_permission_required_for_background">Для управления фоновым воспроизведением нужно разрешение на уведомления</string>
<string name="manage_notifications_permission">Управление разрешением на уведомления</string>
<string name="battery_optimization_hint_title">Оптимизация батареи</string>
<string name="battery_optimization_hint_message">Для стабильного фонового воспроизведения на некоторых устройствах отключите оптимизацию батареи для FastMediaSorter в системных настройках.</string>

<!-- UK -->
<string name="background_audio_local_only_warning">Фонове відтворення доступне лише для локальних файлів</string>
<string name="notification_permission_required_for_background">Для керування фоновим відтворенням потрібен дозвіл на сповіщення</string>
<string name="manage_notifications_permission">Керування дозволом на сповіщення</string>
<string name="battery_optimization_hint_title">Оптимізація батареї</string>
<string name="battery_optimization_hint_message">Для стабільного фонового відтворення на деяких пристроях вимкніть оптимізацію батареї для FastMediaSorter у системних налаштуваннях.</string>
```

---

## Implementation Plan

- [ ] **Step 1** (Build config): Добавить `ENABLE_PERSISTENT_AUDIO_PLAYBACK` в `app_v2/build.gradle.kts` для всех flavors (`true` standard/legacy, `false` lite/photos).
- [ ] **Step 2** (Settings UI): Добавить `switchEnablePersistentAudioPlayback` + `btnNotificationPermission` в `fragment_settings_playback.xml`.
- [ ] **Step 3** (Fragment wire): В `PlaybackSettingsFragment.kt` — биндинг switch, permission request, lite guard, battery hint.
- [ ] **Step 4** (Reset fix): В `SettingsViewModel.resetPlaybackSection()` добавить `enablePersistentAudioPlayback = false`.
- [ ] **Step 5** (Strings): Исправить RU/UK переводы `background_audio_setting_summary`. Добавить новые строки (local_only_warning, permission, battery).
- [ ] **Step 6** (Playlist + local guard): Переписать `PlayerMediaLoaderManager.playAudioViaService()` — filtered audio playlist + local-only guard + Toast для сетевых.
- [ ] **Step 7** (Stop on non-audio): В `PlayerMediaLoaderManager.playVideo()` — stop service при переключении на non-audio файл.
- [ ] **Step 8** (REPEAT_MODE): В `AudioPlaybackService` — `REPEAT_MODE_ALL` для playlist, `REPEAT_MODE_OFF` для single, respect repeat-one из UI.
- [ ] **Step 9** (STATE_ENDED): В `AudioPlaybackService` — обновить listener, `stopSelf()` только при REPEAT_MODE_OFF в конце.
- [ ] **Step 10** (Reconnect): В `PlayerActivity.onResume()` добавить `reconnectToServiceIfActive()`.
- [ ] **Step 11** (Back → stop): В `PlayerActivity` existing `OnBackPressedCallback` — добавить `audioServiceController?.player?.stop()`.
- [ ] **Step 12** (Battery hint): Добавить DataStore key `has_shown_battery_hint_background_audio` + one-time MaterialAlertDialog + text в TROUBLESHOOTING.md.
- [ ] **Step 13** (Build test): Собрать `standard` и `lite` — проверить что toggle виден/скрыт.
- [ ] **Step 14** (Smoke test): Enable toggle → play audio → minimize → notification visible → controls work → reopen → UI synced → Back → notification gone → track ends → next audio plays → end of list → loops.

---

## Edge Cases & Risks

### EC-1: Двойное воспроизведение при возврате
**Риск**: `PlayerActivity.onResume()` начнёт новое воспроизведение через `VideoPlayerManager` параллельно с сервисом.
**Guard**: в `PlayerMediaLoaderManager.playVideo()` уже есть проверка `isPersistentAudioEnabled` + `audioServiceController != null` — убедиться, что reconnect (R5) происходит **до** попытки загрузки нового файла через VideoPlayerManager. Добавить check: `if (isServiceAudioActive) return` в начале `playVideo()`.

### EC-2: Потеря позиции в треке при реконнекте
`MediaController` синхронизирует позицию автоматически через MediaSession — это поведение по умолчанию в Media3. Дополнительный код не нужен.

### EC-3: Сетевые аудиофайлы (SFTP/FTP/SMB/Cloud)
**Проблема**: `AudioPlaybackService` использует стандартный ExoPlayer без кастомных `DataSource.Factory`. Сетевые протоколы (`smb://`, `sftp://`, `ftp://`, `cloud://`) не поддерживаются.
**Причина**: Кастомные `SmbDataSourceFactory`, `SftpDataSourceFactory`, `FtpDataSourceFactory`, `CloudDataSourceFactory` живут в `VideoPlayerManager` и требуют credentials + network clients, которых нет в сервисе (нет Hilt injection).
**Возможность кэширования**: `UnifiedFileCache` может скачать текущий файл в local cache. Но для следующих файлов: foreground service МОЖЕТ делать сетевые операции, но не имеет доступа к `SettingsRepository` credentials и network client instances.
**Решение v1**: Toast "Background playback available for local files only" + fallback на VideoPlayerManager.
**Перспектива v2**: Добавить `@AndroidEntryPoint` в `AudioPlaybackService`, inject network client factories через Hilt, или pre-cache N следующих файлов через `UnifiedFileCache`.

### EC-4: Конфликт AudioFocus с другими приложениями
`AudioPlaybackService` создаёт ExoPlayer с `setHandleAudioBecomingNoisy(true)` + `audioAttributes` с `handleAudioFocus=true`. Достаточно. `VideoPlayerManager` (line ~467) тоже с `handleAudioFocus=true`.

### EC-5: AudioFocus conflict при audio→video switch (internal)
**Риск**: Если сервис ещё играет а `VideoPlayerManager` создаёт новый ExoPlayer для видео — два player-а конкурируют за AudioFocus.
**Guard**: R4 (`playVideo()`) останавливает сервис **перед** созданием нового ExoPlayer для non-audio. Для video-after-audio — сервис уже остановлен.

### EC-6: Флейвор `lite`
`ENABLE_PERSISTENT_AUDIO_PLAYBACK = false` в lite. Toggle скрыт. `AudioPlaybackService` остаётся в APK (безвредно, увеличивает размер минимально), но routing guard `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` в `playVideo()` не даст его активировать. Удаление сервиса из APK через `sourceSets` — лишняя сложность для минимального выигрыша. `photos` flavor — аналогично (`SUPPORT_AUDIO = false`, аудио вообще не появится).

### EC-7: Android 12+ — foreground service restrictions
`MediaSessionService` запускается через `MediaController` binding — это **exempted** путь (не blocked по Android restrictions для `TYPE_MEDIA`). Не используется `startForegroundService()` явно.

### EC-8: Android 13+ — POST_NOTIFICATIONS
Без runtime permission notification не показывается. Сервис работает, но пользователь не видит controls.
**Guard**: R8 — запрос permission при opt-in toggle + видимая кнопка Manage в settings.

### EC-9: Android 14 — foreground service type enforcement
`foregroundServiceType="mediaPlayback"` **обязателен** и уже задан. Отсутствие → `MissingForegroundServiceTypeException`.

### EC-10: Android 15 (target SDK 35) — music start limitations
`mediaPlayback` FGS может запускаться если есть active MediaSession. `MediaSessionService` создаёт MediaSession в `onCreate()` — это разрешённый путь. **Требует тестирования на эмуляторе API 35.**

### EC-11: Samsung One UI / Xiaomi MIUI / Huawei EMUI — агрессивный battery kill
Эти вендоры агрессивно убивают фоновые сервисы. `WAKE_MODE_LOCAL` + `MediaSession` + foreground notification обычно защищают, но не всегда.
**Guard**: R10 — one-time dialog при opt-in с инструкциями по вендорам. Информация в TROUBLESHOOTING.md.

### EC-12: Утечка MediaController при ротации экрана
`AudioServiceController` создаётся в PlayerActivity (не ViewModel). При ротации Activity пересоздаётся → `onDestroy()` вызывает `release()` → MediaController disconnect. Сервис продолжает играть. Новый Activity создаёт новый `AudioServiceController` → `reconnectToServiceIfActive()` (R5) переподключает. **Это штатное поведение.**

### EC-13: Rapid file switching (swipe) при включённом background audio
Каждый swipe может создать новый `connect()` → `buildAsync()`. `MediaController` connection — async. Race condition: callback от предыдущего connect приходит после нового.
**Guard**: в callback `playAudio`/`playAudioPlaylist` проверять что `path` ещё актуален (сравнить `viewModel.state.value.currentFile?.path`).

### EC-14: Slideshow auto-advance при mixed ресурсе
Slideshow переключает: photo → audio → photo → audio. Каждый audio запускает `playAudioViaService()` с полным filtered playlist. Повторный вызов `playAudioPlaylist()` на уже запущенном сервисе = `setMediaItems()` → ExoPlayer заменяет текущий playlist (корректно). Но лишние reconnect-ы создают overhead.
**Optimization (optional)**: если сервис уже играет нужный файл — не переподключать.

### EC-15: REPEAT_MODE_ALL + пользователь хочет остановить
С `REPEAT_MODE_ALL` playlist зациклен — сервис никогда не остановится сам. Остановка только через:
- Back из PlayerActivity (R6)
- Stop button в notification
- Stop button в lock screen controls
- Force stop app
Это ожидаемое поведение (аналог YouTube Music / Spotify).

---

## Notes on Architecture

### Текущая архитектура (правильная)
- **Видео** остаётся в Activity через `VideoPlayerManager` (требует Surface/TextureView)
- **Аудио** рутится через `AudioPlaybackService` только когда `enablePersistentAudioPlayback = true`
- `MediaController implements Player` → `binding.playerView.player = mediaController` работает без изменений в `ExoPlayerControlsManager`. Seekbar, play/pause, timeline — всё через стандартный `Player` interface.

### Разграничение с другими audio фичами
```
enablePersistentAudioPlayback = true
├── Routing: PlayerMediaLoaderManager → AudioPlaybackService (via AudioServiceController)
├── Player: Service ExoPlayer (dedicated instance with MediaSession)
├── Notification: MediaNotificationManager (lock screen + notification shade)
├── Survives: app minimize, screen lock, task switch
└── Stop: Back from Player, notification stop, end of playlist (none — loops)

enableSlideshowBackgroundMusic = true
├── Routing: SlideshowController → BackgroundMusicManager (with resource picker)
├── Player: Activity ExoPlayer (no service, dies with Activity)
├── Notification: None
├── Survives: only while app is in foreground
└── Stop: slideshow stop, app minimize

enablePhotosDuringAudio = true
├── Routing: PlayerActivity → AudioSlideshowPhotoModeManager
├── Player: Activity ExoPlayer (standard VideoPlayerManager)
├── Notification: None (just photo overlay in Activity)
├── Survives: only while app is in foreground
└── Stop: audio stop, app minimize
```

### Network audio — v2 roadmap
Для поддержки сетевых аудиофайлов в background service нужно:
1. Добавить `@AndroidEntryPoint` в `AudioPlaybackService`
2. Inject network client factories (`SmbClient`, `SftpClient`, `FtpClient`) через Hilt Module
3. Implement credential resolution в сервисе (из `SettingsRepository`)
4. Создать composite `DataSource.Factory` который автоматически определяет protocol по URI scheme
5. Или: pre-cache следующий файл через `UnifiedFileCache` пока текущий играет
