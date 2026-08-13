# 06 - Page 4: Functionality Toggles Semantics

Strategic item: S0395 §6.6. Phase: 02, step 02.4.

## Question

What does each proposed toggle (file-manager mode, audio/video/documents, OCR, translation, VR) actually enable, which downloads/permissions does it involve, and what does "if available" mean per flavor and device?

## Sources

- `PLAN/S0386_ondemand-ocr-translation-delivery.md` + its `INDEX.md`, `delivery/INVENTORY.md`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableInventoryImpl.kt`, `DeliverableDescriptorCatalog.kt`, `DeliverableCapabilityRepositoryImpl.kt`, `RealDeliverableSetDownloader.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/delivery/DeliverableSet.kt`, `DeliverableCapability.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/delivery/ExtensionsManagerFragment.kt`, `DeliveryEnableInterceptor.kt`, `DeliveryPromptDialogFragment.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `ui/settings/fragments/OtherMediaSettingsFragment.kt`, `GeneralSettingsViewSetupHelper.kt`, `core/util/DeviceCapabilities.kt`, `core/util/PermissionHelper.kt`
- `src/vr` / `src/vrStub` XR facade + `app_v2/build.gradle.kts` flavor blocks

## Findings

### Deliverable elements (S0386)

- `set_ocr_engines` - Tesseract `.so` stack, ~7.2 MB arm64 (noLegal/vr adds Paddle, ~16.7 MB; Paddle arm64-only); plus optional `rus`/`ukr` traineddata (~15/11.6 MB); base languages auto-download at first OCR use (tessdata_fast).
- `set_translation` - store flavors: Play dynamic-feature `translate_feature` via SplitInstall (~16.6 MB); noLegal/vr: bundled (always Installed); ML Kit language packs managed by ML Kit itself, not inventory rows.
- `set_audio_visualizations` - 5 mp4 backgrounds, 6.35 MB total, ABI-independent.
- `set_ffmpeg_dts` - libffmpegJNI.so, ~6.6-7.7 MB per ABI.
- Hosting: 27 versioned GitHub Release assets (tag `delivery-so-v1`), SHA-256+size pinned in-app. lite/photos contribute NO bundled-set modules and have no usable descriptors - the Extensions screen still lists all rows there (gap).
- "Installed" = bundled-in-build OR payload downloaded (TRANSLATION on store = SplitInstall module present). `DISABLED_BY_USER` capability state is declared but never emitted.

### Delivery UX today

- Entries: Settings General tab button + Translation/OCR settings group button → full-screen `ExtensionsManagerFragment` (per-element Download / Uninstall+confirm / inline progress).
- Enable-interception (`DeliveryEnableInterceptor.requireInstalled`): exactly 4 call sites - settings OCR toggle, settings translation toggle, player translate apply, camera-OCR start. Modal non-cancelable prompt (size shown, Download/Cancel); decline reverts the toggle to OFF. FFMPEG_DTS and AUDIO_VISUALIZATIONS are NOT intercepted anywhere - silent skip at playback, download only via Extensions screen.
- Downloads are ViewModel-scoped OkHttp flows - no WorkManager, no foreground service, no notification; a download dies with its host screen. Play-policy split is already implemented: `.so` via direct download only outside Play context, translation via SplitInstall on store flavors (APP_NOT_OWNED graceful failure on sideload/debug).

### Toggle → action mapping

| Toggle | Existing setting | Deliverables | Permissions | Availability rule | Enabling today does |
|---|---|---|---|---|---|
| File-manager mode | `AppSettings.allFiles` (+`showHiddenFiles`) | none | MANAGE_EXTERNAL_STORAGE (API 30+, special; requested lazily at main screen / Browse, NOT at toggle) | all flavors | persists; all media types forced-on; predefined "All Files" root resource creatable (`EnsureAllFilesPredefinedResourceUseCase`) |
| Use audio | `AppSettings.supportAudio` | optionally FFMPEG_DTS, AUDIO_VISUALIZATIONS (uncoupled today) | none | `SUPPORT_AUDIO` ceiling (false in photos) | persists; scan/show filter |
| Use video | `AppSettings.supportVideos` | none | none | `SUPPORT_VIDEO` (false in photos) | persists |
| Use documents | NO master - 4 fields: `supportText/Pdf/Epub/OfficeDocuments` | none | none | `SUPPORT_DOCUMENTS` (false in lite+photos) | a welcome toggle must write 4 fields (aggregation decision) |
| OCR | `AppSettings.enableOcr` (default OFF) | OCR_ENGINES + optional rus/ukr data | none | flavor `ENABLE_TRANSLATION` (standard/noLegal/legacy/vr) AND `DeviceCapabilities.ocrSupport` (API>=26 + RAM>=3GB + !lowRam) → legacy 23-25 always Unsupported | interceptor → prompt → download+verify → ON; decline → OFF |
| Translation | `AppSettings.enableTranslation` (default OFF) | TRANSLATION | none | flavor `ENABLE_TRANSLATION`; store path additionally requires Play-acquired install | interceptor → SplitInstall (store) or instant (bundled) → ON |
| VR | none in shared code; real toggle = `MasterTogglePreferences` DataStore in `src/vr` | none - VR is NOT download-deliverable (flavor/source-set property) | none | only builds mounting `src/vr` (noLegal, vr) + runtime XR detection; standard/lite/photos/legacy = vrStub NoOp | n/a in standard - toggle must be HIDDEN there |
| Extensions button | n/a | opens `ExtensionsManagerFragment` | none | screen reachable in all flavors; lite/photos rows unusable (gap) | full element list with statuses |

