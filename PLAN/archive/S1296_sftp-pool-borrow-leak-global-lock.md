# S1296 - SFTP pool: playback borrow lost on session invalidation (SSH session + keep-alive thread leak) and global monitor held across 10s connect

**Ticket:** S1296
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): network-io-2, hang-paths-1.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.

## Finding 1: SFTP releaseExoPlayerConnection loses the borrow when the session left the pool map - deferred disconnect never fires, leaking live SSH sessions and keep-alive threads

- Severity: P1, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt:453`
- Symptom: Zombie JSch SSH sessions accumulate over hours of SFTP use: each leaked session keeps its TCP socket and a keep-alive thread pinging every 15 s until process death (battery drain, socket/thread/memory growth); a wedged activeBorrowCount also pins the pooled session so idle cleanup skips it forever.
- Failure scenario: SFTP video is playing (PLAYBACK borrow, activeBorrowCount=1). A concurrent FILE_OPS call hits a dead transport and calls invalidateSession(key): the session is removed from pooledSessions and disconnect is DEFERRED because activeBorrowCount>0 ('last borrower will disconnect', lines 262-275). When the player later closes, releaseExoPlayerConnection(channel) looks the session up via pooledSessions.values - it is no longer there - so the decrement is skipped and disconnectOrphan is never called (only withConnection's finally calls it, lines 155-165; the playback release path never does). The Session object, its socket and its keep-alive thread live until app kill. Variant 2: release(broken=true) on a shared fallback channel evicts it from pooledChannels; the second borrower's later release finds no owner, the count wedges at 1, and that host's session is never idle-evicted and every future invalidation of it defers forever - one more leaked session per network hiccup, repeating over days.
- Fix sketch: Return the owning PooledConnection (not just session+channel) in ExoPlayerConnection and decrement its counter directly on release; after decrementing to 0, if the pooled is no longer in pooledSessions (invalidated while borrowed), call disconnectOrphan(pooled) - same contract withConnection's finally already implements.
- Verifier rationale: Confirmed, and reachable more easily than the finder claimed. releaseExoPlayerConnection (450-465) locates the owner only via pooledSessions.values and never calls disconnectOrphan - the deferred-disconnect contract ('last borrower will disconnect', lines 262-275) is implemented only in withConnection's finally (152-165), which the playback path never runs. A session invalidated while a PLAYBACK borrow is held is therefore orphaned without disconnect and its decrement is silently skipped. Critically, SftpClient.armTransport (SftpClient.kt:743) arms a 30 s idle timer whose callback is pool.invalidate (line 757), and the playback heartbeat only fires from read() - so a pause or a full ExoPlayer buffer >30 s invalidates a LIVE session mid-borrow; on close, the JSch Session, its socket, and its keep-alive thread leak until process death (keep-alive keeps it healthy, so no self-termination). The broken=true wedge variant (MAX_PLAYBACK_CHANNELS=1, eviction at 467-478, channel shared via the fallback path at 388-392) is also present, pinning a session against idle cleanup forever.

Evidence excerpt:

```
fun releaseExoPlayerConnection(channel: ChannelSftp? = null, broken: Boolean = false) {
    if (channel != null) {
        pooledSessions.values
            .find { pooled -> pooled.pooledChannels.any { it.channel == channel } }
            ?.activeBorrowCount
            ?.updateAndGet { maxOf(0, it - 1) }
    } ...
```

## Finding 2: SFTP pool holds global monitor across 10s SSH connect - all hosts serialize behind a dead server

- Severity: P1, effort: medium.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt:245`
- Symptom: Every SFTP operation for every server (file ops, thumbnail range-reads, ExoPlayer playback acquisition) hangs while any one server is being (re)connected; with one dead NAS in the resource list, SFTP work against healthy servers stalls in 10-20 s steps, effectively freezing SFTP browsing/playback for minutes.
- Failure scenario: User has two SFTP resources: home PC (offline) and NAS (online). App runs for hours; a browse grid fires a dozen thumbnail loads against the offline PC. Each load enters getOrCreateSession, takes the single synchronized(pooledSessions) monitor shared by ALL hosts, and blocks up to 10 s in session.connect (plus up to 10 s more in openChannelSafe on a half-dead transport). Failed connects are never cached, so the attempts queue back-to-back. Meanwhile playback/thumbnails from the healthy NAS - including ExoPlayer's loading thread via getOrCreateSessionBlocking, and even the cached-session fast path which must also take the monitor - park behind N x 10-20 s of dead-host handshakes: spinners for minutes, video start hangs. The in-file S0866 comment justifies the plain monitor with 'zero suspension points' but session.connect is blocking network I/O.
- Fix sketch: Do the JSch handshake outside the global monitor: take the monitor only for map lookup/insert, use a per-ConnectionKey mutex (or putIfAbsent on a Deferred/placeholder) so concurrent creates for the same host coalesce while different hosts connect in parallel; re-check and publish the session under the monitor after connect succeeds. Apply to getOrCreateSession, getOrCreateSessionBlocking, invalidateSession's disconnects and cleanupIdleConnections.
- Verifier rationale: Confirmed by reading the file. getOrCreateSession (line 223) and getOrCreateSessionBlocking (line 420) hold the single synchronized(pooledSessions) monitor - shared across ALL hosts - through session.connect(10_000) at line 245 and a second blocking channel connect(10_000) in openChannelSafe (line 248/578). The cached-session fast path (lines 224-225) also needs this monitor, so operations against a healthy server genuinely serialize behind a dead host's 10-20 s handshakes; failed connects are not negatively cached, so N queued thumbnail loads repeat the full attempt back-to-back. invalidateSession/disconnectAll/cleanup also take the monitor with blocking disconnects. The in-file S0866 comment ('zero suspension points') addresses coroutine suspension only, not blocking network I/O under the monitor. All paths are off-main (Dispatchers.IO / ExoPlayer loader), so no ANR - hence P1 (multi-minute functional freeze of SFTP browse/playback), not P0. Fix requires restructuring lock scope across getOrCreateSession, getOrCreateSessionBlocking, invalidateSession, cleanup - medium.

Evidence excerpt:

```
private suspend fun getOrCreateSession(...): PooledConnection {
    ensurePeriodicSweepRunning()
    synchronized(pooledSessions) {
        ...
        session.connect(CONNECTION_TIMEOUT)   // line 245, CONNECTION_TIMEOUT = 10_000 (line 657)
        val pooled = PooledConnection(session = session, jsch = jsch)
        val firstCh = openChannelSafe(pooled) // second blocking connect(10_000) still under the monitor
        ...
    }
}
// identical blocking twin getOrCreateSessionBlocking, lines 420-447 (ExoPlayer path)
```

