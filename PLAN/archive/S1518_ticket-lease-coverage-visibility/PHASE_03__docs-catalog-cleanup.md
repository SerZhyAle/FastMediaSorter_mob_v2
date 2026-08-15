# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S1518_ticket-lease-coverage-visibility.md`](../S1518_ticket-lease-coverage-visibility.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Publish the new operator invocation and regenerate its derived command reference.

## Prerequisites

- [ ] Phases 01 and 02 are ✅ Done.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | ≤ 40 |
| `docs/SCRIPT_CHEATSHEET.md` | Modified | generated |

## Steps

### Step 03.1 - Document the opt-in queue projection

**Files:** `docs/DEV_OPS.md`
**Depends on:** Phases 01 and 02

**Prompt for developer:**

> Add the `release-queue.ps1 -List -WithLeases` operator example beside the existing release-queue commands. State that it reads ephemeral ownership and never rewrites release ordering.

**Why:**

The feature is valuable only when the owner can discover it and understand that busy state is intentionally separate from the persistent release plan.

**Verification:**

- `Grep` - `-List -WithLeases` occurs in `docs/DEV_OPS.md`.
- `Grep` - `ephemeral` occurs in the added documentation paragraph.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Documented the opt-in command; regenerated cheatsheet and verified document registry views.

### Step 03.2 - Regenerate and verify command documentation

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Regenerate the script cheatsheet after the CLI parameter change, then run its sync check and the document-registry generated-view checks.

**Why:**

The repository treats the cheatsheet and document map as generated contract surfaces, so a new command parameter must not leave stale operator documentation.

**Verification:**

- `PowerShell` - `pwsh -NoProfile -File scripts/utils/help.ps1 -Check` exits 0.
- `PowerShell` - `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Documented the opt-in command; regenerated cheatsheet and verified document registry views.

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Documentation checks exit 0.
- [ ] Dev log entry added for every file in "Files Touched".

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
