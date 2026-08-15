# Phase 04 - Wire Copy/Move into the standalone image host

**Strategic spec:** [`../S0610_standalone-image-player-commands.md`](../S0610_standalone-image-player-commands.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-22
**Completed:** 2026-06-22

---

## Objective

Populate the standalone image host's Copy/Move grids from the global destination list and execute copy/move of the current
file via the existing file-operation use-case, with standalone single-file post-operation behavior (stay on copy, finish on move).

---

## Prerequisites

- [ ] Phase 02 and Phase 03 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFileOperationsHandler.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1000 |

> Reuse the global `GetDestinationsUseCase` (Flow `invoke()`) and the injected `FileOperationUseCase`. Do NOT pull in the
> in-app `FileOperationsHandler` / `PlayerFileOperationQueue` / list-navigation - standalone is single-file (research 02).

---

## Steps

### Step 04.1 - Add copy/move-to-destination to the standalone file ops handler

**Files:** `app_v2/.../ui/player/helpers/StandaloneFileOperationsHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `copyCurrentFileTo(destination: MediaResource)` and `moveCurrentFileTo(destination: MediaResource)`. Each resolves the current `MediaFile`, builds a `FileOperation.Copy` / `FileOperation.Move` (mirror the in-app `FileOperationsHandler.performCopy` shape: network-aware source/dest files, `overwrite` from settings), runs `fileOperationUseCase.execute(operation)`, and on the main thread surfaces a factual result toast reusing existing strings (`msg_copy_success` / move equivalent, failure strings). On copy success keep the activity open; on move success call `activity.finish()` (mirrors `onDeleteSuccess`). Inject/pass `FileOperationUseCase` and `GetDestinationsUseCase` through the constructor. Log failures at `Timber.e` with a description (no ticket id in the message). No empty catch blocks.

**Verification:**

- `Grep` - `fun copyCurrentFileTo(` and `fun moveCurrentFileTo(` present.
- `Grep` - `fileOperationUseCase.execute(` present in the handler.
- `Grep` - `activity.finish()` reachable from the move-success path.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. copy/move-to-destination + path variants via shared transferCurrentFile; move finishes the viewer. fileOperationUseCase made nullable (shared handler; only image host wires it - fixes Audio/Document/Text/legacy construction).

---

### Step 04.2 - Construct `DestinationButtonsManager` in the standalone host

**Files:** `app_v2/.../ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Inject `GetDestinationsUseCase`. Lazily construct `DestinationButtonsManager(root = binding.root, settingsRepository, getDestinationsUseCase, lifecycleScope, callback, shouldNumberSlots = { false }, slotKeyGlyph = { null })`. Implement the `DestinationButtonsCallback`: `onCopyClicked` → `fileOperations.copyCurrentFileTo(it)`; `onMoveClicked` → `fileOperations.moveCurrentFileTo(it)`; `onCustomPathPickerRequested` → reuse a folder picker for an arbitrary destination (copy/move to a chosen path; if a standalone folder picker does not yet exist, gate the «..» action to a factual toast and capture a follow-up - do not leave an empty handler); `getCurrentResourceId` → `-1L` (research 01, exclude nothing); `onUpdateCommandAvailability` → no-op or local visibility refresh; `isCommandPanelVisible` → return whether the bottom panels should show.

**Verification:**

- `Grep` - `DestinationButtonsManager(` present in `PhotoVideoStandaloneActivity.kt` with `binding.root`.
- `Grep` - `getCurrentResourceId` lambda returns `-1L` (or a named sentinel) in that file.
- `Grep` - `onCopyClicked` and `onMoveClicked` delegate to `fileOperations.copyCurrentFileTo` / `moveCurrentFileTo`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification 3/3 PASS. DestinationButtonsManager(root=binding.root) + callback (getCurrentResourceId=-1L; custom-path via OpenDocumentTree launcher).

---

### Step 04.3 - Populate the grids when a file is shown

**Files:** `app_v2/.../ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> After a media file is loaded (in `observeData()` where `state.mediaFile` becomes available, alongside the existing per-file UI updates), call `destinationButtonsManager.populateDestinationButtons()` once per shown file. Make the bottom panels visible only for the supported types (image/gif/video) and keep them inside insets. Ensure population does not delay first image render - it already runs in a coroutine inside the manager (Rule: lazy optimization).

**Verification:**

- `Grep` - `populateDestinationButtons()` called from `PhotoVideoStandaloneActivity.kt`.
- Build compiles - run `/build`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification PASS. populate called per shown file in observeData; nav-bottom inset moved to bottomPanelsContainer (findViewById, include has no binding field).

---

### Step 04.4 - Confirm copy/move strings comply with the communication policy

**Files:** `app_v2/.../ui/player/helpers/StandaloneFileOperationsHandler.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Verify every user-visible toast added in Step 04.1 reuses an existing string key (copy started/success, move success, failure) and that the message wording satisfies `docs/COMMUNICATION_POLICY.md` §2 (message formula) and §6 (tone checklist). If a move-success / move-started string does not already exist, add it across EN/RU/UK in lockstep via `scripts/utils/set-android-string.ps1 -Action add` (parity-enforced), then run `scripts/check_strings_localized.ps1`.

**Verification:**

- `Grep` - no hardcoded user-facing literals in the new copy/move toasts (all via `R.string.*`).
- If new strings added: `check_strings_localized.ps1` exits 0 for the new key prefix.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-22 - Verification PASS. No new strings: reused msg_copy_started/msg_copy_success/msg_move_started/msg_move_success/error_copy_failed/error_move_failed/select_folder. All toasts via R.string.* (zero hardcoded literals); messages are factual (COMMUNICATION_POLICY §6 OK).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits (except an explicitly captured custom-path follow-up, if parked as a ticket).
- [ ] Manual smoke: open an external image, copy it to a configured destination (screen stays, file appears at target), move it (screen closes, file relocated).
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Standalone image player now offers Copy/Move groups and prints via «Send to..». Final phase records the capability and
regenerates catalog/dev-log.

---

## Rollback Plan

Revert the phase commit(s) - no schema migration; copy/move uses the existing file-operation use-case. The layout includes
(Phase 03) can stay or be reverted independently.
