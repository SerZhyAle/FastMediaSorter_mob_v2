# Phase 02 — List-Screen Focus Polish

**Strategic spec:** [`../S0230_tv-keyboard-navigation-coverage.md`](../S0230_tv-keyboard-navigation-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Apply the §6.1 best-practice (`descendantFocusability="afterDescendants"` + explicit `getInitialFocusView()` returning the list root) to every RecyclerView-based Activity / Fragment listed in `COVERAGE_MATRIX.md` Phase 02 work list. No new classes; minor `BaseActivity` subclass overrides + XML attribute additions only.

---

## Prerequisites

- [ ] Phase 01 ✅ Done; `COVERAGE_MATRIX.md` Phase 02 work list populated.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_browse.xml` (and `layout-land/`) | Modified | ≤ +1 line per RecyclerView |
| `app_v2/src/main/res/layout/activity_duplicates.xml` (and `layout-land/`) | Modified | ≤ +1 line per RecyclerView |
| `app_v2/src/main/res/layout/fragment_cloud_folder_picker.xml` (and `layout-land/`) | Modified | ≤ +1 line per RecyclerView |
| `app_v2/src/main/res/layout/activity_add_resource.xml` (and `layout-land/`) | Modified | ≤ +1 line per RecyclerView |
| `app_v2/src/main/res/layout/activity_settings.xml` (and `layout-land/`) | Modified | ≤ +1 line per RecyclerView |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ +6 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/duplicates/DuplicatesActivity.kt` | Modified | ≤ +6 lines |
| (more entries derived from Phase 01 work list) | Modified | per item |

> Exact list of files comes from `COVERAGE_MATRIX.md` Phase 02 work list. The names above are the expected coverage based on `dev/CATALOG/app_v2.md`; confirm before editing.

---

## Steps

### Step 02.1 — Audit list layouts

**Files:** `PLAN/S0230_tv-keyboard-navigation-coverage/COVERAGE_MATRIX.md`
**Depends on:** — start of phase

**Prompt for developer:**

> From the Phase 02 work list in `COVERAGE_MATRIX.md`, build the concrete file list (layout XML + matching Activity / Fragment .kt). For every entry, run `Grep -n 'androidx.recyclerview.widget.RecyclerView' <layout.xml>` and confirm the file has a primary RecyclerView. Confirm presence of a `layout-land/` counterpart for each — if missing and the screen supports landscape, note in a sub-bullet ("landscape variant absent — out of scope of this phase").

**Verification:**

- `Grep` — every layout XML in the work list contains `<androidx.recyclerview.widget.RecyclerView`.
- `Grep` — for each `res/layout/<file>.xml` in the work list, `res/layout-land/<file>.xml` either exists OR is annotated as absent in `COVERAGE_MATRIX.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

### Step 02.2 — Apply `descendantFocusability` to layouts

**Files:** `app_v2/src/main/res/layout/<file>.xml`, `app_v2/src/main/res/layout-land/<file>.xml` (where present)
**Depends on:** Step 02.1

**Prompt for developer:**

> For every primary RecyclerView in the work list, add `android:descendantFocusability="afterDescendants"`. If the attribute already exists with a different value — leave it and note the override decision in a step comment. If a `layout-land/` counterpart exists, apply the same attribute there in the same edit (mandatory landscape parity — CLAUDE.md rule 12).

**Verification:**

- `Grep -c 'descendantFocusability="afterDescendants"'` across `res/layout/` and `res/layout-land/` — count equals number of work-list entries × (1 + landscape variants present).
- `Grep` — `descendantFocusability="blocksDescendants"` does NOT appear on any work-list RecyclerView.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

### Step 02.3 — Add `getInitialFocusView()` overrides

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/<feature>/<Activity>.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> For every Activity in the work list, add `override fun getInitialFocusView(): View = binding.<recyclerViewId>`. Place the override next to other lifecycle methods (after `setupViews()` ideally). Import `android.view.View` if not already imported. Do NOT add this override to Fragments — Fragments do not extend `BaseActivity`; their host Activity owns the initial focus.

**Verification:**

- `Grep -c 'override fun getInitialFocusView'` in `ui/**.kt` — matches Activity-row count from the work list.
- For each modified Activity: `Grep -n 'getInitialFocusView'` returns the expected binding accessor.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

### Step 02.4 — Build gate

**Files:** —
**Depends on:** Step 02.3

**Prompt for developer:**

> Run `/build` → `standard debug`. If FAIL: read the error, the most likely cause is a missing `import android.view.View` in one of the modified Activities, or an unresolved binding accessor (the layout uses a different id than expected). Fix the minimal error and retry.

**Verification:**

- `/build` standard debug returns BUILD SUCCESSFUL.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug PASS.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry per modified file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After this phase, every list-based Activity reports a deterministic initial focus on TV (handled by the existing `BaseActivity` initial-focus dispatch). RecyclerViews now claim focus immediately when their children populate, eliminating the "first DPAD press wasted" edge case described in §6.1.

---

## Rollback Plan

Revert phase commit(s) — no data migration, no API change, no user-visible string. Layout-XML attribute removal + Kotlin override removal are reversible by deleting the added lines.
