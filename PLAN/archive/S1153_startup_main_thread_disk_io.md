# S1153 - Startup main-thread disk I/O (StrictMode)

**Status:** Archived

<!-- auto-approved by /spec-all - 2026-07-23 -->

## 0. Raw capture

Surfaced by `/spec-prerelease` sweep 2026-07-22 (run `temp/S0484/run_20260722_151823.log`), step 4.1 deep log audit + manual verification. Not release-blocking (debug-only StrictMode; cold-start measured 1456 ms, under the 5000 ms budget), parked per CLAUDE.md 3.1.

Symptom: 718 StrictMode policy violations during the run, all main-thread disk I/O:
- 689 `DiskRead`
- 29 `DiskWrite`

Evidence (main-thread reads during `MainActivity.onCreate`, from the run log stack frames):
- `com.sza.fastmediasorter.core.db.DatabaseResetNotice.prefs(DatabaseResetNotice.kt:26)` <- `showIfPending` <- `MainActivity.onCreate(MainActivity.kt:292)` (SharedPreferences read)
- `com.sza.fastmediasorter.core.util.LocaleHelper.consumeReturnToSettings(LocaleHelper.kt:272)` <- `MainActivity.onCreate(MainActivity.kt:338)`
- `com.sza.fastmediasorter.core.logging.LoggingHelper$FileLoggingTree.getLogFiles(LoggingHelper.kt:505)` <- `getLatestCrashFile(LoggingHelper.kt:76)` <- `CrashReportPromptManager.maybeShowPrompt(CrashReportPromptManager.kt:26)` <- `MainActivity.onCreate(MainActivity.kt:346)` (file listing on main thread)

Impact:
- Debug-only visibility (StrictMode is not active in release), so no user-facing symptom today.
- Still a real startup main-thread I/O pattern (audit-protocol P2: over-eager startup / main-thread disk read). Worth moving the prefs/file reads off the main thread or deferring them past first frame.

## 1. Goal

Remove main-thread disk I/O from the startup path so StrictMode is clean and first-frame is not blocked by prefs/file reads.

## 2. Current behaviour (AS-IS)

`MainActivity.onCreate` performs three disk reads on the main thread, each gating user-visible behaviour:

- **DB-reset notice** (`DatabaseResetNotice.showIfPending`, onCreate) reads `database_reset_notice` SharedPreferences and, if a destructive reset happened, shows an explanatory `AlertDialog`. Does not gate onCreate control flow.
- **Return-to-settings** (`LocaleHelper.consumeReturnToSettings`, onCreate) reads `app_restart_state` SharedPreferences and, when true, immediately `startActivity(SettingsActivity)` + `finish()` + `return` from onCreate. Gates onCreate control flow.
- **Crash-report prompt** (`CrashReportPromptManager.maybeShowPrompt`, onCreate) lists the log directory (`LoggingHelper.getLatestCrashFile` -> `getLogFiles`) and reads `crash_report_prompt` SharedPreferences, then shows a `MaterialAlertDialog` offering to email the crash report. Does not gate onCreate control flow.

All three run synchronously on the main thread inside `onCreate`, tripping StrictMode `DiskRead`/`DiskWrite`.

## 3. Target behaviour (TO-BE)

- The two deferrable notices (DB-reset, crash-prompt) read from disk on `Dispatchers.IO` and show their dialogs on Main only while the Activity is still alive, past first frame. Same observable outcome (dialog still shown) with no main-thread I/O and no first-frame block.
- The return-to-settings read stays synchronous because it gates an immediate navigate/finish branch in onCreate; deferring it would let MainActivity build its full UI and then navigate away (flicker / wrong control flow). It is treated as an accepted narrow StrictMode exception, suppressed at the read via `StrictModeHelper.allowDiskIO` (already wrapped in `allowDiskWrites`; only the read side leaks).
- No new user-visible strings, no new capability. Pure perf/correctness change.

### 3.1 Behaviour-preservation constraints

- DB-reset dialog: still shown after a destructive DB reset, with the same reason + backup-path text. Reading is one-shot and clears the pending flag exactly once (consume semantics preserved).
- Crash prompt: still shown once per crash; the "handled" watermark is still written before the dialog is shown so a dismissed/backgrounded prompt never re-offers the same crash. Watermark write moves onto IO with the read.
- Return-to-settings: unchanged behaviour and timing; only the StrictMode read suppression widens.
- Redirect edge (Welcome / return-to-settings early-return): the deferred notices are scheduled only after those early returns, and are guarded by `isFinishing` + a lifecycle-aware scope, so a finishing MainActivity never shows a dialog it would immediately dismiss. This is strictly better than the prior order (which called `showIfPending` before the redirects, flashing then dismissing the dialog) and is intentional.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0731 (DB-reset notice), S0490 (crash-report prompt), S0207 (startup perf checkpoints)
- **UI/UX impact:** none - no new strings, no layout, no new dialogs; only defers when the same existing dialogs appear (past first frame instead of during onCreate).
- **Flavor impact:** none - `src/main` only, no flavor gating; StrictMode is DEBUG-only across all flavors.
- **Data impact:** none - same SharedPreferences keys, same consume-once semantics, no schema change.

