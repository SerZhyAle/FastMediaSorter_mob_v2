<!-- Template consumed by: /spec-tech (Process step 5), /spec-all (Stage S1, compact spec phases), /spec-dev (reads the step form back). -->
<!-- Substitute: <Sxxxx>, <short-name>, the phase number NN, the phase slug and title, the step numbers NN.M. Read this file before writing the first phase file of a run. -->

<!--
STEP FORM (S1343, adopted 2026-08-02 on the pilot verdict recorded in `dev/spec-form-pilot.jsonl`).
COMPRESSION. In `Prompt for developer:` the only forbidden things are filler words and redundant turns of phrase - "please", "in order to" (write "to"), restating the step title in the step body. Full sentences are NOT shortened and causal wording is NEVER compressed. Nothing else is banned: this is not a telegraphic or keyword style, and ordinary English prose minus the filler is already compliant.
WHY. Every step carries `**Why:**` between `**Prompt for developer:**` and `**Verification:**`, at least one complete sentence, sourced from the strategic spec. It states the reason the step exists - what breaks without it, or which constraint it satisfies - never a restatement of the prompt above it, and it is exempt from the compression rule. A step whose reason is not in the strategic spec writes `not stated in strategic spec` verbatim instead of inventing one.
-->

# Phase NN - <Phase Title>

**Strategic spec:** [`../Sxxxx_<short-name>.md`](../Sxxxx_<short-name>.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase NN-M (or "none - foundation phase")
**Blocks:** Phase NN+K, Phase NN+L
**Steps done:** 0 / N
**Started:** -
**Completed:** -

---

## Objective

<One sentence. What this phase produces. Example: "Introduce `InputBindingRepository` with Room persistence and Hilt wiring; no UI or dispatch changes yet.">

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] <any phase-specific precondition>

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/<path>/<File>.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/<path>/<Existing>.kt` | Modified | ≤ 500 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >2000 LOC → split via Manager pattern).
>
> **Flavor placement.** Flavor-only classes (vr / vrUnlicensed / noLegal / lite / photos / legacy) MUST be listed under `app_v2/src/<flavor>/java/...` - not under `src/main/java/`. Shared contract interface and No-Op fallback stay in `src/main/java/`. Hilt binding modules for real impl go under `src/<flavor>/java/.../di/`. See `dev/FLAVOR_DEVELOPMENT_RULES.md`.

---

## Steps

### Step NN.1 - <Imperative title>

**Files:** `path/to/File.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> <Self-contained imperative, 1-4 sentences, filler removed. Reader must not need to open strategic spec.>

**Why:**

<At least one complete sentence, sourced from the strategic spec. The reason this step exists: what breaks without it, or which constraint it satisfies. Never a restatement of the prompt. Not compressed. No reason in the strategic spec -> `not stated in strategic spec` verbatim.>

**Verification:**

- `Glob` - `path/to/File.kt` exists.
- `Grep` - `class <ClassName>` matches exactly once in that file (declaration line, not comment).
- `Grep` - `<ExpectedMethodSignature>` present.

**Status:** `[ ]` not done

---

### Step NN.2 - <Imperative title>

**Files:** ..
**Depends on:** Step NN.1

**Prompt for developer:**

> ..

**Why:**

<..>

**Verification:**

- ..

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step NN.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-<NN>)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module <app_v2|wear>`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

<Invariants this phase established. If final phase - "Final phase - see INDEX.md Completion Gate.">

---

## Rollback Plan

<If risk warrants: which commits to revert, config to restore. Low-risk: "Revert phase commit(s) - no data migration or user-facing surface changed.">
