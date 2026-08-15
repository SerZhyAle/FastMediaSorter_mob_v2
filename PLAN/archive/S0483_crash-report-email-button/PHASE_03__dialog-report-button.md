# Phase 03 - Dialog report button

**Strategic spec:** [`../S0483_crash-report-email-button.md`](../S0483_crash-report-email-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research input:** [`research/01__crash-vs-info-gate.md`](research/01__crash-vs-info-gate.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Render a crash-report action in the error dialog, gated on a new `reportableThrowable` parameter, wired to the Phase 02 email+zip capability; activate it on the principal exception-bearing error sink (the browse error path). The dialog shows the button only when a `Throwable` is supplied - default `null` keeps every other call-site unchanged.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_error_detail.xml` | Modified | ≤ 145 |
| `app_v2/src/main/res/layout-land/dialog_error_detail.xml` | Modified | ≤ 145 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScrollableTextDialog.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt` | Modified | ≤ 190 |

> Landscape parity is mandatory: the portrait layout edit (03.1) and the landscape edit (03.2) must stay identical button-for-button.

---

## Steps

### Step 03.1 - Add the report button to the portrait layout

**Files:** `app_v2/src/main/res/layout/dialog_error_detail.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `layoutDialogActions` row, add a new `com.google.android.material.button.MaterialButton` with `android:id="@+id/btnReport"`, `style="@style/Widget.Material3.Button.IconButton"`, `app:icon="@drawable/ic_send_email"`, `android:visibility="gone"`, placed immediately before `btnCopy`. Do not hardcode any colors - the icon button style supplies theming.

**Verification:**

- `Grep` - `@+id/btnReport` matches exactly once in `res/layout/dialog_error_detail.xml`.
- `Grep` - `@drawable/ic_send_email` present in `res/layout/dialog_error_detail.xml`.
- `Grep` - `="#` returns zero hits in `res/layout/dialog_error_detail.xml` (no hardcoded hex).

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (btnReport + ic_send_email present; no hardcoded hex). Files: layout/dialog_error_detail.xml.

---

### Step 03.2 - Mirror the report button in the landscape layout

**Files:** `app_v2/src/main/res/layout-land/dialog_error_detail.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Apply the exact same `btnReport` addition to the landscape variant so the two layouts stay button-for-button identical (CLAUDE.md Rule 11).

**Verification:**

- `Grep` - `@+id/btnReport` matches exactly once in `res/layout-land/dialog_error_detail.xml`.
- `Grep` - `@drawable/ic_send_email` present in `res/layout-land/dialog_error_detail.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (btnReport + ic_send_email present; identical to portrait). Files: layout-land/dialog_error_detail.xml.

---

### Step 03.3 - Gate and wire the report action in the dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScrollableTextDialog.kt`
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Add a parameter `reportableThrowable: Throwable? = null` to the main `show(..)` overload. Resolve `btnReport` from the inflated view. When `reportableThrowable != null`: set the button visible, set `contentDescription` and `TooltipCompat` tooltip to `R.string.error_dialog_report_to_author`, and on click compose the crash report. Otherwise set it `View.GONE`. Compose: launch on `Dispatchers.IO`, call `LogExportHelper.buildLogsZipUri(context)`, then on `Dispatchers.Main` build the body as `getString(R.string.crash_report_email_body_intro)` + a blank line + app version (`BuildConfig.VERSION_NAME`/`VERSION_CODE`) + the throwable's `javaClass.name` and `message` + a blank line + the dialog's `fullText`, build the intent via `SupportIntentFactory.buildCrashReportEmail(context, getString(R.string.crash_report_email_subject), body, zipUri)`, and launch `Intent.createChooser(..)`; add `FLAG_ACTIVITY_NEW_TASK` when `context !is Activity`. Wrap `startActivity` in a `try/catch (ActivityNotFoundException)` that shows a Toast (no email app) - no empty catch. The report action keeps the dialog open (consistent with Share/Copy/Save dismiss semantics). Leave the existing `show(context, title, throwable)` convenience overload unchanged (it must not enable the button - the gate is opt-in per call-site).

**Verification:**

- `Grep` - `reportableThrowable: Throwable?` matches in `ScrollableTextDialog.kt`.
- `Grep` - `R.id.btnReport` matches in `ScrollableTextDialog.kt`.
- `Grep` - `buildCrashReportEmail` matches in `ScrollableTextDialog.kt`.
- `Grep` - `buildLogsZipUri` matches in `ScrollableTextDialog.kt`.
- `Grep` - `error_dialog_report_to_author` matches in `ScrollableTextDialog.kt`.
- `Grep` - `Log\.d\(` returns zero hits in `ScrollableTextDialog.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (reportableThrowable param + btnReport + buildCrashReportEmail + buildLogsZipUri + string ref present; zero Log.d). Convenience throwable overload left unchanged (opt-in gate). Files: ScrollableTextDialog.kt.

---

### Step 03.4 - Activate the gate on the browse error path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseErrorDisplayManager.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `BrowseErrorDisplayManager.showError`, the early `isNonCriticalNetworkImageError` filter already drops benign network image errors. On both detailed-dialog branches (the `details != null` call and the `details == null` call), pass `reportableThrowable = exception` so the report button appears only when a real exception reached this sink. Do not touch the short/Snackbar branch. Do not forward the throwable from any other call-site in this phase.

**Verification:**

- `Grep` - `reportableThrowable = exception` matches exactly twice in `BrowseErrorDisplayManager.kt`.
- `Grep` - `reportableThrowable` returns zero hits in every other `ScrollableTextDialog.show(` call-site file (button stays opt-in).

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (reportableThrowable = exception x2 in BrowseErrorDisplayManager; symbol confined to dialog + browse manager). Files: BrowseErrorDisplayManager.kt.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Portrait and landscape `dialog_error_detail.xml` declare `btnReport` identically.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The button works end-to-end for browse exceptions: crash → dialog with the email icon → mail client to `serzhyale@gmail.com` with subject, body (intro + version + throwable + error text), and the full log ZIP attached. Other call-sites remain opt-in via `reportableThrowable`. Phase 04 documents the capability and regenerates the catalog.

---

## Rollback Plan

Revert the four edits. The new dialog parameter is additive with a default; reverting the browse-path edit alone hides the button everywhere without breaking compilation.
