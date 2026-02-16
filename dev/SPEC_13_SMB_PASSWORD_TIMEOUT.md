# Спецификация #13: Таймаут при неверном пароле SMB

## Описание задачи
При вводе неверного пароля для SMB-соединения пользователь очень долго ждет сообщение об ошибке, хотя сервер уже ответил "неверный пароль". Необходимо исправить логику, чтобы немедленно показывать ошибку при отказе в аутентификации, не дожидаясь таймаута.

## Требования

### Функциональные требования
- Немедленно показывать ошибку при неверном пароле
- Не ждать timeout, если получен ответ об ошибке аутентификации
- Четко различать типы ошибок:
  - Неверный пароль/логин
  - Сервер недоступен
  - Timeout
  - Другие ошибки

### Технические требования
- Корректная обработка SMB исключений
- Различные таймауты для разных операций
- Пользователь должен видеть конкретную причину ошибки

## Возможные причины проблемы
1. Неправильная обработка исключений SMB
2. Timeout установлен на слишком большое значение
3. Ошибка аутентификации не отличается от других ошибок
4. Retry логика пытается повторно подключиться при неверном пароле

## Пошаговая реализация

### Шаг 1: Исследование текущей реализации

#### 1.1: Найти код SMB подключения
```kotlin
// Поиск файлов с SMB
Get-ChildItem -Recurse -Filter "*.kt" | Select-String -Pattern "smb|jcifs"
```

Вероятные файлы:
- `SmbConnectionManager.kt`
- `NetworkSourceHandler.kt`
- `SmbProvider.kt`

#### 1.2: Проверить текущие таймауты
```kotlin
// Найти установку таймаутов
val currentResponseTimeout = System.getProperty("jcifs.smb.client.responseTimeout")
val currentSoTimeout = System.getProperty("jcifs.smb.client.soTimeout")
val currentConnTimeout = System.getProperty("jcifs.smb.client.connTimeout")

Log.d("SMB", "Response timeout: $currentResponseTimeout")
Log.d("SMB", "Socket timeout: $currentSoTimeout")
Log.d("SMB", "Connection timeout: $currentConnTimeout")
```

### Шаг 2: Исследование типов исключений SMB

```kotlin
// Добавить детальное логирование
try {
    val auth = NtlmPasswordAuthentication(domain, username, password)
    val smbFile = SmbFile(url, auth)
    smbFile.connect()
    
} catch (e: SmbAuthException) {
    // Ошибка аутентификации - НЕМЕДЛЕННО
    Log.e("SMB", "Auth failed immediately", e)
    
} catch (e: SmbException) {
    // Другая SMB ошибка
    Log.e("SMB", "SMB error: ${e.message}, NT status: ${e.ntStatus}", e)
    
} catch (e: IOException) {
    // Сетевая ошибка, timeout
    Log.e("SMB", "Network error", e)
}
```

### Шаг 3: Классификация ошибок

#### 3.1: Создать enum для типов ошибок
```kotlin
enum class SmbErrorType {
    AUTHENTICATION_FAILED,  // Неверный пароль/логин
    SERVER_NOT_FOUND,       // Сервер не найден
    CONNECTION_TIMEOUT,     // Таймаут подключения
    PERMISSION_DENIED,      // Нет прав доступа
    SHARE_NOT_FOUND,        // Папка не найдена
    NETWORK_ERROR,          // Общая сетевая ошибка
    UNKNOWN                 // Неизвестная ошибка
}
```

