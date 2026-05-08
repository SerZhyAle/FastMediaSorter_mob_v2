# Phase 04 — kapt-plugin-removal

**Strategic spec:** [`../S0042_agp10-kapt-to-ksp-migration.md`](../S0042_agp10-kapt-to-ksp-migration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Remove the `kotlin-kapt` plugin from `app_v2`, delete the `kapt { }` configuration block, and strip kapt-specific keys from `gradle.properties`. After this phase the project has zero kapt presence in `app_v2` — but the legacy DSL compat flags in `gradle.properties` remain until Phase 05.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] `Grep` — `kapt(` in `app_v2/build.gradle.kts` returns 0 hits before starting (all processors already migrated).
- [ ] Baseline cold-build time from Phase 01 is recorded.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 800 |
| `gradle.properties` | Modified | ≤ 50 |

---

## Steps

### Step 4.1 — Remove kotlin-kapt plugin

**Files:** `app_v2/build.gradle.kts`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, remove the line:
> ```
> id("kotlin-kapt")
> ```
> from the `plugins { }` block. The comment above the `android { }` block referencing `android.newDsl=false` and kapt compatibility (`// android.newDsl=false is intentionally set in gradle.properties (kapt compat). // Remove once kapt → KSP migration is complete.`) should also be removed — the migration is now complete.

**Verification:**

- `Grep` — `kotlin-kapt` in `app_v2/build.gradle.kts` returns 0 hits.
- `Grep` — `kapt compat` in `app_v2/build.gradle.kts` returns 0 hits.

**Status:** `[ ]` not done

---

### Step 4.2 — Remove kapt configuration block

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 4.1

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, remove the entire `kapt { }` block at the end of the file (currently lines ~792–797):
> ```kotlin
> kapt {
>     correctErrorTypes = true
>     javacOptions {
>         option("-Xlint:-processing")
>     }
> }
> ```
> KSP does not use `correctErrorTypes` (it resolves types correctly by default) and does not use javac options.

**Verification:**

- `Grep` — `^kapt \{` in `app_v2/build.gradle.kts` returns 0 hits.
- `Grep` — `correctErrorTypes` in `app_v2/build.gradle.kts` returns 0 hits.

**Status:** `[ ]` not done

---

### Step 4.3 — Remove kapt-specific gradle.properties keys

**Files:** `gradle.properties`
**Depends on:** Step 4.2

**Prompt for developer:**

> In `gradle.properties`, remove the following lines and their associated comments:
> - `kapt.incremental.apt=true`
> - `kapt.use.worker.api=false` (and its comment: `# Force kapt to run in-process (same JVM as Gradle) so java.io.tmpdir is inherited`)
>
> Do not remove `kotlin.incremental=true` — that is a Kotlin compiler flag, not kapt-specific.
> Do not remove `android.builtInKotlin=false` or `android.newDsl=false` — those are removed in Phase 05.

**Verification:**

- `Grep` — `kapt\.incremental\.apt` in `gradle.properties` returns 0 hits.
- `Grep` — `kapt\.use\.worker` in `gradle.properties` returns 0 hits.
- `Grep` — `kotlin\.incremental` in `gradle.properties` returns exactly 1 hit (unchanged).

**Status:** `[ ]` not done

---

### Step 4.4 — Build verification and performance check

**Files:** none
**Depends on:** Step 4.3

**Prompt for developer:**

> Run a clean cold build: `./gradlew clean assembleStandardDebug`. Record the total build time. Compare against the Phase 01 baseline. Confirm that:
> 1. The build succeeds.
> 2. No `kapt`-prefixed task names appear in the build output.
> 3. Configuration cache is used (output contains `Configuration cache entry` — either reused or stored).
> 4. Cold build time does not exceed Phase 01 baseline × 1.20.

**Verification:**

- Build output contains `BUILD SUCCESSFUL`.
- `Grep` build output for `> Task :app_v2:kapt` returns 0 hits.
- Build output contains `Configuration cache entry`.
- Build time ≤ Phase 01 baseline × 1.20 (document both values in the Blockers Log if threshold is exceeded).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] `Grep` — `kotlin-kapt` in `app_v2/build.gradle.kts` returns 0 hits.
- [ ] `Grep` — `kapt` anywhere in `app_v2/build.gradle.kts` returns 0 hits.
- [ ] `Grep` — `kapt` in `gradle.properties` returns 0 hits.
- [ ] Project compiles — run `/build`.
- [ ] Dev log entries added:
  - `.\scripts\add_to_dev_log.ps1 "app_v2/build.gradle.kts" "S0042 Phase 04" "Remove kotlin-kapt plugin and kapt{} config block"`
  - `.\scripts\add_to_dev_log.ps1 "gradle.properties" "S0042 Phase 04" "Remove kapt-specific properties"`

---

## Handoff Notes to Next Phase

- `app_v2` is now fully kapt-free: no plugin, no processor dependencies, no configuration block, no `gradle.properties` keys.
- The two AGP legacy DSL compat flags remain (`android.builtInKotlin=false`, `android.newDsl=false`). They are co-dependent and must be removed together in Phase 05.
- Until Phase 05, the build may still emit some legacy DSL deprecation warnings — this is expected.

---

## Rollback Plan

Revert phase commit. Restore `id("kotlin-kapt")` in plugins, restore the `kapt { }` block, restore `kapt.incremental.apt=true` and `kapt.use.worker.api=false` in `gradle.properties`. Clean build required after revert.
