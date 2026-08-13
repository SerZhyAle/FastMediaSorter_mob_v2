# Phase 10 - kapt to KSP migration

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 06
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-08-02
**Completed:** 2026-08-03

---

## Blocker cleared (2026-08-02)

S1317 landed its in-flight Kotlin and moved to `BlockNeedUserTest`, so `a.ps1 fk` returns 0 and the compile baseline the phase needs exists. The blocker below is kept as written rather than deleted - it is the record of why the phase waited, and its central claim held: the migration was measured against a compiling tree, not migrated first and measured later.

---

## Blocker (2026-08-01, resolved)

`:app_v2:kaptStandardDebugKotlin` fails on the working tree, so this phase cannot start.

- `pwsh -NoProfile -File ./a.ps1 fk`: expected exit 0 | actual **1**, `Execution failed for task ':app_v2:kaptStandardDebugKotlin' > A failure occurred while executing KaptWithoutKotlincTask$KaptExecutionWorkAction`. The cause is masked - `correctErrorTypes = true` turns a real stub error into a stackless failure, and the build log carries no `error:` line to name the offending class.
- Not caused by this ticket: S1338 has changed no `.kt` file, no `build.gradle.kts` and no `gradle.properties`. `.\a.ps1 bf` reads the same failure out of `temp/build_debug_20260801_005349.log`, which predates every edit in this session, and phase 06's log already recorded the same red from S1317's in-flight work. S1317 is `Tactical`, so this is already ticketed and is not parked as a new finding.
- Why this blocks the phase rather than being worked around: **step 10.1 is a measurement step**. A kapt-to-KSP migration is judged by a before/after compile timing and by a green build per flavor, and neither exists on a tree that does not compile. Migrating first and measuring later is exactly the substitution package A was written to stop.
- The second prerequisite is also unmet: phase 07 carries two named residues, so "every other phase Done" does not hold either.
- To unblock: S1317 lands or reverts its in-flight change, `a.ps1 fk` returns 0, then this phase starts at step 10.1. The unmask recipe for the masked kapt failure is in the agent memory note `kapt-npe-unmask`, if S1317 needs it.

---

## Objective

Move `app_v2`'s three annotation processors - Hilt, Room and Glide - from kapt to KSP, one processor at a time, each behind a full-flavor build proof.

---

## Prerequisites

- [ ] Every other phase is ✅ Done. Strategic §5 requires this to land last and alone; strategic §7 names it the only item that can break the build.
- [ ] Phase 01's baseline exists, so the claimed ~35% of the 44 s compile chain can be measured rather than assumed.
- [ ] `temp/BUILD.LOCK` free and no sibling agent session mid-build.
- [ ] `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S1338 phase 10"` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | n/a |
| `gradle.properties` | Modified | n/a |
| `docs/TECH_STACK.md` | Modified | n/a |
| `dev/TECH_REQUIREMENTS.md` | Modified | n/a |
| `app_v2/proguard-rules.pro` | Modified (added by the phase-boundary audit) | n/a |

> `app_v2/build.gradle.kts` is well over 500 lines - back it up to `temp/S1338/` before the first edit (Rule 5).

---

## Steps

### Step 10.1 - Record the compile baseline and check the precedent

**Files:** `temp/S1338/compile-baseline.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Time three cold and three warm `:app_v2:compileStandardDebugKotlin` runs and record where the 44 s goes, separating kapt stub generation and javac annotation processing from Kotlin compilation itself. The claim to test is ~35% in stub generation plus annotation processing. Then check the precedent honestly: `wear/build.gradle.kts` applies KSP for the Hilt compiler only - it has no Room and no Glide dependency at all. So "wear already did it" covers one of the three processors, not three, and Room and Glide have no in-repo precedent. Record that finding before starting, because it changes the risk profile the strategic spec assumed.

**Verification:**

- `Glob` - `temp/S1338/compile-baseline.json` exists with the split timings.
- The wear precedent finding is recorded in `## Last Audit`.

