# Phase 04 - Card menu entry

**Strategic spec:** [`../S1474_stream-about-channel.md`](../S1474_stream-about-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Put the item into the channel card's menu in both display modes and open the window from there, choosing the playing engine when that channel is the one currently playing.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] The owner's placement ruling in strategic §3.3 is read: card menu in tile and row alike; the inline radio bar gets no new control.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInfoDialogManager.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/menu/StreamActionCatalog.kt` | Modified | ≤ 15 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt` | Modified | ≤ 20 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` | Modified | ≤ 20 added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 25 added |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/menu/StreamActionCatalogTest.kt` | Modified | ≤ 15 added |

> `StreamsActivity.kt` is ~1257 LOC against the 1500 hard limit, so the opening logic lives in the new manager and the activity gains only the wiring. Both it and any file over 500 LOC get a timestamped backup under `temp/S1474/` before editing.

> **Plan corrected against the code, 2026-08-08.** Steps 04.2 and 04.3 below were written for a world where each adapter builds its own menu with its own id constants. That stopped being true with **S1424**: `StreamActionCatalog` is now the single source of a channel menu's composition, both adapters render it through `StreamMenuBinder`, and the id of a row is derived from the `StreamMenuAction` ordinal. Adding the item to the enum therefore mirrors it across row and tile by construction, which is exactly what those two steps set out to guarantee by hand - so the composition change moves to the catalog, and each adapter gains only its callback and its click branch. The catalog is also read by the launcher desktop cell, a surface strategic §11 does not name, so the new action joins `DESKTOP_EXCLUDED` and the launcher menu is unchanged.

---

## Steps

### Step 04.1 - Add the launcher manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInfoDialogManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StreamInfoDialogManager`, mirroring how `StreamsFilterDialogManager` is owned by the streams screen. It takes a channel entity and decides how to open the window: when that channel is the one the inline audio manager is currently playing, hand the running engine to the dialog; otherwise let the dialog measure the url. Before opening, cancel the health sweep so two decoders do not run at once. The manager owns no state beyond the currently shown dialog and dismisses it when the screen goes away.

**Why:**

Research artifact 04 rules that the catalog sweep and a measurement must not decode simultaneously, and strategic §11 criterion 6 requires the playing channel to be read rather than reopened - both decisions belong in one place rather than being repeated at four adapter construction sites.

**Verification:**

- `Glob` - `.../streams/helpers/StreamInfoDialogManager.kt` exists.
- `Grep` - `class StreamInfoDialogManager` matches once.
- `Grep` - the health probe's `cancel` is called before the dialog is shown.
- `Grep` - the inline audio accessor from step 02.3 is referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `StreamInfoDialogManager.kt` created (39 LOC, budget 160): `healthProbe()?.cancel()` runs before the window is shown, and `playingEngine` is handed over only when `inlineAudio.playingId == source.id`. Owns nothing but the shown dialog, and `dismiss()` closes it when the screen goes away.
- Both collaborators are taken as accessors rather than as instances: `healthProbe` is `by lazy` and `inlineAudio` is a `lateinit` created in `onCreate`, so reading either at manager-construction time would build the probe too early or throw on the uninitialised field. The `inlineAudio` accessor guards with `::inlineAudio.isInitialized`, which the activity itself already does everywhere else.

---

### Step 04.2 - Add the menu item to the list row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add an `onAboutChannel` callback parameter and a new menu id constant, and insert the item into the overflow menu built in `bindOverflowMenu`. Place it directly above the share item, so the reading actions sit together and the destructive remove stays last. Show it for every channel regardless of origin.

**Why:**

Strategic §11 criterion 1 requires the item in the card menu, and §7 records that this menu is mirrored between the row and the tile - an item added to one and not the other is the recurring defect this pairing exists to prevent.

**Verification:**

- `Grep` - `onAboutChannel` present in the constructor parameter list.
- `Grep` - the new id constant declared in the private companion.
- `Grep` - the new `menu.add` call sits before the share item's `menu.add`.
- `Grep` - the new id handled in `setOnMenuItemClickListener`.

**Status:** `[x] done` - executed against the corrected design recorded above Files Touched.

**Step Log:**

- 2026-08-08 - Verification adapted to the code as it stands, and PASS on the adapted form. The three predicates about a private id constant and a `menu.add` call have no referent since S1424: `StreamSourceAdapter` builds nothing itself, it calls `buildStreamMenu` which delegates to `StreamActionCatalog`, and a row's id is derived from the `StreamMenuAction` ordinal. What was verified instead: `onAboutChannel` is present in the constructor parameter list, and `StreamMenuAction.ABOUT_CHANNEL -> onAboutChannel(source)` is handled in `onStreamActionSelected` - the dispatch the click listener delegates to - placed directly above the `SHARE_LINK` branch.
- `ABOUT_CHANNEL` was added to the enum between `EDIT` and `SHARE_LINK`, which is the position this step asked for: above sharing, with removal still last.
- The ordinal shift this causes for `SHARE_LINK` and `REMOVE` is harmless - `menuItemId` is computed per popup and never persisted, so no stored value refers to it.

---

### Step 04.3 - Mirror the item on the grid tile

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Make the same addition to the tile overflow menu, in the same position and with the same label, so the two menus stay item-for-item identical.

**Why:**

Strategic §11 criterion 1 names both display modes, and the tile menu is documented in its own class KDoc as a mirror of the row menu.

**Verification:**

- `Grep` - `onAboutChannel` present in the constructor parameter list.
- `Grep` - the new id constant declared in the private companion.
- `Grep` - the item's position relative to share matches the row adapter.

**Status:** `[x] done` - executed against the corrected design recorded above Files Touched.

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS in its adapted form. `StreamGridAdapter` gained `onAboutChannel` in its constructor and the same `StreamMenuAction.ABOUT_CHANNEL` branch directly above `SHARE_LINK`. Position relative to share is identical to the row adapter by construction rather than by inspection: both render the list `StreamActionCatalog.actionsFor` returns, so the order is one enum, not two hand-kept sequences.
- This is the step whose stated rationale - "an item added to one and not the other is the recurring defect this pairing exists to prevent" - S1424 has since solved structurally. The mirroring is now impossible to break by editing one adapter.

---

### Step 04.4 - Wire all four adapter construction sites

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Back the file up under `temp/S1474/` first. Instantiate the manager in the activity and pass its open function as `onAboutChannel` at all four adapter construction sites - the list, the pinned list, the grid and the pinned grid. Dismiss the dialog when the screen is destroyed. Add nothing else to the activity.

**Why:**

The screen builds four adapters for the same catalog, so a callback wired at three of them produces a menu item that works everywhere except the pinned block - a failure that looks like an intermittent bug rather than a missing wire.

**Verification:**

- `Grep` - `onAboutChannel` appears exactly four times in the file.
- `Grep` - the manager is dismissed in the destroy path.
- Backup file present under `temp/S1474/`.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `onAboutChannel` appears exactly 4 times in `StreamsActivity.kt` - the list, the pinned list, the grid and the pinned grid, each wired to `streamInfoDialogManager.show(it)`. `streamInfoDialogManager.dismiss()` is the first statement of `onDestroy`, before the engines below it are released. `Log.d` 0. Backup at `temp/S1474/StreamsActivity_20260808_0215.kt.bak` (file is 1325 LOC, over the 500 threshold).
- The activity gained 15 lines: one import, the lazy manager, the destroy call and the four wires - nothing else, and it stays well under the 1500 limit.
- `StreamActionCatalogTest` needed two updates, since it asserts the exact menu composition: the expected list gained `ABOUT_CHANNEL` between `ADD_SHORTCUT` and `SHARE_LINK`, and the launcher-desktop case gained an assertion that it is absent there. Run scoped to that class: `tests="7" skipped="0" failures="0" errors="0"`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, 2026-08-08.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added via `post-change.ps1`, verdict `post-change: PASS`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.
- [x] `screenshot deferred (no device)` - S1338's UI gate. The placement decision is on record: strategic §3.3, owner ruling 2026-08-07, card menu in tile and row alike, no new control on the inline radio bar.

## Phase-boundary audit (2026-08-08)

- Layer 1 - architecture. The opening rules live in one 39-line manager rather than at four construction sites, and `StreamsActivity` gained 15 lines with no logic of its own - Rule 3 holds. Composition moved to `StreamActionCatalog`, which is where a menu's shape already belongs.
- Layer 2 - lifecycle. The manager holds only the shown dialog. `onDestroy` dismisses it as its first statement, before the inline audio engines are released, so a measurement reading a borrowed engine is stopped before that engine goes away.
- Layer 3 - listener and engine ownership. No listener is registered here. The borrowed engine is passed through, never released - the manager hands over `inlineAudio.playingEngine` only when the ids match, and the dialog releases only what the probe opened.
- Layer 4 - Room. Not touched.
- P2, fixed: a `SpacingBetweenDeclarationsWithComments` finding on the new enum constant.
- The plan-vs-code mismatch is recorded above Files Touched rather than worked around silently. Two of this phase's four steps described a menu architecture that S1424 replaced; their intent was preserved and their verification adapted, with the adaptation written into each Step Log.

---

## Handoff Notes to Next Phase

The card path is complete for both display modes and for radio. Phase 05 adds the video player's menu and reuses the same dialog with the player's own engine.

---

## Rollback Plan

Revert phase commit(s) - the menu item disappears and nothing else changes; no stored data is touched.
