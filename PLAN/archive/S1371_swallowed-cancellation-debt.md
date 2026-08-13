# Спецификация (compact bugfix): S1371 - остаток класса «широкий catch проглатывает отмену»

**Ticket:** S1371
**Status:** Archived
**Priority:** 40
**Date:** 2026-08-03
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-03

**Текст:**

Отпочковано от S1363. Там заведён механический гейт `swallowed-cancellation` и вылечен подтверждённый логом участок (`data/cloud` + `FileOperationUseCase`, 72 места). Остаток класса - 732 места в `app_v2/src/main` - остался в baseline как управляемый долг.

Крупнейшие узлы остатка на момент отпочкования:

- `domain/usecase` - 106
- `ui/player/helpers` - 99
- `data/transfer/strategy` - 81
- `data/network` - 63
- `data/remote/ftp` - 40
- `data/transfer` - 40

---

## 1. Проблема / симптом

Широкий `catch (e: Exception)` в корутинном коде ловит `CancellationException`: отмена логируется как ошибка уровня E, конвертируется в доменный результат сбоя и не пробрасывается наверх, из-за чего родительский job считает дочерний завершившимся штатно.

Симптом наблюдаем при разборе удалённых логов: обычный уход с экрана даёт пачку E-записей, которые приходится отсеивать вручную.

---

## 2. Корневая причина

Та же, что в S1363 - см. §2 там. Этот тикет не про причину, а про объём: 732 места, которые не входили в подтверждённый логом участок.

---

## 3. Исправление

- Лечить пакетами по подсистемам, а не одним махом: каждый пакет - отдельная сборка и отдельный прогон гейта, чтобы храповик фиксировал прогресс.
- Идиома - явная ветка `catch (e: CancellationException) { throw e }` перед широкой либо `rethrowIfCancellation()` первой строкой блока; гейт принимает обе.
- Пакетная замена вслепую запрещена: там, где в том же контексте есть `withTimeout`, ветка `catch (e: TimeoutCancellationException)` должна стоять перед пробросом, иначе таймаут перестанет деградировать в доменную ошибку и улетит наверх.
- Отдельно проверять места под `NonCancellable` и в путях очистки: там проброс меняет порядок освобождения ресурсов.

### 3.2 Прогресс по пакетам

Замер при отпочковании (732) успел устареть - живой гейт на 2026-08-04 показывал `baseline 731 | actual 731`, а `-List` перечисляет 734 места. Числа в §0 оставлены как есть, они верны на момент захвата; актуальным считать гейт, а не спеку.

**Пакет 1 - `core/util/AudioMetadataLoader.kt`, 2026-08-04.** Вылечено 9 мест из 10, гейт опустился `731 -> 722`, `.\a.ps1 fk` exit 0, `post-change: PASS`.

Выбран этот файл потому, что он и есть путь из §1: уход с экрана отменяет дозагрузку метаданных. Обнаружилось, что последствие тяжелее описанного - широкий `catch` в точке входа вызывает `recordFailure()`, который крутит счётчик kill-switch, поэтому повторные уходы с экрана приближали отключение загрузки метаданных на всю сессию, а не просто шумели в логе.

Одно место оставлено намеренно: `catch (_: Exception)` вокруг удаления временного файла внутри `finally`. Проброс оттуда вылетел бы из `finally` и затёр исходное исключение, а сама операция удаления не приостанавливаемая и отмену бросить не может. Это не долг, а корректный код, который гейт считает по форме.

Свежая карта остатка на 2026-08-04 (для выбора следующего пакета): `domain/usecase` 104, `ui/player/helpers` 99, `data/transfer/strategy` 81, `data/network` 63, `data/transfer` 40, `data/remote/ftp` 40, `ui/browse/managers` 25, `worker` 23, `ui/player` 23, `data/repository` 21. Наибольшая концентрация в файлах: `FtpConnectedOperations.kt` 22, `SmbOperationsUseCase.kt` 17, `SmbOperationStrategy.kt` 17, `FtpOperationStrategy.kt` 17, `SftpOperationStrategy.kt` 16.