**Step log:**

- `temp/S1338/compile-baseline.json` written by `temp/S1338/measure-compile.ps1`, three rounds, exit **0**.
- **The first measurement attempt was wrong and is recorded because the failure mode is reusable.** Deleting the kapt stub and generated-source directories to force a cold run does not work on this host: Gradle's file-system watching kept a stale VFS snapshot and reported `13 actionable tasks: 13 up-to-date` with the stub directory physically gone, three rounds in a row at ~2 s each. A 2 s "cold" compile is not a plausible number and that is the only reason it was caught. Isolation is by `--rerun` per task instead, which invalidates the requested task alone.
- Per-task medians over three rounds, standard debug: `kaptGenerateStubsStandardDebugKotlin` **52.28 s**, `kaptStandardDebugKotlin` **21.41 s**, `compileStandardDebugKotlin` **69.72 s**. Annotation chain **73.69 s of 143.41 s = 51.4%**.
- **Read that 51.4% against the spec's ~35%, not as a refutation of it.** `--rerun` forces a full non-incremental run of each task, while strategic §4 package C's "~35% of the measured 44 s compile chain" describes the everyday incremental chain. The two measure different things, so the honest statement is narrower: on a full rebuild the annotation-processing chain is about half the wall clock, and stub generation alone is the larger half of that. Stub generation is precisely what KSP removes, so the direction of the claim holds even though its number does not transfer.
- The three rounds trend downward hard - stub generation 80.82 -> 52.28 -> 38.01 s - so the daemon was still warming through round 1. The median is reported rather than the mean for that reason, and the after-measurement in step 10.6 must use the same three-round shape or it will compare a warm run against a cold one.
- **The wear precedent is one processor of three, and the strategic spec's risk profile is correspondingly optimistic.** `wear/build.gradle.kts` applies KSP and declares exactly one processor: `ksp("com.google.dagger:hilt-android-compiler:2.59")`. It has no Room and no Glide dependency at all. So "wear already did it" covers Hilt; Room and Glide have **no in-repo precedent**, and Glide is the one that also changes artifact coordinates rather than just the configuration name.

**Status:** `[x]` done

---

### Step 10.2 - Migrate Hilt

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 10.1

**Prompt for developer:**

> Back up the build file first. Apply `com.google.devtools.ksp` to `app_v2` - the plugin is already declared at the root with `apply false` and pinned at 2.3.8 against Kotlin 2.2.10. Convert the two Hilt processor dependencies from `kapt` to `ksp`: `com.google.dagger:hilt-android-compiler:2.59` and `androidx.hilt:hilt-compiler:1.2.0`, plus the `kaptAndroidTest` variant. Leave Room and Glide on kapt for now - both plugins can coexist. This is the processor with an in-repo precedent, so it goes first.

**Verification:**

- `Grep` - `ksp("com.google.dagger:hilt-android-compiler` matches in `app_v2/build.gradle.kts`.
- `Grep` - no `kapt(` line remains for a Hilt artifact.
- Run `pwsh -NoProfile -File ./a.ps1 fk` - exit code 0.
- Run `pwsh -NoProfile -File ./a.ps1 fu` - exit code 0, and specifically the Hilt-graph tests pass; a `MissingBinding` hides behind two green compiles otherwise.

**Step log:**

