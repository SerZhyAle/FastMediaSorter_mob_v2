https://habr.com/ru/sandbox/280806/

PLATFORM: Habr.com
URL: https://habr.com/ru/articles/
SECTION: Хаб «Разработка под Android» + «Хранение данных» + «Файловые системы»
FORMAT: Markdown (Habr поддерживает полный Markdown)
ЯЗЫК: Русский
NOTE: Статья, не анонс. Habr плохо воспринимает чистую рекламу - нужен технический нарратив.
      Заголовок должен отвечать на вопрос "что интересного?" а не "что это за продукт?".
      Раскрой: проблему → архитектурное решение → конкретные технические детали.

---
ЗАГОЛОВОК:
Как я сделал Android-приложение, которое стримит видео с NAS через SMB, не скачивая файл целиком

---
ЛИД (первые 2-3 предложения, они отображаются в превью):

Два года назад я устал переключаться между VLC, файловым менеджером и облачным клиентом каждый раз, когда хотел посмотреть фильм с домашнего NAS. Решение оказалось нетривиальным: ExoPlayer не умеет в SMB из коробки, а готовых реализаций, которые не тормозят на больших файлах, я не нашёл. Рассказываю, что получилось.

---
ТЕЛО СТАТЬИ:

## Постановка задачи

Типичный сценарий: домашний Synology NAS с несколькими сотнями гигабайт видео, аудиокниг и фотографий. Смотреть это со смартфона удобно только если не нужно предварительно копировать файлы локально.

Существующие решения меня не устраивали:
- **VLC** умеет открывать SMB-потоки, но не даёт управлять файлами, переименовывать, перекладывать
- **Solid Explorer / MiXplorer** - отличные файловые менеджеры, но видеоплеер там системный (читай: Intent → сторонний плеер)
- **Google Photos** не знает, что такое NAS
- **nPlayer** платный и без файловых операций

Хотелось: один инструмент, где SMB - полноправный источник наравне с локальным хранилищем, Google Drive и SFTP.

Так появился **FastMediaSorter v2**.

## Архитектура: почему не получилось взять готовое

Ключевая сложность - воспроизведение медиа напрямую из SMB-потока в ExoPlayer.

ExoPlayer (Media3) использует абстракцию `DataSource` для чтения данных. Стандартные реализации: `DefaultDataSource` (локальные файлы + HTTP) и `UriDataSource`. SMB, SFTP, FTP - не поддерживаются.

Пришлось написать кастомные `DataSource`-обёртки:

```kotlin
class SmbDataSource(
    private val smbFile: SmbFile,
    private val connectionPool: SmbConnectionPool
) : DataSource {

    private var inputStream: InputStream? = null
    private var bytesRead: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        val connection = connectionPool.acquire(smbFile.server)
        val file = connection.openFile(smbFile.path, READ)

        // Seek support - критично для перемотки
        if (dataSpec.position > 0) {
            file.seek(dataSpec.position)
        }

        inputStream = file.inputStream
        bytesRead = 0
        return file.length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = inputStream?.read(buffer, offset, length) ?: return C.RESULT_END_OF_INPUT
        bytesRead += read
        return read
    }
}
```

Аналогично для SFTP (через SSHJ) и FTP (Apache Commons Net).

## Проблема №1: перемотка видео через SMB

HTTP-стриминг позволяет jump к любому байту через Range-запросы. SMB работает по-другому: файл открыт как поток, seek() выполняется на уровне протокола.

SMBJ (библиотека для SMB2/SMB3) поддерживает `SMBFile.seek()`, но есть нюанс: при seek на большое смещение (конец 2-часового фильма) на медленном соединении это занимает заметное время. Решение - буферизация с упреждающим чтением и кэш ключевых позиций.

ExoPlayer помогает: он сам управляет буфером через `LoadControl` и запрашивает данные кусками. Достаточно, чтобы DataSource корректно обрабатывал `dataSpec.position`.

## Проблема №2: пул соединений

Наивная реализация: открывать новое SMB-соединение на каждый файл. На папке с 200 миниатюрами это значит 200 handshake'ов - секунды ожидания при открытии каталога.

Решение: `SmbConnectionPool` - пул с TTL и переиспользованием сессий:

