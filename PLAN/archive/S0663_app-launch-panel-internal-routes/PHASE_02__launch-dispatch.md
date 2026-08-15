# Phase 02 - Launch dispatch & tile rendering

**Strategic spec:** [`../S0663_app-launch-panel-internal-routes.md`](../S0663_app-launch-panel-internal-routes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Make `INTERNAL_ROUTE` tiles launch (feature / resource / OS) and render (label / icon / availability), replacing the current "v1 ships no internal routes" no-op.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Strategic §6.1 (disabled-feature tile behaviour) resolved - it fixes the launch branch for a disabled route.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt` | Modified | ≤ 140 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt` | Modified | ≤ 180 |

---

## Steps

### Step 02.1 - Dispatch INTERNAL_ROUTE launches

**Files:** `domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the `INTERNAL_ROUTE -> null` branch. Decode the tile `targetId` via `AppLaunchPanelRouteTarget`. For `Feature`, look up the route, check availability via `ResolvePanelRouteAvailabilityUseCase`: if enabled, start its intent; if available-but-disabled, follow the §6.1 decision (default: start the relevant Settings toggle intent); if unavailable, return false. For `Resource`, start the resource intent. For `OsShortcut`, start the OS intent if it still resolves, else return false. Keep `RESERVED` returning false. Preserve the existing `runCatching` + `Timber.w` failure handling.

**Verification:**

- `Grep` - `AppLaunchPanelRouteTarget` referenced.
- `Grep` - `INTERNAL_ROUTE -> null` no longer present.
- `Grep -n "Log\.d\("` on this file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt (rewritten, +57 LOC; `launch` now suspend - availability read is suspend; ViewModel adapted to fire in viewModelScope). Feature/disabled(§6.1)/Resource/OsShortcut dispatch wired; RESERVED still false.

---

### Step 02.2 - Render INTERNAL_ROUTE tiles

**Files:** `domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the `INTERNAL_ROUTE, RESERVED -> null` branch so `INTERNAL_ROUTE` resolves a real `AppLaunchPanelTileUi`: decode the target, supply label (from catalog string-res for features/OS, from the resource name for resources) and icon (catalog drawable / resource-type icon). Honour `labelOverride`. Soft-degrade to an empty slot when the target no longer exists (deleted resource, unresolvable OS intent, feature absent from the build). Keep `RESERVED -> null` (empty-slot sentinel unchanged).

**Verification:**

- `Grep` - `INTERNAL_ROUTE` branch returns a non-null `AppLaunchPanelTileUi` (no longer folded into the `RESERVED -> null` line).
- `Grep` - `AppLaunchPanelRouteTarget` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 2/2 PASS. Files: domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt (rewritten, +130 LOC; injects ResourceRepository + ResolvePanelRouteAvailabilityUseCase; feature/OS/resource render with soft-degrade). Compiles (`.\a.ps1 fk`).

---

### Step 02.3 - Add the debug verification tag at the launch entry

**Files:** `domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> At the start of the `INTERNAL_ROUTE` launch branch add `Timber.d("S0663: launch internal route %s", tile.targetId)`. This tag is required while the ticket sits in `BlockNeedUserTest` and is removed when it leaves that status. Do not add the ticket id to any `Timber.i/w/e`.

**Verification:**

- `Grep` - `Timber.d("S0663:` matches exactly once across `.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 1/1 PASS. Inserted `Timber.d("S0663: launch internal route %s", tile.targetId)` at the start of `launchInternalRoute` as the final code edit before the closing build (deferred from Phase 02 per the ticket-log gate; status now moving to BlockNeedUserTest). `Grep "Timber.d("S0663:"` matches exactly once across `.kt`. `.\a.ps1 fk` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done` (02.3 debug tag inserted at final-phase boundary).
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [~] Dev log entry added for both modified files - batched at Phase 05.

---

## Handoff Notes to Next Phase

`INTERNAL_ROUTE` tiles now launch and render. The editor (Phase 03) can persist them via the namespace `encode()` and trust resolve/launch to handle availability.

---

## Rollback Plan

Revert the phase commit(s) - the two use cases return to their `null` branches; persisted `INTERNAL_ROUTE` tiles (none in production yet) degrade to empty slots. No migration involved.
