# Phase 01 - Foundations

**Strategic spec:** [`../S0327_device-profile-onboarding.md`](../S0327_device-profile-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 5 / 5
**Started:** 2026-06-02 01:34:15
**Completed:** 2026-06-02 01:45:00

---

## Objective

Introduce `DeviceProfile` data class, profile enums (11 selectable + auto-skipped variant), shared repository and detector interfaces, and Room schema bump + migration for profile persistence.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (N/A).
- [ ] Strategic §6 research items blocking this phase are Resolved (all Resolved).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/model/DeviceProfile.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/DeviceProfileRepository.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/detector/DeviceProfileDetector.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration31To32.kt` | New | ≤ 100 |

---

## Steps

### Step 01.1 - Define DeviceProfile enum and data classes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/model/DeviceProfile.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a new Kotlin data file defining:
> (1) Enum `DeviceProfileType` with 11 selectable values: PERSONAL_SMARTPHONE, HOME_TABLET, TV_MEDIA_BOX, CAR_HEAD_UNIT, MEDIA_PLAYER, PHOTO_FRAME, VIDEO_PLAYER, AUDIO_PLAYER, EBOOK_READER, VR_HEADSET, OTHER.
> (2) Data class `DeviceProfile` holding: `type: DeviceProfileType`, `source: Source` (enum: MANUAL_SELECTION, AUTO_DETECTED, MIGRATION_EXISTING), `confidence: Confidence` (enum: HIGH, MEDIUM, LOW, NONE), `presetVersion: Int`, `appliedAtInstallTime: Boolean`, `lastModified: Long` (millis UTC).
> (3) Data class `DetectorSignal` holding: `signalName: String`, `profileType: DeviceProfileType`, `confidence: Confidence`.

**Verification:**

- `Glob` - file `app_v2/src/main/java/com/sza/fastmediasorter/data/model/DeviceProfile.kt` exists.
- `Grep` - `enum class DeviceProfileType` appears exactly once.
- `Grep` - all 11 enum values present: PERSONAL_SMARTPHONE, HOME_TABLET, TV_MEDIA_BOX, CAR_HEAD_UNIT, MEDIA_PLAYER, PHOTO_FRAME, VIDEO_PLAYER, AUDIO_PLAYER, EBOOK_READER, VR_HEADSET, OTHER.
- `Grep` - `data class DeviceProfile` with fields: type, source, confidence, presetVersion, appliedAtInstallTime, lastModified.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification 4/4 PASS. File: DeviceProfile.kt (+60 LOC). Enums: DeviceProfileType (11 values), DeviceProfileSource, DetectionConfidence. Data classes: DeviceProfile, DetectorSignal.

---

### Step 01.2 - Define DeviceProfileRepository interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/DeviceProfileRepository.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a repository interface defining contract for profile persistence:
> - `fun getCurrentProfile(): Flow<DeviceProfile>`
> - `fun saveProfile(profile: DeviceProfile): suspend Result<Unit>`
> - `fun updatePresetApplied(presetVersion: Int): suspend Result<Unit>`
> - `fun resetToDefault(fallbackProfile: DeviceProfileType): suspend Result<Unit>`
> - `fun getDetectionHistory(): Flow<List<DetectorSignal>>` (optional, for audit)

**Verification:**

- `Glob` - file exists.
- `Grep` - `interface DeviceProfileRepository` appears exactly once.
- `Grep` - all 4+ methods listed with correct suspend/Flow signatures.
- `Grep` - no implementation code in the file (interface only).

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification 4/4 PASS. File: DeviceProfileRepository.kt (+35 LOC). Interface with 5 methods: getCurrentProfile, saveProfile, updatePresetApplied, resetToDefault, getDetectionHistory.

---

### Step 01.3 - Define DeviceProfileDetector interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/detector/DeviceProfileDetector.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a detector interface for profile auto-detection:
> - `suspend fun detectProfile(): DetectionResult` (returns: profile type, confidence, list of signals fired).
> - `fun getSignalForProfile(type: DeviceProfileType): DeviceProfileDetector.Signal?`
> Enum `Confidence { HIGH, MEDIUM, LOW }` inline or reference from DeviceProfile.
> Data class `DetectionResult` holding: `profile: DeviceProfileType`, `confidence: Confidence`, `signals: List<String>` (names of signals that fired).

**Verification:**

- `Glob` - file exists.
- `Grep` - `interface DeviceProfileDetector` appears exactly once.
- `Grep` - `suspend fun detectProfile()` present with return type `DetectionResult`.
- `Grep` - `data class DetectionResult` with fields: profile, confidence, signals.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification 4/4 PASS. File: DeviceProfileDetector.kt (+30 LOC). Interface with DetectionResult and Signal data classes.

---

### Step 01.4 - Bump Room @Database version and add entity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> (1) Create a new Room `@Entity` class `DeviceProfileEntity` with columns:
>     - `id: Int` (primary key)
>     - `type: String` (DeviceProfileType enum name)
>     - `source: String` (Source enum name)
>     - `confidence: String` (Confidence enum name)
>     - `presetVersion: Int` (nullable)
>     - `appliedAtInstallTime: Boolean`
>     - `lastModified: Long` (Unix millis)
> (2) Add the entity to `@Database(entities = [..., DeviceProfileEntity::class], version = N+1, ...)`
> (3) Add a DAO interface `DeviceProfileDao` with:
>     - `@Query("SELECT * FROM device_profile WHERE id = 1") suspend fun getProfile(): DeviceProfileEntity?`
>     - `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProfile(entity: DeviceProfileEntity)`
>     - `@Delete suspend fun deleteProfile(entity: DeviceProfileEntity)`
> (4) Expose the DAO from AppDatabase: `abstract fun deviceProfileDao(): DeviceProfileDao`

**Verification:**

- `Grep` - `@Entity` class `DeviceProfileEntity` appears exactly once.
- `Grep` - `version = ` in `@Database` increased by 1 from previous value.
- `Grep` - `DeviceProfileEntity::class` listed in `entities` array.
- `Grep` - `interface DeviceProfileDao` with 3+ methods (getProfile, upsertProfile, deleteProfile).
- `Grep` - `abstract fun deviceProfileDao()` in AppDatabase.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification 5/5 PASS. Files: DeviceProfileEntity.kt (+45 LOC), AppDatabase.kt (version 31→32, added DeviceProfileEntity::class and deviceProfileDao()). DAO with 3 methods: getProfile, upsertProfile, deleteProfile.

---

### Step 01.5 - Add Room migration for new schema version

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/Migration31To32.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Create a new Room migration class for the version bump in Step 01.4:
> ```kotlin
> val MIGRATION_<OLD>_<NEW> = object : Migration(<OLD>, <NEW>) {
>     override fun migrate(database: SupportSQLiteDatabase) {
>         database.execSQL("""
>             CREATE TABLE IF NOT EXISTS device_profile (
>                 id INTEGER PRIMARY KEY,
>                 type TEXT NOT NULL,
>                 source TEXT NOT NULL,
>                 confidence TEXT NOT NULL,
>                 preset_version INTEGER,
>                 applied_at_install_time INTEGER NOT NULL,
>                 last_modified INTEGER NOT NULL
>             )
>         """)
>     }
> }
> ```
> Add the migration to AppDatabase builder (e.g., Room.databaseBuilder(...).addMigrations(MIGRATION_X_Y, ...))

**Verification:**

- `Glob` - file `app_v2/src/main/java/com/sza/fastmediasorter/data/db/migration/Migration*.kt` exists.
- `Grep` - `val MIGRATION_` followed by version numbers.
- `Grep` - `CREATE TABLE IF NOT EXISTS device_profile` present.
- `Grep` - migration added to `.addMigrations(...)` in AppDatabase or its provider.

**Status:** `[x] done`

**Step Log:**

- 2026-06-02 - Verification 4/4 PASS. Files: Migration31To32.kt (+20 LOC, MIGRATION_31_32 object), DatabaseModule.kt (import + addMigrations() registration).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 dq` exit 0.
- [x] `Grep` for `TODO(phase-01)` returns zero hits (code has no TODOs).
- [x] Dev log entries added (post-change runs completed for each file).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via post-change catalog_sync.

---

## Handoff Notes to Next Phase

Phase 01 establishes the data model, Room persistence layer, and repository/detector contracts. All phases 02–04 depend on these interfaces and enums. Room migration is complete; all flavor-specific profile behavior will be wired via DI in Phase 04.

---

## Rollback Plan

Revert Phase 01 commits - no data migration impact (only for fresh installs). Remove the new tables from manual devices via `adb shell` if debugging on a device with old schema.
