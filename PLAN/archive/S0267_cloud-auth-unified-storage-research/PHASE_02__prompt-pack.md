# Phase 02 - Prompt Pack

**Strategic spec:** [`../S0267_cloud-auth-unified-storage-research.md`](../S0267_cloud-auth-unified-storage-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-22
**Completed:** 2026-05-22

---

## Objective

Produce the exact `/spec` command pack and owner-input bundle for each child ticket so future strategic drafting is deterministic and does not need another discovery pass.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `CHILD_SPECS.md` exists and is complete.
- [ ] All child slugs are fixed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0267_cloud-auth-unified-storage-research/PROMPTS.md` | New | ≤ 400 |

---

## Steps

### Step 02.1 - Add exact `/spec` commands for every child ticket

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/PROMPTS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `PROMPTS.md`. Add a `## Commands` section with one fenced block per child ticket. Use the exact command form `/spec ad-hoc <slug>`. Include all 7 slugs from Phase 01, including the optional `cloud-auth-auditor-extension`.

**Verification:**

- `Glob` - `PLAN/S0267_cloud-auth-unified-storage-research/PROMPTS.md` exists.
- `Grep` - `/spec ad-hoc cloud-auth-storage-foundation` present.
- `Grep` - `/spec ad-hoc cloud-auth-auditor-extension` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS (expected: 7 commands, each anchor present | actual: 7 `/spec ad-hoc` lines; foundation=1; auditor=1). Files: PLAN/S0267_cloud-auth-unified-storage-research/PROMPTS.md (new).

---

### Step 02.2 - Add owner-input packets for required child specs

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/PROMPTS.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a `## Owner-input packets` section. For each required child slug, provide a flat bullet bundle that future `/spec` drafting can copy into strategic §0:
>
> - `Requested mode`
> - `Goal / expected outcome`
> - `Local anchor`
> - `Scope boundaries / forbidden areas`
> - `Done / success signal`
> - `Autonomy rule`
> - `UI decisions / delegation`
>
> For the two UI-facing children (`settings-authorizations-unified-sources`, `settings-authorizations-unified-ui`), fill the `UI decisions / delegation` line with an explicit reminder that `/ui-clarify` must run before implementation.

**Verification:**

- `Grep` - `## Owner-input packets` present.
- `Grep` - `settings-authorizations-unified-ui` present.
- `Grep` - `/ui-clarify` present at least twice.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS (expected: section present + UI-slug present + `/ui-clarify` ≥2 | actual: 1, 2, 2). Files: PROMPTS.md (+98 lines, 6 packets covering all required slugs; auditor-extension intentionally omitted as post-release).

---

### Step 02.3 - Add validation expectations per child ticket

**Files:** `PLAN/S0267_cloud-auth-unified-storage-research/PROMPTS.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a final `## Validation expectations` section mapping each child slug to its minimum closure class:
>
> - foundation and provider tickets -> Kotlin + Room migration + target build
> - unified-sources ticket -> Kotlin + compile + affected tests
> - unified-ui ticket -> Xml + Kotlin + target build + `/ui-clarify`
> - auditor extension -> Kotlin + compile + audit-focused tests
>
> State the expected target variant as `standardDebug` for all cloud-capable children.

**Verification:**

- `Grep` - `## Validation expectations` present.
- `Grep` - `standardDebug` present at least 4 times.
- `Grep` - `Room migration` present at least once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-22 - Verification 3/3 PASS (expected: section + standardDebug≥4 + Room migration≥1 | actual: 1, 7, 5).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `PROMPTS.md` contains 7 `/spec ad-hoc` command lines (expected: 7 | actual: 7).
- [x] `Grep` for `TODO(phase-02)` returns zero hits in `PROMPTS.md` (the single match in this phase file is the criterion's self-reference).
- [x] Dev log entry added for `PROMPTS.md` via `scripts/post-change.ps1` (3 entries: Phase 02.1, 02.2, 02.3).

---

## Handoff Notes to Next Phase

Phase 03 consumes `CHILD_SPECS.md` and `PROMPTS.md` to build the execution order, parallelism rules, and stop-go checkpoints between waves.

---

## Rollback Plan

Revert this phase commit. This phase adds planning docs only and does not touch production code or spec-catalog records.