В `AudioMetadataLoader.kt` ни `withTimeout`, ни `NonCancellable` нет, поэтому обе оговорки §3 к этому пакету не применялись - для следующих пакетов проверять заново.

**Пакет 2 - подсистема `data/remote/ftp`, 2026-08-04.** Вылечено 35 мест из 40, гейт опустился `722 -> 687`, `.\a.ps1 fk` exit 0, тесты гейта 13/13, `post-change: PASS`.

Затронуты четыре файла: `FtpConnectedOperations.kt` (18 из 22), `FtpStandaloneOperations.kt` (8 из 8), `FtpMediaScanner.kt` (6 из 6), `FtpClient.kt` (3 из 4). Идиома - `e.rethrowIfCancellation()` первой строкой широкого блока, как в вылеченном на S1363 участке `data/cloud`.

Пять мест оставлены намеренно, все пять - `catch` лексически внутри `finally`: `FtpClient.kt` (восстановление `soTimeout` в `disconnectInternal`) и четыре одинаковых блока вокруг `enterLocalPassiveMode()` в `FtpConnectedOperations.kt` (`listFiles`, `readFileBytes`, `readFileBytesRange`, `downloadFile`). Причина та же, что у пропуска в пакете 1: проброс из `finally` затёр бы исходное исключение, ради которого разматывается стек. Вдобавок тела этих блоков - синхронные вызовы Apache commons-net, отмену бросить не могут, так что проброс был бы мёртвым кодом. Это не долг, а корректный код, который гейт считает по форме.

Ни `withTimeout`, ни `NonCancellable` в подсистеме нет - обе оговорки §3 и к этому пакету не применялись.

Попутно починен предсуществующий `ImportOrdering` в `FtpMediaScanner.kt`: `dagger.hilt.android.qualifiers.ApplicationContext` стоял между блоками `com.sza.fastmediasorter.data.*` и `com.sza.fastmediasorter.domain.*`. Пока файл не входил в набор правки, долг лежал в тени; scoped-гейт поднял его, как только файл попал в `-Files`, и без починки закрытие не проходило.

Четыре предсуществующих превышения длины строки в том же файле (250, 408, 428, 452) остались - они в baseline детекта, гейт-вердикт их пропускает, и к отмене они отношения не имеют.

**Пакет 3 - подсистема `data/transfer/strategy`, 2026-08-05.** Вылечено 80 мест из 81, гейт опустился `687 -> 607`, `.\a.ps1 fk` exit 0, `post-change: PASS`.

Затронуты шесть файлов: `SmbOperationStrategy.kt` (17 из 17), `FtpOperationStrategy.kt` (17 из 17), `SftpOperationStrategy.kt` (16 из 16), `CloudOperationStrategy.kt` (15 из 15), `LocalOperationStrategy.kt` (14 из 15), `StrategyUtils.kt` (1 из 1). Идиома - `e.rethrowIfCancellation()` первой строкой широкого блока.

Самая ценная правка пакета - одна строка в `StrategyUtils.safeIo`: это общая обёртка `withContext(IO) + try/catch`, через которую идут короткие операции всех стратегий, поэтому один проброс закрывает отмену сразу у всех её вызывающих.

Одно место оставлено намеренно - вложенный `catch (e2: Exception)` в `LocalOperationStrategy.deleteViaMediaStore`. Он лежит внутри внешнего широкого блока, который теперь пробрасывает отмену первой строкой, а тело вложенного `try` - синхронный `File.delete()`, отмену бросить не может. Проброс оттуда был бы недостижимым кодом.

Оговорки §3 к пакету не применялись: ни `withTimeout`, ни `NonCancellable` в подсистеме нет, а четыре `finally`-блока (`CloudOperationStrategy` - удаление временного файла, `LocalOperationStrategy` - закрытие курсора) не содержат `catch` лексически внутри себя, так что проброс из соседнего `catch` их не отменяет.

