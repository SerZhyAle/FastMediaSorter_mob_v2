# FastMediaSorter v2: Architecture & Flow

**Framework**: Android Native (Kotlin 1.9+, Java 17).
**Pattern**: Clean Architecture + MVVM + Hilt DI.

## Module Structure
- `root/`
  - `app_v2/`: Kotlin, View System + Material3, `compileSdk 36`.
  - `wear/`: Wear OS, Compose.
  - `dev/`: Scripts, specs.
  - `dev/archive/`: READ-ONLY archive.
  - `docs/`: Documentation (MD).
  - `downloads/`: Build results.
  - `scripts/`: Implementation scripts.
  - `store_assets/`: Store assets.
  - `temp/`: **SCRATCHPAD**. Logs/debugs.
  - `web/`: HTML Docs.
  - `test_media/`: Test assets.
  - `app_v2/.../helpers/` - **CRITICAL**: Extracted Player logic.

## Data Flow
`UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`

## Three-Layer Structure
- **UI (`ui/`)**: Observe `StateFlow`. Zero business logic.
- **Domain (`domain/`)**: UseCases, domain models, and repository *interfaces* (their concrete implementations live in `data/repository`).
- **Data (`data/`)**: Repository implementations, DB (Room), network/cloud clients, DTOs.

### Dependency Rule (accepted convention, read before "fixing")

The **runtime call direction** is strictly one-way: `UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`. A lower layer never calls back up, and UI holds no business logic. This part is enforced.

Compile-time dependencies are **not** textbook Clean Architecture. The `domain` layer is deliberately allowed to import concrete `data.*` classes: Room entities and DAOs (`data.local.db.*`), scanners and constants (`data.local.LocalMediaScanner`, `VIRTUAL_PATH_*`), protocol clients (`data.network`/`data.remote`/`data.cloud`), shared enums and DTOs (`data.model.*`, e.g. `DeviceProfileType`), and even concrete repositories (`data.repository.*`). Roughly a third of `domain/*.kt` files import at least one `data.*` type, spread across a dozen-plus `data.*` subpackages. Some repository interfaces in `domain/repository/` also expose `data.model` types in their signatures.

This is a long-standing, consistent project convention - not an accident, and not a violation to refactor on sight:
- The domain layer still owns the repository **interfaces** (`domain/repository/` is interfaces-only, no concrete classes); implementations stay in `data`, so the seam that matters for DI and testing is preserved.
- Shared value types (device-profile enums, media-kind constants, virtual-path markers) are defined once in `data.model`/`data.local` and reused directly, rather than mirrored into parallel domain-owned copies.
- Wrapping every shared enum/constant in a domain-owned abstraction would touch dozens of files for no behavioural gain, so it is intentionally not done.

Implication for new code: importing a concrete `data.*` type from a use case is acceptable and matches precedent. Add a domain-owned abstraction only when it earns a real seam (testing, DI, or flavor isolation via `src/<flavor>/`) - never solely to satisfy layer purity.

## Key Patterns
- **ViewModels**: `@HiltViewModel`. `StateFlow` (state), `SharedFlow` (events).
- **UseCases**: Single-responsibility `VerbNounUseCase`.
- **Manager Pattern**: Delegate complex Activity logic to "Managers". **Mandatory**.
- **Strategy Pattern**: File operations (`FileOperationStrategy`).
- **Connection Pooling**: Network clients (`SmbConnectionManager`).

## UI Patterns - Trigger Row (MANDATORY)

Every toggle/switch or checkbox control that carries a description **must** follow one of the two canonical row patterns below. Mixing the patterns or using ad-hoc sizes is prohibited.

### Pattern A - Switch/Toggle row (settings fragments)

Canonical row layout is `title + helper` inline on the top line, with the
subtitle directly under the title. Prefer the reusable `SettingsToggleRow`
compound view (see "Reusable component" below) over hand-built `LinearLayout`s -
the raw XML below is included for reference and one-off exceptions only.

```xml
<LinearLayout
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:minHeight="@dimen/button_height">

    <!-- 1. Trigger control (leftmost) - canonical on/off class is Material3 MaterialSwitch -->
    <com.google.android.material.materialswitch.MaterialSwitch
        android:layout_marginEnd="@dimen/settings_switch_margin_end" />

    <!-- 2. Text group (fills remaining width) -->
    <LinearLayout
        android:layout_width="0dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <!-- 2a. Title line: title + helper inline (helper sits next to the title) -->
        <LinearLayout
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <!-- Main label: always toggler_title_text_size (14sp) -->
            <TextView
                android:layout_width="wrap_content"
                android:textSize="@dimen/toggler_title_text_size" />

            <!-- Help icon button: inline next to the title (NOT rightmost) -->
            <ImageButton
                android:layout_width="@dimen/settings_help_icon_size"
                android:layout_height="@dimen/settings_help_icon_size"
                android:layout_marginStart="@dimen/settings_help_icon_margin"
                android:src="@drawable/ic_help_outline_24" />
        </LinearLayout>

        <!-- 2b. Subtitle: always toggler_desc_text_size (12sp) = title − 2sp -->
        <TextView
            android:textSize="@dimen/toggler_desc_text_size"
            android:textColor="@color/text_color_secondary" />
    </LinearLayout>

    <!-- 3. Optional trailing action slot (rare; e.g. an extra action button
         that belongs to the row). Empty/hidden by default. -->
</LinearLayout>
```

