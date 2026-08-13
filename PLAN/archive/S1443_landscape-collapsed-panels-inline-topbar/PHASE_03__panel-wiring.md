# Phase 03 - Panel wiring

**Strategic spec:** [`../S1443_landscape-collapsed-panels-inline-topbar.md`](../S1443_landscape-collapsed-panels-inline-topbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Connect the three collapse owners and the command bar measurement to the executor so the relocation becomes live and correct in every layout bucket.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1` before the first edit - Rule 23 requires it for any multi-file source edit. The original reason, a parallel S1444 session on the same command bar, lapsed when S1444 was archived on 2026-08-10.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsCollapseManager.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainProgramsPanelManager.kt` | Modified | ≤ 370 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStreamsPanelManager.kt` | Modified | ≤ 270 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1470 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipsPlacementManager.kt` | Modified | ≤ 230 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). `MainActivity.kt` sits at 1432 lines against the 1500 cap - the wiring in step 03.2 must stay inside its stated budget, and any growth beyond it moves into a helper instead.
>
> Landscape parity: this phase edits no file under `res/layout*`, so CLAUDE.md Rule 11 has nothing to mirror. The three chips and both host containers already exist with identical ids in `layout/`, `layout-land/` and `layout-w600dp/`.

---

## Steps

### Step 03.1 - Notify on every change to the collapsed chip set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsCollapseManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainProgramsPanelManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStreamsPanelManager.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> Add a constructor parameter `onChipVisibilityChanged: () -> Unit = {}` to `MainResourceTabsCollapseManager`, `MainProgramsPanelManager` and `MainStreamsPanelManager`, declared last and defaulted so existing construction sites still compile. Invoke it at the end of each manager's own visibility-applying function - the one that writes the chip's `isVisible` - so it fires on a collapse toggle, on an expand toggle and on an availability change alike. `MainResourceTabsManager` constructs the collapse manager internally, so give it the same defaulted parameter and forward it.
>
> Do not move, rename or otherwise change the existing `resolveVisibility` companion functions or their call sites - the unit tests around them must keep passing untouched.

**Why:**

Strategic §5.2 makes a change of collapsed state the event that starts the whole relocation flow, and §4 records that each owner today toggles its own chip in isolation with nobody watching, so without this notification the executor would never learn the chip set changed.

**Verification:**

- `Grep` - `onChipVisibilityChanged` matches at least twice in each of `MainResourceTabsCollapseManager.kt`, `MainProgramsPanelManager.kt`, `MainStreamsPanelManager.kt`.
- `Grep` - `onChipVisibilityChanged` matches at least twice in `MainResourceTabsManager.kt` (parameter plus forwarding).
- `Grep` - `internal fun resolveVisibility` still matches exactly once in each of the three collapse managers.
- `.\a.ps1 fu` - the three existing `resolveVisibility` test classes still pass; read their class results in the JUnit XML.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x]` done

---

### Step 03.2 - Construct and connect the executor in MainActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`

**Depends on:** Step 03.1

**Prompt for developer:**

> Declare a `MainCollapsedChipsPlacementManager` field and construct it before `MainLayoutChromeManager` at line 386, passing `binding.layoutControlButtons`, `binding.mainCollapsedPanelsRow` and the three chips in row order. Pass `layoutChrome::restitchControlBarFocusChain` as its `onPlacementChanged`. Pass a lambda to the new `MainLayoutChromeManager` parameter that stores the reported free width and calls the executor's `apply` with it and the current `resources.configuration.isWideLayout()`. Pass the same executor's re-apply as the `onChipVisibilityChanged` argument at the three panel-manager construction sites - `MainProgramsPanelManager` at line 861, `MainStreamsPanelManager` at line 882, and the resource-tabs manager - where the re-apply reuses the last reported free width rather than measuring again.
>
> Keep the added lines inside the file's stated budget; if the wiring does not fit, move it into a small helper under `ui/main/helpers/` rather than growing the activity.

**Why:**

Strategic §5.1 puts the coordinator in a helper and not in the activity precisely because the activity is close to the file-size cap, and §5.2 names the command-bar measurement as the source of the budget every relocation decision runs on.

**Verification:**

