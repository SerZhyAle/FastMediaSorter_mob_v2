# Phase 06 - Build hot path

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Stop the three ways the build path wastes wall clock without anyone noticing: a hung gate that still reports PASS, a lock that refuses instead of waiting, and a backgrounding rule miscalibrated for the fast checks. Plus close the build-cache and `catalog_sync` items with a measurement.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - a timeout must be able to return "cannot verify", which needs the 0/1/2 exit contract.
- [ ] `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S1338 phase 06"` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 330 |
| `scripts/utils/lock-status.ps1` | Modified | ≤ 100 |
| `scripts/quality/assert-detekt.ps1` | Modified | ≤ 240 |
| `scripts/post-change.ps1` | Modified | ≤ 720 |
| `scripts/catalog_sync.ps1` | Modified | ≤ 90 |
| `CLAUDE.md` | Modified | n/a |
| `docs/BUILD_TEST_FAST_PATH.md` | Modified | n/a |

---

## Steps

### Step 06.1 - Put a ceiling on every gradle-backed gate

**Files:** `scripts/quality/assert-detekt.ps1`, `scripts/post-change.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> One `post-change.ps1` run hung for three hours and still reported PASS. Give every gradle-backed gate a 600 s ceiling - the observed tail is 5 runs over 300 s out of 311, so 600 s never fires on a healthy run. On expiry, kill the child process, release `BUILD.LOCK`, and exit 2 "cannot verify". A timeout must never produce PASS. Make the ceiling a parameter with 600 as the default so a genuinely long release run can raise it deliberately.

**Verification:**

- `Grep` - a timeout parameter with default 600 matches in `scripts/quality/assert-detekt.ps1`.
- `Grep` - the timeout path exits 2, not 0 and not 1.
- Force a timeout with a 1 s ceiling - exit code 2, `BUILD.LOCK` released afterwards, stdout contains neither `PASS` nor a green verdict.
- `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build` after the forced timeout - reports free.

**Step log:**

- Plan deviation, recorded rather than silent: the ceiling is a shared helper, `scripts/utils/process-timeout.ps1` (`Invoke-ProcessWithTimeout`), not code inlined into `assert-detekt.ps1`. Every gradle-backed gate needs the same behaviour and the invariant "a timeout is CANNOT VERIFY, never PASS" belongs in one place - strategic §9 asks for invariants written as invariants.
- `assert-detekt.ps1` gained `-TimeoutSeconds` (default 600) and now launches gradle through the helper. The kill is `taskkill /PID <pid> /T /F` - killing only the `gradlew.bat` launcher leaves the java build running while the caller reports a timeout.
- Forced 1 s ceiling: expected: exit 2, no PASS, lock free | actual: exit 2, `CANNOT VERIFY - the detekt gradle run exceeded 1s (elapsed 1153 ms) and was killed. Nothing was judged.`, and `lock-status.ps1 -Name Build` reported `absent (free)`.
- Wrapper sanity check against a trivial task: `gradlew.bat --version` through `Invoke-ProcessWithTimeout` returned exit 0 in 477 ms, so the argument passing survives the rewrite from `& $gradlew @tasks` to `Start-Process -ArgumentList`.
- Real run after the rewrite: expected: normal verdict | actual: 20.3 s, `assert-detekt: [app_v2] detekt reported new findings (exit 1)` - a real project-wide verdict on a dirty tree, not a timeout.
- Cost paid for the forced-timeout test: killing the launcher orphaned the gradle daemon, which kept building and then made the next run appear to hang for 15 minutes. Killed the daemon and re-ran clean. Worth knowing before anyone repeats the experiment - the ceiling kills the client, and gradle's daemon outlives it by design.

**Status:** `[x]` done

---

### Step 06.2 - Let callers wait for the lock instead of polling

**Files:** `scripts/utils/agent-lock.ps1`, `scripts/utils/lock-status.ps1`
**Depends on:** Step 06.1

**Prompt for developer:**

> 793 lock-status polls and 48 hand-rolled `until` loops exist purely because the lock refuses rather than waits. Add `-Wait` with a `-WaitTimeoutSeconds` to `Enter-AgentLock`, `Enter-BuildLockOrExit` and `lock-status.ps1`: block until the holder releases or the timeout expires, re-checking holder PID liveness on each iteration so a dead holder is reclaimed immediately rather than waited out. Keep the default behaviour unchanged - without `-Wait` the current single-shot refuse stands, because a caller that cannot afford to block must still be able to fail fast. On wait-timeout exit 2 "cannot verify", not 1.

**Verification:**

- `Grep` - `-Wait` and `WaitTimeoutSeconds` match in `scripts/utils/agent-lock.ps1`.
- Hold the lock from a second process, call with `-Wait -WaitTimeoutSeconds 5` - returns after the holder releases, or exits 2 at 5 s.
- Hold the lock, kill the holder process, call with `-Wait` - reclaims immediately rather than waiting the full timeout.
- Call without `-Wait` while held - refuses immediately, unchanged.

**Step log:**

- `Enter-AgentLock` gained `-Wait`, `-WaitTimeoutSeconds` (900) and `-PollSeconds` (2). The acquire attempt became a loop that re-reads the status each iteration, so PID liveness is re-judged on every poll rather than once at entry.
- `Enter-BuildLockOrExit` forwards both and distinguishes the two failures: a single-shot refusal stays exit 1 ("another build is running"), a wait that ran out of time exits **2** - nothing was built, which is a different fact from "the build failed".
- `lock-status.ps1` gained the same `-Wait` / `-WaitTimeoutSeconds` / `-PollSeconds`, so the 48 hand-rolled `until .. sleep` loops have a supported replacement. Wait-timeout there is exit 2 as well.
- Wait against a held lock: expected: returns at the timeout | actual: `acquired=False timedOut=True elapsed=6.1s` for a 6 s ceiling.
- Without `-Wait` while held: expected: immediate refusal, unchanged | actual: `acquired=False elapsed=0.00s`.
- Wait against a free lock: expected: immediate acquire | actual: `acquired=True elapsed=0.00s`.
- Dead holder (planted `BUILD.LOCK` with pid 999999): expected: reclaimed immediately, not waited out | actual: `Stale=True` then `acquired=True elapsed=0.02s` against a 60 s ceiling, and the lock read free afterwards.

**Status:** `[x]` done

---

### Step 06.3 - Re-scope the backgrounding rule

**Files:** `CLAUDE.md`, `docs/BUILD_TEST_FAST_PATH.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> CLAUDE.md section 6 requires backgrounding *every* gradle-backed `a.ps1` target. For the fast checks that is miscalibrated: the agent hand-polls with `cat` and `sleep` - about 1,297 polling turns and 81 minutes of literal `sleep` in a month - instead of letting the harness notify. Replace the blanket instruction with a duration threshold: above it backgrounding is required, below it forbidden. Derive the threshold from measured durations of `fk`, `fc`, `fu`, `dq` and `d` rather than picking a round number, and state both halves of the rule - the "forbidden below" half is the one that stops the polling. Record the measured durations in `docs/BUILD_TEST_FAST_PATH.md`, which owns the fast-path guidance. In the same edit add the `-Tests` filtering guidance: 163 of 344 fast-check unit runs used the full suite when a single-class filter was available.

