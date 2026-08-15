# Спецификация (compact bugfix): S0866 - SftpConnectionPool - гонки lock-доменов (pooledChannels CME, TOCTOU сессий)

**Ticket:** S0866
**Status:** Archived
**Priority:** 70
**Date:** 2026-07-02
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-02

**Текст:**

Source: mass code audit 2026-07-02 (CODE_AUDIT_PROTOCOL dimensions + player-host release-contract fan-out, workflow wf_34a4d99d-fbf). Findings below are verbatim agent output (static review, evidence = quoted live code).

Verification status: CONFIRMED P1, both findings (2026-07-02, dedicated skeptic). Both rated PLAUSIBLE ROOT CAUSE for S0624 (sftp scan hang). Confirmed mechanics: (1) pooledChannels (:55, plain mutableListOf) touched under FOUR non-excluding guard domains - sessionMutex (:173-184, :192-197), openChannelLock (:356-370), NO lock (evictPlaybackChannel :457-464, releaseExoPlayerConnection .any :443-444), poolMutex (guards the map, still races the list). Real concurrency pair: Media3 Loader thread (SftpDataSource.open/read/close call getConnectionForExoPlayer directly, no dispatch) vs Dispatchers.IO (SftpMediaScanner/SftpOperationStrategy via withConnection) - browsing/scanning a host while its video plays. Race outcome: CME or SILENT channel loss -> leaked ChannelSftp/socket + stuck activeBorrowCount -> per S0219 Pillar B (:254/:290) idle cleanup/invalidation can never evict -> later scans park forever = S0624 symptom. (2) TOCTOU :410: suspend path guards pooledSessions with poolMutex (:207-235), blocking path with synchronized(pooledSessions) (:410-434) - disjoint primitives, comment at :409 wrong for cross-path; both do plain map put (not putIfAbsent), loser session (connected, with JSch keep-alive thread) silently overwritten - unreachable from the map, invisible to releaseExoPlayerConnection/cleanupIdleConnections/disconnectAll permanently; each occurrence eats a MAX_CONCURRENT_CONNECTIONS=15 slot with NO exception. openChannelLock is per-PooledConnection so gives zero cross-path exclusion. SftpConnectionPoolTest covers only sweep toggle. Fix shape: one lock discipline for both paths (single monitor or runBlocking+Mutex bridge) + putIfAbsent semantics + lock all pooledChannels sites.

- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt:55** - PooledConnection.pooledChannels (plain ArrayList) is mutated/iterated under three different lock domains plus lock-free paths - CME/lost-channel race between ExoPlayer loader thread and IO coroutines
  - Evidence: Declaration line 55: `val pooledChannels: MutableList<PooledChannel> = mutableListOf()`. FILE_OPS path adds/removes under sessionMutex: line 173 `pooled.sessionMutex.withLock {` .. line 182 `pooled.pooledChannels.add(pc)`; removeChannel line 192-197 also under sessionMutex. PLAYBACK path adds under a DIFFERENT lock (openChannelLock): line 356 `pooled.openChannelLock.lock()` .. line 370 `pooled.pooledChannels.add(PooledChannel(ch, Mutex(), ChannelPurpose.PLAYBACK))`. evictPlaybackChannel iterates and removes with NO lock at all: lines 458-463 `pooledSessions.values.forEach { pooled -> val target = pooled.pooledChannels.firstOrNull { .. } .. pooled.pooledChannels.remove(target)`; releaseExoPlayerConnection iterates lock-free at 443-444 `pooled.pooledChannels.any { it.channel == channel }`; invalidateSession/cleanupIdleConnections/disconnectAll forEach the list holding only poolMutex (257, 303, 543). sessionMutex, openChannelLock and poolMutex are three independent locks, so none of these accesses mutually exclude each other. Runtime path: SFTP video playing (SftpDataSource.getConnectionForExoPlayer/releaseExoPlayerConnection run on the Media3 loader thread, SftpDataSource.kt:75/236) while thumbnail scans / file ops on the same host run withConnection() on Dispatchers.IO (SftpConnectionPool.kt:90) - concurrent ArrayList add/remove/iterate on the same session's channel list yields ConcurrentModificationException or a lost/duplicated element (channel never disconnected).
  - Fix hint: Pick one guard for all pooledChannels access (e.g. always sessionMutex, or replace with CopyOnWriteArrayList) and route the blocking PLAYBACK path and evict/release/iterate paths through it.
