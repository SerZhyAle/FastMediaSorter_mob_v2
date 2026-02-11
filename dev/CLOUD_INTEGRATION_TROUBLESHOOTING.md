# План ликвидации проблем с облачными хранилищами

## Обзор

### Поддерживаемые облака

| Провайдер | API | Аутентификация |
|-----------|-----|----------------|
| Google Drive | REST v3 | Google Sign-In |
| OneDrive | REST v2 | MSAL |
| Dropbox | REST v2 | OAuth 2.0 |

### Feature Flags

- `SUPPORT_CLOUD = true` → flavors: standard, photos
- `SUPPORT_CLOUD = false` → flavors: lite, legacy

---

## Диагностика

### Шаг 1: Проверка флагов сборки

```bash
# Проверка flavor
.\gradlew.bat :app_v2:assembleStandardDebug

# Ожидается:
# standard/photos: SUPPORT_CLOUD=true
# lite: SUPPORT_CLOUD=false
```

### Шаг 2: Проверка конфигурации

#### Google Drive

**Файл**: `app_v2/google-services.json`

Проверка:
- [ ] Файл существует в `app_v2/`
- [ ] SHA-1 зарегистрирован в Firebase Console

```bash
# Получить SHA-1
.\gradlew.bat signingReport

# Добавить в Firebase Console → Settings → SHA certificates
# Скачать google-services.json → app_v2/
```

#### OneDrive

**Файл**: `app_v2/src/main/res/raw/msal_config.json`

```json
{
  "client_id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "authorities": [{
    "type": "AAD",
    "authority_url": "https://login.microsoftonline.com/common"
  }],
  "redirect_uri": "https://login.microsoftonline.com/common/oauth2/nativeclient"
}
```

Проверка:
- [ ] Файл существует
- [ ] `client_id` формата UUID
- [ ] Зарегистрирован в Azure Portal
- [ ] Разрешение `Files.Read.All` установлено

#### Dropbox

**Файл**: `app_v2/build.gradle.kts`

```gradle
manifestPlaceholders["dropboxAppKey"] = "dpy64e70kqobr6x"
```

Проверка:
- [ ] Ключ установлен
- [ ] Зарегистрирован в Dropbox Developer Console
- [ ] Bundle ID совпадает с `com.sza.fastmediasorter`

### Шаг 3: Проверка зависимостей

```bash
.\gradlew.bat :app_v2:dependencies | Select-String "google-auth|msal|dropbox"
```

Ожидаемые библиотеки:
- `com.google.android.gms:play-services-auth`
- `com.microsoft.identity.client:msal`
- `com.dropbox.core:dropbox-core-sdk`
- `com.squareup.okhttp3:okhttp`
- `com.squareup.retrofit2:retrofit`

При ошибке:
```bash
.\gradlew.bat clean
.\gradlew.bat :app_v2:assembleStandardDebug
```

### Шаг 4: Проверка runtime инициализации

**Логи** (при запуске):
```
D/GoogleDriveRestClient: Initializing Google Drive client...
D/OneDriveRestClient: Initializing OneDrive client...
D/DropboxClient: Initializing Dropbox client...
```

### Шаг 5: Проверка пермиссий

**Файл**: `app_v2/src/main/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCOUNT_MANAGER" />
```

Проверка:
```bash
adb shell pm dump com.sza.fastmediasorter | grep PERMISSION
```

### Шаг 6: Проверка хранилища токенов

```bash
# Проверить файлы
adb shell ls -la /data/data/com.sza.fastmediasorter/shared_prefs/

# Ожидаемые файлы:
# - cloud_credentials.xml (зашифрован)
# - __androidx_security_crypto_shared_prefs__.xml
```

При ошибке:
```bash
adb shell pm clear com.sza.fastmediasorter
adb install -r app_v2/build/outputs/apk/standard/debug/*.apk
```

---

## Исправление проблем

### Google Drive не подключается

#### Отсутствует google-services.json

1. Firebase Console → Android app
2. Download google-services.json
3. Скопировать в `app_v2/google-services.json`
4. Пересобрать: `.\gradlew.bat assembleStandardDebug`

#### SHA-1 не совпадает

```bash
# Получить SHA-1
.\gradlew.bat signingReport

# Добавить в Firebase Console → Settings → SHA certificates
# Скачать новый google-services.json
```

#### Google Sign-In не инициализирован

Файл: `GoogleDriveRestClient.kt`

```kotlin
fun signInInteractive(activity: Activity): Intent {
    val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestScopes(Scope(SCOPE_DRIVE), Scope(SCOPE_DRIVE_READONLY))
        .requestEmail()
    
    val gso = gsoBuilder.build()
    val googleSignInClient = GoogleSignIn.getClient(activity, gso)
    return googleSignInClient.signInIntent
}
```

### OneDrive не подключается

#### Отсутствует msal_config.json

1. Создать: `app_v2/src/main/res/raw/msal_config.json`
2. Заполнить (см. Шаг 2)
3. Client ID из Azure Portal

#### Неправильно сконфигурирован MSAL

Файл: `OneDriveRestClient.kt`

```kotlin
companion object {
    private const val TENANT = "common"
    private const val REDIRECT_URI = "https://login.microsoftonline.com/common/oauth2/nativeclient"
    private const val SCOPE = "Files.Read.All"
}
```

#### MSAL cache повреждена

```bash
adb shell pm clear com.sza.fastmediasorter
adb install -r app_v2/build/outputs/apk/standard/debug/*.apk
```

### Dropbox не подключается

#### Неправильный App Key

