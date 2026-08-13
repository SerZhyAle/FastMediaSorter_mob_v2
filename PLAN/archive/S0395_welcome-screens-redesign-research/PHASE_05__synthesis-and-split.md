# Phase 05 - Synthesis & Dev-Ticket Split

**Strategic spec:** [`../S0395_welcome-screens-redesign-research.md`](../S0395_welcome-screens-redesign-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 2 / 2
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Produce `SYNTHESIS.md` (pros/cons, recommended onboarding structure, deviations from the owner draft) and `research/12__dev-ticket-split.md` (strategic §6.12) - the owner-review deliverables of the whole ticket.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done (artifacts 01-11 all exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0395_welcome-screens-redesign-research/SYNTHESIS.md` | New | ≤ 500 |
| `PLAN/S0395_welcome-screens-redesign-research/research/12__dev-ticket-split.md` | New | ≤ 300 |

---

## Steps

### Step 05.1 - Author SYNTHESIS.md

**Files:** `PLAN/S0395_welcome-screens-redesign-research/SYNTHESIS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Read all 11 artifacts in `research/`. Author `SYNTHESIS.md` in Russian (owner-facing; `..` not `...`, ё mandatory) with sections: `## Плюсы` and `## Минусы` of the target scheme (onboarding friction, first-launch downloads, page order - grounded in artifact findings, not opinion); `## Рекомендованная структура` - final page list in order, each page with its decisions, defaults, skip behavior, and availability rule; `## Матрица страница × flavor` (from artifact 09); `## Отклонения от черновой схемы владельца` - every deviation from the strategic §1 draft (page order changes from artifact 05, granularity changes from artifact 04, anything dropped/merged) with a one-line reason and artifact reference; `## Открытые решения для владельца` - only genuinely owner-level calls research cannot settle, each with a recommended option. Every claim must cite its artifact (`research/NN__*.md`).

**Verification:**

- `Glob` - `PLAN/S0395_welcome-screens-redesign-research/SYNTHESIS.md` exists.
- `Grep` - `## Плюсы` present and `## Минусы` present.
- `Grep` - `## Рекомендованная структура` present.
- `Grep` - `## Отклонения` present.
- `Grep` - `research/0` present (artifact citations exist).

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 4/4 PASS (36 hits incl. per-claim artifact citations). 6 pros / 6 cons, full recommended structure with cross-cutting properties, flavor matrix, 7 deviations from owner draft, 3 owner decisions, ~30 new string keys estimated.

---

### Step 05.2 - Author dev-ticket split proposal

**Files:** `PLAN/S0395_welcome-screens-redesign-research/research/12__dev-ticket-split.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> From `SYNTHESIS.md`'s recommended structure, author the split proposal (uniform skeleton): candidate dev tickets each with a one-line scope, the pages/concerns it covers, its dependencies (S0391 implementation for the network page; S0386 verification for download-triggering toggles; framework/skeleton ticket before page tickets), what is implementable immediately vs blocked, and the recommended implementation order. Slice so that no ticket waits on another without need (the S0391-dependent page must not block the rest). Each candidate ticket gets a proposed kebab-case slug, an estimated new-strings scope (EN/RU/UK lockstep keys - strategic §3.2 localization volume) and a note that its user-visible copy passes `docs/COMMUNICATION_POLICY.md` §6 before integration. Conclude with the creation order and the `BlockByOtherTask` assignments to declare at creation time.

**Verification:**

- `Glob` - `research/12__dev-ticket-split.md` exists under the ticket folder.
- `Grep` - `S0391` present and `S0386` present.
- `Grep` - `BlockByOtherTask` present.
- `Grep` - `## Conclusion` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 4/4 PASS. 8 tickets + 1 optional: A availability-contract, B download-runner, C skeleton (keystone), D profile page, E functionality page (gated by S0386 Verified + F for Play-standard OCR), F Play-compliant .so delivery (S0386 lineage), G permissions page, H network page (BlockByOtherTask S0391), I upgrade pointer. ~30 string keys lockstep.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] No source/config file modified - S0395 changes confined to the ticket folder + dev/CHANGELOG.
- [x] Dev log entry added for both deliverables via post-change.ps1 (Doc) - SYNTHESIS.md and 12 recorded 2026-06-10.

---

## Handoff Notes to Next Phase

SYNTHESIS.md and the split proposal are final content; Phase 06 only links artifacts into the strategic spec and closes bookkeeping. Owner review happens after Phase 06.

---

## Rollback Plan

Delete the two files - no code or data surface touched.