Побочный эффект, которого не было в прошлых пакетах: пятнадцать добавленных строк перевели `CloudOperationStrategy` через порог detekt `LargeClass` (600), и scoped-гейт отказал в закрытии. Вместо baseline из класса вынесены чистые хелперы пути - `CloudUriInfo`, `parseCloudUri`, `splitParentAndName`, `guessMimeType` - в новый `CloudUriInfo.kt`. Они не зависят ни от одного внедрённого клиента, так что вынос механический. Для следующих пакетов держать в уме: файл у порога `LargeClass` может упасть от самой правки, и тогда декомпозиция входит в пакет.

Попутно приведён в порядок предсуществующий `ImportOrdering` во всех пяти стратегиях: `dagger.hilt.android.qualifiers.ApplicationContext` и `javax.inject.Inject` стояли вперемешку между блоками `com.sza.*`. Долг лежал в тени, пока файлы не входили в набор правки - тот же механизм, что и в пакете 2.

Свежая карта остатка на 2026-08-05: `domain/usecase` 104, `ui/player/helpers` 99, `data/network` 63, `data/transfer` 40, `ui/browse/managers` 25, `ui/player` 23, `worker` 23, `data/repository` 21, `ui/addresource` 16, `ui/settings/helpers` 16. Наибольшая концентрация в файлах: `SmbOperationsUseCase.kt` 17, `SmbClient.kt` 15, `SmbFileOperations.kt` 12, `UnifiedFileOperationHandler.kt` 10, `LocalTransferProvider.kt` 9, `AppStartupInitializer.kt` 9.

**Пакет 4 - подсистема SMB, 2026-08-05.** Вылечено 63 места из 83, гейт опустился `607 -> 544`, `.\a.ps1 fk` exit 0, тесты гейта 13/13, `post-change: PASS WITH ADVISORIES (1)`.

Подсистема выбрана как сестра пакета 2: тот же протокольный класс задач, только SMB вместо FTP. Границей пакета взяты все файлы с префиксом `Smb`, а не один каталог - клиент, операции, сканеры, координаторы, провайдер переноса, стратегии и верификатор лежат в пяти разных пакетах, но образуют один стек вызовов.

Затронуто 14 файлов: `SmbOperationsUseCase.kt` (17 из 17), `SmbClient.kt` (9 из 15), `SmbFileOperations.kt` (8 из 12), `SmbTransferProvider.kt` (9 из 9), `SmbMediaScanner.kt` (5 из 5), `SmbFileOperationHandler.kt` (4 из 4), `SmbDirectoryScanner.kt` (3 из 3), `SmbFileMutationCoordinator.kt` (2 из 5), `SmbShareDiscoveryHelper.kt` (1 из 5), `SmbMediaScanCoordinator.kt` (1 из 2), `SmbConnectionManager.kt` (1 из 2), `SmbQuickVerifier.kt` (1 из 1), `SmbToLocalStrategy.kt` (1 из 1), `SmbToSmbStrategy.kt` (1 из 1). `SmbConnectionPool.kt` не тронут (0 из 1).

Новое наблюдение, которого не было в прошлых пакетах: пять мест уже несли рукописное `if (e is CancellationException) throw e` первой строкой - лечение по смыслу, но гейт считает по форме и их не признавал. Замена на `e.rethrowIfCancellation()` семантически тождественна (тело хелпера - ровно эта проверка) и опускает храповик даром. Для следующих пакетов: искать такую форму отдельно, это самые дешёвые места.

Двадцать мест оставлены намеренно, четыре разные причины:

