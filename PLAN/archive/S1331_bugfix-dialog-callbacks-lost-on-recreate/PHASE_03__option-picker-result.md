# Phase 03 - Generic option picker returns an id through FragmentResult

**Strategic spec:** [`../S1331_bugfix-dialog-callbacks-lost-on-recreate.md`](../S1331_bugfix-dialog-callbacks-lost-on-recreate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01, 02
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

`SearchableOptionPickerDialog` returns the picked option id through `setFragmentResult` under a per-call-site
request key, and stops presenting an empty list after restoration. Six call sites across three files migrate to
listeners.

Widest blast radius of the plan: this one dialog serves the streams filter (four pickers), the launcher contact
picker and the launcher weather location picker.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1331 phase 03"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableOptionPickerDialog.kt` | Modified | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsFilterDialogManager.kt` | Modified | ≤ 250 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt` | Modified | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherWeatherLocationDialogFragment.kt` | Modified | ≤ 170 |

No file here exceeds 500 lines, so no backup step. No landscape layout work: this phase changes no XML. The
`launcherEnabled` source set is mounted into the `standard` flavor, so `.\a.ps1 fk` compiles all four files.

---

## Steps

### Step 03.1 - Emit the picked id and refuse to show a restored empty list

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/SearchableOptionPickerDialog.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the `onPicked` field and the `onPicked` parameter of `newInstance`. Add companion constants
> `RESULT_KEY = "option_picker_result"`, `RESULT_OPTION_ID`, and private `ARG_REQUEST_KEY`,
> `ARG_INCLUDE_RESET_ROW`. Add `requestKey: String = RESULT_KEY` to `newInstance` and put both it and
> `includeResetRow` into `arguments` - `includeResetRow` is a Boolean and belongs in the Bundle with the other
> restorable inputs. Extend the existing `onCreate` to read `requestKey` and `includeResetRow` from
> `requireArguments()`. In the `SearchableOptionPickerController.attach` result lambda, replace
> `onPicked?.invoke(picked)` with `setFragmentResult(requestKey, bundleOf(RESULT_OPTION_ID to picked?.id))`,
> keeping the `dismiss()` that follows - a null id is the reset row and hosts must read it as "cleared".
>
> Then add to `onStart`, before the `DialogKeyboardDelegate` call: when `options` is empty, `dismiss()` and
> return. A restored instance has an empty `options` list because the list can hold a non-Parcelable
> `LanguageItem` or `Drawable` and cannot go into a Bundle - the existing class KDoc states this. Without the
> guard a restored picker presents an empty list the user cannot act on. Update that KDoc paragraph to record
> that the callback moved to FragmentResult while the option list stayed transient, and that the dialog now
> closes rather than showing an empty list after restoration.

**Verification:**

- `Grep` - `onPicked` returns zero hits in `SearchableOptionPickerDialog.kt`.
- `Grep` - `setFragmentResult(` matches in `SearchableOptionPickerDialog.kt`.
- `Grep` - `ARG_REQUEST_KEY` matches in `SearchableOptionPickerDialog.kt`.
- `Grep` - `ARG_INCLUDE_RESET_ROW` matches in `SearchableOptionPickerDialog.kt`.
- `Grep` - `options.isEmpty()` matches in `SearchableOptionPickerDialog.kt`.

**Status:** `[x]` done

---

### Step 03.2 - Route the four streams filter pickers through distinct keys

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsFilterDialogManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Give each of the four pickers its own request key constant in a companion object -
> `KEY_CATEGORY`, `KEY_TOPIC`, `KEY_LANGUAGE`, `KEY_COUNTRY` - and pass it to the matching `newInstance` call,
> dropping each `onPicked` lambda. Inside `show(..)`, after the local filter vars are declared, register one
> `activity.supportFragmentManager.setFragmentResultListener(key, activity) { _, bundle -> .. }` per key; each
> body reads `RESULT_OPTION_ID`, assigns the matching local var, calls `renderValues()` and `onApply(..)`
> exactly as the current lambda does. `showCountryPicker` collapses into the country listener - keep its
> behaviour of mapping the picked option to a country code.
>
> Registering inside `show(..)` is deliberate and is the accepted limitation recorded in INDEX: the parent
> filter dialog here is a plain `AlertDialog`, not a `DialogFragment`, so it does not survive recreation and
> the local vars die with it. The FragmentManager holds the pending result until a listener is registered
> again, so a pick made after recreation is applied the next time the filter dialog is opened rather than
> immediately. S1214 accepted the identical limitation for its two plain-`AlertDialog` hosts. Do not rewrite
> the filter dialog into a `DialogFragment` in this ticket.

**Verification:**

- `Grep` - `onPicked =` returns zero hits in `StreamsFilterDialogManager.kt`.
- `Grep` - `setFragmentResultListener(` matches at least four times in `StreamsFilterDialogManager.kt`.
- `Grep` - `KEY_COUNTRY` matches in `StreamsFilterDialogManager.kt`.
- `Grep` - `RESULT_OPTION_ID` matches in `StreamsFilterDialogManager.kt`.

**Status:** `[x]` done

---

### Step 03.3 - Convert the launcher contact picker

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Change the private `showPicker(titleRes, options, tag, onPicked)` helper to take a request key instead of an
> `onPicked` lambda, and pass that key into `newInstance`. Register the listener on
> `activity.supportFragmentManager` with `activity` as the lifecycle owner, in the same helper, before the
> `show(..)` call. Keep the existing `findFragmentByTag(tag) != null` duplicate-open guard as is. Each caller
> of `showPicker` supplies its own key constant so two pickers opened from this manager cannot cross results;
> the caller body that currently matches `picked.id` against `channels` moves into the listener unchanged.

**Verification:**

- `Grep` - `onPicked: (SearchableOptionPickerDialog.Option) -> Unit` returns zero hits in `LauncherContactPickManager.kt`.
- `Grep` - `setFragmentResultListener(` matches in `LauncherContactPickManager.kt`.
- `Grep` - `findFragmentByTag(tag)` still matches in `LauncherContactPickManager.kt`.

**Status:** `[x]` done

---

### Step 03.4 - Convert the launcher weather location picker

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherWeatherLocationDialogFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `showPlaces`, drop the trailing lambda and pass a request key constant to `newInstance`. Register
> `parentFragmentManager.setFragmentResultListener(KEY_PLACE, this) { _, bundle -> .. }` in this fragment's own
> `onCreate`, not in `showPlaces`, so the restored fragment re-registers before the restored picker resumes;
> the body calls the existing `publish(..)` with the returned id. This fragment already publishes its own
> result through `setFragmentResult` with a key read from `arguments`, so follow that same shape for the key
> it now consumes.

**Verification:**

- `Grep` - `setFragmentResultListener(` matches in `LauncherWeatherLocationDialogFragment.kt`.
- `Grep` - `picked?.id?.let(::publish)` returns zero hits in `LauncherWeatherLocationDialogFragment.kt`.
- `Grep` - `fun publish(` still matches in `LauncherWeatherLocationDialogFragment.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` - `onPicked` returns zero hits across `app_v2/src`.
- [x] No `Timber.d("S1331` probe was added by this phase. The six probes were inserted once, at the final `BlockNeedUserTest` transition.
- [x] `Grep` - `Log.d(` returns zero hits in all four touched files.
- [x] Dev log entry added. One entry for the ticket, not one per touched file - CLAUDE.md journaling granularity.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `newInstance` signature changed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Establishes the per-call-site request-key convention for a dialog with several hosts, and the "dismiss rather
than present an empty restored list" guard for any dialog whose payload cannot be bundled.

---

## Rollback Plan

Revert the phase commit. No data migration, no schema change. The streams filter, launcher contact picker and
weather location picker behave as before while their parent stays alive.
