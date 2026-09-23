# Phone UI Component Patterns and Unification Catalogue

Scope: the `app_v2` module, every source set that ships in a flavor. This is a standard, not a report: each rule below is what a future change must follow, names the concrete style, class or attribute that implements it, and cites the measured divergence it replaces. Every count came from the working tree during S3231 Phase 01 and Phase 02 and is reproducible; the two inventories are `PLAN/S3231_research-phone-ui-component-patterns/research/01__dialogs-and-bottom-sheets-inventory.md` and `PLAN/S3231_research-phone-ui-component-patterns/research/02__lists-cards-and-controls-inventory.md`.

The `wear` module is out of scope: it is Compose end to end and owns no XML layout (`docs/ARCHITECTURE.md` "UI Toolkit Boundary").

Precedence: `docs/ARCHITECTURE.md` "Button Taxonomy" and "Compose Island Theming" remain the authority for button roles and for island colour. This catalogue extends that taxonomy to the roles it does not yet cover - list rows, toolbars, bars, panels, insets and landscape - and never contradicts it.

---

## 1. Design Tokens and Theme Hierarchy

### 1.1 The inheritance chain is three levels and no more

A widget's visual form resolves through exactly one chain:

- `Widget.Material3.<Widget>` is the only permitted foreign parent.
- `Widget.FastMediaSorter.<Role>` is the project role style, and it is the only level a layout is normally allowed to name.
- `Widget.FastMediaSorter.<Role>.<Variant>` is the single permitted refinement, and it may only narrow what its parent already sets.

A fourth level, or a role style whose parent is another role's variant, is refused by review. Every style lives in `app_v2/src/main/res/values/themes.xml`; the `launcherEnabled` source set adds its own in `app_v2/src/launcherEnabled/res/values/styles.xml` and must keep the same three-level shape (`Widget.FastMediaSorter.Launcher.TaskbarDone` already does, parenting `Widget.FastMediaSorter.Button.Filled`).

This replaces a family that is grouped by feature rather than by role: 41 `Widget.FastMediaSorter.*` styles exist today, clustered as `Calculator.*` (6), `NetworkMonitor.*` (12), `SettingsButton.*` (5) and `Camera.*` (3), with no list-item family and no toolbar container style at all.

### 1.2 Colour roles come from the theme attribute, never from a literal

- A layout names a colour only as `?attr/color<Role>`, `?attr/<projectAttr>` or `@color/<token>`. A literal `#hex` in any file under `res/layout*` is refused (CLAUDE.md Rule 19, gate `scripts/quality/assert-neuroslop.ps1`).
- The rule covers `app:tint` and `app:iconTint`, not only `android:background`. This is the measured hole: 6 hex literals reach a tint attribute (`player_draw_overlay_toolbar_content.xml` lines 49, 112, 123, 134, 145 and `player_translation_overlay_content.xml` line 35), inside a population of 29 hex occurrences across 10 files in `res/layout` plus a second population in `res/layout-land`.
- A scrim is a named `@color/` token, never an inline `#CCxxxxxx`. Six already exist and are correctly indirected (`player_overlay_standard`, `player_overlay_light`, `player_overlay_heavy`, `player_overlay_translucent`, `player_overlay_grey`, `player_panel_copy` in `colors.xml:223-256`); a seventh alpha level is added there, not in a layout.
- Kotlin obeys the same rule: `Color.parseColor("#..")` inside a `View` subclass is a defect unless the colour is a declared brand constant with a comment saying so. Measured: 38 literals across 8 files, led by `GameBoardView` (14), `PrefetchOverlayView` (9) and `TranslationOverlayView` (7). Resolve through `MaterialColors.getColor(view, R.attr.color..)` or a `@color/` resource.
- Launcher desktop text keeps its dedicated attributes `?attr/launcherDesktopText` and `?attr/launcherDesktopTextSecondary` (S2539), because generic `?attr/colorOnSurface` does not follow the accent overlay over a wallpaper.

Where the module is already right, the standard simply freezes it: `android:textColor` in item layouts measures 34 `?attr/colorOnSurfaceVariant`, 26 `?attr/colorOnSurface`, 11 `?attr/colorPrimary` and 5 `?android:attr/textColorSecondary` against only 6 literals. Text colour is the proof that the attribute discipline is achievable in this codebase; text size is where it was never applied.

### 1.3 The `TextAppearance.FastMediaSorter.*` scale

A layout never carries `android:textSize`. It carries `android:textAppearance="@style/TextAppearance.FastMediaSorter.<Role>"`, and the role is chosen by what the text means, not by how large it should look.

The scale to author, each parenting a Material 3 token so a single type-scale change reaches every surface:

- `TextAppearance.FastMediaSorter.Item.Title`, parent `TextAppearance.Material3.BodyLarge` - the primary line of a list row or grid cell.
- `TextAppearance.FastMediaSorter.Item.Subtitle`, parent `TextAppearance.Material3.BodyMedium` - the secondary line.
- `TextAppearance.FastMediaSorter.Item.Detail`, parent `TextAppearance.Material3.BodySmall` - the size, date and counter line.
- `TextAppearance.FastMediaSorter.Item.Badge`, parent `TextAppearance.Material3.LabelSmall` - badge and overlay text.
- `TextAppearance.FastMediaSorter.Bar.Title`, parent `TextAppearance.Material3.TitleLarge` - a toolbar title.
- `TextAppearance.FastMediaSorter.Dialog.Title`, parent `TextAppearance.Material3.HeadlineSmall`, and `TextAppearance.FastMediaSorter.Dialog.Body`, parent `TextAppearance.Material3.BodyMedium`.
- The two that already exist, `TextAppearance.FastMediaSorter.HelperText` (parent `TextAppearance.Material3.BodySmall`) and `TextAppearance.FastMediaSorter.SettingsTabConnected` (parent `TextAppearance.Material3.LabelLarge`), join this family unchanged and are its precedent.

Divergence replaced: 45 `textAppearance` attributes across the 81 item layouts drawing on 13 distinct tokens, of which 23 are Material 2 bridge tokens (`textAppearanceCaption` 12, `textAppearanceBody2` 6, `textAppearanceSubtitle1` 2, `textAppearanceBody1` 2, `textAppearanceSubtitle2` 1) against 21 Material 3 tokens and one hardcoded `@style/TextAppearance.Material3.BodyLarge`; plus 33 hardcoded `android:textSize` literals across 20 of the 64 `main` item layouts, spanning 8 values (12sp, 13sp, 11sp, 14sp, 16sp, 18sp, 10sp, 40sp) with three of them 1sp apart. Neither of the two existing project text appearances is used by a single item layout.

Two Material 2 bridge attributes and two Material 3 attributes cannot coexist in one visual family: `textAppearanceCaption` and `textAppearanceBodySmall` resolve to different metrics, so two rows meant to match do not.

### 1.4 Elevation tokens

