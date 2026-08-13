# Phase 03 - Settings Screens Migration

**Strategic spec:** [`../S0535_unify-collapsible-groups.md`](../S0535_unify-collapsible-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 5 / 5
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Move the four settings surfaces (General, Operations, Playback, Media) onto the unified `CollapsibleSectionsManager`, deleting the four bespoke orchestrations and the dead section data-classes. State persists through the consolidated store with default-collapsed behavior (research 02 D4).

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsSectionsManager.kt` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 470 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/<OperationsFragment>.kt` | Modified | ≤ 500 |

> No layout edits in this phase - headers are already `CollapsibleSectionHeader`; the Phase 01 visual change applies automatically, so portrait/landscape parity is untouched. Resolve the exact Operations fragment class name (the host of `FragmentSettingsDestinationsBinding`) in step 2 via catalog query before editing.

---

## Steps

### Step 03.1 - Migrate General settings to the unified manager

**Files:** `GeneralSettingsFragment.kt`, delete `GeneralSettingsSectionsHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the `GeneralSettingsSectionsHelper` usage in `GeneralSettingsFragment` with `CollapsibleSectionsManager.register(..)` calls - one per section (Interface, FileBrowser, RemoteSources, Authorization, AppData, System, and the `BuildConfig.DEBUG`-gated Debug section), keys `general__<section>`, default collapsed. Delete `GeneralSettingsSectionsHelper.kt`. Remove the dead `data class CollapsibleSection` and `data class SectionData` from `GeneralSettingsFragment` (flagged unused in research 01/02). Preserve the `BuildConfig.DEBUG` gating of the Debug section.

**Verification:**

- `Glob` - `GeneralSettingsSectionsHelper.kt` no longer exists.
- `Grep` - `CollapsibleSectionsManager` referenced in `GeneralSettingsFragment.kt`.
- `Grep` - `data class CollapsibleSection` and `data class SectionData` absent from `GeneralSettingsFragment.kt`.
- `Grep` - `BuildConfig.DEBUG` still present (debug section still gated).

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 4/4 PASS. Replaced `GeneralSettingsSectionsHelper` with `CollapsibleSectionsManager.register(..)` (7 sections, `general__<section>`, default collapsed, invisible headers skipped); deleted the helper + dead `CollapsibleSection`/`SectionData` data classes + orphaned `TextView` import; Debug section still `BuildConfig.DEBUG`-gated. Retargeted androidTest `DefaultCredentialsInputTest` to the consolidated store key `general__app_data`. Forced recompile (`--rerun-tasks`) BUILD SUCCESSFUL.

---

### Step 03.2 - Migrate Operations settings to the unified manager

**Files:** `<OperationsFragment>.kt`, delete `OperationsSectionsManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Query the catalog (`pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*Destinations*"`) to confirm the fragment hosting `FragmentSettingsDestinationsBinding`. Replace `OperationsSectionsManager` with `CollapsibleSectionsManager.register(..)` per section (Safety, CopyMove, Destinations, the `BuildConfig.ENABLE_SCHEDULED_OPERATIONS`-gated Scheduled, Behaviour, OtherFeatures, SystemApps, ScreenGestures), keys `operations__<section>`, default collapsed. Preserve the scheduled-section flavor gate (hide when disabled). Delete `OperationsSectionsManager.kt`.

**Verification:**

- `Glob` - `OperationsSectionsManager.kt` no longer exists.
- `Grep` - `CollapsibleSectionsManager` referenced in the Operations fragment.
- `Grep` - `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` still present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Replaced `OperationsSectionsManager` with `CollapsibleSectionsManager.register(..)` (8 sections, `operations__<section>`, default collapsed); scheduled section still flavor-gated (hidden when `ENABLE_SCHEDULED_OPERATIONS` is off). Deleted the helper; zero orphaned refs. Backup in temp/ (>500 LOC).

---

### Step 03.3 - Move Playback section logic out of the fragment

**Files:** `PlaybackSettingsFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Remove the in-fragment `data class ExpandableSection` and `setupExpandableSections()` from `PlaybackSettingsFragment` (UI-layer business-logic violation per research 01) and replace with `CollapsibleSectionsManager.register(..)` per section (SortingSlideshow, FileOperations, PlayerUi, TouchZones, SendCommands), keys `playback__<section>`, default collapsed. The fragment must hold no expand/collapse/persistence logic of its own afterward.

**Verification:**

- `Grep` - `data class ExpandableSection` absent from `PlaybackSettingsFragment.kt`.
- `Grep` - `setupExpandableSections` absent from `PlaybackSettingsFragment.kt`.
- `Grep` - `CollapsibleSectionsManager` referenced in `PlaybackSettingsFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Removed in-fragment `ExpandableSection` data class, `setupExpandableSections()`, `getSavedSectionStates()`, `saveSectionState()`, and the companion `PREFS_NAME`/`KEY_*` constants; replaced with `setupCollapsibleSections()` delegating to `CollapsibleSectionsManager.register(..)` (5 sections, `playback__<section>`, default collapsed). Dropped orphaned `StrictModeHelper`/`CollapsibleSectionHeader`/`Context` imports. Fragment now holds no expand/persist logic. (File had unrelated dialog-style WIP - untouched.)

---

### Step 03.4 - Migrate Media settings (preserve lazy child-fragment attach)

**Files:** `MediaSettingsFragment.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Replace the Media container's bespoke section handling with `CollapsibleSectionsManager.register(..)` per section (Images, Video, Vr, Audio, Documents, Other), keys `media__<section>`, default collapsed. Preserve the lazy first-expand child-fragment attach: keep attaching the child fragment on first expansion by hooking the manager's expand callback (register a per-section expand listener in addition to the manager, or expose a one-shot on-first-expand hook). Keep the VR section's help payload intact.

**Verification:**

- `Grep` - `CollapsibleSectionsManager` referenced in `MediaSettingsFragment.kt`.
- `Grep` - the child-fragment attach call (e.g. `childFragmentManager`) still present.
- `Grep` - VR section help wiring still present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Replaced Media's bespoke section handling with `CollapsibleSectionsManager.register(..)` (6 sections, `media__<section>`; VR default expanded preserved). Lazy first-expand child-fragment attach kept via the new `onExpandedChanged` hook (added to the manager) which fires on restore + toggle; `ensureChildAttached`/`childFragmentManager`/`ensureSectionExpanded`/`vrMediaSection` wiring intact. Removed `getSavedSectionStates`/`saveSectionState`/companion keys + orphaned `StrictModeHelper`/`Context` imports.

---

### Step 03.5 - Verify no orphaned references to deleted helpers

**Files:** (repo-wide grep; no edit unless a reference is found)
**Depends on:** Step 03.4

**Prompt for developer:**

> Grep the whole `app_v2/src` for `GeneralSettingsSectionsHelper` and `OperationsSectionsManager`. Any surviving reference (import, instantiation, test) must be updated or removed so the build is clean. If a unit test referenced the deleted helpers, retarget it at `CollapsibleSectionsManager` or delete it.

**Verification:**

- `Grep` - `GeneralSettingsSectionsHelper` returns zero hits across `app_v2/src`.
- `Grep` - `OperationsSectionsManager` returns zero hits across `app_v2/src`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. `GeneralSettingsSectionsHelper` and `OperationsSectionsManager` return zero hits across `app_v2/src` (incl. androidTest, retargeted in 03.1). Forced `compileStandardDebugKotlin --rerun-tasks` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - forced `compileStandardDebugKotlin --rerun-tasks` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via post-change.ps1 (one Phase-03 entry, CLAUDE.md §12).
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to ticket-end catalog_sync (CLAUDE.md §12).

---

## Handoff Notes to Next Phase

All four settings surfaces now share the unified manager + consolidated store; the two settings helper classes are gone. Phase 04 migrates the source editors and player panels.

---

## Rollback Plan

Revert phase commit(s). State already migrated into the consolidated namespace by Phase 02 remains valid; reverting restores the old helpers reading their legacy namespaces (still present, copy-only migration). No data loss.
