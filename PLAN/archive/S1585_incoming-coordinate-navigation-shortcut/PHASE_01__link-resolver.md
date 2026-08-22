# Phase 01 - Maps short-link resolver

**Strategic spec:** [`../S1585_incoming-coordinate-navigation-shortcut.md`](../S1585_incoming-coordinate-navigation-shortcut.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Introduce a replaceable seam that turns a Google Maps short link into coordinates, with its HTTP
implementation and Hilt binding; no intake or tap behaviour changes yet.

---

## Prerequisites

- [ ] Strategic §6 item 1 is Resolved (owner decision 2026-08-13).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/map/MapsShortLinkResolver.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/map/HttpMapsShortLinkResolver.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/MapModule.kt` | Modified | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/map/HttpMapsShortLinkResolverTest.kt` | New | ≤ 140 |

---

## Steps

### Step 01.1 - Declare the resolver seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/map/MapsShortLinkResolver.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create interface `MapsShortLinkResolver` with a single suspend function taking the shared link as
> `String` and returning a nullable coordinate pair. Return `null` for every failure - no exception
> escapes the seam. Define the returned type as a small immutable data class `MapPoint(latitude:
> Double, longitude: Double)` in the same file. Document that `null` means "point unknown", which the
> caller degrades on rather than treating as an error.

**Why:**

Strategic §5.3 requires link resolution to stay replaceable, because today it is a redirect unwrap
and tomorrow the point may come from another source; a domain-level seam is what allows the
implementation to change without touching intake or execution.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/map/MapsShortLinkResolver.kt` exists.
- `Grep` - `interface MapsShortLinkResolver` matches exactly once.
- `Grep` - `suspend fun` present in that file.
- `Grep` - `data class MapPoint` matches exactly once.

**Status:** `[ ]` not done

---

### Step 01.2 - Implement redirect-unwrapping resolver

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/map/HttpMapsShortLinkResolver.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Implement `HttpMapsShortLinkResolver` as an `@Inject constructor` class satisfying
> `MapsShortLinkResolver`. Issue a `HEAD` request with redirects disabled via `HttpURLConnection`,
> read the `Location` response header, and extract the first `<lat>,<lon>` pair from the resulting
> URL path. Apply `applyTimeouts` from `core/network/HttpTimeouts.kt` and additionally cap the whole
> operation at 5000 ms with `withTimeoutOrNull`. Run on `Dispatchers.IO`. Catch `IOException` and
> return `null`; let `CancellationException` propagate. Accept only hosts belonging to Google Maps
> short links, and return `null` for anything else without performing a request.

**Why:**

Strategic §3.2 caps the wait at 5 seconds and requires the work off the main thread, and §7 names a
changed link format as a Medium risk whose mitigation is that failure degrades predictably instead
of throwing; a host allowlist keeps an arbitrary shared URL from turning the intake into a general
HTTP fetcher.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/map/HttpMapsShortLinkResolver.kt` exists.
- `Grep` - `class HttpMapsShortLinkResolver` matches exactly once.
- `Grep` - `withTimeoutOrNull` present.
- `Grep` - `applyTimeouts` present.
- `Grep` - `Dispatchers.IO` present.
- `Grep` - `Log\.d\(` returns zero hits in that file.

**Status:** `[ ]` not done

---

### Step 01.3 - Bind the resolver in Hilt

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/MapModule.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a `@Binds @Singleton` method to the existing `abstract class MapModule` binding
> `HttpMapsShortLinkResolver` to `MapsShortLinkResolver`, alongside its four existing bindings. Keep
> `MapTileClientModule` in the same file untouched.

**Why:**

Strategic §5.1 makes link resolution its own role with its own scenario, which requires an injectable
binding so the intake path depends on the seam rather than constructing the HTTP implementation
directly.

**Verification:**

- `Grep` - `MapsShortLinkResolver` present in `MapModule.kt`.
- `Grep` - `HttpMapsShortLinkResolver` present in `MapModule.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[ ]` not done

---

### Step 01.4 - Cover extraction with unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/map/HttpMapsShortLinkResolverTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Extract the coordinate-from-URL parsing into an internal pure function on the resolver, then test it
> without touching the network. Cover: a `maps/place/48.444152,22.717282/data=..` URL yielding that
> point; a negative-coordinate URL; a redirect URL carrying no coordinates yielding `null`; a
> non-Maps host yielding `null`; a malformed pair yielding `null`.

**Why:**

Strategic §11 criterion 5 requires the share-parsing surface to be covered by tests runnable without
a device, and the coordinate extraction is the part of this phase whose correctness cannot otherwise
be checked before a device test.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/data/map/HttpMapsShortLinkResolverTest.kt` exists.
- `Grep` - `48.444152` present in the test file.
- `Grep` - `@Test` matches at least 5 times.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`MapsShortLinkResolver` is injectable and returns `null` for every failure, including timeout and
non-Maps host. Phase 02 consumes it and owns the decision of what a `null` means for the user.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed; the seam is unused until
Phase 02 wires it in.
