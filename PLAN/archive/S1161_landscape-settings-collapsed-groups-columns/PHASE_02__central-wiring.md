# Phase 02 - Central wiring

**Strategic spec:** [`../S1161_landscape-settings-collapsed-groups-columns.md`](../S1161_landscape-settings-collapsed-groups-columns.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Install the grid into every settings tab from one place - `BaseSettingsFragment` - and make rotation
and expand/collapse re-lay-out correctly.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/SettingsGroupColumnsManager.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt` | Modified | ≤ 200 |

---

## Steps

### Step 02.1 - Implement `SettingsGroupColumnsManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/SettingsGroupColumnsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `object SettingsGroupColumnsManager` with
> `fun install(root: ViewGroup): SettingsGroupsGridLayout?`, modelled on the sibling
> `SettingsRowStackManager` (same package neighbourhood, same "post-inflate transform over the
> settings view tree" role).
>
> Locate the tab's group stack: the single vertical `LinearLayout` child of the root when the root is
> a scroll container, otherwise the root itself when it is already a vertical `LinearLayout`. Return
> `null` - changing nothing - unless that stack has at least two direct children whose subtree
> contains a `CollapsibleSectionHeader`. This guard is what keeps the hook safe for tabs that do not
> follow the card-stack shape, now or later.
>
> Move **all** direct children of the stack, in order, into a new `SettingsGroupsGridLayout`, then add
> the grid as the stack's only child with `MATCH_PARENT` × `WRAP_CONTENT`. Carry each child's existing
> `LayoutParams` across so card margins survive. Children that are not group cards (a trailing action
> button, a storage-info row) are moved too and get a full-width span from the grid's own span rule -
> their position in the sequence is preserved.
>
> Do not gate this on orientation. Portrait correctness comes from the column count being 1, which
> makes the grid a plain vertical stack; gating on orientation here would mean the grid is absent
> after a portrait launch and rotation could not introduce it without re-inflating.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `object SettingsGroupColumnsManager` matches exactly once.
- `Grep` - `fun install` matches exactly once and returns a nullable `SettingsGroupsGridLayout`.
- `Grep` - `CollapsibleSectionHeader` is referenced (the guard predicate).

**Status:** `[x]` done

---

### Step 02.2 - Hook the manager into `BaseSettingsFragment`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `onViewCreated`, after the existing `SettingsRowStackManager::stackNarrowPortraitRows` call,
> invoke `SettingsGroupColumnsManager.install(view)` and keep the returned grid in a private nullable
> field. Order matters: the row-stacking pass rewrites `LinearLayout` orientation inside group
> content and must run against the original tree.
>
> Clear the field in `onDestroyView` so a detached view tree is not retained by the fragment.

**Verification:**

- `Grep` - `SettingsGroupColumnsManager.install` matches exactly once in the file.
- `Grep` - the call sits after `stackNarrowPortraitRows` (compare line numbers).
- `Grep` - `onDestroyView` exists in the file and nulls the field.

**Status:** `[x]` done

---

### Step 02.3 - Re-lay-out on configuration change

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/BaseSettingsFragment.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Override `onConfigurationChanged` in `BaseSettingsFragment`: call `super`, then `requestLayout()` on
> the stored grid. `SettingsActivity` declares `android:configChanges`, so fragments are not recreated
> and `layout-land` never re-applies (strategic §4 / ADR-1); the grid re-reads the column integer on
> the next measure pass, so a `requestLayout()` is the whole rotation story.
>
> Three subclasses already override `onConfigurationChanged` - `GeneralSettingsFragment`,
> `OperationsSettingsFragment`, `PlaybackSettingsFragment`. All three already call
> `super.onConfigurationChanged(newConfig)` first; confirm this per file rather than assuming, because
> a subclass that skips `super` would rotate into a stale column count with no visible error.

**Verification:**

- `Grep` - `override fun onConfigurationChanged` matches exactly once in `BaseSettingsFragment.kt`.
- `Grep` - `super.onConfigurationChanged` matches in each of `GeneralSettingsFragment.kt`, `OperationsSettingsFragment.kt`, `PlaybackSettingsFragment.kt`.
- `Grep` - no other file under `ui/settings/fragments/` declares `onConfigurationChanged` without a `super.` call on the following line.

**Status:** `[x]` done

---

### Step 02.4 - Animate the re-flow with the collapse transition

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/SettingsGroupColumnsManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Strategic §7 requires the card re-flow on expand/collapse to happen as one motion with the body
> animation, not as a second jump. `CollapsibleSectionsManager` already calls
> `TransitionManager.beginDelayedTransition` on the *card's inner* container before toggling body
> visibility; that transition does not cover the grid, which is two levels up.
>
> Make the grid participate: in `install`, mark the grid with `isTransitionGroup = false` and enable
> `LayoutTransition` on it via `layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }`,
> so a child's size change animates the surrounding cards into their new slots over the same interval
> the body animation uses.
>
> Verify on device that the two animations read as one motion. If they visibly desynchronise, the
> fallback is to widen the existing delayed transition's scene root instead - but that couples the
> collapse manager to this layout, so only take it if the cheap option fails.

**Verification:**

- `Grep` - `LayoutTransition` is referenced in `SettingsGroupColumnsManager.kt`.
- `.\a.ps1 fc` - exit code 0 (code + resources).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new class).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Pay attention to the view-retention edge: the grid field must not outlive `onDestroyView`.

---

## Handoff Notes to Next Phase

Every settings tab now renders through the grid. Two columns in landscape, one in portrait, expanded
cards full width. What is not yet checked: whether a group title survives half the width, and whether
D-pad traversal follows the columns.

---

## Rollback Plan

Remove the `install` call from `BaseSettingsFragment` - the grid class becomes inert again with no
other change.
