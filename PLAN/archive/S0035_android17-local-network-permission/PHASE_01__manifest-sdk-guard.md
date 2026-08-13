# Phase 01 — Manifest and SDK Guard

**Strategic spec:** [`../S0035_android17-local-network-permission.md`](../S0035_android17-local-network-permission.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-05-04
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Declare `ACCESS_LOCAL_NETWORK` in the manifest, centralise the API 37 guard in one helper surface, and keep S0035 off the toolchain-upgrade path. Research confirms `ACCESS_LOCAL_NETWORK` is a runtime dangerous permission, so the runtime request flow will be implemented.

---

## Prerequisites

- [ ] Working tree is clean or on a branch dedicated to S0035.
- [ ] Android 17 / API 37 emulator or final SDK notes are available for the runtime-permission decision.
- [ ] No `compileSdk` / `targetSdk` uplift is bundled into S0035.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/build.gradle.kts` | Audit only | n/a |
| `app_v2/src/main/AndroidManifest.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt` | Modified | 323 current LOC |

---

## Steps

### Step 01.1 — Freeze the API 37 guard strategy

**Files:** `app_v2/build.gradle.kts`, `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Confirm that S0035 stays on `compileSdk 35` / `targetSdk 35`. In `PermissionHelper.kt`, introduce `LOCAL_NETWORK_API = 37` and `LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"` as the only API 37 identifiers used by this ticket. Do not reference `Manifest.permission.ACCESS_LOCAL_NETWORK` or new SDK enums on this branch. Add a WHY comment that this keeps the branch buildable before the separate SDK uplift ticket lands.

**Verification:**

- `Grep` — `compileSdk = 35` matches once in `app_v2/build.gradle.kts`.
- `Grep` — `targetSdk = 35` matches once in `app_v2/build.gradle.kts`.
- `Grep` — `LOCAL_NETWORK_API = 37` matches once in `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`.
- `Grep` — `LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"` matches once in `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt (+9 LOC). Dev log recorded.

---

### Step 01.2 — Declare the manifest permission

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" android:minSdkVersion="37" />` beside the existing network permissions in `AndroidManifest.xml`. Do not touch any other manifest entries in this step.

**Verification:**

- `Grep` — `android.permission.ACCESS_LOCAL_NETWORK` matches exactly once in `app_v2/src/main/AndroidManifest.xml`.
- `Grep` — `android:minSdkVersion="37"` matches on the same `<uses-permission` line in `app_v2/src/main/AndroidManifest.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: app_v2/src/main/AndroidManifest.xml (+1 LOC). Dev log recorded.

---

### Step 01.3 — Extend the core helper surface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`
**Depends on:** Step 01.2 `[x] done`

**Prompt for developer:**

> Extend `PermissionHelper` with the local-network surface only once:
>
> - `REQUEST_CODE_LOCAL_NETWORK`
> - `hasLocalNetworkPermission(context)`
> - `shouldShowLocalNetworkRationale(activity)`
> - `requestLocalNetworkPermission(activity)`
> - `routeToLocalNetworkSettings(activity)`
> - `getLocalNetworkPermissionMessage(context)`
> - `isLocalNetworkRuntimePermissionExpected()`
>
> Reuse `SettingsIntentLauncher` for the settings fallback. Runtime request must execute only when `isLocalNetworkRuntimePermissionExpected()` is true.

**Verification:**

- `Grep` — `REQUEST_CODE_LOCAL_NETWORK` matches once in `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`.
- `Grep` — `fun hasLocalNetworkPermission` matches once in that file.
- `Grep` — `fun shouldShowLocalNetworkRationale` matches once in that file.
- `Grep` — `fun requestLocalNetworkPermission` matches once in that file.
- `Grep` — `fun routeToLocalNetworkSettings` matches once in that file.
- `Grep` — `fun getLocalNetworkPermissionMessage` matches once in that file.
- `Grep` — `fun isLocalNetworkRuntimePermissionExpected` matches once in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 7/7 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt (+46 LOC). Dev log recorded.

---

### Step 01.4 — Run the compile gate and decide continue vs stop

**Files:** none modified — verification only
**Depends on:** Step 01.3 `[x] done`

**Prompt for developer:**

> Run:
>
> ```powershell
> ./gradlew.bat :app_v2:compileStandardDebugKotlin
> ```
>
> Continue only if the branch compiles with the string-literal permission guard. If the compiler still requires a broader SDK uplift, stop the spec after Phase 01 and record the blocker in `INDEX.md` before changing any UI/request flow.

**Verification:**

- `Command` — `./gradlew.bat :app_v2:compileStandardDebugKotlin` exits with code `0`, or `INDEX.md` gains a blocker bullet containing `compileSdk 35 blocker` or `non-dangerous permission stop`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — BUILD SUCCESSFUL. compileSdk=35 path confirmed, continue-path selected. Stub string `local_network_permission_rationale_message` added to all 3 locales to unblock compilation (Phase 03 will extend full key set).

---

## Stop Condition

Stop S0035 after Phase 01 if the following is true:

- `compileSdk 35` cannot carry the helper/request flow without a broader SDK / AndroidX upgrade.

If stopped: mark INDEX row `⛔ Blocked`, do not start Phase 02, and respin the remaining work as a manifest-only / passive-unavailable follow-up instead of guessing the runtime UX.

---

## Phase Done Criteria

- [ ] Every Step 01.* above is `[x] done`.
- [ ] `ACCESS_LOCAL_NETWORK` is declared exactly once in the manifest.
- [ ] No `compileSdk` / `targetSdk` uplift was added under S0035.
- [ ] The branch compiles, or the stop decision is explicitly recorded in `INDEX.md`.

---

## Handoff Notes to Next Phase

Phase 02 assumes a continue-path from Phase 01 and builds a typed permission-denied contract. Do not start it while the runtime-vs-normal permission question is still unresolved.

---

## Rollback Plan

Revert the manifest/helper changes and delete the blocker entry if the ticket is re-scoped before Phase 02.