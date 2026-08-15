# Phase 02 - Sections Orchestrator + State Store

**Strategic spec:** [`../S0535_unify-collapsible-groups.md`](../S0535_unify-collapsible-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05
**Steps done:** 4 / 4
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Introduce one orchestrator that binds a header to its content container, animates the body open/close, and persists/restores expanded state through a single store abstraction over one consolidated preferences namespace - plus a one-time migration that folds the existing scattered namespaces into it. Replaces the eight copy-pasted per-screen orchestrations (consumers migrate in Phases 03-05).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionStore.kt` | New | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionsManager.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionStateMigration.kt` | New | ≤ 160 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionsManagerTest.kt` | New | ≤ 220 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionStateMigrationTest.kt` | New | ≤ 180 |

---

## Steps

### Step 02.1 - Define the state store abstraction

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create an interface `CollapsibleSectionStore` with `fun isExpanded(key: String, default: Boolean): Boolean` and `fun setExpanded(key: String, expanded: Boolean)`, plus a default `SharedPreferencesCollapsibleSectionStore(context)` implementation reading/writing one consolidated namespace constant `collapsible_sections_state`. Wrap disk access in `StrictModeHelper.allowDiskReads`/`allowDiskWrites` (mirror the existing settings orchestrators). Keys are caller-supplied `<screen>__<section>` strings. No business logic beyond get/put.

**Verification:**

- `Glob` - `CollapsibleSectionStore.kt` exists.
- `Grep` - `interface CollapsibleSectionStore` matches once.
- `Grep` - `collapsible_sections_state` present.
- `Grep` - `StrictModeHelper` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 4/4 PASS. `CollapsibleSectionStore` interface (`isExpanded`/`setExpanded`) + `SharedPreferencesCollapsibleSectionStore` over consolidated `collapsible_sections_state` namespace; all disk access wrapped in `StrictModeHelper` (mirrors existing orchestrators).

---

### Step 02.2 - Implement the sections manager (bind + animate + persist)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionsManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `CollapsibleSectionsManager` exposing a registration API, e.g. `fun register(header: CollapsibleSectionHeader, container: View, key: String, defaultExpanded: Boolean)`. On register: read expanded state from the injected `CollapsibleSectionStore`, call `header.setExpanded(state, notify = false)`, set `container.isVisible = state` with no animation, then attach an `setOnExpandedChangeListener` that toggles `container.isVisible`, plays a short `TransitionManager.beginDelayedTransition` on the container's parent (light fade/auto-transition), and persists the new state via the store. Restore must not animate; only user toggles animate (satisfies research 02 D6). Keep the manager UI-agnostic about which screen uses it.

**Verification:**

- `Glob` - `CollapsibleSectionsManager.kt` exists.
- `Grep` - `class CollapsibleSectionsManager` matches once.
- `Grep` - `fun register` present.
- `Grep` - `beginDelayedTransition` present.
- `Grep` - `setOnExpandedChangeListener` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 4/4 PASS. `CollapsibleSectionsManager.register(header, container, key, defaultExpanded)` restores state via store (notify=false, no animation), animates body on user toggle with framework `TransitionManager.beginDelayedTransition` (`AutoTransition`, 150ms) on the container parent, persists via store. Secondary `(context)` constructor runs the one-time migration before any register reads. `.\a.ps1 fk` SUCCESSFUL.

---

### Step 02.3 - One-time migration of legacy namespaces

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionStateMigration.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `CollapsibleSectionStateMigration` that, guarded by a one-time flag in the consolidated namespace, copies every known legacy key into the consolidated namespace under its new `<screen>__<section>` name, then marks migration done so legacy stores are never read again. Source namespaces and their keys are enumerated from research 01 §2/§3 (`general_sections_state`, `settings_section_states`, `playback_sections_state`, `media_sections_state`, and the two source-editor namespaces). Idempotent: running twice is a no-op. Do not delete legacy prefs files (cheap to leave; avoids data loss risk).

**Verification:**

- `Glob` - `CollapsibleSectionStateMigration.kt` exists.
- `Grep` - `general_sections_state` and `playback_sections_state` referenced (legacy sources).
- `Grep` - a one-time guard key (e.g. `migration_done` / `migrated_v1`) present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. `CollapsibleSectionStateMigration` folds 4 settings namespaces (general/operations/playback/media, exact key maps) plus AddResource (orientation dropped) and ResourceEditor (type+orientation dropped) into the consolidated store; guarded by `migration_done_v1`, copy-only (legacy files kept), copies only present keys. New `<screen>__<section>` key contract fixed here for Phases 03-05. `.\a.ps1 fk` SUCCESSFUL.

---

### Step 02.4 - Unit-test manager and migration

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionsManagerTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionStateMigrationTest.kt`
**Depends on:** Step 02.2, Step 02.3

**Prompt for developer:**

> Manager test (Robolectric): register persists toggles to a fake/in-memory `CollapsibleSectionStore`; restore sets container visibility without notifying the listener. Migration test: given seeded legacy prefs, after migration the consolidated namespace holds the remapped keys and the guard flag; a second run changes nothing.

**Verification:**

- `Grep` - test method asserting persisted toggle present.
- `Grep` - test method asserting idempotent migration present.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*CollapsibleSectionsManagerTest*" --tests "*CollapsibleSectionStateMigrationTest*"` - per-class reports green.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. `CollapsibleSectionsManagerTest` (2): register persists a user toggle to a fake store; register restores saved state to the container. `CollapsibleSectionStateMigrationTest` (2): legacy keys remap + guard set (incl. user-collapsed VR preserved); second run is a no-op. Per-class reports 2/2 each, 0 failures. Quarantined unrelated WIP `BrowseDialogHelperTest.kt` to run, restored byte-identical.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL; new-class unit tests green.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added via post-change.ps1 (one Phase-02 entry, CLAUDE.md §12).
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to ticket-end catalog_sync (CLAUDE.md §12).

---

## Handoff Notes to Next Phase

`CollapsibleSectionsManager.register(header, container, key, defaultExpanded)` over `CollapsibleSectionStore` is the single integration point for all consumers. Migration runs once before any consumer reads state - call it from app/feature startup or lazily on first manager use (decide in 02.3, document in the class). Phases 03-05 replace each screen's bespoke orchestration with `register(..)` calls and delete the old helpers.

---

## Rollback Plan

Revert phase commit(s). New classes are unreferenced until Phase 03+, so reverting is isolated; no consumer depends on them yet. Legacy namespaces are untouched by migration (copy-only), so no data restore needed.
