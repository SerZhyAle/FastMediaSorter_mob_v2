# Phase 04 - Default seed enrichment

**Strategic spec:** [`../S0663_app-launch-panel-internal-routes.md`](../S0663_app-launch-panel-internal-routes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Seed the first-run panel with FMS, then the available curated feature routes (ours first), then a base OS target (Settings), leaving empty slots for the user's apps.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Strategic §6.2 resolution applied (skip unavailable feature, shift up, no holes).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/SeedDefaultAppLaunchPanelUseCase.kt` | Modified | ≤ 110 |

---

## Steps

### Step 04.1 - Seed our feature routes first, then Settings

**Files:** `domain/usecase/panel/SeedDefaultAppLaunchPanelUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Keep FMS at slot 0. Then fill following slots with the available curated feature routes in order - calculator, mini-game, photo-OCR-translate, streams, resources - persisting each as an `INTERNAL_ROUTE` tile via `AppLaunchPanelRouteTarget.encode()`. Gate each route through `ResolvePanelRouteAvailabilityUseCase` so unavailable-in-build features are skipped and the next available one shifts up (no holes, §6.2). After the feature routes, add an `os:settings` tile. Stop before exhausting all slots so empty slots remain for the user. Keep the existing "no-op when any tile exists" guard.

**Verification:**

- `Grep` - `AppLaunchPanelTileType.INTERNAL_ROUTE` referenced.
- `Grep` - `ResolvePanelRouteAvailabilityUseCase` (or the catalog availability path) referenced.
- `Grep` - `os:settings` (or the OS-settings target key) referenced.
- `Grep` - the existing `if (repository.count() > 0) return` guard still present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - Verification 4/4 PASS. Files: SeedDefaultAppLaunchPanelUseCase.kt (rewritten, +85 LOC). FMS slot 0; curated feature order calculator/game/ocr/streams gated by `availableInBuild` (skip+shift, no holes); then resource; then `os:settings`. `SEED_SLOT_LIMIT = SLOT_COUNT-4` keeps empty slots; count guard kept.

---

### Step 04.2 - For the resource seed tile, pick a safe default target

**Files:** `domain/usecase/panel/SeedDefaultAppLaunchPanelUseCase.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> The "resources" seed tile must point at a real resource. Resolve a default resource (e.g. the predefined "All Files" virtual resource) and seed `resource:<id>`; if none exists yet at seed time, skip the resource tile rather than seeding a dangling id. Resolution runs on IO (the use case already runs in `withContext(Dispatchers.IO)`).

**Verification:**

- `Grep` - `resource:` encoded target referenced in the seed.
- Build passes via `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-24 - PASS with 1 documented deviation. Build passes (`.\a.ps1 fk`). DEVIATION: the seed encodes the resource tile via the typed `AppLaunchPanelRouteTarget.Resource(id).encode()` (the single serializer, ADR-2), not a hardcoded `"resource:"` literal - so the literal grep is 0 by design (no magic string). `defaultResourceId()` prefers a virtual all-media resource, else the first configured resource, else skips (no dangling id).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [~] Dev log entry added for the modified file - batched at Phase 05.

---

## Handoff Notes to Next Phase

First-run panel ships pre-populated with our features (ours first) + Settings, with empty slots remaining. Phase 05 finalizes docs, catalog and FEATURES.

---

## Rollback Plan

Revert the phase commit(s) - the seed returns to FMS + OS-app candidates. Existing installs are unaffected (seed is first-run only).
