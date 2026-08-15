# Phase A - Divergence Map

**Strategic spec:** [`../S0392_player-family-parity-research.md`](../S0392_player-family-parity-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Deliverable:** [`MATRIX.md`](MATRIX.md)

## Objective

A verifiable matrix: every in-app player capability × each standalone host × status (present / partial / absent / differs) × blocker class.

## Method

1. Inventory the in-app etalon (command panel `PlayerCommand` enum + every type-specific action + navigation + gestures + input), flagging per action whether it is binding-coupled or generic.
2. Inventory each standalone host (PhotoVideo, Audio, Document, Text, legacy StandalonePlayer, dispatcher): what panel buttons + actions exist, real-vs-stub, `PlayerHostCapabilities` overrides.
3. Cross them into `MATRIX.md`. Blocker classes: (1) helper binding-coupled, (2) no resource/playlist context, (3) trimmed layout, (4) flavor/type gate, (5) intentional non-goal.
4. Mark each gap cheap-now (generic engine exists, only glue needed) vs needs-seam.

## Verification

- `MATRIX.md` exists and covers all hosts × all in-app capability groups.
- Status values are one of present/partial/absent/differs, each with a blocker class.
- Already-shipped parity (S0389 paging + Open-in-FMS, S0390 Group A) reflected as present.

## Phase Done Criteria

- [ ] `MATRIX.md` complete and cross-checked against the two research passes.
