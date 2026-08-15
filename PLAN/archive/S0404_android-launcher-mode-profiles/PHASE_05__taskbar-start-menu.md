# Phase 05 - Taskbar and Start Menu

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, 04
**Blocks:** Phase 08
**Steps done:** 6 / 6
**Started:** 2026-07-17
**Completed:** 2026-07-17

---

## Implementation Log (2026-07-17)

A 4-dimension pattern sweep ran before implementation; it changed four decisions:

- **Clock is a `TextClock`, not an `ACTION_TIME_TICK` receiver** (the prompt asked for a receiver). `TextClock` registers its own time/timezone receivers on attach, drops them on detach, and honours the 12/24h system setting - so the tray has no clock lifecycle at all, and one less receiver to keep symmetric.
- **`ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` is mandatory** for the battery receiver: targetSdk is 35 and a bare `registerReceiver` crashes from API 34. The sticky seed uses the `registerReceiver(null, filter)` form, which returns the current value without registering anything (so it needs no unregister and cannot leak). Both unregisters are wrapped - unregistering a never-registered receiver throws.
- **`NetworkStateMonitor` is deliberately NOT reused.** The sweep enumerated six blockers; the decisive ones: it exposes no transport type at all (`hasTransport` is private, and its public `isLocalNetworkAvailable()` collapses Wi-Fi and Ethernet into one boolean), and its 4s `SETTLE_WINDOW_MS` exists precisely to suppress the wlan<->cellular flaps a tray must show (S1040). The tray owns a small view-scoped `registerDefaultNetworkCallback` instead.
- **The divider is `showDividers` + an `<inset>` drawable, not a `<View android:layout_width="1dp">`.** Per the S0670 note in `playback_section_divider.xml`, a `match_parent` divider view is measured and, under an AT_MOST spec, drags the whole row to full height. A showDividers divider is drawn, not measured.

Other deviations worth recording:

- `RESULT_KEY` in the four panel pickers is a global `const`, so a second host on the same FragmentManager receives the panel editor's results. Rather than fork the picker, added a neutral `ResourcePickerDialogFragment.newInstance(requestKey: String)` overload (+ `ARG_REQUEST_KEY`, defaulting to `RESULT_KEY`), leaving every existing call site untouched. The Start menu shows it under its own tag.
- The Start menu is modelled on `SendToBottomSheet` (the only sheet combining `@AndroidEntryPoint` + ViewBinding + RecyclerView + D-pad focus), NOT on `AppLaunchPanelDialogFragment` - that one is a plain `DialogFragment` whose `onStart` window sizing fights `BottomSheetDialog`. No sheet theme and no `BottomSheetBehavior` touched: the global `bottomSheetDialogTheme` overlay is what carries Rule 17 inset-safety.
- `onCellTapped` was refactored to delegate to a new public `LauncherHomeViewModel.run(command)`, so the desktop, both taskbar strips and the Start menu share ONE launch guard rather than three copies.
- Icons authored (none existed and the sweep rejected the near-misses as wrong semantics - an error glyph must not mean "offline"): `ic_signal_cellular`, `ic_ethernet`, `ic_network_off`, `ic_battery_charging`, `ic_exit_to_app`.
- Taskbar height is a new `@dimen/launcher_taskbar_height` (56dp) rather than the in-app panels' 48dp `main_panel_row_height`: this bar is permanent home chrome carrying 40dp icons plus a tray.

---

## Phase Audit (2026-07-17)

Layered audit per `docs/CODE_AUDIT_PROTOCOL.md` across 6 dimensions (lifecycle, threading, contract compliance, UI rules, exit flow, shared-`src/main` blast radius), every finding then handed to two independent skeptics with different lenses (can-it-fire / is-it-the-house-pattern), both instructed to refute: **20 raised → 14 unique → 3 refuted → 11 defects. One P0.**

