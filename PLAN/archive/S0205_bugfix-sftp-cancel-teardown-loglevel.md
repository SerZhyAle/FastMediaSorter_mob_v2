# S0205 — bugfix-sftp-cancel-teardown-loglevel

**Ticket:** S0205
**Status:** In Progress
**Priority:** 90
**Date:** 2026-05-15
**Tier:** 1 — Quick Win (ad-hoc)

## Problem

When ConnectionThrottle tears down active SFTP connections on `ON_STOP` or during camera-intent
launch, in-flight `readFileBytes` calls receive `IOException: inputstream is closed`.
Two compounding issues:

- `SftpClient.readFileBytes` logs this at `E` level, which is noise: the IOException is expected
  during intentional teardown, not an actionable error.
- `SftpClient.readFileBytes` catch blocks use `catch (e: Exception)`, which silently swallows
  `CancellationException` — cooperative coroutine cancellation is never propagated.
- `SftpConnectionPool.withConnection` S0147 dead-transport retry branch fires because
  `"inputstream is closed"` is in `DEAD_TRANSPORT_MESSAGES`, triggering a spurious reconnect
  attempt when the coroutine is already being cancelled.

## Approach

- **SftpClient.kt** — `readFileBytes`, first-attempt catch block (lines 271–274):
  - Add `catch (e: CancellationException) { throw e }` before `catch (e: Exception)` to stop
    coroutine cancellation from being swallowed.
  - Split `catch (e: Exception)` into `catch (e: IOException)` logged at `W` + `catch (e: Exception)`
    logged at `E`, so transient IO teardown never produces E-level noise.
- **SftpClient.kt** — `readFileBytes`, retry-attempt catch block (lines 293–296):
  - Same two changes as above.
- **SftpConnectionPool.kt** — `withConnection`, dead-transport retry branch (line 97):
  - Call `ensureActive()` immediately before the S0147 reconnect sequence so that a cancelled
    coroutine throws `CancellationException` (caught at the outer D-level handler) instead of
    attempting a spurious reconnect.

## Done criteria

- `SftpClient.kt`: `catch (e: CancellationException) { throw e }` appears before
  `catch (e: Exception)` in both try blocks of `readFileBytes`; `IOException` is logged at `W`,
  not `E`.
- `SftpConnectionPool.kt`: `ensureActive()` call is present immediately before the
  `Timber.w("SFTP [FILE_OPS] dead transport detected …")` line.
- Debug tag `Timber.d("S0205: …")` present at each changed flow entry while
  `Status: BlockNeedUserTest`.