1. Dropbox Developer Console → App Key
2. Заменить в `app_v2/build.gradle.kts`:

```gradle
manifestPlaceholders["dropboxAppKey"] = "YOUR_APP_KEY_HERE"
```

3. Пересобрать: `.\gradlew.bat assembleStandardDebug`

#### App Bundle ID не совпадает

```bash
# Проверить пакет
adb shell pm list packages | grep fastmediasorter

# Ожидается:
# com.sza.fastmediasorter (standard)
# com.sza.fastmediasorter.lite (lite)
# com.sza.fastmediasorter.photos (photos)

# Создать новое приложение в Dropbox для каждого пакета
```

---

## Тестирование

### Тест 1: Компиляция

```bash
.\gradlew.bat assembleStandardDebug
.\gradlew.bat assembleLiteDebug
```

✓ Успех: Обе версии компилируются  
✗ Ошибка: Cannot resolve symbol

### Тест 2: Инициализация

```bash
adb install -r app_v2/build/outputs/apk/standard/debug/*.apk
adb shell am start -n com.sza.fastmediasorter/.ui.main.MainActivity
adb logcat | grep -E "CloudAuth|GoogleDrive|OneDrive|Dropbox"
```

✓ Успех:
```
D/CloudAuthenticationHelper: Initializing cloud clients
D/GoogleDriveRestClient: Google Drive client ready
D/OneDriveRestClient: OneDrive client ready
D/DropboxClient: Dropbox client ready
```

### Тест 3: Добавление облачного ресурса

1. Меню → Добавить ресурс
2. Выбрать облако
3. Нажать "Подключить"
4. Авторизоваться

✓ Успех: Ресурс добавлен в список  
✗ Ошибка: Кнопка не отвечает

### Тест 4: Просмотр файлов

1. Галерея → облачный ресурс
2. Проверить список файлов

✓ Успех: Файлы загружаются и отображаются  
✗ Ошибка: "Нет файлов"

---

## Дополнительная диагностика

### Логирование

`LoggingHelper.kt`:

```kotlin
fun initialize(context: Context) {
    if (BuildConfig.DEBUG) {
        Timber.plant(object : Timber.DebugTree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                if (tag?.contains("Cloud") == true || 
                    tag?.contains("GoogleDrive") == true ||
                    tag?.contains("OneDrive") == true ||
                    tag?.contains("Dropbox") == true) {
                    super.log(priority, tag, message, t)
                }
            }
        })
    }
}
```

### HTTP запросы

```bash
adb logcat | grep -E "OkHttp|googleapis|graph.microsoft|api.dropboxapi"
```

Ожидаемые:
- `GET https://www.googleapis.com/drive/v3/about`
- `GET https://graph.microsoft.com/v1.0/me/drive/root`
- `GET https://api.dropboxapi.com/2/files/list_folder`

### Проверка хранилища

```bash
adb shell cat /data/data/com.sza.fastmediasorter/shared_prefs/cloud_credentials.xml

# Ожидается:
# <string name="gdrive_access_token">ya29.a0AfH6SMB...</string>
# <string name="onedrive_access_token">EwAIA8l6BAAR...</string>
# <string name="dropbox_access_token">sl.Bqvz3...</string>
```

---

## Чеклист релиза

- [ ] Flavor = standard/photos (`SUPPORT_CLOUD=true`)
- [ ] Сборка через `assembleStandardRelease`
- [ ] Сертификаты совпадают с Google Play Console
- [ ] `google-services.json` в `app_v2/`
- [ ] `msal_config.json` в `app_v2/src/main/res/raw/`
- [ ] Dropbox App Key в `build.gradle.kts`
- [ ] Зависимости скомпилированы
- [ ] Пермиссии в `AndroidManifest.xml`
- [ ] Hilt DI конфигурирует облачные клиенты
- [ ] Инициализация логируется
- [ ] Токены сохраняются в `EncryptedSharedPreferences`
- [ ] Подключение облака работает
- [ ] Файлы загружаются и отображаются

---

## Технический справочник

### Структура кода

```
app_v2/src/main/java/com/sza/fastmediasorter/
├── data/cloud/
│   ├── CloudAuthenticationHelper.kt
│   ├── CloudFileOperationHandler.kt
│   ├── CloudMediaScanner.kt
│   ├── CloudPathParser.kt
│   ├── GoogleDriveRestClient.kt
│   ├── OneDriveRestClient.kt
│   ├── DropboxClient.kt
│   └── helpers/
└── ui/addresource/
    ├── AddResourceActivity.kt
    └── AddResourceViewModel.kt
```

### API Endpoints

#### Google Drive
```
Base: https://www.googleapis.com/drive/v3
GET  /about
GET  /files?pageSize=100
GET  /files/{id}?alt=media
```

#### OneDrive
```
Base: https://graph.microsoft.com/v1.0/me/drive
GET  /root
GET  /root/children
GET  /items/{id}/content
```

#### Dropbox
```
Base: https://api.dropboxapi.com/2
POST /files/list_folder
POST /files/download
POST /files/get_preview
```

---

## Ссылки

- [Google Drive API v3](https://developers.google.com/drive/api/v3/reference)
- [Microsoft Graph OneDrive](https://docs.microsoft.com/en-us/graph/api/resources/driveitem)
- [Dropbox API v2](https://www.dropbox.com/developers/documentation/http/documentation)
- [Google Auth](https://developers.google.com/identity/protocols/oauth2/android)
- [MSAL for Android](https://github.com/AzureAD/microsoft-authentication-library-for-android)