**Verification:**

- `Grep` - CLAUDE.md section 6 contains a numeric duration threshold and both the "required above" and "forbidden below" halves.
- `Grep` - `-Tests` guidance matches in `docs/BUILD_TEST_FAST_PATH.md`.
- The measured durations backing the threshold are recorded in that document.

**Step log:**

- Threshold: **120 s**, which is the Bash tool's own foreground timeout - a real boundary, not a round number. Above it backgrounding is required (a foreground call gets force-migrated anyway and loses clean output capture); below it backgrounding is forbidden (it costs an extra turn and invites the `cat`/`sleep` polling the audit measured at ~1,297 turns and 81 min/month).
- Measured on this host via `temp/S1338/measure-fast-path.ps1`, warm daemon, configuration cache reused: `fg` **18.9 s**, `assert-detekt` **20.3 s**, `fk` **14.1 s**, `fc` **18.6 s**, `dq` **18.4 s**. Raw output in `temp/S1338/fast-path-timings.json` and `temp/S1338/timing-*.log`.
- **Measurement limitation, recorded not smoothed:** the `fk`/`fc`/`dq` runs stopped at a `kaptStandardDebugKotlin` failure from another ticket's in-flight Kotlin (`temp/S1338/timing-fk.log`), so they time configuration plus the compile graph but not packaging. `d`, `dav`, `r` and `fu` could not be measured at all for the same reason. They stay background-required on the standing observation about cold daemons plus the audit's ~44 s compile chain, and `docs/BUILD_TEST_FAST_PATH.md` names the gap and how to re-measure.
- CLAUDE.md section 6: expected: a numeric threshold and both halves | actual: the bullet reads "Above it, backgrounding is required .. Below it, backgrounding is forbidden" with the 120 s figure and the target lists on each side.
- `-Tests` guidance added to `docs/BUILD_TEST_FAST_PATH.md` section 5 with three worked forms (class, package wildcard, single method) and the two reasons it is the default: 163 of 344 fast-check unit runs used the full suite when a filter existed, and the full suite has been observed to OOM part-way and report a truncated pass (S1244).

