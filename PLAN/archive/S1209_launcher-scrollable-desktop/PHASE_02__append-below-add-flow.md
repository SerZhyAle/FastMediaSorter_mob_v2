# Phase 02 - Append-below add flow

**Strategic spec:** [`../S1209_launcher-scrollable-desktop.md`](../S1209_launcher-scrollable-desktop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phase 01
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Let a shortcut be added without first tapping a specific empty square, landing it in the first free position and, when none is free, in a new row below everything.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved - none block it.
- [ ] `temp/CODE.LOCK` free.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 540 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 790 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherEditModeManager.kt` | Modified | ≤ 200 |
| `app_v2/src/launcherEnabled/res/layout/launcher_taskbar.xml` | Modified | ≤ 190 |
| `app_v2/src/main/res/values{,-ru,-uk}/strings.xml` | Modified | ≤ 3 |

> No new class. The placement rule already exists in `LauncherDesktopRepositoryImpl.addCellInFirstFreeSlot` and is already covered by `LauncherDesktopRepositoryImplTest`; this phase gives the desktop a second caller rather than a second implementation.
>
> The taskbar is one shared layout file included by both orientations of `activity_launcher_home`, so Rule 11's portrait/landscape pairing is satisfied by editing it once - there is no `res/layout-land/launcher_taskbar.xml` to keep in step.

---

## Steps

### Step 02.1 - Add a slotless add path to the ViewModel

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a ViewModel entry that records a pending add with no row and no column, alongside the existing one that carries the tapped coordinates. When the chosen content comes back, route the slotless case to the repository operation that picks the first free position (`addCellInFirstFreeSlot`) instead of `addCell`. Keep the coordinate-carrying path exactly as it is - both must work. Use `viewModelScope`; do not add a new dispatcher or scope.

**Why:**

Strategic §1 records that adding a shortcut today requires seeing and tapping a specific empty square, so once the visible part of the screen is full there is nowhere left to tap and the owner's "new shortcuts go under the existing ones" never happens; strategic §5.1 item 2 states the fix is a second entry into the same repository operation the settings widget-placement flow already uses, so the rule for where a new cell lands stays in one place.

**Verification:**

- `Grep` - `addCellInFirstFreeSlot` matches at least once in `LauncherHomeViewModel.kt`.
- `Grep` - the existing coordinate-carrying add call is still present (`addCell(` still matches in the same file).
- `Grep` - `GlobalScope` returns zero hits in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - Verification 4\4 PASS. Files: `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` (+38 LOC). `.\a.ps1 fk` exit 0 (BUILD SUCCESSFUL in 1m 12s). Dev log recorded.
- 2026-08-06 - The column count is a parameter of the new entry rather than a read of `LauncherDesktopRepository.state()`, because that operation's own KDoc states the grid width belongs to the screen currently rendering the desktop and not to the stored desktop (Rule 8 - existing KDoc is a requirement). `PlaceHomeWidgetOnLauncherDesktopUseCase` reads it from state only because the settings flow has no desktop on screen; the launcher Activity has one and already computes it in `currentColumns()`.

---

### Step 02.2 - Offer the slotless add from the taskbar

**Files:** `app_v2/src/launcherEnabled/res/layout/launcher_taskbar.xml`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherEditModeManager.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Put a "+" button on the taskbar as the last element before the Done button, gone at rest and shown while the desktop is being edited - the surface and visibility rule the owner chose (strategic §3.3, §6.5). Give its show/hide and its click to `LauncherEditModeManager`, which already owns the Done affordance, rather than to the activity's own edit-mode collector. Tapping it opens the same content picker the empty-square tap opens, with no coordinates attached, and the chosen content goes to the slotless ViewModel entry from Step 02.1. Do not add a new screen or dialog layout. Wire `nextFocus*` on the new button and on Done so D-pad traversal along the bar has no gap (Rule 16); it needs a `contentDescription` because a bare "+" tells a screen reader nothing.

**Why:**

Strategic §2.2 requires that adding a shortcut without choosing a cell places it in the first free position, and §1 identifies the missing reachable entry point - not the missing placement rule - as the reason the owner's request is unmet on a full screen; §3.3 records the owner's `/ui-clarify` answer that this entry point is a taskbar button shown only in edit mode.

**Verification:**

- `Grep` - the new action calls the slotless ViewModel entry added in Step 02.1, matching at least once.
- `Grep` - `Log.d(` returns zero hits in `LauncherHomeActivity.kt`.
- `Grep` - the new button id appears in `launcher_taskbar.xml` with `android:visibility="gone"`, and no `res/layout-land/launcher_taskbar.xml` exists to pair it against.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - DEFERRED. The prompt names the behaviour but no surface hosts it, and nothing in the strategic spec or the code settles which one does. Two inline resolution attempts, both refused by the evidence: (1) a Start-menu row - the only labelled-row surface in the launcher - needs `LauncherStartMenuFragment` plus `launcher_start_menu.xml` in both orientations, none of which this step's `Files Touched` covers, and the step forbids a new dialog layout; (2) a new `LauncherActionCatalog` key - the project's own "launcher action" concept, which would carry the Step 02.3 label - is circular here, because S1402 makes such an action reachable as a desktop *cell*, and placing a cell requires tapping an empty square, which is the exact thing this phase exists to stop requiring. A third candidate, the long-press on the desktop container while already in edit mode (today an unhandled no-op in `LauncherEditModeManager.attach()`), is both outside this step's `Files Touched` and inside the gesture-arbitration question strategic §6.3 leaves open with S1401.
- 2026-08-06 - Not guessed, per CLAUDE.md Rule 10 and the S1338 UI-phase refusal. The surface decides reach: a Start-menu row is remote- and D-pad-reachable, a desktop gesture is not, and the two are not interchangeable on a TV or a car head unit. Strategic §6.5 now carries the question for `/ui-clarify`.
- 2026-08-06 - Correction to the first deferral note: the Start-menu candidate was overstated. `fragment_launcher_start_menu.xml` has no `res/layout-land` counterpart, so it would have been one file, not two. The candidate lost on merit, not on cost.
- 2026-08-06 - Verification 4\4 PASS after the owner answered §6.5. `addCellInFirstFreeSlot` matches 2 in `LauncherHomeActivity.kt`; `Log.d(` matches 0; `launcherAddCell` carries `android:visibility="gone"` and `res/layout-land/launcher_taskbar.xml` does not exist; `.\a.ps1 fk` exit 0 (BUILD SUCCESSFUL in 15s), `.\a.ps1 fr` exit 0 (BUILD SUCCESSFUL in 7s). Files: `launcher_taskbar.xml` (181 LOC), `LauncherEditModeManager.kt` (186 LOC), `LauncherHomeActivity.kt` (776 LOC) - all inside budget.
- 2026-08-06 - A fourth surface, unseen by the earlier pass, is what the owner picked: the taskbar already hosts `launcherEditDone`, GONE at rest and shown in edit mode since S1412. The "+" is that same pattern one element earlier, so the layout edit is a sibling of an existing button rather than a new surface, and `LauncherEditModeManager` - which already owns the Done affordance - owns both. That is why the activity's own `editMode` collector was left alone.
- 2026-08-06 - "No square was pointed at" travels as `NO_SLOT = -1` in the picker's existing row/col arguments rather than as a parallel boolean field. The picker round-trips those coordinates through its result bundle and never reads them, so a separate flag could desync from them and a slotless flow returning row 0 would silently overwrite the top-left square. All three terminal writes (`placeGadget`, `placeWeatherGadget`, `addShortcut`) now go through one `placeAtPendingSlot`, so the two entry points cannot drift apart.
- 2026-08-06 - Icon-only, per the mockup the owner approved. A labelled button would have cost ~90dp of a bar that already carries Start, two icon strips, the tray and Done; the weighted strip block yields slack first, so on a narrow phone in edit mode the recents strip would have collapsed to nothing. The label reaches the user as `contentDescription` plus `android:tooltipText` (API 26, and both launcher flavors are minSdk 26).
- 2026-08-06 - Label landed and verified on the emulator: the bar reads `Start | [pinned] | + | + Add to desktop | Done`, and the labelled button is now distinct from both the pin-an-app "+" and the empty-square "+". Evidence: `temp/S1209/23_labelled_button.png`. The predicted squeeze is visible and worse than "narrowed" - the pinned strip is clipped mid-icon, because `taskbarRecents` carries the weight and `taskbarPinned` is `wrap_content`, so recents collapses first and pinned is cut only when even that is not enough. Only while editing; reported to the owner rather than redesigned further.
- 2026-08-06 - A black launcher desktop after reinstalling briefly looked like a regression from this edit. It was not: the button is `visibility="gone"` outside edit mode, so no attribute on it can affect the desktop at rest. `MainActivity` in the same process captured normally, logcat carried no inflate error, and the surface came back after a screen off/on cycle - an emulator compositor state, not a defect. Recorded because the same false alarm will cost the next reader the same detour.
- 2026-08-06 - REVERSED after the emulator run. The mockup was wrong about the one thing the icon-only choice rested on: it drew the tray between the two "+" glyphs, but the tray can be empty, and then the new button sits flush against the pinned strip's own "+". Worse, edit mode paints a "+" on every empty square, so the screen carries dozens of the same glyph with a third meaning. The owner was shown the screenshot and chose to label the button; the recents strip squeezing during edit mode is the accepted cost. Evidence: `temp/S1209/19_edit_mode_recheck.png`. This is why an owner sign-off on a drawing is not a sign-off on the rendered widget.

---

### Step 02.3 - Add the action's label in lockstep

**Files:** `app_v2/src/main/res/values{,-ru,-uk}/strings.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add the label for the new action with one `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En -Ru -Uk` call so the three strict locales land together. Name what the action does rather than how it works. The owner chose an icon-only button, so this string is not drawn on the bar - it is the button's `contentDescription` and its long-press tooltip, which is what carries the meaning to a screen reader and to anyone who cannot tell this "+" from the pin-an-app "+" further along the same bar. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 for the message type and §6 for tone.

**Why:**

Strategic §3.2 makes EN/RU/UK mandatory for any new visible string, and the byte-preserving tool with a single lockstep call is the project's mechanism for keeping the three from drifting apart.

**Verification:**

- `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action get -Key <key>` lists all three strict locales.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<key prefix>"` exits 0.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-06 - DEFERRED with Step 02.2. The label names an action that does not exist yet, and the wording depends on the surface: a Start-menu row reads as a verb phrase next to "Edit desktop", a context-menu item on a chosen square reads differently. Writing it before §6.5 is answered would put a string into three locales that the answer could invalidate.
- 2026-08-06 - Verification 3\3 PASS. `launcher_add_cell` added in one `set-android-string.ps1 -Action add` call: EN "Add to desktop", RU "Добавить на стол", UK "Додати на стіл". `check_strings_localized.ps1 -KeyPrefix "launcher_add_cell"` exit 0 - "all 1 key(s) present in en/ru/uk". Wording names the destination rather than the mechanism, which is what tells this "+" apart from the pin-an-app "+" on the same bar - the reason the deferral note said the surface decides the wording.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, `.\a.ps1 fr` exit 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `close-and-log.ps1 -DevLogs`.
- [x] Phase-boundary audit run - Layer 1 architecture and Layer 2 coroutine ownership are in scope; the ViewModel gains a suspend path.

**Phase-boundary audit (2026-08-06).** Layer 1: the layering holds - the activity only chooses which of the two ViewModel entries to call, and the rule for *where* a cell lands stays in the repository, so no placement logic moved up. The edit-mode chrome stayed with `LauncherEditModeManager` instead of leaking into the activity's own `editMode` collector, which would have split one concern across two owners. Layer 2: no coroutine is created here - both writes suspend inside `viewModelScope` in the ViewModel, unchanged from Step 02.1. Listener symmetry: the new `setOnClickListener` sits on a view owned by the activity's binding and dies with it, the same lifetime the Done button already had, so no unregister is owed. Rule 11 (orientation pairing) is satisfied by construction - the taskbar is one shared include with no `layout-land` twin.

---

## Handoff Notes to Next Phase

A cell can now be created without the user choosing where it goes, so the desktop can grow past the visible screen without the user having to scroll first to find a target square.

---

## Rollback Plan

Revert the phase commit. The repository operation it calls is pre-existing and used by another flow, so nothing is orphaned; the desktop returns to tap-an-empty-square as the only add path.
