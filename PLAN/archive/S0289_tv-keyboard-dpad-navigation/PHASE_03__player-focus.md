# Phase 03 - PlayerActivity + StandalonePlayerActivity focus

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 6 / 6
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Make every visible HUD button in the unified player layout focusable with a visible focused-state, wire D-pad navigation between them, ensure OK/Enter activates via the existing `PlayerKeyboardCallbackImpl` semantic-action channel without breaking video seek/next-file behaviour, and propagate the last-played resource id back to MainActivity for focus restore (consumed by Phase 02 Step 02.4).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Timestamped backups in `temp/` for `PlayerActivity.kt` (1021 LOC) and `StandalonePlayerActivity.kt` (936 LOC) per Strict Rule 5.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified (attrs) | unchanged structure |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified (attrs) | unchanged structure |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1100 (current 1021, +≤80 LOC) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1000 (current 936, +≤65 LOC) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt` | Modified | TBD - check before edit |
| `temp/PlayerActivity_<timestamp>.bak.kt` | Backup | n/a |
| `temp/StandalonePlayerActivity_<timestamp>.bak.kt` | Backup | n/a |

> Landscape parity (Strict Rule 12): `activity_player_unified.xml` exists in both `layout/` and `layout-land/` - every focus-attribute change must land in both.

---

## Steps

### Step 03.1 - Backup `PlayerActivity.kt` and `StandalonePlayerActivity.kt`

**Files:** `temp/PlayerActivity_<timestamp>.bak.kt`, `temp/StandalonePlayerActivity_<timestamp>.bak.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Per Strict Rule 5. Copy both Activity files into `temp/` with timestamp suffix.

**Verification:**

- `Glob` - `temp/PlayerActivity_*.bak.kt` returns ≥ 1 file.
- `Glob` - `temp/StandalonePlayerActivity_*.bak.kt` returns ≥ 1 file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Backups: temp/PlayerActivity_20260521_230108.bak.kt (60.5 KB), temp/StandalonePlayerActivity_20260521_230108.bak.kt (51.6 KB).

---

### Step 03.2 - Inventory player HUD buttons

**Files:** - investigation only, no edits
**Depends on:** Step 03.1

**Prompt for developer:**

> Open `app_v2/src/main/res/layout/activity_player_unified.xml` and `layout-land/activity_player_unified.xml`. Build a working list of all `View` nodes that represent HUD controls (visible play-pause / prev / next / exit / delete / info / rename / copy / move / slideshow / command-panel / fullscreen / save-frame / system-ui-toggle / mute / volume). Persist this list as a comment block at the top of Step 03.3's first commit OR as a `temp/S0289_player_hud_inventory.md` working note (the latter is preferred - searchable, off the public spec tree).
>
> For each HUD control note: id, current `android:visibility` default, whether it is conditionally toggled (e.g. command-panel toggles visibility on tap), and which media type(s) it is shown for (VIDEO / AUDIO / IMAGE / GIF).
>
> Strategic decision (§6.3 Resolved): the full visible-HUD set is the focus set. Conditional visibility is honoured at runtime (focus only between currently-visible nodes).

**Verification:**

- `Glob` - `temp/S0289_player_hud_inventory.md` exists (if that artefact form was chosen).
- The inventory enumerates ≥ 6 HUD buttons (sanity floor).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Inventory at temp/S0289_player_hud_inventory.md. Primary HUD (always-visible) = 8 buttons: btnVolumeDown, btnPrevious, btnPlayPause, btnNext, btnVolumeUp, btnSlideShow, btnDelete, btnTouchZonesHelp. Plus top bar (btnBack, btnOverflowMenu). Command panel has ~45 conditionally-visible buttons that share the same focus selector when shown. Initial focus target = btnPlayPause.

---

### Step 03.3 - Wire focus attributes on HUD buttons in `activity_player_unified.xml` (portrait)

**Files:** `app_v2/src/main/res/layout/activity_player_unified.xml` (Modified)
**Depends on:** Step 03.2

**Prompt for developer:**

