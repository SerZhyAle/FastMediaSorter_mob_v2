# Phase 01 - Thin host delegation

**Strategic spec:** [`../S1329_activity-logic-debt-78-baselined-violations.md`](../S1329_activity-logic-debt-78-baselined-violations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 00 - foundation code phase, but the ratchet gate must exist first
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 6 / 6
**Started:** 2026-08-13
**Completed:** 2026-08-13

---

## Objective

Remove the six single-violation `SettingsRepository` injections from thin Activity hosts by moving the
dependency into the Manager that already owns the behavior, and expose the settings stream to `BaseActivity`
subclasses so they never touch the repository themselves.

---

## Prerequisites

- [x] Owner confirmed the split recorded in `INDEX.md` "Scope of this ticket".
- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1329 phase 01"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/KeepScreenAwakeManager.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified (621 LOC - backup first) | ≤ 630 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/CalculatorActivity.kt` | Modified | ≤ 127 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetManagerFactory.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchActivity.kt` | Modified | ≤ 66 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManagerFactory.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureActivity.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManagerFactory.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt` | Modified | ≤ 469 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentManager.kt` | New | ≤ 70 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt` | Modified | ≤ 101 |

> `BaseActivity.kt` is 621 LOC - take a timestamped backup per Rule 5 before editing (CLAUDE.md Rule 5).
>
> **Flavor placement.** `ScreenCaptureConsentActivity` and its new manager live in `app_v2/src/screenCapture/`.
> Do not place either under `src/main/`.
>
> No `res/layout*` file is touched in this phase - landscape parity not applicable.

---

## Steps

### Step 01.1 - Own keep-screen-on in a Manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/KeepScreenAwakeManager.kt` (New),
`app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`

**Prompt for developer:**

> Back up `BaseActivity.kt` to the ticket's scratch directory (Rule 1) first. Create `KeepScreenAwakeManager` in `core/ui/` with
> `@Inject constructor(private val settingsRepository: SettingsRepository)`, exposing `val settings: Flow<AppSettings>`
> delegating to `settingsRepository.getSettings()`. In `BaseActivity`, delete the
> `@Inject lateinit var keepScreenSettingsRepository: SettingsRepository` field (and its now-unused
> `SettingsRepository` import), inject `KeepScreenAwakeManager` instead, and expose
> `protected val appSettings: Flow<AppSettings> get() = keepScreenAwakeManager.settings` so subclasses read
> settings without a repository reference. Keep the existing `collectOnLifecycle(..)` call site and the
> `keepScreenAwakeFor(settings)` contract exactly as they are - this is a dependency move, not a behavior change.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/KeepScreenAwakeManager.kt` exists.
- `Grep` - `class KeepScreenAwakeManager @Inject constructor` matches exactly once in that file.
- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `BaseActivity.kt`.
- `Grep` - `protected val appSettings` matches exactly once in `BaseActivity.kt`.
- `Grep` - `keepScreenAwakeFor` still present in `BaseActivity.kt`.
- `Grep` - `import com.sza.fastmediasorter.domain.repository.SettingsRepository` returns zero hits in `BaseActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Six thin hosts stopped naming the data layer. 01.1 KeepScreenAwakeManager (core/ui, unscoped @Inject constructor - the prompt named no Hilt scope, and the sibling ScreenRecordingDisclosureManager confirms unscoped is the house shape) now holds the repository, and BaseActivity exposes protected val appSettings for subclasses. 01.2 CalculatorActivity reads that inherited stream. 01.3 and 01.4 gained CameraLaunchWidgetManagerFactory and CameraQuickCaptureLaunchManagerFactory, both mirroring PhotoCaptureLaunchManagerFactory; the S1174 finish() branches and the trampoline comment are untouched. 01.5 CameraOcrFlowManagerFactory builds TranslationManager and CameraOcrFlowManager together and carries the one-shot read as suspend fun currentSettings(). 01.6 ScreenCaptureConsentManager took the disclosure read and write out of the host and stayed in src/screenCapture. Plan correction: the Probes-survive invariant was stale - S1242, S1214, S1114 and S0995 are all Archived and their Timber probes were removed on that transition, so the two predicates demanding those tags were replaced with predicates demanding their absence; re-adding one would have broken the CLAUDE.md iff invariant and failed assert-no-ticket-logs. The ordinary // S1214: rationale comments survive, count 2. Evidence: activity-logic expected 72 | actual 72, delta -6, baseline auto-ratcheted 78 -> 72; a.ps1 fk exit 0; a.ps1 dq exit 0 with hiltJavaCompileStandardDebug green, which is what proves the four new @Inject constructor classes resolve in the graph - fk alone does not. standard mounts src/screenCapture (fms.screenCapture=on), so 01.6 compiled. Zero @Suppress(ActivityLogicViolation), zero Timber.d S1329, zero TODO(phase-01), lint-baseline.xml untouched.

---

### Step 01.2 - CalculatorActivity reads the inherited settings stream

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/CalculatorActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete the `@Inject lateinit var settingsRepository: SettingsRepository` field and its import. Replace the
> `collectOnLifecycle(settingsRepository.getSettings())` call with `collectOnLifecycle(appSettings)` using the
> property added to `BaseActivity` in step 01.1. No other change.

**Verification:**

- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `CalculatorActivity.kt`.
- `Grep` - `collectOnLifecycle(appSettings)` matches exactly once in `CalculatorActivity.kt`.
- `Grep` - `SettingsRepository` returns zero hits in `CalculatorActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Six thin hosts stopped naming the data layer. 01.1 KeepScreenAwakeManager (core/ui, unscoped @Inject constructor - the prompt named no Hilt scope, and the sibling ScreenRecordingDisclosureManager confirms unscoped is the house shape) now holds the repository, and BaseActivity exposes protected val appSettings for subclasses. 01.2 CalculatorActivity reads that inherited stream. 01.3 and 01.4 gained CameraLaunchWidgetManagerFactory and CameraQuickCaptureLaunchManagerFactory, both mirroring PhotoCaptureLaunchManagerFactory; the S1174 finish() branches and the trampoline comment are untouched. 01.5 CameraOcrFlowManagerFactory builds TranslationManager and CameraOcrFlowManager together and carries the one-shot read as suspend fun currentSettings(). 01.6 ScreenCaptureConsentManager took the disclosure read and write out of the host and stayed in src/screenCapture. Plan correction: the Probes-survive invariant was stale - S1242, S1214, S1114 and S0995 are all Archived and their Timber probes were removed on that transition, so the two predicates demanding those tags were replaced with predicates demanding their absence; re-adding one would have broken the CLAUDE.md iff invariant and failed assert-no-ticket-logs. The ordinary // S1214: rationale comments survive, count 2. Evidence: activity-logic expected 72 | actual 72, delta -6, baseline auto-ratcheted 78 -> 72; a.ps1 fk exit 0; a.ps1 dq exit 0 with hiltJavaCompileStandardDebug green, which is what proves the four new @Inject constructor classes resolve in the graph - fk alone does not. standard mounts src/screenCapture (fms.screenCapture=on), so 01.6 compiled. Zero @Suppress(ActivityLogicViolation), zero Timber.d S1329, zero TODO(phase-01), lint-baseline.xml untouched.

---

### Step 01.3 - Factory for CameraLaunchWidgetManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetManagerFactory.kt` (New),
`app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchActivity.kt`
**Depends on:** - independent of 01.1

**Prompt for developer:**

> `CameraLaunchActivity` injects `SettingsRepository` for the sole purpose of forwarding it into the
> `CameraLaunchWidgetManager` it builds in `onCreate`. Mirror the existing sibling precedent
> `app_v2/src/main/java/com/sza/fastmediasorter/widget/PhotoCaptureLaunchManagerFactory.kt`: create
> `CameraLaunchWidgetManagerFactory` with `@Inject constructor(private val settingsRepository: SettingsRepository,
> private val mediaCapabilities: MediaCapabilities)` and a `create(..)` method taking the per-instance
> arguments the Activity supplies today (`activity`, `coroutineScope`, `forceVideo`, `requestPermission`,
> `launchCapture`, `finish`). Inject the factory into the Activity, delete both the `settingsRepository`
> and `mediaCapabilities` fields plus their imports, and construct the manager through the factory.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraLaunchWidgetManagerFactory.kt` exists.
- `Grep` - `class CameraLaunchWidgetManagerFactory @Inject constructor` matches exactly once in that file.
- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `CameraLaunchActivity.kt`.
- `Grep` - `CameraLaunchWidgetManagerFactory` matches at least once in `CameraLaunchActivity.kt`.
- `Grep` - `SettingsRepository` returns zero hits in `CameraLaunchActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Six thin hosts stopped naming the data layer. 01.1 KeepScreenAwakeManager (core/ui, unscoped @Inject constructor - the prompt named no Hilt scope, and the sibling ScreenRecordingDisclosureManager confirms unscoped is the house shape) now holds the repository, and BaseActivity exposes protected val appSettings for subclasses. 01.2 CalculatorActivity reads that inherited stream. 01.3 and 01.4 gained CameraLaunchWidgetManagerFactory and CameraQuickCaptureLaunchManagerFactory, both mirroring PhotoCaptureLaunchManagerFactory; the S1174 finish() branches and the trampoline comment are untouched. 01.5 CameraOcrFlowManagerFactory builds TranslationManager and CameraOcrFlowManager together and carries the one-shot read as suspend fun currentSettings(). 01.6 ScreenCaptureConsentManager took the disclosure read and write out of the host and stayed in src/screenCapture. Plan correction: the Probes-survive invariant was stale - S1242, S1214, S1114 and S0995 are all Archived and their Timber probes were removed on that transition, so the two predicates demanding those tags were replaced with predicates demanding their absence; re-adding one would have broken the CLAUDE.md iff invariant and failed assert-no-ticket-logs. The ordinary // S1214: rationale comments survive, count 2. Evidence: activity-logic expected 72 | actual 72, delta -6, baseline auto-ratcheted 78 -> 72; a.ps1 fk exit 0; a.ps1 dq exit 0 with hiltJavaCompileStandardDebug green, which is what proves the four new @Inject constructor classes resolve in the graph - fk alone does not. standard mounts src/screenCapture (fms.screenCapture=on), so 01.6 compiled. Zero @Suppress(ActivityLogicViolation), zero Timber.d S1329, zero TODO(phase-01), lint-baseline.xml untouched.

---

### Step 01.4 - Factory for CameraQuickCaptureLaunchManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManagerFactory.kt` (New),
`app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureActivity.kt`
**Depends on:** Step 01.3 (same pattern - reuse its shape)

**Prompt for developer:**

> Same transformation as step 01.3 for `CameraQuickCaptureActivity`, whose `onCreate` builds
> `CameraQuickCaptureLaunchManager` and forwards `settingsRepository` into it. Create
> `CameraQuickCaptureLaunchManagerFactory` holding the injected `SettingsRepository` and the other
> already-injected singletons the manager needs; keep `appWidgetId` and the lambdas as `create(..)`
> arguments. Delete the Activity's `settingsRepository` field and import. Preserve every `finish()`
> branch - the S1174 comment in the file header explains why this trampoline must not outlive the flow.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManagerFactory.kt` exists.
- `Grep` - `class CameraQuickCaptureLaunchManagerFactory @Inject constructor` matches exactly once in that file.
- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `CameraQuickCaptureActivity.kt`.
- `Grep` - `SettingsRepository` returns zero hits in `CameraQuickCaptureActivity.kt`.
- `Grep` - `S1174` comment still present in `CameraQuickCaptureActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Six thin hosts stopped naming the data layer. 01.1 KeepScreenAwakeManager (core/ui, unscoped @Inject constructor - the prompt named no Hilt scope, and the sibling ScreenRecordingDisclosureManager confirms unscoped is the house shape) now holds the repository, and BaseActivity exposes protected val appSettings for subclasses. 01.2 CalculatorActivity reads that inherited stream. 01.3 and 01.4 gained CameraLaunchWidgetManagerFactory and CameraQuickCaptureLaunchManagerFactory, both mirroring PhotoCaptureLaunchManagerFactory; the S1174 finish() branches and the trampoline comment are untouched. 01.5 CameraOcrFlowManagerFactory builds TranslationManager and CameraOcrFlowManager together and carries the one-shot read as suspend fun currentSettings(). 01.6 ScreenCaptureConsentManager took the disclosure read and write out of the host and stayed in src/screenCapture. Plan correction: the Probes-survive invariant was stale - S1242, S1214, S1114 and S0995 are all Archived and their Timber probes were removed on that transition, so the two predicates demanding those tags were replaced with predicates demanding their absence; re-adding one would have broken the CLAUDE.md iff invariant and failed assert-no-ticket-logs. The ordinary // S1214: rationale comments survive, count 2. Evidence: activity-logic expected 72 | actual 72, delta -6, baseline auto-ratcheted 78 -> 72; a.ps1 fk exit 0; a.ps1 dq exit 0 with hiltJavaCompileStandardDebug green, which is what proves the four new @Inject constructor classes resolve in the graph - fk alone does not. standard mounts src/screenCapture (fms.screenCapture=on), so 01.6 compiled. Zero @Suppress(ActivityLogicViolation), zero Timber.d S1329, zero TODO(phase-01), lint-baseline.xml untouched.

---

### Step 01.5 - Factory for the camera-OCR manager pair

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManagerFactory.kt` (New),
`app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/CameraOcrTranslateActivity.kt`
**Depends on:** Step 01.1, Step 01.3

**Prompt for developer:**

> `CameraOcrTranslateActivity` uses `settingsRepository` four ways: it forwards it into `TranslationManager`
> and into `CameraOcrFlowManager`, collects it for the crop-step language cluster, and reads it once with
> `.first()`. Create `CameraOcrFlowManagerFactory` in `ui/cameraocr/helpers/` with
> `@Inject constructor(private val settingsRepository: SettingsRepository)` and a `create(..)` that builds
> both `TranslationManager` and `CameraOcrFlowManager`, taking the Activity-scoped pieces (context, scope,
> storage manager, callbacks) as arguments. Replace the `collectOnLifecycle(settingsRepository.getSettings())`
> with `collectOnLifecycle(appSettings)` from step 01.1, and move the one-shot `.first()` read behind a
> `suspend fun` on the factory. Delete the Activity's `settingsRepository` field and import.
>
> **Probe note, corrected 2026-08-13.** The step was written when `S1242` and `S1214` were
> `BlockNeedUserTest`. Both are now `Archived` and their `Timber.d` probes were removed on that
> transition, so there is no probe in this file to preserve and none may be re-added - a tag exists
> if and only if its ticket is `BlockNeedUserTest` (CLAUDE.md "Debug Verification Tags"), and
> `assert-no-ticket-logs` enforces it. The two ordinary `// S1214:` rationale comments are not probes
> and must survive unchanged, per Rule 8.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManagerFactory.kt` exists.
- `Grep` - `class CameraOcrFlowManagerFactory @Inject constructor` matches exactly once in that file.
- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `CameraOcrTranslateActivity.kt`.
- `Grep` - `Timber.d("S1242:` and `Timber.d("S1214:` each return zero hits in `CameraOcrTranslateActivity.kt` (both tickets Archived - a probe here would be the stale-tag defect).
- `Grep` - `// S1214:` still matches twice in `CameraOcrTranslateActivity.kt` (rationale comments preserved).
- `Grep` - `SettingsRepository` returns zero hits in `CameraOcrTranslateActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Six thin hosts stopped naming the data layer. 01.1 KeepScreenAwakeManager (core/ui, unscoped @Inject constructor - the prompt named no Hilt scope, and the sibling ScreenRecordingDisclosureManager confirms unscoped is the house shape) now holds the repository, and BaseActivity exposes protected val appSettings for subclasses. 01.2 CalculatorActivity reads that inherited stream. 01.3 and 01.4 gained CameraLaunchWidgetManagerFactory and CameraQuickCaptureLaunchManagerFactory, both mirroring PhotoCaptureLaunchManagerFactory; the S1174 finish() branches and the trampoline comment are untouched. 01.5 CameraOcrFlowManagerFactory builds TranslationManager and CameraOcrFlowManager together and carries the one-shot read as suspend fun currentSettings(). 01.6 ScreenCaptureConsentManager took the disclosure read and write out of the host and stayed in src/screenCapture. Plan correction: the Probes-survive invariant was stale - S1242, S1214, S1114 and S0995 are all Archived and their Timber probes were removed on that transition, so the two predicates demanding those tags were replaced with predicates demanding their absence; re-adding one would have broken the CLAUDE.md iff invariant and failed assert-no-ticket-logs. The ordinary // S1214: rationale comments survive, count 2. Evidence: activity-logic expected 72 | actual 72, delta -6, baseline auto-ratcheted 78 -> 72; a.ps1 fk exit 0; a.ps1 dq exit 0 with hiltJavaCompileStandardDebug green, which is what proves the four new @Inject constructor classes resolve in the graph - fk alone does not. standard mounts src/screenCapture (fms.screenCapture=on), so 01.6 compiled. Zero @Suppress(ActivityLogicViolation), zero Timber.d S1329, zero TODO(phase-01), lint-baseline.xml untouched.

---

### Step 01.6 - Move consent persistence out of ScreenCaptureConsentActivity

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentManager.kt` (New),
`app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt`
**Depends on:** - independent

**Prompt for developer:**

> This Activity does not just read settings - it writes them
> (`updateSettings(current.copy(screenCaptureDisclosureAccepted = true))`), which is business logic in a UI
> host. Create `ScreenCaptureConsentManager` **in the `src/screenCapture` source set** (not `src/main`) with
> `@Inject constructor(private val settingsRepository: SettingsRepository)` exposing
> `suspend fun isDisclosureAccepted(): Boolean` and `suspend fun markDisclosureAccepted()`. Inject the manager
> into the Activity, replace the three repository call sites with manager calls, and delete the
> `settingsRepository` field and its import. The Activity extends `AppCompatActivity`, not `BaseActivity`,
> so it cannot use the inherited `appSettings` property from step 01.1.

**Verification:**

- `Glob` - `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentManager.kt` exists.
- `Glob` - no file named `ScreenCaptureConsentManager.kt` exists under `app_v2/src/main/`.
- `Grep` - `class ScreenCaptureConsentManager @Inject constructor` matches exactly once in the new file.
- `Grep` - `markDisclosureAccepted` matches exactly once in the new file.
- `Grep` (multiline) - `@Inject[\s\S]{0,120}?var\s+\w+\s*:\s*[^\n]*(Repository|UseCase|DataSource|Dao|Database)` returns zero hits in `ScreenCaptureConsentActivity.kt`.
- `Grep` - `updateSettings` returns zero hits in `ScreenCaptureConsentActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Six thin hosts stopped naming the data layer. 01.1 KeepScreenAwakeManager (core/ui, unscoped @Inject constructor - the prompt named no Hilt scope, and the sibling ScreenRecordingDisclosureManager confirms unscoped is the house shape) now holds the repository, and BaseActivity exposes protected val appSettings for subclasses. 01.2 CalculatorActivity reads that inherited stream. 01.3 and 01.4 gained CameraLaunchWidgetManagerFactory and CameraQuickCaptureLaunchManagerFactory, both mirroring PhotoCaptureLaunchManagerFactory; the S1174 finish() branches and the trampoline comment are untouched. 01.5 CameraOcrFlowManagerFactory builds TranslationManager and CameraOcrFlowManager together and carries the one-shot read as suspend fun currentSettings(). 01.6 ScreenCaptureConsentManager took the disclosure read and write out of the host and stayed in src/screenCapture. Plan correction: the Probes-survive invariant was stale - S1242, S1214, S1114 and S0995 are all Archived and their Timber probes were removed on that transition, so the two predicates demanding those tags were replaced with predicates demanding their absence; re-adding one would have broken the CLAUDE.md iff invariant and failed assert-no-ticket-logs. The ordinary // S1214: rationale comments survive, count 2. Evidence: activity-logic expected 72 | actual 72, delta -6, baseline auto-ratcheted 78 -> 72; a.ps1 fk exit 0; a.ps1 dq exit 0 with hiltJavaCompileStandardDebug green, which is what proves the four new @Inject constructor classes resolve in the graph - fk alone does not. standard mounts src/screenCapture (fms.screenCapture=on), so 01.6 compiled. Zero @Suppress(ActivityLogicViolation), zero Timber.d S1329, zero TODO(phase-01), lint-baseline.xml untouched.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly). Include a `screenCapture`-carrying flavor so step 01.6 is compiled.
- [x] `Grep` - `@Suppress("ActivityLogicViolation")` returns zero hits repository-wide.
- [x] `Grep` - `Timber.d("S1329:` returns zero hits (this ticket adds no probes).
- [x] `app_v2/lint-baseline.xml` is byte-identical to its pre-phase state - it is regenerated in Phase 06, never hand-edited here.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - five new public classes added.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).
- [x] `scripts/utils/exit-code-lock.ps1` called (or `post-change.ps1` closure released the lock).

---

## Handoff Notes to Next Phase

`BaseActivity` now exposes `protected val appSettings: Flow<AppSettings>`. Every later phase whose Activity
extends `BaseActivity` and only needs a settings snapshot should consume that property instead of adding a
ViewModel surface for it - this applies to `settingsRepository` in all four standalone hosts (Phases 02-03)
and in `BrowseActivity` (Phase 05). The `@Inject`-constructed factory shape established in steps 01.3-01.5 is
the template for any other manually constructed Manager that currently receives a domain type from its host.

---

## Rollback Plan

Revert the phase commit(s). No data migration, no user-facing surface, no persisted-format change - the only
persistence touched is the pre-existing `screenCaptureDisclosureAccepted` flag, whose read/write semantics are
unchanged.
