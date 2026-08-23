# Phase 02 - Documentation and closure

**Strategic spec:** [`../S1928_stale-java-home-blocks-every-gradle-target.md`](../S1928_stale-java-home-blocks-every-gradle-target.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

The repair is written down where the JVM guard is already documented, and the ticket closes through the facade.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | ≤ 15 added |

---

## Steps

### Step 02.1 - Document the repair

**Files:** `docs/DEV_OPS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Record beside the existing JVM-guard material: what the repair does, that it reads the persisted variable rather than choosing a JDK, that it is loud, that it only touches the current process, and that the refusal is unchanged when there is nothing to repair. Say plainly that the operator should still fix their environment - the repair buys the session, not a cure.

**Why:**

Strategic ADR-2 makes loudness the thing that distinguishes a repair from a silent swap, and a reader who meets the printed line without a written model behind it cannot tell which of the two just happened to their build.

**Verification:**

- `Grep` - `docs/DEV_OPS.md` describes the JAVA_HOME snapshot repair.
- `Grep` - that material states the persisted variable is read, not a JDK chosen.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1928 step 02.1

---

### Step 02.2 - Close the ticket through the facade

**Files:** the whole changed set of this ticket
**Depends on:** Step 02.1

**Prompt for developer:**

> Close with `scripts/post-change.ps1 -Files "<whole set>" -ScopeToFile -ChangeType Tooling`, naming every file this ticket changed. Re-render the script cheatsheet if the gate asks.

**Why:**

CLAUDE.md section 12 requires the whole changed set to be named on a dirty tree, because naming one file while changing several certifies only the one named.

**Verification:**

- Run: the closure command - expected `post-change: PASS`, exit 0.
- `Grep` - `dev/CHANGELOG.md` carries a row for this ticket.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1928 step 02.2

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the documentation edit - no executable behaviour is introduced by this phase.
