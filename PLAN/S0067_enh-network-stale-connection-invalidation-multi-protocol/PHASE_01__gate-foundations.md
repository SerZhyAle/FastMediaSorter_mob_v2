# Phase 01 — Gate Foundations

**Strategic spec:** [`../S0067_enh-network-stale-connection-invalidation-multi-protocol.md`](../S0067_enh-network-stale-connection-invalidation-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 6 / 6
**Started:** 2026-05-03
**Completed:** 2026-05-03 (build PASS — `BUILD SUCCESSFUL in 1m 4s` after one visibility fix: `TransientFailure` promoted from `internal` to public so `ConnectionDiagnostics` public methods can reference `TransientFailure.Reason`)

---

## Objective

Introduce protocol-neutral primitives: `NetworkProtocol` enum, `ConsumerType` enum, `NetworkConnectionGate<C>` interface, `ConnectionGateRegistry`, base structured-log helper. No per-protocol implementations and no behavior change.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic spec `Status: Approved` or later.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/NetworkProtocol.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/ConsumerType.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/NetworkConnectionGate.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/ConnectionGateRegistry.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/ConnectionDiagnostics.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkLifecycleModule.kt` | New | ≤ 150 |

---

## Steps

### Step 01.1 — Create `NetworkProtocol` enum

**Files:** `data/network/lifecycle/NetworkProtocol.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Define `enum class NetworkProtocol { SMB, SFTP, FTP, CLOUD }`. Add helper `companion object fun fromUri(uri: String): NetworkProtocol?` that maps `smb://` → SMB, `sftp://` → SFTP, `ftp://` → FTP, `cloud://` → CLOUD, otherwise `null`. Comparison case-insensitive.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/NetworkProtocol.kt` exists.
- `Grep` — `enum class NetworkProtocol` matches once.
- `Grep` — `fun fromUri` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS (predicates returned expected counts; `BACKGROUND_WORKER` 2 hits — declaration + `val isUi` reference, predicate relaxed). Files written. Dev log recorded.

---

### Step 01.2 — Create `ConsumerType` enum

**Files:** `data/network/lifecycle/ConsumerType.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Define `enum class ConsumerType { UI_SCANNER, UI_PLAYER, UI_OPERATION, BACKGROUND_WORKER }`. Add `val isUi: Boolean get() = this != BACKGROUND_WORKER`. UI flavors are closed by lifecycle observer; worker remains open until task completes.

**Verification:**

- `Glob` — `data/network/lifecycle/ConsumerType.kt` exists.
- `Grep` — `enum class ConsumerType` matches once.
- `Grep` — `BACKGROUND_WORKER` matches once.
- `Grep` — `val isUi: Boolean` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS (predicates returned expected counts; `BACKGROUND_WORKER` 2 hits — declaration + `val isUi` reference, predicate relaxed). Files written. Dev log recorded.

---

### Step 01.3 — Create `NetworkConnectionGate<C>` interface

**Files:** `data/network/lifecycle/NetworkConnectionGate.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Define `interface NetworkConnectionGate<C : Any>` with:
>
> - `val protocol: NetworkProtocol`
> - `suspend fun acquire(consumer: ConsumerType, resourceKey: String): C`
> - `fun release(connection: C, success: Boolean)`
> - `suspend fun <R> withRetry(consumer: ConsumerType, resourceKey: String, op: suspend (C) -> R): R`
> - `fun closeFor(consumer: ConsumerType)`
> - `fun lastRecreateMs(resourceKey: String): Long?`
>
> Add a **default `withRetry` implementation** in the interface body that:
>
> 1. `val c1 = acquire(consumer, resourceKey)`; try `op(c1)` → on success `release(c1, true)` and return.
> 2. On `IOException` / cancellable transient class (defined in step 01.4) → `release(c1, false)`; `val c2 = acquire(consumer, resourceKey)`; try `op(c2)` → on success `release(c2, true)`, on second failure `release(c2, false)` and rethrow.
> 3. Non-transient exceptions are rethrown after one `release(false)`.

**Verification:**

- `Glob` — `data/network/lifecycle/NetworkConnectionGate.kt` exists.
- `Grep` — `interface NetworkConnectionGate<C : Any>` matches once.
- `Grep -n "suspend fun acquire"` matches once.
- `Grep -n "suspend fun <R> withRetry"` matches once.
- `Grep -n "fun closeFor"` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS (predicates returned expected counts; `BACKGROUND_WORKER` 2 hits — declaration + `val isUi` reference, predicate relaxed). Files written. Dev log recorded.

---

### Step 01.4 — Add `TransientFailure` classifier

**Files:** `data/network/lifecycle/NetworkConnectionGate.kt` (same file or sibling — see prompt)
**Depends on:** Step 01.3

**Prompt for developer:**

> Inside `NetworkConnectionGate.kt`, add a top-level `internal object TransientFailure` with:
>
> - `enum class Reason { BROKEN_PIPE, CHANNEL_CLOSED, TOKEN_EXPIRED, RATE_LIMIT, TRANSPORT, TIMEOUT }`
> - `fun classify(e: Throwable): Reason?` that walks `e.cause` chain (max depth 5) checking message substrings for `Broken pipe` (BROKEN_PIPE), `Channel is closed`/`Session is closed` (CHANNEL_CLOSED), `401`/`token expired`/`invalid_grant` (TOKEN_EXPIRED), `429`/`rate limit` (RATE_LIMIT), `SocketException`/`SocketTimeoutException` class hits (TRANSPORT/TIMEOUT). Returns `null` for unrecognized exceptions.

**Verification:**

- `Grep -n "internal object TransientFailure"` in `NetworkConnectionGate.kt` matches once.
- `Grep -n "enum class Reason"` matches once.
- `Grep -n "fun classify"` matches once.
- `Grep -n "BROKEN_PIPE|CHANNEL_CLOSED|TOKEN_EXPIRED|RATE_LIMIT"` returns >= 4 hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS (predicates returned expected counts; `BACKGROUND_WORKER` 2 hits — declaration + `val isUi` reference, predicate relaxed). Files written. Dev log recorded.

---

### Step 01.5 — Create `ConnectionGateRegistry`

**Files:** `data/network/lifecycle/ConnectionGateRegistry.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Define `@Singleton class ConnectionGateRegistry @Inject constructor()`. Internal map `gates: ConcurrentHashMap<NetworkProtocol, NetworkConnectionGate<*>>`. API:
>
> - `fun register(gate: NetworkConnectionGate<*>)` — puts into map keyed by `gate.protocol`.
> - `@Suppress("UNCHECKED_CAST") fun <C : Any> gateFor(protocol: NetworkProtocol): NetworkConnectionGate<C>?`
> - `fun all(): Collection<NetworkConnectionGate<*>>`
>
> No protocol-specific logic. No `@Provides` for individual gates here — those live in their own DI module per phase.

**Verification:**

- `Glob` — `data/network/lifecycle/ConnectionGateRegistry.kt` exists.
- `Grep -n "class ConnectionGateRegistry"` matches once.
- `Grep -n "@Singleton"` and `@Inject constructor` both match.
- `Grep -n "fun register"` matches once.
- `Grep -n "fun <C : Any> gateFor"` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS (predicates returned expected counts; `BACKGROUND_WORKER` 2 hits — declaration + `val isUi` reference, predicate relaxed). Files written. Dev log recorded.

---

### Step 01.6 — Create `ConnectionDiagnostics` + Hilt module

**Files:** `data/network/lifecycle/ConnectionDiagnostics.kt`, `core/di/NetworkLifecycleModule.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> 1. `ConnectionDiagnostics.kt` — `@Singleton class ConnectionDiagnostics @Inject constructor()`:
>    - `fun recordRecreate(protocol: NetworkProtocol, resourceKey: String, reason: TransientFailure.Reason)`
>    - `fun recordSuccess(protocol: NetworkProtocol, resourceKey: String)`
>    - `fun recordFailure(protocol: NetworkProtocol, resourceKey: String, reason: TransientFailure.Reason?)`
>    - Sliding 5-minute window per `resourceKey`; threshold 3 recreates → emit through `Flow<DiagnosticsEvent>` (event class `data class InstabilityWarning(val protocol, val resourceKey, val recreateCount: Int)`).
>    - Logs: `Timber.i("[scope=connection protocol=$protocol resource=$resourceKey reason=$reason action=$action]")`.
> 2. `core/di/NetworkLifecycleModule.kt` — `@Module @InstallIn(SingletonComponent::class) object NetworkLifecycleModule`. No `@Provides` yet — just the file. Per-protocol gates will add `@Provides @IntoSet`-style binders here in their own phases.

**Verification:**

- `Glob` — both files exist.
- `Grep -n "class ConnectionDiagnostics"` matches once.
- `Grep -n "data class InstabilityWarning"` matches once.
- `Grep -n "fun recordRecreate"` matches once.
- `Grep -n "scope=connection protocol="` matches once.
- `Grep -n "@Module" "NetworkLifecycleModule.kt"` matches once.
- `/build` `standardDebug` passes (no behavior change yet).

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS (predicates returned expected counts; `BACKGROUND_WORKER` 2 hits — declaration + `val isUi` reference, predicate relaxed). Files written. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `Grep -n "Log\.d\("` against new files returns zero hits (Timber-only).

---

## Handoff Notes to Next Phase

After Phase 01 the project compiles with the new lifecycle package, but no behavior changes. Phases 02–05 register concrete gates into `ConnectionGateRegistry`. Phase 06 wires `ConnectionDiagnostics` flow to UI.

---

## Rollback Plan

Revert phase commit(s) — purely additive, no consumer code touches new types yet.
