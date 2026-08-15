# Phase 01 - Detector test harness

**Strategic spec:** [`../S1195_lint-strict-but-never-run-72-unbaselined-errors.md`](../S1195_lint-strict-but-never-run-72-unbaselined-errors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - enabling phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 3 / 3

---

## Objective

Make `:lint-rules:test` actually execute, and make it execute where a failure is seen. Today the five tests in `CustomLintRulesTest.kt` are run by no script, no `a.ps1` target and no CI job - the same "configured strictly, never run" failure this ticket exists to fix, one level down. Every later phase's verification predicate is worthless until this holds.

No detector logic changes here.

---

## Prerequisites

- [x] `scripts/utils/lock-status.ps1 -Name Build` shows no live build.
- [x] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1195 phase 01"`.
- [x] `lint-rules/bin/` is stale build output, not source. Never edit it, never add it to a source set. If the test task resolves anything from it, stop and report.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.github/workflows/android-ci.yml` | Modified | +1 task in the existing gradle invocation |
| `a.ps1` | Modified | +1 target row |
| `scripts/builders/check-lint-rules.ps1` | New | ≤ 90 |

---

## Steps

### Step 01.1 - Prove the task runs at all today

**Files:** none - measurement only
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File ./a.ps1` is not applicable yet; invoke the task directly through the build lock: acquire `temp/BUILD.LOCK` via `scripts/utils/agent-lock.ps1`, then `./gradlew.bat :lint-rules:test --stacktrace`, release the lock. Record the verdict and the per-test results from `lint-rules/build/reports/tests/test/index.html`. The module pins `com.android.tools.lint:lint-tests:32.2.1` against a compileSdk-36 project, so a compile or API-mismatch failure is a plausible outcome - if it fails, fix the module's test dependencies before proceeding, and record what was wrong. A green run with five passing tests is equally plausible; either way the result is a fact this phase must establish, not assume.

**Verification:**

- `./gradlew.bat :lint-rules:test` exit code recorded, with `BUILD SUCCESSFUL` or the precise failure.
- `lint-rules/build/test-results/test/*.xml` exists and lists 5 test cases.
- Result written into this file under "Handoff Notes to Next Phase".

**Status:** `[x]` done

---

### Step 01.2 - Give the task a project-native entry point

**Files:** `scripts/builders/check-lint-rules.ps1`, `a.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `scripts/builders/check-lint-rules.ps1` running `:lint-rules:test`, modelled on `scripts/builders/check-standard-fast.ps1`: `-NoProfile`-safe, acquires `temp/BUILD.LOCK` through `Enter-BuildLockOrExit` and releases it through `Exit-AgentLock` on both paths (Rule 23), accepts an optional `-Tests <filter>` passed straight to gradle, and documents its exit codes in the header. Honour Rule 7 on reachable exit codes - `Write-Error $msg -ErrorAction Continue` before any `exit N` where N is not 1. Register it in `a.ps1` as target `flr` ("fast lint rules") next to the existing `fk` / `fu` rows.

**Verification:**

- `pwsh -NoProfile -File ./a.ps1 flr` runs the task and returns exit code 0 (or the honest failure from 01.1).
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` passes for the new script.
- `Grep` - `check-lint-rules.ps1` appears exactly once in `a.ps1`.

**Status:** `[x]` done

---

### Step 01.3 - Gate it in CI

**Files:** `.github/workflows/android-ci.yml`
**Depends on:** Step 01.2

**Prompt for developer:**

> In the `verify` job, append `:lint-rules:test` to the single gradle invocation so it reads `./gradlew lintStandardDebug testStandardDebugUnitTest assembleStandardDebug :lint-rules:test --continue --stacktrace`. Keep it in the same call - the job's comment block explains that one compile is shared across lint, tests and assemble, and a separate step would pay a second cold setup. Extend that comment block to name the new task and why it is there: the detectors are what lint enforces, so a detector regression must fail the same job that runs lint. Add `lint-rules/build/reports/tests/` to the existing "Upload unit test results" artifact paths.

**Verification:**

- `Grep` - `:lint-rules:test` matches exactly once in `.github/workflows/android-ci.yml`, inside the `verify` job's run line.
- `Grep` - `lint-rules/build/reports/tests/` present in the artifact upload paths.
- YAML parses: `pwsh -NoProfile -Command "python -c \"import yaml,sys; yaml.safe_load(open('.github/workflows/android-ci.yml'))\""` exits 0, or an equivalent parse check.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File ./a.ps1 flr` is green, exit code 0, and the run is cited.
- [x] `:lint-rules:test` is reachable from both CI and a local `a.ps1` target.
- [x] `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Mixed -ScopeToFile ..` closure run for the touched files.
- [x] Dev log entry added for the phase (one entry, not one per file).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

**`:lint-rules:test` compiles and passes as-is.** The phase's stated biggest unknown - `lint-tests:32.2.1` against a compileSdk-36 project, never once executed - turned out to be a non-issue. No dependency change was needed.

- First run: `expected: unknown | actual: BUILD SUCCESSFUL, 5/5 tests passing, 19s`. Log: `temp/S1195/phase01-first-run.log`.
- The loop is fast: a full suite run is ~20-26s, and `-Tests '*Filter*'` narrows it further. Phases 02-04 used it as the primary predicate exactly as planned.

Step ordering deviation, deliberate: **01.2 was done before 01.1**. Step 01.1 as written calls `./gradlew.bat :lint-rules:test` directly under a hand-rolled lock, which contradicts Rule 23's "every gradle-backed call goes through a script that takes `temp/BUILD.LOCK`". Writing `check-lint-rules.ps1` first and measuring through it satisfies both, and the measurement is identical.

One environment trap worth recording: the Bash tool's `JAVA_HOME` is stale (`jdk-21.0.10`, which does not exist on this machine), so `gradlew` aborts before reading `gradle.properties`. The user-scope `JAVA_HOME` is valid. **Run gradle-backed scripts through the PowerShell tool, not Bash.** This is not a project defect and needs no fix here.

Two facts Phases 02-04 depend on, both learned the hard way from the harness:

- `TestLintTask` replays every case in extra test modes. The **parenthesized** mode wraps expressions, so any receiver/argument inspection must unwrap `UParenthesizedExpression` or the verdict differs between modes and the test fails.
- The **IMPORT_ALIAS** mode renames imported types at the use site. Any matching on an identifier or annotation short name breaks. Resolve to a qualified name - the harness enforces this, which is precisely the discipline these detectors lacked.

---

## Rollback Plan

Revert the CI line and remove the new script and its `a.ps1` row. No product code is touched, so rollback cannot affect the app.
