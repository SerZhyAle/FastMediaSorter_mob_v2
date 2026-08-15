# S1279 - A deleted or moved file stays in the fullscreen player's navigation list

**Status:** Archived
**Priority:** 80

## 0. Raw capture

Owner, 2026-07-29:

> сортирую видеофайлы в полноэкранном просмотре. предположим нахожусь на файле номер 2, принимаю решение удалить и удаляю, программа переходит к проигрыванию файла номер 3. я нажимаю "назад" и ожидаю файл номер 1. а вместо этого вижу сообщение о том что файл номер 2 удален. файл ещё следует вынуть из списка на отображение и пропускать

## 1. What actually happens

Every step below was traced in the current tree, not inferred.

- The delete removes the file from disk and from the Browse handoff cache, but **not** from `PlayerViewModel.PlayerState.files`, which is the list the player navigates. The list keeps its original size.
- `PlayerFileOpsInitializer.onBeforeDelete` (`ui/player/PlayerFileOpsInitializer.kt:88-99`) stops playback, calls `MediaFilesCacheManager.removeFile`, and advances one step - the optimistic jump to file 3 the owner sees.
- Pressing Previous steps `currentIndex` 2 -> 1, which is still the deleted file's slot. `PlayerState.currentFile` is the derived `files.getOrNull(currentIndex)` (`ui/player/PlayerViewModel.kt:152`), so the player tries to open a path that no longer exists.
- The load fails, and `PlayerActivity.handleMediaLoadErrorAndSkip` (`ui/player/PlayerActivity.kt:1009-1016`) shows `error_loading_media` - "Error loading file, skipping" - then calls `navigateNextFromControl`. That is the message in the report.
- Because that recovery always moves **forward**, the user is bounced back to file 3. Pressing Previous again repeats the same failure, so Previous can never reach file 1 at all; only wrapping around with Next can.

## 2. Why the list is never updated

There are two delete implementations and the UI reaches only the one that does not maintain the list.

- `PlayerDeleteUndoCoordinator.deleteCurrentFile` (`ui/player/helpers/PlayerDeleteUndoCoordinator.kt:139-153`) is the only code in the app that removes the entry and re-points the index. It is reachable from `PlayerViewModel.deleteCurrentFile` (`ui/player/PlayerViewModel.kt:654`), which **has no call site anywhere in `app_v2/src/main/java`**, and from `deleteCurrentFileAndFinish` (line 660), whose single caller is the draw-save editor flow. That variant passes `finishOnSuccess = true` and returns at `PlayerDeleteUndoCoordinator.kt:107-111`, before the removal code.
- Every live trigger instead calls `FileOperationsHandler.deleteCurrentFile` -> `performDelete` (`ui/player/FileOperationsHandler.kt:282-296`), which calls `onBeforeDelete` and enqueues on `PlayerFileOperationQueue`. Neither the enqueue nor the queue's `Succeeded` handler touches `state.files`. The five triggers: command panel (`callbacks/PlayerCommandPanelCallbackImpl.kt:52`), keyboard (`callbacks/PlayerKeyboardCallbackImpl.kt:21`), touch zone (`callbacks/PlayerTouchZoneCallbackImpl.kt:51`), the control-bar button (`helpers/PlayerControlsSetupManager.kt:217`), and the second touch-zone dispatch in `helpers/PlayerNavigationManager.kt:160`.

So this is not "the list was updated and something put the file back" - the list is simply never edited during a queued operation.

## 3. Scope

### 3.1 Move has the same defect

`onBeforeMove` (`ui/player/PlayerFileOpsInitializer.kt:79-86`) is the same shape: stop playback, drop from the handoff cache, advance one step, never touch `state.files`. Moving a file to a destination folder is *how sorting works*, so the owner hits this more often than the delete case he happened to report. Any fix that covers only delete leaves the more common path broken.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** nothing moves on screen. The only visible change is which file appears after a delete or move, and that Previous now reaches the file before the removed one instead of bouncing off it.
- **Accessibility:** unchanged - no new view, no new focus target, no new announcement. The removed toast path simply stops firing for this cause.
- **Flavor scope:** all of standard / lite / photos / legacy / noLegal / vr. Every touched class lives in `src/main/` with no `BuildConfig.IS_*` guard.
- **Localization:** none - no new or changed strings.
- **Validation level:** unit-testable index arithmetic plus an on-device pass through the owner's actual sorting flow, because the risk is a wrong index after a real move, not a compile error.
- **Owner sign-off:** required on device - this changes the highest-traffic flow in the app.
- **Related tickets:** S0242.

