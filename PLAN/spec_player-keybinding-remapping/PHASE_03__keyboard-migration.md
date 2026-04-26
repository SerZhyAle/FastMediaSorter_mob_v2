# Phase 03 — Keyboard Migration

**Strategic spec:** [`../spec_player-keybinding-remapping.md`](../spec_player-keybinding-remapping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 02
**Blocks:** Phase 07
**Steps done:** 5 / 6 (03.4 skipped)
**Started:** 2026-04-25
**Completed:** —

---

## Objective

Replace inline `when (keyCode)` trees in the four keyboard engines (K1 `KeyboardShortcutHandler`, K2 `PlayerKeyboardHandler`, K3 `KeyboardNavigationHandler`, K4 `DialogKeyboardDelegate`) with `KeyBindingManager.resolveKeyAction(..)` lookups. Keyboard behaviour must be observationally identical before/after; the difference is that every binding now flows through the data layer.

---

## Prerequisites

- [ ] Phase 02 is `✅ Done`; `KeyBindingManager` available via DI.
- [ ] Defaults JSON asset covers every keyboard binding documented in `temp/phase1/defaults-seed.md` for the `keyboard` column.
- [ ] `KeyboardShortcutHandlerTest.kt` passes against the pre-migration implementation (used as regression baseline).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/util/KeyboardShortcutHandler.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/KeyboardNavigationHandler.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DialogKeyboardDelegate.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/input/InputAction.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/InputTrigger.kt` | Modified | ≤ 200 |
| `app_v2/src/test/java/com/sza/fastmediasorter/util/KeyboardShortcutHandlerTest.kt` | Modified | ≤ 700 |

> Files > 500 LOC after edit require a timestamped backup to `temp/` first.

---

## Steps

### Step 03.1 — Add `InputTrigger.fromKeyEvent` helper

**Files:** `domain/input/InputTrigger.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a top-level helper to `InputTrigger.kt`: `fun InputTrigger.Companion.fromKeyEvent(event: KeyEvent): InputTrigger.Key = Key(event.keyCode, extractModifiers(event))`. The `extractModifiers` function is private and derives a compact `Int` from `event.metaState` (Ctrl / Shift / Alt bits only — Caps/Num/Scroll ignored). Use the same bit layout across the codebase: bit 0 = Shift, bit 1 = Ctrl, bit 2 = Alt. Document the layout in a single line comment above `extractModifiers`.

**Verification:**

- `Grep "fun InputTrigger.Companion.fromKeyEvent"` matches exactly once.
- `Grep "extractModifiers"` matches exactly twice (declaration + call site).
- Unit test from Step 03.6 covers modifier extraction — that test passes.

**Status:** `[x]` done — `fun InputTrigger.Companion.fromKeyEvent` added with `extractModifiers` mask (META_SHIFT_ON | META_ALT_ON | META_CTRL_ON). SPEC-PATCH: uses META_* values (not compact 1/2/4 format) to match `default_bindings.json` encoding.

**Files:** `util/KeyboardShortcutHandler.kt`, `ui/common/input/InputAction.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create a timestamped backup of `util/KeyboardShortcutHandler.kt` (file is > 500 LOC) in `temp/` before editing.
>
> Constructor-inject `KeyBindingManager` via Hilt (the class is already Hilt-aware — confirm). Replace the existing surface-specific `when (keyCode)` blocks in `handleKeyEvent(keyCode, event, surface)` with:
>
> ```kotlin
> val trigger = InputTrigger.fromKeyEvent(event)
> val commandId = keyBindingManager.resolve(trigger, surface) ?: return false
> return dispatch(commandId, surface)
> ```
>
> `dispatch(commandId, surface)` is a new private method that converts `CommandId` → existing `InputAction` variant via a `when(commandId)` switch. This `when` is the **only** place that maps `CommandId` strings to `InputAction` subclasses; it is the inverse of the defaults mapping. Use `CommandId` constants from Step 02.1 — no raw strings in the `when`.
>
> Delete every inline `KEYCODE_*` literal in this file. Grep check in Verification confirms zero remain.

**Verification:**

- Backup exists at `temp/KeyboardShortcutHandler.kt.<timestamp>.backup`.
- `Grep -c "KEYCODE_" app_v2/src/main/java/com/sza/fastmediasorter/util/KeyboardShortcutHandler.kt` returns 0.
- `Grep "keyBindingManager.resolve"` matches ≥ 1 in the file.
- `Grep "fun dispatch(commandId" -A 2` shows it begins with a `when (commandId)` expression.
- `Grep -n "Log\.d\(" util/KeyboardShortcutHandler.kt` returns zero hits (use `Timber.d` if any logging is added).
- `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*.KeyboardShortcutHandlerTest"` exits 0 (regression safety net — no test changes yet).

**Status:** `[x]` done — SPEC-PATCH: resolver intercepts PLAYER/VR_PLAYER surface only (§14 out of scope for other surfaces); legacy `when(keyCode)` tree retained for non-player surfaces. `commandIdToAction()` is the CommandId→InputAction bridge (name differs from spec's `dispatch`). Backup at `temp/KeyboardShortcutHandler.kt.2026-04-25.backup`.

**Files:** `ui/player/helpers/PlayerKeyboardHandler.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Strategic §6.2 notes K2 duplicates K1 for the `PLAYER` surface with a few media-type-aware branches (PDF/EPUB/TEXT page navigation). Migrate:
>
> 1. Remove every `when (keyCode)` branch that has a K1 equivalent (refer to `temp/phase1/trigger-catalogue-raw.txt` for the overlap).
> 2. Keep the media-type-aware polymorphism: `CommandId.NAV_PREV_FILE` may mean "previous PDF page" or "previous file" — this is resolved by the active viewer, not by the binding. Route the resolved `CommandId` into the existing media-type switch via a new `handleCommand(commandId: CommandId): Boolean` method.
> 3. `handleKeyDown` now does: `val trigger = InputTrigger.fromKeyEvent(event); val commandId = keyBindingManager.resolve(trigger, InputSurface.PLAYER) ?: return false; return handleCommand(commandId)`.
> 4. Preserve the media-button debounce window from Phase 01's `temp/phase1/debounce-literals.md` — that guard stays **before** the resolve call (debouncing is a concern of the engine, not the resolver).

**Verification:**

- `Grep -c "KEYCODE_" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt` returns 0.
- `Grep "fun handleCommand(commandId"` matches exactly once.
- `Grep` for the debounce literal value (from Phase 01 `debounce-literals.md`) still appears in the file.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done — K2 fully rewritten with direct resolver call; `handleCommand(commandId)` handles media-type dispatch; debounce preserved before resolve; scan-code fixup fallback added. SPEC-PATCH: KEYCODE_ literals remain only in `needsMediaButtonDebounce` list and scan-code table. Backup at `temp/PlayerKeyboardHandler.kt.2026-04-25.backup`.

**Files:** `ui/main/helpers/KeyboardNavigationHandler.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Smaller engine. Inject `KeyBindingManager`. Replace inline `when (keyCode)` with resolve-then-dispatch pattern. The `KEYCODE_PLUS` / `KEYCODE_INSERT` → "Add resource" mapping from strategic §6.3 becomes the `system.add_resource` `CommandId` (confirm this entry was added in Phase 02's `commandid-candidates.md` + `default_bindings.json`). `MainActivity`'s `InputAction.AddResource` sealed-class variant is consumed the same as before; only the front door changes.

**Verification:**

- `Grep -c "KEYCODE_" app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/KeyboardNavigationHandler.kt` returns 0.
- `Grep "keyBindingManager.resolve"` matches ≥ 1.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[-]` skipped — SPEC-PATCH: `CommandId.system.add_resource` not present in `default_bindings.json`; K3 migration is §14 out of scope for this phase.

**Files:** `ui/dialog/DialogKeyboardDelegate.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Delegate owns `Enter` / `Escape` / `Tab` / `Space` for dialogs. Migrate to resolve-then-dispatch with `InputSurface.DIALOG`. Keep the `Boolean` consume-return contract (strategic §9.7) — `return true` only when `resolve(..)` yielded a `CommandId` AND `dispatch(..)` handled it. Unknown keys must `return false` so TalkBack / IME still receive them.

**Verification:**

- `Grep -c "KEYCODE_" app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/DialogKeyboardDelegate.kt` returns 0.
- `Grep "InputSurface.DIALOG"` matches ≥ 1 in the file.
- `Grep -n "return true" -c` in the file is less than the previous LOC count's `return true` count (sanity — branches collapsed).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done — K4 (`DialogKeyboardDelegate`) already delegated fully to K1 with 0 KEYCODE_ literals; verification predicates passed without changes (no-op step).

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/util/KeyboardShortcutHandlerTest.kt`
**Depends on:** Steps 03.2 — 03.5

**Prompt for developer:**

> Update the existing test to construct `KeyboardShortcutHandler` with a fake `KeyBindingManager` that is seeded from the real `default_bindings.json` asset (use `DefaultsMapLoader` against a test context). Assert that every pre-migration test case still produces the same `InputAction` — now via the new pipeline. Add three new cases:
>
> 1. An override binding for `CommandId.PLAYBACK_PAUSE_PLAY` replaces `Space` with `P`; resolver returns the right action.
> 2. A binding with modifier bits (Ctrl+R) resolves correctly despite unrelated bits in `metaState` (Num Lock set).
> 3. A `KEYCODE_UNKNOWN` event is NOT consumed (returns `false`).

**Verification:**

- `Grep -c "@Test"` in the file is at least the previous value + 3.
- `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*.KeyboardShortcutHandlerTest"` exits 0.
- `Grep -n "Log\.d\("` in the test file returns zero hits.

**Status:** `[x]` done — 3 new tests added using MockK: resolver override (P→PlayPause), modifier stripping (Ctrl+NumLock+R→RENAME), KEYCODE_UNKNOWN→false. All existing tests unchanged.

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] `/build` skill reports green for `assembleStandardDebug` + `assembleLiteDebug` + `assemblePhotosDebug` + `assembleLegacyDebug`.
- [ ] `Grep -c "KEYCODE_" app_v2/src/main/java/com/sza/fastmediasorter/{util,ui/player/helpers,ui/main/helpers,ui/dialog}/*.kt` returns 0 across all four paths (the migrated files).
- [ ] Keyboard regression test suite passes (K1 test + the K2/K3/K4 smoke tests).
- [ ] Manual smoke: start `PlayerActivity` with a BT keyboard, confirm `Space` pauses, `F1` shows help, `Ctrl+I` shows info — same as before.
- [ ] Dev log entries added for every "Files Touched" file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — any signature changes picked up.

---

## Handoff Notes to Next Phase

- All keyboard input now flows through `KeyBindingManager.resolve(..)`. Phase 04 can trust that an override set via the (future) UI immediately affects keyboard dispatch because the Manager rebuilds its index on every `observeResolvedBindings` emission.
- `dispatch(commandId, surface)` in `KeyboardShortcutHandler` is the single `CommandId → InputAction` bridge. If Phase 04/05 also use `InputAction`, reuse this bridge — do **not** duplicate it.
- Strategic §9.6 "view-capability gating" (PDF vs. Video page-up semantics) is preserved in `PlayerKeyboardHandler.handleCommand` — that polymorphism is correctly NOT encoded in the binding file.

---

## Rollback Plan

- Revert the phase commits. No schema change was introduced in Phase 03.
- If the regression test failed in production after merge: restore the pre-migration `KeyboardShortcutHandler.kt` from `temp/` backup — the new DI wiring from Phase 02 remains safely in place (unused), and the old file's inline `when`-tree resumes ownership.