- `app_v2/build.gradle.kts` backed up to `temp/S1338/build.gradle.kts.bak-20260802_183109` (87,449 B) before the first edit, per Rule 5.
- `com.google.devtools.ksp` applied to `app_v2`; the root already declared it at 2.3.8 with `apply false`, so no version was added here. `com.android.legacy-kapt` stays for now - Room and Glide still need it, and the two plugins coexist.
- Three configurations converted: `hilt-android-compiler:2.59` and `androidx.hilt:hilt-compiler:1.2.0` from `kapt` to `ksp`, plus `kaptAndroidTest` -> `kspAndroidTest`.
- `Grep` - `ksp("com.google.dagger:hilt-android-compiler`: expected match | actual **match**. Remaining `kapt(` line for a Hilt artifact: expected 0 | actual **0**.
- `a.ps1 fk`: expected exit 0 | actual **0**, BUILD SUCCESSFUL in 2m 42s.
- `a.ps1 fu`: expected exit 0 | actual **0**, BUILD SUCCESSFUL in 3m 52s, `assert-test-suite-complete: PASS`, 427 reports for 425 `*Test.kt` files. **This is the check that matters here** - `fk` compiles Kotlin and says nothing about the Hilt graph, so a `MissingBinding` introduced by the processor swap would hide behind a green compile. The `hiltSyncStandardDebugUnitTest` and `hiltAggregateDepsStandardDebugUnitTest` tasks both ran, and the suite is green.

**Status:** `[x]` done

---

### Step 10.3 - Migrate Room

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 10.2

**Prompt for developer:**

> Convert `androidx.room:room-compiler:2.7.0` from `kapt` to `ksp`. The `kapt {}` block carries the Room schema-export argument and `correctErrorTypes = true`; move the schema-export argument to a `ksp { arg(..) }` block and confirm the exported schema JSON still lands in the same directory with the same content - a silently relocated or missing schema breaks migration tests without failing the build. `correctErrorTypes` is a kapt-only concept and has no KSP equivalent; drop it once no kapt processor remains.

**Verification:**

- `Grep` - `ksp("androidx.room:room-compiler` matches.
- The exported schema JSON is byte-identical to the pre-migration one for the current `@Database` version.
- Run `pwsh -NoProfile -File ./a.ps1 fu` - exit code 0, migration tests included.

**Step log:**

- `Grep` - `ksp("androidx.room:room-compiler:2.7.0")` at `app_v2/build.gradle.kts:1296`: expected match | actual **match**. No `kapt(` line remains for Room.
- The schema-export argument moved to the `ksp { }` block - `arg("room.schemaLocation", "$projectDir/schemas")` at line 1550, carrying its S0731 comment. `correctErrorTypes` is gone with the `kapt {}` block, which is correct: it is a kapt-only concept with no KSP equivalent.
- **The exported schema is byte-identical, verified against the committed baseline rather than asserted.** `app_v2/schemas/com.sza.fastmediasorter.data.local.db.AppDatabase/44.json` matches `AppDatabase.kt:36` `version = 44`, and `git status --porcelain app_v2/schemas/` returns **empty** - the KSP-exported schema differs in no byte from the one kapt committed. This is the check the step exists for: a silently relocated or rewritten schema breaks migration tests without failing the build.
- `a.ps1 fu`: expected exit 0 | actual **0**, BUILD SUCCESSFUL in 3m 17s, `assert-test-suite-complete: PASS`, 429 reports for 427 `*Test.kt` files.
- **One failure recorded because it looked like this step's and was not.** An earlier run today (`temp/S1338/S1338_phase10_room_unit_20260803.out.log`) failed with `AddStreamSourceUseCaseTest > duplicateUrl_isRejected_withoutInsertOrStat`, `java.lang.NullPointerException at Lazy.kt:100`, 1 of 3098 tests - and that failure is why the ticket was left `Broken`. It does not reproduce on the completed migration: the fresh report `TEST-..AddStreamSourceUseCaseTest.xml` (2026-08-03 22:54) reads `tests="4" failures="0" errors="0"` and names that test. The failing run's own task list explains it - it still executed `kaptGenerateStubsStandardDebugUnitTestKotlin`, so it was measured against a half-migrated tree with kapt stubs still in the graph, not against this one.

**Status:** `[x]` done

---

### Step 10.4 - Migrate Glide

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 10.3

**Prompt for developer:**

