# Phase 01 — Wire NetworkFileManager into CastMediaManager

**Strategic spec:** [`../S0137_feature-cast-network-cloud-streaming.md`](../S0137_feature-cast-network-cloud-streaming.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Pass the existing `NetworkFileManager` instance into `CastMediaManager` constructor and wire it through `PlayerManagerInitializer`, without changing any cast behaviour yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(none)*
- [ ] Strategic §6 research items blocking this phase are Resolved. *(none for Phase 01)*
- [ ] Working tree is clean or on a feature branch.
- [ ] `PlayerManagerInitializer.initNetworkAndTranslation()` runs **before** `initAudioAndMediaServices()` (verified — `networkFileManager` exists when `castMediaManager` is constructed).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 830 |

> No file projected >500 lines after change. No backup needed.

---

## Steps

### Step 01.1 — Add `networkFileManager` constructor parameter to `CastMediaManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a new constructor parameter `private val networkFileManager: NetworkFileManager` to `CastMediaManager`. Place it as the third parameter (after `lifecycleScope`, before `onCastStateChanged`) so call-site keyword arguments make positional reading natural. Add the import `com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager` (it lives in the same package — no import needed; verify). Insert `Timber.d("S0137: CastMediaManager constructor — networkFileManager wired")` once at the end of the primary constructor's `init {}` block (create the block if absent).

**Verification:**

- `Grep` — `private val networkFileManager: NetworkFileManager` matches exactly once in `CastMediaManager.kt`.
- `Grep` — `Timber.d\("S0137: CastMediaManager constructor` matches exactly once.
- `Grep` — class signature still `class CastMediaManager\(` exists.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. Files: ui/player/helpers/CastMediaManager.kt (+5 LOC). Dev log recorded.

---

### Step 01.2 — Pass `networkFileManager` at construction site in `PlayerManagerInitializer`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `initAudioAndMediaServices()`, update the `CastMediaManager(...)` constructor invocation (around line 691) to pass `networkFileManager = activity.networkFileManager` as the third keyword argument, in front of the existing `onCastStateChanged = { .. }`. Do not reorder existing arguments — keep them keyword-style for readability.

**Verification:**

- `Grep` — `networkFileManager = activity.networkFileManager` appears within the `CastMediaManager(` block in `PlayerManagerInitializer.kt`.
- `Grep` — exactly one occurrence of `CastMediaManager\(` in `PlayerManagerInitializer.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 2/2 PASS. Files: ui/player/PlayerManagerInitializer.kt (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both modified files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`CastMediaManager` now holds a reference to `networkFileManager`. The reference is unused so far — Phase 02 will replace the `openRemoteInputStream` stub with `networkFileManager.prepareFileForRead(file)`.

---

## Rollback Plan

Revert the two-file phase commit. Constructor parameter addition is purely additive at call site (single new keyword argument); no schema, no data migration, no user-visible surface changed.
