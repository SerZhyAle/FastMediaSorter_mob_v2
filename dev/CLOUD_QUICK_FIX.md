# Быстрые решения для облачных проблем

## Google Drive не работает

### SHA-1 не совпадает (основная причина)

```bash
# 1. Получить SHA-1
.\gradlew.bat signingReport

# 2. Найти строку SHA1: AA:BB:CC:...

# 3. Firebase Console → Settings → SHA certificates → добавить SHA-1

# 4. Скачать google-services.json → app_v2/google-services.json

# 5. Пересобрать
.\gradlew.bat assembleStandardDebug
```

### Отсутствует google-services.json

1. Firebase Console → Android app → Download google-services.json
2. Скопировать → `app_v2/google-services.json`

---

## OneDrive не работает

### Отсутствует msal_config.json

Создать `app_v2/src/main/res/raw/msal_config.json`:

```json
{
  "client_id": "YOUR_CLIENT_ID_HERE",
  "authorities": [{
    "type": "AAD",
    "authority_url": "https://login.microsoftonline.com/common"
  }],
  "redirect_uri": "https://login.microsoftonline.com/common/oauth2/nativeclient"
}
```

**Client ID**: Azure Portal → App registrations → Application (client) ID

---

## Dropbox не работает

### Неправильный App Key

1. https://www.dropbox.com/developers/apps → App Key
2. Заменить в `app_v2/build.gradle.kts`:

```gradle
manifestPlaceholders["dropboxAppKey"] = "YOUR_APP_KEY"
```

3. Пересобрать: `.\gradlew.bat assembleStandardDebug`

---

## Проверка

```bash
# Сборка
.\gradlew.bat assembleStandardDebug

# Установка
adb install -r app_v2\build\outputs\apk\standard\debug\*.apk

# Логи
adb logcat | grep -i cloud
```

**Ожидается**: `D/CloudAuthenticationHelper: Cloud clients initialized`

---

## Очистка данных

```bash
# Очистить приложение
adb shell pm clear com.sza.fastmediasorter

# Очистить Gradle
.\gradlew.bat clean
```

---

## Частые ошибки

| Ошибка | Решение |
|--------|---------|
| `Cannot resolve symbol 'GoogleDriveRestClient'` | Использовать flavor `standard` или `photos` |
| `google-services.json not found` | Скачать из Firebase Console |
| `SHA1 mismatch` | Добавить SHA-1 в Firebase Console |
| `msal_config.json not found` | Создать в `app_v2/src/main/res/raw/` |
| `Dropbox authorization failed` | Заменить App Key в build.gradle.kts |

---

## Чеклист перед релизом

- [ ] `google-services.json` в `app_v2/`
- [ ] `msal_config.json` в `app_v2/src/main/res/raw/`
- [ ] Dropbox App Key в `build.gradle.kts`
- [ ] Flavor = `standard` или `photos`
- [ ] SHA-1 в Firebase Console
- [ ] Сборка успешна без ошибок
