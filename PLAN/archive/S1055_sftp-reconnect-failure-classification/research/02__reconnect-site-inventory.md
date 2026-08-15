# Research 02 - Reconnect / retry site inventory on the live SFTP path

Resolves §6 item 2: are all live-path retry/reconnect sites covered, and where is the real defect?

## Key finding - SFTP does NOT use withRetry(isTransient)

- `withRetry` + `RetryPolicy(retryOn = isTransient)` (`data/network/exceptions/RetryPolicy.kt`) is used
  only by **Dropbox/cloud** (`data/cloud/DropboxClient.kt`), not by SFTP.
- `data/network/lifecycle/SftpConnectionGate.kt:48-54` - `withRetry` throws
  `UnsupportedOperationException` ("S0067 Phase 03: SFTP withRetry not yet routed through the gate").
- Therefore **the contract's "silent unbounded retry-loop" risk does not exist on the current SFTP
  stack.** Each SFTP retry site does at most one bounded reconnect. The real, observable O2 defect is
  **misclassification -> wrong user message + wrong semantics** (a host-key mismatch shown as a routine
  "connection lost" transient), not an infinite loop.

## Site-by-site inventory

1. `data/network/datasource/SftpDataSource.kt` (ExoPlayer streaming)
   - `read()` catch (~L169): one transparent `reconnectStream()` on a transient read error, then a
     single re-read. On failure -> wrapped `IOException` -> ExoPlayer. **Bounded (1), no loop.**
   - A host-key/auth failure can only appear on the reconnect's `connect()`, which surfaces as a single
     playback error. Playback surface cannot easily show a re-pair prompt; the requirement here is only
     "no silent infinite retry", already satisfied. Optional hardening: skip the reconnect attempt when
     the error classifies non-transient.

2. `data/remote/sftp/SftpConnectionPool.kt` `withConnection()` (FILE_OPS, browse/scan)
   - Inner catch (L120): on `!session.isConnected` -> `invalidateSession` + one reconnect + retry
     `block` (L125-134); on `isDeadTransportException` -> one reconnect (L138-148, IOException-message
     gated, never matches a JSchException auth/host-key). **Bounded (1), no loop.**
   - Auth/host-key fail at `getOrCreateSession -> session.connect()` on the retry, propagate out, caught
     by the outer catch (L177) -> `Result.failure(rawException)`. The **caller classifies** it -> the
     fix lives in the classifier + mapper, not here.

3. `data/remote/sftp/SftpClient.kt` `readFileBytes` (~L305-358)
   - One retry on `IndexOutOfBoundsException` / `SftpException(SSH_FX_FAILURE|BAD_MESSAGE)` / IOException.
     **Bounded (1).** Auth/host-key never reach here (they fail at session connect, surfaced via
     `withConnection`'s `Result.failure`).

4. `withRetry(isTransient)` callers - none on SFTP (see key finding). Once classification is corrected,
   the fix is automatically inherited by any future SFTP consumer that IS routed through `withRetry`
   (e.g. when S0067 Phase 03 lands), because auth -> AccessDenied and host-key -> new type are both
   non-transient.

## Consequence for the spec

§6 item 2 Resolved. Reconnect sites are already bounded; no de-looping needed. Столп D reframes from
"prevent retry-loop" to: (a) ensure the correctly-typed exception propagates to the display layer
(items 2/3 already do via `Result.failure` -> `classify`), and (b) optional guard in `SftpDataSource`
to not attempt a reconnect for a classified non-transient. The load-bearing work is Столп A/B/C
(subtype + classifier branches + mapper + strings).
