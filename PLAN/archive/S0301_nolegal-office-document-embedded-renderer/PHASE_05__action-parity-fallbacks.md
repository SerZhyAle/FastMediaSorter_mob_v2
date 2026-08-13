# Phase 05 - Action Parity & Fallbacks

**Strategic spec:** [`../S0301_nolegal-office-document-embedded-renderer.md`](../S0301_nolegal-office-document-embedded-renderer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-30
**Completed:** 2026-05-30

---

## Objective

Give the internal Office viewer the same user-facing parity as the current PDF/EPUB route for view/translate/OCR/print, while keeping edit actions disabled and the fallback dialog explicit.

---

## Prerequisites

- [x] Phase 04 is ✅ Done.
- [x] Strategic §6.7 blocker is Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerTranslationButtonCallbackImpl.kt` | Modified | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerGestureCallbackImpl.kt` | Modified | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt` | Modified | ≤ 260 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentPrintAdapter.kt` | New | ≤ 260 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3600 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 3600 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 3600 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 05.1 - Surface Office viewer commands without exposing edit actions

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelAvailabilityUpdater.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an Office document command set that mirrors PDF/EPUB parity for view/translate/OCR/print. Keep edit actions hidden - the Office route is read-only even when the current file is writable.

**Verification:**

- `Grep` - `OFFICE_DOCUMENT` exists in `CommandPanelAvailabilityUpdater.kt`.
- `Grep` - `OFFICE_DOCUMENT` exists in `CommandPanelLayoutPlanner.kt`.
- `Grep` - no Office-specific command maps to `R.string.edit` in the planner.

**Status:** `[x]` done

---

### Step 05.2 - Route translate / OCR / print through the Office viewer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerTranslationButtonCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentPrintManager.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentPrintAdapter.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Wire translate, OCR, and print callbacks into the Office viewer manager. Printing must use an Office-specific adapter/conversion path instead of pretending every Office file is a PDF, and the callbacks must stay read-only.

**Verification:**

- `Grep` - `MediaType.OFFICE_DOCUMENT` exists in `PlayerCommandPanelCallbackImpl.kt`.
- `Grep` - `MediaType.OFFICE_DOCUMENT` exists in `DocumentPrintManager.kt`.
- `Glob` - `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeDocumentPrintAdapter.kt` exists.

**Status:** `[x]` done

---

### Step 05.3 - Add keyboard, touch-zone, and navigation parity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerGestureCallbackImpl.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerKeyboardHandler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationCoordinator.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Extend document-viewer navigation parity to Office documents: touch zones, keyboard shortcuts, and fullscreen exit behavior must follow the same patterns already used by PDF/EPUB. Keep the navigation family-aware so sheet/slide viewers do not inherit page-only assumptions.

**Verification:**

- `Grep` - `MediaType.OFFICE_DOCUMENT` exists in `PlayerGestureCallbackImpl.kt`.
- `Grep` - `MediaType.OFFICE_DOCUMENT` exists in `PlayerKeyboardHandler.kt`.
- `Grep` - `MediaType.OFFICE_DOCUMENT` exists in `TouchZoneConfig.kt`.
- `Grep` - `MediaType.OFFICE_DOCUMENT` exists in `PlayerNavigationCoordinator.kt`.

**Status:** `[x]` done

---

### Step 05.4 - Add fallback-dialog strings and communication-policy checks

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 05.3

**Prompt for developer:**

> Add localized strings for the explicit Office fallback dialog (`external app`, `share`, `cancel`) and any Office-viewer errors. Check every new string against `docs/COMMUNICATION_POLICY.md` §2 for message structure and §6 for tone; the Office viewer must stay factual and non-promissory about fidelity.

**Verification:**

- `Grep` - `office_viewer_fallback_title` exists in all three strings files.
- `Grep` - `office_viewer_fallback_open_external` exists in all three strings files.
- `Grep` - `office_viewer_fallback_share` exists in all three strings files.
- `Grep` - `office_viewer_fallback_cancel` exists in all three strings files.
- `Grep` - no `TODO translate` markers were introduced in these files.
- `Grep` - Strings pass COMMUNICATION_POLICY §6 checklist (record result in step log). Result: PASS - strings are factual, make no fidelity promise ("can't be shown here", "choose how to open it").

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - noLegal BUILD SUCCESSFUL (1m14s) and standard BUILD SUCCESSFUL (1m33s).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1` (17 entries).
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

The internal Office viewer now matches the intended PDF/EPUB user-facing parity for view/translate/OCR/print, with explicit fallback strings and no edit path.

---

## Rollback Plan

Revert phase commit(s) - no data migration or public market surface changed.