# Compose Multiplatform для Desktop - Исследование

**Дата**: 13 декабря 2025  
**Статус**: Начато (досрочно перед Sprint 6)  
**Цель**: Оценить возможность создания desktop companion для FastMediaSorter

---

## 📋 Ключевые Факты из Официальной Документации

### Поддержка Платформ
- ✅ **Desktop**: Windows, macOS, Linux (stable)
- ✅ **Android**: Через Jetpack Compose (уже используется)
- ✅ **iOS**: Stable
- 🔶 **Web**: Beta (Kotlin/Wasm)

### Архитектурные Преимущества
1. **Shared Code**: До 90-96% общего кода между платформами
2. **API Compatibility**: Совместим с Jetpack Compose APIs
3. **Native Feel**: Platform-specific gestures, нативная производительность
4. **Gradual Adoption**: Можно мигрировать постепенно (один экран, компонент)

### Примеры из Production
- **Markaz** (Pakistan e-commerce): 100+ экранов, 5M+ загрузок - полностью CMP
- **Wrike**: Calendars, Boards, Dashboards - CMP в production
- **Instabee**: "Staggering level of shared code without complexity"

---

## 🏗️ Анализ Текущей Архитектуры FastMediaSorter

### Что Можно Переиспользовать (80-90% кода)

#### ✅ Domain Layer (100% переносим)
**Путь**: `app_v2/src/main/java/com/sza/fastmediasorter/domain/`

**UseCases** (все без изменений):
- `GetResourcesUseCase` - получение списка ресурсов
- `GetMediaFilesUseCase` - сканирование файлов
- `FileOperationUseCase` - copy/move/delete
- `AddResourceUseCase`, `UpdateResourceUseCase`, `DeleteResourceUseCase`
- `CleanupTrashFoldersUseCase` - работа с undo
- `DownloadNetworkFileUseCase` - загрузка из SMB/SFTP/FTP

**Models** (все без изменений):
- `MediaFile`, `MediaResource`, `MediaType`
- `AppSettings`, `FilterCriteria`, `SortMode`
- `FileOperationResult`, `ResourceType`

**Repository Interfaces** (100% переносим):
- `ResourceRepository`
- `SettingsRepository`
- `NetworkCredentialsRepository`
- `FavoritesRepository`
- `PlaybackPositionRepository`
- `ThumbnailCacheRepository`

#### ✅ Data Layer (90% переносим, нужны platform-specific детали)

**Room Database** (работает на Desktop через SQLite JDBC):
- `AppDatabase`, `ResourceDao`, `ResourceEntity`
- `NetworkCredentialsEntity`, `FavoritesEntity`, `PlaybackPositionEntity`
- **Миграции**: Все существующие миграции переносятся

**Repository Implementations** (переносятся с минимальными изменениями):
- `ResourceRepositoryImpl`
- `SettingsRepositoryImpl` (SharedPreferences → File-based на Desktop)
- `NetworkCredentialsRepositoryImpl`
- `FavoritesRepositoryImpl`
- `PlaybackPositionRepositoryImpl`

**Network Clients** (100% переносим - чистый Kotlin):
- `SmbClient` (smbj library) - **работает на JVM**
- `SftpClient` (SSHJ library) - **работает на JVM**
- `FtpClient` (Apache Commons Net) - **работает на JVM**
- ✅ Все три клиента - JVM библиотеки, совместимы с Desktop!

**Strategy Pattern** (100% переносим):
- `FileOperationStrategy` interface
- `LocalOperationStrategy`, `SmbOperationStrategy`, `SftpOperationStrategy`, `FtpOperationStrategy`
- Вся логика кросс-протокольного роутинга

#### 🔶 Что Требует Адаптации

**Android-Specific APIs**:
1. **Context-зависимости**:
   - `ScanLocalFoldersUseCase` использует `@ApplicationContext`
   - Решение: Expect/actual для file system access
   
2. **Thumbnail Generation**:
   - `ThumbnailExtractor` использует `MediaMetadataRetriever`
   - Решение Desktop: JCodec/JavaCV для видео, ImageIO для изображений
   
3. **Coil Image Loading**:
   - Android: Coil 2.5.0
   - Desktop: Можно использовать Kamel (Compose Multiplatform image loader)

4. **ExoPlayer** (PlayerActivity):
   - Desktop: VLC Bindings (VLCJ) или JavaFX MediaPlayer
   - Или использовать только для Android (hybrid approach)

---

## 🎯 MVP для Desktop Companion (5 дней)

### Фаза 1: Структура Проекта (День 1)

**Цель**: Настроить Compose Multiplatform проект

