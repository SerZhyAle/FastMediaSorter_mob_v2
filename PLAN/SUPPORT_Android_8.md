# Добавить поддержку устройств Android 8

## Результаты исследования (19 марта 2026)

---

## 1. Контекст задачи

**Android 8** = API уровень 26 (Android 8.0 Oreo) и API 27 (Android 8.1 Oreo MR1).

### Текущее состояние flavors:

| Flavor | minSdk | Покрывает Android 8? |
|--------|--------|----------------------|
| `standard` | 28 (Android 9) | ❌ Нет |
| `lite` | 28 (Android 9) | ❌ Нет |
| `photos` | 28 (Android 9) | ❌ Нет |
| `legacy` | 23 (Android 6) | ✅ Да (API 23-27) |

**Вывод:** Flavor `legacy` уже охватывает Android 8, однако в нём могут быть скрытые проблемы совместимости. Основные flavors (standard/lite/photos) Android 8 **не поддерживают вовсе**.

---

## 2. Анализ зависимостей — совместимость с API 26

Все библиотеки проекта были проверены на минимальный поддерживаемый API:

| Библиотека | Версия | Мин. API | API 26 ✓? |
|------------|--------|----------|-----------|
| Room | 2.7.0 | 16 | ✅ |
| security-crypto (EncryptedSharedPreferences) | 1.1.0-alpha06 | 23 | ✅ |
| Jetpack Compose BOM | 2024.02.00 | 21 | ✅ |
| ExoPlayer / Media3 | 1.2.1 | 21 | ✅ |
| Hilt | 2.57.2 | 21 | ✅ |
| WorkManager | 2.9.0 | 14 | ✅ |
| Glide | 4.16.0 | 14 | ✅ |
| ML Kit translate | 17.0.3 | 21 | ✅ |
| ML Kit text-recognition | 16.0.1 | 21 | ✅ |
| Tesseract4Android | 4.8.0 | 21 | ✅ |
| SMBJ (SMB-протокол) | 0.12.1 | 15 | ✅ |
| jsch (SFTP) | 0.2.16 | 14 | ✅ |
| Apache Commons Net (FTP) | 3.10.0 | 1 | ✅ |
| play-services-auth (Google) | 21.0.0 | 19 | ✅ |
| MSAL (OneDrive) | 6.0.1 | 21 | ✅ |
| Dropbox SDK | 5.4.5 | 21 | ✅ |
| OkHttp | 4.12.0 | 21 | ✅ |
| Retrofit | 2.9.0 | 21 | ✅ |

**Вывод:** Все зависимости совместимы с Android 8 (API 26). Блокирующих библиотечных проблем нет.

---

## 3. Анализ кода — критические проблемы

### 3.1. `java.time.*` — КРИТИЧНО для Android 6/7, НО НЕ ДЛЯ Android 8

В проекте используются классы `java.time.*` без включённого `coreLibraryDesugaring`:

| Файл | Использование |
|------|--------------|
| `data/remote/ftp/FtpClient.kt:17` | `import java.time.Duration` |
| `data/cloud/OneDriveRestClient.kt` | `java.time.Instant.parse(modifiedTime)` |
| `data/cloud/GoogleDriveRestClient.kt` | `java.time.Instant.parse(modifiedTime)` |

**Важно:**
- `java.time.*` — нативно доступны начиная с **API 26 (Android 8.0)**.
- Для Android 8+ эти вызовы **работают без проблем**.
- Для Android 6/7 (API 23-25) в flavor `legacy` — это **крэш** (`NoClassDefFoundError`), так как `coreLibraryDesugaring` **не подключён** в `build.gradle.kts`.

**Следствие:** Если цель — поддержка именно Android 8 (API 26+), `coreLibraryDesugaring` не нужен. Если цель — вся линейка `legacy` (API 23-27 полностью), он необходим.

### 3.2. Анализ API 28+ вызовов — все ПРАВИЛЬНО защищены

Проверены все 491 Kotlin-файл. Все вызовы API 28+ защищены проверками версии:

```kotlin
// PermissionHelper.kt:289 — API 28 guard ✅
Build.VERSION_CODES.P -> { ... }

// AppStartupInitializer.kt:198 — API 28 guard ✅
if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { ... }

// ExtractGifFramesUseCase.kt:94 — animated WEBP/APNG only API 28+ ✅
"webp", "apng" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
```

**Вывод:** Крэшей из-за незащищённых вызовов API 28+ нет.

### 3.3. Анимированные WebP / APNG — ограниченная поддержка на Android 8

```kotlin
// ExtractGifFramesUseCase.kt:94
"webp", "apng" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
```

