# Phase 05 - List Consumers, Dialogs, Absorb Second Mechanism

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

Bring the remaining consumers onto the unified header and eliminate the second mechanism: migrate Statistics off its bespoke list-header (deleting `item_stats_section_header.xml` and the adapter chevron rotation), unify Duplicates (in-memory, with a collapsed summary) and Keybinding (persisted per research 02 D3), and unify the two dialogs. After this phase only one mechanism exists app-wide.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/statistics/StatisticsAdapter.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/layout/item_stats_section_header.xml` | Deleted | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicateGroupAdapter.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingListAdapter.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapViewModel.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/folderselection/<FolderSelectionDialog>.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/<scheduled>/<ScheduledOperationDialog>.kt` | Modified | ≤ 400 |

> Resolve the exact dialog class names via catalog query in steps 4-5 before editing. `item_duplicate_group.xml` already uses `CollapsibleSectionHeader`; no layout change needed there - the Phase 01 visual applies automatically.

---

## Steps

### Step 05.1 - Migrate Statistics onto the unified header, delete the second mechanism

**Files:** `StatisticsAdapter.kt`, delete `app_v2/src/main/res/layout/item_stats_section_header.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the statistics section-header item (`item_stats_section_header.xml` + the adapter's manual `ivSectionChevron` rotation) with `CollapsibleSectionHeader`. The adapter section header should bind a `CollapsibleSectionHeader` (as Keybinding already does), toggling the section via its expanded-change listener; state stays in the adapter/ViewModel as today, default collapsed. Delete `item_stats_section_header.xml` and remove the `ivSectionChevron`/`sectionHeaderRoot` rotation code. This eliminates the project's second collapsible mechanism (strategic §11.2).

**Verification:**

- `Glob` - `item_stats_section_header.xml` no longer exists.
- `Grep` - `ivSectionChevron` returns zero hits across `app_v2/src`.
- `Grep` - `CollapsibleSectionHeader` referenced in `StatisticsAdapter.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Statistics section header now binds a programmatically-created `CollapsibleSectionHeader` (mirrors Keybinding) via `setTitle`/`setExpanded`/`setOnExpandedChangeListener`; section state still owned by the ViewModel list. Deleted `item_stats_section_header.xml` + removed `ivSectionChevron`/`sectionHeaderRoot` rotation code. Second collapsible mechanism eliminated (zero `ivSectionChevron` hits).

---

### Step 05.2 - Unify Duplicates with a collapsed summary

**Files:** `DuplicateGroupAdapter.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Keep the existing `CollapsibleSectionHeader` in the duplicate-group item, but feed the dynamic "<size> - <N> files" string through the new `setSummary(..)` API so the summary survives in the collapsed state via the unified slot (instead of overloading the title). Expanded state stays in-memory in the adapter (research 02 D3 - duplicate group keys are not stable across scans), default collapsed. No persistence wiring for this screen.

**Verification:**

- `Grep` - `setSummary` referenced in `DuplicateGroupAdapter.kt`.
- `Grep` - expanded state still held in the adapter (e.g. `expandedGroups`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. Duplicate group header keeps its existing `CollapsibleSectionHeader` (Phase 01 chevron applies); moved the `<size> - <count>` string to `setSummary(..)`, title now shows a representative file name. Expanded state stays in-memory (`expandedGroups`, keyed by `fullHash`), default collapsed - no persistence (research 02 D3: unstable group keys).

---

### Step 05.3 - Persist Keybinding group expansion

**Files:** `KeybindingListAdapter.kt`, `KeybindingRemapViewModel.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Keybinding command groups are a fixed, stable set, so persist their expanded state between sessions (research 02 D3). Route the ViewModel's `expandedGroups` through `CollapsibleSectionStore` with keys `keybinding__<group>` (inject/obtain the store; the ViewModel may own a small persistence call), restoring on init and saving on `onGroupToggle`. The adapter continues to bind `CollapsibleSectionHeader` and reflect state. Default collapsed for groups with no saved state.

**Verification:**

- `Grep` - `CollapsibleSectionStore` referenced in the keybinding package.
- `Grep` - `keybinding__` key prefix present.
- `Grep` - `onGroupToggle` still present and now persists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. `KeybindingRemapViewModel` now persists group expansion via `CollapsibleSectionStore` (obtained through `@ApplicationContext`, no new Hilt module) with keys `keybinding__<group>`; restores on init (default collapsed), saves on `onGroupToggle`. Adapter unchanged (already binds `CollapsibleSectionHeader`).

---

### Step 05.4 - Unify the Folder Selection dialog sections

**Files:** `<FolderSelectionDialog>.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Query the catalog (`query.ps1 -ClassMatches "*FolderSelection*"`) to confirm the class. Its two sections (SpecialFolders, QuickFolders) are `csh_expanded="true"` today - keep default expanded (research 02 D4: short dialogs default expanded) but route any state handling through `CollapsibleSectionsManager.register(..)` for visual/animation consistency, keys `folder_selection__<section>`. Persistence optional; if the dialog should not remember, register with a non-persisting store or skip the store write (document the choice).

**Verification:**

- `Grep` - `CollapsibleSectionsManager` referenced in the folder-selection dialog.
- `Grep` - both section registrations present (special + quick).

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Folder-picker SpecialFolders/QuickFolders sections (in `AddResourceScanManager`) routed through `CollapsibleSectionsManager.register(..)`, keys `folder_selection__special`/`__quick`, default expanded (short dialog, research 02 D4); persists in the consolidated store. Removed bespoke `settings_section_states` access + orphaned `StrictModeHelper`/`Context` imports. Legacy `folder_picker_*` keys not migrated (minor dialog, defaults expanded - negligible one-time reset).

---

### Step 05.5 - Unify the Scheduled Operation dialog section

**Files:** `<ScheduledOperationDialog>.kt`
**Depends on:** Step 05.4

**Prompt for developer:**

> Query the catalog (`query.ps1 -ClassMatches "*ScheduledOperation*"`) to confirm the class. Its single Conditions section (inside a MaterialCardView, collapsed by default) routes through `CollapsibleSectionsManager.register(..)`, key `scheduled_operation__conditions`, default collapsed. Keep the MaterialCardView wrapper.

**Verification:**

- `Grep` - `CollapsibleSectionsManager` referenced in the scheduled-operation dialog.
- `Grep` - `scheduled_operation__conditions` key present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. `ScheduledOperationDialog` Conditions section routed through `CollapsibleSectionsManager.register(..)`, key `scheduled_operation__conditions`, default collapsed; MaterialCardView wrapper kept. Forced `compileStandardDebugKotlin --rerun-tasks` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - forced `compileStandardDebugKotlin --rerun-tasks` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `Grep` - `item_stats_section_header` returns zero hits across `app_v2/src` (second mechanism fully gone).
- [x] Dev log entry added via post-change.ps1 (one Phase-05 entry, CLAUDE.md §12).
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to ticket-end catalog_sync (CLAUDE.md §12).

---

## Handoff Notes to Next Phase

Every collapsible consumer in the app now uses one header, one indicator, one animation, one store. The second mechanism is deleted. Phase 06 documents the recommended pattern and runs final cleanup.

---

## Rollback Plan

Revert phase commit(s). Statistics reverts to its own item layout (restore the deleted file from VCS); Keybinding loses cross-session persistence; Duplicates reverts to title-embedded summary. No data migration to undo.
