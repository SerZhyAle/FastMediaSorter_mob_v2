# Phase 05 — Diagnostic Channel & Reconnect Metric

**Strategic spec:** [`../S0061_bugfix-smb-stale-connection-invalidation.md`](../S0061_bugfix-smb-stale-connection-invalidation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** —
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Replace the current flood of identical `Broken pipe` stack traces with a single structured INFO line per dead-connection event (already started in Phase 02; Phase 05 finishes it). Add a session-scoped reconnect counter; on > 3 reconnects to the same server within 5 minutes, fire the existing `SmbResetCallback` with a user-visible message ("network unstable — check Wi-Fi"). Add trilingual strings.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 950 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

---

## Steps

### Step 05.1 — Add reconnect-counter state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add private fields:
>
> ```kotlin
> private val reconnectTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
> private val RECONNECT_WINDOW_MS = 5 * 60 * 1000L
> private val RECONNECT_THRESHOLD = 3
> ```
>
> Add a private function `recordReconnect(server: String)` that appends `System.currentTimeMillis()` to the per-server list, prunes entries older than `RECONNECT_WINDOW_MS`, and returns the current list size. Add a private function `maybeNotifyUnstableNetwork(server: String, count: Int)` that calls `resetCallback?.onAutoReset(applicationContext.getString(R.string.smb_network_unstable))` ONLY if `count >= RECONNECT_THRESHOLD` AND time since last `onAutoReset` ≥ `RECONNECT_WINDOW_MS` (use a per-server `AtomicLong` to debounce).

**Verification:**

- `Grep` — `private val reconnectTimestamps` matches in `SmbConnectionManager.kt`.
- `Grep` — `RECONNECT_WINDOW_MS` matches.
- `Grep` — `private fun recordReconnect` matches exactly once.
- `Grep` — `private fun maybeNotifyUnstableNetwork` matches exactly once.

**Status:** `[ ]` not done

---

### Step 05.2 — Hook counter into `purgeClientForHost`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Inside `purgeClientForHost(host, port)` (added in Phase 02), after the existing log line, call `val n = recordReconnect(host); maybeNotifyUnstableNetwork(host, n)`.

**Verification:**

- `Grep` -A 12 `private fun purgeClientForHost` shows both `recordReconnect(` and `maybeNotifyUnstableNetwork(`.

**Status:** `[ ]` not done

---

### Step 05.3 — Suppress redundant stack traces

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Find every `Timber.e(e, ...)` / `Timber.w(e, ...)` call in `SmbConnectionManager.kt` whose message refers to "Broken pipe" or transport failure on a path where Phase 02 already emits a structured `Timber.i("SMB connect dead, reason=...")`. Replace those redundant `Timber.e(e, ...)` with `Timber.i("...")` (no exception attached) so the stack trace is not duplicated for the SAME event. Keep `Timber.e(e, ...)` only on the FINAL failure path (after retries exhausted). Specifically: in `getConnectionForExoPlayer` the line `Timber.e(e, "Failed to create connection for ExoPlayer (attempt $attempt)")` (line ~973) — change to `Timber.w("SMB ExoPlayer connect failed (attempt $attempt, reason=${healthProbe.classify(e)})")`.

**Verification:**

- `Grep` — `Failed to create connection for ExoPlayer` returns zero hits in `SmbConnectionManager.kt` (replaced).
- `Grep` — `SMB ExoPlayer connect failed` matches.
- `Grep` -n `Timber\.e\(e,` in `SmbConnectionManager.kt` shows only the FINAL `handleFreshConnectionFailure` path emitting a stack — no duplicate per-attempt `Timber.e(e, ...)`.

**Status:** `[ ]` not done

---

### Step 05.4 — Add trilingual strings

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a single string resource `smb_network_unstable` to all three files:
>
> - EN: `Network unstable — please check Wi-Fi.`
> - RU: `Сеть нестабильна.. проверьте Wi-Fi.`
> - UK: `Мережа нестабільна.. перевірте Wi-Fi.`
>
> Note: per project author-style rule, use `..` instead of `...` in RU and UK strings. Always use `ё` where grammatically correct (none in this exact string).

**Verification:**

- `Grep` — `name="smb_network_unstable"` matches exactly once in each of the three `strings.xml` files (3 hits total).
- `Grep` — `Network unstable` matches in `app_v2/src/main/res/values/strings.xml`.
- `Grep` — `Сеть нестабильна\.\.` matches in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` — `Мережа нестабільна\.\.` matches in `app_v2/src/main/res/values-uk/strings.xml`.

**Status:** `[ ]` not done

---

### Step 05.5 — Build gate

**Files:** none
**Depends on:** Step 05.4

**Prompt for developer:**

> Run `/build` → standard debug. Build must pass.

**Verification:**

- `/build` standard debug returns PASS.
- `Grep` — `TODO(phase-05)` returns zero hits in `app_v2/src/main/`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug PASS.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Trilingual `smb_network_unstable` resource exists in all three locales.

---

## Handoff Notes to Next Phase

After this phase: a single structured INFO line per dead-connection event; reconnect rate is monitored; user gets a single tost-style notification at unstable-network threshold. Phase 06 finalizes documentation and catalog.

---

## Rollback Plan

Revert phase commits — diagnostic-only changes, no behavioral risk.
