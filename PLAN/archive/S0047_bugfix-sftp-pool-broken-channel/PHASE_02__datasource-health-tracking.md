# Phase 02 — DataSource health tracking

**Strategic spec:** [`../S0047_bugfix-sftp-pool-broken-channel.md`](../S0047_bugfix-sftp-pool-broken-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Make `SftpDataSource` track the health of its loaned channel and pass `(channel, broken)` to the pool on release. After a caught exception in `open()` (post-acquisition), `read()`, or InputStream `close()`, the channel is reported broken so the pool evicts it and the next `open()` opens a fresh channel on the same session.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` | Modified | ≤ 260 |

> Currently 220 LOC; no backup step required.

---

## Steps

### Step 02.1 — Introduce `channelBroken` state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a private mutable property `private var channelBroken: Boolean = false` immediately after the existing `private var connectionAcquired = false` line. The flag stays `false` for the entire lifetime of a healthy DataSource instance and flips to `true` only when an `Exception` is caught in `read()`, in the InputStream `close()` block, or in `open()` after `connectionAcquired` is `true`. Do not reset the flag — `close()` is the terminal call and the DataSource instance is discarded by ExoPlayer afterward.

**Verification:**

- `Grep` — `private var channelBroken: Boolean = false` matches once.
- `Grep` — `private var connectionAcquired = false` still matches once and appears on the line immediately above the new property (read with `-B 0 -A 1`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt (+1 LOC). Dev log recorded.

---

### Step 02.2 — Mark broken in `read()` and InputStream-close catch blocks

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Inside `read(..)` add `channelBroken = true` as the first statement of the existing `catch (e: Exception)` block (currently at the bottom of `read`, before `Timber.e(e, "SftpDataSource: Error reading from SFTP file")`). Inside `close()`, in the `catch (e: Exception)` of the `try { inputStream?.close() }` block, add `channelBroken = true` as the first statement before the existing `Timber.e(e, "SftpDataSource: Error closing InputStream")`. Do not add new logs — the existing Timber.e lines already produce the needed signal.

**Verification:**

- `Grep -n "channelBroken = true" app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` returns at least three hits (one in `read`, one in InputStream-close catch, one in the `open` catch from Step 02.3).
- `Grep -B 1 "Error reading from SFTP file"` shows `channelBroken = true` immediately above.
- `Grep -B 1 "Error closing InputStream"` shows `channelBroken = true` immediately above.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS (count predicate verified jointly with 02.3). Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt (+2 LOC). Dev log recorded.

---

### Step 02.3 — Mark broken in `open()` post-acquisition catch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In the outer `catch (e: Exception)` of `open(dataSpec)`, gate the broken-flag on whether the connection had been acquired by the pool: prepend `if (connectionAcquired) channelBroken = true` to the catch body, before the existing `Timber.e(e, "SftpDataSource: Error opening SFTP file")`. This avoids falsely accusing the pool when failure happened inside `getConnectionForExoPlayer` itself (no channel was ever loaned in that case).

**Verification:**

- `Grep -B 1 "Error opening SFTP file"` shows `if (connectionAcquired) channelBroken = true` immediately above.
- `Grep` — `if \(connectionAcquired\) channelBroken = true` matches exactly once in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt (+1 LOC). Dev log recorded.

---

### Step 02.4 — Pass `(channel, broken)` to pool on release

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `close()`, replace the line `sftpClient.releaseExoPlayerConnection()` with `sftpClient.releaseExoPlayerConnection(channel, channelBroken)`. The call must occur **before** `channel = null` and `session = null` are cleared, otherwise the pool will receive `null` and skip eviction. Reorder the existing block: first capture `val released = channel`, then null out `channel` and `session`, then call `sftpClient.releaseExoPlayerConnection(released, channelBroken)` inside the existing `if (connectionAcquired)` guard. Trigger `/build` after the change to confirm types.

**Verification:**

- `Grep -n "sftpClient\.releaseExoPlayerConnection" app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` returns exactly one hit, and that hit is `sftpClient.releaseExoPlayerConnection(released, channelBroken)`.
- `Grep -n "val released = channel"` matches once.
- `Grep -n "Log\.d\("` returns zero hits in this file.
- `/build` reports `BUILD SUCCESSFUL` for the standard debug variant.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 4/4 PASS. assembleStandardDebug → BUILD SUCCESSFUL in 11s. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt (+1 LOC, ~1 modified). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build` clean for debug.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `SftpDataSource.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] Quick lint sweep: no new warnings in `SftpDataSource.kt` introduced by this phase (resolve any reported by Android Studio inspector for the touched lines).

---

## Handoff Notes to Next Phase

- The DataSource → pool contract is now: every release names the loaned channel; broken flag is true iff an exception was caught after the channel was loaned. Healthy releases (normal EOF, normal close-before-EOF on track switch) keep the channel pooled.
- No code path outside `SftpDataSource` calls `releaseExoPlayerConnection` today; verified by `Grep` in Step 01.4. Phase 03 will lock that down via the catalog snapshot.

---

## Rollback Plan

Revert phase commits. Phase 01 default parameters keep the prior behavior intact: the original `releaseExoPlayerConnection()` no-arg call still resolves to "release semaphore + cleanup" with no eviction.