## 4. Goals

1. A file removed from the resource by a player-initiated delete or move disappears from the navigation list immediately, so no navigation in either direction can land on it.
2. Navigation that does land on an unopenable file continues in the direction the user asked for, instead of always jumping forward.

**Non-goals:**

- The queued-operation architecture itself (`PlayerFileOperationQueue`, the `MutationJournal` -> Browse Reconciler handoff). Those are correct for what they do; the gap is that nothing feeds the *player's own* list.
- Copy. It does not remove the source, so its list is still accurate.
- The standalone players (`StandaloneFileOperationsHandler`), which have their own handler and need their own check.

## 5. Decisions derivable from the code

Recorded here so the implementation does not have to re-litigate them:

- **Remove optimistically, at `onBeforeDelete` / `onBeforeMove`.** The advance is already optimistic at exactly that point; removing there keeps the index arithmetic and the visible jump in one place, and matches what the unreachable coordinator did.
- **The removal must REPLACE the `navigateNextAfterOperation` call, not join it.** Removing the entry at `currentIndex` leaves that same index pointing at the following file, so the advance happens for free. Keeping the explicit `navigateNext` on top would step a second time and silently skip a file after every sort action - a worse bug than the one being fixed. Wrap to index 0 when the deleted entry was last, which is what `PlayerDeleteUndoCoordinator.kt:149` already does.
- **Nothing extra is needed to load the new file.** `PlayerObserverManager.kt:33-43` keys its UI collector on `Triple(currentIndex, currentFile?.path, ..)`; the path at the unchanged index changes, so the collector fires and reloads on its own.
- **`shuffleIndices` must be remapped on removal.** It holds positions into `files` (`PlayerNavigationCoordinator.kt:102-122`), so dropping an element leaves every higher index off by one and can point past the end. Drop the removed index and decrement the ones above it. The unreachable coordinator never did this, so there is no prior art to copy - it is a gap, not a convention.
- **Re-insert at the original index if the queued operation fails.** The queue already emits `PlayerFileOperationEvent.Failed`, so the state exists to undo the optimistic removal, and a failed delete must not silently shrink the user's list.
- **The recovery skip must follow the direction of travel.** `handleMediaLoadErrorAndSkip` hard-codes forward; that is what turns one phantom entry into a trap the user cannot walk past.

## 5.1 Implementation state (2026-07-29)

- `ui/player/helpers/PlayerNavigationCoordinator.kt` - `dropCurrentFile(sourcePath)` removes the current entry and remembers it; `restoreDroppedFile(sourcePath)` puts it back; `forgetDroppedFile(sourcePath)` discards the record on success; `removeFileByPath(path)` enforces the post-success invariant; `shuffleIndicesWithout()` closes the gap in the shuffle order.
- `ui/player/PlayerViewModel.kt` - three delegates, all keyed by source path (`dropCurrentFileFromList`, `restoreDroppedFile`, `confirmFileRemoved`).
- `ui/player/PlayerFileOpsInitializer.kt` - `onBeforeDelete` and `onBeforeMove` now call `dropFromNavigationList` **instead of** `navigateNextAfterOperation`; the queue's `Succeeded` reconciles by path and `Failed` restores before any early return, so a non-retryable failure still gets its entry back.

The API is path-keyed rather than passing a removal token back to the caller. That started as a workaround - `PlayerViewModel`'s baselined `ImportOrdering` finding is signed by its *entire* import block, so adding one import resurfaces a pre-existing violation and fails the detekt gate. Naming no new type there avoids the import, and the result is the better design anyway: the record of what was removed now lives with the list it describes instead of being duplicated in the file-ops initializer.
- `ui/player/helpers/PlayerNavigationManager.kt` - records the direction of travel in the two private navigation funnels and owns `skipAfterLoadError()`, which steps that way.
- `ui/player/PlayerActivity.kt` - `handleMediaLoadErrorAndSkip` now just calls `skipAfterLoadError()`. The direction branch started out inline here and moved to the manager for two reasons: `PlayerActivity` does not import Timber, so the probe could not live there without touching that file's import block (same baseline trap as `PlayerViewModel`), and per Rule 3 the branch was Activity logic that belonged in a manager anyway.