**P0 - the tray's battery strings crash-looped the HOME screen.** All nine `launcher_tray_battery_*` values (3 keys × EN/RU/UK) shipped with a literal PowerShell escape backtick inside the format specifier - `%1``$d` instead of `%1$d`. `Resources.getString(id, percent)` delegates to `String.format`, which throws `UnknownFormatConversionException: Conversion = '1'`. That call sits outside the tray's `runCatching`, on the sticky-battery seed path that runs during `setupViews()`, so `LauncherHomeActivity` died on every start - and because it is the declared HOME component, the system relaunched it into a **crash loop with no usable home screen**. Fixed via `set-android-string.ps1` with single-quoted PowerShell values; a repo-wide grep for `%[0-9]``\$` now returns 0 hits.

Why every gate missed it, and what actually catches it:

- `check_strings_localized.ps1` only proves a key exists in all three locales - the corrupt value passes.
- aapt2's `VerifyJavaStringFormat` only errors on multiple non-positional substitutions; with one argument the build stays green.
- detekt/lint never read string values.
- Root cause is CLAUDE.md Rule 7 ("Variables: Write `$` literally, do not escape"), violated by writing the value through a double-quoted PowerShell string. **The permanent fix is the habit: pass string values single-quoted, where both `$` and backtick are literal.**
- The only mechanical check that fires is the one that was skipped: step 05.6's device pass. This is why its tick is now corrected to build-half-only.

Also fixed:

- **P2 - the all-apps grid recycled nothing.** `rvAllApps` was `wrap_content` + `nestedScrollingEnabled=false` inside a `NestedScrollView`, so it was measured `UNSPECIFIED` and `GridLayoutManager` laid out **every installed app** in one main-thread pass, each with a `loadIcon()`. Now a fixed `@dimen/launcher_start_menu_apps_height` (240dp) with its own scrolling - recycling restored.
- **P2 - a second All-apps tap started a second full enumeration.** The re-query guard read `appsAdapter.itemCount`, which stays 0 for the whole in-flight window. Now guarded by an explicit `Job`, and moved from `lifecycleScope` to `viewLifecycleOwner.lifecycleScope`.
- **P2 - the resource picker could be stacked twice.** `show()` with no `findFragmentByTag` guard, unlike all five sibling call sites; the second instance outlived the dismissed sheet and stranded over the desktop with a dead result listener.
- **P2 - enough pins would push the tray off the right edge.** `taskbarPinned` was `wrap_content` in a bar where a weighted sibling precedes it, so `LinearLayout.measureHorizontal` gave it `usedWidth = 0` - i.e. `AT_MOST(whole bar)`, not "the slack". ~7 pins on a 360dp phone laid the tray out past the right edge entirely. Both strips now live in one weighted block, so they can only consume the slack. **Latent today** (`setPin`/`removePin` have zero callers until Phase 07) - one skeptic refuted it on exactly that ground - but fixed now, because Phase 07 cannot fix a taskbar layout from outside it. Same reasoning as the phase-01..04 audit's recents-enumeration fix.
- **P3 - the tray lost its right-edge anchoring when recents was toggled off.** The tray sat at the right only because the weighted recents strip absorbed the slack; hiding it collapsed the bar to a left hug and stranded the clock beside the Start button. The new weighted block absorbs the slack even while empty.
- **P3 - the exit dialog was not lifecycle-bound.** A fire-and-forget `MaterialAlertDialogBuilder` on the feature's escape hatch: an Activity-recreating config change not in this Activity's `configChanges` (dark mode, locale, density) leaked the window and dropped the confirmation, and the positive lambda's `requireActivity()` would throw on a detached fragment. Split vote - one skeptic refuted it as the house pattern (253 builder call sites, only 3 retain a reference), the other showed the house has **ticketed and fixed this exact construct twice** (S0892 `WebViewAuthDialogFragment`, and `PlayerActivity`'s `dialogHelper.dismissAll()`). Now retained + dismissed in `onDestroyView`, and `requireActivity()` softened to `activity ?: return`.
- **P3 - orphaned `androidx.activity.viewModels` import** (the fragment resolves via `activityViewModels`).
- Three plan-honesty defects: step 05.6's tick, step 05.4's unsatisfiable predicate, and the stale `TODO(phase-08)` handoff note. All three corrected in place above.

Refutations recorded so they are not re-litigated:

