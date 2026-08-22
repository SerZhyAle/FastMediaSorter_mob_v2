# Phase 05 - The Favourites section shows a real list

**Strategic spec:** [`../S1846_wear-phone-browse-favourites-placeholders.md`](../S1846_wear-phone-browse-favourites-placeholders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

The Favourites section opens a list of what was marked on the watch, obeys the file-list view mode, opens a row in the player, and unmarks from the row itself.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done - records can be listed and carry enough to reopen.
- [ ] Owner rulings read: strategic §12 answers 1 and 4, and §13 answer 7.
- [ ] `temp/CODE.LOCK` acquired immediately before each source edit and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/favourites/FavouritesViewModel.kt` | New | ≤ 200 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/favourites/FavouritesScreen.kt` | New | ≤ 260 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt` | Modified | ≤ 420 |
| `wear/src/main/res/values/strings.xml` and every other locale of the module | Modified | +3 keys |
| `wear/src/test/java/com/sza/fastmediasorter/wear/ui/favourites/FavouritesViewModelTest.kt` | New | ≤ 200 |

> The watch module has one layout vocabulary and no orientation variants, so CLAUDE.md Rule 11 does not apply here - but Rule 30 does, and a new key must reach every locale the module declares.

---

## Steps

### Step 05.1 - Hold the list

**Files:** `wear/../ui/favourites/FavouritesViewModel.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `FavouritesViewModel` exposing one state: loading, the records, or empty. It reads the repository's read-all and the `fileListViewMode` setting, and exposes two actions - open a record, and unmark a record through `ToggleFavoriteUseCase`. Unmarking removes the row from the state immediately rather than waiting for a reload.
>
> Collect nothing in the constructor beyond what the screen needs on first frame, and expose no mutable flow publicly.

**Why:**

Strategic §5 states the screen reads the watch's own store, and §3.3 fixes `fileListViewMode` as the view-mode source, so both belong to the state this view model publishes rather than to the composable.

**Verification:**

- `Glob` - `FavouritesViewModel.kt` exists.
- `Grep` - `class FavouritesViewModel` matches exactly once and carries `@HiltViewModel`.
- `Grep` - `fileListViewMode` is read in that file.
- `Grep` - no `public` mutable flow is exposed; every `MutableStateFlow` is private.
- `.\a.ps1 fw` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `FavouritesViewModel` created with three states plus a one-shot open request. Every `MutableStateFlow` is private and only the read-only projection is exposed. `fileListViewMode` is read from preferences, not invented - S1730 split file-list view from resource-list view on purpose and this screen is a list of files.

---

### Step 05.2 - Draw the list on the shared watch shape

**Files:** `wear/../ui/favourites/FavouritesScreen.kt` (New)
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `FavouritesScreen` on `WearScreenScaffold` + `ScalingLazyColumn` + `PositionIndicator` + `wearScreenInsets()`, and render rows with `ThumbnailCell` and `GridColumnFit.columnsFor(..)` so list and grid come from the same place as the browse screen. A legacy record with no media type shows its last path segment and the type-unknown icon rather than a broken thumbnail. Empty state says nothing has been marked yet.
>
> Rotary and D-pad must move the selection. No colour literal.

**Why:**

Research artifact 02 §4 records that this shape and the list/grid duality already exist precisely so a third file list would not need a third implementation, and strategic §3.2 requires rotary and D-pad on a navigational watch screen.

**Verification:**

- `Glob` - `FavouritesScreen.kt` exists.
- `Grep` - `WearScreenScaffold`, `ScalingLazyColumn`, `ThumbnailCell` and `GridColumnFit` are all referenced.
- `Grep` - `="#` and `Color(0x` return zero hits in that file.
- `Grep` - the empty state and the unknown-type label resolve string resources.
- `.\a.ps1 fw` exits 0 and `.\a.ps1 fwr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `FavouritesScreen` built on `WearScreenScaffold` + `ScalingLazyColumn` + `PositionIndicator` + `wearScreenInsets()`, with `ThumbnailCell` and `GridColumnFit.columnsFor(..)` so list-vs-grid is decided in the one place this app decides it. A record with no kind gets the neutral file glyph and a secondary label saying why it will not open. No colour literal; three strings added in en/ru/uk (`wear_favourites_empty`, `wear_favourites_unmark`, `wear_favourites_unopenable`), parity exit 0 with `-Module wear`. Reused the existing `loading` key rather than minting `wear_loading`.

---

### Step 05.3 - Replace the last placeholder and wire the row actions

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/MainActivity.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> In the `WearRoutes.FAVOURITES` composable, replace `NotYetHereScreen(ownerTicket = "S1846")` with `FavouritesScreen`. A row tap opens the audio, video or image destination matching the record's media type; a record that cannot be resolved says so and stays on the list. A long press, or the row's own control, unmarks.

**Why:**

Strategic goals 1 and 5 require the section to open a working list whose row opens a file, and strategic §12 answer 4 records the owner's ruling that unmarking is done on the watch itself.

**Verification:**

- `Grep` - `NotYetHereScreen` returns zero hits in `MainActivity.kt`.
- `Grep` - `FavouritesScreen` is referenced from the `WearRoutes.FAVOURITES` composable.
- `Grep` - the three player route patterns are reached from the row-tap branch.
- `.\a.ps1 fw` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - The `WearRoutes.FAVOURITES` composable now renders `FavouritesScreen`; `NotYetHereScreen` is gone from `MainActivity` for this ticket. The two that remain name S1710, which is open, so they are correct and were not touched. Row tap opens, a second chip unmarks. **A defect was caught here before it compiled** - see the note below.

---

### Step 05.4 - Pin the states with a view-model test

**Files:** `wear/src/test/.../FavouritesViewModelTest.kt` (New)
**Depends on:** Step 05.3

**Prompt for developer:**

> Cover: an empty store yields the empty state; a store with one record and one legacy entry yields both rows with the legacy one carrying no media type; unmarking drops the row from the state and reaches `ToggleFavoriteUseCase`; the view mode read from settings reaches the state.

**Why:**

Strategic criterion 4 requires that unmarking works and leaves as a delta, and that is a claim about the view model rather than the screen, so it is provable without a device - which is the only kind of proof this run can produce.

**Verification:**

- `Glob` - `FavouritesViewModelTest.kt` exists.
- `.\a.ps1 fwu` exits 0.
- `Grep` - the test names cover empty, legacy, unmark and view mode.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - `FavouritesViewModelTest`, 7 cases, `tests="7" failures="0"` read from the results XML. TWO of them failed first and both failures were real information rather than noise: (1) `fileListViewMode` returned LIST, because `stateIn(WhileSubscribed)` stays at its initial value until something collects it - the test now collects, which is what the screen does, and asserting without collecting would only have proved the default; (2) `Uri.parse` is an unmocked stub in a unit test, so the hand-off needed `mockkStatic(Uri::class)`. Neither was a product bug and neither was worked around by deleting the assertion.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [ ] `.\a.ps1 fw` exits 0, `.\a.ps1 fwr` exits 0, `.\a.ps1 fwu` exits 0.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `Grep` - `NotYetHereScreen(ownerTicket = "S1846")` returns zero hits across `wear/src`.
- [x] `check_strings_localized.ps1 -Module wear -SourceSet main -KeyPrefix "wear_favourites"` exits 0.
- [ ] `dev/CATALOG/wear.jsonl` regenerated - deferred to Phase 06, which syncs both modules once.
- [x] Phase-boundary audit run - a new screen and view model, so apply the lifecycle and Flow lenses of `docs/CODE_AUDIT_PROTOCOL.md`.

---

## Handoff Notes to Next Phase

Both placeholders naming S1846 are gone. Phase 06 records the capability and proves no placeholder anywhere names a closed ticket.

---

## Rollback Plan

Revert the phase commit; the Favourites placeholder returns. Phase 04's stored records survive a rollback of this phase alone.

---

## Caught before it compiled - the row would have opened an empty player

The first draft of this screen navigated straight to `WearRoutes.imageViewer(filePath.hashCode())`. That
compiles and reads plausibly, and it would have been a new dead end: the player routes address a file by id
and resolve it through `SelectedMediaManager` or a MediaStore row, and a favourite has neither - research
artifact 02 §3 says exactly that, "nothing resolves a stored filePath back to an openable item". The user
would have tapped a row and landed in an empty player.

Fixed by moving the decision into the view model, which hands the file to `SelectedMediaManager` FIRST and
only then asks the screen to navigate - the same order Phase 03 uses for a phone file. The id means
something to the player because the hand-off happened, not because it was minted.

The pre-record entries fall out of this honestly: no kind means no player can be chosen, so the view model
answers `Unopenable` and the screen says so, instead of guessing a player and failing further from the cause.
