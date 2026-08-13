# Phase 07 - Deprecate legacy StandalonePlayerActivity

**Strategic spec:** [`../S0393_player-host-seam-foundation.md`](../S0393_player-host-seam-foundation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** 05, 06
**Blocks:** -

## Objective

Once every legacy-unique capability is ported (Phases 05-06 against `HARVEST.md`), mark the legacy host deprecated with a removal TODO. Do NOT delete yet (owner decision: harvest → deprecate → later delete).

## Approach

- Confirm `HARVEST.md` fully discharged - no capability lives only in `StandalonePlayerActivity`.
- Confirm external routing: the dispatcher (`StandalonePlayerDispatcherActivity`) routes to the specialized hosts; legacy is only a direct/fallback target.
- Add `@Deprecated` to `StandalonePlayerActivity` + a `TODO(S0393): remove once no entry point targets it` comment. Keep the manifest entry (still `exported`) until a follow-up removal ticket.

## Verification

- `Grep` - `@Deprecated` on `StandalonePlayerActivity` + the TODO present.
- `Grep` - no `Intent`/`setClass`/alias `targetActivity` routes to `StandalonePlayerActivity` except the retained fallback.
- `standard` build green.

## Phase Done Criteria

- [ ] Legacy host deprecated + TODO; harvest fully discharged; nothing lost.
