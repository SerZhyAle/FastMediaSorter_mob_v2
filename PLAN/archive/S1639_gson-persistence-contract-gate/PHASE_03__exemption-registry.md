# Phase 03 - Exemption registry

**Strategic spec:** [`../S1639_gson-persistence-contract-gate.md`](../S1639_gson-persistence-contract-gate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Give the gate its only suppression path: a registry of deliberately unpinned models, each carrying a written justification, which may shrink but never grow silently.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/gson-persistence-exemptions-baseline.txt` | New | ≤ 60 |
| `scripts/quality/assert-gson-persistence-contract.ps1` | Modified | ≤ 620 |

Script budget carried forward from the raise recorded in Phase 02, where the resolution work the Phase 01 handoff demanded took the file past the original 400. This phase adds about 45 lines for the registry.

---

## Steps

### Step 03.1 - Seed the registry from the current tree

**Files:** `scripts/quality/gson-persistence-exemptions-baseline.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the registry as a line-per-entry allowlist of fully qualified model names, each line pairing the name with a justification on the same line under a fixed separator, plus a header comment stating that an entry without a justification is rejected. Seed it from a Phase 02 run: enter only models that are genuinely out of scope, and for a model that is a real defect enter the ticket that owns it rather than a justification excusing it. Do not enter `PrimaryGoogleAccount` - it is a live defect owned by S1657.

**Corrected 2026-08-14 - `PrimaryGoogleAccount` is entered after all, under `Ticket: S1657`.** Three reasons, in order of weight:

1. The sentence before it already rules that a real defect is entered with the ticket that owns it. `PrimaryGoogleAccount` is a real defect and S1657 owns it, so the exclusion contradicts the rule it follows.
2. Steps 04.1 and 04.2 require the gate to exit 0 across the whole tree (fast-gate batch) and to be wired as a failing gate in the closing facade. With S1657 open and its model unregistered, the gate can never be green, so registering the batch would break every close in the repository until an unrelated ticket lands. The plan cannot close as written.
3. What the exclusion was protecting - that a known defect stays visible - is preserved by other means: the registry is a ratchet, the entry names its owning ticket, and step 03.2's verdict line counts ticket-bearing entries separately, so a green run states how many known defects it is carrying. S1657 deletes the two lines when it lands, which a ratchet accepts without ceremony.

`GoogleScope` is entered for the same reason and under the same ticket: it is reached only as `PrimaryGoogleAccount.grantedScopes`, so it is one defect with one fix, and splitting it across the two sides of the registry would say the opposite.

**Why:**

Strategic §7 names registry sprawl as the main risk to the gate's value, and an allowlist of names rather than a count is what stops a new violation from hiding behind a removed one, following the precedent the unreferenced-strings gate already set in this repository.

**Verification:**

- `Glob` - `scripts/quality/gson-persistence-exemptions-baseline.txt` exists.
- `Grep` - every non-comment line contains the separator with non-empty text on both sides.
- `Grep` - every entry naming a live defect carries a `Ticket: Sxxxx` justification, and `PrimaryGoogleAccount` is one of them. Corrected 2026-08-14 together with the prompt above; the original predicate demanded zero hits for that name.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Registry seeded from the corrected phase 02 run: 16 entries under a ' :: ' separator - 7 live defects naming their owning ticket (S1657 for PrimaryGoogleAccount and GoogleScope, S1660 for FileAttributes, S1661 for the four enums) and 9 source paths whose serialization type the gate cannot resolve, each stating what that file actually writes. Two findings were parked as their own tickets first (S1660, S1661) so no entry excuses a defect nobody owns. Predicates: file exists, 0 malformed lines out of 16 entries, PrimaryGoogleAccount present under Ticket: S1657 per the correction recorded in the prompt.

---

### Step 03.2 - Apply the registry and refuse an unjustified entry

**Files:** `scripts/quality/assert-gson-persistence-contract.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Load the registry at the end of the run and suppress a violation whose model is listed. Refuse the whole run with the "could not verify" code when a registry line carries no justification, and report a listed model that no longer violates anything as a stale entry to remove. Print the count of suppressed violations in the verdict line so a green run never hides how much it is ignoring.

**Why:**

Strategic §11 criterion 6 requires that the registry cannot grow silently, and strategic §3.1 wish 2 requires a justification per entry, because a registry that accepts a bare name degenerates into a list nobody rereads.

**Verification:**

- Add a temporary registry line with no justification, run the script - exit code is 2.
- Remove that line, run the script - the verdict line states the number of suppressed violations.
- Add a temporary entry for a model with no violation, run the script - the run reports it as stale.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Registry loaded before the scan so a malformed line refuses in milliseconds rather than after a full tree walk. Suppression covers all three finding shapes - model fqn, enum fqn, source path - and staleness is computed only on a full unscoped run, since a scoped or single-module run legitimately never reaches most entries. Predicates measured: an entry with no justification exits 2 naming the line; after removal the verdict line reads 'suppressed by registry: 16 (7 tracked defect(s))' and exits 0; a probe entry for a non-violating model is reported as stale-exemption. Phase-boundary audit: no P0/P1; duplicate registry keys silently overwrite, left as P3.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase adds no compiled source.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The gate is now self-contained and honest about what it suppresses. Phase 04 only wires it into the existing runners; it must not add a second suppression path.

---

## Rollback Plan

Revert the phase commit and delete the registry file - the gate returns to reporting every violation unsuppressed.
