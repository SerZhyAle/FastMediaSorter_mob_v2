# Phase 05 — Skill Integration

**Strategic spec:** [`../S0102_spec-catalog-ergonomics.md`](../S0102_spec-catalog-ergonomics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Wire the new operator commands into CLAUDE.md and the `/spec*` skill prompts so that no skill computes ids inline, performs manual finalization sequences, or calls raw catalog primitives where the new facade fits.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`next-id.ps1`, `search.ps1`, `stats.ps1` exist and work).
- [ ] Phase 03 is ✅ Done (`close.ps1` exists and works).
- [ ] Phase 04 is ✅ Done (`bulk-update.ps1` exists and works).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 250 |
| `/spec*` skill prompt files (located at runtime via Glob `.claude/commands/spec*.md` or equivalent) | Modified | varies |

> Locate skill files before editing: `Glob "**/*.md" .claude/commands/` or query the catalog with `-Role skills`. The exact paths are discovered at execution time.

---

## Steps

### Step 5.1 — Update CLAUDE.md "Spec Catalog" section to list new operator commands

**Files:** `CLAUDE.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `CLAUDE.md`, find the line:
> ```
> **CLI (only sanctioned mutators):** `insert.ps1`, `update.ps1`, `select.ps1`, `delete.ps1`, `validate.ps1` under `scripts/spec_catalog/`.
> ```
> Replace it with two lines:
> ```
> **CLI — primitives:** `insert.ps1`, `update.ps1`, `select.ps1`, `delete.ps1`, `validate.ps1` under `scripts/spec_catalog/`.
> **CLI — operator facade:** `next-id.ps1`, `search.ps1`, `close.ps1`, `stats.ps1`, `bulk-update.ps1` — prefer these for id allocation, lookup, finalization, summary, and batch changes.
> ```
> Do not change any other content in `CLAUDE.md`.

**Verification:**

- `Grep` — `next-id.ps1` present in `CLAUDE.md`.
- `Grep` — `search.ps1` present in `CLAUDE.md`.
- `Grep` — `close.ps1` present in `CLAUDE.md`.
- `Grep` — `stats.ps1` present in `CLAUDE.md`.
- `Grep` — `bulk-update.ps1` present in `CLAUDE.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 5/5 PASS. All 5 facade scripts present in CLAUDE.md. Dev log recorded.

---

### Step 5.2 — Replace inline id-allocation logic in `/spec` skill prompt with `next-id.ps1`

**Files:** `/spec` skill prompt file (locate first)
**Depends on:** Step 5.1

**Prompt for developer:**

> Locate the skill prompt file for `/spec` (typically `.claude/commands/spec.md` or the equivalent in the project's skill directory). In the "Spec Catalog hooks" section, find any instruction that describes allocating the next free id by reading the catalog and computing `max(id) + 1` inline, or calls `insert.ps1` without a pre-computed id. Replace with the two-step sequence:
> 1. Call `pwsh -File scripts/spec_catalog/next-id.ps1` and capture stdout as `$newId`.
> 2. Pass `$newId` to `insert.ps1 -Id $newId ...`.
>
> If the prompt already delegates to `insert.ps1` which calls `New-CatalogId` internally, and there is no redundant inline computation, add a note: "Use `next-id.ps1` for machine-readable id allocation (outputs `S####` only)." Only edit the spec-creation skill; leave other skill files untouched in this step.

**Verification:**

- `Grep` — `next-id.ps1` present in the `/spec` skill file.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — `next-id.ps1` note added to `.claude/commands/spec.md` Catalog hooks (no inline computation existed — note added per prompt instructions). Dev log recorded.

---

### Step 5.3 — Replace manual finalization sequences with `close.ps1` in `/spec-check` skill prompt

**Files:** `/spec-check` skill prompt file (locate first)
**Depends on:** Step 5.1

**Prompt for developer:**

> Locate the skill prompt file for `/spec-check`. In the section where it advances a spec to `Verified` status (currently calls `update.ps1 -Id <Sxxxx> -Status Verified` or similar), replace that call with `pwsh -File scripts/spec_catalog/close.ps1 -Id <Sxxxx> -Status Verified`. Add a note that `close.ps1` also stamps `closed_at` on the record. Do not change any other logic in the file.
>
> If `/spec-all` or `/spec-dev` contains its own finalization call to `update.ps1 -Status Verified`, apply the same replacement there.

**Verification:**

- `Grep` — `close.ps1` present in the `/spec-check` skill file.
- `Grep` — `close.ps1` present in any `/spec-dev` or `/spec-all` skill file that previously called `update.ps1 -Status Verified` for finalization.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — `close.ps1 -Status Verified` replaces `update.ps1 -Status Verified` in `.claude/commands/spec-check.md` line 142. Verification: `close.ps1` present in spec-check.md. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 5.* above is `[x] done`.
- [ ] `Grep` — `next-id.ps1` appears in `CLAUDE.md` and in the `/spec` skill file.
- [ ] `Grep` — `close.ps1` appears in `CLAUDE.md` and in the `/spec-check` skill file.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for `CLAUDE.md` and each modified skill file.

---

## Handoff Notes to Next Phase

- Final phase — see INDEX.md Completion Gate.
- After this phase, `/spec*` prompts reference the operator facade; the underlying primitives (`insert.ps1`, `update.ps1`, `select.ps1`, `delete.ps1`, `validate.ps1`) remain available for direct use where appropriate.

---

## Rollback Plan

Revert phase commit(s) — text changes only; skills fall back to primitive calls, which still work.