**Rules:**
- Main label → `@dimen/toggler_title_text_size` (14sp). NEVER hardcode sp values.
- Subtitle → `@dimen/toggler_desc_text_size` (12sp). Always exactly 2sp below the title.
- On/off trigger class → Material3 `com.google.android.material.materialswitch.MaterialSwitch` (the single canonical switch class). Never `SwitchMaterial`/`SwitchCompat` for an on/off setting.
- Help icon (`ic_help_outline_24`) → **inline immediately after the title**; a weighted spacer fills the rest of the title line so the icon stays next to the label and is **never pinned to the right edge**. Opens the tooltip dialog; hidden when no help payload is configured.
- The row's right edge is the **optional trailing action slot** (rule below), not the help icon - do not move the helper there.
- Trailing action slot is **optional** and reserved for exceptional rows that genuinely need a second action; the default row has no trailing widget.
- `layout_weight="1"` on the text group is mandatory so the trailing slot (when present) does not crowd the text.

#### Reusable component

The canonical implementation is `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow`
(compound view) backed by `view_settings_toggle_row.xml`. It embeds the canonical
Material3 `MaterialSwitch`, so wrapping a control in this component is the single
recommended form for every new on/off toggle - in settings fragments, forms, AND
dialogs. New switch rows MUST use this component instead of hand-rolled
`MaterialSwitch + TextView + ImageButton` triplets. The component encapsulates
title, subtitle, helper visibility, tooltip wiring, and the optional trailing
action slot. Hand-built rows are technical debt and must be migrated when
adjacent code is touched.

Any on/off switch that must stay **outside** `SettingsToggleRow` (e.g. a dense
list-item row where the full toggle row would break the layout) MUST be a
`com.google.android.material.materialswitch.MaterialSwitch`; it inherits the
project `materialSwitchStyle` (`themes.xml`) so it matches the switch rendered
inside the component.

#### Selection/value row (`SettingsSelectionRow`)

- The value (`app:ssr_value`) renders inline on the title line, right after the title/help, and the trailing chevron stays pinned to the row's right edge via the weighted text group - the value is never separated from its title by the full row width.
- Navigation mode (`app:ssr_navMode="true"`): the trailing glyph becomes a real forward arrow (`@drawable/ic_arrow_forward`) instead of the value chevron and the content collapses to hug the left so the arrow sits right after the text (the row stays a full-width click target). Use it for rows that open another screen/activity/dialog; value-selection rows keep the chevron. Cross-batch glyph rule shared with S0644: arrow `->` = navigation, chevron `>` = value.

### Pattern B - Checkbox row (add-resource, cloud folder pickers)

```xml
<LinearLayout android:orientation="vertical">

    <!-- 1. Trigger control -->
    <com.google.android.material.checkbox.MaterialCheckBox />
    <!-- MaterialCheckBox default text = 16sp (Material3 bodyLarge) -->

    <!-- 2. Help text: always text_size_small (14sp) = checkbox − 2sp -->
    <TextView
        android:layout_marginStart="@dimen/checkbox_subtitle_margin_start"
        android:textSize="@dimen/text_size_small"
        android:textColor="@color/text_color_secondary" />
</LinearLayout>
```

**Rules:**
- Help text indent → `@dimen/checkbox_subtitle_margin_start` (aligns under checkbox label).
- Help text size → `@dimen/text_size_small` (14sp = MaterialCheckBox default 16sp − 2sp).
- No help icon in Pattern B rows (icon not needed when the trigger is a standalone checkbox).

### Dimen reference

| Dimen key | Value | Role |
|-----------|-------|------|
| `toggler_title_text_size` | 14sp | Switch row main label |
| `toggler_desc_text_size` | 12sp | Switch row help text (title − 2sp) |
| `text_size_small` | 14sp | Checkbox row help text (checkbox − 2sp) |
| `settings_switch_margin_end` | - | Gap between switch and text group |
| `settings_help_icon_size` | - | Help icon button size |
| `settings_help_icon_margin` | - | Gap between text group and help icon |
| `checkbox_subtitle_margin_start` | - | Help text indent under checkbox |

## Button Taxonomy (MANDATORY)

