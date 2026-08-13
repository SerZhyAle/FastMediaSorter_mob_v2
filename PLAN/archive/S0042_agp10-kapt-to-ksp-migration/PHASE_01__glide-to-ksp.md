# Phase 01 — glide-to-ksp

**Strategic spec:** [`../S0042_agp10-kapt-to-ksp-migration.md`](../S0042_agp10-kapt-to-ksp-migration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 3
**Started:** 2026-05-07
**Completed:** —

---

## Objective

Apply the KSP plugin to `app_v2` and switch the Glide annotation processor from `kapt` to `ksp`; all other processors stay on kapt for now.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] At least one successful cold build of `standardDebug` has been run (to have a baseline build time to compare against at the end of Phase 04).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 800 |

---

## Steps

### Step 1.1 — Add KSP plugin to app_v2

**Files:** `app_v2/build.gradle.kts`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, add `id("com.google.devtools.ksp")` to the `plugins { }` block. Place it after the existing plugin lines and before the closing brace. Do not add a version — the version is declared in root `build.gradle.kts` as `"2.3.2"`.

**Verification:**

- `Grep` — `id\("com.google.devtools.ksp"\)` matches exactly once in `app_v2/build.gradle.kts`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 1/1 PASS. Files: app_v2/build.gradle.kts (plugin added line 8). Dev log recorded.

---

### Step 1.2 — Replace Glide kapt with ksp

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 1.1

**Prompt for developer:**

> In `app_v2/build.gradle.kts`, replace the line:
> ```
> kapt("com.github.bumptech.glide:compiler:4.16.0")
> ```
> with:
> ```
> ksp("com.github.bumptech.glide:ksp:4.16.0")
> ```
> Note: Glide publishes two separate artifacts. `compiler` is the APT/kapt processor; `ksp` is the KSP processor (added in Glide 4.14.2). The `okhttp3-integration` and `glide` main dependency lines are not touched.

**Verification:**

- `Grep` — `kapt.*bumptech.glide` in `app_v2/build.gradle.kts` returns 0 hits.
- `Grep` — `ksp\("com.github.bumptech.glide:ksp:4.16.0"\)` returns exactly 1 hit.

**Status:** `[x] done`

**Step Log:**
- 2026-05-07 — Verification 2/2 PASS. kapt(glide) removed; ksp(glide) added line 662. Dev log recorded.

---

### Step 1.3 — Build and record baseline

**Files:** none
**Depends on:** Step 1.2

**Prompt for developer:**

> Run a clean debug build: `./gradlew clean assembleStandardDebug`. Record the total build time shown at the end of the output — this is the kapt-coexistence baseline for Phase 04 threshold check. Verify the APK is produced and no compilation errors are present.

**Verification:**

- `Glob` — `app_v2/build/outputs/apk/standard/debug/*.apk` finds exactly one file.
- Build output contains `BUILD SUCCESSFUL`.
- Build output does not contain `error:` (case-sensitive).

**Status:** `[~] in progress`

**Step Log:**
- 2026-05-07 — Attempt 1 FAILED: `compiler:4.16.0` is not a KSP provider.
- 2026-05-07 — Attempt 2 FAILED with `ksp:4.16.0`: `java.lang.IllegalArgumentException: this and base files have different roots` — pre-indexed GlideModule class files in `okhttp3-integration-4.16.0-api.jar` are incompatible with Glide KSP 4.16.0. BLOCKED. See Blockers Log in INDEX. app_v2/build.gradle.kts rolled back to original state.

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `kapt.*bumptech.glide` in `app_v2/build.gradle.kts` returns 0 hits.
- [ ] Dev log entry added: `.\scripts\add_to_dev_log.ps1 "app_v2/build.gradle.kts" "S0042 Phase 01" "Switch Glide compiler kapt → ksp"`.

---

## Handoff Notes to Next Phase

- KSP plugin is now active in `app_v2`; `ksp()` configuration is available.
- Glide's `GlideApp`-generated sources are produced by KSP going forward.
- Three kapt entries remain: Room, Hilt main (`hilt-android-compiler`), Hilt extensions (`hilt-compiler`).
- Baseline cold-build time (with Glide on KSP, rest on kapt) recorded — use as Phase 04 comparison point.

---

## Rollback Plan

Revert phase commit. The Glide switch is isolated; reverting restores `kapt("com.github.bumptech.glide:compiler:4.16.0")` and removes `id("com.google.devtools.ksp")` from `app_v2` plugins. Clean build required after revert.
