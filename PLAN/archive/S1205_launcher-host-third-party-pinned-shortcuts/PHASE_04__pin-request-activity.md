# Phase 04 - The invisible pin-request host

**Strategic spec:** [`../S1205_launcher-host-third-party-pinned-shortcuts.md`](../S1205_launcher-host-third-party-pinned-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Register the activity Android delivers `ACTION_CONFIRM_PIN_SHORTCUT` to, and make it accept the request, show one Toast and finish without ever drawing.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1205 phase 04"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/pin/LauncherPinRequestActivity.kt` | New | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherPinRequestManager.kt` | New | ≤ 90 |
| `app_v2/src/launcherEnabled/AndroidManifest.xml` | Modified | ≤ 60 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> **Flavor placement.** Both files are launcher-only and belong under `src/launcherEnabled/`, the source set `standard` and `noLegal` mount and whose manifest is injected by `addStaticManifestFile` in `app_v2/build.gradle.kts`. No other flavor compiles them, so no `BuildConfig.IS_*` guard is needed or allowed. No layout file is added, so there is no `res/layout-land` counterpart to mirror.

---

## Steps

### Step 04.1 - Write LauncherPinRequestActivity

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/pin/LauncherPinRequestActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `@AndroidEntryPoint class LauncherPinRequestActivity : AppCompatActivity()`, injecting `AppShortcutDataSource` and `AcceptPinnedShortcutUseCase`. Set no content view. In `onCreate`, read the request via `pinRequestFrom(intent)`; when it is null, finish immediately and post nothing. Otherwise derive the orientation from `resources.configuration.orientation` exactly as `HomeWidgetSettingsHelper` does, run the use case on `lifecycleScope` with `System.currentTimeMillis()`, then show one `Toast.makeText` with `R.string.launcher_widget_placed` on true or `R.string.launcher_widget_no_room` on false, and `finish()`. Use the application context for the Toast so it survives the activity ending, and add no dialog, no layout and no confirmation of any kind.

**Why:**

Strategic §4 decision 4 rules that the request is accepted silently with a notice and that a confirmation dialog is rejected, and §4's implementation note states verbatim that the activity nevertheless has to exist because `LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT` is delivered to the launcher as an activity - it simply draws no dialog, places the cell and finishes at once, leaving only the notice behind; the same note requires the message to report a refusal when the desktop could not take the cell.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/pin/LauncherPinRequestActivity.kt` exists.
- `Grep` - `class LauncherPinRequestActivity` matches exactly once in that file.
- `Grep` - `setContentView` returns zero hits in that file.
- `Grep` - `launcher_widget_placed` and `launcher_widget_no_room` each present in that file.
- `Grep -n "Log\.d\("` over that file returns zero hits.

**Status:** `[x]` done

---

### Step 04.2 - Register the activity for the pin action

**Files:** `app_v2/src/launcherEnabled/AndroidManifest.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Declare the activity with `android:exported="true"`, `android:theme="@style/Theme.FastMediaSorter.Transparent"`, `android:excludeFromRecents="true"`, `android:noHistory="true"` and `android:taskAffinity=""`, carrying one intent filter on action `android.content.pm.action.CONFIRM_PIN_SHORTCUT` with `android.intent.category.DEFAULT`. Leave it enabled - unlike the HOME activity above it, this component is inert until the app actually holds the home role. Extend the file's header comment with one sentence saying so.

**Why:**

Strategic §1 states the request the system addresses to the default launcher currently reaches nothing in this app, which is why pressing "add to home screen" in a foreign app produces no result; `LauncherRoleManager` toggles only the HOME component, so leaving this one enabled changes nothing until the role is held.

**Verification:**

- `Grep` - `<action android:name="android.content.pm.action.CONFIRM_PIN_SHORTCUT"` matches exactly once in `app_v2/src/launcherEnabled/AndroidManifest.xml`. (Corrected during implementation: the bare token also appears in the header comment the same step adds, so the predicate anchors on the declaration.)
- `Grep` - `LauncherPinRequestActivity` matches exactly once in that file.
- `Grep` - `android:enabled="false"` still matches exactly once in that file (the HOME activity only).
- `.\a.ps1 fr` - expected exit 0; record `expected: 0 | actual: <code>`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-06 - Step 04.1 PASS. File exists, `class LauncherPinRequestActivity` = 1 hit, `setContentView` = 0 hits, both message strings present, `Log.d(` = 0 hits.
- 2026-08-06 - Step 04.2 PASS. Action declaration = 1 hit, `LauncherPinRequestActivity` = 1 hit, `android:enabled="false"` still = 1 hit (the HOME activity only). Predicate corrected in place: the bare `CONFIRM_PIN_SHORTCUT` token matches twice because the same step adds it to the header comment, so the predicate now anchors on the `<action android:name=..>` line.
- 2026-08-06 - `.\a.ps1 fr` - expected: 0 | actual: 0. Every task reported UP-TO-DATE, so the verdict was confirmed against real evidence rather than the task skip: the merged manifest at `app_v2/build/intermediates/merged_manifest/standardDebug/processStandardDebugMainManifest/AndroidManifest.xml` carries the activity at L359 and its `CONFIRM_PIN_SHORTCUT` filter at L366.
- 2026-08-06 - `.\a.ps1 fkn` - expected: 0 | actual: 0. noLegal mounts the same `launcherEnabled` source set, so the second of the two launcher flavors compiles the new activity too.
- 2026-08-06 - Four S1205 probe tags inserted before this phase's build, one per changed flow entry: request arrival (this activity), accept-and-place (`AcceptPinnedShortcutUseCase`), cell resolution (`ResolveLauncherCommandLabelUseCase`), launch (`ExecuteLauncherCommandUseCase`).
- 2026-08-06 - UI-phase gate (S1338): placement decision recorded - strategic §3.3 "Приём запроса: молча, с уведомлением; диалога подтверждения не будет" plus §4's note that the activity exists but draws nothing. Screenshot deferred (no device attached this session), and this activity renders no screen to capture - the visible outcome is a Toast plus a new desktop cell, which is what the device test in `BlockNeedUserTest` covers.
- 2026-08-06 - Closure FAIL then fixed: the `activity-logic` gate refused the first shape, where the activity `@Inject`ed `AppShortcutDataSource` and `AcceptPinnedShortcutUseCase` directly (CLAUDE.md Rule 3 - an Activity is a host, not a place for domain wiring). Extracted `LauncherPinRequestManager` into `ui/launcher/helpers/`, beside the other launcher managers, returning a three-value `LauncherPinRequestOutcome`; the activity now injects only that manager and maps the outcome to a string. No ViewModel: the screen holds no state and ends inside its first coroutine.
- 2026-08-06 - Phase-boundary audit: Layers 1-3. No listener registered, so nothing for `assert-listener-symmetry` to pair. The one coroutine is `lifecycleScope.launch`, bound to the activity, and the activity finishes inside it; the Toast deliberately uses the application context so it outlives that scope. No Room surface.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly), on `standard debug` and `noLegal debug`, the two flavors that mount this source set.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The feature is complete end to end from here; only device verification and bookkeeping remain. The flow cannot be exercised on an emulator without a third-party app that publishes a pin request, so this ticket ends in `BlockNeedUserTest`.

---

## Rollback Plan

Revert phase commit(s). Cells already written by an accepted request survive the revert and render through Phase 02's resolver; they stop being creatable, not readable.
