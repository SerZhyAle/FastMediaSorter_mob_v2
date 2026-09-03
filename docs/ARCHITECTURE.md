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

### Database Schema Contract (MANDATORY, S2306)

A Room migration and the entity it upgrades toward are **two halves of one statement**, and Room checks
that they agree exactly once - on the user's device, during the first launch after an update. When they
disagree it does not degrade: `DatabaseModule` catches the failed open, copies the database to
`files/db-backups/<timestamp>`, records a user-visible notice and recreates it empty (S0731). There is no
`fallbackToDestructiveMigration`; this recovery path is the only way the database is ever dropped, and it
is a last resort, not a licence to ship an unverified migration.

The watch module carries the same contract and a deliberately different recovery (S2356). `WearAppModule`
also forces the open and also declares no `fallbackToDestructiveMigration`, but on failure it recreates the
store and rebuilds the note index from the recordings on disk rather than copying the old file aside: the
watch database is an index over app-private files, not the only copy of the data, and a backup written to
app-private storage is unreachable on a watch - there is no file manager and the media index does not scan
private directories (ADR-1). A recovered note is reset to `LOCAL_ONLY`, so nothing is delivered twice, and
`WearDatabaseResetNotice` explains the rebuild once in the note list. Read the two paths as one policy with
two shapes: back up what is irreplaceable, rebuild what is derivable.

Every schema change therefore carries all four of these, and none substitutes for another:

1. The migration's SQL names the column exactly as the entity declares it - `screenIndex` is not
   `screen_index` - with the same nullability, and it writes any `@ColumnInfo(defaultValue = ..)` the
   entity declares. A `NOT NULL` column added without a `DEFAULT` is refused by SQLite outright.
2. The migration is listed in `DatabaseModule.addMigrations()`. An unregistered migration is not a
   no-op: the hop throws and the recovery path deletes the database.
3. An instrumented test `AppDatabaseMigration<N>To<M>Test` exists, and `AppDatabaseMigrationChainTest`
   still names the current version so the whole chain stays covered.
4. The migrations were **executed**, not only compiled - `.\a.ps1 fam`, and `/spec-prerelease` step 1.4
   before a release. A green `.\a.ps1 fa` proves the tests parse and nothing more.

Points 1-3 are gated in every closure by `assert-migration-schema-conformance.ps1` and
`assert-migration-test-pairing.ps1`. They compare text; only point 4 runs SQL. The rule exists because on
2026-09-01 a migration satisfying none of them shipped, and the first execution of Room's comparison
anywhere was on the owner's phone, which lost its resources, credentials, favourites and desktop (S2251).

### Dependency Rule (accepted convention, read before "fixing")

The **runtime call direction** is strictly one-way: `UI` → `ViewModel` → `UseCase` → `Repository` → `DataSource`. A lower layer never calls back up, and UI holds no business logic. This part is enforced.

Compile-time dependencies are **not** textbook Clean Architecture. The `domain` layer is deliberately allowed to import concrete `data.*` classes: Room entities and DAOs (`data.local.db.*`), scanners and constants (`data.local.LocalMediaScanner`, `VIRTUAL_PATH_*`), protocol clients (`data.network`/`data.remote`/`data.cloud`), shared enums and DTOs (`data.model.*`, e.g. `DeviceProfileType`), and even concrete repositories (`data.repository.*`). Roughly a third of `domain/*.kt` files import at least one `data.*` type, spread across a dozen-plus `data.*` subpackages. Some repository interfaces in `domain/repository/` also expose `data.model` types in their signatures.

This is a long-standing, consistent project convention - not an accident, and not a violation to refactor on sight:
- The domain layer still owns the repository **interfaces** (`domain/repository/` is interfaces-only, no concrete classes); implementations stay in `data`, so the seam that matters for DI and testing is preserved.
- Shared value types (device-profile enums, media-kind constants, virtual-path markers) are defined once in `data.model`/`data.local` and reused directly, rather than mirrored into parallel domain-owned copies.
- Wrapping every shared enum/constant in a domain-owned abstraction would touch dozens of files for no behavioural gain, so it is intentionally not done.

Implication for new code: importing a concrete `data.*` type from a use case is acceptable and matches precedent. Add a domain-owned abstraction only when it earns a real seam (testing, DI, or flavor isolation via `src/<flavor>/`) - never solely to satisfy layer purity.

### Rule 3 is mechanically enforced (S1329)

An Activity may not declare an `@Inject` field of a repository, use case, data source, DAO or database type. The count is held down by `scripts/quality/assert-activity-logic-not-growing.ps1` against a committed baseline, so a new violation fails the gate rather than joining the lint baseline unnoticed. The remaining debt is 32 violations, all inside `PlayerActivity` and `PhotoVideoStandaloneActivity`, which carry a shared image-edit cluster and are being cleared by a follow-up ticket. Two fixes are sanctioned: move the dependency into the host's ViewModel and expose behaviour rather than the injected type, or - when the host only forwards the object into a manager it builds by hand - put it in an `@Inject constructor` factory that builds that manager, as `app_v2/src/main/java/com/sza/fastmediasorter/widget/PhotoCaptureLaunchManagerFactory.kt` does. A screen that merely reads settings needs neither: `BaseActivity.appSettings` is the inherited stream, and reaching for `SettingsRepository` in a subclass is the mistake it exists to prevent.

## Key Patterns
- **ViewModels**: `@HiltViewModel`. `StateFlow` (state), `SharedFlow` (events).
- **UseCases**: Single-responsibility `VerbNounUseCase`.
- **Manager Pattern**: Delegate complex Activity logic to "Managers". **Mandatory**.
- **Strategy Pattern**: File operations (`FileOperationStrategy`).
- **Connection Pooling**: Network clients (`SmbConnectionManager`).

