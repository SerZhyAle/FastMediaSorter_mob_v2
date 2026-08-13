# Phase 07 — Network Idle Disconnect

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 4 / 5
**Started:** 2026-05-15
**Completed:** —

---

## Objective

Introduce `IdleDisconnectPolicy` — a per-transport idle timer that closes the connection (and releases native buffers) after a configurable period of inactivity. Wire into SFTP, SMB, FTP transports. Default idle 30 seconds when the user leaves a browse session in that transport. Trigger reconnect transparently on next request.

For SFTP specifically, the phase is intentionally **two-part**: a per-session timer in `SftpClient` plus an independent periodic sweep in `SftpConnectionPool`. The current pool cleanup is reactive-only, so a client-side timer by itself would still miss the stale pooled-session case.

**SFTP correction (research 2026-05-15).** The strategic spec previously described the SFTP transport as built on **SSHJ** — that is incorrect. The actual library is **JSch** (`com.jcraft.jsch.{JSch,Session,ChannelSftp}`, see `data/remote/sftp/SftpConnectionPool.kt:3-5`). All "SSHJ" references below are corrected to "JSch".

**Current SFTP cleanup is reactive-only.** `SftpConnectionPool.cleanupIdleConnections()` sweeps idle entries > `IDLE_TIMEOUT_MS = 30_000L`, but before Phase 07 it only ran from reactive release paths. If no SFTP op ran after the user left the browse screen, the sweep never executed — sessions lived until process-level `ProcessLifecycleOwner.ON_STOP`. `BrowseViewModel.onCleared()` still does **not** close SFTP sessions (`BrowseViewModel.kt:574-592`).

**`System.gc()` is already wired** on `ON_STOP` immediately after `disconnectAllPool()` via `FastMediaSorterApp.kt:318` (`onAppBackgrounded`). Variant (b) of Research item 3 ("does close+gc help") therefore needs **zero new code** — profile `ON_STOP` with that single line enabled vs commented out.

**Implementation choice for SFTP (Option A from research).** Run a periodic tick inside `SftpConnectionPool` that calls `cleanupIdleConnections()` independently of op release. The newly designed `IdleDisconnectPolicy` interface still covers SMB/FTP per-session timers as before; SFTP uses **both** the policy (per-session timer wired in Step 07.3) AND the pool-level periodic sweep (Step 07.3 sub-task).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Baseline `MEM_PROBE | checkpoint=AFTER_STATE_READY` recorded for SFTP MP3 playback.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicy.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/IdleDisconnectModule.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt` | Modified | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImplTest.kt` | New | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPoolTest.kt` | New | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/ftp/FtpClientTest.kt` | Modified | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/remote/sftp/SftpClientTest.kt` | Modified | ≤ 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/smb/SmbConnectionManagerTest.kt` | Modified | ≤ 200 |

---

## Steps

### Step 07.1 — Add `IdleDisconnectPolicy` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create interface `IdleDisconnectPolicy`:
> ```kotlin
> interface IdleDisconnectPolicy {
>     fun arm(transport: String, idleMs: Long, onTimeout: suspend () -> Unit)
>     fun touch(transport: String)
>     fun disarm(transport: String)
> }
> ```
> Semantics:
> - `arm` — start (or reset) the idle timer for the named transport. If already armed for this transport, replace the existing callback.
> - `touch` — caller reports activity; timer restarts from zero.
> - `disarm` — caller explicitly stops the timer (e.g., user re-entered the browse screen for this transport).
>
> `transport` is an arbitrary string key (e.g., `"sftp@host:port:user"`) chosen by the caller so multiple simultaneous sessions can coexist.
>
> Package `com.sza.fastmediasorter.data.network`.

**Verification:**

- `Glob` — `IdleDisconnectPolicy.kt` exists.
- `Grep` — `interface IdleDisconnectPolicy` matches exactly once.
- `Grep` — all three method signatures present.

**Status:** `[x]` done — `IdleDisconnectPolicy` landed in `data/network` as the shared arm/touch/disarm contract.

---

### Step 07.2 — Implement `IdleDisconnectPolicyImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/IdleDisconnectPolicyImpl.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Create `@Singleton class IdleDisconnectPolicyImpl @Inject constructor() : IdleDisconnectPolicy`. State: `private val timers = ConcurrentHashMap<String, Job>()`, and an internal `CoroutineScope(SupervisorJob() + Dispatchers.Default)` for timer jobs.
>
> `arm`: cancel any existing job for the key; launch a new `delay(idleMs)` job that calls `onTimeout()` and removes itself from the map; store it.
>
> `touch`: equivalent to re-`arm` with the same `idleMs` and callback — but the simplest implementation requires the caller to keep the callback. Pragmatic alternative: track `(idleMs, callback)` per key in a second map; `touch` reads from that map and re-arms. Use whichever shape is simpler in Kotlin (likely a `data class TimerEntry(val idleMs: Long, val callback: suspend () -> Unit)`).
>
> `disarm`: cancel and remove without invoking the callback.
>
> Add Timber.i for `arm`, `touch`, `disarm`, `timeout fired` events — tag prefix `IdleDisconnect`.
>
> Confirms Research item 3 only partially: JSch behaviour on `Session.disconnect()` (whether GC is needed) is observed at the call site (Step 07.3), not in this class.