#### 3.2: Создать классификатор ошибок
```kotlin
object SmbErrorClassifier {
    
    fun classifyError(exception: Throwable): SmbErrorType {
        return when (exception) {
            is SmbAuthException -> {
                SmbErrorType.AUTHENTICATION_FAILED
            }
            
            is SmbException -> {
                when (exception.ntStatus) {
                    NtStatus.NT_STATUS_LOGON_FAILURE,
                    NtStatus.NT_STATUS_WRONG_PASSWORD,
                    NtStatus.NT_STATUS_ACCOUNT_RESTRICTION,
                    NtStatus.NT_STATUS_INVALID_LOGON_HOURS,
                    NtStatus.NT_STATUS_INVALID_WORKSTATION,
                    NtStatus.NT_STATUS_PASSWORD_EXPIRED,
                    NtStatus.NT_STATUS_ACCOUNT_DISABLED -> {
                        SmbErrorType.AUTHENTICATION_FAILED
                    }
                    
                    NtStatus.NT_STATUS_ACCESS_DENIED,
                    NtStatus.NT_STATUS_PRIVILEGE_NOT_HELD -> {
                        SmbErrorType.PERMISSION_DENIED
                    }
                    
                    NtStatus.NT_STATUS_BAD_NETWORK_NAME,
                    NtStatus.NT_STATUS_BAD_NETWORK_PATH -> {
                        SmbErrorType.SHARE_NOT_FOUND
                    }
                    
                    NtStatus.NT_STATUS_HOST_UNREACHABLE -> {
                        SmbErrorType.SERVER_NOT_FOUND
                    }
                    
                    else -> {
                        Log.w("SMB", "Unmapped NT status: ${exception.ntStatus}")
                        SmbErrorType.UNKNOWN
                    }
                }
            }
            
            is SocketTimeoutException -> {
                SmbErrorType.CONNECTION_TIMEOUT
            }
            
            is UnknownHostException -> {
                SmbErrorType.SERVER_NOT_FOUND
            }
            
            is IOException -> {
                // Проверить сообщение
                when {
                    exception.message?.contains("authentication", ignoreCase = true) == true -> {
                        SmbErrorType.AUTHENTICATION_FAILED
                    }
                    exception.message?.contains("timeout", ignoreCase = true) == true -> {
                        SmbErrorType.CONNECTION_TIMEOUT
                    }
                    else -> {
                        SmbErrorType.NETWORK_ERROR
                    }
                }
            }
            
            else -> SmbErrorType.UNKNOWN
        }
    }
    
    fun getErrorMessage(context: Context, errorType: SmbErrorType, exception: Throwable): String {
        return when (errorType) {
            SmbErrorType.AUTHENTICATION_FAILED -> 
                context.getString(R.string.smb_error_auth_failed)
            
            SmbErrorType.SERVER_NOT_FOUND -> 
                context.getString(R.string.smb_error_server_not_found)
            
            SmbErrorType.CONNECTION_TIMEOUT -> 
                context.getString(R.string.smb_error_timeout)
            
            SmbErrorType.PERMISSION_DENIED -> 
                context.getString(R.string.smb_error_permission_denied)
            
            SmbErrorType.SHARE_NOT_FOUND -> 
                context.getString(R.string.smb_error_share_not_found)
            
            SmbErrorType.NETWORK_ERROR -> 
                context.getString(R.string.smb_error_network, exception.message ?: "Unknown")
            
            SmbErrorType.UNKNOWN -> 
                context.getString(R.string.smb_error_unknown, exception.message ?: "Unknown")
        }
    }
}
```

### Шаг 4: Оптимизация таймаутов

```kotlin
object SmbConnectionConfig {
    
    fun configure() {
        // Таймаут ответа на команду (сократить для быстрого обнаружения проблем)
        System.setProperty("jcifs.smb.client.responseTimeout", "5000") // 5 секунд
        
        // Таймаут сокета
        System.setProperty("jcifs.smb.client.soTimeout", "8000") // 8 секунд
        
        // Таймаут подключения
        System.setProperty("jcifs.smb.client.connTimeout", "5000") // 5 секунд
        
        // Отключить автоматическую повторную попытку при auth ошибках
        System.setProperty("jcifs.smb.client.maxRetries", "0")
    }
    
    fun configureForValidation() {
        // Еще более короткие таймауты для проверки credentials
        System.setProperty("jcifs.smb.client.responseTimeout", "3000") // 3 секунды
        System.setProperty("jcifs.smb.client.connTimeout", "3000")
    }
}
```

### Шаг 5: Улучшенная обработка подключения

```kotlin
class SmbConnectionManager(private val context: Context) {
    
    suspend fun connect(
        serverUrl: String,
        username: String,
        password: String,
        domain: String = ""
    ): Result<SmbFile> = withContext(Dispatchers.IO) {
        
        try {
            Log.d("SMB", "Attempting connection to $serverUrl")
            val startTime = System.currentTimeMillis()
            
            // Настроить таймауты для подключения
            SmbConnectionConfig.configureForValidation()
            
            val auth = NtlmPasswordAuthentication(domain, username, password)
            val smbFile = SmbFile(serverUrl, auth)
            
            // Попытка подключения
            try {
                smbFile.connect()
                val elapsed = System.currentTimeMillis() - startTime
                Log.d("SMB", "Connected successfully in ${elapsed}ms")
                Result.success(smbFile)
                
            } catch (authEx: SmbAuthException) {
                // НЕМЕДЛЕННАЯ ошибка аутентификации
                val elapsed = System.currentTimeMillis() - startTime
                Log.e("SMB", "Auth failed immediately in ${elapsed}ms", authEx)
                
                val errorType = SmbErrorClassifier.classifyError(authEx)
                val message = SmbErrorClassifier.getErrorMessage(context, errorType, authEx)
                
                Result.failure(SmbConnectionException(message, errorType, authEx))
            }
            
        } catch (e: Exception) {
            val errorType = SmbErrorClassifier.classifyError(e)
            val message = SmbErrorClassifier.getErrorMessage(context, errorType, e)
            
            Log.e("SMB", "Connection failed: $errorType - $message", e)
            Result.failure(SmbConnectionException(message, errorType, e))
        }
    }
}

class SmbConnectionException(
    message: String,
    val errorType: SmbErrorType,
    cause: Throwable
) : Exception(message, cause)
```

