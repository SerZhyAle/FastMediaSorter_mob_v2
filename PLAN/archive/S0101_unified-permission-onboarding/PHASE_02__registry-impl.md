# Phase 02 — Registry Implementation + DI

**Strategic spec:** [`../S0101_unified-permission-onboarding.md`](../S0101_unified-permission-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05, 06
**Steps done:** 4 / 4
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Provide the in-memory `PermissionRegistryRepositoryImpl` (full list of known permissions for all flavors/API levels), a `SharedPreferences`-backed `ContextualRationaleRepositoryImpl`, and the Hilt module wiring them.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Research §6.2 (API 23–25 behavior) and §6.5 (storage choice) are resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/ContextualRationaleRepositoryImpl.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/PermissionModule.kt` | New | ≤ 50 |

---

## Steps

### Step 2.1 — Create PermissionRegistryRepositoryImpl

**Files:** `data/permissions/PermissionRegistryRepositoryImpl.kt`
**Depends on:** Phase 01 complete

**Prompt for developer:**

> Create `PermissionRegistryRepositoryImpl.kt` in `data/permissions/`. Implement `PermissionRegistryRepository`.
>
> The `entries` list must cover all runtime-requested permissions visible in the merged manifest (result of research §6.1):
> - STORAGE group: `READ_EXTERNAL_STORAGE` (minSdk 23, maxSdk 32), `READ_MEDIA_IMAGES/VIDEO/AUDIO` (minSdk 33), `MANAGE_EXTERNAL_STORAGE` (minSdk 30), `MANAGE_MEDIA` (minSdk 31) — all optional=false.
> - NETWORK group: `ACCESS_LOCAL_NETWORK` (minSdk 37) — optional=true, flavorGate `SUPPORT_SMB_SCANNING` or equivalent.
> - MICROPHONE group: `RECORD_AUDIO` — optional=true, flavorGate `SUPPORT_AUDIO`.
> - NOTIFICATION group: `POST_NOTIFICATIONS` (minSdk 33) — optional=true, flavorGate `ENABLE_PERSISTENT_AUDIO_PLAYBACK`.
> - SYSTEM group: battery optimization (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — optional=true.
> - VR group (flavorGate `SUPPORT_VR_PLAYER`): hand tracking (`com.oculus.permission.HAND_TRACKING`) — optional=true; passthrough camera (`horizonos.permission.HEADSET_CAMERA`, related to S0058) — optional=true.
>
> `getEntries()` filters by `Build.VERSION.SDK_INT` range and evaluates `flavorGates` against `BuildConfig` via reflection (field name → Boolean value; non-existent field → gate fails → exclude).
> `getGroups()` returns one `PermissionGroupHeader` per `PermissionGroup` that has at least one applicable entry, in display order: STORAGE, NETWORK, MICROPHONE, NOTIFICATION, CAMERA, SYSTEM, VR.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` exists.
- `Grep` — `class PermissionRegistryRepositoryImpl` matches exactly once.
- `Grep` — `READ_MEDIA_IMAGES` present (confirms storage group entries).
- `Grep` — `RECORD_AUDIO` present (confirms microphone entry).
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 5/5 PASS. Files: data/permissions/PermissionRegistryRepositoryImpl.kt (new, 120 LOC). Dev log recorded.

---

### Step 2.2 — Create ContextualRationaleRepositoryImpl

**Files:** `data/permissions/ContextualRationaleRepositoryImpl.kt`
**Depends on:** Phase 01 complete

**Prompt for developer:**

> Create `ContextualRationaleRepositoryImpl.kt` in `data/permissions/`. Implement `ContextualRationaleRepository` using `SharedPreferences` (prefs name `"perm_rationale_prefs"`).
> `isShown(id)` → `prefs.getBoolean(id, false)`.
> `markShown(id)` → `prefs.edit().putBoolean(id, true).apply()`.
> Wrap disk reads in `StrictModeHelper.allowDiskReads { }` and writes in `StrictModeHelper.allowDiskWrites { }`, consistent with `WelcomeViewModel`.
> Annotate class with `@Singleton`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/ContextualRationaleRepositoryImpl.kt` exists.
- `Grep` — `class ContextualRationaleRepositoryImpl` matches exactly once.
- `Grep` — `perm_rationale_prefs` present (confirms prefs key).

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: data/permissions/ContextualRationaleRepositoryImpl.kt (new, 27 LOC). Dev log recorded.

---

### Step 2.3 — Create PermissionModule Hilt module

**Files:** `di/PermissionModule.kt`
**Depends on:** Steps 2.1, 2.2

**Prompt for developer:**

> Create `PermissionModule.kt` in `di/`. Annotate with `@Module @InstallIn(SingletonComponent::class)`.
> Provide:
> - `@Provides @Singleton fun providePermissionRegistryRepository(impl: PermissionRegistryRepositoryImpl): PermissionRegistryRepository = impl`
> - `@Provides @Singleton fun provideContextualRationaleRepository(impl: ContextualRationaleRepositoryImpl): ContextualRationaleRepository = impl`

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/di/PermissionModule.kt` exists.
- `Grep` — `@InstallIn(SingletonComponent` present in that file.
- `Grep` — `PermissionRegistryRepository` present in that file.
- `Grep` — `ContextualRationaleRepository` present in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 4/4 PASS. Files: di/PermissionModule.kt (new, 25 LOC). Dev log recorded.

---

### Step 2.4 — Verify compile

**Files:** (none new)
**Depends on:** Steps 2.1, 2.2, 2.3

**Prompt for developer:**

> Run `/build` (standard debug flavor). The build must complete without errors. Fix any Hilt injection or import issues before marking done.

**Verification:**

- `/build` exits with code 0 for `standardDebug`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Build SUCCESSFUL (43s). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 2.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 02 delivers a fully wired, injectable permission registry. Phases 03 and 04 can start in parallel after this phase is done.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
