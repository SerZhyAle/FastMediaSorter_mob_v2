# Спецификация #14: SMB Connection Reset не работает

## Описание задачи
После множественных ошибок SMB-соединения появляется сообщение "SMB connection reset", но это не помогает - пока не закроешь программу совсем и не откроешь заново, связь по SMB не восстанавливается ни с одним ресурсом.

## Требования

### Функциональные требования
- "Connection reset" должен действительно сбрасывать состояние SMB
- После reset должна быть возможность подключения к SMB ресурсам
- Не должно требоваться полное закрытие приложения
- Reset должен очищать все кэши и переустанавливать соединения

### Технические требования
- Полная очистка SMB connection pool
- Сброс всех кэшированных credentials
- Переинициализация SMB context
- Корректная очистка всех SMB-related ресурсов

## Возможные причины
1. Connection pool не очищается при reset
2. Старые соединения остаются в памяти
3. jCIFS context не переинициализируется
4. Кэшированные ошибки блокируют новые попытки подключения
5. Неправильная обработка многопоточности (race conditions)

## Пошаговая реализация

### Шаг 1: Исследование текущей реализации reset

#### 1.1: Найти код "connection reset"
```powershell
Get-ChildItem -Recurse -Filter "*.kt" | Select-String -Pattern "connection reset|reset.*smb|smb.*reset" -CaseSensitive:$false
```

#### 1.2: Добавить детальное логирование
```kotlin
fun resetSmbConnection() {
    Log.d("SMB", "=== SMB RESET STARTED ===")
    Log.d("SMB", "Active connections: ${getActiveConnectionCount()}")
    Log.d("SMB", "Cached credentials: ${getCachedCredentialsCount()}")
    
    // Текущий код reset
    
    Log.d("SMB", "=== SMB RESET COMPLETED ===")
}
```

### Шаг 2: Создать централизованный SMB Manager

```kotlin
class SmbConnectionPool private constructor() {
    
    private val connections = ConcurrentHashMap<String, SmbFile>()
    private val credentials = ConcurrentHashMap<String, NtlmPasswordAuthentication>()
    private val errorCache = ConcurrentHashMap<String, Long>()
    private val lock = ReentrantLock()
    
    companion object {
        @Volatile
        private var instance: SmbConnectionPool? = null
        
        fun getInstance(): SmbConnectionPool {
            return instance ?: synchronized(this) {
                instance ?: SmbConnectionPool().also { instance = it }
            }
        }
        
        fun resetInstance() {
            synchronized(this) {
                instance?.fullReset()
                instance = null
            }
        }
    }
    
    fun getConnection(
        url: String,
        auth: NtlmPasswordAuthentication
    ): SmbFile {
        val key = generateKey(url, auth)
        
        // Проверить кэш ошибок
        errorCache[key]?.let { errorTime ->
            if (System.currentTimeMillis() - errorTime < 60000) { // 1 минута
                throw CachedErrorException("Recent error for this connection")
            } else {
                errorCache.remove(key)
            }
        }
        
        return connections.getOrPut(key) {
            SmbFile(url, auth)
        }
    }
    
    fun markError(url: String, auth: NtlmPasswordAuthentication) {
        val key = generateKey(url, auth)
        errorCache[key] = System.currentTimeMillis()
        connections.remove(key)
    }
    
    fun fullReset() {
        lock.withLock {
            Log.d("SMB", "Full reset started. Connections: ${connections.size}")
            
            // Закрыть все соединения
            connections.values.forEach { smbFile ->
                try {
                    smbFile.close()
                } catch (e: Exception) {
                    Log.e("SMB", "Error closing connection", e)
                }
            }
            
            connections.clear()
            credentials.clear()
            errorCache.clear()
            
            // Сброс jCIFS
            resetJcifsContext()
            
            Log.d("SMB", "Full reset completed")
        }
    }
    
    private fun resetJcifsContext() {
        try {
            // Очистить все system properties
            val propertiesToReset = listOf(
                "jcifs.smb.client.responseTimeout",
                "jcifs.smb.client.soTimeout",
                "jcifs.smb.client.connTimeout",
                "jcifs.resolveOrder",
                "jcifs.netbios.cachePolicy"
            )
            
            propertiesToReset.forEach { prop ->
                System.clearProperty(prop)
            }
            
            // Переустановить дефолтные значения
            SmbConnectionConfig.configure()
            
            // Если используется SmbConfig (jCIFS ng)
            // SingletonContext.getInstance().close()
            
        } catch (e: Exception) {
            Log.e("SMB", "Error resetting jCIFS context", e)
        }
    }
    
    private fun generateKey(url: String, auth: NtlmPasswordAuthentication): String {
        return "$url:${auth.username}:${auth.domain}"
    }
}

class CachedErrorException(message: String) : Exception(message)
```

