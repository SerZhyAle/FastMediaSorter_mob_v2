# Phase 05 - Browse and Main hosts

**Strategic spec:** [`../S1329_activity-logic-debt-78-baselined-violations.md`](../S1329_activity-logic-debt-78-baselined-violations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-14
**Completed:** 2026-08-14

> **Re-planned 2026-08-13.** The original phase moved all ten fields into `BrowseViewModel` and `MainViewModel`,
> "exposing behavior rather than the injected types". Measured against the live tree, that shape fits 1 of the
> 15 call sites: `BrowseActivity` forwards 5 of 6 into manager constructors and `MainActivity` forwards 6 of 9.
> This phase now uses the factory template, exactly as Phases 02-04. Rationale in strategic §9 ADR-1.
>
> **Two consequences worth reading before starting.** `MainViewModel` is no longer touched at all - every one of
> `MainActivity`'s five fields resolves to either the factory or the inherited `appSettings`, which takes the
> app's startup path out of this phase's blast radius entirely. And `MainActivity.kt` is **1478 LOC**, not the
> 1400 the plan recorded: the margin to the Rule 2 ceiling is 22 lines, not 100.

---

## Objective

Clear the ten remaining in-scope violations across the two highest-traffic screens: `BrowseActivity` (5) and
`MainActivity` (5). These go last among the code phases because they carry the most traffic, not because they
are the hardest - the factory shape makes both mechanical.

---

## Prerequisites

- [x] Phase 01 is ✅ Done - `BaseActivity.appSettings` exists. Both hosts extend `BaseActivity`, so both can use it.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1` immediately before the first edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseHostFactory.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified (backup first) | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModelDependencies.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified (852 LOC - backup first) | < 852 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainHelperFactory.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified (1478 LOC - backup first) | < 1478 |

> Every modified file exceeds 500 LOC - take timestamped backups per Rule 5 before editing (Rule 5).
> Re-read each file's current line count at the start of its step rather than trusting this table; the previous
> revision of this plan was stale by 78 lines on `MainActivity` alone.
>
> **`MainActivity.kt` is 1478 LOC against the 1500 ceiling - 22 lines of headroom.** The factory shape removes
> lines rather than adding them (a five-argument constructor call becomes a two-argument `create(..)`), so this
> phase should end well clear of the ceiling. If any edit would push it past 1500, stop and extract a helper
> manager under `ui/main/helpers/` first (CLAUDE.md Rule 2).
>
> `MainViewModel.kt` is deliberately absent from this table - see the note at the top.
> No `res/layout*` file is touched - landscape parity not applicable.
>
> **Two budget corrections made while executing (2026-08-14).** `BrowseHostFactory` came out at 184 lines,
> not 120: `BrowseManagerInitializer` takes 33 host-supplied arguments and the factory mirrors that surface
> one-to-one, because ADR-1 forbids changing the manager's signature. And `BrowseViewModelDependencies.kt`
> joined the table: `GetDestinationsUseCase` had to enter `BrowseFileMutationDependencies` rather than
> `BrowseViewModel`'s own constructor, which already sits at detekt's 10-parameter ceiling - exactly the
> "join the group, not the constructor" rule S1350 wrote into that file.

---

## Per-field map (measured 2026-08-13)

`BrowseActivity` - 5 fields, 6 sites, 5 forwards:

| Field | Line | Shape | Sites |
|---|---:|:--:|---|
| `fileOperationUseCase` | 132 | F | `BrowseManagerInitializer` (~400) |
| `getDestinationsUseCase` | 133 | **F + V** | `BrowseManagerInitializer` (~401); plus one real call `getDestinationsExcluding(resource.id)` in `observeData()` (~526) |
| `settingsRepository` | 134 | F | `BrowseCameraCaptureManager` (~266), `BrowseMicRecordingManager` (~301), `BrowseManagerInitializer` (~402) |
| `resourceRepository` | 137 | F | `BrowseCameraCaptureManager` (~267) |
| `credentialsRepository` | 141 | F | `BrowseManagerInitializer` (~409) |

`MainActivity` - 5 fields, 9 sites, 6 forwards:

| Field | Line | Shape | Sites |
|---|---:|:--:|---|
| `settingsRepository` | 208 | **F + S** | Forwards into `MainResumePlaybackHelper` (~387), `MainPanelItemActionsManager` (~866), `MainProgramsPanelManager` (~900), `MainStreamsPanelManager` (~949), `MainEventHandler` (~1221), `MainResourceTabsManager` (~1404); plus three real reads at ~444, ~562 (`getSettings().first().cameraOcrTranslationEnabled`) and ~1233 (`collectOnLifecycle`) |
| `resourceRepository` | 227 | F | `MainResumePlaybackHelper` (~388) |
| `getResumeStateUseCase` | 217 | F | `MainResumePlaybackHelper` (~389) |
| `clearResumeStateUseCase` | 220 | F | `MainResumePlaybackHelper` (~390) |
| `streamResumeStateRepository` | 224 | F | `MainResumePlaybackHelper` (~391) |

Line numbers are a starting point, not a contract - locate each site by name.

Not flagged and not to be touched in either host: `faviconAtlasStore` (its package segment is lowercase
`repository` and the detector matches case-sensitively - it only *looks* like a violation), `unifiedFileCache`,
`unifiedCache`, the cloud and network clients, `unifiedFileOperationHandler`, `resourceOpsMenuManager`,
`browseFileOverflowMenuManager`, `shareResultBus`, `shareResultPresenter`, `remoteSourceGate`.

---

## Steps

### Step 05.1 - Create BrowseHostFactory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseHostFactory.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Mirror `CameraOcrFlowManagerFactory` (step 01.5): unscoped `@Inject constructor` holding
> `FileOperationUseCase`, `GetDestinationsUseCase`, `SettingsRepository`, `ResourceRepository` and
> `Lazy<NetworkCredentialsRepository>`, all `private val`. Three methods, one per manager the host builds:
>
> - `createManagerInitializer(..)` -> `BrowseManagerInitializer`, supplying `fileOperationUseCase`,
>   `getDestinationsUseCase`, `settingsRepository` and `credentialsRepository`.
> - `createCameraCaptureManager(..)` -> `BrowseCameraCaptureManager`, supplying `settingsRepository` and
>   `resourceRepository`.
> - `createMicRecordingManager(..)` -> `BrowseMicRecordingManager`, supplying `settingsRepository`.
>
> Preserve the `Lazy` wrapping on `credentialsRepository` - laziness is Rule 18 and must not be dropped while
> moving the dependency. Change no manager's constructor signature (strategic §9 ADR-1).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseHostFactory.kt` exists.
- `Grep` - `class BrowseHostFactory @Inject constructor` matches exactly once in that file.
- `Grep` - `Lazy<` matches at least once in that file (Rule 18 laziness preserved).
- `Grep` - `^\s+val \w+:` (no `private` modifier) returns zero hits in that file.
- `Grep` - `git diff --stat` shows no change to `BrowseManagerInitializer.kt`, `BrowseCameraCaptureManager.kt` or `BrowseMicRecordingManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - BrowseHostFactory created (184 LOC, plan budget said 120 - the initializer mirrors a 33-argument host surface). Verified: class BrowseHostFactory @Inject constructor x1, Lazy< x7, zero non-private vals, git diff --stat empty for BrowseManagerInitializer/BrowseCameraCaptureManager/BrowseMicRecordingManager. LongParameterList suppressed per the Phase 01 factory precedent (CameraCaptureHelperFactory).

---

### Step 05.2 - Migrate BrowseActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`,
`app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Back up both files per Rule 5 first. Inject `BrowseHostFactory` into the Activity, delete all five
> flagged fields and their imports, and route the five forwarding sites through the factory.
>
> The sixth site is a real call: `getDestinationsUseCase.getDestinationsExcluding(resource.id)` inside
> `observeData()`'s state collector (~526), whose result feeds `mediaFileAdapter.setResourcePermissions`.
> `BrowseViewModel` does not hold `GetDestinationsUseCase` today - add it as a constructor dependency there and
> expose the `hasDestinations` answer as behaviour or derived state. Do not expose the use case itself.
>
> Keep the `S0367` comment with the capture-destination resolution when it moves (Rule 8).

**Verification:**

- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `BrowseActivity.kt`.
- `Grep` - `browseHostFactory` matches at least once in `BrowseActivity.kt`.
- `Grep` - `faviconAtlasStore` and `unifiedFileOperationHandler` still match in `BrowseActivity.kt` (deliberately untouched).
- `Grep` - `S0367` matches exactly once across `BrowseActivity.kt` + `BrowseViewModel.kt` combined.
- `Grep` - `^\s+val \w+: GetDestinationsUseCase` (no `private` modifier) returns zero hits in `BrowseViewModel.kt`.
- `BrowseActivity.kt` line count is lower than its backup; `BrowseViewModel.kt` line count ≤ 1500.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - BrowseActivity 852 -> 839 LOC, five flagged fields and three imports gone, three construction sites routed through browseHostFactory. The sixth site now asks BrowseViewModel.hasDestinationsExcluding(resourceId); the use case is a private ctor dependency there. Verified: zero domain @Inject fields, browseHostFactory x4, faviconAtlasStore and unifiedFileOperationHandler untouched, S0367 present exactly once across both files, no non-private GetDestinationsUseCase val, BrowseViewModel 945 LOC.

---

### Step 05.3 - Create MainHelperFactory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainHelperFactory.kt` (New)
**Depends on:** - independent of Steps 05.1-05.2

**Prompt for developer:**

> Same template. Unscoped `@Inject constructor` holding `SettingsRepository`, `ResourceRepository`,
> `GetResumeStateUseCase`, `ClearResumeStateUseCase` and `StreamResumeStateRepository`, all `private val`.
> Six methods, one per helper the host builds: `createResumePlaybackHelper(..)` (supplies all five),
> `createPanelItemActionsManager(..)`, `createProgramsPanelManager(..)`, `createStreamsPanelManager(..)`,
> `createEventHandler(..)` and `createResourceTabsManager(..)` (each supplies `settingsRepository`).
>
> `MainResumePlaybackHelper` takes `ResourceRepository` eagerly, so hold it eagerly here to match - do not
> introduce a `Lazy` wrapper the call site cannot accept. (`MainViewModel` holds the same type as
> `dagger.Lazy<ResourceRepository>`, but that instance is not involved in this phase.)
>
> Change no helper's constructor signature (strategic §9 ADR-1).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainHelperFactory.kt` exists.
- `Grep` - `class MainHelperFactory @Inject constructor` matches exactly once in that file.
- `Grep` - each of `createResumePlaybackHelper`, `createPanelItemActionsManager`, `createProgramsPanelManager`, `createStreamsPanelManager`, `createEventHandler`, `createResourceTabsManager` matches exactly once in that file.
- `Grep` - `^\s+val \w+:` (no `private` modifier) returns zero hits in that file.
- `Grep` - `git diff --stat` shows no change to `MainResumePlaybackHelper.kt`, `MainPanelItemActionsManager.kt`, `MainProgramsPanelManager.kt`, `MainStreamsPanelManager.kt`, `MainEventHandler.kt` or `MainResourceTabsManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - MainHelperFactory created (170 LOC; budget cell corrected from 160). Verified: class MainHelperFactory @Inject constructor x1, each of the six create names exactly once, zero non-private vals, git diff --stat empty for all six helpers. createEventHandler is internal because MainEventHandler is.

---

### Step 05.4 - Migrate MainActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Back up the file per Rule 5 first, and record its exact starting line count - the budget is a
> *decrease* from 1478 and the ceiling is 22 lines away.
>
> Inject `MainHelperFactory`, delete all five flagged fields and their imports, route the six forwarding sites
> through the factory, and replace the three real settings reads with the inherited `appSettings` from step
> 01.1: the two one-shot `getSettings().first().cameraOcrTranslationEnabled` reads (~444 in `onCreate`'s
> `ACTION_CAMERA_OCR_TRANSLATE` branch, ~562 in `onNewIntent`) become `appSettings.first()`, and the
> `collectOnLifecycle(settingsRepository.getSettings())` block (~1233) becomes `collectOnLifecycle(appSettings)`.
>
> `MainViewModel` is **not** touched by this step - every field resolves to the factory or to `appSettings`.
> Do not move anything into it; that would put the app's startup path back into this phase's blast radius for
> no benefit.
>
> The Activity keeps its `latestSettings: AppSettings?` snapshot field (S0770) for synchronous menu reads - keep
> feeding it from the collect block, and leave the field itself in place.
>
> `MainActivity` registers several `ActivityResultLauncher` fields as initializers because they must exist
> before the Activity is STARTED (S0523) - do not move or reorder any of them. `setupViews()` runs from a posted
> lambda; nothing here may depend on a value only available after that post.

**Verification:**

- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `MainActivity.kt`.
- `Grep` - `mainHelperFactory` matches at least once in `MainActivity.kt`.
- `Grep` - `collectOnLifecycle(appSettings)` matches at least once in `MainActivity.kt`.
- `Grep` - `latestSettings` still matches in `MainActivity.kt`.
- `Grep` - `quickCaptureRecordAudioLauncher` and `quickCaptureCameraLauncher` still match in `MainActivity.kt`.
- `Grep` - `S0523` comment still present in `MainActivity.kt`.
- `git diff --stat` shows **zero** changes to `MainViewModel.kt`.
- `MainActivity.kt` line count is lower than its backup and ≤ 1500.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - MainActivity 1478 -> 1451 LOC. Five flagged fields and four imports gone; six helper sites routed through mainHelperFactory; the three real settings reads now use the inherited appSettings, which KeepScreenAwakeManager resolves to settingsRepository.getSettings() - the same stream, so behaviour is unchanged. Verified: zero domain @Inject fields (multiline grep), mainHelperFactory x7, collectOnLifecycle(appSettings) x1, latestSettings and both quick-capture launchers and S0523 still present, git diff --stat empty for MainViewModel.kt. a.ps1 fk BUILD SUCCESSFUL.
- 2026-08-14 - Phase-boundary audit (CLAUDE.md 13, Layers 1-3). Startup path: no dependency is pulled earlier than before - MainActivity field-injected the same five domain objects eagerly, and MainHelperFactory now takes exactly those five eagerly at the same field-injection moment; BrowseHostFactory likewise, keeping Lazy on credentialsRepository (Rule 18). No work moved onto the main thread: hasDestinationsExcluding is suspend and runs in the same lifecycleScope.launch the inline call used. appSettings resolves to settingsRepository.getSettings() through KeepScreenAwakeManager, so the three migrated reads observe the identical stream. Listener-symmetry and neuroslop gates report zero delta. BrowseFileMutationDependencies gained a member; its only test consumer mocks it with mockk(relaxed=true), so no test constructs it positionally. No P0/P1 findings. Accepted P3: createManagerInitializer carries 33 parameters, suppressed with the Phase 01 factory rationale, because ADR-1 forbids changing the manager signature. Screenshot deferred (no device); the phase changes no user-visible string, layout or flow, which is the phase invariant, so there is no placement decision to record.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `.\a.ps1 dq` (not `fk` alone - two new `@Inject constructor` classes must resolve
      in the Hilt graph, which only `hiltJavaCompileStandardDebug` proves).
- [x] `scripts/quality/assert-activity-logic-not-growing.ps1` reports `actual 32`, and the baseline is ratcheted
      down to 32 with `-UpdateBaseline`.
- [x] `Grep` - `@Suppress("ActivityLogicViolation")` returns zero hits repository-wide.
- [x] `Grep` - `Timber.d("S1329:` returns zero hits (this ticket adds no probes).
- [x] `app_v2/lint-baseline.xml` unchanged - regenerated only in Phase 06.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for the phase via `.\scripts\add_to_dev_log.ps1` (one entry, not one per file).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - two new public classes, `BrowseViewModel` API changed.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings. Focus: `MainActivity` is the startup path -
      confirm no work moved onto the main thread and no new eager initialization was added (CLAUDE.md §13).
      A factory is constructed by Hilt at field-injection time, so check it did not pull a dependency graph
      earlier than the manual construction did.

---

## Handoff Notes to Next Phase

All 46 in-scope violations are cleared. Phase 06 regenerates the baseline and expects exactly 32 remaining
entries, all in `PlayerActivity.kt` (20) and `PhotoVideoStandaloneActivity.kt` (12).

Four factories now exist across the ticket - `StandaloneHostFactory`, `ReceiveShareUiFactory`,
`BrowseHostFactory`, `MainHelperFactory` - plus Phase 01's three. Phase 07 documents the pattern once rather
than seven times.

---

## Rollback Plan

Revert the phase commit(s). No data migration and no persisted-format change. Steps 05.1-05.2 (Browse) and
05.3-05.4 (Main) touch disjoint screens and are independently revertible as pairs.
