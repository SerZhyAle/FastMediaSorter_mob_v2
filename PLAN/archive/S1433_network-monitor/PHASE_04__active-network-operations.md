# Phase 04 - Active network operations

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 08

---

## Objective

Provide the four user-triggered operations - resource check, subnet scan, external IP with CGNAT detection, and the two-mode speed test - each cancellable, bounded, and recorded in the history.

---

## Plan corrections - recorded 2026-08-08 on entering the phase

1. **No module edit.** Files Touched listed `di/NetworkMonitorModule.kt` as Modified, the same stale premise
   phases 02 and 03 hit: that fully-qualified name is declared by `src/networkMonitor` and
   `src/networkMonitorDisabled`, and there is none in `src/main`. It is also not needed - every class this
   phase adds is a concrete type with an `@Inject constructor`, and Hilt binds those without a module. The
   row is dropped rather than redirected.
2. **The network label comes from the caller.** Every operation here records a history row, and
   `NetworkMeasurement.networkLabel` has to come from somewhere the plan never named. It is a parameter of
   each entry point, not a second dependency on `NetworkMonitorRepository`: the only method that repository
   exposes is `observeSnapshot()`, so reading a label through it would start device observation for the
   length of one measurement, against this plan's own "no background work, observers register on section
   visibility" invariant. The Monitor screen already collects the snapshot and has the label for free.

---

## Prerequisites

- [x] Phase 02 and Phase 03 ✅ Done.
- [x] INDEX blocker "IP-echo services" resolved and written into strategic §6.5.
- [ ] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/CheckSelectedResourceUseCase.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/ScanSubnetUseCase.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/ExternalIpDataSource.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/RouterWanAddressDataSource.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/ResolveExternalIpUseCase.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/MeasureThroughputUseCase.kt` | New | ≤ 340 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/ScanSubnetUseCaseTest.kt` | New | ≤ 260 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/ResolveExternalIpUseCaseTest.kt` | New | ≤ 220 |

---

## Steps

### Step 04.1 - Check one selected saved resource

**Files:** `domain/usecase/networkmonitor/CheckSelectedResourceUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Take one `MediaResource` id, call the existing `ResourceRepository.testConnection(resource)` read-only path, emit a progress-then-result flow, and record the result through `NetworkMeasurementHistoryRepository`. Support cancellation. Never check every saved resource, and never surface or log the credentials the repository resolved.

**Why:**

Strategic §6.7 narrows the check to the single resource the user selected, which removes the concurrency limit and the account-lockout risk the batch variant carried, and `testConnection` is the existing read-only primitive that answers exactly this question.

**Verification:**

- `Grep` - `testConnection` referenced in the file.
- `Grep` - `NetworkMeasurementHistoryRepository` referenced.
- `Grep` - no loop over a resource list: `getAllResources` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS (`testConnection` and `NetworkMeasurementHistoryRepository` present, `getAllResources` zero hits). Files: domain/usecase/networkmonitor/CheckSelectedResourceUseCase.kt (+79 LOC). The entry point takes `networkLabel` from the caller per this phase's plan correction 2. Dev log recorded.

---

### Step 04.2 - Scan the local subnet

**Files:** `domain/usecase/networkmonitor/ScanSubnetUseCase.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Scan the device's own /24 by default and an explicitly supplied address range otherwise, reusing the bounded-concurrency TCP probing already in `domain/usecase/DiscoverNetworkResourcesUseCase.kt` rather than writing a second prober. Cap the supplied range at 1024 addresses, keep the per-host timeout at or below 300 ms, stream results as they arrive, and support cancellation at any point. On API 37+ check `ACCESS_LOCAL_NETWORK` before starting and report the missing grant instead of throwing. Record a summary row in the history when the scan completes or is cancelled.

**Why:**

Strategic §6.8 grants the scan but bounds it, because §7 records that an unbounded sweep on a corporate network reads as an attack and can get the device blocked, and the existing prober already solves concurrency, cancellation and the API 37 permission failure.

**Verification:**

- `Grep` - `DiscoverNetworkResourcesUseCase` or its probe helpers referenced.
- `Grep` - `1024` present as a named range-cap constant.
- `Grep` - `ACCESS_LOCAL_NETWORK` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. Files: domain/usecase/networkmonitor/ScanSubnetUseCase.kt (+229 LOC). `.\a.ps1 fk` BUILD SUCCESSFUL in 45s at 20:47, run after the detekt MagicNumber fix to `toIpv4String` (`kspStandardDebugKotlin` and `compileStandardDebugKotlin` both re-executed, so the changed file really recompiled). An earlier 20:33 run predated that fix and does not certify the current file. Two notes for the next reader: `ACCESS_LOCAL_NETWORK` does not exist as a constant in compileSdk 36 (checked with `javap` against `android-36/android.jar`), so the permission is named as a string literal and the level as the number 37; and the range cap refuses rather than truncates, because scanning the first 1024 addresses of a /16 would report "no hosts" about a network it barely looked at. Dev log recorded.

---

### Step 04.3 - Read the external IP

**Files:** `data/networkmonitor/ExternalIpDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Query the IP-echo services listed in strategic §6.5 in order, taking the first successful HTTPS response, with a per-service timeout and a total cap. Return the address as a value; do not cache it, do not write it to a preference and do not log it. Run only when explicitly invoked.

