# Phase 06 — wear-and-sourcesets-unify

**Strategic spec:** [`../S0042_agp10-kapt-to-ksp-migration.md`](../S0042_agp10-kapt-to-ksp-migration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Unify the remaining two legacy build-config patterns: migrate `wear/build.gradle.kts` from the deprecated `kotlinOptions { jvmTarget }` block to the modern `compilerOptions` DSL, and confirm that the `vrUnlicensed` sourceSets declaration in `app_v2` produces no deprecation warnings under the new DSL.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done.
- [ ] Both `android.builtInKotlin` and `android.newDsl` flags are absent from `gradle.properties`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/build.gradle.kts` | Modified | ≤ 150 |
| `app_v2/build.gradle.kts` | Modified (if vrUnlicensed sourceSets deprecation found) | ≤ 800 |

> `app_v2/build.gradle.kts` is listed as conditional: Step 6.2 determines whether a change is required. If no deprecation warning is found, the file is not touched.

---

## Steps

### Step 6.1 — Migrate Wear kotlinOptions to compilerOptions DSL

**Files:** `wear/build.gradle.kts`
**Depends on:** — start of phase

**Prompt for developer:**

> In `wear/build.gradle.kts`, remove the `kotlinOptions { }` block inside `android { }`:
> ```kotlin
> kotlinOptions {
>     // CRITICAL: Do not change - must match compileOptions.targetCompatibility
>     jvmTarget = "17"
> }
> ```
> Add the following block **outside** `android { }`, after the closing brace of the `android { }` block:
> ```kotlin
> // CRITICAL: Do not change - must match compileOptions.targetCompatibility
> // Replaces the deprecated android { kotlinOptions { jvmTarget } } block
> tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
>     compilerOptions {
>         jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
>     }
> }
> ```
> This matches the pattern already used in `app_v2/build.gradle.kts` (see lines ~558–564 in that file).

**Verification:**

- `Grep` — `kotlinOptions` in `wear/build.gradle.kts` returns 0 hits.
- `Grep` — `JvmTarget.JVM_17` in `wear/build.gradle.kts` returns exactly 1 hit.

**Status:** `[ ]` not done

---

### Step 6.2 — Audit vrUnlicensed sourceSets for deprecation warnings

**Files:** `app_v2/build.gradle.kts` (conditional)
**Depends on:** Step 6.1

**Prompt for developer:**

> Build `assembleVrUnlicensedDebug` and capture the output:
> ```
> ./gradlew assembleVrUnlicensedDebug 2>&1
> ```
> Scan the output for any deprecation warning that mentions `sourceSets`, `getByName`, `java.srcDir`, or `manifest.srcFile`.
>
> **If no such warning is found:** mark this step done with "no change required".
>
> **If a deprecation warning IS found for the `sourceSets` block**, replace the current block in `app_v2/build.gradle.kts`:
> ```kotlin
> sourceSets {
>     getByName("vrUnlicensed") {
>         java.srcDir("src/vr/java")
>         res.srcDir("src/vr/res")
>         manifest.srcFile("src/vr/AndroidManifest.xml")
>     }
> }
> ```
> with the equivalent using the Variant Sources API:
> ```kotlin
> androidComponents {
>     onVariants(selector().withFlavor("version" to "vrUnlicensed")) { variant ->
>         variant.sources.java?.addStaticSourceDirectory("src/vr/java")
>         variant.sources.res?.addStaticSourceDirectory("src/vr/res")
>     }
> }
> ```
> and move the `manifest.srcFile` reference to the `vrUnlicensed` flavor block using `manifestPlaceholders` or a dedicated `AndroidManifest.xml` — consult AGP 9.x migration docs for the exact manifest override API.
>
> Note: if the `androidComponents` block already exists in `app_v2/build.gradle.kts` (for output filename wiring), add the new `onVariants` call inside it rather than creating a second block.

**Verification:**

- Build output for `assembleVrUnlicensedDebug` does not contain any deprecation warning mentioning `sourceSets`, `getByName`, `java.srcDir`, or `manifest.srcFile`.
- `Glob` — `app_v2/build/outputs/apk/vrUnlicensed/debug/*.apk` finds exactly one file.

**Status:** `[ ]` not done

---

### Step 6.3 — Final build for both modules, all flavors

**Files:** none
**Depends on:** Step 6.2

**Prompt for developer:**

> Run the full multi-flavor + Wear build:
> ```
> ./gradlew assembleStandardDebug assembleLiteDebug assemblePhotosDebug assembleLegacyDebug assembleVrDebug assembleVrUnlicensedDebug :wear:assembleDebug
> ```
> Confirm zero deprecation warnings in the combined output. This is the "all-clear" gate for the migration.

**Verification:**

- Build output contains `BUILD SUCCESSFUL`.
- Build output does not contain `This behavior has been deprecated`.
- Build output does not contain `has been deprecated`.
- `Glob` — `wear/build/outputs/apk/debug/*.apk` finds exactly one file.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] `Grep` — `kotlinOptions` in `wear/build.gradle.kts` returns 0 hits.
- [ ] `Grep` — `JvmTarget.JVM_17` in `wear/build.gradle.kts` returns 1 hit.
- [ ] Full multi-flavor build (all 6 app flavors + wear) completes with 0 deprecation warnings.
- [ ] Dev log entries added:
  - `.\scripts\add_to_dev_log.ps1 "wear/build.gradle.kts" "S0042 Phase 06" "Migrate kotlinOptions to compilerOptions DSL"`
  - (if app_v2 changed) `.\scripts\add_to_dev_log.ps1 "app_v2/build.gradle.kts" "S0042 Phase 06" "Migrate vrUnlicensed sourceSets to Variant Sources API"`

---

## Handoff Notes to Next Phase

- Final phase — see INDEX.md Completion Gate.
- Both modules use the same `tasks.withType<KotlinCompile>().configureEach { compilerOptions { jvmTarget.set(JVM_17) } }` pattern.
- Build output is clean: zero deprecation warnings across all flavors.

---

## Rollback Plan

Revert phase commit(s). Restore `kotlinOptions { jvmTarget = "17" }` inside `android {}` in `wear/build.gradle.kts`. If `app_v2/build.gradle.kts` was changed for sourceSets, restore the original `sourceSets { getByName("vrUnlicensed") { ... } }` block. Clean build required after revert.
