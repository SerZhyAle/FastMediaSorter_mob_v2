# Phase 07 - Edit Mode

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done 2026-07-18
**Depends on:** Phase 05B, 06
**Blocks:** Phase 08
**Steps done:** 6 / 6
**Started:** 2026-07-17
**Completed:** -

---

## Resume point (2026-07-17, part 1 of 2)

Step 07.1 is done and the ground under 07.2-07.4 is laid. `.\a.ps1 fc` green, post-change PASS, CODE.LOCK released. **Read this before touching anything - three of the steps below are now partly wrong.**

Landed:

- **The no-overlap invariant (not in the plan; supersedes 07.4's "swap").** `LauncherCellDao.getAt` was anchor-only (`WHERE rowIndex = :rowIndex AND colIndex = :colIndex`), so a 2x2 gadget's other three squares read as free and a drag would have written a cell straight on top of it. The phase-01..04 audit filed this as unreachable ("nothing writes a cell yet") - Phase 07 is what makes it reachable. Replaced by `findOverlapping(orientation, row, col, spanW, spanH, excludeId)` (rect intersection), and BOTH `addCell` and `moveCell` now check-and-write in one transaction. `addCell` returns `Long?` and `moveCell` returns `Boolean` - null/false mean "the square was taken".
  - **Occupied target: equal footprints trade places, mismatched ones are rejected** (owner decision 2026-07-17, landed in `moveCell`). This supersedes BOTH the plan's original blanket "swap the two cells" AND the blanket "always reject" this resume point briefly carried. The reject-half of the argument stands: a 2x2 gadget cannot take a 1x1's place without landing on that 1x1's neighbours, trading one overlap for another. The reject-all half did not: the "Windows does not swap desktop icons" analogy was wrong about the hardware - this is a finger/D-pad grid on a phone, tablet or TV, where Pixel Launcher, One UI and Nova all swap equal icons, and 1x1-onto-1x1 is the most common drag there is.
  - **A trade exchanges anchors, never the dropped square.** Two equal footprints swapping anchors is collision-free by construction: each lands exactly on a rectangle the other already held, and those were already free of every other cell - so no second lookup is needed. Honouring the finger's actual square instead would be a real bug: a drop inside a 2x2 is usually off-anchor, so the mover would land on a rectangle nobody vacated and could cover a third cell. Locked by `a trade lands on the anchor, not on the dropped square` in `LauncherDesktopRepositoryImplTest`.
- **Edit-mode state**: `LauncherHomeViewModel.editMode: StateFlow<Boolean>` + `setEditMode`, `addCell(row, col, kind, target, spanW, spanH)`, `moveCell(id, row, col)`, `removeCell(id)`.
- **Empty slots are geometry, never data.** The plan asked the ViewModel to emit synthetic `EmptySlot` items; after ADR-9 that would put layout state in the ViewModel for no gain. `LauncherCellViewBinder.bind(.., editMode)` derives free squares itself (any coordinate no cell's footprint covers) and shows two spare rows past the last occupied one. `item_launcher_cell_empty.xml` + `launcher_empty_slot_frame.xml` (dashed) are new; the remove badge is added programmatically as a child of the cell view so it sits above a gadget that owns its own touches.
- **Neutral factories retrofitted (Rule 13)** into `AppPickerDialogFragment`, `InternalRoutePickerDialogFragment`, `OsShortcutPickerDialogFragment` - each gained `ARG_REQUEST_KEY`, a `requestKey` field defaulting to `RESULT_KEY`, and a `newInstance(requestKey: String)` overload, exactly as `ResourcePickerDialogFragment` got in Phase 05. Existing panel call sites untouched. **All four pickers are now reusable from the launcher.**
- **13 strings** `launcher_edit_*` in EN/RU/UK.

Still to do (07.2, 07.3, 07.4, 07.5-tail, 07.6):

- `LauncherCellContentPickerDialogFragment` + `LauncherResourceModePickerDialogFragment`, wiring the four retrofitted pickers by `requestKey`.
- `LauncherStreamPickerDialogFragment` - **net-new, no forkable dialog exists.** Copy `ResourcePickerDialogFragment`'s shape (DialogFragment + `SearchableOptionPickerController.attach(binding, options, selectedId, resetRow, onPicked)` + `DialogSearchableOptionPickerBinding`), neutral factory from day one. `StreamSourceRepository` has NO sync accessor (unlike `ResourceRepository.getAllResourcesSync()`), so use `observeSources().first()`. A favicon CAN be the leading visual: `LeadingVisual.Thumbnail(model: Any)` is Glide-loadable and accepts a Bitmap.
- `LauncherEditModeManager`: drag via `View.startDragAndDrop` + `OnDragListener` on `LauncherDesktopLayout`; pins `+`/`x`; the first-rotation Snackbar.
  - **Add `cellAt(x, y)` to `LauncherDesktopLayout` rather than re-deriving the formula.** `cellSize(totalWidth)` is private and the plan's `col = (x - paddingLeft) / cellSize` would be a fourth copy of grid arithmetic - the exact drift that put a slot on top of a live cell (see the audit below). The container owns the pixel->(row, col) mapping; feed the result through `LauncherGridGeometry.footprint` for the clamp. Deliberately not added in part 1: it would have been dead code.
  - Scroll offset is a **non-issue**: `DragEvent.getX()/getY()` on a listener attached to `LauncherDesktopLayout` are already local to that view, post-scroll. What IS missing is auto-scroll near the viewport edge (a plain `OnDragListener` gives none, unlike `ItemTouchHelper`), so a single gesture cannot reach the spare rows below the fold. Iteration-1 answer: lift and re-drag; document in KDoc beside the D-pad-move exclusion.
  - `moveCell` returns `Boolean` and `LauncherHomeViewModel.moveCell` currently drops it. Snap-back already reads as "rejected" for free, so a result channel is only needed if 07.4 wants *distinct* feedback. Note `LauncherHomeViewModel.kt` is missing from step 07.4's own Files line.
- **`launcherRotationHintShown` setting.** `AppSettings.kt` is 488 lines (no backup needed). `SettingsRepositoryImpl.kt` is **880 lines -> Rule 5 backup to `temp/S0404/` is MANDATORY before editing**, and it needs three non-adjacent touch points, verified to the line: key in the companion (`:210-213`), read in the settings-flow mapper (`:555-559`), write in the save function (`:758-762`).
  - **Rule 22 does NOT bite here** (corrected 2026-07-17 - the earlier claim that it did was wrong). `assert-settings-doc-sync.ps1` builds its manifest from `LayoutSettingsSearchSource`, i.e. it scans setting *rows* in `fragment_settings_*.xml`; it never reads `AppSettings` fields. This flag is one-shot with no UI row, so it is invisible to every stage of the gate and regenerating the manifest would produce an empty diff. Do not spend the effort.
- Obligations carried in from the phase-06 audit - **both re-verified true, and neither has a mechanism yet** (P1 for 07.2, and neither file is in Files Touched):
  - `rememberFileList` (ADR-10): confirmed `MediaResource.rememberFileList = false` (`Models.kt:220`) and `GetMediaFilesUseCase.kt:240` gates its cache-skip on it. But **`LauncherHomeViewModel` has no `ResourceRepository`** and `addCell` has no channel for a resource-side write. The write belongs in the ViewModel (`ResourceRepository.updateResource`), not in a dialog: a picker that mutates data on the way out hides a write behind a UI event.
  - Audio filter: confirmed `supportedMediaTypes` defaults to `{IMAGE, VIDEO}` (`Models.kt:195`), and **`ResourcePickerDialogFragment.buildOptions()` has no filter parameter at all** (`:76-83`) - the obligation is unimplementable until one is added. Use the permissive test (`MediaType.AUDIO in supportedMediaTypes`), NOT the existing `isAudioOnly()` (`Models.kt:237`): that one demands `size == 1`, which would hide a `VIDEO_LIBRARY` resource ({VIDEO, AUDIO}) that legitimately holds tracks.
- **Gadget default spans: no gap** (corrected 2026-07-17). `LauncherGadget` already declares `defaultSpanW`/`defaultSpanH` (`:31-32`) and all four gadgets implement them (clock 2x1, the rest 2x2). Step 07.2 reads them straight off the registry entry.
- **Wiring 07.2 and 07.4 share.** `LauncherHomeActivity` builds the binder with only `onCellClick` (`:48`) and both `bind()` call sites (`:87`, `:158`) omit `editMode`, so edit mode cannot turn on today. 07.2 needs `onEmptySlotClick`; 07.4 needs `editMode` + `onRemoveClick`. **`applyGridGeometry()` is a third caller of `bind()`** - wire `editMode` through a single source, or rotating while editing silently drops back to the non-edit render.
- An unrelated gap the picker discovery surfaced, worth one line in Phase 10 rather than a ticket: `dev/PROJECT_OPERATIONS_INDEX.md` §9 Feature-to-Path Map has no entry for `ui/applaunchpanel/`, `core/panel/` or the launcher.

---

## Objective

Explicit edit mode (owner quiz 2026-07-17: entered via a button, never long-press): framed cells, tap-empty-to-fill chooser (five shortcut kinds + gadgets), drag to move, remove, taskbar pin management, D-pad assignment, first-rotation hint.

> **Re-scoped 2026-07-17 (ADR-9).** Phase 05B replaced the `RecyclerView` grid with `LauncherDesktopLayout`, a 2D `ViewGroup`. Two consequences for this phase, both simplifications: drag/drop is coordinate arithmetic instead of `ItemTouchHelper` + adapter positions, and "empty slot" no longer needs synthetic list items - it is any grid coordinate with no child. The steps below were written against the old renderer; where they disagree with `LauncherDesktopLayout`, the container wins.
>
> **ADR-10 obligation:** the add-flow for `PlaylistGadget` / `FolderPreviewGadget` must enable `rememberFileList` on the chosen resource (default is `false`, `Models.kt:220`). Without it a network-resource gadget shows "Unavailable" after every reboot. Step 07.2's resource-param chain owns this write.

---

## Prerequisites

- [x] Phases 04 and 06 are ✅ Done.
- [x] CODE.LOCK acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherEditModeManager.kt` | New | ≤ 300 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt` | New | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherStreamPickerDialogFragment.kt` | New | ≤ 120 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/picker/LauncherResourceModePickerDialogFragment.kt` | New | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified (edit decorations + empty slots) | +80 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | +60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified (wire manager) | +25 |
| `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_empty.xml` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | +1 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified (backup first) | +6 |

Added by the phase-07 audit (2026-07-17) - each was an obligation the plan named without budgeting a file for:

| File | New / Modified | Step | Why |
|------|:--------------:|:----:|-----|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/applaunchpanel/edit/ResourcePickerDialogFragment.kt` | Modified | 07.2 | It has no media-type filter at all, so "playlist gadgets offer only audio-capable resources" is otherwise unimplementable. |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherDesktopLayout.kt` | Modified | 07.4 | Owns the new `cellAt(x, y)`; `cellSize` is private today. |
| `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_remove_badge.xml` | New (landed 07.1) | 07.1 | Badge needs a themed ripple + focus stroke + an id; it was built programmatically without any. |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/local/db/LauncherCellDaoTest.kt` | New (landed 07.1) | 07.1 | The overlap invariant had no test. |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt` | New (landed 07.1) | 07.1 | Covers normalisation, rejection and the trade rule. |

> All new dialogs are selection dialogs (single-choice lists) - exempt from the S0538 confirm/cancel pair; render via the canonical `SearchableOptionPickerController` where a list is searchable (streams), else plain list dialog matching `InternalRoutePickerDialogFragment`'s style.

---

## Steps

### Step 07.1 - Edit-mode state + empty-slot rendering

**Files:** `ui/launcher/LauncherHomeViewModel.kt`, `ui/launcher/grid/LauncherCellViewBinder.kt`, `res/layout/item_launcher_cell_empty.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> ViewModel: `val editMode: StateFlow<Boolean>` + `fun setEditMode(on: Boolean)`; in edit mode `observeDesktop` emits the full grid matrix - occupied `LauncherCellUi` items PLUS `EmptySlot(rowIndex, colIndex)` items for every free cell in rows `0..(maxOccupiedRow + 2)` (desktop grows downward - one screen + scroll). Adapter: third view type for empty slots (`item_launcher_cell_empty.xml`: dashed/stroked frame + centered `+` icon, focusable); in edit mode occupied cells draw a frame overlay + a 20dp remove badge `@+id/cellRemoveBadge` (top-end, own click target). Callbacks: `onEmptySlotClick(row, col)`, `onRemoveClick(cellUi)`.

**Verification:**

> Predicate rewritten 2026-07-17: as first written it grepped for `EmptySlot` in the ViewModel and `cellRemoveBadge` in the adapter, and after ADR-9 **neither can ever exist** - empty slots became geometry rather than list items, and there is no adapter. A predicate that cannot pass is worse than none: it reports "not done" on finished work, which teaches the next reader to tick without running it.

- `Grep` - `editMode` present in `LauncherHomeViewModel.kt`. **PASS** (`:109`, `:124`).
- `Grep` - `addEmptySlots` and `onEmptySlotClick` present in `LauncherCellViewBinder.kt`. **PASS** (`:79`, `:32`).
- `Grep` - `cellRemoveBadge` present in `res/layout/item_launcher_cell_remove_badge.xml`. **PASS**.
- `.\a.ps1 fc` → BUILD SUCCESSFUL. **PASS**.
- `:app_v2:testStandardDebugUnitTest --tests "*LauncherCellDaoTest" --tests "*LauncherDesktopRepositoryImplTest" --tests "*LauncherGridGeometryTest"` → 34 tests, 0 failures. **PASS**.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 5/5 PASS. Files: `LauncherHomeViewModel.kt`, `LauncherCellViewBinder.kt`, `LauncherGridGeometry.kt`, `LauncherDesktopLayout.kt`, `LauncherDesktopRepositoryImpl.kt`, `LauncherCellEntity.kt`, + 3 new resources, + 2 new test classes. Predicate rewritten to the ADR-9 shape before it was run. Post-audit fixes folded in (see Phase audit below).

---

### Step 07.2 - Content chooser (type → delegate pickers)

**Files:** `ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt`, `LauncherResourceModePickerDialogFragment.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> `LauncherCellContentPickerDialogFragment(row, col)` - list of six entries: External app / Our feature / Resource / Stream / Android setting / Gadget (icons: PM default, `ic_launcher_mode`, `ResourceTypeIconMap` generic, `ic_cast`, settings gear, per-gadget icons). Selection routes to existing FragmentResult pickers - reuse `AppPickerDialogFragment`, `InternalRoutePickerDialogFragment`, `ResourcePickerDialogFragment`, `OsShortcutPickerDialogFragment` from `ui/applaunchpanel/edit/` (adapt via their existing result keys; if a picker hard-codes panel slot arguments, add a neutral secondary factory to THAT file rather than forking the dialog - script-ownership Rule 13 spirit). Resource flow chains `LauncherResourceModePickerDialogFragment` - three options with the Phase 04 badges: Browse / Slideshow / Play (reader-and-audio) → produces `Resource(id, mode)`. Gadget entry lists `LauncherGadgetRegistry.all()`; entries with `requiresResourceParam` chain the resource picker and encode via `encodeTarget(key, resourceId)`. Terminal result = one `FragmentResult` to the activity carrying either an encoded SHORTCUT command or a GADGET target + default spans; the host inserts via a new `viewModel.addCell(orientation, row, col, kind, target, spanW, spanH)` that delegates to `LauncherDesktopRepository.addCell`.

> **Two obligations with no mechanism yet (phase-07 audit, both P1 - do not treat as done until the code exists):**
> - **`rememberFileList` (ADR-10).** Adding a resource-backed gadget must set `rememberFileList = true` on the chosen resource via `ResourceRepository.updateResource`, or the gadget reads "Unavailable" after every reboot on a network resource. `LauncherHomeViewModel` has no `ResourceRepository` today - inject it and do the write there. Do NOT have the picker dialog write it on its way out: a data mutation hidden behind a UI event is invisible to every reader of the add-flow.
> - **Audio filter.** `ResourcePickerDialogFragment.buildOptions()` lists every resource unconditionally; pointed at a default-profile resource (`supportedMediaTypes` = {IMAGE, VIDEO}) the playlist gadget would offer photos as tracks. Add an optional media-type filter to that dialog (Rule 13: fix the shared dialog, do not fork it) and use the permissive test `MediaType.AUDIO in supportedMediaTypes` - the existing `isAudioOnly()` demands `size == 1` and would hide a `VIDEO_LIBRARY` resource that legitimately holds tracks.
> - Gadget spans come straight off the registry entry (`gadget.defaultSpanW/defaultSpanH`) - they already exist.
> - The chooser needs `onEmptySlotClick` wired in `LauncherHomeActivity` (`:48`), which is currently a no-op default.

**Verification:**

- `Grep` - `class LauncherCellContentPickerDialogFragment` and `class LauncherResourceModePickerDialogFragment` match once each.
- `Grep` - `addCell` present in `LauncherHomeViewModel.kt`.
- `Grep` - `rememberFileList` present in `LauncherHomeViewModel.kt` (ADR-10 write actually exists, not just planned).
- `Grep` - a media-type filter parameter present in `ResourcePickerDialogFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 5/5 PASS. New: `ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt` (category + gadget list, one class/two modes, FragmentResult-based - survives config change), `ui/launcher/picker/LauncherResourceModePickerDialogFragment.kt` (Browse/Slideshow/Play, carries resource id, returns id+mode). Modified: `LauncherHomeViewModel.kt` (injected `ResourceRepository`; `addCell` gained `rememberFileListResourceId` and writes `rememberFileList=true` in the ViewModel per ADR-10, not in the picker), `LauncherHomeActivity.kt` (add-flow orchestration by static request keys, mirroring `EditAppLaunchPanelActivity`; `onEmptySlotClick` wired), `ui/applaunchpanel/edit/ResourcePickerDialogFragment.kt` (optional `mediaTypeFilter`, permissive `AUDIO in supportedMediaTypes` - Rule 13, shared dialog not forked). Strings: +`launcher_edit_mode_browse/slideshow/play` + `launcher_edit_pick_mode_title` (EN/RU/UK; reused existing `ic_open_in_browse`/`ic_slideshow`/`ic_play`). `.\a.ps1 fk` BUILD SUCCESSFUL; fast gates PASS; detekt scoped PASS (my 5 files absent from the report; 153 untouched-debt files project-wide are the accepted always-dirty state the scoped gate S0826/S1077 exists for). Dev log + catalog roles recorded.
- **Deliberate deviation:** the chooser ships **5** categories (App / Feature / Resource / Android setting / Gadget). The sixth, **Channel** (`launcher_edit_kind_stream`), is added in **07.3** together with its picker, so no chooser row is a dead end in the meantime. 07.3 therefore also edits `LauncherCellContentPickerDialogFragment` (one row) and `LauncherHomeActivity` (STREAM branch).
- **Gate note (not a defect):** the detekt task went UP-TO-DATE after the wrapping fix and served a stale report (warm-daemon staleness); forced a real re-run with `--rerun-tasks` before trusting the PASS. Deleting `detekt.xml` alone did not force re-run under the configuration cache.

**Wiring is inert until 07.4:** `onEmptySlotClick` only fires on an empty slot, and empty slots render only in edit mode, which 07.4 turns on. 07.2 builds and compiles the whole add-flow; 07.4 flips the switch.

---

### Step 07.3 - Stream picker

**Files:** `ui/launcher/picker/LauncherStreamPickerDialogFragment.kt`, `ui/launcher/picker/LauncherCellContentPickerDialogFragment.kt`, `LauncherHomeActivity.kt`
**Depends on:** Step 07.2

**Prompt for developer:**

> Mirror `ResourcePickerDialogFragment` structurally (DialogFragment + `SearchableOptionPickerController` + `DialogSearchableOptionPickerBinding`), backed by `StreamSourceRepository` (pinned first, then title order). Returns the chosen `streamId: String` via FragmentResult const `RESULT_STREAM_ID`. Favicon as leading visual when available, else `ic_cast`.
> Then finish wiring the sixth category (07.2 shipped only five so no chooser row was a dead end): add the **Channel** row to `LauncherCellContentPickerDialogFragment.categoryOptions()` (`launcher_edit_kind_stream`, `ic_cast`, a new `CATEGORY_STREAM` constant) and a `CATEGORY_STREAM` branch to `LauncherHomeActivity.registerAddFlowListeners()` that opens this picker (`REQ_STREAM`) and, on result, `addShortcut(LauncherCellCommand.Stream(streamId))`.

**Verification:**

- `Grep` - `class LauncherStreamPickerDialogFragment` matches once; `RESULT_STREAM_ID` declared.
- `Grep` - `CATEGORY_STREAM` present in both `LauncherCellContentPickerDialogFragment.kt` and `LauncherHomeActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification PASS. New: `ui/launcher/picker/LauncherStreamPickerDialogFragment.kt` (mirrors `ResourcePickerDialogFragment`; snapshots the catalog with `ObserveStreamSourcesUseCase().first()` - **domain use case, not the data-layer `StreamSourceRepository` directly**, so the dialog stays off the data layer; favicon from the packed atlas as `LeadingVisual.Thumbnail(Bitmap)`, `ic_cast` fallback; returns `streamId` via `RESULT_STREAM_ID`). Wired the sixth **Channel** category into `LauncherCellContentPickerDialogFragment` (`CATEGORY_STREAM`, `ic_cast`) and a `CATEGORY_STREAM` branch + a `RESULT_KEY` listener into `LauncherHomeActivity` (`addShortcut(LauncherCellCommand.Stream(id))`). The chooser now offers all six kinds. `.\a.ps1 fk` BUILD SUCCESSFUL; fast gates PASS; detekt scoped PASS (3 files absent from the report). Dev log + catalog role recorded.
- Net-new, launcher-only picker uses a fixed `RESULT_KEY` (not the shared-picker `requestKey` scheme) - no other host lists streams, so there is nothing to disambiguate. Self-audit: the `FaviconAtlasSlicer` member captures `this.faviconAtlasStore` lazily and is only invoked in `buildOptions` (well after Hilt injection), so the `lateinit` field is set by then - no NPE. Still inert until 07.4 turns edit mode on.

---

### Step 07.4 - Move / remove / pins / D-pad

**Files:** `ui/launcher/helpers/LauncherEditModeManager.kt`, `LauncherHomeActivity.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> `LauncherEditModeManager` (activity-scoped helper, keeps the activity thin):
> - Entry points: a small persistent edit button on the desktop visible ONLY in edit mode plus the Start-menu row "Edit desktop" (add the row to `LauncherStartMenuFragment` here); exit via a `Done` chip pinned top-end during edit mode.
> - Drag: **NOT `ItemTouchHelper`** - that is RecyclerView-only, and Phase 05B replaced the grid with `LauncherDesktopLayout` (ADR-9). Use `View.startDragAndDrop` (or a plain touch-slop drag) on a cell view while edit mode is on, with an `OnDragListener` on `LauncherDesktopLayout`. This is what the 2D container was for: the drop target is arithmetic, not adapter bookkeeping. **Add a `cellAt(x, y)` accessor to `LauncherDesktopLayout`** and call that - do NOT re-type `col = (x - paddingLeft) / cellSize` at the listener, because `cellSize(totalWidth)` is private and a fourth private copy of grid arithmetic is precisely what the phase-07 audit caught drifting. Feed its result through `LauncherGridGeometry.footprint` for the clamp; an unclamped drop persists an out-of-range index that only the renderer quietly fixes, so the DB and the screen disagree until the next edit. Then call `viewModel.moveCell(id, row, col)`.
> - **Occupied target (owner decision 2026-07-17): equal footprints trade places, mismatched ones are rejected. Already implemented in `LauncherDesktopRepositoryImpl.moveCell` and covered by tests - 07.4 only supplies the drop coordinate.** Do not re-open it: the trade exchanges anchors (not the dropped square, which is usually off-anchor inside a multi-square cell), and that is what keeps it collision-free without a second lookup.
> - Remove: badge click → `viewModel.removeCell(id)`; gadget cells identical.
> - Pins: in edit mode the taskbar pinned strip shows a trailing `+` (opens `AppPickerDialogFragment` → `LauncherPinsRepository.setPin(nextFreePosition, App(pkg))`) and an X badge per pin (`removePin`).
> - D-pad (strategic §3.3): empty slots and cells are focusable in edit mode; OK on empty slot fires `onEmptySlotClick` (same chooser). Moving cells via D-pad is NOT iteration-1 - document in KDoc.
> - First-rotation hint (risk 6): on first orientation change while the desktop has ≥1 user cell, show a one-shot Snackbar `R.string.launcher_edit_rotation_hint`; persist `launcherRotationHintShown: Boolean = false` in `AppSettings` + `SettingsRepositoryImpl` (backup the impl before editing).

**Verification:**

- `Grep` - `class LauncherEditModeManager` matches once; `OnDragListener` present; **no `import .*ItemTouchHelper` and no `ItemTouchHelper(` usage** (it cannot work without a RecyclerView). The KDoc names it once to document the anti-pattern - that mention is intentional, so grep for import/instantiation, not the bare word.
- `Grep` - `launcherRotationHintShown` present in `AppSettings.kt` and `SettingsRepositoryImpl.kt`.
- `Grep` - taskbar pins edit controls (`+`/`x`) exist. **PASS** (follow-up): `LauncherTaskbarManager` `onAddPin`/`onRemovePin`/`setEditMode`; `LauncherHomeViewModel.addPin`/`removePin`; `LauncherTaskbarIconAdapter` `LauncherTaskbarRow.Add` + `taskbarUnpinBadge`; `item_launcher_taskbar_add.xml`; `LauncherHomeActivity` `REQ_PIN_APP` + `openPinAppPicker`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 (core) - Landed the desktop edit-mode core; **taskbar pins `+`/`x` deferred** to a follow-up (a distinct subsystem: the pinned strip is a `RecyclerView` adapter needing an edit variant + ViewModel pin writes + an AppPicker-for-pins flow - too large to fold in without making this change unverifiable). What landed: `LauncherEditModeManager` (drag-move via `startDragAndDrop` + an `OnDragListener` on the container; **not** `ItemTouchHelper`; Done chip; the one-shot rotation hint); `LauncherDesktopLayout.cellAt(x, y)` (pixel->grid through the private `cellSize` + `footprint` clamp - the audit's "no fourth copy of grid arithmetic"); binder gained `onCellDragStart` (long-press) and its `onRemoveClick`/drag are now wired in the activity; `LauncherHomeViewModel.onCellTapped` is edit-mode-guarded (a tap arranges, never launches) and gained `rotationHintShown`/`markRotationHintShown`; `launcherRotationHintShown` added to `AppSettings` + `SettingsRepositoryImpl` (backup at `temp/S0404/SettingsRepositoryImpl_20260717_212258.kt.bak`); Start-menu "Edit desktop" row; a Done button in **both** home layouts (Rule 11); `launcher_edit_rotation_hint` in EN/RU/UK.
- **UI ambiguity resolved (Rule 10).** The prompt's "a small persistent edit button on the desktop visible ONLY in edit mode" contradicts itself as an entry point. Resolved by the owner-quiz spirit ("entered via a button"): entry is the Start-menu "Edit desktop" row; exit is the Done chip (visible only while editing); no separate always-on desktop button, keeping Home clean.
- **Render consolidation.** The three render triggers (cells, edit-mode toggle, rotation/density re-derive) now funnel through one `renderDesktop()` that passes `editMode`, so no path drops edit mode - the "third `bind()` caller" trap the resume flagged.
- `.\a.ps1 fk` BUILD SUCCESSFUL; fast gates PASS; **settings-doc-sync PASS** (confirms Rule 22 does not apply - the flag has no UI row). Detekt: all seven of my launcher/settings-field files are clean. One finding was genuinely mine - `onCellTapped` reached 3 returns after the edit-mode guard (`ReturnCount`), refactored to a `when` (1 return). Two pre-existing findings in touched launcher files were fixed under Rule 7 (`LauncherCellViewBinder`'s magic `4` -> `MAX_FOOTPRINT_SQUARES`; `LauncherDesktopLayout.forEachCell`'s double-`continue` -> one guard). `SettingsRepositoryImpl` still carries **4 pre-existing findings** (import-ordering + `SpacingBetweenDeclarationsWithComments` from S0781/S0807/S0808) at lines 3/165/167/169 - none in this ticket's 3-line diff (214/560/763); left as-is under the S0826 dirty-tree policy rather than reordering a shared core file's imports for a launcher feature.

- 2026-07-17 (follow-up: taskbar pins `+`/`x`) - **07.4 now complete.** `LauncherTaskbarIconAdapter` gained an edit variant: a sealed `LauncherTaskbarRow` (`Icon(icon, editing)` / `Add`) with two view types, so edit mode adds an unpin "X" (`ic_clear`, its own click target) to every pin and a trailing "+" (`item_launcher_taskbar_add.xml`). Only the pinned adapter turns editing on (`setEditMode`); recents and the Start-menu all-apps list never do. `LauncherTaskbarManager` gained `onAddPin`/`onRemovePin` + `setEditMode`; `LauncherHomeViewModel` stores `pinsRepository` and gained `addPin(command)` (fills the lowest free position - a gap an unpin left is reused, `observeAll` is `ORDER BY position ASC` so the strip stays stable) and `removePin(position)`; `pinnedIcons` now carries the pin `position` (the id is the command, not the slot). `LauncherHomeActivity` wires `REQ_PIN_APP` -> `AppPickerDialogFragment` (its own key, shared picker) -> `addPin`, `onRemovePin` -> `removePin`, and toggles `taskbarManager.setEditMode` alongside the desktop render. Strings `launcher_edit_pin_add` + `launcher_edit_unpin_named` (EN/RU/UK). `.\a.ps1 fk` BUILD SUCCESSFUL; fast gates PASS; scoped detekt PASS (151 project-wide finding-files, none among my 5); `check_strings_localized.ps1 -KeyPrefix launcher_edit_` 21/21 OK.
- **Files-Touched deviation (documented):** the taskbar files (`LauncherTaskbarIconAdapter.kt`, `LauncherTaskbarManager.kt`, `item_launcher_taskbar_icon.xml`, new `item_launcher_taskbar_add.xml`) and `LauncherStartMenuFragment.kt` were not in 07.4's budgeted table, which named only the manager + activity. They are the unavoidable surface of the prompt's own "Pins" bullet (the pinned strip is a `RecyclerView` adapter) - the resume point already flagged the table as under-budgeted ("`LauncherHomeViewModel.kt` is missing from step 07.4's own Files line"). Same launcherEnabled UI subsystem, no flavor leak, no read-only zone.
- **Self-audit (Flow/adapter change trigger).** Listener symmetry: lifecycle-scoped collectors + FragmentResult listener bound to the activity, no VH leak (edit-off resets the body click). Room main-safety: `addPin` reads `observePins().first()` off the main thread and the impl writes on `Dispatchers.IO`. Stability: `ORDER BY position ASC` + lowest-free-fill, so pins do not reshuffle. No P0/P1/P2. Deferred to the device pass (same rung as the desktop badge): whether the 24dp unpin badge is a fat-finger target, and whether a user who hid the pinned block (default `showPinned = true`) needs an alternate add path - out of scope for iteration 1.

---

### Step 07.5 - Strings (EN/RU/UK)

**Files:** trilingual `strings.xml` via tool
**Depends on:** Steps 07.1-07.4

**Prompt for developer:**

> Via `set-android-string.ps1 -Action add`: `launcher_edit_enter` ("Edit desktop"), `launcher_edit_done`, `launcher_edit_add_cell_title` (chooser title), `launcher_edit_kind_app`, `launcher_edit_kind_feature`, `launcher_edit_kind_resource`, `launcher_edit_kind_stream`, `launcher_edit_kind_os`, `launcher_edit_kind_gadget`, `launcher_edit_mode_browse`, `launcher_edit_mode_slideshow`, `launcher_edit_mode_play`, `launcher_edit_rotation_hint` (explain portrait and landscape desktops are arranged separately - outcome-first, no jargon), `launcher_edit_pick_stream_title`. `check_strings_localized.ps1 -KeyPrefix "launcher_edit_"` → exit 0; COMMUNICATION_POLICY §6 PASS.

**Verification:**

- `check_strings_localized.ps1 -KeyPrefix "launcher_edit_"` → exit 0. **PASS** - 21/21 keys present in EN/RU/UK.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification PASS (`check_strings_localized.ps1 -KeyPrefix launcher_edit_` -> exit 0, 21/21 EN/RU/UK). Every string this step enumerates was already added at the point of use across 07.2-07.4 (each via `set-android-string.ps1 -Action add`, which is parity-enforced), so this step is a confirmation pass, not a fresh write - re-running `-Action add` would fail on the existing keys by design. The 21 present keys are a superset of the 14 the prompt lists: it also covers `launcher_edit_empty_slot`, `launcher_edit_pick_gadget_title`, `launcher_edit_pick_mode_title`, `launcher_edit_remove_cell`, `launcher_edit_remove_cell_named`, `launcher_edit_pin_add`, `launcher_edit_unpin_named` (the last two from the 07.4 pins follow-up). COMMUNICATION_POLICY §6: PASS - outcome-first, no jargon, `..`/hyphen/Ё conventions held; the rotation hint explains portrait and landscape desktops are arranged separately without naming orientation internals.

**Status note:** the prompt's `launcher_edit_pick_stream_title` key is the one shipped for the stream picker title (07.3 used `launcher_edit_pick_stream_title`); `launcher_edit_add_cell_title` is the chooser title. Both present.

---

### Step 07.6 - Build + device walkthrough

**Files:** - (validation only)
**Depends on:** Steps 07.1-07.5

**Prompt for developer:**

> `.\a.ps1 d` + install. Walkthrough: enter edit mode → add one of each kind (app, feature, resource+mode, stream, OS setting, gadget with param) → drag one cell → remove one → pin an app to the taskbar → Done → relaunch Home (config survives restart - strategic §11.4) → rotate (hint appears once). Record `expected | actual` for each.

**Verification:**

- `.\a.ps1 d` → BUILD SUCCESSFUL; walkthrough recorded.

**Status:** `[x]` done (device walkthrough confirmed by owner 2026-07-18 as part of the S0404 BlockNeedUserTest pass - Wave 0 verified)

**Step Log:**

- 2026-07-17 - Build half PASS: `.\a.ps1 d` -> **BUILD SUCCESSFUL in 58s**, APK `v2.60.7122.153-DEBUG` assembled (proves the new `item_launcher_taskbar_add.xml` + modified icon layout + all `launcher_edit_*` strings link and package, not just compile). Device half NOT run: `device-ready.ps1` -> `ready:false, exitCode:2, "no online device"` - no emulator or phone attached, so the manual `expected | actual` walkthrough is deferred (Device-test gate: do not fabricate a device pass).
- **Enable dependency for whoever runs the device pass:** the Home activity ships **disabled** (ADR-2); the in-app enable path (System-launcher settings group + welcome toggle) is **Phase 08**, which is blocked on 07. Until Phase 08 lands, enable it by hand on the device before the walkthrough: `adb shell pm enable com.sza.fastmediasorter.debug/…​.ui.launcher.LauncherHomeActivity` (alias, resolve the exact component from the merged manifest) then assign the Home role, or run the walkthrough as part of the Phase 08 device pass once activation exists. Scenarios to cover verbatim: enter edit mode -> add one of each kind (app, feature, resource+mode, stream, OS, gadget+param) -> drag a cell -> remove a cell -> pin an app via the taskbar "+" -> unpin via the "X" -> Done -> relaunch Home (config survives, strategic §11.4) -> rotate (hint appears once).

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Cells survive process kill + relaunch (`adb.ps1 stop` then Home).
- [ ] `Grep` - `TODO(phase-07)` zero hits.
- [ ] Dev log + `catalog_sync.ps1`; CODE.LOCK released.

---

## Phase audit (2026-07-17)

Four dimension auditors over part 1 (data layer, UI/lifecycle/a11y, shared-code retrofit + spec compliance, forward-fit into part 2). **No P0, no P1 in what landed.** The headline invariant was confirmed rather than assumed: `findOverlapping` was traced case by case (identical rect, one-axis overlap, 2x2 vs 1x1 inside it, adjacent-not-touching, self-exclusion, origin), orientation scoping proved airtight (`moveCell` reads the orientation from the DB row, not from ViewModel state), and check-and-write proved genuinely atomic. The retrofit does not break the shipped panel editor, and the phase-05 format-string P0 has not regressed (repo-wide sweep: 0).

Fixed here:

- **P2 - the same clamp existed in three files and one copy was wrong.** `addEmptySlots` floored the column but not the row, while both renderers floor both. A negative `rowIndex` would therefore draw the cell at row 0 while marking a *negative* row occupied - and since slots are added after cells, a "tap to add" frame would land on top of a live cell and hide it. Fixed at the cause, not the symptom: `LauncherGridGeometry.footprint` is now the single definition and all three call sites go through it. It also absorbed the `coerceIn(1, columns)` that would have thrown had `columns` ever been 0.
- **P3 -> the invariant had a hole that step 07.2 was about to make reachable.** A zero or negative span makes the stored rectangle empty, and an empty rectangle intersects nothing - so `findOverlapping` would report that square free forever while the renderer still drew the cell (it floors spans at 1). The DB would swear there was no overlap while the screen showed one. Every span is hardcoded 1 or 2 today, but 07.2 is where literal spans first reach `addCell`. Normalised on write instead of clamped at each reader.
- **P2 - the remove badge had no press feedback** (`focus_button_background` as `background`; that drawable is transparent at rest and its own KDoc says it belongs in `foreground`). Same misuse the phase-06 audit fixed project-wide one phase earlier; the sibling file from this same step got it right. Rebuilt as `item_launcher_cell_remove_badge.xml`: ripple in `background`, focus stroke in `foreground` - and it now carries the `cellRemoveBadge` id the step originally asked for.
- **P2 - every remove badge announced the same words.** Twelve cells, twelve identical "Remove from desktop" - a screen-reader user could hear what the button does but not which cell it does it to, while the shortcut beside it already builds a per-cell description. Now names the cell (`launcher_edit_remove_cell_named`), falling back to the bare verb.
- **P2 - a resource was called a folder.** `launcher_edit_kind_resource` shipped as "Folder or playlist" in all three locales, which `docs/COMMUNICATION_POLICY.md:149` forbids by name. Now "My resource", matching the launcher's own "My resources".
- **P2 - the invariant had no test.** `InMemoryRoomHelper` was already there and used by 22 other repository tests, and this ticket had already set the precedent by unit-testing the render geometry in 05B. The plan's test exemption is scoped to launcher *UI*; a rectangle-intersection SQL query is not UI. Added `LauncherCellDaoTest` (12) + `LauncherDesktopRepositoryImplTest` (11), covering the rect cases, orientation scoping, span normalisation and the new trade rule. 34/34 green including the 11 pre-existing geometry tests, which is also what proves the `footprint` refactor above changed no behaviour.
- **P3 x4 - plan honesty.** INDEX said `Not started 0/6` while the phase header said `In Progress 1/6`; step 07.1's own status still read "not done"; the prerequisites were unticked. Worst of the four: **step 07.1's verification predicate could not pass** - it grepped for `EmptySlot` and `cellRemoveBadge`, which ADR-9 had made impossible. A predicate that always fails trains the reader to tick without running it, which is exactly how the phase-05 P0 shipped. Rewritten to the ADR-9 shape and then actually run.

Corrected in this file rather than fixed in code (both were my own claims, both wrong):

- **Rule 22 does not apply to `launcherRotationHintShown`.** The gate scans settings *rows* in layout XML, not `AppSettings` fields, so a one-shot flag with no UI row is invisible to it and the regen would be an empty diff.
- **Gadget default spans already exist** (`LauncherGadget:31-32`, implemented by all four). The recorded "gap" was not real.

Refutations recorded so they are not re-litigated: the `if (view !is FrameLayout) return` guard cannot silently swallow the badge - `MaterialCardView` extends `CardView` extends `FrameLayout`, verified in the resolved dependency's bytecode, not by inference. The `lastBound` guard's key is complete: `LauncherCommandVisual` is deliberately not a data class and defines equality over `iconKey`, precisely so a fresh `Drawable` per PackageManager call cannot make every rebind look changed. `item_launcher_cell_empty.xml` needs no `layout-land` counterpart - the cell is square in both orientations, matching its two siblings. S1081 does not touch the badge: it is an `ImageView`, not a card.

Deferred to the device pass (no device could confirm them from code): whether the 24dp badge is a fat-finger target on a dense grid, and whether the badge's ripple actually reads on top of a gadget.

---

## Handoff Notes to Next Phase

- Desktop is now fully user-assemblable; Phase 08 seeds starter content and exposes activation UI.
- Chooser + pickers are reusable from any host if Phase 08 wants an onboarding shortcut.

---

## Rollback Plan

Revert phase commit(s); stored cells remain readable (render-only regression).