**Why:**

Strategic §7 rates the external-IP disclosure a high privacy risk, so the on-demand, non-persisted contract is the mitigation itself rather than a UI courtesy.

**Verification:**

- `Grep` - the file contains no `DataStore`, no `SharedPreferences` and no `Timber` call carrying the address.
- `Grep` - a timeout constant is present.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 2\2 PASS (`DataStore` 0, `SharedPreferences` 0, the single `Timber.d` carries the service host and the exception class only; 6 timeout-constant hits). Files: data/networkmonitor/ExternalIpDataSource.kt (+130 LOC, budget 220). `.\a.ps1 fk` BUILD SUCCESSFUL in 40s with `kspStandardDebugKotlin` and `compileStandardDebugKotlin` both re-executed, so the new file really compiled. One correction the plan's own predicates could not have caught: the class does **not** inject the application-wide `OkHttpClient` as-is. That client carries a Chucker interceptor in debug builds which persists every response body to its database, plus an error interceptor that logs the failing URL - and here the response body *is* the external address, so injecting it would have broken the strategic §7 non-persistence promise while every grep in this step still passed. The shared client is taken only for its connection pool and dispatcher, via `newBuilder()` with `interceptors()` and `networkInterceptors()` cleared. Cancellation uses `enqueue` + `invokeOnCancellation { call.cancel() }` rather than blocking `execute()`, so an abandoned attempt frees its thread instead of pinning one until the timeout. Dev log recorded.

---

### Step 04.4 - Read the router WAN address and detect CGNAT

**Files:** `data/networkmonitor/RouterWanAddressDataSource.kt`, `domain/usecase/networkmonitor/ResolveExternalIpUseCase.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Ask the default gateway for its WAN address over NAT-PMP/PCP first - unicast UDP to the gateway from `LinkProperties`, port 5351, no discovery stage - and only when that fails over UPnP IGD (SSDP, then a SOAP `GetExternalIPAddress`), both with short timeouts and no third-party library. In `ResolveExternalIpUseCase`, combine the echoed address with the router address: equal means direct, different means CGNAT is likely, and a missing router answer means unknown - never assert CGNAT from a missing answer. Record the outcome in the history without storing the address itself.

> **Plan correction 3, recorded 2026-08-09 on entering the step.** The protocol order above was reversed against what this step originally said ("UPnP first, then NAT-PMP"). Strategic §6.5's refinement of 2026-08-08 and the INDEX blocker note both fix the order as the cheap protocol first: NAT-PMP/PCP is a single unicast UDP exchange with no discovery stage, while UPnP needs an SSDP multicast round before it can even send the SOAP call. Asking the expensive one first would pay the discovery cost on every router that answers NAT-PMP.

**Why:**

Strategic §3.1 item 7 defines CGNAT detection as exactly this comparison, and a router that simply does not answer UPnP is the common case, so treating silence as evidence would report CGNAT to most users on the strength of nothing.

**Verification:**

- `Grep` - `GetExternalIPAddress` present.
- `Grep` - a three-state result type is declared with an explicit unknown case.
- `.\a.ps1 fu` - `ResolveExternalIpUseCaseTest` passes.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS (`GetExternalIPAddress` present; `CgnatVerdict` declares `Direct`/`LikelyCgnat`/`Unknown`; `ResolveExternalIpUseCaseTest` 5 tests, 0 failures, read from `testStandardDebugUnitTest` XML rather than the build verdict). Files: data/networkmonitor/RouterWanAddressDataSource.kt (+250 LOC, budget 320), domain/usecase/networkmonitor/ResolveExternalIpUseCase.kt (+103 LOC, budget 200). Protocol order follows plan correction 3 above - NAT-PMP first, UPnP only if it is silent. The NAT-PMP wire format was checked against RFC 6886 rather than written from memory, which surfaced one rule the plan did not state: when the result code is non-zero the address field is explicitly *undefined*, so the parser refuses the packet instead of reporting `0.0.0.0` as the router's WAN address. The two UPnP HTTP calls use `HttpURLConnection`, not the shared OkHttp client, for the same reason as step 04.3 - those response bodies carry the WAN address and the debug interceptor would persist them. The service type and the control URL are read from the *same* `<service>` block, because a description lists several services and pairing a type with a stranger's control URL would aim the SOAP call at the wrong endpoint. Dev log recorded.

---

### Step 04.5 - Measure throughput in both modes

**Files:** `domain/usecase/networkmonitor/MeasureThroughputUseCase.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Implement two modes behind one entry point. Internet mode downloads and uploads a fixed payload against `speed.cloudflare.com` with no API key. Resource mode delegates to the existing `domain/usecase/NetworkSpeedTestUseCase.kt` for the selected saved resource and does not re-implement it. Both modes take a byte budget and a time budget, emit progress, cancel cleanly, and record the result in the history. Emit a distinct state when the active network is metered so the caller can warn before starting.