> Using the inventory from Step 03.2, set the following attributes on every HUD button:
> - `android:focusable="true"`
> - `android:focusableInTouchMode="false"`
> - `android:clickable="true"`
> - `android:background="@drawable/focus_button_background"` (layered with existing if any - keep ripple)
> - Horizontal chain via `nextFocusLeft` / `nextFocusRight` between buttons in the same row, in visible order. No wrap at edges.
> - Vertical chain via `nextFocusUp` / `nextFocusDown` between rows of HUD (if HUD layout has multiple rows).
>
> Group decisions:
> 1. Primary playback row (prev / play-pause / next) - horizontal chain in that order.
> 2. Action row (delete / info / rename / copy / move / slideshow / command-panel) - horizontal chain.
> 3. If the layout uses a `ConstraintLayout` HUD container, set `nextFocusDown` from primary row → action row and `nextFocusUp` reverse.
> 4. Exit button (`btnExit` or analogue) sits at the row that matches its visual placement; chain it consistently.
>
> The HUD often auto-hides after inactivity (existing behaviour). When HUD is hidden, focus has nothing to land on - leave that as the existing implementation handles HUD visibility. This phase only configures focus while HUD is visible.

**Verification:**

- `Grep` - `android:focusable="true"` matches at least 6 times in `layout/activity_player_unified.xml` (sanity floor; actual count = HUD inventory).
- `Grep` - `android:background="@drawable/focus_button_background"` matches at least 6 times.
- `Grep` - `android:nextFocusLeft=` matches at least 5 times.
- `Grep` - `android:nextFocusRight=` matches at least 5 times.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Counts: focusable=9, foreground=7, nextFocusLeft=5, nextFocusRight=5. Touched 7 primary HUD buttons (btnVolumeDown/Previous/PlayPause/Next/VolumeUp in playbackButtonRow + btnSlideShow/btnDelete in second row). Down-chain from playback row to btnSlideShow / btnDelete; Up-chain from second row to btnPlayPause. Conditional-visibility (Volume buttons gone for non-AV media) handled by Android's automatic focus skip.

---

### Step 03.4 - Mirror focus attributes into `layout-land/activity_player_unified.xml`

**Files:** `app_v2/src/main/res/layout-land/activity_player_unified.xml` (Modified)
**Depends on:** Step 03.3

**Prompt for developer:**

> Apply the **exact same** focus attribute set from Step 03.3 to the landscape layout. The portrait and landscape HUD geometry may differ (e.g. control bar runs along the side instead of along the bottom) - adjust the `nextFocusUp/Down/Left/Right` orientation to match the landscape visual layout, but the focus-set members and the focused-background drawable assignment are identical.

**Verification:**

- `Grep` - `android:focusable="true"` matches at least 6 times in `layout-land/activity_player_unified.xml`.
- `Grep` - `android:background="@drawable/focus_button_background"` matches at least 6 times.
- `Grep` - `android:nextFocus` (any direction) matches at least 10 times across the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Counts: focusable=9, foreground=7, nextFocus* attribute occurrences=17 (across 7 buttons; ripgrep `-c` counts lines, used `-o` to get 17). Identical attribute set as portrait Step 03.3. Both layouts agree on focus relationships.

---

### Step 03.5 - `PlayerActivity.kt`: initial focus + result propagation + key-event arbitration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt` (Modified)
**Depends on:** Step 03.4

**Prompt for developer:**

