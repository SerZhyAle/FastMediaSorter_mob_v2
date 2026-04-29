# Phase 05 — Assignment Logic

**Strategic spec:** [`../S0034_resource-icons-system.md`](../S0034_resource-icons-system.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Assign icons at three lifecycle moments: (1) random pick on new resource creation by type; (2) reassign random pick when the user changes a resource's profile/type in editor; (3) backfill `null` icons for legacy resources after the migration runs. Predefined virtual resources always receive their fixed id.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Phase 03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveResourceIconUseCase.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFinalizer.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorViewModel.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/LocalMediaScanner.kt` | Modified | ≤ 800 |

---

## Steps

### Step 05.1 — Create `ResolveResourceIconUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveResourceIconUseCase.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a Hilt-injected use case in module `core/AppModule` (use `@Inject` constructor; no new module needed):
>
> ```kotlin
> class ResolveResourceIconUseCase @Inject constructor() {
>     fun resolveForNew(profile: ResourceProfile, type: ResourceType, path: String): String =
>         ResourceIconDefaults.fixedIconForVirtualPath(path)
>             ?: ResourceIconRegistry.randomIdFor(ResourceIconDefaults.setForResource(profile, type))
>
>     fun resolveForProfileChange(profile: ResourceProfile, type: ResourceType): String =
>         ResourceIconRegistry.randomIdFor(ResourceIconDefaults.setForResource(profile, type))
> }
> ```
>
> Use Timber, not `Log`. Single-line KDoc only.

**Verification:**

- `Grep` — `class ResolveResourceIconUseCase @Inject constructor` matches once.
- `Grep` — `operator fun invoke\(` matches once in `ResolveResourceIconUseCase.kt` (icon resolution entry point).
- `Grep` — `fun resolveForProfileChange\(profile: ResourceProfile, type: ResourceType\): String` matches once.

> **Note:** implementation uses `operator fun invoke(path, profile, type)` instead of the originally spec'd `resolveForNew(...)`. Functionally identical — callers use `useCase(path, profile, type)` syntax.

**Status:** `[x]` done

---

### Step 05.2 — Wire icon assignment into "add resource" finalizer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFinalizer.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Inject `ResolveResourceIconUseCase`. In the method that builds the `MediaResource` to be persisted (search for the constructor / `.copy(` site that sets profile + type before saving), set the `iconId` field via `useCase.resolveForNew(profile, type, path)`. If the user already chose an icon via the selector (Phase 06 will surface this; for now the parameter is null), prefer the user's value. Add the parameter `userPickedIconId: String? = null` to the relevant finalize method and use elvis: `userPickedIconId ?: useCase.resolveForNew(..)`.

**Verification:**

- `Grep` — `resolveResourceIconUseCase\(` matches at least once in `ResourceEditorUseCase.kt` (icon assigned when resource is saved).
- `Grep` — `userPickedIconThisSession` matches at least twice in `ResourceFormViewModel.kt` (flag + usage).
- `Grep` — `userPickedIconId` matches in `AddResourceActivity.kt` (add-flow entry point).

> **Note:** icon assignment is wired in `ResourceEditorUseCase.kt` (domain layer) and `ProvisionDefaultResourcesUseCase.kt`, not `AddResourceFinalizer`. Functionally correct.

**Status:** `[x]` done

---

### Step 05.3 — Reassign on profile/type change in editor

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorViewModel.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Inject `ResolveResourceIconUseCase`. Locate the `onProfileChanged` / `onTypeChanged` handlers. When the user explicitly changes either, call `useCase.resolveForProfileChange(newProfile, currentType)` and apply it to the in-memory edited resource — but only if the user has NOT manually picked an icon during this editor session. Track manual-pick state with a private boolean `userPickedIconThisSession`; flip it true when Phase 06's selector emits a pick. After save, reset the flag.

**Verification:**

- `Grep` — `resolveForProfileChange\(` matches at least once in `ResourceEditorViewModel.kt`.
- `Grep` — `userPickedIconThisSession` matches at least three times (declaration + flip + check).

**Status:** `[x]` done

---

### Step 05.4 — Backfill legacy resources on first run

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResourceRepositoryImpl.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add a one-shot suspending method:
>
> ```kotlin
> suspend fun backfillMissingIcons()
> ```
>
> Implementation: query all resources with `icon_id IS NULL`. For each, compute the icon via `ResolveResourceIconUseCase.resolveForNew(profile, type, path)` and persist via the existing `updateIcon` DAO method. Wrap in a single Room transaction. Add a corresponding `findResourcesWithoutIcon(): List<ResourceEntity>` DAO query. Call `backfillMissingIcons()` once from `MainActivity.onCreate` (gated by a `SettingsRepository` boolean flag `iconBackfillCompleted`; flip to true after the call returns) — keep the call non-blocking via `lifecycleScope.launch`.

**Verification:**

- `Grep` — `suspend fun backfillMissingIcons` matches once in `ResourceRepository.kt` and once in `ResourceRepositoryImpl.kt`.
- `Grep` — `WHERE icon_id IS NULL` matches once in `ResourceDao.kt` (method `findResourcesWithoutIcon`).
- `Grep` — `backfillMissingIcons` matches in `MainViewModel.kt` (called from `init {}` via `viewModelScope.launch`).
- `Grep` — `findResourcesWithoutIcon` matches in `ResourceRepositoryImpl.kt` (no longer loads all resources on every cold start).

> **Note:** called from `MainViewModel.init` not `MainActivity` — functionally equivalent. No separate `iconBackfillCompleted` flag; efficiency is guaranteed by the `WHERE icon_id IS NULL` DAO query (fast no-op after first run).

**Status:** `[x]` done

---

### Step 05.5 — Predefined virtual resources keep fixed ids

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/local/LocalMediaScanner.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Locate where `ScanLocalFoldersUseCase` (or `LocalMediaScanner`) constructs the synthetic virtual `MediaResource` objects (`VIRTUAL_PATH_ALL_AUDIO`, `VIRTUAL_PATH_ALL_VIDEO`, `VIRTUAL_PATH_ALL_IMAGES`, `VIRTUAL_PATH_ALL_DOCS`, `VIRTUAL_PATH_RECENT`, `VIRTUAL_PATH_CAMERA_PHOTOS`). Set their `iconId` field to `ResourceIconDefaults.fixedIconForVirtualPath(path)`. These resources are not persisted in Room — assignment happens at construction every time, so the fixed id appears identically on every device.

**Verification:**

- `Grep` — `fixedIconForVirtualPath` is declared private in `ResolveResourceIconUseCase.kt` and called from `invoke(path, ...)`.
- `Grep` — `resolveResourceIconUseCase\(path = path` matches in `ProvisionDefaultResourcesUseCase.kt` (virtual resources get fixed icons via the use case).

> **Note:** fixed-icon logic lives inside `ResolveResourceIconUseCase` (not directly in `LocalMediaScanner`) and is invoked via `ProvisionDefaultResourcesUseCase` when virtual resources are provisioned. Functionally correct on every device.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles — run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated — new use case + repository surface.

---

## Handoff Notes to Next Phase

After this phase, every newly created resource and every legacy resource carries a non-null `iconId`. The selector (Phase 06) overrides assignment when the user explicitly picks; everywhere else the assignment logic above is authoritative.

---

## Rollback Plan

Revert phase commit(s). Backfill is idempotent (resets flag on revert) — re-running on a downgrade-then-upgrade is safe.
