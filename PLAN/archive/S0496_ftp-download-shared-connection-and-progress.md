# S0496 - FTP-загрузка: изоляция соединения + byte-прогресс

**Status:** Archived
**Priority:** 45
**Date:** 2026-06-18

<!-- auto-approved by /spec-all - 2026-06-18 -->

## 0. Raw capture (symptom / evidence)

Найдено при адверсариал-ревью S0493 (две независимые проблемы в FTP-ветке `DownloadNetworkFileUseCase`):

1. Гонка общего соединения. `DownloadNetworkFileUseCase.downloadFtpFile()` делает `ftpClient.connect(..)` затем `ftpClient.downloadFile(..)` на `@Singleton FtpClient` с общим изменяемым полем `private var ftpClient: FTPClient?`. `connect()` внутри вызывает `disconnect()`. При двух параллельных FTP-операциях (фоновая музыка + «Отправить в..» с того же FTP) второй `connect()` рвёт соединение первого -> загрузка первого падает. Готовый безопасный путь: `FtpClient.downloadFileWithNewConnection(..)` (изолированное соединение на вызов через `FtpStandaloneOperations`).

2. Нет byte-level прогресса. FTP-download помечен `@Suppress("UNUSED_PARAMETER") progressCallback` - Apache Commons Net `retrieveFile()` не отдаёт прогресс. S0493 обошёл это спиннером для ftp/sftp, но определённый прогресс был бы лучше.

## 1. Цель

Сделать FTP-загрузку в `DownloadNetworkFileUseCase` атомарной (изолированное соединение на каждый вызов, без разрыва чужого) и отдающей byte-level прогресс. После этого «Отправить в..» (S0493) для FTP показывает определённый прогресс-бар вместо спиннера. Затрагивает всех потребителей `execute()` (фоновая музыка, file-info, GIF-редактор, send-to).

Прогресс вешается на `FtpStandaloneOperations.downloadFile` - именно этот путь становится живым после перехода на `downloadFileWithNewConnection`. `FtpConnectedOperations.downloadFile` остаётся нетронутым: его вызывающие (transfer-стратегии, Glide-загрузчики) передают `progressCallback = null`, так что проводка туда была бы scaffolding без потребителя.

**Расширение задачи (S0496-Extended):**
1. Включить byte-level прогресс для SFTP-загрузки (через авто-резолв размера файла по `stat(path)` внутри `SftpClient.downloadFile` при переданном `fileSize = 0L`), что позволит показывать определённый прогресс-бар в «Отправить в..» и для SFTP.
2. Реализовать поддержку byte-level прогресса для FTP-выгрузки (upload) как в режиме одиночного (`FtpStandaloneOperations.uploadFile`), так и постоянного соединения (`FtpConnectedOperations.uploadFile`) с помощью обёртки входного потока `FtpProgressInputStream`.

## 2. Зависимости и факты

- `FtpClient.downloadFileWithNewConnection(host, port, username, password, remotePath, outputStream, fileSize, progressCallback)` уже существует и делегирует в `FtpStandaloneOperations.downloadFile` (`FtpClient.kt:314`).
- `ByteProgressCallback.onProgress(bytesTransferred, totalBytes, speedBytesPerSecond)` - `suspend`. SMB/SFTP-ветки `DownloadNetworkFileUseCase` уже строят адаптер `ByteProgressCallback` -> `((Int) -> Unit)` (только при `totalBytes > 0`).
- `FtpStandaloneOperations.downloadFile` сейчас зовёт `tempClient.retrieveFile(remotePath, outputStream)` внутри `executeWithNewConnection { .. }`; `block` там non-suspend.
- `retrieveFile` пишет в `OutputStream` через `Util.copyStream` -> вызовы `write(buf, 0, len)`, поэтому counting-обёртка должна переопределять 3-арг `write`.
- Размер удалённого файла: `FTPClient.mlistFile(path)?.size` (MLST, есть на vsftpd/proftpd/IIS/FileZilla) -> fallback `listFiles(path).firstOrNull()?.size` -> fallback параметр `fileSize`.
- (Расширение) SFTP: размер удалённого файла можно узнать через `ChannelSftp.stat(remotePath).size`. `SftpClient.downloadFile` принимает `fileSize` и `progressCallback`, но сейчас вызывается с `fileSize = 0L` в `DownloadNetworkFileUseCase.downloadSftpFile`, из-за чего прогресс не транслируется.
- (Расширение) FTP Upload: Apache Commons Net `storeFile` читает из `InputStream` в буфер, поэтому для подсчёта переданных байт на отправку можно обернуть исходный поток в `FilterInputStream` (по аналогии с `FtpProgressOutputStream` для загрузки).