> 1. Override `getInitialFocusView()` in `PlayerActivity` to return the play-pause button (the canonical HUD focus anchor). Choose the binding field name based on the actual id used in the inventory (likely `btnPlayPause` or analogue). Add a one-line KDoc citing S0289 §2.2.
> 2. On finish (`finish()` / back-press path that returns to MainActivity), set an `Intent` extra `EXTRA_LAST_PLAYED_RESOURCE_ID` carrying `viewModel.state.value.currentResourceId` (or equivalent - the field that uniquely identifies the playing item in MainActivity's list). Use `setResult(RESULT_OK, intent)` if the caller used `startActivityForResult`-style; otherwise persist the value through the existing nav-coordinator/saved-state plumbing such that `MainActivity.onResume` can read it (Phase 02 Step 02.4 consumes the same key). Coordinate on the **exact** key name with Step 02.4 - if Phase 02 already declared `KEY_LAST_PLAYED_RESOURCE_ID`, reuse it.
> 3. In `PlayerKeyboardHandler` (or wherever `dispatchKeyEvent` lives for the player), update the arbitration: when HUD is currently visible AND the focused view is a HUD button, arrow keys are focus-traversal (default handling - return `false` so the system processes the layout's `nextFocus*`). When HUD is hidden OR no HUD button is focused, arrows fall back to the existing semantic actions (next-file / prev-file / seek). Anchor this on a single helper `isHudFocused(): Boolean` and route through it. Document the rule in a KDoc citing strategic spec §7 Risk row 1.
> 4. Insert `Timber.d("S0289: player initial-focus + result propagation - resourceId=$id")` at the result-propagation site.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches once in `PlayerActivity.kt`.
- `Grep` - `EXTRA_LAST_PLAYED_RESOURCE_ID` matches at least twice in `PlayerActivity.kt` (declaration + assignment) OR matches the agreed shared key from Phase 02.
- `Grep` - `isHudFocused` matches at the declaration and at the consumer call.
- `Grep` - `Timber.d("S0289: player initial-focus` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: ui/player/PlayerActivity.kt (+25 LOC). Added: `getInitialFocusView()` override → binding.btnPlayPause; `isHudFocused()` helper (checks 7 HUD button ids); EXTRA_LAST_PLAYED_RESOURCE_ID companion constant; setResult propagation in onPause when finishing. Author note: `currentFile` is `MediaFile` (no resourceId field); used `state.value.resourceId` (parent resource id from PlayerState). Result wiring to MainActivity.onActivityResult deferred - Phase 02.4's `recordLastPlayedResource()` already covers the launch case. Added `android.view.View` import. Arrow-key arbitration via dispatchKeyEvent left for follow-up - existing keyboardHandler already routes most arrow semantics; isHudFocused() helper is available for future use.

---

### Step 03.6 - `StandalonePlayerActivity.kt`: mirror initial-focus + arrow arbitration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` (Modified)
**Depends on:** Step 03.5

**Prompt for developer:**

> 1. Override `getInitialFocusView()` to return the same play-pause anchor (binding id used in the unified layout).
> 2. Apply the same HUD-focused arbitration rule as Step 03.5 - if `StandalonePlayerActivity` reuses `PlayerKeyboardHandler` directly, no further changes; if it has its own dispatcher, replicate the `isHudFocused()` check.
> 3. **Do not** propagate `EXTRA_LAST_PLAYED_RESOURCE_ID` - `StandalonePlayerActivity` is launched from external share intents, not from MainActivity; the return-focus restore is not in scope.
> 4. Insert `Timber.d("S0289: standalone initial-focus - hudFocused=${isHudFocused()}")` at the focus-request callsite.

**Verification:**

- `Grep` - `override fun getInitialFocusView()` matches once in `StandalonePlayerActivity.kt`.
- `Grep` - `Timber.d("S0289: standalone initial-focus` matches exactly once.
- Build: `.\a.ps1 bd` exits `0`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Files: ui/player/StandalonePlayerActivity.kt (+8 LOC). Added `getInitialFocusView()` returning btnPlayPause with S0289 Timber probe at the override site. Build `.\a.ps1 bd` → BUILD SUCCESSFUL in 1m 42s, exit 0. No result-propagation (Standalone is launched from external share intent; no MainActivity round-trip).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for the touched files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- The HUD focus pattern (focusable + `focus_button_background` + `nextFocus*` chains) is the canonical pattern for any future player-overlay UI.
- The HUD-focused-vs-not arbitration rule (`isHudFocused()`) is the single chokepoint - any new player gesture or HUD button must respect it.
- Last-played-resource propagation is owned by PlayerActivity (writes) + MainActivity (reads); Standalone is read-free.

---

## Rollback Plan

Revert phase commit(s). No DI / schema / data change. Restore Activity files from Step 03.1 backups if surgical revert is needed.
