# Phase 02 — tv-key-routing

**Strategic spec:** [`../S0081_tv-remote-key-coverage.md`](../S0081_tv-remote-key-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, §6.1 research resolved
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Add default key bindings for TV remote buttons to `default_bindings.json`, then route those buttons through `KeyBindingManager` in `BrowseActivity` and `MainActivity` before the hardcoded fallback in `KeyboardShortcutHandler` — making them remappable by the user.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] §6.1 research (TV KEYCODE survey) is Resolved in the strategic spec.
- [ ] Working tree is clean or on a feature branch.
- [ ] Backup `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` to `temp/` (file is 914 lines — backup required by CLAUDE.md §Strict Rules).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/assets/input/default_bindings.json` | Modified | 858 + 6 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 930 |

---

## Steps

### Step 2.1 — Add TV remote key triggers to default_bindings.json

**Files:** `app_v2/src/main/assets/input/default_bindings.json`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/src/main/assets/input/default_bindings.json`, add the following `"keyboard"` trigger entries to the matching `command_id` blocks. Trigger format is `"key:<keycode>:<modifiers>"` where modifiers `0` means no modifier.
>
> - `"navigation.next_file"` → add `"key:166:0"` (`KEYCODE_CHANNEL_UP`)
> - `"navigation.previous_file"` → add `"key:167:0"` (`KEYCODE_CHANNEL_DOWN`)
> - `"sorting.delete"` → add `"key:183:0"` (`KEYCODE_PROG_RED`)
> - `"sorting.copy"` → add `"key:184:0"` (`KEYCODE_PROG_GREEN`)
> - `"sorting.move"` → add `"key:185:0"` (`KEYCODE_PROG_YELLOW`)
> - `"sorting.rename"` → add `"key:186:0"` (`KEYCODE_PROG_BLUE`)
> - `"system.toggle_info"` → add `"key:165:0"` (`KEYCODE_INFO`) — Info button present on IPTV/Smart TV remotes
>
> Append each new trigger string to the existing `"keyboard": [...]` array for that entry — do not replace existing values. If `"system.toggle_info"` entry does not yet exist in `default_bindings.json`, create a new entry with the same structure as neighboring entries.

**Verification:**

- `Grep` — `"key:166:0"` present in `default_bindings.json`.
- `Grep` — `"key:167:0"` present in `default_bindings.json`.
- `Grep` — `"key:183:0"` present in `default_bindings.json`.
- `Grep` — `"key:184:0"` present in `default_bindings.json`.
- `Grep` — `"key:185:0"` present in `default_bindings.json`.
- `Grep` — `"key:186:0"` present in `default_bindings.json`.
- `Grep` — `"key:165:0"` present in `default_bindings.json`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 7/7 PASS. Files: app_v2/src/main/assets/input/default_bindings.json (+7 trigger entries). Dev log recorded.

---

### Step 2.2 — Pre-check KeyBindingManager in BrowseActivity.dispatchKeyEvent

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> In `BrowseActivity`, inject `KeyBindingManager` via `@Inject lateinit var keyBindingManager: KeyBindingManager`.
>
> In `dispatchKeyEvent(event: KeyEvent)`, before the `super.dispatchKeyEvent(event)` call, add a binding lookup for `ACTION_DOWN` events only:
>
> ```kotlin
> if (event.action == KeyEvent.ACTION_DOWN) {
>     val commandId = keyBindingManager.resolveKeyAction(event.keyCode, event.metaState, InputSurface.BROWSER)
>     if (commandId != null && routeBrowserCommandId(commandId)) return true
> }
> ```
>
> Implement `private fun routeBrowserCommandId(commandId: String): Boolean` — dispatch the command using the existing `InputAction`-based routing already present in the activity (map `sorting.*` and `navigation.*` command IDs to their corresponding `InputAction` values, then call the existing handler). Return `true` if the command was dispatched, `false` if unknown.
>
> Existing hardcoded handling in `KeyboardShortcutHandler` remains untouched and acts as the default fallback when the user has no binding for a TV key.

**Verification:**

- `Grep` — `@Inject` and `KeyBindingManager` on adjacent lines in `BrowseActivity.kt`.
- `Grep` — `resolveKeyAction` called within `dispatchKeyEvent` in `BrowseActivity.kt`.
- `Grep` — `routeBrowserCommandId` declared in `BrowseActivity.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseActivity.kt` (Timber-only rule).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Files: BrowseActivity.kt (+7 LOC), KeyboardNavigationManager.kt (+9 LOC). Note: KeyboardNavigationManager.kt added to phase scope (not in original Files Touched) — internal fun only, no public API change. Dev log recorded.

---

### Step 2.3 — Pre-check KeyBindingManager in MainActivity.dispatchKeyEvent

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Apply the same pattern as Step 2.2 to `MainActivity`. Inject `KeyBindingManager`, add the `ACTION_DOWN` pre-check in `dispatchKeyEvent`, and implement `private fun routeMainCommandId(commandId: String): Boolean` dispatching `sorting.*` commands to the existing file-operation handlers in MainActivity.
>
> Use `InputSurface.MAIN` if it exists; otherwise fall back to `InputSurface.BROWSER` — both resolve the same trigger map since `KeyBindingManager.resolve()` currently ignores the surface parameter.

**Verification:**

- `Grep` — `@Inject` and `KeyBindingManager` on adjacent lines in `MainActivity.kt`.
- `Grep` — `resolveKeyAction` called within `dispatchKeyEvent` in `MainActivity.kt`.
- `Grep` — `routeMainCommandId` declared in `MainActivity.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `MainActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Files: MainActivity.kt (+10 LOC), KeyboardNavigationHandler.kt (+9 LOC). Note: KeyboardNavigationHandler.kt added to phase scope — internal fun only. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 2.* above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for all three modified files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

TV remote color buttons and channel keys now route through the binding system first in both Browse and Main surfaces. The user can remap them via the existing keybinding UI (CaptureDialogFragment captures the key press from the physical remote). Hardcoded fallback in `KeyboardShortcutHandler` remains intact.

---

## Rollback Plan

Revert the three file changes. `default_bindings.json` changes affect only newly installed or reset bindings — users with an existing DB are unaffected. Remove the `@Inject` field and the pre-check block from both activities.
