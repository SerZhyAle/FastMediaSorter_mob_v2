<!-- auto-approved by /spec-all - 2026-06-21 -->
# Strategic Specification: S0567 - Settings, forms, and dialog components unification

**Ticket:** S0567
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-20
**Tier:** 3 - Standard
**Roadmap entry:** Ad-hoc - UI unification initiative (2026-06-20)
**Tactical plan:** `PLAN/S0567_ui-settings-forms-dialogs-unification/INDEX.md`

---

## 0. Raw capture (verbatim owner input)

> недавно на проекте проходила масштабная работа по унификации интерфейса кнопок, тумблеров, сворачиваемых групп. Изучи интерфейс, что ещё имеет смысл и полезно объеденить в одни хорошие UI-классы решения чтобы переиспользоовать повсеместно? Создай об этом спецификацию.

---

## 1. Problem

Following the successful unification of buttons (Button Taxonomy), toggle rows ([SettingsToggleRow.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsToggleRow.kt)), and collapsible groups ([CollapsibleSectionHeader.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt)), several settings screen elements, parameter forms, and selection dialogs still rely on legacy, ad-hoc, hand-crafted XML layouts and dynamic code styling.

This creates several issues:
1. **Ad-hoc Navigation Rows**: Navigation lines (e.g. "Device Profile" in [fragment_settings_general.xml](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/res/layout/fragment_settings_general.xml#L51-L63)) are built manually using nested horizontal `LinearLayout`s, violating the uniform padding, height (Touch Target), typography, and ripple-feedback styling.
2. **Scattered Spinner Layouts**: Standard dropdown selectors/spinners (e.g. language/theme in [fragment_settings_general.xml](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/res/layout/fragment_settings_general.xml#L33-L48)) are implemented inconsistently. Some use basic `Spinner` tags, others use Material dropdowns, and they lack inline helper icon support.
3. **Manual Help Inputs**: Numeric inputs (e.g. slideshow interval in [fragment_settings_playback.xml](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/res/layout/fragment_settings_playback.xml#L24-L40)) duplicate code for placing `TextInputLayout` side-by-side with an `ImageButton` for the tooltip.
4. **Dynamic Code Styling Anti-pattern**: Selection dialogs (such as [ResourcePickerDialog.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ResourcePickerDialog.kt#L100-L144)) build item list buttons dynamically inside Kotlin code using hardcoded hex/system colors (e.g. `Color.LTGRAY`, `Color.WHITE`, `R.color.blue_500`), which completely bypasses the Material3 theme styling and the established Button Taxonomy.
5. **Horizontal Form Pairs**: Input forms (e.g. Host + Port in [fragment_resource_editor.xml](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/res/layout/fragment_resource_editor.xml#L78-L110)) require manual `layout_weight` and margin configurations on each screen.
6. **Manual Tooltip Wiring**: Settings, form, and dialog surfaces still expose tooltip icons as raw `ImageButton` / `ImageView` controls and then wire `TooltipDialog.show(..)` manually in fragment/helper code, increasing controller boilerplate and making help behavior inconsistent.
7. **Checkbox Pattern Duplication**: Resource screens repeat the same `MaterialCheckBox + subtitle + optional help icon` structure in both Add Resource and Resource Editor flows instead of using a single reusable row.
8. **Taxonomy Drift In Dialog/Form Buttons**: Several dialogs and form sections still use raw `Widget.Material3.Button.*` styles or ad-hoc runtime buttons instead of the project button family, especially where the same "action + help" composition repeats.

## 1.1 Research Findings

The current draft direction is valid, but the codebase survey shows the scope is broader and more concrete than the original text captured:

1. **Legacy raw `Spinner` usage is still live in four logical surfaces, duplicated across `layout/` and `layout-land/`:**
   - `fragment_settings_general.xml` - Language and Color theme.
   - `dialog_filter_resource.xml` - Sort selector.
   - `dialog_player_settings.xml` - Subtitle language and Audio track.
   - `dialog_translation_settings.xml` - Font size and Font family.
2. **Manual clickable selection/value rows are a distinct reusable pattern, not just a one-off in General settings:**
   - `fragment_settings_general.xml:50-63` - Device Profile.
   - `fragment_settings_general.xml:320-360` - Saved Authorizations.
   - `fragment_settings_general.xml:660-689` - Statistics entry.
   - `fragment_settings_destinations.xml:779-789` - Link autodownload resource.
   - `fragment_settings_destinations.xml:839-877` - Screenshot gesture action/destination selectors.
3. **Inline help icon debt is spread across both XML and controller glue:**
   - `fragment_settings_playback.xml:23-39` + `PlaybackSettingsFragment.kt:204-210`.
   - `fragment_settings_general.xml:366-378`, `426-441`, `526-538` + `GeneralSettingsFragment.kt:295-304`, `GeneralSettingsViewSetupHelper.kt:320-327`.
   - `fragment_settings_destinations.xml:183-193` + `OperationsDestinationsManager.kt:54-60`.
4. **Field-pair layouts are repeated in multiple resource-entry surfaces and are not limited to one screen:**
   - `fragment_resource_editor.xml:78-109` - Host + Port, Username + Password.
   - `fragment_resource_editor.xml:289-304` - Comment + Access PIN.
   - `activity_add_resource.xml:163-205` - SMB Username + Password, Share Name + Resource Name.
   - `activity_add_resource.xml:351-358` - SMB Domain + Port.
   - `activity_add_resource.xml:495-510` - SFTP Path + Resource Name.
5. **Checkbox + subtitle + optional help is another repeated form primitive:**
   - `activity_add_resource.xml:219-250` - SMB scanning rows including Remember File List help.
   - `activity_add_resource.xml:524-555` - SFTP scanning rows with the same structure.
   - `fragment_resource_editor.xml:236-242` - Remember File List editor row.
6. **List-picking dialogs are inconsistent in both rendering strategy and theme usage:**
   - `ResourcePickerDialog.kt` and `DestinationPickerDialog.kt` create runtime `AppCompatButton`s with manual padding, text size, and color state.
   - `FileOperationDestinationDialog.kt` creates a runtime grid of colored destination buttons; this surface may remain specialized, but it still needs a shared adapter/item-rendering strategy rather than hand-built widgets.
7. **Dialog/form action rows repeat a compact `button + help icon` composition that deserves its own primitive:**
   - `dialog_gif_editor.xml` repeats the same structure three times.
8. **There is already a positive in-project direction to emulate:**
   - `SettingsToggleRow.kt` and `CollapsibleSectionHeader.kt` prove that compound views with built-in tooltip ownership fit the codebase.
   - `dialog_copy_to.xml` already uses the unified S0538 dialog action styles.
   - `dialog_scheduled_operation.xml` + `ScheduledOperationDialog.kt` already move toward Material exposed dropdowns with adapter-backed data.

---

## 2. Goals

1. **Introduce Reusable Compound Views for Forms**:
   - `SettingsSelectionRow`: Row for launching actions or switching screens (Title + Value or Subtitle + optional Helper + trailing Chevron or trailing slot + click ripple effect). Primary migration targets: Device Profile, Saved Authorizations, Statistics, Link autodownload resource, Screenshot gesture selectors.
   - `SettingsDropdownRow` (or `SettingsSpinnerRow`): Row wrapping a Material3 ExposedDropdownMenu with integrated helper icon.
   - `SettingsInputRow`: Row containing a text/numeric input field with integrated helper icon.

2. **Introduce Layout Helper for Fields Grid**:
   - `FormFieldPairLayout`: Custom container to place two text fields side-by-side (like Host + Port or Username + Password) with preset weights, margins, ratio presets (`1:1`, `2:1`), and compact scaling.
   - `FormCheckboxRow`: Reusable checkbox row for `checkbox + subtitle + optional help icon`, aligned with the existing Architecture Pattern B and usable in Add Resource / Resource Editor scanning sections.
   - `ActionHelpRow`: Compact action strip for `button + help icon` compositions used by dialogs or dense form sections.

3. **Standardize Selection Lists in Dialogs**:
   - `ListSelectionDialog<T>`: A generic, reusable dialog/bottom sheet component that displays a selectable list of items using consistent styling, replacing the legacy `ResourcePickerDialog`, `DestinationPickerDialog`, etc., and enforcing the Button Taxonomy at runtime.

4. **Reduce Controller Boilerplate**:
   - New reusable views must own their tooltip icon visibility, help payload, and basic click surface wiring so that fragments/helpers stop manually binding repeated `TooltipDialog.show(..)` handlers for every row.

## 2.1 Priority Order

1. **P0 - High leverage / high repetition**
   - `SettingsSelectionRow`
   - `SettingsDropdownRow`
   - `SettingsInputRow`
   - `ListSelectionDialog<T>`
2. **P1 - Medium leverage / large form cleanup**
   - `FormFieldPairLayout`
   - `FormCheckboxRow`
   - `ActionHelpRow`
3. **P2 - Follow-up normalization**
   - Specialized dialog grids and domain-specific button surfaces that must preserve semantic visuals (for example destination color chips) but should still migrate to shared item renderers/adapters.

## 2.2 Non-Goals

1. Do not flatten every domain-specific dialog into one mega-widget.
2. Do not remove semantic destination colors where color is user data rather than decoration.
3. Do not change `TooltipDialog` itself under this ticket; the goal is to standardize how surfaces host and trigger it.
4. Do not migrate player-only dark-surface controls that intentionally live outside the settings/forms visual language.

---

## 3. Constraints & Guidelines

- **Typography & Dimensions**:
  - Keep main labels bound to `@dimen/toggler_title_text_size` (14sp).
  - Keep secondary/sub-labels bound to `@dimen/toggler_desc_text_size` (12sp).
  - Minimum touch target height must be at least `@dimen/button_height` (or 56dp).
- **Theme integration**:
  - All visual assets, backgrounds, text colors, and chevrons must be themed via standard theme attributes (`?attr/colorOnSurfaceVariant`, `?attr/selectableItemBackground`, etc.). Hardcoded HEX colors are prohibited.
- **Accessibility & Focus**:
  - Elements must support TalkBack (automatic announcement of values) and keyboard/D-pad navigation.
- **Public attributes naming**:
  - New compound views must follow the same prefixed `attrs.xml` convention already used by `SettingsToggleRow` (`str_*`) and `CollapsibleSectionHeader` (`csh_*`). No generic unprefixed public attrs.
- **Language & Localization**:
  - Support EN, RU (with grammatically correct Ё/ё), and UK locales.
- **Orientation parity**:
  - Migration targets present in both `res/layout/` and `res/layout-land/` must be migrated symmetrically. This ticket is about reducing duplicated UI debt, not moving it into a different orientation folder.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** No new screens and no relocations - this ticket replaces existing settings/form/dialog elements in place with reusable compound views. Visual layout, attribute schema, and migration targets are fixed by §5. Per-row visual parity is measured against the existing `SettingsToggleRow` / `CollapsibleSectionHeader`; any ambiguous per-surface placement is resolved via `/ui-clarify` at migration time rather than guessed.
- **Visual reference:** `dialog_copy_to.xml` (S0538 action-button taxonomy) and `dialog_scheduled_operation.xml` (adapter-backed Material exposed dropdown) are the canonical visual references; new views must match their themed styling with no hardcoded HEX.
- **Accessibility:** D-pad/remote + keyboard + mouse are a mandatory input contract; TalkBack announces row values; focus indication is non-color. Each compound row behaves as one predictable focus stop.
- **Communication policy:** No new user-visible strings are expected (migration reuses existing labels and help payloads); any genuinely new string follows `docs/COMMUNICATION_POLICY.md` and ships EN/RU (Ё/ё)/UK in lockstep.
- **Validation level:** resource + mixed compile (`a.ps1 fr`, `a.ps1 fc`) per phase, anti-pattern shrink greps (§7), plus on-device verification of each migrated settings/dialog/form surface in portrait and landscape.
- **Owner sign-off:** 2026-06-21 - auto-approved by /spec-all; strategic design (§5) and migration sequence (§6.1) accepted as the implementation contract.
- **Related tickets:** S0538 (dialog action-button taxonomy) is the reference for dialog buttons; prior `SettingsToggleRow` / `CollapsibleSectionHeader` unification is reused prior art; no blocking `Sxxxx`.

---

## 4. Current Architecture Context

Currently, the custom UI controls are situated under `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/`.
- [SettingsToggleRow.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsToggleRow.kt) handles toggles.
- [CollapsibleSectionHeader.kt](file:///p:/ANDROID/FastMediaSorter_mob_v2/app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt) manages expandable card headers.
- `app_v2/src/main/res/values/attrs.xml` already defines a stable prefix-based public-attribute scheme for reusable widgets.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` and related settings helpers currently contain repeated manual tooltip/click wiring that should shrink after row unification.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ScheduledOperationDialog.kt` plus `dialog_scheduled_operation.xml` represent the closest existing reference for adapter-backed Material dropdown usage.
- `app_v2/src/main/res/layout/dialog_copy_to.xml` already follows the unified S0538 action-button taxonomy and should be treated as the dialog-button reference.

---

## 5. Proposed Strategic Design

### 5.1 SettingsSelectionRow (Compound View)
* **Visuals**:
  `[Title] [Help Icon]                       [Value / Subtitle] >`
* **Attributes** (`attrs.xml`):
  - `ssr_title` (string/reference)
  - `ssr_value` (string/reference)
  - `ssr_subtitle` (string/reference)
  - `ssr_showHelp` (boolean)
  - `ssr_helpTitle` (string/reference)
  - `ssr_helpMessage` (string/reference)
  - `ssr_showChevron` (boolean)
  - `ssr_icon` (reference, optional leading icon)
* **Behavior**:
  - Whole row is clickable/focusable.
  - Supports `value-only`, `subtitle-only`, or `value + subtitle` presentation without layout rewrites.
  - Owns tooltip visibility and optional trailing slot.
* **First migrations**:
  - Device Profile
  - Saved Authorizations
  - Statistics
  - Link autodownload resource
  - Screenshot gesture action / destination rows

### 5.2 SettingsDropdownRow (Compound View)
* **Visuals**:
  `[Dropdown Label / Title] [Help Icon]       [Dropdown (Menu Arrow) v]`
* **Attributes** (`attrs.xml`):
  - `sdr_title` (string/reference)
  - `sdr_showHelp` (boolean)
  - `sdr_helpTitle` (string/reference)
  - `sdr_helpMessage` (string/reference)
  - `sdr_entries` (reference to string-array)
* **First migrations**:
  - Language
  - Color theme
  - Filter dialog sort selector
  - Player settings subtitle/audio selectors
  - Translation font selectors
* **Implementation note**:
  - The row should wrap `TextInputLayout + MaterialAutoCompleteTextView` and make raw `Spinner` a legacy-only escape hatch.

### 5.3 SettingsInputRow (Compound View)
* **Visuals**:
  `[Input Label / Title] [Help Icon]          [TextInputField]`
* **Attributes** (`attrs.xml`):
  - `sir_title` (string/reference)
  - `sir_hint` (string/reference)
  - `sir_inputType` (integer, e.g. text/number)
  - `sir_showHelp` (boolean)
  - `sir_endIconMode` (enum/reference, optional for password toggles)
* **First migrations**:
  - Slideshow interval
  - Prefetch cache / cache-size fields with inline help
  - Default credentials row follow-up where the help icon currently lives outside the field group

### 5.4 FormFieldPairLayout
* **Purpose**:
  Shared container for two adjacent inputs with predefined ratios and spacing, eliminating repeated `layout_weight`, `marginStart`, and `marginEnd` tuning.
* **Use cases**:
  - Host + Port
  - Username + Password
  - Share Name + Resource Name
  - Domain + Port
  - Comment + Access PIN
* **Required behavior**:
  - Ratio presets: `1:1`, `2:1`
  - Baseline-alignment disabled by default
  - Safe stacking fallback for narrow widths or future compact variants

### 5.5 FormCheckboxRow
* **Purpose**:
  Reusable checkbox row aligned with the documented Pattern B, with optional subtitle and optional help anchor.
* **Use cases**:
  - Remember File List rows
  - Scanning option rows in Add Resource
  - Scanning option rows in Resource Editor
* **Required behavior**:
  - Subtitle indent uses `@dimen/checkbox_subtitle_margin_start`
  - Help icon is optional and only shown when payload exists
  - Must support keyboard / D-pad focus as one predictable row

### 5.6 ActionHelpRow
* **Purpose**:
  Small reusable row for `button + help icon` compositions that currently repeat in dialogs and compact form sections.
* **Use cases**:
  - `dialog_gif_editor.xml` repeated action/help strips
  - Potential future compact settings/button rows where the CTA is the primary element and the tooltip is secondary
* **Required behavior**:
  - Uses project button taxonomy styles, never raw `Widget.Material3.Button.*`
  - Keeps the help icon touch target at least 48dp
  - Supports multi-line button labels on narrow screens

### 5.7 ListSelectionDialog<T> (Generic Picker)
Instead of subclassing `DialogFragment` for every resource type:
```kotlin
class ListSelectionDialog<T> private constructor(
    private val config: SelectionConfig<T>
) : DialogFragment() {
    
    interface ItemFormatter<T> {
        fun getDisplayName(item: T): String
        fun getIcon(item: T): Drawable?
    }
    
    // Renders the list items using a unified MaterialCard / MaterialButton style.
}
```
* **Primary migrations**:
  - `ResourcePickerDialog`
  - `DestinationPickerDialog`
* **Secondary migration candidate**:
  - `FileOperationDestinationDialog` should reuse the same data/adaptor contract even if its final visual treatment stays color-aware and grid-based.

---

## 6. Architectural Decisions (ADR)

### ADR-1: Raw Spinner Deprecation
- **Decision:** All new settings/dialog dropdowns (`SettingsDropdownRow`) must render via Material3's exposed dropdown pattern (`TextInputLayout` + `MaterialAutoCompleteTextView`) rather than standard Android `Spinner` widgets.
- **Why:** Delivers modern, accessible UI/UX consistent with Material3 styling, including consistent text sizing and click targets.

### ADR-2: Selection Rows Are First-Class Components
- **Decision:** Clickable `title + current value + chevron` rows are promoted to an explicit reusable widget (`SettingsSelectionRow`) instead of being treated as ad-hoc layout exceptions.
- **Why:** The same behavior already appears in multiple settings surfaces, and each manual copy currently duplicates XML, focus handling, chevron placement, and click wiring.

### ADR-3: Code-based Layout Generation Decoupling
- **Decision:** Dynamic item list generation inside dialogs must delegate view rendering to a dedicated RecyclerView adapter using XML item views (e.g. `item_resource.xml`), and use `Widget.FastMediaSorter.Button.*` styles. Programmatic instantiation of buttons with hardcoded colors is strictly banned.
- **Why:** Enforces the "Button Taxonomy" and theme consistency across all dialogs.

### ADR-4: Reusable Rows Own Tooltip Payloads
- **Decision:** New compound rows must own help-icon visibility and tooltip payload handling directly instead of requiring every fragment/helper to wire raw icon click listeners manually.
- **Why:** This removes repetitive glue code and keeps tooltip behavior consistent across settings, forms, and dialogs.

### ADR-5: Attr Prefixes Stay Explicit
- **Decision:** Every new public compound view exposes prefixed attrs only (`ssr_*`, `sdr_*`, `sir_*`, `ffp_*`, `fcr_*`, `ahr_*` or equivalent final prefixes).
- **Why:** The project already established this convention with `str_*` and `csh_*`; extending it keeps XML APIs readable and collision-safe.

## 6.1 Proposed Migration Sequence

1. Build `SettingsSelectionRow` and migrate the obvious settings rows with chevrons.
2. Build `SettingsDropdownRow` and remove legacy raw `Spinner` usage from settings/dialog surfaces.
3. Build `ListSelectionDialog<T>` and migrate `ResourcePickerDialog` / `DestinationPickerDialog`.
4. Build `FormFieldPairLayout` and `FormCheckboxRow`; migrate Resource Editor and Add Resource repeated form structures.
5. Build `ActionHelpRow` and migrate compact dialog/form action-help strips such as GIF Editor.
6. Audit surviving special cases and explicitly mark long-term exceptions.

---

## 7. Verification Plan

### Automated Tests
- Spec stage:
  - Grep audit for referenced surfaces and candidate components.
- Implementation stage:
  - Run resource compilation checks: `./a.ps1 fr` to ensure layout changes and custom attributes compile cleanly.
  - Run mixed code/resource check: `./a.ps1 fc`.
  - Use targeted grep to confirm old anti-patterns are shrinking:
    - `rg "<Spinner" app_v2/src/main/res/layout app_v2/src/main/res/layout-land`
    - `rg "TooltipDialog.show" app_v2/src/main/java/com/sza/fastmediasorter/ui/settings app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource`
    - `rg "Color.WHITE|Color.LTGRAY|setBackgroundColor|setTextColor" app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog`

### Manual Verification
- Deploy to debug target.
- Open **Settings > General** and verify the "Device Profile" row matches the style of adjacent `SettingsToggleRow` items.
- Verify the Language / Color theme selectors use the new dropdown component.
- Verify all tooltips continue to launch `TooltipDialog` correctly when the inline help icon is tapped.
- Verify Add Resource and Resource Editor paired-field rows keep alignment in both narrow and wide widths.
- Verify migrated picker dialogs preserve keyboard / D-pad navigation and selected-item clarity without hardcoded runtime colors.
