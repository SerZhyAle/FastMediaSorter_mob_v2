# S0572 - Crash-report send fails when no email client installed (add share-sheet fallback)

**Status:** Archived

## 0. Capture (raw evidence)

Source: device log `logs/fastmediasorter_20260621_023707.log` (build 2.60.6210.225-NoLegal-DEBUG, Samsung SM-S731B, Android 16 / API 36).

After a previous-session crash the app prompts to send a debug-log zip. User tapped send; nothing was delivered.

Log (verbatim):
```
W/App: CrashReportPromptManager: no email app to send crash report
android.content.ActivityNotFoundException: No Activity found to handle Intent
  { act=android.intent.action.SEND typ=application/zip ...
    sel=act=android.intent.action.SENDTO dat=mailto: } }
  at com.sza.fastmediasorter.ui.main.helpers.CrashReportPromptManager$sendReport$1$1.invokeSuspend(CrashReportPromptManager.kt:67)
```

## 1. Problem

`CrashReportPromptManager.sendReport()` launches an `ACTION_SEND` (application/zip) intent whose **selector** is `Intent(ACTION_SENDTO, "mailto:")`. The selector restricts resolution to email clients only. On a device with no mail app, `startActivity` throws `ActivityNotFoundException`. The catch block logs `Timber.w` and shows a SHORT toast (`R.string.export_logs_no_share_target`, generic "No app available to share log archive"), then exits. The zip is built but never delivered and the user gets no second chance - the developer loses the crash data.

Confirmed call/build sites:
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/CrashReportPromptManager.kt:63-70` - direct `activity.startActivity(intent)` + catch -> Timber.w + Toast.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt:120` - `selector = Intent(ACTION_SENDTO, Uri.parse("mailto:"))`.
- `SupportIntentFactory.kt:102-103` - KDoc: "The selector means the result MUST be launched directly: Intent.createChooser strips the selector." (correct for happy path, but no guard for the no-email-app case).

Related secondary defect (same factory, different manifestation):
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScrollableTextDialog.kt:244-245` - wraps the same `buildCrashReportEmail()` in `Intent.createChooser()`, which **strips** the mailto selector and silently drops the recipient (`EXTRA_EMAIL`) for non-email targets - contradicting the factory KDoc.

## 2. Proposed direction (to refine on approval)

- Keep the direct-launch (mailto selector) path first - preserves pre-filled recipient + subject for users who do have an email client.
- In the `ActivityNotFoundException` catch: strip the selector (`intent.selector = null`, or rebuild without selector) and re-launch via `Intent.createChooser(..)` so any share target (Drive, messengers, file managers) can receive the zip.
- If the chooser launch also throws (kiosk/sandboxed device), show a precise message and note the zip remains in internal storage. Likely needs a dedicated string (e.g. `crash_report_no_share_target`) with EN/RU/UK; current reuse of `export_logs_no_share_target` is misleading in the crash context. Existing `R.string.no_email_client` may also be reused.
- Reconcile `ScrollableTextDialog.kt:244-245` with the chosen contract (either drop createChooser and launch directly, or adopt the same try-direct / fallback-to-chooser pattern). Update `SupportIntentFactory.buildCrashReportEmail()` KDoc to document the supported call patterns.
- No new API gating: `Intent.setSelector` (API 15) and `Intent.createChooser` (API 1) are below all minSdk targets (legacy 23, standard 26).

## 3. Affected files

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/CrashReportPromptManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScrollableTextDialog.kt`
- `app_v2/src/main/res/values/strings*.xml` (+ values-ru, values-uk) if a new string is added

## 4. Acceptance (draft)

- On a device with no email client, tapping send opens the generic share sheet and the zip is deliverable to non-email targets.
- On a device with an email client, behavior is unchanged (recipient + subject pre-filled).
- Fully sandboxed device: precise, crash-report-specific message; zip retained on internal storage.
- `ScrollableTextDialog` crash-report send no longer silently drops the recipient.

## 5. Related

- Crash-report prompt feature: S0490 (per dedup search).
- Affects all flavors (path in `src/main`, no flavor guard).
