# Phase 02 — Open-in-FMS Relocation

**Strategic spec:** [`../S0180_standalone-player-file-info-button.md`](../S0180_standalone-player-file-info-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

After Phase 01 removes `openInFms()` from `btnInfoCmd`, restore the "Open in FMS" action via the overflow menu so users can still reach it from the standalone player.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Confirm `openInFms()` is currently unreachable from any UI entry point (grep for click-site calls — only keyboard handler and button should have had it).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml` | **New** | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1100 |

> **Architecture correction (discovered during execution):** `StandalonePlayerActivity` uses `Theme.FastMediaSorter.FullScreen` (no ActionBar) — `onCreateOptionsMenu` / `onOptionsItemSelected` are never called. The layout already contains `btnOverflowMenu` (visibility="gone") in both portrait (line 110) and landscape (line 97). Correct approach: create a minimal standalone menu XML, show it via `PopupMenu` anchored to `btnOverflowMenu`.
> Landscape parity: `btnOverflowMenu` is already present in `res/layout-land/activity_player_unified.xml` (line 97). No new layout edits needed — the binding provides the button in both orientations.

---

## Steps

### Step 02.1 — Create `overflow_menu_standalone_player.xml`

**Files:** `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create new file `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml` with a single item:
> - `android:id="@+id/menu_open_in_fms"`
> - `android:title="@string/open_in_fms"` (string key already exists in all locales)
> - `app:showAsAction="never"`

**Verification:**

- `Glob` — `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml` exists.
- `Grep` — `menu_open_in_fms` appears in `overflow_menu_standalone_player.xml`.
- `Grep` — `open_in_fms` appears in `app_v2/src/main/res/values/strings.xml`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 3/3 PASS. Created `overflow_menu_standalone_player.xml` with `menu_open_in_fms`.

---

### Step 02.2 — Wire `btnOverflowMenu` with PopupMenu in `StandalonePlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `setupFileOperationButtons()`, add two lines after the existing button wiring:
> 1. Make `binding.btnOverflowMenu` visible.
> 2. Set its click listener to show a `PopupMenu` anchored to `binding.btnOverflowMenu`, inflated from `R.menu.overflow_menu_standalone_player`. In `setOnMenuItemClickListener`, handle `R.id.menu_open_in_fms` → `openInFms()` and return `true`.
>
> Add import `androidx.appcompat.widget.PopupMenu` if not already present.
> Use `Timber.d(...)` for any debug logging — never `Log.d`.

**Verification:**

- `Grep` — `btnOverflowMenu` appears in `StandalonePlayerActivity.kt`.
- `Grep` — `overflow_menu_standalone_player` appears in `StandalonePlayerActivity.kt`.
- `Grep` — `menu_open_in_fms` appears in `StandalonePlayerActivity.kt`.
- `Grep` — `openInFms()` is called inside the `menu_open_in_fms` handler.
- `Grep -n "Log\.d\("` — zero hits in `StandalonePlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-13 — Verification 5/5 PASS. Added import + `btnOverflowMenu` PopupMenu wiring in `setupFileOperationButtons()`.

---

## Phase Done Criteria

- [ ] Every Step 02.* above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] Manual check: in standalone player, the overflow menu shows "Open in FMS"; in normal player it does not.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for both modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- All functional changes are complete after this phase.
- "Open in FMS" is in overflow menu (standalone-only visible), `btnInfoCmd` shows `FileInfoDialog`.
- Phase 03 is a cleanup-only phase: catalog render + final dev log.

---

## Rollback Plan

Revert phase commit(s). Remove `menu_open_in_fms` from XML and Kotlin. No data migration or persistent state changed.
