# PHASE_03 - Layout: remove HorizontalScrollView

**Strategic spec:** `PLAN/S0374_browse-top-command-buttons-20pct.md`
**Status:** Pending

## Goal

Delete the `topCommandScroll` `HorizontalScrollView` wrapper in both orientations; promote `layoutControls` to the constrained top bar. No clipping, no scroll.

## Steps

### Step 3.1 - Portrait `activity_browse.xml`

In `app_v2/src/main/res/layout/activity_browse.xml`:
- Remove the `<HorizontalScrollView android:id="@+id/topCommandScroll" ...>` open tag and its matching `</HorizontalScrollView>` close tag.
- Move the constraint attributes from the HSV onto `layoutControls`: `layout_width="@dimen/match_constraint"` (0dp), `layout_constraintStart/End_toStartOf/EndOf="parent"`, `layout_constraintTop_toTopOf="parent"`.
- Keep `layoutControls` `orientation="horizontal"`; its children and ids are unchanged.
- Replace the stale S0374 scroll comment with: `<!-- S0374: adaptive priority bar - overflow commands move to the "⋮" menu (BrowseCommandOverflowManager); no horizontal scroll. -->`.
- Verify `layoutResourceInfo` still constrains `Top_toBottomOf="@id/layoutControls"` (was `@id/topCommandScroll`) - update that reference.

### Step 3.2 - Landscape `layout-land/activity_browse.xml`

Apply the identical structural change (Strict Rule 12). Update any `topCommandScroll` reference to `layoutControls`.

### Step 3.3 - Grep for residual references

`Grep` `topCommandScroll` across `app_v2/src/main` → expected after edits: 0 in layout XML; any remaining `.kt` reference (e.g. old `ResourceOpsMenuManager.isControlFullyVisibleInCommandViewport`) is removed in PHASE_04.

**Verification:**
- `Grep` `topCommandScroll` in `app_v2/src/main/res/layout/activity_browse.xml` → expected: 0 | actual: record.
- `Grep` `topCommandScroll` in `app_v2/src/main/res/layout-land/activity_browse.xml` → expected: 0 | actual: record.
- `Grep` `layout_constraintTop_toBottomOf="@id/layoutControls"` in both layouts → expected: ≥1 each | actual: record.
- Build gate deferred to PHASE_05 (the `.kt` reference to `topCommandScroll` still exists until PHASE_04).

## Phase Done Criteria

- [ ] No `HorizontalScrollView` / `topCommandScroll` in either `activity_browse.xml`.
- [ ] `layoutControls` carries the parent constraints + `match_constraint` width in both orientations.
- [ ] `layoutResourceInfo` top constraint retargeted to `@id/layoutControls` in both orientations.
- [ ] Portrait and landscape edited in the same phase (Rule 12).