- `Grep` - `MainCollapsedChipsPlacementManager(` matches exactly once in `MainActivity.kt`.
- `Grep` - `onChipVisibilityChanged` matches at least three times in `MainActivity.kt`.
- `Grep` - `isWideLayout()` matches at least once in `MainActivity.kt`.
- `MainActivity.kt` is at most 1470 lines.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x]` done

---

### Step 03.3 - Re-apply on configuration change

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`

**Depends on:** Step 03.2

**Prompt for developer:**

> In `onConfigurationChanged`, next to the existing `streamsPanelManager.onConfigurationChanged()` call at line 1286, re-run the command-bar measurement so a fresh free width is reported for the new configuration, and guard the executor call with the initialization check the surrounding calls already use. Rely on the existing `applyControlBarOverflow` path for the measurement rather than measuring in the activity.

**Why:**

Strategic §5.2 lists a configuration change as one of the events that must restart the relocation flow, and §11 criterion 6 requires the narrow portrait layout to look exactly as it does today, which only holds if rotating out of wide layout returns every chip to the row.

**Verification:**

- `Grep` - `onConfigurationChanged` in `MainActivity.kt` contains a call reaching `applyControlBarOverflow` or the placement executor.
- `Grep` - `isInitialized` matches on the placement-executor call in `onConfigurationChanged`.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x]` done

---

### Step 03.4 - Give the relocated chip a command-bar touch target

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCollapsedChipsPlacementManager.kt`

**Depends on:** Step 03.3

**Prompt for developer:**

> When a chip is moved into the command bar, set its `minimumHeight` to `R.dimen.control_button_size` so it matches the buttons it now sits beside and clears the minimum touch target; restore `minimumHeight` to `0` when the chip is moved back into the collapsed-panels row, where the row's own vertical rhythm applies. Set the chip's `contentDescription` to its own `text` when it enters the bar and clear it back to `null` when it leaves, so a chip surrounded by icon buttons is still announced.

**Why:**

Strategic §3.2 requires the relocated remnant to keep its touch-target size and its TalkBack label, and the chip's inflated padding alone does not reach a command-bar-sized target once it stands in a row of full-height buttons.

**Verification:**

- `Grep` - `minimumHeight` matches at least twice in `MainCollapsedChipsPlacementManager.kt`.
- `Grep` - `contentDescription` matches at least twice in that file.
- `Grep` - `control_button_size` matches at least once in that file.
- `.\a.ps1 fk` - exit code 0.

**Status:** `[x]` done

---

## Step Log

