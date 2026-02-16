# Спецификация #9: Прогресс копирования сетевых файлов

## Описание задачи
При попытке поделиться файлом с сетевого ресурса (FTP, SMB) через Android Share, приложение копирует файл локально перед отправкой. Необходимо показывать прогресс этой операции, чтобы пользователь не думал, что приложение зависло.

## Требования

### Функциональные требования
- Показывать диалог прогресса при копировании сетевого файла
- Отображать процент загрузки и/или скорость
- Возможность отменить операцию
- После успешного копирования открывать стандартный Android Share диалог
- При ошибке показывать соответствующее сообщение

### Технические требования
- Операция должна выполняться в фоновом потоке
- Не блокировать UI
- Корректно обрабатывать прерывания (отмена, поворот экрана)
- Очищать временные файлы после завершения

## Пошаговая реализация

### Шаг 1: Найти код Android Share
1. Найти место, где вызывается Intent.ACTION_SEND или аналогичный
2. Вероятные файлы:
   - `FileOperationsHandler.kt`
   - `ShareManager.kt`
   - `PlayerActivity.kt`

### Шаг 2: Определить тип файла (локальный/сетевой)
1. Добавить метод проверки:
   ```kotlin
   fun MediaFile.isNetworkFile(): Boolean {
       return this.uri.scheme in listOf("ftp", "ftps", "smb", "http", "https") ||
              this.source?.isRemote == true
   }
   
   fun MediaFile.needsLocalCopy(): Boolean {
       // Для share нужна локальная копия сетевых файлов
       return isNetworkFile()
   }
   ```

### Шаг 3: Создать диалог прогресса
1. Создать layout для прогресс диалога:
   `res/layout/dialog_file_copy_progress.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
       android:layout_width="match_parent"
       android:layout_height="wrap_content"
       android:orientation="vertical"
       android:padding="24dp">
       
       <TextView
           android:id="@+id/titleText"
           android:layout_width="match_parent"
           android:layout_height="wrap_content"
           android:text="@string/copying_file"
           android:textSize="18sp"
           android:textStyle="bold" />
       
       <TextView
           android:id="@+id/fileNameText"
           android:layout_width="match_parent"
           android:layout_height="wrap_content"
           android:layout_marginTop="8dp"
           android:ellipsize="middle"
           android:singleLine="true"
           android:textSize="14sp" />
       
       <ProgressBar
           android:id="@+id/progressBar"
           style="@android:style/Widget.ProgressBar.Horizontal"
           android:layout_width="match_parent"
           android:layout_height="wrap_content"
           android:layout_marginTop="16dp" />
       
       <TextView
           android:id="@+id/progressText"
           android:layout_width="match_parent"
           android:layout_height="wrap_content"
           android:layout_marginTop="8dp"
           android:gravity="center"
           android:text="0%"
           android:textSize="14sp" />
       
       <TextView
           android:id="@+id/speedText"
           android:layout_width="match_parent"
           android:layout_height="wrap_content"
           android:layout_marginTop="4dp"
           android:gravity="center"
           android:textSize="12sp"
           android:textColor="?android:textColorSecondary" />
       
       <Button
           android:id="@+id/cancelButton"
           android:layout_width="wrap_content"
           android:layout_height="wrap_content"
           android:layout_gravity="center"
           android:layout_marginTop="16dp"
           android:text="@string/cancel"
           style="@style/Widget.MaterialComponents.Button.TextButton" />
       
   </LinearLayout>
   ```

### Шаг 4: Создать класс для копирования с прогрессом
1. Создать `NetworkFileCopier.kt`:
   ```kotlin
   class NetworkFileCopier(
       private val context: Context
   ) {
       
       interface ProgressListener {
           fun onProgressUpdate(bytesRead: Long, totalBytes: Long, speed: Long)
           fun onComplete(localFile: File)
           fun onError(error: Exception)
           fun onCancelled()
       }
       
       private var isCancelled = false
       
       suspend fun copyFileToLocal(
           sourceFile: MediaFile,
           listener: ProgressListener
       ) = withContext(Dispatchers.IO) {
           
           isCancelled = false
           
           try {
               // Создать временный файл
               val tempFile = createTempFile(sourceFile)
               
               // Открыть input stream
               val inputStream = openInputStream(sourceFile)
               val outputStream = FileOutputStream(tempFile)
               
               val totalBytes = sourceFile.size
               var bytesRead = 0L
               val buffer = ByteArray(8192)
               var lastProgressTime = System.currentTimeMillis()
               var lastProgressBytes = 0L
               
               inputStream.use { input ->
                   outputStream.use { output ->
                       var read: Int
                       while (input.read(buffer).also { read = it } != -1) {
                           
                           // Проверка на отмену
                           if (isCancelled) {
                               tempFile.delete()
                               withContext(Dispatchers.Main) {
                                   listener.onCancelled()
                               }
                               return@withContext
                           }
                           
                           output.write(buffer, 0, read)
                           bytesRead += read
                           
                           // Обновление прогресса каждые 200ms
                           val currentTime = System.currentTimeMillis()
                           if (currentTime - lastProgressTime >= 200) {
                               val timeDiff = currentTime - lastProgressTime
                               val bytesDiff = bytesRead - lastProgressBytes
                               val speed = (bytesDiff * 1000 / timeDiff) // bytes/sec
                               
                               withContext(Dispatchers.Main) {
                                   listener.onProgressUpdate(bytesRead, totalBytes, speed)
                               }
                               
                               lastProgressTime = currentTime
                               lastProgressBytes = bytesRead
                           }
                       }
                   }
               }
               
               // Завершено успешно
               withContext(Dispatchers.Main) {
                   listener.onComplete(tempFile)
               }
               
           } catch (e: Exception) {
               withContext(Dispatchers.Main) {
                   listener.onError(e)
               }
           }
       }
       
       fun cancel() {
           isCancelled = true
       }
       
       private fun createTempFile(sourceFile: MediaFile): File {
           val cacheDir = context.externalCacheDir ?: context.cacheDir
           val shareDir = File(cacheDir, "share_temp").apply { mkdirs() }
           return File(shareDir, sourceFile.name)
       }
       
       private fun openInputStream(file: MediaFile): InputStream {
           // Реализация зависит от типа источника
           return when {
               file.uri.scheme == "ftp" -> openFtpStream(file)
               file.uri.scheme == "smb" -> openSmbStream(file)
               else -> context.contentResolver.openInputStream(file.uri)
                   ?: throw IOException("Cannot open stream")
           }
       }
   }
   ```