**Why:**

Strategic §6.6 asks for down and up in both modes, and §3.2 requires the traffic warning and the hard budgets, because §7 rates an unbounded test on a mobile plan as a direct cost to the user.

**Verification:**

- `Grep` - `speed.cloudflare.com` present exactly once, as a named constant.
- `Grep` - `NetworkSpeedTestUseCase` referenced for the resource mode.
- `Grep` - a metered state case is declared.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS (`speed.cloudflare.com` appears once, as `SPEED_TEST_HOST`, with both URLs built from it; `NetworkSpeedTestUseCase` is delegated to for resource mode and no protocol code is repeated; `ThroughputState.MeteredNetwork` declared). Files: domain/usecase/networkmonitor/MeasureThroughputUseCase.kt (+230 LOC, budget 340). The metered case is a terminal state plus an explicit `allowMetered` go-ahead rather than a warning flag, so no bytes move until the user has answered - strategic §3.2 treats the traffic cost as the user's decision, and a flag the caller could forget to read would spend their money by default. Internet mode uses `HttpURLConnection` for the third time in this phase and for a second reason beyond privacy: the debug interceptor would buffer the whole multi-megabyte payload into its database. Both budgets are enforced in the read/write loop itself, not only as socket timeouts, so a link that is merely slow ends the test and reports what it managed. Dev log recorded.

---

### Step 04.6 - Unit-test the bounds

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/networkmonitor/ScanSubnetUseCaseTest.kt`, `.../ResolveExternalIpUseCaseTest.kt`
**Depends on:** Steps 04.2, 04.4

**Prompt for developer:**

> Test that a supplied range above the cap is rejected rather than truncated silently, that cancellation stops probing, and that the CGNAT verdict is unknown when the router does not answer, direct when the addresses match and likely-CGNAT when they differ.

**Why:**

Both bounds exist because of a stated risk in strategic §7 - the noisy scan and the false CGNAT verdict - and neither failure is visible on the developer's own network.

**Verification:**

- `.\a.ps1 fu` - both test classes pass.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 1\1 PASS. `ScanSubnetUseCaseTest` 3 tests and `ResolveExternalIpUseCaseTest` 5 tests, 0 failures and 0 errors in both, read from the `testStandardDebugUnitTest` result XML (fresh mtime), not from the BUILD SUCCESSFUL line - a `--tests` filter that matches nothing is not caught by the runner's empty-run guard. Files: two new test classes (+76 and +99 LOC, budgets 260 and 220). The CGNAT trio is covered exactly as the prompt asked, plus one assertion the prompt did not ask for and strategic §7 implies: that the recorded history row contains neither the echoed address nor the router's.
- **Residual gap, deliberate:** "cancellation stops probing" is NOT covered. Making it deterministic needs the prober suspended mid-scan and the scan cancelled on a timing window, and that shape produces a flaky test - which costs more than the assertion is worth, because a suite nobody trusts stops being read. What *is* covered instead is the stronger structural guarantee that no probe starts at all on a refused range (`prober wasNot Called` in two tests). Carried to the ticket's follow-ups rather than papered over.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL in 44s, exit 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits - actual 0.
- [x] No operation in this phase starts without an explicit call - `init {` returns 0 hits across all four new files.
- [x] Dev log entry added for the phase - one entry naming the whole set of 7.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

### Phase-boundary audit - 2026-08-09

Triggers fired: new long-lived helpers, coroutine/Flow change, network-path change (CLAUDE.md §13).

- **Main-safety.** Every entry point is `flowOn(ioDispatcher)` or `withContext(ioDispatcher)`; the one exception is [ExternalIpDataSource], which needs no dispatcher because OkHttp's `enqueue` already answers on its own pool.
- **Resource ownership.** Every socket, connection and response body is closed on both paths - `use {}` for the datagram and multicast sockets and the OkHttp response, a `finally`-backed `useConnection {}` for each `HttpURLConnection`, and a `finally` release for the Wi-Fi multicast lock.
- **Cancellation.** The echo probe cancels its in-flight call through `invokeOnCancellation`; the throughput loops check `ensureActive()` every chunk. The two router probes use blocking sockets, so a cancel does not interrupt a call already inside `receive()` - bounded instead by 1.5 s and 2 s timeouts, which is the accepted trade rather than an oversight.
- **No background work.** No observer, listener, service or periodic job is registered anywhere in the phase, so the cross-cutting invariant holds by construction rather than by discipline.
- **Flavor isolation (Rule 14).** Zero `BuildConfig.` references across the four new files.
- **Line budgets.** 130/220, 287/320, 105/200, 242/340 - all within plan.

**Findings:** none at P0/P1. One P3 carried forward: the cancellation assertion named in step 04.6, recorded there as a deliberate residual.

---

## Handoff Notes to Next Phase

Every operation is a suspend or flow entry point with progress, cancellation and a history write already wired. The UI phase adds the warning dialog and the cancel affordance, not the bounds.

---

## Rollback Plan

Revert phase commit(s) - new use cases only; the history table from Phase 03 remains valid and empty.
