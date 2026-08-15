# Research 01 - Reuse map + touchpoints

Spec: S0490. §6 items 1-2. Source: codebase audit (read-only) 2026-06-17.

## Reused from S0483 (already in code)

- `SupportIntentFactory.buildCrashReportEmail(subject, body, attachmentUri)` - ACTION_SEND email to
  `serzhyale@gmail.com` with optional attachment.
- `LogExportHelper.buildLogsZipUri(context): Uri?` - packages all logs (incl. crash files) into the
  cache ZIP and returns a FileProvider URI; off-main-thread.
- Strings `crash_report_email_subject`, `crash_report_email_body_intro` (EN/RU/UK).

## Crash detection (existing)

- `LoggingHelper.installCrashHandler()` writes `fastmediasorter_crash_<ts>.log` on the uncaught
  handler, then delegates to the prior handler (app dies).
- `LoggingHelper.hasPreviousCrash()` - boolean (crash files exist). Currently only used in
  `FastMediaSorterApp.onCreate()` to emit a Timber.w.
- `LoggingHelper.getLogFiles()` returns all `fastmediasorter_*.log` sorted by lastModified DESC.
  So the latest crash file = `getLogFiles().firstOrNull { it.name.startsWith("fastmediasorter_crash_") }`.
  A small public `getLatestCrashFile(): File?` belongs in `LoggingHelper`.
- A crash file already contains a self-describing block (CRASH REPORT header, App version, thread,
  throwable class/message, stack trace) - suitable to drop into the email body verbatim.

## §6 item 1 - "already prompted" watermark (resolved direction)

- Use a dedicated `SharedPreferences` (e.g. file `crash_report_prompt`) with one key holding the last
  handled crash file name. No Room change. Show the prompt only when the latest crash file name differs
  from the stored value; write the watermark immediately on showing (before send) so a dismissed or
  backgrounded prompt never re-appears for the same crash.

## §6 item 2 - where to show (resolved)

- `MainActivity` is `@AndroidEntryPoint`, extends `BaseActivity<ActivityMainBinding>`, has `onCreate`
  (super calls setContentView) and a custom `onResumeWithViews()` hook, and already constructs several
  feature managers. Show the prompt from a delegate manager called in `onCreate` guarded by
  `savedInstanceState == null` (fresh launch only, not rotation). Not from `Application` (no Activity
  context there).

## Reusable button string

- Negative button reuse `R.string.cancel` ("Cancel"). New strings needed: prompt title, prompt message,
  positive "Send report" label.

## Touchpoints a tactical implementation will hit

1. `LoggingHelper.kt` - add `getLatestCrashFile(): File?`.
2. New `ui/main/helpers/CrashReportPromptManager.kt` - detect + watermark + dialog + send orchestration
   (reuses SupportIntentFactory + LogExportHelper + the crash file text).
3. `ui/main/MainActivity.kt` - construct the manager and call it once in `onCreate`.
4. `res/values*/strings.xml` - 3 new trilingual strings.
5. FEATURES + catalog + functionality log.
