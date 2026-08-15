# S0904 - Infra misc: tile controller leak, latent pool unlock, dead FTP idle pool, capture latch (P2 cluster)

**Ticket:** S0904
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->
<!-- auto-approved by /spec-all (compact) - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком.

- app_v2/src/main/java/com/sza/fastmediasorter/core/AudioToggleTileService.kt:78 - MediaController built asynchronously can land after onStopListening; next listen cycle overwrites it without release, leaking the controller and its Player.Listener
- app_v2/src/main/java/com/sza/fastmediasorter/data/network/pool/BaseConnectionPool.kt:119 - invalidateConnection ignores poolMutex.tryLock() result and unconditionally unlock()s - would corrupt another coroutine's critical section or throw IllegalStateException; latent only (no production subclass)
- app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpExoPlayerPool.kt:132 - Idle-pool machinery is dead code: connectionPool is never populated, so cleanupIdleFtpConnections() is a permanent no-op that FtpClient still arms on every acquire
- app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt:75 - Enable-all sequence stalls permanently after recreation/process death: stage-completion callback is instance-bound and restored inProgress=true has no resume path
- app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt:178 - isFinalizing latch is never reset - a recording started during finalization cannot be stopped and is silently killed and stranded

## 1. Goal (RU)

Четыре независимых инфра-дефекта (пятая находка вынесена - см. §5). Утечка MediaController в QS-tile при перезаписи stale-контроллера; latent illegal-unlock в пуле соединений; мёртвая FTP idle-pool машинерия, которую FtpClient вооружает на каждый acquire; незакрываемый latch финализации в сервисе записи экрана.

## 2. Constraints

- Finding 3 - удалить только мёртвую ExoPlayer-idle-pool машинерию; НЕ трогать живой `idleDisconnectPolicy` FtpClient (control-connection idle disconnect, используется широко).
- Finding 2 latent (нет prod-подкласса) - минимальный корректный фикс, без смены на suspend/withLock.
- No behavior change on happy paths.

## 3. Phases

### Phase 1 - `AudioToggleTileService` release stale controller (finding 78)

- Step 1.1: In the `connectToSession` future callback success branch, release the previous `mediaController` (removeListener + release) before assigning the new one. The guard `if (mediaController?.isConnected == true) return` skips only the connected case; a stale/disconnected controller otherwise gets overwritten without release (leaking it + its `Player.Listener`).
  - Verification: grep - the callback releases any existing `mediaController` before `mediaController = controller`.

### Phase 2 - `BaseConnectionPool.invalidateConnection` guarded unlock (finding 119)

