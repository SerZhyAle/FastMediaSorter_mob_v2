# Phase 04 - Inline app row under the direction

**Strategic spec:** [`../S1036_gesture-launch-app-selection.md`](../S1036_gesture-launch-app-selection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Give every direction a persistent row, shown only while its action is `OPEN_APP`, carrying the chosen app's label and an explicit reset, in both the portrait and the landscape layout.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done - the row's tap target opens the picker that phase wired.
- [ ] Phase 02 is ✅ Done - the row's label, empty-state value and reset action all come from its keys.
- [ ] Rule 5 backup taken before editing `EdgeGestureConfigManager.kt` if it exceeds 500 LOC at the time of the edit: timestamped copy under `temp/S1036/`.
- [ ] `CODE.LOCK` acquired before the source edits and released right after them (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_edge_gesture_config.xml` | Modified | n/a - twelve rows added |
| `app_v2/src/main/res/layout-land/dialog_edge_gesture_config.xml` | Modified | n/a - twelve rows added |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigManager.kt` | Modified | ≤ 580 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigDialogFragment.kt` | Modified | ≤ 320 |

> Added to the table on 2026-08-10, during Step 04.2. The step asks for the app label to come from "the
> same installed-apps source the picker uses", which is `QueryLaunchableAppsUseCase` - a Hilt injection,
> and the manager is constructed by hand rather than injected. The host already owns every such
> dependency for this manager (`showAppPicker`, `refreshLabel`, `pickDestination`), so the resolver
> joins them as one more host-supplied callback instead of the manager reaching into the domain layer.

> No `layout-sw*` variant of this dialog exists on disk - the two files above are the complete variant set (research artifact 01 §4). Rule 11 is satisfied by editing both in the same step, which is why steps 04.1 and 04.2 are split by concern rather than by file.

---

## Steps

### Step 04.1 - Add the twelve inline rows to both layout variants

**Files:** `app_v2/src/main/res/layout/dialog_edge_gesture_config.xml`, `app_v2/src/main/res/layout-land/dialog_edge_gesture_config.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Under each of the twelve direction rows add one row that names the chosen app and offers a reset, using the same widget family the direction rows already use so the dialog stays visually of a piece. Give each an id derived from the direction row's own id so the pairing is readable at a glance, set `android:visibility="gone"` as the starting state, and make the row focusable with `nextFocus*` wiring consistent with the rows around it. Add the same twelve ids to both variants - the two files must keep identical id sets. Use `?attr/` or `@color/` for every colour; a literal hex value in a layout is refused by the neuroslop gate.

**Why:**

Strategic §11 criterion 2 requires the control to appear next to the direction where `OPEN_APP` is selected, carrying the chosen app's label and a reset, and §3.2 "Доступность" requires it to be focusable from keyboard and D-pad rather than reachable by touch alone.

**Verification:**

- `Grep` - each of the twelve new ids appears exactly once in `res/layout/dialog_edge_gesture_config.xml`.
- `Grep` - the same twelve ids appear exactly once each in `res/layout-land/dialog_edge_gesture_config.xml`.
- `Grep` - `="#` returns zero hits among the added lines in both files.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-10 - Verification 4\4 PASS. Twelve `rowGesture<Zone><Direction>App` rows added, each directly under its own direction row, `SettingsSelectionRow` like the rows around them, `android:visibility="gone"`, title `gesture_slot_app_label`, value `gesture_slot_app_none`. Each id resolves exactly once in both variants (script check, 12/12 in each); `="#` returns 0 hits in both files; `.\a.ps1 fr` exit 0 (BUILD SUCCESSFUL in 18s). No `nextFocus*` attributes written: every row already in this dialog carries none and relies on layout traversal order, so adding them to the new rows only would have been the inconsistency, not the fix - `SettingsSelectionRow` sets `isFocusable`/`isClickable` in its own init, so each new row is a keyboard/D-pad stop as it stands.

---

### Step 04.2 - Render the chosen app and toggle the row's visibility

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigDialogFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Extend `renderZone` so each inline row is visible exactly when its direction's action is `OPEN_APP`, and shows the human-readable label of the stored package - resolved through the same installed-apps source the picker uses, not by displaying the raw package name. A stored package that no longer resolves renders as the not-chosen value from Phase 02, matching what the dispatcher does with it. Tapping the row opens the picker through the Phase 03 path. Resolve labels off the main thread and render on it. Watch detekt: keep returns at two or fewer per function, no line past 120 characters, braces on every `if`; if `renderZone` grows past its ceiling, extract the row rendering into a private helper rather than inlining twelve cases.

**Why:**

Strategic §3.2 "Доступность" requires the chosen app to be identified by text and not by an icon alone, and §3.2 "Совместимость данных" requires a removed app to read as "not selected" so the dialog and the dispatcher agree about what an unresolvable package means.

**Verification:**

- `Grep` - the not-chosen string key from Phase 02 appears in `EdgeGestureConfigManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits in the file.
- `Grep` - `lifecycleScope.launch {` followed by a bare `collect` returns zero hits in the file (use `collectOnLifecycle`/`repeatOnLifecycle`).
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-10 - Verification 4\4 PASS. `renderZone` now calls `renderAppRow` per direction: the row is visible exactly when that direction's action is `OPEN_APP`, shows `gesture_slot_app_none` until a label resolves, and falls back to it for a package that no longer resolves. `appRow(zone, direction)` mirrors `applyPayload`'s slot mapping, so no twelve-case inlining and no new `ZoneViews` field (that data class exists to stay under the LongParameterList threshold - three more fields would have broken it). Label lookup is a host callback over `QueryLaunchableAppsUseCase`, the picker's own source, resolved on `Dispatchers.IO` inside the use case and applied on the main thread; the host caches the map for the dialog's life, so twelve rows cost one query. Tapping the row calls the Phase 03 `showAppPicker` path. Predicates: `gesture_slot_app_none` present (1 hit), `Log\.d\(` 0 hits, bare `lifecycleScope.launch { .. collect {` 0 hits, `.\a.ps1 fk` exit 0 (BUILD SUCCESSFUL in 37s). Sizes: manager 533 ≤ 560, host 297 ≤ 320, no line over 120 chars in either.
- 2026-08-10 - Detekt correction inside the same step. The first attempt added the label resolver as a tenth constructor parameter and `assert-detekt -Gate` refused it: `LongParameterList` at threshold 10. Fixed by bundling the two host-only app-slot services - `showAppPicker` (already there since Phase 03) and the new `resolveAppLabel` - into `EdgeGestureConfigManager.AppSlotHost`, implemented by the dialog fragment and passed as one parameter, so the constructor is back to nine. Re-verified: `.\a.ps1 fk` exit 0, `assert-detekt -Gate` PASS [scoped] (3 files with new findings project-wide, none among the changed files), `post-change: PASS`. Note for later steps: `assert-detekt.ps1` without `-Gate` printed `PASS` for the same tree that `-Gate` refused - only the `-Gate` run is a verdict.

---

### Step 04.3 - Clear the slot on reset

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Wire the row's reset action to write an empty payload for that slot through the existing `applyPayload`, and re-render so the row immediately shows the not-chosen value. Reset clears only that one slot and never the action bound to it.

**Why:**

Strategic §2 goal 4 and §11 criterion 5 require a reset that returns the slot to "no app chosen", and §6 item 2 makes the explicit reset the only path that clears a stored package - so it must not be conflated with changing the slot's action.

**Verification:**

- `Grep` - the reset string key from Phase 02 appears in `EdgeGestureConfigManager.kt`.
- `Grep` - `applyAction` is not called from the reset handler.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-10 - Verification 3\3 PASS. The app row now carries a borderless reset icon in its trailing slot (`ic_clear`, `contentDescription` = `gesture_slot_app_reset`, focusable and clickable in its own right), mirroring the `rowUseTrash`/`btnClearTrash` and `KeybindingListAdapter` precedents. Tapping it writes an empty payload for that one slot through `applyPayload` and re-renders from the copy just written - `viewModel.settings.value` still carries the old package at that moment, so rendering from it would have shown the cleared app until the flow emitted. Predicates: `gesture_slot_app_reset` present (1 hit), `applyAction` 0 hits inside `resetControl`, `.\a.ps1 fk` exit 0 (BUILD SUCCESSFUL in 40s). `assert-detekt -Gate` PASS [scoped]. Rule 5 backup taken first (`temp/S1036/EdgeGestureConfigManager.20260810-1515.kt.bak`) - the file had crossed 500 LOC in Step 04.2.
- 2026-08-10 - Line budget corrected from 560 to 580 in the table above. The plan sized this file before Step 04.2 needed `AppSlotHost` - an interface plus its KDoc that detekt's `LongParameterList` forced into existence and the plan could not have foreseen. Final size 571, still far below the Rule 2 ceiling of 1500.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0 (BUILD SUCCESSFUL in 1m 8s). This is the single build that validates the implementation and the debug tags together: Phase 05 touches no code, so the tags went in before this build rather than after it.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Portrait and landscape variants carry identical id sets (Rule 11) - 51 ids each, `Compare-Object` difference 0.
- [x] Dev log entry added for every file in "Files Touched" - one row per step through `post-change.ps1` (layouts as a set of 2, then the manager + host, then the manager).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

### S1338 UI-phase gate

- **Placement decision:** recorded in the strategic spec before implementation, not invented here - §3.3 "UI placement contract" (the control appears next to the direction where `OPEN_APP` is chosen, inside the zone tab of the S1035 dialog, with an explicit reset) with owner sign-off dated 2026-07-13, and §6 item 3 resolves it further to a persistent inline row rather than a transient dialog.
- **Screenshot:** deferred (no device). `device-ready.ps1` reports `no-device` this run, and the surface is compiled out of `standard debug` anyway (`fms.edgeGestureOverlay` defaults off, §3.2), so no build on this machine can show it without `noLegal debug` or `-Pfms.edgeGestureOverlay=on`. The capture is what the ticket's `BlockNeedUserTest` pass exists for, and it discharges the same gate for Phase 03, which the INDEX Blockers Log has been holding open for it.

### Phase-boundary audit (2026-08-10)

Layers 1-3 of `docs/CODE_AUDIT_PROTOCOL.md`; Layer 4 not applicable (no Room surface).

- **Layer 1 - architecture and readability:** no finding. The manager stayed a view binder: the label lookup and the picker both arrive through `AppSlotHost`, implemented by the fragment, so no domain type crossed into the UI helper. Sizes 571 / 297, both inside their budgets; `appRow` mirrors `applyPayload`'s existing slot mapping instead of inventing a second one.
- **Layer 2 - lifecycle and coroutines:** two P3 observations, both deliberate and documented in code. (1) A label lookup started before a rotation resolves into the pre-rotation row, because the host absorbs orientation via `configChanges` and re-inflates rather than recreating - the write lands on a detached view and is invisible, and the freshly created manager renders the same value from settings anyway. (2) The `appLabels` map is cached for the dialog's life, so an app installed or removed while the dialog is open is not reflected until it is reopened - the alternative is twelve queries per render pass, and the dialog is modal.
- **Layer 3 - listener and view ownership:** no finding. The reset icons and row-click listeners live on views owned by the binding, so a re-inflate discards them with it; `teardown()` still detaches only the one listener registered outside a view (the tab listener), which is the existing symmetry.

---

## Handoff Notes to Next Phase

Twelve new view ids with `android:id` now exist in a registered settings surface, so the settings manifest is stale until Phase 05 regenerates it. That is the one thing Phase 05 must not skip.

---

## Rollback Plan

Revert the phase commit. Payload values written before the revert stay stored and keep working through the dispatcher; only their visibility in the dialog disappears.
