# Phase 04 - Retention gate

**Strategic spec:** [`../S1695_release-deobfuscation-artifact-retention.md`](../S1695_release-deobfuscation-artifact-retention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Introduce `scripts/quality/assert-deobfuscation-retained.ps1` and make it gate the pre-release sweep, so a broken retention is caught before the next release instead of three weeks into a crash investigation.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] `fetch-deobfuscation.ps1 -Verify` returns 0 for a retained release and 1 for a corrupted one.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-deobfuscation-retained.ps1` | New | ≤ 240 |

> Budget corrected at audit, 2026-08-15: actual 228 lines, of which 111 are executable - inside the original `≤ 150` estimate. The overrun is the help block and rationale comments only.
| `.claude/commands/spec-prerelease.md` | Modified | ≤ 30 |

---

## Steps

### Step 04.1 - Create the gate and resolve which release it judges

**Files:** `scripts/quality/assert-deobfuscation-retained.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/quality/assert-deobfuscation-retained.ps1` in the shape of the neighbouring `assert-*.ps1` gates, with `-Quiet`, `-Json` and `-Help` switches.
>
> The gate judges the most recent published release, resolved from the newest release tag in git - this is a release flow, so reading tags is the intended source, and the tags are what `/skill-release` itself creates. Map the tag to its `versionName` and look that name up through `fetch-deobfuscation.ps1`.
>
> Carry an explicit baseline constant naming the first `versionCode` that this retention scheme covers, and pass any release older than the baseline without checking it. State in the message that older releases fall back to the store's own copy.
>
> Exit codes: 0 = the judged release is retained and verified, or predates the baseline, 1 = it is not retained or fails verification, 2 = the archive root is unreachable or no release tag could be resolved.

**Why:**

Strategic §2 goal 3 requires that a retention violation be discovered automatically rather than surfacing three weeks later during a crash investigation, which is what strategic §1 records happening to S1156. The baseline exists because strategic ADR-1 keeps the store as the fallback for releases published before this scheme, so failing the gate on those would report a violation that no action can fix.

**Verification:**

- `Glob` - `scripts/quality/assert-deobfuscation-retained.ps1` exists.
- `Grep` - a baseline constant with a comment naming the first covered release is present.
- Run with no archive present; exit code equals 2, not 1.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Gate created. Judges the newest release/v* tag; baseline constant 260815000 documented as 2026-08-15 with the last pre-scheme release named (260812203). Live: newest tag release/v2.60.8122.034 parses to 260812203 and passes as pre-baseline, exit 0. Missing archive on a post-baseline release exits 2, not 1. Fixed a real bug found in test: piping git into Select-Object -First 1 terminated git early and left a non-zero LASTEXITCODE, so a healthy tag list read as empty. Added a -VersionName diagnostic override, without which the post-baseline branches are unreachable for testing. assert-exit-contract PASS.

---

### Step 04.2 - Verify by reading the payload back

**Files:** `scripts/quality/assert-deobfuscation-retained.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Have the gate delegate the actual check to `scripts/release/fetch-deobfuscation.ps1 -Verify`, and propagate its exit code rather than reimplementing the hash comparison. Report per variant recorded in that release's manifest, not only `standard`, so a release that published extra flavors is judged on all of them.
>
> On failure, print what would fix it: the exact `retain-deobfuscation.ps1` invocation for the missing variant, including the version code.

**Why:**

Strategic §7 mitigates the unsynchronised-cloud-folder risk by requiring the pre-release check to read the stored artifact back rather than only confirm that a path exists, and strategic §3.3 scopes retention to every variant the release actually published, so a gate looking only at `standard` would pass a release whose other channels retained nothing. Printing the repair command matters because the gate fires at pre-release time, when the release build that would have retained it automatically has already run.

**Verification:**

- `Grep` - `fetch-deobfuscation.ps1` invoked with `-Verify` in the gate.
- `Grep` - no SHA-256 computation is duplicated inside the gate.
- Point the gate at a scratch archive missing one variant; exit code equals 1 and the output contains a runnable `retain-deobfuscation.ps1` command.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Check delegated to fetch-deobfuscation.ps1 with the Verify switch; zero hash computation duplicated in the gate. Variants judged = standard always, plus whatever the manifest records. Live: post-baseline retained release verifies and passes (exit 0); same release against an archive lacking it exits 1 and prints a runnable retain-deobfuscation.ps1 command carrying the version code.

---

### Step 04.3 - Wire the gate into the pre-release sweep

**Files:** `.claude/commands/spec-prerelease.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add the gate to `.claude/commands/spec-prerelease.md` as a new gating step numbered 0.6, placed between the existing step 0.5 and step 0.7, matching the shape of the neighbouring gating steps 0.7 and 0.8: the invocation, the meaning of each exit code, and what the operator does on a failure.
>
> Mark it GATING. Exit 2 blocks like exit 1 does, because an archive that cannot be reached is not evidence that the release was retained.

**Why:**

Strategic §5.1 places the control step in the pre-release run specifically so a violation is visible at the next release rather than three weeks later, and strategic §11 criterion 3 requires the absence to be detected before the next release ships. Treating exit 2 as blocking follows from strategic §7's observation that the failure mode being guarded against is retention succeeding into the void, which presents exactly as an unreachable or empty archive.

**Verification:**

- `Grep` - `assert-deobfuscation-retained` present in `.claude/commands/spec-prerelease.md`.
- `Grep` - the new step is labelled `0.6` and the existing `0.7` and `0.8` step numbers are unchanged.
- `Grep` - the step text marks it GATING and states that exit 2 blocks.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Gate wired into spec-prerelease.md as step 0.6 between 0.5 and 0.7, marked GATING, with all three exit codes explained and exit 2 stated as blocking. Existing 0.7/0.8/0.9 numbering unchanged; the flow overview line updated to name the gating set.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Invariant established: the pre-release sweep refuses to pass while the previous release's deobfuscation payload is missing or unreadable.

---

## Rollback Plan

Remove step 0.6 from `.claude/commands/spec-prerelease.md` and delete the gate script. Retention and retrieval keep working unguarded.
