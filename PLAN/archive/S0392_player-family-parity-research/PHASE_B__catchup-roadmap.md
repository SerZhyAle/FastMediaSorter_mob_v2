# Phase B - Catch-up Roadmap

**Strategic spec:** [`../S0392_player-family-parity-research.md`](../S0392_player-family-parity-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** A
**Deliverable:** [`ROADMAP.md`](ROADMAP.md)

## Objective

Turn the divergence matrix into a prioritized catch-up roadmap: spawned tickets, the binding-agnostic host-seam as the enabling fundamental, each item ordered by value × cost.

## Method

1. Group matrix gaps into ticket-sized units (one seam / one action group = one ticket).
2. Put the host-seam fundamental first (root view + current file + reload hook + overlay mount points) - it unblocks the cheap protraction of every binding-coupled action.
3. Order remaining items by user value × cost; fold in already-deferred work (draw-overlay = S0390 phase 06; waves C: OCR/Lens/print/translate/save-frame/sleep-timer/lyrics).
4. For each ticket: scope, which hosts, blocker it clears, cheap-now vs needs-seam.

## Verification

- `ROADMAP.md` exists with an ordered ticket list, host-seam first, each item value/cost-classified and traced to matrix rows.

## Phase Done Criteria

- [ ] `ROADMAP.md` complete; owner-reviewable.
- [ ] Strategic §6 research items marked Resolved from the matrix + roadmap.
