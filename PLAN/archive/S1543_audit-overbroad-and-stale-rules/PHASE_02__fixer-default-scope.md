# Phase 02 - Style fixer stops defaulting into the spec catalogue

**Strategic spec:** [`../S1543_audit-overbroad-and-stale-rules.md`](../S1543_audit-overbroad-and-stale-rules.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Narrow the hand-run ellipsis fixer's default directory set to documentation, so no default invocation can rewrite a specification, and re-render the generated script cheatsheet that publishes that default.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/fix-ellipsis-docs.ps1` | Modified | ≤ 90 |
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | n/a |

> `docs/SCRIPT_CHEATSHEET.md` is generated. Never hand-edit it - re-render it with the generator named in step 02.2.

---

## Steps

### Step 02.1 - Drop the spec catalogue from the fixer's default directories

**Files:** `scripts/utils/fix-ellipsis-docs.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the `$Dirs` parameter default from `@("docs", "PLAN")` to `@("docs")`. Add a one-line comment above the parameter recording that the specification catalogue is deliberately excluded because the house text style does not apply to specifications (S1543), and that an operator who genuinely wants a one-off pass over another directory can still pass `-Dirs` explicitly. Change nothing else - the fence and backtick-span handling inside `Replace-EllipsisInMarkdown` is correct and stays.

**Why:**

Strategic §5.1 pillar B requires that no tool rewrite the specification catalogue for style by default, and this script's default set is the only remaining path by which a bare invocation could rewrite verbatim captured material.

**Verification:**

- `Grep` - `\$Dirs = @\("docs"\)` matches in `scripts/utils/fix-ellipsis-docs.ps1`.
- `Grep` - `"PLAN"` returns zero hits in that file.
- `Grep` - `S1543` matches at least once in that file.
- Command `pwsh -NoProfile -File scripts/utils/fix-ellipsis-docs.ps1 -DryRun` exits 0 and names no file whose path starts with `PLAN`.

**Status:** `[x]` done

---

### Step 02.2 - Re-render the generated script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Re-render the cheatsheet with `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` so the published default for `fix-ellipsis-docs.ps1` matches the script. Then run `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` and record its exit code.

**Why:**

The cheatsheet is generated from every repository parameter block and is drift-gated, so a parameter default changed without a re-render leaves a document asserting a value the script no longer has.

**Verification:**

- Command `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` exits 0.
- `Grep` - `fix-ellipsis-docs` matches in `docs/SCRIPT_CHEATSHEET.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - not applicable, no compiled source changed in this phase.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added - deferred to Phase 04, which batches the whole ticket into one entry.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated - not applicable.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Results 2026-08-09

- Step 02.1 - `-DryRun` exit 0. It named three files, all under `docs/`, none under `PLAN/`. Expected: no `PLAN` path. Actual: none.
- Step 02.2 - `help.ps1 -Generate` exit 0, "Wrote docs\SCRIPT_CHEATSHEET.md (294 scripts)". `assert-script-cheatsheet-sync.ps1 -Gate` exit 0, "in sync".
- Side observation, not fixed here: the dry run named `docs/FEATURES_noLegal.md`, `_RU` and `_UK` as carrying style violations. They are `/skill-release`-generated showcase files that CLAUDE.md §11 forbids editing per-spec, and they are evidence for S1544 (the style rule is unenforced on documentation prose), not work for this ticket.

---

## Handoff Notes to Next Phase

No automated or default-argument path now writes style changes into `PLAN/`. Only an explicit `-Dirs PLAN` does, which is a deliberate operator act.

---

## Rollback Plan

Restore the two-element default and re-render the cheatsheet - no data migration, no user-facing surface touched.
