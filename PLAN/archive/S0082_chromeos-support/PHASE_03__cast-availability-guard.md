# Phase 03 — Cast Availability Guard

**Strategic spec:** [`../S0082_chromeos-support.md`](../S0082_chromeos-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Wire `CastMediaManager.isCastAvailable` into the player command panel so the Cast button is hidden when Cast cannot initialise (Play Services absent, port binding failure, or ARC++ container isolation). `FastMediaSorterApp` already wraps `CastContext.getSharedInstance()` in try/catch; this phase closes the UI side of the gap.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] Blocker §6.2 resolved or acknowledged (Cast port reachability on Chrome OS). Phase proceeds regardless of result — graceful degradation is implemented either way.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt` | Modified | ≤ 390 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1010 |

> `CommandPanelController.kt` is currently ~984 lines — **backup required before edit**. See Step 3.1.

---

## Steps

### Step 3.1 — Backup CommandPanelController before edit

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` (read + copy)
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `CommandPanelController.kt` in `temp/` before any edits. Example: `temp/CommandPanelController_20260504_backup.kt`. Verify the backup file exists with a Glob check before proceeding to Step 3.2.

**Verification:**

- `Glob` — `temp/CommandPanelController_*_backup.kt` returns at least one match.

**Status:** `[ ]` not done

---

### Step 3.2 — Expose isCastAvailable as observable state in CastMediaManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CastMediaManager.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> `CastMediaManager` already has `val isCastAvailable: Boolean get() = castContext != null`. Add a companion `StateFlow<Boolean>` named `castAvailableState` backed by a `MutableStateFlow(false)` private field. Update it to `true` after successful initialization and leave it `false` on failure. Emit the value after both the success branch and the catch branch in `initialize()`. This allows `CommandPanelController` to observe availability reactively instead of polling a boolean getter at bind time.
>
> Keep the existing `isCastAvailable` getter — do not remove it (other callers may use it).

**Verification:**

- `Grep` — `castAvailableState` present in `CastMediaManager.kt`.
- `Grep` — `MutableStateFlow` present in `CastMediaManager.kt`.
- `Grep` — `castAvailableState.value = true` present in `CastMediaManager.kt`.
- `Grep` — `castAvailableState.value = false` present in `CastMediaManager.kt`.
- `Grep` for `Log.d(` in `CastMediaManager.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 3.3 — Hide Cast button when Cast is unavailable in CommandPanelController

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt`
**Depends on:** Step 3.2

**Prompt for developer:**

> In `CommandPanelController`, find the line that sets `btnCastCmd.isVisible` (currently: `BuildConfig.SUPPORT_CAST && (isImage || isVideo) && isWifiConnected(...)`). Add an additional condition: `&& castMediaManager.isCastAvailable`. The result must be:
>
> ```kotlin
> safeViews.btnCastCmd.isVisible =
>     BuildConfig.SUPPORT_CAST &&
>     castMediaManager.isCastAvailable &&
>     (isImage || isVideo) &&
>     isWifiConnected(binding.root.context)
> ```
>
> Also subscribe to `castMediaManager.castAvailableState` in the controller's setup/lifecycle scope; on each emission call the existing visibility-update method so the button hides dynamically if Cast becomes unavailable mid-session (e.g. Play Services disconnects).

**Verification:**

- `Grep` — `castMediaManager.isCastAvailable` present in `CommandPanelController.kt`.
- `Grep` — `castAvailableState` present in `CommandPanelController.kt` (subscription).
- `Grep` for `Log.d(` in `CommandPanelController.kt` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

When `CastContext.getSharedInstance()` throws (Play Services absent, Chrome OS port isolation), `castAvailableState` emits `false`, and the Cast button is hidden. No crash, no phantom UI. The backup in `temp/` can be removed after the PR is merged.

---

## Rollback Plan

Revert phase commit(s). `isCastAvailable` getter and `castAvailableState` are removed; Cast button visibility reverts to `BuildConfig.SUPPORT_CAST && (isImage || isVideo) && isWifiConnected(...)`. No data migration needed. Restore `CommandPanelController.kt` from backup if needed.