### Cross-page coupling

- Page-1 profile presets ALREADY write `allFiles` and `support*` fields (CSV rows; e.g. photo_frame sets supportVideos=FALSE) and auto-create the All-Files resource for some profiles - page-4 toggles overlap page-1 presets; precedence must be: preset applies first (page 1), page-4 toggles render post-preset values as their defaults and override on explicit user action.
- Turning OFF a media type while allFiles is ON exits allFiles (`SettingsAllFilesOverride`) - toggle interactions are not independent.

### Gaps the welcome page needs filled

1. No flavor-clean availability contract for OCR/translation/VR visibility (current check = `BuildConfig.ENABLE_TRANSLATION` read in src/main - pre-existing Rule 15 debt); pattern to copy: `DeviceProfileAvailability` + per-source-set Hilt modules.
2. No "documents" master setting - aggregation semantics needed.
3. No onboarding-survivable download runner - downloads die with the screen; "toggle starts download while user keeps swiping" needs app-scoped execution (WorkManager or app-scope coroutine + re-attachable progress), plus a POST_NOTIFICATIONS decision for progress (declared in manifest already).
4. No per-flavor filtering of the Extensions inventory (lite/photos see doomed rows).
5. No prompt-free download API - interceptor is modal-per-toggle; several toggles flipped = sequential modal dialogs. Direct `DeliverableInventory.download()` + inline progress is the page-appropriate path; primitives exist.
6. `DISABLED_BY_USER` tri-state never emitted - needed only if "declined during onboarding" must be remembered distinctly.

## Options

- Documents toggle: one master writing 4 fields (simple, matches owner draft) vs per-format rows (settings parity, heavy).
- Download trigger: flip = enqueue download immediately (background runner) vs collect intents and download on onboarding finish (single progress step) vs keep modal-per-toggle (worst UX on a form page).
- Translation toggle on non-Play installs (debug/sideload standard): hide vs show-with-failure-path.
- VR toggle: emit only in noLegal/vr (maps to XR master pref) vs drop from page 4 entirely (leave to VR settings block).

## Conclusion

All five owner-proposed toggles map to real, existing semantics except VR-in-standard, which maps to nothing and must be hidden by an availability contract (it exists only as the XR master preference in `src/vr` builds). OCR/translation toggles reuse the S0386 enable-path but need a prompt-free, onboarding-survivable download mechanism (app-scoped runner + inline progress) instead of the modal interceptor; audio/video reuse `support*` settings; documents needs a defined 4-field aggregation; file-manager mode reuses `allFiles` with its lazy special-permission pattern (the toggle itself needs no permission at flip time - feeds artifact 05). Page-1 preset overlap requires "preset first, toggles render post-preset defaults" precedence. The Extensions button opens the existing manager screen; its lite/photos inventory filtering should be fixed in an S0386 follow-up, not by page 4.

## Impact on recommendation

- Page 4 is viable in every flavor with per-flavor toggle subsets: standard/legacy = file-manager, audio, video, documents, OCR*, translation*; lite = file-manager, audio, video; photos = file-manager (images-only build); noLegal/vr = full set + VR. (*subject to device capability / Play-acquisition rules.)
- Dev-ticket split input: an availability-contract ticket (also retires the pre-existing ENABLE_TRANSLATION src/main gate debt), a background-download-runner ticket, the page-4 UI ticket consuming both; S0386 verification precedes any download-triggering toggle going live.
- SYNTHESIS owner decisions: documents aggregation (default: one master toggle), download trigger timing (default: enqueue on flip, app-scoped), translation visibility on non-Play installs (default: hide), VR toggle placement (default: only where `src/vr` mounted).
