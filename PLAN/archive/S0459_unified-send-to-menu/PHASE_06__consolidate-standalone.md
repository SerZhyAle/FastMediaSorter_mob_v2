# Phase 06 - Consolidate standalone players + file-info open-with

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

Route the standalone players' own outbound entry points - the shared `btnShareCmd` (`shareCurrentFile`, all four hosts incl. audio), the standalone Office share fallback, and the file-info "open in external player" - through the unified «Send to..» menu (research 01 correction).

---

## Prerequisites

- [x] Phase 04 ✅ (menu + `SendToMenuManager` exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/helpers/FileInfoLaunchManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml` | Modified | - |

> `StandaloneViewManager.kt` may exceed 500 LOC - take a timestamped backup in `temp/` before editing (Constraints).

---

## Steps

### Step 06.1 - Route standalone share button through the menu

**Files:** `StandaloneFileOperationsHandler.kt`, `overflow_menu_standalone_player.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Change `shareCurrentFile()` to build `ShareableContent` from the current standalone file (uri, mime, `MediaType` incl. audio) and call `SendToMenuManager` instead of its own `ACTION_SEND` chooser. The `btnShareCmd` button now opens the unified menu in every standalone host (Photo/Video, Audio, Document, Text). Keep the audio overflow gating (image/video-only items already hidden) - applicability handles receiver visibility.

**Verification:**

- `Grep` - `SendToMenuManager` referenced in `StandaloneFileOperationsHandler.kt`.
- `Grep` - `Intent.createChooser` removed from `shareCurrentFile` (no direct chooser).
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log (2026-06-16):** `shareCurrentFile()` now builds `ShareableContent` (uri, mime, `file.type` incl. AUDIO, displayName, mediaFile) and calls `SendToMenuManager.show()`; direct `ACTION_SEND` + `createChooser` removed. Two ctor params added to `StandaloneFileOperationsHandler` (`sendToMenuManager`, `getCurrentSettings: suspend () -> AppSettings`); all five hosts (StandalonePlayerActivity + Audio/PhotoVideo/Document/Text standalone) inject `SendToMenuManager` and supply `settingsRepository.getSettings().first()`. The shared `btnShareCmd` routes through here in every host; audio overflow gating untouched (applicability decides receiver visibility). `overflow_menu_standalone_player.xml` needed no change - the standalone share affordance is the dedicated `btnShareCmd` button, not a menu item; the menu carries no share entry to fold. Verified: `SendToMenuManager` referenced; no `createChooser`/`ACTION_SEND` in the handler. `a.ps1 fk` → BUILD SUCCESSFUL (exit 0).

---

### Step 06.2 - Fold the standalone Office share fallback into the menu

**Files:** `StandaloneViewManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Replace `shareOfficeDocument`'s direct `ACTION_SEND` chooser with a `SendToMenuManager` invocation built from the prepared Office file (`ShareableContent` with OFFICE_DOCUMENT `MediaType`). Keep the network-file preparation (`prepareFileForRead`) off the main thread as today. The Office open path (`ACTION_VIEW`) is the "Open in.." receiver and is also reachable from the menu.

**Verification:**

- `Grep` - `SendToMenuManager` referenced in `StandaloneViewManager.kt`.
- `Grep` - the standalone `shareOfficeDocument` no longer calls `Intent.createChooser` directly.
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log (2026-06-16):** `shareOfficeDocument` now builds `ShareableContent` with `MediaType.OFFICE_DOCUMENT` from the prepared FileProvider Uri and calls `SendToMenuManager.show()`; direct `ACTION_SEND` + `createChooser` removed (only a historical KDoc note retains the term). `prepareFileForRead` stays inside `lifecycleScope.launch` exactly as before (off the main thread). `SendToMenuManager` is resolved via a Hilt `EntryPoint` (manager is manually built, not `@AndroidEntryPoint`), mirroring the Phase 05 `TextViewerManager` pattern; `activity` is cast to `FragmentActivity` (all standalone hosts are). Verified: `SendToMenuManager` referenced; no live `createChooser` in `shareOfficeDocument`. `a.ps1 fk` → BUILD SUCCESSFUL (exit 0).

---

### Step 06.3 - Route file-info open-with through the «Open in..» receiver

**Files:** `FileInfoLaunchManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Route the file-info dialog's "open in external player" entry points through `SendToMenuManager` (which surfaces the "Open in.." receiver), or, if the file-info dialog should stay a one-tap open, delegate to the same `OpenInShareTargetHandler` so the open-with logic lives in one place. Do not leave a second independent `ACTION_VIEW` chooser implementation. Build `ShareableContent` from the file-info `MediaFile`.

**Verification:**

- `Grep` - `SendToMenuManager` or `OpenInShareTargetHandler` referenced in `FileInfoLaunchManager.kt`.
- `Grep` - no standalone duplicate `Intent.createChooser(.*open_with` remains outside the shared handler.
- Project compiles - run `/build`.

**Status:** `[x]` done

**Step Log (2026-06-16):** Both file-info open-with paths (`openInExternalPlayer` and the post-download `openDownloadedFile`) now delegate to a shared `openWithSharedHandler(uri, mime)` that builds `ShareableContent` from the file-info `MediaFile` and calls `OpenInShareTargetHandler.send(activity, content)` - the single ACTION_VIEW + chooser implementation also used by the unified menu's "Open in.." receiver. The file-info dialog stays a one-tap open (no menu), so it delegates to the handler rather than `SendToMenuManager` (per the prompt's alternative). The handler is resolved via a Hilt `EntryPoint` (`FileInfoLaunchManager` is manually built); `context` is cast to `Activity`. Both local `Intent.createChooser` blocks removed. Verified: `OpenInShareTargetHandler` referenced; no `createChooser` remains. `a.ps1 fk` → BUILD SUCCESSFUL (exit 0).

