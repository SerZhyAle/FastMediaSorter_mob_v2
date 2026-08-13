# Phase 02 - Domain snapshot and radio control

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05, Phase 07
**Steps done:** 7 / 7
**Completed:** 2026-08-08

---

## Objective

Produce the read model of the device's network state and the shared radio-control component, both headless and unit-testable, reusing `NetworkStateMonitor` and `NetworkContextAnalyzer` rather than wrapping `ConnectivityManager` a second time.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/networkmonitor/NetworkMonitorSnapshot.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/networkmonitor/SectionAvailability.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/ConnectivitySnapshotDataSource.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/TelephonySnapshotDataSource.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/networkmonitor/BluetoothSnapshotDataSource.kt` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/NetworkMonitorRepository.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkMonitorRepositoryImpl.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/NetworkMonitorDataModule.kt` | New | ≤ 120 |
| `app_v2/build.gradle.kts` | Modified | ≤ 20 |
| `app_v2/src/testNetworkMonitor/java/com/sza/fastmediasorter/radio/RadioControlContractImplTest.kt` | New | ≤ 300 |

Three rows this table used to carry are gone, and the reason is the same in each case - the file already
exists under a different owner, so writing the planned one would be a duplicate rather than a delivery:

- `core/network/radio/RadioControl.kt` and `RadioControlImpl.kt` - S1441 shipped the seam first as
  `domain/radio/RadioControlContract.kt` plus `RadioKind.kt`, with the real controller in
  `src/networkMonitor/java/.../radio/RadioControlContractImpl.kt` and a no-op in
  `src/networkMonitorDisabled`. The INDEX invariant "S1433 never writes a second copy" was written
  expecting this phase to be the producer; in the tree the arrow points the other way, and the invariant
  binds all the same.
- `di/NetworkMonitorModule.kt` in `src/main` - the fully-qualified name
  `com.sza.fastmediasorter.di.NetworkMonitorModule` is already taken, twice, by the two flavor source
  sets Phase 01 created. Every flavor mounts exactly one of them, so a third declaration in `src/main`
  is a duplicate JVM class in all six builds. The repository binding goes in `NetworkMonitorDataModule`.

---

## Steps

### Step 02.1 - Model the snapshot and the section availability states

