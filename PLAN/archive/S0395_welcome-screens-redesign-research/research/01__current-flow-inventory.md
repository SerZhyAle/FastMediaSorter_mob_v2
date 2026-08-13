# 01 - Current Welcome Flow Inventory

Strategic item: S0395 §6.1. Phase: 01.

## Question

Which pages of the current welcome flow are decorative, what educational content do they carry, what is lost when they are removed, and where should that content live instead?

## Sources

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` (735 LOC)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` (incl. `WelcomePage`, `FeatureCard`)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt`
- `app_v2/src/main/res/layout/page_welcome*.xml` + `layout-land/` counterparts, `activity_welcome.xml` (+`layout-sw480dp`, `layout-sw720dp`)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` (fromWelcome mode)
- `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`
- `temp/done/S0143_welcome-screens-overhaul.md`, `temp/done/S0327_device-profile-onboarding.md`
- `docs/FEATURES.md`, `app_v2/src/main/res/values/strings.xml`, `values/strings_input.xml`, `values/strings_setup.xml`

## Findings

### Pages in order

Pager is built in `WelcomeActivity.setupViewPager()`. View types: NORMAL / TOUCH_ZONES / PERMISSIONS / ENHANCED / DEFAULT_PLAYER.

- Index 0 - Welcome (ENHANCED). DECISION page. Language via 3-button toggle group (`LocaleHelper.saveLanguage` + `recreate()` on API<33); device profile via card → shared `DeviceProfilePickerDialogFragment`; profile persisted only at flow end (`saveDeviceProfile(isSkipped)`). Plus 6 fixed feature cards NOT BuildConfig-gated. Layout `page_welcome_enhanced.xml` + land variant.
- Index 1 - Resource Types (NORMAL). INFO page. `welcome_title_2/_description_2/_2_details`. Layout `page_welcome.xml` + land.
- Index 2 - Touch Zones (TOUCH_ZONES). INFO page. Inline scalable 3×3 grid of 9 `MaterialCardView` cells (`touch_zone_*` strings); `welcome_description_3` is passed but never rendered (no `tvDescription` in layout). Layout `page_welcome_touch_zones.xml` + land.
- Index 3 - Resources & Destinations (NORMAL). INFO page. Copy claims "up to 30 colour-coded destinations" - actual cap is `AddResourceUseCase.MAX_DESTINATIONS = 10`; stale in 3 locales. Layout `page_welcome.xml` + land.
- Index 4 - Powerful Extras (ENHANCED). INFO page. Tiles from `buildExtrasFeatureCards()`, each BuildConfig-gated (13 tiles full flavors / 10 photos / 9 lite). Layout `page_welcome_enhanced.xml` + land.
- Index 5 (conditional) - Default Player (DEFAULT_PLAYER). DECISION page. Shown when `BuildConfig.SUPPORTS_DEFAULT_PLAYER && (!defaultPlayerOnboardingShown || !isAlreadyDefaultPlayer)`. Four MIME buttons gated by SUPPORT_AUDIO/VIDEO/IMAGES/DOCUMENTS. Layout `page_welcome_default_player.xml` + land.

Navigation chrome: single bottom bar (Previous | indicator | Skip | Next/Finish) per S0143 ADR-1; Skip jumps to default-player page if present, else completes; per-page pastel backgrounds with forced `MODE_NIGHT_NO`; full D-pad/TV key ownership in `dispatchKeyEvent` (S0289); mouse wheel routed to pager.

### Non-pager permissions step

`finishWelcome()` hides the pager and shows `PermissionsManagementFragment.newInstance(fromWelcome = true)` in `fragment_container_welcome` - permissions are an overlay AFTER the pager, not a page; the page indicator and bottom bar disappear for this step. Fragment is shared with Settings. Permission list from `PermissionRegistryRepositoryImpl`, SDK- and flavor-filtered: required storage (READ_EXTERNAL_STORAGE API 23-32; READ_MEDIA_IMAGES/VIDEO/AUDIO 33+; MANAGE_EXTERNAL_STORAGE 30+ special; MANAGE_MEDIA 31+ special), optional CAMERA, RECORD_AUDIO (SUPPORT_AUDIO), POST_NOTIFICATIONS (33+, gated by ENABLE_PERSISTENT_AUDIO_PLAYBACK), battery-optimization exemption (special). "Grant all" = one runtime batch + special permissions one-by-one via system screens with state surviving config change. Completion paths: Continue tap, grant-all exhausted, or return from a single special screen → `onWelcomeComplete()` → `welcome_completed=true` → first-run Settings redirect (one-shot `first_run_after_welcome`) or MainActivity.