## One Visual Form Per Element Role (MANDATORY, S2193)

A UI element's visual form is declared once per module, by the role that owns it - a named reusable component, or a named style/taxonomy entry - never redeclared per screen. This is the general statement behind several rules already in force separately: the Trigger Row patterns and the Button Taxonomy below own every toggle, checkbox and button role in `app_v2`; `S2006` ADR-2 ("a command button is declared once per module") owns the wear player's controls; `S2133` ADR-2/ADR-4 own the wear dialog toggle and its mechanical gate. A hand-rolled duplicate of an already-owned role is technical debt, migrated opportunistically when its screen is next touched - never a forced rework campaign, the same policy Pattern A below already states for its own hand-built rows.

## Caption and Value Proximity (MANDATORY, S2328)

A value is read together with the caption that names it, so the two must stay adjacent at every screen width. The gap between them is set by the text, never by the display.

- **The caption hugs its own text and carries no `layout_weight`.** A weighted caption takes the row's whole free width, which is what pushes the value to the far edge.
- **The value takes the remaining width** (`layout_width="0dp"` + `layout_weight="1"`) **and stays start-aligned inside it**, so it begins immediately after the caption. Giving the leftover width to the value rather than the caption also means a long value wraps or truncates in its own box instead of shoving the caption aside.
- **The row's slack falls after the value, never between the pair.** Where a trailing element must stay pinned to the row's end, put the weighted spacer *after* the value - the shape `view_settings_selection_row.xml` already uses.
- **Never `android:gravity="end"` on a value**, and in `ConstraintLayout` never pin a value to the parent's end without also constraining its start to the caption.
- **A shared label column is sized by the longest caption in the group, never by the container width**, and drops itself when it does not fit - see `SettingsValueRowGroup`. Proximity outranks a straight column of glyphs: when the two conflict, proximity wins.

Reference layouts: `view_settings_selection_row.xml`, `view_settings_toggle_row.xml`, `view_settings_dropdown_row.xml`.

A **control** at the row's end - a switch, a chevron, an icon button - is not a value, and this section does not apply to it; that is the Trigger Row pattern below and it is unaffected.

Mechanical gate: the `caption-value-split` rule in `scripts/quality/lib/source-matchers.ps1` (in `post-change.ps1` via the neuroslop umbrella, and `.\a.ps1 fg`). It is structural rather than lexical, because the reference row and the defect carry the same attributes and differ only in where they sit. Its baseline also carries a known ambiguity class - two co-equal data columns, such as a player's position/duration pair - which the rule cannot distinguish from a caption and its value; if a new row is genuinely that shape, justify it in review instead of raising the baseline.

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

- The value (`app:ssr_value`) renders inline on the title line, right after the title/help - it is **never** separated from its caption by the width of the row. This is the owner's standing rule for every caption/value pair in the app, not a detail of this widget (ruling 2026-09-01, on a landscape screenshot where a right-aligned value sat "meters" from its label across a 2000 px card): a value pushed to the far edge aligns beautifully and loses the reader on the way across. Align a column of values by the longest LABEL, never by the screen edge.
- Navigation mode (`app:ssr_navMode="true"`): the trailing glyph becomes a real forward arrow (`@drawable/ic_arrow_forward`) instead of the value chevron and the content collapses to hug the left so the arrow sits right after the text (the row stays a full-width click target). Use it for rows that open another screen/activity/dialog; value-selection rows keep the chevron. Cross-batch glyph rule shared with S0644: arrow `->` = navigation, chevron `>` = value.
- Shared label column (`SettingsValueRowGroup`): wrap a stack of value rows in that container and every row gets the same label width - the widest label among them - so their values start at one offset while each value still sits right beside its own caption. The column is sized by the longest LABEL, never by the group's width, and it is applied **only when every row still fits at full length**: a fixed label column plus a long value plus a glyph does not fit a portrait phone, and the first attempt wrapped one value onto a second line and pushed two chevrons off the screen. When it does not fit, each row keeps its own hug layout - alignment is worth having only while it costs nothing. Both row widgets implement `LabelColumnRow`, so a group may mix them.
- A `SettingsDropdownRow` in value text mode (`app:sdr_valueAsText`) is a value row and carries the same chevron, not the field's drop-down caret: the two widget types sit in the same settings sections, and a differing glyph read as two different kinds of control where there is only one.

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

#### Reusable component

The canonical implementation is `com.sza.fastmediasorter.ui.common.widget.FormCheckboxRow`
(compound view) backed by `view_form_checkbox_row.xml`. The subtitle is **optional**:
`setSubtitle(null)` / an empty `app:fcr_subtitle` hides the subtitle view without breaking row
layout, so this component is the single canonical form for every checkbox row - with or without a
subtitle, including a short fixed-choice grid (e.g. a media-type filter row) laid out with
`layout_weight`/`layout_columnWeight`. A raw `MaterialCheckBox` outside this component (S2193
inventory: 17 file entries, mostly a duplicated media-type filter set with no shared style) is
technical debt, migrated opportunistically per the same policy as Pattern A - not a required
rework whenever the debt is merely noticed.

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
| Destructive (standalone) | `Widget.FastMediaSorter.Button.Destructive` | A non-dialog destructive action that sits next to a neutral action and must not read lighter/secondary than it (e.g. "Delete all" beside "Install all" in a screen footer). Standard Filled sizing - NOT the oversized dialog-pair `DialogDestructive` below, which would visually dominate a standalone surface (S2179). |

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

