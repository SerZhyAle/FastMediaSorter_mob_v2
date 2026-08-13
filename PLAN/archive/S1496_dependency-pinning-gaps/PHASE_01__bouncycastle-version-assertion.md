# Phase 01 - BouncyCastle version assertion

**Strategic spec:** [`../S1496_dependency-pinning-gaps.md`](../S1496_dependency-pinning-gaps.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Replace the dead commented `resolutionStrategy` block in `app_v2/build.gradle.kts` with a configuration-time assertion that fails the build when the transitively resolved BouncyCastle version stops matching the expected one.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `research/01__bouncycastle-actual-resolution.md` read - it fixes the expected coordinate as `org.bouncycastle:bcprov-jdk18on:1.75`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 30 net |

> `app_v2/build.gradle.kts` is ~1657 lines, above the 500-line backup threshold - step 01.1 takes the backup first.

---

## Steps

### Step 01.1 - Back up the build file and delete the dead resolutionStrategy block

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/build.gradle.kts` to `temp/S1496/build.gradle.kts.<yyyyMMdd-HHmmss>.bak` before editing, per CLAUDE.md Rule 5. Then delete the commented block at the tail of the file - the `// TEMPORARILY DISABLED: BouncyCastle resolutionStrategy (was needed for PDFBox)` header and the six commented lines under it that force `bcprov-jdk15to18`, `bcpkix-jdk15to18` and `bcutil-jdk15to18` at 1.72. Also correct the trailing comment on the `smbj` dependency line, currently reading `// Network - SMB (uses BouncyCastle jdk15to18:1.72 via resolutionStrategy)`, to state that SMBJ pulls `bcprov-jdk18on` transitively and that the version is asserted below.

**Why:**

Strategic ADR-1 records that the block forces three coordinates that do not exist in the resolved graph - SMBJ 0.12.1 pulls the `jdk18on` line, not `jdk15to18` - so `force()` would match nothing even uncommented, and leaving it in place preserves a false sense of protection while the comment names a version that is not in the APK.

**Verification:**

- `Grep` - `jdk15to18` returns zero hits in `app_v2/build.gradle.kts`.
- `Grep` - `TEMPORARILY DISABLED` returns zero hits in `app_v2/build.gradle.kts`.
- `Glob` - a `temp/S1496/build.gradle.kts.*.bak` file exists.

**Status:** `[x]` done

---

### Step 01.2 - Add the expected-version constant and the resolution assertion

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add, at the position the deleted block occupied, a named constant holding the expected BouncyCastle version and a `configurations.all { resolutionStrategy.eachDependency { .. } }` rule that inspects every requested dependency whose group is `org.bouncycastle` and throws `GradleException` when the requested version differs from the constant. Name the constant so the doc-drift parser can find it on a single line - use exactly `val expectedBouncyCastleVersion = "1.75"`. The failure message must name the artifact, the expected version, the version that actually arrived, and instruct the reader to re-check the `org/bouncycastle/**` entries in `packagingOptions` before raising the constant. Do not use `force()` anywhere in the rule.

**Why:**

Strategic ADR-2 requires the version to be checked rather than frozen, because a hard `force` would cut off the security updates that arrive with an SMBJ bump, while §3.2 records that a static text gate cannot see a transitive version at all, so the check has to live inside the build itself.

**Verification:**

- `Grep` - `val expectedBouncyCastleVersion = "1.75"` matches exactly once in `app_v2/build.gradle.kts`.
- `Grep` - `eachDependency` matches at least once in `app_v2/build.gradle.kts`.
- `Grep` - `force(` returns zero hits in `app_v2/build.gradle.kts`.
- `Grep` - the failure message text contains `packagingOptions`.

**Status:** `[x]` done

---

### Step 01.3 - Prove the assertion passes and the resolved graph is unchanged

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.2

**Prompt for developer:**

> Re-run the dependency report used in research 01 through `temp/S1496/resolve-deps.ps1`, which takes `temp/BUILD.LOCK` per Rule 23, and confirm the runtime classpath still lists `org.bouncycastle:bcprov-jdk18on:1.75` under `com.hierynomus:smbj:0.12.1`. Then temporarily change the constant to a wrong value, confirm the build fails with the new message, and restore the correct value. Record both outcomes as `expected: X | actual: Y` in the step notes.

**Why:**

Strategic §11 criteria 2 and 3 require both halves to be observed - that the APK's crypto content did not change, and that a version change actually stops the build - and CLAUDE.md section 12 forbids claiming a gate works without running the command that proves it.

**Verification:**

- `Grep` - `org.bouncycastle:bcprov-jdk18on:1.75` present in the regenerated `temp/S1496/app_v2-deps.txt`.
- `Grep` - `jdk15to18` returns zero hits in the regenerated `temp/S1496/app_v2-deps.txt`.
- Step notes record the deliberate-mismatch run with its non-zero exit code and the message text.

**Status:** `[x]` done

---

## Step Log

- 2026-08-09 - Step 01.1 Verification 3/3 PASS. Files: `app_v2/build.gradle.kts` (-8 LOC). Backup at `temp/S1496/build.gradle.kts.20260809-210800.bak`. expected: 0 hits `jdk15to18` | actual: 0. expected: 0 hits `TEMPORARILY DISABLED` | actual: 0.
- 2026-08-09 - Step 01.2 Verification 4/4 PASS. Files: `app_v2/build.gradle.kts` (+17 LOC). expected: 1 hit `val expectedBouncyCastleVersion = "1.75"` | actual: 1. expected: 0 hits `force(` | actual: 0.
- 2026-08-09 - **Phase-boundary audit, P1 finding, fixed in phase.** `configurations.all` applied the assertion to every configuration, test classpaths included, and Robolectric 4.11.1 requests `bcprov-jdk18on:1.76` on `standardDebugUnitTestRuntimeClasspath` - the assertion failed that edge, so `.\a.ps1 fu` would have broken while the debug build stayed green. Fix: the rule now runs through `configurations.matching { !it.name.contains("test", ignoreCase = true) }`, because nothing on a test classpath reaches the APK. Re-verified with `temp/S1496/check-all-configs.ps1`: 0 failed bouncycastle edges; the test classpath resolves `1.75 -> 1.76` cleanly; and `standardRelease`, `noLegalDebug`, `vrDebug`, `liteDebug`, `photosDebug`, `legacyDebug` each carry exactly `bcprov-jdk18on:1.75`, so no flavor or the release build can be surprised later. `.\a.ps1 dq` exit 0 after the fix.
- 2026-08-09 - Step 01.3 Verification 3/3 PASS, after one oracle correction. Positive: `temp/S1496/resolve-deps.ps1` exit 0, runtime classpath still `org.bouncycastle:bcprov-jdk18on:1.75` under `com.hierynomus:smbj:0.12.1`, 0 hits `jdk15to18`. Negative, first attempt INVALID: `gradlew :app_v2:dependencies` renders a failed node as `bcprov-jdk18on:1.75 FAILED` and still exits 0, swallowing the exception message entirely - the report task is not a valid oracle for "the build stops". Negative, re-run with a task that consumes the classpath: version literal patched to `1.99`, `.\a.ps1 dq` exit 1 with `Could not resolve org.bouncycastle:bcprov-jdk18on:1.75 .. > BouncyCastle version drift: org.bouncycastle:bcprov-jdk18on resolved to 1.75, expected 1.99. Re-check the org/bouncycastle/** entries in packagingOptions ..`; literal restored to `1.75`, `.\a.ps1 dq` exit 0.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0 (2026-08-09 21:09).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated - not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

`app_v2/build.gradle.kts` now carries `expectedBouncyCastleVersion` on a single line, in a shape Phase 03 step 03.4 registers as a doc-drift pin. Do not reformat that line across several lines.

---

## Rollback Plan

Restore `temp/S1496/build.gradle.kts.<timestamp>.bak` over `app_v2/build.gradle.kts`. No data migration or user-facing surface changed.