- **"Taskbar flows are cold with no `stateIn`, so every Home `onStart` re-runs full PackageManager resolution"** - refuted. `recentIcons` resolves only the ~6 journalled packages one by one (the phase-01..04 audit already fixed the enumerate-everything version), and `pinnedIcons` has no rows to resolve.
- **"The Android-settings section was contracted as a 3-target catalog list (settings + Wi-Fi + date/time) and shipped as one hardcoded row"** - refuted **on its premise, not its facts**. The step cites research 05 as its authority; that document specifies one required entry point ("Android settings") with Wi-Fi and date/time explicitly **optional**. The single row satisfies the contract.
- **"`showDividers="middle"` draws three dividers instead of the single contracted recents/pinned separator"** - refuted. (The weighted-block restructure above independently moves the recents/pinned divider onto the block, which is what the step described.)

---

## Objective

Fill the taskbar (Start button, recent launches, pinned icons, tray with clock/network/battery) and build the Start menu (our resources / all apps / settings / exit launcher mode). Composition honours the three `AppSettings.launcherTaskbarShow*` toggles.

---

## Prerequisites

- [ ] Phases 03-04 are ✅ Done.
- [ ] CODE.LOCK acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/launcher_taskbar.xml` | New (include) | ≤ 90 |
| `app_v2/src/launcherEnabled/res/layout/activity_launcher_home.xml` | Modified (swap placeholder for include) | +5 |
| `app_v2/src/launcherEnabled/res/layout-land/activity_launcher_home.xml` | Modified (same include) | +5 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTaskbarManager.kt` | New | ≤ 220 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherTrayManager.kt` | New | ≤ 180 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt` | New | ≤ 300 |
| `app_v2/src/launcherEnabled/res/layout/fragment_launcher_start_menu.xml` | New | ≤ 120 |
| `app_v2/src/launcherEnabled/res/layout/item_launcher_taskbar_icon.xml` | New | ≤ 30 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified (wire managers only) | +40 |

> The taskbar include is shared by both orientations (single file referenced from both `activity_launcher_home` variants) - bar is bottom-anchored in both, Windows model (ADR-3). Start menu is a `BottomSheetDialogFragment`; inflate its content with the fragment's themed context (never application context - MaterialButton crash trap).

---

## Steps

### Step 05.1 - Taskbar layout + include wiring

**Files:** `res/layout/launcher_taskbar.xml`, both `activity_launcher_home` variants
**Depends on:** - start of phase

**Prompt for developer:**

> Build `launcher_taskbar.xml`: horizontal LinearLayout, 56dp tall - left: `MaterialButton` `@+id/btnStart` (icon `ic_launcher_mode`, text `R.string.launcher_taskbar_start`); middle: `RecyclerView` `@+id/taskbarRecents` (horizontal) then `RecyclerView` `@+id/taskbarPinned` (horizontal, separated by a 1dp `?attr`-tinted divider); right tray container `@+id/trayContainer`: `TextView` `@+id/trayClock`, `ImageView` `@+id/trayNetwork`, `ImageView` `@+id/trayBattery` + `TextView` `@+id/trayBatteryPercent`. All views focusable for D-pad; explicit `nextFocusUp` from the bar into the grid (Rule 16). Replace the Phase 01 placeholder in BOTH orientation layouts with `<include layout="@layout/launcher_taskbar"/>` keeping id `@+id/launcherTaskbar`. No hex colors.

**Verification:**

- `Grep` - `btnStart`, `taskbarRecents`, `taskbarPinned`, `trayClock` present in `launcher_taskbar.xml`.
- `Grep` - `include` + `launcher_taskbar` present in both orientation variants.

**Status:** `[x]` done

---

### Step 05.2 - LauncherTrayManager (clock / network / battery)