```
FastMediaSorter_mob_v2/
├── app_v2/                    # Существующее Android приложение
├── desktop/                   # Новый desktop модуль
│   ├── build.gradle.kts
│   └── src/
│       └── jvmMain/kotlin/
│           └── Main.kt        # Entry point для Desktop
├── shared/                    # Общий код (domain + data)
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/
│       │   ├── domain/        # Копия из app_v2/domain
│       │   └── data/          # Адаптированная data layer
│       ├── androidMain/kotlin/
│       │   └── platform/      # Android-specific реализации
│       └── jvmMain/kotlin/
│           └── platform/      # Desktop-specific реализации
└── settings.gradle.kts        # Обновить для включения shared/desktop
```

**Задачи**:
- [ ] Создать `shared` Kotlin Multiplatform модуль
- [ ] Создать `desktop` JVM модуль с Compose for Desktop
- [ ] Настроить `build.gradle.kts` для обоих модулей
- [ ] Скопировать `domain/` из `app_v2` в `shared/commonMain`

### Фаза 2: Platform Abstractions (День 2)

**Expect/Actual для файловой системы**:

```kotlin
// shared/commonMain/kotlin/platform/FileSystem.kt
expect object FileSystem {
    fun listFiles(path: String): List<String>
    fun isFile(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun getFileSize(path: String): Long
    fun exists(path: String): Boolean
}

// shared/androidMain/kotlin/platform/FileSystem.kt
actual object FileSystem {
    actual fun listFiles(path: String): List<String> {
        return File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
    }
    // ... остальные методы через java.io.File
}

// shared/jvmMain/kotlin/platform/FileSystem.kt
actual object FileSystem {
    actual fun listFiles(path: String): List<String> {
        // Та же реализация - java.io.File работает на Desktop JVM
        return File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
    }
}
```

**Задачи**:
- [ ] Создать expect/actual для file operations
- [ ] Создать expect/actual для Settings (SharedPreferences vs Properties file)
- [ ] Портировать `LocalMediaScanner` с expect/actual зависимостями

### Фаза 3: Data Layer Migration (День 3)

**Room на Desktop**:
- Room работает на JVM через SQLite JDBC driver
- Нужно добавить зависимость `org.xerial:sqlite-jdbc`

**Задачи**:
- [ ] Портировать Room entities в `shared/commonMain`
- [ ] Настроить Room для JVM в `shared/jvmMain`
- [ ] Портировать Repository implementations
- [ ] **Тест**: Создать/прочитать ResourceEntity на Desktop

### Фаза 4: UI Prototype (День 4)

**Минимальный Desktop UI**:
```kotlin
// desktop/src/jvmMain/kotlin/Main.kt
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "FastMediaSorter Desktop",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        MaterialTheme {
            FastMediaSorterApp()
        }
    }
}

@Composable
fun FastMediaSorterApp() {
    val viewModel = remember { BrowseViewModel(/* inject dependencies */) }
    val resources by viewModel.resources.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Resources") })
        
        LazyColumn(Modifier.fillMaxSize()) {
            items(resources) { resource ->
                ResourceItem(resource) {
                    viewModel.selectResource(resource.id)
                }
            }
        }
    }
}
```

**Задачи**:
- [ ] Создать основной Window
- [ ] Реиспользовать Composable функции (ResourceItem, FileCard)
- [ ] Подключить ViewModel из shared module
- [ ] **Тест**: Отобразить список локальных папок

### Фаза 5: Network Operations Test (День 5)

**Цель**: Проверить SMB/SFTP/FTP на Desktop

**Задачи**:
- [ ] Портировать `SmbClient`, `SftpClient`, `FtpClient` в shared module
- [ ] Тест: Подключение к SMB share с Desktop
- [ ] Тест: Copy файл из SMB → Local на Desktop
- [ ] Тест: Browse файлов через SFTP
- [ ] Документировать результаты

---

## 🔬 Технические Детали

### Зависимости для Desktop Module

```kotlin
// desktop/build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.6.0"
}

dependencies {
    implementation(project(":shared"))
    
    // Compose for Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "FastMediaSorter"
            packageVersion = "2.25.12"
        }
    }
}
```

### Shared Module Dependencies

```kotlin
// shared/build.gradle.kts
kotlin {
    androidTarget()
    jvm("desktop")
    
    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            
            // Network libraries (JVM-only, но доступны в commonMain)
            implementation("com.hierynomus:smbj:0.12.1")
            implementation("com.hierynomus:sshj:0.37.0")
            implementation("commons-net:commons-net:3.10.0")
        }
        
        androidMain.dependencies {
            implementation("androidx.room:room-runtime:2.6.1")
            implementation("androidx.room:room-ktx:2.6.1")
        }
        
        val desktopMain by getting {
            dependencies {
                implementation("androidx.room:room-runtime:2.6.1") // Работает на JVM
                implementation("org.xerial:sqlite-jdbc:3.44.1.0")
            }
        }
    }
}
```

