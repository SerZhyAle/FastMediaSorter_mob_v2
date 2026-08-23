# Phase 03 - Documentation and closure

**Strategic spec:** [`../S1915_blockneedusertest-without-a-packaging-build.md`](../S1915_blockneedusertest-without-a-packaging-build.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

The Validation Ladder's "Target build passes" requirement for layouts and manifests now names the mechanism that enforces it, and the ticket closes through the facade.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Strategic §6 item 5 reads `Resolved`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 4 changed |
| `AGENTS.md` | Modified | ≤ 4 changed |
| `docs/DEV_OPS.md` | Modified | ≤ 15 added |

> `CLAUDE.md` and `AGENTS.md` are a shared rule set: CLAUDE.md section 1 requires the parallel file to be synced in the same change.

---

## Steps

### Step 03.1 - Point the Validation Ladder at its enforcement

**Files:** `CLAUDE.md`, `AGENTS.md`, `docs/DEV_OPS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In the Validation Ladder's `Layout/manifest` rung, name the gate that now enforces "Target build passes" mechanically, and mirror the same edit into `AGENTS.md`. In `docs/DEV_OPS.md`, describe the gate beside the other facade gates: its trigger, its variant selection, its measured cost from Phase 02, and the fact that a non-zero exit means a resource that does not link rather than a missing JDK.

**Why:**

Strategic §0 records that the Validation Ladder already demanded a passing target build for layouts and manifests and that nothing mechanical stood between that demand and the device gate - so a rung that now has an enforcer should say which one, or the next reader re-derives the same gap.

**Verification:**

- `Grep` - `resource-link-gate` appears in `CLAUDE.md`, `AGENTS.md` and `docs/DEV_OPS.md`.
- Run: `pwsh -NoProfile -File scripts/quality/assert-rule-digest-sync.ps1` if present - expected exit 0; the rule-digest gate fires on any `CLAUDE.md` edit.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 03.1

---

### Step 03.2 - Close the ticket through the facade

**Files:** the whole changed set of this ticket
**Depends on:** Step 03.1

**Prompt for developer:**

> Close with `scripts/post-change.ps1 -Files "<whole set>" -ScopeToFile -ChangeType Tooling`, naming every file this ticket changed. Confirm the run's own `resource-link-gate` line reports a skip, since this ticket's set carries no resource file.

**Why:**

CLAUDE.md section 12 requires the whole changed set to be named on a dirty tree, because naming one file while changing several certifies only the one named - and this ticket's own closure is the first honest test of the skip branch it added.

**Verification:**

- Run: the closure command - expected `post-change: PASS`, exit 0, and `[resource-link-gate] SKIP` in its output.
- `Grep` - `dev/CHANGELOG.md` carries a row for this ticket.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 03.2

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] `docs/FEATURES*` untouched - strategic §8 reads "Без изменений".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the documentation edits - no executable behaviour is introduced by this phase.
