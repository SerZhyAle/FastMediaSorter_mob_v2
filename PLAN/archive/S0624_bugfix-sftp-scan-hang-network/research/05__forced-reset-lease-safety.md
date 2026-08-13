# Research 05 - Forced-reset lease safety + why the scan timeout needs a force-close watchdog

**Ticket:** S0624
**Date:** 2026-06-22
**Method:** static read of `SftpConnectionPool` / `SftpClient` / `SftpMediaScanner` / `GetMediaFilesUseCase` + coroutine-cancellation semantics.
**Resolves:** strategic §6 item 5 (safety of forced pool reset under an active lease). Also justifies the FIX #2 watchdog design used in the tactical plan.

> Class/method/line names are intentional - this is a `/spec-tech` input.

---

## Part A - Forced `disconnectAll()` under an active borrow does NOT self-deadlock

Question (§6 item 5): does `SftpConnectionPool.disconnectAll()` (the FIX #1 force-reset) deadlock against a scan that is parked inside `withConnection`, holding the channel mutex and an active borrow?

Lock inventory of a hung FILE_OPS scan (`SftpConnectionPool.withConnection`, `:87-145`):

- holds `connectionSemaphore` permit (`:93`).
- holds the per-channel `pc.mutex` via `pc.mutex.withLock { block(pc.channel) }` (`:106`).
- holds `activeBorrowCount == 1` (`:100`).
- does **NOT** hold `poolMutex` - `getOrCreateSession` locks and unlocks `poolMutex` internally (`:207-234`) and returns before `block` runs.

`disconnectAll()` (`:527-541`) acquires only `poolMutex` (free) and then calls JSch `channel.disconnect()` / `session.disconnect()`. Those are plain JSch calls; they do not touch the Kotlin `pc.mutex` or the semaphore. Therefore:

- `disconnectAll()` cannot block on `pc.mutex` (it never asks for it).
- `disconnectAll()` cannot block on `poolMutex` (the hung scan released it).

Verdict: **no self-deadlock**. The force-reset proceeds immediately.

Effect on the hung scan: `session.disconnect()` closes the underlying socket and the JSch IO streams. The blocking `channel.ls()` parked on that socket (read on a half-open transport, or write of the request) throws a `SocketException` / `IOException` and returns. Control re-enters the `withConnection` catch ladder (`:107-137`): channel not connected -> `removeChannel`; session not connected -> `invalidateSession` + `getOrCreateSession` retry on a fresh session. If the network is mid-handover the retried connect fails fast (connect timeout) and the exception propagates out of the flow to the existing collector error handlers. Either way the infinite park is broken.

This is exactly the SMB recovery mechanism (`SmbConnectionManager.handleNetworkReconnect` -> `closeAllConnections`), so FIX #1 reaches behavioural parity.

Deferred-disconnect interaction (S0219): if FIX #1 fires `invalidateSession` rather than `disconnectAll`, the active borrow defers the actual disconnect to the last borrower's `finally` (`:142-145`). FIX #1 must call `disconnectAll()` (unconditional socket close), NOT `invalidate()`, precisely because the parked borrower will never reach its `finally` until the socket is closed. `disconnectAll()` closes sockets regardless of `activeBorrowCount`, which is the required behaviour here.

---

## Part B - A bare `withTimeout` does NOT bound a blocking `ls`; the timeout must force-close

Naive reading of FIX #2 is "wrap the scan in `withTimeout(60s)`". That is **insufficient** on its own, and the tactical plan must not ship it that way.

`channel.ls()` (inside `SftpClient.listFiles`, run on `Dispatchers.IO`) is a blocking JVM call, not a suspending one. Coroutine cancellation is cooperative: `withTimeout` / `withTimeoutOrNull` fires its timer and marks the coroutine cancelled, but the IO thread parked in the native socket read never reaches a suspension point to observe the cancellation. The `withTimeout` frame cannot complete until the blocking body returns. Net result: the timeout does not return until `ls` unparks on its own - which is the very thing that is broken. So a lone `withTimeout` gives a false sense of safety.

To actually bound the hang the timeout handler must close the transport so the blocking `ls` throws and returns:

- start a watchdog (`launch { delay(budget); forceClose() }`) alongside the scan op;
- `forceClose()` = `sftpClient.disconnectAll()` (Part A proves this unblocks the parked `ls`);
- when the op throws after the socket close, convert to a domain `ScanTimeoutException` (because the watchdog fired), otherwise rethrow the original;
- cancel the watchdog on the normal-completion path.

This is why FIX #2 lives in `SftpMediaScanner` (it owns the `sftpClient` handle needed to force-close), not in `GetMediaFilesUseCase` (domain layer, no client handle - a layer violation to reach the socket).

The application watchdog budget sits **above** the JSch SO_TIMEOUT (30 s, `SftpConnectionPool.kt:630`) and above the keep-alive detection window (FIX #3, ~30 s) so those cleaner library-level recoveries get first crack; the watchdog is the last-resort backstop that guarantees scan termination in any scenario (ADR-2).

Order of defences against a half-open socket, fastest clean recovery first:

1. FIX #1 - network-change invalidation: immediate force-close on the known trigger (Wi-Fi -> LTE).
2. FIX #3 - JSch keep-alive: its own thread drops a dead session after `interval x countMax` (~30 s) in ANY scenario, unblocking `ls`.
3. FIX #2 - application watchdog (~60 s): backstop force-close + the user-visible error channel, independent of the trigger.

---

## Consequences for the tactical plan

- FIX #1 must call the unconditional `disconnectAll()` (socket close), never the borrow-deferring `invalidate()`.
- FIX #2 is a watchdog that force-closes on timeout, not a bare `withTimeout`; it belongs in `SftpMediaScanner`.
- `ScanTimeoutException` is the domain error the existing flow collectors (`BrowseLoadingManager.catch{}`, `PlayerMediaFilesLoader` try/catch) already surface - no new error-channel plumbing is required, only a typed message mapping.
- §6 item 5 is **Resolved**: force-reset is deadlock-free and is the mechanism that unparks both FIX #1 and the FIX #2 watchdog.