- **[P1] app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt:410** - Session-creation TOCTOU: suspend path guards pooledSessions with poolMutex while ExoPlayer blocking path guards it with synchronized(pooledSessions) - duplicate JSch sessions, loser leaks socket + keep-alive thread and its borrow count is never decremented
  - Evidence: Suspend path getOrCreateSession: line 207 `poolMutex.lock()` .. line 209 `val existing = pooledSessions[key]` .. line 235 `pooledSessions[key] = pooled`. Blocking path getOrCreateSessionBlocking: line 410 `synchronized(pooledSessions) {` .. line 411 `val existing = pooledSessions[key]` .. line 434 `pooledSessions[key] = pooled` (comment on 409 claims this avoids TOCTOU, but it uses a different lock than the suspend path, so they do not exclude each other). Runtime path: user starts SFTP video playback (Media3 loader thread -> getConnectionForExoPlayer -> getOrCreateSessionBlocking) while a thumbnail/file-op coroutine on Dispatchers.IO calls withConnection -> getOrCreateSession for the same host - both see existing==null, both connect a JSch session (line 230/432 session.connect), the second map put overwrites the first. The overwritten PooledConnection stays connected (keep-alive thread per lines 228-229/430-431) but is unreachable by cleanup (cleanupIdleConnections iterates only pooledSessions). If the orphan is the PLAYBACK one, releaseExoPlayerConnection (443-446) searches pooledSessions.values for its channel, finds nothing, decrements no counter and never disconnects it - permanent SSH session/socket leak per occurrence.
  - Fix hint: Use a single lock for both creation paths (e.g. make the blocking path use runBlocking-free poolMutex.tryLock loop or route both through one synchronized(pooledSessions) monitor), or putIfAbsent + disconnect-the-loser.

Full recovered dataset: see attachments of the audit follow-up ticket (audit-mass-2026-07-02-followup).

---

## 1. Проблема / симптом

SftpConnectionPool - гонки lock-доменов (pooledChannels CME, TOCTOU сессий). Детали и точные строки кода - в §0 (вербатим-находки аудита).

---

## 2. Корневая причина

`SftpConnectionPool` защищает своё общее состояние несколькими независимыми примитивами блокировки, которые не исключают друг друга:

1. `pooledChannels` (список каналов одной сессии) мутируется/итерируется под четырьмя разными доменами: `sessionMutex` (FILE_OPS-путь), `openChannelLock` (PLAYBACK-путь), `poolMutex` (invalidate/cleanup/disconnectAll), и вовсе без блокировки (`evictPlaybackChannel`, `releaseExoPlayerConnection`). Ни один из этих примитивов не защищает от трёх остальных - конкурентные add/remove/iterate с ExoPlayer loader thread (PLAYBACK) против Dispatchers.IO (FILE_OPS scan/ops) дают `ConcurrentModificationException` либо тихую потерю канала.
2. Создание сессии в карте `pooledSessions` защищено ДВУМЯ независимыми примитивами: suspend-путь (`getOrCreateSession`) использовал `kotlinx.coroutines.sync.Mutex` (`poolMutex`), а blocking ExoPlayer-путь (`getOrCreateSessionBlocking`) использовал JVM `synchronized(pooledSessions)` - разные мониторы, TOCTOU: оба видят `existing == null`, оба подключают JSch-сессию, второй `put` в карту молча перезаписывает первую - сессия-проигравший остаётся подключена (keep-alive поток жив), но недостижима для cleanup/invalidate/disconnectAll - утечка сокета/потока на каждое срабатывание гонки.

