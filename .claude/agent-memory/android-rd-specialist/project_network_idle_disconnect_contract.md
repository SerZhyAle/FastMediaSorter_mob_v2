---
name: network-idle-disconnect-contract
description: IdleDisconnectPolicy 30s timers tore down LIVE SMB/SFTP/FTP transports - every long-running consumer must heartbeat (DataSource) or defer (in-flight counter); S1295/S1296/S1297
metadata:
  type: project
---

All three network stacks arm a 30 s `IdleDisconnectPolicy` timer, and until 2026-07-30 none of them knew about work that runs longer than the window:

- **SMB** - a PLAYER pool entry keeps `usageCount = 0` and playback never re-enters `SmbConnectionManager`, so the timer closed share/session/connection under the running player every ~30 s for a whole film. Fixed by `SmbConnectionManager.touchPlaybackTransport(connectionInfo)` called from `SmbDataSource.read()` on a 15 s heartbeat - the same shape `SftpDataSource.maybeTouchPlaybackTransport` already had.
- **SFTP** - `releaseExoPlayerConnection` resolved the owning session by scanning `pooledSessions`, which fails once `invalidateSession` removed it while the borrow was live: the decrement was skipped and the deferred disconnect (implemented only in `withConnection`'s finally) never ran, leaking the JSch Session, its socket and its keep-alive thread. Fixed with a `playbackOwners` channel->session registry. Also: `getOrCreateSession*` held `synchronized(pooledSessions)` across a 10 s `session.connect()`, serializing every other host behind one dead server - now a per-key `sessionCreationLocks` monitor wraps the handshake and only map read/write stays on the shared monitor.
- **FTP** - one shared `FTPClient`; nothing refreshed the timer during a transfer, so any download/upload/recursive listing longer than 30 s was killed mid-stream. Fixed with an `inFlightOperations` counter: the idle callback re-arms instead of disconnecting while a operation is in flight.

**How to apply:** adding any long-running consumer of these clients (new DataSource, bulk transfer, background scan), decide up front which side owns the timer - heartbeat from the consumer, or a counter the idle callback honours. A silent reconnect loop in logs (`already been closed` + fresh connect every ~30 s) is this bug, not a flaky server.
