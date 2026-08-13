# Phase 03 — Unified Single-Retry Policy

**Strategic spec:** [`../S0061_bugfix-smb-stale-connection-invalidation.md`](../S0061_bugfix-smb-stale-connection-invalidation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Consolidate the retry decision into a single private helper inside `SmbConnectionManager`. Remove the duplicate ad-hoc retry loop in `getConnectionForExoPlayer`. Remove the misleading "retrying with longer timeout" log from the Broken-pipe path (the longer timeout does not help when the error is instant). Both consumers (suspend and blocking) end up with a single, predictable "one retry with a freshly purged cache" policy.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Backup of `SmbConnectionManager.kt` placed under `temp/` with timestamp.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 950 |

---

## Steps

### Step 03.1 — Backup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy current `SmbConnectionManager.kt` to `temp/SmbConnectionManager.kt.<YYYYMMDD_HHmmss>.phase03.backup` before edits.

**Verification:**

- `Glob` — `temp/SmbConnectionManager.kt.*.phase03.backup` returns at least one match.

**Status:** `[ ]` not done

---

### Step 03.2 — Introduce `acquireFreshWithRetry`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a private function:
>
> ```kotlin
> private fun acquireFreshWithRetry(
>     connectionInfo: SmbConnectionInfo,
>     consumer: ConnectionConsumer,
>     allowRetry: Boolean
> ): PooledConnection
> ```
>
> Behavior: build the `ConnectionKey` from `connectionInfo`. Loop up to `if (allowRetry) 2 else 1` attempts. Each attempt: get the appropriate `SMBClient` via `getClient`, call `client.connect(host, port)`, authenticate, `connectShare`, build `PooledConnection(consumer = consumer)`, store via `pool.put(key, ...)`, return. On catch:
> - If `healthProbe.classify(e) in [BROKEN_PIPE, SERVER_RESET, SOCKET_CLOSED]` AND attempt < max → call `purgeClientForHost(host, port)`, log `Timber.i("SMB connect dead, reason=$reason — purging cache and retrying once")`, `continue`.
> - If `isNonRetriableConnectionError(e)` → throw immediately.
> - Otherwise on last attempt → throw wrapped `IOException`.
>
> The function MUST NOT contain the misleading `"retrying with longer timeout"` log. The "longer timeout" tier is a separate decision (see Step 03.4).

**Verification:**

- `Grep` — `private fun acquireFreshWithRetry` matches exactly once in `SmbConnectionManager.kt`.
- `Grep` -A 30 `private fun acquireFreshWithRetry` shows `purgeClientForHost`, `healthProbe.classify`, `pool.put`.
- `Grep` — `retrying with longer timeout` returns zero hits.

**Status:** `[ ]` not done

---

### Step 03.3 — Refactor `getConnectionForExoPlayer` to delegate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `getConnectionForExoPlayer()` (line ~887), after the pooled-reuse check (which already uses `healthProbe.isAlive` from Phase 02) and the stale-eviction step (line ~914), REPLACE the entire `for (attempt in 1..2)` block (lines ~928-976) with a single call:
>
> ```kotlin
> return acquireFreshWithRetry(connectionInfo, ConnectionConsumer.PLAYER, allowRetry = true)
> ```
>
> Wrap in try/catch to convert thrown `IOException` into the same `throw IOException("Failed to connect to SMB: ...", lastException)` shape callers expect. Do not change the public signature.

**Verification:**

- `Grep` — `for (attempt in 1..2)` returns zero hits in `SmbConnectionManager.kt`.
- `Grep` — `acquireFreshWithRetry(connectionInfo, ConnectionConsumer.PLAYER` matches in `getConnectionForExoPlayer`.
- The function signature `fun getConnectionForExoPlayer(connectionInfo: SmbConnectionInfo): PooledConnection` is unchanged (`Grep` matches exactly once).

**Status:** `[ ]` not done

---

### Step 03.4 — Simplify `withConnection` retry path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `withConnection()` (line ~267) the inner `while (freshConnectionAttempts < maxAttempts)` loop calls `createFreshConnection()` which already gained a `purgeClientForHost` side-effect in Phase 02. Keep the loop but: (1) replace the misleading `Timber.w("Fresh connection attempt $freshConnectionAttempts failed, retrying with longer timeout")` (line ~387) with `Timber.i("SMB fresh-connect attempt $freshConnectionAttempts failed (reason=${healthProbe.classify(e)}) — retrying")`; (2) keep the "longer timeout" semantics by passing `useDegradedTimeout = freshConnectionAttempts > 1 && allowRetry` to `createFreshConnection` (already correct — preserve as is); (3) pass `consumer = ConnectionConsumer.SCANNER` to a new `consumer` parameter in `createFreshConnection` so the pooled entry is tagged correctly. Update `createFreshConnection` signature to accept `consumer` (default to `SCANNER` for backwards compat).

**Verification:**

- `Grep` — `retrying with longer timeout` returns zero hits in `SmbConnectionManager.kt`.
- `Grep` — `SMB fresh-connect attempt` matches.
- `Grep` — `private suspend fun createFreshConnection` shows `consumer:` parameter.
- `Grep` — `createFreshConnection(\s*connectionInfo` calls all pass either `consumer = ConnectionConsumer.SCANNER` explicitly or rely on the default.

**Status:** `[ ]` not done

---

### Step 03.5 — Build gate

**Files:** none
**Depends on:** Step 03.4

**Prompt for developer:**

> Run `/build` → standard debug. Build must pass.

**Verification:**

- `/build` standard debug returns PASS.
- `Grep` — `TODO(phase-03)` returns zero hits in `app_v2/src/main/`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug PASS.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `acquireFreshWithRetry` is the single retry method on the blocking path.
- [ ] `withConnection` retry path no longer logs the misleading "longer timeout" message.

---

## Handoff Notes to Next Phase

After this phase: a single retry policy lives in `acquireFreshWithRetry`; `getConnectionForExoPlayer` is short and delegates; `withConnection` keeps the suspend semantics with a clean retry log. Phase 04 will add lifecycle-aware closing on background.

---

## Rollback Plan

Revert phase commits — Phase 02 fix is preserved (health probe + purge stays effective even with the old retry shape). The unification is a structural refactor; rollback returns to Phase-02 behavior, which is still correct, just less DRY.
