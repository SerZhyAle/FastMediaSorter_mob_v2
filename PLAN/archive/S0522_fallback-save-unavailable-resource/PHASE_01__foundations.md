# Phase 01 - Foundations

**Strategic spec:** [`../S0522_fallback-save-unavailable-resource.md`](../S0522_fallback-save-unavailable-resource.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 4 / 4
**Started:** 2026-06-18
**Completed:** 2026-06-18

---

## Objective

Introduce the shared fallback decision primitives: synchronous resource reachability queries, a shared fallback-reason enum, a pure media-type→local-fallback policy, and unit tests. No save flow is rewired yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/network/NetworkStateMonitor.kt` | Modified | ≤ 290 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/SaveFallbackReason.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/SaveFallbackPolicy.kt` | New | ≤ 130 |
| `app_v2/src/test/java/com/sza/fastmediasorter/util/SaveFallbackPolicyTest.kt` | New | ≤ 180 |

---

## Steps

### Step 01.1 - Add synchronous reachability queries to NetworkStateMonitor

**Files:** `core/network/NetworkStateMonitor.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three synchronous public methods reading the current `connectivityManager` state without registering callbacks: `isInternetAvailable(): Boolean` (active network has `NET_CAPABILITY_INTERNET`), `isLocalNetworkAvailable(): Boolean` (active network has `TRANSPORT_WIFI` or `TRANSPORT_ETHERNET`), and `canReach(type: ResourceType): Boolean` mapping `LOCAL` → always `true`, `SMB`/`SFTP`/`FTP` → `isLocalNetworkAvailable()`, `CLOUD` → `isInternetAvailable()`. Wrap the `getNetworkCapabilities` lookup so a null active network returns `false` for the network checks. Do not change the existing callback machinery.

**Verification:**

- `Grep` - `fun canReach(` matches once in `NetworkStateMonitor.kt`.
- `Grep` - `fun isLocalNetworkAvailable(` and `fun isInternetAvailable(` each match once.
- `Grep` - `TRANSPORT_WIFI` and `TRANSPORT_ETHERNET` present.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-18 - Verification 5/5 PASS. Added canReach/isLocalNetworkAvailable/isInternetAvailable + helpers to NetworkStateMonitor.kt.

---

### Step 01.2 - Add shared SaveFallbackReason enum

**Files:** `domain/model/SaveFallbackReason.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `enum class SaveFallbackReason { NoResourceConfigured, ResourceUnavailable, ResourceWriteFailed }` in package `com.sza.fastmediasorter.domain.model`. This is the single fallback-reason vocabulary reused by every save flow; `ResourceUnavailable` and `ResourceWriteFailed` are the user-visible (notify) cases, `NoResourceConfigured` is silent.

**Verification:**

- `Glob` - `domain/model/SaveFallbackReason.kt` exists.
- `Grep` - `enum class SaveFallbackReason` matches once; the three constants present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-18 - Verification 2/2 PASS. Created domain/model/SaveFallbackReason.kt.

---

### Step 01.3 - Add pure SaveFallbackPolicy with media-type→category mapping

**Files:** `util/SaveFallbackPolicy.kt` (New)
**Depends on:** Step 01.2

**Prompt for developer:**

> Create a pure `object SaveFallbackPolicy` (no Android `Context`, no DI - mirrors `CaptureDestinationPolicy`). Expose a sealed `Decision`: `UseResource(resource: MediaResource)` and `UseLocalFallback(collection: LocalDestinationCategory.PublicCollection.Kind, relativePath: String, reason: SaveFallbackReason)`. Add `fun decide(resource: MediaResource?, mediaType: MediaType, isResourceReachable: Boolean): Decision`: when `resource == null` → `UseLocalFallback(..., NoResourceConfigured)`; when `resource.type.isNetworkResource && !isResourceReachable` → `UseLocalFallback(..., ResourceUnavailable)`; otherwise `UseResource(resource)`. Add `fun fallbackCollection(mediaType: MediaType): Pair<Kind, String>` mapping `IMAGE`/`GIF` → `IMAGES` + `Environment.DIRECTORY_PICTURES + "/"`, `VIDEO` → `VIDEO` + `DIRECTORY_MOVIES + "/"`, `AUDIO` → `AUDIO` + `DIRECTORY_MUSIC + "/"`, everything else → `DOWNLOADS` + `DIRECTORY_DOWNLOADS + "/"`. Keep the public folder strings consistent with `ScreenshotDestinationPolicy` constants where they overlap.

**Verification:**

- `Glob` - `util/SaveFallbackPolicy.kt` exists.
- `Grep` - `object SaveFallbackPolicy` matches once.
- `Grep` - `fun decide(` and `fun fallbackCollection(` each match once.
- `Grep` - `NoResourceConfigured`, `ResourceUnavailable` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-18 - Verification 4/4 PASS. Created util/SaveFallbackPolicy.kt (decide + fallbackCollection).

---

### Step 01.4 - Unit-test SaveFallbackPolicy

**Files:** `src/test/java/com/sza/fastmediasorter/util/SaveFallbackPolicyTest.kt` (New)
**Depends on:** Step 01.3

**Prompt for developer:**

> Add JUnit tests for `SaveFallbackPolicy.decide` and `fallbackCollection`: null resource → `UseLocalFallback`/`NoResourceConfigured`; LOCAL resource → `UseResource` regardless of reachability; network resource + reachable → `UseResource`; network resource + unreachable → `UseLocalFallback`/`ResourceUnavailable`; each `MediaType` maps to the expected `Kind`. Construct `MediaResource` test instances directly; no Robolectric needed (pure logic).

**Verification:**

- `Glob` - `SaveFallbackPolicyTest.kt` exists.
- Build: `.\a.ps1 fu` (or `gradlew testStandardDebugUnitTest --tests *SaveFallbackPolicyTest`) - the new test class passes (check its per-class XML report; ignore unrelated pre-existing failures).

**Status:** `[x] done`

**Step Log:**

- 2026-06-18 - Verification PASS. SaveFallbackPolicyTest: tests=5 failures=0 errors=0 (per-class XML). Test run compiled main+test source sets.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API added).

---

## Handoff Notes to Next Phase

`NetworkStateMonitor.canReach(ResourceType)`, `SaveFallbackReason`, and `SaveFallbackPolicy.decide/fallbackCollection` exist and are tested. All later phases consume these; none reaches into `ConnectivityManager` directly.

---

## Rollback Plan

Revert phase commit(s) - new files are unreferenced until later phases wire them; no data migration or user-facing surface changed.
