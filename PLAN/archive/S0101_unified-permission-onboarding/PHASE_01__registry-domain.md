# Phase 01 — Registry Domain

**Strategic spec:** [`../S0101_unified-permission-onboarding.md`](../S0101_unified-permission-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 5 / 5
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Introduce the declarative permission domain model — `PermissionEntry`, `PermissionGroup`, repository interfaces, and two use cases — with no implementation or UI changes.

---

## Prerequisites

- [ ] All Pre-Implementation Blockers in INDEX.md are resolved (research §6.1–§6.5).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PermissionRegistryRepository.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ContextualRationaleRepository.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MarkContextualShownUseCase.kt` | New | ≤ 40 |

---

## Steps

### Step 1.1 — Create PermissionEntry domain model

**Files:** `domain/model/PermissionEntry.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `PermissionEntry.kt` in `domain/model/` containing:
> - `enum class PermissionGroup { STORAGE, NETWORK, MICROPHONE, NOTIFICATION, CAMERA, SYSTEM, VR }`
> - `enum class PermissionStatus { GRANTED, DENIED, PERMANENTLY_DENIED, NOT_APPLICABLE }`
> - `data class PermissionEntry(val id: String, val manifestName: String, val titleRes: Int, val descriptionRes: Int, val iconRes: Int, val group: PermissionGroup, val optional: Boolean, val minSdk: Int = 0, val maxSdk: Int = Int.MAX_VALUE, val flavorGates: Set<String> = emptySet())` where `flavorGates` is empty (= all flavors) or contains the BuildConfig field names that must be `true` for this entry to apply (e.g. `"SUPPORT_AUDIO"`).
> - `data class PermissionGroupHeader(val group: PermissionGroup, val titleRes: Int)`

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PermissionEntry.kt` exists.
- `Grep` — `enum class PermissionGroup` matches exactly once in that file.
- `Grep` — `data class PermissionEntry` matches exactly once in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: domain/model/PermissionEntry.kt (new, 22 LOC). Dev log recorded.

---

### Step 1.2 — Create PermissionRegistryRepository interface

**Files:** `domain/repository/PermissionRegistryRepository.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Create `PermissionRegistryRepository.kt` in `domain/repository/` with a single interface:
> `interface PermissionRegistryRepository { fun getEntries(): List<PermissionEntry>; fun getGroups(): List<PermissionGroupHeader> }`
> Both methods return a snapshot of the registry filtered to entries applicable to the current build (flavor gates + API level). No suspend — the registry is in-memory.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/PermissionRegistryRepository.kt` exists.
- `Grep` — `interface PermissionRegistryRepository` matches exactly once.
- `Grep` — `fun getEntries()` present in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: domain/repository/PermissionRegistryRepository.kt (new, 9 LOC). Dev log recorded.

---

### Step 1.3 — Create ContextualRationaleRepository interface

**Files:** `domain/repository/ContextualRationaleRepository.kt`
**Depends on:** Step 1.1

**Prompt for developer:**

> Create `ContextualRationaleRepository.kt` in `domain/repository/` with:
> `interface ContextualRationaleRepository { fun isShown(permissionId: String): Boolean; fun markShown(permissionId: String) }`
> Purpose: tracks, per `PermissionEntry.id`, whether the contextual rationale bottom sheet was already shown to the user once (so it is never shown a second time).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ContextualRationaleRepository.kt` exists.
- `Grep` — `interface ContextualRationaleRepository` matches exactly once.
- `Grep` — `fun isShown(permissionId: String)` present in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: domain/repository/ContextualRationaleRepository.kt (new, 6 LOC). Dev log recorded.

---

### Step 1.4 — Create CheckPermissionStatusUseCase

**Files:** `domain/usecase/CheckPermissionStatusUseCase.kt`
**Depends on:** Steps 1.1, 1.2

**Prompt for developer:**

> Create `CheckPermissionStatusUseCase.kt` in `domain/usecase/`. It accepts `Context` and `PermissionEntry` and returns `PermissionStatus`. Logic:
> - If `entry.minSdk > Build.VERSION.SDK_INT` or `entry.maxSdk < Build.VERSION.SDK_INT` → `NOT_APPLICABLE`.
> - Else if `ContextCompat.checkSelfPermission` returns `PERMISSION_GRANTED` → `GRANTED`.
> - Else if `ActivityCompat.shouldShowRequestPermissionRationale` returns `false` and the permission was previously requested (tracked externally) → `PERMANENTLY_DENIED`.
> - Else → `DENIED`.
> Note: special permissions (`MANAGE_EXTERNAL_STORAGE`, `MANAGE_MEDIA`, battery optimization) require dedicated checks via their own system APIs — the use case must delegate those per `entry.manifestName` using existing `PermissionHelper` methods.
> Annotate with `@Singleton` and inject `Context` with `@ApplicationContext`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/CheckPermissionStatusUseCase.kt` exists.
- `Grep` — `class CheckPermissionStatusUseCase` matches exactly once.
- `Grep` — `fun invoke` or `operator fun invoke` present in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 4/4 PASS. Files: domain/usecase/CheckPermissionStatusUseCase.kt (new, 52 LOC). Dev log recorded.

---

### Step 1.5 — Create MarkContextualShownUseCase

**Files:** `domain/usecase/MarkContextualShownUseCase.kt`
**Depends on:** Steps 1.1, 1.3

**Prompt for developer:**

> Create `MarkContextualShownUseCase.kt` in `domain/usecase/`. Simple delegator:
> `class MarkContextualShownUseCase @Inject constructor(private val repo: ContextualRationaleRepository) { fun invoke(permissionId: String) = repo.markShown(permissionId) }`
> Also add a companion query method `fun isShown(permissionId: String) = repo.isShown(permissionId)`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MarkContextualShownUseCase.kt` exists.
- `Grep` — `class MarkContextualShownUseCase` matches exactly once.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 2/2 PASS. Files: domain/usecase/MarkContextualShownUseCase.kt (new, 12 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 1.*` above is `[x] done`.
- [x] Project compiles — run `/build`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 01 establishes the domain contract. Phase 02 provides the in-memory implementation and Hilt wiring. No UI or runtime behavior changes until Phase 03.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
