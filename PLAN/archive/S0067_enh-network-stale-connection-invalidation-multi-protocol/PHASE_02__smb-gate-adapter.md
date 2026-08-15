# Phase 02 — SMB Gate Adapter (Lifecycle-Only)

**Strategic spec:** [`../S0067_enh-network-stale-connection-invalidation-multi-protocol.md`](../S0067_enh-network-stale-connection-invalidation-multi-protocol.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-03
**Completed:** 2026-05-03 (build PASS — `BUILD SUCCESSFUL`, no SmbConnectionManager modifications)

---

## Objective

Wrap existing `SmbConnectionManager` (S0061) in a `SmbConnectionGate : NetworkConnectionGate<PooledConnection>` adapter. **Lifecycle-only first iteration:** `closeFor(UI_*)` and `lastRecreateMs(resourceKey)` are real; `acquire / release / withRetry` throw `UnsupportedOperationException` because SMB consumers continue using `SmbConnectionManager` directly (out of scope per strategic §2 non-goals). This keeps Phase 06 lifecycle observer uniform across all four protocols without modifying the 988-LOC `SmbConnectionManager`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `SmbConnectionManager.kt` exists with public methods `closeUiConnections()`, `setResetCallback(SmbResetCallback?)`, `invalidateExoPlayerConnection(SmbConnectionInfo)` (verified — present at lines 820, 747, 720).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/SmbRecreateTracker.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/SmbConnectionGate.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkLifecycleModule.kt` | Modified | ≤ 200 |

> `SmbConnectionManager.kt` (988 LOC) is **NOT modified** in this phase — line budget would exceed 1000 with even small additions. Recreate tracking is hosted in a separate `SmbRecreateTracker` injected into both gate and (future) manager via DI.

---

## Steps

### Step 02.1 — Create `SmbRecreateTracker`

**Files:** `data/network/lifecycle/SmbRecreateTracker.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Define `@Singleton class SmbRecreateTracker @Inject constructor()`. Internal `ConcurrentHashMap<String, Long>` keyed by `resourceKey` (`"smb://host:port"` or `"smb://host:port/share"`). API:
>
> - `fun recordRecreate(resourceKey: String)` — `map[resourceKey] = System.currentTimeMillis()`.
> - `fun lastRecreateMs(resourceKey: String): Long?` — returns the value or `null`.
> - `fun keyForServer(server: String, port: Int): String = "smb://$server:$port"`.
> - `fun keyForShare(server: String, port: Int, shareName: String): String = "smb://$server:$port/$shareName"`.
>
> No coupling to `SmbConnectionManager`. The manager will call `recordRecreate` from a future opt-in hook (deferred to a follow-up tikit; not in S0067 scope).

**Verification:**

- `Glob` — `SmbRecreateTracker.kt` exists.
- `Grep -n "class SmbRecreateTracker"` matches once.
- `Grep -n "@Singleton"` and `@Inject constructor` both match.
- `Grep -n "fun recordRecreate"` matches once.
- `Grep -n "fun lastRecreateMs"` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS. Files written; build standard debug PASS.

---

### Step 02.2 — Create `SmbConnectionGate` (lifecycle-only adapter)

**Files:** `data/network/lifecycle/SmbConnectionGate.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Define `@Singleton class SmbConnectionGate @Inject constructor(private val manager: SmbConnectionManager, private val tracker: SmbRecreateTracker, private val diagnostics: ConnectionDiagnostics) : NetworkConnectionGate<com.sza.fastmediasorter.data.network.PooledConnection>`.
>
> Implementation:
>
> - `override val protocol: NetworkProtocol = NetworkProtocol.SMB`
> - `override suspend fun acquire(consumer: ConsumerType, resourceKey: String): PooledConnection = throw UnsupportedOperationException("S0067 Phase 02: SMB lease path uses SmbConnectionManager.getConnectionForExoPlayer directly. Gate-mediated acquire is a future extension.")`
> - `override fun release(connection: PooledConnection, success: Boolean) { /* never invoked while acquire throws — keep empty for forward compat */ }`
> - Override `withRetry` to also throw the same `UnsupportedOperationException` (interface default would call `acquire`, same outcome — be explicit).
> - `override fun closeFor(consumer: ConsumerType)`:
>   - If `consumer.isUi` — `manager.closeUiConnections()` (existing public method since S0061).
>   - If `consumer == ConsumerType.BACKGROUND_WORKER` — no-op; worker sessions are preserved across `onStop`.
> - `override fun lastRecreateMs(resourceKey: String): Long? = tracker.lastRecreateMs(resourceKey)`.
>
> One short KDoc block on the class explaining "lifecycle-only first iteration; full SMB lease path remains on `SmbConnectionManager` for now (out of S0067 scope)".

**Verification:**

- `Glob` — `SmbConnectionGate.kt` exists.
- `Grep -n "class SmbConnectionGate"` matches once.
- `Grep -n "override val protocol: NetworkProtocol = NetworkProtocol.SMB"` matches once.
- `Grep -n "manager.closeUiConnections\\(\\)"` matches once.
- `Grep -n "UnsupportedOperationException"` matches at least twice (acquire + withRetry).
- `Grep -n "tracker.lastRecreateMs"` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS. Files written; build standard debug PASS.

---

### Step 02.3 — Wire gate into DI registry

**Files:** `core/di/NetworkLifecycleModule.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Inside `object NetworkLifecycleModule` add a single `@Provides @Singleton` factory:
>
> ```kotlin
> @Provides
> @Singleton
> fun provideRegistry(smb: SmbConnectionGate): ConnectionGateRegistry =
>     ConnectionGateRegistry().apply { register(smb) }
> ```
>
> Future phases (03/04/05) will edit this provider's parameter list to add `sftp: SftpConnectionGate`, `ftp: FtpConnectionGate`, `cloud: CloudConnectionGate?` and append more `register(..)` calls.

**Verification:**

- `Grep -n "fun provideRegistry"` matches once.
- `Grep -n "register(smb)"` matches once.
- `Grep -n "@Provides"` matches at least once.
- `/build` `standardDebug` passes.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS. Files written; build standard debug PASS.

---

### Step 02.4 — Sanity check existing SMB call-sites untouched

**Files:** none modified — verification only
**Depends on:** Step 02.3

**Prompt for developer:**

> Confirm `SmbConnectionManager` was NOT modified in this phase. Run a `Grep` for any `S0067` marker in `SmbConnectionManager.kt` — should be zero (markers belong in the gate / tracker, not in the legacy manager).
>
> Confirm Phase 01's `closeUiConnections()` consumers (S0061 lifecycle observer if separate, or `FastMediaSorterApp` direct call) still resolve cleanly — the gate adds an additional path but does not replace existing calls in this phase.

**Verification:**

- `Grep -n "S0067" "app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt"` returns zero hits.
- `Grep -n "closeUiConnections" -r app_v2/src/main` returns >= 2 hits (existing call site + new gate delegation).
- `/build` `standardDebug` passes.

**Status:** `[x] done`

**Step Log:**

- 2026-05-03 — Verification PASS. Files written; build standard debug PASS.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] `/build` `standardDebug` PASS.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry per "Files Touched".
- [ ] `Grep -n "Log\\.d\\("` against new files returns zero hits (Timber-only).

