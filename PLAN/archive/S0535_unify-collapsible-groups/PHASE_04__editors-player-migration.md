# Phase 04 - Source Editors + Player Panels Migration

**Strategic spec:** [`../S0535_unify-collapsible-groups.md`](../S0535_unify-collapsible-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Move AddResource and ResourceEditor onto the unified manager, and bring the player Copy/Move panels onto the unified header with theme tokens - removing the hardcoded hex colors and the `invisible`/`gone` divergence in the landscape player layout, and the defunct text-prefix attrs. Folds in the StrictMode-alignment defect for the AddResource form manager.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceFormManager.kt` | Modified | ≤ 460 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/res/layout/player_bottom_panels_container_content.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout-land/player_bottom_panels_container_content.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/values/colors.xml` | Modified | +~2 colors (if missing) |
| `app_v2/src/main/res/values/attrs.xml` | Modified | -2 dead attrs |

> `ResourceEditorFragment.kt` is already ~1000 LOC: this phase must not push it over 1500. Migration replaces bespoke section code with manager calls, so net lines should drop, not grow. If an edit would exceed budget, extract the section wiring into a small `ResourceEditorSectionsBinder` helper instead of inlining.
> Player layout edits touch BOTH portrait and landscape variants (parity rule) - both listed above.

---

## Steps

### Step 04.1 - Migrate AddResource form sections + fix StrictMode

**Files:** `AddResourceFormManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the private `bindCollapsibleHeader()` per-section handling with `CollapsibleSectionsManager.register(..)` calls for the seven SMB/SFTP sections, keys `add_resource__<type>__<section>` (keep the type discriminator; orientation no longer needs to be in the key since the consolidated store is orientation-agnostic), default collapsed. Route the section state read/write through the unified store so it is wrapped in `StrictModeHelper` consistently (closes the missing-StrictMode defect from research 01/02). Remove the redundant `android:contentDescription` left on the CSH tag (research 01 §3.G) if touched.

**Verification:**

- `Grep` - `CollapsibleSectionsManager` referenced in `AddResourceFormManager.kt`.
- `Grep` - `bindCollapsibleHeader` absent (or now delegating to the manager) in `AddResourceFormManager.kt`.
- `Grep` - direct `add_resource_ui_state` / `addResourceUiPrefs` access removed - section state now flows through the unified store (which wraps reads/writes in `StrictModeHelper`), closing the missing-StrictMode defect. (Predicate corrected from "StrictModeHelper referenced in AddResourceFormManager": delegating to the store is the cleaner fix and the StrictMode wrap lives in the store.)

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Replaced `bindCollapsibleHeader`/`sectionKey` per-section + direct `addResourceUiPrefs` with `CollapsibleSectionsManager.register(..)` (7 SMB/SFTP sections, keys `add_resource__<type>__<section>`, orientation dropped, default collapsed). Missing-StrictMode defect closed (store wraps access). Removed orphaned `CollapsibleSectionHeader` import.

---

### Step 04.2 - Migrate ResourceEditor sections

**Files:** `ResourceEditorFragment.kt` (optionally new `ResourceEditorSectionsBinder.kt`)
**Depends on:** Step 04.1

**Prompt for developer:**

> Replace the editor's `data class ExpandableSection` + bespoke persistence with `CollapsibleSectionsManager.register(..)` for the six sections (ConnectionSettings, MediaTypes, Scanning, Destination, Advanced, Statistics), keys `resource_editor__<section>`, default collapsed (Statistics stays gone in ADD mode as today). Drop the type+orientation key composition - the consolidated store keys are orientation-agnostic. If inlining pushes the fragment toward 1500 LOC, extract a `ResourceEditorSectionsBinder` helper and call it from the fragment.

**Verification:**

- `Grep` - `CollapsibleSectionsManager` referenced (in the fragment or the new binder).
- `Grep` - `data class ExpandableSection` absent from `ResourceEditorFragment.kt`.
- `Grep -c` - `ResourceEditorFragment.kt` line count < 1500.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Replaced `ExpandableSection` + `sectionStateKey` + `uiPrefs` per-section persistence with `CollapsibleSectionsManager.register(..)` (6 sections, `resource_editor__<section>`, type+orientation dropped). The 3 dynamic-visibility re-apply sites (MediaTypes/Connection/Statistics) now re-sync the body to `header.isExpanded()`. Removed dead `PREFS_RESOURCE_EDITOR_UI`/`SECTION_*` consts + orphaned `CollapsibleSectionHeader` import. LOC 962 (<1500), no binder extraction needed.

---

### Step 04.3 - Bring player Copy/Move panels onto the unified header

**Files:** `app_v2/src/main/res/layout/player_bottom_panels_container_content.xml`, `app_v2/src/main/res/layout-land/player_bottom_panels_container_content.xml`, `app_v2/src/main/res/values/attrs.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Remove the now-defunct `csh_collapsedPrefix`/`csh_expandedPrefix` attr usages from both panel headers - the chevron supersedes them. Then remove the two `csh_collapsedPrefix`/`csh_expandedPrefix` styleable attrs from `attrs.xml` (Phase 01 left them defined-but-unread; the player was their last user, so they are now safe to delete). Replace the hardcoded hex backgrounds `#CC004D00` / `#CC00004D` in the landscape file with `@color` resources (reuse `@color/activity_player_unified_copyToPanel_background` / `..._moveToPanel_background` as portrait already does, or add equivalents to `colors.xml`). Align the panel default visibility between portrait and landscape (pick `gone` for both unless the overlay layout genuinely needs `invisible` - document the choice in an XML comment if it stays divergent). Keep player text legible on the colored panel via a player-context token, not `@color/white` hardcoded if avoidable.

**Verification:**

- `Grep` - `#CC004D00` and `#CC00004D` absent from both player layout files.
- `Grep` - `csh_collapsedPrefix` and `csh_expandedPrefix` return zero hits across `app_v2/src` (usages gone AND attrs removed from `attrs.xml`).
- `Grep` - panel `android:visibility` values match between portrait and landscape (or a justifying comment is present).

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Both player panel headers: removed defunct `csh_collapsedPrefix`/`csh_expandedPrefix` (+ dead `csh_prefixTextColor`/`csh_prefixPaddingEnd`) and the `csh_titleBold="false"` opt-out (now inherit the unified bold token per owner addendum); added `csh_chevronTint="@color/white"` (new widget attr) for indicator legibility on the dark panel. Removed `csh_collapsedPrefix`/`csh_expandedPrefix` from attrs.xml. Landscape hex `#CC004D00`/`#CC00004D` -> `@color/activity_player_unified_*Panel_background` (matches portrait). Both panels visibility aligned to `gone` (player toggles via `isVisible`=VISIBLE/GONE, never INVISIBLE).

---

### Step 04.4 - Verify no orphaned editor-section references

**Files:** (repo-wide grep)
**Depends on:** Step 04.3

**Prompt for developer:**

> Grep `app_v2/src` for leftover orientation-keyed section pref keys (`add_{type}_{land/port}_`, `editor_{type}_{land/port}_`) and any direct reads of the old `add_resource_ui_state` / `resource_editor_ui_state` namespaces outside the Phase 02 migration. Remove or redirect them through the unified store; the only place that may still name legacy namespaces is `CollapsibleSectionStateMigration`.

**Verification:**

- `Grep` - `_land_` / `_port_` section-key patterns absent outside the migration class.
- `Grep` - `resource_editor_ui_state` referenced only in `CollapsibleSectionStateMigration.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. No legacy `_land_`/`_port_` section-key patterns outside the migration class; `resource_editor_ui_state` and `add_resource_ui_state` referenced only in `CollapsibleSectionStateMigration.kt`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - forced `compileStandardDebugKotlin --rerun-tasks` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `assert-neuroslop` PASS via post-change (layout hex reduced, no new hex).
- [x] Dev log entry added via post-change.ps1 (one Phase-04 entry, CLAUDE.md §12).
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to ticket-end catalog_sync (no new binder added).

---

## Handoff Notes to Next Phase

Source editors and player panels are unified. Player layout hex/visibility defects are closed. Phase 05 migrates the list-based consumers and dialogs and absorbs the second (statistics) mechanism.

---

## Rollback Plan

Revert phase commit(s). Player layout reverts to hex/text-prefix; editors revert to bespoke section code reading legacy namespaces (still present). No data loss.
