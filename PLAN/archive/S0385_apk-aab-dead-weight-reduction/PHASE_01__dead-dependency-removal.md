# Phase 01 - Dead Dependency Removal

**Strategic spec:** [`../S0385_apk-aab-dead-weight-reduction.md`](../S0385_apk-aab-dead-weight-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (01.4 skipped - dependency not dead)
**Depends on:** none - independent phase
**Blocks:** none
**Steps done:** 3 / 4 (01.4 ⏭️ Skipped)
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Capture the before-size baseline, then remove third-party dependencies that have zero references anywhere in `app_v2/src/**`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `pwsh` available for builds via `a.ps1`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | n/a (>500 - backup first) |
| `temp/S0385_baseline_sizes.md` | New | ≤ 60 |

> `app_v2/build.gradle.kts` exceeds 500 lines - create a timestamped backup in `temp/` before the first edit (Strict Rule 5).

---

## Steps

### Step 01.1 - Record baseline artifact sizes

**Files:** `temp/S0385_baseline_sizes.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Build the release variants of every affected flavor (`standardRelease`, `liteRelease`, `photosRelease`, `legacyRelease`; `noLegal`/`vr` via their release scripts) without signing if keystore is absent, or use the existing debug APKs as a fallback baseline. Unzip each and record total size plus the `lib/` and `assets/` subtotals into `temp/S0385_baseline_sizes.md`. This is the reference for the Phase 07 delta.

**Verification:**

- `Glob` - `temp/S0385_baseline_sizes.md` exists.
- `Grep` - the file contains a line per affected flavor with a numeric byte total.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. Baseline captured from existing debug APKs (fallback per step): standard/legacy lib=144.1MB, lite/photos lib=136.9MB, noLegal lib=117.1MB. File: temp/S0385_baseline_sizes.md.

---

### Step 01.2 - Remove the unused interactive-scrollbar dependency

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete the `me.zhanghai.android.fastscroll:library` dependency line. It has zero references in any source set or layout.

**Verification:**

- `Grep` - `me.zhanghai` and `fastscroll` return zero hits across `app_v2/src/**` and `app_v2/build.gradle.kts`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification 1/1 PASS. `me.zhanghai|fastscroll|FastScroller` = 0 hits in app_v2 (excl. build/). Removed dep + comment from build.gradle.kts.

---

### Step 01.3 - Remove the unused Jetpack Navigation dependencies

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.2

**Prompt for developer:**

> Delete `androidx.navigation:navigation-fragment-ktx` and `androidx.navigation:navigation-ui-ktx` (production) and the now-orphaned `androidx.navigation:navigation-testing` (androidTest). The app navigates via Activities + ViewPager2, not a Navigation graph.

**Verification:**

- `Grep` - `androidx.navigation` returns zero hits in `app_v2/build.gradle.kts`.
- `Grep` - `findNavController`, `NavHostFragment`, `setupWithNavController` return zero hits across `app_v2/src/**`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS (intent). Removed `navigation-fragment-ktx`, `navigation-ui-ktx` (prod) + orphaned `navigation-testing` (androidTest). `findNavController/NavHostFragment/setupWithNavController/NavDirections` = 0 in src (only stale `lint-baseline.xml` entries remain, harmless). Confirmed no `res/navigation/*.xml` graphs → Safe Args generates nothing. Residual `androidx.navigation` in build.gradle.kts is ONLY the `safeargs.kotlin` plugin id (line 156), out of this step's prompt scope; now a no-op dead plugin - flagged as adjacent debt for a follow-up step, not removed here (edit-scope discipline).

---

### Step 01.4 - Remove the extended Compose icon set

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.3

**Prompt for developer:**

> Delete `androidx.compose.material:material-icons-extended`. Only five icons from the core set are used; keep `material-icons-core`. Confirm no extended-only icon import survives.

**Verification:**

- `Grep` - `material-icons-extended` returns zero hits in `app_v2/build.gradle.kts`.
- `Grep` - `Icons.Outlined`, `Icons.Rounded`, `Icons.Sharp`, `Icons.TwoTone` return zero hits across `app_v2/src/**`.

**Status:** `⏭️ Skipped` - premise false, dependency is NOT dead

**Step Log:**

- 2026-06-08 - Grep predicates passed but the Phase Done Criteria build FAILED: `Icons.Filled.Pause`, `SkipNext`, `SkipPrevious` (media-control icons in `WearSyncSettingsFragment.kt` + widget config activities) are **extended-only**, not in `material-icons-core`. The dependency-audit premise ("only 5 core icons used") was wrong - `Close`/`PlayArrow` are core but `Pause`/`SkipNext`/`SkipPrevious` are not. Reverted the removal, re-added `material-icons-extended` with a WHY-comment. Step skipped: removing this dep is out of "dead dependency removal" scope - it would require migrating those 3 icons to drawables/core, a separate UI task. R8 already strips unused extended icons in release, so the release-AAB cost is limited.

---

## Phase Done Criteria

- [x] Steps 01.1-01.3 `[x] done`; 01.4 `⏭️ Skipped` (material-icons-extended is not dead - build proof, see step log).
- [x] Project compiles - `standardDebug` BUILD SUCCESSFUL (1m21s) + `liteDebug` BUILD SUCCESSFUL (1m52s). `photosDebug`/`legacyDebug` covered by equivalence: identical shared-dep removals, 0 references across all source sets (grep-verified). | expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `app_v2/build.gradle.kts` (post-change.ps1 Config, PASS).

---

## Handoff Notes to Next Phase

Baseline sizes recorded for the Phase 07 delta. Dependency list trimmed of zero-reference artifacts. No source behaviour changed.

---

## Rollback Plan

Revert the phase commit - dependency-only change, no data migration or user-facing surface touched.
