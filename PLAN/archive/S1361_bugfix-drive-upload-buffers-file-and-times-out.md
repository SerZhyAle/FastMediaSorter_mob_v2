# Спецификация (compact bugfix): S1361 - Загрузка в Google Drive буферизует файл целиком и падает по 60-секундному таймауту

**Ticket:** S1361
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-02
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-02

**Текст:**

Автозахват при анализе удалённого лог-бандла (`/newlog`), сессия `logs/fastmediasorter_20260801_183450.log`, устройство SM-S731B, Android 16 / API 36, сборка `2.60.7302.058-NoLegal-DEBUG`, мобильная сеть (rmnet0, адрес 10.56.x).

Перенос файлов в облачный ресурс "temp" (Google Drive). Файлы ~130 КБ уезжают за 5 секунд, файлы ~2 МБ два раза подряд отваливаются ровно через 60 секунд с `SocketTimeoutException`. Пользователю показывается "Не получилось загрузить файл в облако. Попробуйте ещё раз.", повтор не выполняется, файл в пакете просто пропускается.

Лог-строки:

```
[29578] 20:29:53  D  uploadToCloudFromPath: START - fileName=screenshot_20260802_001802.png, cloudPath=cloud:/google_drive/0B2Wse.., sourceType=LOCAL
[29579] 20:30:54  E  Failed to upload file
[29617] 20:30:54  E  uploadToCloudFromPath: FAILED - Не получилось загрузить файл в облако. Попробуйте ещё раз.
[29618] 20:30:54  D  uploadToCloudFromPath: START - fileName=screenshot_20260802_001049.png ..
[29621] 20:30:59  I  uploadToCloudFromPath: SUCCESS - uploaded screenshot_20260802_001049.png
[30832] 20:32:30  D  uploadToCloudFromPath: START - fileName=screenshot_20260802_001802.png ..
[30931] 20:33:30  E  Failed to upload file
[30984] 20:33:30  E  uploadToCloudFromPath: FAILED - Не получилось загрузить файл в облако. Попробуйте ещё раз.
```

Размеры файлов из того же бандла: `screenshot_20260802_001802.png` = 2 016 006 байт (отказ), `screenshot_20260802_001049.png` = 130 692 байта (успех).

Стек:

```
java.net.SocketTimeoutException: timeout
	at com.android.okhttp.okio.Okio$3.newTimeoutException(Okio.java:214)
	..
	at com.android.okhttp.internal.huc.HttpURLConnectionImpl.getResponseCode(HttpURLConnectionImpl.java:542)
	at com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveMultipartUploader.upload(GoogleDriveMultipartUploader.kt:60)
	at com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient$uploadFile$2.invokeSuspend(GoogleDriveRestClient.kt:471)
	at com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler$uploadToCloudFromPath$result$1.invokeSuspend(CloudFileOperationHandler.kt:947)
Caused by: java.net.SocketException: Socket closed
```

---

## 1. Проблема / симптом

Перенос или копирование в Google Drive теряет крупные файлы на медленном канале: ~60 секунд ожидания, ошибка, файл пропущен, повторной попытки нет. На той же сессии мелкие файлы проходят, то есть учётные данные и папка назначения исправны.

Ровно 60 секунд - это `HttpTimeouts.STREAM_READ_MS`, применённый в `GoogleDriveMultipartUploader.upload`. Таймаут введён в S1298 и работает как задумано; проблема в том, что под этот же бюджет попадает вся отправка тела.

Три отдельных дефекта в одном месте:

1. `connection.doOutput = true` без `setFixedLengthStreamingMode` / `setChunkedStreamingMode` - `HttpURLConnection` буферизует всё тело в памяти и отправляет его внутри `getResponseCode()`. На устройстве с `MemoryTier=LOW` и heapMax 512 МБ это же означает риск OOM на видеофайле.
2. Из-за пункта 1 время отправки тела списывается с read-таймаута: медленный аплинк не "медленно читает ответ", а не успевает дописать тело.
3. Ни повтора, ни возобновляемой загрузки (Drive resumable upload) нет - одна ошибка сети = потерянный файл в пакете.

