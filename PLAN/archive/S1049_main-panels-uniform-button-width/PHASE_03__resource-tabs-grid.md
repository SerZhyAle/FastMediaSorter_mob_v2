# Phase 03 - Resource Tabs Grid (Row 4)

**Strategic spec:** [`../S1049_main-panels-uniform-button-width.md`](../S1049_main-panels-uniform-button-width.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent resources; sequenced after Phase 02 only to avoid two phases editing `activity_main.xml` in the same session
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-15
**Completed:** 2026-07-15

---

## Objective

Give the resource-type tab strip a fixed per-tab width of two shared item-modules (`96dp`, per the owner's
resolved §6 decision), left-aligned instead of stretched to fill, in the portrait bucket only - landscape and
`w600dp` keep today's fixed/fill (or scrollable, on narrow width) behavior unchanged.

---

## Prerequisites

- [ ] Strategic §6 is `Resolved` (already true - see strategic spec).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/dimens_main_panels.xml` | Modified | ≤ 22 |
| `app_v2/src/main/res/values/bools.xml` | Modified | ≤ 10 |
| `app_v2/src/main/res/values-land/bools.xml` | Modified | ≤ 12 |
| `app_v2/src/main/res/values-w600dp/bools.xml` | Modified (file pre-existed - see Step 03.2 correction note) | ≤ 10 |
| `app_v2/src/main/res/layout/activity_main.xml` | Modified | ≤ 700 (already backed up in Phase 02; re-backup if Phase 02 ran in an earlier session) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt` | Modified | ≤ 160 (currently 133) |

> **Landscape / wide parity.** `layout-land/activity_main.xml` and `layout-w600dp/activity_main.xml` both
> already contain the same `tabResourceTypes` `TabLayout` block (identical `paddingStart`, `tabMode="fixed"`,
> `tabGravity="fill"`) as the portrait file. This phase does NOT add `app:tabMinWidth`/`app:tabMaxWidth` to
> either of those two files - the new `main_resource_tabs_fixed_grid` bool (Step 03.2) is `false` in both
> `values-land` and the new `values-w600dp`, so `MainResourceTabsManager` keeps running its existing
> width-aware `MODE_FIXED`/`MODE_SCROLLABLE` logic there unchanged, matching strategic §3.2's portrait-only
> scope even though the Kotlin method itself is shared across orientations.
> **Why `values-w600dp/bools.xml` must be created, not skipped:** `main_programs_panel_show_labels` /
> `main_streams_panel_show_labels` only have `values/` and `values-land/` overrides today - there is no
> `values-w600dp/bools.xml`. If this phase only added the new bool to `values/` (true) and `values-land/`
> (false), a `w600dp` device (tablet portrait, or a phone landscape wide enough to hit the `w600dp` bucket -
> which takes precedence over `land` in Android's qualifier resolution) would fall through to the `values/`
> default (`true`) while still inflating `layout-w600dp/activity_main.xml`, which has no `tabMinWidth`/
> `tabMaxWidth` - tabs would switch to scrollable+start with no forced width, a visible regression from
> today's fixed/fill on that bucket. The new `values-w600dp/bools.xml` file prevents this.

---

## Steps

### Step 03.1 - Add the shared tab-width dimen

**Files:** `app_v2/src/main/res/values/dimens_main_panels.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a new dimen to `dimens_main_panels.xml`, next to `main_panel_item_min_width`:
> ```xml
> <!-- S1049: resource tabs carry a text label (not just an icon), so each tab is two item-modules wide -
>      owner decision, strategic §6. -->
> <dimen name="main_panel_tab_min_width">96dp</dimen>
> ```

**Verification:**

- `Grep` - `app_v2/src/main/res/values/dimens_main_panels.xml` contains `main_panel_tab_min_width">96dp<` (1 match).

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification 1/1 PASS.

---

### Step 03.2 - Add the fixed-grid bool across all three qualifier buckets

**Files:** `app_v2/src/main/res/values/bools.xml`, `app_v2/src/main/res/values-land/bools.xml`, `app_v2/src/main/res/values-w600dp/bools.xml`
**Depends on:** - start of phase (independent of Step 03.1)

**Prompt for developer:**

> Add `main_resource_tabs_fixed_grid` to all three files, following the existing
> `main_programs_panel_show_labels` pattern in `values/bools.xml` and `values-land/bools.xml`:
> - `values/bools.xml` (portrait default): `<bool name="main_resource_tabs_fixed_grid">true</bool>`
> - `values-land/bools.xml`: `<bool name="main_resource_tabs_fixed_grid">false</bool>`
> - `values-w600dp/bools.xml` - **correction during execution:** this file already exists (`is_tab_inline`,
>   `is_resource_actions_inline`) - the tactical plan's "create it" assumption was wrong (only checked for
>   this specific bool name during `/spec-tech`, not file existence). Add the new bool to the existing file
>   instead of creating a new one: `<bool name="main_resource_tabs_fixed_grid">false</bool>`.
> Add a one-line comment above each new entry in all three files explaining it drives the resource-tabs
> width strategy (S1049), matching the existing comment style.

**Verification:**

- `Grep` - `app_v2/src/main/res/values/bools.xml` contains `main_resource_tabs_fixed_grid">true<`.
- `Grep` - `app_v2/src/main/res/values-land/bools.xml` contains `main_resource_tabs_fixed_grid">false<`.
- `Glob` - `app_v2/src/main/res/values-w600dp/bools.xml` exists.
- `Grep` - `app_v2/src/main/res/values-w600dp/bools.xml` contains `main_resource_tabs_fixed_grid">false<`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification 4/4 PASS. `values/bools.xml`=true, `values-land/bools.xml`=false, `values-w600dp/bools.xml`=false. Correction: `values-w600dp/bools.xml` already existed (2 unrelated bools) - edited in place rather than created new.

---

### Step 03.3 - Constrain the portrait tab width in XML

**Files:** `app_v2/src/main/res/layout/activity_main.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the portrait `activity_main.xml`, on the `tabResourceTypes` `TabLayout` element, add two attributes
> (do not change `app:tabMode`/`app:tabGravity` in this file - Kotlin sets both at runtime unconditionally,
> see Step 03.4, so the static XML values are inert design-time defaults not worth touching):
> ```xml
> app:tabMinWidth="@dimen/main_panel_tab_min_width"
> app:tabMaxWidth="@dimen/main_panel_tab_min_width"
> ```
> Do not add these attributes to `layout-land/activity_main.xml` or `layout-w600dp/activity_main.xml` - see
> "Landscape / wide parity" above.

**Verification:**

- `Grep` - `app_v2/src/main/res/layout/activity_main.xml` contains `app:tabMinWidth="@dimen/main_panel_tab_min_width"` (1 match) and `app:tabMaxWidth="@dimen/main_panel_tab_min_width"` (1 match).
- `Grep` - `app_v2/src/main/res/layout-land/activity_main.xml` for `tabMinWidth` → 0 matches.
- `Grep` - `app_v2/src/main/res/layout-w600dp/activity_main.xml` for `tabMinWidth` → 0 matches.

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification 4/4 PASS. Portrait: 1 `tabMinWidth` + 1 `tabMaxWidth`. `layout-land`/`layout-w600dp`: 0 matches each (untouched).

---

### Step 03.4 - Branch the tab mode/gravity decision on the new bool

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt`
**Depends on:** Step 03.2, Step 03.3

**Prompt for developer:**

> In `createTabs()`, the current block:
> ```kotlin
> // Width-aware mode: scrollable on narrow phones to avoid truncated labels.
> val screenWidthDp = configuration.screenWidthDp
> if (screenWidthDp < 480) {
>     tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
>     tabLayout.tabGravity = TabLayout.GRAVITY_START
> } else {
>     tabLayout.tabMode = TabLayout.MODE_FIXED
>     tabLayout.tabGravity = TabLayout.GRAVITY_FILL
> }
> ```
> becomes:
> ```kotlin
> // S1049: the portrait-only fixed-grid bucket (main_resource_tabs_fixed_grid) always goes scrollable +
> // start-aligned so app:tabMinWidth/tabMaxWidth (main_panel_tab_min_width, 2x the shared item module,
> // Step 03.1) are respected exactly instead of being overridden by fill-stretch. Landscape/w600dp leave
> // this bool false and keep the pre-existing width-aware fixed/fill split below, unchanged.
> val fixedGrid = tabLayout.resources.getBoolean(R.bool.main_resource_tabs_fixed_grid)
> val screenWidthDp = configuration.screenWidthDp
> if (fixedGrid || screenWidthDp < 480) {
>     tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
>     tabLayout.tabGravity = TabLayout.GRAVITY_START
> } else {
>     tabLayout.tabMode = TabLayout.MODE_FIXED
>     tabLayout.tabGravity = TabLayout.GRAVITY_FILL
> }
> ```
> `fixedGrid || screenWidthDp < 480` keeps the narrow-phone scrollable fallback working exactly as before in
> the non-fixed-grid (landscape/w600dp) buckets, while every portrait device takes the scrollable+start
> branch regardless of width - `tabMinWidth`/`tabMaxWidth` from Step 03.3 do the actual sizing there.

**Verification:**

- `Grep` - `MainResourceTabsManager.kt` contains `R.bool.main_resource_tabs_fixed_grid`.
- `Grep` - `MainResourceTabsManager.kt` contains `fixedGrid || screenWidthDp < 480`.
- `Grep` - `MainResourceTabsManager.kt` for `Log\.d\(` → 0 hits (Timber-only).

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification 3/3 PASS. `R.bool.main_resource_tabs_fixed_grid` referenced once, `fixedGrid || screenWidthDp < 480` present once, 0 `Log.d(` hits. `480` extracted to `NARROW_TAB_LAYOUT_MAX_WIDTH_DP` companion const (detekt MagicNumber). Added `Timber.d("S1049: ...")` debug tag per final-phase tag insertion; flipped journal status to `BlockNeedUserTest` immediately (not deferred to post-Phase-04) so `assert-no-ticket-logs` blesses the tag. Post-change also surfaced a genuinely pre-existing, unrelated `LongParameterList` finding on the untouched 10-param constructor (never previously baselined); resolved with one targeted baseline entry sourced from a scoped `detektBaseline` regen (diffed and reverted everything except this file's line, per CLAUDE.md Rule 13 dead-weight/scope discipline - a full regen pulled in ~400 unrelated line changes that were not committed). `post-change.ps1` full PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). BUILD SUCCESSFUL in 1m 47s (`build_debug_20260715_005424.log`) - single build validates implementation + the BlockNeedUserTest debug tag together.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (plus `config/detekt/baseline-app_v2.xml`, an unplanned but necessary detour - see Step 03.4 log).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (via `post-change.ps1`'s `catalog-sync` step on the Step 03.4 edit).

---

## Handoff Notes to Next Phase

All three row-level fixes (streams chip width, command-bar grid, resource-tabs grid) are now in source.
Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Low-risk: revert phase commit(s), or restore the `activity_main.xml` backup. The new bool/dimen resources
and the `createTabs()` branch are additive and self-contained - no data migration, no persisted-state format
change.
