# Phase 08 - Activation, Settings Group, Welcome Toggle, Seeding

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done 2026-07-18
**Depends on:** Phase 05, 06, 07
**Blocks:** Phase 09
**Steps done:** 7 / 7
**Started:** 2026-07-17
**Completed:** -

---

## Objective

Make the mode reachable by users: a dedicated collapsible "System launcher" settings group (owner addition 2026-07-17), the Welcome first-page toggle (owner addition 2026-07-17), settings-search registration, and profile-based starter-set seeding on first open.

---

## Prerequisites

- [~] Phases 05-07 done: 05 / 05B / 06 ✅. **07 is 5/6 - code-complete and audited; only its 07.6 device walkthrough is open**, and that walkthrough is physically un-runnable until this phase ships the activation UI (the HOME activity is disabled by default, ADR-2). 07.6's device-half is therefore batched with 08.7. Phase 08 depends on Phase 07 *code*, which is landed - not on its device validation.
- [x] CODE.LOCK acquired (`/spec-dev S0404 step 08.1`).
- [ ] Re-read the header comments of `ui/settings/fragments/OperationsSettingsFragment.kt` and `ui/welcome/WelcomeActivity.kt` before editing (Rule 8 - existing KDoc is requirements). (Done at 08.2 / 08.4 - 08.1 is layout-only.)

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified (backup first - large) | +90 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified (backup first) | +90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified (backup first) | +90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | +15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchRegistry.kt` | Modified | +15 |
| `app_v2/src/main/res/layout/page_welcome_enhanced.xml` | Modified | +20 |
| `app_v2/src/main/res/layout-land/page_welcome_enhanced.xml` | Modified | +20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified (backup first - large) | +30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` | Modified | +25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt` | New | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | +25 |
| `docs/settings/settings-manifest.json` + `docs/settings/settings-annotations.json` + `docs/SETTINGS_REFERENCE*.md` | Regenerated | - |

> Settings group visibility is gated by injected `LauncherModeContract.isAvailableInBuild` - NEVER by a `BuildConfig` check in these `src/main` files (Rule 14; the existing `ENABLE_SCHEDULED_OPERATIONS` check in this fragment is legacy debt - do not copy it).

---

## Steps

### Step 08.1 - Settings group layout (both orientations)

**Files:** `res/layout/fragment_settings_destinations.xml`, `res/layout-land/fragment_settings_destinations.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Back both files up to `temp/S0404/`. Immediately after the `groupSystemApps` block (portrait ~line 836; find the same anchor in land) add a sibling group cloned from its structure: container `@+id/groupLauncherMode` → `CollapsibleSectionHeader` `@+id/headerLauncherMode` (title `@string/launcher_settings_group_title`, icon `@drawable/ic_launcher_mode` - copy whatever icon attribute neighbouring headers use) → `LinearLayout` `@+id/containerLauncherMode` with rows:
> 1. `SettingsToggleRow` `@+id/rowLauncherModeEnabled` - title `launcher_settings_enable_title`, subtitle `launcher_settings_enable_desc`, help popup (`str_showHelp="true"`, help strings below).
> 2. `SettingsToggleRow` `@+id/rowLauncherShowRecents`, `@+id/rowLauncherShowPinned`, `@+id/rowLauncherShowTray` - taskbar composition.
> 3. `SettingsDropdownRow` `@+id/rowLauncherDensity` - grid density (4 options from `AppSettings.LAUNCHER_DENSITY_OPTIONS`).
> 4. A plain action row `@+id/rowLauncherOpenHomeSettings` (clone the visual pattern of an existing tappable row in this file) - opens the system default-home chooser.
> Identical ids and order in BOTH orientation files (Rule 11).

**Verification:**

