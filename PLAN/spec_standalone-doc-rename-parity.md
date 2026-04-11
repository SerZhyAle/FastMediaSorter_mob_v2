# Specification: NEW.4 — Standalone Player Rename Button

**Status:** Implemented  
**Date:** 2026-04-11  
**Tier:** 3 — Moderate (4–8h, medium risk)  
**Roadmap entry:** *(New item — not yet in IMPROVEMENT_ROADMAP.md. Proposed for TIER 3.)*  
В StandalonePlayerActivity кнопка `btnRenameCmd` отображается для всех типов файлов
(в том числе EPUB, PDF, TXT), но click-listener не зарегистрирован — нажатие игнорируется.
Кроме того, кнопка видна даже для content:// URI, для которых переименование невозможно,
что противоречит поведению нормального PlayerActivity, где видимость управляется флагами
возможностей (`canWrite && state.allowRename`). Цель: одинаковое поведение и внешний вид
кнопки в standalone и в нормальном режиме для всех типов документов.

---

## 1. Problem Statement

### Нет click-listener

`setupFileOperationButtons()` в `StandalonePlayerActivity` (строка ~516) регистрирует
listener для `btnDeleteCmd`, `btnShareCmd`, `btnFavorite`, `btnInfoCmd`, но **не для
`btnRenameCmd`**. Нажатие кнопки — silent no-op.

### Кнопка видна для не-переименовываемых файлов

В нормальном Player `CommandPanelController` устанавливает:
```kotlin
safeViews.btnRenameCmd.isVisible = canWrite && state.allowRename
```
(строка 298, `CommandPanelController.kt`). В standalone `CommandPanelController` не
инстанциируется → кнопка всегда видна (`android:visibility` в portrait XML не задан,
значит `VISIBLE` по умолчанию). Standalone получает файлы через content:// URI. Многие
из них (от сторонних приложений, облачных провайдеров, Downloads) не поддерживают
переименование через стандартный `File.renameTo()`.

### Несовместимость RenameDialog с content:// URI

`PlayerDialogHelper.showRenameDialog()` создаёт `File(currentFile.path)` (строки 283–301).
В standalone `MediaFile.path = uri.toString()` — т. е. строка вида
`content://com.android.providers.media.documents/document/document%3A12345`. Передача
такой строки в `File(path)` создаёт неправильный объект, `LocalRenameFileOperation`
использует `File.renameTo()` — это не работает с content URI. Нужна отдельная реализация
через `DocumentsContract.renameDocument()` (SAF) или `ContentResolver.update()` (MediaStore).

---

## 2. Goals

1. `btnRenameCmd` видна в standalone **только** для файлов, поддерживающих переименование:
   - SAF-документы с флагом `FLAG_SUPPORTS_RENAME` (проверка через `DocumentsContract`)
   - MediaStore-ресурсы (`content://media/...`) на API 29+ с правом `WRITE`
   - Файлы файловой системы (маловероятны в standalone, но обрабатываются через существующий `RenameDialog`)
2. Нажатие кнопки открывает диалог переименования с одним полем ввода (простой `InputDialog`), а не `RenameDialog` с RecyclerView (избыточен для одного файла в standalone).
3. После успешного переименования:
   - Заголовок (toolbar-title/content description) обновляется на новое имя.
   - `viewModel.state.mediaFile` обновляется с новым именем и новым URI (если URI изменился после SAF-rename).
4. Кнопка скрыта (`GONE`) в landscape для документов (EPUB/PDF/TXT) — паритет с нормальным Player, где rename доступен только в portrait-панели и overflow-menu.

**Non-goals:**
- Переименование файлов на сетевых ресурсах (SMB/SFTP/FTP) — standalone получает только локальные файлы.
- Пакетное переименование.
- Интеграция с существующим `RenameDialog`+`RenameFilesAdapter` — избыточно для одного файла.
- Поддержка переименования для `file://`-схемы (добавить при реальных кейсах).

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Все типы файлов |
| `lite`     | ✅ | Видео + изображения (нет EPUB/PDF/TXT, но rename кнопка в layout существует) |
| `photos`   | ✅ | Только изображения |
| `legacy`   | ✅ | Все типы, minSdk 23 |

