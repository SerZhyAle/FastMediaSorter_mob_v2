# Phase 01 — Foundations: NetworkReachabilityGate

**Strategic spec:** [`../S0025_smb-fast-fail.md`](../S0025_smb-fast-fail.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Introduce a single `NetworkReachabilityGate` abstraction that exposes `requireAnyNetwork()` and `requireWifi()` operations, both throwing `NetworkConnectionLostException` synchronously when the precondition fails. Make `NetworkContextAnalyzer` Hilt-injectable so the gate can be wired into network sources. Update `NetworkErrorMessageMapper` to handle the "no transport at all" case. Cover with unit tests. No call sites are migrated yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic spec `Status: Approved`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkReachabilityGate.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkContextAnalyzer.kt` | Modified | ≤ 100 |
| ~~`app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkContextModule.kt`~~ | ~~New~~ — not needed (audit in 01.4 confirmed no existing `@Provides`; both `NetworkContextAnalyzer` and `NetworkReachabilityGate` are constructor-injectable) | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapper.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceNavigationCoordinator.kt` | Modified | ≤ existing |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/network/NetworkReachabilityGateTest.kt` | New | ≤ 200 |

---

## Steps

### Step 01.1 — Make NetworkContextAnalyzer Hilt-injectable

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkContextAnalyzer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Annotate `NetworkContextAnalyzer` with `@Singleton` and replace its primary constructor with `@Inject constructor(@ApplicationContext context: Context)`. Add a method `hasAnyNetwork(): Boolean` returning `true` iff `connectivityManager.activeNetwork != null` and the active network has any transport (cellular OR Wi‑Fi OR ethernet). Add `hasWifi(): Boolean` returning `true` iff active transport includes `TRANSPORT_WIFI` or `TRANSPORT_ETHERNET`. Keep all existing public methods.

**Verification:**

- `Grep` — `@Singleton` matches once in `NetworkContextAnalyzer.kt`.
- `Grep` — `@Inject constructor` matches once in `NetworkContextAnalyzer.kt`.
- `Grep` — `fun hasAnyNetwork\(\)` matches once.
- `Grep` — `fun hasWifi\(\)` matches once.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-04-29 — Verification 5/5 PASS. Files: NetworkContextAnalyzer.kt (~100 LOC). Dev log recorded.

---

### Step 01.2 — Update direct instantiation in ResourceNavigationCoordinator

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/ResourceNavigationCoordinator.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace the manual `NetworkContextAnalyzer(context)` instantiation with constructor injection — add `private val networkContextAnalyzer: NetworkContextAnalyzer` to the coordinator's constructor parameters. Remove the field initializer that calls `NetworkContextAnalyzer(context)`. Propagate the dependency to the call site (`@Inject` chain or assisted factory — match the existing pattern of the surrounding class).

**Verification:**

- `Grep` — `NetworkContextAnalyzer\(context\)` returns zero hits in `ResourceNavigationCoordinator.kt`.
- `Grep` — `networkContextAnalyzer: NetworkContextAnalyzer` matches once in `ResourceNavigationCoordinator.kt` (constructor parameter).

**Status:** `[x]` done

**Step Log:**

- 2026-04-29 — Verification 2/2 PASS. Files: ResourceNavigationCoordinator.kt, MainViewModel.kt (propagated injection through Hilt chain). Dev log recorded.

---

### Step 01.3 — Introduce NetworkReachabilityGate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkReachabilityGate.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a `@Singleton class NetworkReachabilityGate @Inject constructor(private val analyzer: NetworkContextAnalyzer)` exposing two synchronous methods:
> - `fun requireAnyNetwork(resourceLabel: String)` — throws `NetworkConnectionLostException` with a Timber.w log `"NetworkReachabilityGate: no-network for $resourceLabel"` when `!analyzer.hasAnyNetwork()`.
> - `fun requireWifi(resourceLabel: String)` — calls `requireAnyNetwork(resourceLabel)` first, then throws `NetworkConnectionLostException` with Timber.w log `"NetworkReachabilityGate: no-wifi for $resourceLabel"` when `!analyzer.hasWifi()`.
>
> Both methods return `Unit` on success. Use Timber only — no `Log.d`. Resource label is a free-form string (`"SMB"`, `"FTP"`, etc.) used purely for diagnostics.

**Verification:**

- `Glob` — `NetworkReachabilityGate.kt` exists.
- `Grep` — `class NetworkReachabilityGate` matches once.
- `Grep` — `fun requireAnyNetwork\(` matches once.
- `Grep` — `fun requireWifi\(` matches once.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-04-29 — Verification 5/5 PASS. Files: NetworkReachabilityGate.kt (~46 LOC). Dev log recorded.

---

### Step 01.4 — Wire DI module if needed

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkContextModule.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Audit existing Hilt modules for any `@Provides` of `NetworkContextAnalyzer`. If one exists in another module, remove it (the class is now `@Inject`-constructable). If `NetworkReachabilityGate` requires no `@Provides` (covered by `@Inject` constructor + `@Singleton`), this file is not needed — delete the New entry from this phase. Otherwise create `NetworkContextModule` with `@Module @InstallIn(SingletonComponent::class)` and any required `@Provides` bindings.

**Verification:**

- `Grep` — `@Provides.*NetworkContextAnalyzer` returns zero hits across the module (the class is constructor-injected).
- If `NetworkContextModule.kt` exists: `Grep` — `@Module` matches once and `@InstallIn(SingletonComponent::class)` matches once.

**Status:** `[x]` done

**Step Log:**

- 2026-04-29 — Audit confirmed no existing `@Provides NetworkContextAnalyzer` in any module. Both `NetworkContextAnalyzer` (Step 01.1) and `NetworkReachabilityGate` (Step 01.3) are constructor-injectable via `@Inject` + `@Singleton`. New `NetworkContextModule.kt` not created (entry struck from Files Touched). Verification 1/1 PASS.

---

### Step 01.5 — Extend NetworkErrorMessageMapper for no-transport case

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/exceptions/NetworkErrorMessageMapper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `toContextAwareMessage`, before the cellular and private-IP branches, add a branch: if `isConnectivityError && !contextAnalyzer.hasAnyNetwork()`, return the existing `error_network_connection_lost` string. This ensures the generic "no connection" text fires when no transport is active, regardless of resource type. Do not introduce new string resources.

**Verification:**

- `Grep` — `hasAnyNetwork\(\)` matches once in `NetworkErrorMessageMapper.kt`.
- `Grep` — `error_network_connection_lost` matches at least once in `NetworkErrorMessageMapper.kt`.
- `Grep` — `<string name="error_network_connection_lost"` matches in `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (already present — verify).

**Status:** `[x]` done

**Step Log:**

- 2026-04-29 — Verification 3/3 PASS. Files: NetworkErrorMessageMapper.kt (+4 LOC). Trilingual strings already present. Dev log recorded.

---

### Step 01.6 — Unit tests for NetworkReachabilityGate

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/network/NetworkReachabilityGateTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create JUnit tests using mocked `NetworkContextAnalyzer`. Cover:
> - `requireAnyNetwork()` succeeds when `hasAnyNetwork() == true`.
> - `requireAnyNetwork()` throws `NetworkConnectionLostException` when `hasAnyNetwork() == false`.
> - `requireWifi()` succeeds when both `hasAnyNetwork()` and `hasWifi()` are true.
> - `requireWifi()` throws `NetworkConnectionLostException` when `hasAnyNetwork() == false` (delegates to `requireAnyNetwork`).
> - `requireWifi()` throws `NetworkConnectionLostException` when `hasAnyNetwork() == true` but `hasWifi() == false`.
>
> Use MockK or the existing test mocking framework — match the style of `NetworkErrorClassifierTest.kt`.

**Verification:**

- `Glob` — `NetworkReachabilityGateTest.kt` exists.
- `Grep` — `@Test` matches at least 5 times in this file.
- `Grep` — `NetworkConnectionLostException` matches at least 3 times in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-04-29 — Verification 3/3 PASS (file exists, @Test=5, NetworkConnectionLostException=5). Files: NetworkReachabilityGateTest.kt (~50 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `assembleStandardDebug` PASS (build 2.60.4290.137).
- [x] All unit tests in `NetworkReachabilityGateTest.kt` pass.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `NetworkReachabilityGate` is available as `@Inject` dependency anywhere in `app_v2`.
- `requireAnyNetwork(label)` is the universal fast-fail; `requireWifi(label)` is the SMB-specific gate.
- Both throw the existing `NetworkConnectionLostException` — error mapper already produces correct user-facing text.
- Phase 02 wires `requireWifi("SMB")` into the SMB connection path.
- Phase 03 wires `requireAnyNetwork("FTP" / "SFTP")` into the FTP/SFTP paths.
- Phase 04 wires `requireAnyNetwork("Cloud-<provider>")` into Cloud REST clients, with care for WorkManager-driven background calls.

---

## Rollback Plan

Revert the phase commit(s). No persistent data, schema, or user-facing surface introduced — pure new abstraction with no production call sites yet.