### Шаг 3: Улучшенный механизм reset

```kotlin
object SmbResetManager {
    
    private var resetCount = 0
    private val resetLock = Mutex()
    
    suspend fun performFullReset(context: Context): ResetResult = withContext(Dispatchers.IO) {
        resetLock.withLock {
            try {
                Log.i("SMB", "Performing SMB full reset #${++resetCount}")
                
                // 1. Отменить все активные операции
                cancelAllSmbOperations()
                
                // 2. Дать время на завершение
                delay(500)
                
                // 3. Закрыть все соединения
                SmbConnectionPool.resetInstance()
                
                // 4. Очистить кэши приложения
                clearApplicationSmbCaches(context)
                
                // 5. Переинициализировать конфигурацию
                SmbConnectionConfig.configure()
                
                // 6. Дать время на стабилизацию
                delay(200)
                
                Log.i("SMB", "SMB reset completed successfully")
                ResetResult.Success
                
            } catch (e: Exception) {
                Log.e("SMB", "SMB reset failed", e)
                ResetResult.Failure(e)
            }
        }
    }
    
    private suspend fun cancelAllSmbOperations() {
        // Отменить все корутины, работающие с SMB
        activeSmbJobs.forEach { job ->
            job.cancel("SMB reset requested")
        }
        activeSmbJobs.clear()
    }
    
    private fun clearApplicationSmbCaches(context: Context) {
        // Очистить SharedPreferences с сохраненными данными SMB (если есть)
        context.getSharedPreferences("smb_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        
        // Очистить кэш файлов SMB (если есть)
        val smbCacheDir = File(context.cacheDir, "smb_cache")
        if (smbCacheDir.exists()) {
            smbCacheDir.deleteRecursively()
        }
    }
    
    private val activeSmbJobs = mutableSetOf<Job>()
    
    fun registerJob(job: Job) {
        activeSmbJobs.add(job)
        job.invokeOnCompletion { activeSmbJobs.remove(job) }
    }
}

sealed class ResetResult {
    object Success : ResetResult()
    data class Failure(val exception: Exception) : ResetResult()
}
```

### Шаг 4: UI для reset

```kotlin
// В Settings или в диалоге ошибки
private fun showSmbResetDialog() {
    AlertDialog.Builder(this)
        .setTitle(R.string.smb_connection_issues)
        .setMessage(R.string.smb_reset_explanation)
        .setPositiveButton(R.string.reset_connection) { _, _ ->
            performSmbReset()
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

private fun performSmbReset() {
    lifecycleScope.launch {
        // Показать прогресс
        val progressDialog = ProgressDialog(this@Activity).apply {
            setMessage(getString(R.string.resetting_smb_connections))
            setCancelable(false)
            show()
        }
        
        val result = SmbResetManager.performFullReset(applicationContext)
        
        progressDialog.dismiss()
        
        when (result) {
            is ResetResult.Success -> {
                Toast.makeText(
                    this@Activity,
                    R.string.smb_reset_successful,
                    Toast.LENGTH_LONG
                ).show()
                
                // Опционально: обновить UI
                refreshSmbResources()
            }
            
            is ResetResult.Failure -> {
                AlertDialog.Builder(this@Activity)
                    .setTitle(R.string.reset_failed)
                    .setMessage(getString(R.string.reset_failed_message, result.exception.message))
                    .setPositiveButton(R.string.restart_app) { _, _ ->
                        restartApplication()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }
}

private fun restartApplication() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    finish()
    exitProcess(0)
}
```

### Шаг 5: Автоматический reset при критических ошибках