**Files:** `domain/model/networkmonitor/NetworkMonitorSnapshot.kt`, `domain/model/networkmonitor/SectionAvailability.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Define `NetworkMonitorSnapshot` as an immutable record holding: the list of visible networks (transport, active, validated Internet, captive portal, metered, VPN), link details of the active network (interface, IPv4 and IPv6 addresses, DNS servers, default route and gateway, proxy, system bandwidth estimates), the connected Wi-Fi entry, the list of visible SIM entries, the Bluetooth adapter entry, and the sample timestamp. Define `SectionAvailability` as a sealed type with the cases `Available`, `NoHardware`, `NoPermission` and `NoNetwork`; every section-bearing field of the snapshot carries one. Nothing in these files may hold IMEI, phone number, ICCID or any subscriber identifier.

**Why:**

Strategic §11 criterion 2 requires the screen to either show the data or say honestly why it is missing, and §3.2 forbids subscriber identifiers anywhere in the UI, export or logs, so the absence reason must be a modelled value rather than a blank field decided in the UI.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `sealed` and each of `NoHardware`, `NoPermission`, `NoNetwork` present in `SectionAvailability.kt`.
- `Grep` - no *declaration* in `domain/model/networkmonitor/` names a subscriber identifier: `val [a-zA-Z]*(imei|iccid|phoneNumber|bssid|subscriberId|serial)` returns zero case-insensitive hits.

The last predicate was rewritten during execution, because as originally written - zero case-insensitive hits for the bare words anywhere in the directory - it could not pass a file that documents *why* those identifiers are absent. The KDoc naming them is the reason the model is safe, not a violation of it, so the check now tests declarations rather than prose. A zero-hit predicate must never be able to match its own explanation.

**Status:** `[x]` done

---

### Step 02.2 - Collect connectivity and link properties

**Files:** `data/networkmonitor/ConnectivitySnapshotDataSource.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Build the connectivity part of the snapshot from `ConnectivityManager`, `NetworkCapabilities` and `LinkProperties`, and expose it as a `Flow` that registers its callback on collection and unregisters on cancellation. Reuse the existing `core/network/NetworkContextAnalyzer.kt` for capability queries and subscribe to `core/network/NetworkStateMonitor.kt` for change events instead of registering a second global `NetworkCallback`. Add the connected Wi-Fi entry here, without any scan API.
>
> Two facts about that monitor were established by reading it during execution, and both change this step:
>
> 1. **It may not be running.** `NetworkStateMonitor.start()` is not called at process start - it is one of four registrations behind `NetworkLifecycleBootstrapper.ensureInitialized()`, which S0195 ADR-1 defers until a consumer-side remote-flow entry boundary. A Monitor opened on a fresh process, before any SMB/SFTP/stream work, would therefore register its callback on a monitor that never started and would sit there never updating. So collection must call `ensureInitialized()` first; it is idempotent and a cheap no-op afterwards.
> 2. **It deliberately debounces away same-network events.** Its own contract is that only a transition to a *different* network id schedules a notification, and capability or link ticks on the same network are ignored. That is right for its existing consumers, who care whether a connection died, and wrong as the Monitor's only trigger, because RSSI, link speed and DNS all change without the network id changing. So the flow also re-samples on a bounded tick while it is collected. This is not background work and does not breach the phase invariant: the tick exists only for the lifetime of the collection, which is the lifetime of the visible section, and it stops with it.
>
> Do not solve either by registering a second global `NetworkCallback` - the per-UID callback limit recorded in research artifact 01 is exactly why this data source borrows the existing one.

**Why:**

Research artifact 01 records that Android limits registered callbacks per UID and that `NetworkStateMonitor` already owns the process-wide callback, so a parallel observer both risks the limit and produces a second, disagreeing view of the same state.

**Verification:**

- `Grep` - `NetworkStateMonitor` and `NetworkContextAnalyzer` both referenced in `ConnectivitySnapshotDataSource.kt`.
- `Grep` - `startScan` returns zero hits in `data/networkmonitor/`.
- `Grep` - `awaitClose` present, proving the callback is unregistered on cancellation.
- `Grep` - `ensureInitialized` present, proving the borrowed monitor is started before its callback is trusted.
- `Grep` - `registerNetworkCallback` and `registerDefaultNetworkCallback` return zero hits in `data/networkmonitor/`, proving no second global callback was registered. The predicate names the *call*, not the type `ConnectivityManager.NetworkCallback`, because the KDoc explaining why there is no second callback necessarily names the type - the same self-matching flaw corrected in step 02.1, repeated here while writing this step and caught by running the check.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

### Step 02.3 - Collect telephony state for up to two SIMs