## 3. Фазы

### Phase 01 - Isolate FTP download connection (race fix)

- Add parameter `progressCallback: ((Int) -> Unit)? = null` to `DownloadNetworkFileUseCase.downloadFtpFile`.
- In `execute()`, change the ftp branch to forward the callback: `downloadFtpFile(remotePath, targetFile, progressCallback)`.
- Replace the `downloadFtpFile` body: keep URL parse + credentials lookup, then build a `ByteProgressCallback` adapter identical to the SMB/SFTP branches (emit `((bytesTransferred * 100) / totalBytes).toInt()` only when `totalBytes > 0`), and run `FileOutputStream(targetFile).use { ftpClient.downloadFileWithNewConnection(server, port, credentials.username, credentials.password, filePath, it, fileSize = 0L, progressCallback = adapter) }`. Return `result.isSuccess`.
- Delete the `ftpClient.connect(..)` / `ftpClient.disconnect()` calls and the dead `connectResult` block from `downloadFtpFile`.

Verification:
- `rg "ftpClient\.(connect|disconnect)\(" app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DownloadNetworkFileUseCase.kt` -> no matches.
- `rg "downloadFileWithNewConnection" app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DownloadNetworkFileUseCase.kt` -> exactly 1 match.
- `.\a.ps1 fk` exit 0.

### Phase 02 - Byte-level progress for standalone FTP download

- Add `FtpProgressOutputStream` (`data/remote/ftp/FtpProgressOutputStream.kt`): a `FilterOutputStream` that adds the written length to an injected `AtomicLong` counter; override `write(b: Int)` and `write(b: ByteArray, off: Int, len: Int)`, each delegating to the wrapped stream then incrementing the counter.
- Change `FtpStandaloneOperations.executeWithNewConnection` block type to `suspend (FTPClient) -> Result<T>` (existing non-suspend lambda callers stay source-compatible).
- Rewrite `FtpStandaloneOperations.downloadFile` body (inside `executeWithNewConnection`):
  - Resolve `total`: `tempClient.mlistFile(remotePath)?.size?.takeIf { it > 0 }` ?: `tempClient.listFiles(remotePath).firstOrNull()?.size?.takeIf { it > 0 }` ?: `fileSize.takeIf { it > 0 }` ?: -1L.
  - `val counter = AtomicLong(0)`; `val countingOut = FtpProgressOutputStream(outputStream, counter)`.
  - `coroutineScope { val emitter = launch { while (isActive) { progressCallback?.onProgress(counter.get(), total, 0L); if (total in 1..counter.get()) break; delay(PROGRESS_POLL_MS) } }; val success = tempClient.retrieveFile(remotePath, countingOut); emitter.cancelAndJoin(); val transferred = counter.get(); progressCallback?.onProgress(transferred, if (total > 0) total else transferred, 0L); if (success) Result.success(Unit) else Result.failure(IOException("FTP download failed: ${tempClient.replyString}")) }`.
  - Add `private const val PROGRESS_POLL_MS = 100L`.
