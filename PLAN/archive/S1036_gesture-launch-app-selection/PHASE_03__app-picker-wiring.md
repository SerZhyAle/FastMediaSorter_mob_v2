# Phase 03 - Reuse the quick-launch app picker from the gesture dialog

**Strategic spec:** [`../S1036_gesture-launch-app-selection.md`](../S1036_gesture-launch-app-selection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress - both steps done, UI-phase screenshot gate outstanding
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-09
**Completed:** -

---

## Objective

When the user picks `OPEN_APP` for a direction, open the existing `AppPickerDialogFragment` under this dialog's own request key and write the chosen package into that slot's payload.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - the picker entry's reworded copy is what the user reads before this flow starts.
- [ ] `CODE.LOCK` acquired before the source edits and released right after them (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigDialogFragment.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigManager.kt` | Modified | ≤ 500 |

> `EdgeGestureConfigManager` is 461 LOC before this phase. Keep this phase's addition small; Phase 04 adds more to the same file and carries the Rule 5 backup step.

---

## Steps

### Step 03.1 - Register a result listener under this dialog's own request key

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigDialogFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Register a `setFragmentResultListener` for a request key owned by this dialog - not `AppPickerDialogFragment.RESULT_KEY`, which the panel editor already uses - and read `AppPickerDialogFragment.RESULT_PACKAGE` out of the bundle. Register it on the same `FragmentManager` the picker will be shown in, and show the picker on that manager, so the pair cannot disagree; because the host is itself a `DialogFragment`, state which manager was chosen in a one-line comment explaining why. Carry the target slot across the round trip through the fragment's own saved state rather than a field, so a configuration change during the pick does not lose which direction was being edited. Route the chosen package to `EdgeGestureConfigManager`.

**Why:**

Strategic ADR-2 requires reusing the quick-launch panel's picker rather than writing a second one, and research artifact 01 §3 records that `newInstance(requestKey)` exists precisely so a second host can share one `FragmentManager` without receiving the panel editor's results.

**Verification:**

- `Grep` - `AppPickerDialogFragment` appears in `EdgeGestureConfigDialogFragment.kt`.
- `Grep` - `setFragmentResultListener` appears exactly once in that file.
- `Grep` - `AppPickerDialogFragment.RESULT_KEY` returns zero hits in that file (the dialog must use its own key).
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 4\4 PASS. `AppPickerDialogFragment` 4 hits, `setFragmentResultListener` exactly 1, `AppPickerDialogFragment.RESULT_KEY` 0, `.\a.ps1 fk` exit 0 (BUILD SUCCESSFUL in 21s). Listener registered on `childFragmentManager` under this dialog's own key `edge_gesture_app_picker_result`, which is also the manager `showAppPicker` shows the picker in, so the pair cannot drift apart. Pending slot lives on the fragment and is written to `onSaveInstanceState`, because the manager is rebuilt on every re-inflate and losing the slot mid-pick would write the package into nothing. Files: `EdgeGestureConfigDialogFragment.kt` (+47 LOC).
- 2026-08-09 - Caught a self-matching predicate before claiming the step: the first draft of the companion-object comment contained the literal `AppPickerDialogFragment.RESULT_KEY`, which the zero-hit check greps for, so the check would have failed on my own comment. Comment reworded to name the key without quoting it, then re-verified at 0.

---

### Step 03.2 - Open the picker on `OPEN_APP` and store the package

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `openActionPicker`, add a branch mirroring the existing `OPEN_URL` one: when the picked action is `ScreenshotGestureAction.OPEN_APP`, ask the host to show the app picker for that zone and direction. Add a method that takes the returned package and writes it into the slot through the existing `applyPayload(settings, zone, direction, payload)` - do not add a second mutator and do not clear the payload when the action changes to something else. Keep `applyAction` untouched.

**Why:**

Strategic §6 item 2 is resolved as "keep the stored package when the slot's action changes, clear only on an explicit reset", and that is already how the neighbouring URL action behaves because applying an action does not touch the payload - diverging here would make two sibling actions behave differently for no stated reason.

**Verification:**

- `Grep` - `ScreenshotGestureAction.OPEN_APP` appears in `EdgeGestureConfigManager.kt`.
- `Grep` - `applyPayload` appears in that file (pre-existing) and no second payload mutator was introduced: `Grep` for `fun apply.*Payload` matches exactly once.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. `ScreenshotGestureAction.OPEN_APP` 1 hit in the manager, `fun apply\w*Payload` exactly 1 (the pre-existing mutator - no second one added), `.\a.ps1 fk` exit 0. The branch sits directly under the OPEN_URL one in `openActionPicker` and mirrors it. `applyAction` was not touched, so changing a slot's action still leaves its stored package alone, which is what strategic §6 item 2 resolved. Files: `EdgeGestureConfigManager.kt` (+18 LOC, one new constructor callback plus `onAppPicked`).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0 (BUILD SUCCESSFUL in 1m 3s). The two deprecation warnings are pre-existing in `CrashReportFormatter.kt` and `LoggingHelper.kt`, neither touched here.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" - one row naming both, `post-change: PASS`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.
- [ ] **UI-phase screenshot gate (S1338) - outstanding.** Discharged jointly with Phase 04, see the note below.

### Phase-boundary audit (2026-08-09)

Scope: Layer 1 always; Layer 3 because a listener was registered.

- **Layer 1 - architecture: clean.** The fragment keeps the round trip and the manager keeps the decision, which is the split the file already used for the destination picker - the manager receives a function reference rather than a `FragmentManager`. No business logic entered the fragment.
- **Layer 3 - listener ownership: clean.** `setFragmentResultListener(key, this)` binds to the fragment's own lifecycle, so the framework removes it at `DESTROYED`; there is no manual registration to pair with a manual removal. The `listener-symmetry` gate agrees (new imbalance 0).
- **Deliberate choice worth recording:** the pending slot is fragment state, not manager state, because the manager is rebuilt on every re-inflate (S1123). Putting it in the manager would lose the slot on rotation exactly when the picker is open.

### UI-phase screenshot gate - why it is deferred, not skipped

The changed surface is the edge-gesture config dialog, which is compiled **off** on a plain `standard debug` build (`fms.edgeGestureOverlay` defaults to off - strategic §3.2, research 01 §6). The emulator currently carries the standard debug APK, so the dialog cannot be reached there at all: this is a flavor gate, not a missing device. Phase 04 adds the row that makes this phase's work visible in the first place, so one `noLegal debug` build and one device pass discharge the gate for both phases. Flipping this phase to Done before that would certify a screen nobody looked at.

---

## Handoff Notes to Next Phase

After this phase the package can be chosen and stored, but nothing shows it: the direction row still renders only its action. Phase 04 makes the choice visible and resettable, which is what §11 criterion 2 actually asks for.

---

## Rollback Plan

Revert the phase commit. Any package already written into a payload stays there harmlessly - Phase 01's dispatcher treats an unused payload as inert for every action other than `OPEN_APP`.
