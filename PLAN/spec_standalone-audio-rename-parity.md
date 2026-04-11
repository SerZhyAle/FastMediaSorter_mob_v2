# Specification: NEW.5 — Standalone Player Rename Button (Audio Files)

**Status:** Draft  
**Date:** 2026-04-11  
**Tier:** 3 — Moderate (3–5h, medium risk)  
**Companion spec:** NEW.4 — `spec_standalone-doc-rename-parity.md` (shares root cause and core mechanism; adds audio-specific URI lifecycle step)

В `StandalonePlayerActivity` кнопка `btnRenameCmd` отображается при воспроизведении аудиофайлов
(MP3, FLAC, OGG, WAV, M4A и др.), но click-listener не зарегистрирован — нажатие игнорируется.
Та же проблема, что и в NEW.4, но с дополнительным аудио-специфичным риском: SAF-rename
возвращает новый URI, который необходимо передать в ExoPlayer внутри `AudioPlaybackService`,
иначе при повторной буферизации (если пользователь перематывает за пределы кэша) плеер попытается
открыть уже несуществующий URI.

---

## 1. Problem Statement

### Нет click-listener (аудио)

`setupFileOperationButtons()` (строка ~552 в `StandalonePlayerActivity`) регистрирует
listener для `btnDeleteCmd`, `btnShareCmd`, `btnFavorite`, `btnInfoCmd`, но **не для
`btnRenameCmd`**. Это одинаково для всех типов файлов, включая аудио.

### Кнопка видна для не-переименовываемых URI

`btnRenameCmd` всегда видна по умолчанию (в XML нет `android:visibility="gone"`) — без
capability-check на то, можно ли переименовать данный URI (SAF-флаг `FLAG_SUPPORTS_RENAME`
или MediaStore URI).

### Аудио-специфичный риск: SAF-rename инвалидирует URI в AudioPlaybackService

Аудиофайлы открытые из Files/DocumentsPicker передаются как SAF URI. `StandaloneViewManager`
запускает `AudioPlaybackService` через `AudioServiceController.playAudio(uri)`, который
вызывает `MediaItem.fromUri(uri)` и начинает воспроизведение. После успешного
`DocumentsContract.renameDocument(resolver, oldUri, newName)` провайдер SAF инвалидирует
`oldUri` и возвращает `newUri`. ExoPlayer в `AudioPlaybackService` продолжает играть из
already-buffered данных, но при попытке повторной буферизации (перемотка за пределы кэша,
background restart) использует `oldUri` → `FileNotFoundException` → фатальный
`ExoPlaybackException`.

Механизм от нормального `PlayerActivity` здесь не применим: `CommandPanelController` и
`PlayerDialogHelper` не инстанциируются в standalone.

---

## 2. Goals

1. `btnRenameCmd` видна в standalone для аудиофайлов **только** если URI поддерживает rename:
   - SAF-документ с `FLAG_SUPPORTS_RENAME`
   - MediaStore URI (`content://media/...`) — optimistic
2. Нажатие кнопки открывает простой диалог переименования (`MaterialAlertDialogBuilder + EditText`).
3. После успешного rename:
   - `viewModel.state.mediaFile` обновляется (новое имя и новый URI).
   - Для SAF-rename: ExoPlayer в `AudioPlaybackService` получает новый `MediaItem` через
     `StandaloneViewManager.updateAudioMediaItem(newUri)` → `replaceMediaItem(0, newUri)`.
   - Playback продолжается без прерывания (Media3 `replaceMediaItem()` не останавливает播放).
4. Notification title **не** требует принудительного обновления: `DefaultMediaNotificationProvider`
   читает title из ID3-тегов файла (а не из `DISPLAY_NAME`) — тег остаётся неизменным.
5. Кнопка скрыта (`GONE`) по умолчанию до завершения асинхронной capability-проверки.

