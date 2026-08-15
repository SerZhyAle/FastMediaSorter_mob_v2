# Phase 01 - Hook inventory and its sync gate

**Strategic spec:** [`../S1604_codify-agent-skills-rules-canon.md`](../S1604_codify-agent-skills-rules-canon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 4 / 4
**Started:** -
**Completed:** 2026-08-12

---

## Objective

Produce the single inventory naming every registered hook with its event, verdict shape, escape hatch and home, and a gate that fails when the inventory and the registered set disagree.

---

## Prerequisites

- [ ] Strategic §6 items are Resolved (both are).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/AGENT_HOOKS.md` | New | ≤ 200 |
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified | ≤ 5 |
| `scripts/quality/assert-hook-inventory.ps1` | New | ≤ 220 |
| `scripts/quality/assert-hook-inventory.tests/run-tests.ps1` | New | ≤ 180 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 20 |

---

## Steps

### Step 01.1 - Write the hook inventory document

**Files:** `docs/AGENT_HOOKS.md`

**Depends on:** - start of phase

**Prompt for developer:**

> Create `docs/AGENT_HOOKS.md` as the single inventory of every registered Claude Code hook. Give each hook a row carrying: file name, event, matcher, verdict shape (refuses / rewrites / observes / warns / arms), home (`global` = `~/.claude/`, per-machine and not version-controlled; `project` = `.claude/hooks/`, travels with the checkout), the CLAUDE.md rule number it cites in its refusal text or `-` when it cites none, its escape hatch, and its contract-test path or `-`. Cover all 11 registered hooks: global `warn-context-size`, `guard-find-command`, `guard-ps1-in-bash`, `guard-fire-and-forget`, `guard-bash-unavailable-command`, `guard-uncapped-read`; project `reset-catalog-touch-marker`, `nudge-small-task-tier`, `guard-catalog-before-kt-search`, `guard-bash-slash-arg`, `observe-empty-grep`. Open the document with the recovery instruction an agent needs at the moment it is refused: the refusal text names the hook and the rule, and this file carries the full contract and the escape hatch. State plainly that a global hook is absent on a fresh machine while a project hook is not.

**Why:**

Strategic §1 records that 5 of the 11 registered hooks are named nowhere the agent reads, and that two of those five alter the call itself - one refuses a `Grep`/`Glob`, one rewrites `Read` input. Strategic §2 goal 1 requires that a refused or silently corrected agent can find the responsible mechanism in a file it already reads.

**Verification:**

- `Glob` - `docs/AGENT_HOOKS.md` exists.
- `Grep` - each of the 11 hook base names matches at least once in that file.
- `Grep` - the words `refuses`, `rewrites`, `observes` each match at least once.
- `Grep` - `~/.claude/` and `.claude/hooks/` both match, so both homes are distinguished.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Inventory of 11 registered hooks, registry record, sync gate with 8 contract tests, wired into a.ps1 fg (PASS, 288 ms)

---

### Step 01.2 - Register the inventory in the document registry

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`

**Depends on:** Step 01.1

**Prompt for developer:**

> Add a record for `docs/AGENT_HOOKS.md` to `docs/DOCUMENT_REGISTRY.jsonl` through the registry's own CLI, never by hand-editing generated targets. Give it product areas covering `agents` and `workflow` and the trigger vocabulary already in use for that material. Then run `scripts/document_registry/validate.ps1` and confirm exit 0.

**Why:**

The document-registry skill states that a maintained document is registered before a workflow relies on it, and strategic §2 goal 1 makes this document a workflow dependency the moment a rule points at it.

**Verification:**

- `Grep` - `AGENT_HOOKS.md` matches in `docs/DOCUMENT_REGISTRY.jsonl`.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea agents` lists the new record.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Inventory of 11 registered hooks, registry record, sync gate with 8 contract tests, wired into a.ps1 fg (PASS, 288 ms)

---

### Step 01.3 - Write the inventory sync gate

**Files:** `scripts/quality/assert-hook-inventory.ps1`

**Depends on:** Step 01.1

**Prompt for developer:**

> Create `scripts/quality/assert-hook-inventory.ps1`. It reads the registered hook set and compares it against the names in `docs/AGENT_HOOKS.md`, failing on any name present in one and absent from the other, in both directions. Judge the project half strictly and always: parse `.claude/settings.json` and enumerate `.claude/hooks/*.ps1`, excluding `*.tests`/`tests`/`global-hook-tests` directories. Judge the global half only when `~/.claude/settings.json` is readable; when it is absent, print one advisory line naming the skipped half and do not fail. Follow the exit contract in CLAUDE.md Rule 7 and list the codes in the header: 0 in sync, 1 a real divergence, 2 could not verify because the inventory or `.claude/settings.json` is missing or unparsable. Emit `Write-Error $msg -ErrorAction Continue` before any `exit N` where N is not 1.

**Why:**

Strategic ADR-2 rules that inventory sync is held by a gate rather than by discipline, because the divergence is mechanically detectable and the batch measured ungated rules at 1-8% against ~99% for gated ones - the original gap arose exactly this way, with five hooks registered and never written down. Strategic §6 item 1 rules the asymmetric scope: the project half is version-controlled and reproducible on any machine, while a strict verdict on a per-machine set produces a red that cannot be fixed from the repository.

**Verification:**

- `Glob` - `scripts/quality/assert-hook-inventory.ps1` exists.
- `Grep` - `-ErrorAction Continue` matches, so the exit contract is reachable.
- `Grep` - the header block lists exit codes `0`, `1` and `2`.
- Run the script on the current tree: exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Inventory of 11 registered hooks, registry record, sync gate with 8 contract tests, wired into a.ps1 fg (PASS, 288 ms)

---

### Step 01.4 - Add contract tests and wire the gate into the fast batch

**Files:** `scripts/quality/assert-hook-inventory.tests/run-tests.ps1`, `scripts/quality/assert-fast-gates.ps1`

**Depends on:** Step 01.3

**Prompt for developer:**

> Create `scripts/quality/assert-hook-inventory.tests/run-tests.ps1` covering, against fixtures in a temporary directory rather than the live tree: a hook registered but absent from the inventory fails with 1; an inventory naming a hook that is not registered fails with 1; a matched set exits 0; an absent global settings file yields exit 0 plus the advisory line; an unparsable inventory yields 2. Then register the gate in `scripts/quality/assert-fast-gates.ps1` so `.\a.ps1 fg` runs it.

**Why:**

Strategic §11 criterion 2 requires that registering or removing a hook without editing the inventory fails the closure, which holds only once the gate runs inside the batch the closure invokes; and the batch's own lesson from S1594 is that a guard whose reachability is untested is indistinguishable from one that allows everything.

**Verification:**

- `Glob` - `scripts/quality/assert-hook-inventory.tests/run-tests.ps1` exists.
- Run the tests: all cases pass, exit 0.
- `Grep` - `assert-hook-inventory` matches in `scripts/quality/assert-fast-gates.ps1`.
- `pwsh -NoProfile -File ./a.ps1 fg` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Inventory of 11 registered hooks, registry record, sync gate with 8 contract tests, wired into a.ps1 fg (PASS, 288 ms)

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `.\a.ps1 fg` exits 0 with the new gate included.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Catalog regeneration not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`docs/AGENT_HOOKS.md` is the pointer target Phase 02 compresses the rules onto, and the gate makes that pointer non-optional. Phase 02 must not remove a rule number that a hook's refusal text cites.

---

## Rollback Plan

Revert the phase commit. No data migration, no user-facing surface, no Kotlin - the gate is additive and its removal from `assert-fast-gates.ps1` restores the prior batch exactly.