- Вложенный `catch` вокруг синхронного вызова SMBJ под внешним блоком, который теперь пробрасывает отмену первой строкой - 12 мест (`SmbClient` 5, `SmbFileOperations` 4, `SmbFileMutationCoordinator` 3). Проброс оттуда был бы недостижимым кодом, как в пакете 3.
- `catch` лексически внутри `finally` - 1 место (удаление тестового файла в `SmbClient.checkWritePermission`). Причина та же, что в пакетах 1 и 2.
- В теле функции нет ни одной точки приостановки - 4 места из 5 в `SmbShareDiscoveryHelper`. `getClient` не `suspend`, а `connect` / `authenticate` / `connectShare` - блокирующие вызовы SMBJ, отмену бросить не могут. Вылечена только точка входа `listShares`, куда отмена может прийти при будущей правке.
- Блок уже пробрасывает всё и обязан сперва доделать свою работу - 3 места. Коалесер `SmbMediaScanCoordinator.scanMediaFiles` до `throw` обязан выполнить `completeExceptionally`, иначе проброс первой строкой навсегда подвесил бы ждущих на `await()`. `SmbConnectionManager.createFreshConnection` до `throw` чистит кеш клиента через `purgeClientForHost`. `SmbConnectionPool.closeConnectionAsync` - путь принудительной очистки, где проброс оборвал бы закрытие сессии и соединения.

Оговорки §3 проверены и не применились: `NonCancellable` в подсистеме нет; `withTimeout` в корутинном смысле тоже нет - `SmbMediaScanner` использует `withTimeoutOrNull`, который гасит таймаут внутри себя, а `SmbConnectionManager` использует одноимённый метод SMBJ `SmbConfig.withTimeout`, к корутинам отношения не имеющий.

Попутно приведён в порядок предсуществующий `ImportOrdering` в трёх файлах (`SmbTransferProvider`, `SmbMediaScanner`, `SmbFileMutationCoordinator`) - тот же механизм теневого долга, что в пакетах 2 и 3. Четыре предсуществующих превышения длины строки (`SmbMediaScanner` 443 и 655, `SmbFileOperationHandler` 332, `SmbConnectionManager` 420) остались: они в baseline детекта и всплыли только в лексическом preflight из-за сдвига номеров строк, сам detekt-гейт их пропускает.

Свежая карта остатка на 2026-08-05 после пакета 4: `ui/player/helpers` 99, `domain/usecase` 87, `data/network` 32, `data/transfer` 31, `ui/browse/managers` 25, `ui/player` 23, `worker` 23, `data/repository` 21, `ui/settings/helpers` 16, `ui/addresource` 16. Наибольшая концентрация в файлах: `UnifiedFileOperationHandler.kt` 10, `LocalTransferProvider.kt` 9, `AppStartupInitializer.kt` 9, `BaseFileOperationHandler.kt` 8, `RestoreDeletedUseCase.kt` 8, `ThumbnailCacheRepositoryImpl.kt` 8.

**Пакет 5 - остаток подсистемы `data/transfer`, 2026-08-05.** Вылечено 30 мест из 45, гейт опустился `544 -> 514`, `.\a.ps1 fk` exit 0, тесты гейта 13/13, `post-change: PASS WITH ADVISORIES (1)`.

Границей пакета взято всё поддерево `data/transfer` за вычетом каталога `strategy/`, который был пакетом 3. Карта остатка после пакета 4 показывала здесь 31 место, потому что считала только прямых детей каталога; подкаталоги `strategies/`, `access/` и `local/` добавили ещё 14.

Затронуто 8 файлов: `UnifiedFileOperationHandler.kt` (10 из 10), `LocalTransferProvider.kt` (9 из 9), `BaseFileOperationHandler.kt` (5 из 8), `AtomicFileOperationStrategy.kt` (1 из 4), `access/LocalFileAccess.kt` (2 из 2), `access/FtpFileAccess.kt` (1 из 1), `strategies/LocalToSftpStrategy.kt` (1 из 3), `strategies/SftpToLocalStrategy.kt` (1 из 2). Не тронуты `local/MediaStoreLocalDestinationWriter.kt` (0 из 2), `strategies/LocalToFtpStrategy.kt` (0 из 2), `strategies/FtpToLocalStrategy.kt` (0 из 1), `strategies/LocalToSmbStrategy.kt` (0 из 1).