**Non-goals:**
- Редактирование ID3-тегов (artist, title, album) — отдельная функция, не входит в эту задачу.
- Rename аудиофайлов по сети (SMB/SFTP/FTP) — standalone получает только локальные/SAF URI.
- Поддержка `file://`-схем (нехарактерно для standalone).
- Обновление системного уведомления MediaSession с новым display name.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Аудио поддерживается? | Кнопка rename затронута? |
|--------|-----------------------|--------------------------|
| `standard` | ✅ | ✅ |
| `lite`     | ❌ (`SUPPORT_AUDIO=false`) | Кнопка в layout есть, но audio-path недостижим |
| `photos`   | ❌ | Кнопка в layout есть, но audio-path недостижим |
| `legacy`   | ✅ | ✅ |

`btnRenameCmd` присутствует в общем layout `activity_player_unified.xml` — capability check по
умолчанию скрывает кнопку, так что `lite`/`photos` не пострадают.

### 3.2 Android API Level Forks

| API | Поведение |
|-----|-----------|
| 23–28 (`legacy` minSdk) | SAF: `DocumentsContract.renameDocument()`. MediaStore write без scoped storage. |
| 29+ | Scoped storage. MediaStore audio write требует ownership или `RecoverableSecurityException`. SAF предпочтителен. |
| ≥ 30 | SAF — основной гарантированный путь для rename. |

### 3.3 Wear OS Impact

Нет изменений.

---

## 4. Current Architecture (Relevant Parts)

| Компонент | Путь | Роль в аудио-контексте |
|-----------|------|------------------------|
| `StandalonePlayerActivity` | `ui/player/StandalonePlayerActivity.kt` (~767 LOC) | `setupFileOperationButtons()` без `btnRenameCmd`; `resolveToLocalPath()` уже существует (строка ~740) |
| `StandaloneViewManager` | `ui/player/helpers/StandaloneViewManager.kt` | `playAudio(mediaFile)` → `AudioServiceController.playAudio(uri)` → `MediaItem.fromUri(uri)`. `audioServiceController` — **private**. `getExoPlayer()` возвращает video ExoPlayer, не аудио. |
| `AudioServiceController` | `ui/player/helpers/AudioServiceController.kt` | `playAudio(uri) { player -> ... }` → `player.setMediaItem(MediaItem.fromUri(uri))`. `player` — `Player` (Media3 ExoPlayer) в `AudioPlaybackService`. |
| `AudioPlaybackService` | `ui/player/AudioPlaybackService.kt` | `MediaSessionService`; `MediaSession.Builder(this, wrappedPlayer)`. Notification через `DefaultMediaNotificationProvider` — title из ID3-тегов. |
| `MediaNotificationManager` | `ui/player/MediaNotificationManager.kt` | `DefaultMediaNotificationProvider`: notification title = ID3 `TITLE` тег (или URI-path если ID3 нет). Не зависит от `DISPLAY_NAME`. |
| `StandalonePlayerViewModel` | `ui/player/StandalonePlayerViewModel.kt` | `loadFromUri()` → `MediaFile(path = uri.toString(), name = displayName)`. `updateState {}` — паттерн уже используется. |
| `DocumentsContract` | android.provider | `renameDocument(resolver, uri, newName)` → возвращает `newUri?`. |

**Ключевые пробелы:**
1. `setupFileOperationButtons()` — нет listener для `btnRenameCmd`.
2. `StandaloneViewManager` не имеет публичного метода для обновления аудио-URI в runtime.
3. `canRenameCurrentFile()` не реализован (нужен, как и в NEW.4).

**Существующая инфраструктура для переиспользования:**
- `resolveToLocalPath(uri)` уже есть в Activity (строка ~740) → capability check.

---

## 5. Proposed Architecture

### 5.1 Добавить `updateAudioMediaItem()` в `StandaloneViewManager`

Единственное audio-специфичное дополнение по сравнению с NEW.4 — новый публичный метод в
`StandaloneViewManager`, позволяющий Activity передать обновлённый URI без перезапуска сервиса:

