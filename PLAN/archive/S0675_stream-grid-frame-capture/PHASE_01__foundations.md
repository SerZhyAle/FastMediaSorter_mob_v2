# Phase 01 - Foundations

**Strategic spec:** [`../S0675_stream-grid-frame-capture.md`](../S0675_stream-grid-frame-capture.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Persist the chosen list/grid display mode across sessions and expose it as ViewModel state with a toggle action. No new UI yet.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `DisplayMode` enum (LIST/GRID) confirmed in `domain/model/Models.kt` - reused, not redefined.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/StreamsSessionStore.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 440 |

---

## Steps

### Step 01.1 - Persist last display mode in StreamsSessionStore

**Files:** `data/repository/settings/StreamsSessionStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a persisted `lastDisplayMode: String?` to the streams session DataStore, mirroring the existing `lastSort`/`lastMediaFilter` keys. Add `KEY_LAST_DISPLAY_MODE = stringPreferencesKey("last_display_mode")`, surface `lastDisplayMode` in the `Session` data class (null = no remembered value), read it in `read()`, and add `suspend fun writeDisplayMode(mode: String)` that edits only that key. Store the raw `DisplayMode.name` string - keep this store enum-free (the ViewModel owns the decode), consistent with the existing contract-free comment.

**Verification:**

- `Grep` - `last_display_mode` matches once in `StreamsSessionStore.kt`.
- `Grep` - `fun writeDisplayMode` present.
- `Grep` - `lastDisplayMode` appears in the `Session(` constructor and `data class Session`.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Added `KEY_LAST_DISPLAY_MODE`, `Session.lastDisplayMode`, read mapping, and `writeDisplayMode(mode)`. Verification grep: all predicates matched.

---

### Step 01.2 - Expose displayMode state + toggle in StreamsViewModel

**Files:** `ui/streams/StreamsViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `displayMode: DisplayMode = DisplayMode.LIST` to `StreamsUiState`. On screen open (the existing session-restore path that already reads `StreamsSessionStore`), decode the persisted `lastDisplayMode` via `DisplayMode.valueOf` guarded against an unknown name (fall back to LIST) and emit it into state. Add `fun onToggleDisplayMode()` that flips LIST<->GRID, emits the new state, and persists it through `StreamsSessionStore.writeDisplayMode(newMode.name)` in `viewModelScope`. If `StreamsSessionStore` is not already injected into the ViewModel, add it to the constructor (Hilt `@Inject constructor` wiring only - no new module). Reuse the existing import of `DisplayMode`.

**Verification:**

- `Grep` - `displayMode` present in `StreamsUiState`.
- `Grep` - `fun onToggleDisplayMode` present.
- `Grep` - `writeDisplayMode` referenced in `StreamsViewModel.kt`.
- `.\a.ps1 fk` exit 0.

**Status:** `[x]` done

**Step Log:**
- 2026-06-25 - Added `StreamsUiState.displayMode`, decode of `lastDisplayMode` in `seedInitialFilter` (fallback LIST), `displayMode` preserved across combine emissions, and `onToggleDisplayMode()` persisting via `sessionStore.writeDisplayMode`. `.\a.ps1 fk` exit 0.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build` (or `.\a.ps1 fk` for this compile-only phase).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`StreamsUiState.displayMode` is the single source of truth for the toggle; persistence is automatic. Phase 05 binds the toolbar toggle to `onToggleDisplayMode()` and switches layout/adapter on `displayMode`.

---

## Rollback Plan

Revert phase commit(s) - no data migration; a stale `last_display_mode` key is harmless (decode falls back to LIST).
