# Phase 01 - App-Picker Grid

**Strategic spec:** [`../S1095_launcher-app-picker-layout.md`](../S1095_launcher-app-picker-layout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-07-21
**Completed:** 2026-07-21

**Step Log:**

- 2026-07-21 - 01.1-01.3 grep-verified (attach columns:Int=1 + GridLayoutManager + initial-filter; bg_app_picker_surface drawable; app picker title/opaque bg/taller/adaptive columns). Fix: added missing `import com.sza.fastmediasorter.R`. `.\a.ps1 fc` BUILD SUCCESSFUL. Other 7 pickers untouched (default columns). Audit: backward-compatible, no P0/P1.

---

## Objective

Give only the app picker a taller sheet, an adaptive multi-column grid (>=2 columns in portrait), and an opaque surface background, by adding an optional column count to the shared controller and customizing the app-picker fragment - leaving the seven other pickers untouched.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_picker_title` resolves in EN/RU/UK (already present - no string work).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableOptionPickerController.kt` | Modified | ≤ 240 |
| `app_v2/src/main/res/drawable/bg_app_picker_surface.xml` | New | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/AppPickerDialogFragment.kt` | Modified | ≤ 160 |

> No shared layout edit. `dialog_searchable_option_picker.xml` / `item_searchable_option.xml` stay byte-identical (strategic non-goal). No `res/layout-land` dialog variant exists; column count recomputes on config change in code, so no landscape layout needed.

---

## Steps

### Step 01.1 - Add optional column count + initial filter to the controller

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableOptionPickerController.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `attach`, add a parameter `columns: Int = 1` positioned BEFORE the trailing `onPicked` lambda (so the eight existing `attach(binding, options, selectedId, resetRow) { .. }` calls keep working via the default). Set `binding.recyclerOptions.layoutManager = if (columns > 1) GridLayoutManager(context, columns) else LinearLayoutManager(context)`. `scrollToSelected` already casts to `LinearLayoutManager`, which `GridLayoutManager` extends, so leave it. After `adapter.submit(rows)`, apply the current search text as an initial filter so a restored query survives rotation: read `binding.editOptionSearch.text?.toString().orEmpty()`, and when non-empty call `adapter.filter(it)` and set `binding.tvOptionsEmpty.isVisible = (count == 0)`. Import `androidx.recyclerview.widget.GridLayoutManager`.

**Verification:**

- `Grep` - `columns: Int = 1` present in the `attach` signature.
- `Grep` - `GridLayoutManager(context, columns)` present.
- `Grep` - `editOptionSearch.text` read inside `attach` for the initial filter.
- `Grep` (negative) - no `attach(` caller outside AppPickerDialogFragment passes a `columns` argument.

**Status:** `[x]` done

---

### Step 01.2 - Opaque rounded surface background drawable

**Files:** `app_v2/src/main/res/drawable/bg_app_picker_surface.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `bg_app_picker_surface.xml`: a `<shape android:shape="rectangle">` with `<solid android:color="?attr/colorSurface"/>` and `<corners android:radius="16dp"/>`. Theme-attr solid is valid from API 21 (minSdk is 26). No hardcoded hex (Rule 20).

**Verification:**

- `Glob` - `bg_app_picker_surface.xml` exists.
- `Grep` - `?attr/colorSurface` present; no `="#` hardcoded color.

**Status:** `[x]` done

---

### Step 01.3 - Make the app picker taller, multi-column, titled and opaque

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/AppPickerDialogFragment.kt`
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> In `onViewCreated`, before/around the `attach` call: set the title (`binding.tvOptionPickerTitle.text = getString(R.string.app_picker_title)`, `binding.tvOptionPickerTitle.isVisible = true`); give the root the opaque bg (`binding.root.setBackgroundResource(R.drawable.bg_app_picker_surface)`); make the list taller (`binding.recyclerOptions.updateLayoutParams { height = (resources.displayMetrics.heightPixels * APP_PICKER_HEIGHT_FRACTION).toInt() }` using `androidx.core.view.updateLayoutParams`). Compute an adaptive column count: `val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density; val columns = (widthDp / APP_PICKER_MIN_CELL_DP).toInt().coerceAtLeast(APP_PICKER_MIN_COLUMNS)`; pass `columns = columns` to `SearchableOptionPickerController.attach(...)`. Add companion consts `APP_PICKER_HEIGHT_FRACTION = 0.6f`, `APP_PICKER_MIN_CELL_DP = 160f`, `APP_PICKER_MIN_COLUMNS = 2`. Keep the transparent WINDOW background in `onStart` (the root drawable now supplies the opaque, rounded surface). Do not change the shared layout.

**Verification:**

- `Grep` - `R.string.app_picker_title` set and `tvOptionPickerTitle.isVisible = true` in the fragment.
- `Grep` - `setBackgroundResource(R.drawable.bg_app_picker_surface)` present.
- `Grep` - `columns = columns` passed to `attach`; `coerceAtLeast(APP_PICKER_MIN_COLUMNS)` present.
- `Grep` - `APP_PICKER_HEIGHT_FRACTION` used for `recyclerOptions` height.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (controller signature changed).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings; confirm the seven other pickers still call `attach` with the default column count (grep).

---

## Handoff Notes to Next Phase

Only the app picker changed shape; the shared controller stays default-single-column for everyone else. Phase 02 records + regenerates.

---

## Rollback Plan

Revert the phase commit(s) - one optional param, one new drawable, one fragment customization; no persistence or schema impact.