## UI Toolkit Boundary (MANDATORY)

`app_v2` is View: XML layouts and ViewBinding. `wear` is Compose end to end and owns no XML layout at all. A new screen in `app_v2` is built in View, and a new `setContent { .. }` under `app_v2/src/main` is refused by the `compose-island` dimension of `scripts/quality/assert-source-gates.ps1` (CLAUDE.md Rule 32, S1694).

The boundary is drawn by module rather than by screen because that is where the technical necessity already sits: on the watch Compose has no reasonable alternative, and in the phone app 404169 lines of View have no reason to move. Converting `app_v2` to Compose was proposed and rejected by the owner on 2026-08-15 - it is a rewrite with no user-visible result and a large regression surface.

Six islands exist and are allowed to: the Wear companion settings screen, the beam animation dialog, and the four widget-configuration screens (resource shortcut, photo frame, camera quick capture, network monitor). They are removed **opportunistically** - when another ticket reaches one for its own reasons - never as a campaign. Each removal lowers the baseline in `scripts/quality/compose-island-baseline.txt` by one. The baseline is a ceiling that only descends; raising it is a boundary decision, not a build fix.

Why a gate and not only this paragraph: the fifth-to-sixth island appeared five days after an audit had counted five, without anyone deciding to grow the set. A rule addressed to someone who is *already* writing an island (the theming section below) is read at the right moment; a rule addressed to someone still choosing a toolkit is not.

### Removing Compose from app_v2 entirely

Only possible once the last island is gone, and it has a precondition that is **not** discharged by removing the island:

- `Icons.Default.Pause`, `Icons.Default.SkipNext` and `Icons.Default.SkipPrevious` exist only in `androidx.compose.material:material-icons-extended`, not in `material-icons-core`. All three are still in use. They must be migrated to vector drawables **first**, or the build stops compiling - which is exactly what happened in S0385 when a dependency audit declared the extended set dead.
- Only then do the Compose lines in `app_v2/build.gradle.kts` come out: the Compose compiler plugin, `compose = true`, the BOM, and the `androidx.compose.*` / `activity-compose` / `lifecycle-viewmodel-compose` dependencies.
- The argument for removal is dependency hygiene, not APK size: R8 already strips the unused extended icons from a release build.

## Compose Island Theming (MANDATORY)

Every `ComposeView.setContent { .. }` in `app_v2` wraps its content in `FastMediaSorterComposeTheme` (`ui/common/compose/`). The app is View-based and its colours live in a View theme - `Theme.FastMediaSorter.App` plus whichever `ThemeOverlay.FastMediaSorter.*` accent the user picked (S0569). Compose reads none of that: an unthemed `setContent`, and equally a bare `MaterialTheme { .. }` with no `colorScheme` argument, falls back to the Material3 baseline palette and renders in stock purple no matter which accent is active. The island then looks foreign next to the Views around it, and an `AndroidView` hosted inside it - which does inherit the View theme - disagrees with its own container.

The wrapper resolves the M3 colour attributes off the host `Context` at composition time and hands them to `MaterialTheme` as a `ColorScheme`, so an island follows the accent overlay and the day/night variant without a second source of colour. Light-vs-dark is decided from the resolved surface luminance rather than the system night mode, because the accent overlays set brightness independently of it.

Rules:

- Never call `setContent` without the wrapper, and never re-introduce a bare `MaterialTheme { .. }` as the outermost layer.
- Read colours inside a Composable from `MaterialTheme.colorScheme`, never as a literal `Color(0xFF..)` - the literal is the Compose equivalent of the hardcoded layout hex Rule 19 already forbids.
- A role the wrapper cannot resolve falls back to the Compose baseline for that role. When a new role starts mattering on screen, add the matching `?attr/` to the theme and the accent overlays rather than hardcoding it in the Composable.

## Dialog Result Delivery (MANDATORY)

A `DialogFragment` never holds its result callback in a field. `FragmentManager` rebuilds a restored dialog through the no-argument constructor, so any handler the caller assigned after construction is null on the rebuilt instance - the user confirms, nothing happens, and nothing is logged. The recreation does not need a rotation to happen: a theme change, a language change, a font-size change, "don't keep activities" and process death all trigger it, and most hosts here declare `configChanges` for orientation, so rotation is in fact the one trigger that does NOT reproduce it.

The result travels as a `FragmentResult` instead. The dialog declares a `RESULT_KEY`, one payload key per returned value, and a private `ARG_REQUEST_KEY`; `newInstance` takes `requestKey: String = RESULT_KEY` and stores it in `arguments`; `onCreate` reads it back out of `requireArguments()`, so a restored instance recovers it. The confirm path calls `setFragmentResult(requestKey, bundleOf(..))`. The host registers `setFragmentResultListener` in its own `onCreate`/`onViewCreated` - never at the moment the dialog is opened, because a recreated host must have the listener back before the restored dialog resumes. `SearchableLanguagePickerDialog` is the reference implementation (S1214).

Payloads carry Bundle primitives. Where a value is a domain object, put its fields in the bundle and rebuild the object in the host rather than making a domain model `Parcelable`. Where one picker serves many rows, the row id rides in the arguments and comes back in the result bundle, so a single host listener serves them all.

One accepted limitation: when the opening host is a plain `AlertDialog` rather than a `DialogFragment`, the host itself does not survive recreation, so a pick made after recreation is delivered the next time that picker is opened rather than immediately. Making such a host a `DialogFragment` is a separate change per surface.

## Dialog Lifecycle Binding (MANDATORY)

