# Phase 01 - Pin-on-create link inside the creation flow

**Strategic spec:** [`../S1423_launcher-create-resource-shortcut.md`](../S1423_launcher-create-resource-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Make the Add Resource screen able to pin a home-screen shortcut for the resource it just created, switched on by an intent flag that defaults to off; no launcher-side change yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - both are.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/S1423/` exists for the Rule 5 backups this phase takes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AddResourceUseCase.kt` | Modified | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt` | Modified | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceVirtualCoordinator.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSmbCoordinator.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpKeyCoordinator.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceSftpFtpCoordinator.kt` | Modified | ≤ 12 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceCompanionCoordinator.kt` | Modified | ≤ 6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/helpers/CreatedResourcePinManager.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 30 |

> Backup / split thresholds: `AddResourceViewModel.kt` (623 LOC) and `AddResourceActivity.kt` (537 LOC) are both over 500 LOC - each carries a Rule 5 backup sub-step. No file in this phase approaches the Rule 2 1500-LOC ceiling.

---

## Steps

### Step 01.1 - Return the inserted row ids from `addMultiple`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/AddResourceUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `AddMultipleResult` reports how many rows were inserted but not which. Add `val createdResourceIds: List<Long> = emptyList()` to it, and change the insert loop (currently `resourcesToAdd.forEach { repository.addResource(it) }`) to collect the `Long` each `repository.addResource` call returns, then pass the collected list into the `AddMultipleResult` constructor. The list covers the inserted rows only - rows taken by the `matchExistingByPath` update path are updates, not creations, and must not appear in it. Keep the collected order aligned 1:1 with `resourcesToAdd`, which already drives `addedNames` in the same constructor. Leave the single-resource `invoke()` alone; it already returns `Result<Long>`.

**Why:**

Strategic §5.1.2 puts the pin where creation success is determined, and ADR-1 makes that the finalizing step of the shared flow rather than the calling screen. Seven of the nine sites that announce success route through `addMultiple`, which discards every id the repository returns, so without this the success signal cannot name the resource to pin and ADR-1 has nothing to bind to.

**Verification:**

- `Grep` - `createdResourceIds` matches in `AddResourceUseCase.kt`.
- `Grep` - `resourcesToAdd.forEach { repository.addResource(it) }` returns zero hits in that file.
- `Grep` - `addedCount = resourcesToAdd.size` still present (the count semantics are unchanged).
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 01.2 - Give `ResourcesAdded` the ids of what it created

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceViewModel.kt`, `AddResourceVirtualCoordinator.kt`, `AddResourceSmbCoordinator.kt`, `AddResourceSftpKeyCoordinator.kt`, `AddResourceSftpFtpCoordinator.kt`, `AddResourceCompanionCoordinator.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Back up `AddResourceViewModel.kt` to `temp/S1423/` first (Rule 5, 623 LOC).
>
> Change `object ResourcesAdded : AddResourceEvent()` to `data class ResourcesAdded(val createdResourceIds: List<Long>) : AddResourceEvent()`. The compiler then names all nine emit sites; fill each with the ids that path actually created:
>
> - `AddResourceViewModel.addManualResource` - the `onSuccess { id -> }` lambda already binds the id; pass `listOf(id)`.
> - `AddResourceViewModel.addSelectedResources` - pass `addResult.createdResourceIds`.
> - `AddResourceVirtualCoordinator` - the `addResourceUseCase(resource)` call discards its `Result<Long>`; capture it and pass the id.
> - `AddResourceSmbCoordinator` (both sites), `AddResourceSftpKeyCoordinator`, `AddResourceSftpFtpCoordinator` (both sites) - these bind the `addMultiple` result as `_`; bind it by name and pass its `createdResourceIds`.
> - `AddResourceCompanionCoordinator` - pass `emptyList()` and add one comment line stating that a companion config import restores many resources at once and is not the single-resource creation act this ticket pins for.
>
> Do not change what any of these paths insert, scan, or message - only what the success event carries.

**Why:**

Strategic ADR-1 requires the pin to be initiated where success is already known rather than by the calling screen, because the alternative lets creation-from-app and creation-from-launcher drift apart; a success signal that names no subject cannot satisfy that, and §2 goal 4 additionally needs the signal to be the one thing neither cancel nor failure emits.

**Verification:**

- `Grep` - `data class ResourcesAdded(val createdResourceIds: List<Long>)` matches exactly once in `AddResourceViewModel.kt`.
- `Grep` - `AddResourceEvent.ResourcesAdded)` (the bare no-argument form) returns zero hits across `app_v2/src`.
- `Grep` - `onSuccess { _ ->` returns zero hits in `AddResourceSmbCoordinator.kt`, `AddResourceSftpKeyCoordinator.kt`, `AddResourceSftpFtpCoordinator.kt`.
- `Grep` - `Log\.d\(` returns zero hits in every file this step modifies.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 01.3 - Add `CreatedResourcePinManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/helpers/CreatedResourcePinManager.kt` (New)
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `CreatedResourcePinManager`, a `@Singleton` with `@Inject constructor(private val resourceRepository: ResourceRepository, private val shortcutPinManager: ResourceShortcutPinManager)`, exposing one suspend function that takes a `Context` and a `List<Long>` of resource ids and returns a nullable `@StringRes Int` message for the caller to show.
>
> For each id: load the row with `resourceRepository.getResourceById(id)`, skip a null, build the icon with `ResourceIconComposer.compose(context, resource)`, and call `shortcutPinManager.requestPin(resource.id, resource.name, icon)`. Reuse `ResourceShortcutPinManager` unchanged - it already answers `PinResult.Unsupported` when `ShortcutManagerCompat.isRequestPinShortcutSupported` is false or the launcher refuses.
>
> Return `R.string.resource_shortcut_unsupported` when any id came back `Unsupported`, and `null` when every pin was requested. Both strings already exist and need no new key. Take the `Context` as a parameter rather than injecting `@ApplicationContext`, because the composed icon resolves theme attributes and must come from the visible Activity - `StreamShortcutPinManager` takes its context the same way and for the same reason. Read the row on `Dispatchers.IO`; compose and request the pin on the caller's thread.

**Why:**

Strategic §4 records that shortcut pinning already exists in the widget layer with a working stream-side sibling as the applied example, and ADR-2 forbids writing any of it again, so this class is glue and nothing else; §5.1.3 additionally requires an explicit branch for a platform or home screen that cannot pin, so the unavailable answer has to be carried back to the caller rather than dropped.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/helpers/CreatedResourcePinManager.kt` exists.
- `Grep` - `class CreatedResourcePinManager` matches exactly once in that file.
- `Grep` - `ResourceShortcutPinManager` and `ResourceIconComposer` both match in that file.
- `Grep` - `R.string.resource_shortcut_unsupported` matches in that file.
- `Grep` - `ShortcutManagerCompat` returns zero hits in that file (pinning is not reimplemented).
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

### Step 01.4 - Add the call flag and wire the pin to success

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Back up `AddResourceActivity.kt` to `temp/S1423/` first (Rule 5, 537 LOC).
>
> Add `EXTRA_PIN_SHORTCUT_ON_CREATE` to the companion object and a `pinShortcutOnCreate: Boolean = false` parameter to `createIntent`, written into the intent only when true. Read it in `onCreate` into a private val.
>
> Replace the `AddResourceEvent.ResourcesAdded -> finish()` branch with a call to a private routing function that takes `event.createdResourceIds`. When the flag is false it calls `finish()` unchanged. When true it launches on `lifecycleScope`, awaits the injected `CreatedResourcePinManager`, shows the returned message res as a `Toast` when it is non-null, and only then calls `finish()` - finishing first would cancel the scope before the pin is requested. Show no Toast when the manager returns null: the resource-added message has already fired and the shortcut appearing is the confirmation.
>
> Inject the manager with `@Inject lateinit var`. Keep the new function to routing only - loading rows, composing icons and requesting pins stay in the manager (Rule 3).

**Why:**

Strategic §5.3 requires the success-to-shortcut link to be switched on by a call flag rather than being unconditional, and §11.4 makes the consequence explicit - creating a resource from inside the app must go on pinning nothing - so the default has to be off and the launcher has to opt in.

**Verification:**

- `Grep` - `EXTRA_PIN_SHORTCUT_ON_CREATE` matches in `AddResourceActivity.kt`.
- `Grep` - `pinShortcutOnCreate: Boolean = false` matches in that file (the default is off).
- `Grep` - `AddResourceEvent.ResourcesAdded -> finish()` returns zero hits.
- `Grep` - `CreatedResourcePinManager` matches in that file.
- `Grep` - `pinShortcutOnCreate` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainEventHandler.kt`, which is the only production caller of `createIntent` - this is the static half of §11.4.
- `Grep` - `Log\.d\(` returns zero hits in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). **UNPROVEN** - no gradle was run in this session (run-level deviation: the owner runs every gradle-backed check afterwards). Do not read this box as a failure; read it as "not looked at".
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`. **DEFERRED** - single batched closure is the owner's.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` - this phase adds a public class. **DEFERRED** - part of the same batched closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`). Layer triggers that fire here: DI scope change (new `@Singleton`), coroutine change (the `lifecycleScope` pin-then-finish ordering), repository/DAO read on a new path.

---

## Handoff Notes to Next Phase

`AddResourceActivity.createIntent(context, pinShortcutOnCreate = true)` is the single call any entry point uses; nothing else has to be done at a call site to get a shortcut. The flag is off for every existing caller, so app behaviour is unchanged after this phase. `ResourcesAdded` now names its subject, which is what S1424 and S1428 will read.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no user-facing surface changed. `AddMultipleResult.createdResourceIds` is an added field with a default, so reverting it breaks no stored data.

---

## Step Log

- 2026-08-07 - Step 01.1 - Verification 3\4 PASS (`createdResourceIds` present, old `forEach` gone, `addedCount` unchanged). `.\a.ps1 fk` NOT RUN - owner runs every gradle-backed check after this session (run-level deviation), so the compile predicate is unproven. Files: `domain/usecase/AddResourceUseCase.kt` (+5 LOC). Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Step 01.2 - Verification 4\5 PASS (data class exactly once; bare `ResourcesAdded)` zero hits; `onSuccess { _ ->` zero hits in the three coordinators; `Log.d(` zero hits). `.\a.ps1 fk` NOT RUN (run-level deviation). Note: this step alone leaves the tree non-compiling by design - `AddResourceActivity:431` still refers to `ResourcesAdded` as an object and is repaired in Step 01.4, so `fk` could not have passed here in isolation. Files: `AddResourceViewModel.kt`, `AddResourceVirtualCoordinator.kt`, `AddResourceSmbCoordinator.kt`, `AddResourceSftpKeyCoordinator.kt`, `AddResourceSftpFtpCoordinator.kt`, `AddResourceCompanionCoordinator.kt`. Backup: `temp/S1423/AddResourceViewModel.kt.20260807_194952.backup`. Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Step 01.3 - Verification 6\7 PASS (file exists; `class CreatedResourcePinManager` once; `ResourceShortcutPinManager` + `ResourceIconComposer` present; `R.string.resource_shortcut_unsupported` present; `ShortcutManagerCompat` zero hits; `Log.d(` zero hits). `.\a.ps1 fk` NOT RUN (run-level deviation). Files: `ui/addresource/helpers/CreatedResourcePinManager.kt` (new, 49 LOC). Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Step 01.4 - Verification 6\7 PASS (`EXTRA_PIN_SHORTCUT_ON_CREATE` present; `pinShortcutOnCreate: Boolean = false` present; old `ResourcesAdded -> finish()` zero hits; `CreatedResourcePinManager` present; `pinShortcutOnCreate` zero hits in `MainEventHandler.kt`; `Log.d(` zero hits). `.\a.ps1 fk` NOT RUN (run-level deviation). Deviations from the prompt, both forced by the code: (a) the flag is a `private var` assigned in `onCreate`, not a `private val` - a `val` cannot be assigned there, and `copyResourceId` two lines above uses exactly this shape; (b) `createIntent`'s parameter list was wrapped one-per-line - the single-line form was already 150 chars and a fourth parameter would have widened it further. Files: `ui/addresource/AddResourceActivity.kt` (+31 LOC). Backup: `temp/S1423/AddResourceActivity.kt.20260807_195512.backup`. Dev log NOT recorded - batched closure is the owner's.
- 2026-08-07 - Phase 01 boundary audit (CLAUDE.md §13). Layer 1: `CreatedResourcePinManager` carries the loading/composing/pinning, the Activity keeps routing only (Rule 3); no `data` import from `ui` - the manager depends on `domain.repository.ResourceRepository`. Layer 2: `routeResourcesAdded` awaits the pin before `finish()`, so the scope outlives the request; `pinCreatedResources` reads the row on `Dispatchers.IO` and composes/pins on Main, which is where `ResourceEditorFragment` already pins. `BaseViewModel.events` is a replay-0 `MutableSharedFlow`, so turning `ResourcesAdded` into a data class introduces no replay or dedupe change. Layer 3: the `@Singleton` manager stores no `Context` - it takes one per call, so no Activity leak. Layer 4: the only Room read is `getResourceById` off the main thread. **No P0/P1 findings.** P3 noted, not acted on: when `pinShortcutOnCreate` is true and the ids list is empty (companion import), nothing pins and nothing is said - intended per Step 01.2, since that path is not a single-resource creation act.
- 2026-08-07 - Phase 01 left `🚧 In Progress`, not flipped `✅ Done`: three Done Criteria (compile, dev log, catalog regen) are out of this run's scope by the owner's deviations, and a phase must not be certified on criteria nobody checked.

---

## Step Log - phase closure

- 2026-08-07 - Compile criterion PROVEN, by the owner session rather than the implementation run: `.\a.ps1 fk` (standard) exit 0, `check-standard-fast.ps1 -Mode Code -Flavor Lite` exit 0 - which is the one that matters here, because `ResourcePickerDialogFragment` lives in `src/main` and compiles into the four flavors that mount `src/launcherDisabled` - and `.\a.ps1 dq` exit 0, a full debug build whose kapt pass is what actually validates the Hilt graph the new `@Singleton CreatedResourcePinManager` joins (a Kotlin compile alone would not have).
- 2026-08-07 - Two detekt findings introduced by this phase were fixed before closure: `SpacingBetweenDeclarationsWithAnnotations` on the new `@Inject` field and `SpacingBetweenDeclarationsWithComments` on the new event declaration. Scoped gate afterwards: `assert-detekt -Gate -ChangedFiles <all seven Kotlin files>` -> `PASS [scoped]`.
- 2026-08-07 - Dev log, catalog regen and the `ALL_FEATURES` record ran as one batched closure in Phase 04: `post-change: PASS WITH ADVISORIES (1)`, the single advisory being detekt-preflight attributing findings to files outside this change (verified by the scoped run above).
- 2026-08-07 - **UI-gate screenshot: DEFERRED, and here is exactly why.** An emulator run was attempted against the fresh APK. It never reached the launcher desktop: the walk toggled "Use as home screen" and worked through the resulting cascade of system permission dialogs, but the system "Home app" chooser still offered only Pixel Launcher - our app was absent, meaning `LauncherHomeActivity` was still disabled and the in-app path that enables the component had not actually run. The emulator then dropped offline mid-walk and did not return. The placement decision itself IS on record (strategic 3.3, owner ruling of 2026-08-07), so the first half of the S1338 gate holds; the visual half moves to the device test this ticket is now parked for, and its status note carries the setup sequence.