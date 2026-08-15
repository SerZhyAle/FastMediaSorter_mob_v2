# Phase 03 - Manual number entry

**Strategic spec:** [`../S0428_home-screen-call-sms.md`](../S0428_home-screen-call-sms.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Offer "Enter a number" beside "Pick from contacts" for the Call and Send-SMS categories, and pin a cell from a typed number.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] Working tree is clean or on a feature branch.
- [x] `CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/dialog_launcher_phone_number.xml` | New | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherPhoneNumberDialogFragment.kt` | New | ≤ 170 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt` | Modified | ≤ 220 |

> **Landscape parity (Rule 11).** `app_v2/src/launcherEnabled/res/layout-land/` exists, but its sibling `dialog_launcher_weather_location.xml` has no landscape variant - the dialog is a `wrap_content` column that reflows. The new dialog follows it: landscape variant deliberately absent, not needed.

---

## Steps

### Step 03.1 - Add the manual-entry strings across three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add five keys, each in one lockstep `scripts/utils/set-android-string.ps1 -Action add` call: `launcher_contact_source_title` ("Where does the number come from?" / «Откуда взять номер?» / «Звідки взяти номер?»), `launcher_contact_source_pick` ("Pick from contacts" / «Выбрать из контактов» / «Вибрати з контактів»), `launcher_contact_source_manual` ("Enter a number" / «Ввести номер» / «Ввести номер»), `launcher_contact_number_hint` ("Phone number" / «Номер телефона» / «Номер телефону») and `launcher_contact_number_invalid` ("That is not a phone number." / «Это не номер телефона.» / «Це не номер телефону.»). Check each against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist.

**Why:**

Strategic §3.3 requires EN/RU/UK for the cell labels, the empty and error states and the picker fallback text, and the fallback path is exactly what this phase adds.

**Verification:**

- `Grep` - all five keys present in all three `strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_contact_"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 03.2 - Add the number-entry dialog layout

**Files:** `app_v2/src/launcherEnabled/res/layout/dialog_launcher_phone_number.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Model the layout on `dialog_launcher_weather_location.xml`: vertical `LinearLayout`, title `TextView`, a `TextInputLayout` + `TextInputEditText` with `android:inputType="phone"` and `android:singleLine="true"`, an error `TextView` starting `gone`, and the action row. The action pair uses the named styles - cancel `Widget.FastMediaSorter.Button.DialogCancel`, confirm `Widget.FastMediaSorter.Button.DialogConfirm` - and no hardcoded colours.

**Why:**

The dialog action-pair standard is mandatory for any confirm/cancel pair in a custom layout (CLAUDE.md §11) and the weather-location dialog is the launcher's existing instance of the same shape.

**Verification:**

- `Glob` - the file exists.
- `Grep` - both `Widget.FastMediaSorter.Button.DialogCancel` and `Widget.FastMediaSorter.Button.DialogConfirm` present.
- `Grep` - `="#` returns zero hits in that file.
- `pwsh -NoProfile -File scripts/quality/assert-dialog-cancel-style.ps1` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Add the number-entry dialog fragment

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherPhoneNumberDialogFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Write a `DialogFragment` mirroring `LauncherWeatherLocationDialogFragment`: view binding, `setFragmentResult` with the typed number under a `RESULT_NUMBER` key, `DialogAccessibilityHelper.applyInitialFocus` and `DialogKeyboardDelegate` in `onStart`, binding nulled in `onDestroyView`. Confirm is refused with `launcher_contact_number_invalid` shown in the error `TextView` when the trimmed input holds no digit. Report the result over `FragmentResult`, never a lambda, so it survives a configuration change.

**Why:**

Strategic §3 requires the number entry to work from a D-pad, a remote, a keyboard and a mouse, which is what the accessibility helper and the keyboard delegate deliver on every other launcher dialog.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class LauncherPhoneNumberDialogFragment` matches exactly once.
- `Grep` - `setFragmentResult` present.
- `Grep` - `DialogAccessibilityHelper` and `DialogKeyboardDelegate` both present.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 03.4 - Ask for the number source before the system picker

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherContactPickManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `start(action)`, for `DIAL` and `SMS` show a two-row `SearchableOptionPickerDialog` - pick from contacts, or enter a number - reusing the existing `showPicker` helper with its own tag and request key; `PROFILE` and `MESSAGE` launch the system picker directly as they do now. The manual branch shows `LauncherPhoneNumberDialogFragment` and turns its result into `LauncherContactTarget(action = action, phoneNumber = number, displayName = number)`, handed to `onTargetPicked`. Keep the display name as the number itself - a typed number has no name to show.

**Why:**

The owner ruled on 2026-08-06 that manual entry is a row in the source choice rather than an error-path fallback, so a number missing from the address book - or a device with no contacts at all - can still be pinned; `PROFILE` and `MESSAGE` are excluded because they address a contact record, not a number (strategic §3.3).

**Verification:**

- `Grep` - `LauncherPhoneNumberDialogFragment` present in the manager.
- `Grep` - `LauncherContactAction.DIAL, LauncherContactAction.SMS ->` present in `start`.
- `Grep` - `Log\.d\(` returns zero hits in every file this phase modified.
- `.\a.ps1 fc` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0, then `.\a.ps1 d` exit 0 for the full debug APK once the S0428 probe tags were in.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for the phase via `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `LauncherPhoneNumberDialogFragment` is new.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.
- [x] `CODE.LOCK` released by `post-change.ps1`.
- [x] UI placement decision recorded before shipping - strategic §3.3 carries the owner's ui-clarify ruling of 2026-08-06 that manual entry is a source row, not an error-path fallback. **Screenshot deferred to `/spec-test-device`**, with the reason recorded rather than skipped: `LauncherHomeActivity` ships `android:enabled="false"` (`src/launcherEnabled/AndroidManifest.xml:23`) and only the user turning launcher mode on enables it, so `am start` cannot reach the screen - the shot needs the launcher-mode walk that `/spec-test-device` drives.

---

## Step Log

- 2026-08-06 - Steps 03.1-03.4 executed. Verification 4/4 PASS. `.\a.ps1 fc` exit 0, `assert-dialog-cancel-style` exit 0, string parity 19/19 across en/ru/uk. `post-change: PASS` (Mixed, scoped).
- 2026-08-06 - `post-change` first FAILED on `ticket-log-audit`: the three `Timber.d("S0428: ..")` probe tags were in place while the journal still said `In Progress`, and the gate allows a ticket id in a log line only for a `BlockNeedUserTest` spec. Order corrected - the status flip runs before the closure, not after. Re-run PASS.
- 2026-08-06 - Phase-boundary audit, Layers 1-3. No P0/P1. `LauncherPhoneNumberDialogFragment` nulls its binding in `onDestroyView` and reports over `FragmentResult`; the manager registers the number listener against the Activity lifecycle, the same ownership the channel picker beside it already uses. One known limitation inherited rather than introduced: the in-flight action lives in a field, so a process death behind the system picker loses it - the existing code already says so and already tells the user, and the pending desktop square dies with it either way.

---

## Handoff Notes to Next Phase

Every strategic goal that changes code is delivered. Phase 04 records the capability and regenerates the derived indexes.

---

## Rollback Plan

Revert the phase commit. Cells pinned from a typed number stay valid - they are ordinary `Contact` targets and Phase 01 executes them.
