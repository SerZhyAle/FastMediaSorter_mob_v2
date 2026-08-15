# Phase 02 - Launcher status presentation

**Strategic spec:** [`../S1087_system-status-area-replace-option.md`](../S1087_system-status-area-replace-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-07-30
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3

## Objective

Expose the setting and make the launcher show exactly one status area while restoring system bars on exit.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/dialog_launcher_settings.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 1500 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 500 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt` | Modified | ≤ 500 |

## Steps

### Step 02.1 - Add the localized launcher setting row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/LauncherSettingsDialogFragment.kt`, `app_v2/src/main/res/layout/dialog_launcher_settings.xml`, `app_v2/src/main/res/layout-land/dialog_launcher_settings.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`

**Depends on:** Step 01.2

**Prompt for developer:**

> Add a checked setting row labelled “Replace system status area” to the launcher settings dialog and bind it to `launcherReplaceSystemStatusArea`. Add EN/RU/UK strings with `set-android-string.ps1`; use the same row placement in portrait and landscape. Verify the copy against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Grep` - the new setting key appears in all three locale files.
- `Grep` - the row id appears in both dialog layouts and the fragment.
- `Bash` - `scripts/check_strings_localized.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-30 - Verification 3/3 PASS. Key `launcher_settings_replace_status_area_title` present in en/ru/uk (1/1/1); `rowLauncherReplaceStatusArea` in both dialog layouts and the fragment; `check_strings_localized.ps1 -KeyPrefix launcher_settings_` exit 0 (23 keys, no gaps). Row placed next to the tray toggle in both orientations. Wording taken verbatim from the owner's Quiz decision (2026-07-18). Files: LauncherSettingsDialogFragment.kt, layout/ + layout-land/dialog_launcher_settings.xml, values/values-ru/values-uk strings.xml. Dev log recorded.

### Step 02.2 - Map the preference to launcher presentation state

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt`

**Depends on:** Step 02.1

**Prompt for developer:**

> Extend the launcher UI state with the persisted replacement preference. When false, remove duplicate clock/network/battery content from the FMS tray; when true, preserve the existing FMS tray content. Keep recents and pinned controls independent from this policy.

**Verification:**

- `Grep` - `launcherReplaceSystemStatusArea` occurs in the ViewModel.
- `Grep` - tray rendering has an explicit status-content predicate.
- `Grep -n "Log\.d\("` - zero hits in modified Kotlin files.

**Status:** `[x]` done

**Step Log:**

- 2026-07-30 - Verification 3/3 PASS. `launcherReplaceSystemStatusArea` exposed as a `StateFlow` on the ViewModel (mirrors the `desktopLocked` pattern); `LauncherTrayManager` gained `bind(Flow<Boolean>)` + `applyStatusContent()` as the explicit status-content predicate (5 hits); `Log.d(` 0 hits. Hiding the content also drops the battery receiver and network callback instead of feeding invisible views; `onStart` re-registers only while the content is visible, `onStop` still unregisters unconditionally so listener symmetry holds. Recents and pinned strips untouched. Files: LauncherHomeViewModel.kt (+12 LOC), helpers/LauncherTrayManager.kt (+30 LOC). Dev log recorded.

### Step 02.3 - Apply and restore only the launcher status bar

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`

**Depends on:** Step 02.2

**Prompt for developer:**

> Observe the launcher presentation state and use WindowInsetsCompat to hide only `statusBars()` when replacement is true. Show `statusBars()` whenever it becomes false and in the symmetric lifecycle exit path. Reapply safe insets after each change; do not hide navigation bars.

**Verification:**

- `Grep` - both `hide(WindowInsetsCompat.Type.statusBars())` and `show(WindowInsetsCompat.Type.statusBars())` occur in the activity.
- `Grep` - no navigation-bar hide is added in the activity.
- `Bash` - `./a.ps1 fc` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-30 - Verification 3/3 PASS. `hide(WindowInsetsCompat.Type.statusBars())` 1 hit, `show(...)` 2 hits (policy + symmetric `onStop`), `navigationBars` 0 hits, `a.ps1 fc` exit 0 (BUILD SUCCESSFUL, 48s). Confirmed the check is meaningful: `src/launcherEnabled/java` is mounted by the `standard` flavor (build.gradle.kts:603), so `compileStandardDebugKotlin` really compiled these files. Policy lives in `applyStatusBarPolicy()`; safe-area padding re-applied after each change. Backup of the 587-LOC activity taken to `temp/S1087/` before editing (Rule 5). Files: LauncherHomeActivity.kt (+24 LOC). Dev log recorded.

## Phase-boundary audit (2026-07-30)

Scope: this phase's Files Touched. Layers 1-3 (no Room surface here).

- **AUDIT-FIX P1 - inset padding compounded on every toggle.** `applySystemBarInsetPadding` captures the
  view's current padding as its base and adds insets on top, so calling it again from the policy would
  have treated the already-inset padding as the base: 2x the top inset after the first emission, 3x
  after the next toggle. Fixed by requesting a fresh dispatch (`ViewCompat.requestApplyInsets`) so the
  listener installed once in `setupViews` recomputes from the original base. The constraint is now
  stated in the extension's KDoc - the next caller would have hit the same trap.
- **AUDIT-FIX P1 - hiding the bar would have looked like a no-op.** `getStatusBarHeightSafe` falls back
  to the platform `status_bar_height` resource when the type inset reads 0, which is exactly the state
  after hiding the bar: the launcher would keep a top band the height of the bar it just removed, and
  strategic criterion 3 would fail on device. Added `useStatusBarHeightFallback` (default `true`, every
  existing caller unchanged - all pass named arguments) and passed `false` from the launcher root, the
  one surface that controls the bar itself.
- **AUDIT-P2 - empty tray gap in one combination.** `trayContainer` carries its own
  `paddingStart`/`paddingEnd`, so with "show tray" ON and "replace status area" OFF the container keeps
  two spacings of empty width after its four children go. Not fixed here on purpose: the container's
  visibility is owned by `LauncherTaskbarManager` (`showTray`), and writing it from the tray manager too
  would give one view two owners. The single-owner fix is to fold `replaceSystemStatusArea` into
  `LauncherTaskbarComposition` so the container reads `showTray && replaceSystemStatusArea`; that file
  is outside this phase's Files Touched. Flagged for the device test.
- No P0 findings. Listener symmetry holds: `onStart`/`onStop` stay the registration edges, and the
  policy-driven register/unregister pair is symmetric on its own.

### Gate state at phase close (stated, not glossed)

- `.\a.ps1 fg` PASS - every fast gate green, including `assert-no-ticket-logs` (0 stale probes) and
  `assert-layout-variant-id-parity` (the new row exists in both orientations). `listener-symmetry`
  moved to 132 against a baseline of 133.
- Diff-scoped detekt over the five touched files: **one finding left**, `LongParameterList 11/10` on
  `LauncherHomeViewModel`'s constructor. It predates this ticket - the baseline holds **zero** entries
  for that file, so whoever last grew the constructor left it unfrozen - and cutting it is a DI
  redesign of a 405-line class this ticket only reads. Parked as **S1314** with the measurements. The
  two mechanical findings in the same file (unused `onEach` import, misordered
  `ExecuteScheduledOperationUseCase`) were fixed here, because a file this ticket touches must not be
  left dirtier than it was found.
- Re-freezing the baseline was rejected on purpose: `:app_v2:detektBaseline` freezes the whole project
  from a dirty working tree and would swallow other in-flight tickets' new findings.

## Phase Done Criteria

- [x] Every Step 02.* is `[x]` done.
- [x] Project compiles - `.\a.ps1 d` BUILD SUCCESSFUL (assembleStandardDebug, APK produced), re-verified
      after the audit fixes with `.\a.ps1 fc` exit 0.
- [x] Portrait and landscape launcher layouts respect systemBars and displayCutout - the launcher root
      applies the safe-area padding once and recomputes it on every bar change; the new dialog row sits
      inside the existing scrolling container in both orientations. Visual proof belongs to strategic
      criterion 6 and is part of the device test.
- [x] Phase-boundary audit has no unresolved P0/P1 findings - both P1s fixed above; one P2 recorded.
