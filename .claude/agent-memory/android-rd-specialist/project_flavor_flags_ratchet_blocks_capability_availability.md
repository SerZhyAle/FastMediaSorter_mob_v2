---
name: flavor-flags-ratchet-blocks-capability-availability
description: A new flavor capability cannot be added as a CapabilityAvailability accessor - the flavor-flags gate is a down-only ratchet that refuses to raise its baseline
metadata:
  type: project
---

`CapabilityAvailability.kt` is **frozen debt for the flavor-flags gate, not a sanctioned place to read a new `BuildConfig.SUPPORT_*` flag.** Adding one there fails `post-change` at `[flavor-flag-gate]`, and `assert-source-gates.ps1 -Only flavor-flags -UpdateBaseline` refuses with `refusing to RAISE baseline (N -> N+1)`. The only name-excluded reader in `src/main` is `PermissionRegistryRepositoryImpl.kt`, because it resolves gate *names* supplied by callers rather than guarding a consumer (`-ExcludeNames` in `scripts/quality/lib/source-matchers.ps1`).

**Why:** the file's own KDoc says it is "the only place shared code may read it", which reads like permission but is not - the gate baselines its existing reads as debt to ratchet down. Following the KDoc costs a full implement-then-revert cycle (hit on 2026-08-07 in S1433).

**How to apply:** for a new flavor-scoped capability, copy the `LauncherModeContract` seam instead - interface in `src/main/java/.../domain/<area>/`, real impl plus a `@Module @InstallIn(SingletonComponent::class)` provider in `src/<feature>Enabled/java/`, a No-Op impl plus a same-named module in `src/<feature>Disabled/java/`, and mount the two source sets in the flavor blocks of `app_v2/build.gradle.kts`. The `BuildConfig` field is still declared per flavor - it is needed by the permission registry's `buildGateValues` map, which is exempt. Live example added by S1433: `NetworkMonitorContract` with `src/networkMonitor` and `src/networkMonitorDisabled`. See also [[flavor-matrix-cloud-correction]].