> Convert `com.github.bumptech.glide:compiler:4.16.0` from `kapt` to `ksp`. Glide 4.16 supports KSP through `com.github.bumptech.glide:ksp` rather than the `compiler` artifact - use the KSP-specific artifact at the matching version, not the kapt one under a `ksp(..)` call. Confirm the generated `GlideApp` API surface, if this project uses one, is still generated and still resolves. Once this lands, remove the `kapt {}` block and the `com.android.legacy-kapt` plugin, and drop the kapt-specific tuning from `gradle.properties` - `kapt.incremental.apt` and `kapt.use.worker.api`. Leave `android.disallowKotlinSourceSets=false`: its comment records that KSP still wires generated sources through `kotlin.sourceSets`, so it is load-bearing for KSP, not for kapt.

**Verification:**

- `Grep` - no `kapt` token remains anywhere in `app_v2/build.gradle.kts`.
- `Grep` - `com.android.legacy-kapt` no longer applied by `app_v2`.
- `Grep` - `android.disallowKotlinSourceSets` still present in `gradle.properties`.
- Run `pwsh -NoProfile -File ./a.ps1 fk` - exit code 0.

**Step log:**

- Glide converted to the KSP-specific artifact, not the kapt one under a `ksp(..)` call: `ksp("com.github.bumptech.glide:ksp:4.16.0")` at `app_v2/build.gradle.kts:1353`, beside the unchanged `glide:4.16.0` runtime.
- `kapt` tokens anywhere in `app_v2/build.gradle.kts`: expected 0 | actual **0** (case-insensitive count). `com.android.legacy-kapt` is no longer in the module's `plugins { }` block, which now reads `com.android.application`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android`, `org.jetbrains.kotlin.plugin.compose`.
- `gradle.properties`: the kapt tuning is gone and `android.disallowKotlinSourceSets=false` survives at line 70, as the step requires - its comment records that KSP still wires generated sources through `kotlin.sourceSets`, so it is load-bearing for KSP rather than a kapt leftover.
- **Added beyond the prompt, and worth naming:** `ksp.incremental=false` is now set with a comment recording the failure that forced it - `e: [ksp] java.lang.IllegalArgumentException: this and base files have different roots`. Incremental KSP is off on this host; that is a cost the migration carries, not a free win, and `dev/TECH_REQUIREMENTS.md` records it in place of the retired `kapt.incremental.apt` row.
- `a.ps1 fk` (standard): expected exit 0 | actual **0**. Proven per flavor in step 10.5 rather than on standard alone.

**Status:** `[x]` done

---

### Step 10.5 - Prove it on every flavor

**Files:** none - verification step
**Depends on:** Step 10.4

**Prompt for developer:**

> Strategic §7 requires a full-flavor proof, and CLAUDE.md section 13 requires a minified-release proof for any change touching DI graphs or reflection - a kapt-to-KSP switch is exactly that. Build every flavor in the matrix: standard, lite, photos, legacy, plus noLegal and vr. Then build a minified release variant and confirm no new `R8: missing class` or unresolved reference, since the generated code now comes from a different processor. Run the full unit suite per flavor where one exists. Never run two gradle invocations at once - `temp/BUILD.LOCK` enforces it.

**Verification:**

- Every flavor listed above builds - exit code 0 recorded per flavor with the command used.
- A minified release build completes with no new R8 missing-class or unresolved warnings against the pre-migration log.
- `pwsh -NoProfile -File ./a.ps1 fu` - exit code 0.

**Step log:**

