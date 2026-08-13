# Phase 02 - Filter dialog carries its inputs and its result in Bundles

**Strategic spec:** [`../S1331_bugfix-dialog-callbacks-lost-on-recreate.md`](../S1331_bugfix-dialog-callbacks-lost-on-recreate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

`FilterResourceDialog` reads its four starting values from `arguments` and returns the applied filter through
`setFragmentResult`, so a filter configured after the main screen is recreated is both preserved on screen and
delivered to the ViewModel.

This dialog is the only one in the plan that loses its **inputs** as well as its result: `newInstance` assigns
`currentSortMode`, `selectedResourceTypes`, `selectedMediaTypes` and `nameFilter` straight onto instance fields.
A restored instance therefore shows an empty, default-sorted filter form even before the dead callback matters.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.
- [x] S1272 is not mid-edit on the main screen - it touches the filter warning strip on the same screen.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1331 phase 02"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1420 |

`MainActivity.kt` is 1400 lines against a 1500-line ceiling - take a timestamped backup into `temp/S1331/`
before editing (Rule 5) and keep the net delta near zero by deleting the inline `onApply` lambda as the
listener is added. No landscape layout work: this phase changes no XML.

---

## Steps

### Step 02.1 - Back up the oversized host file

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `MainActivity.kt` to `temp/S1331/MainActivity.<yyyyMMdd-HHmmss>.kt.bak` before any edit, per Rule 5 for
> files over 500 lines.

**Verification:**

- `Glob` - `temp/S1331/MainActivity.*.kt.bak` matches at least one file.

**Status:** `[x]` done

---

### Step 02.2 - Move the dialog's inputs and result into Bundles

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/FilterResourceDialog.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Delete the `onApplyListener` field. Add companion constants: `RESULT_KEY = "filter_resource_result"`,
> `RESULT_SORT_MODE`, `RESULT_RESOURCE_TYPES`, `RESULT_MEDIA_TYPES`, `RESULT_NAME_FILTER`, plus private
> `ARG_SORT_MODE`, `ARG_RESOURCE_TYPES`, `ARG_MEDIA_TYPES`, `ARG_NAME_FILTER`, `ARG_REQUEST_KEY`. Keep the
> `newInstance` parameter list but drop the `onApply` parameter, add `requestKey: String = RESULT_KEY`, and
> write every value into `arguments` instead of onto fields. `SortMode`, `ResourceType` and `MediaType` are
> enums - store `SortMode.name` as a String and each set as an `ArrayList<String>` of names via
> `putStringArrayList`. Add an `onCreate` override that rebuilds `currentSortMode`, `selectedResourceTypes`,
> `selectedMediaTypes`, `nameFilter` and `requestKey` from `requireArguments()`, mapping names back with
> `enumValueOf`. In `applyFilters()` replace the listener call with `setFragmentResult(requestKey, ..)`
> carrying the same four values in the same null-when-empty shape the current lambda receives, then keep the
> existing `dismiss()`.
>
> Do not restate what each constant holds in a comment - only the enum-name round trip is worth a WHY line.

**Verification:**

- `Grep` - `onApplyListener` returns zero hits in `FilterResourceDialog.kt`.
- `Grep` - `setFragmentResult(` matches in `FilterResourceDialog.kt`.
- `Grep` - `ARG_REQUEST_KEY` matches in `FilterResourceDialog.kt`.
- `Grep` - `override fun onCreate(savedInstanceState: Bundle?)` matches in `FilterResourceDialog.kt`.
- `Grep` - `putStringArrayList` matches in `FilterResourceDialog.kt`.

**Status:** `[x]` done

---

### Step 02.3 - Receive the applied filter in MainActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `MainActivity.onCreate`, register
> `supportFragmentManager.setFragmentResultListener(FilterResourceDialog.RESULT_KEY, this) { _, bundle -> .. }`.
> The lambda rebuilds the enums from the bundled names and calls the same four ViewModel setters the current
> inline lambda calls: `setSortMode`, `setFilterByType`, `setFilterByMediaType`, `setFilterByName`. Then reduce
> the `btnFilter` click handler to building `FilterResourceDialog.newInstance(..)` from
> `viewModel.state.value` and showing it, with the `onApply` argument removed. Keep the handler on
> `setOnClickListenerDebounced`.

**Verification:**

- `Grep` - `setFragmentResultListener(` matches in `MainActivity.kt`.
- `Grep` - `onApply =` returns zero hits in `MainActivity.kt`.
- `Grep` - `FilterResourceDialog.RESULT_KEY` matches in `MainActivity.kt`.
- `Grep` - `setFilterByMediaType` matches in `MainActivity.kt`.
- File length of `MainActivity.kt` stays below 1500 lines.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] No `Timber.d("S1331` probe was added by this phase. The six probes were inserted once, at the final `BlockNeedUserTest` transition.
- [x] `Grep` - `Log.d(` returns zero hits in both touched files.
- [x] Dev log entry added. One entry for the ticket, not one per touched file - CLAUDE.md journaling granularity.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `newInstance` signature changed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Shows the enum-set round trip through a Bundle, which no other phase repeats: the remaining payloads are a
plain Int, an option id String and a Boolean.

---

## Rollback Plan

Revert the phase commit. No data migration, no schema change; the filter's on-screen behaviour before
recreation is unchanged.
