# Phase 04 - Color picker returns the chosen color through FragmentResult

**Strategic spec:** [`../S1331_bugfix-dialog-callbacks-lost-on-recreate.md`](../S1331_bugfix-dialog-callbacks-lost-on-recreate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01-03
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

`ColorPickerDialog` returns the chosen color through `setFragmentResult`, and the destinations settings screen
receives it through a listener registered on the fragment, so a color confirmed after the settings screen is
recreated is written to the destination.

Smallest payload in the plan - a single Int - and the conversion closest to the S1214 reference. The dialog also
currently loses its in-progress selection, because `selectedColor` is a plain field seeded from `arguments`
only on first creation.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1331 phase 04"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ColorPickerDialog.kt` | Modified | ≤ 210 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsDestinationsManager.kt` | Modified | ≤ 250 |

No file here exceeds 500 lines, so no backup step. No landscape layout work: this phase changes no XML.

---

## Steps

### Step 04.1 - Emit the confirmed color as a FragmentResult

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ColorPickerDialog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the `onColorSelected` field and the `onColorSelected` parameter of `newInstance`. Add companion
> constants `TAG = "ColorPickerDialog"`, `RESULT_KEY = "color_picker_result"`, `RESULT_COLOR`,
> `RESULT_SUBJECT_ID`, and private `ARG_REQUEST_KEY`, `ARG_SUBJECT_ID`; keep the existing private
> `ARG_INITIAL_COLOR`. Add `requestKey: String = RESULT_KEY` and `subjectId: String` to `newInstance` and put
> both into `arguments`. `subjectId` identifies which row the color is for, so one host listener can serve
> every row - the dialog only stores it and echoes it back. Move the `arguments` reads out of `onCreateDialog`
> into a new `onCreate` override that sets `initialColor`, `selectedColor`, `requestKey` and `subjectId` -
> `onCreateDialog` should only inflate and wire views. In `confirmSelection()` replace
> `onColorSelected?.invoke(selectedColor)` with
> `setFragmentResult(requestKey, bundleOf(RESULT_COLOR to selectedColor, RESULT_SUBJECT_ID to subjectId))`,
> keeping the `dismiss()` that follows.
>
> The class KDoc is currently a one-line restatement of the class name. Replace it with a note naming S1331 and
> stating why the request key is read from `arguments`, matching the `SearchableLanguagePickerDialog` header.

**Verification:**

- `Grep` - `onColorSelected` returns zero hits in `ColorPickerDialog.kt`.
- `Grep` - `setFragmentResult(` matches in `ColorPickerDialog.kt`.
- `Grep` - `ARG_REQUEST_KEY` matches in `ColorPickerDialog.kt`.
- `Grep` - `override fun onCreate(savedInstanceState: Bundle?)` matches in `ColorPickerDialog.kt`.
- `Grep` - `const val TAG` matches in `ColorPickerDialog.kt`.
- `Grep` - `RESULT_SUBJECT_ID` matches in `ColorPickerDialog.kt`.

**Status:** `[x]` done

---

### Step 04.2 - Receive the color on the destinations settings screen

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsDestinationsManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> This manager holds a `fragment` reference and shows the picker on `fragment.parentFragmentManager`. Give it
> an `init`-time or explicitly-called registration that runs from the fragment's own `onViewCreated` path -
> follow whichever entry point the manager already uses for one-time wiring rather than inventing a new one -
> and register
> `fragment.parentFragmentManager.setFragmentResultListener(ColorPickerDialog.RESULT_KEY, fragment.viewLifecycleOwner) { _, bundle -> .. }`.
> The body reads `RESULT_COLOR` and calls `viewModel.updateDestinationColor(resource, color)`.
>
> The resource the color applies to is the one thing the bundle cannot carry back on its own: `showColorPicker`
> currently closes over `resource`. Give `ColorPickerDialog.newInstance` an extra `subjectId: String` argument
> stored in `arguments` and echoed back in the result bundle under `RESULT_SUBJECT_ID`; the listener resolves
> the resource from that id. One listener then serves every destination row, and the row identity survives
> recreation with the rest of the arguments. Then reduce `showColorPicker` to building and showing the dialog
> with `ColorPickerDialog.TAG`.

**Verification:**

- `Grep` - `onColorSelected` returns zero hits across `app_v2/src`.
- `Grep` - `setFragmentResultListener(` matches in `OperationsDestinationsManager.kt`.
- `Grep` - `ColorPickerDialog.RESULT_KEY` matches in `OperationsDestinationsManager.kt`.
- `Grep` - `RESULT_SUBJECT_ID` matches in `OperationsDestinationsManager.kt`.
- `Grep` - `updateDestinationColor(` matches in `OperationsDestinationsManager.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] No `Timber.d("S1331` probe was added by this phase. The six probes were inserted once, at the final `BlockNeedUserTest` transition.
- [x] `Grep` - `Log.d(` returns zero hits in both touched files.
- [x] Dev log entry added. One entry for the ticket, not one per touched file - CLAUDE.md journaling granularity.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `newInstance` signature changed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Shows the echo-the-subject pattern: when a host opens one dialog for many rows, the row identity rides in the
arguments and comes back in the result bundle, so a single listener serves every row.

---

## Rollback Plan

Revert the phase commit. No data migration, no schema change, no user-facing surface changed.