A dialog raised from a helper, manager or any other non-`DialogFragment` holder is shown with `AlertDialog.Builder.showBoundTo(fragment)` (`util/LifecycleDialogExt.kt`), never with a bare `.show()`. The extension registers a lifecycle observer that dismisses the dialog on `ON_DESTROY`, so the window cannot outlive the host. A site that needs the dialog before showing it calls `create()` and then the same `showBoundTo(owner)` on the created `AlertDialog`.

The rule has a ratchet gate behind it: `scripts/quality/assert-untracked-dialogs.ps1` counts builder chains ending in a bare `.show()` across every shipped source set and fails when the count grows. It runs inside `post-change.ps1` through the source-gate runner, so a new untracked dialog fails closure rather than waiting to be noticed in review (S1456).

A bare `.show()` discards the returned `AlertDialog`, which leaves nothing able to close it: a dialog still on screen during a configuration change keeps the destroyed Fragment and Activity alive. The predecessor fix (S1197) tracked the dialog by hand - a field in the helper, a dismiss method, a call from the host `onDestroy` - and that shape needs three coordinated edits per dialog, which is why it was never applied beyond the one helper it was written for while 34 untracked dialogs accumulated in the settings helpers alone (S1447).

Exempt: a `DialogFragment`, whose `FragmentManager` already dismisses it, and OS/system dialogs we do not own.

## Landscape Layouts under `configChanges` (MANDATORY)

An Activity that lists `orientation` in `android:configChanges` does not recreate on rotation, and an Activity that does not recreate never re-inflates its layout. Its `layout-land/` (or `layout-w600dp/`) variant therefore applies only when the screen is opened while already in that configuration - a file that looks live, is referenced by nothing, and drifts silently. S1549 found sixteen screens in this state.

Owning a landscape layout and absorbing the rotation is the pair that must never coexist. Three resolutions count as fixed, and only the applied result matters, not the means (ADR-2a):

- Stop absorbing - drop `orientation|screenSize` from the manifest entry, so the system re-inflates and resolves the variant itself. Requires that nothing is lost on recreation; state that would be lost goes through `onSaveInstanceState` first.
- Re-apply in code - keep absorbing (a live media surface, an unfinished user action) and set the landscape values in the rotation handler. Every value comes from a qualified resource, never a literal, so the two orientations stay declared in `values/` and `values-land/`.
- Delete the file - a landscape variant that encodes no difference is dead weight, not a fix to apply.

Two traps worth knowing before touching this. **`values-w600dp` outranks `values-land`**, and it matches a tablet or an unfolded foldable held in *portrait*: a landscape-only override under a `layout-w600dp` variant re-applies portrait metrics over a wide-layout tree. **A partial re-inflate has no seam** - `BaseActivity` assigns its ViewBinding once and never reassigns it, so swapping a subtree leaves every binding field and every helper built from it pointing at discarded views, silently. **And re-pointing every reference is still not enough**: a view whose state was set once, imperatively, at load time - and is never re-derived from any observable state - comes back at its XML default and nothing restores it. S1943 lost stream video to exactly this, `PlayerView` being declared `gone` and revealed only by the one-shot call in the media loader, so the surviving ExoPlayer decoded into a hidden surface for good. When you inventory what a re-inflate must carry, list one-shot view state beside the reference holders; a reference audit alone will not find it.

Gate: `scripts/quality/assert-orientation-layout-pairing.ps1`, wired into `.\a.ps1 fg` and `post-change.ps1`. Exceptions live in `scripts/quality/orientation-layout-pairing-exceptions.txt`, and every entry carries a mandatory `#` reason naming both what the screen would lose on recreation and where its re-apply lives - a list without reasons is indistinguishable from a list of forgotten defects.

## Standalone Player Toolbar Order (MANDATORY)

The four standalone hosts (`PhotoVideoStandaloneActivity`, `TextStandaloneActivity`, `DocumentStandaloneActivity`, `AudioStandaloneActivity`) share ONE top-toolbar button order so a file feels the same whichever host opened it (S0920). Each host declares its own `activity_standalone_*.xml`, so there is no single shared layout to enforce this - a new host or an edit must follow the order by hand.

Canonical order: `Back -> [paging: Prev, Next, Random, Slideshow] -> Delete -> Favorite -> Share -> Info -> Rename -> [type-specific actions] -> Overflow`.

Rules:

- Rename comes BEFORE the type-specific cluster (Crop/Rotate for image/video, Search/Translate/Copy/Edit for text, PDF/EPUB/Text tools for documents), never after it.
- Type-specific buttons are the only per-host variation; everything before Rename and the trailing Overflow are fixed.
- These four layouts have **no** `layout-land/` variant, and re-creating one is a defect (S1549). The landscape copies existed but were byte-identical to the portrait files, and the hosts declare `configChanges` for orientation - so Android never re-inflated them and the copies could not have applied even if they had differed. Rule 11 parity therefore has nothing to keep in sync here; a real landscape difference on these screens has to be applied by the orientation gate, not by a second file.

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
- **Play-outcome side channel (S1502, re-keyed by S1832)**: the green/red/amber row status is NOT part of the list state. It lives in its own table, and since S1832 that table is `stream_user_state`, keyed by the channel's derived identity rather than by the catalog row id, so the history survives a prune and a later re-import. `stream_play_outcome` was dropped in schema 53 after `MIGRATION_51_52` copied every outcome across. Reads go `StreamSourceDao.observePlayOutcomesByRowId()`, which joins identity back to the current row id so every consumer keeps the map-keyed-by-id contract it was written against, -> `StreamSourceRepository.observePlayOutcomes()` -> `ObserveStreamPlayOutcomesUseCase` -> a `StreamsViewModel.playOutcomes` StateFlow the Activity pushes into all four adapters, which repaint only the affected rows. Writes and the clear-all action go through `StreamUserStateDao`. The split exists because Room invalidates per table, not per column: while the outcome sat on the catalog row, every finished reachability probe re-emitted all ~20k rows and forced a full filter, sort and diff pass. The one-shot read for the channel-info window goes through `GetStreamPlayOutcomeUseCase` instead, since that surface renders once and observes nothing.
- **Catalog import**: `ImportStreamCatalogUseCase` enforces a connect+read timeout; fails fast on dead/slow-trickle host instead of blocking indefinitely.
- **Flavor scope**: standard/legacy/noLegal/vr - HLS, DASH VOD, RTSP, progressive HTTP/ICY (`SUPPORT_STREAMS=true`); lite/photos/foss - feature absent, no entry point (`SUPPORT_STREAMS=false`, lite hidden by S0575; foss carries no streaming stack because it ships no proprietary SDKs).
- **Public cleartext**: `android:usesCleartextTraffic` allowed for internet radio (most streams are http://).

## Cast (Chromecast) Path

Casting is a flavor-scoped seam: `CastController` lives in `src/main`, its Google Cast implementation in `src/castEnabled`, and the `vr` flavor mounts `src/castDisabled` instead. Local files reach the receiver through `LocalCastProxyServer`, which used to serve bytes unchanged.

- **Single-eye crop (S1558)**: a stereo file cast while the single-eye panel option is on is served as a half-frame copy, not as the original. The player resolves the crop - `CastStereoCrop` carries the decision the panel already made across the seam, so the Cast path never re-detects the stereo mode and cannot disagree with the screen. The geometry mirrors `PanelStereoCropApplier`: the right half for side-by-side modes, the bottom half for over-under.
- **Where the bytes change**: `CastStereoCropTranscoder` (castEnabled only) writes the cropped copy into the cache and the proxy serves that copy, so the proxy itself still transforms nothing. Above `CastStereoCropTranscoder.MAX_CROP_DURATION_MS` the crop is skipped and the original is cast whole - the same "refuse loudly, keep casting" behaviour the rest of the path uses.
- **Out of scope by construction**: a live URL returns early as a direct Cast decision, so streams never enter the transcode path.

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

**Native runtime.** `NativeDiagnosticXrRuntime` loads `libfms_diagnostic_xr.so` (built by `app_v2/src/vr/cpp/CMakeLists.txt`) and forwards every session call over JNI to `diagnostic_xr_runtime.cpp`. The native side is single-instance. The `noLegal` flavor ships only the arm64-v8a slice - and still does in the split debug build S1972 added, because `externalNativeBuild.cmake.abiFilters` stays arm64-only regardless of packaging: on x86_64 emulators / non-arm64 devices the library is intentionally absent - `isNativeAvailable` flips to `false` and every call short-circuits to a clean "loader unavailable" outcome. This is an expected device-capability mismatch, not an error (no `UnsatisfiedLinkError` storm in logcat).

**Render thread + EGL/GL confinement.** `DiagnosticXrRenderThread` owns the whole pipeline (init -> attach `Surface` -> start session -> upload texture -> frame loop -> shutdown) and blocks inside the native frame loop for its whole life - it has no `Handler`, and nothing else is posted to it. All GL/OpenXR objects are created and torn down on this one thread, satisfying both EGL and OpenXR thread-confinement rules. The `suspend` modifier on the runtime's setup methods is an API artefact - they execute synchronously on the render thread; hopping to a coroutine dispatcher would create EGL on the wrong thread and leave the render thread without a current GL context (a featureless black composition layer).

**Two texture channels.** The main scene is rendered per frame in native code. The 2D HUD is a separate channel: `HudCanvasRenderer` paints a `Canvas` bitmap (a 1024-wide RGBA panel - status line, AUDIO/SUBS cycle rows, transport buttons + sliders) that is uploaded to a HUD quad via `queueHud` only on state change, never per frame. `SubtitleCueRenderer` feeds subtitle cues into the same HUD channel. HUD interaction is controller-ray UV hit-testing against the quad, not view-level touch.

**Re-entry.** The `XrInstance` is reused across immersive entry/exit. On re-entry `xrCreateSession` runs before Meta Horizon OS re-registers the volumetric window, so the render thread awaits window focus before `startSession`; otherwise the runtime defers readiness and never fires the native ready callback.

Related specs: S0249 (render thread), S0290 / S0964 (HUD quad), S0156 (native library-availability ADR), S0986 (immersive subtitles). VR classes are indexed in the class catalog under `ui/xr` and `core/xr`.

## Launcher Mode

Launcher Mode turns the app into an Android home screen: a cell desktop, a bottom taskbar with a status tray, and placeable gadgets. It is the most restricted subsystem here - more than VR, not less - because it needs **two** independent conditions, a flavor that compiles it and a role only the user can grant.

**Entry and gating.** `LauncherHomeActivity` carries the HOME intent filter and ships `android:enabled="false"`. `LauncherRoleManager` owns the role protocol: it flips that component with `PackageManager.setComponentEnabledSetting`, then asks for the role through `RoleManager.createRequestRoleIntent` on API 29+, or sends the user to `Settings.ACTION_HOME_SETTINGS` below it. Android never hands the HOME role over programmatically - enabling the component only makes the app a *candidate*, and the user chooses. Anything that reasons about "is the launcher active" must ask the role manager, not a build flag. One secondary entry point ships enabled regardless: `LauncherPinRequestActivity`, the `CONFIRM_PIN_SHORTCUT` target other apps use to pin a shortcut into our desktop.

**Flavor seam.** `SUPPORT_LAUNCHER` is true in `standard` and `noLegal` only. Those two flavors mount `src/launcherEnabled` (the entire `ui/launcher/**` tree, its `res`, and an explicitly injected manifest); the rest mount `src/launcherDisabled`, which holds nothing but a no-op `LauncherModeContract` implementation and its Hilt module. The domain and data layers stay in `src/main` and therefore compile into every flavor, self-hiding at runtime through `LauncherModeContract.isAvailableInBuild` - the same shape as Desktop Companion Config above. Per Rule 14 there is no `BuildConfig.SUPPORT_LAUNCHER` branch in `src/main`; the single production read of that flag is the permission registry, which uses it to gate rationale rows.

**Desktop model.** Cells live in one Room table, with `kind` and `orientation` stored as enum names and the command encoded into a single prefixed TEXT column, so a new command variant never forces a migration. Portrait and landscape are **two fully independent layouts**, not one layout re-flowed: every repository call is scoped to a `LauncherOrientation`, and the resolved column count is stored per orientation too. A cell is an anchor plus a span, so gaps between cells are meaningful and a gadget claims a rectangle.

**First-run starter set.** An empty desktop is seeded once from `LauncherStarterSets`, the table for what a detected device profile receives. Third-party app cells are conditional on the matching package being installed, so a first seed never leaves a dead app icon; existing desktops are not rewritten when the profile changes. The set is packed **per section, not across the whole grid**: a section header raises a packing floor to its own row, and nothing seeded after it may anchor above that row. The floor is a correctness rule before it is an aesthetic one, because section membership is positional - a cell that backfilled the gap a shorter group left behind would belong to the section above it and collapse with it. Content leads the set and the launcher's own actions close it, so the first screen of a phone carries the media resources rather than five service shortcuts; the actions stay reachable from the Start menu, which is what makes that order safe. **Composition has two axes (S2309).** The profile decides intent - which groups have anything in them at all - and the device's screen class decides capacity: the section order, how many items each section seeds, and how many screens the desktop fills, which is composed rather than fixed at two. The class is a discrete pair - size by smallest width in dp, shape by the long-to-short ratio - derived by `LauncherScreenClassifier` from the running device and independent of the current orientation, so rotating a phone cannot change what its desktop contains. The rules live in `LauncherStarterLayoutRules` and take the screen class alone: a layout stated per profile was measured wrong, because a 20:9 phone and a 4:3 tablet can carry one profile and want different first screens. Several groups may seed one section key - the profile gadgets beside the utility widgets, the core resource aggregates beside the user tail beside the media windows - and when they land on one screen they are emitted under a single header, because a section is addressed by its key alone and two headers sharing one would fold together and collide in the packing pass; the screen cut is pushed past any such neighbouring pair, since two halves of one key on two screens would fold into each other. **A per-section budget applies only where a shorter list is still a list (S2321).** The content aggregates - recent, the four media types, the camera and "All files" - are a closed set that cannot grow without an edit to `LauncherStarterSets`, and each entry is the desktop's only way into a whole content type, so they seed under their own unbounded `CORE_RESOURCES` group while the open user tail keeps the budget; the launcher's own actions are exempt for the same reason. A budget reaching either group removes a capability rather than trimming a list - which is exactly what a compact screen did to "All documents", the camera and "All files" until they were split out.

**Grid.** The desktop is a hand-written `ViewGroup`, deliberately not a `RecyclerView` (ADR-9): the persisted model is a canvas with 2D positions, spans and meaningful gaps, which no stock `LayoutManager` expresses - and a desktop is dozens of cells, not a feed, so recycling buys nothing while costing the model. Column count resolves from available width and a user density factor within a fixed range; height is the scroll axis. All footprint arithmetic funnels through one geometry helper precisely so layout, hit-testing and the free-slot sweep cannot disagree. Drag-to-move uses a container-level `OnDragListener` with `startDragAndDrop` rather than `ItemTouchHelper`, which is RecyclerView-only for the same ADR-9 reason.

**Gadgets.** A gadget is an interactive block the user places on the desktop, and it is always **our own view - never a third-party `AppWidget`** (ADR-5): hosting foreign widgets means foreign layout outside our control and breaks the D-pad contract, so instead the pre-existing home-screen widget catalog is *bridged* into gadgets rather than duplicated. The registry is an open extension point fed by qualified Hilt list multibindings; treat the set of gadgets as growing, and read the current membership from the registry rather than from any document. Gadget lifecycle is enforced in one place: the view starts its work in `onActive` under `repeatOnLifecycle(STARTED)` and cancels on detach, because the grid is not a `RecyclerView` and there is no `onViewRecycled` to lean on. A bridged widget that keeps **per-instance** state reaches the desktop as well: its cell carries a launcher-minted instance token in the cell param instead of an `appWidgetId`, its own configuration screen is what the add flow opens, and the widget chain skips the calls that would hand that token to `AppWidgetManager`.

**Taskbar and command funnel.** The taskbar is bottom-anchored in both orientations, hosting the Start button, the recents and pinned strips, and the status tray. Each tray indicator subscribes to its source *only* while that indicator is switched on and the launcher owns the status area, and going false cancels the collector rather than merely hiding the view; an indicator whose state cannot be read is absent rather than drawn as "off". Every tap on every surface - desktop cell, either taskbar strip, Start menu row, gadget-issued command - funnels through a single guarded execution path on the launcher's ViewModel, so there is exactly one launch guard and one failure message, and a gadget never builds a parallel one.

**Task placement.** A target opened from a launcher surface lands in **three** possible tasks, and which one it is decides two separate behaviours, so neither may be changed alone. `LauncherHomeActivity` holds a task affinity of its own (`${applicationId}.launcherhome`, `type=home`); the app's ordinary screens share the application default affinity; a foreign package has its own. Android refuses `enterPictureInPictureMode()` to anything inside a **home-type** task, which is why the launcher never starts a target into its own task - starting from the launcher `Activity`'s context, with no flags, is exactly the mistake that does (S2026). And `FLAG_ACTIVITY_NEW_TASK` **joins an existing task whose affinity matches** rather than always making a new one, so that flag alone drops an internal target on top of the app's existing task and Back then unwinds into `MainActivity` or `BrowseActivity` (S2215). The rule that satisfies both: the shared executor keeps the start, adds `FLAG_ACTIVITY_NEW_TASK` for every target, and adds `FLAG_ACTIVITY_MULTIPLE_TASK` **only when the target's component is in our own package**. A foreign target is deliberately left alone - its task is already separate by affinity, and forcing a duplicate would stop that app resuming where the user left it.

**Surface colours.** Every foreground on the taskbar and the Start panel comes from a launcher-scoped theme attribute - `launcherTaskbarStartText` and `launcherTaskbarAllAppsText` for the two taskbar buttons, which sit on `colorSurfaceVariant`, and `launcherStartRowGroup1`..`launcherStartRowGroup4` for the four semantic row groups, which sit on `colorSurface`. Three rules hold together. Each attribute has a value in **every** theme set: the base day and night themes define all six, and the six `ThemeOverlay.FastMediaSorter.*` colour themes inherit them, overriding one only where their own surfaces would fall short. Each M3 role the app actually paints with is defined **by the app** in both sets - `colorSurfaceVariant`, `colorTertiary` and `colorError` used to resolve from the library baseline, which is a colour nobody chose and a library upgrade can move. And the result is **measured, not eyeballed**: `scripts/quality/assert-launcher-contrast.ps1` (in the `.\a.ps1 fg` batch) resolves each attribute and its background out of the resource files for all eight themes and fails below 7:1, the owner's threshold, above WCAG's 4.5:1 for ordinary text. The attributes are launcher-scoped rather than plain M3 roles because those roles paint dozens of other surfaces, where the lightness this threshold demands would be an unrelated change; the check is a script because the previous pass over these same colours was signed off by looking at it and shipped the Start label at 4.22:1. A new colour theme, or a new Start-panel row, runs the gate rather than matching a value by eye.

Related specs: S0404 (the founding ADR set, archived), S1103 (cell actions), S1170 (widget-to-gadget bridge), S1415 (tray composition), S1461 (this section), S1587 (per-section seeding floor and content-first order), S1895 (surface colours and the contrast gate), S1930 (per-instance widgets on the desktop), S2026 and S2215 (task placement), S2309 (starter layout composed per screen class). Launcher classes are indexed in the class catalog under the `launcher` sector.

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

## List Refine State Persistence (MANDATORY, S2199)

A list that offers a filter or a sort remembers the choice. This is a project rule, not a per-screen decision: it went unwritten until S2199, and three screens skipped it independently as a result.

- **One session boundary.** The choice is written to disk when it changes, so it survives leaving the screen, process death and a reboot alike. These three are not distinguished - every persistence mechanism in the tree already writes immediately, so a weaker tier would be a new mechanism with no benefit.
- **Keys belong to one screen.** Each screen owns its own keys; a filter is never stored under a record another screen also reads. The one implementation that ignored this, `BrowseStateDataStore`, is why the rule is stated: its single global key carries a filter chosen in one resource into the next resource opened (S2203).
- **A separate search field is not persisted; a filter facet is.** The line is drawn by whether the screen shows the restored value back. Text typed into a search box or dialog is dropped - it empties the list on a word nobody can see - while a facet the active-filter indicator names, including a textual one, is kept. `StreamsSessionStore` and the launcher's all-apps list both recorded this exclusion in code before it was a rule.
- **The active-filter indicator is a precondition, not a part.** A screen does not begin persisting until it already shows that a filter is on. Without that, restoring a narrow filter produces a list indistinguishable from an empty one, so persistence would be the cause of a screen that looks broken.
- **A restored value is checked against what the screen offers.** Apply only the part of a stored choice the open route actually holds, and apply nothing when none of it applies. Browse on the watch is entered separately per media type and per category, so a type saved on one route names nothing on another.

Housing differs by module, because `wear` does not depend on `app_v2` and the two can share a pattern but never a class:

- **Phone** - a dedicated store class in `data/repository/settings/` with its own DataStore file per screen: `StreamsSessionStore` (`streams_session`), `MainListSessionStore` (`main_list_session`).
- **Watch** - `WearPreferencesRepository`, the module's single settings mechanism, using plain `context.dataStore.edit`. Never `stampedEdit`: that call enters a value into the phone-watch settings exchange, and how a list was last narrowed is state the wearer set on this device, not a setting to replicate.

**A new store reaches its screen through the full layer chain**, however short the operation looks. On the phone that is `MainListSessionStore` (data) behind `MainListSessionRepository` (domain), read and written by `ReadMainListSessionUseCase` / `SaveMainListSessionUseCase`, which the screen's `MainListSessionManager` holds. The ViewModel imports neither the store nor the repository. This is not a preference: the `ui-imports-data` and `viewmodel-imports-repository` dimensions of `scripts/quality/assert-source-gates.ps1` (S2103) refuse both imports in a changed file, and their baselines only ever fall. The older shortcuts that predate those gates - `StreamsSessionStore` injected straight into `StreamsViewModel`, `WearPreferencesRepository` into the watch ViewModels - are baselined history, not a pattern to copy. S2199 ADR-6 originally proposed copying them and was refuted by the gate at closure time; the record is in `dev/REFUTED_APPROACHES.md`.

## Wear List Residency (S2205)

A watch content list is read in full and narrowed in memory. There is no page at the query level, and that is a decision rather than an omission - S2205 was raised because a spec described these lists as paged and nothing in the tree ever was.

- **The full list is load-bearing, twice.** `BrowseViewModel.loadedFiles` holds the source's answer unnarrowed, and two shipped decisions read it that way. `BrowseListProjection.refine` (S2136) runs search, type filter and sort as an in-memory pass over the whole list, so a query-level `LIMIT` would answer "not found" for a file that exists. `PlaybackSetManager` (S1683 ADR-2) is handed the displayed list as the player's paging set and walks it by modular index, so a page boundary would become the end of "next track".
- **Windowing is the renderer's job, and it is already done.** Lists compose inside a `ScalingLazyColumn` through the lazy `items(..)`, so only visible rows are composed however long the list is. Adding a second window in the ViewModel would duplicate this one.
- **A per-collection `LIMIT` would not even bound the merged listing.** `getAllMediaFiles()` unions three typed MediaStore collections with the document query and re-sorts; capping each source separately drops arbitrary rows rather than the oldest ones.
- **`WearLocalFolderRepositoryImpl` is not a counter-example.** Its `PAGE_SIZE = 50` windows a level *after* reading that level unbounded. It bounds what the ViewModel receives, not what the cursor walks.
- **What would reopen the question.** The resident cost is the row count of one listing, and `WearMediaRepositoryImpl.listing` already logs it per listing. Revisit when that number on a real watch stops fitting - on the measurement that is already collected, not on a re-argument.

Watch module only. The phone's lists answer to different constraints and this section says nothing about them.

## Settings Model Shape (MANDATORY, S2300)

`AppSettings` is one data class with every default supplied, and that shape has a hard ceiling: a JVM
method descriptor carries at most 255 slots including `this`, and Kotlin's synthetic default-argument
constructor spends one slot per parameter (two per non-nullable `Long`), one bitmask int per 32
parameters, and one marker. `copy$default` spends the same plus the receiver.

- Crossing the ceiling is invisible until runtime. kotlinc and D8 emit the class without a warning, every
  static gate passes, and ART refuses it at verification - `VerifyError .. invalid arg count (0) in
  invoke-direct/range` - which kills the app in `Application.onCreate` and makes every `copy(..)` of that
  class equally dead. It happened twice: S1470 and again on 2026-09-01 (S2300).
- A cohesive group of settings therefore lives in its own nested data class, held by `AppSettings` as one
  field: `LauncherSettings` (`domain/model/launcher/`) and `ScreenshotGestureSettings` are the pattern. A
  group costs one slot instead of one per field.
- Write to a nested group through its helper - `settings.withLauncher { copy(desktopLocked = true) }` -
  rather than spelling out `copy(launcher = settings.launcher.copy(..))` at the call site.
- The launcher group keeps read-through properties on `AppSettings` (`settings.launcherWallpaperMode`), so
  a read needs no knowledge of the grouping and the device-profile preset CSV keeps naming fields as
  before. Reads may use either form; writes go through the group.
- `scripts/quality/assert-ctor-arg-slots.ps1` does the arithmetic and runs in every closure that changes a
  `.kt`. It reports headroom in whole properties - when it warns, group a domain, never buy back one slot.

## Wear Settings Persistence (S2050)

A wear-related field on the phone belongs to exactly one of two stores, decided by who reads it:

- **`WearSettingsMirrorStore`** (`data/repository/wear/`) - a field belongs here only if nothing outside the companion sheet's own restore path ever reads it, and it exists solely to remember what was last told to the watch. Backed by the `wear_sync_prefs` `SharedPreferences` file, Gson-serialized. The two actual writers are `WearSyncViewModel` (settings the sheet pushed) and `PhoneWearListenerService.markSynced` (the watch's ack timestamp) - neither touches `SharedPreferences` directly, both inject the store.
- **`AppSettings`/`ProgramsSettingsStore`** - a field belongs here if any other part of the app reads it reactively as a phone-behaviour toggle, the way `KEY_ENABLE_WEAR_COMPANION` is read by `MainActivity`, `SubProgramCatalog`, `ShareTargetAvailabilityResolver` and others outside the companion sheet.

These are not two names for one setting - they answer different questions ("what did we last tell the watch" vs. "is the companion feature on") - so merging them is out of scope; see `PLAN/S2050_wear-settings-two-stores-on-phone.md` §9 for the rejected alternative.

## Wear Shared UI Components (S2004)

The watch module has exactly one component for each of these two jobs. Both live in `wear/ui/common/`, and a screen that needs either asks for it rather than writing its own - S2004 was raised because seven screens had each written their own empty state and three had none at all.

- **`WearStateBlock`** - the empty, unavailable and error states of any browsing screen, carrying up to two chips: a retry where retrying changes something, and a back that is always there. A screen decides which case it is in and whether retry is meaningful; it does not decide how the case looks.
- **`WearFileActionsDialog`** - the long-press menu over one file, on every surface where a file is visible. It renders the set of `WearFileOperationKind` it is handed and holds no list of its own, because the single answer to "what may this file be asked to do" is `WearFileCapabilityPolicy` (strategic ADR-4 of S2004). The multi-select menu in `ui/browse/FileActionsDialog.kt` is a different question - actions over a selection - and deliberately offers a narrower set.
