# Phase 02 - Viewer Boundary

**Strategic spec:** [`../S0301_nolegal-office-document-embedded-renderer.md`](../S0301_nolegal-office-document-embedded-renderer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Create a flavor-safe internal Office viewer seam and shared host slots so Player and Standalone can request an internal Office viewer without leaking noLegal engine code into market builds.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6.1, §6.2, §6.3, and §6.6 blockers are Resolved.
- [ ] UI clarification is READY in `INDEX.md` and strategic §5.2.1.
- [ ] Working tree is clean or on a feature branch.

---

## UI Clarification Status

Status: READY

### Approved Decisions

- The Office viewer host uses the same document-view area family as the current PDF/EPUB viewer surface.
- The Office container replaces the active document-view surface only for noLegal Office files.
- Portrait and landscape layouts must receive equivalent neutral Office host slots in the same step.
- Office commands live in the existing document overflow/action surface next to PDF/EPUB actions.
- Unsupported or unavailable Office actions are hidden instead of disabled.
- Provider code returns display/fallback/delegate results; Player and Standalone UI code owns the fallback dialog.
- The fallback dialog actions are `external app`, `share`, and `cancel`.
- `Cancel` dismisses the fallback dialog and keeps the current screen open without `finish()`.
- Loading, error, unsupported, and empty states follow the current document-viewer pattern.
- Keyboard, D-pad, mouse, TalkBack labels, and focus order must match the existing document action surface.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProvider.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerSession.kt` | New | ≤ 220 |
| `app_v2/src/standard/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt` | New | ≤ 120 |
| `app_v2/src/legacy/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt` | New | ≤ 120 |
| `app_v2/src/vrOnly/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt` | New | ≤ 120 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt` | New | ≤ 140 |
| `app_v2/src/photos/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt` | New | ≤ 120 |
| `app_v2/src/lite/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerUiStateCoordinatorCallbackImpl.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | ≤ 820 |
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | ≤ 1300 |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified | ≤ 1300 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Add the shared viewer-provider contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProvider.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerSession.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define a shared provider contract for internal Office viewing plus a session/result model that can represent `display internally`, `show explicit fallback dialog`, and `delegate to external app`. Keep the contract neutral about the concrete engine so Phase 03 can plug in the noLegal implementation later.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProvider.kt` exists.
- `Grep` - `interface OfficeDocumentViewerProvider` matches exactly once in that file.
- `Grep` - `data class OfficeDocumentViewerSession` exists in `OfficeDocumentViewerSession.kt`.

**Status:** `[x]` done - expected: contract files present | actual: both created; `interface OfficeDocumentViewerProvider` x1, `data class OfficeDocumentViewerSession` present; `OfficeDocumentViewerOutcome` enum added (DISPLAY_INTERNALLY/SHOW_FALLBACK_DIALOG/DELEGATE_EXTERNAL). noLegal+standard builds PASS.

---

### Step 02.2 - Add flavor-specific provider factories

**Files:** `app_v2/src/standard/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt`, `app_v2/src/legacy/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt`, `app_v2/src/vrOnly/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt`, `app_v2/src/photos/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt`, `app_v2/src/lite/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentViewerProviderFactory.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Provide one factory peer per flavor source set. `standard` / `legacy` / `vr` must preserve the S0299 external-open path, `noLegal` must return the internal viewer provider, and unsupported flavors (`photos`, `lite`) must stay disabled without `BuildConfig.IS_*` checks in `src/main`.
>
> **Path correction (Phase 01 lesson):** the `vr` peer lives in `src/vrOnly/java`, NOT `src/vr/java`, because the `noLegal` flavor also mounts `src/vr/java` (`app_v2/build.gradle.kts` sourceSets) and a peer there would collide with the noLegal factory. `src/vrOnly/java` is mounted only by the `vr` flavor - same convention as `OfficeDocumentFamilyCatalog` in Phase 01.

**Verification:**

- `Grep` - `class OfficeDocumentViewerProviderFactory` exists in all six files listed above.
- `Grep` - no `BuildConfig.IS_NO_LEGAL_FLAVOR` hits exist in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/**`.
- `Grep` - `OfficeDocumentOpenManager` still exists in the standard factory file.

**Status:** `[x]` done - expected: 6 factories, no flavor guards in src/main, standard references OfficeDocumentOpenManager | actual: 6 `class OfficeDocumentViewerProviderFactory` peers (standard/legacy/vrOnly/noLegal/photos/lite); no `BuildConfig.IS_NO_LEGAL_FLAVOR` in player src/main (only KDoc mentions of the avoided pattern); standard factory KDoc references `OfficeDocumentOpenManager`. noLegal build PASS (no duplicate class).

---

### Step 02.3 - Reserve shared viewer host slots in portrait and landscape layouts

**Files:** `app_v2/src/main/res/layout/activity_player_unified.xml`, `app_v2/src/main/res/layout-land/activity_player_unified.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add an Office viewer container and any required command-panel anchor views to both portrait and landscape `activity_player_unified.xml` variants. Keep the shared layout host neutral - no engine-specific widgets in `src/main/res`.

**Verification:**

- `Grep` - `officeDocumentViewerContainer` exists in `app_v2/src/main/res/layout/activity_player_unified.xml`.
- `Grep` - `officeDocumentViewerContainer` exists in `app_v2/src/main/res/layout-land/activity_player_unified.xml`.
- `Grep` - no `noLegal` string literals exist in either layout file.

**Status:** `[x]` done - expected: container in both orientations, no noLegal literal | actual: `officeDocumentViewerContainer` FrameLayout (GONE) added after `epubWebView` in both `layout/` and `layout-land/`; grep found no `noLegal` literal in either file.

---

### Step 02.4 - Route Player and Standalone through the provider seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerUiStateCoordinatorCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Replace the hardwired Office external-open path with provider-driven routing in both Player and Standalone. The seam must preserve S0299 behavior for non-noLegal builds and surface the explicit `external app / share / cancel` fallback dialog contract for the future noLegal viewer.

**Verification:**

- `Grep` - `OfficeDocumentViewerProviderFactory` exists in `PlayerViewerFactory.kt`.
- `Grep` - `displayOfficeDocument` exists in `PlayerUiStateCoordinatorCallbackImpl.kt` and `StandaloneViewManager.kt`.
- `Grep` - no `activity.finish()` call remains immediately after the Office branch in `StandaloneViewManager.kt` without consulting the provider result.

**Status:** `[x]` done - expected: factory referenced, displayOfficeDocument present in both, no unconditional finish | actual: `PlayerViewerFactory.createOfficeDocumentViewerProvider()` references the factory; `PlayerActivity.officeDocumentViewerProvider` lazy seam; `PlayerShareManager.routeOfficeDocument()` consults the provider and the callback `displayOfficeDocument` routes through it; `StandaloneViewManager.displayOfficeDocument()` now finishes only when `outcome == DELEGATE_EXTERNAL`. Backup at `temp/StandaloneViewManager.kt.20260530_010350.bak`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` for the noLegal target. (noLegal `assembleNoLegalDebug` BUILD SUCCESSFUL 3m05s; standard `assembleStandardDebug` BUILD SUCCESSFUL 1m56s.)
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. (1487 records.)

---

## Handoff Notes to Next Phase

The player stack now has a compile-time-safe Office viewer seam; Phase 03 only needs to plug the chosen noLegal engine into that seam.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.