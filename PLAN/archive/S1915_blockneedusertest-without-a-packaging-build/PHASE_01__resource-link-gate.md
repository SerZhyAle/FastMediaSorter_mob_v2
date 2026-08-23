# Phase 01 - Resource-link gate in the closure facade

**Strategic spec:** [`../S1915_blockneedusertest-without-a-packaging-build.md`](../S1915_blockneedusertest-without-a-packaging-build.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 5 / 5
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

`scripts/post-change.ps1` runs the Gradle resource-processing task whenever the changed set contains a resource or manifest file, for every variant that set touches, and prints an explicit skip with a reason when it does not.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - items 1-4 are Resolved; item 5 is deliberately not a blocker (see INDEX "Pre-Implementation Blockers").
- [ ] Working tree is clean or on a feature branch.
- [ ] `JAVA_HOME` points at a JDK that exists - a stale value makes every Gradle target fail for an unrelated reason (S1928).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/post-change.ps1` | Modified | ≤ 60 added |
| `scripts/post-change.tests/Run-Tests.ps1` | Modified | ≤ 120 added |

> `scripts/post-change.ps1` is well over 500 LOC, so Constraints require a timestamped backup in the ticket's scratch directory before the first edit.

---

## Steps

### Step 01.1 - Back up the facade before editing it

**Files:** `scripts/post-change.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `scripts/post-change.ps1` to a timestamped name in the ticket's scratch directory before the first edit.

**Why:**

CLAUDE.md Rule 5 requires a timestamped backup before editing any file over 500 LOC, and this file is the closure facade every other ticket depends on.

**Verification:**

- The timestamped copy exists in the ticket's scratch directory at the moment of the first edit. Deliberately not cited by path in this closed spec: the scratch tree is disposable by CLAUDE.md Rule 1, so a path recorded here would be a promise the repository is not obliged to keep. The backup is a precaution during the edit, not part of the audit trail - the durable record of what changed is the phase's dev-log row.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 01.1

---

### Step 01.2 - Add the variant-selection helper

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a function next to `Test-AnyChangedFile` that reads the normalised changed set and returns the distinct list of flavors whose resources it touches. Map each `<module>/src/<sourceSet>/` segment to a flavor when `<sourceSet>` is one of the six the fast-check helper accepts (`Standard`, `NoLegal`, `Lite`, `Photos`, `Legacy`, `Vr`, matched case-insensitively); map every other source set, `main` included, to the module's default flavor. Return the union with duplicates removed, and never return an empty list when the gate is going to run.

**Why:**

Strategic §3.2 requires the gate to pick the variant from the touched source set: a resource under `src/vr/res` compiled only as `standard` is checked by a variant that never sees it, which is the same class of false green that S1807 found when a phone target was quoted under a watch change.

**Verification:**

- `Grep` - the new function name matches exactly once as a declaration in `scripts/post-change.ps1`.
- `Grep` - the six flavor names appear in that function.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 01.2

---

### Step 01.3 - Add the gate applicability classifier

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Define `$runsResourceLinkGate` beside `$runsAndroidTestCompileGate`, set from the existing `$isResourceChange`. Add a comment naming this as the second Gradle task the facade adds beyond detekt and stating the trigger, matching the comment style already on `$runsAndroidTestCompileGate`.

**Why:**

Strategic §2 goal 2 requires a change that touches no resource to pay nothing, and reusing `$isResourceChange` gets that for free because it is already false for a Kotlin-only or docs-only set - so the gate is conditional by construction rather than by a new rule someone can forget.

**Verification:**

- `Grep` - `$runsResourceLinkGate` is assigned exactly once in `scripts/post-change.ps1`.
- `Grep` - `$isResourceChange` appears on that assignment's right-hand side.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 01.3

---

### Step 01.4 - Wire the gate and its skip branch

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add `Invoke-Gate "resource-link-gate"` immediately before the androidTest gate, iterating the flavors from Step 01.2 and invoking `scripts/builders/check-standard-fast.ps1 -Mode Resources -Module $Module -Flavor <flavor>` once per flavor. Stop at the first non-zero exit and surface it. Print the module and every flavor the gate checked. Add the matching `Skip-Step "resource-link-gate"` branch naming the reason, worded like the neighbouring skip reasons. Route through the fast-check helper, never `gradlew` directly.

**Why:**

Strategic ADR-1 puts the check in the facade because that is the only place the changed set arrives as an argument, and §3.2 requires `temp/BUILD.LOCK` to be taken by the same helper every other build path uses (CLAUDE.md Rule 23) - calling `gradlew` here would take no lock and race every sibling session.

**Verification:**

- `Grep` - `resource-link-gate` appears in both an `Invoke-Gate` and a `Skip-Step` call.
- `Grep` - `-Mode Resources` appears in `scripts/post-change.ps1`.
- `Grep` - `gradlew` does not appear in the added block.
- Run: `pwsh -NoProfile -File scripts/post-change.ps1 -File "docs/DEV_OPS.md" -Target "S1915 probe" -Description "S1915 probe - resource-link gate skips a docs set" -ChangeType Doc` - expected: output contains `[resource-link-gate] SKIP` with a reason, exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 01.4

---

### Step 01.5 - Cover the classifier and the variant map with tests

**Files:** `scripts/post-change.tests/Run-Tests.ps1`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add cases asserting: a set with only `.kt` files selects no flavor and does not arm the gate; a set with `app_v2/src/main/res/layout/x.xml` arms it and selects the default flavor alone; a set with `app_v2/src/vr/res/values/x.xml` selects the `Vr` variant; a set touching both selects both, once each; a `wear` set selects the wear default. Assert against the helper and the classifier directly - do not invoke Gradle from the test.

**Why:**

Strategic §7 names variant combinatorics as a risk whose mitigation is a union without repeats, and a union is exactly the kind of set operation that regresses silently unless a test pins it; running Gradle inside the test would make the suite pay the cost the gate is being measured for.

**Verification:**

- Run: `pwsh -NoProfile -File scripts/post-change.tests/Run-Tests.ps1` - expected: exit 0, every added case reported as passing.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1915 step 01.5

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - not applicable: no Kotlin, XML or build file changed in this phase. The gate's own Gradle invocation is exercised in Phase 02.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the phase via `scripts/post-change.ps1`.
- [ ] `dev/CATALOG/<module>.jsonl` regeneration - not applicable, no Kotlin touched.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The gate exists and its skip branch is proven. What is NOT yet proven and is Phase 02's whole job: that the gate goes red on a genuinely broken resource, what it costs on a warm daemon, and whether its task list also fails on a malformed manifest.

---

## Rollback Plan

Revert `scripts/post-change.ps1` from the Step 01.1 backup and drop the added test cases - no data migration, no user-facing surface, no other script reads the new function.
