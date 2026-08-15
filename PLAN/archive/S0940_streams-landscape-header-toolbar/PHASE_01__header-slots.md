# Phase 01 - Header slots

**Strategic spec:** [`../S0940_streams-landscape-header-toolbar.md`](../S0940_streams-landscape-header-toolbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-07-04
**Completed:** 2026-07-04

---

## Objective

Add an in-header host slot inside the streams toolbar while keeping the existing below-toolbar control bar, in both the portrait and landscape layout variants, so a later manager can move the control group between the two slots. No behaviour change yet.

---

## Prerequisites

- [ ] Strategic §6 items Resolved (they are - see INDEX Blockers).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_streams.xml` | Modified | ≤ 340 |
| `app_v2/src/main/res/layout-land/activity_streams.xml` | Modified | ≤ 340 |

> Landscape variant present - both portrait and landscape files are edited in lockstep (identical host structure), because the streams window does not recreate on rotation and either file may be the one inflated at launch (see `research/03__rotation-no-recreate.md`).

---

## Steps

### Step 01.1 - Add in-header host slot to both layout variants

**Files:** `app_v2/src/main/res/layout/activity_streams.xml`, `app_v2/src/main/res/layout-land/activity_streams.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In both files, add an empty `FrameLayout` with `android:id="@+id/headerControlsHost"` as a direct child of the `MaterialToolbar` (id `toolbar`), sized `layout_width="0dp"`/`layout_height="match_parent"` with `app:layout_constraintHorizontal` not applicable - use `android:layout_width="match_parent"` inside the toolbar and let the placement manager decide visibility. The host starts empty and `android:visibility="gone"`. Do NOT move `streamControls` yet; leave the existing below-toolbar `streamControls` LinearLayout exactly where it is. Keep all existing ids (`streamControls`, `tilSearch`, `etSearch`, `btnFilter`, `btnSort`) unchanged. Do not hardcode hex colors; reuse existing `?attr/` styling. Keep the two files identical in this host structure.

**Verification:**

- `Grep` - `@+id/headerControlsHost` matches once in `app_v2/src/main/res/layout/activity_streams.xml`.
- `Grep` - `@+id/headerControlsHost` matches once in `app_v2/src/main/res/layout-land/activity_streams.xml`.
- `Grep` - `@+id/streamControls` still matches once in each of the two files (bar not removed).

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS. headerControlsHost added inside MaterialToolbar in both layout/ and layout-land/ activity_streams.xml. streamControls unchanged.

---

### Step 01.2 - Preserve focus order across both slots

**Files:** `app_v2/src/main/res/layout/activity_streams.xml`, `app_v2/src/main/res/layout-land/activity_streams.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Confirm the control group keeps a coherent D-pad/TV traversal in both slots: `etSearch`, `btnFilter`, `btnSort` retain `focusable="true"` and their `nextFocus*` chain (search → filter → sort → list). The `headerControlsHost` container itself must not be a focus stop (`android:focusable="false"`); focus lands on the inner fields. Do not add duplicate ids. No Kotlin here.

**Verification:**

- `Grep` - `android:focusable="false"` present on the `headerControlsHost` block in both files.
- `Grep` - `nextFocusRight="@id/btnFilter"` still present on `etSearch` in both files.
- Build resources: `/build` (resources/manifest target) passes.

**Status:** `[x] done`

**Step Log:**

- 2026-07-04 - Verification 3/3 PASS (`a.ps1 fr` BUILD SUCCESSFUL). headerControlsHost has focusable="false" in both files; etSearch focus chain intact.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project resources compile - `a.ps1 fr` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the layout change via `post-change.ps1`.

---

## Handoff Notes to Next Phase

Both layout variants now carry two placement slots: the empty in-header `headerControlsHost` (currently gone) and the populated below-toolbar `streamControls`. Phase 02's manager reparents `streamControls` into `headerControlsHost` for landscape and back for portrait, toggling slot visibility. Ids are stable, so `StreamsActivity`'s existing view lookups keep resolving.

---

## Rollback Plan

Revert phase commit(s) - purely additive layout ids, no data migration or user-facing surface changed.