- `Grep` - `headerLauncherMode` and `rowLauncherModeEnabled` present in BOTH layout files.
- `Grep` - `="#` zero new hits in the diffs.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS. Added the collapsible `groupLauncherMode` card (sibling of `groupSystemApps`, inserted before `groupScreenGestures`) to BOTH orientation files with identical ids/order (Rule 11): `headerLauncherMode` (`CollapsibleSectionHeader`, icon `ic_launcher_mode`), `rowLauncherModeEnabled` (`SettingsToggleRow` + help popup), `rowLauncherShowRecents`/`rowLauncherShowPinned`/`rowLauncherShowTray` (taskbar composition toggles - bound to `AppSettings.launcherTaskbarShow*` in 08.2), `rowLauncherDensity` (`SettingsDropdownRow`, inline+compact, entries wired programmatically in 08.2 from `LAUNCHER_DENSITY_OPTIONS`), `rowLauncherOpenHomeSettings` (Outlined `MaterialButton`, models `btnOpenDefaultAppsDialog`). Grep: all 9 ids present in both files; hex in launcher blocks = 0. String refs (`launcher_settings_*`) are added in 08.6 before the phase build - no compile runs between here and then (per-step verification is Grep-only by plan design).
- **Note for 08.2:** `TODO(phase-08)` has **0 repo-wide hits** - the placeholder the step expects does not exist. `LauncherStartMenuFragment` section 5 must be inspected live to find the real settings-open insertion point; the step's "zero hits" predicate passes trivially either way.
- **Deviation - 08.6 strings pulled forward.** The layout references `launcher_settings_*` / `launcher_welcome_toggle_title`; aapt cannot link (so nothing in 08.2-08.5 can compile-check) until those keys exist. All 16 keys were seeded now via `set-android-string.ps1 -Action add` (EN/RU/UK, parity 16/16, `check_strings_localized.ps1` OK, Cyrillic verified incl. ё in "закреплённые"). The remaining 08.6 work - manifest regen, annotations, `SETTINGS_REFERENCE*`, and the one `assert-settings-doc-sync` gradle-test run - stays at 08.6 (it is expensive and can only go green once every layout row is final). Intermediate steps close with batched dev-logs at the phase boundary, not per-step `post-change` (which would fire the deferred settings-doc-sync gate).

---

### Step 08.2 - Fragment + deep-link wiring

**Files:** `ui/settings/fragments/OperationsSettingsFragment.kt`, `ui/settings/SettingsActivity.kt`
**Depends on:** Step 08.1

**Prompt for developer:**

> Back up the fragment. `@Inject lateinit var launcherModeContract: LauncherModeContract` + `@Inject lateinit var launcherRoleManager: LauncherRoleManager`. When `!isAvailableInBuild` hide `groupLauncherMode` entirely (mirror the hidden-Scheduled pattern minus BuildConfig). Register the section: `register(binding.headerLauncherMode, binding.containerLauncherMode, "operations__launcher_mode")`. Wire rows:
> - `rowLauncherModeEnabled`: reflect `launcherRoleManager.isModeEnabled()` on resume; ON → `enableMode(activity, roleLauncher)` where `roleLauncher` is an `ActivityResultLauncher<Intent>` registered in the fragment (result only refreshes the row state); OFF → `disableMode()`.
> - Composition toggles + density dropdown ↔ the four `AppSettings.launcher*` fields via `viewModel.updateSettings` exactly like neighbouring rows.
> - `rowLauncherOpenHomeSettings` → `launcherRoleManager.openHomeChooser(requireActivity())`.
> In `SettingsActivity`: add `const val SECTION_LAUNCHER_MODE = "launcher_mode"` + factory `fun openLauncherSectionIntent(context: Context): Intent` (mirror `openProgramsSectionIntent` - `TAB_OPERATIONS` + expand extra). Extend the fragment's `ensureSectionExpanded`/`checkAndExpandSectionFromIntent` to accept the new section id. Then replace the `TODO(phase-08)` in `LauncherStartMenuFragment` section 5 with `startActivity(SettingsActivity.openLauncherSectionIntent(context))`.

**Verification:**

