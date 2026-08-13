# Phase 03 - Reconnect guard on the streaming data source

**Strategic spec:** [`../S1055_sftp-reconnect-failure-classification.md`](../S1055_sftp-reconnect-failure-classification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 1 / 1
**Started:** -
**Completed:** 2026-07-15

---

## Objective

Stop `SftpDataSource` from attempting a transparent reconnect when the read failure classifies as a non-transient auth-reject or host-key-change, and propagate the typed cause instead (Столп D).

---

## Prerequisites

- [ ] Phase 02 ✅ Done (classifier produces the typed non-transient outcomes).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` | Modified | ≤ 360 |

---

## Steps

### Step 03.1 - Skip reconnect on a classified non-transient read failure

**Files:** `data/network/datasource/SftpDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `read()`, inside the generic `catch (e: Exception)` block that currently calls `reconnectStream(...)`, first classify the throwable via `NetworkErrorClassifier.classifySilently(e)`. If the classified type is `NetworkHostKeyChangedException` or `NetworkAccessDeniedException`, do NOT reconnect: mark `channelBroken = true` and rethrow the classified exception wrapped in an `IOException` (so ExoPlayer surfaces a terminal error rather than retrying a doomed reconnect). Keep the existing single transparent reconnect for every other (transient) failure exactly as-is. Do not touch the `InterruptedIOException` branch above it. Keep the added log line `<= 120` chars and at `Timber.w`.

**Verification:**

- `Grep` - `classifySilently` referenced once in `SftpDataSource.kt`.
- `Grep` - `NetworkHostKeyChangedException` referenced in the `read()` guard.
- `/build` standard debug compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] `Step 03.1` is `[x] done`.
- [ ] Project compiles - run `/build` standard debug.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `SftpDataSource.kt`.

---

## Handoff Notes to Next Phase

All live SFTP paths now propagate a typed non-transient exception without a futile reconnect. Phase 04 locks the classifier behaviour in unit tests.

---

## Rollback Plan

Revert the phase commit - the data source reverts to unconditional single reconnect; no persisted state touched.