## 4. Design

New helper `ui/main/helpers/StartupNoticeManager.kt` (NounVerbManager, no Activity business logic) owns the deferral of both dialog notices:

- Constructor takes `AppCompatActivity` (MainActivity IS-A AppCompatActivity via BaseActivity).
- `presentDeferredNotices(showCrashPrompt: Boolean)` launches once on `activity.lifecycleScope` (Main), hops to `Dispatchers.IO` for each read, returns to Main to show. `lifecycleScope` cancels on DESTROY (no leak); one-shot suspend, not a Flow collection (Rule 19 compliant).
- Delegates the crash prompt to the existing `CrashReportPromptManager`, split into an IO `findPendingCrash()` and a Main `showPrompt(file)`.

`DatabaseResetNotice` (core/db object) split:
- `consumePending(context): PendingReset?` - IO-safe read + clear; returns payload or null.
- `showNotice(activity, notice)` - Main-thread dialog render.
- `showIfPending` removed (single caller replaced; dead-weight hygiene Rule 20).

`CrashReportPromptManager` split:
- `findPendingCrash(): File?` - IO-safe: list log dir, check + write watermark, return file or null.
- `showPrompt(crashFile: File)` - Main-thread `MaterialAlertDialog`.
- `maybeShowPrompt` removed (single caller replaced).

`LocaleHelper.consumeReturnToSettings`: `StrictModeHelper.allowDiskWrites { .. }` -> `allowDiskIO { .. }` (suppress the read too). KDoc notes the accepted synchronous exception.

`MainActivity.onCreate`:
- Remove the inline `DatabaseResetNotice.showIfPending(this)` call.
- Replace the inline `CrashReportPromptManager(this).maybeShowPrompt()` block with a single `StartupNoticeManager(this).presentDeferredNotices(showCrashPrompt = savedInstanceState == null)`, placed after all early-return redirects so notices are scheduled only when MainActivity actually continues.

## 5. Phases

### Phase 01 - Split DatabaseResetNotice read/show
- [x] Add `data class PendingReset(reason, backupPath)`; add `consumePending(context): PendingReset?` (IO-safe read+clear) and `showNotice(activity, notice)` (Main dialog); remove `showIfPending`.
- Verification: `grep -n "consumePending\|showNotice\|showIfPending" DatabaseResetNotice.kt` shows the two new fns and no `showIfPending`. PASS.

### Phase 02 - Split CrashReportPromptManager find/show
- [x] Add `findPendingCrash(): File?` (IO-safe list+watermark) and `showPrompt(crashFile)` (Main dialog); remove `maybeShowPrompt`. Keep `sendReport` private.
- Verification: `grep -n "findPendingCrash\|showPrompt\|maybeShowPrompt" CrashReportPromptManager.kt` shows the two new fns and no `maybeShowPrompt`. PASS.

### Phase 03 - Add StartupNoticeManager + rewire onCreate
- [x] Create `StartupNoticeManager.kt` orchestrating IO read -> Main show for both notices on `lifecycleScope`.
- [x] In `MainActivity.onCreate`: remove `showIfPending`; replace crash-prompt block with `StartupNoticeManager(this).presentDeferredNotices(savedInstanceState == null)` after the early returns.
- Verification: `grep` MainActivity has no `showIfPending`/`maybeShowPrompt`; has one `presentDeferredNotices` call. PASS.

### Phase 04 - Suppress return-to-settings read + build
- [x] `LocaleHelper.consumeReturnToSettings`: `allowDiskWrites` -> `allowDiskIO`; KDoc note.
- [x] Insert `Timber.d("S1153: ..")` probe tags at the changed startup-flow entries (deferred-notices entry, each dialog shown) as the final code edits.
- [x] Build `standard debug` (`a.ps1 dq`) - validates code + tags in one pass.
- Verification: `BUILD SUCCESSFUL in 2m 26s` (log `temp/build_debug_20260723_013537.log`). PASS.

### Phase 05 - Residual startup reads found on-device (Hilt-init + pre-inflation)

The first on-device sweep (Partial audit below) proved the three onCreate reads gone but StrictMode still logged startup main-thread reads from other sites: Hilt-singleton constructors built during Activity field injection, one pre-inflation theme mirror read, and an Application-scope crash check. Each addressed by the pattern that fits the site (defer vs accepted-exception), no new Hilt scope invented.

