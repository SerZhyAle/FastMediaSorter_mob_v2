# Phase 04 - Fit the header into a two-cell box

**Strategic spec:** [`../S1642_launcher-section-header-grid-span.md`](../S1642_launcher-section-header-grid-span.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Make `item_launcher_section_header.xml` read correctly at two grid cells wide while keeping the chevron,
the 48dp target and the spoken state it already carries.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Placement decision recorded: strategic §0 "UI decisions / delegation" and §6.1, §6.2, §6.4 - owner ruling of 2026-08-15 fixes 2x1 in both orientations, delegates the state indicator to the agent under the §0 autonomy rule, and §6.2 records the chosen chevron.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/item_launcher_section_header.xml` | Modified | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 480 |

> Landscape parity: `app_v2/src/launcherEnabled/res/layout-land/item_launcher_section_header.xml` is absent
> and stays absent - strategic §6.1 rules one 2x1 header for both orientations, so a landscape variant would
> be a second rule for the same geometry.

---

## Steps

### Step 04.1 - Compact the header layout

**Files:** `app_v2/src/launcherEnabled/res/layout/item_launcher_section_header.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Keep the root `FrameLayout`, the chevron, the bold title and the underline, and make the layout legible in a box roughly two grid cells wide: the title keeps `maxLines="1"` with `ellipsize="end"` and keeps its 48dp floor, the underline keeps `match_parent` so it stops at the header's own edge, and the horizontal padding drops to 4dp so a short title is not squeezed by chrome. Rewrite the file's leading comment, which currently states the header is drawn full-width and therefore needs no landscape counterpart - the new reason for having no counterpart is that strategic §6.1 rules one 2x1 geometry for both orientations. Use no hardcoded hex colour: keep `?attr/colorOutline`, `?attr/colorOnSurface` and `?attr/colorOnSurfaceVariant`.

**Why:**

Strategic §3.2 requires the header to stay a 48dp target whose state is distinguishable without colour, and
§2.3 requires it to remain visible and readable once it no longer has a whole row to spread across.

**Verification:**

- `Grep` - `android:minHeight="48dp"` present in the file.
- `Grep` - `app:srcCompat="@drawable/ic_arrow_drop_down"` present.
- `Grep` - `="#` returns zero hits in the file.
- `Glob` - `app_v2/src/launcherEnabled/res/layout-land/item_launcher_section_header.xml` does not exist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Header layout compacted for a two-cell box (padding 4dp, leading comment rewritten to the 6.1 one-geometry reason); binder span comment no longer claims the header is widened. Layout evidence deferred: the header still renders full-width until phase 05, so the shot showing the compact placement is taken at the ticket's device-test gate, not here (phase Done Criteria do not demand it).

---

### Step 04.2 - Correct the binder's span commentary

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Rewrite the inline comment above the `spanW` argument in `bind`, which states that a section header is widened to the live column count. State instead that the header is drawn at the span the same helper reports to the empty-square sweep, so layout and occupancy cannot disagree - a claim that stays true both before and after phase 05 changes what that helper returns. Leave the code untouched.

**Why:**

Existing comments are requirements per CLAUDE.md Rule 8, and a comment that will describe the opposite of
the shipped behaviour after phase 05 is the kind of stale claim a later reader implements against.

**Verification:**

- `Grep` - `widened to the live column` returns zero hits in that file.
- `Grep` - `LauncherGridGeometry.renderSpanW(item.cell, columns)` still present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Header layout compacted for a two-cell box (padding 4dp, leading comment rewritten to the 6.1 one-geometry reason); binder span comment no longer claims the header is widened. Layout evidence deferred: the header still renders full-width until phase 05, so the shot showing the compact placement is taken at the ticket's device-test gate, not here (phase Done Criteria do not demand it).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The header view survives any width. Phase 05 can narrow it without a second layout pass.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