### Шаг 6: UI обработка

```kotlin
// В Activity/Fragment где происходит подключение
private fun attemptSmbConnection() {
    lifecycleScope.launch {
        // Показать прогресс
        showLoading(true)
        
        val result = smbConnectionManager.connect(
            serverUrl = binding.serverUrl.text.toString(),
            username = binding.username.text.toString(),
            password = binding.password.text.toString()
        )
        
        showLoading(false)
        
        result.fold(
            onSuccess = { smbFile ->
                // Успешно подключились
                Toast.makeText(this@Activity, R.string.connection_successful, Toast.LENGTH_SHORT).show()
                proceedWithConnection(smbFile)
            },
            onFailure = { exception ->
                if (exception is SmbConnectionException) {
                    // Специфическая обработка по типу
                    handleSmbError(exception)
                } else {
                    // Общая ошибка
                    Toast.makeText(this@Activity, exception.message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

private fun handleSmbError(exception: SmbConnectionException) {
    when (exception.errorType) {
        SmbErrorType.AUTHENTICATION_FAILED -> {
            // Подсветить поля логина/пароля
            binding.passwordLayout.error = exception.message
            binding.password.requestFocus()
        }
        
        SmbErrorType.SERVER_NOT_FOUND -> {
            binding.serverUrlLayout.error = exception.message
        }
        
        else -> {
            // Показать общий диалог ошибки
            AlertDialog.Builder(this)
                .setTitle(R.string.connection_error)
                .setMessage(exception.message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }
}
```

### Шаг 7: Добавить строки

```xml
<!-- values/strings.xml -->
<string name="smb_error_auth_failed">Неверное имя пользователя или пароль</string>
<string name="smb_error_server_not_found">Сервер не найден. Проверьте адрес.</string>
<string name="smb_error_timeout">Превышено время ожидания подключения</string>
<string name="smb_error_permission_denied">Доступ запрещен</string>
<string name="smb_error_share_not_found">Общая папка не найдена</string>
<string name="smb_error_network">Сетевая ошибка: %s</string>
<string name="smb_error_unknown">Ошибка подключения: %s</string>
<string name="connection_successful">Подключение выполнено успешно</string>
<string name="connection_error">Ошибка подключения</string>
```

### Шаг 8: Тестирование

#### Тест 1: Неверный пароль
1. Ввести правильный сервер, логин, но неверный пароль
2. Нажать подключиться
3. **Ожидаемый результат**: Ошибка показывается немедленно (< 3 сек)
4. Сообщение: "Неверное имя пользователя или пароль"

#### Тест 2: Несуществующий сервер
1. Ввести несуществующий IP/hostname
2. Нажать подключиться
3. **Ожидаемый результат**: Ошибка через ~5 секунд
4. Сообщение: "Сервер не найден"

#### Тест 3: Правильные credentials
1. Ввести правильные данные
2. Нажать подключиться
3. **Ожидаемый результат**: Успешное подключение

#### Тест 4: Логирование времени
1. Проверить логи для каждого случая
2. Убедиться, что время ошибки аутентификации < 5с

## Критерии приемки
- ✅ При неверном пароле ошибка показывается немедленно (< 5 секунд)
- ✅ Ошибки аутентификации не ждут timeout
- ✅ Различные типы ошибок показывают разные сообщения
- ✅ Пользователь видит четкую причину ошибки
- ✅ Таймауты оптимизированы

## Файлы для создания/изменения
- Создать: `SmbErrorClassifier.kt`
- Создать/изменить: `SmbConnectionManager.kt`
- Создать: `SmbConnectionConfig.kt`
- Изменить: SMB connection UI (Activity/Fragment)
- Изменить: `res/values/strings.xml` (+ локализации)

## Связанные задачи
- Спецификация #14: SMB Connection Reset

## Примечания
- Убедиться, что используется правильная версия jCIFS
- Рассмотреть кэширование успешных подключений
- Добавить возможность сохранения credentials (с шифрованием)