- Every flavor compiled through `scripts/builders/check-standard-fast.ps1 -Mode Code -Flavor <F> -Quiet`, run sequentially by `temp/S1338/flavor-sweep.ps1` so only one gradle invocation is ever live (Rule 23). All six **exit 0**: Standard 3 s, Lite 28 s, Photos 71 s, Legacy 65 s, NoLegal 20 s, Vr 64 s. Raw logs in `temp/S1338/flavor-<F>.log`, verdicts in `temp/S1338/flavor-sweep-results.txt`.
- **Standard's 3 s is an up-to-date run and is not by itself proof**, so KSP execution was confirmed at the artifact instead: `app_v2/build/generated/ksp/` holds a directory per flavor - `standardDebug`, `liteDebug`, `photosDebug`, `legacyDebug`, `noLegalDebug`, `vrDebug` - each stamped today. The processor ran for all six variants, not only the one that recompiled.
- Minified release proof: `scripts/builders/build-nolegal-release.ps1` (`assembleNoLegalRelease`, `isMinifyEnabled = true`, `isShrinkResources = true`): expected exit 0 | actual **0**, 297 s, APK written at 137,257,414 B. `minifyNoLegalReleaseWithR8` ran.
- **R8 warnings: exactly one, and it names nothing this migration touches** - `Unexpected reference to missing service class: META-INF/services/javax.script.ScriptEngineFactory` out of the merged java resources, which is the Python/scripting runtime the noLegal flavor bundles. No R8 warning mentions Hilt, Dagger, a Room `_Impl`, or Glide generated code, which is the failure class a processor swap can actually cause.
- **The step asks for a diff against the pre-migration log and no such log exists on this host**, so the claim is stated at the width the evidence supports rather than dressed up: the only surviving pre-migration release log (`temp/release_build_2.60.6270.802.log`, 2026-06-27) is a standard-flavor AAB build from the release worktree and records zero R8 lines at all, so diffing against it would prove nothing. What is proven is the sentence above - no R8 warning names generated DI, Room or Glide code.
- `a.ps1 fu`: expected exit 0 | actual **0**, 198 s.

**Status:** `[x]` done

---

### Step 10.6 - Record the measured saving and update the stack docs

**Files:** `docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md`
**Depends on:** Step 10.5

**Prompt for developer:**

> Re-run the step 10.1 timings and record the actual saving against the ~35% claim. Strategic §6 requires landing the change and then measuring, never promising the estimate. Then update the toolchain documentation: `docs/TECH_STACK.md` and `dev/TECH_REQUIREMENTS.md` both describe the annotation-processor toolchain and are covered by the `architecture` registry record under the `dependency` trigger.

**Verification:**

- The measured before/after compile timings are written into strategic §6.
- `Grep` - `kapt` no longer described as the app_v2 annotation processor in either document.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit code 0.

**Step log:**

