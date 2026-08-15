# Phase 04 — ResourceEditor migration

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

Migrate the six chevron-style collapsible groups in `ResourceEditorFragment` to `CollapsibleSectionHeader`. Drop the `setupCollapsibleSections` / `setupCollapsibleHeader` / `setSectionExpanded` helpers in favor of the listener-based wiring used by other migrated screens.

---

## Prerequisites

- [ ] Phase 01 is `✅ Done`.
- [ ] `temp/research/collapsible_groups_inventory.md` groups 28–33 understood.
- [ ] Existing prefs file `resource_editor_ui_state` keys known (pattern: `editor_<type>_<port|land>_<id>`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_resource_editor.xml` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt` | Modified | ≤ 800 |

`fragment_resource_editor.xml` has no landscape counterpart — explicit note: landscape variant absent, not needed.

---

## Steps

### Step 04.1 — Migrate the six ResourceEditor groups

**Files:** `fragment_resource_editor.xml`, `ResourceEditorFragment.kt`

**Prompt for developer:**

> In the layout: replace each header row of the six groups (Connection Settings, Media Types, Scanning, Destination, Advanced, Statistics) with a `CollapsibleSectionHeader` instance. Each row today is a horizontal `LinearLayout` containing the title `TextView` (e.g. `headerConnectionSettings`) and a chevron `ImageView` (e.g. `ivConnectionExpand`) — both get replaced by the single component node.
>
> Use `app:csh_title` from the existing string resources (`@string/resource_editor_connection_settings`, etc. — verify the exact resource names). `app:csh_showHelp="false"` for all six; TODO-comment in XML next to each for the next content pass.
>
> For the `Statistics` group: it starts with `android:visibility="gone"` in XML and is shown by code only in EDIT mode. Preserve this — set `android:visibility="gone"` on the `CollapsibleSectionHeader` node and the existing show-in-EDIT-mode code keeps working unchanged.
>
> For the `Media Types` group: the toggled scope today is an inner `GridLayout` (not a wrapper container) — that is the inconsistency noted in inventory group 29. Preserve this scope: the new listener toggles the inner `GridLayout` directly. Optionally wrap it in a `FrameLayout` first to align with other groups; if wrapping changes layout behavior visibly, leave the GridLayout-direct binding as is.
>
> In Kotlin: remove `setupCollapsibleHeader`, `setSectionExpanded`, the manual chevron-rotation logic, and the `sectionStateKey` helper if it becomes single-call. Replace `setupCollapsibleSections` with the listener-loop pattern.

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count == 6 in `fragment_resource_editor.xml`.
- `Grep` — `ivConnectionExpand`, `ivMediaTypesExpand`, `ivScanningExpand`, `ivDestinationExpand`, `ivAdvancedExpand`, `ivStatisticsExpand` — each returns zero hits in the layout (chevron views gone).
- `Grep` — `setupCollapsibleHeader(` not present in `ResourceEditorFragment.kt`.
- `Grep` — `setSectionExpanded(` not present in `ResourceEditorFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count == 6 in `fragment_resource_editor.xml` | actual: 6; expected: `ivConnectionExpand` absent | actual: absent; expected: `ivMediaTypesExpand` absent | actual: absent; expected: `ivScanningExpand` absent | actual: absent; expected: `ivDestinationExpand` absent | actual: absent; expected: `ivAdvancedExpand` absent | actual: absent; expected: `ivStatisticsExpand` absent | actual: absent; expected: `setupCollapsibleHeader(` absent in `ResourceEditorFragment.kt` | actual: absent; expected: `setSectionExpanded(` absent in `ResourceEditorFragment.kt` | actual: absent. Files: `app_v2/src/main/res/layout/fragment_resource_editor.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorFragment.kt`. Dev log recorded.

---

### Step 04.2 — Verify drawable usage after migration

**Files:** project-wide grep, no edits

**Prompt for developer:**

> Run `Grep -r "ic_expand_more"` across `app_v2/src/main/res/layout/` and `app_v2/src/main/res/layout-land/` to identify any remaining usages of the chevron drawable. If only Phases 05/06 sites remain (Player panel header, ScheduledOperationDialog, DuplicateGroupAdapter) — note in step log "expected sites: <list>". If any usage is found that does not belong to a known later phase — flag in Blockers Log of INDEX.md and pause.
>
> Do NOT delete the drawable file in this step; final cleanup goes to Phase 07.

**Verification:**

- `Grep` — `ic_expand_more` in `app_v2/src/main/res/layout/` returns zero hits across all *settings* + AddResource + ResourceEditor layouts (sites migrated in Phases 02–04).
- Any remaining hits are explicitly inside `dialog_scheduled_operation.xml`, `item_duplicate_group.xml`, `player_bottom_panels_container_content.xml`, or layouts not in scope of S0256 — listed in the step log.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: no `ic_expand_more` hits remain in migrated settings/AddResource/ResourceEditor layouts | actual: none. expected residual sites: Phase 05/06 only | actual: `app_v2/src/main/res/layout/dialog_scheduled_operation.xml`, `app_v2/src/main/res/layout-land/dialog_scheduled_operation.xml`, `app_v2/src/main/res/layout/item_duplicate_group.xml`. Additional ResourceEditor profile selector reuse was converted to `ic_arrow_drop_down` before this check.

---

### Step 04.3 — Catalog sync + dev log for Phase 04

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`

**Prompt for developer:**

> Catalog sync + per-file dev log with `S0256 Phase 04:` prefix.

**Verification:**

- `Grep` — `S0256 Phase 04` count ≥ 2 in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `S0256 Phase 04` entries in `dev/CHANGELOG.md` >= 2 | actual: 4. Files: `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`. Catalog sync recorded.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` is `[x] done`.
- [ ] Project compiles.
- [ ] Manual smoke: open ResourceEditor in CREATE and EDIT modes; expand each of the six groups; close and reopen — state persists. In EDIT mode the Statistics group is reachable.
- [ ] Dev log entries added.

---

## Handoff Notes to Next Phase

After Phase 04 only three sites still use ad-hoc collapsible headers: Player Copy/Move panels, ScheduledOperationDialog, and item-level / programmatic cases. Those are Phases 05 and 06.

---

## Rollback Plan

Revert layout + fragment. Prefs keys unchanged.
