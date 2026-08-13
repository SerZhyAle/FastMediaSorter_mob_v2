# Phase 03 — Browse Wiring

**Strategic spec:** [`../S0159_file-ops-overflow-menu.md`](../S0159_file-ops-overflow-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Wire `BrowseObserverManager` to push `fileOpsInOverflowMenu` to the adapter; wire `BrowseManagerInitializer` to pass `BrowseFileOverflowMenuManager` as the `onOverflowMenuClick` callback; implement the one-time Toast hint.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseObserverManager.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 550 |

---

## Steps

### Step 3.1 — Observe `fileOpsInOverflowMenu` in `BrowseObserverManager`

**Files:** `ui/browse/managers/BrowseObserverManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `BrowseObserverManager.startAll()`, add a call to `observeFileOpsOverflowMenu()` after `observeHideGridActionButtons()`.
>
> Add the method:
> ```kotlin
> private fun observeFileOpsOverflowMenu() {
>     lifecycleOwner.collectOnLifecycle(settingsRepository.getSettings()) { settings ->
>         adapter.setFileOpsInOverflowMenu(settings.fileOpsInOverflowMenu)
>     }
> }
> ```

**Verification:**

- `Grep` — `observeFileOpsOverflowMenu()` called inside `startAll()` in `BrowseObserverManager.kt`.
- `Grep` — `fun observeFileOpsOverflowMenu` present in `BrowseObserverManager.kt`.
- `Grep` — `adapter.setFileOpsInOverflowMenu` present in `BrowseObserverManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: `ui/browse/managers/BrowseObserverManager.kt` (+7 LOC). Dev log recorded.

---

### Step 3.2 — Inject `BrowseFileOverflowMenuManager` into `BrowseManagerInitializer`

**Files:** `ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `BrowseManagerInitializer`, add `BrowseFileOverflowMenuManager` as a constructor parameter alongside `resourceOpsMenuManager`. The exact injection mechanism mirrors `resourceOpsMenuManager` (field or constructor param — match the existing pattern).
>
> Concretely, if `BrowseManagerInitializer` receives managers via constructor, add:
> ```kotlin
> private val browseFileOverflowMenuManager: com.sza.fastmediasorter.ui.browse.helpers.BrowseFileOverflowMenuManager,
> ```

**Verification:**

- `Grep` — `BrowseFileOverflowMenuManager` present in `BrowseManagerInitializer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 1/1 PASS. Files: `ui/browse/managers/BrowseManagerInitializer.kt` (+1 param). Dev log recorded.

---

### Step 3.3 — Wire `onOverflowMenuClick` in `MediaFileAdapter` construction

**Files:** `ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Step 3.2

**Prompt for developer:**

> In the `MediaFileAdapter(...)` constructor call block inside `BrowseManagerInitializer` (around line 152), add the `onOverflowMenuClick` parameter after `onBinaryFileClick`:
>
> ```kotlin
> onOverflowMenuClick = { file, anchor ->
>     val state = viewModel.state.value
>     browseFileOverflowMenuManager.showFor(
>         anchor = anchor,
>         file = file,
>         appSettings = settingsRepository.getSettings().value ?: return@MediaFileAdapter,
>         isWritable = state.resource?.isReadOnly == false,
>         hasDestinations = state.destinations.isNotEmpty(),
>         isGridMode = adapter.isGridMode,  // expose via a getter if needed
>         onCopy = { f -> viewModel.selectFile(f.path); showCopyDialog() },
>         onMove = { f -> viewModel.selectFile(f.path); showMoveDialog() },
>         onRename = { f -> viewModel.selectFile(f.path); showRenameDialog() },
>         onDelete = { f -> viewModel.selectFile(f.path); showDeleteConfirmation() }
>     )
> },
> ```
>
> Note: `settingsRepository.getSettings()` returns a `Flow`. Use `.value` only if it is a `StateFlow`, otherwise cache last-known settings via the observer established in `BrowseObserverManager`. Adapt as needed to match the actual type. The key invariant is: `AppSettings` is read synchronously from a cached state, not triggering a new collector.
>
> Expose a public `val isGridMode: Boolean` getter on `MediaFileAdapter` if not already present, or capture it from the ViewModel state.

**Verification:**

- `Grep` — `onOverflowMenuClick` present in `BrowseManagerInitializer.kt`.
- `Grep` — `browseFileOverflowMenuManager.showFor` present in `BrowseManagerInitializer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. Files: `ui/browse/managers/BrowseManagerInitializer.kt` (+18 LOC). Dev log recorded.

---

### Step 3.4 — Ensure `BrowseFileOverflowMenuManager` injection reaches `BrowseActivity`

**Files:** `ui/browse/managers/BrowseManagerInitializer.kt` (or `BrowseActivity.kt` if injection is there)
**Depends on:** Step 3.2

**Prompt for developer:**

> Locate where `BrowseManagerInitializer` is constructed (likely in `BrowseActivity`). Add `BrowseFileOverflowMenuManager` as an `@Inject`ed field in `BrowseActivity` (or wherever `BrowseManagerInitializer` is instantiated), and pass it to the initializer's constructor.
>
> If `BrowseActivity` uses field injection (Hilt `@AndroidEntryPoint`):
> ```kotlin
> @Inject lateinit var browseFileOverflowMenuManager: BrowseFileOverflowMenuManager
> ```
> Then pass it to `BrowseManagerInitializer(... browseFileOverflowMenuManager = browseFileOverflowMenuManager ...)`.
>
> `BrowseFileOverflowMenuManager` is `@ActivityScoped`, so Hilt already scopes it to `BrowseActivity`'s component.

**Verification:**

- `Grep` — `@Inject` and `BrowseFileOverflowMenuManager` co-occur in the same file where `BrowseManagerInitializer` is created.
- Project compiles.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 1/1 PASS. Files: `ui/browse/BrowseActivity.kt` (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 3.* above is `[x] done`.
- [x] Project compiles — run `/build`.
- [ ] In a manual smoke test (optional at this stage): toggle `fileOpsInOverflowMenu = true` via a temporary test ViewModel call and verify the ⋮ button appears and opens a `PopupMenu` with copy/move/rename/delete items.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- The adapter, observer, and menu manager are fully wired. Tapping ⋮ on a file row opens a `PopupMenu` with the correct items filtered by permissions/settings.
- Phase 04 adds the settings UI toggle so the user can enable the feature. Phase 05 does final cleanup.

---

## Rollback Plan

Revert phase commit(s). No data migration. The feature remains unreachable via UI until Phase 04 lands.
