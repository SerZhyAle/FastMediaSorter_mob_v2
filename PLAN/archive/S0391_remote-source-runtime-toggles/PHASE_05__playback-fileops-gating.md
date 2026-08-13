# Phase 05 - Playback, File-Ops & Connection Gating

**Strategic spec:** [`../S0391_remote-source-runtime-toggles.md`](../S0391_remote-source-runtime-toggles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 06 (none hard)
**Steps done:** 6 / 6
**Started:** 2026-06-14
**Completed:** 2026-06-14

---

## Objective

Close the remaining interaction paths for a disabled source: video/audio playback (including the Favorites mixed-source path), file operations, the connection gates/registry, and the quick verifier. Migrate the infra `BuildConfig.SUPPORT_CLOUD` reads to `MediaCapabilities`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (gate available).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Modified | ≤ 900 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/UnifiedFileOperationHandler.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/ConnectionGateRegistry.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkLifecycleModule.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/CloudConnectionGate.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/verifier/QuickVerifierDispatcher.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneLaunchDebugLogger.kt` | Modified | ≤ 120 |

Construction-site / call-site threading discovered at impl (manual constructors + capability call sites):
- `ui/player/PlayerActivity.kt`, `ui/player/PlayerViewerFactory.kt` - inject + pass gate into `VideoPlayerManager`.
- `ui/player/PlayerManagerInitializer.kt` - pass gate into `PlayerMediaLoaderManager`.
- `ui/player/StandalonePlayerActivity.kt` - inject `MediaCapabilities`, pass `supportsCloud` to the logger.
- `ui/cloudfolders/{GoogleDrive,Dropbox,OneDrive}FolderPickerActivity.kt` - migrate the `SUPPORT_CLOUD` guard to injected `MediaCapabilities` (Phase Done Criteria: zero `SUPPORT_CLOUD` across `src/main/java`).
- 4 unit-test files updated with an all-enabled gate mock so the new constructor params resolve.
- `data/network/lifecycle/ConnectionGateRegistry.kt` - left unmodified (no BuildConfig read; acquire is caller-driven, gating stays upstream + with `RemoteSourceDisableCoordinator`).

> `VideoPlayerManager.kt`, `PlayerMediaLoaderManager.kt`, `UnifiedFileOperationHandler.kt` are large - backed up to `temp/backups/` before editing.

---

## Steps

### Step 05.1 - Gate playback dispatch

**Files:** `ui/player/VideoPlayerManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `playVideo()` (the `when (resourceType)` dispatch), before routing to a remote playback path, turn back if `!gate.isEnabled(resource)`. Back up the file first. Do not add a debug-verification tag here (deferred to the final `BlockNeedUserTest` transition, CLAUDE Rule 2).

**Verification:**

- `Grep` - `gate.isEnabled(` referenced in `VideoPlayerManager.kt`.
- `Grep` - no `Timber.d("S0391:` tag in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. Guard before the `when (resourceType)` dispatch: LOCAL→true, CLOUD→`anyCloudEnabled()`, network→`isEnabled(networkFromResourceType)`. On disabled: `Timber.w` + `onPlaybackError(error_resource_unavailable, fileName)` + return. Gate threaded from `PlayerActivity` (@Inject) via `PlayerViewerFactory`. File backed up. `.\a.ps1 fk` SUCCESSFUL.

---

### Step 05.2 - Gate the Favorites mixed-source player path

**Files:** `ui/player/helpers/PlayerMediaLoaderManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Where `actualResourceType` is inferred from the path prefix for the virtual Favorites aggregate (id=-100L), resolve the original source identity from the path/prefix and turn back when the corresponding `RemoteSourceId` is disabled per the gate. A hidden source must not be playable from the Favorites aggregate. Back up the file first.

**Verification:**

- `Grep` - `gate` (the availability gate) referenced in `PlayerMediaLoaderManager.kt`.
- `Grep` - the Favorites/`actualResourceType` branch consults the gate.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. Added `isPathSourceEnabled(path, defaultType)` (maps the resolved per-file source: LOCAL→true, CLOUD→`anyCloudEnabled()`, network→`isEnabled(id)`) and a turn-back guard in `playVideo(path)` before any load UI - so a hidden source in the Favorites (-100L) mixed aggregate is not playable. Gate threaded from `PlayerManagerInitializer`. File backed up.

---

### Step 05.3 - Gate file operations

**Files:** `data/transfer/UnifiedFileOperationHandler.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Before dispatching a file operation on a remote path (the path-prefix dispatch), turn back / refuse when the corresponding source is disabled per the gate. Map the path prefix to a `RemoteSourceId` and consult `gate.isEnabled(id)`.

**Verification:**

- `Grep` - `RemoteSourceAvailabilityGate` or `gate.isEnabled(` referenced in `UnifiedFileOperationHandler.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. Added `requireSourceEnabled(path)` (protocol prefix → `isEnabled`; smb/sftp/ftp/cloud, LOCAL always) called at the top of both `getProvider()` and `getStrategy()` dispatch resolvers; throws `IllegalStateException` when disabled, caught by each public method's existing `catch` → standard failure `Result`. File backed up.

---

### Step 05.4 - Connection registry passive gating + infra capability migration

**Files:** `data/network/lifecycle/ConnectionGateRegistry.kt`, `core/di/NetworkLifecycleModule.kt`, `data/network/lifecycle/CloudConnectionGate.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Ensure no connection lease is acquired for a disabled source (passive gating at the acquire boundary). Migrate the `BuildConfig.SUPPORT_CLOUD` reads in `NetworkLifecycleModule` and `CloudConnectionGate` to injected `MediaCapabilities.supportsCloud` (compile-tier only - registration still depends on build support, not the user toggle). The user-toggle close-on-disable is handled by `RemoteSourceDisableCoordinator` from Phase 04; this step only stops new acquisitions and removes the `BuildConfig` reads.

**Verification:**

- `Grep -n "BuildConfig.SUPPORT_CLOUD"` - zero hits in `NetworkLifecycleModule.kt` and `CloudConnectionGate.kt`.
- `Grep` - `supportsCloud` referenced where the cloud gate registration decision is made.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. `NetworkLifecycleModule`: `provideRegistry` takes `MediaCapabilities` and registers the cloud gate on `mediaCapabilities.supportsCloud` (compile-tier). `CloudConnectionGate`: the only `SUPPORT_CLOUD` reference was a KDoc line - reworded to `MediaCapabilities.supportsCloud`; `acquire()` already throws unconditionally. `ConnectionGateRegistry` left unmodified (no BuildConfig read; acquire is caller-driven, runtime close-on-disable owned by `RemoteSourceDisableCoordinator`).

---

### Step 05.5 - Gate the quick verifier

**Files:** `data/verifier/QuickVerifierDispatcher.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> In the `when (resource.type)` verifier dispatch, skip probing a resource whose source is disabled per the gate (no connection probe for a disabled source).

**Verification:**

- `Grep` - `gate.isEnabled(` referenced in `QuickVerifierDispatcher.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. After resolving `resource`, before the `when (resource.type)` probe dispatch: `if (!remoteSourceGate.isEnabled(resource)) return emptyList()` - a disabled source is never connection-probed. Reuses the existing skipped/empty return path.

---

### Step 05.6 - Migrate the debug logger capability read

**Files:** `ui/player/helpers/StandaloneLaunchDebugLogger.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Replace the `BuildConfig.SUPPORT_CLOUD` read with injected `MediaCapabilities.supportsCloud`. This is a Rule 14 hygiene migration - the logger only reports the compile-time flag.

**Verification:**

- `Grep -n "BuildConfig.SUPPORT_CLOUD"` - zero hits in `StandaloneLaunchDebugLogger.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-14 - Verification PASS. `StandaloneLaunchDebugLogger` is an `object`; added a `supportsCloud: Boolean` param to `log()` replacing the `BuildConfig.SUPPORT_CLOUD` read. Call site `StandalonePlayerActivity` injects `MediaCapabilities` and passes `supportsCloud`. Also migrated the 3 cloudfolders picker activities' `SUPPORT_CLOUD` guards to injected `MediaCapabilities` - zero `SUPPORT_CLOUD` now across `src/main/java`.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (Hilt graph resolves; only pre-existing deprecation warnings).
- [x] `Grep -n "BuildConfig.SUPPORT_CLOUD"` - zero hits across all `src/main/java` files (full Rule 14 remediation complete - incl. the 3 cloudfolders pickers).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Every interaction path - listing, background work, playback, file operations, connections, verification - respects the gate. No `BuildConfig.SUPPORT_CLOUD` remains in shared code. Phase 06 builds the user-facing toggles (settings section + welcome page) and the disable-confirmation dialog.

---

## Rollback Plan

Revert phase commit(s). Restores `BuildConfig` infra reads and removes player/file-op gating - no data change.