**Files:** `data/networkmonitor/TelephonySnapshotDataSource.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Report the active modem count unconditionally through `TelephonyManager.getActiveModemCount()`, and per-subscription operator name and state only when `READ_PHONE_STATE` is granted, emitting `SectionAvailability.NoPermission` otherwise. Enumerate subscriptions through `SubscriptionManager`. Do not read, log or return device identifiers, phone numbers, ICCID or radio configuration.

**Why:**

Research artifact 01 states the modem count needs no permission while per-SIM detail does, and strategic §3.2 forbids subscriber identifiers outright, so the split must exist in the data source rather than being filtered later in the UI.

**Verification:**

- `Grep` - the modem count is actually read: `\.activeModemCount\b` present. Named as the property rather than as `getActiveModemCount`, because Kotlin reads the getter as a synthetic property, so the literal Java name survives only in prose - and a predicate satisfied by its own comment proves nothing.
- `Grep` - `READ_PHONE_STATE` referenced as a grant check.
- `Grep` - no *call* to a subscriber identifier reaches `data/networkmonitor/`: `\.(getDeviceId|getImei|getLine1Number|getSimSerialNumber)\(|\.(deviceId|imei|line1Number|simSerialNumber)\b` returns zero hits.

The predicate was rewritten during execution for the third time in this phase, for the same reason as in steps 02.1 and 02.2 and with the same fix. As originally written it forbade the bare words anywhere in the directory, so it failed the very file that documents why those APIs are never called - the KDoc naming them is the guarantee, not a breach of it. It now names the call, which prose cannot spell.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `TelephonySnapshotDataSource.kt` (+156 LOC) reads the modem count
  through `manager.activeModemCount` with no permission at all and gates per-subscription detail on
  `ContextCompat.checkSelfPermission(READ_PHONE_STATE)`, emitting
  `SectionAvailability.NoPermission(READ_PHONE_STATE)` otherwise. No call to a subscriber identifier
  exists in the directory (0 hits). `.\a.ps1 fk` exit 0.
- **The manifest claim behind this step was checked rather than trusted.** `src/networkMonitor`s manifest
  states that `READ_PHONE_STATE` is deliberately absent because `src/launcherEnabled` declares it and
  the same two flavors mount both source sets. Confirmed against `app_v2/build.gradle.kts`: `standard`
  and `noLegal` each mount `src/launcherEnabled/java` and `src/networkMonitor/java`, and the other four
  mount `src/networkMonitorDisabled/java`. So the permission is declared everywhere the Monitor ships,
  and this step adds no manifest change - the registered permission docs are untouched.
- Two API floors are handled rather than assumed, because `legacy` still ships minSdk 23:
  `getActiveModemCount` is API 30, so below it the deprecated `phoneCount` is read behind an explicit
  `@Suppress("DEPRECATION")`; `getDefaultDataSubscriptionId` is API 24, so below it no SIM is marked as
  the data one instead of one being guessed.
- Both `SecurityException` catches are narrow and each has a recovery plus a `Timber.w`: a manufacturer
  build can refuse `activeSubscriptionInfoList` or `phoneType` even with the grant, and a diagnostic
  screen that crashes while reporting that nothing is there is worse than one that says so.
- Sampling is a plain function, not a `Flow`. The class registers no listener and starts no timer, so it
  holds nothing when the Monitor is closed; step 02.5 re-reads it on the connectivity flow tick, which is
  already capped at one sample per second. This keeps the phase invariant "no background work" literally
  true for this data source rather than argued.

---

### Step 02.4 - Collect Bluetooth adapter state

**Files:** `data/networkmonitor/BluetoothSnapshotDataSource.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Report whether a Bluetooth adapter exists and whether it is on. Report bonded-device details only when `BLUETOOTH_CONNECT` is granted on API 31+, and emit `SectionAvailability.NoHardware` when the device has no adapter. Never call a discovery or scan API.

**Why:**

Research artifacts 02 and 03 exclude Bluetooth scanning from the first release and permit only connected-device data, so a scan call here would both break that constraint and pull in a permission the strategic spec refuses to declare.

**Verification:**

- `Grep` - `startDiscovery` and `BluetoothLeScanner` return zero hits in `data/networkmonitor/`.
- `Grep` - `NoHardware` referenced in `BluetoothSnapshotDataSource.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `BluetoothSnapshotDataSource.kt` (+95 LOC) reports the adapter's
  existence and on/off state, counts bonded devices without ever naming one, and returns
  `SectionAvailability.NoHardware` when the device has no adapter. Zero hits for a discovery or scan call
  in `data/networkmonitor/`. `.\a.ps1 fk` exit 0.
- The KDoc deliberately does not spell the two forbidden API names. This phase has already corrected the
  same self-matching predicate twice (steps 02.1 and 02.2), and a zero-hit check over this directory would
  have been failed by a comment promising not to call them - so the promise is phrased without the words.
- The permission name is chosen per API level rather than fixed, because API 31 moved adapter access from
  the install-time permission to the runtime one, and the UI has to offer the user whichever one actually
  governs their device.
- **parked: S1515 bluetooth-permission-missing-below-api31.** `android.permission.BLUETOOTH` is declared
  nowhere in the app, yet on API 30 and below it is what governs the adapter reads this step makes - the
  manifest covers only `BLUETOOTH_ADMIN` (maxSdk 30, the S1441 toggle path) and `BLUETOOTH_CONNECT`
  (minSdk 31). This data source degrades safely either way, so nothing here changes; but on Android
  8.0-11 the section would report a permission gap forever, and Phase 08 must not ship a Grant button for
  a permission the manifest never declares. Not fixed inline: it moves the store permission surface, it
  has to keep `PermissionRegistryManifestParityTest` green, and confirming the runtime behaviour needs an
  API 26-30 device that is not attached. Android lint cannot answer it either - compileSdk 36 stubs carry
  only the `BLUETOOTH_CONNECT` annotation, so the older requirement is invisible to it.

---

### Step 02.5 - Compose the repository and bind it

**Files:** `domain/repository/NetworkMonitorRepository.kt`, `data/repository/NetworkMonitorRepositoryImpl.kt`, `di/NetworkMonitorDataModule.kt`
**Depends on:** Steps 02.2, 02.3, 02.4

**Prompt for developer:**

> Declare `NetworkMonitorRepository` with a single `observeSnapshot(): Flow<NetworkMonitorSnapshot>` and implement it by combining the three data sources, dropping the emission rate to at most one snapshot per second. Bind the implementation in a new Hilt module `di/NetworkMonitorDataModule.kt` with `@Binds` inside an `@InstallIn(SingletonComponent::class)` module. The name must not be `NetworkMonitorModule` - both flavor source sets already declare `com.sza.fastmediasorter.di.NetworkMonitorModule`, and every flavor mounts one of them.

**Why:**

Strategic §5.2 makes the screen a consumer of one composed state rather than of three device APIs, and §3.2 caps redraw cost, which is why the rate limit belongs in the repository and not in each observing view.

**Verification:**

- `Grep` - `interface NetworkMonitorRepository` matches exactly once.
- `Grep` - `@Binds` and `NetworkMonitorRepositoryImpl` both present in `di/NetworkMonitorDataModule.kt`.
- `Grep` - `object NetworkMonitorModule` still matches exactly twice across `app_v2/src`, both under flavor source sets.
- `.\a.ps1 fk` - exit 0.
- `.\a.ps1 fkn` - exit 0, proving the binding compiles in the other flavor that mounts the real controller.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 5\5 PASS. `interface NetworkMonitorRepository` matches exactly once;
  `di/NetworkMonitorDataModule.kt` carries `@Binds` and `NetworkMonitorRepositoryImpl`;
  `object NetworkMonitorModule` still matches exactly twice, in `src/networkMonitor` and
  `src/networkMonitorDisabled`, so no third declaration was introduced. `.\a.ps1 fk` exit 0 and
  `.\a.ps1 fkn` exit 0.
- **Neither compile check proves the Hilt graph**, and the step's predicates cannot: `fk`/`fkn` stop at
  Kotlin compilation, while a `MissingBinding` surfaces only in `hiltJavaCompile`. The binding is therefore
  proven by this phase's own Done Criteria build, not by these two exits - recorded so a later reader does
  not mistake two green compiles for a validated graph.
- Only connectivity is observed; telephony and Bluetooth are read on each of its emissions. Two more
  observers would mean two more registrations to unregister for sources that have no event stream worth
  registering for, and the phase invariant "no background work" stays literally true rather than argued.
- The one-per-second cap drops rather than delays, and lets the first value through immediately. A
  delaying operator would postpone the first paint by a second on a screen whose whole job is showing the
  current state; a dropped burst costs nothing because the source re-samples on its own tick. The cap sits
  before composition, so a suppressed emission costs no telephony or Bluetooth read at all.

---

### Step 02.6 - Adopt the existing radio-control seam, write no second copy

**Files:** none - verification only
**Depends on:** Step 02.2

**Prompt for developer:**

> Write no radio class in this phase. Confirm by reading that the component this step was written to produce is already in the tree, shipped by S1441, and that it satisfies every requirement this step listed: `domain/radio/RadioControlContract.kt` declares `isToggleSupported`, `state(kind): Flow<Boolean?>` and `suspend toggle(kind): Boolean`; `RadioKind` is the closed Wi-Fi/Bluetooth pair; `src/networkMonitor/java/.../radio/RadioControlContractImpl.kt` performs the direct call off the main thread and proves the flip by observed state within a capped window, returning `false` on refusal, on a missing `BLUETOOTH_CONNECT` above API 30 and on an unknown starting state; `src/networkMonitorDisabled/.../NoOpRadioControlContract.kt` is the other side of the seam. Record the two contract differences from the original plan text as accepted rather than outstanding: the confirmation window is 1200 ms, not 1500 ms, fixed by S1441 strategic §6.2 against the Android 13 Bluetooth-off case; and the three-case `RadioRequestOutcome` is a boolean plus a caller-side intent, because the system surface for each radio already lives in `core/panel/OsShortcutCatalog.kt` as `Target.fallbackIntent` (`Settings.Panel.ACTION_WIFI` on API 29+, `Settings.ACTION_WIFI_SETTINGS` below, `Settings.ACTION_BLUETOOTH_SETTINGS`), addressable by `Target.radio`. The Monitor's section switch consumes `ToggleRadioTargetUseCase` and that same fallback in Phase 08; nothing in this phase needs a richer return type.

**Why:**

The INDEX cross-cutting invariant "the shared radio-control component produced by Phase 02 is consumed by S1441; S1433 never writes a second copy" was authored expecting this phase to be the producer, and the tree reversed the order - S1441 landed the seam first. The invariant's purpose is one implementation, not one authorship, so honouring it now means consuming what exists; writing the planned classes would create exactly the duplicate it forbids, and a second controller would let the two disagree about whether a radio flipped.

**Verification:**

- `Grep` - `interface RadioControlContract` matches exactly once, under `app_v2/src/main/java/`.
- `Grep` - `class RadioControlContractImpl` matches exactly once and `class NoOpRadioControlContract` exactly once, each under its own flavor source set. Both predicates name the *declaration*: the bare type name also appears as an import and a provider return in the same flavor module, so counting references would have failed a tree that is exactly right - the fourth instance of that trap in this phase.
- `Grep` - `RadioControl` returns zero hits under `app_v2/src/main/java/com/sza/fastmediasorter/core/network/`, proving no second copy was written here.
- `Grep` - `fallbackIntent` and `radio = RadioKind` both present in `core/panel/OsShortcutCatalog.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS, and no radio class was written. `interface RadioControlContract`
  matches exactly once under `src/main/java/.../domain/radio/`; `class RadioControlContractImpl` once under
  `src/networkMonitor` and `class NoOpRadioControlContract` once under `src/networkMonitorDisabled`;
  `RadioControl` returns zero hits under `core/network/`, so nothing was duplicated there;
  `OsShortcutCatalog.kt` carries `fallbackIntent` plus `radio = RadioKind.WIFI` and
  `radio = RadioKind.BLUETOOTH`.
- The contract was read, not assumed, and it satisfies every requirement this step was written to produce:
  `isToggleSupported`, `state(kind): Flow<Boolean?>` documented so `null` means unknown and never off, and
  `suspend toggle(kind): Boolean` documented so the platform's own return value is not treated as proof.
  `RadioControlContractImpl` performs the direct call inside `withContext(Dispatchers.IO)` and confirms by
  observed state inside `withTimeoutOrNull(CONFIRM_WINDOW_MS)`, and gates Bluetooth on
  `BLUETOOTH_CONNECT` above API 30. `RadioKind` is a closed pair: the impl's `when` covers `WIFI` and
  `BLUETOOTH` with no `else` branch, which is what makes it exhaustive.
- Both recorded contract differences hold in the tree and are accepted rather than outstanding: the
  confirmation window is 1200 ms, and the three-case outcome is a boolean plus a caller-side intent, whose
  system surface is already `Target.fallbackIntent` in `OsShortcutCatalog`. Nothing in this phase needs a
  richer return type.

---

### Step 02.7 - Unit-test the radio-control refusal contract, in a flavor-scoped test source set

**Files:** `app_v2/src/testNetworkMonitor/java/com/sza/fastmediasorter/radio/RadioControlContractImplTest.kt`, `app_v2/build.gradle.kts`
**Depends on:** Step 02.6

**Prompt for developer:**

> The class under test is `RadioControlContractImpl`, which lives in `src/networkMonitor/java` and therefore does not exist in four of the six flavors. Do not put the test in `src/test/java`: that source set compiles against every variant, so a reference to a flavor-only class breaks the unit-test compile of `lite`, `photos`, `legacy` and `vr` - the same shape already ticketed as S1453 and S1455. Create `app_v2/src/testNetworkMonitor/java` and mount it in `app_v2/build.gradle.kts` on the unit-test source sets of `standard` and `noLegal` only, mirroring how their main blocks mount `src/networkMonitor/java`. Then cover four cases against a fake `RadioStateReader`: the direct call lands and the observed state flips, so `toggle` returns `true`; the call is accepted but the observed state never changes, so it returns `false` after the confirmation window; the starting state reads `null`, so it returns `false` without attempting anything; and `BLUETOOTH_CONNECT` is missing above API 30, so it returns `false` for `BLUETOOTH` while `WIFI` is unaffected. Drive the timeout with a test dispatcher rather than real time.

**Why:**

The second case is the defect strategic §7 predicts - a firmware that accepts the call and ignores it - and it is invisible on a developer phone, so only a test pins the behaviour that the owner's Android 8 head unit and a modern phone must share. The third and fourth replace the original `Unsupported` cases, which no longer exist as a return value: the seam expresses "cannot even try" as the same `false`, so they are the only way to prove the impl does not spend the confirmation window on an attempt it knows will fail.

**Verification:**

- `Glob` - `app_v2/src/testNetworkMonitor/java/com/sza/fastmediasorter/radio/RadioControlContractImplTest.kt` exists.
- `Grep` - `src/testNetworkMonitor/java` matches exactly twice in `app_v2/build.gradle.kts`.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*RadioControlContractImpl*"` - exit 0, five test methods reported in the JUnit XML. Five rather than the four this step listed: a granted-`BLUETOOTH_CONNECT` case was added because without it the refusal case passes for the wrong reason - a `toggle` that returned `false` for Bluetooth unconditionally would satisfy every one of the original four.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Flavor Lite` - the lite unit tests **compile and run**, and the run produces **zero** `RadioControlContractImpl` reports. Rewritten from "exit 0" during execution: the suite's exit code answers "is every lite test green", which is not this step's question and which no step of this ticket can control - the leak question is answered by compilation succeeding at all, because a leaked source set would fail to compile against a class the flavor does not have.

**Status:** `[x]` done

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `src/testNetworkMonitor/java` matches exactly twice in
  `app_v2/build.gradle.kts` (lines 613 and 616, `testStandard` and `testNoLegal`); the test file exists;
  `check-standard-fast.ps1 -Mode Unit -Tests "*RadioControlContractImpl*"` exits 0 with
  `tests=5 failures=0 errors=0 skipped=0`, and the XML was copied to
  `temp/S1433/RadioControlContractImplTest.xml` before citing it, per the lesson S1399 recorded about a
  sibling gradle run wiping the first one.
- **No leak into lite, proven positively.** The lite unit run compiled and produced 453 reports for 453
  `*Test.kt` files (ratio 1) and **zero** `RadioControlContractImpl` reports. Compilation succeeding is the
  proof: had the source set leaked, lite would have failed to compile against a class it does not mount -
  the S1450 shape this step was written to avoid.
- **The lite suite still exits 1, for a reason outside this ticket.** The single failure is
  `IconInventoryExportTest > committed icon inventory is fresh`: `docs/icons/icon-inventory.json` is stale
  on `player-command` entries (`STREAM_INFO`/`ic_info`, `TEXT_SETTINGS`/`ic_book`). Nothing in this phase
  touches icons, settings surfaces or player commands. Already covered by open ticket **S1194**
  (`icon-inventory-stale-settings-header-entry`), so no duplicate was drafted, and the render target was
  deliberately not regenerated here: it is generated from another ticket's in-flight source, and
  regenerating it would silently certify that work as finished.
- Robolectric needed a class-level `@Config(sdk = [34])`, which the first run did not have and which cost
  one failed run: Robolectric 4.11.1 caps at API 34 while `targetSdkVersion` is 36, so without the pin the
  runner fails in SDK selection before any test method runs. Every other Robolectric test in this repo
  carries the same pin - the idiom was there to copy and was not copied the first time.
- The fifth test is the one that makes the fourth mean something. With only the refusal cases, a `toggle`
  that returned `false` for Bluetooth unconditionally would satisfy every predicate this step listed, so a
  granted-`BLUETOOTH_CONNECT` case was added to prove the gate is a gate and not a constant.
- The "never settling" source emits the old state and then stays open rather than completing. A completing
  flow would make the confirmation window raise instead of expire, which models the wrong firmware: the one
  strategic §7 predicts keeps reporting the old state, it does not stop reporting.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0. This is also what proves the Hilt graph: it runs
      `hiltJavaCompileStandardDebug`, which `fk`/`fkn` never reach.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `Grep` for `Log\.d\(` returns zero hits in every file touched.
- [x] Dev log entry added for the phase - one per step through `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `catalog-sync` PASS on each closure.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Phase-boundary audit

Layers 1-3 of `docs/CODE_AUDIT_PROTOCOL.md`; Layer 4 does not apply, the phase touches no Room surface.

- **AUDIT-FIX (P1, main-safety): composition was running on the collector's thread.**
  `NetworkMonitorRepositoryImpl.compose` calls `telephony.sample()` and `bluetooth.sample()`, and both reach
  the platform through binder - subscription enumeration and adapter/bonded-device lookups are IPC, not
  field reads. With no dispatcher of its own the flow composed on whatever context the section collected on,
  which for a screen is the main thread, once per second for as long as the section is visible - against the
  redraw budget strategic §3.2 sets. Fixed with `flowOn(Dispatchers.IO)`, which also moves the upstream
  callback registration off main. `.\a.ps1 fk` exit 0 after the fix.
- Layer 3, listener ownership: the only registration in this phase is the connectivity `callbackFlow`, whose
  `awaitClose` cancels the re-sample tick and unregisters the borrowed callback. The two sampled sources
  register nothing at all, which is what keeps the count symmetric - `listener-symmetry` reports new
  imbalance 0 on every closure in this phase.
- Layer 2, shared state: the one-per-interval cap keeps its timestamp inside the `flow { }` builder rather
  than in a field of the `@Singleton`. That is load-bearing rather than incidental - as a field, two
  collectors would suppress each other's emissions.
- Layer 1: no finding. Each data source has one reason to change, the repository is the only composer, and
  the Hilt binding is the only place the implementation is named.

---

## Handoff Notes to Next Phase

`NetworkMonitorRepository.observeSnapshot()` is the only read path for device state. `RadioControl` is the only write path for Wi-Fi and Bluetooth and is deliberately UI-free so S1441 can consume it from the launcher.

---

## Rollback Plan

Revert phase commit(s) - new classes only, nothing existing depends on them yet.