Пакет впервые потребовал явного критерия отбора, потому что подсистема смешивает корутинный и синхронный код гуще предыдущих. Критерий: лечить, если либо в теле `try` есть точка приостановки, либо этот `catch` - входной для публичной `suspend`-функции, то есть внутри `try` лежит всё тело. Второе условие - то же обоснование, по которому в пакете 4 вылечена точка входа `listShares`: приостановки сегодня нет, но она появится от любой будущей правки, а блок уже будет защищён. Оставлять - середину функции с чисто синхронным телом, где проброс был бы недостижимым кодом.

Пятнадцать оставленных мест раскладываются на три причины:

- Синхронное тело в середине функции - 10 мест. Это открытие локального потока перед сетевой передачей в четырёх стратегиях `strategies/*` (`contentResolver.openInputStream`, `File.inputStream()`), вложенный `catch` вокруг `contentResolver.query` за размером файла, и три запроса к `MediaStore` в `BaseFileOperationHandler`. Ни одного вызова `suspend` внутри, отмена туда прийти не может.
- `NonCancellable` - 3 места, и это первое срабатывание одноимённой оговорки §3 за все пять пакетов. Оба `commit()` в `MediaStoreLocalDestinationWriter` идут под `Dispatchers.IO + NonCancellable`, `AtomicFileOperationStrategy.cleanupTempFile` - под `withContext(NonCancellable)`. Отмена туда не доставляется по построению, а проброс сломал бы порядок освобождения ресурсов, ради которого `NonCancellable` там и стоит.
- Вложенный `catch` под внешним блоком, который теперь пробрасывает отмену первой строкой - 2 места в `AtomicFileOperationStrategy` (`verifyTempPostCondition`, `deletePath`), обе ветки вокруг синхронных операций с `File`. Причина та же, что в пакетах 3 и 4.

Вторая оговорка §3 не применилась: `withTimeout` в подсистеме нет ни в одном виде.

Побочный эффект, которого не было в прошлых пакетах: в `UnifiedFileOperationHandler.executeCopy` очистка `progressTracker.clearOperation(operationId)` стояла дважды - в конце `try` и первой строкой `catch`. Проброс первой строкой отменил бы вторую копию, и отменённая операция навсегда осталась бы висеть в трекере прогресса. Обе копии заменены на один `finally`, так что теперь запись снимается и при отмене - раньше не снималась ни при отмене, ни при пробросе. Для следующих пакетов держать в уме: если `catch` начинается с уборки, проброс первой строкой её отменяет, и уборку надо переносить в `finally`, а не оставлять перед пробросом.

Попутно приведён в порядок предсуществующий `ImportOrdering` в трёх файлах (`BaseFileOperationHandler`, `access/FtpFileAccess`, обе SFTP-стратегии в `strategies/`) - тот же механизм теневого долга, что в пакетах 2, 3 и 4.

Единственное замечание закрытия - advisory лексического preflight на пять предсуществующих превышений длины строки (`UnifiedFileOperationHandler` 525 и 563, `LocalTransferProvider` 217 и 218, `AtomicFileOperationStrategy` 213). Ни одна из них не добавлена этим пакетом, все всплыли из-за сдвига номеров строк; сам detekt-гейт вынес `PASS [scoped]` и среди изменённых файлов находок не нашёл. То же самое было в пакете 4.

Свежая карта остатка на 2026-08-05 после пакета 5: `ui/player/helpers` 99, `domain/usecase` 87, `data/network` 32, `ui/browse/managers` 25, `worker` 23, `ui/player` 23, `data/repository` 21, `ui/settings/helpers` 16, `ui/addresource` 16, `data/remote/sftp` 15. Наибольшая концентрация в файлах: `AppStartupInitializer.kt` 9, `RestoreDeletedUseCase.kt` 8, `ThumbnailCacheRepositoryImpl.kt` 8, `GeneralSettingsCacheHelper.kt` 7, `PdfViewerManager.kt` 7, `PlayerViewModel.kt` 7, `SearchLyricsUseCase.kt` 7, `MediaStoreRepositoryImpl.kt` 7.

