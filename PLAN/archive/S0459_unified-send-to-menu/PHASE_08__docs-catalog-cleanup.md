# Phase 08 - Docs, catalog, and consolidation audit

**Strategic spec:** [`../S0459_unified-send-to-menu.md`](../S0459_unified-send-to-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all (01-07)
**Blocks:** -
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-17

---

## Objective

Document the new «Send to..» capability trilingually, regenerate the class catalog, and prove the consolidation completeness criterion (§11.7) with an explicit grep audit that no duplicate outbound surfaces remain.

---

## Prerequisites

- [ ] Phases 01-07 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | - |
| `docs/FEATURES_RU.md` | Modified | - |
| `docs/FEATURES_UK.md` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/FUNCTIONALITY.log` | Appended | - |

---

## Steps

### Step 08.1 - Document the feature (trilingual)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the «Send to..» menu to all three FEATURES docs (strategic §8 mandates it): one high-priority command listing the receivers enabled in settings and applicable to the current file type, replacing the former scattered share/print/open-in items. Keep the existing tone and structure of these docs. EN/RU/UK in lockstep.

**Verification:**

- `Grep` - "Send to" (EN) / "Отправить в" (RU) / "Надіслати" (UK) present in the respective FEATURES files.
- All three files modified (one entry each).

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Replaced the former "Send to Telegram" feature entry (line 41) with the unified «Send to..» entry in FEATURES.md + _RU + _UK, in lockstep. Tag `[Standard / VR]` preserved. Telegram is now described as one receiver among many.

---

### Step 08.2 - Consolidation completeness audit (§11.7)

**Files:** (audit only - no file delta; findings drive any residual fix)
**Depends on:** Step 08.1

**Prompt for developer:**

> Run the grep audit that §11.7 requires: confirm no surface outside the unified path still constructs an outbound `ACTION_SEND` / share-`createChooser` for media content except `SystemShareInvoker` (the shared invoker) and the registered handlers. Expected remaining hits are OUT-of-menu only (log export, bug-report mailto, resource/backup export, `ReceiveShareActivity` incoming). Any media-content sender outside a handler is a missed consolidation - fix it before closing.

**Verification:**

- `Grep` - `Intent.createChooser` hits across `app_v2/src` reduced to the shared invoker + handlers + OUT-of-menu set (enumerate them; no player/browse/standalone media sender remains).
- `Grep` - `ACTION_SEND` media-content senders all live under `core/share/` or registered handlers.

**Status:** `[x]` done

**Step Log (audit result, 2026-06-17):**

The audit drove three additional consolidation/cleanup fixes beyond the planned steps:

- Browse per-file Google Lens overflow item folded into the unified menu (`launchGoogleLensForFile` + its browse `ACTION_SEND` removed; Lens now reachable via the menu's lens receiver).
- Standalone overflow folds: PhotoVideo Google Lens item and Text "Send to Keep" item removed (redundant with the standalone share button which already opens the unified menu).
- Dead-code removal (Rule 20): orphaned in-app player share cluster (`PlayerActivity.shareCurrentFile`, `FileOperationsHandler.performShare`/`shareLocalFile`/`shareNetworkFileWithProgress`/`cleanupOldShareTempFiles`), dead `PlayerShareManager.sendCurrentFileToTelegram`/`sendFileToTelegram`, dead `TextViewerManager.sendCurrentTextToKeep`/`isKeepTargetAvailable`/`keepChecker`.

Remaining `createChooser` / `ACTION_SEND` sender hits, enumerated and confirmed acceptable (no scattered share command remains):

- `core/share/SystemShareInvoker` + the registered `*ShareTargetHandler`s - the shared invoker and the menu's own receiver send logic (the intended single path).
- OUT-of-menu exports (strategic §6 firewall): `LogExportHelper` (logs), `MainEventHandler` (resource-config export), `BackupRestoreFragment` (backup JSON), `ScrollableTextDialog` (diagnostic/error text).
- Distinct features, not share-command duplicates: `GoogleLensButtonsManager` dedicated floating Lens button + `PdfViewerManager` PDF-page-to-Lens (`PlayerShareManager.shareFileToGoogleLens` / `shareCurrentFileToGoogleLens`); `DrawKeepExportHelper` in-editor Keep quick-export.
- Error-recovery affordances, not the share menu: `PlayerShareManager.openInExternalPlayer` (playback-failure "open externally" snackbar action) and the Office fallback dialog (`PlayerShareManager` Office share when no embedded viewer).
- Incoming-intent `when (action) { ACTION_SEND -> .. }` parsing in standalone dispatchers (not senders).

---

### Step 08.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 08.1

**Prompt for developer:**

> Regenerate the catalog (public API changed: new `ShareTargetHandler`, `ShareableContent`, `BuildSendToReceiverListUseCase`, `SendToMenuManager`, handlers). Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Fill `role` + `status` for the new classes via `set.ps1`.

**Verification:**

- `Grep` - `SendToMenuManager` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `ShareTargetHandler` present in the catalog.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - `catalog_sync.ps1 -Module app_v2` re-scanned 1832 records. `role` + `status=new` set for `SendToMenuManager`, `SendToBottomSheet`, `BuildSendToReceiverListUseCase` via `set.ps1`.

---

### Step 08.4 - Dev log + functionality log

**Files:** `dev/CHANGELOG.md` (via script), `dev/FUNCTIONALITY.log` (via script)
**Depends on:** Step 08.1, 08.2, 08.3

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has an entry for every file modified across phases 01-08 (via `add_to_dev_log.ps1`). Append one `dev/FUNCTIONALITY.log` entry via `scripts/add_to_functionality_log.ps1` (`CHANGE`: scattered share/print/open-in commands replaced by a unified «Send to..» menu).

**Verification:**

- `Grep` - `SendToMenuManager` (or a phase file path) present in `dev/CHANGELOG.md`.
- `Grep` - an `S0459` entry present in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Dev changelog entries added per changed file across phases 04 fixes + 08 audit (agents logged phases 05-07 files). `FUNCTIONALITY.log` `CHANGE` entry appended for S0459 (scattered share commands → unified menu).

---

## Phase Done Criteria

- [x] Every `Step 08.*` is `[x] done`.
- [x] FEATURES EN/RU/UK updated in lockstep.
- [x] Catalog regenerated; new classes carry `role` + `status`.
- [x] §11.7 grep audit clean (remaining senders enumerated: shared invoker/handlers, OUT-of-menu exports, distinct features, error-recovery, incoming-intent parsing - no scattered share command remains).
- [x] `dev/CHANGELOG.md` + `dev/FUNCTIONALITY.log` updated.
- [x] Ready for `/spec-check S0459` (after on-device BlockNeedUserTest sweep).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Run `/spec-check S0459` to set `Verified`.

---

## Rollback Plan

Docs/catalog/log only - revert the doc edits; no code or user-facing surface affected by this phase.
