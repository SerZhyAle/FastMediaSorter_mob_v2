# S0466 — SFTP audio: inputstream closed immediately after slow directory scan

**Status:** Archived
**Priority:** 60

## §0 — Raw evidence (auto-captured, 2026-06-16)

Device: SPRD `ums512_1h10_Natv`, Android 14 firmware / API 29, release 2.60.6150.338 standard.

Log sequence (fastmediasorter_20260616_181028.log):

```
18:11:42  W  ScanMetrics: SLOW SCAN detected - 19617ms threshold=6000ms expected_file_count=0 actual_file_count=3696 resourceId=33 type=SFTP
18:12:59  W  ScanMetrics: SLOW SCAN detected - 20294ms threshold=6000ms expected_file_count=0 actual_file_count=3696 resourceId=33 type=SFTP
18:13:14  W  PlayerMediaLoaderManager.playVideo: START - path=sftp://46.54.0.135:22022/J:/MEDIA/mp3/_bestMp3/4/Shogun - UFO (New Version).mp3
18:13:14  W  SFTP [FILE_OPS] IOException attempt 0: /J:/MEDIA/mp3/.../Shogun - UFO (New Version).mp3 - inputstream is closed
18:13:21  W  preCacheNetworkAudio: source=sftp reason=sftp-audio-early-direct-stream timed out after 5000ms - falling back to direct streaming
18:13:21  W  playAudioViaService: pre-cache failed, falling back to in-app player
18:13:42  W  PlayerMediaLoaderManager.playVideo: START - path=sftp://...фильм Служебный роман...mp3
18:13:43  W  SFTP [FILE_OPS] IOException attempt 0: ... - inputstream is closed
```

Pattern: two slow SFTP scans (19–20 s, 3696 files) → user opens audio file → `IOException: inputstream is closed` fires on attempt 0 → 5 s timeout exhausts retries → audio never plays.

Same server and credentials work for scanning (3696 files enumerated successfully). Failure is specific to file-read after the long scan.

## §1 — Problem

`SftpClient.downloadFile()` iterates attempts 0-3. Attempt 0 gets `IOException: inputstream is closed` immediately — the JSch `channel.get(remotePath)` returns a stream that is already closed before a single byte is read. The retry mechanism calls `disconnectTransport` before attempt 1, but the 5 s `withTimeout` in `preCacheNetworkAudio` (`sftp-audio-early-direct-stream` policy) fires during or after subsequent retries, producing `TimeoutCancellationException` → fallback to in-app player → also fails.

Root hypothesis: `SftpConnectionPool` shares one session between `FILE_OPS` and `PLAYBACK` channels (unified design, S0113 Phase 04). After a 20 s listing-heavy scan that issues hundreds of `ChannelSftp.ls()` calls on the shared session, the session's internal JSch state becomes stale (server-side inactivity timeout, JSch window exhaustion, or SSH channel reuse after the server silently closed the last channel). The next `withConnection` call for FILE_OPS delivers a pooled channel whose InputStream is already closed.

## §2 — Scope

**In scope:**
- `SftpConnectionPool.withConnection` — validate that pooled `ChannelSftp` channels are still alive before use (e.g. `channel.isClosed` or `channel.isConnected` check; reconnect on stale).
- `SftpClient.downloadFile` retry chain — verify `disconnectTransport` fully invalidates the pooled session so the next `withConnection` opens a fresh one.
- Pre-cache timeout policy (`sftp-audio-early-direct-stream`, 5 s) — assess whether it's long enough to survive one reconnect + retry.

**Out of scope:**
- `InlinePlayer` SFTP fallback — tracked separately (see §0 of S0464 and the `BrowseInlineAudioManager.resolveLocalPath` fix already applied).
- Slow scan itself — 19-20 s is expected for 3696 files over SFTP on car Wi-Fi; not a bug.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0113 (SftpConnectionPool unified design), S0195 (lifecycle bootstrap), S0219 (dead-transport retry)

## §4 — Root cause (confirmed)

- The pool already recovers a dead transport (`SftpConnectionPool.isDeadTransportException` matches `"inputstream is closed"`) by invalidating the stale session and retrying once on a fresh one - but only when the borrowed `block` **throws**.
- `SftpClient.downloadFile`'s block catches `IOException` and returns `Result.failure` instead of throwing, so the pool never runs its fast reconnect for downloads.
- `downloadFile`'s own retry loop then applies exponential backoff (1s + 2s + 4s). The first reconnect that would cure the stale session sits behind a 1s sleep; the chain outlasts the 5s `sftp-audio-early-direct-stream` pre-cache budget, so both pre-cache and the direct-stream fallback fail.
- Proactive `channel.isConnected` / `session.isConnected` validation cannot detect this: after a silent transport death both flags stay `true`; only an actual read surfaces `"inputstream is closed"`. Recovery must stay reactive.

## §5 — Implementation

- `SftpConnectionPool`: extract dead-transport detection into a shared `companion` `isDeadTransport(Throwable?)`; the existing instance `isDeadTransportException` delegates to it.
- `SftpClient.downloadFile`: track whether the prior attempt failed with a dead-transport `IOException`; when it did, skip the exponential backoff and retry immediately - `disconnectTransport` already established a fresh session. Backoff is preserved for genuinely transient errors (`SSH_FX_FAILURE` / `SSH_FX_BAD_MESSAGE`, `IndexOutOfBounds`, other `IOException`s).
- The outer loop still resets a `ByteArrayOutputStream` before each retry, so the immediate retry carries no partial-write risk.
- `readFileBytes` / `readFileBytesRange` already retry once with no backoff after `disconnectTransport`, so they need no change.
- Pre-cache timeout (`SFTP_AUDIO_STARTUP_PRECACHE_TIMEOUT_MS = 5_000`) left unchanged: an immediate reconnect-retry fits comfortably; the 5s budget is an intentional early fallback to direct streaming, not a full-download guarantee.

### Files

- `app_v2/.../data/remote/sftp/SftpConnectionPool.kt`
- `app_v2/.../data/remote/sftp/SftpClient.kt`

### Validation

- `.\a.ps1 fk` - PASS.
- Device test pending: reproduce two slow SFTP scans, then open an audio track; expect playback to start (pre-cache or direct stream) instead of failing on `"inputstream is closed"`.
