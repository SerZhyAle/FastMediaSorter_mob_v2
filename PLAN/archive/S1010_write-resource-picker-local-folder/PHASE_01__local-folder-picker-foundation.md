# Phase 01 - Local-folder-picker foundation

**Strategic spec:** [`../S1010_write-resource-picker-local-folder.md`](../S1010_write-resource-picker-local-folder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Wire `SettingsViewModel` to the three hidden-resource use cases S1009 already shipped
(`CheckLocalFolderWritableUseCase`, `ResolveLocalFolderResourceUseCase`, `CleanupHiddenResourceUseCase`), and
introduce `LocalFolderDestinationPickerManager` - the one shared class that detects the "Local Folder" leading
pseudo-item, drives the SAF folder pick, and completes the resolve/write-check/orphan-cleanup flow for any host
that constructs it. No UI insertion point is touched yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch (dirty tree tolerated per this repo's norm).
- [ ] `S1009` is `Verified` - confirmed (`AppDatabase.kt` is at `version = 44`; `CheckLocalFolderWritableUseCase.kt`,
      `ResolveLocalFolderResourceUseCase.kt`, `CleanupHiddenResourceUseCase.kt` all exist at
      `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/`).
- [ ] Strings `local_folder` and `error_folder_not_writable` already exist in `values/`, `values-ru/`,
      `values-uk/strings.xml` (confirmed during tactical research) - no new string work in this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | existing + ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/LocalFolderDestinationPickerManager.kt` | New | ≤ 120 |

---

## Steps

### Step 01.1 - Add the local-folder wrapper methods to SettingsViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three constructor params to `SettingsViewModel`'s existing `@Inject constructor` (alongside the already
> present `resourceRepository: ResourceRepository` and `getDestinationsUseCase: GetDestinationsUseCase`):
> `private val checkLocalFolderWritableUseCase: CheckLocalFolderWritableUseCase`,
> `private val resolveLocalFolderResourceUseCase: ResolveLocalFolderResourceUseCase`,
> `private val cleanupHiddenResourceUseCase: CleanupHiddenResourceUseCase` (all three already `@Inject
> constructor`-annotated, single dependency each - no new Hilt module or scope). Add three public suspend
> methods:
> - `suspend fun isLocalFolderWritable(path: String): Boolean = checkLocalFolderWritableUseCase(path)`.
> - `suspend fun resolveLocalFolderResource(path: String, name: String, isWritable: Boolean): MediaResource? { val id = resolveLocalFolderResourceUseCase(path, name, isWritable); return resourceRepository.getResourceById(id) }`.
> - `suspend fun cleanupPreviousHiddenDestination(previousResourceId: Long?) = cleanupHiddenResourceUseCase(previousResourceId)`.
> These mirror `ScheduledOperationsViewModel`'s existing `isFolderWritable`/`resolveResourceById`/orphan-cleanup
> wrappers from S1009 - same use cases, same call shape, different host ViewModel.

**Verification:**

- `Grep` - `checkLocalFolderWritableUseCase: CheckLocalFolderWritableUseCase` present in the constructor.
- `Grep` - `fun isLocalFolderWritable(path: String): Boolean` present.
- `Grep` - `fun resolveLocalFolderResource(path: String, name: String, isWritable: Boolean): MediaResource?` present.
- `Grep` - `fun cleanupPreviousHiddenDestination(previousResourceId: Long?)` present.

**Status:** `[x]` done

---

### Step 01.2 - Create LocalFolderDestinationPickerManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/LocalFolderDestinationPickerManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> New plain class (not Hilt-injected - constructed manually by each host Fragment, matching
> `OperationsScheduledManager`'s/`OperationsDestinationsManager`'s existing convention in this same package).
> Constructor: `(private val fragment: Fragment, private val viewModel: SettingsViewModel, private val
> folderPickerLauncher: ActivityResultLauncher<Uri?>)`.
>
> Companion object:
> - `const val LOCAL_FOLDER_RECEIVER_SENTINEL_ID = -1L` (Room ids are positive autoincrement; `-1L` never
>   collides with a real resource id, `0L` is already used elsewhere as "unset").
> - `fun sentinelItem(context: Context): MediaResource = MediaResource(id = LOCAL_FOLDER_RECEIVER_SENTINEL_ID,
>   name = context.getString(R.string.local_folder), path = "", type = ResourceType.LOCAL)` (every other
>   `MediaResource` constructor param has a default - confirmed in `domain/model/Models.kt`).
> - `fun isSentinelSelection(item: MediaResource): Boolean = item.id == LOCAL_FOLDER_RECEIVER_SENTINEL_ID`.
>
> Instance state: `private var pendingCompletion: ((MediaResource?) -> Unit)? = null` and `private var
> pendingPreviousId: Long? = null` (mirrors `OperationsScheduledManager`'s `pendingPickSide` pattern - one
> in-flight pick at a time, correlated across the async SAF round-trip).
>
> `fun wrapOnSelected(previousResourceId: Long?, onPicked: (MediaResource?) -> Unit): (MediaResource?) -> Unit`
> returns a lambda that: if the selected item is the sentinel, stores `onPicked` into `pendingCompletion` and
> `previousResourceId` into `pendingPreviousId`, then calls `folderPickerLauncher.launch(null)`; otherwise calls
> `completeSelection(previousResourceId, selected, onPicked)` directly (covers both a normal row tap and the
> dialog's Clear action, since both route through whatever `ListSelectionConfig.onSelected` this method
> produces).
>
> `fun onFolderPicked(uri: Uri?)` - call from the host's registered launcher callback. Take `pendingCompletion`/
> `pendingPreviousId` and clear both fields immediately (same one-shot-consume pattern as
> `OperationsScheduledManager.onFolderPicked`); if `uri == null` or no pending completion, return. Otherwise:
> take a persistable URI permission (`FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION`,
> catching `SecurityException` with a `Timber.w` exactly like `OperationsScheduledManager.onFolderPicked` does),
> then on `fragment.viewLifecycleOwner.lifecycleScope.launch`: compute `path = uri.toString()`, call
> `viewModel.isLocalFolderWritable(path)`; if `false`, toast `R.string.error_folder_not_writable` and return
> (no resource created, setting unchanged); if `true`, compute `name =
> DocumentFile.fromTreeUri(fragment.requireContext(), uri)?.name ?: uri.lastPathSegment ?: path`, call
> `viewModel.resolveLocalFolderResource(path, name, isWritable = true)`, then call
> `completeSelection(pendingPreviousId, resolved, pendingCompletion)`.
>
> `private fun completeSelection(previousId: Long?, selected: MediaResource?, onPicked: (MediaResource?) ->
> Unit)`: calls `onPicked(selected)` first (so the setting is reassigned/cleared immediately), then - only if
> `previousId != null && previousId != selected?.id` (guards against deleting the same hidden resource just
> resolved via dedup-reuse) - launches `fragment.viewLifecycleOwner.lifecycleScope.launch {
> viewModel.cleanupPreviousHiddenDestination(previousId) }`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/LocalFolderDestinationPickerManager.kt` exists.
- `Grep` - `class LocalFolderDestinationPickerManager` matches exactly once (declaration, not comment).
- `Grep` - `LOCAL_FOLDER_RECEIVER_SENTINEL_ID` and `fun isSentinelSelection(item: MediaResource): Boolean` both present.
- `Grep` - `fun wrapOnSelected(` and `fun onFolderPicked(uri: Uri?)` both present.
- `pwsh -NoProfile -File ./a.ps1 fk` exits 0 (Kotlin compile, standard flavor).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 fk` exits 0 (no consumer wired yet, so no `dq` needed
      until a phase actually reaches a UI entry point - Phase 02 runs the first `dq`).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for both files in "Files Touched".
- [x] No public API regen needed yet for the catalog - `LocalFolderDestinationPickerManager` is unused by any
      consumer until Phase 02; catalog sync happens once, at Phase 05.

---

## Step Log

- 2026-08-02 - Step 01.1: Verification 4/4 PASS. Files: ui/settings/SettingsViewModel.kt (+3 ctor params, +3 suspend wrappers, +3 imports). Dev log recorded. `post-change: PASS`.
- 2026-08-02 - Step 01.1 gate note: the +3 constructor params resurfaced two already-baselined detekt findings on this
  file (signature-keyed baseline entries stop matching on any param/import change). `ImportOrdering` fixed properly by
  sorting the whole import block; `LongParameterList` (accepted debt, frozen at 20 params, live count now 23) re-frozen
  by a surgical single-entry edit in `config/detekt/baseline-app_v2.xml` - not a project-wide `detektBaseline` regen,
  which would have captured other tickets' in-flight WIP.
- 2026-08-02 - Step 01.2: Verification 5/5 PASS (`a.ps1 fk` exit 0). Files: ui/settings/helpers/LocalFolderDestinationPickerManager.kt (new, 105 LOC). Dev log recorded. `post-change: PASS`.
- 2026-08-02 - Phase-boundary audit (Layer 1 + Layer 2 + Layer 3): no P0/P1/P2 findings. Layer 1 - manager sits in
  `ui/settings/helpers/`, orchestration only, all data work delegated through `SettingsViewModel` -> S1009 use cases,
  no `data` import from `ui`. Layer 2 - one-shot `pendingCompletion`/`pendingPreviousId` consumed and cleared before any
  suspension, so a cancelled SAF round-trip (`uri == null`) leaks no captured callback; coroutines run on
  `viewLifecycleOwner.lifecycleScope`, the same scope S1009's reviewed `OperationsScheduledManager.onFolderPicked`
  already uses for this exact SAF flow. Layer 3 - the `Fragment` reference is owned by the constructing Fragment
  (no static/singleton retention); `sentinelItem` takes `Context` as a parameter and never stores it. Layer 4 (Room)
  not applicable - no direct DAO/entity/migration surface in this phase.

---

## Handoff Notes to Next Phase

Phases 02/03/04 each: register their own `ActivityResultLauncher<Uri?>`, construct one
`LocalFolderDestinationPickerManager(fragment, viewModel, thatLauncher)`, prepend
`LocalFolderDestinationPickerManager.sentinelItem(context)` to whatever `loader` their picker already uses, and
route `onSelected`/`onResourceSelected` through `manager.wrapOnSelected(currentResourceId, onPicked)` instead of
passing the raw callback directly.

---

## Rollback Plan

Low-risk: both files are new-or-additive (no existing method signature removed, only new constructor params and
new public methods added to `SettingsViewModel`). Revert both files - no consumer exists yet in this phase, so
no cascading revert needed.