Нет flavor-специфичных флагов — `btnRenameCmd` присутствует в общем layout.

### 3.2 Android API Level Forks

| API level | Поведение |
|-----------|-----------------------|
| 23–28 (legacy minSdk) | SAF: `DocumentsContract.renameDocument()`. MediaStore write без scoped storage. |
| 29+ (Android 10) | Scoped storage: MediaStore write требует `RecoverableSecurityException` или ownership. SAF предпочтителен. |
| 30+ (Android 11) | MediaStore batch ops недоступны для rename; SAF — основной путь. |
| ≥ 34 (Android 14) | То же; photo picker не влияет на rename. |

### 3.3 Wear OS Impact

No Wear OS changes required.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `StandalonePlayerActivity` | `ui/player/StandalonePlayerActivity.kt` (~767 LOC) | "Open With" Activity; `setupFileOperationButtons()` (~строка 516) обрабатывает Delete/Share/Favorite/Info; `btnRenameCmd` **не подключён** |
| `StandalonePlayerViewModel` | `ui/player/StandalonePlayerViewModel.kt` | `state.mediaFile.path = uri.toString()` (content:// URI); `state.mediaFile.name = displayName`; нет `fileOperationUseCase` |
| `RenameDialog` | `ui/dialog/RenameDialog.kt` | Реализован через `FileOperationUseCase` + `LocalRenameFileOperation`; работает с `File` объектами, несовместим с content:// URI |
| `FileOperationUseCase` | `domain/usecase/FileOperationUseCase.kt` | `@Inject constructor`; использует `LocalRenameFileOperation` → `File.renameTo()` |
| `LocalRenameFileOperation` | внутри `FileOperationUseCase.kt` | `file.renameTo(File(parent, newName))` — не работает с content:// |
| `CommandPanelController` | `ui/player/CommandPanelController.kt` | В нормальном Player: `btnRenameCmd.isVisible = canWrite && state.allowRename` (строка 298) — **не используется в standalone** |
| `PlayerDialogHelper` | `ui/player/PlayerDialogHelper.kt` | `showRenameDialog(MediaFile)` (строка 283): создаёт `File(currentFile.path)` — неверно для content:// |
| `DocumentsContract` | android.provider | `renameDocument(resolver, uri, displayName)`: переименовывает SAF-документ; возвращает новый URI |

**Ключевой пробел**: В standalone `MediaFile.path` — строка `content://...`, а не файловый путь. Стандартный `RenameDialog` нельзя использовать напрямую. Нужна standalone-специфичная логика переименования через `DocumentsContract`.

---

## 5. Proposed Architecture

### 5.1 Определение capability rename

Перед отображением кнопки нужно проверить, поддерживает ли конкретный URI переименование:

```kotlin
private fun canRenameCurrentFile(): Boolean {
    val uri = viewModel.state.value.mediaFile?.path?.toUri() ?: return false
    return when {
        DocumentsContract.isDocumentUri(this, uri) -> {
            // SAF document — check FLAG_SUPPORTS_RENAME
            try {
                val cursor = contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
                    null, null, null
                )
                cursor?.use {
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
        uri.toString().startsWith("content://media/") -> {
            // MediaStore — rename via update() supported for owned files on API < 29
            // On API 29+ requires ownership; optimistically show button, handle exception on attempt
            true
        }
        else -> false
    }
}
```

### 5.2 Вызов canRenameCurrentFile() и управление видимостью

Обновлять видимость после загрузки файла (в `observeViewModelState()`, когда `state.mediaFile` меняется с null на конкретный файл):

```kotlin
private fun updateRenameButtonVisibility() {
    binding.btnRenameCmd.isVisible = canRenameCurrentFile()
}
```

### 5.3 Диалог переименования в standalone

Использовать простой `MaterialAlertDialogBuilder` с `EditText` — без `RenameDialog`+RecyclerView:

```kotlin
private fun showStandaloneRenameDialog() {
    val currentFile = viewModel.state.value.mediaFile ?: return
    val currentName = currentFile.name
    val uri = currentFile.path.toUri()

    val input = EditText(this).apply {
        setText(currentName)
        selectAll()
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.rename))
        .setView(input)
        .setPositiveButton(R.string.apply) { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotBlank() && newName != currentName) {
                performStandaloneRename(uri, newName)
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
```

### 5.4 Выполнение rename через DocumentsContract

```kotlin
private fun performStandaloneRename(uri: Uri, newName: String) {
    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val newUri = if (DocumentsContract.isDocumentUri(this@StandalonePlayerActivity, uri)) {
                DocumentsContract.renameDocument(contentResolver, uri, newName)
            } else {
                // MediaStore update
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                contentResolver.update(uri, values, null, null)
                uri // URI unchanged for MediaStore rename
            }

            withContext(Dispatchers.Main) {
                if (newUri != null) {
                    viewModel.onRenameComplete(newUri, newName)
                    Timber.d("StandalonePlayerActivity: rename succeeded → $newUri")
                } else {
                    Toast.makeText(
                        this@StandalonePlayerActivity,
                        getString(R.string.rename_failed_generic),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "StandalonePlayerActivity: rename failed for $uri → $newName")
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

### 5.5 Метод onRenameComplete в ViewModel

Добавить в `StandalonePlayerViewModel`:

```kotlin
fun onRenameComplete(newUri: Uri, newName: String) {
    updateState { state ->
        val updatedFile = state.mediaFile?.copy(
            name = newName,
            path = newUri.toString(),
            contentUri = newUri.toString()
        )
        state.copy(mediaFile = updatedFile)
    }
}
```

### 5.6 Новые классы / файлы

Новых файлов не создаётся.

| Файл | Изменение | Строк после |
|------|-----------|-------------|
| `StandalonePlayerActivity.kt` | +`canRenameCurrentFile()`, `showStandaloneRenameDialog()`, `performStandaloneRename()`, `updateRenameButtonVisibility()`, wire listener в `setupFileOperationButtons()` | ~802 |
| `StandalonePlayerViewModel.kt` | +`onRenameComplete(newUri, newName)` | ~35 строк (файл небольшой) |
| `values/strings.xml` | +`rename_failed_generic` (если отсутствует) | +1 строка |
| `values-ru/strings.xml` | +RU-перевод | +1 строка |
| `values-uk/strings.xml` | +UK-перевод | +1 строка |

> `StandalonePlayerActivity.kt` > 500 строк → создать timestamped backup в `temp/`.

### 5.7 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ⚠️ | `performStandaloneRename()` содержит IO-логику прямо в Activity. Допустимо для standalone (lightweight, 1 функция). Если логика растёт — выделить в `StandaloneFileOpsManager`. |
| Naming convention | ✅ | `showStandaloneRenameDialog()`, `performStandaloneRename()` |
| Data flow | ✅ | Activity → ViewModel.onRenameComplete() для обновления state |
| No `Log.d()` — Timber only | ✅ | |
| `StateFlow` для state | ✅ | `updateState { }` — паттерн уже используется в ViewModel |

---

## 6. Data Flow

```
[Файл загружен → viewModel.state.mediaFile != null]
        │
        ▼
updateRenameButtonVisibility()
    └── canRenameCurrentFile()
            ├── DocumentsContract.isDocumentUri?
            │       └── query(COLUMN_FLAGS) → FLAG_SUPPORTS_RENAME?
            │               └── YES → btnRenameCmd.isVisible = true
            │               └── NO  → btnRenameCmd.isVisible = false
            ├── MediaStore URI? → isVisible = true (optimistic)
            └── else → isVisible = false

[User taps btnRenameCmd]
        │
        ▼
showStandaloneRenameDialog()
    └── MaterialAlertDialogBuilder → EditText(currentName)

[User taps "Apply"]
        │
        ▼
performStandaloneRename(uri, newName)       [Dispatchers.IO]
    ├── DocumentsContract.isDocumentUri?
    │       └── DocumentsContract.renameDocument() → newUri?
    └── Else (MediaStore):
            └── ContentResolver.update(DISPLAY_NAME) → uri unchanged
                        │
                        ▼ [Dispatchers.Main]
                viewModel.onRenameComplete(newUri, newName)
                    └── updateState { copy(mediaFile = updatedFile) }
                                           │
                                           ▼
                              observeViewModelState() → обновляет title/name UI

[On error]
        └── Toast(rename_failed_generic)
```

---

## 7. Files to Modify

| Файл | Изменение | Ожид. размер |
|------|-----------|-------------|
| `ui/player/StandalonePlayerActivity.kt` | +4 метода, wire listener `btnRenameCmd`, вызов `updateRenameButtonVisibility()` в `observeViewModelState()` | ~802 строки |
| `ui/player/StandalonePlayerViewModel.kt` | +`onRenameComplete(newUri, newName)` | ~35 строк |
| `res/values/strings.xml` | +`rename_failed_generic` (если отсутствует) | +1 строка |
| `res/values-ru/strings.xml` | +RU | +1 строка |
| `res/values-uk/strings.xml` | +UK | +1 строка |

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| `DocumentsContract.renameDocument()` возвращает `null` для URI без write-permission | Med | Guard `if (newUri != null)` + Toast с `rename_failed_generic` |
| MediaStore rename (ContentResolver.update) бросает `SecurityException` на API 29+ для чужих файлов | Med | `try/catch SecurityException` → Toast. На API 29+, если нужен `RecoverableSecurityException`, показывать запрос разрешения через `startIntentSenderForResult` — выходит за scope этого spec, скрыть кнопку |
| После SAF-rename видимый в плеере EPUB/PDF уже загружен в память → UI соответствует новому имени, но старый URI может быть invalidated | Low | PDF/EPUB/TEXT-контент уже в памяти (PdfRenderer / WebView / TextView); reload не нужен. Только имя в заголовке |
| `canRenameCurrentFile()` вызывает ContentResolver.query на UI-thread → ANR при медленном контент-провайдере | Med | Перенести проверку в `lifecycleScope.launch(Dispatchers.IO)` с последующим обновлением visibility на Main |
| `btnRenameCmd` видна в portrait XML по умолчанию до завершения capability check | Low | Изначально `btnRenameCmd.isVisible = false` в `setupFileOperationButtons()`, затем выставлять в `true` только после успешного check |
| `StandalonePlayerActivity.kt` приближается к 800 строк | Med | Если size > 900, вынести rename-логику в `StandaloneFileOpsManager` |

---

## 9. Testing Plan

### 9.1 Unit Tests

Добавить в `StandalonePlayerViewModelTest`:
```
fun `onRenameComplete updates mediaFile name and path`()
```

### 9.2 Manual Test Cases

#### Happy path — SAF-документ

1. Открыть `.epub` через Files app (SAF) → `StandalonePlayerActivity`.
2. Убедиться, что `btnRenameCmd` **видна** (SAF URI с `FLAG_SUPPORTS_RENAME`).
3. Нажать → диалог с текущим именем в EditText.
4. Ввести новое имя → «Применить» → Toast отсутствует.
5. Заголовок/contentDescription обновлён на новое имя.
6. `viewModel.state.mediaFile.name` = новое имя.

#### Реnoname-capable URI (content://media)

1. Открыть `.mp4` через Gallery → `StandalonePlayerActivity`.
2. `btnRenameCmd` видна (если файл принадлежит приложению/MediaStore).
3. Нажать → ввести новое имя → переименование успешно.

#### Файл без прав rename

1. Открыть файл из стороннего облачного приложения (Dropbox, Drive) → `StandalonePlayerActivity`.
2. `btnRenameCmd` **скрыта** (`FLAG_SUPPORTS_RENAME` отсутствует).

#### SecurityException path (API 29+ чужой MediaStore файл)

1. Открыть файл от другого приложения без write permission.
2. `btnRenameCmd` видна (optimistic).
3. Нажать → ввести имя → Toast `rename_failed_generic`.

#### Regression: нормальный PlayerActivity

1. Открыть файл во встроенном браузере → убедиться, что rename через `CommandPanelController` работает как прежде.

### 9.3 Maestro E2E

Не применимо для rename (требует реальный SAF-файл; Maestro не может создать права на URI). Тестирование только вручную.

---

## 10. Accessibility

`btnRenameCmd` в portrait XML имеет `android:contentDescription="@string/rename"` — TalkBack описывает кнопку правильно. `MaterialAlertDialogBuilder`-диалог стандартен и полностью доступен через TalkBack. `EditText` с `selectAll()` при открытии позволяет быстро заменить имя. Минимальный размер кнопки — `@dimen/player_cmd_button_size` (≥ 48dp). Без дополнительных действий.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN): в раздел **File Operations** добавить:  
  `- Rename file directly from the standalone "Open with" player (supported for SAF documents and MediaStore files with write access).`
- `docs/FEATURES_RU.md` (RU):  
  `- Переименование файла прямо из плеера в режиме «Открыть с помощью» (для SAF-документов и MediaStore-файлов с правом на запись).`
- `docs/FEATURES_UK.md` (UK):  
  `- Перейменування файлу безпосередньо з плеєра у режимі «Відкрити за допомогою» (для SAF-документів і MediaStore-файлів з правом на запис).`

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Простой MaterialAlertDialogBuilder вместо RenameDialog**
- **Decision:** Показывать диалог переименования через `MaterialAlertDialogBuilder + EditText`, не переиспользовать `RenameDialog`.
- **Alternatives considered:** Адаптировать `PlayerDialogHelper.showRenameDialog()` для content URI; создать `StandaloneRenameDialog`.
- **Reason:** `RenameDialog` построен вокруг `FileOperationUseCase → LocalRenameFileOperation → File.renameTo()` — не работает с content://. Адаптация потребует рефакторинга `RenameDialog`. Простой диалог покрывает единственный use-case (одно поле, один файл) без избыточности.

**ADR-2: DocumentsContract.renameDocument() как первичный путь, MediaStore.update() как fallback**
- **Decision:** Определять тип URI через `DocumentsContract.isDocumentUri()`, затем выбирать метод rename.
- **Alternatives considered:** Всегда использовать `ContentResolver.update(DISPLAY_NAME)` для всех content:// URI.
- **Reason:** SAF URI (из Files, Downloads, File Manager apps) правильно поддерживают `renameDocument()`. MediaStore URI (из Gallery, Camera) могут не иметь DocumentsContract-слоя. Два пути покрывают оба класса URI.

**ADR-3: Optimistic visibility для MediaStore URI**
- **Decision:** `btnRenameCmd` видна для MediaStore URI без предварительной проверки ownership (optimistic check). Ошибка перехватывается при попытке rename.
- **Alternatives considered:** Проверять ownership через `MediaStore.MediaColumns.OWNER_PACKAGE_NAME` (API 29+); скрывать кнопку если не owner.
- **Reason:** `OWNER_PACKAGE_NAME` доступен только API 29+. Добавление двойного conditional усложняет код при низкой частоте ошибки для owned-файлов. Optimistic + graceful error — нормальный Android UX паттерн.

**ADR-4: IO capability check через coroutine**
- **Decision:** `canRenameCurrentFile()` выполняется в `Dispatchers.IO` через `lifecycleScope.launch`, результат применяется на Main thread.
- **Alternatives considered:** Блокирующий вызов на UI-thread (быстрый для local storage, но ANR-опасен).
- **Reason:** Content provider query не гарантирует быстрый ответ; запуск на IO-dispatcher — стандартная Android практика.

---

## 13. Implementation Steps

1. **[Backup]** Создать резервную копию перед правкой:
   ```powershell
   Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" `
             "temp/StandalonePlayerActivity_$(Get-Date -Format 'yyyyMMdd_HHmmss').kt.bak"
   ```

2. **[Strings]** Добавить строку `rename_failed_generic` в `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (если отсутствует). Также проверить/добавить `rename_failed_no_permission`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "strings" "Add rename_failed_generic string for standalone rename error"
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "strings-ru" "Add RU rename_failed_generic"
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "strings-uk" "Add UK rename_failed_generic"
   ```

3. **[StandalonePlayerViewModel.kt]** Добавить `onRenameComplete()`:
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
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt" "StandalonePlayerViewModel" "Add onRenameComplete(newUri, newName) for standalone rename state update"
   ```

4. **[StandalonePlayerActivity.kt — setupFileOperationButtons()]** Скрыть кнопку initial state и зарегистрировать listener:
   ```kotlin
   // Rename — hidden until capability check completes
   binding.btnRenameCmd.isVisible = false
   binding.btnRenameCmd.setOnClickListener { showStandaloneRenameDialog() }
   ```

5. **[StandalonePlayerActivity.kt]** Добавить три приватных метода (`canRenameCurrentFile()`, `updateRenameButtonVisibility()`, `showStandaloneRenameDialog()`, `performStandaloneRename()`).

6. **[StandalonePlayerActivity.kt — observeViewModelState()]** Вызывать `updateRenameButtonVisibility()` при переходе `mediaFile != null`:
   ```kotlin
   // После отображения файла
   if (state.mediaFile != null) updateRenameButtonVisibility()
   ```
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" "StandalonePlayerActivity" "Wire btnRenameCmd: capability check, standalone rename dialog via DocumentsContract, onRenameComplete state update"
   ```

7. **[Unit Test]** Добавить `onRenameComplete updates mediaFile name and path` в `StandalonePlayerViewModelTest`.
   ```powershell
   .\scripts\add_to_dev_log.ps1 "app_v2/src/test/.../StandalonePlayerViewModelTest.kt" "StandalonePlayerViewModelTest" "Add test: onRenameComplete updates state correctly"
   ```

8. **[Build & Test]** Собрать `standardDebug` и провести ручные тест-кейсы из раздела 9.2.
   ```powershell
   .\scripts\builders\build-debug.PS1 -SkipZip
   ```

9. **[Feature Docs]** Обновить все три FEATURES-файла (раздел 11).
   ```powershell
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES" "Add standalone rename via SAF/MediaStore"
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "FEATURES_RU" "Add standalone rename via SAF/MediaStore"
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "FEATURES_UK" "Add standalone rename via SAF/MediaStore"
   ```

### Mandatory step checklist

- [ ] `values/strings.xml` + `values-ru/` + `values-uk/` — строки переименования добавлены (шаг 2)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` обновлены (шаг 9)
- [ ] Room DB: без изменений схемы
- [ ] `.\scripts\add_to_dev_log.ps1` выполнен после каждого изменённого файла (шаги 2, 3, 6, 7, 9)

---

## 14. Out of Scope (future items)

- Обработка `RecoverableSecurityException` для MediaStore rename на API 29+ (чужие файлы): показывать системный диалог разрешения через `startIntentSenderForResult`.
- Rename для `file://`-путей через существующий `RenameDialog` + `LocalRenameFileOperation`.
- Undo переименования в нотификации или Snackbar.
- Проверка `OWNER_PACKAGE_NAME` для точного определения возможности MediaStore rename на API 29+.
- Если `StandalonePlayerActivity.kt` > 900 строк после изменений — вынести `canRenameCurrentFile()`, `performStandaloneRename()` в `StandaloneFileOpsManager.kt`.
