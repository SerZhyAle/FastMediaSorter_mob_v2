# Phase 01 - Grid layout container

**Strategic spec:** [`../S1161_landscape-settings-collapsed-groups-columns.md`](../S1161_landscape-settings-collapsed-groups-columns.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Introduce `SettingsGroupsGridLayout` - a `ViewGroup` that lays its children out in N columns, giving
a full-width span to any child that is expanded or is not a group card - plus the integer resource
that supplies N. Nothing installs it yet.

---

## Prerequisites

- [x] On a feature branch (the tree carries other tickets' WIP - hence `-ScopeToFile` at closure).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsGroupsGridLayout.kt` | New | ≤ 210 |
| `app_v2/src/main/res/values/integers.xml` | Modified | +1 line |
| `app_v2/src/main/res/values-land/integers.xml` | Modified | +1 line |

No layout XML is edited in this ticket, so CLAUDE.md Rule 11 (landscape parity) does not apply -
the landscape difference is carried entirely by the integer resource.

---

## Steps

### Step 01.1 - Add the column-count integer resource

**Files:** `app_v2/src/main/res/values/integers.xml`, `app_v2/src/main/res/values-land/integers.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `<integer name="settings_group_columns">1</integer>` to `values/integers.xml` and
> `<integer name="settings_group_columns">2</integer>` to `values-land/integers.xml`. Do **not** add
> a `values-sw600dp` variant: strategic §11 criterion 3 requires portrait layout to be unchanged on
> every device, and `settings_send_commands_columns` (the neighbouring resource) does define a
> `sw600dp` value - copying it here would silently give portrait tablets two columns.

**Verification:**

- `Grep` - `settings_group_columns` matches exactly once in `app_v2/src/main/res/values/integers.xml`.
- `Grep` - `settings_group_columns` matches exactly once in `app_v2/src/main/res/values-land/integers.xml`.
- `Grep` - `settings_group_columns` returns zero hits under `app_v2/src/main/res/values-sw*/`.

**Status:** `[x]` done

---

### Step 01.2 - Implement `SettingsGroupsGridLayout`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsGroupsGridLayout.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `SettingsGroupsGridLayout : ViewGroup` with the standard three-constructor `@JvmOverloads`
> shape. Read the column count in `onMeasure` from `resources.getInteger(R.integer.settings_group_columns)`
> - reading it per pass (not caching it in a field) is what makes rotation free, because the view's
> `Resources` already reflect the new configuration by the time the re-layout runs.
>
> Span rule for a child: full span when the column count is 1, when the child is `GONE`, when it is
> not a group card, or when its group card is expanded; otherwise one column. A child "is a group
> card that is collapsed" iff a `CollapsibleSectionHeader` can be found in its subtree and that
> header's `isExpanded()` is `false`. Bound the header search to a small depth constant - a card
> nests the header two levels down (`MaterialCardView` → `LinearLayout` → header), so depth 3 is
> enough and stops the search from descending into section content.
>
> Lay out row by row: walk children in order, accumulating single-span children into the current row
> until the row is full or a full-span child arrives; a full-span child always occupies a row of its
> own. Within a row of single-span children, place them left to right - the column-major *reading*
> order required by strategic §5.1 is produced in Step 01.3, which reorders the run before it is
> chunked into rows, so this layout pass stays a plain sequential walk.
>
> Row height is the tallest measured child in that row. Support `MarginLayoutParams` (cards carry
> horizontal margins) and honour the view's own padding. Column width is
> `(availableWidth - totalHorizontalGaps) / columns`; give the remainder pixels to the last column so
> the right edge lands exactly on the content bound.
>
> Measure single-span children with an EXACTLY width spec of the column width and an AT_MOST height
> spec, and full-span children with an EXACTLY width spec of the full content width. Report the
> accumulated height in `onMeasure`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsGroupsGridLayout.kt` exists.
- `Grep` - `class SettingsGroupsGridLayout` matches exactly once.
- `Grep` - `override fun onMeasure` and `override fun onLayout` each match exactly once.
- `Grep` - `R.integer.settings_group_columns` appears inside `onMeasure`, not in a field initializer.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 01.3 - Column-major ordering inside each collapsed run

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsGroupsGridLayout.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Before chunking a run of consecutive single-span children into rows, reorder that run so reading
> down the left column follows the original child order. For a run of `n` children in `c` columns:
> column `0` takes the first `ceil(n / c)` children, the next column takes the following
> `ceil(remaining / remainingColumns)`, and so on - the same balanced split
> `PlaybackSettingsFragment.distributeColumnMajor` already uses for rows inside a group. Row `r` of
> the run is then formed by taking index `r` of each column, and a column that ran out contributes an
> empty cell so the row keeps its column alignment.
>
> A run is bounded by a full-span child or by the end of the child list. Reordering is positional
> only - never call `removeView`/`addView`, because child order is what D-pad traversal and the
> caller's view references depend on.

**Verification:**

- `Grep` - the file contains no `removeView` / `addView` / `removeAllViews` call.
- `Grep` - a private ordering function exists whose name contains `ColumnMajor`.
- `.\a.ps1 fk` - exit code 0 (Kotlin compile, standard flavor).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`SettingsGroupsGridLayout` is self-contained and compiled but not referenced anywhere. Phase 02
installs it. It reads span state from the live view tree only - it holds no reference to
`CollapsibleSectionsManager` and registers no listeners.

---

## Rollback Plan

Delete the new file and the two integer entries - nothing else references them.