One named Material3 style per semantic role, defined in `values/themes.xml`. The same role must look identical everywhere - do NOT introduce a plain `<Button>`, a raw `Widget.MaterialComponents.*`/`Widget.Material3.*` reference, or a one-off per-screen style for a role already covered below. Pick by the button's role, not by how it should look.

| Role | Style | When to use |
|------|-------|-------------|
| Primary / confirm | `Widget.FastMediaSorter.Button.Filled` | The single main affirmative action of a screen or dialog (Save, OK, Grant, primary CTA). At most one per surface. |
| Secondary emphasis | `Widget.FastMediaSorter.Button.Tonal` | A secondary action that still needs weight next to the primary (alternative confirm, "Use anyway"). |
| Secondary | `Widget.FastMediaSorter.Button.Outlined` | Neutral secondary action paired with a Filled primary (Back, Choose, Browse). |
| Low-emphasis / cancel | `Widget.FastMediaSorter.Button.Text` | Link-like / inline dismiss ("Not now", "Skip") OUTSIDE a dialog action pair; anything that previously used `?android:attr/borderlessButtonStyle`. For a dialog/bottom-sheet confirm-cancel pair use the S0538/S0684 `DialogCancel` slot below (soft-pink tonal), not this style. |
| Icon-only | `Widget.FastMediaSorter.Button.Icon` | Toolbar / inline icon actions that want a Material ripple and 48dp target. |

Dialog action pair (S0538/S0684) - special-purpose, NOT the general role taxonomy. Use these (and only these) for the confirm/cancel pair of any non-system dialog, action-pair bottom sheet, or custom dialog layout. The pair is deliberately asymmetric so a blind finger tap (e.g. while driving) cannot miss or confuse the actions: the confirm/destructive slot is large (min `dialog_action_button_min_height`, ~56dp) and wide (`dialog_confirm_button_min_width`), while the cancel is intentionally shorter (`dialog_cancel_button_min_height`, 48dp) and narrower so the affirmative action dominates. A `dialog_action_button_gap` sits between them. Colour key: green = confirm, soft-pink tonal = cancel, saturated red = destructive confirm only. The "at most one Filled per surface" rule does not apply to this pair.

| Slot | Style | Look |
|------|-------|------|
| Confirm (OK / Save / Apply) | `Widget.FastMediaSorter.Button.DialogConfirm` | Green filled (`@color/confirm_button_bg`), wide (`dialog_confirm_button_min_width`) so it is the dominant "under-finger" action |
| Cancel | `Widget.FastMediaSorter.Button.DialogCancel` | Soft-pink tonal fill (`@color/cancel_button_bg`/`cancel_button_on`), deliberately SMALLER than the green confirm - shorter (`dialog_cancel_button_min_height`, 48dp touch floor) and narrower (content-sized vs the wide confirm) - so confirm dominates and cancel reads as the lighter escape. Saturated red is reserved for `DialogDestructive` only (S0684). |
| Destructive confirm (delete / remove / clear) | `Widget.FastMediaSorter.Button.DialogDestructive` | Red filled (`@color/delete_button`) |

Seam: `MaterialAlertDialogBuilder` dialogs inherit this pair automatically via `materialAlertDialogTheme` on the app theme (positive -> DialogConfirm, negative/neutral -> DialogCancel) - no per-call edit. A destructive builder dialog opts into the red variant with the per-dialog overload `MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)`. Custom inflated layouts apply the named style directly on each `MaterialButton`. OS/system dialogs are exempt (we do not own their chrome).

Rules:

- Apply via `style="@style/Widget.FastMediaSorter.Button.<Role>"` on a `com.google.android.material.button.MaterialButton`.
- Settings surfaces use the `Widget.FastMediaSorter.SettingsButton.*` variants - they inherit the family and only adjust text size/style. Do not fork a new settings button style.
- Colors come from the theme (`?attr/color*`) and the family's shape appearance - never hardcode hex on a button (Rule 19). Use `?attr/`/`@color/`.
- Keep `res/layout/` and `res/layout-land/` in sync (Rule 11); preserve ≥48dp touch target and D-pad/TV focus (Rule 16).
- Compact elements (global): when "Compact elements" is on (`AppSettings.useCompactElements`, default on), unified buttons on a surface that participates in compact mode must shrink with the rest of that surface. Compact scaling is applied per-surface (layout swap such as `custom_player_controls` <-> `custom_player_controls_large`, or a `*SmallControls`/`*CompactElements` manager driven by the setting), not by a single global theme switch - a new compact-aware surface wires its own scaling. EXEMPT: the S0538 dialog action pair keeps its large fixed size even in compact mode (its whole purpose is to stay unmissable).
- Exempt by design (do not migrate to this family): player/media `ImageButton` borderless controls, reserved ExoPlayer `@id/exo_*` controls, and the intentionally dark camera/viewfinder surfaces.
- A new role that none of the five covers is added as a new `Widget.FastMediaSorter.Button.*` style here, not as an ad-hoc layout style.

