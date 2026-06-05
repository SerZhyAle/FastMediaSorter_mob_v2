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

    <!-- 1. Trigger control (leftmost) -->
    <com.google.android.material.switchmaterial.SwitchMaterial
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
- Help icon (`ic_help_outline_24`) → **inline next to the title**, opens the tooltip dialog. Hidden when no help payload is configured for the row.
- Trailing action slot is **optional** and reserved for exceptional rows that genuinely need a second action; the default row has no trailing widget.
- `layout_weight="1"` on the text group is mandatory so the trailing slot (when present) does not crowd the text.

#### Reusable component

The canonical implementation is `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow`
(compound view) backed by `view_settings_toggle_row.xml`. New switch rows in
settings fragments and forms MUST use this component instead of hand-rolled
`SwitchMaterial + TextView + ImageButton` triplets. The component encapsulates
title, subtitle, helper visibility, tooltip wiring, and the optional trailing
action slot. Hand-built rows are technical debt and must be migrated when
adjacent code is touched.

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