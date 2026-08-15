# Phase 07 - Monitor shell and navigation

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 08, Phase 09

---

## Objective

Ship the Activity, the Summary screen of status tiles, and the navigation contract that lets an external caller open one section directly.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [x] UI placement decided - strategic §3.1.2 records the `/ui-clarify` answers for the Summary layout, the landscape grid, the toggle row and the chart.
- [x] `temp/CODE.LOCK` acquired before the first source edit (queued at position 2, waited via `wait-for-lock-turn.ps1`, released before the builds).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/NetworkMonitorActivity.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/NetworkMonitorSection.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/summary/NetworkMonitorSummaryFragment.kt` | New | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/summary/NetworkMonitorSummaryViewModel.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/networkmonitor/helpers/NetworkMonitorSectionNavigator.kt` | New | ≤ 200 |
| `app_v2/src/main/res/layout/activity_network_monitor.xml` | New | ≤ 80 |
| `app_v2/src/main/res/layout/fragment_network_monitor_summary.xml` | New | ≤ 200 |
| `app_v2/src/main/res/layout-land/fragment_network_monitor_summary.xml` | New | ≤ 200 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values/strings.xml` + `values-ru` + `values-uk` | Modified | ≤ 60 |
| `app_v2/src/main/res/values/themes.xml` | Modified | ≤ 45 |
| `app_v2/src/main/res/values/dimens.xml` | Modified | ≤ 10 |

> The last two rows were added after the fact. The tile grid needs shared styles and one dimension, and the
> alternative the plan implied - repeating the attributes per tile in both orientation layouts - is what pushes
> a layout past its budget and lets the two orientations drift apart at the first change.

---

## Steps

### Step 07.1 - Declare the section enum and the launch contract

**Files:** `ui/networkmonitor/NetworkMonitorSection.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Declare `NetworkMonitorSection` with the cases `Summary`, `Wifi`, `Mobile`, `Bluetooth`, `Gnss`, `Internet` and `History`, each carrying a stable string key. Add the intent-extra name and the two helper functions that write a section into a launch intent and read it back, defaulting to `Summary` when the extra is absent or unknown.

**Why:**

Strategic §11 criterion 12 requires an external entry point to open the Wi-Fi, Bluetooth or SIM subscreen directly, and a stable key is what lets the launcher, a shortcut and later S1440's widgets address a section without depending on screen order.

**Verification:**

- `Grep` - all seven cases present.
- `Grep` - the default-to-`Summary` branch present in the reader.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. All seven cases matched as declarations at lines 13-19 (`Summary("summary")` .. `History("history")`), not as prose; the reader's fallback is `entries.firstOrNull { it.key == key } ?: Summary` at line 34. File: `ui/networkmonitor/NetworkMonitorSection.kt` (new, 44 LOC). The launch helpers are `Intent` extension functions rather than companion methods, because both ends of the contract are an `Intent` and the call site then reads `intent.readNetworkMonitorSection()` at the point the extra is consumed. `EXTRA_SECTION` is public: Phase 09's route catalog and S1440's widgets address a section from outside this package, and a private extra would force each of them to duplicate the literal.

---

### Step 07.2 - Add the Activity and its manifest entry

