# Phase 03 - Standalone window parity

**Strategic spec:** [`../S1364_image-player-rotation-edit-submenu.md`](../S1364_image-player-rotation-edit-submenu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 4 done, 1 deferred (03.3 undo - blocked on S1326)
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Give the separate player window the same editing section and the four commands it was missing, so the two menus stop diverging.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the three new strings exist.
- [ ] Phase 02 is ✅ Done - the editing membership is settled in one place.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | Modified | ≤ 6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified | ≤ 6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified | ≤ 6 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandalonePlayerActivity.kt` | Modified | ≤ 6 |

> Several of these are over 500 LOC - back each one up to `temp/S1364/` before editing (CLAUDE.md Rule 5). No `res/layout*` file is touched, so Rule 11 does not apply.
>
> Five hosts inflate this one menu. Every id added to the XML must be hidden explicitly by the four hosts that do not implement it, or it appears on a screen with no handler.

---

## Steps

### Step 03.1 - Regroup the standalone menu and add the missing items

**Files:** `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Wrap the editing items in a nested `<item android:title="@string/menu_edit_submenu_title"><menu> .. </menu></item>` section: `menu_edit_image`, `menu_edit_crop_to_file`, `menu_edit_compress`, `menu_draw_overlay`, `menu_rotate_content_standalone`. Add inside the same section a new `menu_edit_crop_standalone` titled `@string/menu_crop` and a new `menu_rotate_content_ccw_standalone` titled `@string/rotate_content_ccw_title`.
>
> Outside the section add `menu_rename_standalone` titled `@string/rename`, `menu_undo_standalone` titled `@string/undo`, and `menu_autorotate_standalone` titled `@string/menu_autorotate_screen_title` with `android:checkable="true"`.
>
> Keep the existing `_standalone` id-naming convention this file already uses for `menu_rotate_content_standalone`; do not reuse the embedded player's ids, which are a different set by design. Leave every non-editing item where it is.

**Why:**

Strategic §6 item 3 records the owner's ruling that the separate window is brought to full parity rather than merely regrouped, and §5 states the section is expressed as a `<menu>` tag here because, unlike the embedded player's file, this one really is inflated.

**Verification:**

- `Grep` - `menu_edit_submenu_title` matches once in the file.
- `Grep` - each of `menu_edit_crop_standalone`, `menu_rotate_content_ccw_standalone`, `menu_rename_standalone`, `menu_undo_standalone`, `menu_autorotate_standalone` matches once.
- `Grep` - `android:checkable="true"` matches on the autorotate item.
- `Grep` - `menu_rename_standalone` and `menu_undo_standalone` are NOT inside the `<menu>` block - strategic §11 criterion 4.
- `.\a.ps1 fr` - exit 0.

**Status:** `[x]` done

---

### Step 03.2 - Wire the three commands that already have handlers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> This host already implements all three behind inline command-bar buttons; the menu items only need to reach the same code. Add to its overflow `when (item.itemId)`:
>
> - `R.id.menu_edit_crop_standalone` -> the same `cropDelegate.enterCropMode(CropMode.CROP)` call the `btnEditCrop` listener makes.
> - `R.id.menu_rename_standalone` -> the same `fileOperations.showStandaloneRenameDialog()` the `btnRenameCmd` listener makes.
> - `R.id.menu_autorotate_standalone` -> the same `viewModel.toggleRotationSensor()` the `btnEditRotate` listener makes.
> - `R.id.menu_rotate_content_ccw_standalone` -> `viewModel.rotateSessionCounter90()` followed by the same rotation-applying call the existing `menu_rotate_content_standalone` branch makes.
>
> Mirror the existing per-item visibility rules for each: crop is gated on the same condition as `btnEditCrop`, rename on the same async rename-support probe as `btnRenameCmd`, autorotate on `hasAccelerometer` like `btnEditRotate`. Set `menu_autorotate_standalone`'s `isChecked` from the host's `rotationSensorEnabled` state when the popup is built.
>
> Hide `menu_undo_standalone` here for now - Step 03.3 owns it.

**Why:**

Strategic §11 criterion 6 requires the added commands to work in the separate window, and the planning research established that these three already have working handlers on this host and were missing only a menu entry, which is why they are one step and undo is another.

**Verification:**

- `Grep` - each of the four new `R.id.menu_*_standalone` ids appears in this host's `when`.
- `Grep` - `rotateSessionCounter90` matches in this host.
- `Grep` - `isChecked` is set for `menu_autorotate_standalone`.
- `Grep` - `Log\.d\(` returns zero hits in the file.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

### Step 03.3 - Wire undo, or record precisely why it defers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Undo has no wiring on any standalone host - unlike the other three, this is new behaviour, not a new entry point. Find how the embedded player's `onUndoClicked()` reaches the restore-deleted-file path and what it depends on (view model, repository, the last-operation state it reads). If the standalone host can reach the same path with its existing dependencies, wire `R.id.menu_undo_standalone` to it and gate its visibility on the same last-operation condition the embedded player uses.
>
> If it cannot - the dependency is not available to this host, or the last-operation state is not tracked there - do **not** invent a second undo. Leave `menu_undo_standalone` hidden, mark this step `[DEFERRED]`, and write into the Step Log exactly which dependency is missing. Strategic §10 already assigns undo's semantics to S1326; a missing wiring path is a finding for that ticket, not a design decision to take here.

**Why:**

Strategic §11 criterion 6 names undo among the commands that must work in the separate window, while §2 non-goals and §10 place undo's semantics in S1326 - so this step delivers the wiring if the capability is reachable and reports the exact obstacle if it is not, rather than guessing at a parallel implementation.

**Verification:**

- Either: `Grep` - `menu_undo_standalone` resolves to a handler in the host's `when`, and its visibility is gated on the same last-operation condition as the embedded player.
- Or: the Step Log records `[DEFERRED]` plus the named missing dependency, and `menu_undo_standalone` is explicitly hidden on every host.
- `.\a.ps1 fk` - exit 0 either way.

**Status:** `[DEFERRED]` - see the Step Log. The item was **removed** from the menu rather than shipped permanently hidden.

---

### Step 03.4 - Hide the new ids on the four hosts that do not implement them

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandalonePlayerActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Each of these four inflates the same menu and hides what does not apply to it. Add every id introduced in Step 03.1 to each host's hide list, except where the host genuinely implements the command. `StandalonePlayerActivity` hides everything but `menu_open_in_fms` via a loop, so it needs no edit if that loop covers new ids by construction - verify rather than assume.
>
> Hide the parent editing item itself, not only its children: hiding children leaves an empty section, which is exactly the defect strategic §6 item 2 warns about.

**Why:**

Strategic §11 criterion 5 requires that no empty section appears on a file where nothing applies, and the planning research established that five hosts share this one menu resource with per-item hiding, so an id added centrally is visible everywhere until each host hides it.

**Verification:**

- `Grep` - each of the five new ids appears in the hide list of `DocumentStandaloneActivity`, `AudioStandaloneActivity` and `TextStandaloneActivity`, or the Step Log records why a given host keeps one.
- `Grep` - the parent editing item's id appears in each hide list, not merely its children.
- `StandalonePlayerActivity`'s loop is confirmed by reading it to cover new ids; the Step Log records the evidence.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done` or explicitly `[DEFERRED]` with a recorded reason.
- [x] Project compiles - `.\a.ps1 fc` exit 0 in 22s (the single build validating this phase's code **and** the S1364 probe tags).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Every file touched is under the 1500-LOC ceiling - largest is `PhotoVideoStandaloneActivity.kt` at 1316.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Step Log

- 2026-08-07 - Step 03.1 done. Editing section added as a nested `<menu>` holding seven items; `menu_draw_overlay` and `menu_rotate_content_standalone` were **moved** into it rather than copied, verified by a per-id count showing exactly one declaration each. `.\a.ps1 fr` exit 0.
- 2026-08-07 - Step 03.2 done. Four branches wired on the image/video host, each reaching the handler its inline bar button already used, and each menu item's visibility mirrors that button's own gate so the entry can never offer what the button would not. The autorotate item's `isChecked` reads `viewModel.rotationSensorEnabled.value`. The host also hides the whole section when every child is hidden, matching the embedded player's rule.
- 2026-08-07 - **Step 03.3 `[DEFERRED]`. Named dependency: `PlayerDeleteUndoCoordinator`.** It is not injected - `PlayerViewModel` constructs it inline (`PlayerViewModel.kt:241`) passing `stateFlow = state`, which is a `StateFlow<PlayerViewModel.PlayerState>`, plus a `ParentCallbacks` object implementing `saveResumeState()` and `reloadFiles()` on `PlayerViewModel` itself. The standalone hosts carry a different state type (`StandalonePlayerState`) and neither callback. Reaching undo from them therefore requires generifying the coordinator over its state type or introducing an abstraction between them - a design decision that is not derivable from this spec or the codebase, and strategic §10 already assigns undo's semantics to **S1326**.
- 2026-08-07 - Step 03.3 consequence: `menu_undo_standalone` was **removed from the menu XML** rather than shipped permanently hidden. A menu item no host can ever show is dead weight under Rule 20 and would be one accidental `isVisible = true` away from a dead tap. The XML carries a comment naming the blocking dependency and S1326 so the next reader does not have to re-derive it. **Strategic §11 criterion 6 is therefore only partly met** - crop-in-place, rename and screen-autorotate landed; undo did not. This must be stated in the final report and must not be described as delivered in `ALL_FEATURES`.
- 2026-08-07 - Step 03.4 done, and it turned up a **pre-existing defect worth more than the step itself**. `menu_rotate_content_standalone` was absent from the hide lists of the Document, Text and Audio hosts, so it rendered on all three - but none of those hosts has a `when` branch for it, so it was a dead tap on every one. Hiding the new parent section on those hosts removes its children with it and retires that dead item as a side effect; each host's comment records why. For Audio the item was doubly wrong: audio has no frame to rotate.
- 2026-08-07 - Step 03.4 evidence for `StandalonePlayerActivity`: it was read rather than assumed. Its loop walks `popup.menu` top-level items and sets `isVisible = (itemId == menu_open_in_fms)`, so the new section - itself a top-level item - is hidden by construction along with its children, as are the new rename and autorotate items. No edit was needed there.
- 2026-08-07 - Probe tags inserted before this phase's build, 2 for S1364: `CommandPanelController.kt` logs the editing section's member count and the autorotate state each time the overflow opens, and `PhotoVideoStandaloneActivity.kt` logs the reverse-rotation tap. `.\a.ps1 fc` exit 0 validated code and tags in one build.
- 2026-08-07 - Phase-boundary audit. Layer 1: no business logic entered the activities - every new branch delegates to an existing delegate, handler or view-model call; the largest file is 1316 LOC against the 1500 ceiling. Layers 2-3: no listener added or removed, no lifecycle or coroutine surface touched; the one `lifecycleScope.launch` in this menu is pre-existing and untouched. Layer 4: Room untouched. No P0/P1. **P2 recorded, not fixed:** the editing-section membership now exists twice - as `EDIT_SUBMENU_COMMANDS` in `CommandPanelController` and as the `<menu>` block in the XML - so the two windows can drift. The XML comment and INDEX both name the Kotlin set as the source of truth, which is the cheapest available guard short of generating one from the other.

---

## Handoff Notes to Next Phase

Both windows carry the same editing section. Which commands the separate window actually gained - and whether undo among them - is recorded in this phase's Step Log and is the input Phase 04's FEATURES sentences must match; do not describe a capability this phase deferred.

---

## Rollback Plan

Revert the phase commit. The five hosts share one menu resource, so a partial revert that keeps the XML but drops a host's hide list would surface unhandled items - revert the whole phase together.
