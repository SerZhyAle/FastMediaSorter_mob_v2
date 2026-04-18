# Phase 2 — Shared Contracts

**Статус:** 🔴 Not started  
**Оценка:** ~2h  
**Spec-ref:** [../spec_openxr_3d_player.md](../spec_openxr_3d_player.md) — Section 5.2, Step 4  
**Блокирует:** Phase 3 (CTA), Phase 4 (VR engine)  
**Блокируется:** Phase 1 (нужен `vr` flavor в Gradle)

---

## Предусловие

- [ ] Phase 1 завершена: `assembleVrDebug` проходит

---

## Назначение фазы

Все shared contracts живут в `app_v2/src/main/java/...` — они доступны всем flavors (`standard`, `vr`, `lite` и т.д.).  
Ни один из этих классов **не должен** зависеть от: `PlayerView`, `ActivityPlayerUnifiedBinding`, `ExoPlayer`, `LibVLC`, `PlayerActivity`.

Структура пакетов:

- `ui/player/contracts/` — facades и models
- `ui/player/entry/` — coordinator

---

## Задача 2.1 — `StereoDetectionFacade`

**Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/StereoDetectionFacade.kt`  
**Бюджет:** ≤ 100 строк  
**Зависит от:** существующего `StereoDetector.kt`

- [ ] **2.1.1** Прочитать `StereoDetector.kt` — понять текущий API и типы `StereoMode`
- [ ] **2.1.2** Создать интерфейс:

```kotlin
/**
 * Backend-agnostic фасад над детектором стерео-режима.
 * Скрывает crop-preview и render-specific детали StereoVideoProcessor.
 * Доступен всем flavors через main source set.
 */
interface StereoDetectionFacade {
    fun detectMode(mediaFile: MediaFile): StereoMode
    fun isStereoContent(mediaFile: MediaFile): Boolean
}
```

- [ ] **2.1.3** Создать `StereoDetectionFacadeImpl`, делегирующий в существующий `StereoDetector`
- [ ] **2.1.4** Добавить скелет unit test `StereoDetectionFacadeTest` в `test/` source set

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/StereoDetectionFacade.kt" "StereoDetectionFacade" "Backend-agnostic stereo detection facade; wraps existing StereoDetector"
```

---

## Задача 2.2 — `PlaybackCommandModel`

**Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackCommandModel.kt`  
**Бюджет:** ≤ 150 строк

- [ ] **2.2.1** Создать data class / sealed class нормализованных playback команд:

```kotlin
/**
 * Нормализованная модель команд playback.
 * VR command set НЕ включает copy/move/delete.
 * Standard command set содержит полный набор file operations.
 */
sealed class PlaybackCommand {
    data object Play : PlaybackCommand()
    data object Pause : PlaybackCommand()
    data object SeekForward : PlaybackCommand()
    data object SeekBackward : PlaybackCommand()
    data object PreviousFile : PlaybackCommand()
    data object NextFile : PlaybackCommand()
    data object OpenControls : PlaybackCommand()
    data object Exit : PlaybackCommand()
    // File operations — только в standard; в vr недоступны
    data object MoveFile : PlaybackCommand()
    data object CopyFile : PlaybackCommand()
    data object DeleteFile : PlaybackCommand()
}

data class PlaybackCommandSet(
    val available: Set<PlaybackCommand>
) {
    companion object {
        fun forVrPlayback() = PlaybackCommandSet(
            available = setOf(Play, Pause, SeekForward, SeekBackward,
                              PreviousFile, NextFile, OpenControls, Exit)
        )
        fun forStandardPlayback() = PlaybackCommandSet(
            available = PlaybackCommand::class.sealedSubclasses
                .mapNotNull { it.objectInstance }.toSet()
        )
    }
}
```

- [ ] **2.2.2** Добавить скелет unit test `PlaybackCommandModelTest`:  
  Проверить что `forVrPlayback()` не содержит `MoveFile`, `CopyFile`, `DeleteFile`

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackCommandModel.kt" "PlaybackCommandModel" "Normalized playback command model; VR set excludes file operations"
```

