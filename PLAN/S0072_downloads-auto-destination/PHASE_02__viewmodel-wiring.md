# Phase 02 — ViewModel Wiring

**Strategic spec:** [`../S0072_downloads-auto-destination.md`](../S0072_downloads-auto-destination.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Inject `ProvisionDownloadsDestinationUseCase` into `MainViewModel` and call it immediately after `provisionDefaultResourcesUseCase()` in the init block, so the Downloads destination is created on the same first-launch coroutine as the virtual resources.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `ProvisionDownloadsDestinationUseCase.kt` compiles successfully.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt` | Modified | ≤ 500 |

> `MainViewModel.kt` has ~300+ lines. A backup in `temp/` is required before editing (rule: file >500 LOC). Check current line count; if >500 after adding, create `temp/MainViewModel_<timestamp>.kt.backup` first.

---

## Steps

### Step 02.1 — Inject `ProvisionDownloadsDestinationUseCase` into `MainViewModel`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`
**Depends on:** — start of phase (Phase 01 complete)

**Prompt for developer:**

> In `MainViewModel.kt`, add `ProvisionDownloadsDestinationUseCase` as a constructor parameter immediately after `private val provisionDefaultResourcesUseCase: ProvisionDefaultResourcesUseCase`:
>
> ```kotlin
> private val provisionDownloadsDestinationUseCase: ProvisionDownloadsDestinationUseCase,
> ```
>
> Add the import:
> ```kotlin
> import com.sza.fastmediasorter.domain.usecase.ProvisionDownloadsDestinationUseCase
> ```
>
> Hilt will auto-provide the new use case — no `@Module` changes needed.

**Verification:**

- `Grep` — `provisionDownloadsDestinationUseCase: ProvisionDownloadsDestinationUseCase` present in `MainViewModel.kt`.
- `Grep` — `import com.sza.fastmediasorter.domain.usecase.ProvisionDownloadsDestinationUseCase` present in `MainViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: MainViewModel.kt (import + constructor param). Dev log deferred to phase end.

---

### Step 02.2 — Call provisioning in `init` block after virtual resources

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `MainViewModel.kt`, locate the `init` block. After the `provisionDefaultResourcesUseCase()` call (currently the first line of the coroutine body), add:
>
> ```kotlin
> provisionDownloadsDestinationUseCase()
> ```
>
> The resulting sequence must be:
> ```kotlin
> provisionDefaultResourcesUseCase()
> provisionDownloadsDestinationUseCase()
> migrateCameraResourceUseCase()
> migrateS0059UseCase()
> // ... rest unchanged
> ```
>
> No conditional wrapping — `ProvisionDownloadsDestinationUseCase.invoke()` already handles its own idempotency guard internally.

**Verification:**

- `Grep` with context — in `MainViewModel.kt`, the line `provisionDownloadsDestinationUseCase()` appears immediately after `provisionDefaultResourcesUseCase()` in the init coroutine body.
- `Grep` — `provisionDownloadsDestinationUseCase()` appears exactly once in `MainViewModel.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `MainViewModel.kt` (unchanged, just confirming no regressions).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: MainViewModel.kt (init block). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `.\build-debug.PS1` exit 0 (BUILD SUCCESSFUL in 48s).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `MainViewModel.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (modified class).

---

## Handoff Notes to Next Phase

- On first launch, `provisionDownloadsDestinationUseCase()` runs in the same init coroutine as virtual-resource provisioning.
- The Downloads resource is created with `isDestination = true` and visible in the player's destination panel.
- Phase 03 updates feature docs and runs catalog render.

---

## Rollback Plan

Revert phase commit(s). The Downloads destination entry may remain in the DB on devices where provisioning already ran; users can delete it via the normal resource-removal flow.