**Verification:**

- `Glob` — `IdleDisconnectPolicyImpl.kt` exists.
- `Grep` — `class IdleDisconnectPolicyImpl @Inject constructor() : IdleDisconnectPolicy` present.
- `Grep` — `ConcurrentHashMap` present.
- `Grep` — `SupervisorJob()` present.
- `Grep` for `Log.d\(` returns zero hits.

**Status:** `[x]` done — `IdleDisconnectPolicyImpl` landed with `ConcurrentHashMap` timer state, process-scope coroutine timers, and focused JVM coverage for arm/touch/disarm semantics.

---

### Step 07.3 — Wire SFTP transport (per-session timer + pool-level periodic sweep)

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpClient.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt`

**Depends on:** Step 07.2

**Prompt for developer:**

> **Part A — Per-session idle policy (mirrors SMB/FTP shape).** Inject `IdleDisconnectPolicy` into `SftpClient`. On every successful connect:
> ```kotlin
> idleDisconnectPolicy.arm(transportKey(), IDLE_TIMEOUT_MS) {
>     disconnect()
>     // Research item 3, variant (a): JSch Session.disconnect() releases per-channel I/O buffers
>     // synchronously. The existing System.gc() at FastMediaSorterApp.kt:318 already runs
>     // immediately after disconnectAllPool on ProcessLifecycleOwner.ON_STOP, so variant (b)
>     // (close + gc) is observable from logs without extra code here. Do NOT add System.gc()
>     // inside this per-session callback — it would fire too frequently.
> }
> ```
> Where `transportKey()` returns a stable string identifier for this client instance and `IDLE_TIMEOUT_MS = 30_000L`.
>
> Every method that performs a request (list, read, stat, download chunk) calls `idleDisconnectPolicy.touch(transportKey())` BEFORE issuing the network call.
>
> Disconnect path: when the user-driven disconnect happens (explicit `disconnect()` invocation NOT from the timer callback), call `idleDisconnectPolicy.disarm(transportKey())` first to prevent double-disconnect.
>
> **Part B — Pool-level periodic sweep (Option A from research).** Today `SftpConnectionPool.cleanupIdleConnections()` (`SftpConnectionPool.kt:222-246`) fires only inside `releaseExoPlayerConnection` (L341) and `openInputStream` finally (L414). If no op runs, sessions live until `ON_STOP`. Schedule a periodic tick:
> ```kotlin
> // In SftpConnectionPool (or a co-located @Singleton holder):
> private val sweepScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
> private val sweepJob: Job = sweepScope.launch {
>     while (isActive) {
>         delay(IDLE_TIMEOUT_MS)
>         runCatching { cleanupIdleConnections() }
>     }
> }
> ```
> Tag log lines with `SftpConnectionPool.periodicSweep`. Cancel `sweepJob` from a `close()`/`disconnectAll()` path so test harnesses don't leak.
>
> Backup any file >500 lines.

**Verification:**

- `Glob` — `temp/SftpClient.*.kt.bak` and `temp/SftpConnectionPool.*.kt.bak` exist (if >500 lines).
- `Grep` — `idleDisconnectPolicy.arm(` present in `SftpClient.kt`.
- `Grep` — `idleDisconnectPolicy.touch(` present (at least 3 occurrences — list, read, download paths).
- `Grep` — `idleDisconnectPolicy.disarm(` present at the explicit-disconnect path.
- `Grep` — `IDLE_TIMEOUT_MS = 30_000L` declared in `SftpClient.kt`.
- `Grep` — `System.gc()` count inside `SftpClient.kt` is 0 (no extra GC inside the per-session callback).
- `Grep` — `SftpConnectionPool.periodicSweep` literal present in `SftpConnectionPool.kt`.
- `Grep` — `while (isActive)` + `delay(IDLE_TIMEOUT_MS)` + `cleanupIdleConnections()` all present in the same scope inside `SftpConnectionPool.kt`.

**Status:** `[x]` done — `SftpClient` now arms/touches the shared policy on request and playback entry points, `SftpConnectionPool` runs an independent 30 s periodic sweep, and required backups were written for the >500 LOC transport owners before editing.

---

### Step 07.4 — Wire SMB + FTP transports

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbConnectionManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt`

**Depends on:** Step 07.3

**Prompt for developer:**

> Repeat the Step 07.3 Part A pattern for SMB and FTP (per-session timer only — no pool-level periodic sweep needed; SMB has its own `BaseConnectionPool.cleanupIdleConnections()` driven by ON_STOP path, FTP has `FtpExoPlayerPool.cleanupIdleFtpConnections()`). Differences:
> - SMB: no `System.gc()` — SMBJ releases native buffers reliably on close (documented). Keep only `close()`.
> - FTP: no `System.gc()` — Apache Commons Net is JVM-only, no native side. Keep only `disconnect()`.
> - SFTP also has no `System.gc()` in the per-session callback (see Step 07.3 Part A rationale).
> - Transport key composition mirrors the SFTP shape (`"smb@host:share:user"`, `"ftp@host:port:user"`).

**Verification:**

- `Grep` — `idleDisconnectPolicy.arm(` present in `SmbConnectionManager.kt` AND `FtpClient.kt`.
- `Grep` — `System.gc()` ABSENT from `SmbConnectionManager.kt` AND `FtpClient.kt` (in the new code blocks).
- `Grep` — `IDLE_TIMEOUT_MS = 30_000L` declared in each.

**Status:** `[x]` done — SMB and FTP request choke points now arm/touch the shared idle policy with 30 s timers, and explicit pool-reset / disconnect paths disarm tracked timers.

---

### Step 07.5 — Calibration measurement (idle disconnect)

**Files:** —
**Depends on:** Step 07.4 + project compiles

**Prompt for developer:**

> Manual scenario: open SFTP browse, navigate to MP3 list, leave the screen (e.g., back to main / minimize). Wait 35 seconds. Re-enter SFTP browse.
>
> Expected in `logs/current.log`:
> - `IdleDisconnect: arm(transport=sftp@..)` line on initial connect.
> - `IdleDisconnect: touch(..)` lines during list/read.
> - `IdleDisconnect: timeout fired (sftp@..)` ~30s after leaving the screen.
> - Re-entry triggers a fresh connect (existing Timber log from `SftpClient.connect`).
>
> Measure: `Debug.getNativeHeapAllocatedSize()` via the next `MEM_PROBE` (if scenario continues) — delta vs Phase 06 baseline should be measurably lower (≥ 3 MB).

**Verification:**

- `Grep` in `logs/current.log` — `IdleDisconnect: timeout fired` present in the most recent session.
- `Grep` — `IdleDisconnect: arm` count ≤ `IdleDisconnect: timeout fired` + 1 in same session (sanity: no leaked timers).

**Status:** `[manual — deferred to human]` — requires device/emulator run; deferred to BlockNeedUserTest operator test.

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [x] Project compiles — `./gradlew.bat :app_v2:compileStandardDebugKotlin :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.data.network.IdleDisconnectPolicyImplTest" --tests "com.sza.fastmediasorter.data.remote.sftp.SftpConnectionPoolTest"` PASS.
- [ ] All three transports trigger idle-disconnect after 30s of inactivity.
- [ ] Re-entry transparently reconnects with no user-visible error.
- [x] Narrow coverage exists for the SFTP idle-sweep path (`SftpConnectionPool`) and at least one timer-arm / touch / disarm path. If no tests exist yet, add them in this phase.
- [x] Dev log entry added for all touched files.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Phase 08 (docs-catalog-cleanup) is the final phase — catalog regen, comprehensive dev log audit, validate that no `Timber.d("S0207:"` debug tags remain (they will be inserted by `/spec-dev` automatically and removed by `/spec-check` on `Verified`).

---

## Rollback Plan

Revert phase commits. Transports return to keeping connections alive indefinitely until explicit user disconnect or app death. No data migration.

---

## Revision History

- **2026-05-16** — manual implementation sync after Phase 07 code landed
  - Applied: marked Steps 07.1..07.4 complete; added the shared idle-disconnect policy contract/implementation/module, wired SFTP/SMB/FTP transport choke points to 30 s idle timers, added the SFTP periodic sweep lifecycle and focused JVM coverage, and left Step 07.5 open pending runtime logcat calibration plus reconnect confirmation.
- **2026-05-15** — by `/spec-update` (Claude Opus 4.7, focus: completeness, consistency)
  - Applied: Objective rewritten — corrected "SSHJ" → "JSch" (the library used is `com.jcraft.jsch`, verified by class catalog), documented the reactive-only nature of `SftpConnectionPool.cleanupIdleConnections()` and the `BrowseViewModel.onCleared()` gap, documented the already-wired `System.gc()` at `FastMediaSorterApp.kt:318` so variant (b) of Research 3 needs zero new code; Step 07.2 reference SSHJ → JSch; Step 07.3 expanded to Parts A + B (per-session timer + pool-level periodic sweep via `applicationScope`) — Option A from research; removed the misleading `System.gc()` call in the per-session SFTP arm-callback (JSch releases buffers synchronously on `disconnect()`); Step 07.4 SMB/FTP note clarified that none of the three transports need `System.gc()` in the per-session callback. Proposed (DISCUSS): 0.
  - Evidence: `temp/S0207_research/03_sshj_close_map.md` (full SFTP/JSch lifecycle map, all `System.gc()` call-sites) + `00_SUMMARY.md` F1, F7, F8.
- **2026-05-15** — by `/spec-update` (GPT-5.4, focus: consistency, verifiability)
  - Applied: added `SftpConnectionPool.kt` to the formal file scope, made the SFTP two-part implementation explicit in the objective, and added targeted test guidance for the idle sweep / timer lifecycle so `/spec-dev` has a concrete verification target. Proposed (DISCUSS): 0.