- Remove the `@Suppress("UNUSED_PARAMETER")` from both `fileSize` and `progressCallback` of `FtpStandaloneOperations.downloadFile` (both are now used).
- Add imports: `org.apache.commons.net.ftp.FTPFile` (if needed by `mlistFile`/`listFiles` typing), `java.util.concurrent.atomic.AtomicLong`, `kotlinx.coroutines.coroutineScope`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.isActive`.

Verification:
- `rg "@Suppress\(\"UNUSED_PARAMETER\"\)" app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpStandaloneOperations.kt` -> no match inside the `downloadFile` signature.
- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpProgressOutputStream.kt` exists.
- `.\a.ps1 fk` exit 0.

### Phase 03 - Determinate FTP progress in Send-to dialog

- In `ShareMaterializationManager`, widen the `determinate` gate to FTP: `val determinate = content.sourcePath?.let { it.startsWith("smb:") || it.startsWith("ftp:") } == true` (SFTP keeps the indeterminate spinner - its callback never fires).
- Update the adjacent KDoc comment so it states SMB and FTP report byte-level progress; SFTP stays a spinner.

Verification:
- `rg 'startsWith\("ftp:"\)' app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ShareMaterializationManager.kt` -> exactly 1 match.
- `.\a.ps1 fc` exit 0 (final phase - packaging proof).

### Phase 04 - SFTP byte-level progress & determinate Send-to

- In `SftpClient.downloadFile` (lines 445-515), resolve `fileSize` if it is `<= 0L` by calling `try { channel.stat(remotePath).size } catch (e: Exception) { 0L }`.
- Pass this resolved size to `copyToWithProgress` when `progressCallback != null && resolvedSize > 0`.
- In `ShareMaterializationManager.kt` (lines 50-52), update the `determinate` flag gate to include `sftp:`:
  `val determinate = content.sourcePath?.let { it.startsWith("smb:") || it.startsWith("ftp:") || it.startsWith("sftp:") } == true`
- Update the KDoc comment in `ShareMaterializationManager` to state that SMB, FTP, and SFTP all support determinate progress.

Verification:
- `rg 'startsWith\("sftp:"\)' app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ShareMaterializationManager.kt` -> matches `determinate` line.
- `.\a.ps1 fk` exit 0.

### Phase 05 - FTP upload progress support

- Create `FtpProgressInputStream` (`app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpProgressInputStream.kt`):
  - A `FilterInputStream` wrapping an `InputStream` and incrementing an `AtomicLong` counter on `read()`, `read(b)`, and `read(b, off, len)`.
- In `FtpConnectedOperations.uploadFile` (`FtpConnectedOperations.kt`):
  - Remove `@Suppress("UNUSED_PARAMETER")` from the progress callback.
  - Implement progress polling: if `progressCallback` is provided, wrap `inputStream` in `FtpProgressInputStream`, launch a coroutine to poll the counter and emit progress updates every `PROGRESS_POLL_MS` (100ms) when `fileSize > 0`.
  - On complete, emit the final 100% progress.
- In `FtpStandaloneOperations.uploadFile` (`FtpStandaloneOperations.kt`):
  - Remove `@Suppress("UNUSED_PARAMETER")` from the progress callback.
  - Wrap `inputStream` in `FtpProgressInputStream` and run the same progress polling coroutine block during the file upload.
- In `FtpClient.uploadFile` & `FtpClient.uploadFileWithNewConnection`, forward the `progressCallback` parameter and remove any unused parameter suppression.

Verification:
- `rg "FtpProgressInputStream" app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp` -> matches.
- `.\a.ps1 fk` exit 0.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0493 (parent - honest Send-to progress; S0496 parked during its adversarial review).

## 4. Заметки

- Остаточный риск: если сервер не отдаёт размер (нет MLST, `listFiles` по файловому пути пуст) и `fileSize <= 0`, `total = -1` -> процент не вычисляется, FTP-бар стоит на 0. На современных серверах размер резолвится; деградация косметическая, не хуже текущего спиннера.
- Pre-existing инфраструктурные проблемы FTP, не введены S0493. Запаркованы при ревью S0493.
