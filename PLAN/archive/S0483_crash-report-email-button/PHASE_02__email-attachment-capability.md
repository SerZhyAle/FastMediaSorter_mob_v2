# Phase 02 - Email + attachment capability

**Strategic spec:** [`../S0483_crash-report-email-button.md`](../S0483_crash-report-email-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research input:** [`research/02__email-attachment-delivery.md`](research/02__email-attachment-delivery.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Introduce the data/util layer for crash-report delivery: a log-zip URI builder (reusing the existing zip + FileProvider pipeline) and an `ACTION_SEND` email intent builder addressed to the author with an optional attachment. No UI or call-site changes.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LogExportHelper.kt` | Modified | ≤ 170 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactoryTest.kt` | Modified | ≤ 200 |

---

## Steps

### Step 02.1 - Crash-report email intent builder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactory.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `SupportIntentFactory`, add a private constant `CRASH_REPORT_EMAIL = "serzhyale@gmail.com"` (a bare address, not a `mailto:` URI - `ACTION_SEND` uses `EXTRA_EMAIL`). This is a distinct channel from the existing `SUPPORT_MAILTO` (`sza@ukr.net`); keep both. Add `fun buildCrashReportEmail(context: Context, subject: String, body: String, attachmentUri: Uri?): Intent` returning an `Intent(Intent.ACTION_SEND)` with `EXTRA_EMAIL = arrayOf(CRASH_REPORT_EMAIL)`, `EXTRA_SUBJECT = subject`, `EXTRA_TEXT = body`, and - when `attachmentUri != null` - `type = "application/zip"`, `putExtra(EXTRA_STREAM, attachmentUri)`, `addFlags(FLAG_GRANT_READ_URI_PERMISSION)`; when null, `type = "text/plain"`. Do not call `startActivity` here - return the intent.

**Verification:**

- `Grep` - `fun buildCrashReportEmail` matches exactly once in `SupportIntentFactory.kt`.
- `Grep` - `serzhyale@gmail.com` matches exactly once in `SupportIntentFactory.kt`.
- `Grep` - `Intent.EXTRA_EMAIL` present in `SupportIntentFactory.kt`.
- `Grep` - `Intent.EXTRA_STREAM` present in `SupportIntentFactory.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (buildCrashReportEmail + CRASH_REPORT_EMAIL + EXTRA_EMAIL + EXTRA_STREAM present). Dropped unused `context` param (lint). Files: SupportIntentFactory.kt.

---

### Step 02.2 - Log-zip URI builder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LogExportHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `LogExportHelper`, add `fun buildLogsZipUri(context: Context): Uri?` that packages all log files into the cache ZIP and returns a shareable `content://` URI - reuse the existing zip-building logic (`LoggingHelper.getLogFiles()`, `ZIP_FILE_NAME`, `StrictModeHelper.allowDiskIO`) and `FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", zipFile)`. Return `null` when there are no logs or on failure (log the failure with `Timber.e`). Refactor `exportLogs()` to call `buildLogsZipUri` so the zip-building code is not duplicated. This method performs disk I/O - callers must invoke it off the main thread.

**Verification:**

- `Grep` - `fun buildLogsZipUri` matches exactly once in `LogExportHelper.kt`.
- `Grep` - `FileProvider.getUriForFile` present in `LogExportHelper.kt`.
- `Grep` - `Log\.d\(` returns zero hits in `LogExportHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (buildLogsZipUri present; FileProvider reused; zero Log.d). exportLogs refactored onto shared buildLogsZip (no dup). Files: LogExportHelper.kt.

---

### Step 02.3 - Unit test for the crash-report intent

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/support/SupportIntentFactoryTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend `SupportIntentFactoryTest` with a test verifying `buildCrashReportEmail` produces `Intent.ACTION_SEND`, `EXTRA_EMAIL` containing `serzhyale@gmail.com`, the passed subject and body, and - when an attachment URI is supplied - `EXTRA_STREAM` set plus `FLAG_GRANT_READ_URI_PERMISSION`. Add a second case asserting that a null attachment yields `type = "text/plain"` and no `EXTRA_STREAM`. Follow the existing Robolectric/JUnit style already in the file.

**Verification:**

- `Grep` - `buildCrashReportEmail` matches at least once in `SupportIntentFactoryTest.kt`.
- `Grep` - `serzhyale@gmail.com` present in `SupportIntentFactoryTest.kt`.
- Run `pwsh -NoProfile -File a.ps1 -- fu` (or `gradlew testStandardDebugUnitTest --tests "*SupportIntentFactoryTest"`); the new test class passes (inspect the per-class XML report, ignore unrelated pre-existing failures).

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (buildCrashReportEmail/recipient in test; `.\a.ps1 fu` BUILD SUCCESSFUL 2m12s - compiles + all unit tests pass). Files: SupportIntentFactoryTest.kt.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of `SupportIntentFactory` and `LogExportHelper` changed) - deferred to Phase 04, noted here.

---

## Handoff Notes to Next Phase

`SupportIntentFactory.buildCrashReportEmail(context, subject, body, attachmentUri)` and `LogExportHelper.buildLogsZipUri(context): Uri?` are available for Phase 03. The dialog orchestrates: build the zip URI off the main thread, then build the intent, then launch a chooser.

---

## Rollback Plan

Revert both source edits and the test edit - no caller references the new methods until Phase 03.
