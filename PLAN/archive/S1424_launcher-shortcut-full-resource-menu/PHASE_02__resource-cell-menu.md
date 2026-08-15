# Phase 02 - Resource cell menu

**Strategic spec:** [`../S1424_launcher-shortcut-full-resource-menu.md`](../S1424_launcher-shortcut-full-resource-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Long press on a `res:` cell opens a `ListPopupWindow` of that resource's actions, and every cell kind gains an accessibility action that opens whatever menu it has.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] `CODE.LOCK` acquired before the first source edit, and released after (`scripts/utils/exit-code-lock.ps1`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceDeleteConfirmation.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1437 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | +1 key |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResourceActionManager.kt` | New | ≤ 260 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherCellActionMenuManager.kt` | New | ≤ 160 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt` | Modified | ≤ 420 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 900 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 600 |

> No `res/layout*` file is touched: the popup reuses `item_launcher_app_shortcut.xml` through `LauncherAppShortcutAdapter`, so CLAUDE.md Rule 11 (layout-land parity) does not apply to this phase.
>
> `MainActivity.kt` is at 1436 of 1500 lines. Step 02.1 must leave it **shorter**, not longer - it moves a dialog out and calls the moved builder.

---

## Steps

### Step 02.1 - Extract the delete confirmation so a second host can raise it

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceDeleteConfirmation.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Move the body of `MainActivity.showDeleteConfirmation` into a new `object ResourceDeleteConfirmation` exposing `fun show(activity: AppCompatActivity, resourceName: String, onConfirm: () -> Unit)`. Keep the destructive theme overlay, the `delete_resource_title` / `delete_resource_message` strings and the `isFinishing || isDestroyed` guard byte-for-byte. Make `MainActivity.showDeleteConfirmation` a one-line delegation that still calls `viewModel.deleteResource(resource)` from its `onConfirm`.

**Why:**

Strategic §6.2 resolved that the desktop must raise the same confirmation dialog as the main window rather than its own copy, because a divergence in the wording of a delete confirmation is the worst kind of divergence; the dialog is a private method today and no second host can reach it.

**Verification:**

- `Glob` - `ResourceDeleteConfirmation.kt` exists.
- `Grep` - `object ResourceDeleteConfirmation` matches once.
- `Grep` - `delete_resource_message` no longer appears in `MainActivity.kt`.
- `Grep` - `ResourceDeleteConfirmation.show(` present in `MainActivity.kt`.

**Status:** `[x]` done

---

### Step 02.2 - Add the accessibility label string

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one key `launcher_home_cell_actions` in all three locales with a single lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_home_cell_actions -En "Show actions" -Ru "Показать действия" -Uk "Показати дії"`. Never hand-edit `strings.xml`. Then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_home_cell_actions"` and fix anything it reports before continuing. Check the caption against `docs/COMMUNICATION_POLICY.md` §2 (message formula for a control label) and §6 (tone checklist).

**Why:**

Strategic §3.2 requires any new key to land in EN, RU and UK at once, and §6.3 resolved that the menu needs a second way in besides the long press, which needs a spoken name for the action.

**Verification:**

- `Grep` - `launcher_home_cell_actions` present in `values/strings.xml`, `values-ru/strings.xml` and `values-uk/strings.xml`.
- `check_strings_localized.ps1 -KeyPrefix "launcher_home_cell_actions"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.3 - Add the launcher-side resource action executor

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResourceActionManager.kt`
**Depends on:** Step 02.1, Phase 01

**Prompt for developer:**

> Create `class LauncherResourceActionManager(activity, loadResource, runCommand, deleteResource, shortcutPinManager, vrCinema)` translating a `ResourceMenuAction` plus a resource id into the work the desktop can do without the main window. It takes no `CoroutineScope`: its one suspending entry point is `suspend fun rowsFor(resourceId): List<LauncherAppMenuRow>`, and the scope belongs to the menu manager that calls it. Wire OPEN and LAUNCH_PLAYER through `runCommand` as `LauncherCellCommand.Resource(id, BROWSE)` and `Resource(id, SLIDESHOW)`; EDIT through `ResourceEditorActivity.createEditIntent`, guarded by the resource's `accessPin` exactly as `ResourcePasswordManager` guards it in the main window; COPY through the same copy intent `MainEventHandler` builds for `MainEvent.NavigateToAddResourceCopy`; ADD_TO_HOME_SCREEN through the injected `ResourceShortcutPinManager` with the same `ResourceIconComposer.compose` icon and the same `resource_shortcut_created` / `resource_shortcut_unsupported` toast as `MainActivity.pinResourceLaunchShortcut`; OPEN_IN_VR_CINEMA through `ResourceVrCinemaLaunchManager`; DELETE through `ResourceDeleteConfirmation.show` then `deleteResource`.
> Expose `fun supports(action: ResourceMenuAction): Boolean` returning false for every action this class does not yet execute, so the catalog can withhold it.

**Why:**

Strategic §5.1.2 requires an action provider that needs neither the main window nor the streams screen, and §4 records that part of the actions transfer unchanged because they are expressed as intents or as managers taking a plain `AppCompatActivity` while the rest need new wiring.

**Verification:**

- `Glob` - the file exists under `src/launcherEnabled/`, not `src/main/`.
- `Grep` - `class LauncherResourceActionManager` matches once.
- `Grep` - `fun supports(` present.
- `Grep -n "BuildConfig\.IS_"` - zero hits (CLAUDE.md Rule 14).
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x]` done

---

### Step 02.4 - Add the cell action menu manager

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherCellActionMenuManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Create `class LauncherCellActionMenuManager(scope, resourceRows, streamRows)` that renders a list of `LauncherAppMenuRow.Action` in a `ListPopupWindow` anchored to the cell, mirroring `LauncherAppActionMenuManager`: dismiss any open window first, set `isModal = true`, width `maxOf(anchor.width, launcher_shortcut_popup_width)`, adapter `LauncherAppShortcutAdapter`, and a `dismiss()` the host calls on its teardown edge. Expose `fun showForResource(anchor, resourceId)` and `fun showForStream(anchor, streamId)`; both return early when the row list comes back empty so a cell with nothing to offer keeps behaving like an un-long-pressable cell.

**Why:**

The owner's §3.3 ruling fixes the presentation as a `ListPopupWindow` matching the app quick-actions menu already on that gesture, so two long presses on neighbouring cells look the same, and §5.1.3 requires reusing the existing menu primitive rather than introducing a third way to show a list of actions.

**Verification:**

- `Grep` - `class LauncherCellActionMenuManager` matches once.
- `Grep` - `ListPopupWindow(` present.
- `Grep` - `LauncherAppShortcutAdapter(` present.
- `Grep` - `fun dismiss()` present.

**Status:** `[x]` done

---

### Step 02.5 - Dispatch the long press by command kind

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 02.3, Step 02.4

**Prompt for developer:**

> Replace `LauncherHomeActivity.showAppShortcuts` with a dispatcher that decodes the cell target once and branches on the command kind: `App` keeps calling `shortcutMenuManager.show`, `Resource` calls `cellActionMenuManager.showForResource`, every other kind returns `false`. Keep the existing edit-mode guard ahead of the branch. Add to `LauncherHomeViewModel` only what the desktop cannot reach from the Activity - loading a `MediaResource` by id and deleting one - and dismiss the new manager in `onStop` beside the existing `shortcutMenuManager.dismiss()`.

**Why:**

Strategic §5.1.1 states the long-press handler stops rejecting everything but the app kind and instead picks the action provider by command kind, and the owner's §3.3 ruling limits the gesture to cells the launcher itself created, which is what the `false` branch preserves for the `pin:` kind named in §2 Non-goals.

**Verification:**

- `Grep` - `is LauncherCellCommand.Resource ->` present in `LauncherHomeActivity.kt`.
- `Grep` - `cellActionMenuManager.dismiss()` present inside `onStop`.
- `Grep` - `editMode.value` still guards the dispatcher's entry.
- `Grep -n "Log\.d\("` - zero hits in both files.

**Status:** `[x]` done

---

### Step 02.6 - Register the accessibility action on every cell

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellViewBinder.kt`
**Depends on:** Step 02.2, Step 02.5

**Prompt for developer:**

> In `bindShortcut`, after the long-press listener is attached, register an `AccessibilityNodeInfoCompat.AccessibilityActionCompat` labelled `launcher_home_cell_actions` via `ViewCompat.replaceAccessibilityAction` that invokes the same `onCellLongPress` lambda. Register it before any command-kind is consulted so it reaches app cells too, and only when the lambda reports the press was consumed, so a cell with no menu does not announce an action that does nothing.

**Why:**

Strategic §6.3 resolved that the long press must not be the only way in and that the action lives in the shared per-cell handler so it covers app cells as well, because partial coverage would make neighbouring cells behave differently.

**Verification:**

- `Grep` - `replaceAccessibilityAction` present in `LauncherCellViewBinder.kt`.
- `Grep` - `launcher_home_cell_actions` referenced in that file.
- `Grep` - the registration sits inside `bindShortcut`, not inside a command-kind branch.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - **UNPROVEN**: no gradle was run in this session, by instruction. Nothing here may be read as a compile claim.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `MainActivity.kt` is no longer than it was before Step 02.1 - 1436 -> 1432 lines.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The dispatcher's `when` is the single place a new cell kind joins the gesture, and `LauncherCellActionMenuManager` already carries the stream entry point, so Phase 03 adds a branch and a row builder rather than a second handler.

---

## Rollback Plan

Revert the phase commit. No schema, no stored data and no layout changed; the only `src/main` edits are the extracted dialog and one string key.
