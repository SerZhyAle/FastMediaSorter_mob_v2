# S1084 - ActivityResult callbacks deref binding without view-alive guard

**Status:** Archived
**Priority:** 35
**Tier:** 5
**Date:** 2026-07-18
**Source:** parked from S0404 Phase 08 audit (2026-07-17)

## 0. Raw capture

Auditor finding (S0404 Phase 08, dimension: UI/lifecycle). Cross-cutting, out of S0404 scope.

**Symptom:** `registerForActivityResult` callbacks dereference `binding` (`_binding!!`) with no view-alive guard, inside a Fragment hosted by a `ViewPager2`/`FragmentStateAdapter` that destroys its view (`onDestroyView` -> `_binding = null`) on a tab switch while the Fragment instance and its registered launcher survive. If the launched system Activity returns after the view is gone, the callback throws `KotlinNullPointerException`.

**Evidence (pre-existing pattern, not introduced by S0404):**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` - `recordAudioPermissionLauncher`, `locationPermissionLauncher` both `binding.rowXxx.setCheckedSilently(...)` in their result callbacks. (S0404's own `launcherRoleLauncher` was guarded with `_binding != null` as a point fix; the two siblings were left for this ticket.)
- Hosting confirmed: `ui/settings/SettingsActivity.kt` mounts `OperationsSettingsFragment` as a `ViewPager2`/`FragmentStateAdapter` tab.

**Why its own ticket:** a proper fix is a project-wide guard idiom (helper or lint) applied uniformly across all such launchers, not a one-off. Needs its own audit of every `registerForActivityResult { binding... }` site.

## 1. Implementation

- [x] Audit Fragment and DialogFragment ActivityResult callbacks for direct `binding` dereferences.
- [x] Guard each direct UI update with `_binding ?: return@registerForActivityResult`.
- [x] Preserve non-UI ViewModel state updates when a result arrives after `onDestroyView`.
- [x] Run fast Kotlin validation and lifecycle audit.

## 2. Validation

- `./a.ps1 fk`: PASS - `:app_v2:compileStandardDebugKotlin` completed successfully.
- Scoped static gates: PASS - no new flavor, neuroslop, mutable-flow, PackageManager, or listener-symmetry findings.
- `:app_v2:detekt --rerun-tasks`: S1084 files have no findings. The project task remains red on 566 pre-existing project-wide findings.