- 2026-08-08 - Step 03.1 done. Defaulted `onChipVisibilityChanged` added to `MainResourceTabsCollapseManager`, `MainProgramsPanelManager` and `MainStreamsPanelManager`, invoked at the end of each manager's own visibility-applying function, and forwarded through `MainResourceTabsManager`. The `resolveVisibility` companions were not touched. Targeted unit run: `MainProgramsPanelManagerTest` 4/4, `MainResourceTabsCollapseManagerTest` 4/4, `MainStreamsPanelManagerTest` 8/8, zero failures.
- 2026-08-08 - Step 03.2 done. `MainCollapsedChipsPlacementManager` constructed before `MainLayoutChromeManager` so it captures the chips' inflated row indices; the chrome manager's free-width callback stores the width and re-places; the three panel construction sites pass `::reapplyCollapsedChipPlacement`. `MainActivity.kt` is 1320 lines, inside the 1470 budget.
- 2026-08-08 - Step 03.3 done. `onLayoutConfigurationChanged` calls `reapplyCollapsedChipPlacement()` after the existing panel refreshes; the initialization guard lives inside that helper.
- 2026-08-08 - Step 03.4 done. A chip entering the bar takes `control_button_size` as `minimumHeight` and its own text as `contentDescription`; both are reset when it returns to the row.
- 2026-08-08 - On-device evidence, emulator-5554, display reshaped to 2400x1080 at density 240 (1600dp wide, rotation left at 0 - a rotated AVD returns zero-byte screencaps). BEFORE: `S1443: control bar measured available=2400 needed=1295 free=1105`, `placement wide=true free=1105 inline=[] row=false` - bar ends after "Slideshow" with a wide empty band, filter tabs on their own row (`temp/S1443/S1443_03_main_wide.png`). AFTER long-pressing the "Local" tab: `S1443: re-place collapsed chips free=1105 wide=true`, `placement wide=true free=1105 inline=[2131362701] row=false` - the filter chip is appended after "Slideshow" inside the command bar and the collapsed-panels row is gone (`temp/S1443/S1443_04_after_collapse_wide.png`).
- 2026-08-08 - Counter-evidence for the fallback, from the same build at the AVD's native shape (914dp wide): `control bar measured available=2400 needed=2375 free=25` and `inline=[]` - with the bar nearly full the chip correctly stayed in its own row (`temp/S1443/S1443_02_after_longpress_local_tab.png`). The two runs together exercise both branches of §11 criteria 1 and 3.
- 2026-08-08 - Acceptance sweep on emulator-5554, same build. §11 criterion 5 (expand) PASS: tapping the inline chip returned the tab row and logged `placement wide=true free=1105 inline=[] row=false` (`temp/S1443/S1443_05_after_expand.png`). §11 criterion 3 (fallback) PASS at a third display width: `wm size 1400x1080` gave `control bar measured available=1400 needed=1295 free=105` and `placement wide=true free=105 inline=[] row=true` - 105px is real but below the chip's 357px plus gap plus reserve, so it correctly stayed in its row (`temp/S1443/S1443_08_narrow_fallback.png`). §11 criterion 6 (portrait untouched) PASS: `wm size 1080x2400` + `density 420` logged `placement wide=false free=0 inline=[] row=true` with the chip on its own row (`temp/S1443/S1443_09_portrait.png`).
- 2026-08-08 - §11 criterion 2 (two and three chips at once) NOT EXERCISED: on freshly onboarded data no programs or streams panel row renders at all, so only the filter chip was ever collapsible. Needs a device with at least one configured program and one pinned stream.
- 2026-08-08 - §11 criterion 7 (D-pad reaches an inline chip) INCONCLUSIVE, not a recorded pass or fail. `DPAD_DOWN` focused `btnExit`; the first `DPAD_RIGHT` reported focus on `btnMoreActions` of a content-grid card at `[2274,246]`, and eleven further presses moved nothing at all. `chipFilterCollapsed` was present and `focusable="true"` in the same dump. A geometric right-search from `btnExit` at `[0,90][106,186]` would not land on x=2274 either, and eleven dead presses in a row reads as key events not reaching the window rather than as a chain that points somewhere wrong - so this evidence cannot support a verdict in either direction. Carried into the ticket's status note as the sharpest open item.
- 2026-08-08 - Eight `re-place collapsed chips` entries against two `placement` entries during startup: the callback fires on every chip-visibility write, and the idempotence guard absorbs all but the two that changed anything. This is the §3.2 no-repeated-layout-cycle constraint observed working, not noise.
- 2026-08-08 - Probe tags inserted before this phase's build, one per changed flow entry: control-bar measurement (`MainLayoutChromeManager`), placement application (`MainCollapsedChipsPlacementManager`), re-place entry (`MainActivity`). `.\a.ps1 dq` exit 0 - the single build that validated implementation plus tags. APK `FastMediaSorter_standard_debug_v2.60.8071.632-DEBUG.apk`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, APK built.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1 -Files`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated - chained by `post-change.ps1`.
- [x] `temp/CODE.LOCK` released via `scripts/utils/exit-code-lock.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).
- [x] UI phase refusal (S1338): placement decision recorded in strategic §3.3 as the owner's verbatim ruling; screenshots captured this phase at `temp/S1443/S1443_03_main_wide.png` and `temp/S1443/S1443_04_after_collapse_wide.png`.

---

## Handoff Notes to Next Phase

The relocation is live end to end: collapse toggle, availability change and configuration change all reach the executor, and the narrow bucket is restored explicitly. Remaining work is inventory and catalog bookkeeping plus the on-device verification gate.

---

## Rollback Plan

Revert phase commit(s) - every change in this phase is either a defaulted constructor parameter or a call site in `MainActivity`; reverting leaves Phase 01 and 02 code in the tree, unreachable and harmless.
