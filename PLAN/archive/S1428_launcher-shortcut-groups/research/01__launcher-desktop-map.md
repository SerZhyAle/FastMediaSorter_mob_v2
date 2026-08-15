# S1428 research: launcher desktop map

Read-only research pass, 2026-08-08. Every claim carries a file:line citation. This file is mandatory
planning input for the tactical phases (per `/spec-tech` step 2).

## 1. Cell model

`app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCell.kt`:

- `LauncherOrientation` (4-7): `PORTRAIT`, `LANDSCAPE` - "which of the two independent desktop layouts a cell belongs to".
- `LauncherCellKind` (9-12): exactly two values, `SHORTCUT`, `GADGET`. No third kind exists today.
- `LauncherCell` (19-31): `id`, `orientation`, `rowIndex`, `colIndex`, `spanW`, `spanH`, `kind`, `target`, `labelOverride`, `addedAt`.

`LauncherCellCommand.kt`: sealed interface, ten prefixes (116-125): `app:`, `fn:`, `res:<id>:<MODE>`,
`stream:`, `os:`, `op:`, `act:` (S1402), `fav:`, `contact:`, `pin:`. `decode(raw)` (146-181) is a `when`
over `startsWith`, with `else -> null` (179); bad ids, wrong field counts and unknown enum names all
yield `null`, never throw. Multi-field payloads percent-encode each field (`encodeField`/`decodeField`,
244-248).

`LauncherCellUi.kt` (12-16): `cell` + `visual: LauncherCommandVisual?` + `modeBadge`. `visual` is `null`
both for a GADGET cell and for an unresolvable SHORTCUT target.

## 2. Storage

`data/local/db/LauncherCellEntity.kt`: table `launcher_cells` (17-30); `orientation`/`kind` stored as
enum-name **strings**, so a new kind needs no migration (KDoc 13-15). `LauncherCellDao` (32-88) carries
the rect-intersection query `findOverlapping` (75-87), excluding the moved cell by id.

`data/local/db/AppDatabase.kt`: `@Database` (12-43), `LauncherCellEntity::class` (31), **`version = 47`**
(39). Latest migration test `AppDatabaseMigration46To47Test.kt`.

`domain/repository/LauncherDesktopRepository.kt` (21-97): `observeCells`, `addCell`,
`addCellInFirstFreeSlot`, `removeCell`, `moveCell`, `resizeCell`, `updateCellTarget`, `seedIfEmpty`,
`clearAll`, `state`, `updateColumns`. `LauncherDesktopState` (8-13) holds seeded flags and column count
**per orientation**.

`data/repository/LauncherDesktopRepositoryImpl.kt` - overlap enforcement is `LauncherCellDao.findOverlapping`,
called inside `db.withTransaction` from every mutator:

- `addCell` (33-57): checks before `upsert`, returns `null` on collision.
- `addCellInFirstFreeSlot` -> `findFreeAnchor` (89-105): row-major scan.
- `moveCell` (144-173): equal-footprint blocker triggers an anchor swap (`swapAnchors`, 185-188); otherwise refuses.
- `resizeCell` (113-134): refuses on any blocker.
- `seedIfEmpty` (190-211): **no** overlap guard - `insertAll` directly (203).
- `normalized()` (249-254) floors row/col at 0 and spans at 1 before every write.

**Forward compatibility, decisive for this ticket:** `toDomainOrNull()` (269-289) resolves
`orientation`/`kind` by name lookup over `entries` and, on an unknown value, logs `Timber.w` and returns
`null` - the row is skipped, never a crash. KDoc (269): "A row written by a newer build (unknown
orientation/kind) is skipped, never a crash."

## 3. Renderer

Hand-written `ViewGroup`, deliberately not a `RecyclerView` (ADR-9).

`ui/launcher/grid/LauncherDesktopLayout.kt`: `columns`/`rows` are settable fields with no-op guards
(34-49) against re-entrant `requestLayout()` from a replaying `StateFlow`. `CellLayoutParams(row, col,
spanW, spanH)` (52-57) is the only layout-params type. `onMeasure`/`onLayout` (65-86) place children via
`LauncherGridGeometry.boundsOf` and size self to `rows * cellSize` (75). `cellAt(x, y)` (95-100) maps a
drop point to `(row, col)`.

`ui/launcher/grid/LauncherGridGeometry.kt`: `columns(availableWidthDp, densityFactor)` (22-26) -
`cellDp = 96f / densityFactor`, result coerced into `[3, 12]`. `cellSizePx` (29-30). `rowsFor(cells)`
(37-38) = `max(row + spanH)`, floor 1. `footprint(...)` (73-82) is the single clamp; its KDoc (61-71)
states it exists because per-call-site copies of the clamp drifted and desynced layout from hit-testing.

**The rebuild guard** - `ui/launcher/grid/LauncherCellViewBinder.kt`, fields `lastBound:
Triple<List<LauncherCellUi>, Int, Boolean>` (46) and `lastRows: Int` (48), compared in `bind(..)` at 71:

```kotlin
if (lastBound == Triple(cells, columns, editMode) && lastRows == rows) return
```

KDoc (50-58): the guard is load-bearing, not an optimization - two replaying `StateFlow`s plus rotation
land on `bind()` on every Home visit, and an unguarded rebuild tears down and recreates every gadget
view, destroying its in-flight work.

## 4. Render pipeline

`observeCells(orientation)` -> `ResolveLauncherDesktopUseCase` (36-45):
`combine(observeCells, radio WIFI, radio BLUETOOTH).flowOn(Dispatchers.IO)`; per-cell `toUi` (47-61)
decodes the command and resolves the label. `ResolveLauncherCommandLabelUseCase.invoke()` (111-135) runs
under `withContext(Dispatchers.IO)` with a compile-time-total `when`; returns `null` for anything
unresolvable (unknown route key 238, deleted rows 246/264/179, unusable contact 147, unknown action key
282, unknown OS key 293).

Degrade-to-unavailable: `LauncherCellViewBinder.bindShortcut` (242-280) - `contentAlpha` drops to
`UNAVAILABLE_ALPHA` (252) and label/icon fall back to `launcher_home_cell_unavailable` + `ic_launcher_mode`
(255-259).

`LauncherHomeViewModel.cells` (93-96): `_orientation.flatMapLatest { resolveDesktop(it) }.stateIn(..)`.
`LauncherHomeActivity.observeData()` (403-425) collects cells + edit mode into the single
`renderDesktop()` (856-869), which calls `cellBinder.bind(desktop, cells, currentColumns(), editMode,
currentViewportRows())` (862-868). KDoc (856-859) names this the one render path shared by all triggers.

## 5. Edit mode

- **Remove**: `LauncherHomeActivity.kt:141` wires `onRemoveClick = { viewModel.removeCell(it.cell.id) }`; badge drawn in `decorateForEdit` (182-226), strings `launcher_edit_remove_cell` (236) / `_named` (238). `LauncherHomeViewModel.removeCell` (338-342) delegates straight to the repository.
- **Drag/move**: `ui/launcher/helpers/LauncherEditModeManager.kt` - container-level `OnDragListener` (86-110); `ACTION_DROP` (94-100) resolves `desktop.cellAt(..)` and calls `viewModel.moveCell(..)`. Drag starts from the edit scrim's long-click (`LauncherCellViewBinder.decorateForEdit`, 199-203). KDoc (17-25): "no placement logic lives here .. the placement itself .. is the repository's". Edge auto-scroll 112-169.
- **Resize**: `ui/launcher/helpers/LauncherResizeManager.kt` - handle listener (55-65), candidate clamped between the gadget's own min span and the viewport ceiling (67-103), committed on `onUp` only when changed (110). Only gadgets get a handle (`decorateForEdit`, 220).
- **Placement validation owner**: neither UI manager; both call thin ViewModel wrappers (318-329) over the repository, where the invariants live (see 2).
- **Content picker**: `ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt`. `categoryOptions()` (121-155) is a **static hand-written `listOfNotNull(..)` of 12 rows** - 8 unconditional plus 4 contact rows gated by availability (129-152). Not an enum, not a resource array. `CATEGORY_GADGET` and `CATEGORY_ACTION` re-open the same dialog in a sub-mode (`newGadgetInstance`/`newActionInstance`, 274-282) backed by `gadgetOptions()` (170) and `actionOptions()` (179).
- **The S1402 "Launcher action" row**: `core/panel/LauncherActionCatalog.kt` - `object` with `Action(key, labelRes, iconRes)` (23-27) and a hardcoded ordered `all` of exactly four entries (30-35): `app_settings`, `launcher_settings`, `edit_desktop`, `exit_launcher_mode`; `byKey` (37). Category label `launcher_edit_kind_action` = "Launcher action" (`strings.xml:3252`).

## 6. Seeding

`core/launcher/LauncherStarterSets.kt` - pure data plus a pure packer, unit-tested (KDoc 12-18).

- `StarterItem(kind, target, spanW=1, spanH=1)` (45-50), `PlacedStarterItem` (52-58).
- `itemsFor(profile, resources, routeAvailableInBuild)` (77-89) = `clock()` + `commonResources()` + `profileItems()` (exhaustive per-profile `when`, 119-146) + `commonFeatures()` + `commonTail()`.
- **The four S1402 actions** live in `commonTail()` (186-190), appended **last** in the whole set via `LauncherActionCatalog.all.map { .. }`. KDoc (181-185) says this is deliberate: "putting four of them first would push the clock and the lists below the fold".
- `place(items, columns)` (154-166) is the row-major packer over an in-memory `occupied` set (`cellKey`, 214) - entirely independent of the DB.
- **Seed bypasses the interactive guard**: `seedIfEmpty` calls `insertAll` directly; `LauncherStarterSets` KDoc (16-18) states `place()` is "the SOLE guarantor that seeded cells never overlap".
- Entry point `SeedLauncherDesktopUseCase.invoke()` (41-81), per-orientation `seedOrientation()` (83-105).