`navigateNextAfterOperation` deliberately survives: copy does not remove its source, so its two call sites still need a real advance.

Known limitation, accepted rather than hidden: `RemovedFile.index` is captured at removal time, so if several operations are queued back to back and an early one fails, the restored file can land a position or two off. The index is clamped, so this is a cosmetic ordering slip in a rare failure path, not a crash or a lost file.

## 6. Open for the owner

- When the last remaining file is deleted the player currently finishes the Activity. Removing entries properly makes that path reachable far more often - confirm finishing is still what should happen, rather than returning to Browse with a message.

## 7. Risks

- **The optimistic removal changes index arithmetic on every sort action**, which is the owner's highest-traffic flow. A wrong index here is worse than the bug being fixed - it would show the wrong file after every move.
- **No test coverage exists** on any class in this chain (`PlayerDeleteUndoCoordinator`, `PlayerNavigationCoordinator`, `PlayerFileOperationQueue`, `FileOperationsHandler`, `PlayerFileOpsInitializer`), so a regression here would not be caught mechanically.

## 8. Related

- **S0242** - introduced the queue + mutation-journal path that replaced the coordinator's direct delete, which is where the list maintenance was lost.
- Dead weight to resolve with the fix (Rule 20): `PlayerViewModel.deleteCurrentFile` has no caller, and with it `PlayerDeleteUndoCoordinator`'s undo-snackbar branch is unreachable - meaning player deletes currently offer no undo at all. Confirm whether undo is meant to come back before deleting the branch.

## Last Audit

### Implementation closure - 2026-07-29

- P0/P1: none found. The zero-count branch runs in the existing queued-event collector and only restores the already-captured source entry; it neither changes player ownership nor starts I/O.
- `PlayerFileOpsInitializer.wasSourceRemoved()` now distinguishes a completed move/delete (`processedCount > 0`) from the queue's skipped-result shape (`processedCount == 0`). A skipped source operation restores the optimistic player entry and does not emit a Browse mutation or a false success toast.
- Static validation: scoped detekt passed for `PlayerFileOpsInitializer.kt`; standard Kotlin compile passed via `a.ps1 fk`.
- Runtime validation: standard-debug APK built, installed, and launched on `emulator-5554`; MainActivity was top-resumed and the 200-line app crash scan returned zero FATAL/Exception/ANR lines.
- Remaining device check: repeat the duplicate-destination player move from this ticket's existing fixture. Expected: after `SUCCESS (with skips) {count=0, skipped=1}`, the source file remains navigable in Player and remains in Browse without Refresh.

### Manual - device test 2026-07-29, emulator-5554 (Android 17 / SDK 37, tablet), standard debug

Verdict: the reported defect is fixed, but the fix opens a mirror-image gap on skipped moves. Evidence: `temp/S1279/evidence.txt`, probes in `temp/S1279/probes_and_ops.log`.

Passing:

- Delete on file 2 shows file 3, and Previous then lands on file 1 - `f02.png (2/5)` -> `f03.png (2/4)` -> `f01.png (1/4)`, with no "Error loading file, skipping" toast.
- Move on file 2 behaves identically - `f03.png (2/4)` -> `f04.png (2/3)` -> `f01.png (1/3)`.
- Previous walks the whole folder without meeting the removed entry: `f05` -> `f04` -> `f03` -> `f01`.
- Every delete and move landed on the immediate neighbour, so nothing is silently skipped.
- Deleting the last remaining file still finishes the Activity and returns to Browse.
- With shuffle on, deleting a file left the order working - five steps visited `a05` -> `a01` -> `a02` -> `a05` with no blank and no reappearance of the deleted file.
- `S1279: skip after load error` never fired in the whole session, which is the direct evidence that navigation never landed on a dead entry.

Not exercised: the failure-restore path. No lever on this emulator produced a real `PlayerFileOperationEvent.Failed` - `chmod` does not stick on FUSE `/sdcard`, the read-only resource flag returns at `FileOperationsHandler.kt:334` before any optimistic drop, a destination under `/sdcard/Android/data/<other package>` is refused by resource validation, and there is no network share. `S1279: failed op` fired 0 times, correctly.