Оба дефекта - варианты одной болезни (несогласованная дисциплина блокировок между suspend- и blocking-путями одного пула) и правдоподобная общая причина зависаний сканирования из S0624 (§3.3).

---

## 3. Исправление

1. `pooledChannels`: тип поля сменён с `mutableListOf()` на `java.util.concurrent.CopyOnWriteArrayList()` - убирает риск CME/потери элемента на уровне самой коллекции независимо от того, какой (или никакой) лок держит вызывающий код. `sessionMutex` и `openChannelLock` оставлены как есть - они защищают СВОИ отдельные инварианты "проверить количество каналов своей purpose и добавить" (FILE_OPS ≤4, PLAYBACK ≤1), которые не пересекаются между собой (у каждого purpose свой count-check-then-add).
2. Создание/инвалидация/cleanup/disconnectAll сессий переведены на ОДИН общий монитор `synchronized(pooledSessions)` (тот же объект уже использовался blocking-путём) - `poolMutex` (kotlinx Mutex) удалён, четыре suspend-функции (`getOrCreateSession`, `invalidateSession`, `cleanupIdleConnections`'s launch-блок, `disconnectAll`) переписаны на `synchronized(pooledSessions) { .. }` вместо `poolMutex.lock()/withLock`. Безопасно, потому что ни один из этих критических участков не содержит точек приостановки (весь код внутри - блокирующие вызовы JSch/операции с картой, ни одного `suspend fun`-вызова) - `synchronized` не может застрять на suspend-границе.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0624 (sftp scan hang - возможная общая первопричина; S0624 сейчас BlockNeedUserTest, независимый фикс, конфликта нет)
- Внутренняя механика (дисциплина блокировок), без изменений UI/строк/flavor/schema - доп. owner-инпутов не требуется.

---

## 4. Проверка

- `.\a.ps1 fk` - компиляция Kotlin (standard).
- `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests SftpConnectionPoolTest` - существующий тест-класс проходит (покрывает только sweep-toggle, но подтверждает отсутствие регрессии компиляции/поведения).
- Статический ре-обзор: `pooledChannels` - `CopyOnWriteArrayList`; все 4 suspend-пути создания/инвалидации/cleanup/disconnect сессий используют один `synchronized(pooledSessions)`; blocking-путь (`getOrCreateSessionBlocking`) не менялся - уже использовал тот же монитор.
- Ручная device-проверка (BlockNeedUserTest, опционально): SFTP-видео воспроизводится, одновременно запустить сканирование/файловые операции на том же хосте (или переключить сеть Wi-Fi->LTE во время того и другого) - ожидание: ни CME, ни зависшего скана, ни утечки сессии в логах (см. также S0624 device-проверку).

---

## Last Audit

**Date:** 2026-07-02
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Checks: `pooledChannels` field type is `CopyOnWriteArrayList` (SftpConnectionPool.kt:61) - PASS. `poolMutex` (kotlinx Mutex) fully removed from the file - PASS. All 5 session-map critical sections (`getOrCreateSession`, `invalidateSession`, `cleanupIdleConnections`, `getOrCreateSessionBlocking`, `disconnectAll`) use `synchronized(pooledSessions)` - PASS x5 (one per site, `getOrCreateSessionBlocking` unchanged/pre-existing). `standard debug` Kotlin compile - PASS. `SftpConnectionPoolTest` unit suite - PASS (sweep-toggle coverage, no regression). detekt scoped gate (import-ordering fixed: `Job`/`SupervisorJob` swap) - PASS. Dev log entry present (S0866 @ 16:24:58) - PASS. FEATURES trilingual - EXEMPT (internal lock-discipline fix, no user-visible capability).

### Manual / on-device

- [ ] SFTP video playback concurrent with scan/file-ops on the same host (or a Wi-Fi->LTE handover during both) - expect no CME, no parked scan, no leaked session in logs; cross-check against S0624's own device test since both may share this root cause.

