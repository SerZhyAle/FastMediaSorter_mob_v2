# S0910 - Welcome enable-all: stage-chain callback lost on process death, sequence stalls

**Ticket:** S0910
**Status:** Archived
**Priority:** 30
**Date:** 2026-07-03
**Tier:** 3 - Moderate (ad-hoc)

<!-- discovered by /spec-all S0904 triage - 2026-07-03 (deferred finding 4) -->

## Goal

Enable-all в Welcome теряет переход permissions-стадии в default-player-стадию, если процесс убит посреди special-permission экрана: пермишн-стадия дорезюмируется, но `WelcomePermissionsManager` не может вызвать `WelcomeEnableAllManager.beginDefaultPlayerStage()`, потому что коллбек - переходное поле, не переживающее пересоздание. Цель: `WelcomeEnableAllManager` персистит факт "ещё не начал default-player-стадию" и на recreation переустанавливает коллбек в `WelcomePermissionsManager`, не перезапуская уже пройденную часть run.

## 0. Raw capture / evidence (research confirmed 2026-07-04)

- [WelcomePermissionsManager.kt:75](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt#L75) - `grantAllOnComplete: (() -> Unit)?` - instance-поле, не в `onSaveInstanceState`/`onRestoreInstanceState` (строки 140-152).
- [WelcomeEnableAllManager.kt:112-114](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeEnableAllManager.kt#L112-L114) - `pm.runGrantAll { beginDefaultPlayerStage() }` - именно этот лямбда-аргумент теряется.
- [WelcomePermissionsManager.kt:216-220](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomePermissionsManager.kt#L216-L220) - `launchNextSpecialPermission()` на финише run вызывает `grantAllOnComplete?.invoke()` - no-op после пересоздания, `default-player`-стадия не запускается, `WelcomeEnableAllManager.inProgress` остаётся `true` навсегда (последовательность молча стоит).
- [WelcomeActivity.kt:97-124,155-171](app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt#L97-L124) - порядок вызовов при recreate: `setupViews()` (внутри `super.onCreate()`) вызывает `permissionsManager.attach(this)` затем `enableAllManager.attach(this, permissionsManager) { .. }`; **после** возврата из `super.onCreate()` вызываются `permissionsManager.onRestoreInstanceState(..)` (строка 162), затем `enableAllManager.onRestoreInstanceState(..)` (строка 163) - т.е. `pm.grantAllInProgress` уже восстановлен к моменту восстановления `eam`, что и делает возможным реаттач коллбека именно в `eam.onRestoreInstanceState`.
- Затрагивает не только process death, но и любое пересоздание Activity (rotation) - `WelcomePermissionsManager`/`WelcomeEnableAllManager` field-injected заново на каждый recreate (см. KDoc обоих классов), коллбек теряется в обоих случаях одинаково; текущий `onSaveInstanceState`/`onRestoreInstanceState` в обоих менеджерах закрывает только "восстановить флаги", но не "восстановить кросс-менеджерный коллбек".

## 1. Root cause

`WelcomeEnableAllManager.inProgress` персистится и покрывает **весь** enable-all run (обе стадии - permissions и default-player), но менеджер не различает "ещё жду коллбек от permissions-стадии" и "уже веду default-player-стадию (там resume и так работает через persisted `currentTypeIndex` + переустановленный `defaultPlayerLauncher`)". Из-за этого на recreation нет сигнала "нужно заново передать `pm` завершающий коллбек".

## 2. Fix

### Phase 1 - Persist default-player-stage-started flag and reattach the completion callback

1. В `WelcomePermissionsManager` добавить публичный метод рядом с `runGrantAll`:
   `fun reattachGrantAllCallback(onComplete: () -> Unit) { if (grantAllInProgress) grantAllOnComplete = onComplete }` - идемпотентно, no-op если run не идёт (обычный холодный старт страницы).
   - Verification: компилируется; вызов при `grantAllInProgress=false` не создаёт висящий коллбек.
2. В `WelcomeEnableAllManager` добавить приватное персистируемое поле `defaultPlayerStageStarted: Boolean = false`:
   - `true` в начале `beginDefaultPlayerStage()`.
   - сброс в `false` в `finishSequence()` (рядом с существующим сбросом `inProgress`/`currentTypeIndex`).
   - persist/restore через новый ключ бандла (`STATE_DEFAULT_PLAYER_STAGE_STARTED`) в `onSaveInstanceState`/`onRestoreInstanceState`.
   - Verification: существующий resume default-player-стадии (`currentTypeIndex` + `onDefaultPlayerDialogReturned`) не меняется - новое поле только различает, какая стадия шла на момент смерти процесса.
3. В `WelcomeEnableAllManager.onRestoreInstanceState`, после восстановления трёх полей: если `inProgress && !defaultPlayerStageStarted`, вызвать `permissionsManager?.reattachGrantAllCallback { beginDefaultPlayerStage() }`.
   - Verification: смерть процесса на special-permission экране -> после возврата permissions-run завершается штатно И `beginDefaultPlayerStage()` реально вызывается (а не no-op).

### Phase 2 - Build gate

1. `standard debug` компилируется (`a.ps1 dq`).
   - Verification: BUILD SUCCESSFUL.

### Phase 3 - Device verification (deferred, device-gated)

1. На устройстве: Welcome -> "Enable all" -> на экране special-permission (например, All files access) убить процесс приложения (`Don't keep activities` или `adb shell am kill`) -> вернуться в приложение -> permissions-стадия завершается И default-player-стадия запускается (либо, на сборках без поддержки default-player, сразу завершается онбординг).
   - Verification: device test через `/spec-test-device` / `/spec-sweep`, когда устройство online.

**Emulator attempt 2026-07-05 (emulator-5554, Android, build v2.60.7041.926-DEBUG) - INCONCLUSIVE.** Прогнал Welcome -> Enable all -> все runtime-пермишены -> дошёл до special-экрана "Media management apps" (MANAGE_MEDIA) -> `am kill` процесса -> Back. Наблюдения: `WelcomePermissionsManager: grant-all run finished (shown 1 special permissions)` и `DefaultPlayerManager: primary player ENABLED` появились после возврата (положительный признак - default-player-стадия отработала, видимого stall нет), НО целевой пробойник `S0910: reattached grant-all completion callback after recreation` НЕ сработал. После `am kill` приложение, судя по экрану, перезапустилось на Welcome page 1 (свежий старт), а не восстановило savedInstanceState mid-run, поэтому именно reattach-путь чисто воспроизвести не удалось; эмулятор к тому же ушёл в touch-wedge (тапы перестали регистрироваться) - известная AVD-болячка. Вывод: нужен реальный девайс для чистого process-death + savedInstanceState-restore прогона (как и указано в исходном плане Phase 3). Статус остаётся BlockNeedUserTest.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0904 (infra-misc-p2 - источник triage, находки 1/2/3/5 закрыты там), S0876 (welcome enable-all lost-update - смежный поток, уже закрыт отдельно; этот тикет - соседний пробел в той же оркестрации).
- **Flavor scope:** оба менеджера живут в `src/main` - фикс применяется ко всем flavor'ам одинаково, `BuildConfig`-специфики нет.

## Related

- S0904 (infra-misc-p2 - deferred this finding here).
- S0876 (welcome enable-all lost-update).

<!-- auto-approved by /spec-all - 2026-07-04 -->

## Last Audit

- **2026-07-10** - `/spec-test-device` on emulator-5554 (Android 13 / SDK 33, x86_64, FRESH standard debug v2.60.7092.225-DEBUG with the 2026-07-10 attach-ordering fix). Verdict: **PASS** - process-death + savedInstanceState-restore path genuinely exercised; probe fires and the enable-all flow resumes into the default-player stage. Evidence: temp/S0910/raw_full_buffer_20260710_PASS.log.
- **2026-07-09** - `/spec-test-device` on emulator-5554 (Android 13 / SDK 33, x86_64, FRESH standard debug v2.60.7092.225-DEBUG built + installed with the attach()-based fix). Verdict: **FAIL** - the fix is still ineffective on real process death; probe never fires and the enable-all default-player stage never runs. New, deeper root cause found (attach ordering vs synchronous ActivityResult redelivery). Scenario + evidence: temp/S0910/mobile_test_scenario_20260709.md, raw buffer temp/S0910/raw_full_buffer_20260709.log.
- **2026-07-07** - `/spec-test-device` on emulator-5554 (Android 17 / SDK 37, x86_64, standard debug v2.60.7041.926-DEBUG). Verdict: **FAIL** - the Phase 1 fix is ineffective on process death; the enable-all sequence stalls and the probe never fires. Scenario + full evidence: temp/S0910/mobile_test_scenario_20260707.md.

### Manual / on-device

- [x] 2026-07-10 (fresh build v2.60.7092.225 with attach-ordering fix): Welcome -> Enable all -> granted all runtime perms -> reached the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` special-permission screen (`com.android.settings/.fuelgauge.RequestIgnoreBatteryOptimizations`, task t220), grant-all mid special-permissions stage (special launched, no "run finished" line). Killed process mid-screen: HOME (task backgrounded, process cached) -> `am kill` (pid 24095 dead, pidof empty), task t220 preserved in recents with both WelcomeActivity + the settings activity. Reopened task from recents card -> process respawned (pid 24321), WelcomeActivity recreated DIRECTLY from saved state (`onCreate: WelcomeActivity` with NO `MainActivity` entry for the pid = genuine savedInstanceState restore, not a cold start), settings dialog on top; `setupViews[WelcomeActivity]` deferred 6954ms until the dialog was dismissed. Tapped Deny on the battery dialog -> result redelivered to the restored WelcomeActivity. RESULT: probe `S0910: beginDefaultPlayerStage reached after grant-all completion` FIRED (08:59:28.727); `WelcomePermissions: grant-all run finished (shown 1 special permissions)` (08:59:28.727); `DefaultPlayerManager: primary player ENABLED` (08:59:28.753); top activity advanced to the default-player `ResolverActivity` ("Open with" chooser) in task t220. The completion callback was re-armed and invoked (not a no-op); enable-all resumed into the default-player stage - no stall on Welcome page 1. **PASS** - temp/S0910/raw_full_buffer_20260710_PASS.log.
  - Repro note: on this emulator image both `isExternalStorageManager()` and `canManageMedia()` return true at startup regardless of `appops set MANAGE_MEDIA/MANAGE_EXTERNAL_STORAGE ignore`, so the MANAGE_MEDIA/all-files special screens cannot be forced here (this is why prior runs showed "0 special permissions"). The `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` special screen was forced instead by removing the app from the deviceidle whitelist (`dumpsys deviceidle whitelist -com.sza.fastmediasorter.debug`); it exercises the identical special-permission ActivityResult-redelivery code path. The savedInstanceState restore (vs cold-start-on-page-1) was achieved via HOME -> `am kill` (cached) -> reopen from the recents card; killing the foreground process directly (kill -TERM / am kill while resumed) instead cold-started through MainActivity into a fresh task.
- [!] 2026-07-09 (fresh build with reattach in `enableAllManager.attach()`): Welcome -> Enable all -> grant all runtime perms -> on MANAGE_MEDIA "Media management apps" special screen `am kill` (old pid 11174 dead, pidof empty) -> BACK -> app recreated (new pid 11620), WelcomeActivity restored from saved state. Expected: probe `S0910:` fires, grant-all finishes into the default-player STAGE. Actual: probe appeared 0 times in the full 1409-line buffer; `WelcomePermissions: grant-all run finished` fired at 23:47:09.554 (2ms into setupViews) with a null completion callback; `beginDefaultPlayerStage()` never ran (the `DefaultPlayerManager ENABLED` lines at 23:47:39 are from `DeferredStartupWorker default-player-state-bootstrap` re-applying persisted settings, NOT the stage). Sequence STALLED on Welcome page 1. FAIL - temp/S0910/mobile_test_scenario_20260709.md.
- [!] 2026-07-07 (build v2.60.7041.926): pid 11219 killed -> restarted 11785, WelcomeActivity recreated, `grant-all run finished` fired from new pid, but `S0910:` probe 0/17007 lines and sequence stalled on Welcome page 1 - see temp/S0910/mobile_test_scenario_20260707.md.

### Root cause found on-device (2026-07-09) - attach ordering vs synchronous ActivityResult redelivery

- The attach()-based fix is still dead code on real process death, one layer deeper than the 2026-07-07 finding. `WelcomeActivity.setupViews` calls `permissionsManager.attach(this)` (WelcomeActivity.kt:115) BEFORE `enableAllManager.attach(this, permissionsManager){ .. }` (WelcomeActivity.kt:119). Because `setupViews` is deferred to the first frame (post{}), the lifecycle is already STARTED/RESUMED when it runs, so `permissionsManager.attach()`'s `registryHost.register(KEY_SPECIAL_SETTINGS, ..)` makes the ActivityResultRegistry SYNCHRONOUSLY redeliver the pending MANAGE_MEDIA result. Its callback runs `launchNextSpecialPermission()`, which ends the run: `grantAllOnComplete?.invoke()` is a no-op (still null) and `grantAllInProgress` is set false (WelcomePermissionsManager.kt:227-231) - all before `enableAllManager.attach()` executes. When `enableAllManager.attach()` then calls `reattachGrantAllCallback { beginDefaultPlayerStage() }`, it no-ops because `reattachGrantAllCallback` guards on `grantAllInProgress` (now false). Probe never logs, callback never armed, `beginDefaultPlayerStage()` never runs, `inProgress` stays true - the exact stall this ticket targets.
- Fix direction (NOT applied): arm the completion callback BEFORE the pending special-permission result is redelivered - e.g. re-order so `enableAllManager.attach()` (its reattach) runs before `permissionsManager.attach()` registers `specialSettingsLauncher`, or re-arm `grantAllOnComplete` inside `WelcomePermissionsManager.onRestoreInstanceState` before its own `register()`, or drop the `grantAllInProgress` guard's dependence on the synchronous run-finish. The `if (grantAllInProgress)` guard in `reattachGrantAllCallback` also races the synchronous finish and must not gate the re-arm.

### Root cause found on-device (2026-07-07)

- The reattach is dead code in practice. `WelcomeActivity.onCreate` calls `enableAllManager.onRestoreInstanceState()` synchronously right after `super.onCreate()`, but `BaseActivity` defers `setupViews()` - which runs `permissionsManager.attach()` / `enableAllManager.attach()` - via `binding.root.post { .. }` (BaseActivity.kt:145-155, documented at :73-75), so it runs one frame later. At restore time `enableAllManager.permissionsManager` is therefore still null and `permissionsManager?.reattachGrantAllCallback { .. }` short-circuits: the callback is never restored and the probe never logs. When the pending special-permission result is then dispatched during the posted `attach()`, `launchNextSpecialPermission` finishes the run and invokes a null `grantAllOnComplete`, so `beginDefaultPlayerStage()` never runs and `inProgress` stays true forever - the exact stall this ticket targets.
- The spec Section 0 ordering assumption ("setupViews() (inside super.onCreate()) runs before onRestoreInstanceState()") is invalid because of the posted first-frame defer. The flaw applies to every recreation path (rotation, Don't-keep-activities, process death), not only process death.
- Fix direction (not yet applied): move the reattach off the synchronous `onCreate` restore to after both managers are attached - e.g. reattach inside `enableAllManager.attach()` guarded by the restored `inProgress && !defaultPlayerStageStarted`, or re-run it at the end of `setupViews()` / in `onResumeWithViews()`.

### DKA note

- "Don't keep activities" did NOT destroy WelcomeActivity across the in-task `startActivityForResult` special-permission flow on this emulator (no `onDestroy: WelcomeActivity` from BaseActivity), so it cannot drive the reattach path here - real `am kill` was required to reproduce.

### Applied fix (2026-07-10) - attach ordering

- **Root fix:** in `WelcomeActivity.setupViews()`, `enableAllManager.attach(..)` now runs BEFORE `permissionsManager.attach(this)` (previously the reverse). This arms `grantAllOnComplete` (via `reattachGrantAllCallback { beginDefaultPlayerStage() }`, guarded on the restored `grantAllInProgress == true`) BEFORE `permissionsManager.attach()`'s `register(KEY_SPECIAL_SETTINGS)` synchronously redelivers the pending MANAGE_MEDIA result. When that redelivery then finishes the grant-all run (`launchNextSpecialPermission()` -> `grantAllInProgress = false` + `grantAllOnComplete?.invoke()`), the callback is now non-null, so `beginDefaultPlayerStage()` runs instead of no-opping.
- Why re-ordering is sufficient and safe: `enableAllManager.attach()` only needs the (already-constructed) `permissionsManager` reference and the restored `inProgress`/`defaultPlayerStageStarted` (set in `onRestoreInstanceState` during `onCreate`, before `setupViews`); it registers its own independent `KEY_DEFAULT_PLAYER` launcher. It has no dependency on `permissionsManager.attach()` having run. The two launchers use distinct registry keys, so registration order is irrelevant to correctness. Normal first-run / rotation paths are unaffected (`inProgress == false` skips the re-arm block).
- The `reattachGrantAllCallback` `if (grantAllInProgress)` guard is left intact: with the new order the re-arm precedes the synchronous run-finish, so the guard sees `true`.
- Validation: `compileStandardDebugKotlin` PASS. Device re-test pending (probe `S0910: beginDefaultPlayerStage reached ..` in `WelcomeEnableAllManager.beginDefaultPlayerStage`).
