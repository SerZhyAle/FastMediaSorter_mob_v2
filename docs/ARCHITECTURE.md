# FastMediaSorter v2: Architecture & Flow

**Framework**: Android Native (Kotlin 1.9+, Java 17).
**Pattern**: Clean Architecture + MVVM + Hilt DI.

## Module Structure
- `root/`
  - `app_v2/`: Kotlin, View System + Material3, `compileSdk 35`.
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
- **Domain (`domain/`)**: UseCases. Repository interfaces only.
- **Data (`data/`)**: Repositories, DB, Network.
**Dependency Rule**: `UI` → `Domain` → `Data`.

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

## Standalone Player Toolbar Order (MANDATORY)

The four standalone hosts (`PhotoVideoStandaloneActivity`, `TextStandaloneActivity`, `DocumentStandaloneActivity`, `AudioStandaloneActivity`) share ONE top-toolbar button order so a file feels the same whichever host opened it (S0920). Each host declares its own `activity_standalone_*.xml` (portrait + `layout-land/`), so there is no single shared layout to enforce this - a new host or an edit must follow the order by hand.

Canonical order: `Back -> [paging: Prev, Next, Random, Slideshow] -> Delete -> Favorite -> Share -> Info -> Rename -> [type-specific actions] -> Overflow`.

Rules:

- Rename comes BEFORE the type-specific cluster (Crop/Rotate for image/video, Search/Translate/Copy/Edit for text, PDF/EPUB/Text tools for documents), never after it.
- Type-specific buttons are the only per-host variation; everything before Rename and the trailing Overflow are fixed.
- Keep `layout/` and `layout-land/` in the same order (Rule 11).

## Internet Streams Subsystem

Dedicated screen for internet audio/video/RTSP sources. Architectural boundaries:

- **Entry**: `StreamsActivity` (no business logic) delegates to `StreamsViewModel` and `StreamInlineAudioManager`.
- **Inline audio**: `StreamInlineAudioManager` manages ExoPlayer lifecycle for radio playback directly from the list; exposes ICY now-playing metadata as `StateFlow`; stops or continues on leave depending on the background-audio playback setting.
- **Video/RTSP**: delegates to the existing fullscreen player. `VideoPlayerManager` routes `HTTP_STREAM`/`RTSP_STREAM` to `playStreamVideo` (`StreamPlaybackHelper`), which builds the ExoPlayer media source through `StreamDataSourceFactoryProvider` with a per-session `BandwidthAdaptiveLoadControl` (HLS/DASH/progressive; RTSP where the build's media stack supports it, logged when not). `NetworkAwareMediaSourceFactory` is the audio-service factory (`AudioPlaybackService`/`AudioServiceController`), not the fullscreen-video path.
- **Data flow**: `StreamsViewModel` -> `ImportStreamCatalogUseCase` (with `StreamCatalogCsvParser`, `StreamMediaKindClassifier`, `FaviconAtlasStore`) -> `StreamSourceRepository` -> `StreamSourceDao` / `StreamSourceEntity` (Room). The catalog ships as a mutable GitHub Release asset (`delivery/stream-catalog/`), fetched over HTTP, parsed, and merged de-duplicated by URL.
- **Catalog import**: `ImportStreamCatalogUseCase` enforces a connect+read timeout; fails fast on dead/slow-trickle host instead of blocking indefinitely.
- **Flavor scope**: standard/legacy/noLegal - HLS, DASH VOD, RTSP, progressive HTTP/ICY; lite - progressive-audio only (HLS/DASH/RTSP show unsupported message); photos - feature absent (no entry point).
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