**Пакет 6 - подсистема SFTP, 2026-08-05.** Вылечено 14 мест из 22, гейт опустился `514 -> 500`, `.\a.ps1 fk` exit 0, тесты гейта 13/13.

Граница взята как в пакете 4: все файлы с префиксом `Sftp`, а не один каталог - клиент, пул, тестер соединения, сканер, обработчик операций, lifecycle-гейт и верификатор лежат в пяти пакетах, но образуют один стек вызовов. Пакет закрывает трио протоколов: FTP был пакетом 2, SMB - пакетом 4.

Затронуто 7 файлов: `SftpFileOperationHandler.kt` (4 из 4), `SftpClient.kt` (3 из 3), `SftpConnectionPool.kt` (2 из 7), `SftpConnectionTester.kt` (2 из 4), `SftpConnectionGate.kt` (1 из 1), `SftpMediaScanner.kt` (1 из 1), `SftpQuickVerifier.kt` (1 из 1).

Самая ценная правка пакета - `SftpConnectionPool.withConnection`. Широкий `catch` вокруг `block(pc.channel)` стоит перед тремя ветками переподключения, и отменённая операция выглядела там ровно как мёртвый транспорт: `pc.channel.isConnected` и `pooled.session.isConnected` при разрыве корутины уже false, так что блок молча перезапускался на свежесозданной сессии. Проброс первой строкой закрывает это; точечный `ensureActive()` из S0205 в ветке мёртвого транспорта оставлен - он ловит другой случай, когда отмена приходит не как `CancellationException`, а как «inputstream is closed» от teardown-а throttle.

Вторая по ценности - `SftpClient.downloadFile`: тело `try` вызывает `copyToWithProgress`, а тот дёргает `ensureActive()` на каждый буфер. Это единственное место пакета, где отмена гарантированно доставляется в середину блока: уход с экрана во время скачивания давал E-запись и нерезаиваемый `Result.failure` вместо кооперативной отмены.

Восемь мест оставлены намеренно, четыре причины, все уже встречались в прошлых пакетах:

- Синхронный разрыв сессии внутри `synchronized` - 4 места (`SftpConnectionPool`: закрытие устаревшей сессии в `getOrCreateSession`, `invalidateSession`, `cleanupIdleConnections`, `disconnectAll`). Точек приостановки в телах нет, проброс был бы недостижимым кодом.
- `catch` лексически внутри `finally` - 2 места (оба teardown-блока в `SftpConnectionTester`). Причина та же, что в пакетах 1, 2 и 4.
- Блок уже пробрасывает всё и обязан сперва доделать свою работу - 1 место (`SftpConnectionPool.openInputStream`, `channel.disconnect(); throw e`). Тот же случай, что три места коалесера и пула в пакете 4.
- Синхронное тело в середине функции - 1 место (`SftpToLocalStrategy`, открытие локального потока). Уже разобрано и оставлено в пакете 5 по тому же критерию.

Попутно приведён в порядок предсуществующий `ImportOrdering` в трёх файлах (`SftpFileOperationHandler`, `SftpMediaScanner`, `SftpConnectionGate`) - тот же механизм теневого долга, что в пакетах 2-5.

Свежая карта остатка на 2026-08-05 после пакета 6: `ui/player/helpers` 99, `domain/usecase` 87, `data/network` 28, `ui/browse/managers` 25, `ui/player` 23, `worker` 23, `data/repository` 21, `ui/addresource` 16, `ui/settings/helpers` 16, `ui/main/helpers` 14. Наибольшая концентрация в файлах: `AppStartupInitializer.kt` 9, `RestoreDeletedUseCase.kt` 8, `ThumbnailCacheRepositoryImpl.kt` 8, `MediaStoreRepositoryImpl.kt` 7, `PlayerViewModel.kt` 7, `SearchLyricsUseCase.kt` 7, `LocalMediaScanner.kt` 7, `GeneralSettingsCacheHelper.kt` 7.