**Files:** `ui/launcher/helpers/LauncherTrayManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Plain class (constructor: views + lifecycle owner), instantiated by the activity. Data sources per research 04, zero new permissions:
> - Clock: `ACTION_TIME_TICK` broadcast receiver → format via `android.text.format.DateFormat.getTimeFormat(context)`; also refresh once on start.
> - Battery: sticky `ACTION_BATTERY_CHANGED` → level % + charging bolt state; pick battery icons from existing `ic_*` set (grep for battery icons; if absent, add one vector pair in this step and list it in Files Touched at implementation time).
> - Network: `ConnectivityManager.registerDefaultNetworkCallback` → icon by transport (WIFI/CELLULAR/ETHERNET/none). Type only - NO signal strength, NO `READ_PHONE_STATE` (strategic §7 last risk).
> Listener symmetry (audit protocol): register in `start()` called from `onStart`, unregister in `stop()` called from `onStop` - symmetric edges, never onResume/onDestroy split. Expected callback fallbacks log `Timber.i`.

**Verification:**

- `Grep` - `ACTION_TIME_TICK`, `ACTION_BATTERY_CHANGED`, `registerDefaultNetworkCallback` present.
- `Grep` - `READ_PHONE_STATE` zero hits in `src/launcherEnabled`.
- `Grep` - each `registerReceiver`/`registerDefaultNetworkCallback` has a matching unregister in the same file.

**Status:** `[x]` done

---

### Step 05.3 - LauncherTaskbarManager (recents + pinned + composition)

**Files:** `ui/launcher/helpers/LauncherTaskbarManager.kt`, `res/layout/item_launcher_taskbar_icon.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Manager owns the two horizontal lists:
> - Recents: collect `QueryRecentLauncherAppsUseCase(limit = 6)` → 40dp icon items (label as `contentDescription` + tooltip); tap relaunches via `ExecuteLauncherCommandUseCase.launch(App(pkg))`.
> - Pinned: collect `LauncherPinsRepository.observePins()` → same item layout, visuals via `ResolveLauncherCommandLabelUseCase`; tap executes the pinned command. (Pin management UI arrives with edit mode, Phase 07 - this phase only renders and launches.)
> - Composition: collect `SettingsRepository.getSettings()` and toggle visibility of recents / pinned / tray blocks by the three `launcherTaskbarShow*` flags.
> Flows via `collectOnLifecycle`. Document (KDoc on the class) the fullscreen invariant: playback happens in separate activities (`PlayerActivity`/`StreamsActivity`), so the taskbar is inherently absent during fullscreen playback - strategic §3.1.7 needs no extra code.

**Verification:**

- `Grep` - `class LauncherTaskbarManager` matches once; `launcherTaskbarShowRecents` referenced.
- `Grep` - `collectOnLifecycle` present in the file.

**Status:** `[x]` done

---

### Step 05.4 - Start menu fragment