```kotlin
class SmbConnectionPool(private val maxConnections: Int = 8) {

    private val pool = ConcurrentHashMap<String, ArrayDeque<SMBConnection>>()

    fun acquire(host: String): SMBConnection {
        val available = pool[host]?.removeFirstOrNull()
        return available?.takeIf { it.isAlive } ?: createConnection(host)
    }

    fun release(host: String, connection: SMBConnection) {
        if (pool[host]?.size ?: 0 < maxConnections) {
            pool.getOrPut(host) { ArrayDeque() }.addLast(connection)
        } else {
            connection.close()
        }
    }
}
```

Соединения живут пока активны и возвращаются в пул после операции. Открытие большой папки с миниатюрами ускорилось в 4-5 раз.

## Проблема №3: кэш списка файлов

SMB-листинг директории с 10 000 файлов через Wi-Fi занимает 2-8 секунд в зависимости от оборудования. Повторный вход в ту же папку должен быть мгновенным.

Используем Room DB: после первого сканирования индекс файлов (имя, размер, дата, тип) записывается в локальную базу. При повторном открытии - мгновенно из кэша, фоновое обновление асинхронно.

```kotlin
@Entity(tableName = "file_cache")
data class FileCacheEntry(
    @PrimaryKey val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val resourceId: Long,
    val cachedAt: Long = System.currentTimeMillis()
)
```

TTL кэша: 30 минут по умолчанию (настраивается). При изменении файлов через само приложение - инвалидация соответствующих записей.

## Многопоточная передача файлов

Копирование большого количества файлов через SMB последовательно медленнее, чем параллельно (протокол позволяет несколько одновременных сессий).

Используется `Dispatchers.IO` с ограниченным пулом потоков (до 24, настраивается). Каждый файл - отдельная корутина с семафором:

```kotlin
val semaphore = Semaphore(maxParallelTransfers)

files.map { file ->
    async(Dispatchers.IO) {
        semaphore.withPermit {
            transferFile(file, destination)
        }
    }
}.awaitAll()
```

На практике: передача 500 файлов по 5 МБ через Gigabit LAN на Synology - ~3× быстрее при 8 потоках vs 1.

## OCR прямо в просмотрщике

Функция "AR-перевод" (аналог Google Lens) работает не через камеру, а на открытом в приложении файле. Использует ML Kit On-Device Text Recognition.

Для фото с NAS: файл не скачивается полностью - загружается через `BitmapRegionDecoder` с нужным разрешением для OCR, исходный файл на NAS не трогается.

```kotlin
suspend fun recognizeText(source: MediaSource): List<TextBlock> {
    val bitmap = loadBitmapForOcr(source) // downscaled, from cache if available
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return recognizer.process(bitmap).await().textBlocks
}
```

Результат - наложение translucent-блоков с переведённым текстом поверх оригинального изображения.

## Операции по расписанию

WorkManager + PeriodicWorkRequest. Минимальный интервал - 15 минут (ограничение Android).

Атомарный MOVE реализован так:
1. Копирование файла в destination
2. Верификация: сравнение размера и CRC32 (SHA-256 слишком медленно для больших файлов при каждом запуске)
3. Только при успехе - удаление источника

Если шаг 2 или 3 упал - источник остаётся нетронутым. Следующий запуск повторит попытку.

## Итог

В результате получилось приложение, которым я сам пользуюсь ежедневно. Основные метрики:
- Открытие папки с 3000 файлов на NAS: первый раз ~4 сек, повторный <0.3 сек
- Стриминг 4K-видео через Gigabit LAN: буферизация 2-3 сек, затем без прерываний
- Передача 1000 файлов (mix 1-50 МБ): ~3× ускорение при 8 потоках vs 1

**Технический стек:**
- Kotlin 100%, minSdk 26 (API 23 для Legacy-флейвора)
- MVVM + Clean Architecture, Hilt DI
- ExoPlayer (Media3 1.2.1)
- SMBJ (SMB2/SMB3), SSHJ (SFTP), Apache Commons Net (FTP)
- Room DB (кэш + операции по расписанию), WorkManager
- ML Kit (OCR), Glide (превью с кастомным NetworkFileModelLoader)

**Ссылки:**
- Google Play: https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
- GitHub: https://github.com/SerZhyAle/FastMediaSorter_mob_v2

Готов ответить на вопросы по реализации - особенно по SMB/ExoPlayer интеграции и WorkManager-шедулингу в условиях агрессивного battery optimization на Chinese OEM.
