# PHASE_04 - Overflow menu: items + push-model in ResourceOpsMenuManager

**Strategic spec:** `PLAN/S0374_browse-top-command-buttons-20pct.md`
**Status:** Pending
**Depends on:** PHASE_02

## Goal

Every overflow-eligible command gets a `menu_resource_ops.xml` item, shown iff the overflow manager reports that command overflowed, routed to the same callback as its toolbar button. Remove the old viewport pull-check.

## Steps

### Step 4.1 - Add menu items

In `app_v2/src/main/res/menu/menu_resource_ops.xml` add items (place them in a group at the top, above the existing resource-ops items, so overflowed primary commands read first):
- `action_overflow_sort`, `action_overflow_filter`, `action_overflow_refresh`, `action_overflow_toggle_view`, `action_overflow_select_all`, `action_overflow_deselect_all`, `action_overflow_play`, `action_overflow_play_random`, `action_overflow_mic`.
- Each `android:title` reuses the existing string the button's `contentDescription` uses (e.g. `@string/refresh`, `@string/play`, `@string/select_all`, `@string/play_random`, `@string/mic_recording_button_content_desc`, `@string/toggle_view`, `@string/filter`, `@string/action_sort` or the sort label). Reuse existing keys - add none unless a gap is found; if a key is missing, add via `scripts/utils/set-android-string.ps1 -Action add` in EN/RU/UK.
- create-folder/text/drawing items already exist - keep them.
- All `android:visible="false"` by default (the manager toggles them).

### Step 4.2 - Push-model in `ResourceOpsMenuManager.showMenu`

In `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/ResourceOpsMenuManager.kt`:
- Add a parameter `isOverflowed: (Int) -> Boolean` (default `{ false }`) and a `callbacks: BrowseButtonSetupHelper.ButtonCallbacks?` (or individual lambdas) to route overflow clicks.
- For each overflow item, set `isVisible = isOverflowed(R.id.btnXxx)` AND the command's feature predicate (mic also needs its feature flag; create-* keep their existing `canCreate*` predicates AND `isOverflowed`).
- Replace `isControlFullyVisibleInCommandViewport(anchor, R.id.btnCreateFolder)` gates with `isOverflowed(R.id.btnCreateFolder)` (and text/drawing). The visibility now reads: feature-eligible AND overflowed.
- Delete the `isControlFullyVisibleInCommandViewport` function and its `HorizontalScrollView`/`Rect`/`offsetDescendantRectToMyCoords` imports if unused.
- In `setOnMenuItemClickListener`, route each `action_overflow_*` to the matching callback (`onRefreshClicked`, `onPlayClicked`, `onPlayRandomClicked`, `onSelectAllClicked`, `onDeselectAllClicked`, `onToggleViewClicked`, `onFilterClicked`, mic → `onMicRecordSingleTap`, sort → open sort menu via existing path).

WHY-comment: push model - the overflow manager is the single owner of "is this command on the bar"; the menu only mirrors its decision, so the two never disagree (research §7 High risk).

### Step 4.3 - Pass overflow query from caller

In `app_v2/.../ui/browse/managers/BrowseManagerInitializer.kt` `showBrowseResourceOpsMenu(anchor)` → pass `isOverflowed = { id -> commandOverflowManager.isOverflowed(id) }` and the callbacks into `showMenu(...)`. (Manager field added in PHASE_05.)

**Verification:**
- `Grep` `action_overflow_` in `menu_resource_ops.xml` → expected: ≥9 | actual: record.
- `Grep` `isControlFullyVisibleInCommandViewport` across `app_v2/src/main` → expected: 0 | actual: record.
- `Grep` `isOverflowed` in `ResourceOpsMenuManager.kt` → expected: ≥4 | actual: record.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "..."` if any string key added → expected exit 0.

## Phase Done Criteria

- [ ] ≥9 `action_overflow_*` items in `menu_resource_ops.xml`, all default-hidden.
- [ ] `isControlFullyVisibleInCommandViewport` fully removed; no `topCommandScroll` ref remains in `.kt`.
- [ ] Overflow items routed to existing `ButtonCallbacks`; mic uses single-tap.
- [ ] Any new string keys parity-checked EN/RU/UK (exit 0).
