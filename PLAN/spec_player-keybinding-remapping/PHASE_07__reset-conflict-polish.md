# Phase 07 — Hierarchical Reset · Conflict Policy · Polish

**Strategic spec:** [`../spec_player-keybinding-remapping.md`](../spec_player-keybinding-remapping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03, 04, 05, 06
**Blocks:** Phase 08
**Steps done:** 0 / 6
**Started:** —
**Completed:** —

---

## Objective

Complete the user-facing surface:

1. Group-level and global-level reset flows with destructive-confirmation dialog.
2. Conflict visualiser — flag rows that share a trigger.
3. Capture-mode conflict handling (per strategic §10 resolution: block / flag / overwrite).
4. Optional undo-snackbar (if strategic §10 resolved to "time-limited undo").
5. Trilingual string polish — every user-facing phrase finalised in EN/RU/UK with Author Style (`..`, `ё`/`Ё`).

---

## Prerequisites

- [ ] Phases 03, 04, 05, 06 are `✅ Done`.
- [ ] Strategic §10 items resolved: **conflict policy**, **reset confirmation granularity**, **undo window**.
- [ ] Trilingual string files clean of any `...` (ellipsis rule) and missing `ё`/`Ё`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapViewModel.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/CaptureDialogFragment.kt` | Modified | ≤ 450 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/ResetGroupUseCase.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/ResetAllUseCase.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/DetectConflictsUseCase.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/helpers/ResetConfirmationDialog.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/helpers/UndoSnackbarController.kt` | New (if applicable) | ≤ 150 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 07.1 — Group + global reset use cases

**Files:** `domain/input/usecase/ResetGroupUseCase.kt`, `domain/input/usecase/ResetAllUseCase.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> - `ResetGroupUseCase(repo)`: `suspend operator fun invoke(group: CommandGroup)` — iterates commands in the group (source: Defaults Map File), calls `repo.clearOverride(commandId, device = *)` for each.
> - `ResetAllUseCase(repo)`: `suspend operator fun invoke()` — single call to `repo.clearAll()` which wipes the override table in one transaction (strategic §11 atomicity).
>
> Both are idempotent.

**Verification:**

- `Grep "class ResetGroupUseCase"` matches exactly once.
- `Grep "class ResetAllUseCase"` matches exactly once.
- `Grep "repo.clearAll"` in `ResetAllUseCase.kt` matches exactly once.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[ ]` not done

---

### Step 07.2 — Conflict detection use case

**Files:** `domain/input/usecase/DetectConflictsUseCase.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Pure computation over a `List<InputBinding>`: return `Map<InputTrigger, List<CommandId>>` filtered to only triggers bound to ≥ 2 commands. Consider `(device, serialized trigger string)` as the equality key — same keycode on different devices is not a conflict.
>
> Expose: `operator fun invoke(bindings: List<InputBinding>): ConflictReport` where `ConflictReport = Map<InputTrigger, List<CommandId>>`.
>
> Called from `KeybindingRemapViewModel` on every binding list emission. No coroutine, no repository access — pure function.

**Verification:**

- `Grep "class DetectConflictsUseCase"` matches exactly once.
- `Grep -n "@Inject constructor"` matches exactly once (no dependencies besides empty constructor).
- `Grep "repo" app_v2/src/main/java/com/sza/fastmediasorter/domain/input/usecase/DetectConflictsUseCase.kt` returns zero hits (pure — no repo).

**Status:** `[ ]` not done

---

### Step 07.3 — Wire conflicts + group/global reset into the ViewModel

**Files:** `ui/keybinding/KeybindingRemapViewModel.kt`
**Depends on:** Steps 07.1, 07.2

**Prompt for developer:**

> Extend `RemapUiState` with `conflicts: Map<InputTrigger, List<CommandId>> = emptyMap()`. On every `observeResolvedBindings` emission, call `DetectConflictsUseCase` and update state.
>
> Extend `KeybindingRow` with `conflictWith: List<CommandId> = emptyList()` — populated from the conflict report.
>
> Add intent handlers:
>
> - `fun onResetGroupRequested(group: CommandGroup)` — emits a `PendingConfirmation.GroupReset(group)`.
> - `fun onResetAllRequested()` — emits `PendingConfirmation.AllReset`.
> - `fun onConfirmReset()` — resolves pending confirmation and calls the appropriate use case.
> - `fun onCancelReset()` — clears pending confirmation.
>
> Add to state: `val pendingConfirmation: PendingConfirmation? = null` where `PendingConfirmation = GroupReset(group) | AllReset`. Single-command reset stays instant (no confirmation, per strategic §10 recommendation — adjust if the resolution went differently).

**Verification:**

- `Grep "conflicts: Map<InputTrigger" app_v2/src/main/java/com/sza/fastmediasorter/ui/keybinding/KeybindingRemapViewModel.kt` matches exactly once.
- `Grep "fun onResetGroupRequested"` matches exactly once.
- `Grep "fun onResetAllRequested"` matches exactly once.
- `Grep "DetectConflictsUseCase"` matches ≥ 1 (use case injected and called).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[ ]` not done

---

### Step 07.4 — ResetConfirmationDialog + UI wiring

**Files:** `ui/keybinding/helpers/ResetConfirmationDialog.kt`, `ui/keybinding/KeybindingRemapActivity.kt`, `res/values/strings.xml` (+ `-ru`, `-uk`)
**Depends on:** Step 07.3

**Prompt for developer:**

> `ResetConfirmationDialog` is a thin `AlertDialog.Builder` wrapper with two entry points:
>
> - `showForGroup(context, group, onConfirm)` — body text: `"Reset all bindings in the ${group.localisedName} group to factory defaults?"` (localised).
> - `showForAll(context, onConfirm)` — body text: `"Reset every binding to factory defaults? This cannot be undone."` (localised).
>
> Strings added to all three `strings.xml` files. Keys: `reset_group_title`, `reset_group_body_fmt`, `reset_all_title`, `reset_all_body`, `reset_confirm_button`, `reset_cancel_button`. Apply Author Style across RU text (`..`, `ё`/`Ё`).
>
> Wire up in `KeybindingRemapActivity`:
>
> - Bottom "Reset all" floating button (placeholder from Phase 06) → `viewModel.onResetAllRequested()`.
> - Per-group header carries a small `resetGroup` icon → `viewModel.onResetGroupRequested(group)`.
> - Observe `state.pendingConfirmation` — when non-null, show the appropriate dialog; on confirm → `viewModel.onConfirmReset()`; on cancel → `viewModel.onCancelReset()`.

**Verification:**

- `Glob` — `ResetConfirmationDialog.kt` exists.
- Each of the six string keys present in all three `strings.xml` files (18 grep hits total).
- `Grep "showForGroup\|showForAll"` matches ≥ 2 in activity.
- Author Style check: `Grep "\\.\\.\\." app_v2/src/main/res/values-ru/strings.xml` returns 0 (no `...` — only `..`).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[ ]` not done

---

### Step 07.5 — Capture-mode conflict handling

**Files:** `ui/keybinding/CaptureDialogFragment.kt`
**Depends on:** Step 07.2

**Prompt for developer:**

> In `CaptureDialogFragment`, on every trigger sample inside capture mode, check the current `ConflictReport` (passed in via fragment arguments or a small conflict-check callback injected by the Activity). If the sampled trigger already maps to another `CommandId`:
>
> - **Policy "block":** disable the commit button, show a red inline label `"Already bound to: <conflicting command name>"`.
> - **Policy "flag":** allow commit, show an amber inline label `"Also used by: <conflicting command name>. Commit will create conflict."`.
> - **Policy "silently overwrite":** commit without warning, but the resulting row in the list visually flags the conflict (already handled by Step 07.3 via `KeybindingRow.conflictWith`).
>
> Pick the implementation that matches the strategic §10 resolution — only one branch lives in the final code.

**Verification:**

- `Grep "conflictWith\|conflict_label"` matches ≥ 1 in the capture fragment.
- `Grep "setEnabled\(false\)"` matches in "block" policy OR `visibility = View.VISIBLE` color guard matches in "flag" policy.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[ ]` not done

---

### Step 07.6 — Undo-snackbar (conditional on strategic §10 resolution)

**Files:** `ui/keybinding/helpers/UndoSnackbarController.kt`, `ui/keybinding/KeybindingRemapViewModel.kt`, strings files
**Depends on:** Steps 07.1, 07.3

**Prompt for developer:**

> **Skip this step entirely** if strategic §10 "Undo window" resolved to "immediate commit — no undo". Write a one-line note in the Handoff Notes below stating "Undo snackbar skipped per §10 resolution — immediate commit." and mark this step `[-] skipped` in the tracker.
>
> **If the resolution chose "time-limited snackbar":**
>
> - `UndoSnackbarController`: thin wrapper around `Snackbar.make(..., Snackbar.LENGTH_LONG)` with an "Undo" action button. Keeps a single `Snackbar` reference; dismissing on new show.
> - In the ViewModel: on `setBinding` / `resetBinding` / group-reset / all-reset, emit a `one-shot event` via `SharedFlow<UndoEvent>` that the activity observes. Event carries a `() -> Unit` revert callback — the callback stores the prior state and restores it.
> - Strings: `undo_binding_changed_snackbar_text`, `undo_action_label` — in all three languages. Apply Author Style.

**Verification (if not skipped):**

- `Grep "class UndoSnackbarController"` matches exactly once.
- `Grep "SharedFlow<UndoEvent>"` matches in `KeybindingRemapViewModel.kt`.
- Each string key present in all three `strings.xml` files.
- `Grep -n "Log\.d\("` returns zero hits.

**Verification (if skipped):**

- Handoff Notes contains the skip rationale line.
- This step's `Status:` is `[-] skipped` and `INDEX.md` `Phases: X/N done` counts Phase 07 as 5/6 complete with the phase still flipping to ✅ Done (strategic decision records the skip).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` is `[x] done` OR `[-] skipped` with recorded rationale.
- [ ] `/build` reports green for all flavors.
- [ ] End-to-end manual: in `standardDebug`, set a keyboard override, close-and-reopen app — override persists. Reset the one row — default restored. Reset a group — every row in the group returns to default. Reset all — entire list returns to factory.
- [ ] Conflict visualiser renders: create two rows with `Ctrl+R` (rename + refresh) — both rows show conflict flags.
- [ ] Author Style: `Grep -R "\.\.\." app_v2/src/main/res/values*/` returns zero hits.
- [ ] Grep for `TODO(phase-07)` returns zero hits.
- [ ] Dev log entries added for every "Files Touched" file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

- User-facing feature is complete. Phase 08 handles docs (`docs/FEATURES.md` trilingual), catalog refresh, and final dev-log sweep.
- Every resolved item from strategic §10 now has a concrete UI manifestation — audit that §10's resolution column matches the implemented behaviour and note any drift in the Change Log of `INDEX.md`.

---

## Rollback Plan

- Revert the phase commits. The feature remains usable after a Phase 07 rollback — set / per-row reset still work (Phase 06 surface) — only group/global reset and conflict visualiser disappear.
- Phase 02's `clearAll()` repository method stays harmless if unused — no rollback needed there.
