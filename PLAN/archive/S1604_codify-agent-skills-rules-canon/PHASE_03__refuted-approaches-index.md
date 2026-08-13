# Phase 03 - Index of measured dead ends

**Strategic spec:** [`../S1604_codify-agent-skills-rules-canon.md`](../S1604_codify-agent-skills-rules-canon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-12

---

## Objective

Give every measured dead end from S1594-S1599 one findable home, and route the approach-choosing moment through it.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - `CLAUDE.md` edits are settled, so this phase does not collide on the same file.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/REFUTED_APPROACHES.md` | New | ≤ 160 |
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified | ≤ 5 |
| `CLAUDE.md` | Modified | ≤ 5 |
| `.claude/commands/spec-tech.md` | Modified | ≤ 10 |
| `.claude/commands/research.md` | Modified | ≤ 10 |

---

## Steps

### Step 03.1 - Write the refuted-approaches index

**Files:** `dev/REFUTED_APPROACHES.md`

**Depends on:** - start of phase

**Prompt for developer:**

> Create `dev/REFUTED_APPROACHES.md`. Each entry names the idea in the words someone would propose it in, the ticket that measured it, the measurement that refuted it, and what shipped instead. Seed it with: extending the lexical detekt preflight to more rules (S1595 - its three rules fully cover 13.9% of attributable failures, nine hand-listed rules reach 48.1%, and the size rules have no lexical threshold at all because flagged and unflagged classes overlap by a 240-line band; shipped instead: the real analyser scoped to changed files); detekt `--auto-correct` (S1595 - the only switch is the shared `formatting` flag, which would arm ~5,591 findings, 46% of the baseline); a closed `ValidateSet` on the document-registry query parameters (S1597 - rejected by the PowerShell host before the script body runs, producing the same emptiness more expensively and duplicating the value list); a hook that fixes and retries a failed command (S1594 - `PostToolUse` cannot rewrite a tool result and no hook can retry, so a failing command class is cheaper to prevent with a PATH shim or a `PreToolUse` rewrite); parallelising the closure gates instead of accumulating their failures (S1598 - solves fast-path time, not the recovery problem, and breaks deterministic output order); a context-pricing hook on `UserPromptSubmit` (refuted as timing-blind because the tax accrues inside autonomous blocks where no prompt is submitted, while routing on the same event is correct - record the boundary so the refutation is not reused against the hook it does not apply to). Add the two retractions the batch produced about its own evidence: the S1599 zero-hit pattern list, which was a counter incremented on every call rather than on misses, and `ComplexMethod`, named in an owner capture as a top failure cause while it caused zero failures because `CyclomaticComplexMethod` supersedes it. Open the file with its admission rule: an entry needs a measurement and a source ticket, so an opinion cannot be filed here.

**Why:**

Strategic §1 records that each measured dead end lives only inside a closed spec, so re-proposing one costs a full research round to refute again, and strategic ADR-3 rules a single index over per-document footnotes because the dead end is proposed while choosing an approach, not while reading a topical document.

**Verification:**

- `Glob` - `dev/REFUTED_APPROACHES.md` exists.
- `Grep` - `S1594`, `S1595`, `S1597`, `S1598`, `S1599` each match at least once.
- `Grep` - `ComplexMethod` matches, so the capture-was-wrong case is carried.

**Status:** `[x]` done - Implemented and verified

**Step Log:**

- 2026-08-12 - Refuted-approaches index added, registered and routed from research and spec-tech; expected: all phase-03 content checks and registry validation pass | actual: PASS, registry 29 records

---

### Step 03.2 - Register the index in the document registry

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`

**Depends on:** Step 03.1

**Prompt for developer:**

> Add a record for `dev/REFUTED_APPROACHES.md` through the registry CLI, with product areas covering `workflow` and `specs`. Run `scripts/document_registry/validate.ps1` and confirm exit 0.

**Why:**

The document-registry skill requires a maintained document to be registered before a workflow relies on it, and step 03.3 makes this document a workflow dependency.

**Verification:**

- `Grep` - `REFUTED_APPROACHES` matches in `docs/DOCUMENT_REGISTRY.jsonl`.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.

**Status:** `[x]` done - Implemented and verified

**Step Log:**

- 2026-08-12 - Refuted-approaches index added, registered and routed from research and spec-tech; expected: all phase-03 content checks and registry validation pass | actual: PASS, registry 29 records

---

### Step 03.3 - Route the approach-choosing moment through the index

**Files:** `CLAUDE.md`, `.claude/commands/spec-tech.md`, `.claude/commands/research.md`

**Depends on:** Step 03.2

**Prompt for developer:**

> Add one pointer line to the CLAUDE.md section 5 research order naming `dev/REFUTED_APPROACHES.md` as the check before proposing an approach. In `.claude/commands/spec-tech.md` and `.claude/commands/research.md`, add an explicit condition to open the index before an approach is fixed, and to add an entry when a measurement refutes one.

**Why:**

Strategic §6 item 2 rules both placements with different weights: the research order carries one pointer line because it is literally the list of what to consult, while the drivers carry the explicit condition because the dead end is proposed at approach-choosing time, which is after the research order was read, and drivers are injected only on invocation so they add no always-loaded cost.

**Verification:**

- `Grep` - `REFUTED_APPROACHES` matches in `CLAUDE.md`, in `.claude/commands/spec-tech.md` and in `.claude/commands/research.md`.

**Status:** `[x]` done - Implemented and verified

**Step Log:**

- 2026-08-12 - Refuted-approaches index added, registered and routed from research and spec-tech; expected: all phase-03 content checks and registry validation pass | actual: PASS, registry 29 records

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `.\a.ps1 fg` exits 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The index holds the repository-local form of the batch's negative results. Phase 04 carries only the portfolio-generalizable half of them into the canon, not the repository-specific entries.

---

## Rollback Plan

Revert the phase commit. The index is additive and the three pointer edits are prose.
