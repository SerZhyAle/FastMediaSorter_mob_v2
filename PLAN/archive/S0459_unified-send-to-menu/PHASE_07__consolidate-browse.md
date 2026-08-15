# Phase 07 - Consolidate browse grid + binary file sheet

**Strategic spec:** [`../S0459_unified-send-to-menu.md`](../S0459_unified-send-to-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 08
**Steps done:** 3 / 3
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Route the browse-screen outbound entry points - grid multi-select Share / Telegram and the binary-file bottom-sheet Share / Open-with - through the unified «Send to..» menu, applying the multi-file semantics (ADR-4).

---

## Prerequisites

- [ ] Phase 04 ✅ (menu + `SendToMenuManager` exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseShareOperationsHelper.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseFileOperationsManager.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt` | Modified | ≤ 300 |
| `app_v2/src/main/res/menu/menu_file_ops.xml` | Modified | - |

> `BrowseFileOperationsManager.kt` may exceed 500 LOC - take a timestamped backup in `temp/` before editing.

---

## Steps

### Step 07.1 - Route grid multi-select share/telegram to the menu

**Files:** `BrowseManagerInitializer.kt`, `BrowseFileOperationsManager.kt`, `BrowseShareOperationsHelper.kt`, `menu_file_ops.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the separate multi-select Share and Telegram actions with one «Send to..» action that builds `ShareableContent` from the selected `MediaFile`s (multi-uri, representative `MediaType`) and calls `SendToMenuManager`. Batch receivers (Share/Telegram/Email) send the whole selection; single-only receivers apply to the first file with the hint (ADR-4 - handled by `SendToMenuManager`). Remove the dedicated Telegram action-mode item from `menu_file_ops.xml`.

**Verification:**

- `Grep` - `SendToMenuManager` referenced in the browse action-mode wiring (`BrowseManagerInitializer.kt` or `BrowseFileOperationsManager.kt`).
- `Grep` - the standalone Telegram action item removed from `menu_file_ops.xml`.
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log (2026-06-16):**

- The browse outbound entry points are NOT an action-mode menu: `menu_file_ops.xml` only carries copy/move/rename/delete/move-up/move-down (no Share/Telegram items), so nothing was removed from it. The Share/Telegram surfaces are (a) the toolbar Share button (`onShareClicked` → `BrowseFileOperationsManager.shareSelectedFiles`, multi-select) and (b) the per-file overflow menu (`BrowseFileOverflowMenuManager` `PlayerCommand.SHARE` / `SEND_TO_TELEGRAM` items → `onShare` / `onSendToTelegram`, single file). Both were consolidated.
- Added `ShareableContent.mimeForMediaType(name, type)` companion helper so browse derives the share MIME identically to the player.
- `BrowseShareOperationsHelper`: replaced both `shareSelectedFiles` and `sendSelectedFilesToTelegram` with one `sendFilesToMenu(selectedFiles, resource, settings)` that stages Uris (local FileProvider Uri or cached network download via the existing progress path) then calls `SendToMenuManager.show`. Representative `MediaType` = first selected file; MIME specific for single, `*/*` for heterogeneous multi-select.
- `BrowseFileOperationsManager` now takes `sendToMenuManager` and exposes only `sendFilesToMenu`. `BrowseManagerInitializer` routes the toolbar `onShareClicked` and the collapsed per-file `onSendTo` through it. `BrowseActivity` injects `SendToMenuManager` (+ `OpenInShareTargetHandler`).
- `BrowseFileOverflowMenuManager`: collapsed the per-file Share + Telegram items into one `PlayerCommand.SEND_TO` entry routed to a single `onSendTo` callback; dropped the `TelegramShareTargets` install gate (now resolved inside the menu).
- Verification: `Grep` `SendToMenuManager` present in `BrowseShareOperationsHelper`/`BrowseFileOperationsManager`/`BrowseManagerInitializer`; `menu_file_ops.xml` confirmed to have never contained Telegram. `.\a.ps1 fk` PASS, `.\a.ps1 fc` PASS.

---

### Step 07.2 - Route binary-file sheet share through the menu

**Files:** `BrowseBinaryFileHandler.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Replace `shareFile()`'s direct `ACTION_SEND` chooser with a `SendToMenuManager` invocation built from the binary `MediaFile` (detected MIME, `MediaType`). Keep `openWithDefaultApp()` as the "Open in.." path but ensure it routes through the shared `OpenInShareTargetHandler` (no second open-with implementation).

**Verification:**

- `Grep` - `SendToMenuManager` referenced in `BrowseBinaryFileHandler.kt`.
- `Grep` - `shareFile` no longer calls `Intent.createChooser` directly.
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log (2026-06-16):**

- `BrowseBinaryFileHandler.shareFile()` now builds `ShareableContent` from the binary `MediaFile` (detected MIME via `getMimeTypeForFile`, `MediaType`, `mediaFile`) and calls `SendToMenuManager.show`. `openWithDefaultApp()` now delegates to the shared `OpenInShareTargetHandler.send` (no second ACTION_VIEW chooser). Uri staging: FileProvider for a local file path, parse for an existing `://` scheme.
- New deps threaded in: `BrowseBinaryFileHandler(sendToMenuManager, openInHandler, getSettings)` constructed in `BrowseManagerInitializer` from injected `SendToMenuManager` + `OpenInShareTargetHandler` + cached `latestSettings`.
- Verification: `Grep` `SendToMenuManager` present and `Intent.createChooser` absent in `BrowseBinaryFileHandler.kt`. `.\a.ps1 fc` PASS.

---

### Step 07.3 - Verify no orphaned browse share symbols remain

**Files:** `BrowseShareOperationsHelper.kt`, `BrowseFileOperationsManager.kt`
**Depends on:** Step 07.1, Step 07.2

**Prompt for developer:**

> Remove now-unreferenced browse share helpers (`sendSelectedFilesToTelegram`, the standalone `shareSelectedFiles` if fully replaced) after grepping for remaining callers. If a method is still used by a non-consolidated path, leave it and note why. Goal: one outbound path from browse, no dead share helpers.

**Verification:**

- `Grep` - `sendSelectedFilesToTelegram` returns zero hits across `app_v2/src` (or a recorded note explains any remaining caller).
- `Grep` for `TODO(phase-07)` returns zero hits.
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log (2026-06-16):**

- Removed `sendSelectedFilesToTelegram` and the standalone `shareSelectedFiles` from both `BrowseFileOperationsManager` and `BrowseShareOperationsHelper` (fully replaced by `sendFilesToMenu`). The now-unused `SystemShareInvoker` / `TelegramShareTargets` imports were dropped from the share helper, and `TelegramShareTargets` from `BrowseFileOverflowMenuManager`.
- Verification: `Grep` `shareSelectedFiles|sendSelectedFilesToTelegram|SystemShareInvoker|TelegramShareTargets` across `ui/browse` → zero hits. No `TODO(phase-07)` introduced.

**Cross-phase enum / id cleanup (final disposition):**

- `PlayerCommand.SEND_TO_TELEGRAM` + `R.id.menu_send_to_telegram`: **REMOVED.** After browse consolidation its only remaining reference was its own enum declaration - `CommandPanelController` does not map it and the unit test does not use it.
- `PlayerCommand.SHARE` + `R.id.menu_share`: **KEPT.** Still consumed by `CommandPanelController.barViewForCommand` (maps to the live `btnShareCmd` standalone-host view with its own click handler) and by 4 `CommandPanelLayoutPlannerTest` fixtures (generic bar-capable command). Browse was not its last consumer.
- `PlayerCommand.GOOGLE_LENS_IMAGE`: **KEPT.** Still consumed by `CommandPanelController.barViewForCommand` (`btnGoogleLensImageCmd`) and by `BrowseFileOverflowMenuManager`'s separate Google Lens path (`onGoogleLens`), which is independent of the Send-to consolidation.
- Planner + `ids.xml` comments updated to reflect the new disposition.

**Debug tags (BlockNeedUserTest invariant):** 3 new `Timber.d("S0459: ..")` tags added at the new entry points - `BrowseShareOperationsHelper.sendFilesToMenu` (browse selection send), `BrowseBinaryFileHandler.shareFile` (binary sheet send), `BrowseBinaryFileHandler.openWithDefaultApp` (binary open-with). The 6 pre-existing S0459 tags are untouched (9 total).

**Step Log (2026-06-16) - Phase 08 §11.7 audit follow-up (browse Lens fold):**

- The §11.7 consolidation audit found a missed surface: the browse per-file overflow still carried a separate "Google Lens" item that built its own `Intent.ACTION_SEND` + `createChooser` to Lens, bypassing the unified «Send to..» menu. This violated strategic goal 8 ("one item, no duplicates") and §11.7 ("no media-content sender outside a registered handler").
- Removed: `BrowseFileOverflowMenuManager` `GOOGLE_LENS_IMAGE` branch + `onGoogleLens` param + the `add(PlayerCommand.GOOGLE_LENS_IMAGE)` emission in `buildExtendedCommands`; `BrowseManagerInitializer.launchGoogleLensForFile` + its `onGoogleLens = {..}` wiring (and the now-unused `ClipData` / `FileProvider` imports). Lens stays reachable for image/gif files through the unified `onSendTo` path - the "lens" receiver registers for the image MediaType, which a single-file send preserves (`representative.type`).
- **Disposition correction to the cross-phase note above:** `PlayerCommand.GOOGLE_LENS_IMAGE` is **STILL KEPT**, but now solely because `CommandPanelController.barViewForCommand` maps it to the in-app player's `btnGoogleLensImageCmd` view. The browse `onGoogleLens` justification cited at item 4 of the disposition list no longer applies after this fold.
- Verification: `Grep` `GOOGLE_LENS_IMAGE` across `app_v2/src` → only `CommandPanelController.barViewForCommand` (in-app player) + the enum declaration + planner comment remain. `Grep` `launchGoogleLensForFile` → zero hits. BUILD: `a.ps1 fk` SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 07.*` is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` - browse outbound routes through `SendToMenuManager`; no dead `sendSelectedFilesToTelegram`.
- [x] Backup of `BrowseFileOperationsManager.kt` taken in `temp/` before edit.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

All audited IN-menu surfaces (player, editors, standalone, file-info, browse) now route through the single menu. Phase 08 closes out docs, catalog, and the grep-audit completion criterion (§11.7).

---

## Rollback Plan

Revert phase commit(s) - restores browse direct-share paths; unified menu unaffected.
