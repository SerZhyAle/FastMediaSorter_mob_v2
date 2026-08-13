# Phase 04 — Keybinding Registration

**Strategic spec:** [`../S0050_player-black-screen-mode.md`](../S0050_player-black-screen-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-02
**Completed:** 2026-05-02

---

## Objective

Register `player.black_screen` as a named command in the keybinding system: add it to `default_bindings.json` with a default shortcut, add display-label strings in all three locales, and wire it to `toggleBlackScreenOverlay()` in the `PlayerActivity` key-dispatch path.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done (`toggleBlackScreenOverlay()` exists in `PlayerActivity`).
- [ ] Read `app_v2/src/main/assets/input/default_bindings.json` to understand the entry format (`commandId`, `keyboard` trigger array, `gamepad`, `mouse` arrays).
- [ ] Identify where existing command IDs (e.g. `"player.next"`, `"player.previous"`) are resolved in `PlayerActivity` or its key handler — this is where the new dispatch call goes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/assets/input/default_bindings.json` | Modified | existing JSON |
| `app_v2/src/main/res/values/strings.xml` | Modified | existing file |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | existing file |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | existing file |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1000 |

---

## Steps

### Step 4.1 — Add default keybinding entry

**Files:** `app_v2/src/main/assets/input/default_bindings.json`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new entry to `default_bindings.json` for command id `"player.black_screen"`. Assign a simple, unambiguous default keyboard shortcut that does not conflict with existing player bindings — inspect the file to pick a free key (e.g. `B` with no modifiers, or `Ctrl+B`). Follow the exact JSON structure of the nearest existing player command entry (`commandId`, `keyboard` array with `keyCode` and `modifiers`, empty `gamepad` and `mouse` arrays).

**Verification:**

- `Grep` — `player.black_screen` in `default_bindings.json`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 1/1 PASS. Files: default_bindings.json (+12 lines, key:30:0 = B, flavor_gate audio_required). Dev log recorded.

---

### Step 4.2 — Add command label strings (all three locales)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 4.1

**Prompt for developer:**

> Add string key `cmd_black_screen_label` to all three locale files:
> - EN: `"Black Screen"`
> - RU: `"Чёрный экран"`
> - UK: `"Чорний екран"`
>
> This key is used by `KeybindingRemapActivity` (or the binding list adapter) to display the human-readable command name. Check whether the keybinding UI reads labels from strings.xml directly (by resource name derived from commandId) or from a separate mapping — adapt accordingly.

**Verification:**

- `Grep` — `cmd_black_screen_label` in `values/strings.xml`.
- `Grep` — `cmd_black_screen_label` in `values-ru/strings.xml`.
- `Grep` — `cmd_black_screen_label` in `values-uk/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS. Files: strings.xml×3 (+1 each). Dev log recorded.

---

### Step 4.3 — Dispatch command in PlayerActivity key handler

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 4.2

**Prompt for developer:**

> In the section of `PlayerActivity` (or its delegate manager) where resolved command IDs from `KeyBindingManager.resolve()` are dispatched (the `when (commandId)` or `if (commandId == ...)` block), add a branch:
> ```kotlin
> "player.black_screen" -> { toggleBlackScreenOverlay(); true }
> ```
> Place it alongside other player-specific commands (`player.next`, `player.previous`, etc.).

**Verification:**

- `Grep` — `player.black_screen` in `CommandId.kt` (constant declaration).
- `Grep` — `CommandId.BLACK_SCREEN` in `PlayerKeyboardHandler.kt` (handleCommand dispatch branch).
- `Grep` — `onToggleBlackScreen` in `PlayerKeyboardCallbackImpl.kt` (implementation calls activity.toggleBlackScreenOverlay()).

> _Spec patch: dispatch is in `PlayerKeyboardHandler.kt` (delegate manager), implemented via `PlayerKeyboardCallbackImpl.kt`. `PlayerActivity.kt` owns `toggleBlackScreenOverlay()` (added in Phase 03)._

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification 3/3 PASS (patched predicates). Files: CommandId.kt (+1 const), PlayerKeyboardHandler.kt (+1 callback method, +1 handleCommand branch), PlayerKeyboardCallbackImpl.kt (+1 override). Dev log recorded.

---

### Step 4.4 — Verify keybinding appears in remap UI

**Files:** (read-only verification, no edits)
**Depends on:** Step 4.3

**Prompt for developer:**

> Confirm that `KeybindingRemapViewModel` (or `KeybindingListAdapter`) will surface `player.black_screen` in the remap list. Typically this requires no code change if the adapter iterates all entries from `InputBindingRepository.observeResolvedBindings()`. If the adapter uses a hard-coded allowlist of command IDs, add `"player.black_screen"` to that list.

**Verification:**

- `Grep` — if a hard-coded command list exists, `player.black_screen` is in it. If no hard-coded list exists, this step is satisfied by the presence of the `default_bindings.json` entry from Step 4.1.

**Status:** `[x] done`

**Step Log:**

- 2026-05-02 — Verification PASS (no hard-coded allowlist; remap adapter iterates all `observeResolvedBindings()` entries from default_bindings.json). No code change needed.

---

## Phase Done Criteria

- [x] Every Step 4.* above is `[x] done`.
- [x] Project compiles — run `/build`. (auto-build — PASS)
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `player.black_screen` is fully wired: default binding in JSON, label strings in all locales, dispatched to `toggleBlackScreenOverlay()`.
- Feature is now complete from a code perspective; Phase 05 handles documentation and catalog.

---

## Rollback Plan

Revert phase commit(s). JSON and strings changes are purely additive. The dispatch branch is one line in an existing `when` block.