- `Grep` - `operations__launcher_mode` present in the fragment; `SECTION_LAUNCHER_MODE` in `SettingsActivity.kt`.
- `Grep` - `TODO(phase-08)` zero hits repo-wide.
- `Grep` - `BuildConfig` zero NEW hits in the fragment diff.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 5/5 PASS + `.\a.ps1 fk` BUILD SUCCESSFUL (40s; `mergeStandardDebugResources` clean, proving the 08.1 string refs resolve). `SettingsActivity`: `SECTION_LAUNCHER_MODE` const + `openLauncherSectionIntent` (mirrors `openProgramsSectionIntent`: `TAB_OPERATIONS` + expand extra). `OperationsSettingsFragment`: injected `LauncherModeContract` + `LauncherRoleManager`; `launcherRoleLauncher` (`StartActivityForResult`, only refreshes the enable row on return); `setupLauncherRows()` wires the enable toggle (ON -> `enableMode(host, launcher)` / OFF -> `disableMode()` - reflects the HOME component state, not a settings field), the three `launcherTaskbarShow*` composition toggles + the density dropdown (entries from `AppSettings.LAUNCHER_DENSITY_OPTIONS`, all through `viewModel.updateSettings`), and `rowLauncherOpenHomeSettings` -> `openHomeChooser`; the group is hidden via `binding.groupLauncherMode.isVisible = false` when `!isAvailableInBuild` (contract-gated, NOT `BuildConfig` - Rule 14; the legacy `ENABLE_SCHEDULED_OPERATIONS` check was deliberately not copied); section registered as `operations__launcher_mode`; `checkAndExpandSectionFromIntent`/`ensureSectionExpanded` extended for `SECTION_LAUNCHER_MODE`; the enable row is reflected in `onResume` from `isModeEnabled()`. `LauncherStartMenuFragment`: `rowAppSettings` now deep-links via `openLauncherSectionIntent`.
- **Deviation (Rule 10 - no placeholder existed):** the step's `TODO(phase-08)` in `LauncherStartMenuFragment` was never present (0 repo-wide). Wired the deep-link into the existing `rowAppSettings` row instead. `openLauncherSectionIntent` opens the FULL settings on the Operations tab pre-expanded to the launcher group, so it is a strict improvement over the prior generic `Intent(SettingsActivity)`, not a scope reduction.
- Backups: `temp/S0404/OperationsSettingsFragment.kt.20260717_230129.bak`, `temp/S0404/SettingsActivity.kt.20260717_230129.bak`.

---

### Step 08.3 - Settings search registration

**Files:** `ui/settings/search/SettingsSearchRegistry.kt`
**Depends on:** Step 08.2

**Prompt for developer:**

> Read the registry file first and mirror an existing entry group: add entries for the launcher rows (enable, recents, pinned, tray, density, home-chooser) pointing at `TAB_OPERATIONS` + section `operations__launcher_mode`, gated through `SettingsSearchCapabilityGate` on `LauncherModeContract.isAvailableInBuild` (follow how an existing capability-gated entry is declared - the gate file is `ui/settings/search/SettingsSearchCapabilityGate.kt`).

**Verification:**

- `Grep` - `launcher` present in `SettingsSearchRegistry.kt` with the new section key.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification PASS (`launcher` present in `SettingsSearchRegistry.kt`, 11 hits). Gated the six System-launcher row keys (`rowLauncherModeEnabled`, `rowLauncherShowRecents`, `rowLauncherShowPinned`, `rowLauncherShowTray`, `rowLauncherDensity`, `rowLauncherOpenHomeSettings`) on `launcherModeContract.isAvailableInBuild` inside `SettingsSearchRegistry.isCapabilityAvailable` (injected `LauncherModeContract`), so on `lite`/`photos`/`legacy` search yields no dead hit into a hidden group. The rows are auto-indexed from the layout scan (`fragment_settings_destinations` already in `SettingsSearchLayoutCatalog` - no catalog change); the search deep-link reuses the collapsible -> `SECTION_LAUNCHER_MODE` path wired in 08.2's `ensureSectionExpanded` (same mechanism as `SECTION_ADDITIONAL_PROGRAMS`).
- **Deviation:** the prompt named `SettingsSearchCapabilityGate` as the host, but the step's own verification greps `SettingsSearchRegistry.kt`. `isCapabilityAvailable` there is the equivalent per-key capability spot (it already gates `btnOpenDefaultAppsDialog` the same DI way), so the gate landed there to satisfy the predicate with an existing pattern. Functionally identical - both filters run over every entry in `entries`.
- Compile deferred to the batched `fk` after 08.4 (DI-only change; `LauncherModeContract` is already Hilt-bound - injected by `LauncherRoleManager`).