### Dead paths

- PERMISSIONS pager view type fully dead: no page sets `isPermissionsPage=true`; dead are `VIEW_TYPE_PERMISSIONS`, `PermissionsViewHolder`, `onGrantClick`, `page_welcome_permissions.xml` (+land), strings `welcome_permissions_title/_description`.
- `WelcomePage.onSkipClick` declared, never wired.
- Unreferenced strings: `welcome_vr_title/_description`, `welcome_vr_feature_formats/_headset/_stereo`, `welcome_battery_optim_message` (3 locales each).
- `welcome_description_3` passed but never rendered.
- `welcome_page_7_background` unreachable (max 6 pages); night welcome palette unreachable due to forced light mode.
- Pref `media_permissions_granted` written, never read.

### Launch & re-entry

- MainActivity early-returns to WelcomeActivity when `welcome_prefs/welcome_completed` is false; flag set on completing/skipping past permissions.
- App update: flag persists → redesigned onboarding will NOT auto-show to existing users.
- Manual re-entry exists: Settings → General → `btnOpenWelcome`. Re-run re-applies the profile preset on Finish without the warning Settings-side reapply requires, and exits with `CLEAR_TASK` discarding the Settings backstack.
- COUPLING: `RealDeviceProfileRepository.initializeMigrationIfNeeded()` reads `welcome_prefs/welcome_completed` to decide existing-install migration - renaming the pref file/key breaks S0327 migration.

### Per-flavor differences

- Gates: SUPPORTS_DEFAULT_PLAYER false only in lite; SUPPORT_VIDEO/AUDIO false in photos; SUPPORT_CLOUD false in lite; SUPPORT_DOCUMENTS false in lite+photos; ENABLE_TRANSLATION false in lite+photos; ENABLE_ANIMATIONS false in lite.
- Default-player page absent in lite; photos shows only the Images button; legacy on API<29 re-shows the page every run (pre-Q default probe always false).
- Extras grid flavor-gated; page-0 feature cards NOT gated (lite shows a Cloud Storage card with SUPPORT_CLOUD=false - violates S0143 §3.2 rule).
- Profile picker hides the VR tile in non-VR flavors via `DeviceProfileAvailability` (10 vs 11 tiles).
- No flavor source-set overrides of any welcome class/layout exist.

### Educational content inventory

- Page 1 (Welcome): app identity - covered by `docs/FEATURES.md` preamble; loss-free.
- Page 2 (Resource Types): local/network/cloud source kinds + "add in Settings → Resources" - covered by `docs/FEATURES.md` §1 and HOW_TO docs; loss-tolerable.
- Page 3 (Touch Zones): 3×3 player gesture map, remappable - NOT in `docs/FEATURES.md`; equivalent legend EXISTS in-app at Settings → Playback (`fragment_settings_playback.xml` uses the same `touch_zone_*` strings) and in `docs/HOW_TO.md`. Highest-value educational content of the three.
- Page 4 (Resources & Destinations): resource vs destination concept - covered by FEATURES §4 (which also has the correct cap 10, unlike the stale page copy).
- Page 5 (Extras): capability catalogue - covered by FEATURES §§5-15; also duplicated by the (gated) extras grid itself.

### Prior art constraints (S0143, S0327)

- S0143 ADR-1: fixed header + scrollable body template, external bottom nav - keep for new form pages.
- S0143 ADR-2 intent "pages/tiles as data with BuildConfig gating" was only partially implemented (page list hardcoded; page-0 cards ungated).
- S0143 deliberate Skip semantics: never silently exit; Skip routes via default-player page; order "player page → permissions" was justified by "default player needs no permissions".
- S0327: profile selector under language picker; welcome preset apply without confirmation; Skip = AUTO_SKIPPED profile, manual pick = MANUAL_SELECTION; existing installs migrate to OTHER keyed off `welcome_completed`.
- Both archived specs closed at BlockNeedUserTest - device matrices never confirmed; do not inherit their acceptance as proven.

