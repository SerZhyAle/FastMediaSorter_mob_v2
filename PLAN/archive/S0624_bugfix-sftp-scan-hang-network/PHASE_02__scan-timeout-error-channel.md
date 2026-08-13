# Phase 02 - Scan timeout + error channel

**Strategic spec:** [`../S0624_bugfix-sftp-scan-hang-network.md`](../S0624_bugfix-sftp-scan-hang-network.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 5 / 5
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Bound the SFTP listing with a force-close watchdog so a hung scan always terminates, and surface the resulting `ScanTimeoutException` as a localized, retry-guiding user message (strategic Pillar B / FIX #2 / ADR-2; goals G1, G3; criteria C1, C3).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`sftpClient.disconnectAll()` is the force-close used by the watchdog).
- [ ] Read [`research/05__forced-reset-lease-safety.md`](research/05__forced-reset-lease-safety.md) Part B - a bare `withTimeout` does NOT interrupt the blocking `ls`; the timeout MUST force-close the socket.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt` | Modified | ≤ 95 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt` | Modified | ≤ 600 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLoadingAuxManager.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` | Modified | ≤ 615 |

> `SftpMediaScanner.kt` (548) and `PlayerMediaFilesLoader.kt` (607) exceed 500 LOC - timestamped backup into `temp/` before editing.

---

## Steps

### Step 02.1 - Add ScanTimeoutException to the sealed network hierarchy

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkExceptions.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `class ScanTimeoutException(resourceName: String, cause: Throwable? = null) : NetworkException("SFTP scan for '$resourceName' timed out", cause)` to `NetworkExceptions.kt`. It MUST live in this file because `NetworkException` is `sealed`. Keep the literal substring `timed out` in the message so the existing message-based error mappers still classify it as a timeout if a typed branch is ever missed (defense in depth). The base already extends `IOException`, so existing `catch (e: Exception)` and Flow `.catch { }` collectors handle it without new plumbing.

**Verification:**

- `Grep` - `class ScanTimeoutException` matches once in `NetworkExceptions.kt`.
- `Grep` - the declaration extends `NetworkException(` and the message contains `timed out`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-22 - Verification 2/2 PASS. Files: NetworkExceptions.kt (+12 LOC, `ScanTimeoutException`). Dev log batched at finalization.

---

### Step 02.2 - Force-close watchdog around the SFTP listing

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Wrap the hanging listing call in `scanFolder` - `sftpClient.listFiles(clientInfo, connectionInfo.remotePath, recursive = scanSubdirectories)` inside the `ConnectionThrottleManager.withThrottle { }` block (`:67-73`) - with a watchdog so the scan cannot hang forever. Implement a private suspend helper `listWithWatchdog(resourceName: String, op: suspend () -> Result<List<SftpFileListing>>): Result<List<SftpFileListing>>` using `coroutineScope { }`:
> 1. `var timedOut = false`; start a watchdog `launch { delay(SCAN_WATCHDOG_TIMEOUT_MS); timedOut = true; runCatching { sftpClient.disconnectAll() } }` - the force-close unblocks the blocking `ls` (research/05 Part B; a bare `withTimeout` cannot, because `ls` is a blocking call).
> 2. **CRITICAL - handle both failure shapes.** `sftpClient.listFiles` does NOT throw on the force-close path: `SftpConnectionPool.withConnection` has an outer `catch (e: Exception) { Result.failure(e) }` (`SftpConnectionPool.kt:164-166`), so an aborted listing comes back as `Result.failure`, not an exception. Therefore: run `val result = op()`; if `timedOut` -> `throw ScanTimeoutException(resourceName, result.exceptionOrNull())` (covers the `Result.failure`-while-timed-out case); otherwise return `result`.
> 3. Also guard the throwing path: `catch (e: ScanTimeoutException) { throw e }`, then `catch (e: CancellationException) { throw e }` (never swallow cancellation), then `catch (e: Exception) { if (timedOut) throw ScanTimeoutException(resourceName, e) else throw e }`.
> 4. `finally { watchdog.cancel() }` so the watchdog never lingers on the normal-completion path.
>
> Add `private const val SCAN_WATCHDOG_TIMEOUT_MS = 60_000L` with a WHY comment: the budget sits above the 30 s JSch SO_TIMEOUT and the ~30 s keep-alive window (Phase 03) so the cleaner library-level recoveries get first crack; the watchdog is the last-resort backstop. Pass a human resource label (the parsed host or `resourceKey`) as `resourceName`. Apply the same helper to the `sftpClient.listFiles` call in `scanFolderPaged` for parity. The existing `catch (e: CancellationException) { throw e }` ladder in `scanFolder` (`:178-184`) stays and still rethrows cancellation first.

**Verification:**

- `Grep` - `SCAN_WATCHDOG_TIMEOUT_MS` declared once and referenced by the watchdog `delay`.
- `Grep` - `sftpClient.disconnectAll()` is called inside the watchdog `launch` block.
- `Grep` - `throw ScanTimeoutException(` present in `SftpMediaScanner.kt`.
- `Grep` - the watchdog helper is used by both `scanFolder` and `scanFolderPaged` listing calls.

**Status:** `[x] done`

**Step Log:**

- 2026-06-22 - Verification 4/4 PASS (const+delay, disconnectAll in watchdog, throw ×2, listWithWatchdog ×3 = decl + 2 calls). Both Result.failure-on-timeout and throw-on-timeout paths converted to ScanTimeoutException. Files: SftpMediaScanner.kt (+37 LOC). Dev log batched at finalization.

---

### Step 02.3 - Add the localized scan-timeout message (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a new string key `error_scan_timeout` to all three locales in lockstep. Suggested copy (refine to match `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist - neutral, retry guidance, no technical jargon such as "socket"/"timeout exception"; model the tone on the existing `error_network_timeout`):
> - EN: `The folder is taking too long to load. Check your network and try again.`
> - RU: `Папка загружается слишком долго. Проверьте сеть и повторите попытку.`
> - UK: `Тека завантажується надто довго. Перевірте мережу та повторіть спробу.`
>
> **Cyrillic byte-safety:** do NOT pass RU/UK literals as `pwsh` CLI arguments from a Bash shell - they mojibake at the bash→pwsh boundary. Either author a UTF-8 `.ps1` wrapper (via the Write tool) that calls `scripts/utils/set-android-string.ps1 -Action add -Key error_scan_timeout -En "..." -Ru "..." -Uk "..."` and run it, or edit the three `strings.xml` files directly with the Edit tool. Verify with `Grep`/`Read`, never by echoing to a console.

**Verification:**

- `Grep` - `name="error_scan_timeout"` matches once in each of `values/`, `values-ru/`, `values-uk/` `strings.xml`.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "error_scan_timeout"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS (present in EN/RU/UK, locale audit exit 0, tone neutral + retry guidance + no jargon). Added via byte-safe UTF-8 wrapper temp/S0624_add_string.ps1 -> set-android-string.ps1 -Action add. Files: values/values-ru/values-uk strings.xml.

---

### Step 02.4 - Map ScanTimeoutException to the message in Browse

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLoadingAuxManager.kt`
**Depends on:** Step 02.1, Step 02.3

**Prompt for developer:**

> In `resolveFriendlyBrowseErrorRes(throwable)` add a typed branch `if (throwable is com.sza.fastmediasorter.data.network.exceptions.ScanTimeoutException) return R.string.error_scan_timeout` immediately after the existing `WifiRequiredException` type check (`:70`), before the message-substring `when`. This routes the watchdog timeout through the existing `BrowseEvent.ShowError` path (`handleLoadingError` → `getFriendlyBrowseErrorMessage`), which is the app's standard network-error surface (strategic §3.1 "в духе остальных сетевых ошибок"). No new dialog/snackbar wiring - the user retries via the existing pull-to-refresh / resource re-open (the message itself invites a retry, satisfying §3.3 "действие повторить"). The connection-error branch (`:170-178`) already marks the resource unavailable on a "timed out" message, which still applies.

**Verification:**

- `Grep` - `is ScanTimeoutException` and `R.string.error_scan_timeout` both present in `BrowseLoadingAuxManager.kt`.
- The new branch precedes the `val message = throwable.message.orEmpty()` line.

**Status:** `[x] done`

**Step Log:**

- 2026-06-22 - Verification 2/2 PASS (typed branch present, precedes message line). Files: BrowseLoadingAuxManager.kt (+6 LOC).

---

### Step 02.5 - Map ScanTimeoutException to the message in the Player

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** Step 02.1, Step 02.3

**Prompt for developer:**

> In the media-load `catch (e: Exception)` block (`:568-576`) choose the error message by type: `val msgRes = if (e is com.sza.fastmediasorter.data.network.exceptions.ScanTimeoutException) R.string.error_scan_timeout else R.string.player_media_files_load_failed`, and pass `context.getString(msgRes)` to the existing `ShowError` event. Keep the existing `FinishActivity` behaviour - the player cannot list files, so re-opening the resource is the retry path. This converts the former infinite spinner into a bounded, explained close.

**Verification:**

- `Grep` - `is ScanTimeoutException` and `R.string.error_scan_timeout` present in `PlayerMediaFilesLoader.kt`.
- `Grep` - `player_media_files_load_failed` still referenced (non-timeout fallback retained).

**Status:** `[x] done`

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS (typed branch, error_scan_timeout, fallback retained). Files: PlayerMediaFilesLoader.kt (+8 LOC).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL (consolidated code+resources build, phases 01-03 + strings + tags). Adding `ScanTimeoutException` to the sealed hierarchy required an `is ScanTimeoutException` branch in `NetworkErrorMessageMapper.toMessageRes` (central mapper) - added, maps to `error_scan_timeout`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `scripts/check_strings_localized.ps1 -KeyPrefix "error_scan_timeout"` exits 0.
- [~] Dev log entry - batched at ticket finalization.

---

## Handoff Notes to Next Phase

The watchdog is the application backstop; Phase 03's keep-alive should make the JSch session drop a dead transport before the 60 s watchdog ever fires in the common case.

---

## Rollback Plan

Revert the phase commit(s). The new exception subclass, watchdog, and string key are additive; the typed UI branches fall through to prior generic messages if reverted. No data migration or schema change.
