# Phase 02 - Pre-release gate

**Strategic spec:** [`../S1627_strings-thirteen-locales-by-default.md`](../S1627_strings-thirteen-locales-by-default.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Turn the new-lexeme list into a release blocker: a gate script that refuses while the list is non-empty, and a mandatory step in the pre-release sweep that runs it and names the translation task.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `list-new-lexemes.ps1` returns exit 3 on a seeded key and exit 0 on a clean tree.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-new-lexemes-translated.ps1` | New | ≤ 160 |
| `.claude/commands/spec-prerelease.md` | Modified | ≤ 40 added |

---

## Steps

### Step 02.1 - Add the gate script

**Files:** `scripts/quality/assert-new-lexemes-translated.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Write the gate. It calls `list-new-lexemes.ps1`, and on a non-empty list fails with a message that names the count, up to twenty keys, the missing locales and both repair routes - the bulk round trip through the external translator, and filling the values directly with `set-android-string.ps1 -Translations`. Exit 0 clean, 1 on a non-empty list, 2 when it cannot verify (baseline or producer missing). Follow the reachable-exit-code rule: `Write-Error <msg> -ErrorAction Continue` before every non-1 exit, and list the codes in the header.

**Why:**

Strategic §5.1 requires the refusal to sit on the release path rather than in the author's memory, because the repository has measured that an ungated rule is followed 1-8% of the time while a gated one holds at ~99%.

**Verification:**

- `Glob` - `scripts/quality/assert-new-lexemes-translated.ps1` exists.
- Run on the current tree - exit code 0.
- Seed one throwaway key, run again - exit code 1, output names the key and at least one missing locale. Remove the key afterwards and confirm exit 0.
- Delete the baseline path temporarily via `-BaselinePath temp/scratch/absent.txt` - exit code 2, not 1.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.

**Status:** `[x]` done - 2026-08-14. All four paths measured: clean tree exit 0; seeded `s1627_probe_key` exit 1 naming the key and both repair routes; `-BaselinePath temp/scratch/absent.txt` exit 2, not 1; after removing the probe, exit 0 again. `assert-exit-contract.ps1` exit 0.

---

### Step 02.2 - Wire the mandatory step into the pre-release sweep

**Files:** `.claude/commands/spec-prerelease.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add step `0.8 - Produce and clear the new-lexeme list (content, no device, GATING)` after step 0.7, in the shape 0.7 already uses: the command, then a branch per exit code. Exit 0 continues; exit 1 is a hard release blocker naming the translation task - run the bulk export, translate, import, re-run this step; exit 2 aborts the sweep like any infrastructure failure. State that the step is mandatory and unconditional rather than "if strings changed", and carry its outcome into the step 4 verdict the way 0.7 is carried.

**Why:**

Strategic ADR-2 makes the translation a step of the release cycle owned by the pre-release stage, not a side effect of the ticket that added the strings; §3.1 records the owner's requirement that the program produce the list before the release rather than a person remembering to.

**Verification:**

- `Grep` - `0.8 - Produce and clear the new-lexeme list` matches exactly once in `.claude/commands/spec-prerelease.md`.
- `Grep` - `assert-new-lexemes-translated.ps1` matches in that file.
- `Grep` - `GATING` matches on the new step's heading line.
- The step's body names all three exit codes 0, 1 and 2.

**Status:** `[x]` done - 2026-08-14. Step `0.8` sits between 0.7 and step 1, in 0.7's own shape, and its outcome is carried into the step 4 verdict line beside 0.7's.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - not applicable; run `.\a.ps1 fg` and record its exit code.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The release path now refuses on untranslated new keys. Phase 03 adds the early signal so that refusal is never a surprise; it must stay non-blocking, per the owner's ruling that only the pre-release check fails.

---

## Rollback Plan

Delete the gate script and remove step 0.8 from the pre-release command. No shipped artifact changes.