**Files:** `ui/networkmonitor/NetworkMonitorActivity.kt`, `res/layout/activity_network_monitor.xml`, `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 07.1

**Prompt for developer:**

> Add a Hilt-annotated Activity hosting a fragment container, declared in the manifest with the same theme and parent-activity attributes as the calculator entry. Route to the section from the launch intent on first creation only, so a rotation does not re-navigate. Delegate every decision to the ViewModel and the navigator - the Activity itself holds no business logic. Show the "not enabled" fallback view when `AppSettings.enableNetworkMonitor` is false or `NetworkMonitorContract.isAvailableInBuild` is false, mirroring `CalculatorActivity`.

**Plan correction, 2026-08-09 (before implementation):** the step named `CapabilityAvailability.isNetworkMonitorAvailable()`, which does not exist. Phase 01 step 01.2 deliberately did *not* extend `CapabilityAvailability` - its own rationale calls that class "frozen debt" because the `flavor-flags` ratchet refuses to raise its baseline - and shipped `domain/networkmonitor/NetworkMonitorContract.isAvailableInBuild` with flavor source sets instead. Phase 01's Handoff Notes still carry the stale sentence. The `configChanges` attribute also deviates from the calculator entry: see step 07.2's Step Log.

**Why:**

Strategic §11 criterion 17 requires the screen to be unreachable in the four excluded flavors, and the calculator's own re-check on entry is the existing defence against a stale panel tile or shortcut that outlived its setting; CLAUDE.md Rule 3 forbids logic in the Activity.

**Verification:**

- `Grep` - `NetworkMonitorActivity` declared in `AndroidManifest.xml`.
- `Grep` - `isAvailableInBuild` and `enableNetworkMonitor` both referenced in the Activity.
- `Grep` - `savedInstanceState == null` guards the initial navigation.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 3/3 PASS. Manifest line 262 declares `.ui.networkmonitor.NetworkMonitorActivity`; `savedInstanceState == null` at Activity line 59, `enableNetworkMonitor` at 80, `isAvailableInBuild` at 96. Files: `ui/networkmonitor/NetworkMonitorActivity.kt` (new, 154 LOC), `res/layout/activity_network_monitor.xml` (new, 68 LOC), `AndroidManifest.xml` (+3 lines). `.\a.ps1 fk` exit 0, `.\a.ps1 fr` exit 0.
- Two deliberate deviations from the prompt, both forced by the tree. **(1)** The prompt's `CapabilityAvailability.isNetworkMonitorAvailable()` does not exist - see the Plan correction above the Verification list; the Activity injects `NetworkMonitorContract` and reads `isAvailableInBuild`. **(2)** The manifest entry does *not* copy the calculator's `configChanges="orientation|screenSize|keyboardHidden"`. An Activity that absorbs the orientation change is never re-inflated, so step 07.3's `layout-land` variant would exist and never be used - the calculator has exactly that defect today (`res/layout-land/activity_calculator.xml` is unreachable except on a landscape cold start). This entry declares `keyboardHidden` only, which is what makes the phase's own "rotates without re-navigating" criterion a real test rather than a tautology.
- The initial navigation is deferred rather than committed in `onCreate`: the availability answer arrives from a settings Flow, and a fragment mounted before it lands would collect the snapshot behind the fallback view - live platform observers behind a screen the user is being told is off. `pendingInitialSection` holds the launch section until the gate says yes and is nulled after one use; a recreation sets it to null outright, so the FragmentManager's restored section wins.

---

### Step 07.3 - Build the Summary screen

**Files:** `ui/networkmonitor/summary/NetworkMonitorSummaryFragment.kt`, `.../NetworkMonitorSummaryViewModel.kt`, `res/layout/fragment_network_monitor_summary.xml`, `res/layout-land/fragment_network_monitor_summary.xml`
**Depends on:** Step 07.2

**Prompt for developer:**

> Render the active-connection card above a two-column grid of section tiles, per strategic §3.1.2, feeding both from `NetworkMonitorRepository.observeSnapshot()`. Each tile shows its `SectionAvailability` in words as well as by colour and opens its subscreen on tap. The landscape layout keeps the same grid with more tiles per row - do not build a two-pane master-detail variant. Collect the flow with the project's lifecycle-aware helper, never a bare `lifecycleScope.launch`. Keep the content inside the system-bar and cutout safe bounds, and set `focusable`, `clickable` and the `nextFocus*` chain so D-pad, keyboard and mouse all reach every tile.

**Why:**

Strategic §11 criteria 2 and 4 require an honest status or an honest reason on the first screen, and CLAUDE.md Rules 11, 16, 17 and 19 make the landscape variant, the input coverage, the safe bounds and the lifecycle-safe collection part of the same edit rather than a follow-up.

**Verification:**

- `Glob` - both the portrait and the landscape layout exist.
- `Grep` - `collectOnLifecycle` or `repeatOnLifecycle` present; a bare `lifecycleScope.launch {` collecting a flow returns zero hits.
- `Grep` - `nextFocus` present in both layouts.
- `Grep` - `="#` returns zero hits in both layouts.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 4/4 PASS. Both layouts exist (`res/layout/` and `res/layout-land/fragment_network_monitor_summary.xml`); `collectOnLifecycle` at Fragment line 50 and Activity line 79, with zero hits for `lifecycleScope.launch` anywhere under `ui/networkmonitor/`; `nextFocus` 14 hits in each of the two layouts, 28 total; `="#` zero hits across every `*network_monitor*.xml`. Files: `ui/networkmonitor/summary/NetworkMonitorSummaryFragment.kt` (new, 139 LOC), `.../NetworkMonitorSummaryViewModel.kt` (new, 118 LOC), `res/layout/fragment_network_monitor_summary.xml` (new, 206 LOC), `res/layout-land/fragment_network_monitor_summary.xml` (new, 216 LOC). `assert-layout-variant-id-parity` PASS, which is the mechanical proof the two variants expose the same id set to ViewBinding.
- Both layouts run 6-16 lines over the 200-line budget, and the tiles cannot be factored out with `<include>`: an `<include>` tag accepts only `android:id`, `android:visibility` and `layout_*`, so `nextFocus*` on it is silently dropped - and this step's own Verification requires `nextFocus` inside the layouts. What could be factored out was: four `Widget.FastMediaSorter.NetworkMonitor.*` styles in `values/themes.xml` plus one `network_monitor_tile_min_height` dimen carry everything the two grids share, leaving them differing only in `columnCount` (2 vs 3) and in the focus chain, which is the only thing that legitimately differs.
- `SharingStarted.WhileSubscribed()` with no grace period, against the repo's usual `WhileSubscribed(5000)`: the Fragment's STARTED lifecycle is the only subscriber, so a stop timeout would keep the connectivity, Wi-Fi and telephony callbacks registered for five seconds after the user leaves - the tactical plan's "no background work" invariant is written per-observer, not per-screen.
- Two tiles report no availability because the snapshot cannot answer for them, and they say so ("Open to check") rather than guessing "Available": GNSS state is only knowable while its own status callback is registered, which Phase 08's section owns, and History is a local table with nothing to be unavailable about. The map value is therefore `SectionAvailability?`, and the null branch is a rendered state rather than a fallback.
- Tapping a tile currently returns to Summary and logs at `Timber.i` - Phase 08 owns the six section Fragments, and `NetworkMonitorSectionNavigator.createFragment` returns null for them until then. This is the shell, not a working navigation: the phase objective is the contract and the container, and nothing outside the app points at the Activity yet (see Rollback Plan).

---

### Step 07.4 - Add the section navigator

**Files:** `ui/networkmonitor/helpers/NetworkMonitorSectionNavigator.kt`
**Depends on:** Step 07.3

**Prompt for developer:**

> Move every fragment transaction into this manager: open a section, return to Summary, and restore the current section across recreation. The Fragment calls the navigator; it never builds a transaction itself.

**Why:**

CLAUDE.md Rule 3 and strategic §5 keep navigation decisions out of the screen classes, and Phase 08 adds six more sections through this one seam rather than six more transaction sites.

**Verification:**

- `Grep` - `beginTransaction` appears only in `NetworkMonitorSectionNavigator.kt` under `ui/networkmonitor/`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 1/1 PASS. `beginTransaction` has exactly 2 hits under `ui/networkmonitor/`, both in `helpers/NetworkMonitorSectionNavigator.kt` (lines 74, 80) - none in the Activity or the Fragment. File: `ui/networkmonitor/helpers/NetworkMonitorSectionNavigator.kt` (new, 108 LOC).
- `currentSection` is read back from `FragmentManager.findFragmentById(..).tag` rather than from a field the navigator keeps. The navigator is reconstructed in `setupViews()` on every recreation while the FragmentManager restored its own state, so after a rotation a field of ours would be a stale guess and the FragmentManager is the only party that still knows where the user was. That is why every transaction tags the fragment with `section.key`.
- The file also declares `NetworkMonitorSectionHost`, the one-method interface the Activity implements and the Fragments call. Without it a tile would have to cast its host to `NetworkMonitorActivity`, and Phase 08's six Fragments would each acquire a compile-time dependency on the Activity for a single call.

---

### Step 07.5 - Add the shell strings in three locales

**Files:** `res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 07.3

**Prompt for developer:**

> Add the section titles and the three availability phrases - no hardware, no permission, no network - through `scripts/utils/set-android-string.ps1 -Action add`, one call per key. Check each against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §3.2 requires EN, RU and UK parity, and the availability phrases carry criterion 2's honesty promise, so their wording is product text and not a developer placeholder.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "network_monitor"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-09 - Verification 2/2 PASS. `check_strings_localized.ps1 -KeyPrefix "network_monitor"` reports 28 keys, all present in en/ru/uk, exit 0 (the ten best-effort locales are reported, not fatal). 27 new keys added one `set-android-string.ps1 -Action add` call each into `values/`, `values-ru/`, `values-uk/strings.xml`; `network_monitor_title` already existed from Phase 01. §6 checklist: no exception text or error codes; no "Are you sure?"; no "completed successfully"; the disabled state names where to turn the feature on rather than dead-ending; the three availability phrases are sentences a user can act on ("Permission not granted", "No such hardware on this device", "Nothing connected right now"); no emoji; no legal or machine-readable artifact touched.
- The Wi-Fi, Mobile and Bluetooth transport labels reuse the section-title keys instead of getting their own. The word is the same word - a duplicate key would be two strings a translator can drift apart while the UI insists they are one thing. Only `ethernet`, `vpn` and `other` needed transport keys of their own.
- The availability wording deliberately avoids promising an action this phase does not ship: "Permission not granted" rather than "Tap to grant", because the grant flow is registry-driven and belongs to Phase 08's sections.

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` and `.\a.ps1 fr` both BUILD SUCCESSFUL, exit 0, re-verified after the audit fixes below rather than taken from the implementing agent's report.
- [ ] The screen opens on a device, shows tiles, and rotates without re-navigating. **Deferred to the ticket's device-test gate** - there is nothing to reach it from yet, because the settings row, the menu entry and the launcher cell are Phase 09. Left unticked on purpose: the rotation half of this criterion is the one thing the manifest deviation in step 07.2 is betting on, and a build passing says nothing about it.
- [x] `Grep` for `TODO(phase-07)` returns zero hits - 0 occurrences across `app_v2/src`.
- [x] Dev log entry added for the phase - one entry naming the whole 17-file set.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See the audit note below.
- [x] `post-change -ScopeToFile`: `post-change: PASS`, exit 0, on the third attempt - the first two were real failures, both recorded below.

## Phase-boundary audit - 2026-08-09

Layers 1, 2 and 3 of `docs/CODE_AUDIT_PROTOCOL.md`. Two findings were caught by the closure gates rather than by reading, which is worth stating plainly: reading the Activity first, I had judged its shape acceptable because it copies `CalculatorActivity` exactly, as step 07.2 instructed. The gate disagreed, and the gate was right.

- **P1, fixed here** - the Activity field-injected `SettingsRepository` and `NetworkMonitorContract` and composed the availability decision itself. That is domain access from a screen class (CLAUDE.md Rule 3), and the `activity-logic` ratchet failed the closure over it. The named precedent does the same thing, but a precedent inside the baseline is debt rather than a licence to add another. Moved into a new `NetworkMonitorViewModel` exposing a four-state `NetworkMonitorAvailability`; the Activity now renders a state and holds no repository. The fourth state, `UNKNOWN`, exists because the extraction introduced a frame in which the answer is not yet known, and showing the "switched off" fallback to somebody who has it switched on reads as a fault.
- **Gate defect, fixed here** - the closure then failed on `listener-symmetry` for `onBackPressedDispatcher.addCallback`, which AndroidX unregisters through the lifecycle it is handed and which therefore has no `remove*Callback` to pair with, ever. In delta mode a new file's whole count reads as growth, so the gate rejected every new Activity that handles Back, while the dozen existing screens making the identical call passed only by sitting in the baseline. Fixed in `scripts/quality/assert-listener-symmetry.ps1` by discounting that one call, exactly as `registerReceiver(null, ..)` was already discounted (CLAUDE.md Rule 13 - fix the script, do not contort the code around it). It removed **18** false positives project-wide, which is the evidence that this was a defect and not special pleading for one file; baseline ratcheted down 133 -> 115.
- **P2, recorded not fixed** - a tile tap currently returns the user to Summary and logs at `Timber.i`, because `NetworkMonitorSectionNavigator.createFragment` answers null for all six sections until Phase 08 supplies the Fragments. This is the shell, and the plan splits it that way deliberately, but it means the screen's headline interaction does not work yet. Nothing outside the app can reach it either (Phase 09 owns the entry points), so nothing ships in this state.
- **P2, recorded not fixed** - both summary layouts overrun the plan's 200-line budget (206 and 216). The tiles cannot be factored into an `<include>`, because an `<include>` drops `nextFocus*` silently and step 07.3's own verification requires those attributes present. Four shared styles and one dimension absorbed everything else, leaving the two files differing only in column count and focus chain.
- Layer 1 - files sit in their layers, the navigator is the only holder of `beginTransaction` under `ui/networkmonitor/`, and nothing reads a flavor `BuildConfig` flag: availability arrives through the injected `NetworkMonitorContract`.
- Layer 2 - no `GlobalScope`; the Summary state is `stateIn(WhileSubscribed())` with no stop timeout, so the device observers behind the snapshot unregister when the screen stops rather than after a grace period the "no background work" invariant does not allow.
- Layer 3 - the flow collection is lifecycle-aware in both the Activity and the Fragment (`collectOnLifecycle`, 4 sites, zero bare `lifecycleScope.launch`), and the Activity tears the container down through `navigator.clear()` when the gate turns to disabled, so a Fragment cannot keep collecting behind a fallback covering it.

---

## Handoff Notes to Next Phase

Every subscreen added in Phase 08 is a fragment reached through `NetworkMonitorSectionNavigator` and addressable by its `NetworkMonitorSection` key from outside the app.

- Phase 08 fills in `NetworkMonitorSectionNavigator.createFragment`, which is the one place that has to change - six `null` branches become six constructors. Until every branch is filled, a tile for a missing section stays on Summary rather than crashing.
- A section Fragment reaches its host through `NetworkMonitorSectionHost`, never through the concrete Activity type.
- The availability gate lives in `NetworkMonitorViewModel`, not the Activity. A section must not re-read the setting or the contract for itself - if the gate closes, the host removes the container out from under it.

---

## Rollback Plan

Revert phase commit(s) - the Activity is not yet registered in any menu, panel or launcher set, so nothing points at it.