## Dialog Result Delivery (MANDATORY)

A `DialogFragment` never holds its result callback in a field. `FragmentManager` rebuilds a restored dialog through the no-argument constructor, so any handler the caller assigned after construction is null on the rebuilt instance - the user confirms, nothing happens, and nothing is logged. The recreation does not need a rotation to happen: a theme change, a language change, a font-size change, "don't keep activities" and process death all trigger it, and most hosts here declare `configChanges` for orientation, so rotation is in fact the one trigger that does NOT reproduce it.

The result travels as a `FragmentResult` instead. The dialog declares a `RESULT_KEY`, one payload key per returned value, and a private `ARG_REQUEST_KEY`; `newInstance` takes `requestKey: String = RESULT_KEY` and stores it in `arguments`; `onCreate` reads it back out of `requireArguments()`, so a restored instance recovers it. The confirm path calls `setFragmentResult(requestKey, bundleOf(..))`. The host registers `setFragmentResultListener` in its own `onCreate`/`onViewCreated` - never at the moment the dialog is opened, because a recreated host must have the listener back before the restored dialog resumes. `SearchableLanguagePickerDialog` is the reference implementation (S1214).

Payloads carry Bundle primitives. Where a value is a domain object, put its fields in the bundle and rebuild the object in the host rather than making a domain model `Parcelable`. Where one picker serves many rows, the row id rides in the arguments and comes back in the result bundle, so a single host listener serves them all.

One accepted limitation: when the opening host is a plain `AlertDialog` rather than a `DialogFragment`, the host itself does not survive recreation, so a pick made after recreation is delivered the next time that picker is opened rather than immediately. Making such a host a `DialogFragment` is a separate change per surface.

## Dialog Lifecycle Binding (MANDATORY)

A dialog raised from a helper, manager or any other non-`DialogFragment` holder is shown with `AlertDialog.Builder.showBoundTo(fragment)` (`util/LifecycleDialogExt.kt`), never with a bare `.show()`. The extension registers a lifecycle observer that dismisses the dialog on `ON_DESTROY`, so the window cannot outlive the host.

A bare `.show()` discards the returned `AlertDialog`, which leaves nothing able to close it: a dialog still on screen during a configuration change keeps the destroyed Fragment and Activity alive. The predecessor fix (S1197) tracked the dialog by hand - a field in the helper, a dismiss method, a call from the host `onDestroy` - and that shape needs three coordinated edits per dialog, which is why it was never applied beyond the one helper it was written for while 34 untracked dialogs accumulated in the settings helpers alone (S1447).

Exempt: a `DialogFragment`, whose `FragmentManager` already dismisses it, and OS/system dialogs we do not own.

## Standalone Player Toolbar Order (MANDATORY)

The four standalone hosts (`PhotoVideoStandaloneActivity`, `TextStandaloneActivity`, `DocumentStandaloneActivity`, `AudioStandaloneActivity`) share ONE top-toolbar button order so a file feels the same whichever host opened it (S0920). Each host declares its own `activity_standalone_*.xml` (portrait + `layout-land/`), so there is no single shared layout to enforce this - a new host or an edit must follow the order by hand.

Canonical order: `Back -> [paging: Prev, Next, Random, Slideshow] -> Delete -> Favorite -> Share -> Info -> Rename -> [type-specific actions] -> Overflow`.

Rules:

- Rename comes BEFORE the type-specific cluster (Crop/Rotate for image/video, Search/Translate/Copy/Edit for text, PDF/EPUB/Text tools for documents), never after it.
- Type-specific buttons are the only per-host variation; everything before Rename and the trailing Overflow are fixed.
- Keep `layout/` and `layout-land/` in the same order (Rule 11).

## Directory Operations Subsystem

Create, rename, delete, copy and move a whole folder, for every resource type. Architectural boundaries:

- **Dispatch**: `UnifiedFileOperationHandler` is the only entry point (`executeCreateDirectory` / `executeRenameDirectory` / `executeDeleteDirectory` / `executeCopyDirectory` / `executeMoveDirectory`). Nothing calls a strategy's directory method directly.
- **Pre-flight refusal**: every copy/move passes `refuseUnsafeDirectoryOperation` before a strategy is resolved, so a refused operation never creates a partial structure. It rejects a destination inside the source, a destination that resolves to the source itself (a same-parent copy would overwrite its own input), and a document-tree URI, which the path-based local strategy cannot address. The reason travels as `DirectoryOperationRefusal.Reason` and becomes a user-facing message through `ui/browse/helpers/DirectoryRefusalMessages.kt` - one table, used by the background worker and the folder picker alike.
- **Same protocol**: handled by the per-protocol `FileOperationStrategy` implementation (local, SMB, SFTP, FTP, cloud), each of which owns its own recursive walk. The local walk is cycle-guarded by canonical path and depth-capped.
- **Different protocols**: `DirectoryTreeTransferManager` streams the tree - one directory listing in memory at a time, never the whole tree - creating each destination directory through the destination strategy and transferring each entry through the same per-file path a single file uses. Remote to a different remote goes through one temp file per entry. A move deletes a source entry only after that entry's copy is confirmed.
- **Listing**: `FileOperationStrategy.listEntries` returns `DirectoryEntry` (path, name, isDirectory, size). The interface default is built from `listFiles` plus a per-entry `isDirectory` probe; local and SMB override it from a listing that already carries the type.
- **Progress and cancellation**: `BrowseFileTransferWorker` passes a per-entry callback into the directory operations, rate-limited through the same `TransferProgressReporter` the file path uses; the callback also checks the job, so a cancelled transfer stops at the entry in flight. Already-written entries stay at the destination and the message says so - folder transfers have no undo (S1326).
- **Item applicability**: `BrowseItemOperationPolicy` answers whether a browse row supports an operation. Row binding, the row menu and the action buttons read that answer instead of testing `isDirectory` inline - the split that let "select all" reach a state the user could not reach by hand.

## Internet Streams Subsystem

Dedicated screen for internet audio/video/RTSP sources. Architectural boundaries:

- **Entry**: `StreamsActivity` (no business logic) delegates to `StreamsViewModel` and `StreamInlineAudioManager`.
- **Inline audio**: `StreamInlineAudioManager` manages ExoPlayer lifecycle for radio playback directly from the list; exposes ICY now-playing metadata as `StateFlow`; stops or continues on leave depending on the background-audio playback setting.
- **Video/RTSP**: delegates to the existing fullscreen player. `VideoPlayerManager` routes `HTTP_STREAM`/`RTSP_STREAM` to `playStreamVideo` (`StreamPlaybackHelper`), which builds the ExoPlayer media source through `StreamDataSourceFactoryProvider` with a per-session `BandwidthAdaptiveLoadControl` (HLS/DASH/progressive; RTSP where the build's media stack supports it, logged when not). `NetworkAwareMediaSourceFactory` is the audio-service factory (`AudioPlaybackService`/`AudioServiceController`), not the fullscreen-video path.
- **Stream thumbnails**: both the grid snapshot engine and the fullscreen player's one-shot `TextureView` capture pass decoded frames to `StreamFrameIngestor`. The shared owner rejects empty/recycled or nearly-black frames, then updates `StreamFrameCache` and `StreamFramePersistentStore`. A successful fullscreen adoption returns the stream URL through the Activity Result API so `StreamsActivity` repaints only the matching grid tile; persistence keeps that frame available after restart.
- **Data flow**: `StreamsViewModel` -> `ImportStreamCatalogUseCase` (with `StreamCatalogCsvParser`, `StreamMediaKindClassifier`, `FaviconAtlasStore`) -> `StreamSourceRepository` -> `StreamSourceDao` / `StreamSourceEntity` (Room). The catalog ships as a mutable GitHub Release asset (`delivery/stream-catalog/`), fetched over HTTP, parsed, and merged de-duplicated by URL.
- **Play-outcome side channel (S1502)**: the green/red/amber row status is NOT part of the list state. It lives in its own table, `stream_play_outcome`, reached through `StreamPlayOutcomeDao` -> `StreamSourceRepository.observePlayOutcomes()` -> `ObserveStreamPlayOutcomesUseCase` -> a `StreamsViewModel.playOutcomes` StateFlow the Activity pushes into all four adapters, which repaint only the affected rows. The split exists because Room invalidates per table, not per column: while the outcome sat on the catalog row, every finished reachability probe re-emitted all ~20k rows and forced a full filter, sort and diff pass. The one-shot read for the channel-info window goes through `GetStreamPlayOutcomeUseCase` instead, since that surface renders once and observes nothing.
- **Catalog import**: `ImportStreamCatalogUseCase` enforces a connect+read timeout; fails fast on dead/slow-trickle host instead of blocking indefinitely.
- **Flavor scope**: standard/legacy/noLegal/vr - HLS, DASH VOD, RTSP, progressive HTTP/ICY (`SUPPORT_STREAMS=true`); lite/photos - feature absent, no entry point (`SUPPORT_STREAMS=false`, lite hidden by S0575).
- **Public cleartext**: `android:usesCleartextTraffic` allowed for internet radio (most streams are http://).

## Desktop Companion Config (`.fmscfg`) Subsystem

Imports an SFTP share published by the **Windows desktop companion** (a separate Go/Wails app in its own repository) as ready-made resources, so the user never types host/port/credentials by hand. Not to be confused with the **Wear OS companion** (`wear/`) - unrelated subsystem, same word.

- **Contract ownership**: the schema is a **cross-repo frozen contract**; the authoritative description is the companion repo's `docs/CONFIG_FORMAT.md`, and a canonical test vector is frozen on both ends (`CompanionConfigParserTest`). This repo is authoritative only for the **consumer** half. Do not restate the field list here - it drifts. Producer-side work lives in the external repo (see S0421, `BlockExternal`).
- **Versioning rule**: producer emits a frozen shape, consumer stays tolerant. `schemaVersion` 2 is current, 1 still parses (absent v2 field == v1 default). A *newer* version than supported is a hard `UNSUPPORTED_VERSION` refusal, not a best-effort parse. Additive optional fields (`accessNote`, per-root `readOnly`, IPv6) do **not** bump `schemaVersion`; `CompanionRootDto` field order is contract-frozen (append after `label`).
- **Transports**: plain JSON (payload starts with `{`) for the file share, or `FMSCFG1:` + base64(gzip(json)) for the compact QR path. `FMSCFG1:` is the **transport-envelope marker, not the schema version** - it stays fixed across schema bumps.
- **Data layer**: `CompanionConfigParser` (read side: transport decode -> Gson -> validate) and `CompanionConfigSerializer` (write side: `serialize` plain, `serializeCompressed` for QR) are exact mirrors and round-trip each other. `CompanionConfigDto` mirrors the companion's `CompanionResourceConfig`; `CompanionResourceTokens` maps profile/media-type tokens onto app resource types.
- **Data flow**: `CompanionConfigImportActivity` -> `ImportCompanionConfigUseCase` -> parser -> resource creation; `ExportCompanionConfigUseCase` -> serializer -> `.fmscfg` file or `CompanionQrShareActivity` (`QrCodeEncoder`).
- **Entry points**: `CompanionConfigImportActivity` is `exported=true` with intent filters on `application/octet-stream`, `application/vnd.fms.companion-config+json`, and the `*.fmscfg` path pattern - a shared file opens the import directly. `CompanionQrShareActivity` is `exported=false` (in-app share only).
- **Validation invariants** (consumer-owned): `protocol` must be `sftp`; `accessPaths` is ordered LAN-first then port-forward and is tried in that order; empty password / empty host-key fingerprint are legal Android-side (password typed at import; no-pin TOFU on first connect) even though the producer always sends both.
- **Flavor scope**: the subsystem has **no gate of its own** - it lives in `src/main`, reads no `BuildConfig` flag and consults no capability facade, so it compiles into every flavor. What bounds it is its payload: an imported root is an **SFTP** resource, and the network source group (SMB/SFTP/FTP) is gated by `SUPPORT_LOCAL_NETWORK` via `RemoteSourceAvailabilityGate` / `MediaCapabilities.supportsLocalNetworkSources` - true in standard/photos/legacy/vr/noLegal, **false in `lite`**. Treat "which flavors is this useful in" as a question about the network group, not about this package.

## Immersive VR / OpenXR Subsystem

Immersive VR is a flavor-scoped subsystem: code lives in `app_v2/src/vr/` (packages `core/xr`, `ui/xr`) plus a native OpenXR layer under `app_v2/src/vr/cpp/`. It compiles only in the `vr` and `noLegal` flavors; `standard`/`lite`/`photos`/`legacy` never see it.

**Entry and gating.** `XrEnvironmentDetectorImpl` / `XrDetectionFacadeImpl` detect a headset; `VrMediaSectionContractImpl` gates the VR entry points, so a phone build reports the section unavailable and falls back gracefully. `XrEntryGatewayImpl` + `StartVrPlaybackUseCaseImpl` route a media item into an immersive host. Two hosts exist: `DiagnosticXrActivity` (diagnostic playlist) and `ImmersiveBrowseActivity` (immersive browse grid).

**Native runtime.** `NativeDiagnosticXrRuntime` loads `libfms_diagnostic_xr.so` (built by `app_v2/src/vr/cpp/CMakeLists.txt`) and forwards every session call over JNI to `diagnostic_xr_runtime.cpp`. The native side is single-instance. The `noLegal` flavor ships only the arm64-v8a slice: on x86_64 emulators / non-arm64 devices the library is intentionally absent - `isNativeAvailable` flips to `false` and every call short-circuits to a clean "loader unavailable" outcome. This is an expected device-capability mismatch, not an error (no `UnsatisfiedLinkError` storm in logcat).

**Render thread + EGL/GL confinement.** `DiagnosticXrRenderThread` owns the whole pipeline (init -> attach `Surface` -> start session -> upload texture -> frame loop -> shutdown) and blocks inside the native frame loop for its whole life - it has no `Handler`, and nothing else is posted to it. All GL/OpenXR objects are created and torn down on this one thread, satisfying both EGL and OpenXR thread-confinement rules. The `suspend` modifier on the runtime's setup methods is an API artefact - they execute synchronously on the render thread; hopping to a coroutine dispatcher would create EGL on the wrong thread and leave the render thread without a current GL context (a featureless black composition layer).

**Two texture channels.** The main scene is rendered per frame in native code. The 2D HUD is a separate channel: `HudCanvasRenderer` paints a `Canvas` bitmap (a 1024-wide RGBA panel - status line, AUDIO/SUBS cycle rows, transport buttons + sliders) that is uploaded to a HUD quad via `queueHud` only on state change, never per frame. `SubtitleCueRenderer` feeds subtitle cues into the same HUD channel. HUD interaction is controller-ray UV hit-testing against the quad, not view-level touch.

**Re-entry.** The `XrInstance` is reused across immersive entry/exit. On re-entry `xrCreateSession` runs before Meta Horizon OS re-registers the volumetric window, so the render thread awaits window focus before `startSession`; otherwise the runtime defers readiness and never fires the native ready callback.

Related specs: S0249 (render thread), S0290 / S0964 (HUD quad), S0156 (native library-availability ADR), S0986 (immersive subtitles). VR classes are indexed in the class catalog under `ui/xr` and `core/xr`.

## Launcher Mode

Launcher Mode turns the app into an Android home screen: a cell desktop, a bottom taskbar with a status tray, and placeable gadgets. It is the most restricted subsystem here - more than VR, not less - because it needs **two** independent conditions, a flavor that compiles it and a role only the user can grant.

**Entry and gating.** `LauncherHomeActivity` carries the HOME intent filter and ships `android:enabled="false"`. `LauncherRoleManager` owns the role protocol: it flips that component with `PackageManager.setComponentEnabledSetting`, then asks for the role through `RoleManager.createRequestRoleIntent` on API 29+, or sends the user to `Settings.ACTION_HOME_SETTINGS` below it. Android never hands the HOME role over programmatically - enabling the component only makes the app a *candidate*, and the user chooses. Anything that reasons about "is the launcher active" must ask the role manager, not a build flag. One secondary entry point ships enabled regardless: `LauncherPinRequestActivity`, the `CONFIRM_PIN_SHORTCUT` target other apps use to pin a shortcut into our desktop.

**Flavor seam.** `SUPPORT_LAUNCHER` is true in `standard` and `noLegal` only. Those two flavors mount `src/launcherEnabled` (the entire `ui/launcher/**` tree, its `res`, and an explicitly injected manifest); the rest mount `src/launcherDisabled`, which holds nothing but a no-op `LauncherModeContract` implementation and its Hilt module. The domain and data layers stay in `src/main` and therefore compile into every flavor, self-hiding at runtime through `LauncherModeContract.isAvailableInBuild` - the same shape as Desktop Companion Config above. Per Rule 14 there is no `BuildConfig.SUPPORT_LAUNCHER` branch in `src/main`; the single production read of that flag is the permission registry, which uses it to gate rationale rows.

**Desktop model.** Cells live in one Room table, with `kind` and `orientation` stored as enum names and the command encoded into a single prefixed TEXT column, so a new command variant never forces a migration. Portrait and landscape are **two fully independent layouts**, not one layout re-flowed: every repository call is scoped to a `LauncherOrientation`, and the resolved column count is stored per orientation too. A cell is an anchor plus a span, so gaps between cells are meaningful and a gadget claims a rectangle.

**Grid.** The desktop is a hand-written `ViewGroup`, deliberately not a `RecyclerView` (ADR-9): the persisted model is a canvas with 2D positions, spans and meaningful gaps, which no stock `LayoutManager` expresses - and a desktop is dozens of cells, not a feed, so recycling buys nothing while costing the model. Column count resolves from available width and a user density factor within a fixed range; height is the scroll axis. All footprint arithmetic funnels through one geometry helper precisely so layout, hit-testing and the free-slot sweep cannot disagree. Drag-to-move uses a container-level `OnDragListener` with `startDragAndDrop` rather than `ItemTouchHelper`, which is RecyclerView-only for the same ADR-9 reason.

**Gadgets.** A gadget is an interactive block the user places on the desktop, and it is always **our own view - never a third-party `AppWidget`** (ADR-5): hosting foreign widgets means foreign layout outside our control and breaks the D-pad contract, so instead the pre-existing home-screen widget catalog is *bridged* into gadgets rather than duplicated. The registry is an open extension point fed by qualified Hilt list multibindings; treat the set of gadgets as growing, and read the current membership from the registry rather than from any document. Gadget lifecycle is enforced in one place: the view starts its work in `onActive` under `repeatOnLifecycle(STARTED)` and cancels on detach, because the grid is not a `RecyclerView` and there is no `onViewRecycled` to lean on.

**Taskbar and command funnel.** The taskbar is bottom-anchored in both orientations, hosting the Start button, the recents and pinned strips, and the status tray. Each tray indicator subscribes to its source *only* while that indicator is switched on and the launcher owns the status area, and going false cancels the collector rather than merely hiding the view; an indicator whose state cannot be read is absent rather than drawn as "off". Every tap on every surface - desktop cell, either taskbar strip, Start menu row, gadget-issued command - funnels through a single guarded execution path on the launcher's ViewModel, so there is exactly one launch guard and one failure message, and a gadget never builds a parallel one.

Related specs: S0404 (the founding ADR set, archived), S1103 (cell actions), S1170 (widget-to-gadget bridge), S1415 (tray composition), S1461 (this section). Launcher classes are indexed in the class catalog under the `launcher` sector.

## Performance & Resource Optimization

To maintain fast startup times (cold start), low memory consumption, and efficient CPU usage, the following patterns must be strictly enforced:

### 1. Lazy Dependency Injection (dagger.Lazy)
Heavy singletons, network managers, and protocol clients (e.g., `SmbClient`, `SftpClient`, `DropboxClient`) must NOT be eagerly injected into global scopes like `Application` or entry points like `PlayerActivity`. 
- **Rule:** Wrap heavy/optional dependencies using `dagger.Lazy<T>` and retrieve them via `.get()` only when requested.
- **Example:**
  ```kotlin
  @Inject lateinit var smbClient: dagger.Lazy<SmbClient>
  ```

### 2. Layout Optimization via ViewStub
Do not use `android:visibility="gone"` for complex, format-specific, or optional layout elements (e.g., search overlays, specific player controls, game modules) in main activity XML layouts. 
- **Rule:** Declare optional layout overlays inside a `<ViewStub>` and inflate them programmatically on demand. This avoids parsing overhead and unnecessary View hierarchy memory allocations on startup.
- **Example:**
  ```xml
  <ViewStub
      android:id="@+id/searchPanelStub"
      android:layout="@layout/player_search_panel_content"
      android:layout_width="match_parent"
      android:layout_height="wrap_content" />
  ```

### 3. On-Demand Media Lifecycle Management
Media players (`ExoPlayer`, `MediaPlayer`) and image loading caches (Glide) must only allocate system resources (decoders, native memory) when active playback is running.
- **Rule:** Release media player resources (`release()`) immediately when pausing, transitioning to other media types, or backgrounding the activity. Avoid preloading multiple heavy assets unless explicitly requested.

### 4. Dynamic OS Component Gating
Optional background elements like widget receivers (`AppWidgetProvider`) should not consume system resources when disabled by user settings.
- **Rule:** Use `PackageManager.setComponentEnabledSetting` to dynamically enable or disable widget receivers, services, or activities at runtime depending on the configuration in `AppSettings`.
- **Example:**
  ```kotlin
  context.packageManager.setComponentEnabledSetting(
      ComponentName(context, GameLaunchWidgetProvider::class.java),
      if (enabled) COMPONENT_ENABLED_STATE_ENABLED else COMPONENT_ENABLED_STATE_DISABLED,
      DONT_KILL_APP
  )
  ```

## Collapsible Section Groups (MANDATORY)

New screens with collapsible/expandable sections MUST use the unified pattern (S0535) - do not build a bespoke header or persistence mechanism.

- **Header:** one widget `CollapsibleSectionHeader` (`ui/common/widget/`) - a clickable row with a graphical chevron indicator that rotates on toggle, an optional collapsed-state summary slot (`setSummary(..)`), and a bold title (the unified typography token - bold on every screen).
- **Orchestrator:** `CollapsibleSectionsManager.register(header, container, key, defaultExpanded, onExpandedChanged?)` binds a header to its content container, animates the body open/close, announces expanded/collapsed for TalkBack, and persists state. The optional `onExpandedChanged` hook supports lazy first-expand work (e.g. attaching a child fragment on first expand).
- **Store:** `CollapsibleSectionStore` over one consolidated SharedPreferences namespace (`collapsible_sections_state`). `CollapsibleSectionStateMigration` folds the legacy per-screen namespaces in once on upgrade (copy-only, guarded, idempotent).
- **Keys:** `<screen>__<section>` (e.g. `general__interface`, `operations__safety`, `media__vr`, `resource_editor__connection`).
- **Default expansion:** dense config screens (settings, source editors) and list groupings collapsed; short dialogs (folder picker) expanded; player overlay panels collapsed until activated.
- **Accessibility:** state announced via `ViewCompat.setStateDescription` (API 30+) with a `contentDescription` fallback below; chevron tinted via theme attribute (`?attr/colorOnSurfaceVariant`, override per-context with `csh_chevronTint`); no hardcoded colors.
- **List consumers** (RecyclerView section headers, e.g. Statistics/Keybinding) build the `CollapsibleSectionHeader` programmatically and bind it via `setTitle`/`setExpanded`/`setOnExpandedChangeListener`.