---

### Step 08.4 - Welcome first-page toggle

**Files:** `ui/welcome/WelcomeActivity.kt`, `ui/welcome/WelcomePagerAdapter.kt`, `res/layout/page_welcome_enhanced.xml`, `res/layout-land/page_welcome_enhanced.xml`
**Depends on:** Step 08.2

**Prompt for developer:**

> Back up `WelcomeActivity.kt`. Owner requirement: the toggle sits on the VERY FIRST welcome page. In `page_welcome_enhanced.xml` (+ land, same id) add a compact toggle row `@+id/rowWelcomeLauncherMode` (icon `ic_launcher_mode` + title `launcher_welcome_toggle_title` + `MaterialSwitch`), `visibility="gone"` by default, placed below the theme picker block. Extend `WelcomePage` (data class in `WelcomePagerAdapter.kt`) with `showLauncherModeToggle: Boolean = false` and `onLauncherModeToggled: ((Boolean) -> Unit)? = null`; bind visibility+listener where `showLanguagePicker` is bound. In `WelcomeActivity.setupViewPager` page-1 block set `showLauncherModeToggle = launcherModeContract.isAvailableInBuild` (inject the contract) and record the choice in a `WelcomeViewModel` state field (`launcherModeRequested: Boolean`). At the point where the wizard completes (`isWelcomeCompleted` is persisted / activity finishes into MainActivity), if requested: call `launcherRoleManager.enableMode(this, welcomeRoleLauncher)` (register the `ActivityResultLauncher` in the activity) so the system role dialog appears once, after onboarding - not mid-wizard.

**Verification:**

- `Grep` - `rowWelcomeLauncherMode` present in BOTH `page_welcome_enhanced` variants.
- `Grep` - `showLauncherModeToggle` present in `WelcomePagerAdapter.kt` and `WelcomeActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 4/4 PASS + `.\a.ps1 fk` BUILD SUCCESSFUL (29s; also validates the 08.3 Registry DI through kapt/Hilt). Added the compact `rowWelcomeLauncherMode` row (ImageView `ic_launcher_mode` + title + `MaterialSwitch` `switchWelcomeLauncherMode`, the canonical switch class from `view_settings_toggle_row`) below the theme-picker block in BOTH `page_welcome_enhanced` variants, `visibility=gone` by default (Rule 11). `WelcomePagerAdapter`: `WelcomePage` gained `showLauncherModeToggle` + `onLauncherModeToggled`; `EnhancedViewHolder` binds visibility + the switch listener where the theme picker is bound. `WelcomeActivity`: injected `LauncherModeContract` + `LauncherRoleManager`; `welcomeRoleLauncher` (`StartActivityForResult`, no-op result); page-1 sets `showLauncherModeToggle = launcherModeContract.isAvailableInBuild` + `onLauncherModeToggled = { viewModel.setLauncherModeRequested(it) }`; `completeWelcomeFlow` calls `maybeRequestLauncherMode()` before navigation, which (once, guarded) calls `enableMode(this, welcomeRoleLauncher)` when requested + available. `WelcomeViewModel`: `launcherModeRequested` var (config-change-surviving, `private set`) + `setLauncherModeRequested`.
- **Design note (role dialog vs finishing Activity):** `enableMode` runs just before `goToMainActivity` finishes the wizard. The HOME-component enable is persistent; the API 29+ role request is a separate system activity that survives the caller finishing, so the dialog shows over MainActivity ("appears once, after onboarding") and the empty `welcomeRoleLauncher` callback being skipped is harmless (it only refreshes a settings row not on screen here).
- **Rule 5 process miss (flagged for audit):** `WelcomeActivity.kt` is 822 LOC and the pre-edit timestamped backup was not taken before editing. Post-edit checkpoint saved at `temp/S0404/WelcomeActivity.kt.postedit_*.bak`; edits are surgical + compile-verified and the working tree is intact, so risk is low, but the guard was skipped. `WelcomeViewModel.kt` (374) and `WelcomePagerAdapter.kt` (381) are < 500 - no backup required.

---

### Step 08.5 - Starter sets + seeding

**Files:** `core/launcher/LauncherStarterSets.kt`, `domain/usecase/launcher/SeedLauncherDesktopUseCase.kt`, `ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** - parallel to 08.1-08.4

