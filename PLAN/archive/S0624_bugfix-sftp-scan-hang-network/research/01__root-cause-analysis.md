# Research 01 - Root-cause analysis: SFTP scan hang on network handover

**Ticket:** S0624
**Date:** 2026-06-22
**Method:** 5 параллельных читателей кода SFTP-пути + синтез; сверка с рантайм-логом `logs/fastmediasorter_20260622_150558.log`.
**Status:** Resolved (механизм подтверждён по коду; on-device проверки - Research items 2-5 в спеке).

> Имена классов/файлов/строк здесь приведены намеренно - это research-артефакт для `/spec-tech`, а не стратегическая спека.

---

## Симптом

Ресурс id=25 «Home ApeFlac», SFTP, тип AUDIO, путь `sftp://46.54.0.135:22022/J:/MEDIA/apeflac`. Открыт дважды (PlayerActivity 15:53, BrowseActivity 15:54). Оба раза: connection test OK → скан стартует → `SFTP new session for 46.54.0.135` → ~90 с полной тишины от сканера (только `SftpConnectionPool.periodicSweep: tick` с `active=1`) → пользователь выходит вручную. Ни файлов, ни ошибки, ни завершения. UI - бесконечный спиннер без прогресса.

Ключевая улика из лога: ровно в момент старта скана (15:53:09.531) сеть переключилась `wlan0 → rmnet0`. SMB при этом инвалидировал соединения (`SmbConnectionManager: Network reconnected - invalidating all SMB connections`), SFTP - нет.

---

## Корневая причина (3 дефекта, ранжировано)

### #1 (главный) - нет инвалидации SFTP-сессии при смене сети, в отличие от SMB

- SMB сбрасывается: `NetworkStateMonitor.onNetworkChanged()` → `SmbConnectionManager.handleNetworkReconnect()` → `closeAllConnections()` (`SmbConnectionManager.kt:59`, `:918-925`).
- SFTP-стека среди подписчиков `NetworkStateMonitor` нет: grep по `data/remote/sftp/` → подписки `registerCallback` отсутствуют. `SftpClient`/`SftpConnectionPool`/`SftpMediaScanner` не подписаны.
- Существующий сброс пула `SftpConnectionGate.closeFor()` → `client.disconnectAllPool()` (`SftpConnectionGate.kt:55-63`) привязан только к `ON_STOP` (уход в фон), не к смене транспорта.
- Итог: сокет, открытый на мёртвом wlan0 (`SftpConnectionPool.kt:231`), остался `isConnected == true` и не инвалидирован.

### #2 - нет прикладного таймаута на листинге

- `channel.ls(remotePath)` (`SftpClient.kt:201` single-level, `:235` recursive) вызывается «голым».
- Во всей цепочке нет `withTimeout`/`withTimeoutOrNull`:
  - `GetMediaFilesUseCase` → `scanner.scanFolder(..)` (`:329-337`) - без таймаута;
  - `ScanDispatcher.withScanPermit` (`:44-55`) - только конкурентность;
  - `ConnectionThrottleManager.withThrottle` (`:427`) - вызывает `operation()` без таймаута; таймаут лишь реактивно классифицируется постфактум (`:455-457`);
  - `SftpConnectionPool.withConnection` (`:90`) - только `withContext(Dispatchers.IO)`.
- Единственный барьер - JSch `session.timeout = SOCKET_TIMEOUT = 30_000` (`SftpConnectionPool.kt:224`, константа `:630`), то есть SO_TIMEOUT сокета. Не сработал: тишина >90 с (> 30 с), ни одна catch-ветка `withConnection` (`:107-137`) не залогировала потерю channel/session. Значит чтение не парковалось в SO_TIMEOUT-защищённом `socket.read()` - согласуется с зависанием на фазе записи запроса / в transport-read-loop на half-open сокете (write-сторона SO_TIMEOUT не покрывается).

### #3 - нет SSH keep-alive (ServerAliveInterval)

- `setServerAliveInterval`/`ServerAlive*`/`sendKeepAlive` отсутствуют во всём модуле (grep → ноль).
- Half-open сокет никто не пингует, `session/channel.isConnected` остаются `true`.
- Реактивное `isDeadTransport()` (`SftpConnectionPool.kt:659-663`) требует уже выброшенного IOException и на запаркованном чтении не запускается.

