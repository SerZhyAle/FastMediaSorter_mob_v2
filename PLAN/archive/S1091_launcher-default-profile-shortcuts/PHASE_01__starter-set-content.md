# Phase 01 - Starter-Set Content

**Strategic spec:** [`../S1091_launcher-default-profile-shortcuts.md`](../S1091_launcher-default-profile-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-07-21
**Completed:** 2026-07-21

**Step Log:**

- 2026-07-21 - 01.1-01.3 grep-verified (StarterResources, commonResources/commonFeatures, itemsFor new signature). 01.4 tests migrated; targeted `:app_v2:testStandardDebugUnitTest --tests *LauncherStarterSets*` BUILD SUCCESSFUL (both classes pass). Note: API + sole consumer compile as a unit - Phase 02 was implemented before the test run since itemsFor's only caller is SeedLauncherDesktopUseCase.

---

## Objective

Widen `LauncherStarterSets` so every profile seeds the full set of existing virtual-resource shortcuts plus availability-gated padding feature shortcuts, reaching ~12-15 cells, while keeping the pure-data / pure-packer contract and the existing profile-gadget extras.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 260 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt` | Modified | ≤ 200 |
| `app_v2/src/testStandard/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt` | Modified | ≤ 80 |

> Pure Kotlin (no Android imports in the core object); no layout, no strings.

---

## Steps

### Step 01.1 - Add StarterResources input holder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `LauncherStarterSets`, add a public `data class StarterResources` holding seven nullable `Long?` ids, each defaulting to `null`: `recentId`, `allAudioId`, `allImagesId`, `allVideoId`, `allDocsId`, `cameraId`, `lastResourceId`. This groups the ids the seed resolves so `itemsFor` stays readable. No behavior yet.

**Verification:**

- `Grep` - `data class StarterResources` present with all seven properties.

**Status:** `[x]` done

---

### Step 01.2 - Add common resource + padding-feature sections

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add two private helpers. `commonResources(res: StarterResources): List<StarterItem>` emits a `resourceShortcut(id, LauncherResourceMode.BROWSE)` for each non-null id in this fixed order: `recentId`, `allAudioId`, `allImagesId`, `allVideoId`, `allDocsId`, `cameraId` (skip nulls). `commonFeatures(routeAvailableInBuild: Map<String, Boolean>): List<StarterItem>` emits a `shortcut(LauncherCellCommand.Feature(key))` for each key present-and-true in the map, in this fixed order: `InternalRouteCatalog.KEY_STREAMS`, `KEY_QUICK_CAMERA`, `KEY_QUICK_VOICE`, `KEY_CALCULATOR`, `KEY_OCR`. Gate on build availability only (a compiled-but-runtime-disabled feature keeps its cell, which resolves to its setting per the existing route contract). Reuse the existing `resourceShortcut`/`shortcut` helpers; do not add new encoding.

**Verification:**

- `Grep` - `fun commonResources(` and `fun commonFeatures(` present.
- `Grep` - `LauncherResourceMode.BROWSE` present in `commonResources`.
- `Grep` - `InternalRouteCatalog.KEY_STREAMS`, `KEY_QUICK_CAMERA`, `KEY_QUICK_VOICE`, `KEY_CALCULATOR`, `KEY_OCR` all referenced.

**Status:** `[x]` done

---

### Step 01.3 - Restructure itemsFor to the new signature

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Change `itemsFor` to `fun itemsFor(profile: DeviceProfileType, resources: StarterResources, routeAvailableInBuild: Map<String, Boolean>): List<StarterItem>`. Body order: `clock()`, then `commonResources(resources)`, then `profileItems(profile, resources.lastResourceId, resources.allAudioId, streamsAvailable)`, then `commonFeatures(routeAvailableInBuild)`, then `commonTail()` - where `streamsAvailable = routeAvailableInBuild[InternalRouteCatalog.KEY_STREAMS] == true`. Keep `profileItems`, `place`, `commonTail`, and all packer helpers unchanged (profile gadget extras stay additive). The `profileItems` exhaustive `when` over `DeviceProfileType` is unchanged.

**Verification:**

- `Grep` - `fun itemsFor(` signature contains `resources: StarterResources` and `routeAvailableInBuild: Map<String, Boolean>`.
- `Grep` - `commonResources(resources)` and `commonFeatures(routeAvailableInBuild)` both called inside `itemsFor`.
- `Grep` - `profileItems(` still called inside `itemsFor`.

**Status:** `[x]` done

---

### Step 01.4 - Update and extend the starter-set tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`, `app_v2/src/testStandard/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsParityTest.kt`

**Depends on:** Step 01.3

**Prompt for developer:**

> Migrate every `itemsFor(...)` call in both test files to the new signature: replace `lastResourceId`/`allAudioResourceId`/`streamsAvailable` positional args with `StarterResources(...)` (named ids) and a `routeAvailableInBuild` map (use `emptyMap()` where the old call passed `streamsAvailable = false`, and `mapOf(InternalRouteCatalog.KEY_STREAMS to true)` where it passed `true`). Existing assertions that expected `clock + profileItems + tail` for all-null inputs stay valid (empty resources + empty routes add nothing). Add one new test in `LauncherStarterSetsTest`: `fun `mainstream profile seeds the full resource and padding set``() building `StarterResources(recentId=1, allAudioId=2, allImagesId=3, allVideoId=4, allDocsId=5, cameraId=6)` and a map with all five padding keys true, for `DeviceProfileType.PERSONAL_SMARTPHONE`, asserting the target list equals exactly: `clock`, `res:1:BROWSE`, `res:2:BROWSE`, `res:3:BROWSE`, `res:4:BROWSE`, `res:5:BROWSE`, `res:6:BROWSE`, `fn:streams`, `fn:quick_camera`, `fn:quick_voice`, `fn:calculator`, `fn:ocr`, `fn:favorites`, `os:settings`, `app:__self__` (15 items).

**Verification:**

- `Grep` - no remaining old-signature `streamsAvailable =` call in either test file.
- `Grep` - new test name `mainstream profile seeds the full resource and padding set` present.
- `.\a.ps1 fu` (or `gradlew :app_v2:testStandardDebugUnitTest --tests "*LauncherStarterSets*"`) - the two starter-set test classes pass.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `LauncherStarterSetsTest` + `LauncherStarterSetsParityTest` pass.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`itemsFor` now consumes `StarterResources` + a route-availability map. Phase 02 resolves those inputs in the use case.

---

## Rollback Plan

Revert the phase commit(s) - pure-data change, no persistence or schema impact.