- A surface declares `android:elevation="@dimen/elevation_<role>"`. A raw `dp` literal on an elevation attribute is a defect.
- One name per value. `@dimen/toolbar_elevation` and `@dimen/dimension_zero` were two names for the same zero; S3248 deleted the toolbar one and moved its remaining call sites onto `@dimen/dimension_zero`, the role name that survives.
- The permitted set is `elevation_dialog` (8dp), `elevation_operations_bar` (4dp), `elevation_button_bar` (2dp), `elevation_player_overlay` (8dp), `elevation_player_controls` (16dp), `card_elevation` (2dp), `card_elevation_low` (1dp), `card_elevation_high` (4dp). A new value needs a new role, not a new number.
- Omitting the attribute is a decision, not a default. Measured: 5 distinct elevation values in use, with no attribute at all on toolbar families B, E, F and G, on `activity_main.xml`'s `AppBarLayout`, on `launcher_taskbar.xml`, and on `player_epub_controls_overlay_content.xml` whose structural twin `player_pdf_controls_overlay_content.xml` sets 16dp.

### 1.5 Corner and shape tokens

- Corner radius is a `ShapeAppearance` on the style, not an `app:cardCornerRadius` on the instance. `ShapeAppearanceOverlay.FastMediaSorter.ThumbnailSquare` (`themes.xml:662`) is the existing precedent and the pattern to follow.
- Three radii are sanctioned, and a fourth needs a decision: small 8dp (`card_corner_radius`, `settings_tab_corner_radius`), medium 12dp (`focus_decoration_corner_radius`, `tourist_secondary_card_corner_radius`), large 16dp (`welcome_perm_list_corner`, `welcome_content_panel_corner`, `tourist_card_corner_radius`). The per-feature names collapse onto `shape_corner_small` / `shape_corner_medium` / `shape_corner_large` as each feature is next touched, never as a campaign.
- `FocusMaterialCardView` (`ui/common/widget/`, 42 XML references across 14 files) is the de facto card base for tile layouts and keeps that role; its focus ring is a shape concern and belongs in its style, not in each of the 14 host layouts.

### 1.6 The rule in one line

A layout file declares structure, ids and references. It never declares a literal colour, a literal text size or a literal elevation. If a value cannot be expressed as `?attr/`, `@style/`, `@color/` or `@dimen/`, the missing token is authored first and the layout edit follows.

---

## 2. Standard Component Primitives

Each primitive below states the target API shape, the XML style it carries and the anti-pattern it retires. A primitive exists so that a surface stops owning a decision; a surface that keeps owning it after the primitive lands is the defect, not the primitive.

### 2.1 Dialogs and BottomSheet Specifications

#### The dialog factory

One `object AppDialog` under `ui/common/dialog/`. No call site names a builder class directly after it exists.

```kotlin
object AppDialog {
    fun confirm(
        owner: LifecycleOwner,
        context: Context,
        title: CharSequence,
        message: CharSequence,
        confirmLabel: CharSequence,
        onConfirm: () -> Unit,
    ): AlertDialog

    fun destructive(/* same shape; selects the Destructive theme overlay internally */): AlertDialog

    fun input(
        owner: LifecycleOwner,
        context: Context,
        title: CharSequence,
        hint: CharSequence,
        initial: String,
        validate: (String) -> Boolean = { it.isNotBlank() },
        onAccept: (String) -> Unit,
    ): AlertDialog

    fun singleChoice(
        owner: LifecycleOwner,
        context: Context,
        title: CharSequence,
        items: List<CharSequence>,
        selectedIndex: Int,
        searchable: Boolean = false,
        onPick: (Int) -> Unit,
    ): AlertDialog

    fun progress(owner: LifecycleOwner, context: Context, title: CharSequence, determinate: Boolean): AlertDialog

    fun custom(
        owner: LifecycleOwner,
        context: Context,
        @LayoutRes layoutRes: Int,
        keyboardContract: Boolean = true,
        bind: (View, AlertDialog) -> Unit,
    ): AlertDialog
}
```

Construction guarantees, so that none of them is a per-author obligation:

- Builds on `MaterialAlertDialogBuilder`, which is what makes the positive slot inherit `Widget.FastMediaSorter.Button.DialogConfirm` and the negative slot `Widget.FastMediaSorter.Button.DialogCancel` through `materialAlertDialogTheme`.
- `destructive` selects `ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive` internally, so the 27 sites that name that overlay by hand stop naming it.
- Shows through `showBoundTo(owner)`, honoured at 357 call sites today and no longer restatable wrongly.
- Installs `DialogKeyboardDelegate` unless `keyboardContract = false`, which is how `CaptureDialogFragment` declares its exemption instead of accidentally having one.
- Calls `DialogAccessibilityHelper` for the TalkBack landing.
- Applies insets through `Dialog.applyDialogInsets()` (section 4) and sizes the window through `DialogWindowSizer` (below).

Anti-patterns retired: the 29 `AlertDialog.Builder(` call sites in 22 files, which do not read `materialAlertDialogTheme` at all and therefore render stock AppCompat buttons instead of the asymmetric confirm/cancel pair; the six overlapping single-choice pickers (`ListSelectionDialog`, `SimpleValueChoiceDialog`, `SearchableOptionPickerDialog` with its second `SearchableOptionPickerWindow` rendering, `SearchableLanguagePickerDialog`, `ResourcePickerDialog`, `DestinationPickerDialog`); the lone hand-written `Dialog` subclass `MaterialProgressDialog`; and the 11 dialog layouts with no named owning class, each of which becomes one `AppDialog.custom(..)` call with a named binding function.

#### The bottom sheet base

```kotlin
abstract class BaseAppBottomSheet : BottomSheetDialogFragment() {
    @LayoutRes protected abstract val contentLayout: Int
    protected open val showActionPair: Boolean = false
    protected open val usesShell: Boolean = true
    protected abstract fun bindContent(content: View)
    protected fun deliverResult(payload: Bundle)
}
```

- Inflates one fixed shell: a `BottomSheetDragHandleView`, a title slot, a `NestedScrollView` content slot for the subclass, and an optional action-pair slot.
- A subclass whose dialog is not a sheet sets `usesShell = false` (S3244): the content layout inflates as the whole view and the drag handle and bottom inset are skipped, because an anchored top panel must not carry either. The launcher Start menu in top-panel mode is the case - its bottom-placement mode keeps the full shell.
- Applies the bottom `systemBars` inset to the content slot in `onViewCreated`, so a sheet's last row never sits under the navigation bar whatever the host's edge-to-edge state is.
- Wires `DialogKeyboardDelegate` in `onStart`, giving Escape-to-dismiss and arrow-key movement to a family that has neither.
- Declares the `FragmentResult` plumbing once - `RESULT_KEY`, a private `ARG_REQUEST_KEY` read back in `onCreate`, `deliverResult(Bundle)` - following the `SearchableLanguagePickerDialog` reference named by `docs/ARCHITECTURE.md` "Dialog Result Delivery".