---

## Phase Done Criteria

- [x] Every `Step 06.*` is `[x] done`.
- [x] Project compiles - `a.ps1 fk` BUILD SUCCESSFUL (exit 0).
- [x] `Grep` - standalone hosts route outbound through `SendToMenuManager` (no direct `createChooser` in `shareCurrentFile` / standalone `shareOfficeDocument`).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Backup of `StandaloneViewManager.kt` taken in `temp/` before edit (`temp/StandaloneViewManager.kt.20260616_232954.bak`).
- [x] Dev log entry added for every changed file (`overflow_menu_standalone_player.xml` unchanged - no entry needed).

---

**§11.7 audit follow-up (2026-06-17):** Two standalone hosts still carried per-file outbound overflow items duplicating receivers the unified «Send to..» menu already offers (strategic goal 8 "one item, no duplicates"; §11.7 "no media sender outside a registered handler"). Removed: (1) `PhotoVideoStandaloneActivity` - the `menu_google_lens` overflow item (handler + visibility wiring), the now-unused `googleLensSettingEnabled` field, its `settingsRepository.getSettings()` collector, and the orphaned `IsShareTargetEnabledUseCase` injection; the Lens receiver is reached via `btnShareCmd → SendToMenuManager` for image/gif. The shared `menu_google_lens` XML item stays (in-app player + other standalone hosts reference it); the host now hides it explicitly. (2) `TextStandaloneActivity` - the `menu_send_to_keep` overflow item (handler + `isKeepTargetAvailable()` visibility wiring); the Keep-text receiver is reached via `btnShareCmd → SendToMenuManager` for TEXT. The `menu_send_to_keep` XML item was orphaned and removed from `overflow_menu_standalone_player.xml`. `TextViewerManager.sendCurrentTextToKeep()` + `isKeepTargetAvailable()` left orphaned (send logic conceptually mirrored by the Keep-text registry handler; out-of-scope to delete). In-app player Lens (`GoogleLensButtonsManager`) and PDF-page Lens (`GOOGLE_LENS_PDF`) untouched. `a.ps1 fc` → BUILD SUCCESSFUL (exit 0).

---

## Handoff Notes to Next Phase

Player family (in-app + standalone) and file-info are unified. Only the browse screen surfaces remain (Phase 07).

---

## Rollback Plan

Revert phase commit(s) - restores the standalone direct-chooser paths; unified menu stays available, so worst case is duplicate affordances, not loss.
