# Phase 02 — Datasource: Per-Protocol Transient Detection

**Strategic spec:** [`../S0066_enh-network-transient-failure-classification-multi-protocol.md`](../S0066_enh-network-transient-failure-classification-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Add `@Volatile var transientFailureReason: TransientReason?` to `NetworkMediaDataSource`. Populate it from per-protocol detectors inside `readAt`. The existing `encounteredStaleShare` flag remains and is set alongside `transientFailureReason = STALE_SHARE` to preserve S0060 semantics. Decoder keeps reading `encounteredStaleShare` for now — Phase 03 will switch it to `transientFailureReason`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `TransientReason` enum compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkMediaDataSource.kt` | Modified | ≤ 500 (current ~443; backup required if it grows past 500) |

> Current size 443 LOC. Projected delta: +30–50 lines (new field + 3 detector helpers). If projected size crosses 500, create a timestamped backup in `temp/` first.

---

## Steps

### Step 02.1 — Add `transientFailureReason` field

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkMediaDataSource.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `NetworkMediaDataSource`, immediately below the existing `encounteredStaleShare` declaration, add:
>
> ```kotlin
> /**
>  * Protocol-agnostic transient-failure reason populated by readAt() detectors. S0066.
>  * Read by NetworkVideoFrameDecoder after extraction to classify the failure as transient
>  * for SMB / SFTP / FTP uniformly. Null = no transient signal observed.
>  */
> @Volatile var transientFailureReason: TransientReason? = null
>     private set
> ```
>
> Add the import `import com.sza.fastmediasorter.data.network.glide.TransientReason` if not already present (same package — import not required, but verify).

**Verification:**

- `Grep` — `var transientFailureReason: TransientReason\?` matches exactly once in `NetworkMediaDataSource.kt`.
- `Grep` — `private set` appears at least once on a line within ±2 lines of the new field.
- `Grep` — `// S0066` or `S0066` appears in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: NetworkMediaDataSource.kt (+8 LOC). Dev log recorded.

---

### Step 02.2 — Per-protocol transient detectors

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkMediaDataSource.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add three private functions to `NetworkMediaDataSource`:
>
> ```kotlin
> private fun classifySftpTransient(e: Throwable): TransientReason? {
>     var current: Throwable? = e
>     var depth = 0
>     while (current != null && depth < 5) {
>         val msg = current.message?.lowercase().orEmpty()
>         val cls = current.javaClass.simpleName
>         if (cls == "TransportException" || msg.contains("transport")) return TransientReason.TRANSPORT
>         if (msg.contains("channel") && msg.contains("closed")) return TransientReason.BROKEN_CHANNEL
>         if (msg.contains("session is down") || msg.contains("session closed")) return TransientReason.BROKEN_CHANNEL
>         if (current is java.net.SocketException) return TransientReason.TRANSPORT
>         current = current.cause
>         depth++
>     }
>     return null
> }
>
> private fun classifyFtpTransient(e: Throwable): TransientReason? {
>     var current: Throwable? = e
>     var depth = 0
>     while (current != null && depth < 5) {
>         val msg = current.message?.lowercase().orEmpty()
>         if (msg.contains("broken pipe") || msg.contains("connection reset")) return TransientReason.BROKEN_PIPE
>         if (current is java.net.SocketException) return TransientReason.TRANSPORT
>         if (msg.contains("replycode=421") || msg.contains("reply='421") ||
>             msg.contains("replycode=426") || msg.contains("reply='426")) return TransientReason.BROKEN_PIPE
>         current = current.cause
>         depth++
>     }
>     return null
> }
>
> private fun classifyTimeoutTransient(e: Throwable): Boolean {
>     return e is java.net.SocketTimeoutException ||
>         e.cause is java.net.SocketTimeoutException ||
>         e.message?.contains("timed out", ignoreCase = true) == true
> }
> ```
>
> Place them adjacent to the existing `isSmbStaleShareError` helper.

**Verification:**

- `Grep` — `private fun classifySftpTransient\(e: Throwable\): TransientReason\?` matches exactly once.
- `Grep` — `private fun classifyFtpTransient\(e: Throwable\): TransientReason\?` matches exactly once.
- `Grep` — `private fun classifyTimeoutTransient\(e: Throwable\): Boolean` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: NetworkMediaDataSource.kt (+34 LOC).

---

### Step 02.3 — Populate `transientFailureReason` from `readAt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkMediaDataSource.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `readAt`, replace the existing exception-handling cascade in the `catch (e: Exception)` block with a protocol-aware version. The new logic must:
>
> 1. Keep the existing `InterruptedException` / `CancellationException` short-circuit and the existing `SocketTimeoutException` log.
> 2. After the cancellation short-circuit, set `transientFailureReason` based on path prefix:
>    - For `path.startsWith("smb://")`: if `isSmbStaleShareError(e)` → also set `transientFailureReason = TransientReason.STALE_SHARE` (in addition to `encounteredStaleShare = true`, which stays).
>    - For `path.startsWith("sftp://")`: `transientFailureReason = classifySftpTransient(e)`.
>    - For `path.startsWith("ftp://")`: `transientFailureReason = classifyFtpTransient(e)`.
> 3. If `classifyTimeoutTransient(e)` is true and `transientFailureReason == null`, set `transientFailureReason = TransientReason.TIMEOUT`.
> 4. Preserve the existing log lines exactly (`failureClass=stale-share`, `Network read timeout`, `Network read interrupted`, generic `Error reading`). Add a single new log line **after** classification for non-SMB transient cases:
>
>    ```kotlin
>    transientFailureReason?.let { reason ->
>        if (!path.startsWith("smb://")) {
>            Timber.w("[scope=thumbnail S0066 protocol=${path.substringBefore("://")} failureClass=$reason] Transient at position=$position: ${e.message}")
>        }
>    }
>    ```
> 5. Always `return -1` at the end of the `catch` block (current behavior).

**Verification:**

- `Grep` — `transientFailureReason = TransientReason.STALE_SHARE` matches exactly once in this file.
- `Grep` — `transientFailureReason = classifySftpTransient\(e\)` matches exactly once.
- `Grep` — `transientFailureReason = classifyFtpTransient\(e\)` matches exactly once.
- `Grep` — `transientFailureReason = TransientReason.TIMEOUT` matches exactly once.
- `Grep` — `\[scope=thumbnail S0066 protocol=` matches at least once.
- `Grep` — `encounteredStaleShare = true` remains in the file (preserve S0060 semantics).
- `Grep -n "Log\.d\("` returns zero hits in this file (Timber-only rule).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 7/7 PASS. Files: NetworkMediaDataSource.kt (+19 LOC).

---

### Step 02.4 — Build gate

**Files:** —
**Depends on:** Steps 02.1–02.3

**Prompt for developer:**

> Run `/build` for `standard debug`. Confirm compilation passes.

**Verification:**

- `/build` skill returns success for `standard debug`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — BUILD SUCCESSFUL (standard debug, 40s, v2.60.5031.801).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `NetworkMediaDataSource.kt`.
- [ ] No regression: `Grep` for `encounteredStaleShare = true` still present (S0060 SMB path preserved).

---

## Handoff Notes to Next Phase

Phase 03 will switch `NetworkVideoFrameDecoder.decode` to read `mediaDataSource.transientFailureReason` instead of `encounteredStaleShare`, generalize `extractSmbServerKey` → `extractNetworkResourceKey`, and update the failure-classification log line to the unified format `[scope=thumbnail protocol=X resource=Y failureClass=Z playbackActive=W]`.

---

## Rollback Plan

Revert the phase commit — `transientFailureReason` is unconsumed by callers until Phase 03; no behavioral change for SMB (encounteredStaleShare logic intact).