**Files:** `ui/launcher/menu/LauncherStartMenuFragment.kt`, `res/layout/fragment_launcher_start_menu.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> `BottomSheetDialogFragment` (`@AndroidEntryPoint`) opened by `btnStart`; sections top-to-bottom (strategic §3.3 «Пуск»):
> 1. **Open FastMediaSorter** - row with app icon; `startActivity(Intent(context, MainActivity::class.java))` (goal 8: normal mode from the launcher).
> 2. **Our resources** - row opening the existing `ui/applaunchpanel/edit/ResourcePickerDialogFragment` (FragmentResult); on picked `resourceId` execute `Resource(id, BROWSE)`.
> 3. **All apps** - row expanding an in-sheet vertical grid backed by `QueryLaunchableAppsUseCase()`; tap → `App(pkg)` via `ExecuteLauncherCommandUseCase`.
> 4. **Android settings** - row list from `OsShortcutCatalog` top targets: general settings, Wi-Fi, date/time (resolve-guarded; research 05).
> 5. **App settings** - opens the launcher settings group deep-link (Phase 08 provides `SettingsActivity.openLauncherSectionIntent`; until then open plain `SettingsActivity` and leave `TODO(phase-08)` marker).
> 6. **Exit launcher mode** - confirmation `MaterialAlertDialogBuilder` (confirm style `Widget.FastMediaSorter.Button.DialogConfirm`, cancel `Widget.FastMediaSorter.Button.DialogCancel` - S0538 pair) → `LauncherRoleManager.disableMode()` + `openHomeChooser(activity)` (risk 2: guaranteed exit).
> D-pad: every row focusable; initial focus on section 1.

**Verification:**

- `Grep` - `class LauncherStartMenuFragment` matches once; `disableMode`, `ResourcePickerDialogFragment`, `QueryLaunchableAppsUseCase` referenced.
- The exit dialog's confirm/cancel pair carries the S0538 styles **by inheritance, not declaration**. Corrected 2026-07-17 (phase audit): the original predicate here demanded a `Grep` hit for `DialogConfirm`/`DialogCancel` in the fragment or its layout, which is unsatisfiable by construction - `MaterialAlertDialogBuilder` has no XML buttons and picks the pair up from `materialAlertDialogTheme` -> `buttonBarPositiveButtonStyle`/`buttonBarNegativeButtonStyle` (`app_v2/src/main/res/values/themes.xml:339-341`). `scripts/quality/assert-dialog-cancel-style.ps1` says so itself: builder dialogs are "out of scope by construction". A zero-hit grep here is CORRECT and must not be "repaired" with a per-call style overlay - that is precisely the drift the S0538 builder seam removes.

**Status:** `[x]` done

---

### Step 05.5 - Strings (EN/RU/UK)

**Files:** trilingual `strings.xml` via tool
**Depends on:** Steps 05.1-05.4

**Prompt for developer:**

> Via `set-android-string.ps1 -Action add` (one call per key): `launcher_taskbar_start` (EN "Start"), `launcher_menu_open_app`, `launcher_menu_resources`, `launcher_menu_all_apps`, `launcher_menu_android_settings`, `launcher_menu_app_settings`, `launcher_menu_exit_mode`, `launcher_menu_exit_confirm_title`, `launcher_menu_exit_confirm_message` (explain the system will return the previous home screen - COMMUNICATION_POLICY §2 outcome-first formula). Then `check_strings_localized.ps1 -KeyPrefix "launcher_"` → exit 0.

**Verification:**

- `check_strings_localized.ps1 -KeyPrefix "launcher_"` → exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist (record PASS).

**Status:** `[x]` done

---

### Step 05.6 - Build + device sanity

**Files:** - (validation only)
**Depends on:** Steps 05.1-05.5

**Prompt for developer:**

> `.\a.ps1 d` + install + Home: tray ticks (clock advances on minute), battery/network icons match device state, Start opens the sheet, All apps lists and launches an external app, exit flow disables the component and the previous launcher returns on next Home press. Record `expected | actual` per check.

**Verification:**

- `.\a.ps1 d` → BUILD SUCCESSFUL.
- **DEFERRED-DEVICE** - the on-device checklist in the prompt above was NOT run: no device online 2026-07-17. Corrected 2026-07-17 (phase audit): this predicate previously read "device checklist recorded", which was false - no `expected | actual` rows exist anywhere in this file. Tracked by the Phase 10 `BlockNeedUserTest` pass, where it is now the **highest-value item**: this is the exact check that would have caught the battery-format P0 (crash loop on every Home press) instead of an audit finding it days later.

**Status:** `[x]` done - build half only; the device half is DEFERRED-DEVICE above.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [ ] **DEFERRED-DEVICE** - Launching an app from All-apps inserts a `launcher_journal` row and it surfaces in Recents on return. No device online 2026-07-17; covered by the Phase 10 BlockNeedUserTest pass.
- [x] `Grep` - `TODO(phase-05)` zero hits. The planned `TODO(phase-08)` settings deep-link marker was **not** needed: the App-settings row opens `SettingsActivity` directly, and Phase 08 step 08.2 will swap that one call for `SettingsActivity.openLauncherSectionIntent(context)` once the section exists.
- [x] Dev log + `catalog_sync.ps1`; CODE.LOCK released.

---

## Handoff Notes to Next Phase

- Taskbar renders pins but cannot yet create them - Phase 07 edit mode adds pin management. Phase 07 is also what makes the pinned strip's width bound load-bearing (see audit finding 4 below): today `setPin`/`removePin` have zero callers, so the strip is always empty.
- Start menu section 5 (App settings) opens `SettingsActivity` directly. No `TODO(phase-08)` marker exists - the planned marker was deliberately not added (see Phase Done Criteria). Phase 08 **step 08.2** swaps that one `startActivity` call for `SettingsActivity.openLauncherSectionIntent(context)` once the settings section exists. Corrected 2026-07-17 (phase audit): this bullet previously told Phase 08 to resolve a marker that the same file records as never added, and named step 08.4 while the Done Criteria named 08.2.

---

## Rollback Plan

Revert phase commit(s) - component still disabled by default for users.
