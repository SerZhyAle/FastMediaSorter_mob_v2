# Phase 05 — Player panels + ScheduledOperationDialog

**Strategic spec:** [`../S0256_collapsible-section-header.md`](../S0256_collapsible-section-header.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Migrate three remaining XML-defined collapsible headers to the canonical component:

- Player Copy-to panel header (with landscape counterpart).
- Player Move-to panel header (with landscape counterpart).
- ScheduledOperationDialog "Conditions" header (with landscape counterpart).

Player headers currently use a leading-indicator pattern (separate `▼/▶` TextView). ScheduledOperationDialog currently uses a chevron + caption typography (compact style). Both converge on the canonical style.

---

## Prerequisites

- [ ] Phase 01 is `✅ Done`.
- [ ] `temp/research/collapsible_groups_inventory.md` groups 34, 36, 37 understood.
- [ ] Player persistence state model known: `AppSettings.copyPanelCollapsed`, `AppSettings.movePanelCollapsed` (DataStore via `SettingsRepositoryImpl`, mirrored into backup via `BackupMapper`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/player_bottom_panels_container_content.xml` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout-land/player_bottom_panels_container_content.xml` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 400 of delta |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout/dialog_scheduled_operation.xml` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout-land/dialog_scheduled_operation.xml` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt` | Modified | ≤ 300 |

---

## Steps

### Step 05.1 — Migrate Player Copy/Move panel headers

**Files:** both `player_bottom_panels_container_content.xml` (portrait + landscape), `CommandPanelController.kt`, `DestinationButtonsManager.kt`

**Prompt for developer:**

> In both layouts: replace the `copyToPanelHeader` row (`LinearLayout` containing `copyToPanelIndicator` + the title TextView) with a `CollapsibleSectionHeader` instance — `app:csh_title="@string/<copy_to_title>"` (verify the exact resource), `app:csh_showHelp="false"`. Same for the `moveToPanelHeader` row.
>
> Drop the separate `copyToPanelIndicator` / `moveToPanelIndicator` leading TextViews — the indicator becomes the component's built-in `▼/▶` prefix on the title.
>
> In `CommandPanelController.kt` lines L229-238: replace the click wiring that today flips the indicator text via `DestinationButtonsManager.updateCopyPanelVisibility` / `updateMovePanelVisibility`. New pattern: `header.setExpanded(initialState, notify = false)`, then `header.setOnExpandedChangeListener { expanded -> destinationButtonsManager.applyPanelVisibility(panel, expanded); appSettingsWriter.setCopyPanelCollapsed(!expanded) }`.
>
> In `DestinationButtonsManager.kt`: simplify `updateCopyPanelVisibility` (L478-482) and `updateMovePanelVisibility` (L487-491) — remove the indicator-text-flip code, keep the buttons-grid visibility flip (`safeViews.copyToButtonsGrid` / `moveToButtonsGrid`). Keep `updateContainerOrientation(L498)` logic that switches whole container orientation when both panels are collapsed — that is independent of the header style and continues to work.
>
> Persistence: `AppSettings.copyPanelCollapsed` and `movePanelCollapsed` (DataStore + backup) preserved unchanged. The semantics flip (collapsed ↔ expanded) is already handled by the existing writer — keep it.
>
> Note: the leading-indicator string resource `activity_player_unified_moveToPanelIndicator_text` (= "▼") becomes unused. Don't delete it yet — Phase 07 final cleanup. Mark with an `<!-- S0256: candidate for removal -->` TODO comment in `strings.xml` next to the key.

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count == 2 in each of `layout/player_bottom_panels_container_content.xml` and `layout-land/player_bottom_panels_container_content.xml`.
- `Grep` — `@+id/copyToPanelIndicator` not present in either layout.
- `Grep` — `@+id/moveToPanelIndicator` not present in either layout.
- `Grep` — `updateCopyPanelVisibility` body in `DestinationButtonsManager.kt` no longer references `indicatorTextView` or "▼" / "▶" literals (line check via context grep).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count == 2 in `layout/player_bottom_panels_container_content.xml` | actual: 2; expected: `CollapsibleSectionHeader` count == 2 in `layout-land/player_bottom_panels_container_content.xml` | actual: 2; expected: `copyToPanelIndicator` absent in both layouts | actual: absent; expected: `moveToPanelIndicator` absent in both layouts | actual: absent; expected: `DestinationButtonsManager.updateCopyPanelVisibility` / `updateMovePanelVisibility` no longer reference indicator views or `▼` / `▶` literals | actual: none. Files: `app_v2/src/main/res/layout/player_bottom_panels_container_content.xml`, `app_v2/src/main/res/layout-land/player_bottom_panels_container_content.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDialogAndUiStateManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`. Dev log recorded.

---

### Step 05.2 — Migrate ScheduledOperationDialog Conditions header

**Files:** both `dialog_scheduled_operation.xml` (portrait + landscape), `ScheduledOperationDialog.kt`

**Prompt for developer:**

> In both dialog layouts: replace the `headerConditions` row (L108-135 portrait, L134 landscape — header `LinearLayout` + chevron `ivConditionsChevron`) with a `CollapsibleSectionHeader` instance. `app:csh_title="@string/scheduled_ops_col_condition"`, `app:csh_showHelp="false"`.
>
> Today the dialog uses `?attr/textAppearanceCaption` + `alpha=0.7` for a lighter "compact" look. Per strategic §6.4 resolution: unified style — do **not** carry over the caption/alpha. The header now matches the canonical settings-style appearance.
>
> In `ScheduledOperationDialog.kt` `setupConditionsCollapse` (L110-121): replace the chevron rotation + click logic with `header.setExpanded(false, notify = false)` and `header.setOnExpandedChangeListener { expanded -> b.containerConditionsContent.isVisible = expanded }`. State still resets each open (no persistence — per inventory).

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count == 1 in each of `layout/dialog_scheduled_operation.xml` and `layout-land/dialog_scheduled_operation.xml`.
- `Grep` — `@+id/ivConditionsChevron` not present in either layout.
- `Grep` — `setupConditionsCollapse` body in `ScheduledOperationDialog.kt` no longer contains `rotation` (verify via context grep).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count == 1 in `layout/dialog_scheduled_operation.xml` | actual: 1; expected: `CollapsibleSectionHeader` count == 1 in `layout-land/dialog_scheduled_operation.xml` | actual: 1; expected: `ivConditionsChevron` absent in both layouts | actual: absent; expected: `setupConditionsCollapse` contains no `rotation` handling | actual: none. Files: `app_v2/src/main/res/layout/dialog_scheduled_operation.xml`, `app_v2/src/main/res/layout-land/dialog_scheduled_operation.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt`. Dev log recorded.

---

### Step 05.3 — Catalog sync + dev log for Phase 05

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`

**Prompt for developer:**

> Catalog sync + per-file dev log with `S0256 Phase 05:` prefix.

**Verification:**

- `Grep` — `S0256 Phase 05` count ≥ 7 in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `S0256 Phase 05` entries in `dev/CHANGELOG.md` >= 7 | actual: 14. Files: `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`. Catalog sync recorded.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles.
- [ ] Manual smoke (Player): open Player, collapse and expand both Copy/Move panels; restart Player → previously-saved state restored from `AppSettings`.
- [ ] Manual smoke (Dialog): open ScheduledOperationDialog, toggle Conditions section, close and reopen → state resets (expected, unchanged).
- [ ] Dev log entries added.

---

## Handoff Notes to Next Phase

After Phase 05 only two ad-hoc collapsible-header sites remain: item-level (DuplicateGroupAdapter) and programmatic (KeybindingListAdapter). Phase 06 covers them.

---

## Rollback Plan

Revert the seven files touched. `AppSettings.copyPanelCollapsed` / `movePanelCollapsed` field semantics unchanged — no DataStore migration risk.
