# Phase 4 — VR Engine & Host

**Статус:** 🔴 Not started  
**Оценка:** ~6h (крупнейшая фаза; требует Quest-девайса с задачи 4.4)  
**Spec-ref:** [../spec_openxr_3d_player.md](../spec_openxr_3d_player.md) — Sections 5.3, 5.4, Steps 8-11  
**Блокируется:** Phase 2 (нужны shared contracts)  
**Требует девайс:** Quest 3 / Quest Pro начиная с задачи 4.4

---

## Предусловие

- [ ] Phase 2 завершена
- [ ] `assembleVrDebug` собирается (Phase 1)
- [ ] Quest 3 или Quest Pro доступен и подключён по ADB для задач 4.4+

---

## Задача 4.1 — Playback engine abstraction

### 4.1.1 — `VrPlaybackEngine` interface

**Файл:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/playback/VrPlaybackEngine.kt`  
**Бюджет:** ≤ 80 строк

- [ ] Создать interface (Surface приходит в `prepare()`, не как property — см. ADR-2):

```kotlin
/**
 * Backend-agnostic движок воспроизведения для VR.
 *
 * Жизненный цикл Surface:
 *   VrPlayerActivity создаёт OpenXR session →
 *   OpenXrSessionManager.createSwapchain() возвращает Surface →
 *   VrPlayerActivity вызывает prepare(source, surface).
 *
 * Surface НЕ является property: он недоступен до инициализации XR-сессии.
 */
interface VrPlaybackEngine {
    suspend fun prepare(source: VrPlaybackSource, outputSurface: android.view.Surface)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    suspend fun release()
    fun getAvailableAudioTracks(): List<PlaybackTrack>
    fun getAvailableSubtitleTracks(): List<PlaybackTrack>
    fun selectAudioTrack(index: Int)
    fun selectSubtitleTrack(index: Int)
}
```

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/playback/VrPlaybackEngine.kt" "VrPlaybackEngine" "VR playback engine interface; Surface passed via prepare(), not as property (ADR-2)"
```

### 4.1.2 — `ExoVrPlaybackEngine` (fallback / PoC)

**Файл:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/playback/ExoVrPlaybackEngine.kt`  
**Бюджет:** ≤ 300 строк

- [ ] Реализовать через `Media3/ExoPlayer` — используется как fallback если LibVLC не пройдёт gate review
- [ ] Surface передаётся ExoPlayer через `player.setVideoSurface(outputSurface)` в `prepare()`
- [ ] Timber logging — не `Log.d()`

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/playback/ExoVrPlaybackEngine.kt" "ExoVrPlaybackEngine" "ExoPlayer-based VR playback engine; fallback implementation for backend gate review"
```

### 4.1.3 — `LibVlcVrPlaybackEngine` (primary candidate)

**Файл:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/playback/LibVlcVrPlaybackEngine.kt`  
**Бюджет:** ≤ 350 строк

- [ ] **Сначала проверить:** LibVLC AAR доступен для arm64-v8a (Quest ABI):

  ```powershell
  .\gradlew.bat :app_v2:dependencies | Select-String "libvlc"
  ```

- [ ] Реализовать через LibVLC API, передавая Surface в `mediaPlayer.setVideoSurface(outputSurface)` в `prepare()`
- [ ] **Если LibVLC недоступен** — пропустить эту задачу и отметить в backend gate review (Phase 5.1)

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/playback/LibVlcVrPlaybackEngine.kt" "LibVlcVrPlaybackEngine" "LibVLC-based VR playback engine; primary candidate pending backend gate review"
```

### 4.1.4 — Hilt binding в `VrModule`

- [ ] Добавить binding в `VrModule.kt` (созданный в Phase 1.3.3):

  ```kotlin
  @Binds
  abstract fun bindVrPlaybackEngine(impl: LibVlcVrPlaybackEngine): VrPlaybackEngine
  // Заменить на ExoVrPlaybackEngine если gate review блокирует LibVLC
  ```

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt" "VrModule" "Added VrPlaybackEngine Hilt binding; points to LibVlcVrPlaybackEngine (primary candidate)"
```

---

## Задача 4.2 — VR Player host activities

### 4.2.1 — `VrPhoneFallbackActivity`

**Файл:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPhoneFallbackActivity.kt`  
**Бюджет:** ≤ 150 строк

- [ ] Простой экран "FastMediaSorter VR — требуется шлем Quest"
- [ ] Кнопка "Закрыть" (finish())
- [ ] Кнопка "Открыть standard версию" (Intent на `com.sza.fastmediasorter`)
- [ ] TalkBack content descriptions
- [ ] Добавить string resources (EN/RU/UK):

  ```
  vr_phone_fallback_title
  vr_phone_fallback_message
  vr_phone_fallback_close
  vr_phone_fallback_open_standard
  ```

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPhoneFallbackActivity.kt" "VrPhoneFallbackActivity" "Fallback screen for vr flavor on non-headset device"
```

### 4.2.2 — `VrControlOverlayManager`

**Файл:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt`  
**Бюджет:** ≤ 250 строк

