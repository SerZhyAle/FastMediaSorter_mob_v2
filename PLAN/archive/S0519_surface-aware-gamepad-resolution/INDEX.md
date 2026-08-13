# S0519 - Tactical Plan: Surface-aware gamepad resolution

**Strategic spec:** [../S0519_surface-aware-gamepad-resolution.md](../S0519_surface-aware-gamepad-resolution.md)
**Decision:** Variant C - namespace browser actions via `browser.*` commandId, resolve by surface inferred from command group. No Room migration.

## Phases

- [Phase 01 - Domain: browser CommandIds + group](phase-01-domain.md)
- [Phase 02 - Resolver: surface-aware KeyBindingManager](phase-02-resolver.md)
- [Phase 03 - Gamepad routing: browser through resolver](phase-03-gamepad-routing.md)
- [Phase 04 - Defaults + trilingual strings](phase-04-defaults-strings.md)
- [Phase 05 - Test inversion + verification tags](phase-05-test-tags.md)

## Phase ordering rationale

- 01 first: new commandId/group constants are referenced by every later phase; `when`-over-enum branches (ResetGroupUseCase, commandGroupOf) must compile.
- 02 next: resolver must hold multiple candidates per trigger before browser routing can disambiguate by surface.
- 03 depends on 01 (browser commandIds) + 02 (surface-aware resolve).
- 04 supplies the default browser gamepad bindings the resolver reads, plus user-facing labels.
- 05 inverts the unit test that asserted the old literal-tree behaviour and lands the device-test probes.

## Invariants

- `resolve(trigger, surface)` with a single candidate returns it unchanged - no regression for existing player/keyboard/mouse paths.
- `device` column stays `gamepad`; no schema/PK change.
- Browser commandIds are gamepad-default only; browser keyboard keeps using shared commandIds via `routeBrowserCommandId`.