Failing: a move the operation **skips** still drops the file from the navigation list.

- Trigger: moving a file to a destination that already holds a file of the same name. The player's quick "Move to.." path offers no overwrite dialog.
- Observed: `S1279: dropped=true for .../a02.mp3`, then `executeMove: SKIPPED - a02.mp3 (already exists in S1279_Dest)`, `moved: 0, skipped: 1`, and `SUCCESS (with skips) {count=0, skipped=1}` - so the queue reports `Succeeded`, `confirmFileRemoved` keeps the entry out, and `restoreDroppedFile` never runs.
- Result: the file is still in the source folder on disk but is absent from the player's list and from Browse until a manual Refresh restores it. No data loss.
- Reproduced twice - once with a directory occupying the destination name, once with a plain duplicate file.
- This sits inside this ticket rather than a new one: the optimistic drop plus confirm-on-success is the mechanism introduced here, and section 5 enumerates `Succeeded` and `Failed` but not "succeeded with skips". Before this change the list was never maintained, so a skipped move left the entry alone.

### Manual - device test 2026-07-30, emulator-5556 (Android 13 / SDK 33, phone 1080x2400), standard debug

Verdict: PASS. The skipped-move gap the 2026-07-29 run recorded as FAIL no longer reproduces, and the successful-move path is unharmed. Evidence: `temp/S1279/evidence_5556.txt`, app session log `temp/S1279/session_5556.log`.

Build under test: standard-debug APK built 2026-07-29 23:27, which postdates every S1279-touched source file; the twelve `app_v2/src` files newer than the APK all sit outside the player chain, so the code exercised equals the working tree.

Passing - the skipped move keeps the file:

- Collision mid-list (`f02.png`, index 1 of 4): overlay went `f02.png (2/4)` -> `f03.png (3/4)` with the denominator unchanged, so the list never shrank. Log chain: `dropCurrentFile: removed 1, 4 -> 3, now 1` -> `executeMove: SKIPPED - f02.png (already exists in S1279_Dst)` -> `moved: 0, skipped: 1` -> `SUCCESS (with skips) {count=0, skipped=1}` -> `succeeded .. (processed=0)` -> `restoreDroppedFile: re-inserted f02.png at 1`.
- Collision at index 0, the wrap case (`f01.png`, first of 5): same shape, `dropCurrentFile: removed 0, 5 -> 4, now 0` then `restoreDroppedFile: re-inserted f01.png at 0`.
- Both repros: Previous lands on the restored file and it loads; `error_loading_media` never fires in the whole session.
- Both repros: Browse reached with the player's Back button and no Refresh tap still lists every source file, and the source file is untouched on disk while the pre-existing destination file of the same name is untouched too.
- No false success toast and no Browse mutation for a skip - `showSuccessToast` and `recordQueuedOperationMutation` both sit behind `wasSourceRemoved`, which returns false on `processedCount == 0`.

Passing - regression check on the happy path, run because the fix rewrites the predicate that governs it: a non-colliding move of `f03.png` still removes the entry (`f03.png (2/4)` -> `f04.png (2/3)`, list 4 -> 3), logs `MutationJournal: record Move seq=1` with no `restoreDroppedFile`, and Browse without Refresh drops `f03.png`.

Not exercised: the `Failed` restore path, for the same reason as the 2026-07-29 run - no emulator lever produces a genuine `PlayerFileOperationEvent.Failed`. Section 6's last-file question is an owner decision, not a device check.

Rule 2 violation found and left in place: the ticket is `BlockNeedUserTest` but no live probe exists. Greps over `app_v2/src` return 13 `S1279` hits, all KDoc, and zero `Timber.d("S1279` calls - the three probes the 2026-07-29 audit quotes are gone. This run substituted the permanent `dropCurrentFile` / `restoreDroppedFile` log lines, which print the same list arithmetic. Re-adding a probe was outside this run's mandate.

Tooling note for the next device pass: `screencap` yields a 0-byte PNG on this AVD, and app logs do not reach logcat on this build - they land in `/sdcard/Android/data/com.sza.fastmediasorter.debug/files/logs/`.