Протокольная сетевая часть на этом исчерпана - остаток целиком в прикладных слоях (`ui/*`, `domain/usecase`, `worker`, `data/repository`), где широкий `catch` чаще прикрывает не сеть, а работу с базой и MediaStore. Для пакета 7 это значит другой критерий отбора: точка приостановки в теле почти всегда есть, поэтому решать придётся по тому, что делает сам `catch`, а не по наличию `suspend`.

**Пакет 7 - длинные пользовательские потоки импорта/экспорта/резервных копий/восстановления в `domain/usecase`, 2026-08-05.** Вылечено 24 места из 34, ещё 3 удалены вместе с мёртвым кодом, гейт опустился `500 -> 473`, `.\a.ps1 fk` exit 0, тесты гейта 13/13, `post-change: PASS`.

Границу впервые пришлось резать внутри слоя, а не по нему. Весь `domain/usecase` - это 99 мест в 43 файлах, вдвое больше любого прошлого пакета, а храповик фиксирует прогресс только на закрытом пакете, так что взята связная по функции часть: потоки, которые пользователь запускает явно и бросает на полпути. Это ровно симптом §1, а не произвольная выборка по концентрации.

Затронуто 12 файлов: `RestoreDeletedUseCase.kt` (4 из 8 вылечено + 3 удалено), `ApplyBackupPayloadUseCase.kt` (4 из 4), `BackupToGoogleDriveUseCase.kt` (2 из 2), `ExtractArchiveUseCase.kt` (2 из 2), `ImportSettingsUseCase.kt` (2 из 4), `RestoreFromGoogleDriveUseCase.kt` (2 из 2), `companion/ExportCompanionConfigUseCase.kt` (2 из 2), `streams/ImportStreamCatalogUseCase.kt` (2 из 4), `CleanupTrashFoldersUseCase.kt` (1 из 1), `ExportResourcesToFileUseCase.kt` (1 из 1), `ImportFavoritesUseCase.kt` (1 из 1), `companion/ImportCompanionConfigUseCase.kt` (1 из 1). `streams/ImportStreamPlaylistUseCase.kt` разобран и не тронут (0 из 2).

Самая ценная правка пакета - четыре `catch` внутри `db.withTransaction` в `ApplyBackupPayloadUseCase`. Файл сам декларирует инвариант (S0732: «Room-секции коммитятся all-or-nothing, убийство на середине больше не оставляет частичную БД»), но против отмены инвариант не работал: `CancellationException` от каждой строки ловился как «пропустить одну запись», цикл шёл дальше по остальным, и транзакция коммитила частично восстановленный бэкап вместо того, чтобы откатиться. Проброс возвращает файлу его же заявленное поведение. Это первый случай в серии, когда широкий `catch` ломал не логи, а сохранность данных.

Три места вылечены не были, а удалены: `RestoreDeletedUseCase` нёс приватные `readMetadata`, `moveFile` и `deleteDirectory`, ни на один из которых нет ни одного вызова - вся работа делается в `invoke` встроенно. Лечить мёртвый код бессмысленно, храповик опускается от удаления так же. Для следующих пакетов держать в уме: перед правкой места стоит проверить, вызывается ли вообще его функция.

Второе наблюдение - `ExtractArchiveUseCase`, первый в серии `flow {}`, а не `suspend fun`. Отмена там ловилась последним широким `catch` и превращалась в `emit(ExtractProgress.Failure("extract_error"))`: пользователь уходил с экрана сам, а получал результат «распаковка не удалась». Там же вскрылась защита через эвристику: в `withZipInputStream` отмена пробрасывалась не по типу, а потому что `isCharsetRelatedError(e)` возвращал false по тексту сообщения. Работало, но держалось на форме чужого текста; проброс первой строкой убирает эту зависимость. Родня той находки пакета 4 про рукописное `if (e is CancellationException) throw e` - защита есть, но не та, которую видит гейт.

