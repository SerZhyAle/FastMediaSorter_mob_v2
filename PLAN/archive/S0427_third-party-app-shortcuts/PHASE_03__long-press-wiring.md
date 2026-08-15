# Phase 03 - Long-press wiring on the home surface

**Strategic spec:** [`../S0427_third-party-app-shortcuts.md`](../S0427_third-party-app-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Make a long press on an installed-app entry open its quick actions - on desktop cells that carry an `app:` command and in the Start menu's "All apps" grid.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] `LauncherAppShortcutMenuManager` compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 320 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 570 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAppGridAdapter.kt` | Modified | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt` | Modified | ≤ 240 |

> `LauncherHomeActivity` is 527 lines - over the 500-line backup threshold. Take a timestamped copy into `temp/S0427/` before editing it (CLAUDE.md Rule 5) and keep the edit small enough to stay well under the 1500-line ceiling.
>
> Taskbar icons are deliberately untouched: their long press is reserved for pin/unpin (strategic §3.3).

---

## Steps

### Step 03.1 - Give the cell binder a long-press callback

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a constructor parameter `onCellLongPress: (View, LauncherCellUi) -> Boolean = { _, _ -> false }` and, in `bindShortcut`, attach `binding.root.setOnLongClickListener { onCellLongPress(binding.root, item) }`. Return the callback's own result so an entry that has nothing to show still behaves like an ordinary un-long-pressable cell.
>
> Do not touch `decorateForEdit`: the edit-mode scrim added there is a later child and already swallows the long press into the drag gesture, which is exactly the wanted split - arranging in edit mode, quick actions at rest.

**Verification:**

- `Grep` - `onCellLongPress: (View, LauncherCellUi) -> Boolean` matches exactly once.
- `Grep` - `setOnLongClickListener` matches exactly once inside `bindShortcut`.
- `Grep` - `LauncherApps` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 3/3 PASS. Files: grid/LauncherCellViewBinder.kt (+5 LOC, plus an `android.view.View` import the file previously spelled out inline).

---

### Step 03.2 - Open the popup from desktop app cells

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Inject `QueryAppShortcutsUseCase` and `StartAppShortcutUseCase` into the Activity and build a `LauncherAppShortcutMenuManager` with `lifecycleScope`. Pass an `onCellLongPress` lambda into `LauncherCellViewBinder`: decode the cell's stored command with `LauncherCellCommand.decode(..)`, and when it is a `LauncherCellCommand.App` - and only then - call `menuManager.show(view, command.packageName)` and return true; return false for every other command kind.
>
> Suppress the popup while `viewModel.editMode.value` is true so the gesture cannot fight the drag path. Call `menuManager.dismiss()` from the same lifecycle edge that already tears the surface down (`onStop`/`onDestroy`, matching the symmetry the other managers use) so no window outlives the Activity.
>
> Add one probe at this entry point: `Timber.d("S0427: app shortcuts requested for %s", packageName)`.

**Verification:**

- `Grep` - `LauncherAppShortcutMenuManager` matches at least twice in the file (field plus construction).
- `Grep` - `is LauncherCellCommand.App` present.
- `Grep` - `menuManager.dismiss()` present.
- `Grep` - `S0427:` matches at least once.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 5/5 PASS. Files: LauncherHomeActivity.kt (527 -> 564 LOC; backup at `temp/S0427/LauncherHomeActivity_20260724_1630.kt.bak`). The manager is built `by lazy` on `lifecycleScope` - the injected use cases are not yet available when this class's field initialisers run, and most Home visits never open the popup. Dismissed from `onStop()`, symmetric with the long press that opens it. The stored target is `LauncherCell.target`, decoded through `LauncherCellCommand.decode`.

---

### Step 03.3 - Open the popup from the Start menu app grid

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherAppGridAdapter.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/menu/LauncherStartMenuFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `onAppLongClick: (View, AppItem) -> Boolean = { _, _ -> false }` to `LauncherAppGridAdapter` and attach it as the row's long-click listener beside the existing click listener.
>
> In `LauncherStartMenuFragment`, inject the two use cases, build its own `LauncherAppShortcutMenuManager` on `viewLifecycleOwner.lifecycleScope`, and pass a lambda that calls `show(view, app.id)` - `AppItem.id` is the package name. Dismiss the manager in `onDestroyView` next to the existing binding teardown. Keep the sheet open: the popup anchors inside it, and dismissing the sheet on a long press would take the anchor away.

**Verification:**

- `Grep` - `onAppLongClick` matches at least twice across the two files.
- `Grep` - `LauncherAppShortcutMenuManager` present in `LauncherStartMenuFragment.kt`.
- `Grep` - `dismiss()` present inside `onDestroyView` in `LauncherStartMenuFragment.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 3/3 PASS. Files: LauncherAppGridAdapter.kt (+5 LOC), LauncherStartMenuFragment.kt (+20 LOC). Deviation from the prompt: the manager is bound to the fragment's own `lifecycleScope`, not `viewLifecycleOwner.lifecycleScope` - a `by lazy` field cannot safely capture a view scope that is replaced on every view recreation. Teardown is still `onDestroyView`, so the popup never outlives the sheet's view.
- 2026-07-24 - `post-change.ps1` first FAILed on `ticket-log-audit` (`S0427:` probes present while the ticket was still `In Progress`). Flipped the ticket to `BlockNeedUserTest` with a device-test note first, then re-ran: PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL in 22s.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via `post-change.ps1` (PASS on re-run).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2323 records.
- [x] Phase-boundary audit run - no P0/P1. Layer 1: no business logic entered the Activity beyond one command decode and a guard, the popup's own work stays in the manager (Rule 3). Layer 2: edit mode is read from the same `StateFlow` the binder renders from, so the gesture split cannot drift. Layer 3: listener symmetry holds - the Activity dismisses in `onStop`, the sheet in `onDestroyView`; the binder's long-click listener is re-attached on every rebuild along with the click listener it sits next to, so no stale listener survives a rebind. Gate `listener-symmetry` reported new imbalance 0.

---

## Handoff Notes to Next Phase

Two hosts now own a `LauncherAppShortcutMenuManager` instance each, both dismissed on their own teardown edge. Strategic criteria 1-3 are satisfied in code; criterion 4 (no new permissions) holds because nothing was added to any manifest.

---

## Rollback Plan

Revert phase commit(s). The binder's new parameter is defaulted, so reverting only the two host files also leaves a compiling tree.
