# Phase 02 - Functional alignment (parity gap-check)

**Strategic spec:** [`../S0380_split-standalone-player.md`](../S0380_split-standalone-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none (Phase 01 skipped - foundation already in code)
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-06-07
**Completed:** 2026-06-07

---

> **Reduced scope (2026-06-07, owner-directed "minimal critical parity").** The parity audit (INDEX → Parity Audit) found the shared foundation already in place and **no critical-for-reuse gaps** blocking the split. Broad per-file parity (image editing, GIF ops, OCR, Google Lens, save-frame, lyrics, print, draw overlay, text editor, image translation, audio cover/metadata, VR launch, Cast) is **out of S0380 scope** and deferred to a separate ticket. This phase is therefore a verification + deferral record, not a feature-implementation phase.

---

## Objective

Confirm the split can proceed without functional regression: the standalone player's current per-file behavior already rides the shared contract, and the deferred parity items are recorded for a follow-up ticket.

---

## Prerequisites

- [ ] Parity audit recorded in INDEX (done 2026-06-07).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| [`INDEX.md`](INDEX.md) (Parity Audit / deferral note) | Modified (doc) | - |

> No source files. This phase records the deferral and confirms the gap list; it does not implement broad parity.

---

## Steps

### Step 02.1 - Confirm gap-check and record deferral

**Files:** [`INDEX.md`](INDEX.md)
**Depends on:** - start of phase

**Prompt for developer:**

> Verify the INDEX Parity Audit accurately lists (a) the shared foundation already in place, (b) the internal-only per-file features absent from standalone, and (c) that none of them block the split. Record that the broad per-file parity set is deferred to a separate follow-up ticket (allocate the id with `next-id.ps1` when the owner wants it opened). No source code is modified in this phase.

**Verification:**

- `Grep` - INDEX contains a `Parity Audit` section listing internal-only features and the "Critical-for-reuse gaps blocking the split: NONE" conclusion.
- `Grep` - INDEX records the deferral of broad per-file parity.

**Status:** `[x] done`

**Step Log:**

- 2026-06-07 - Verification 2/2 PASS. INDEX "Parity Audit" section lists shared foundation, internal-only per-file features, and "Critical-for-reuse gaps blocking the split: NONE"; deferral of broad per-file parity to a future ticket recorded. No source change (verification-only phase). Build state unaffected.

---

## Phase Done Criteria

- [ ] Step 02.1 is `[x] done`.
- [ ] No source change introduced by this phase (verification-only).
- [ ] Deferred parity follow-up captured for a future ticket.

---

## Handoff Notes to Next Phase

The split (Phases 03-05) reuses the existing `PlayerHostCapabilities` + standalone helpers. Specialized activities must preserve current standalone per-file behavior; they need not add the deferred internal-only features.

---

## Rollback Plan

Documentation-only phase - nothing to roll back.
