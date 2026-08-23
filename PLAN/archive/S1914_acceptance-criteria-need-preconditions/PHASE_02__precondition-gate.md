# Phase 02 - Precondition gate

**Strategic spec:** [`../S1914_acceptance-criteria-need-preconditions.md`](../S1914_acceptance-criteria-need-preconditions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

A gate that rejects a §11 criterion about accumulated state which names no precondition, ratcheted against the existing corpus.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-acceptance-preconditions.ps1` | New | ≤ 220 |
| `scripts/quality/acceptance-precondition-baseline.txt` | New | n/a - generated |
| `docs/AGENT_HOOKS.md` or the gate inventory | Modified | n/a - only if the gate is registered as a hook |

---

## Steps

### Step 02.1 - Write the checker

**Files:** `scripts/quality/assert-acceptance-preconditions.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Walk `PLAN/*.md`, read the §11 acceptance section only, and flag a criterion that matches an accumulation trigger while naming no precondition. Triggers, from strategic §6.2: `переживает`, `сохраняется`, `не теряется`, `уцелел`, `survive`, `preserved`. Narrow to state accumulated outside the session per §6.4 - migration, import, upgrade, merge, schema - and do not flag restart or rotation criteria. Recognise a precondition as a named prior state in the same criterion, which is the form three of the eight sampled criteria already use. Follow the repo exit contract (CLAUDE.md Rule 7): document the codes the script returns in its header.

**Why:**

Strategic §4 measured that no gate reads §11 at all - of 74 `assert-*.ps1` scripts, the three that read spec prose check the tactical step form, §3.3 owner inputs, and probe literals - so this defect currently has no mechanical reader anywhere.

**Verification:**

- `Glob` - the script exists.
- Run it: exit code is one of the codes its header documents.
- `Grep` - the trigger list is a data structure, not inlined into the matcher, so §5.3 extensibility holds.

**Status:** `[x]` done

---

### Step 02.2 - Generate the baseline

**Files:** `scripts/quality/acceptance-precondition-baseline.txt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Run the checker over the whole `PLAN/**` corpus and record every existing violation as the baseline, so only new criteria are refused. Include a header comment saying the baseline is a debt record, not an approval.

**Why:**

Strategic §7 row 1 rates "gate rejects the existing corpus" as high-probability with the consequence that tickets stop closing; §3.2 makes the ratchet a hard constraint for exactly that reason.

**Verification:**

- Run the checker with the baseline present: exit 0 on the unchanged corpus.
- `Grep` - the baseline file names at least the S1832 criteria that motivated the ticket.

**Status:** `[x]` done

---

### Step 02.3 - Prove both directions

**Files:** none - verification only
**Depends on:** Step 02.2

**Prompt for developer:**

> Demonstrate the two outcomes strategic §3.3 names as the success condition: the checker rejects an S1832-shaped criterion (accumulated state, no precondition) and accepts one that writes the precondition into the wording. The fixtures live beside this plan in `fixtures/`, never in a real spec - they are kept rather than scratched so the proof stays reproducible after the ticket closes. The gate's corpus walk is non-recursive over `PLAN/*.md`, so a fixture in this subfolder is never mistaken for a spec.

**Why:**

A gate that only ever passes is indistinguishable from one that reads nothing - the very failure mode this ticket exists to close, recorded in strategic §4 as knowledge without a mechanism.

**Verification:** run both, from the repo root:

```powershell
pwsh -NoProfile -File scripts/quality/assert-acceptance-preconditions.ps1 -Gate -Path PLAN/S1914_acceptance-criteria-need-preconditions/fixtures/negative__no-precondition.md
pwsh -NoProfile -File scripts/quality/assert-acceptance-preconditions.ps1 -Gate -Path PLAN/S1914_acceptance-criteria-need-preconditions/fixtures/positive__names-precondition.md
```

Observed 2026-08-21: negative exits **1** and names both accumulated-state criteria while leaving the restart criterion alone; positive exits **0**. Against the real corpus the gate exits **0** over 308 specs with 2 baselined entries, and a missing path exits **2** - all three documented codes observed.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] The checker exits 0 over the real corpus with its baseline (308 specs, 2 baselined).
- [x] Phase-boundary audit run.

---

## Handoff Notes to Next Phase

The gate exists and is proven in both directions. Phase 03 tells authors about it, so the rule is discoverable before the gate refuses them.

---

## Rollback Plan

Delete the script and its baseline - nothing else references them until Phase 03.