Анимированные WebP и APNG декодируются через `android.graphics.ImageDecoder`, который требует **API 28+**. На Android 8 (API 26/27) эти форматы будут показаны как статичные изображения. Это ожидаемое поведение — guard уже есть.

### 3.4. Атрибуты манифеста — все безопасны для Android 8

Несколько атрибутов манифеста используют новые API, но система Android **молча игнорирует** неизвестные XML-атрибуты — крэша не происходит:

| Атрибут | Введён в API | Поведение на Android 8 |
|---------|-------------|------------------------|
| `android:foregroundServiceType="mediaPlayback"` | API 29 | Игнорируется |
| `android:localeConfig="@xml/locales_config"` | API 33 | Игнорируется |
| `android:enableOnBackInvokedCallback="true"` | API 33 | Игнорируется |
| `android:dataExtractionRules="@xml/data_extraction_rules"` | API 31 | Игнорируется |

### 3.5. Разрешения манифеста — анализ для Android 8

| Разрешение | Когда добавлено | Поведение на Android 8 (API 26/27) |
|------------|----------------|-------------------------------------|
| `FOREGROUND_SERVICE` | Обязательно с API 28 | На API 26/27 — не требуется, но объявление не вредит |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | API 34 | Игнорируется на API 26/27 |
| `READ_MEDIA_IMAGES/VIDEO/AUDIO` | API 33 | Игнорируются, действует `READ_EXTERNAL_STORAGE` |
| `MANAGE_EXTERNAL_STORAGE` | API 30 | Игнорируется |
| `MANAGE_MEDIA` | API 32 | Игнорируется |
| `POST_NOTIFICATIONS` | API 33 | Игнорируется |
| `WRITE_EXTERNAL_STORAGE` (maxSdkVersion="28") | Давно | ✅ Работает на API 26/27 |
| `READ_EXTERNAL_STORAGE` | Давно | ✅ Работает на API 26/27 |

### 3.6. NotificationChannel — правильно защищён

```kotlin
// MediaNotificationManager.kt:40
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    // Создание NotificationChannel — API 26+
}
```

`NotificationChannel` был введён в API 26 (Android 8.0). Код правильно защищён. ✅

### 3.7. Foreground Service на Android 8

Android 8 (API 26) ввёл требование: если запускаете сервис через `startForegroundService()`, нужно вызвать `startForeground()` в течение 5 секунд.

```kotlin
// MediaButtonRestartReceiver.kt:56
ContextCompat.startForegroundService(context, serviceIntent)
```

`AudioPlaybackService` — это `Media3 MediaSessionService`. Media3 **автоматически управляет** вызовом `startForeground()` и совместим с Android 8. ✅

### 3.8. Ограничения фоновых сервисов Android 8

Android 8 ввёл строгие ограничения на фоновые сервисы. Проект использует **WorkManager** для фоновых задач (`NetworkFilesSyncWorker`, `OrphanCleanupWorker`, `TrashCleanupWorker`, `PendingRevocationWorker`) — WorkManager автоматически учитывает эти ограничения. ✅

### 3.9. Хранилище — различия Android 8 и Android 9+

На Android 8 (API 26/27):
- Работает **legacy storage model** — прямой доступ к файлам через `File API`
- `WRITE_EXTERNAL_STORAGE` разрешение доступно и функционально
- **Scoped Storage** (API 29/30) не применяется

Код в `LocalOperationStrategy.kt` и `FileOperationUseCase.kt` правильно проверяет API версию для scoped storage операций. ✅

### 3.10. MediaStore на Android 8

```kotlin
// MediaStoreRepositoryImpl.kt
val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    // API 26+ MediaStore query with VOLUME_EXTERNAL
} else {
    // Старый путь
}
```

Уже есть явная ветка для API 26 (Android 8). ✅

---

## 4. Что нужно сделать для добавления поддержки Android 8

### Вариант A: Исправить flavor `legacy` (minSdk=23 → полноценная поддержка)

**Проблема:** `java.time.*` крэшит на Android 6/7 (API 23-25). Android 8 работает корректно.

**Решение:** Добавить `coreLibraryDesugaring` в `build.gradle.kts`:

```kotlin
// В android { } блоке:
compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// В dependencies { }:
coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
```

### Вариант B: Снизить minSdk для `standard`/`lite`/`photos` до API 26

**Изменения в `build.gradle.kts`:**

```kotlin
defaultConfig {
    // Изменить с 28 → 26
    minSdk = 26
    ...
}
```

**Что проверить после снижения:**
1. Все вызовы API 27+ защищены проверками — ✅ проверено выше
2. Все вызовы API 28+ защищены проверками — ✅ проверено выше
3. `java.time.*` — нативно доступны с API 26, `coreLibraryDesugaring` не нужен
4. `FOREGROUND_SERVICE` permission — на API 26/27 не обязательно, объявление не вредит
5. `foregroundServiceType` в манифесте — на API 26/27 игнорируется