- After-arm measured by `temp/S1338/measure-compile-ksp.ps1`, written to `temp/S1338/compile-after-ksp.json`, exit **0**. Deliberately the same shape as step 10.1's arm - same warmup, same `--rerun` isolation, three rounds, median not mean - because 10.1 recorded that its rounds trended downward hard while the daemon warmed, and an after-arm measured any other way would report a warmup as a saving.
- Per-task medians, standard debug, kapt -> KSP: annotation chain **73.69 s -> 12.05 s** (kapt was two tasks, 52.28 s of stub generation plus 21.41 s of processing; KSP is one task at 12.05 s), `compileStandardDebugKotlin` **69.72 s -> 49.56 s**, full chain **143.41 s -> 61.55 s**. The annotation chain's share of the chain falls **51.4% -> 19.6%**.
- **The annotation-chain number is the one this step earns; the Kotlin-compile number is not clean.** Stub generation is exactly what KSP removes, so -83.6% on the annotation chain is directly attributable. The 20 s off `compileStandardDebugKotlin` is confounded twice over: the two arms ran on different days with different daemon and file-system-watch state, and dropping kapt also removes generated stubs from that task's own inputs. Reported as measured, not apportioned.
- Read against the spec's original claim: strategic §4 package C predicted "~35% of the measured 44 s compile chain" in stub generation plus annotation processing. Step 10.1 already recorded that `--rerun` measures the full rebuild rather than the everyday incremental chain, so neither the 35% nor the 44 s transfers. What holds is the direction and the size of the effect on a full rebuild.
- Documentation updated: `docs/TECH_STACK.md` now reads "Annotation processing: `ksp` in both modules"; `dev/TECH_REQUIREMENTS.md` drops the `kapt.incremental.apt` property row for `ksp.incremental=false` with the reason it is off, renames `glide-compiler` to `glide-ksp`, marks `hilt-android-compiler` as KSP, and its pinning-decision row no longer says "KSP migration pending".
- **Two stale pins were found in those same documents and fixed inline** - `compileSdk`/`targetSdk` read 35 against 36 in both modules, and "Room DB version" read 42 against `version = 44`. `scripts/check-doc-vs-gradle.ps1` reports exit **0** across both before and after, covering neither pin: the coverage gap is parked as **S1381**, out of scope here.
- `document_registry/validate.ps1`: expected exit 0 | actual **0**.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 10.*` above is `[x] done`.
- [x] Full flavor matrix builds green, with the minified release proof recorded - six flavors exit 0, `assembleNoLegalRelease` exit 0 with R8, re-proved after the audit's keep-rule removal.
- [x] `pwsh -NoProfile -File ./a.ps1 fu` - exit code **0**.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - exit code **0** (22 s, eleven gates). With `-IncludeDetekt` it exits 1 on 13 findings in one untouched file - see phase 11 step 11.3.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` run once - exit **0**, "up to date", which is correct: this phase changed the build graph, not a class.
- [x] Phase-boundary audit run - one P2 finding, fixed in the phase (dead Glide keep rules); no P0/P1.
- [x] `CODE.LOCK` - **not acquired, and recorded rather than ticked silently**. The prerequisite assumes this phase edits Kotlin or build files; by the time this session opened, steps 10.2 to 10.4 had already landed in the tree, so the only edits made here were documentation, one keep-rule block and spec files. `lock-status.ps1 -Name Code` reported free at the start and nothing multi-file-Kotlin was written.

---

## Phase-boundary audit (2026-08-03)

Trigger per CLAUDE.md section 13: a build/R8/keep-rule change affecting minification. One finding.

- **P2 - three keep rules for a Glide facade that no longer exists.** `proguard-rules.pro` kept `com.sza.fastmediasorter.GlideApp`, `GlideRequest` and `GlideRequests`. Glide's KSP processor generates only `com.bumptech.glide.GeneratedAppGlideModuleImpl` - verified in `app_v2/build/generated/ksp/*/kotlin/` for every flavor - and generates no facade at all. Nothing in `app_v2/src` referenced those three types (the `GlideApp*` hits are all `GlideAppModule`, a different class), which is why six flavors and a minified release all passed with the rules dead.
- **The same finding also corrects the record: those rules were already dead before this migration.** kapt emitted the facade as `com.sza.fastmediasorter.di.GlideApp` - present in the stale `build/generated/source/kapt/` tree - while the rules named `com.sza.fastmediasorter.GlideApp`, one package up. They matched nothing under kapt either. R8 does not fail on a keep rule that matches nothing, which is exactly why a wrong rule can sit in a shipped config for as long as this one did.
- Fixed in this phase per Rule 20 (delete orphaned keep rules in the same change), and the minified release re-run afterwards rather than argued to be safe: `build-nolegal-release.ps1` expected exit 0 | actual **0**, 223 s, still exactly one R8 warning and still the unrelated `javax.script.ScriptEngineFactory` one. `app_v2/proguard-rules.pro` backed up to `temp/S1338/` before the edit (Rule 5 applies at 500 LOC and the file is 305, so this was belt and braces).

---

## Handoff Notes to Next Phase

Final implementation phase. If any processor destabilises, strategic §4 package C permits splitting it out into its own ticket rather than blocking the umbrella - the three steps 10.2 to 10.4 are deliberately separable so one can be reverted without the others.

---

## Rollback Plan

Restore `app_v2/build.gradle.kts` from the `temp/S1338/` backup and revert the `gradle.properties` kapt lines. Each processor migration is a separate commit, so a single failing processor reverts alone. No source code changed - only the build graph - so a revert needs no code cleanup.