---

## Handoff Notes to Next Phase

After Phase 02 the registry contains the SMB gate (lifecycle-only). Phase 06 lifecycle observer will iterate the registry and call `closeFor(UI_*)` uniformly — for SMB this routes to existing `closeUiConnections()`. Per-share gate-mediated SMB lease is a future extension (separate ticket); SMB consumers continue calling `SmbConnectionManager.getConnectionForExoPlayer(..)` directly. SFTP/FTP/Cloud gates (Phases 03–05) implement full lease path because their pools are simpler and well-bounded.

---

## Rollback Plan

Revert phase commit. Two new files (`SmbRecreateTracker.kt`, `SmbConnectionGate.kt`) and one DI module modification — all additive. No legacy code paths altered.

---

## Revision History

- **2026-05-03** — by `/spec-update` (`claude-opus-4-7`, focus: verifiability + consistency)
  - Applied: 6. Proposed (DISCUSS): 0.
  - Rationale: original Phase 02 prompts referenced an SMB pool API that does not exist
    (`acquireConnection`, `invalidateConnection`, `PooledEntry`). Refined to the actual
    legacy surface (`getConnectionForExoPlayer`, `invalidateExoPlayerConnection`,
    `closeUiConnections`, type `PooledConnection`). To respect the 988-LOC line budget on
    `SmbConnectionManager.kt`, the recreate-tracking logic was moved to a sibling
    `SmbRecreateTracker` rather than added to the manager. Phase 02 was narrowed to a
    lifecycle-only adapter (`closeFor` + `lastRecreateMs`); per-share gate-mediated lease
    becomes a future extension, consistent with the strategic non-goal "SMB consumers
    continue to use SmbConnectionManager directly". Refinement applied during `In Progress`
    status — override justified because the lock was set by the same `/spec-all` pipeline
    that issued the refinement request.