```kotlin
class SmbErrorHandler(private val context: Context) {
    
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 5
    
    suspend fun handleError(error: Exception): ErrorAction {
        consecutiveErrors++
        
        return when {
            consecutiveErrors >= maxConsecutiveErrors -> {
                Log.w("SMB", "Too many consecutive errors ($consecutiveErrors), triggering auto-reset")
                
                val resetResult = SmbResetManager.performFullReset(context)
                consecutiveErrors = 0
                
                if (resetResult is ResetResult.Success) {
                    ErrorAction.AutoResetPerformed
                } else {
                    ErrorAction.RequireManualReset
                }
            }
            
            else -> {
                ErrorAction.ShowError
            }
        }
    }
    
    fun onSuccessfulConnection() {
        consecutiveErrors = 0
    }
}

enum class ErrorAction {
    ShowError,
    AutoResetPerformed,
    RequireManualReset
}
```

### Шаг 6: Строки

```xml
<string name="smb_connection_issues">Проблемы с SMB подключением</string>
<string name="smb_reset_explanation">Обнаружены проблемы с SMB подключением. Сброс соединения может помочь. Это закроет все активные подключения и очистит кэш.</string>
<string name="reset_connection">Сбросить соединение</string>
<string name="resetting_smb_connections">Сброс SMB соединений...</string>
<string name="smb_reset_successful">SMB соединения сброшены. Попробуйте подключиться снова.</string>
<string name="reset_failed">Сброс не удался</string>
<string name="reset_failed_message">Не удалось сбросить SMB соединения: %s\n\nРекомендуется перезапустить приложение.</string>
<string name="restart_app">Перезапустить приложение</string>
```

### Шаг 7: Тестирование

#### Сценарий 1: Множественные ошибки → Reset
1. Подключиться к SMB с неверным паролем 3 раза
2. Проверить логи: накопление ошибок
3. Выполнить manual reset через UI
4. Попытаться подключиться с правильным паролем
5. **Ожидаемый результат**: Подключение успешно без перезапуска приложения

#### Сценарий 2: Авто-reset
1. Создать условия для 5+ последовательных ошибок
2. Проверить логи: "triggering auto-reset"
3. Проверить: auto-reset выполнен
4. Попытаться новое подключение
5. **Ожидаемый результат**: Подключение работает

#### Сценарий 3: Несколько SMB ресурсов
1. Подключить 3 разных SMB ресурса
2. Вызвать ошибки на всех
3. Выполнить reset
4. Попытаться подключиться ко всем трем снова
5. **Ожидаемый результат**: Все три подключения работают

#### Сценарий 4: Проверка очистки
1. Перед reset: проверить логи connection pool size
2. Выполнить reset
3. После reset: проверить логи
4. **Ожидаемый результат**: Pool размер = 0

#### Debug проверки
```kotlin
// Добавить в reset для проверки
private fun verifyResetCompleteness() {
    Log.d("SMB_VERIFY", "Connection pool size: ${SmbConnectionPool.getInstance().getSize()}")
    Log.d("SMB_VERIFY", "Error cache size: ${errorCache.size}")
    Log.d("SMB_VERIFY", "Active jobs: ${activeSmbJobs.size}")
    
    // Все должны быть 0
}
```

## Критерии приемки
- ✅ После reset SMB соединения восстанавливаются без перезапуска приложения
- ✅ Connection pool полностью очищается
- ✅ Все кэши (credentials, errors) очищаются
- ✅ jCIFS context переинициализируется
- ✅ Можно подключиться к нескольким SMB ресурсам после reset
- ✅ Автоматический reset при критическом количестве ошибок
- ✅ Ручной reset через UI работает

## Файлы для создания/изменения
- Создать: `SmbConnectionPool.kt`
- Создать: `SmbResetManager.kt`
- Создать: `SmbErrorHandler.kt`
- Изменить: Settings или error handling UI
- Изменить: `res/values/strings.xml` (+ локализации)

## Связанные задачи
- Спецификация #13: SMB Password Timeout

## Примечания
- Убедиться в thread-safety всех операций
- Рассмотреть добавление метрики для отслеживания частоты resets
- Можно добавить настройку для автоматического reset
- Важно: проверить утечки памяти после множественных resets