### Шаг 5: Создать диалог с прогрессом
```kotlin
class FileCopyProgressDialog(
    context: Context,
    private val fileName: String
) : Dialog(context) {
    
    private lateinit var binding: DialogFileCopyProgressBinding
    private val fileCopier = NetworkFileCopier(context)
    private var copyJob: Job? = null
    
    var onComplete: ((File) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null
    
    init {
        setCancelable(false)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogFileCopyProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.fileNameText.text = fileName
        binding.cancelButton.setOnClickListener {
            cancelCopy()
        }
    }
    
    fun startCopy(file: MediaFile, scope: CoroutineScope) {
        copyJob = scope.launch {
            fileCopier.copyFileToLocal(file, object : NetworkFileCopier.ProgressListener {
                
                override fun onProgressUpdate(bytesRead: Long, totalBytes: Long, speed: Long) {
                    val progress = (bytesRead * 100 / totalBytes).toInt()
                    binding.progressBar.progress = progress
                    binding.progressText.text = "$progress%"
                    binding.speedText.text = formatSpeed(speed)
                }
                
                override fun onComplete(localFile: File) {
                    dismiss()
                    onComplete?.invoke(localFile)
                }
                
                override fun onError(error: Exception) {
                    dismiss()
                    onError?.invoke(error)
                }
                
                override fun onCancelled() {
                    dismiss()
                }
            })
        }
    }
    
    private fun cancelCopy() {
        fileCopier.cancel()
        copyJob?.cancel()
        dismiss()
    }
    
    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }
}
```

### Шаг 6: Интегрировать в Share функционал
```kotlin
// В FileOperationsHandler или аналогичном
fun shareFile(file: MediaFile, activity: Activity) {
    if (file.needsLocalCopy()) {
        // Показать прогресс и скопировать
        val dialog = FileCopyProgressDialog(activity, file.name)
        
        dialog.onComplete = { localFile ->
            shareLocalFile(localFile, activity)
        }
        
        dialog.onError = { error ->
            Toast.makeText(
                activity,
                activity.getString(R.string.error_copying_file, error.message),
                Toast.LENGTH_LONG
            ).show()
        }
        
        dialog.show()
        dialog.startCopy(file, activity.lifecycleScope)
        
    } else {
        // Локальный файл, сразу share
        shareLocalFile(file.toFile(), activity)
    }
}

private fun shareLocalFile(file: File, activity: Activity) {
    val uri = FileProvider.getUriForFile(
        activity,
        "${activity.packageName}.fileprovider",
        file
    )
    
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = file.getMimeType()
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    activity.startActivity(Intent.createChooser(shareIntent, null))
}
```

### Шаг 7: Добавить строки
```xml
<string name="copying_file">Копирование файла...</string>
<string name="error_copying_file">Ошибка копирования: %s</string>
```

### Шаг 8: Очистка временных файлов
```kotlin
// При закрытии приложения или периодически
fun cleanupTempShareFiles(context: Context) {
    val cacheDir = context.externalCacheDir ?: context.cacheDir
    val shareDir = File(cacheDir, "share_temp")
    
    shareDir.listFiles()?.forEach { file ->
        // Удалить файлы старше 1 часа
        if (System.currentTimeMillis() - file.lastModified() > 3600000) {
            file.delete()
        }
    }
}
```

### Шаг  9: Тестирование
1. Подключиться к FTP/SMB ресурсу
2. Открыть файл и выбрать "Поделиться"
3. Проверить:
   - Появляется диалог с прогрессом
   - Отображается название файла
   - Прогресс бар и проценты обновляются
   - Показывается скорость копирования
   - Можно отменить операцию
   - После завершения открывается Android Share
4. Тестировать с разными размерами файлов и скоростями сети

## Критерии приемки
- ✅ При share сетевого файла показывается диалог прогресса
- ✅ Отображается процент выполнения и скорость
- ✅ Пользователь может отменить операцию
- ✅ После копирования открывается стандартный Android Share
- ✅ При ошибке показывается понятное сообщение
- ✅ Временные файлы очищаются

## Файлы для создания/изменения
- Создать: `NetworkFileCopier.kt`
- Создать: `FileCopyProgressDialog.kt`
- Создать: `res/layout/dialog_file_copy_progress.xml`
- Изменить: `FileOperationsHandler.kt` (или аналогичный)
- Изменить: `res/values/strings.xml`

## Примечания
- Рассмотреть использование WorkManager для больших файлов
- Добавить настройку для автоматической очистки кэша
