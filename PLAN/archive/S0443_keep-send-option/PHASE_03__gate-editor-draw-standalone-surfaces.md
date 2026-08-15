# Phase 03 - Gate the editor / draw / standalone Keep surfaces

**Strategic spec:** [`../S0443_keep-send-option.md`](../S0443_keep-send-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Apply the same `keepEnabled && keepInstalled` gate (pattern from Phase 02) to the remaining three Keep command surfaces so the toggle controls the command everywhere it appears: the text editor action panel, the draw-overlay editor, and the standalone text host overflow. After this phase, criterion §11.2 ("OFF -> hidden everywhere") holds across the whole program.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (Keep target + `KEEP_TARGET_ID`).
- [ ] Phase 02 ✅ Done (host-injection + combined-predicate pattern established).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt` | Modified | (ctor wiring only) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | (ctor wiring only) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified | (ctor wiring only) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/ImageDrawOverlayManager.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | (ctor wiring only) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandaloneDrawSaveHelper.kt` | Modified | (ctor wiring only) |

> The standalone text host that toggles `menu_send_to_keep` visibility in `overflow_menu_standalone_player.xml` is the same `TextViewerManager`/standalone-host path; identify the exact class that flips `menu_send_to_keep.isVisible` during research-in-step and gate it there. No XML edit is expected (the item is already `android:visible="false"` and toggled in code); if a `layout-land/` counterpart of an edited layout is touched, list it (none expected - this is a `menu/` resource, which has no orientation variant).

---

## Steps

### Step 03.1 - Gate the text editor action panel

**Files:** `ui/player/helpers/TextViewerManager.kt`, `ui/player/PlayerViewerFactory.kt`, `ui/player/helpers/StandaloneViewManager.kt`, `ui/player/standalone/TextStandaloneActivity.kt`

**Depends on:** Phase 02

**Prompt for developer:**

> `TextViewerManager` builds `EditorActionPanelBinder(keepAvailable = keepChecker.isKeepAvailable(), ...)`, which shows the "Send to Keep" overflow item. Change `keepAvailable` to the combined predicate `isKeepEnabled() && keepChecker.isKeepAvailable()`, where `isKeepEnabled()` calls `isShareTargetEnabledUseCase(KEEP_TARGET_ID, settings)` against the current `AppSettings`. `TextViewerManager` already receives `settingsRepository`; read settings the same way it already does for other editor flags (it observes `settingsRepository.getSettings()`), and pass the use-case in as a new constructor parameter. Wire the use-case from the three construction sites - `PlayerViewerFactory.createTextViewerManager()` (panel host), `StandaloneViewManager.createTextViewerManager()` and `TextStandaloneActivity` (standalone hosts) - each reading the host activity's injected `isShareTargetEnabledUseCase`. The `keepChecker` launch-time guard stays. Also gate the actual `onSendToKeep` path defensively: if the flag is off, the menu item is already hidden, so no extra runtime block is required - do not add a redundant check inside `TextEditorActionPanelCallbacks`.

**Verification:**

- `Grep` - `isShareTargetEnabledUseCase` referenced in `TextViewerManager.kt`.
- `Grep` - `KEEP_TARGET_ID` referenced (no inline `"keep"`).
- `Grep` - `keepAvailable =` in `TextViewerManager.kt` no longer reads `keepChecker.isKeepAvailable()` alone (combined predicate).
- `Grep` - the use-case is passed at all three construction sites (`PlayerViewerFactory`, `StandaloneViewManager`, `TextStandaloneActivity`).
- Project compiles (`.\a.ps1 fk`).

**Status:** `[ ]` not done

---

### Step 03.2 - Gate the draw-overlay editor

**Files:** `ui/player/helpers/ImageDrawOverlayManager.kt`, `ui/player/PlayerManagerInitializer.kt`, `ui/player/standalone/StandaloneDrawSaveHelper.kt`

**Depends on:** Phase 02

**Prompt for developer:**

> `ImageDrawOverlayManager` (S0362) gates its "Send to Google Keep" overflow item on `keepChecker.isKeepAvailable()`. Change that gate to `isKeepEnabled() && keepChecker.isKeepAvailable()`, with `isKeepEnabled()` = `isShareTargetEnabledUseCase(KEEP_TARGET_ID, settings)`. This manager does not currently receive `SettingsRepository`; pass in the already-injected use-case as a new constructor parameter (the use-case bundles registry + resolver, but the effective state still needs the current `AppSettings`). Resolve the current `AppSettings`: prefer passing a lightweight `currentSettings` provider / the `SettingsRepository` from the host (the host activity is `@AndroidEntryPoint` and already exposes `settingsRepository`); read it where the Keep item visibility is decided. Wire from both construction sites - `PlayerManagerInitializer` (panel host) and `StandaloneDrawSaveHelper` (standalone host). Keep the `keepChecker` launch guard.

**Verification:**

- `Grep` - `isShareTargetEnabledUseCase` referenced in `ImageDrawOverlayManager.kt`.
- `Grep` - `KEEP_TARGET_ID` referenced (no inline `"keep"`).
- `Grep` - the use-case is passed at both construction sites (`PlayerManagerInitializer`, `StandaloneDrawSaveHelper`).
- `Grep -n "Log\.d\("` - zero hits in `ImageDrawOverlayManager.kt`.
- Project compiles (`.\a.ps1 fk`).

**Status:** `[ ]` not done

---

### Step 03.3 - Gate the standalone text-host overflow item

**Files:** the standalone text host that flips `menu_send_to_keep.isVisible` (identify in-step; same `TextViewerManager`/standalone path as Step 03.1)

**Depends on:** Step 03.1

**Prompt for developer:**

> Find where `menu_send_to_keep` (from `res/menu/overflow_menu_standalone_player.xml`) is made visible at runtime for the standalone text host (Grep `menu_send_to_keep` across `ui/player/standalone/` and `ui/player/helpers/`). That visibility decision currently keys on the Keep installed-check; combine it with `isKeepEnabled()` so the item is hidden when the toggle is off. Reuse the use-case already threaded into the standalone host in Step 03.1 - do not re-inject. If this visibility is actually decided through the same `EditorActionPanelBinder`/`buildActiveCommands` path already gated in Step 03.1 / Phase 02, confirm that and record "covered by 03.1/02" instead of adding a redundant gate.

**Verification:**

- `Grep` - `menu_send_to_keep` runtime visibility is gated by `isShareTargetEnabledUseCase` (or recorded as already covered by Step 03.1 / Phase 02 with the covering call site cited).
- Project compiles (`.\a.ps1 fk`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` or `assembleStandardDebug`.
- [ ] All four Keep surfaces (this phase's three + Phase 02's command panel) now gate on `keepEnabled && keepInstalled`. Cross-check via `Grep` that no remaining `keepChecker.isKeepAvailable()` / `isKeepInstalled()` visibility gate is left ungated for the Keep command (launch-time guards may remain).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] No new hardcoded `"keep"` literal outside `KEEP_TARGET_ID`.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- All Keep command surfaces are now flag-gated; Phase 04 records the user-visible feature and verifies on device.
- Device test (Phase 04) must walk all four surfaces with the toggle ON and OFF, and with Keep uninstalled.

---

## Rollback Plan

Revert phase commit(s). Each gated surface reverts to installed-only visibility. No data migration.