**Prompt for developer:**

> `LauncherStarterSets` - a pure data table (strategic §5.3: sets are data, adding one must not touch the surface): `data class StarterItem(kind: LauncherCellKind, target: String, spanW: Int = 1, spanH: Int = 1)` and `fun itemsFor(profile: DeviceProfileType, lastResourceId: Long?, allAudioResourceId: Long?, streamsAvailable: Boolean): List<StarterItem>`. Table (skip any item whose id-dependency is null; encode via `LauncherCellCommand`/`LauncherGadgetRegistry.encodeTarget` string constants - keep this file free of launcherEnabled imports by hardcoding the gadget key consts here with a cross-reference comment):
> - `PHOTO_FRAME`: clock gadget 2×1, folder_preview(lastResourceId) 2×2, shortcut `res:<last>:SLIDESHOW`, `fn:favorites`, `os:settings`, `app:<own package>` (own package placeholder resolved by the use case).
> - `AUDIO_PLAYER`, `CAR_HEAD_UNIT`: clock 2×1, playlist(allAudioResourceId) 2×2, streams gadget 2×2 when `streamsAvailable`, `fn:favorites`, `os:settings`, own app.
> - `TV_MEDIA_BOX`, `MEDIA_PLAYER`, `VIDEO_PLAYER`: clock 2×1, streams gadget 2×2 when available, folder_preview(lastResourceId) 2×2, `fn:favorites`, `os:settings`, own app.
> - `EBOOK_READER`: clock 2×1, shortcut `res:<last>:PLAY`, `fn:favorites`, `os:settings`, own app.
> - all remaining profiles (incl. `OTHER`, `PERSONAL_SMARTPHONE`, `HOME_TABLET`, `VR_HEADSET`): clock 2×1, `fn:favorites`, `os:settings`, own app.
> `SeedLauncherDesktopUseCase @Inject constructor(desktop: LauncherDesktopRepository, profiles: DeviceProfileRepository, resources: ResourceRepository, settings: SettingsRepository, streamsAvailability: ResolvePanelRouteAvailabilityUseCase, @ApplicationContext context: Context)`: `suspend operator fun invoke(portraitColumns: Int, landscapeColumns: Int)` - resolve profile (null → OTHER), `lastResourceId` via `SettingsRepository.getLastUsedResourceId()`, all-audio resource by `LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO` path lookup through `ResourceRepository`, `streamsAvailable` via the availability use case for `InternalRouteCatalog.KEY_STREAMS`; lay items row-major into BOTH orientations (independent placement per orientation's column count) and call `seedIfEmpty` per orientation (ADR-4: seeds once, never overwrites; profile change later never re-seeds). ViewModel: call the use case from the home surface start path once per process after columns are resolved; landscape columns when starting in portrait = `LauncherGridGeometry.columns(currentHeightDp, densityFactor)` (swap axes).

**Verification:**

- `Grep` - `class LauncherStarterSets` (or `object`) and `class SeedLauncherDesktopUseCase` match once each; `seedIfEmpty` called with both orientations.
- `Grep` - `PHOTO_FRAME` and `CAR_HEAD_UNIT` present in `LauncherStarterSets.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 4/4 PASS + `.\a.ps1 fk` BUILD SUCCESSFUL (22s; validates the new use case's Hilt injection into `LauncherHomeViewModel`). New `core/launcher/LauncherStarterSets.kt` (`object`; pure data table `StarterItem(kind, target, spanW, spanH)` + `itemsFor(profile, lastResourceId, allAudioResourceId, streamsAvailable)`; gadget keys hardcoded with a keep-in-sync comment since `LauncherGadgetRegistry` lives in launcherEnabled; own-app placeholder `OWN_APP_TOKEN`; shortcut targets encoded via `LauncherCellCommand`, gadget targets as `"<key>:<param>"`). New `domain/usecase/launcher/SeedLauncherDesktopUseCase.kt` (resolves profile [null -> OTHER], `lastResourceId` via `getLastUsedResourceId() > 0`, all-audio via `getAllResourcesSync().path == VIRTUAL_PATH_ALL_AUDIO`, `streamsAvailable` via `ResolvePanelRouteAvailabilityUseCase(KEY_STREAMS).availableInBuild` - mirrors `SeedDefaultAppLaunchPanelUseCase`; lays items row-major over an occupancy grid per orientation so no footprint overlaps [Phase 07 invariant]; substitutes the own-app token; `seedIfEmpty` per orientation). `LauncherHomeViewModel`: injected the use case + `seedDesktopIfNeeded(portrait, landscape)` guarded once per process.
- **Deviation (Files Touched):** `LauncherHomeActivity.kt` was edited (not in 08.5's budgeted table - the same under-budgeting the 07.4 note flagged). A `ViewModel` cannot read display metrics, so the Activity derives both orientations' column counts (current axis = `screenWidthDp`, other = `screenHeightDp` - the "swap axes" the plan assigned to the ViewModel, placed where the metrics live) and calls `viewModel.seedDesktopIfNeeded(..)` once from `setupViews()` after `applyGridGeometry()`. Same launcherEnabled subsystem, no flavor leak.
- **Concurrent session noted:** the `fk` build warned that `CODE.LOCK` is held by another session (reason "Update product history with pre-2026 lineage and owner context") - a docs task on non-overlapping files. Compile is read-only, no conflict; the lock is not disturbed here.

---

### Step 08.6 - Strings + settings docs sync (Rule 22)

**Files:** trilingual `strings.xml` via tool; `docs/settings/*`; `docs/SETTINGS_REFERENCE*.md`
**Depends on:** Steps 08.1-08.5

**Prompt for developer:**

> Strings via `set-android-string.ps1 -Action add`: `launcher_settings_group_title` ("System launcher"), `launcher_settings_enable_title`, `launcher_settings_enable_desc` (outcome-first: what the user gets, how to leave the mode), `launcher_settings_enable_help_title`, `launcher_settings_enable_help_message` (mention the system Always/Just-once chooser and that turning it off returns the previous home screen), `launcher_settings_show_recents_title`, `launcher_settings_show_pinned_title`, `launcher_settings_show_tray_title`, `launcher_settings_density_title` + 4 option labels (`launcher_settings_density_sparse`/`_default`/`_dense`/`_densest`), `launcher_settings_open_home_chooser_title` + `_desc`, `launcher_welcome_toggle_title`. `check_strings_localized.ps1 -KeyPrefix "launcher_"` → exit 0; COMMUNICATION_POLICY §2+§6 PASS (these are the most user-visible strings of the epic).
> Then regenerate the settings docs per Rule 22 (run the regeneration path referenced in `scripts/quality/assert-settings-doc-sync.ps1` - read its header for the generator command), add annotations for every new row in `docs/settings/settings-annotations.json`, and confirm the icon-inventory gate passes (new group icon).

**Verification:**

- `check_strings_localized.ps1 -KeyPrefix "launcher_"` → exit 0.
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` → exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification PASS (both predicates). `check_strings_localized.ps1 -KeyPrefix launcher_` -> 68 keys present in EN/RU/UK. `assert-settings-doc-sync.ps1 -Gate` -> OK on all 5 stages (catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync). Regenerated `docs/settings/settings-manifest.json` from the live layout scan (`SettingsManifestExportTest` generate mode; +70 lines: `headerLauncherMode` + the 6 rows), added 7 en/ru/uk annotations to `settings-annotations.json`, re-rendered `SETTINGS_REFERENCE.md`/`_RU`/`_UK` in place (+7 lines each). The 16 `launcher_settings_*` / `launcher_welcome_toggle_title` strings were already seeded at 08.1 (pull-forward); this step is the docs cycle + the gate.
- **Concurrent-session note:** the first gate attempt was refused at stage 2 because another session held `BUILD.LOCK` (`build-nolegal-debug.ps1`, host MARK); re-ran to green once that build and its `CODE.LOCK` were released. No content issue - purely the Rule-23 concurrency guard doing its job.

---

### Step 08.7 - Build + full activation walkthrough

**Files:** - (validation only)
**Depends on:** Steps 08.1-08.6

**Prompt for developer:**

> `.\a.ps1 d` + install on emulator/device. Walkthrough (strategic §3.3 validation level): fresh install → Welcome shows the toggle on page 1 → complete wizard with toggle ON → role dialog appears → grant → Home opens the seeded desktop matching the chosen profile → Settings group reflects ON → toggle OFF → previous launcher returns → toggle ON again from Settings → desktop unchanged (no re-seed). Also verify settings search finds "launcher". Record `expected | actual` per step.

**Verification:**

- `.\a.ps1 d` → BUILD SUCCESSFUL; walkthrough recorded; `.\a.ps1 fkn` also BUILD SUCCESSFUL (welcome/settings edits touch shared code used by noLegal).

**Status:** `[x]` done (device walkthrough + vendor Home-role/reboot gate confirmed by owner 2026-07-18 as the S0404 BlockNeedUserTest pass - Wave 0 verified)

**Step Log:**

- 2026-07-18 - Build half PASS: `.\a.ps1 d` -> **BUILD SUCCESSFUL in 1m 38s**, APK `FastMediaSorter_standard_debug_v2.60.7122.153-DEBUG.apk` assembled (full package: dex + manifest merge + resources + Hilt, all Phase 08 code + audit fixes). `.\a.ps1 fkn` -> BUILD SUCCESSFUL (shared welcome/settings/RoleManager edits compile on noLegal). Unit tests (`LauncherStarterSetsTest` 9 + `LauncherStarterSetsParityTest` 1 + retrofitted `SettingsSearchCapabilityGateTest`) green. detekt scoped PASS (all changed files clean). **Device half NOT run** - no device attached and, per the plan, the activation walkthrough is batched with 07.6's deferred device pass (the launcher HOME activity is unreachable until this phase's activation UI exists, which is exactly what this phase built). Owner requested a manual device test of Phase 08 next; the built APK is ready.
- **Combined device walkthrough (07.6 + 08.7), to run when a device is online:** fresh install -> Welcome shows the "use as home screen" toggle on page 1 -> complete wizard with it ON -> the app becomes a home candidate; press Home -> system "Set as home" chooser -> pick this app -> the seeded desktop matches the device profile -> enter edit mode, add one of each cell kind, drag/remove, pin an app -> Settings "System launcher" group reflects state, toggle off returns the previous launcher, toggle on again does not re-seed -> settings search finds "launcher". Vendor Home-role + reboot survival (strategic §6 item 14) gates Verified.

---

## Phase Done Criteria

- [ ] Every `Step 08.*` above is `[x] done`.
- [ ] Settings docs gates pass (`assert-settings-doc-sync.ps1`, icon inventory).
- [ ] On `lite` emulator build the group and welcome toggle are absent (`compileLiteDebugKotlin` + spot-check).
- [ ] Dev log + `catalog_sync.ps1`; CODE.LOCK released.

---

## Phase audit (2026-07-17/18)

Owner-requested before the device test. Three adversarial auditors (seed correctness/concurrency/encoding; UI/lifecycle/a11y; flavor/spec-compliance/Rule-22), each told to refute. **No P0. 4 P1, all fixed. Several P2/P3 fixed; 3 out-of-scope findings parked.** Every finding hand-verified against the code before acting.

**P1 - fixed:**
- **Seed `densityFactor` race.** `seedDesktopIfNeeded` read `densityFactor.value` synchronously in `setupViews()`, but that StateFlow is `Eagerly`-started over DataStore - its initial value is the `1.0` default until the async disk read lands, and seeding is one-shot, so a wrong column count would misplace/overlap cells permanently on a non-default-density device (TV/car). Fixed: the Activity now passes raw dp + orientation; the ViewModel resolves columns INSIDE the coroutine from `getSettings().first().launcherDensityFactor` (the real persisted value).
- **Seed exception-safety on a HOME surface.** Only the profile read was guarded; a Room exception from `getAllResourcesSync()`/`streamsAvailability` would crash-loop the device's own home screen (no alternate launcher on a kiosk/car/TV). Fixed: the whole seed body is wrapped in `runCatching { }.onFailure { Timber.w(..) }` - a failed read degrades to an empty desktop, never a crash.
- **Welcome role dialog never shown on first-run.** `completeWelcomeFlow` launched the role dialog then `goToMainActivity` pushed MainActivity+SettingsActivity in the same frame, burying the dialog. Fixed: Welcome now calls the new `LauncherRoleManager.markAsHomeCandidate()` (durable component-enable, no racy dialog); the system chooser surfaces on the next Home press (ADR-2). The Settings toggle, which does not finish, still shows the dialog directly.
- **`launcherRoleLauncher` NPE.** The settings-fragment callback dereferenced `binding` unguarded; a tab swipe during the role dialog nulls the view. Fixed: guarded with `_binding != null` (the two pre-existing sibling launchers parked as S1084).

**P2 - fixed:**
- Search gate relocated from `SettingsSearchRegistry.isCapabilityAvailable` (a documented wart) to the canonical `SettingsSearchCapabilityGate` (auditor 3 + the plan's own prompt); predicate corrected.
- The overlap-invariant packer extracted to `LauncherStarterSets.place()` (pure, testable) + `LauncherStarterSetsTest` (9 tests: itemsFor mapping, no-overlap, span reservation, clamp, own-app token) + `LauncherStarterSetsParityTest` (gadget-key parity vs `LauncherGadgetRegistry`, so a rename can't silently ship a broken cell).
- Welcome switch "phantom state": bound `switch.isChecked` from the surviving `launcherModeRequested` so an Activity recreate (language/theme pick, fold) never drops the user's ON choice; whole-row tap added (bigger touch target, switch stays the D-pad stop).

**P3 - fixed:** seeded resource ids validated against `getAllResourcesSync()` (no permanently-dead tile from a stale last-used id); help string now names the "Just once" chooser branch.

**Parked (CLAUDE.md 3.1, dedup-checked):** **S1084** (ActivityResult callbacks deref `binding` unguarded, project-wide), **S1085** (dead `fragmentContainerWelcome` + `WelcomeCompleteListener` in `WelcomeActivity`), **S1086** (`ARCHITECTURE.md` domain->data dependency-rule vs practice gap).

**Refuted (recorded so not re-litigated):** `firstFreeAnchor` infinite loop (span clamped before the range), `cellKey` stride collision, seed-once race (transactional `seedIfEmpty` + `seedTriggered` fast-path), main-thread I/O, listener double-fire, MaterialSwitch inflate-context, flavor isolation (contract-gated, zero `BuildConfig.IS_*` in `src/main`), layer discipline (matches codebase convention), ADR-4 seed-once, deep-link namespace pairing, Rule-22 icon inventory, communication policy.

**Detekt note:** touching `OperationsSettingsFragment`/`LauncherRoleManager` surfaced their pre-existing ktlint/detekt debt (import order, arg-wrapping, ReturnCount) via the scoped gate; fixed under Rule 7. All changed files end detekt-clean (`assert-detekt` scoped PASS).

## Handoff Notes to Next Phase

- All user-visible surfaces exist; Phase 09 documents them. Freeze wording only after Phase 08 strings are final.
- Settings path for docs: Settings → Operations tab → System launcher (verify the exact localized tab/section names against `docs/settings/settings-manifest.json` when writing HOW_TO paths).

---

## Rollback Plan

Revert phase commit(s); Phases 01-07 remain inert without activation UI (component disabled by default).