- [ ] Управляет overlay команд в VR:  
  Play/Pause, Seek ±15s, Previous/Next, Exit — через OpenXR action bindings (controller ray-cast)
- [ ] Получает `PlaybackCommandSet.forVrPlayback()` — file operations недоступны
- [ ] Input model: controller ray-cast через OpenXR XR action set  
  _(гарантия: hand tracking и gaze — вне scope этой фазы)_
- [ ] Overlay появляется по нажатию Menu/Back на контроллере, скрывается автоматически через 5с

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt" "VrControlOverlayManager" "VR overlay: controller ray-cast input, PlaybackCommandSet forVrPlayback (no file ops)"
```

### 4.2.3 — `VrPlayerActivity`

**Файл:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`  
**Бюджет:** ≤ 350 строк  
**Требует:** задачи 4.1, 4.2.1, 4.2.2 завершены

- [ ] Thin host: никакой бизнес-логики напрямую — только координация
- [ ] В `onCreate`: вызвать `PlayerEntryCoordinator.resolveEntry()` → если `ShowPhoneFallbackScreen` → стартовать `VrPhoneFallbackActivity` и завершить
- [ ] В `onResume`: инициализировать `OpenXrSessionManager` (задача 4.3)
- [ ] После получения Surface от `OpenXrSessionManager` → вызвать `vrPlaybackEngine.prepare(source, surface)`
- [ ] `VrControlOverlayManager` инициализируется после готовности OpenXR session

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" "VrPlayerActivity" "Thin VR host activity; routes to phone fallback; coordinates OpenXrSessionManager + VrPlaybackEngine"
```

---

## Задача 4.3 — OpenXR session и renderer

> Эти задачи требуют Quest-устройства для финальной проверки.

### 4.3.1 — `OpenXrSessionManager`

**Файл:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt`  
**Бюджет:** ≤ 350 строк

- [ ] Инициализирует OpenXR instance, system и session
- [ ] Создаёт swapchain → из swapchain получает `android.view.Surface`
- [ ] Возвращает Surface через suspend fun или callback:

  ```kotlin
  suspend fun createSessionAndGetSurface(activity: Activity): android.view.Surface
  ```

- [ ] Управляет event loop (`xrPollEvent`) в coroutine (`Dispatchers.IO`)
- [ ] `release()` корректно завершает XR session и instance
- [ ] Timber logging для XR lifecycle событий

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt" "OpenXrSessionManager" "OpenXR session init; creates swapchain and returns Surface for VrPlaybackEngine.prepare()"
```

### 4.3.2 — `VrStereoRenderer`

**Файл:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt`  
**Бюджет:** ≤ 400 строк

- [ ] Рендерит per-eye frame: SBS → left eye left half, right eye right half; OU → left eye top half, right eye bottom half
- [ ] Подход: GLSL UV-crop shader (предпочтителен перед dual swapchain)
- [ ] Получает `StereoMode` из `StereoDetectionFacade` — рендерер не детектирует сам
- [ ] Вызывает `xrEndFrame()` в render loop
- [ ] Для 2D-контента — простой cinema mode (один экран, заполнение)

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt" "VrStereoRenderer" "Per-eye stereo renderer: UV-crop GLSL shader for SBS/OU; cinema mode for 2D; StereoMode injected externally"
```

---

## Задача 4.4 — Ручная проверка на Quest (минимальная)

> Нужен физический Quest 3 или Quest Pro.

- [ ] Собрать и установить VR debug APK:

  ```powershell
  .\gradlew.bat assembleVrDebug
  adb install -r app_v2/build/outputs/apk/vr/debug/app-vr-debug.apk
  ```

- [ ] Открыть любой видеофайл → убедиться что воспроизводится (cinema mode)
- [ ] Открыть SBS-файл → убедиться что показывается per-eye rendering
- [ ] Контроллер → нажать Menu → overlay появляется
- [ ] Play/Pause через overlay → работает
- [ ] Exit → возвращает в Horizon Home

---

## Финальная проверка Phase 4

- [ ] `assembleVrDebug` — SUCCESS, ABI `arm64-v8a`
- [ ] Базовое воспроизведение на Quest — работает
- [ ] SBS/OU рендеринг — работает (хотя бы для одного тестового файла)
- [ ] Phone fallback — `VrPhoneFallbackActivity` показывается на телефоне с `vr` APK
- [ ] Timber logging видно в logcat без `Log.d()`

## Gate → Phase 5

Обновить строку в [00_OVERVIEW.md](00_OVERVIEW.md): `Phase 4 | 🟢 Done`

---

## Заметки разработчика

```
Дата начала:
Дата завершения:
Quest model:
LibVLC статус (доступен / не доступен для arm64):
Проблемы:
Что изменилось по сравнению со спеком:
```