Anti-patterns retired: the 4 raw `BottomSheetDialog(..) + setContentView(..)` constructions in `BrowseBinaryFileHandler`, `PdfThumbnailSheet` and `EpubSearchAndTocPresenter` (two sites), none of which survives recreation or has a `FragmentResult` path; zero drag handles across all 9 sheet layouts; and the inconsistency where the same sheet insets differently depending on which screen raised it, because 8 of the 9 sheet layouts are plain `LinearLayout` trees and only `bottom_sheet_now_playing.xml` is rooted in a `CoordinatorLayout`.

#### The shared shell and the action pair

Two new layouts replace retyping:

```xml
<!-- res/layout/dialog_action_pair.xml -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="end"
    android:orientation="horizontal">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnDialogCancel"
        style="@style/Widget.FastMediaSorter.Button.DialogCancel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="@dimen/dialog_action_button_gap"
        android:text="@string/cancel" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnDialogConfirm"
        style="@style/Widget.FastMediaSorter.Button.DialogConfirm"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/ok" />
</LinearLayout>
```

- `dialog_action_pair_destructive.xml` is its only sibling, swapping the confirm slot for `Widget.FastMediaSorter.Button.DialogDestructive`.
- `dialog_shell.xml` holds the title slot, a `NestedScrollView` content slot and an `<include layout="@layout/dialog_action_pair" />`, so the scroll boundary sits in the same place on every surface and the action pair is always outside the scroll region.
- No new button style is introduced. The existing three-style pair with its `dialog_action_button_min_height`, `dialog_cancel_button_min_height`, `dialog_confirm_button_min_width` and `dialog_action_button_gap` dimens is already the right taxonomy; what changes is where it is applied.

Anti-patterns retired: zero `<include>` elements across all 74 dialog and sheet surfaces, with the confirm/cancel block retyped in each of the 43 layouts that carry a `DialogCancel`; and six distinct root container types across the 60 `main` dialog layouts (38 `LinearLayout`, 14 `ScrollView`, 3 `ConstraintLayout`, 2 `MaxHeightLinearLayout`, 2 `MaterialCardView`, 1 `NestedScrollView`), with 27 containing a scrolling container at a different depth each time. `MaxHeightLinearLayout` is retained only where a measured height cap is the actual point.

#### Window sizing

One `DialogWindowSizer` resolves the window width from the existing `@dimen/dialog_min_width` and `@dimen/dialog_max_width` against the current configuration, and the factory consults it. This retires 22 hand-rolled `window.setLayout(..)` sites, each computing its own width, including `ImageEditDialog`'s private `WIDTH_FRACTION_OF_SCREEN`, `GesturePickerDialog`'s private `DIALOG_WIDTH_FRACTION = 0.85`, and `LauncherWallpaperSettingsDialogFragment`'s unconditional `MATCH_PARENT` on both axes. Neither of the two existing dimens is consulted by any of the 22 today.

#### Naming

A surface that behaves as a bottom sheet is named `sheet_*.xml` and a surface that behaves as a dialog is named `dialog_*.xml`. The name is what a gate can see, so a misnamed file is an ungated file. S3244 closed the gap this section opened with: the offload offer layout is `sheet_stream_offload_offer.xml`, and `assert-dialog-cancel-style.ps1` now scans `sheet_*.xml` and `*_sheet.xml` as well as the two original prefixes, so `sheet_send_to.xml`, `launcher_signal_list_sheet.xml` and `sheet_launcher_section_actions.xml` are inside a gate rather than beside one.

#### Context menus

One `Context.showActionMenu(anchor: View, items: List<ActionItem>)` helper, so that "pick an action on this item" has one construction with the dialog keyboard contract attached. This retires three parallel mechanisms used for one job: `PopupMenu` in 24 files, `PopupWindow` in 10 files including the six launcher action menus, and action-sheet `BottomSheetDialogFragment`s. None of the popup mechanisms carries any keyboard contract today.

### 2.2 List and Grid Card Specifications

#### `MediaItemView`

```kotlin
class MediaItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    enum class LayoutMode { LIST, GRID, PLANK }

    fun setLayoutMode(mode: LayoutMode)
    fun setTitle(text: CharSequence?)
    fun setSubtitle(text: CharSequence?)
    fun setDetail(text: CharSequence?)
    fun setBadge(badge: ItemBadge?)
    fun setSelectionState(selected: Boolean)
    fun thumbnailView(): ImageView
}
```

- Lives under `ui/common/widget/`, inflates `view_media_item.xml`, and declares a `declare-styleable MediaItemView` with `mivLayoutMode`, `mivShowSelection`, `mivShowBadge` and `mivTrailingSlot`.
- Root carries `Widget.FastMediaSorter.Item.Row`; the three text children carry `TextAppearance.FastMediaSorter.Item.Title` / `.Subtitle` / `.Detail`; the image child carries `Widget.FastMediaSorter.Item.Thumbnail`; the badge carries `Widget.FastMediaSorter.Item.Badge`.
- `setSelectionState` sets `isSelected` and nothing else. The state-list drawable does the work.

The row style is what closes the background hole in one place:

```xml
<style name="Widget.FastMediaSorter.Item.Row" parent="">
    <item name="android:background">@drawable/item_focus_selector</item>
    <item name="android:foreground">@drawable/item_interaction_overlay</item>
    <item name="android:focusable">true</item>
    <item name="android:focusableInTouchMode">false</item>
    <item name="android:paddingHorizontal">@dimen/item_row_padding_horizontal</item>
    <item name="android:paddingVertical">@dimen/item_row_padding_vertical</item>
</style>
```

Anti-patterns retired, each with its measurement:

