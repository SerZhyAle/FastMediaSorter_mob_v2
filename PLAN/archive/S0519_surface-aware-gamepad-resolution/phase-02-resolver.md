# Phase 02 - Resolver: surface-aware KeyBindingManager

Goal: let one trigger carry multiple commandIds and pick the one matching the requested surface, while keeping single-candidate behaviour byte-identical.

## Steps

- [ ] In `core/input/KeyBindingManager.kt`, replace `triggerToCommand: Map<InputTrigger, String>` with `triggerToCommands: Map<InputTrigger, List<String>>`.
  - Build it in the `observeResolvedBindings().onEach` block via `bindings.groupBy(keySelector = { it.trigger }, valueTransform = { it.commandId })`.
  - Keep `commandToTriggers` as is.
  - Verification: field type is `Map<InputTrigger, List<String>>`.

- [ ] Rewrite `resolve(trigger, surface)`:
  - `val candidates = triggerToCommands[trigger] ?: return null`
  - `if (candidates.size == 1) return candidates.first()` (fast path, back-compat).
  - else `return candidates.firstOrNull { surfaceOf(it) == surface } ?: candidates.first()`.
  - Verification: single-candidate triggers return the same commandId as before.

- [ ] Add private `surfaceOf(commandId: String): InputSurface` (non-null):
  - `browser.` prefix -> `InputSurface.BROWSER`
  - `vr.` prefix -> `InputSurface.VR`
  - else -> `InputSurface.PLAYER`
  - Decision: non-null return avoids a dead `== null` fallback branch (Rule 20); the `?: candidates.first()` net stays reachable only for a genuine same-surface conflict (two player commands on one trigger).
  - Verification: `surfaceOf("browser.select") == InputSurface.BROWSER`, `surfaceOf("playback.pause_play") == InputSurface.PLAYER`.

- [ ] Keep `resolveKeyAction` unchanged (delegates to `resolve`).

## Notes

- `commandToTriggers` consumers (conflict detection) are unaffected - that map is still commandId -> triggers.
- The multi-candidate branch only triggers when defaults bind the same physical trigger to commands of different surfaces (the browser/player collision). All existing single-surface triggers stay on the fast path.
