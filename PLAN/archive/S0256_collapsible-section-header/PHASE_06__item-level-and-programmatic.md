# Phase 06 — Item-level + Programmatic headers

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

Migrate the two remaining special-shape collapsible headers:

- **Item-level (RecyclerView item):** `DuplicateGroupAdapter` group header (`item_duplicate_group.xml`).
- **Programmatic (no XML):** `KeybindingListAdapter` group header — view built in Kotlin via `createHeaderView`.

Both cases prove out the component's `setExpanded` event API working without a sibling content container (item-level reflow via adapter, programmatic reflow via list-content rebuild), per strategic §5.3 ADR.

---

## Prerequisites

- [ ] Phase 01 is `✅ Done`.
- [ ] `temp/research/collapsible_groups_inventory.md` groups 35, 38 understood.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/item_duplicate_group.xml` | Modified | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicateGroupAdapter.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingListAdapter.kt` | Modified | ≤ 200 |

---

## Steps

### Step 06.1 — Migrate `DuplicateGroupAdapter` item header (item-level mode)

**Files:** `item_duplicate_group.xml`, `DuplicateGroupAdapter.kt`

**Prompt for developer:**

> In the layout: replace the row containing `tvGroupSize`, `tvGroupCount`, and `ivExpand` with a `CollapsibleSectionHeader` instance. The header carries dynamic title (file size + file count), so the title is set in code via `header.setTitle(...)`. `app:csh_showHelp="false"`. Keep the sibling `rvFiles` RecyclerView intact — it stays the toggled content target.
>
> In `DuplicateGroupAdapter.bind` (L51-72): replace the rotation logic on `ivExpand` and the click wiring on the row with `header.setExpanded(expandedGroups.contains(groupId), notify = false)` and `header.setOnExpandedChangeListener { expanded -> if (expanded) expandedGroups.add(groupId) else expandedGroups.remove(groupId); binding.rvFiles.isVisible = expanded }`.
>
> The header still uses content descriptions `R.string.cd_collapse_group` / `R.string.cd_expand_group` — wire them via `header.setContentDescriptions(collapseRes, expandRes)` if such API exists on the component, or set them manually after each state change. Note: if the component doesn't expose this yet, document the gap in the step log and either (a) extend the component API (a small follow-up to Phase 01 — keep API change minimal and add a unit test) or (b) skip the dynamic content-description and use a single static one. **Decision deferred to implementation moment** — but log it explicitly.
>
> In-memory adapter state (`expandedGroups: MutableSet<String>`) preserved as-is. No persistence change.

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count == 1 in `item_duplicate_group.xml`.
- `Grep` — `@+id/ivExpand` not present in `item_duplicate_group.xml`.
- `Grep` — `ivExpand.rotation` not present in `DuplicateGroupAdapter.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count == 1 in `item_duplicate_group.xml` | actual: 1; expected: `ivExpand` absent in `item_duplicate_group.xml` | actual: absent; expected: `ivExpand.rotation` absent in `DuplicateGroupAdapter.kt` | actual: absent. Accessibility gap resolved by extending `CollapsibleSectionHeader` with optional expand/collapse content descriptions instead of downgrading to a static label. Files: `app_v2/src/main/res/layout/item_duplicate_group.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicateGroupAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeaderTest.kt`. Dev log recorded; targeted unit test passed.

---

### Step 06.2 — Migrate `KeybindingListAdapter` programmatic header

**Files:** `KeybindingListAdapter.kt`

**Prompt for developer:**

> In `createHeaderView` (L111-137): replace the manually-built `LinearLayout` + `TextView` + reset-`ImageView` row with a programmatically-instantiated `CollapsibleSectionHeader`:
>
> ```
> val header = CollapsibleSectionHeader(parent.context).apply {
>     setTitle(groupLabel)
>     setExpanded(isExpanded, notify = false)
>     setTrailingControl(resetIcon)   // the reset button keeps its own click
>     setOnExpandedChangeListener { expanded -> onGroupHeaderClick(groupId, expanded) }
> }
> ```
>
> In `HeaderViewHolder.bind` (L64-71): replace the prefix-building line with `header.setExpanded(headerData.isExpanded, notify = false)` — let the component own the prefix.
>
> The reset-button `ImageView` continues to be a separate view, but now lives in the component's trailing slot instead of being a sibling of the title. Its click handler stays unchanged.
>
> The `onGroupHeaderClick` callback owns the list-content rebuild — the component does not touch the RecyclerView. This matches strategic §6.3 resolution.

**Verification:**

- `Grep` — `CollapsibleSectionHeader(` count ≥ 1 in `KeybindingListAdapter.kt` (programmatic instantiation).
- `Grep` — the old prefix-format line `"$prefix  $groupLabel"` not present in `KeybindingListAdapter.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: programmatic `CollapsibleSectionHeader(` instantiation exists in `KeybindingListAdapter.kt` | actual: present; expected: old prefix-format line `"$prefix  $groupLabel"` absent | actual: absent. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingListAdapter.kt`. Dev log recorded.

---

### Step 06.3 — Catalog sync + dev log for Phase 06

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`

**Prompt for developer:**

> Catalog sync + per-file dev log with `S0256 Phase 06:` prefix.

**Verification:**

- `Grep` — `S0256 Phase 06` count ≥ 3 in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `S0256 Phase 06` entries in `dev/CHANGELOG.md` >= 3 | actual: 6. Files: `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`. Catalog sync recorded.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] Project compiles.
- [ ] Manual smoke (DuplicateGroupAdapter): open Duplicates screen, expand/collapse group cards.
- [ ] Manual smoke (KeybindingListAdapter): open the keybinding remap screen, toggle each group; reset button still works.
- [ ] Dev log entries added.

---

## Handoff Notes to Next Phase

After Phase 06 there are **no remaining ad-hoc collapsible-group headers in `app_v2/src/main/`**. Phase 07 verifies this by grep, regenerates the catalog one last time, and adds the release-note hint.

---

## Rollback Plan

Revert each of the three files. No data migration; adapter state remains in memory.