---

## 📊 Оценка Effort

| Компонент                     | Сложность | Время    | Переиспользование |
| :---------------------------- | :-------- | :------- | :---------------- |
| Domain Layer Migration        | Низкая    | 2 часа   | 100%              |
| Data Layer (Room/Repos)       | Средняя   | 6 часов  | 90%               |
| Network Clients (SMB/SFTP)    | Низкая    | 1 час    | 100% (JVM libs)   |
| File Operation Strategies     | Низкая    | 1 час    | 100%              |
| Platform Abstractions         | Средняя   | 4 часа   | Новый код         |
| Desktop UI (Browse Screen)    | Средняя   | 8 часов  | 60% Composables   |
| Settings/Preferences          | Низкая    | 2 часа   | Expect/actual     |
| Testing & Debugging           | Средняя   | 8 часов  | -                 |
| **ИТОГО**                     |           | **32 ч** | **~85% shared**   |

---

## ✅ Success Criteria для PoC

После 5 дней работы должно быть:

1. ✅ Desktop приложение запускается (Window с Material Design)
2. ✅ Отображается список локальных ресурсов из Room DB
3. ✅ Browse локальной папки → показать список файлов
4. ✅ Copy файл из локальной папки в другую
5. ✅ Подключение к SMB share → browse файлов
6. ✅ Copy файл SMB → Local
7. ✅ Общий код domain/data работает на обеих платформах

**Bonus** (если останется время):
- Move операции с undo
- Grid/List view toggle
- File filtering (Images/Videos/Audio)

---

## 🚧 Известные Ограничения

1. **ExoPlayer не работает на Desktop**:
   - Решение: Desktop использует VLC или JavaFX MediaPlayer
   - Hybrid approach: PlayerActivity только для Android
   
2. **Android-specific thumbnails**:
   - MediaMetadataRetriever → JCodec на Desktop
   - Coil → Kamel image loader
   
3. **Cloud Storage (Google Drive/OneDrive)**:
   - OAuth flow отличается на Desktop (browser redirect)
   - Отложить на Phase 2

4. **Две кодовые базы для UI**:
   - Часть Composables можно переиспользовать (ResourceItem, FileCard)
   - PlayerActivity уникален для Android
   - Settings UI нужно дублировать (разные паттерны)

---

## 📅 Roadmap после PoC

### Q2 2026 (Если PoC успешен)

**Sprint 7-8 (Apr 2026)**: Desktop v1.0
- Полноценный Browse UI с пагинацией
- Все file operations (copy/move/delete/undo)
- Settings UI
- Packaging для Windows/macOS/Linux

**Sprint 9-10 (May 2026)**: Two-Way Sync
- Local network discovery Android ↔ Desktop
- WebSocket или gRPC для real-time sync
- Clipboard sharing между устройствами

**Sprint 11-12 (Jun 2026)**: Advanced Features
- Batch operations UI
- Advanced filtering (shared с Android)
- Plugin system (если реализован на Android)

---

## 🎓 Учебные Ресурсы

**Официальная Документация**:
- https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-getting-started.html
- https://github.com/JetBrains/compose-multiplatform

**Примеры**:
- https://github.com/JetBrains/compose-multiplatform/tree/master/examples
- https://github.com/JetBrains/compose-multiplatform-desktop-template

**Туториалы**:
- "Kotlin Multiplatform Wizard" - стартовый шаблон
- JetBrains YouTube: "Compose Multiplatform Desktop Tutorial"

---

## 🤔 Решение: Go or No-Go

**Аргументы ЗА**:
- ✅ 85% кода переиспользуется (domain + data + network clients)
- ✅ Production-ready платформа (Wrike, Markaz используют)
- ✅ Minimal investment: 5 дней на PoC, ~3 недели на MVP
- ✅ Расширяет целевую аудиторию (фотографы/видеографы с Desktop workflow)
- ✅ JetBrains tooling (IntelliJ IDEA, Hot Reload)

**Аргументы ПРОТИВ**:
- ❌ Дублирование UI кода (хотя и частичное)
- ❌ Отвлекает от Android roadmap (Google Drive OAuth, Pagination testing)
- ❌ Поддержка двух платформ → больше тестирования
- ❌ Desktop UX expectations отличаются (keyboard shortcuts, window management)

**Рекомендация**: 
- **GO** для PoC (5 дней) - оценить feasibility
- Если PoC успешен → Full Desktop App в Q2 2026
- Если возникнут блокеры → отложить до Q3-Q4

---

**Следующий шаг**: Создать минимальный `shared` модуль и портировать `domain/` слой.