### Усугубляющий фактор (не причина зависания)

Путь распарсен как `/J:/MEDIA/apeflac` - буква диска Windows за ведущим слешем (`Parsed SFTP path ... Remote: '/J:/MEDIA/apeflac'`). До листинга дело не дошло, на зависание не влияет, но это потенциальный отдельный баг валидности пути - см. Research item 3.

---

## Механизм бесконечного спиннера

1. Скан висит на блокирующем `channel.ls(remotePath)` (`SftpClient.kt:201/235`) внутри `SftpConnectionPool.withConnection` под `pc.mutex.withLock { block(pc.channel) }` (`:106`) - suspend-await блокирующего JSch-вызова без прикладного таймаута.
2. Сокет на wlan0 после хэндовера - half-open (нет RST/FIN). SO_TIMEOUT либо не покрывает фазу, либо не отрабатывает на мёртвом интерфейсе → исключения нет.
3. Раз исключения нет:
   - реконнект-логика `withConnection` (`:107-137`) полностью exception-driven и не запускается;
   - `GetMediaFilesUseCase` не имеет `try/catch`/`.catch{}` вокруг скана; flow эмитит только `List<MediaFile>`, без Result/UiState (`:144`). Ни throw, ни emit → в UI ничего;
   - `onProgress` (`:127/336`) дёргается только при отдаче прогресса сканером; листинг не вернулся → прогресса нет;
   - прогрессивная ранняя отдача (`:268-302`) - только для `SmbMediaScanner` (guard `:269`); для SFTP недостижима. Единственный `emit(sortedFiles)` (`:407`) недостижим.
4. `periodicSweep` тикает, но не спасает: `cleanupIdleConnections` (`:282-306`) выселяет сессию только при `activeBorrowCount == 0` (`:285`). Скан держит borrow (`incrementAndGet`, `:100`), декремент в `finally` (`:142`) не выполняется (block не вернулся) → `active=1` навсегда (ровно лог).

Результат: корутина запаркована в `scanFolder`, держит channel-mutex, спиннер не снимается до ручного back-out.

---

## План исправлений (вход для /spec-tech)

Минимум для закрытия инцидента: **#1 + #2**. #3 - defense-in-depth. #4/#5 - дёшево попутно.

- **FIX #1 (главный)** - паритет с SMB: `SftpConnectionPool` (или тонкая `@Singleton`-обёртка) подписывается на `NetworkStateMonitor.NetworkChangeCallback` (образец `SmbConnectionManager.kt:57-68`); в `onNetworkChanged`/`onNetworkLost` → существующий `SftpConnectionPool.disconnectAll()` (`:527`). Перед проводкой проверить безопасность под активной арендой (Research item 5).
- **FIX #2 (обязателен в паре)** - `withTimeout` вокруг `SftpMediaScanner.scanFolder` (`:37-185`) или в `GetMediaFilesUseCase` (`:329-337`), потолок ~45-60 с (> 30 с SO_TIMEOUT). Плюс `.catch{}`/Result-канал в flow `GetMediaFilesUseCase` (`:149-410`, вместо «голого» `List<MediaFile>` на `:144`), чтобы таймаут стал user-visible сообщением.
- **FIX #3 (усиление)** - `session.setServerAliveInterval(10-15с)` + `setServerAliveCountMax(2-3)` в `getOrCreateSession()` рядом с `session.timeout`/`session.connect` (`:224-225`).
- **FIX #4 (митигейшен)** - явный per-operation таймаут на channel перед `ls` (`SftpClient.kt:201/235`); НЕ покрывает write-сторону, потому лишь митигейшен.
- **FIX #5 (митигейшен)** - удалить мёртвую дублирующую константу `SOCKET_TIMEOUT = 30000` в `SftpClient.kt:88` (сессия для `ls` создаётся в пуле, использует `SftpConnectionPool.kt:630`).

---

## On-device проверки (см. §6 спеки)

- Воспроизведение без смены сети (стабильный Wi-Fi) - изолировать причину #1 от пути/размера каталога.
- Валидность `/J:/..` сторонним клиентом.
- Размер каталога и обход подпапок on/off.
- Реально ли SO_TIMEOUT когда-либо роняет `ls` на half-open за ~30 с.
- `disconnectAll()` под активной арендой не дедлочится на mutex.
