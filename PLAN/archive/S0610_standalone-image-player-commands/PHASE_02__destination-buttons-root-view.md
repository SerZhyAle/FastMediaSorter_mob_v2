# Phase 02 - Generalize destination buttons to a binding-agnostic root-View surface

**Strategic spec:** [`../S0610_standalone-image-player-commands.md`](../S0610_standalone-image-player-commands.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Refactor `DestinationButtonsManager` to depend on a layout root `View` instead of the concrete `ActivityPlayerUnifiedBinding`,
so the same manager can drive the copy/move grids in any layout that includes the shared bottom-panels content. No behavior
change for the in-app player.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1000 |

> `PlayerBindingSafeViews` already has a `constructor(root: View)` that resolves `copyToPanel` / `moveToPanel` / `copyToButtonsGrid` / `moveToButtonsGrid` / `bottomPanelsContainer` / panel headers via `findViewById`. No change needed there.

---

## Steps

### Step 02.1 - Replace the binding dependency with a root `View`

**Files:** `app_v2/.../ui/player/helpers/DestinationButtonsManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the `DestinationButtonsManager` constructor parameter from `binding: ActivityPlayerUnifiedBinding` to `root: View`. Build `safeViews` via `PlayerBindingSafeViews(root)`. Replace every `binding.root.context` with `root.context` and every `binding.root.requestLayout()` with `root.requestLayout()`. Do not change the population algorithm, distribution math, collapse-state handling, or callback contract.

**Verification:**

- `Grep` - `ActivityPlayerUnifiedBinding` returns zero hits in `DestinationButtonsManager.kt`.
- `Grep` - `private val root: View` present in the constructor.
- `Grep` - `PlayerBindingSafeViews(root)` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 4/4 PASS. Files: DestinationButtonsManager.kt (binding -> root: View; all binding.root -> root; import dropped).

---

### Step 02.2 - Update the in-app player construction site

**Files:** `app_v2/.../ui/player/PlayerActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Find where `DestinationButtonsManager(...)` is constructed and pass `binding.root` for the new `root` parameter (instead of `binding`). No other call-site changes; the public methods (`populateDestinationButtons()`, `refreshSlotBadges()`, panel toggles) keep their signatures.

**Verification:**

- `Grep` - `DestinationButtonsManager(` in `PlayerActivity.kt` passes `binding.root` (no longer `binding`).
- Build compiles - run `/build`.
- In-app player copy/move panels still populate (manual smoke: open a resource file in the in-app player, destinations render).

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification PASS (call-site passes binding.root; compile BUILD SUCCESSFUL). In-app player copy/move smoke deferred to the spec-level device-test gate (BlockNeedUserTest).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (constructor signature changed) via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`DestinationButtonsManager` now binds to any layout root carrying the bottom-panels ids. Phase 04 constructs it for the
standalone image host with `binding.root`.

---

## Rollback Plan

Revert the phase commit - mechanical signature change, no persistent surface affected.
