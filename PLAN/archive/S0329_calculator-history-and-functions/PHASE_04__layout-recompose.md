# Phase 04 - Layout Recompose

**Strategic spec:** [`../S0329_calculator-history-and-functions.md`](../S0329_calculator-history-and-functions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (layout-only; safe alongside other phases)
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Step Log (2026-06-02):** Step 04.1 PASS - portrait display/history block changed to `0dp` + `layout_weight="1"` so the button grid (wrap_content) is pinned to the bottom and history fills the space above. Step 04.2: the landscape layout is a horizontal split where the button grid already occupies a full-height weighted column and the history `ScrollView` already fills its column (`layout_weight="1"`); the target end-state (buttons not leaving dead space, history filling) already holds by construction, so no functional XML change was required. Verified by inspection; debug build PASS.

---

## Objective

Pin the button grid to the bottom of the screen and give the freed top space to an enlarged history feed, in both portrait and landscape layouts, without changing button order, ids, or styles.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_calculator.xml` | Modified | - |
| `app_v2/src/main/res/layout-land/activity_calculator.xml` | Modified | - |

> Landscape counterpart exists and is edited in the same phase (CLAUDE.md Rule 12).

---

## Steps

### Step 04.1 - Portrait: bottom-pinned grid, expanded history

**Files:** `app_v2/src/main/res/layout/activity_calculator.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Restructure `calculatorContentGroup` so the history area expands to fill the space above the display and the `calculatorGrid` is pinned to the bottom. Concretely: make the history `ScrollView`/display block take the remaining vertical space (`layout_height="0dp"` + `layout_weight="1"`) and keep the grid at `wrap_content` at the bottom of the vertical `LinearLayout`, so empty space is consumed by history rather than left blank under the buttons. Preserve every button id, style, `contentDescription`, and order. Keep the display value (`calculatorDisplay`) visible and right-aligned; the history feed (`calculatorHistory`) grows upward and stays bottom-aligned within its scroll. Do not change `GridLayout` internals.

**Verification:**

- `Grep` - `calculatorGrid` still present and is the last child of the content `LinearLayout`.
- `Grep` - the history/display block uses `layout_weight="1"` (expands).
- `Grep` - all button ids (`btnCalculatorEquals`, `btnCalculatorSeven`, `btnCalculatorMenu`, ..) still present.

**Status:** `[x] done`

---

### Step 04.2 - Landscape parity

**Files:** `app_v2/src/main/res/layout-land/activity_calculator.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Apply the equivalent intent to the landscape layout: history fills the available area and the grid keeps its place without leaving dead space, consistent with the portrait result. The landscape layout is a horizontal split (history/display column + grid column); ensure the history column uses the freed vertical space (history `ScrollView` weighted to fill, display bottom/top-aligned as today). Preserve all ids, styles, and order.

**Verification:**

- `Grep` - `calculatorGrid` and all button ids still present in `layout-land/activity_calculator.xml`.
- `Grep` - history `ScrollView` (`calculatorHistoryScroll`) retains a fill/weight attribute.
- Project builds - run `/build` (layout compile).

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Both `layout/` and `layout-land/` edited (no portrait-only change).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Layout now pins buttons to the bottom with history filling the top in both orientations. Final phase handles docs, catalog, changelog.

---

## Rollback Plan

Revert phase commit(s) - layout XML only; no code or data surface changed.
