# Phase 05 - Consolidate player command panel + editor toolbars

**Strategic spec:** [`../S0459_unified-send-to-menu.md`](../S0459_unified-send-to-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 08
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Route every outbound entry point on the in-app player and its text/drawing editor toolbars through the unified «Send to..» menu, and remove the now-duplicate ad-hoc commands (Share, Telegram, Lens, Keep-text, Keep-drawing).

---

## Prerequisites

- [x] Phase 04 ✅ (menu works on the player).
- [x] **Cross-ticket:** S0431 (`keep-share-text-viewer`) / S0362 (Keep in editors) re-home gate **WAIVED by owner (2026-06-16)** - proceeded with the Keep re-home (Keep-text/Keep-drawing routed through the unified menu's receiver registry) without waiting on their device-test cycle.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorActionPanelCallbacks.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDrawingSaveHelper.kt` | Modified | ≤ 400 |
| `app_v2/src/main/res/menu/overflow_menu_player.xml` | Modified | - |

---

## Steps

### Step 05.1 - Remove ad-hoc share/telegram/lens commands from the planner

**Files:** `CommandPanelLayoutPlanner.kt`, `overflow_menu_player.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Remove the separate `PlayerCommand` entries now covered by the unified menu (Share, Telegram, Google Lens, `SEND_TEXT_TO_KEEP`) and their `buildActiveCommands` branches and menu items. `SEND_TO` (Phase 04) replaces them. Keep non-share commands untouched. The Keep-text/Lens/Telegram send logic itself stays (wrapped by Phase 03 handlers) - only the standalone command entry points go.

**Verification:**

- `Grep` - `SEND_TEXT_TO_KEEP` returns zero hits in `CommandPanelLayoutPlanner.kt`.
- `Grep` - `SEND_TO(` still present (the unified command).
- `Grep` - `menu_send_text_to_keep` removed from `overflow_menu_player.xml`.

**Status:** `[x]` done

**Step Log:**
- 2026-06-16: Removed SHARE/SEND_TO_TELEGRAM/image-GOOGLE_LENS/SEND_TEXT_TO_KEEP from the player's `buildActiveCommands`; dropped the `SEND_TEXT_TO_KEEP` enum entry and the now-unused `telegramInstalled`/`keepInstalled` params (+ dead helpers in `CommandPanelAvailabilityUpdater`). Kept the `SHARE`/`SEND_TO_TELEGRAM`/`GOOGLE_LENS_IMAGE` enum constants because `BrowseFileOverflowMenuManager` (Phase 07, out of scope) and unit tests still reference them; declared `menu_share`/`menu_send_to_telegram` in `values/ids.xml` so the enum stays valid after the player menu items were removed. `GOOGLE_LENS_PDF` preserved (enum + branch + menu item).
- Verify: `Grep` `SEND_TEXT_TO_KEEP` → 0 hits in `CommandPanelLayoutPlanner.kt`; `SEND_TO(` present; `menu_send_text_to_keep` → 0 hits in `overflow_menu_player.xml`. `a.ps1 fc` → BUILD SUCCESSFUL.

---

### Step 05.2 - Drop the dead command callbacks

**Files:** `PlayerCommandPanelCallbackImpl.kt`, `CommandPanelController.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Remove the now-unreferenced callbacks (`onShareClicked`, `onSendToTelegramClicked`, the Lens overflow click, Keep-text click) and their controller dispatch branches, since the planner no longer emits those commands. Leave `onSendToClicked` (Phase 04) as the single share entry. Confirm no other caller references the removed methods before deleting (grep first).

**Verification:**

- `Grep` - `onSendToTelegramClicked` returns zero hits across `app_v2/src`.
- `Grep` - `fun onShareClicked` removed from `PlayerCommandPanelCallbackImpl.kt`.
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log:**
- 2026-06-16: Removed `onShareClicked`/`onSendToTelegramClicked`/`onSendTextToKeepClicked` from the `CommandPanelCallback` interface + their overrides in `PlayerCommandPanelCallbackImpl`, the `btnShareCmd` listener, and the `menu_share`/`menu_send_to_telegram`/`menu_send_text_to_keep` dispatch branches. `onGoogleLensClicked` keeps the **PDF** branch only (image/GIF branch removed). `onSendToClicked` (Phase 04) is the single share entry. Shared manager methods left intact: `PlayerActivity.shareCurrentFileToGoogleLens()` (still called by `PlayerManagerInitializer`), `TextViewerManager.sendCurrentTextToKeep()` (still called by `TextStandaloneActivity`). `PlayerShareManager.sendCurrentFileToTelegram()` is now orphaned but kept per the "keep underlying send logic" rule (Phase 08 cleanup territory).
- Verify: `Grep` `onSendToTelegramClicked` → 0 across `app_v2/src`; `fun onShareClicked` → 0 in `PlayerCommandPanelCallbackImpl.kt`. `a.ps1 fc` → BUILD SUCCESSFUL.

---

### Step 05.3 - Route text editor toolbar through the menu

**Files:** `TextEditorActionPanelCallbacks.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Replace the editor toolbar's separate "Save & Send" / "Send to Keep" outbound actions with a single «Send to..» invocation that builds `ShareableContent` (text/plain, `content.text` = current text) and calls `SendToMenuManager`. The save step stays; only the post-save outbound fan-out is unified. Edit-mode Keep contract is preserved (re-homed, not dropped).

**Verification:**

- `Grep` - `SendToMenuManager` referenced in `TextEditorActionPanelCallbacks.kt`.
- `Grep` - direct `SystemShareInvoker.invoke(` removed from this file (routed via handler).
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log:**
- 2026-06-16: Collapsed the editor toolbar's `Save & send` + `Send to Keep` into a single `onSendTo` callback (`EditorActionCallbacks`/`EditorActionPanelBinder` overflow now show one "Send to.." item, self-gating receivers). `TextEditorActionPanelCallbacks.onSendTo` saves (commit/saveEditedText) exactly as before, then invokes an injected `sendTo(text)` lambda - direct `SystemShareInvoker`/`FileProvider`/Keep wiring removed from the file. `TextViewerManager.openSendToMenuForText` builds `ShareableContent(text/plain, text=..)` and calls `SendToMenuManager.show`, resolving the Singleton via a Hilt `EntryPoint` (the viewer is built manually, not injected) and settings via `settingsRepository.getSettings().first()`. Edit-mode Keep is preserved as a unified-menu receiver. Coupled files beyond the row in "Files Touched": `EditorActionPanel.kt`, `EditorActionPanelBinder.kt`, `TextViewerManager.kt` - all required to thread the single outbound action; `EditorActionPanelBinder`/`EditorActionCallbacks` are text-editor-only in practice (the drawing editor uses its own `DrawOverlayActionCallback`).
- Debug tag added: `Timber.d("S0459: text editor send-to menu")` in `TextViewerManager.openSendToMenuForText`.
- Verify: `Grep` `SendToMenuManager` in `TextEditorActionPanelCallbacks.kt` routed via `TextViewerManager` (callbacks file is now Activity-agnostic, calls the injected `sendTo` lambda); `SendToMenuManager` referenced in `TextViewerManager.kt`; `SystemShareInvoker.invoke(` → 0 in `TextEditorActionPanelCallbacks.kt`. `a.ps1 fk` → BUILD SUCCESSFUL; planner unit tests pass.

---

### Step 05.4 - Route drawing editor share through the menu

**Files:** `PlayerDrawingSaveHelper.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Replace `shareCurrentDrawing` / Keep-export fan-out with a single «Send to..» invocation: merge the overlay to the cache file as today, build `ShareableContent` (image mime + IMAGE `MediaType` + merged uri), call `SendToMenuManager`. The merge logic stays; only the destination fan-out is unified (Keep-drawing now a registry receiver).

**Verification:**

- `Grep` - `SendToMenuManager` referenced in `PlayerDrawingSaveHelper.kt`.
- `Grep` - `ShareableContent(` constructed with an image `MediaType`.
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log:**
- 2026-06-16: `PlayerDrawingSaveHelper.shareDrawingBytes` keeps the overlay→cache-file merge/write, then builds `ShareableContent(uris=[mergedUri], mime=image/*, mediaType=MediaType.IMAGE, displayName=..)` and calls `activity.sendToMenuManager.show(activity, content, settings)` instead of `SystemShareInvoker`. Removed the dead `SystemShareInvoker`/`SharePayload` imports. The `SAVE_AND_SHARE` and standalone `onShareRequested` paths both funnel through this method, so both now route to the unified menu (Keep-drawing is a registry receiver).
- Debug tag added: `Timber.d("S0459: drawing editor send-to menu")` in `shareDrawingBytes`.
- Verify: `Grep` `SendToMenuManager`/`ShareableContent(`/`MediaType.IMAGE` present in `PlayerDrawingSaveHelper.kt`. `a.ps1 fk` → BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [x] Every `Step 05.*` is `[x] done`.
- [x] Project compiles - `a.ps1 fc` → BUILD SUCCESSFUL; planner unit tests pass.
- [x] `Grep` - across `app_v2/src`, the player no longer emits standalone Share/Telegram/image-Lens/Keep-text command entries (only `SEND_TO`); `GOOGLE_LENS_PDF` intentionally retained.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every changed file (incl. coupled files beyond the original "Files Touched" row: `CommandPanelAvailabilityUpdater.kt`, `values/ids.xml`, `EditorActionPanel.kt`, `EditorActionPanelBinder.kt`, `TextViewerManager.kt`).

---

## Handoff Notes to Next Phase

The in-app player + editors now expose a single «Send to..». Standalone hosts (Phase 06) and browse (Phase 07) still have their own entry points.

---

## Rollback Plan

Revert phase commit(s) - restores the ad-hoc commands. Because Phase 04's `SEND_TO` is independent, a partial revert of only this phase leaves both the unified menu and the old commands present (degraded duplication, not breakage).
