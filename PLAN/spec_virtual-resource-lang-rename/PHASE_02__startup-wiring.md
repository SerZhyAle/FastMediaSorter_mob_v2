# Phase 02 — startup-wiring

**Strategic spec:** [`../spec_virtual-resource-lang-rename.md`](../spec_virtual-resource-lang-rename.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-04-26
**Completed:** 2026-04-26

---

## Objective

Wire `RenameVirtualResourcesUseCase` into `AppStartupInitializer` so it runs automatically on every app start before the UI is shown.

---

## Prerequisites

- [ ] Phase 01 is `✅ Done`.
- [ ] `RenameVirtualResourcesUseCase.kt` and `VirtualResourceDefaultNames.kt` compile cleanly.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
| --- | --- | --- |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 500 |

> Both files are under 500 LOC after modification — no backup required.

---

## Steps

### Step 2.1 — Add use-case parameter to AppStartupInitializer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `AppStartupInitializer`, add a new constructor parameter `private val renameVirtualResourcesUseCase: RenameVirtualResourcesUseCase` (import from `com.sza.fastmediasorter.domain.usecase`). Place it as the last parameter after `applicationScope: CoroutineScope`. No other changes to the constructor.

**Verification:**

- `Grep` — `renameVirtualResourcesUseCase: RenameVirtualResourcesUseCase` appears in `AppStartupInitializer.kt`.
- `Grep` — `import com.sza.fastmediasorter.domain.usecase.RenameVirtualResourcesUseCase` appears in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 2.2 — Add renameVirtualResourceNames() call in initialize()

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `AppStartupInitializer`, add a private function `renameVirtualResourceNames()` that launches a coroutine on `applicationScope`:
>
> ```kotlin
> private fun renameVirtualResourceNames() {
>     applicationScope.launch {
>         renameVirtualResourcesUseCase()
>     }
> }
> ```
>
> Add a call to `renameVirtualResourceNames()` inside `initialize()`, after `fixLocalResourcesWritableFlag()` and before `cleanupPlaybackPositions()`. This ordering ensures writable-flag fixes run before the rename pass reads resource data.

**Verification:**

- `Grep` — `fun renameVirtualResourceNames()` appears in `AppStartupInitializer.kt`.
- `Grep` — `renameVirtualResourceNames()` matches at least twice in `AppStartupInitializer.kt` (function definition + call site).
- `Grep` — `renameVirtualResourcesUseCase()` appears in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

### Step 2.3 — Inject use-case in FastMediaSorterApp and pass to AppStartupInitializer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `FastMediaSorterApp`:
>
> 1. Add an injected field after the existing `@Inject lateinit var resourceRepository` block:
>
>    ```kotlin
>    @Inject
>    lateinit var renameVirtualResourcesUseCase: RenameVirtualResourcesUseCase
>    ```
>
>    Import from `com.sza.fastmediasorter.domain.usecase.RenameVirtualResourcesUseCase`.
>
> 2. In the `AppStartupInitializer(...)` constructor call inside `onCreate()`, add `renameVirtualResourcesUseCase = renameVirtualResourcesUseCase` as the last named argument (after `applicationScope = applicationScope`).

**Verification:**

- `Grep` — `lateinit var renameVirtualResourcesUseCase: RenameVirtualResourcesUseCase` appears in `FastMediaSorterApp.kt`.
- `Grep` — `@Inject` appears in `FastMediaSorterApp.kt` (field is annotated — presence check sufficient).
- `Grep` — `renameVirtualResourcesUseCase = renameVirtualResourcesUseCase` appears in that file.
- `Grep` — `import com.sza.fastmediasorter.domain.usecase.RenameVirtualResourcesUseCase` appears in that file.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 2.*` above is `[x] done`.
- [x] Project compiles — run the `/build` skill.
- [x] `Grep` for `Log\.d\(` in both modified files returns zero hits.
- [x] Dev log entries added for both modified files via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` (constructor signature of `AppStartupInitializer` changed).

---

## Handoff Notes to Next Phase

After this phase, `RenameVirtualResourcesUseCase` runs automatically on every app start. Phase 03 only handles docs, catalog, and dev log — no code changes.

---

## Rollback Plan

Revert the constructor parameter addition and the inject field. No data migration, no DB change.

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all six)
  - ACCEPT applied: 0
  - REVIEW applied: 2 (Step 2.2 ordering predicate → count-based; Step 2.3 line-relative predicate → field-exists check)
  - DISCUSS proposed: 0 items — phase clean
