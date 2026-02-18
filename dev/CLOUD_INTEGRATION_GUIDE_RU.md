# Руководство по интеграции и диагностике облачных хранилищ

В этом документе объединена информация из диагностических скриптов, инструкций по устранению неполадок и быстрых решений.

---

## 1. Обзор и Поддерживаемые Провайдеры

Приложение поддерживает интеграцию со следующими облачными хранилищами:

| Провайдер | API | Метод аутентификации | Требуемые файлы конфигурации |
|-----------|-----|----------------------|------------------------------|
| **Google Drive** | REST v3 | Google Sign-In | `app_v2/google-services.json` |
| **OneDrive** | REST v2 | MSAL (Microsoft Auth) | `app_v2/src/main/res/raw/msal_config.json` |
| **Dropbox** | REST v2 | OAuth 2.0 | `build.gradle.kts` (manifestPlaceholder) |

### Feature Flags (Флаги функций)
Поддержка облаков зависит от Flavor сборки:
- **standard / photos**: `SUPPORT_CLOUD = true` (Включено)
- **lite / legacy**: `SUPPORT_CLOUD = false` (Отключено)

---

## 2. Быстрый старт: Устранение частых проблем (Quick Fix)

Если что-то не работает, начните с этого раздела. Решение 90% проблем занимает 5 минут.

### Google Drive
*   **Симптом**: "Ошибка подключения" или ничего не происходит после выбора аккаунта.
*   **Причина**: Несовпадение SHA-1 отпечатка сертификата.
*   **Решение**:
    1.  Запустите в терминале: `.\gradlew.bat signingReport`
    2.  Найдите строку `SHA1: ...` (для debug или release варианта).
    3.  Добавьте этот SHA-1 в Firebase Console → Project Settings → SDK Setup.
    4.  Скачайте свежий `google-services.json` и замените им файл в папке `app_v2/`.
    5.  Пересоберите приложение: `.\gradlew.bat assembleStandardDebug`.

### OneDrive
*   **Симптом**: Приложение падает при старте или ошибка инициализации MSAL.
*   **Причина**: Отсутствует или некорректен файл `msal_config.json`.
*   **Решение**:
    Создайте файл `app_v2/src/main/res/raw/msal_config.json` с содержимым:
    ```json
    {
      "client_id": "ВАШ_CLIENT_ID_ИЗ_AZURE",
      "authorities": [{
        "type": "AAD",
        "authority_url": "https://login.microsoftonline.com/common"
      }],
      "redirect_uri": "https://login.microsoftonline.com/common/oauth2/nativeclient"
    }
    ```

### Dropbox
*   **Симптом**: Ошибка авторизации.
*   **Причина**: Неверный App Key в манифесте.
*   **Решение**:
    1.  Зайдите в Dropbox Developer Console и скопируйте App Key.
    2.  Откройте `app_v2/build.gradle.kts`.
    3.  Найдите и обновите строку: `manifestPlaceholders["dropboxAppKey"] = "ВАШ_НОВЫЙ_КЛЮЧ"`
    4.  Сделайте Sync Gradle и пересоберите проект.

---

## 3. Полная диагностика (Скрипты)

Для автоматической проверки конфигурации используйте скрипт `cloud-diagnostic.ps1`.

### Запуск диагностики
```powershell
# Для Windows
.\scripts\cloud-diagnostic.ps1 -flavor standard
```

Скрипт проверяет 6 критических точек:
1.  **Flavor Support**: Включена ли поддержка облаков в текущем билде.
2.  **Required Files**: Наличие `google-services.json` и `msal_config.json`.
3.  **Dropbox Key**: Наличие ключа в `build.gradle.kts`.
4.  **Dependencies**: Подключены ли библиотеки `play-services-auth`, `msal`, `dropbox-core-sdk`.
5.  **Permissions**: Есть ли `INTERNET` permission в манифесте.
6.  **Classes**: Существование классов клиентов (`GoogleDriveRestClient`, и т.д.).

### Проверка отпечатков сертификатов (SHA-1)
```powershell
.\scripts\check-sha1.ps1
```
Этот скрипт покажет SHA-1 отпечатки для Debug и Release keystore и подскажет, куда их нужно добавить (Firebase или Google Play Console).

### Тест интеграции (Build & Run)
Скрипт для сборки, установки и запуска приложения с просмотром логов инициализации:
```powershell
.\scripts\test-cloud-integration.ps1 -flavor standard -install -run
```

---

## 4. Подробное устранение неполадок (Troubleshooting)

### Шаг 1: Проверка зависимостей
Убедитесь, что Gradle видит библиотеки:
```bash
.\gradlew.bat :app_v2:dependencies | Select-String "google-auth|msal|dropbox"
```
Если пусто → выполните Clean Build.

### Шаг 2: Проверка Runtime инициализации
Смотрите логи при запуске приложения:
```bash
adb logcat -s "CloudAuthenticationHelper" -s "GoogleDriveRestClient" -s "OneDriveRestClient" -s "DropboxClient"
```
Должно быть: `Initializing ... client...` -> `... client ready`.

### Шаг 3: Очистка состояния (Reset)
Если возникают странные ошибки кеширования (особенно MSAL):
```powershell
.\scripts\clean-cloud-state.ps1
```
Скрипт очистит данные приложения, удалит его и сделает `gradle clean`.

---

## 5. Чеклист перед релизом

Перед выпуском версии в Google Play убедитесь:

- [ ] Выбран Flavor `standard` или `photos`.
- [ ] `google-services.json` соответствует Release версии (проверьте SHA-1 release ключа).
- [ ] `msal_config.json` содержит правильный Production Client ID.
- [ ] Dropbox App Key правильный для Production версии.
- [ ] ProGuard/R8 правила не вырезают DTO классы API (добавлены `@Keep` или правила).
- [ ] Тестовый прогон на чистом устройстве прошел успешно.

---

## Приложение: Полезные команды для терминала

**Сборка Debug версии:**
```bash
.\gradlew.bat assembleStandardDebug
```

**Просмотр логов HTTP запросов:**
```bash
adb logcat | grep -E "OkHttp|googleapis|graph.microsoft|api.dropboxapi"
```

**Проверка прав доступа в установленном приложении:**
```bash
adb shell pm dump com.sza.fastmediasorter | grep PERMISSION
```