Побочно: `progressCallback` вызывается на каждом `outputStream.write` в буфер, то есть прогресс доезжает до 100 % задолго до реальной отправки.

---

## 2. Корневая причина

Ответ сервера на загрузку приходит только после того, как принято всё тело запроса. Значит время до первого байта ответа примерно равно длительности отправки файла и растёт линейно с его размером. `readTimeout`, который `applyTimeouts(HttpTimeouts.STREAM_READ_MS)` ставит равным 60 000 мс, взводится именно на это ожидание - то есть на величину, которая от размера файла зависит, а константой не описывается.

Почему стена ровно 60 секунд, а не «размер / скорость»: без `setFixedLengthStreamingMode` тело целиком уходит в память (`RetryableSink`), затем из памяти - в буфер сокета. На LTE-соединении буфер отправки ядра вмещает все 2 МБ, поэтому запись возвращает управление почти мгновенно, чтение ответа стартует в момент t=0 и упирается в свой 60-секундный бюджет, пока данные ещё уходят в сеть.

Арифметика из лога подтверждает: 130 692 байта прошли за 5 с (~26 КБ/с), 2 016 006 байт при той же скорости требуют ~66 с - на 6 секунд больше бюджета, отсюда детерминированный отказ обоих повторов.

Три следствия одной причины:

1. Любой файл крупнее `скорость_аплинка × 60 с` не загружается в принципе - на медленном канале это единицы мегабайт.
2. Тело буферизуется целиком в памяти: на устройстве с `MemoryTier=LOW` и heapMax 512 МБ видеофайл даёт риск OOM.
3. `progressCallback` считает запись в память, а не в сеть, поэтому прогресс достигает 100 % задолго до реальной отправки, а `TransferProgress.total` вообще передаётся нулём.

Ошибка сети никак не переигрывается: `uploadToCloudFromPath` возвращает `null`, и файл выпадает из пакета.

Область поражения шире Drive. Тот же `HttpURLConnection`-контур с тем же фиксированным бюджетом - `OneDriveRestClient.uploadFile` (PUT `/content`). `DropboxClient` не затронут: SDK сам стримит и уже обёрнут в `withRetry`.

Побочный дефект, найденный по пути: в `CloudFileOperationHandler.uploadToCloud` поток открывается **снаружи** лямбды `executeWithAutoReauth`, поэтому повтор после переавторизации отправляет уже вычерпанный поток, то есть пустой файл. В `CloudToCloudTransferHelper.copyCloudToCloud` того же дефекта нет - там клиент берётся через `getCloudClient`, без обёртки переавторизации; поток там лишь не закрывается при исключении.

---

## 3. Исправление

Бюджет чтения ответа на загрузку выводится из размера тела, тело стримится без буферизации, а единичный сбой сети больше не теряет файл.

