# Phase 03 - Repository Implementation

**Strategic spec:** [`../S0327_device-profile-onboarding.md`](../S0327_device-profile-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, 07, 08
**Steps done:** 5 / 5
**Started:** 2026-06-02
**Completed:** 2026-06-02

---

## Objective

Implement `DeviceProfileRepository` reading/writing to Room, managing profile lifecycle (fresh install, existing install migration, update on settings change). Expose as `Flow<DeviceProfile>` for reactive observation.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (Room schema, DAO, entities).
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/RealDeviceProfileRepository.kt` | New | ≤ 350 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/datasource/DeviceProfileLocalDataSource.kt` | New | ≤ 150 |

---

## Steps

### Step 03.1 - Create local data source wrapper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/datasource/DeviceProfileLocalDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a data source class:
> ```kotlin
> class DeviceProfileLocalDataSource(
>     private val dao: DeviceProfileDao
> ) {
>     fun observeProfile(): Flow<DeviceProfile?>
>     suspend fun saveProfile(profile: DeviceProfile)
>     suspend fun deleteProfile()
> }
> ```
> Map DAO results to domain DeviceProfile objects.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class DeviceProfileLocalDataSource` with DAO injection.
- `Grep` - `fun observeProfile(): Flow<...>` present.
- `Grep` - `suspend fun saveProfile` and `deleteProfile` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification PASS. `DeviceProfileLocalDataSource` maps DAO entity to/from domain `DeviceProfile`; `observeProfile(): Flow<DeviceProfile?>`, suspend `saveProfile`/`deleteProfile`. Compiles in `standardDebug`.

---

### Step 03.2 - Implement RealDeviceProfileRepository

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/RealDeviceProfileRepository.kt`
**Depends on:** Step 03.1, Phase 01, Phase 02

**Prompt for developer:**

> Create repository implementation:
> ```kotlin
> class RealDeviceProfileRepository(
>     private val detector: DeviceProfileDetector,
>     private val localDataSource: DeviceProfileLocalDataSource
> ) : DeviceProfileRepository {
>     override fun getCurrentProfile(): Flow<DeviceProfile> = localDataSource.observeProfile()
>         .map { it ?: getDefaultProfile() }
>
>     override suspend fun saveProfile(profile: DeviceProfile): Result<Unit> = runCatching {
>         localDataSource.saveProfile(profile)
>     }
>
>     override suspend fun updatePresetApplied(presetVersion: Int): Result<Unit> = ...
>     override suspend fun resetToDefault(fallbackProfile: DeviceProfileType): Result<Unit> = ...
>
>     private suspend fun getDefaultProfile(): DeviceProfile = ...
> }
> ```
> On fresh install (no profile in DB), invoke detector and return detected profile.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class RealDeviceProfileRepository` implements `DeviceProfileRepository`.
- `Grep` - all override methods present: getCurrentProfile, saveProfile, updatePresetApplied, resetToDefault.
- `Grep` - detector invoked in fresh-install path.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification PASS. `RealDeviceProfileRepository` implements all 5 contract methods; `getCurrentProfile()` maps null to safe default; @Singleton with `@Inject`. Covered by `RealDeviceProfileRepositoryTest`.

---

### Step 03.3 - Handle migration for existing installs

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/RealDeviceProfileRepository.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In repository initialization (constructor or init block):
> (1) Check if profile exists in DB.
> (2) If not (fresh install after app update), check SharedPreferences `has_run_before` flag.
> (3) If flag is true (existing install), write `DeviceProfile(type=OTHER, source=MIGRATION_EXISTING, confidence=NONE, appliedAtInstallTime=false)` to DB.
> (4) If flag is false (fresh install), leave blank; profile will be set by first-run detector.

**Verification:**

- `Grep` - migration logic comments explain the three paths: fresh install, existing install, no-change.
- `Grep` - SharedPreferences.hasRunBefore() check or equivalent.
- `Grep` - DeviceProfile created with source=MIGRATION_EXISTING when migrating.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification PASS. `initializeMigrationIfNeeded()` checks `welcome_prefs/welcome_completed`; existing install -> writes profile type=OTHER, source=MIGRATION_EXISTING, confidence=NONE, no preset. Note: init runs a fire-and-forget IO coroutine (see strategic Last Audit follow-up).

---

### Step 03.4 - Test repository with mock DAO and detector

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/RealDeviceProfileRepositoryTest.kt`
**Depends on:** Step 03.2, 03.3

**Prompt for developer:**

> Create unit tests:
> - Fresh install: detector returns VR_HEADSET → repository saves and emits it via Flow.
> - Existing install: migration flag set → repository initializes with OTHER.
> - Update preset: saveProfile(...) stores and reflects in getCurrentProfile() Flow.

**Verification:**

- `Glob` - test file exists.
- `Grep` - at least 3 test methods (testFreshInstall, testMigrationExisting, testUpdateProfile).
- `./a.ps1 dq app_v2:testDebugUnitTest` passes for repository tests.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification PASS. `RealDeviceProfileRepositoryTest` present; runs in filtered `testStandardDebugUnitTest` (failures=0).

---

### Step 03.5 - Expose repository profile as injectable Flow

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/RealDeviceProfileRepository.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a public field or getter:
> ```kotlin
> val currentProfileFlow: Flow<DeviceProfile> = getCurrentProfile()
>     .stateIn(viewModelScope, SharingStarted.Eagerly, getDefaultProfile())
> ```
> This will be injected into ViewModels in Phase 05–06. Keep the repository itself inject-ready.

**Verification:**

- `Grep` - `@Inject` on constructor of RealDeviceProfileRepository.
- Repository is injectable; binding in Phase 04.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification PASS. `@Inject constructor` on `RealDeviceProfileRepository`; bound to `DeviceProfileRepository` in Phase 04 `RepositoryModule`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `standardDebug` BUILD SUCCESSFUL.
- [x] Repository unit tests pass - `RealDeviceProfileRepositoryTest` (filtered run, failures=0).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entries for LocalDataSource and RealDeviceProfileRepository.

---

## Handoff Notes to Next Phase

Repository is complete and testable with mock detector and DAO. Phase 04 wires repository + detector into Hilt; Phase 07 adds preset matrix application.

---

## Rollback Plan

Revert phase commits - repository data layer only; no UI impact.
