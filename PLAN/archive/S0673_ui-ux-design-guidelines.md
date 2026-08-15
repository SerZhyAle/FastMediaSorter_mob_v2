# S0673 - Actionable empty state for the streams catalog

**Status:** Archived

## 1. Goal

Turn the streams catalog (Трансляции) empty state from a text-only message into an actionable one: an icon plus a primary "add" and a secondary "import" action directly under the message, so the recovery path is one tap instead of a hunt for unlabeled toolbar glyphs.

## 2. Background

- Distilled from a screenshot UI/UX review; the only surviving recommendation is the empty-state rule (the rest were either existing engineering decisions or rejected variants).
- Streams catalog empty state previously rendered a single centered `TextView` ("No streams yet. Add a URL or import a list.") with no icon and no inline action - the user had to find the unlabeled toolbar add/import glyphs to recover.

## 3. Rule

- An empty state pairs the guidance text with a primary action button placed directly under the message.
- Add a light icon above the message to anchor the eye and reduce the dead-void feel on tall screens.
- Map the recovery path to one tap on the named action (add / import); do not force a glyph hunt across an unlabeled toolbar.

## 4. Implementation

Layout (portrait + landscape, Rule 11):
- `app_v2/src/main/res/layout/activity_streams.xml`
- `app_v2/src/main/res/layout-land/activity_streams.xml`
- Replaced the bare `TextView @+id/tvEmpty` with a centered vertical `LinearLayout @+id/emptyStateView` containing: `ImageView` (`@drawable/ic_cast`, `empty_state_icon_size` 64dp, tint `?attr/colorOnSurfaceVariant`), the existing `tvEmpty` message, a primary `MaterialButton @+id/btnEmptyAddUrl` (`@string/streams_add`), and a secondary text `MaterialButton @+id/btnEmptyImport` (`@string/streams_import`, style `Widget.FastMediaSorter.Button.Text`).
- Reused existing `empty_state_*` dimens; mirrored the inline pattern from `activity_browse.xml` (no reusable empty-state widget exists in the project).
- Both buttons are `focusable` with `nextFocus*` wired for D-pad/TV (Rule 16).

Kotlin (`app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`):
- New buttons reuse the existing toolbar handlers, no duplicated logic: `btnEmptyAddUrl -> showSourceDialog(isImport = false)`, `btnEmptyImport -> showImportChooser()`.
- Visibility toggle repointed from `tvEmpty` to the new container: `binding.emptyStateView.isVisible = state.isEmpty` (the `state.isEmpty` derivation in `StreamsViewModel` is unchanged).

Strings: no new keys - reused `streams_empty`, `streams_add`, `streams_import` (full EN/RU/UK, already used by `menu_streams.xml`, so wording stays consistent between menu and empty state).

## 5. Validation

- `.\a.ps1 fc` (compileStandardDebugKotlin + processStandardDebugResources): BUILD SUCCESSFUL - view-binding fields generated, Kotlin references resolve, resources valid.

## 6. Out of scope

- Other text-only empty states across the app (cloud folder pickers, network discovery, duplicates, auth sessions, playback track lists, settings-search no-results) share the same weakness but are not changed here.
- Consolidating the 6+ duplicated inline empty-state blocks into a parameterized `view_empty_state.xml` is a separate refactor.
