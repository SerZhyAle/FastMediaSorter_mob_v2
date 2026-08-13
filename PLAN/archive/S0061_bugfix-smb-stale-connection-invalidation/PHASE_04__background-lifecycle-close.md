# Phase 04 — Background Lifecycle Close

**Strategic spec:** [`../S0061_bugfix-smb-stale-connection-invalidation.md`](../S0061_bugfix-smb-stale-connection-invalidation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

When the app moves to background (`ProcessLifecycleOwner.onStop()`), close all UI-owned SMB connections (PLAYER + SCANNER tags) so the server-side idle FIN never has the chance to leave a half-open socket in the cache. Connections opened by long-running WorkManager workers (a new `BACKGROUND_WORKER` consumer tag) are preserved until the worker completes.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Backup of `FastMediaSorterApp.kt` placed under `temp/` with timestamp.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbBackgroundLifecycleManager.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionPool.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 950 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 540 |

---

## Steps

### Step 04.1 — Backup `FastMediaSorterApp.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy current `FastMediaSorterApp.kt` to `temp/FastMediaSorterApp.kt.<YYYYMMDD_HHmmss>.phase04.backup` before edits.

**Verification:**

- `Glob` — `temp/FastMediaSorterApp.kt.*.phase04.backup` returns at least one match.

**Status:** `[ ]` not done

---

### Step 04.2 — Extend `ConnectionConsumer` with `BACKGROUND_WORKER`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionPool.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a third variant `BACKGROUND_WORKER` to the `enum class ConnectionConsumer` introduced in Phase 01. Add a public method `fun closeAllExceptWorker()` to `SmbConnectionPool` that iterates `snapshot()` and calls `removeAndCloseAsync(key)` for every entry whose `consumer != ConnectionConsumer.BACKGROUND_WORKER`.

**Verification:**

- `Grep` — `BACKGROUND_WORKER` matches in `SmbConnectionPool.kt`.
- `Grep` — `fun closeAllExceptWorker` matches exactly once in `SmbConnectionPool.kt`.
- `Grep` -A 5 `fun closeAllExceptWorker` shows `consumer != ConnectionConsumer.BACKGROUND_WORKER`.

**Status:** `[ ]` not done

---

### Step 04.3 — Expose `closeUiConnections()` on manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add a public method `fun closeUiConnections()` to `SmbConnectionManager` that calls `pool.closeAllExceptWorker()` and logs `Timber.i("SMB UI connections closed (lifecycle: background)")`. Do NOT call `resetClients()` — clients are cheap to recreate but workers may be using them concurrently. The method MUST be safe to call from the main thread.

**Verification:**

- `Grep` — `fun closeUiConnections` matches exactly once in `SmbConnectionManager.kt`.
- `Grep` -A 3 `fun closeUiConnections` shows `pool.closeAllExceptWorker()` and `Timber.i`.

**Status:** `[ ]` not done

---

### Step 04.4 — Create `SmbBackgroundLifecycleManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbBackgroundLifecycleManager.kt` (New)
**Depends on:** Step 04.3

**Prompt for developer:**

> Create a new `@Singleton` class `SmbBackgroundLifecycleManager` with `@Inject constructor(private val smbConnectionManager: SmbConnectionManager)`. Implements `androidx.lifecycle.DefaultLifecycleObserver`. Override `onStop(owner: LifecycleOwner)` to call `smbConnectionManager.closeUiConnections()`. Registration is done directly from `FastMediaSorterApp.onCreate()` via `ProcessLifecycleOwner.get().lifecycle.addObserver(smbBackgroundLifecycleManager)` — no separate `attach()` helper needed.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbBackgroundLifecycleManager.kt` exists.
- `Grep` — `class SmbBackgroundLifecycleManager` matches exactly once in that file.
- `Grep` — `: DefaultLifecycleObserver` present.
- `Grep` — `override fun onStop` present.
- `Grep` — `smbConnectionManager.closeUiConnections()` present.
- `Grep` — `@Inject constructor` and `@Singleton` annotations both present.
- `Grep` -n `Log\.d\(` returns zero hits.

**Status:** `[ ]` not done

---

### Step 04.5 — Wire lifecycle manager into `FastMediaSorterApp.onCreate`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> In `FastMediaSorterApp` (line ~105 already has `ProcessLifecycleOwner.get().lifecycle.addObserver(...)` for the existing `isInForeground` flag — keep it), add `@Inject lateinit var smbBackgroundLifecycleManager: SmbBackgroundLifecycleManager`. In `onCreate()`, immediately after the existing observer registration, call `ProcessLifecycleOwner.get().lifecycle.addObserver(smbBackgroundLifecycleManager)`. The two observers operate on the same lifecycle independently.

**Verification:**

- `Grep` — `lateinit var smbBackgroundLifecycleManager: SmbBackgroundLifecycleManager` matches in `FastMediaSorterApp.kt`.
- `Grep` — `addObserver(smbBackgroundLifecycleManager)` matches in `FastMediaSorterApp.kt`.
- `Grep` -n `Log\.d\(` returns zero hits in `FastMediaSorterApp.kt` (no regression on Timber-only rule).

**Status:** `[ ]` not done

---

### Step 04.6 — Build gate

**Files:** none
**Depends on:** Step 04.5

**Prompt for developer:**

> Run `/build` → standard debug. Build must pass.

**Verification:**

- `/build` standard debug returns PASS.
- `Grep` — `TODO(phase-04)` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug PASS.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] `BACKGROUND_WORKER` enum variant exists and `closeAllExceptWorker` is called only from `closeUiConnections`.

---

## Handoff Notes to Next Phase

After this phase: app background → UI connections close; worker connections preserved. Future workers that want preserved connections can pass `consumer = ConnectionConsumer.BACKGROUND_WORKER` (no current worker is updated as part of this spec — that's a follow-up if needed). The hot reconnect path still relies on Phase 02 health probe and Phase 03 retry policy.

---

## Rollback Plan

Revert phase commits — lifecycle observer is additive; removing it returns to "connections never close on background" behavior, which is the pre-fix state. No data migration.
