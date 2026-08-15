# 03 - Page 1: Device Profiles Inline

Strategic item: S0395 §6.3. Phase: 02, step 02.2.

## Question

How many device profiles exist, do they fit as buttons directly on a welcome page across form factors, and what happens to the existing picker dialog?

## Sources

- `app_v2/src/main/java/com/sza/fastmediasorter/data/model/DeviceProfile.kt` (`DeviceProfileType`)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/DeviceProfileUi.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/profile/DeviceProfilePickerDialogFragment.kt`, `DeviceProfileTileAdapter.kt`, `DeviceProfileAvailability.kt`
- `app_v2/src/vrStub/java/com/sza/fastmediasorter/di/VrStubDeviceProfileAvailabilityModule.kt`, `src/vr/java/.../VrDeviceProfileAvailabilityModule.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/detector/RealDeviceProfileDetector.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeViewModel.kt` (selection/save), `WelcomeActivity.kt` (D-pad ownership)
- `app_v2/src/main/res/layout/dialog_device_profile_picker.xml`, `item_device_profile_tile.xml`, `page_welcome_enhanced.xml` (+land)
- `dev/DEVICE_PROFILE_PRESET_MATRIX.md`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsProfileHelper.kt`

## Findings

- 11 profile types in fixed display order: PERSONAL_SMARTPHONE, HOME_TABLET, TV_MEDIA_BOX, CAR_HEAD_UNIT, MEDIA_PLAYER, PHOTO_FRAME, VIDEO_PLAYER, AUDIO_PLAYER, EBOOK_READER, VR_HEADSET, OTHER. Each has icon + title + description strings in EN/RU/UK.
- Availability via `DeviceProfileAvailability` (Hilt per source set): non-VR flavors (vrStub) filter VR_HEADSET → 10 tiles; vr/noLegal → 11.
- Detection priority: VR/car/TV ui-mode and PackageManager features (HIGH) → Chromebook/desk/sw600 tablet vs phone (MEDIUM) → PERSONAL_SMARTPHONE fallback (LOW). If detected profile unavailable in flavor, recommendation downgrades to PERSONAL_SMARTPHONE with LOW confidence. State seeds BOTH recommended and selected with the recommendation - an inline grid renders pre-selected for free.
- Current picker: dialog hosting a non-scrolling wrap-content RecyclerView inside NestedScrollView; columns by smallestWidthDp (>=720 → 3, >=480 → 2, else 1 - deliberately fewer than the welcome feature grid for tile readability); info-rich tile (30dp icon, 16sp title, optional "(Recommended)" badge, 2-line 13sp description, minHeight 76dp); selection = stroke styling; tap = immediate result + dismiss; Settings mode adds an overwrite warning INSIDE the dialog.
- Selection callback `WelcomePage.onProfileSelected` is already plumbed from WelcomeActivity but never invoked by the view holder (dialog path uses `onOpenProfilePicker`) - inline tiles can use the existing lambda; fragment-result listener becomes removable from Welcome.
- ViewPager2 rebind unreliability: page-0 card is refreshed via an adapter-cached binding (`refreshSelectedProfile`); an inline grid needs the same direct re-style path (stroke move), not `notifyItemChanged`.
- Persistence happens only at flow end: Finish → selectedProfile, source MANUAL_SELECTION; Skip → recommendedProfile (selected IGNORED), source AUTO_SKIPPED. Save chain: `saveProfile` → `ApplyProfilePresetUseCase` (~90 settings from owner CSV; OTHER applies nothing) → if `profileImpliesAllFilesUseCase(type)` → `ensureAllFilesPredefinedResourceUseCase()` - presets ALREADY couple some profiles to all-files behavior (input for artifacts 05/06).
- Dialog retention: Settings (`GeneralSettingsProfileHelper`, warnOnApply=true) keeps the dialog alive after welcome goes inline; the welcome-only branches (badge arg, warnOnApply=false) become unused but harmless.
- Fit budget (tile pitch ≈ 88dp): phones sw<480 at dialog's 1-column rule → 10-11 rows ≈ 880-968dp = deep scroll even on a DEDICATED page; sw480-719 at 2 cols ≈ 440-528dp (TV typically lands here - roughly a full screen of grid); sw>=720 at 3 cols ≈ 352dp - comfortable. Compact tile variant (icon+title, no description) roughly halves height on phones at 2 cols.
- D-pad hazard: WelcomeActivity's edge-aware key slider flips the page when LEFT/RIGHT finds no horizontal neighbour - in a 2-3-column grid the row-edge tile is one keypress away from an accidental page flip (the dialog window trapped focus, a page does not).
- The shared `page_welcome_enhanced.xml` also renders the extras page; if the grid lands in a shared layout it must be `<ViewStub>`-gated (Rule 18). Both orientations must change in lockstep (Rule 11).
- No unit coverage exists for the welcome UI chain (only detector/repository/preset use-case tests).

## Options

- Tile form on the dedicated page: (a) reuse info-rich dialog tiles - readable, deep scroll on phones; (b) compact icon+title tiles with description only for the selected one - halves height, new tile variant to build; (c) info-rich but 2 columns on phones (override the dialog's 1-col rule) - squeezes text.
- Skip semantics: keep "Skip saves recommendation" vs change to "Skip saves explicit selection when one was made". Inline buttons make tap-then-Skip likely; current behavior silently discards the tap.
- Column source: dialog sw-rule vs `welcome_feature_grid_columns` vs a new dedicated integer resource tuned per form factor.

## Conclusion

A dedicated profile page (target page 1) is feasible: data, detection, pre-selection, tile layout, strings and preset chain all exist; the work is a grid host on a page + direct-refresh path + retiring the dialog from welcome (dialog stays for Settings). Phone portrait needs a deliberate density decision - recommend compact tiles (icon + title, selected tile shows its description) on a new dedicated column resource (2 cols phone portrait / 2-3 TV / 3 tablet+), keeping the recommended badge. Skip-discards-selection must be fixed alongside (save explicit selection if the user tapped one) - otherwise inline buttons aggravate an existing semantics bug. D-pad: page-1 grid needs explicit focus handling at row edges to avoid accidental page flips.

## Impact on recommendation

- Target page 1 confirmed; its dev ticket carries: dedicated page layout (+land), compact tile variant, column resource, direct-refresh hook, Skip-semantics fix, dialog removal from welcome path.
- Preset→all-files coupling feeds the permissions-ordering question (artifact 05): choosing TV/media-box-like profiles can already imply all-files - page order must account for a permission consequence originating on page 1, not only page 4.
- SYNTHESIS owner-decision candidates: tile density (default: compact), Skip semantics fix (default: save explicit pick), column counts.