- [x] `UnifiedFileCache`: `cacheDir` -> `by lazy`, remove eager `init { mkdirs }`. Ctor no longer touches `context.cacheDir` or disk; every write path already ensures the dir exists.
- [x] `AuthSessionRepositoryImpl`: inject `@ApplicationScope CoroutineScope`; move `migrateIfNeeded` + initial `refreshFlows` from the ctor `init` into `appScope.launch { }` (IO). StateFlows populate reactively; migration is idempotent + `@Volatile`-guarded so the tiny pre-completion window is safe. Test updated to pass an eager `Unconfined` scope so init stays synchronous under `runTest`.
- [x] `InputHelpFirstRunHint.showIfNeeded`: read/write the prefs on `Dispatchers.IO` via `activity.lifecycleScope`, show the Snackbar on Main guarded by `isFinishing`. `isNonTouchDevice` (PackageManager/Config, no disk) short-circuits first.
- [x] `RealDeviceProfileRepository`: `prefs` + `welcomePrefs` -> `by lazy` (both only touched inside its existing `Dispatchers.IO` migration coroutine, so the `getSharedPreferences` disk load lands on IO). Fixes a latent gap its own comment already intended.
- [x] `CalculatorAprilFoolsPrankManager`: `prefs` -> `by lazy`; also skips the read entirely on the 364 non-April-1 days.
- [x] `PlayerLayoutModePrefs.isCompact` / `isBigButtonsMode`: wrapped in `StrictModeHelper.allowDiskIO`. These are synchronous pre-inflation mirror reads (theme overlay must apply before `super.onCreate`/inflate), so they are treated as accepted narrow exceptions exactly like the return-to-settings read in section 3 - not deferrable.
- [x] `FastMediaSorterApp.onCreate`: `LoggingHelper.hasPreviousCrash()` only gates a warning log, so moved into `applicationScope.launch(Dispatchers.IO)`.
- Verification: on-device StrictMode sweep = 0 `DiskRead`/`DiskWrite` violations at cold start (round-2 log below). PASS.

## 6. Open items

- (Resolved) On-device StrictMode confirmation - see Last Audit `Verified` entry: 0 violations at startup, MainActivity displayed cleanly, no crash.

## Last Audit

2026-07-23, `/spec-all` F5 on-device StrictMode verification after Phase 05 residual fixes (emulator-5554, standard DEBUG v2.60.7220.314, API 35).

Result: **Verified**. Zero main-thread disk I/O at startup - acceptance (§1/§6) met.

Evidence (cold-start logcat `temp/S1153/startup_strictmode_round2.log`, adb.ps1-driven stop -> `logcat -c` -> launch -> dump):
- `StrictMode policy violation` count at startup = **0**; `DiskReadViolation`/`DiskWriteViolation` count across the whole buffer = **0**.
- `Displayed .. MainActivity +3s198ms`, `Fully drawn +3s640ms`, no `FATAL`/`AndroidRuntime`/`ANR`. App reaches MainActivity cleanly.
- Deferred-flow probes fired (before removal): `S1153: previous-crash check deferred to IO`, `S1153: AuthSession deferred init (migrate + refresh)`, `S1153: InputHelp first-run hint deferred prefs read`.
- Round-1 log `temp/S1153/startup_strictmode.log` for comparison: 7 violations from `LoggingHelper.hasPreviousCrash`, `PlayerLayoutModePrefs.isCompact`, `RealDeviceProfileRepository.<init>`, `CalculatorAprilFoolsPrankManager.<init>` - all cleared in round-2.

Post-change audit (DI-scope + startup-path trigger), no P0/P1:
- Lifecycle: `InputHelpFirstRunHint` on `activity.lifecycleScope` (cancels on DESTROY, `isFinishing`-guarded show); `AuthSessionRepositoryImpl` on app-lifetime `@ApplicationScope` (singleton, no Activity retained); lazy-prefs classes hold no lifecycle. No listener register/remove added.
- Concurrency: `by lazy` (SYNCHRONIZED) for cache/prefs; `migrateIfNeeded` idempotent + `@Volatile`/`synchronized`, safe if a public method races the deferred init. `RealDeviceProfileRepository`/`Calculator` prefs only ever read inside their own IO/deferred paths.
- Behaviour preserved: cache dir still created before first use; auth flows populate reactively (unit test `AuthSessionRepositoryImplTest` green, EXIT=0); migration still runs once on IO (not skipped); F1 hint decision unchanged; pre-inflation theme mirror reads still synchronous (allowDiskIO), so dialog/timebar sizing unaffected.
- R8/keep: `@ApplicationScope` is an existing qualifier already in the graph; Hilt codegen compiled clean (`hiltJavaCompileStandardDebug`), no reflection/keep-rule impact.

