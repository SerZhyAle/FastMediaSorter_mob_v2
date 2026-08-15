# Phase 02 - Availability Gate

**Strategic spec:** [`../S0391_remote-source-runtime-toggles.md`](../S0391_remote-source-runtime-toggles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-13
**Completed:** 2026-06-13

---

## Objective

Introduce the single source-availability node: a `RemoteSourceId` identity for the six managed sources and a `RemoteSourceAvailabilityGate` that folds compile-time support (`MediaCapabilities`) with the user toggle (`AppSettings`) into one in-memory query. No consumer wiring yet.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (six `AppSettings` flags exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/RemoteSourceId.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/RemoteSourceAvailabilityGate.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/RemoteSourceAvailabilityModule.kt` | New | ≤ 40 |

---

## Steps

### Step 02.1 - Define RemoteSourceId identity + mapping

**Files:** `core/capability/RemoteSourceId.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `enum class RemoteSourceId { SMB, SFTP, FTP, GOOGLE_DRIVE, ONEDRIVE, DROPBOX }`. Add a companion mapping: `fromCloudProvider(CloudProvider): RemoteSourceId`, `networkFromResourceType(ResourceType): RemoteSourceId?` (SMB/SFTP/FTP only, null for LOCAL/CLOUD), and grouping helpers `NETWORK = setOf(SMB,SFTP,FTP)` and `CLOUD = setOf(GOOGLE_DRIVE,ONEDRIVE,DROPBOX)`. No Android dependencies in this file.

**Verification:**

- `Glob` - `core/capability/RemoteSourceId.kt` exists.
- `Grep` - `enum class RemoteSourceId` matches once.
- `Grep` - `fun fromCloudProvider` and `networkFromResourceType` both present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 3/3 PASS. Created `RemoteSourceId` (6-value enum + `NETWORK`/`CLOUD` sets, `fromCloudProvider`, `networkFromResourceType`). Confirmed `CloudProvider{GOOGLE_DRIVE,ONEDRIVE,DROPBOX}` and `ResourceType{LOCAL,SMB,SFTP,FTP,CLOUD}` before mapping. No Android deps. Dev log recorded.

---

### Step 02.2 - Implement RemoteSourceAvailabilityGate

**Files:** `core/capability/RemoteSourceAvailabilityGate.kt` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `@Singleton class RemoteSourceAvailabilityGate @Inject constructor(...)` taking `MediaCapabilities` and `SettingsRepository`. Hold an in-memory snapshot of the six flags, kept current by collecting `settingsRepository.getSettings()` on an injected application `CoroutineScope` (no DataStore read on the query path). Expose: `isEnabled(id: RemoteSourceId): Boolean` (compile-tier AND user-tier - cloud ids require `mediaCapabilities.supportsCloud`, network ids are always compile-supported), `isEnabled(resource: MediaResource): Boolean` (maps `type`/`cloudProvider` to a `RemoteSourceId`; LOCAL resources are always enabled), `anyNetworkEnabled()`, `anyCloudEnabled()` (false when cloud unsupported), `anyRemoteEnabled()`, and `isCloudGroupSupported()` (== `mediaCapabilities.supportsCloud`). Do not read `BuildConfig` here. Do not add a debug-verification tag here - per CLAUDE Rule 2, `Timber.d("S0391: ...")` probes are inserted across all changed flows only at the final transition into `BlockNeedUserTest`, never in an intermediate phase.

**Verification:**

- `Glob` - `core/capability/RemoteSourceAvailabilityGate.kt` exists.
- `Grep` - `class RemoteSourceAvailabilityGate` matches once.
- `Grep` - `fun anyRemoteEnabled` and `fun isEnabled` present.
- `Grep -n "BuildConfig"` - zero hits in this file.
- `Grep -n "S0391"` - zero hits in this file (no debug tag in an intermediate phase).

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 4/4 PASS (class once, `isEnabled`/`anyRemoteEnabled` present, no `BuildConfig`, no `S0391` tag). Gate folds compile-tier (`MediaCapabilities.supportsCloud`) AND user-tier (`@Volatile` snapshot from `SettingsRepository.getSettings()` on the app scope) - hot path is in-memory. Cloud ids require compile cloud support; network ids always compile-supported; LOCAL/unmapped resources always enabled. Implemented as a plain class (no `@Inject`/`@Singleton` on the ctor) so step 02.3's Hilt module is the single, non-empty binding point rather than a slop module. Per CLAUDE Rule 2 the debug probe is deferred to the final `BlockNeedUserTest` transition, not inserted here. Dev log recorded.

---

### Step 02.3 - Provide the gate via Hilt

**Files:** `di/RemoteSourceAvailabilityModule.kt` (New)
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a Hilt `@Module @InstallIn(SingletonComponent::class)` that provides `RemoteSourceAvailabilityGate`. If the gate's constructor is fully `@Inject`-annotated with already-provided dependencies, this module only needs to provide the application `CoroutineScope` binding if one is not already available; otherwise the class is constructor-injected and the module documents the single binding point. Reuse the existing application-scope qualifier if present.

**Verification:**

- `Glob` - `di/RemoteSourceAvailabilityModule.kt` exists.
- `Grep` - `RemoteSourceAvailabilityGate` referenced in a `@Module` file (this module or constructor injection confirmed).
- `/build` compiles - Hilt graph resolves.

**Status:** `[x]` done

**Step Log:**

- 2026-06-13 - Verification 3/3 PASS. Added `@Module @InstallIn(SingletonComponent::class) object RemoteSourceAvailabilityModule` with `@Provides @Singleton provideRemoteSourceAvailabilityGate(MediaCapabilities, SettingsRepository, @ApplicationScope CoroutineScope)`. Reused existing `core.di.ApplicationScope` qualifier. `.\a.ps1 fc` BUILD SUCCESSFUL (32s; kaptStandardDebugKotlin ran - Hilt graph resolves). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` BUILD SUCCESSFUL (Hilt graph resolves via kapt), `.\a.ps1 fk` SUCCESSFUL on final tag-free state.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new `RemoteSourceId`, `RemoteSourceAvailabilityGate`, `RemoteSourceAvailabilityModule`).

---

## Handoff Notes to Next Phase

`RemoteSourceAvailabilityGate` is the single node. Phases 03-06 inject it and call `isEnabled(...)` / `anyRemoteEnabled()` instead of reading `BuildConfig.SUPPORT_CLOUD` or computing availability locally. The gate already respects compile-time cloud support, so consumers must not re-check `BuildConfig`.

---

## Rollback Plan

Revert phase commit(s) - three new files, no consumers wired, no behavior change.
