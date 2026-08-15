# Phase 01 - Foundations and permissions

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 06

---

## Objective

Declare the capability flag, the manifest permissions, the permission-registry entries and the two new settings, so every later phase has a gate to read and a permission to request. No UI and no monitor logic yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired before the first source edit (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/capability/CapabilityAvailability.kt` | Modified | ≤ 30 |
| `app_v2/src/networkMonitor/AndroidManifest.xml` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 30 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 40 |

---

## Steps

### Step 01.1 - Declare `SUPPORT_NETWORK_MONITOR` per flavor

**Files:** `app_v2/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `buildConfigField("boolean", "SUPPORT_NETWORK_MONITOR", ..)` declaration to every one of the six product flavors: `true` in `standard` and `noLegal`, `false` in `lite`, `photos`, `legacy` and `vr`. Declare it explicitly in all six blocks rather than relying on `defaultConfig` inheritance, so the generated matrix shows a decision and not an inherited value.

**Why:**

Strategic §3.2 restricts the Monitor to `standard` and `noLegal`; §11 criterion 17 requires the screen, its setting, its route and its shortcut to be absent entirely in the other four flavors, and a capability is only absent if it is not compiled in.

**Verification:**

- `Grep` - `SUPPORT_NETWORK_MONITOR` matches exactly six times in `app_v2/build.gradle.kts`.
- `Grep` - `SUPPORT_NETWORK_MONITOR", "true"` matches exactly twice.

**Status:** `[x]` done

---

### Step 01.2 - Introduce the capability contract and its two flavor source sets

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/networkmonitor/NetworkMonitorContract.kt` (New), `app_v2/src/networkMonitor/java/com/sza/fastmediasorter/networkmonitor/NetworkMonitorContractImpl.kt` (New), `app_v2/src/networkMonitor/java/com/sza/fastmediasorter/di/NetworkMonitorModule.kt` (New), `app_v2/src/networkMonitorDisabled/java/com/sza/fastmediasorter/networkmonitor/NoOpNetworkMonitorContract.kt` (New), `app_v2/src/networkMonitorDisabled/java/com/sza/fastmediasorter/di/NetworkMonitorModule.kt` (New), `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> Follow the `LauncherModeContract` seam exactly. In `src/main` declare `interface NetworkMonitorContract` with `val isAvailableInBuild: Boolean`. In `src/networkMonitor/java` add `NetworkMonitorContractImpl` returning `true` and a `NetworkMonitorModule` that is `@Module @InstallIn(SingletonComponent::class) object` with a `@Provides @Singleton` function returning it; in `src/networkMonitorDisabled/java` add `NoOpNetworkMonitorContract` returning `false` and a same-named module providing that. Mount `src/networkMonitor/java` in the `standard` and `noLegal` flavor blocks and `src/networkMonitorDisabled/java` in `lite`, `photos`, `legacy` and `vr`, next to the existing `src/launcherEnabled` / `src/launcherDisabled` lines. `src/main` must not read `BuildConfig.SUPPORT_NETWORK_MONITOR` anywhere.

**Why:**

CLAUDE.md Rule 14 requires an interface plus flavor source sets rather than a `BuildConfig` guard in shared code, and the `flavor-flags` ratchet refuses to raise its baseline, so `CapabilityAvailability` is frozen debt rather than a sanctioned reader: the only place `src/main` may still read the flag is `PermissionRegistryRepositoryImpl`, which the gate excludes by name because it resolves gate names rather than guarding a consumer.

**Verification:**

- `Grep` - `interface NetworkMonitorContract` matches exactly once, under `app_v2/src/main/java/`.
- `Grep` - `BuildConfig.SUPPORT_NETWORK_MONITOR` returns zero hits under `app_v2/src/main/java/`.
- `Grep` - `NetworkMonitorModule` matches exactly once in each of the two flavor source sets.
- `Grep` - `src/networkMonitor/java` matches exactly twice and `src/networkMonitorDisabled/java` exactly four times in `app_v2/build.gradle.kts`.
- `pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only flavor-flags -Gate` exits 0.

**Status:** `[x]` done

---

### Step 01.3 - Declare the manifest permissions in a flavor-scoped source set

**Files:** `app_v2/src/networkMonitor/AndroidManifest.xml`, `app_v2/build.gradle.kts`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `app_v2/src/networkMonitor/AndroidManifest.xml` declaring the install-time permissions `CHANGE_WIFI_STATE` and `BLUETOOTH_ADMIN` (the latter with `android:maxSdkVersion="30"`, since API 31+ routes through `BLUETOOTH_CONNECT`), and the runtime permissions `NEARBY_WIFI_DEVICES` (`android:minSdkVersion="33"`) and `BLUETOOTH_CONNECT` (`android:minSdkVersion="31"`). Do not declare `BLUETOOTH_SCAN`, any Wi-Fi scan permission, or `READ_PHONE_STATE` - the latter is already declared by `src/launcherEnabled/AndroidManifest.xml`, which the same two flavors mount, and a second declaration is dead weight. Add a one-line comment above each entry naming the section that needs it. In `app_v2/build.gradle.kts` register the source set in the `standard` and `noLegal` flavor blocks next to their `src/launcherEnabled` lines, and inject its manifest in the variant hook that already calls `addStaticManifestFile("src/launcherEnabled/AndroidManifest.xml")` for the same flavor pair, since a source set mounted by directory does not contribute its manifest on its own.

**Why:**

Strategic §11 criterion 17 requires the Monitor to be absent entirely in the other four flavors, and `src/main/AndroidManifest.xml` merges into every one of them: a permission declared there but registry-gated off would leave `lite`, `photos`, `legacy` and `vr` declaring a permission with no row behind it, which is the defect already ticketed as S1442 and S1454 and which `PermissionRegistryManifestParityTest` fails as a release blocker.

**Verification:**

- `Glob` - `app_v2/src/networkMonitor/AndroidManifest.xml` exists.
- `Grep` - `CHANGE_WIFI_STATE`, `BLUETOOTH_ADMIN`, `NEARBY_WIFI_DEVICES`, `BLUETOOTH_CONNECT` each match exactly once in that file.
- `Grep` - no `<uses-permission` line in that file names `BLUETOOTH_SCAN` or `READ_PHONE_STATE` (the header comment names the latter to explain the omission).
- `Grep` - `src/networkMonitor/` matches exactly three times in `app_v2/build.gradle.kts` (standard block, noLegal block, manifest injection).

**Status:** `[x]` done

---

### Step 01.4 - Register the runtime permissions in the permission registry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add two optional `PermissionEntry` records following the shape of the existing `activity_recognition` entry: `nearby_wifi_devices` (`minSdk = 33`, group `NETWORK`) and `bluetooth_connect` (`minSdk = 31`, group `NETWORK`). Gate both with `buildGates = setOf("SUPPORT_NETWORK_MONITOR")` - the field is named `buildGates`, not `flavorGates` - and add `"SUPPORT_NETWORK_MONITOR" to BuildConfig.SUPPORT_NETWORK_MONITOR` to the `buildGateValues` map that already resolves `SUPPORT_LAUNCHER`. Do not add a `read_phone_state` entry: one already exists, gated on `SUPPORT_LAUNCHER`, and `evaluateBuildGates` is a conjunction, so widening its gate set would narrow the row instead of widening it. Add no new permission screen and no bespoke grant flow. Each entry needs a title and a description resource, so add `perm_title_nearby_wifi_devices`, `perm_desc_nearby_wifi_devices`, `perm_title_bluetooth_connect` and `perm_desc_bluetooth_connect` in EN, RU and UK first, one `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>` call per key, each description stating what the Monitor reads and that nothing leaves the device; check them against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Research artifact 03 states the Monitor must add every runtime permission to this one registry and must not create a separate permission screen, Welcome branch or bulk-grant flow; Settings -> Permissions, the Welcome page and "Grant all" then pick the entries up with no further work.

**Verification:**

- `Grep` - `nearby_wifi_devices` and `bluetooth_connect` each match exactly once as an entry `id` in `PermissionRegistryRepositoryImpl.kt`.
- `Grep` - `read_phone_state` still matches exactly once in that file.
- `Grep` - `SUPPORT_NETWORK_MONITOR` matches exactly three times in that file (two gate sets plus the `buildGateValues` row).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "perm_title_nearby_wifi_devices"` exits 0, and the same for the other three keys.
- `pwsh -NoProfile -File ./a.ps1 fu` - `PermissionRegistryRepositoryImplTest` and `PermissionRegistryManifestParityTest` pass.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 01.5 - Add the two settings fields and their persistence

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `enableNetworkMonitor: Boolean = false` and `recordGnssTrack: Boolean = false` to `AppSettings`, and the matching DataStore keys, reads and writes in `SettingsRepositoryImpl`, following the existing `enableCalculator` pair exactly. Both default to `false`.

**Why:**

Strategic §3.2 requires a separate enable setting for the program and a separate track-recording setting that is off after installation, because §7 records that an on-by-default track changes the Play Data Safety answer with no user act.

**Verification:**

- `Grep` - `enableNetworkMonitor` and `recordGnssTrack` each match in both files.
- `Grep` - `recordGnssTrack: Boolean = false` present in `AppSettings.kt`.

**Status:** `[x]` done

---

### Step 01.6 - Add the program name and the two setting labels in three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 01.5

**Prompt for developer:**

> Add the string keys `network_monitor_title` ("Network Monitor" / "Сетевой монитор" / "Мережевий монітор"), `settings_enable_network_monitor` with its summary, and `settings_record_gnss_track` with a summary stating the track stays on the device. Use one `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <..> -Ru <..> -Uk <..>` call per key rather than three manual edits. Check each string against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist.

**Why:**

Strategic §3.2 fixes the program name as "Сетевой монитор" / "Network Monitor" and requires EN/RU/UK parity; the track setting's own text is part of the privacy claim that §11 criterion 10 requires to match the Play form and the privacy policy.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "network_monitor"` - exit 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_enable_network_monitor"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 01.1: Verification 2/2 PASS. Files: app_v2/build.gradle.kts (+6 lines), docs/FLAVOR_MATRIX.md + docs/flavors/flavor-matrix.json regenerated. `post-change: PASS` (exit 0). Dev log recorded.
- 2026-08-07 - Phase build evidence. `pwsh -NoProfile -File ./a.ps1 dq` -> BUILD SUCCESSFUL in 2m19s, Hilt tasks included, so the enabled side of the seam compiles and binds. `scripts/builders/check-standard-fast.ps1 -Mode Code -Flavor Lite` -> BUILD SUCCESSFUL in 1m14s, which is the evidence for the other side: `networkMonitorDisabled` and its same-named no-op module compile in a flavor that mounts only that source set.
- 2026-08-07 - Step 01.4 NOT closed: its unit-test predicate is unverifiable in this environment. The Gradle test executor dies before running anything - `Process 'Gradle Test Executor NN' finished with non-zero exit value 10` after a `Connection reset by peer`, three consecutive attempts with different filters - and the full `.\a.ps1 fu` lost its worker mid-run, which `assert-test-suite-complete` correctly reports as a failed run rather than a result. Compilation is healthy in the same window, so this is not the change under test. Parked as S1463. Separately, `PermissionRegistryManifestParityTest` reports `tests=1 skipped=1` under `standard`, so even a green run of it here would prove nothing about the flavors whose composition differs - the coverage gap already ticketed as S1453 and S1455. The step stays `[~]` and the phase stays In Progress; nothing is marked done on an unproven predicate.
- 2026-08-07 - Phase-boundary audit (protocol Layer 1 only - no lifecycle, coroutine, listener or Room surface was touched). Contract in `domain/`, implementations and Hilt modules in their own flavor source sets, mutually exclusive by construction (2 mounts vs 4), naming mirrors the `LauncherModeContract` precedent, `@Singleton` matches it. No P0/P1 findings. Noted as expected-for-a-foundation-phase, not defects: `NetworkMonitorContract` has no consumer and the nine new string keys are unreferenced until Phases 07-09.
- 2026-08-07 - Steps 01.4-01.6: static verification PASS. Registry gained `nearby_wifi_devices` (minSdk 33) and `bluetooth_connect` (minSdk 31), both `buildGates = setOf("SUPPORT_NETWORK_MONITOR")`, plus that gate in `buildGateValues`; `read_phone_state` left untouched at one occurrence. `AppSettings` gained `enableNetworkMonitor` and `recordGnssTrack` with DataStore keys, reads and writes; nine string keys added across EN/RU/UK with parity green. Step 01.4 stays `[~]` until the unit suite confirms `PermissionRegistryRepositoryImplTest` and `PermissionRegistryManifestParityTest`. `post-change: PASS`.
- 2026-08-07 - Device-profile matrix follow-up, surfaced as an advisory by the closure above: every `AppSettings` field needs a preset row or a registry entry. `recordGnssTrack` joined `docs/settings/device-profile-nonpresettable.json` next to `cameraGeotagEnabled` - the strategic §7 reason is that recording a track changes the Play Data Safety answer, so only the user turns it on. `enableNetworkMonitor` got an empty preset row and an applier branch beside `enableCalculator`. Gate green, `post-change: PASS`.
- 2026-08-07 - Step 01.3: Verification 4/4 PASS. `src/networkMonitor/AndroidManifest.xml` declares CHANGE_WIFI_STATE, BLUETOOTH_ADMIN (maxSdk 30), NEARBY_WIFI_DEVICES (minSdk 33) and BLUETOOTH_CONNECT (minSdk 31); `src/networkMonitor/` is referenced three times in the build script. `post-change: PASS`.
- 2026-08-07 - Step 01.2: rewritten before execution and Verification 5/5 PASS. The planned `CapabilityAvailability.isNetworkMonitorAvailable()` was implemented, then reverted: the `flavor-flags` ratchet refuses to raise its baseline, so a fourth `BuildConfig` read in that file is rejected by design - it is frozen debt, and the only name-excluded reader in `src/main` is `PermissionRegistryRepositoryImpl`. Replaced with the `LauncherModeContract` seam: `NetworkMonitorContract` in `src/main`, real and no-op implementations plus same-named Hilt modules in the new `src/networkMonitor` and `src/networkMonitorDisabled` source sets, mounted 2 and 4 times respectively. `post-change: PASS` (exit 0).
- 2026-08-07 - Repo tooling fixed inside the step (CLAUDE.md Rule 13): `scripts/utils/agent-lock.ps1` read `turnGrantedAt` directly on a queue ticket, which is a terminating error under a caller's `Set-StrictMode` when the ticket predates that field - it took the detekt gate down with it. Both reads now probe `PSObject.Properties` first; the following closure ran through the BUILD.LOCK queue cleanly.
- 2026-08-07 - Step 01.4 retried on a free BUILD.LOCK, still not closable. Static half is fully green this run: `read_phone_state`, `nearby_wifi_devices` and `bluetooth_connect` each appear exactly once as an entry `id` (lines 273/315/325), `SUPPORT_NETWORK_MONITOR` three times, and all four `perm_*` keys exit 0 on `check_strings_localized.ps1`. The test half is still unreachable - `check-standard-fast.ps1 -Mode Unit -Tests "*PermissionRegistry*"` compiled the test sources cleanly (`compileStandardDebugUnitTestKotlin` executed) and then lost the test JVM at startup: `Connection reset by peer` -> `Process 'Gradle Test Executor 2' finished with non-zero exit value 10`, BUILD FAILED in 46s, exit 1. Log: `temp/S1433/test-permissionregistry.log`. That the failure lands after a clean test compile confirms it is the S1463 tooling outage and not this change. Step stays `[~]`, phase stays In Progress, ticket parked `BlockByOtherTask` on S1463.
- 2026-08-08 - Phase-boundary audit, second pass. The 2026-08-07 audit predates steps 01.4-01.6, so it did not cover them; this pass does. Layers 1 and 5 only, since the phase still touches no lifecycle, coroutine, listener or player surface. `enableNetworkMonitor` and `recordGnssTrack` default `false` in `AppSettings` and their DataStore reads fall back to `false` as well, so the model default and the persisted default cannot drift apart - the usual defect in this shape, absent here. Reads and writes are symmetric, and DataStore keeps the path main-safe by construction. `recordGnssTrack` sits in `device-profile-nonpresettable.json` so no preset can switch it on behind the user, which is what strategic §7 requires; the `device-profile-matrix-gate` re-confirmed that today. No P0/P1 findings.
- 2026-08-08 - Step 01.4 closed, and with it the phase. The S1463 tooling outage that parked this ticket is `Verified`, so the test half finally ran: `check-standard-fast.ps1 -Mode Unit -Tests "*PermissionRegistry*"` -> BUILD SUCCESSFUL in 1m 40s, exit 0. JUnit XML, read rather than inferred from the exit code: `PermissionRegistryManifestParityTest` tests=3 skipped=0 failures=0 errors=0, `PermissionRegistryRepositoryImplTest` tests=9 skipped=0 failures=0 errors=0. The parity test is the load-bearing one - it fails when a registry row names a permission the merged manifest does not declare, so a green run is the proof that the `src/networkMonitor` manifest injection actually reaches the standard variant and that the two new rows are backed by real declarations. The `skipped=1` recorded against this test in earlier notes is gone; it now reports three real cases. Static half re-proven the same day rather than trusted from the 2026-08-07 entry: entry ids `nearby_wifi_devices`, `bluetooth_connect` and `read_phone_state` one occurrence each, `SUPPORT_NETWORK_MONITOR` three, all four `perm_*` keys exit 0.
- 2026-08-08 - The first attempt at the above failed for a reason outside this ticket, recorded so the log does not read as a flaky test: `compileStandardDebugKotlin` FAILED on `worker/ScheduledOperationsWorker.kt` with `Unresolved reference 'NotificationIcons'`. A sibling session was mid-migration on S1399 (`/spec-dev S1399 step 02.1`) and had released `CODE.LOCK` leaving the call site using `NotificationIcons.STATUS_BAR` while the file still imported only `NotificationIds`. Fixed inline as a one-line import per CLAUDE.md 3.1 (trivial, so fixed rather than parked); no other file in the tree used the class without importing it.
- 2026-08-07 - Plan corrections applied before execution, from reading the current code: the permission-registry field is `buildGates`, not `flavorGates`; a `read_phone_state` entry already exists gated on `SUPPORT_LAUNCHER` and `evaluateBuildGates` is a conjunction, so it is not re-added; the manifest permissions moved out of `src/main/AndroidManifest.xml` into a new `src/networkMonitor` source set mounted by `standard` and `noLegal` only, because `src/main` merges into every flavor and would recreate the S1442/S1454 defect that `PermissionRegistryManifestParityTest` fails on. The four permission-label strings were folded into Step 01.4, which is the step that references them.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `compileStandardDebugKotlin` and `compileStandardDebugUnitTestKotlin` both executed clean inside the 2026-08-08 test run, BUILD SUCCESSFUL in 1m 40s.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `pwsh -NoProfile -File scripts/docs/generate-flavor-matrix.ps1` re-run, `docs/FLAVOR_MATRIX.md` line 43 reads `SUPPORT_NETWORK_MONITOR | [+] | [+] | [-] | [-] | [-] | [-]`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`NetworkMonitorContract.isAvailableInBuild` (`domain/networkmonitor/`, one implementation per flavor source set, on the `LauncherModeContract` model) is the single availability entry point. This note previously named `CapabilityAvailability.isNetworkMonitorAvailable()`, which was never written: step 01.2 deliberately left `CapabilityAvailability` alone because the `flavor-flags` ratchet refuses to raise its baseline, and Phase 07 hit the dead name. The three runtime permissions are registry-driven: later phases check grant state, never request through their own flow. `AppSettings.enableNetworkMonitor` and `AppSettings.recordGnssTrack` exist and default to `false`.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no user-facing surface changed beyond three unused string keys.