```kotlin
/**
 * Updates the ExoPlayer media item in AudioPlaybackService after a SAF rename.
 * Playback continues uninterrupted; only the media source reference is updated.
 * No-op if no audio is playing.
 */
fun updateAudioMediaItem(newUri: Uri) {
    val player = audioServiceController?.player ?: return
    val currentPosition = player.currentPosition
    player.replaceMediaItem(0, MediaItem.fromUri(newUri))
    // replaceMediaItem preserves position in Media3; explicit seek as safety net.
    player.seekTo(currentPosition)
    Timber.d("StandaloneViewManager: audio media item updated to $newUri, pos=$currentPosition ms")
}
```

Media3 `replaceMediaItem(index, item)` обновляет source в playlist без остановки воспроизведения
(см. [Media3 Javadoc](https://developer.android.com/reference/androidx/media3/common/Player#replaceMediaItem(int,androidx.media3.common.MediaItem))).

### 5.2 Capability check (идентично NEW.4)

```kotlin
private fun canRenameCurrentFile(): Boolean {
    val uri = viewModel.state.value.mediaFile?.path?.toUri() ?: return false
    return when {
        DocumentsContract.isDocumentUri(this, uri) -> {
            try {
                contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                    null, null, null
                )?.use {
                    if (it.moveToFirst()) {
                        val flags = it.getInt(0)
                        flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
                    } else false
                } ?: false
            } catch (e: Exception) {
                Timber.w(e, "StandalonePlayerActivity: canRename query failed for $uri")
                false
            }
        }
        uri.authority?.startsWith("com.android.providers.media") == true ||
        uri.toString().startsWith("content://media/") -> true   // optimistic for MediaStore
        else -> false
    }
}
```

Вызывать из `lifecycleScope.launch(Dispatchers.IO)` — ContentResolver query не должен быть на Main thread.

### 5.3 Диалог переименования (идентично NEW.4)

`MaterialAlertDialogBuilder + EditText` с отображением текущего имени файла. `RenameDialog` не
используется: он построен вокруг `LocalRenameFileOperation → File.renameTo()`, что несовместимо с
content://URI.

### 5.4 Выполнение rename + аудио-специфичная постобработка

```kotlin
private fun performStandaloneRename(uri: Uri, newName: String) {
    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val newUri = if (DocumentsContract.isDocumentUri(this@StandalonePlayerActivity, uri)) {
                DocumentsContract.renameDocument(contentResolver, uri, newName)
            } else {
                // MediaStore: same URI, only DISPLAY_NAME changes
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                contentResolver.update(uri, values, null, null)
                uri
            }

            withContext(Dispatchers.Main) {
                if (newUri != null) {
                    // Update ViewModel state
                    viewModel.onRenameComplete(newUri, newName)
                    // Audio-specific: update ExoPlayer MediaItem if URI changed (SAF case)
                    if (newUri != uri) {
                        viewManager.updateAudioMediaItem(newUri)
                    }
                    Timber.d("StandalonePlayerActivity: audio rename succeeded → $newUri")
                } else {
                    Toast.makeText(
                        this@StandalonePlayerActivity,
                        getString(R.string.rename_failed_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "StandalonePlayerActivity: audio rename failed for $uri → $newName")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@StandalonePlayerActivity,
                    getString(R.string.rename_failed_generic),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
```

### 5.5 `onRenameComplete` в ViewModel (идентично NEW.4)

```kotlin
fun onRenameComplete(newUri: Uri, newName: String) {
    updateState { state ->
        state.copy(mediaFile = state.mediaFile?.copy(
            name = newName,
            path = newUri.toString(),
            contentUri = newUri.toString()
        ))
    }
}
```

Если этот метод уже добавлен в рамках NEW.4 — повторно не добавлять.

### 5.6 Notification title (No-op)

`DefaultMediaNotificationProvider` читает `MediaMetadata.title` из текущего `MediaItem`.
`MediaItem.fromUri(uri)` не выставляет явный title → Media3 читает его из ID3-тега `TITLE` файла.
`ContentResolver.update(DISPLAY_NAME)` меняет имя файла в MediaStore, но **не изменяет** ID3-тег
внутри файла. Поэтому:
- Если в файле есть ID3 TITLE (напр., `"Song Title"`) → notification показывает эту строку и после rename.
- Если ID3 TITLE отсутствует → notification показывала бы URI-компонент (или пусто) и до rename.
**Вывод: notification update не требуется**. Это ожидаемое поведение для rename (изменение имени файла ≠ редактирование ID3).

### 5.7 Новые / изменённые файлы

| Файл | Изменение |
|------|-----------|
| `StandaloneViewManager.kt` | +`fun updateAudioMediaItem(newUri: Uri)` |
| `StandalonePlayerActivity.kt` | Те же изменения, что и в NEW.4: `canRenameCurrentFile()`, `updateRenameButtonVisibility()`, `showStandaloneRenameDialog()`, `performStandaloneRename()` + вызов `viewManager.updateAudioMediaItem()` |
| `StandalonePlayerViewModel.kt` | +`fun onRenameComplete(newUri, newName)` (если не добавлен NEW.4) |
| `values/strings.xml` + `values-ru/` + `values-uk/` | +`rename_failed_generic` (если не добавлен NEW.4) |

> Если NEW.4 уже реализован — из этого spec дополнительно реализуется **только** `updateAudioMediaItem()` в `StandaloneViewManager` и вызов `viewManager.updateAudioMediaItem(newUri)` в `performStandaloneRename()`.

---

## 6. Data Flow (Audio)

```
[Аудиофайл загружен → viewModel.state.mediaFile != null]
        │
        ▼  [Dispatchers.IO]
canRenameCurrentFile()
    ├── SAF URI + FLAG_SUPPORTS_RENAME → btnRenameCmd.isVisible = true
    ├── MediaStore URI               → btnRenameCmd.isVisible = true (optimistic)
    └── Другое                       → btnRenameCmd.isVisible = false

[AudioPlaybackService]
AudioServiceController.playAudio(oldUri)
    └── MediaItem.fromUri(oldUri) → player.setMediaItem(...)
        [Воспроизведение идёт с буферами из oldUri]

[Пользователь нажимает btnRenameCmd]
        ▼
showStandaloneRenameDialog()         [EditText с текущим файлом]

[Пользователь вводит newName → "Применить"]
        ▼
performStandaloneRename()            [Dispatchers.IO]
    ├── SAF: DocumentsContract.renameDocument(oldUri, newName) → newUri
    │           ├── newUri != null:
    │           │       ├── viewModel.onRenameComplete(newUri, newName)  → State update
    │           │       │       └── mediaFile.path = newUri.toString(), name = newName
    │           │       └── viewManager.updateAudioMediaItem(newUri)
    │           │               └── audioServiceController.player
    │           │                       .replaceMediaItem(0, MediaItem.fromUri(newUri))
    │           │                   oldUri INVALIDATED — новый MediaItem предотвращает
    │           │                   FileNotFoundException при перемотке
    │           └── newUri == null → Toast rename_failed_generic
    └── MediaStore: ContentResolver.update(DISPLAY_NAME)
                ├── Успех: viewModel.onRenameComplete(sameUri, newName) → только name меняется
                │          updateAudioMediaItem NOT called (URI не изменился)
                └── Exception → Toast rename_failed_generic
```

---

## 7. Risk Analysis

| Риск | Вероятность | Митигация |
|------|:-----------:|-----------|
| SAF rename возвращает `null` (нет прав) | Medium | Guard `if (newUri != null)` + Toast |
| `replaceMediaItem(0, ...)` во время буферизации вызывает заметный пропуск | Low | Media3 `replaceMediaItem` разработан для hot-swap без остановки; пропуск <100ms допустим |
| MediaStore rename бросает `SecurityException` (API 29+, чужой файл) | Medium | `catch (e: SecurityException)` → Toast. `RecoverableSecurityException` — out of scope |
| Новый аудиофайл с другим MIME после rename (изменение расширения) | Low | Валидация: не разрешать менять расширение файла. Добавить проверку: `newName.substringAfterLast('.') == currentName.substringAfterLast('.')` — иначе предупреждение |
| `audioServiceController` is null (файл ещё не начал играть, capability check завершился раньше) | Low | Guard `?: return` в `updateAudioMediaItem()` |
| `StandaloneViewManager` приближается к 411+ строк | Medium | Метод `updateAudioMediaItem()` — 8 строк; общий размер остаётся в пределах |

---

## 8. Architecture Compliance

| Правило | Соответствие | Комментарий |
|---------|:-----------:|-------------|
| Нет бизнес-логики в Activity | ⚠️ | `performStandaloneRename()` содержит IO-логику прямо в Activity. Допустимо (1 функция, lightweight standalone). При росте логики → выделить в `StandaloneFileOpsManager`. |
| Нет `Log.d()` — только Timber | ✅ | |
| StateFlow для state | ✅ | `updateState { }` — существующий паттерн ViewModel |
| Именование | ✅ | `updateAudioMediaItem`, `performStandaloneRename` |
| Activity logic → Manager | ✅ | URI update делегируется в `StandaloneViewManager.updateAudioMediaItem()` |
| Coroutines: IO для I/O операций | ✅ | `Dispatchers.IO` для ContentResolver/DocumentsContract |
| Backup файлов >500 строк | ✅ | `StandalonePlayerActivity` > 500 строк → создать бэкап в `temp/` |

---

## 9. Testing Plan

### 9.1 Unit Tests

В `StandalonePlayerViewModelTest`:
```
fun `onRenameComplete updates audio mediaFile name and uri`()
```
(Если тест уже добавлен в рамках NEW.4 — пропустить.)

### 9.2 Manual Test Cases

#### Happy path — MP3 через SAF (Files app)

1. Открыть `.mp3` через Files app → `StandalonePlayerActivity`.
2. Убедиться, что `btnRenameCmd` **видна** (SAF URI с `FLAG_SUPPORTS_RENAME`).
3. Аудио воспроизводится нормально.
4. Нажать rename → ввести новое имя → «Применить».
5. Воспроизведение **продолжается без прерывания**.
6. `viewModel.state.mediaFile.name` = новое имя, `.path` = новый URI.
7. Перемотать в конец трека → seek + resume работают (нет `FileNotFoundException`).

#### Happy path — MP3 через MediaStore (Gallery/Files)

1. Открыть `.mp3` через Music app / long press → "Open with" → `StandalonePlayerActivity`.
2. `btnRenameCmd` видна (MediaStore URI).
3. Rename → успех → воспроизведение продолжается.
4. URI не изменился → `updateAudioMediaItem` не вызывается.

#### Файл без прав rename (облачный кэш)

1. Открыть файл из стороннего приложения без SAF write permissions.
2. `btnRenameCmd` **скрыта**.

#### Попытка смены расширения

1. Ввести имя с другим расширением (напр. `track.wav` → `track.mp4`).
2. Появляется предупреждение/кнопка Apply заблокирована.
   *(Если валидация расширения не реализована в этой итерации — документировать как TODO.)*

#### Regression: нормальный PlayerActivity (аудио)

1. Открыть MP3 через встроенный браузер FMS.
2. `CommandPanelController` управляет rename как прежде — изменений нет.

#### SAF playback integrity после rename

1. Открыть длинный `.flac` файл (~30+ мин) через SAF.
2. Перемотать на 20 мин.
3. Переименовать файл.
4. Перемотать на 25 мин → воспроизведение продолжается (нет ошибки буферизации).

### 9.3 Maestro E2E

Не применимо (SAF URI и переименование требуют реального устройства и прав от Files provider).

---

## 10. Accessibility

- `btnRenameCmd` имеет `contentDescription="@string/rename"` → TalkBack описывает правильно.
- `MaterialAlertDialogBuilder` — стандартный диалог, полностью доступен через TalkBack.
- `EditText.selectAll()` при открытии → быстрая замена имени клавиатурой.
- Минимальный touch target — `@dimen/player_cmd_button_size` (≥ 48dp). Без дополнительных действий.

---

## 11. User-Facing Feature Update

Обновить только если в рамках NEW.4 ещё не добавлена аналогичная строка. Если добавлена — дополнить уточнением "including audio files":

- `docs/FEATURES.md` (EN), раздел **File Operations**:  
  `- Rename files directly from the standalone "Open with" player, including audio files (MP3, FLAC, OGG, WAV, M4A). Supported for SAF documents and MediaStore files with write access.`

- `docs/FEATURES_RU.md` (RU):  
  `- Переименование файлов прямо из плеера в режиме «Открыть с помощью», в том числе аудиофайлов (MP3, FLAC, OGG, WAV, M4A). Для SAF-документов и MediaStore-файлов с правом на запись.`

- `docs/FEATURES_UK.md` (UK):  
  `- Перейменування файлів безпосередньо з плеєра у режимі «Відкрити за допомогою», зокрема аудіофайлів (MP3, FLAC, OGG, WAV, M4A). Для SAF-документів і MediaStore-файлів з правом на запис.`

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: `replaceMediaItem(0, newUri)` для обновления аудио-источника без перезапуска**
- **Decision:** Обновлять ExoPlayer media source через `player.replaceMediaItem(0, MediaItem.fromUri(newUri))` без остановки сервиса.
- **Alternatives considered:** Остановить AudioPlaybackService и перезапустить с новым URI; перезагрузить Activity.
- **Reason:** `replaceMediaItem` в Media3 разработан именно для этого сценария — обновление источника при сохранении позиции без прерывания воспроизведения. Перезапуск сервиса — UX-регресс.

**ADR-2: Не обновлять notification title после rename**
- **Decision:** Notification title не обновляется принудительно после переименования файла.
- **Reason:** `DefaultMediaNotificationProvider` читает title из `MediaItem.MediaMetadata.title`. При `MediaItem.fromUri(uri)` без явного metadata Media3 читает ID3 `TITLE` тег из бинарного содержимого файла. `DISPLAY_NAME` rename не изменяет ID3 теги. Обновление notification требовало бы разбора ID3 или работы с `MediaSession.setCustomLayout()` — out of scope. Нотификация с ID3 title корректна — пользователь видит правильное название трека.

**ADR-3: `updateAudioMediaItem()` в `StandaloneViewManager`, не прямой доступ к `audioServiceController`**
- **Decision:** Activity вызывает `viewManager.updateAudioMediaItem(newUri)`, который инкапсулирует доступ к `audioServiceController`.
- **Reason:** `audioServiceController` — private поле `StandaloneViewManager`. Добавление getter нарушило бы инкапсуляцию. Делегирование через именованный метод соответствует существующим паттернам (`getExoPlayer()` для видео — публичный, но аудио-controller нарочно инкапсулирован; добавляем минимальный публичный API для конкретной операции).

**ADR-4: Запрет смены расширения**
- **Decision:** При вводе нового имени с другим расширением показывать предупреждение (или заблокировать Apply).
- **Reason:** Смена расширения не переименовывает аудио в другой формат — она только путает MediaStore, который определяет MIME по расширению. Аудиофайл с расширением `.mp4` будет отображаться как видео в Gallery.

**ADR-5: Capability check выполняется в Dispatchers.IO**
- **Decision:** `canRenameCurrentFile()` запускается в `lifecycleScope.launch(Dispatchers.IO)`.
- **Reason:** ContentResolver.query к SAF-провайдеру не гарантирован быстрым ответом → ANR если выполнять на Main thread. Аналогично NEW.4.

---

## 13. Implementation Steps

> **Зависимость**: NEW.4 должна быть реализована ИЛИ реализовываться параллельно. Большинство шагов ниже пересекаются с NEW.4 — если NEW.4 уже реализован, выполнять только шаг 3 и связанные части шага 4.

1. **[Backup]** Перед правкой создать резервные копии:
   ```powershell
   $ts = Get-Date -Format 'yyyyMMdd_HHmmss'
   Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt" `
             "temp/StandaloneViewManager_$ts.kt.bak"
   Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" `
             "temp/StandalonePlayerActivity_$ts.kt.bak"
   ```

2. **[Strings]** Добавить строки (если не добавлены в NEW.4):
   - `rename_failed_generic` в `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "Add rename_failed_generic (shared with NEW.4 / NEW.5)"
   ```

3. **[StandaloneViewManager.kt]** Добавить публичный метод `updateAudioMediaItem(newUri)`:
   ```kotlin
   fun updateAudioMediaItem(newUri: Uri) {
       val player = audioServiceController?.player ?: return
       val currentPosition = player.currentPosition
       player.replaceMediaItem(0, MediaItem.fromUri(newUri))
       player.seekTo(currentPosition)
       Timber.d("StandaloneViewManager: audio MediaItem updated to $newUri pos=${currentPosition}ms")
   }
   ```
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt" "StandaloneViewManager" "Add updateAudioMediaItem(newUri) for hot-swap after SAF rename without interrupting playback"
   ```

4. **[StandalonePlayerActivity.kt + StandalonePlayerViewModel.kt]** Применить все изменения из
   NEW.4 (`canRenameCurrentFile()`, `updateRenameButtonVisibility()`, `showStandaloneRenameDialog()`,
   `performStandaloneRename()`, wire `btnRenameCmd`, `onRenameComplete()` в ViewModel). В
   `performStandaloneRename()` добавить аудио-специфичный блок:
   ```kotlin
   if (newUri != uri) {
       viewManager.updateAudioMediaItem(newUri)
   }
   ```
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" "StandalonePlayerActivity" "Wire btnRenameCmd for audio: capability check, standalone rename dialog, updateAudioMediaItem on SAF URI change"
   ```

5. **[Unit Test]** Добавить тест в `StandalonePlayerViewModelTest` (если не добавлен в NEW.4):
   ```
   fun `onRenameComplete updates audio mediaFile name and uri`()
   ```

6. **[Build & Manual Tests]** Собрать `standardDebug`, выполнить тест-кейсы из раздела 9.2.
   ```powershell
   .\scripts\builders\build-debug.PS1 -SkipZip
   ```

7. **[Feature Docs]** Обновить три FEATURES-файла (раздел 11).
   ```powershell
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES" "Expand rename note: includes audio files (MP3, FLAC, OGG, WAV, M4A)"
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "FEATURES_RU" "Expand rename note: включает аудиофайлы"
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "FEATURES_UK" "Expand rename note: включає аудіофайли"
   ```

### Mandatory step checklist

- [ ] Backup создан (шаг 1)
- [ ] `values/strings.xml` + ru + uk — строки добавлены (шаг 2)
- [ ] `StandaloneViewManager.updateAudioMediaItem()` добавлен (шаг 3)
- [ ] Вызов `viewManager.updateAudioMediaItem(newUri)` в `performStandaloneRename()` добавлен (шаг 4)
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` обновлены (шаг 7)
- [ ] `.\scripts\add_to_dev_log.ps1` выполнен после каждого шага

---

## 14. Out of Scope (future items)

- Редактирование ID3-тегов (artist, title, album, year) — отдельный feature.
- Принудительное обновление notification после DISPLAY_NAME rename (не нужно: ID3 title ≠ filename).
- `RecoverableSecurityException` для MediaStore rename API 29+ (чужие файлы): системный диалог разрешения.
- Поддержка `file://`-схем.
- Rename видео-файлов через standalone (механизм тот же, что NEW.4 + NEw.5; отдельный spec не требуется — VideoExoPlayer использует `exoPlayer`, не `AudioServiceController`).

> **Примечание о видео:** rename для видеофайлов в standalone использует ТOЖЕ механизм SAF/MediaStore (NEW.4), но не нуждается в `updateAudioMediaItem()` (Video ExoPlayer через `getExoPlayer()`, и SAF video rename URI update не критичен: видео не буферизируется на телефонном уровне так же, как аудио). Если необходимо — создать NEW.6.