- Step 2.1: Capture `val locked = poolMutex.tryLock()`; in `finally`, `if (locked) poolMutex.unlock()`. An ignored `tryLock()` followed by an unconditional `unlock()` unlocks a Mutex this call may not own (corrupts another coroutine's section / throws `IllegalStateException`).
  - Verification: grep - `tryLock()` result captured; `unlock()` only when `locked`.

### Phase 3 - Remove dead FTP idle pool (finding 132)

- Step 3.1: `FtpExoPlayerPool` - delete `connectionPool`, `poolMutex`, `PooledFtpConnection`, `ConnectionKey`, `cleanupIdleFtpConnections()`, `IDLE_TIMEOUT_MS`, and the now-unused `ConcurrentHashMap` import; drop the "idle pool tracking remains intact" KDoc paragraph. `connectionPool` was never populated (each acquire builds a fresh client, released via `releaseExoPlayerConnection`), so the sweep was a permanent no-op.
- Step 3.2: `FtpClient` - in `getConnectionForExoPlayer`, drop the `.also { idleDisconnectPolicy.arm(transportKey, IDLE_TIMEOUT_MS) { cleanupIdleFtpConnections() } }` (arming a no-op) and return the connection directly; delete the `cleanupIdleFtpConnections()` delegate. Keep the control-connection idle policy (`idleDisconnectPolicy`, `transportKey` track/touch) untouched - it is live and used elsewhere.
- Step 3.3 (orphaned caller, found by the build): `FtpConnectionGate.closeFor` delegated to the removed `client.cleanupIdleFtpConnections()`. Make `closeFor` a no-op (the idle sweep was permanently dead), drop the now-unused `client: FtpClient` constructor param + `FtpClient`/`Timber` imports, and refresh the class KDoc. Update the two unit tests that referenced the removed API: delete `FtpExoPlayerPoolTest`'s empty-pool cleanup test; collapse `ConnectionGatesTest`'s three FTP closeFor tests into one asserting `closeFor` is a no-op via the 2-arg constructor (drop the now-unused `FtpClient`/`every` imports).
  - Verification: grep - no `cleanupIdleFtpConnections`/`connectionPool` anywhere; `idleDisconnectPolicy` control-connection wiring (arm ~348, disarm ~366) unchanged; `standard debug` compiles; `FtpExoPlayerPoolTest` + `ConnectionGatesTest` compile and pass.

### Phase 4 - `ScreenVideoRecordingService` reset finalize latch (finding 178)

- Step 4.1: At the start of `startRecording`, set `isFinalizing = false` so a fresh recording is stoppable. The latch was set in `stopAndSave` and never cleared, so a recording started during/after finalization hit `if (isFinalizing) return` and could never be stopped (silently killed + stranded).
  - Verification: grep - `startRecording` clears `isFinalizing`; `standard debug` compiles (screenCapture source set is in the standard variant).

### Phase 5 - Build gate

- Step 5.1: `standard debug` compiles (`a.ps1 fk`) - covers `src/main` + `src/screenCapture`. Detekt-clean on the touched files.
  - Verification: BUILD SUCCESSFUL; no new detekt findings.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878 (audit tail container - triage source), S0876 (welcome enable-all lost-update - Verified, adjacent WelcomePermissionsManager flow), S0910 (deferred finding 4 - welcome enable-all process-death resume).

## 5. Deferred - finding 4 (welcome enable-all process-death resume)

`WelcomePermissionsManager.grantAllOnComplete` (the `{ beginDefaultPlayerStage() }` chain from `WelcomeEnableAllManager`) is an instance-bound lambda that cannot be parceled. After process death mid-run, `onRestoreInstanceState` restores `grantAllInProgress=true` (the run resumes via the re-registered launcher) but `grantAllOnComplete` is null, so on completion `grantAllOnComplete?.invoke()` is a no-op and the enable-all sequence never advances to the default-player stage. Fixing it needs orchestrator-level persistence + a re-attach path across `WelcomeEnableAllManager`/`WelcomePermissionsManager`/the Welcome activity save-restore, and a UX decision (auto-resume vs restart the enable-all run). Deferred to its own ticket - see S0910.

## Related

- S0878 (audit tail container - triage source).
- S0910 (deferred: welcome enable-all process-death resume).

## Last Audit

**Date:** 2026-07-03 (spec-all, static). **Status:** Verified.

Findings 1/2/3/5 implemented; finding 4 deferred to S0910. `standard debug` Kotlin compile PASS (incl. `src/screenCapture`); detekt-clean on the six touched main files; affected unit tests (`FtpExoPlayerPoolTest`, `ConnectionGatesTest`) PASS.

- **`AudioToggleTileService` (78)** - the connect callback now releases any prior `mediaController` (removeListener + release) before assigning the new one, so a stale/disconnected controller (the `isConnected` guard's blind spot) is no longer overwritten unreleased.
- **`BaseConnectionPool.invalidateConnection` (119)** - `val locked = poolMutex.tryLock()`; `finally { if (locked) poolMutex.unlock() }`. No longer unlocks a Mutex it may not own. Latent (no prod subclass).
- **FTP idle pool (132)** - dead machinery removed from `FtpExoPlayerPool`; `FtpClient` no longer arms the no-op sweep; `FtpConnectionGate.closeFor` is now a no-op with its unused `client` param dropped. Live control-connection idle policy untouched. Two unit tests updated to match (orphaned callers found by the test compile - see the S0898/collateral note in the dev log).
- **`ScreenVideoRecordingService.startRecording` (178)** - clears `isFinalizing` at the top so a recording started during/after a prior finalization is stoppable (was permanently latched).

**Evidence rung:** static + compile + detekt + affected unit tests (P2). The leaks/latch fixes are structural; none is reliably reproducible by a device gesture (stale-controller race, contended tryLock, dead code, finalize-window race). No device gate. The detekt gate's project-wide FAIL is unrelated sibling WIP (`ScreenVideoRecordingService` `ReturnCount`/`MagicNumber` pre-date this change - line numbers shifted only because `isFinalizing = false` added 3 lines).