- Four incompatible selection-highlight mechanisms: a root colour swap in Kotlin (`MediaFileAdapter:883-887`, `ResourceAdapter:618`, `:633`, `:854`, `:865`), `View.isSelected` with a state-list drawable (`ResourceAdapter:615`, `:982`), `setBackgroundResource` (`PdfThumbnailAdapter:67`, `StatisticsAdapter:146`, `WelcomePagerAdapter:154`), and checkbox-only with no row highlight (7 `cbSelect` plus 3 `btnSelect` sites). `setBackgroundColor` on a root destroys the XML selector, so the focus ring stops working for exactly the rows being selected. One adapter, `ResourceAdapter`, uses two of the mechanisms in its grid row and one in its list row, and its own comment at line 610 records it.
- 26 of the 64 `main` item layouts declare no root background at all, so they have no ripple, no pressed state and no focus state; 17 distinct background forms are in use across the population, and the correct two-drawable idiom (`item_focus_selector` as background plus `item_interaction_overlay` as foreground) is adopted by only 7 of 81.
- Four names for one selection affordance - `cbSelect` (7), `btnSelect` (3), `item_check` (2), `cbSelected` (1) - plus `ivSelected` and `vSelected` as markers, 16 ids over 6 names.
- Six one-off badge ids (`tvPinBadge`, `tvErrorBadge`, `tvDestinationBadge`, `taskbarUnpinBadge`, `cellRemoveBadge`, `cellModeBadge`), each with its own background drawable and text sizing, and no shared badge layout anywhere.
- Three byte-identical cloud layouts, `item_dropbox_folder.xml`, `item_google_drive_folder.xml` and `item_onedrive_folder.xml`, unified today only by a Kotlin `CloudFolderItemBinding` wrapper.
- Two adapters over one layout (`BrowseRenameFilesAdapter:43` and `RenameDialog:321` both inflating `ItemRenameFileBinding`) and two independent "track row" implementations (`QueueTrackAdapter`, `MusicTrackAdapter`).
- `item_media_file_grid_operations.xml` (79 lines) reads as dead against `R.layout.` and the binding call sites, and is not: four `ViewStub android:layout="@layout/item_media_file_grid_operations"` references inflate it - `item_media_file_grid.xml`, `item_media_file_grid_no_thumb.xml` and both `noLegal` counterparts - and an XML `ViewStub` reference appears in neither search. It stays (S3247 measured this before deleting it; the strip it holds is the grid tile's copy/move/rename/delete row). A layout reached only from a `ViewStub` is the one shape the usual dead-resource sweep cannot see.

Consolidation candidates, to be taken opportunistically as each screen is next touched and never as a campaign: `item_media_file`, `item_media_file_grid`, `item_media_file_grid_no_thumb`, `item_resource`, `item_resource_grid`, `item_stream_grid_cell`, `item_stream_source`, `item_duplicate_file`, `item_queue_track`, `item_music_track`, `item_folder`, and the three cloud-folder layouts.

#### Adapter usage

```kotlin
class MediaFileViewHolder(
    private val itemView: MediaItemView,
    private val thumbnails: MediaItemThumbnailBinder,
) : RecyclerView.ViewHolder(itemView) {

    fun bind(file: MediaFile, selected: Boolean) {
        itemView.setTitle(file.name)
        itemView.setSubtitle(file.formattedSize)
        itemView.setSelectionState(selected)
        thumbnails.bind(itemView.thumbnailView(), file, ThumbnailRole.LIST_ROW)
    }
}
```

An adapter never touches the row's background, never sets a text size and never configures Glide inline.

#### `MediaItemThumbnailBinder`

```kotlin
enum class ThumbnailRole { LIST_ROW, GRID_CELL, PREVIEW }

class MediaItemThumbnailBinder @Inject constructor(/* .. */) {
    fun bind(view: ImageView, file: MediaFile, role: ThumbnailRole)
    fun clear(view: ImageView)
}
```

The role pins the override size, the disk-cache strategy, the signature and the placeholder. Promoted from the existing `ui/browse/AdapterThumbnailLoader.kt`, which is already the only place in the list layer owning a Glide pipeline, and injected wherever an adapter needs it.

Anti-pattern retired: Glide configured per call site rather than per role - 5 distinct `diskCacheStrategy` values (`RESOURCE` 12, `ALL` 10, `DATA` 4, `NONE` 3, `AUTOMATIC` 3), 8 distinct `.override(..)` argument shapes, 4 distinct transitions and 3 placeholder conventions across 23 files. `LauncherAppGridAdapter` is the only adapter outside `ui/browse` calling `Glide.with` directly and shares nothing with the loader. `MediaFileAdapter`'s two `setBackgroundColor(Color.TRANSPARENT)` calls at lines 802 and 1069 disappear, because the thumbnail background belongs to `Widget.FastMediaSorter.Item.Thumbnail`.

#### Flavor overrides

A flavor override of an item layout carries the same formatting as its `src/main` original. The three `noLegal` overrides differ functionally by one badge container and one wrapper frame, but `src/main` is minified to one line per element while the override is conventionally formatted, so `diff` reports a whole-file replacement on two of the three pairs and a future divergence between the flavors will not be visible in review.

### 2.3 Toolbars and Action Bar Specifications

#### `StandardToolbar`

```kotlin
class StandardToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.toolbarStyle,
) : MaterialToolbar(context, attrs, defStyleAttr) {

    fun setUpNavigation(activity: ComponentActivity)
}
```

`setUpNavigation` routes through `activity.onBackPressedDispatcher.onBackPressed()` in every case, so the three current wiring idioms collapse to one: 7 files pairing `setSupportActionBar` with `setDisplayHomeAsUpEnabled`, 19 files calling `toolbar.setNavigationOnClickListener(..)`, 25 files using `onBackPressedDispatcher`, and `BrowseActivity`'s hand-rolled `btnBack` wired independently of all three.

Two styles, and only two:

```xml
<style name="Widget.FastMediaSorter.Toolbar" parent="Widget.Material3.Toolbar">
    <item name="android:elevation">@dimen/dimension_zero</item>
    <item name="titleTextAppearance">@style/TextAppearance.FastMediaSorter.Bar.Title</item>
</style>

<style name="Widget.FastMediaSorter.Toolbar.Primary">
    <item name="android:background">?attr/colorPrimary</item>
    <item name="navigationIconTint">?attr/colorOnPrimary</item>
    <item name="titleTextColor">?attr/colorOnPrimary</item>
</style>

<style name="Widget.FastMediaSorter.Toolbar.Flat">
    <item name="android:background">@android:color/transparent</item>
    <item name="navigationIconTint">?attr/colorControlNormal</item>
</style>
```

A toolbar declaration carries `style="@style/Widget.FastMediaSorter.Toolbar.<Variant>"` and nothing else beyond `android:id`, layout params and `app:title`.

Anti-patterns retired by S3248: 27 `MaterialToolbar` declarations in 7 attribute fingerprints, zero of them carrying a `style` attribute, so every visual decision was restated inline and a theme change had to be applied 27 times. All 27 are now `StandardToolbar` - 14 `Primary`, 13 `Flat` - and the `styleless-toolbar` dimension of `scripts/quality/assert-source-gates.ps1` holds the count at zero for both class names, so a new screen cannot reintroduce the pattern. Two sites needed a stated choice rather than a mechanical one: `activity_duplicates.xml` mixed Family A hosting with Family B tinting and went to `Primary`, and `fragment_permissions_management.xml` was the one primary-coloured settings fragment and went to `Flat` beside the licences fragment. `activity_streams.xml` keeps `ThemeOverlay.FastMediaSorter.Toolbar.Primary` alongside its style: it is the only site with an `app:menu`, and the overlay carries the action-menu text colour and the D-pad focus selectors that the two variants do not.

#### `ActionBarView`

```kotlin
class ActionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Action(@IdRes val id: Int, @DrawableRes val icon: Int, @StringRes val contentDescription: Int)

    enum class Placement { TOP, BOTTOM, FLOATING }

    fun setActions(actions: List<Action>, onAction: (Int) -> Unit)
    fun setActionEnabled(@IdRes id: Int, enabled: Boolean)
    fun setActionVisible(@IdRes id: Int, visible: Boolean)
    fun hasVisibleAction(): Boolean
}
```

Children are added as `Action` records, so every control gets `Widget.FastMediaSorter.Button.Icon` by construction and a 48dp touch target with it. The root carries `Widget.FastMediaSorter.Bar.Action` with `.Bottom` and `.Floating` as its two variants, each pinning its own background and elevation. Placement, the item tint, the item background tint and the item edge are the four `declare-styleable ActionBarView` attributes (`abvPlacement`, `abvItemTint`, `abvItemBackgroundTint`, `abvItemSize`); a `FLOATING` bar rounds its items into pills, because a control drawing over content needs a shape of its own.

Delivered by S3249. **A bar whose children stay declared in XML is the sanctioned second form**, not a shortfall: `ActionBarView` is a `LinearLayout`, so converting a container alone gives the bar its placement token and its surface while every id-keyed lookup around it keeps working. Records are for a bar nothing but its own helper addresses - the Browse operations bar and the two floating scroll strips. The Browse command bar (16 controls) and the Main action strip stayed declared, because their ids are the addressing scheme of an adaptive overflow manager, a wide-bar label and metric pass and a per-command eligibility map; rebuilding them from records would discard all three in one step. A generated child is inflated from `view_action_bar_item.xml` rather than constructed, since `MaterialButton`'s constructor takes a `defStyleAttr` and cannot be handed a widget style.

Anti-patterns retired:

- Two screens with no toolbar at all, and they are the two busiest: `activity_main.xml:10-410`, an `AppBarLayout` containing only a `MaterialButton` row and a `TabLayout` with no title, no navigation icon and no menu; and `activity_browse.xml:7-68`, a bare `LinearLayout` command bar holding `btnBack` plus 15 further controls.
- Two icon-button idioms inside one file: `activity_browse.xml` used `MaterialButton` with `?attr/materialIconButtonStyle` for its 16 command-bar controls and raw `<ImageButton>` for its 7 operations-bar controls and 4 scroll controls; `activity_player_unified.xml` mixes 46 raw `ImageButton` with 5 `MaterialButton` icon buttons. The two forms differ in touch target, ripple shape and disabled-state tint. S3249 retired the browse half: the 11 raw controls are `Action` records in three `ActionBarView` strips - one bottom operations bar and two floating scroll strips, two because the backward pair anchors to the top of the list and the forward pair to its bottom.
- `Widget.FastMediaSorter.Button.Icon` already exists (`themes.xml:364`, parent `Widget.Material3.Button.IconButton`) and is referenced by exactly 2 layouts, neither of them a bar. It becomes the single icon-button token, with `Widget.FastMediaSorter.Button.Icon.Overlay` added as the variant carrying the player tint state list.

Exempt by design, unchanged from `docs/ARCHITECTURE.md` "Button Taxonomy": reserved ExoPlayer `@id/exo_*` controls and the intentionally dark camera and viewfinder surfaces.

### 2.4 Floating Action Panels

A floating panel is any surface that draws over content with a scrim and its own elevation: the player overlay panels, the browse scroll column and the launcher taskbar.

#### `PlayerOverlayPanel`

```kotlin
class PlayerOverlayPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr)
```

Under `ui/player/views/`, applying `Widget.FastMediaSorter.Player.OverlayPanel` whose background is a named scrim token and whose elevation is `@dimen/elevation_player_overlay`. Its icon children inherit `Widget.FastMediaSorter.Button.Icon.Overlay`, which carries the tint state list.

Anti-patterns retired, all three delivered by S3250: `player_pdf_controls_overlay_content.xml` at 16dp against its structural twin `player_epub_controls_overlay_content.xml` at no elevation at all, now both on `@dimen/elevation_player_controls`; the hex-literal tints across `player_draw_overlay_toolbar_content.xml` and `player_translation_overlay_content.xml`, and every other literal their landscape counterparts carried - 58 removed across the player layouts, and the `layout-hardcoded-colors` ratchet fell from 88 to 30; and the 46 raw `<ImageButton>` in `activity_player_unified.xml`'s `topCommandPanel`, 44 of which now carry `style="@style/Widget.FastMediaSorter.Button.Icon.Overlay"` in place of four attributes each, in both orientations.

`Widget.FastMediaSorter.Button.Icon.Overlay` is declared with `parent=""` rather than under `Widget.Material3.Button.IconButton`. Its whole population is `ImageButton`, and the Material style blanks `android:background` because `MaterialButton` paints its own - inheriting it would strip the borderless ripple from every one of those buttons. `Widget.FastMediaSorter.Player.OverlayPanel` has a `.Docked` variant that keeps the scrim and drops the elevation, for a panel pinned in the layout flow rather than floating over it; `textViewerContainer` uses it, so adopting `PlayerOverlayPanel` did not change its z-order against the search panel.

`activity_player_unified.xml:228-255` holds a second, parallel top bar in the same file - a `MaterialToolbar` plus a `playbackButtonRow` of 5 `Widget.Material3.Button.IconButton`. Both bars are live in `PlayerActivity` (owner ruling 2026-09-18), so both are migrated. `topCommandPanel` is done; `controlsOverlay` waits on the toolbar widget style S3248 authors, because a toolbar style invented here would be that ticket's answer given twice.

#### Floating scroll and transfer controls

- A floating circular action uses `ActionBarView` with `Placement.FLOATING`, or `com.google.android.material.floatingactionbutton.FloatingActionButton` where the Material FAB semantics are actually wanted. The current form at `activity_browse.xml:94-103` is 4 raw `ImageButton` with `android:background="@drawable/bg_scroll_button"` and no elevation attribute, which is neither.
- A status pill (`activity_browse.xml:132`) keeps `?attr/colorSecondaryContainer` and gains the badge text appearance from section 1.3.
- `ui/common/backgroundop/BackgroundOperationBarView` is a `LinearProgressIndicator` subclass attached programmatically with `isClickable = false` and `isFocusable = false`. It is a progress hairline, not a panel, and is explicitly out of this primitive's scope.
- `BottomAppBar` is not used anywhere in the module and is not introduced by this standard. `ActionBarView` with `Placement.BOTTOM` covers the role.

#### The launcher taskbar

`launcher_taskbar.xml` is bottom-anchored with `?attr/colorSurfaceVariant` and no elevation. It adopts `Widget.FastMediaSorter.Bar.Action.Bottom` for the container and keeps `Widget.FastMediaSorter.Launcher.TaskbarDone` for its one bespoke action, which already parents `Widget.FastMediaSorter.Button.Filled` and therefore already satisfies section 1.1.

---

## 3. Interaction and Navigation Contracts

CLAUDE.md Rule 16 (keyboard, D-pad/TV and mouse input, with `focusable`, `clickable` and `nextFocus*` declared) and Rule 17 (UI inside the `systemBars` plus `displayCutout` safe bounds in both orientations) are the invariant source. This section states how those two rules are satisfied mechanically rather than per author.

### 3.1 The keyboard contract is a property of construction

- `ui/dialog/DialogKeyboardDelegate` is the one keyboard contract in the module: Enter to the caller's confirm lambda, Escape to `dismiss()`, the help key to `InputHelpDialogFragment`, Space to a toggle on a focused `CompoundButton` or `Chip`, arrow keys to a `focusSearch` move, routed through `util/KeyboardShortcutHandler` with `UiSurface.DIALOG`. The key map is already centralized and unit-tested; only its adoption is not.
- Every dialog raised through `AppDialog` and every sheet extending `BaseAppBottomSheet` has it. A surface opting out passes `keyboardContract = false` and states why in a comment, which is how `CaptureDialogFragment` declares its genuine exemption - it exists to record a keystroke and the delegate would swallow the very key it captures.

Divergence replaced: 34 files call the delegate, of which 27 are among the 47 Fragment-based dialog classes, leaving 20 with no keyboard contract at all and only one of those 20 with a stated reason. None of the 196 `MaterialAlertDialogBuilder` sites and none of the 29 legacy builder sites wires it. The sharpest instance is `WebViewAuthDialogFragment`: a third-party login form reachable from the share flow, with no Escape route and no D-pad contract.

### 3.2 Focus traversal is declared, not guessed

- A row or panel with more than two focusable children declares `nextFocusDown`, `nextFocusUp`, `nextFocusLeft` and `nextFocusRight` among them, or has a compound view that sets the traversal order programmatically. `MediaItemView` does the latter for its own children, which covers `item_media_file.xml`'s six focusable controls.
- Traversal between a row and the bars above and below it stays a per-screen declaration, because only the screen knows what is adjacent.
- `nextFocus*` declared in `res/layout/` is declared in `res/layout-land/` too. A focus chain that exists in portrait and not rotated is a regression the user meets by turning the phone.
- A focus chain that ends is acceptable; a focus chain that ends in a place the user cannot leave is not. `DialogKeyboardDelegate.moveFocus` delegates to `View.focusSearch`, which has no wrap-around, so arrow-down from the last row of a scrolled list reaches the confirm button and then returns null. Where a surface needs a closed loop, the last element's `nextFocusDown` names the first element explicitly.

Divergence replaced: only 6 of 74 dialog and sheet surfaces declare any `nextFocus*` (`dialog_cross_device_packets.xml`, `dialog_delivery_prompt.xml`, `dialog_enable_all_explainer.xml`, `dialog_network_monitor_clear_history.xml`, `dialog_stream_info.xml`, `dialog_undo_folder_copy.xml`), only 2 of those repeat it in landscape, and 2 of them carry the attributes while wiring no keyboard delegate, so focus moves but Escape does not dismiss. Zero sheet layouts declare any. Among item layouts, 43 of the 64 `main` files set `android:focusable` and zero set any `nextFocus*`.

### 3.3 Visible focus indication is a style property

- A focusable surface carries a state-list background that renders a focused state. `@drawable/item_focus_selector` is the list-row token, delivered by `Widget.FastMediaSorter.Item.Row`; `FocusMaterialCardView` paints the ring for cards; `@drawable/toolbar_action_item_focus_selector` and `@drawable/focus_button_background` cover bar actions.
- A view that handles touch input handles key input. Five interactive custom views fail this today: `LauncherScrollThumbView` and `PrefetchOverlayView` and `TranslationOverlayView` never set `isFocusable` despite drag, tap-dismiss and pinch gestures respectively; `GameBoardView` and `EdgeGestureSchemaView` are focusable and clickable but implement no `onKeyDown`, so their swipe and pinch input has no D-pad equivalent. Both `CropOverlayView` classes and `VerticalSeekBar` already do it correctly and are the reference. A view handling no input at all is exempt, which covers the two audio visualisers, `NetworkPathDiagramView` and `SensorSeriesChartView`.

### 3.4 Touch feedback

- Every tappable surface has a ripple: `?attr/selectableItemBackground` for a bounded target, `?attr/selectableItemBackgroundBorderless` for a circular icon target, or a state-list drawable that includes a ripple layer. A row with no root background has no ripple, which is 26 of the 64 `main` item layouts.
- Where a row needs both a persistent selection state and a ripple, the two are separate layers: the state-list drawable as `android:background` and `@drawable/item_interaction_overlay` as `android:foreground`. Setting a flat colour on the root replaces the first and loses both.
- Minimum touch target is 48dp, delivered by the button styles rather than by per-layout `minHeight`. `Widget.FastMediaSorter.Button.DialogCancel` uses `dialog_cancel_button_min_height` as exactly that floor.

### 3.5 TalkBack landing

`DialogAccessibilityHelper` posts a focus event 100 ms after `show()` onto the positive, neutral or negative button, falling back to a walk of the content view. It is called by `AppDialog` and by `BaseAppBottomSheet` rather than by 20 files out of 74 surfaces, and its content walk deepens from one nesting level to a full tree search, which is only safe once `dialog_shell.xml` makes the tree shape predictable. Today the one-level fallback silently no-ops on most surfaces, because 38 of the 60 `main` dialog layouts are rooted in a `LinearLayout` and 27 contain a scrolling container, putting the first focusable control two or more levels down.

---

## 4. WindowInsets, Edge-to-Edge and Cutout Rules

CLAUDE.md Rule 17 is the invariant. There is one mechanism in this module and every surface class uses it.

### 4.1 The single helper

`View.applySystemBarInsetPadding(..)` in `app_v2/src/main/java/com/sza/fastmediasorter/utils/ViewExtensions.kt` is the only sanctioned way to inset a view. Its signature:

```kotlin
fun View.applySystemBarInsetPadding(
    applyLeft: Boolean = true,
    applyTop: Boolean = true,
    applyRight: Boolean = true,
    applyBottom: Boolean = true,
    useStatusBarHeightFallback: Boolean = true,
    suspendWhile: (() -> Boolean)? = null,
    onApplied: ((left: Int, top: Int, right: Int, bottom: Int) -> Unit)? = null,
)
```

Why nothing else: it already takes `maxOf(systemBars, displayCutout)` per edge, so Rule 17's cutout half is satisfied by construction rather than by a second call; it remembers the view's designed padding as the base on the first call, so re-applying with a different edge mask recomputes from the bare view instead of stacking (the S1766 regression, where the launcher desktop lost a navigation bar's height on every foreground return); and `suspendWhile` lets a surface sit out an inset episode it starts and ends itself (S2667, where a single padding-driven layout pass measured 1.53 s of a 1.555 s frame). A hand-rolled `setOnApplyWindowInsetsListener` reproduces none of that and is refused.

The same file provides `WindowInsetsCompat.getStatusBarHeightSafe(resources)` for the case where a raw inset is needed rather than a padding.

### 4.2 What each surface class applies

- **Activity** - the content root applies all four edges. The existing consumers are the pattern: `SettingsActivity`, `WelcomeActivity`, the four standalone player activities and the three cloud folder picker activities.
- **Fragment hosted in an Activity** - applies nothing. The host already did it, and a second application on the same tree is exactly the stacking case the helper's base-padding memory exists to survive.
- **Dialog** - `AppDialog` calls `Dialog.applyDialogInsets()`, an extension built on `applySystemBarInsetPadding`, on the dialog's decor content. A dialog that does not fill the screen still needs it, because a cutout can cross a dialog positioned near the top edge.
- **BottomSheet** - `BaseAppBottomSheet` applies the bottom edge to the content slot in `onViewCreated`, unconditionally. Nothing else: left, right and top are the host's business, and a sheet never reaches the status bar.
- **Full-bleed dialog** - applies all four edges programmatically. `android:fitsSystemWindows` on the layout root is not a substitute and is not added to new layouts; it is kept only where it already exists.
- **Overlay manager drawing over a player or the desktop** - applies the edges it occupies, and uses `suspendWhile` for any episode that hides and restores the system bars itself.

### 4.3 The measured gap this replaces

- Zero of the 47 Fragment-based dialog classes and zero of the 4 raw `BottomSheetDialog` constructions register any inset listener. All 31 files in the module that do apply insets are Activities, non-dialog Fragments or overlay managers. The helper exists and no dialog or sheet calls it.
- Only 2 of the 65 dialog layouts declare `android:fitsSystemWindows` (`dialog_launcher_settings.xml` and `dialog_launcher_wallpaper_settings.xml`, each mirrored in `layout-land`), and they are also the only two dialogs that expand to fill the screen - the case where it matters most and where the other full-bleed surfaces do not have it.
- `LauncherWallpaperSettingsDialogFragment` forces `setLayout(MATCH_PARENT, MATCH_PARENT)` with no inset listener at all, so its content extends under both bars with only the layout attribute to save it.
- `WebViewAuthDialogFragment` sizes its own window and hosts a `WebView` whose content cannot be inset from outside, so a cutout crosses a third-party login page. This one needs the inset applied to the `WebView`'s container before the page loads, not after.
- The only `displayCutout` consumer on the dialog side is `CameraSettingsDialogRotationManager`, and it reads the cutout to compute a rotated window size rather than to inset content away from it.
- No sheet does anything programmatic about the navigation bar. The module relies on the Material `BottomSheetDialog` default, which insets only when the host window is already edge-to-edge and the theme resolves `paddingBottomSystemWindowInsets` true. Because sheets are raised from hosts in mixed edge-to-edge states - `BrowseEdgeToEdgeHelper` makes Browse edge-to-edge while `PdfThumbnailSheet` and `EpubSearchAndTocPresenter` are raised from `SystemBarsManager`-managed player surfaces - the same sheet behaves differently depending on which screen opened it. That is the defect the unconditional bottom inset in `BaseAppBottomSheet` removes.

---

## 5. Landscape and Multi-Column Adaptability

### 5.1 The parity rule

CLAUDE.md Rule 11: editing `res/layout/*.xml` requires the equivalent edit in the `res/layout-land/*.xml` counterpart where one exists. This catalogue adds the direction the rule does not state: **prefer having no counterpart to having a stale one.** A second file is a second thing to keep true, and 92 of them already exist in `app_v2/src/main/res/layout-land/`.

A landscape counterpart is warranted when, and only when, the two orientations need a different **structure**: a column becomes a row, a panel moves from below the content to beside it, a dialog's action pair moves from a stacked block to an inline one. A counterpart is not warranted for a different size, a different margin or a different number of visible lines.

### 5.2 A shared layout with qualified dimens beats a second file

When the delta is dimensional, express it in `res/values-land/dimens.xml` or in a size-qualified variant and keep one layout file. The qualifier directories are already live in this module: `values-land`, `values-sw320dp`, `values-sw320dp-land`, `values-sw480dp`, `values-sw480dp-land`, `values-sw600dp`, `values-sw720dp` and `values-w600dp`.

Choose by what actually varies:

- Orientation alone, dimensional delta - `values-land/dimens.xml`, one layout.
- Orientation alone, structural delta - a `layout-land/` counterpart, and the two files are edited together forever after.
- Available width, dimensional delta - `values-w600dp/dimens.xml`, one layout.
- Available width, column-count delta - a `layout-w600dp/` counterpart, as `activity_main.xml` already has.
- Column count in a `RecyclerView` - neither. Resolve the span count from an integer resource in `values-w600dp/integers.xml` and set it on the `GridLayoutManager`, so the layout file does not fork at all.

### 5.3 The zero item landscape counterparts

Measured: **0** `item_*.xml` under `app_v2/src/main/res/layout-land/`, and none in any flavor `layout-land`, against 92 landscape screen layouts and 81 item layouts in total. Every list row and grid cell in the app is a portrait-authored layout reused verbatim rotated, inside screens that do have a landscape variant.

The decision this standard takes, in preference order:

1. **Do not author 81 counterparts.** The count is the same either way and the maintenance doubles, which is the outcome Rule 11 exists to prevent rather than to cause.
2. **`MediaItemView` reads the configuration itself** and switches its internal constraint set. A compound view is where an orientation decision belongs, because one class covers the layouts it consolidates and the switch is testable without a device rotation.
3. **Dimensional deltas go to `values-land/dimens.xml` - unless a smallestWidth bucket already declares the key.** smallestWidth outranks orientation, so a key declared in `values-sw320dp` can never be overridden from `values-land`; that override belongs in the combined `values-swNNNdp-land` bucket, and `scripts/quality/assert-qualifier-shadowing.ps1` refuses the dead form. `item_grid_thumbnail_size` is exactly that trap and is no longer the example to copy: this text named it, S3254 phase 03 followed the text, and the 120dp it wrote resolved on no device until S3343 removed it. `item_row_padding_vertical`, declared in no smallestWidth bucket, is the example that works.
4. **A row layout that is genuinely not consolidated onto `MediaItemView` and genuinely needs a different structure rotated** gets a counterpart, with the reason stated in a comment at the top of both files.

**The grid cell does not compact in landscape, and the thumbnail container is not the place to make it (S3344).** Three findings close the question the S3254 attempt left open:

- `MediaFileAdapter.GridViewHolder.bind()` overwrites `flThumbnailContainer`'s height on every bind with the user's icon-size setting, halved under compact elements. `@dimen/item_grid_thumbnail_size` in `item_media_file_grid.xml` is the inflation seed and nothing more, so an override in any bucket - combined or not - would be dead a second time, in a form that reads as alive.
- `MediaItemView` honours the dimension for real, through `constrainHeight` in its grid constraint set, but no screen inflates it yet: the class is referenced only by its own unit test. Tuning the value would freeze a number nobody has judged on screen.
- The cell already adapts on rotation through the span rather than the height. Landscape reads `grid_column_count_landscape` - 6 on phones, 5 on `sw600dp` tablets - against `grid_column_count`'s 3, while the window widens by 1.78 to 2.22 times across the 16:9 to 20:9 range, so the column pitch stays within roughly 11% of the portrait pitch and the row carries twice as much.

The question reopens only when `MediaItemView` is adopted by the Browse grid. That adoption ticket decides whether the cell's height comes from the resource or stays with the user's setting; only the first answer brings the landscape override back, and the combined `values-swNNNdp-land` buckets are then the mechanism.

The four surfaces that lose a declared focus chain when rotated are the exception that must be fixed rather than deferred: `dialog_enable_all_explainer.xml`, `dialog_network_monitor_clear_history.xml`, `dialog_stream_info.xml` and `dialog_undo_folder_copy.xml` carry `nextFocus*` in portrait with no landscape counterpart or no attributes in it, so a D-pad-navigable dialog stops being navigable on rotation. Section 3.2 already requires the parity; this is where the four are named.

### 5.4 Dialog landscape coverage

28 of the 65 dialog layouts have a `layout-land` counterpart and 37 do not, which today is an author-by-author choice rather than a rule. Once `dialog_shell.xml` puts the content in a `NestedScrollView` with the action pair outside it, most of the 37 have no structural reason to fork: a short landscape window scrolls instead of clipping. A dialog keeps or gains a counterpart only where its body is genuinely two-dimensional - a colour picker, an image editor, a gesture schema - and the comment in both files says which.

Zero landscape sheet layouts exist, and that is correct rather than a gap: a bottom sheet is anchored to one edge and sized by its content, and `sheet_send_to.xml` already records the reasoning in a layout comment. No landscape sheet counterpart is authored.

---

## 6. Adoption and Enforcement

Adoption is opportunistic, matching the policy `docs/ARCHITECTURE.md` already states for the Button Taxonomy: a hand-rolled duplicate of an owned role is technical debt, migrated when its screen is next touched, never as a forced rework campaign. The sequencing constraint is that a primitive exists before any surface migrates onto it, and a gate lands after the migration of the sites it would refuse, so that no gate is introduced red.

Gates that already enforce part of this standard:

- `scripts/quality/assert-dialog-cancel-style.ps1` enforces section 2.1's action pair. It prints `baseline 0 | actual 0 | delta 0` today, and that clean verdict covers less than it appears to: it scans only `dialog_*` and `bottom_sheet_*` layout files for `MaterialButton` elements, so it sees neither the builder-constructed dialogs, nor the 14 `main` dialog layouts carrying no `MaterialButton` at all, nor `sheet_send_to.xml`, `launcher_signal_list_sheet.xml` and `sheet_launcher_section_actions.xml`, whose names match neither scanned prefix.
- The hex-colour dimension of `scripts/quality/assert-neuroslop.ps1` enforces section 1.2's literal ban (CLAUDE.md Rule 19). What it inspects must be confirmed against the 6 `app:tint` literals in section 1.2, which reach a tint attribute rather than a background attribute.
- `scripts/quality/assert-source-gates.ps1` carries the `compose-island` and `deprecated-pm-flags` dimensions and is the natural host for new source-side dimensions.

Gate dimensions that each new rule would require. None is written in this ticket; each is named so the rule has a known enforcement path.

- Section 1.2, tint literals: widen the neuroslop hex dimension to `app:tint`, `app:iconTint` and `app:backgroundTint` in every `res/layout*` directory, with a seeded baseline at the post-migration count.
- Section 1.3, no `android:textSize` in a layout: a new ratchet dimension counting the attribute across `res/layout*`, seeded at the current 33 in item layouts plus whatever the screen layouts add, and only ever lowered.
- Section 1.3, no Material 2 bridge token: a dimension refusing `textAppearanceCaption`, `textAppearanceBody1`, `textAppearanceBody2`, `textAppearanceSubtitle1` and `textAppearanceSubtitle2`, seeded at 23.
- Section 1.4, no `dp` literal on an elevation attribute, and no new elevation dimen outside the sanctioned set.
- Section 2.1, no legacy builder: a dimension refusing `AlertDialog.Builder(` under `app_v2/src/main`, exactly as Rule 21 refuses the raw PackageManager overloads, landing after the 29 sites have moved.
- Section 2.1, sheet naming and gate reach: the `assert-dialog-cancel-style.ps1` file filter reaches `sheet_*` and `*_sheet.xml`, delivered by S3244 in the same change that renamed `dialog_stream_offload_offer.xml`, at `delta 0`.
- Section 2.1, no hand-rolled window sizing: a dimension refusing `window.setLayout(` outside `DialogWindowSizer`, seeded at 22.
- Section 2.2, no root-background mutation in an adapter: a dimension refusing `setBackgroundColor(` and `setBackgroundResource(` on a ViewHolder root inside any `*Adapter.kt` under `ui/`, seeded at 9.
- Section 2.2, one Glide entry point: a dimension refusing `Glide.with(` outside `MediaItemThumbnailBinder` for files under the adapter packages, seeded at the current 3 adapter call sites.
- Section 2.3, no styleless toolbar: `styleless-toolbar` in `scripts/quality/lib/source-matchers.ps1` refuses a `StandardToolbar` or `MaterialToolbar` declaration without a `style` attribute. Delivered by S3248 at 0, not at the 27 this section first planned for: the families migrated inside that one ticket, so the baseline never needed a descent.
- Section 2.4, no raw `ImageButton` in a bar or panel layout: `raw-imagebutton-in-bar` in `scripts/quality/lib/source-matchers.ps1`, delivered by S3249. Seeded at **268**, not the 57 this section predicted: the prediction counted two files, the dimension walks every layout root and every variant, so the player's panels are inside it too. A hit is an `<ImageButton>` whose enclosing container's id names a strip (`bar`, `panel`, `controls`, `operations`, `toolbar`, `strip`), so a list row's trailing action is not judged by it. S3249 removed 33 - eleven controls in each of the three `activity_browse.xml` variants.
- Section 3.1, keyboard contract present: a dimension requiring every `DialogFragment` and `BottomSheetDialogFragment` under `app_v2/src/main` either to route through `AppDialog` or `BaseAppBottomSheet`, or to name its exemption, seeded at the current 20 unwired classes.
- Section 3.2, focus parity: a dimension failing when a `res/layout/*.xml` declares `nextFocus*` and its existing `res/layout-land/` counterpart does not.
- Section 4, insets present: a dimension requiring a dialog or sheet class to reach `applySystemBarInsetPadding` transitively, which is only checkable once the two base constructs exist and is therefore the last of these to land.
- Section 5.2, no orphan counterpart: a dimension failing when a `res/layout-land/*.xml` has no `res/layout/` original, which is the failure mode the "prefer no counterpart" direction introduces.