### Surprises / risks for the redesign

- Rotation mid-flow does NOT switch layouts: manifest `configChanges="orientation|screenSize"` + no re-inflate; `layout-land` variants apply only when launched in landscape. Any "form page" redesign inherits this.
- Activity shell uses sw480dp/sw720dp qualifiers while pages use -land - two orthogonal qualifier strategies on one screen.
- Skip pressed early jumps to the default-player page; the eventual Finish records the profile as MANUAL_SELECTION though the user's intent was skip - selection-source semantics are already muddy.
- `fragment_permissions_management.xml` has no landscape counterpart (Rule 11 debt if touched).
- Adapter-held `profileSelectorBinding` works around ViewPager2 rebind unreliability - any interactive page must plan its own refresh path.
- Permanent `S0289DIAG:`/`S0289:` Timber.d tags in WelcomeActivity key handling - stale-tag hygiene depends on S0289 status (flagged for separate cleanup, out of S0395 scope).
- WIP stream-of-consciousness comment block in `updateUI()` about a permissions page that no longer exists.
- MainActivity instantiates the full WelcomeViewModel (with detector + preset use cases) only to read one boolean.

## Options

Relocation options per decorative page content:

- Page 1 (Welcome greeting): KEEP as target page 0 (greeting + language + theme) - no relocation needed; drop the ungated marketing card grid or gate it.
- Page 2 (Resource Types): DROP from onboarding. Concept teaching belongs to the add-resource flow (type picker already names each source kind); FEATURES/HOW_TO retain the reference copy. No in-app relocation needed.
- Page 3 (Touch Zones): DROP from onboarding BUT content must survive in-app because it is the only gesture map a new user meets. Options: (a) rely on the existing Settings → Playback legend (zero work, discoverability poor); (b) first-entry hint overlay in the fullscreen player offering "show touch zones" once (best timing - teaches at the moment of need; new dev-ticket scope); (c) add a Help entry. Recommendation: (b), with (a) as the already-existing fallback; decision finalized in SYNTHESIS.
- Page 4 (Resources & Destinations): DROP. Fix or retire the stale "up to 30" strings in the same dev ticket that deletes the page (they are wrong regardless of the redesign).
- Page 5 (Extras): DROP as a page. Its honest successor is the target functionality page (real toggles instead of marketing tiles) plus the Extensions Manager dialog button.
- Dead-path cleanup rides along: PERMISSIONS view type + layouts + strings, `onSkipClick`, `welcome_vr_*`, `welcome_battery_optim_message`, unreachable background colour, write-only pref.

## Conclusion

The current flow has exactly two decision pages (0 and conditional default-player) and three pure info pages (1, 2, 3 by content - Resource Types, Touch Zones, Resources & Destinations) plus one marketing page (Extras). All four info/marketing pages are droppable: three are documented elsewhere; only the Touch Zones gesture map is not discoverable enough after removal and needs a first-player-launch hint (or explicit reliance on the existing Settings → Playback legend). The permissions step is an overlay, not a page - converting it into target page 3 is a structural change, not a reshuffle. Re-entry from Settings exists and re-applies presets without warning - the redesign must define re-entry semantics deliberately. The `welcome_completed` pref is load-bearing for S0327 migration and must not be renamed.

## Impact on recommendation

- Target page 0/1 split is feasible today: language wiring and profile machinery already exist on page 0; profile needs inline-grid rework (artifact 03).
- Removing decorative pages costs one real relocation (touch zones → player first-entry hint) and one string fix ("up to 30").
- The redesign must include: gating page-0 cards or dropping them; dead-path cleanup; explicit re-entry/pre-population rules (artifact 08); keeping `welcome_prefs` keys stable.
- Existing users will not see the new flow automatically (`welcome_completed` persists) - upgrade exposure is a deliberate choice for artifact 08/SYNTHESIS.
