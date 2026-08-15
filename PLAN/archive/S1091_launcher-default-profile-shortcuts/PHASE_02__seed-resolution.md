# Phase 02 - Seed Resolution

**Strategic spec:** [`../S1091_launcher-default-profile-shortcuts.md`](../S1091_launcher-default-profile-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-21
**Completed:** 2026-07-21

**Step Log:**

- 2026-07-21 - 02.1-02.3 grep-verified (provisionDefaultResources injected + called after seeded-check; six VIRTUAL_PATH ids resolved via idOf; routeAvailability.all() mapped to availableInBuild; itemsFor new call). Orphaned InternalRouteCatalog import + old locals removed. Ctor param renamed streamsAvailability -> routeAvailability for accuracy (deviation from plan wording, clarity). AUDIT-P2: theoretical double-provision race vs MainViewModel - low likelihood, no race in the target HOME-first-boot scenario. compileStandardDebugKotlin SUCCESSFUL.

---

## Objective

Resolve all six virtual-resource ids and the route-availability map in `SeedLauncherDesktopUseCase`, provision the default resources before seeding so the HOME-first-boot path has them, and feed the widened `itemsFor`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (new `itemsFor` signature available).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt` | Modified | ≤ 160 |

> Constructor gains two `@Inject` params (both are themselves `@Inject constructor` use cases) - no new Hilt `@Module`.

---

## Steps

### Step 02.1 - Provision default resources before seeding

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `private val provisionDefaultResources: ProvisionDefaultResourcesUseCase` into the constructor. Inside `invoke`, immediately after the `if (state.seededPortrait && state.seededLandscape) return@runCatching` early-return and before reading resources, call `provisionDefaultResources()` (ignore its Boolean result). This guarantees the six virtual resources exist on a device that becomes Home without ever opening MainActivity (its provisioning is idempotent per-slot, so a second call is a no-op). Keep the whole body inside the existing `runCatching` so a provisioning failure still degrades to an empty desktop, never a crash loop.

**Verification:**

- `Grep` - `provisionDefaultResources: ProvisionDefaultResourcesUseCase` in the constructor.
- `Grep` - `provisionDefaultResources()` called after the seeded-state early return, inside `runCatching`.

**Status:** `[x]` done

---

### Step 02.2 - Resolve the six virtual-resource ids and the route-availability map

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> After re-reading `allResources`, resolve one id per virtual path via `allResources.firstOrNull { it.path == LocalMediaScanner.<PATH> }?.id` for `VIRTUAL_PATH_RECENT`, `VIRTUAL_PATH_ALL_AUDIO`, `VIRTUAL_PATH_ALL_IMAGES`, `VIRTUAL_PATH_ALL_VIDEO`, `VIRTUAL_PATH_ALL_DOCS`, `VIRTUAL_PATH_CAMERA_PHOTOS`. Build a `LauncherStarterSets.StarterResources(...)` with those ids plus the existing `lastResourceId`. Replace the single `streamsAvailability(KEY_STREAMS)` call with `streamsAvailability.all()` (returns `Map<String, Availability>`) and map it to `route.key to availability.availableInBuild` (`routeAvailableInBuild`).

**Verification:**

- `Grep` - all six `VIRTUAL_PATH_` constants referenced in the use case.
- `Grep` - `StarterResources(` constructed in the use case.
- `Grep` - `streamsAvailability.all()` called; `availableInBuild` mapped.

**Status:** `[x]` done

---

### Step 02.3 - Feed the widened itemsFor

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/SeedLauncherDesktopUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Change the `LauncherStarterSets.itemsFor(...)` call to pass `profile`, the new `StarterResources`, and the `routeAvailableInBuild` map. Remove the now-unused single `allAudioResourceId`/`streamsAvailable` locals if they are no longer referenced (dead-weight hygiene, Rule 21). The rest of `seedOrientation`/placement is unchanged.

**Verification:**

- `Grep` - `LauncherStarterSets.itemsFor(` call passes `StarterResources`-typed arg and the route map.
- `Grep` - no orphaned `val streamsAvailable =` / `val allAudioResourceId =` locals remain.
- `.\a.ps1 fk` - compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (SeedLauncherDesktopUseCase ctor changed).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (Layer 2 coroutine/IO on the seed path; confirm provisioning stays inside `runCatching` and off the main thread).

---

## Handoff Notes to Next Phase

The seed now fills ~12-15 cells on standard/noLegal. Phase 03 relabels the Settings cell independently.

---

## Rollback Plan

Revert the phase commit(s) - `seedIfEmpty` is one-shot; a device already seeded with the old 4-item set is unaffected (no re-seed), matching strategic §11.5.