**Status:** `[x]` done

---

### Step 06.4 - Settle the build-cache question

**Files:** `docs/BUILD_TEST_FAST_PATH.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> `gradle.properties:41` sets `org.gradle.caching=true`, but `settings.gradle.kts` disables the **local** build cache on Windows because Gradle 9.x fails packing certain outputs such as `RClassOutputJar`, and the resulting warning is filtered out of the log. So the property claims something the host does not deliver, and every flavor switch recompiles from zero across roughly 134 switches in the window. Determine which of the two is currently true on this host by measuring a repeated flavor switch with and without the local cache, then either re-enable it if Gradle 9.x no longer fails, or record the finding and correct the misleading property comment so the next reader is not misled. This item closes either way - strategic §10 accepts an explicit recorded decision not to land.

**Verification:**

- Both timing sets recorded in `temp/S1338/`.
- Either the local cache is enabled and a repeated flavor switch is measurably faster, or `gradle.properties` carries a corrected comment naming the Windows packing failure and the finding is in `## Last Audit`.

**Step log:**

- **Recorded decision not to land the re-enable**, which strategic §10 accepts. The comparison the step asks for - a repeated flavor switch with and without the local cache - needs a tree that builds, and every gradle target in this window failed at `kaptStandardDebugKotlin` from another ticket's in-flight Kotlin. Measuring cache behaviour against a build that dies before packaging would produce a number that means nothing.
- Direct evidence of the current state was captured anyway, from gradle itself rather than by inference: every run prints `Using the build cache is enabled, but no build caches are configured or enabled.` (`temp/S1338/timing-fk.log`). So `org.gradle.caching=true` is inert on this host - `settings.gradle.kts:16` sets `local { isEnabled = !isWindowsHost }` and no remote cache is configured.
- `gradle.properties` comment extended: it now states the consequence (the property is INERT on a Windows host, every flavor switch recompiles from zero), names that log line as the setting working as configured rather than a warning to chase, and points at this step for the conditions under which re-enabling can be judged.
- Timing artifacts that do exist: `temp/S1338/fast-path-timings.json`, `temp/S1338/timing-{fk,fc,dq}.log`.

**Status:** `[x]` done - as an explicit recorded decision, per strategic §10.

---

### Step 06.5 - Make `catalog_sync` no-op when the index is current

**Files:** `scripts/catalog_sync.ps1`
**Depends on:** Step 06.4

**Prompt for developer:**

> `catalog_sync.ps1` unconditionally calls `scan.ps1` then `render.ps1` on every invocation - 12.9 s across 280 runs, the largest non-gradle gate - even when nothing changed. Add an up-to-date check: if no source file under the module's scan roots has an mtime newer than the generated `dev/CATALOG/<module>.jsonl`, skip both calls and report a no-op. Add `-Force` to bypass the check. This enforces the once-per-ticket rule mechanically instead of by instruction.

**Verification:**

- Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` twice in a row - the second run reports a no-op and completes in under 1 s.
- `touch` a `.kt` file, run again - the sync runs in full.
- Run with `-Force` on an unchanged tree - the sync runs in full.

**Step log:**

- `catalog_sync.ps1` gained an up-to-date check ahead of both calls: it compares the newest `.kt`/`.java` mtime under `<module>/src` (excluding `build`, `.gradle`, `.kotlin`) against the OLDER of the two generated artifacts - `dev/CATALOG/<module>.jsonl` and `.md`. A stale render behind a fresh index still triggers the pass.
- `-Force` bypasses the check.
- Two runs back to back on an unchanged tree: expected: second is a sub-1 s no-op | actual: **0.8 s** and **0.7 s**, both reporting `up to date (app_v2) - newest source .. <= index ..`. Both runs no-op because the index was already current when the step started.
- After touching `app_v2/src/main/java/com/sza/fastmediasorter/utils/ViewExtensions.kt`: expected: full sync | actual: full sync, **66.8 s**, `2399 records` rendered.
- `-Force` on the now-current tree: expected: full sync | actual: full sync, **66.0 s**.
- The saving is larger than the audit's 12.9 s figure on this host: a redundant call costs ~66 s here, because a full refresh recomputes git last-touched across the module.

**Status:** `[x]` done

---

### Step 06.6 - Fix the self-contradiction about parallel builds

**Files:** `CLAUDE.md`
**Depends on:** Step 06.5

**Prompt for developer:**

> CLAUDE.md line 77 reads "Parallel Subagents: Concurrently run independent tasks (e.g. build + search)". Rule 23 blocks a second concurrent gradle invocation mechanically via `temp/BUILD.LOCK`, and the owner banned it. The example authorises what the lock refuses, in always-on text. Keep the parallel-subagent guidance and replace the example with one that does not involve a build, then name the constraint explicitly: never more than one gradle-backed invocation at a time.

**Verification:**

- `Grep` - CLAUDE.md line 77 no longer contains `build + search`.
- `Grep` - the one-gradle-invocation constraint is stated in the same bullet and references `BUILD.LOCK`.

**Step log:**

- The example is replaced, not the guidance: parallel subagents stay encouraged, now illustrated with "a catalog query alongside a docs sweep, or two independent file searches" - work that cannot reach gradle.
- Constraint stated in the same bullet: expected: names `BUILD.LOCK` | actual: "**Never more than one gradle-backed invocation at a time** - a build, a compile check, detekt, the unit suite. Rule 23 refuses the second one mechanically via `temp/BUILD.LOCK`".
- `build + search`: expected: zero hits | actual: zero hits in `CLAUDE.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done` - 06.4 as an explicit recorded decision, per strategic §10.
- [x] `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` re-run - exit 0, 255 scripts (the new `process-timeout.ps1` plus the changed lock and catalog-sync signatures).
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - exit 0, `fail: 0`.
- [ ] `pwsh -NoProfile -File ./a.ps1 fk` - **cannot verify, not a failure of this phase.** `fk` exits 1 at `kaptStandardDebugKotlin` with no message, from another ticket's in-flight Kotlin on the shared working tree (`temp/S1338/timing-fk.log`). The same failure predates this phase - phase 04's log already recorded the settings-doc gate returning CANNOT-VERIFY for it. Nothing in this phase touches Kotlin, product code, or the build graph: the changes are a process-timeout helper, two lock functions, `catalog_sync.ps1`, two docs and two CLAUDE.md bullets. Re-run on a green tree.
- [x] Dev log entry added covering the phase as one logical change.
- [x] Document registry: `developer-operations` covers `docs/BUILD_TEST_FAST_PATH.md`, `repository-rules` covers `CLAUDE.md` - both acknowledged at closure; `validate.ps1` and `generate.ps1 -Check` exit 0.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.
- [x] `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1` released by the closure.

## Phase-boundary audit

- **P1, found and fixed inside the phase.** `lock-status.ps1`'s new wait-timeout branch had a bare `Write-Error` before `exit 2` under `$ErrorActionPreference = 'Stop'`, so the process would have reported 1 instead of 2 - the exact Rule 7 defect S1070 exists for, in the very step that adds a "cannot verify" path. Caught by `assert-exit-contract.ps1 -Gate`, which went red on the first run after the edit. Fixed by building the message first and passing `-ErrorAction Continue`; the gate is green.
- The `-Wait` loop polls rather than watching for a filesystem event. At 2 s it costs one status read per poll against a 900 s ceiling - cheaper than the 793 external polls it replaces. P3.
- `Invoke-ProcessWithTimeout` kills with `taskkill /T /F`, which does not reach the gradle daemon - the daemon is started detached and outlives the client by design. Observed directly: after the forced timeout the daemon kept building and made the next run look hung for 15 minutes. Documented in step 06.1's log so the next reader does not re-derive it. Not fixable here - killing the daemon on every timeout would be worse. P2.
- The `catalog_sync` up-to-date check judges mtimes, not content hashes. A file touched without being changed forces a 66 s rebuild; a file changed without its mtime moving would be missed. The first is harmless, the second cannot happen through any normal edit path. P3.
- No unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The `-Wait` mode changes lock semantics for every caller in the repo - phase 10's full-flavor KSP proof is the first heavy user of it. The CLAUDE.md line 77 fix and the section 6 re-scope are the two edits S1340 must not re-litigate when it compresses the rules; both are recorded as fixes, not compressions.

---

## Rollback Plan

Each step is independent. `-Wait` is opt-in so reverting it changes no default behaviour. The `catalog_sync` no-op reverts to unconditional running. No product code and no build output touched.