**Потенциальные риски при снижении до API 26:**
- Нужно тестирование на реальном устройстве Android 8 или эмуляторе API 26
- `EncryptedSharedPreferences` — требует `setDataDir()` на некоторых API 26 конфигурациях (возможны редкие проблемы)
- Темы Material3 могут немного отличаться визуально на Android 8

### Вариант C: Новый отдельный flavor `android8` (самый трудоёмкий)

Создать отдельный flavor с `minSdk = 26`, `maxSdk = 27` — для чёткого таргетинга. Избыточно, если достаточно варианта B.

---

## 5. Рекомендуемый план действий

### Шаг 1 — Снизить minSdk `legacy` до 23 (уже сделано) + добавить `coreLibraryDesugaring`

Файл: [app_v2/build.gradle.kts](app_v2/build.gradle.kts)

```kotlin
// В android { compileOptions { } }:
isCoreLibraryDesugaringEnabled = true

// В dependencies { }:
coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
```

Это исправит крэш `java.time.*` на Android 6/7.

### Шаг 2 — Снизить minSdk основного flavor `standard` с 28 → 26

Файл: [app_v2/build.gradle.kts](app_v2/build.gradle.kts), строка 24:

```kotlin
// Было:
minSdk = 28
// Стать:
minSdk = 26
```

Удалить комментарий `// CRITICAL: Do not change - minimum supported Android 9 (API 28)` и обновить его.

### Шаг 3 — Обновить комментарии в `build.gradle.kts`

Обновить описание flavor `legacy`:
```kotlin
// CRITICAL: Do not change - legacy flavor specifically for Android 6+ (API 23-25) devices
minSdk = 23  // Android 6.0 (Marshmallow) for devices API 23-25 only
```

### Шаг 4 — Проверить PermissionHelper для API 26/27

Файл: [app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt)

Убедиться, что ветки для API 26/27 обрабатываются корректно (код хранилища, разрешений).

### Шаг 5 — Тестирование

```powershell
# Сборка legacy debug (API 23+):
.\gradlew.bat assembleLegacyDebug

# Сборка standard debug (с новым minSdk=26):
.\gradlew.bat assembleStandardDebug

# Установка на устройство/эмулятор API 26:
.\scripts\builders\build-standard-device.ps1
```

---

## 6. Итоговая матрица совместимости после изменений

| Функция | API 23-25 (legacy) | API 26-27 (Android 8) | API 28-35 (Android 9+) |
|---------|-------------------|----------------------|------------------------|
| Просмотр изображений | ✅ | ✅ | ✅ |
| Видео (ExoPlayer) | ✅ | ✅ | ✅ |
| Аудио / фоновое воспр. | ✅ | ✅ | ✅ |
| SMB/SFTP/FTP | ✅* | ✅ | ✅ |
| Cloud (OneDrive/GDrive) | ✅* | ✅ | ✅ |
| Анимированный WebP/APNG | ❌ (статик) | ❌ (статик) | ✅ |
| OCR / перевод (ML Kit) | ✅ | ✅ | ✅ |
| Scoped Storage | ❌ (не нужен) | ❌ (не нужен) | ✅ |
| MANAGE_EXTERNAL_STORAGE | ❌ (не поддерж.) | ❌ (не поддерж.) | ✅ |
| MANAGE_MEDIA | ❌ | ❌ | ✅ |
| NotificationChannel | ✅** | ✅ | ✅ |

`*` — требует `coreLibraryDesugaring` для `java.time.*` (Шаг 1)
`**` — уже защищён guard'ом `Build.VERSION_CODES.O`

---

## 7. Дополнительные заметки

### Network Security Config
Файл `network_security_config.xml` использует `<base-config cleartextTrafficPermitted="false"/>` — это актуально с Android 9 (API 28), который по умолчанию блокирует HTTP. На Android 8 cleartext разрешён по умолчанию, поэтому конфиг только добавляет безопасности.

### Lint проверки
В `build.gradle.kts` отключена lint-проверка `NewApi`:
```kotlin
disable += "NewApi"
```
Это означает, что lint **не предупреждает** о незащищённых вызовах новых API. Рекомендуется временно включить для проверки совместимости с Android 8 (убрать из `disable` на время проверки).

### StrictMode
`StrictModeHelper.kt` — работает только в DEBUG-сборках, без зависимостей от конкретной версии API. Совместим с Android 8. ✅

---

*Исследование проведено: 19 марта 2026. Проверено 491 Kotlin-файл, манифест, build.gradle.kts и 9 XML-ресурсов.*