### Фаза 1 - размерный бюджет чтения

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/network/HttpTimeouts.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/core/network/HttpTimeoutsTest.kt`

#### Step 1.1 - Add a size-derived upload read budget to `HttpTimeouts`

**Prompt for developer:**

> Add to `HttpTimeouts`: `UPLOAD_MIN_UPLINK_BYTES_PER_SEC = 16 * 1024`, `UPLOAD_READ_MAX_MS = 600_000`, and `fun uploadReadTimeoutMs(contentLengthBytes: Long): Int` returning `UPLOAD_READ_MAX_MS` when the length is unknown (`<= 0`), otherwise `STREAM_READ_MS + contentLengthBytes * 1000 / UPLOAD_MIN_UPLINK_BYTES_PER_SEC` coerced to at most `UPLOAD_READ_MAX_MS`. Compute in `Long` and convert once at the end so a multi-gigabyte length cannot overflow. Document in KDoc that the response to an upload cannot arrive before the whole body has been received, so this budget must scale with the body.

**Why:**

A fixed 60-second read budget cannot cover a wait whose length is proportional to file size divided by uplink speed, which is why every file above roughly two megabytes fails deterministically on a mobile link.

**Verification:**

- `Grep` - `fun uploadReadTimeoutMs` present exactly once in `HttpTimeouts.kt`.
- `Grep` - `UPLOAD_READ_MAX_MS` present.

**Status:** `[x]` done

#### Step 1.2 - Cover the budget helper with unit tests

**Prompt for developer:**

> Add `HttpTimeoutsTest` asserting: unknown length (`0` and `-1`) returns `UPLOAD_READ_MAX_MS`; a 2 016 006-byte body returns more than the 66 000 ms the reported session needed; a 10 GB body is clamped to `UPLOAD_READ_MAX_MS` and does not overflow into a negative value; a 1-byte body still returns at least `STREAM_READ_MS`.

**Why:**

The exact failing size from the reported session is the only regression anchor available without a throttled network, so it belongs in an automated test rather than in a manual device check.

**Verification:**

- `.\a.ps1 fu` - `HttpTimeoutsTest` passes.

**Status:** `[x]` done

### Фаза 2 - стриминг тела и реальный прогресс

**Files:** `CloudStorageClient.kt`, `GoogleDriveMultipartUploader.kt`, `GoogleDriveRestClient.kt`, `OneDriveRestClient.kt`, `DropboxClient.kt`, `CloudFileOperationHandler.kt`, `CloudToCloudTransferHelper.kt`, `CloudOperationStrategy.kt`, `BackupToGoogleDriveUseCase.kt`, `NetworkSpeedTestUseCase.kt`, `BackupToGoogleDriveUseCaseTest.kt`

#### Step 2.1 - Add `fileSize` to the `CloudStorageClient.uploadFile` contract

**Prompt for developer:**

> Add `fileSize: Long = 0L` to `CloudStorageClient.uploadFile`, placed after `parentFolderId` and before `progressCallback`, with KDoc stating that `0` means unknown. Mirror the parameter into `GoogleDriveRestClient`, `OneDriveRestClient` and `DropboxClient`. Pass the real size from every call site that has a `File` in hand: both `CloudFileOperationHandler` uploads, `CloudToCloudTransferHelper.copyCloudToCloud`, `CloudOperationStrategy`, both `BackupToGoogleDriveUseCase` uploads and `NetworkSpeedTestUseCase`. Update the `uploadFile` matchers in `BackupToGoogleDriveUseCaseTest` to take the extra argument.

**Why:**

Both the streaming mode and the size-derived timeout need the body length, and the transport clients only ever receive an `InputStream`, whose `available()` is not a reliable size.

**Verification:**

- `Grep` - `fileSize: Long = 0L` present in `CloudStorageClient.kt`.
- `.\a.ps1 fk` - compiles.
- `.\a.ps1 fu` - `BackupToGoogleDriveUseCaseTest` passes.

**Status:** `[x]` done

#### Step 2.2 - Stream the Drive multipart body and scale its read budget

**Prompt for developer:**

> Give `GoogleDriveMultipartUploader.upload` a `fileSize: Long` parameter. When it is positive, compute the exact multipart body length (both boundary blocks, the metadata part, the content headers, the file bytes and the closing delimiter), call `connection.setFixedLengthStreamingMode(totalLength)`, and apply `connection.applyTimeouts(HttpTimeouts.uploadReadTimeoutMs(totalLength))`. When the size is unknown, keep the current buffered write but still apply `uploadReadTimeoutMs(0)`. Report progress as `TransferProgress(totalBytes, fileSize)`. Build the byte arrays for the fixed parts once so the length calculation and the write cannot drift apart.

**Why:**

Buffering the whole body in memory risks an out-of-memory kill on a low-memory device with a video file, and it is what lets the kernel socket buffer absorb the upload so the response read starts before the data has actually left the device.

**Verification:**

- `Grep` - `setFixedLengthStreamingMode` present in `GoogleDriveMultipartUploader.kt`.
- `Grep` - `uploadReadTimeoutMs` present in `GoogleDriveMultipartUploader.kt`.
- `Grep` - `TransferProgress(totalBytes, 0L)` absent from `GoogleDriveMultipartUploader.kt`.

**Status:** `[x]` done

#### Step 2.3 - Apply the same treatment to the OneDrive upload

**Prompt for developer:**

> In `OneDriveRestClient.uploadFile` call `connection.setFixedLengthStreamingMode(fileSize)` when the size is positive, replace `applyTimeouts(HttpTimeouts.STREAM_READ_MS)` with `applyTimeouts(HttpTimeouts.uploadReadTimeoutMs(fileSize))`, and report progress as `TransferProgress(totalBytes, fileSize)`. Leave the download paths on `STREAM_READ_MS` - a download response starts arriving immediately and needs no size-derived budget.

**Why:**

The OneDrive PUT carries the identical defect shape, and patching only the reported provider leaves the same failure waiting behind a different cloud account.

**Verification:**

- `Grep` - `uploadReadTimeoutMs` present in `OneDriveRestClient.kt`.
- `Grep` - `STREAM_READ_MS` still present in `OneDriveRestClient.kt` for the download paths.

**Status:** `[x]` done

### Фаза 3 - устойчивость пакетного переноса

**Files:** `CloudFileOperationHandler.kt`, `CloudToCloudTransferHelper.kt`

#### Step 3.1 - Retry a transient upload failure instead of dropping the file

**Prompt for developer:**

> In `CloudFileOperationHandler.uploadToCloudFromPath` wrap the upload in a bounded retry: up to three attempts, re-opening the `FileInputStream` on each one, with a 2-second and then a 5-second pause between them. Retry only when the `CloudResult.Error` carries an `IOException` cause; return immediately on anything else, including an error with no cause, which is how the clients report an HTTP rejection that would repeat identically. Log each retry at `Timber.w` with the attempt number and the file name.

**Why:**

A single network hiccup currently drops the file out of the batch with no second attempt, which is how the reported session lost the same screenshot twice.

**Verification:**

- `Grep` - `uploadToCloudFromPath` retry loop present with a max-attempt constant.
- `.\a.ps1 fk` - compiles.

**Status:** `[x]` done

#### Step 3.2 - Open the upload stream inside the re-auth lambda

**Prompt for developer:**

> In `CloudFileOperationHandler.uploadToCloud` move the `InputStream` creation inside the `executeWithAutoReauth` lambda and close it there, matching what `uploadToCloudFromPath` already does. In `CloudToCloudTransferHelper.copyCloudToCloud` there is no re-auth wrapper to move into - wrap the upload stream in `use` so it closes when the upload throws.

**Why:**

`executeWithAutoReauth` re-invokes its lambda after a token refresh, so a stream opened outside it is already exhausted on the second attempt and uploads a zero-byte file.

**Verification:**

- `Grep` - no `.inputStream()` binding above the `executeWithAutoReauth` call in `CloudFileOperationHandler.uploadToCloud`.
- `.\a.ps1 fk` - compiles.

**Status:** `[x]` done

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1298 (missing-http-timeouts-cloud-ocr, Implemented) - ввёл сам таймаут; S1156 (Cloud thumbnail bitmap downsampling / memory, BlockExternal).
- **Assumed floor uplink:** 16 КБ/с. При нём файл 2 МБ получает бюджет 183 с против наблюдённой потребности 66 с. Потолок 10 минут сохраняет исходную цель S1298 - не висеть бесконечно на мёртвом пире.
- **Out of scope:** возобновляемая загрузка Drive (resumable upload session) и OneDrive upload session для файлов свыше 4 МБ - отдельные тикеты, если размерный бюджет окажется недостаточным.
- **Известный пробел:** транспортные клиенты теряют HTTP-код при отказе и возвращают ошибку без причины, поэтому повтор не срабатывает на временном 5xx. Пробрасывать код наверх - отдельная работа.

---

## 4. Проверка

- Юнит-тест `HttpTimeoutsTest` фиксирует бюджет на точном размере из отчёта (2 016 006 байт) и на переполнении.
- `.\a.ps1 fk` и `.\a.ps1 fu` зелёные.
- Устройство: перенести в Drive файл 20+ МБ на дросселированном канале, подтвердить отсутствие `Failed to upload file`, корректный процент прогресса и рост heap в пределах нормы.
- Устройство: оборвать сеть в середине загрузки и убедиться, что в логе есть повтор, а не молчаливый пропуск файла.
