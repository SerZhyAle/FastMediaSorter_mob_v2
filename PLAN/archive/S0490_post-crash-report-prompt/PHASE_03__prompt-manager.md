# Phase 03 - Prompt manager

**Strategic spec:** [`../S0490_post-crash-report-prompt.md`](../S0490_post-crash-report-prompt.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research input:** [`research/01__reuse-and-touchpoints.md`](research/01__reuse-and-touchpoints.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Introduce a delegate manager that, given an Activity, detects a not-yet-offered crash, shows the one-time confirmation prompt, and on consent sends the crash report by reusing the S0483 email + log-zip path. No Activity logic and no UI wiring yet (Phase 04).

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/CrashReportPromptManager.kt` | New | ≤ 130 |

---

## Steps

### Step 03.1 - Create CrashReportPromptManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/CrashReportPromptManager.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class CrashReportPromptManager(private val activity: Activity)`. Public `fun maybeShowPrompt()`:
> 1. `val crashFile = LoggingHelper.getLatestCrashFile() ?: return`.
> 2. Open `activity.getSharedPreferences("crash_report_prompt", Context.MODE_PRIVATE)`; if the stored key `"last_handled_crash"` equals `crashFile.name`, `return` (already offered).
> 3. Write `crashFile.name` to that key immediately (`apply()`) - so a dismissed or backgrounded prompt never re-appears for the same crash.
> 4. Show a `MaterialAlertDialogBuilder(activity)` with title `R.string.crash_prompt_title`, message `R.string.crash_prompt_message`, positive button `R.string.crash_prompt_send` -> `sendReport(crashFile)`, negative button `R.string.cancel` (dismiss). 
>
> Private `fun sendReport(crashFile: File)`: launch on `Dispatchers.IO`; read the crash text with `crashFile.readText()` guarded so a read failure degrades to the intro only; build `body = getString(R.string.crash_report_email_body_intro) + "\n\n" + crashText`; `val zipUri = LogExportHelper.buildLogsZipUri(activity)`; switch to `Dispatchers.Main`; `val intent = SupportIntentFactory.buildCrashReportEmail(getString(R.string.crash_report_email_subject), body, zipUri)`; `val chooser = Intent.createChooser(intent, subject)`; add `FLAG_ACTIVITY_NEW_TASK` if `activity` is not usable as a launching context; wrap `startActivity` in `try/catch (ActivityNotFoundException)` that logs `Timber.w` and shows a Toast `R.string.export_logs_no_share_target` - no empty catch. Use Timber only. Reuse a coroutine scope tied to the activity lifecycle where available, otherwise a local `CoroutineScope(Dispatchers.IO)` consistent with the existing dialog pattern.

**Verification:**

- `Glob` - `CrashReportPromptManager.kt` exists under `ui/main/helpers/`.
- `Grep` - `class CrashReportPromptManager` matches exactly once.
- `Grep` - `fun maybeShowPrompt` present.
- `Grep` - `LoggingHelper.getLatestCrashFile` present.
- `Grep` - `SupportIntentFactory.buildCrashReportEmail` present.
- `Grep` - `LogExportHelper.buildLogsZipUri` present.
- `Grep` - `crash_report_prompt` (SharedPreferences name) present.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification PASS (class + maybeShowPrompt + getLatestCrashFile + buildCrashReportEmail + buildLogsZipUri + prefs name present; no Log.d; `.\a.ps1 fk` BUILD SUCCESSFUL). Dropped dead non-Activity branch; crash-read failure degrades to intro-only. Files: CrashReportPromptManager.kt (New). S0490 debug tag deferred to final-tag insertion.

---

## Phase Done Criteria

- [ ] Step 03.1 is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

`CrashReportPromptManager(activity).maybeShowPrompt()` is the single entry point. Phase 04 constructs it in `MainActivity` and calls it once on fresh launch. The S0490 debug-verification tag will be inserted here (prompt-show decision) as the final code edit before the last build.

---

## Rollback Plan

Delete the new file - nothing references it until Phase 04.
