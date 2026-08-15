# Phase 03 - Cross-module pin gate

**Strategic spec:** [`../S1496_dependency-pinning-gaps.md`](../S1496_dependency-pinning-gaps.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Teach `GradleParser.ps1` to read library coordinates from both modules and fail on a divergence, and revive the `GradleParser` test suite that has been silently dead.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Phase 02 landed - `temp/S1496/diff-module-coords.ps1` reports `diverged: 0`, otherwise step 03.2 turns the gate red on the clean tree.
- [ ] Phase 01 landed - step 03.4 parses the `expectedBouncyCastleVersion` line that step 01.2 writes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift.tests/fixtures/wear/build.gradle.kts` | New | ≤ 30 |
| `scripts/doc-drift.tests/fixtures/app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/AppDatabase.kt` | New | ≤ 20 |
| `scripts/doc-drift/GradleParser.ps1` | Modified | ≤ 40 net |
| `scripts/doc-drift.tests/GradleParser.Tests.ps1` | Modified | ≤ 40 net |
| `scripts/doc-drift/pins.psd1` | Modified | ≤ 20 net |
| `docs/TECH_STACK.md` | Modified | ≤ 6 net |

> The `AppDatabase.kt` fixture and `docs/TECH_STACK.md` were added to this phase during execution - see the Step Log entries for step 03.1 and the plan correction.

---

## Steps

### Step 03.1 - Restore the missing wear fixture so the GradleParser suite runs at all

**Files:** `scripts/doc-drift.tests/fixtures/wear/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the fixture at `scripts/doc-drift.tests/fixtures/wear/build.gradle.kts`. It must declare `compileSdk = 35` and `targetSdk = 35` so they match `fixtures/app_v2/build.gradle.kts` and `Get-SharedModulePin` does not throw, a `minSdk`, and at least two `implementation(..)` coordinates - one shared with the app fixture at the same version, one declared only in wear. Before the edit, run `pwsh -NoProfile -File scripts/doc-drift.tests/Run-Tests.ps1` and record its output; after the edit, run it again and record that the six `GradleParser` test names now appear.

**Why:**

`Get-GradlePins` reads `wear/build.gradle.kts` through `Read-RequiredText`, which throws when the file is absent, so against the current fixture root the call throws at suite load and every `GradleParser` assertion is skipped rather than passing - the harness that strategic §11 criterion 6 requires as proof of the new rule is not running, and a rule proved by a suite that never executes is not proved at all.

**Verification:**

- `Glob` - `scripts/doc-drift.tests/fixtures/wear/build.gradle.kts` exists.
- `pwsh -NoProfile -File scripts/doc-drift.tests/Run-Tests.ps1` exits 0.
- Its output contains `library coordinate extractor produces lib.* keys`, which the pre-edit run did not.

**Status:** `[x]` done

---

### Step 03.2 - Fail Get-GradlePins on a diverging shared library coordinate

**Files:** `scripts/doc-drift/GradleParser.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `Get-GradlePins`, run `Get-LibraryCoords` over `$wearText` as well as `$appText`. For every coordinate key declared in both, compare the versions and throw when they differ, using the same message shape `Get-SharedModulePin` already uses: name the pin, the `app_v2` value and the `wear` value. A coordinate declared in only one module is not an error and must not appear twice in the returned pin set - keep the returned keys exactly as they are today, sourced from `app_v2`, so no existing `pins.psd1` record changes meaning.

**Why:**

Strategic §4 records that `$wearText` is already read and then never used for class-3 pins, which is why a library divergence is structurally invisible while the identical divergence in `compileSdk` fails, and ADR-3 chooses extending the parser over introducing an allowed-divergence registry that would today hold zero entries.

**Verification:**

- `Grep` - `Get-LibraryCoords -Text $wearText` present in `scripts/doc-drift/GradleParser.ps1`.
- `Grep` - the new throw message contains both `app_v2` and `wear`.
- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1` exits 0 on the current tree.

**Status:** `[x]` done

---

### Step 03.3 - Cover both outcomes of the new rule with tests

**Files:** `scripts/doc-drift.tests/GradleParser.Tests.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add two assertions. The first proves a coordinate shared by both fixtures at the same version resolves to that version without throwing. The second builds a temporary fixture root where the wear file declares a shared coordinate at a different version, asserts `Get-GradlePins` throws via `Assert-Throws`, and asserts the message names both versions via `Assert-Match`. Follow the helper vocabulary already used in the file and clean up any temporary directory the test creates.

**Why:**

Strategic §11 criterion 6 requires the suite to prove both directions of the rule, and a test that only covers the passing direction would not have caught the defect step 03.1 fixes, where the whole suite silently stopped running.

**Verification:**

- `pwsh -NoProfile -File scripts/doc-drift.tests/Run-Tests.ps1` exits 0.
- `Grep` - `Assert-Throws` matches at least twice in `scripts/doc-drift.tests/GradleParser.Tests.ps1`.
- Output names both new tests.

**Status:** `[x]` done

---

### Step 03.4 - Register the BouncyCastle expected version as a doc-drift pin

**Files:** `scripts/doc-drift/GradleParser.ps1`, `scripts/doc-drift/pins.psd1`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add a regex constant matching the `val expectedBouncyCastleVersion = "<v>"` line that Phase 01 step 01.2 wrote, expose it from `Get-GradlePins` as the pin key `bouncycastle-expected`, and add a `pins.psd1` record for that key with a `docs/TECH_STACK.md` matcher marked `required = $true`. Phase 04 writes the matching documentation line, so run `check-doc-vs-gradle.ps1` after that phase, not this one.

**Why:**

Strategic §11 criteria 1 and 7 both name a BouncyCastle version - one in the build file, one in the documentation - and without a pin record the two can drift apart unchecked, which is the exact failure S1489 had to clean up by deleting an unverifiable number from four README files.

**Verification:**

- `Grep` - `bouncycastle-expected` present in both `scripts/doc-drift/GradleParser.ps1` and `scripts/doc-drift/pins.psd1`.
- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Pin bouncycastle-expected` reports the pin as `MISSING` until Phase 04 lands the doc line, and names no other pin.

**Status:** `[x]` done

---

### Step 03.5 - Write the TECH_STACK lines that close the new pin

**Files:** `docs/TECH_STACK.md`
**Depends on:** Step 03.4

**Prompt for developer:**

> Pulled forward from step 04.1 during execution - see the Step Log for why it cannot stay in phase 04. Under "Network Protocol Notes" add the BouncyCastle line in the coordinate form the `bouncycastle-expected` matcher reads, and rewrite the SFTP line so it states the single shared version and that equality across modules is enforced. Correct the stale "JSch `0.2.26` (app) / `0.2.17` (wear)" claim under "Dependency Highlights" as well. Mention the BouncyCastle coordinate exactly once in the document, because the pin policy is `allMustMatch`.

**Why:**

Registering the pin in step 03.4 without its documentation line leaves `check-doc-vs-gradle.ps1` reporting `MISSING`, and `assert-doc-pin-drift.ps1` is a gate inside `post-change.ps1`, so phase 03 could not close its own mechanical closure while the pin stayed open.

**Verification:**

- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Pin bouncycastle-expected` exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-doc-pin-drift.ps1` exits 0.
- `Grep` - `different pinned versions` returns zero hits in `docs/TECH_STACK.md`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-09 - Step 03.1 Verification 3/3 PASS, after the fixture gap turned out to be two gaps, not one. Pre-edit `Run-Tests.ps1` exit 1 with an exception at suite load and no `GradleParser` test names in the output. Adding `fixtures/wear/build.gradle.kts` surfaced the next missing fixture immediately - `room-schema-version` reads `fixtures/app_v2/src/main/java/.../db/AppDatabase.kt`, also absent - so the suite had been dead through at least two separate pin additions. Both fixtures written; post-edit `Run-Tests.ps1` exit 0, `pass: 19 | fail: 0`, and `library coordinate extractor produces lib.* keys` now appears.
- 2026-08-09 - Step 03.2 Verification 3/3 PASS. Files: `scripts/doc-drift/GradleParser.ps1` (+12 LOC). `check-doc-vs-gradle.ps1` exit 0 on the tree left by phase 02.
- 2026-08-09 - Step 03.3 Verification 3/3 PASS. Files: `scripts/doc-drift.tests/GradleParser.Tests.ps1` (+29 LOC). `Run-Tests.ps1` exit 0, `pass: 21 | fail: 0`; the divergent case builds a throwaway fixture root, asserts the throw and asserts the message names `hilt-android`, `2.59` and `2.48`, then removes the copy.
- 2026-08-09 - Step 03.4 Verification 2/2 PASS. Files: `scripts/doc-drift/GradleParser.ps1`, `scripts/doc-drift/pins.psd1`. `check-doc-vs-gradle.ps1 -Pin bouncycastle-expected` reported exactly `MISSING | bouncycastle-expected`, total 1, naming no other pin - the state the plan predicted.
- 2026-08-09 - **Plan correction, applied during execution.** The plan put the TECH_STACK line in phase 04, but `assert-doc-pin-drift.ps1` runs inside `post-change.ps1`, and with the pin registered and its doc line missing that gate exits 1 - so phase 03 could not close its own closure step while waiting for phase 04. The doc line was pulled forward as step 03.5 and step 04.1 was removed from phase 04 rather than ticked, since the work did not happen there. Registering a pin and writing its documented value are one atomic change, not two phases.
- 2026-08-09 - Step 03.5 Verification 3/3 PASS. The document-registry loop at this boundary returned the `architecture` record (`docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`, `dev/NETWORK_SPECS.md` among its paths) on trigger `dependency`, which surfaced a third stale claim the plan had not listed: `docs/TECH_STACK.md:134` still read "JSch `0.2.26` (app) / `0.2.17` (wear)". Corrected with the other two. `dev/TECH_REQUIREMENTS.md:156` needs no edit - its `jsch` row carries the app_v2 value `0.2.26`, which did not change - and `dev/NETWORK_SPECS.md:5` names the fork without a version, so it stays accurate. `check-doc-vs-gradle.ps1 -Pin bouncycastle-expected` exit 0; `assert-doc-pin-drift.ps1` exit 0 with 23 pins passing, up from 22.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/doc-drift.tests/Run-Tests.ps1` exits 0 - `pass: 21 | fail: 0`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable, no product Kotlin touched - the one `.kt` added is a parser fixture under `scripts/`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`bouncycastle-expected` is already closed - step 03.5 wrote its `docs/TECH_STACK.md` line in this phase, because the gate that reads it runs inside `post-change.ps1` and would have blocked this phase's own closure. Phase 04 therefore no longer carries a TECH_STACK step; what remains there is the checker's README and the whole-ticket closure.

---

## Rollback Plan

Revert the four files. The fixture addition is safe to keep independently - it only restores a suite that was already meant to run.