Builds: `a.ps1 dq` BUILD SUCCESSFUL (round-1 2m01s, round-2 1m37s); post-tag-removal `a.ps1 fk` BUILD SUCCESSFUL 34s; `AuthSessionRepositoryImplTest` EXIT=0.

Probe tags: all `Timber.d("S1153:` removed on the Verified transition (grep clean).

No `[FOLLOW-UP]` outstanding - every on-device residual was code-derivable and fixed inline.

---

2026-07-23, `/spec-sweep` on-device StrictMode verification (emulator-5554, standard DEBUG v2.60.7230.145, API 35).

Result: **Partial**. The three targeted onCreate reads are eliminated, deferred path fires, but StrictMode is not clean at startup - residual main-thread reads remain (out of this ticket's original scope, now the ticket's remaining work).

Evidence (live cold-start logcat `temp/scratch/spec-sweep_20260723_0148/live_launch.log`):
- Deferred path fires: `D/StartupNoticeManager: S1153: presentDeferredNotices showCrashPrompt=true`. Expected the deferred-notice entry to run past first frame - confirmed.
- Targeted three reads ABSENT from onCreate: no StrictMode frame for `DatabaseResetNotice.prefs`, `LocaleHelper.consumeReturnToSettings`, or `LoggingHelper.getLogFiles`/`getLatestCrashFile`. Expected zero - confirmed. The ticket's own deliverable is done.
- App reaches `MainActivity` normally (no crash, no forced return-to-settings); DB-reset / crash-prompt flows not triggered this run (no pending reset/crash) but the deferral coroutine executes.
- RESIDUAL (why Partial, not Verified): StrictMode still logs main-thread disk reads reachable during `MainActivity.onCreate`, all from Hilt field injection at `BaseActivity.onCreate(BaseActivity.kt:122)` plus one posted lambda - NOT the three this ticket addressed:
  - `UnifiedFileCache.<init>` (`AppModule.provideUnifiedFileCache`) - cache dir read during DI graph build.
  - `EncryptedCookieStore.getPrefs`/`migrateIfNeeded` via `AuthSessionRepositoryImpl.<init>` - encrypted-prefs read during DI graph build.
  - `InputHelpFirstRunHint.showIfNeeded` (`MainActivity.onCreate$lambda$8`, posted Runnable) - SharedPreferences read past first frame.
  - (Application-scope, not MainActivity: `LoggingHelper.hasPreviousCrash` from `FastMediaSorterApp.onCreate`.)

Remaining work (this ticket): defer/hoist the DI-init disk reads (`UnifiedFileCache`, `EncryptedCookieStore` eager construction) and the `InputHelpFirstRunHint` prefs read off the main-thread onCreate path to reach the "zero StrictMode at startup" goal in §1/§6.

---

2026-07-23, `/spec-all` F5 phase-boundary audit (startup-path trigger: Layer 1 lifecycle + Layer 2 coroutine). Static evidence; on-device StrictMode observation deferred to the parent loop.

Result: no P0/P1 findings.

- Layer 1 (lifecycle): `StartupNoticeManager` runs on `activity.lifecycleScope` (cancels on DESTROY). It is a throwaway local in onCreate, not a field, so it does not retain the Activity beyond the coroutine. Dialogs are shown only after the IO hop and guarded by `isFinishing`; if the Activity is destroyed mid-read the coroutine cancels before any `show`, so no window leak on a dead Activity. No listener register/remove added - no symmetry concern.
- Layer 2 (coroutine): `withContext(Dispatchers.IO)` sits exactly at the two read boundaries; the show steps return to Main (lifecycleScope default `Main.immediate`). Both read-check-write sequences (`consumePending` clear, `findPendingCrash` watermark) are one-shot and single-caller per startup - no concurrent access, no data race.
- Behaviour preservation: DB-reset dialog reads the same keys and clears once; crash prompt writes the "handled" watermark before showing and keeps the `savedInstanceState == null` gate; return-to-settings unchanged (only StrictMode read suppression widened via `allowDiskIO`). Redirect edge (Welcome / return-to-settings early return) now preserves the pending notice for the next normal launch instead of the prior flash-and-dismiss-and-clear - strictly better, documented in §3.1.
- P3 note (pre-existing, out of scope): `CrashReportPromptManager.sendReport` still uses a raw `CoroutineScope(Dispatchers.IO)` rather than a lifecycle scope; unchanged by this ticket.

Remaining gate: on-device StrictMode sweep at startup (`BlockNeedUserTest`).
