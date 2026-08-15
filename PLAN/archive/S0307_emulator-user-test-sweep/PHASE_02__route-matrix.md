# Phase 02 - Route Matrix

**Strategic spec:** [`../S0307_emulator-user-test-sweep.md`](../S0307_emulator-user-test-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Resolve each eligible ticket into an emulator verification route, external blocker, or not-testable classification.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/s0307/01_scope.md` exists.
- [ ] Working tree is clean or current changes are owned by this pipeline.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/s0307/02_ticket_inputs.jsonl` | New | ≤ 600 |
| `temp/s0307/02_route_matrix.md` | New | ≤ 800 |
| `temp/s0307/02_external_blockers.md` | New | ≤ 400 |

---

## Steps

### Step 02.1 - Resolve Ticket Inputs

**Files:** `temp/s0307/02_ticket_inputs.jsonl`
**Depends on:** start of phase

**Prompt for developer:**

> For every eligible ticket from Phase 01, resolve the current catalog record and strategic file path. Extract ticket id, slug, status, priority, file path and any `Last Audit` manual/on-device section when present.

**Verification:**

- `Glob` - `temp/s0307/02_ticket_inputs.jsonl` exists.
- `Grep` - `"status":"BlockNeedUserTest"` appears at least once.
- `Grep` - `"file":"PLAN/` appears at least once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/02_ticket_inputs.jsonl`. Eligible rows: 26.

---

### Step 02.2 - Classify Verification Routes

**Files:** `temp/s0307/02_route_matrix.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Classify each ticket as `direct-emulator`, `local-service`, `external-dependency`, `not-testable-by-fixtures`, or `review-needed`. Include expected observable signal, required fixture type, target flavor and whether status mutation is allowed after emulator evidence.

**Verification:**

- `Glob` - `temp/s0307/02_route_matrix.md` exists.
- `Grep` - `route_matrix_count=` appears exactly once.
- `Grep` - `direct-emulator` appears at least once or the file explains why no direct route exists.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/02_route_matrix.md`. direct=13, local-service=3, external=8, not-testable=1, review=1.

---

### Step 02.3 - Extract External Blockers

**Files:** `temp/s0307/02_external_blockers.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> List cloud, OAuth, third-party app, hardware-only and account-dependent tickets. Mark the exact missing dependency and the safe verdict policy. Do not request or store secrets.

**Verification:**

- `Glob` - `temp/s0307/02_external_blockers.md` exists.
- `Grep` - `secret_policy=no-secrets-in-evidence` appears exactly once.
- `Grep` - `external_blocker_count=` appears exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-30 - Verification 3/3 PASS. Artifact: `temp/s0307/02_external_blockers.md`. external/review/not-testable rows: 10.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `temp/s0307/02_route_matrix.md` exists.
- [x] Every eligible ticket from Phase 01 appears in exactly one route bucket or a count mismatch is recorded as a blocker.
- [x] No target ticket status is mutated in this phase.

---

## Handoff Notes to Next Phase

Phase 03 consumes route buckets and prepares fixture/build inputs only for routes that can run on the emulator.

---

## Rollback Plan

Delete `temp/s0307/02_*` artifacts and rerun Phase 02. No source or catalog status changes are made in this phase.