Семь мест оставлены, причина у всех одна и уже разобранная - синхронное тело, куда отмену доставить нечем: разбор JSON и `contentResolver.openInputStream` в середине функции (`RestoreDeletedUseCase`, `ImportSettingsUseCase` - 3 места), `MediaType.valueOf` внутри `mapNotNull` (`ImportSettingsUseCase`), блокирующие загрузка и разбор в обоих потоковых импортах (`ImportStreamCatalogUseCase`, `ImportStreamPlaylistUseCase` - 4 места, `download`/`parse` не `suspend`). Оговорки §3 проверены и не применились: ни `withTimeout`, ни `NonCancellable` в пакете нет.

Попутно приведён в порядок предсуществующий `ImportOrdering` в двух файлах (`RestoreDeletedUseCase` - `trash` перед `strategy`, `ExtractArchiveUseCase` - `MalformedInputException` перед `Charset`) - тот же механизм теневого долга, что в пакетах 2-6. В отличие от пакетов 4 и 5, замечаний закрытия не было вовсе: `detekt-preflight` прошёл по всем 12 файлам, вердикт чистый `post-change: PASS`.

Свежая карта остатка на 2026-08-05 после пакета 7: `ui/player` 127, `domain/usecase` 72, `data/network` 37, `ui/browse` 30, `data/repository` 24, `worker` 23, `ui/main` 19, `ui/settings` 16, `data/transfer` 16, `ui/addresource` 16. Наибольшая концентрация в файлах: `AppStartupInitializer.kt` 9, `ThumbnailCacheRepositoryImpl.kt` 8, `SearchLyricsUseCase.kt` 7, `GeneralSettingsCacheHelper.kt` 7, `PlayerViewModel.kt` 7, `MediaStoreRepositoryImpl.kt` 7, `PdfViewerManager.kt` 7, `LocalMediaScanner.kt` 7.

`ui/player` (127) - самый крупный оставшийся узел и первый, где проброс может изменить порядок освобождения ресурсов плеера, а не только текст лога. Для пакета 8 это значит вернуть в работу обе оговорки §3, которые в пакете 7 не понадобились.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1363 (гейт и подтверждённый участок), S1212 (первый точечный случай того же дефекта).
- **Решение владельца, 2026-08-05:** оставить храповик работать оппортунистически. Остаток класса не лечится до нуля выделенными пакетами; baseline опускается попутно, когда файл и так правится по другому тикету.

Что это меняет для тикета:

- Пакет 7 - последний выделенный пакет. Дальнейших пакетов не планируется, поэтому работа самого тикета исчерпана: гейт заведён (S1363), семь пакетов сданы, `731 -> 473`.
- Оставшиеся 473 места - принятый управляемый долг, а не незакрытая задача. Их удерживает храповик: гейт валит сборку при росте и молча фиксирует любое снижение.
- Единственное требование к будущим тикетам: правя файл по своей причине, не увеличивать в нём число широких `catch` без проброса. Отдельного тикета на остаток заводить не нужно - его роль выполняет сам гейт.
- Идиома для попутного лечения та же, что в §3: `e.rethrowIfCancellation()` первой строкой либо явная ветка `catch (e: CancellationException) { throw e }` перед широкой. Оговорки §3 про `withTimeout` и `NonCancellable` продолжают действовать.

---

## 4. Проверка

Критерий готовности после решения владельца от 2026-08-05 - не «остаток равен нулю», а «храповик стоит и работает»:

- `scripts/quality/assert-swallowed-cancellation.ps1` - exit 0, значение baseline равно фактическому.
- `scripts/quality/assert-swallowed-cancellation.tests/Run-Tests.ps1` - exit 0.
- Гейт входит в `post-change.ps1`, то есть проверяется на каждом закрытии, а не по памяти.
- Компиляция затронутых flavor, exit 0.

Замер на момент закрытия: `baseline 473 | actual 473 | delta 0`, тесты гейта 13/13.
