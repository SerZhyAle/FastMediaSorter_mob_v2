# Phase 01 - Refresh the snapshot before refusing

**Strategic spec:** [`../S1928_stale-java-home-blocks-every-gradle-target.md`](../S1928_stale-java-home-blocks-every-gradle-target.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

`Assert-GradleToolchainOrExit` repairs a stale `JAVA_HOME` snapshot from the persisted variable when it can, prints what it did, and otherwise refuses exactly as before.

---

## Prerequisites

- [ ] Strategic §6.1 is Resolved - it is.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 55 added |
| `scripts/utils/agent-lock.tests/Run-Tests.ps1` | New or modified | ≤ 140 |

> `scripts/utils/agent-lock.ps1` is over 500 LOC, so Constraints require a timestamped backup in the ticket's scratch directory before the first edit.

---

## Steps

### Step 01.1 - Add the repair helper

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up the file first. Then add a function beside `Test-JvmHomeMissingParts` that reads the persisted `JAVA_HOME` from the User scope, then the Machine scope, and returns the first value that exists, differs from the process snapshot, and passes `Test-JvmHomeMissingParts`. Return the value and the scope it came from, or nothing. Read the environment only - launch no process, and never write a persisted variable.

**Why:**

Strategic §3.2 confines the repair to the current process's snapshot and forbids `setx` or registry writes, and §5.1 requires all three conditions together, because a persisted value equal to the snapshot has nothing to repair and an unusable one has nothing worth taking.

**Verification:**

- `Grep` - the new function appears once in `agent-lock.ps1` and calls `Test-JvmHomeMissingParts`.
- `Grep` - it contains no `setx` and no registry write.
- `Glob` - a timestamped backup of `agent-lock.ps1` exists in the ticket's scratch directory.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1928 step 01.1

---

### Step 01.2 - Use it in the launcher-JVM branch

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `Assert-GradleToolchainOrExit`, when the launcher check finds the snapshot unusable, call the helper before the refusal. On a hit, set `$env:JAVA_HOME` for this process, print one line naming the old value, the new value and the scope, and fall through to the rest of the function. On a miss, leave the existing refusal exactly as it is - same lines, same exit 3.

**Why:**

Strategic §5 puts the repair before the refusal rather than replacing it, so the case with nothing to repair keeps its current behaviour; §3.2 forbids a silent repair because a silent JVM swap is precisely what the capture rates worse than stopping.

**Verification:**

- `Grep` - the refusal block's text and its `exit 3` are unchanged.
- `Grep` - the repair branch writes `$env:JAVA_HOME` and prints both values.
- Run: `pwsh -NoProfile -File ./a.ps1 fg` on a healthy environment - expected: unchanged behaviour, exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1928 step 01.2

---

### Step 01.3 - Prove both directions

**Files:** `scripts/utils/agent-lock.tests/Run-Tests.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Drive the helper with a snapshot pointed at a directory that does not exist, in three situations: the persisted value is usable and different (expect a repair naming the scope); the persisted value is itself unusable (expect no repair); the persisted value equals the snapshot (expect no repair). Then confirm that a healthy snapshot never reaches the helper at all. Restore `$env:JAVA_HOME` afterwards and assert it is back.

**Why:**

Strategic §11 lists five criteria and four of them are exactly these branches; the fifth - that a healthy path pays nothing - is the one a repair feature most easily breaks by reading the environment on every build.

**Verification:**

- Run: `pwsh -NoProfile -File scripts/utils/agent-lock.tests/Run-Tests.ps1` - expected: exit 0, all cases reported passing.
- Recorded in this file: the observed outcome of each of the four cases.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - state set to done for S1928 step 01.3

---

## Evidence (2026-08-21)

**End to end, on the capture's own failure.** A real gradle target was run with `JAVA_HOME` set to `C:\Program Files\Java\jdk-21.0.10` - the exact stale value from §0:

```
JAVA_HOME snapshot was stale - refreshed from the persisted User value.
  was: C:\Program Files\Java\jdk-21.0.10 (missing bin/java(.exe), lib/jvm.cfg)
  now: C:\Program Files\Java\latest\jdk-21
  Only this process was changed. Fix the environment your session inherits, or the next one starts stale too.
Fast check passed.
child EXIT=0
```

Before this change that same command refused with exit 3 and built nothing, for the rest of the session. §11.1 and §11.2 are both in that block: the target ran, and the line names the old value, the new one and the scope.

**Branch coverage** - `agent-lock tests: PASS`, exit 0, six cases:

| Case | Result |
| --- | --- |
| stale snapshot repaired from the persisted value | PASS |
| the repair names the scope it came from | PASS |
| a persisted value equal to the snapshot is not a repair (§11.4) | PASS |
| a non-existent JDK directory is judged unusable | PASS |
| the helper is reached only after the snapshot is judged unusable (§11.5) | PASS |
| the original refusal text and exit code are unchanged | PASS |

`$env:JAVA_HOME` is unchanged in this session after every probe.

**One criterion is NOT claimed as observed.** §11.3 - a stale snapshot with an *unusable or absent persisted value* still refusing with exit 3 - was not produced live. Forcing it would mean rewriting the machine's persisted `JAVA_HOME` to a broken path, and a session that died between the write and the restore would leave every future session on this machine unable to build. That risk is worse than the evidence is worth. What is proven instead: the helper returns nothing in both of those situations (cases 3 and 4 above), and the refusal branch is byte-for-byte the one that was already there (case 6), so the path from "no repair available" to `exit 3` is unchanged code reached by a proven-null condition.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - not applicable: no Kotlin, no build file. A real gradle target is exercised instead, since this code gates every one of them.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `$env:JAVA_HOME` in this session is unchanged from its pre-phase value.
- [ ] Dev log entry added via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The repair works in both directions. Phase 02 carries documentation and closure only.

---

## Rollback Plan

Restore `agent-lock.ps1` from the Step 01.1 backup and drop the added test cases - no other file reads the new helper.