---

## Задача 2.3 — `PlaybackPreferencesFacade`

**Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackPreferencesFacade.kt`  
**Бюджет:** ≤ 200 строк  
**Зависит от:** `SettingsRepository`, resume repository

- [ ] **2.3.1** Прочитать текущий код resume и speed preferences в `PlayerViewModel` (какие репозитории использует)
- [ ] **2.3.2** Создать интерфейс:

```kotlin
/**
 * Унифицированный доступ к playback preferences без зависимости на
 * конкретный backend (ExoPlayer, LibVLC) или Activity lifecycle callbacks.
 * Используется как standard, так и vr flavor.
 */
interface PlaybackPreferencesFacade {
    fun getPlaybackSpeed(): Float
    fun getResumePosition(mediaId: String): Long
    fun saveResumePosition(mediaId: String, positionMs: Long)
    fun getPreferredAudioTrackIndex(): Int
    fun getPreferredSubtitleTrackIndex(): Int
    fun isStereoPlaybackEnabled(): Boolean
}
```

- [ ] **2.3.3** Создать `PlaybackPreferencesFacadeImpl`, извлечённый из текущего `PlayerViewModel`
- [ ] **2.3.4** Добавить скелет unit test `PlaybackPreferencesFacadeTest`

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlaybackPreferencesFacade.kt" "PlaybackPreferencesFacade" "Playback preferences facade; extracted from PlayerViewModel; backend-agnostic"
```

---

## Задача 2.4 — `PlayerSourceResolver`

**Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerSourceResolver.kt`  
**Бюджет:** ≤ 220 строк  
**Зависит от:** repositories, network/cloud resolvers, file metadata

- [ ] **2.4.1** Прочитать как `VideoPlayerManager` и `PlayerMediaLoaderManager` сейчас получают URI/source
- [ ] **2.4.2** Создать интерфейс и модель:

```kotlin
sealed class VrPlaybackSource {
    data class LocalFile(val uri: Uri, val stereoMode: StereoMode) : VrPlaybackSource()
    data class NetworkFile(val url: String, val credentials: NetworkCredentials?, val stereoMode: StereoMode) : VrPlaybackSource()
    // Расширяется при необходимости
}

/**
 * Преобразует MediaFile / URI в backend-ready VrPlaybackSource.
 * Не зависит от PlayerView или ActivityPlayerUnifiedBinding.
 */
interface PlayerSourceResolver {
    suspend fun resolve(mediaFile: MediaFile): VrPlaybackSource
}
```

- [ ] **2.4.3** Создать `PlayerSourceResolverImpl`, делегирующий в существующие resolvers
- [ ] **2.4.4** Добавить скелет unit test `PlayerSourceResolverTest`

**Dev log:**

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/PlayerSourceResolver.kt" "PlayerSourceResolver" "Resolves MediaFile to VrPlaybackSource; extracted from VideoPlayerManager; no PlayerView dependency"
```

---

## Финальная проверка Phase 2

- [ ] `./gradlew.bat assembleStandardDebug` — SUCCESS
- [ ] `./gradlew.bat assembleVrDebug` — SUCCESS
- [ ] `./gradlew.bat testStandardDebugUnitTest` — все скелеты проходят
- [ ] Ни один из новых классов не импортирует `PlayerView`, `ExoPlayer`, `LibVLC` или Android-lifecycle напрямую

## Gate → Phase 3 и Phase 4

Обновить строку в [00_OVERVIEW.md](00_OVERVIEW.md): `Phase 2 | 🟢 Done`

---

## Заметки разработчика

> Заполняй по мере работы.

```
Дата начала:
Дата завершения:
Проблемы:
Что изменилось по сравнению со спеком:
```