## 7. Long press and accessibility

`ui/launcher/helpers/LauncherCellActionMenuManager.kt` renders a shared `ListPopupWindow` (65-80) fed by
injected suspend lambdas; it decides no rows itself (KDoc 19-20).

**Surface enum (S1424)**: `core/menu/MenuActionSurface.kt` (11-18) - `MAIN_WINDOW`, `LAUNCHER_DESKTOP`.
KDoc (6-9): "a further cell kind joins by answering this enum rather than by changing the long-press
handler."

**Dispatcher**: `LauncherHomeActivity.showCellActions(view, cellUi)` (500-521) - a `when` over
`LauncherCellCommand.decode(..)` routing `App` / `Resource` / `Stream` to their managers, with
`else -> false`. Every other command kind has no menu today.

**Accessibility attach point**: `LauncherCellViewBinder.nameLongPressForAccessibility(view, item)`
(290-296), called from `bindShortcut` at 278 - therefore **only for `SHORTCUT` cells**; `bindGadget`
(298-306) never calls it, and gadgets never receive `onCellLongPress` from the binder. Uses
`ViewCompat.replaceAccessibilityAction(view, ACTION_LONG_CLICK, getString(launcher_home_cell_actions))`
(291-295). The KDoc phrase "before any command kind is read" refers to the command dispatch inside
`showCellActions`, not to the SHORTCUT/GADGET split.

## 8. Existing collapsible header

`ui/common/widget/CollapsibleSectionHeader.kt` (391 LOC): a `LinearLayout` subclass (34) inflating
`R.layout.view_collapsible_section_header`; owns only the visual expanded flag (45) and emits
`expandedChangeListener` (52). KDoc (24-29): "Persistence and content-container visibility stay outside
this component."

`CollapsibleSectionsManager.kt` (17-64): `register(header, container: View, key, defaultExpanded,
onExpandedChanged)` - binds a header to an arbitrary `View` container, toggles `container.isVisible`,
animates through `TransitionManager`, persists via the store.

`CollapsibleSectionStore.kt`: `isExpanded(key, default)` / `setExpanded(key, expanded)`; default impl
`SharedPreferencesCollapsibleSectionStore` (30-43) over one namespace `collapsible_sections_state`
(`NAMESPACE`, 20), with caller-supplied `<screen>__<section>` keys.

**Reusability verdict.** The header view and the store are generic. The *manager* is not usable here: it
models a section as "one header plus one container View whose visibility toggles", and the desktop has no
per-section container - membership is positional geometry. Both production call sites
(`ui/keybinding/KeybindingListAdapter.kt` - `HeaderViewHolder` (60), `createHeaderView()` setting
`RecyclerView.LayoutParams` (137); `ui/statistics/StatisticsAdapter.kt`) consume the header as a
RecyclerView **row view type**, which is exactly the full-width-row mechanic the strategic spec (section 4)
says the canvas cannot borrow.

## 9. Strings

`app_v2/src/main/res/values/strings.xml`, launcher block from ~3088. Convention
`launcher_<subarea>_<detail>`; observed subareas `launcher_edit_*`, `launcher_home_*`, `launcher_menu_*`,
`launcher_gadget_*`, `launcher_contact_*`, `launcher_cell_*`. Neighbours of the removal string:

- `launcher_edit_enter` = "Edit desktop" (3090)
- `launcher_edit_done` = "Done" (3091)
- `launcher_edit_empty_slot` = "Empty space. Tap to put something here." (3092)
- `launcher_edit_remove_cell` = "Remove from desktop" (3093)
- `launcher_edit_add_cell_title` = "Put on the desktop" (3094)
- `launcher_edit_kind_action` = "Launcher action" (3252)

## 10. Tests

Unit (`app_v2/src/test/`):

- `domain/model/launcher/LauncherCellCommandTest.kt` - encode/decode round-trip and tolerant-decode cases.
- `domain/model/launcher/LauncherContactCommandCodecTest.kt` - `Contact` field-encoding edges.
- `core/launcher/LauncherStarterSetsTest.kt` - `itemsFor`/`place` packing and no-overlap coverage.
- `data/repository/LauncherDesktopRepositoryImplTest.kt` - placement/move/resize/overlap behaviour.

Instrumented (`app_v2/src/androidTest/`): `data/local/db/AppDatabaseMigration46To47Test.kt` plus the three
prior migration tests, covering up to `version = 47`.

**No unit coverage** for `LauncherCellViewBinder` (the rebuild guard), `LauncherGridGeometry` (column and
footprint math), `LauncherEditModeManager` or `LauncherResizeManager` - confirmed by grep across
`app_v2/src/test/`, zero matches for all four class names.
