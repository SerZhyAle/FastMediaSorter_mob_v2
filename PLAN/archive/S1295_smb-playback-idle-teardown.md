# S1295 - SMB playback connection torn down by the 30s idle timer during active playback

**Ticket:** S1295
**Status:** Archived
**Priority:** 60
**Date:** 2026-07-30

> Parked from the 2026-07-30 long-running/background-use code audit (10-dimension workflow with per-dimension adversarial verification, run wf_35a236bb-aa9). Umbrella reference: S0715 static Layer-3 pass (2026-06-26). Raw result: temp/scratch/longrun-audit/audit-result.json.

## 0. Source

- Audit finding id(s): network-io-1.
- Every finding below was confirmed by an adversarial verifier that re-read the cited code and tried to refute it.
- Related: the SFTP twin already carries the heartbeat fix (SftpDataSource.maybeTouchPlaybackTransport) - mirror it or count PLAYER borrows.

## Finding 1: SMB playback connection is torn down by the 30 s idle timer in the middle of active playback

- Severity: P1, effort: small.
- File: `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt:973`
- Symptom: Long SMB video playback suffers a forced connection teardown + full TCP/auth/tree-connect reconnect roughly every 30 seconds: stutter/rebuffer, reconnect churn in logs, server session churn, battery drain.
- Failure scenario: User plays a 2-hour movie from an SMB share. getConnectionForExoPlayer() arms the idle timer (lines 860/916) but the PLAYER PooledConnection is created with usageCount=0 and never incremented, and SmbDataSource has no heartbeat (unlike SftpDataSource.maybeTouchPlaybackTransport, added specifically for this failure class - see its comment 'falsely invalidates a live ExoPlayer transport'). 30 s after open, the timer fires; closeConnectionAsync sees usageCount==0 and closes share/session/connection under the active player. The next file.read() throws 'DiskShare has already been closed' (the exact branch SmbDataSource.readInternal line 472 was added to survive), triggering closeQuietly + invalidate + fresh connect. This repeats every ~30 s for the entire movie.
- Fix sketch: Either increment usageCount for PLAYER borrows in getConnectionForExoPlayer and decrement from SmbDataSource.close() (closeConnectionAsync already honours the counter), or mirror the SFTP fix: expose smbClient.touchPlaybackTransport() and call it from SmbDataSource.read() on a 15 s heartbeat like SftpDataSource does.
- Verifier rationale: Confirmed. getConnectionForExoPlayer arms the 30 s idle timer (lines 860/916, IDLE_TIMEOUT_MS=30_000 at line 115) but the PLAYER PooledConnection keeps usageCount=0 (increments at lines 297/360 belong only to the scanner withConnection path) and SmbDataSource has no touch/heartbeat anywhere (unlike SftpDataSource.maybeTouchPlaybackTransport). handleIdleTimeout (line 967) is cleanup-only: pool.removeAndCloseAsync -> closeConnectionAsync closes share/session/connection when usageCount==0 (SmbConnectionPool.kt:96-107), i.e. under the active player. The next file.read() hits the 'already been closed' branch (SmbDataSource.kt:472), reconnects (fresh TCP/auth/tree-connect), re-arms, and repeats roughly every 30 s for the whole playback. Recovery exists but the churn, retry-failure risk, and server session churn during a core feature make this P1.

Evidence excerpt:

```
private fun armIdleTransport(transportKey: String, key: ConnectionKey) {
    idleDisconnectPolicy.arm(transportKey, IDLE_TIMEOUT_MS) {   // IDLE_TIMEOUT_MS = 30_000L
        handleIdleTimeout(transportKey, key)
    }
}
private fun handleIdleTimeout(...) { pool.removeAndCloseAsync(key) }
```

