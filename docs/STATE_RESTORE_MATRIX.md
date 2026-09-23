# State Restore Matrix

Every user-facing screen and every long operation has a **declared fate** under the four events
that can take its state away. The fate is decided here and then proved, not discovered afterwards
on a device: a cell that says "state preserved" is a claim the instrumented suite
`app_v2/src/androidTest/java/com/sza/fastmediasorter/staterestore/CriticalStateRestoreTest.kt`
executes for the critical rows, and a cell that says "deliberate reset" carries the reason the
reset is correct.

The four events, and what each one actually destroys:

| Event | What survives without help | What is lost |
|---|---|---|
| **Rotation** | The `ViewModel` and everything in it, plus the saved-instance `Bundle`. | View state that no id or adapter restores. |
| **Process death** | Disk: Room, preferences, files under `filesDir`, WorkManager's queue. The saved-instance `Bundle` when the system had time to write it. | The `ViewModel`, every in-memory flow, every un-persisted selection. |
| **Relaunch** | Disk only. The task may be recreated from the launcher with no `Bundle` at all. | Everything the previous task held, including the `Bundle`. |
| **Cancellation** | Whatever the operation committed before it stopped. | The remainder of the work, which is why the end state has to be stated rather than assumed. |

A row's fate is one of three words:

- **preserved** - the state comes back, and the row names the mechanism that carries it.
- **reset** - the state deliberately does not come back, and the row says why that is right.
- **contract** - for a long operation: what the user is left holding when the work stops early.

---

## Critical surfaces

These four rows are the ones the nightly contour executes. They were chosen because each one can
be holding work the user cannot reconstruct: a browse position inside a deep remote tree, a
playback position in a long file, a transfer already halfway through its bytes, and a schedule the
user set once and expects to keep firing.

### Main browse

| Event | Fate | Mechanism, or the reason |
|---|---|---|
| Rotation | **preserved** | `BrowseViewModel` survives the configuration change with its loaded folder, filter and sort intact; `BrowseActivity.onSaveInstanceState` adds the window id and the pending camera-capture target, which live outside the `ViewModel`. |
| Process death | **preserved** | The launch arguments - resource id, window id, initial folder and file path - reach the recreated `ViewModel` through its `SavedStateHandle`, which is restored from the same `Bundle`. The pending camera capture is restored in `onCreate` from `BrowseCameraCaptureManager.saveState`, so a kill while the camera host is foreground does not abandon the captured file. |
| Relaunch | **reset** to the resource root | A relaunch from the launcher carries no `Bundle` and no window id, so the screen opens at the resource the user picked rather than at a folder they have since left. Restoring a path a user never asked for again is worse than opening where they can see where they are. |
| Cancellation | **contract**: a cancelled folder load leaves the previously loaded folder on screen | The load is a `viewModelScope` job; cancelling it does not clear the list that is already rendered, so the screen never blanks because a slow remote listing was abandoned. |

### Player

| Event | Fate | Mechanism, or the reason |
|---|---|---|
| Rotation | **preserved** | `PlayerViewModel` keeps the playlist, index and playback state across the configuration change; `PlayerActivity.onConfigurationChanged` rebinds the orientation layout in place rather than recreating the Activity, so the surface is not torn down. |
| Process death | **preserved** | `PlayerActivity.onSaveInstanceState` writes the window id, and `PlayerViewModel` reads `resourceId`, `initialIndex`, `initialFilePath`, `resumeIsPlaying` and `resumeSlideshowEnabled` from its `SavedStateHandle`, so the recreated player opens on the same file with the same transport intent. |
| Relaunch | **reset** to the resource, then to the resume point the settings allow | A cold relaunch has no `Bundle`; what comes back is whatever the resume feature persisted, not the previous Activity's in-memory position. |
| Cancellation | **contract**: leaving the player releases the media session and the player instance immediately | Rule 18: player resources are released when paused rather than held for a return that may not happen. A half-finished slideshow does not survive the exit. |

### Transfer strip

| Event | Fate | Mechanism, or the reason |
|---|---|---|
| Rotation | **preserved** | The transfer is not owned by the screen at all. `BrowseFileTransferCoordinator` enqueues unique work with WorkManager, so the strip is a view onto work that never notices the rotation. |
| Process death | **preserved** | `BrowseFileTransferRequestStore` writes the active request, the pending queue and the terminal event as JSON under `filesDir`, and WorkManager's own queue is on disk. Both halves outlive the process, which is why the strip can reappear mid-transfer with its progress intact. |
| Relaunch | **preserved**, including a transfer that finished while the app was gone | The terminal event is stored rather than emitted and forgotten: `consumeStoredTerminalEvent` hands it to the next screen that asks, and `markTerminalHandled` keeps it from being shown twice. |
| Cancellation | **contract**: `cancelActiveTransfer` cancels the unique work; files already written stay written and the journal holds what was done | The operation is not transactional across files, so the honest contract is "what completed, completed" plus a journal entry per mutation - which is what makes the result reconcilable and undoable rather than merely stopped. |

### Scheduled operations

| Event | Fate | Mechanism, or the reason |
|---|---|---|
| Rotation | **preserved** | The screen renders `StateFlow`s that `stateIn` republishes from Room on re-collection; nothing the screen holds is the source of truth. |
| Process death | **preserved** | Every schedule is a Room row and every run is a WorkManager job. The screen is a projection of both, so it comes back complete without saving anything of its own. |
| Relaunch | **preserved**, including across a device reboot | `ScheduledOperationsBootReceiver` re-arms the schedules after boot, so a reboot does not silently stop a program the user set once. |
| Cancellation | **contract**: a cancelled run leaves the operations already performed in place and writes its outcome to the operations log | The log is the record the next screen read shows, so a partially completed run is visible rather than indistinguishable from one that never started. |

---

## The long tail

Every other user-facing screen inherits the same three questions, and each one is answered as that
screen is next touched - an unfilled row is a screen nobody has changed since this matrix was
written, not a screen that has been judged and found fine.

Filling a row is part of the change that touches the screen:

1. state the fate for all four events, in the three words above;
2. name the mechanism for a **preserved** cell and the reason for a **reset** one;
3. if the screen is one a user can lose real work on, add it to the critical suite rather than
   leaving the claim untested.

Unfilled today: add resource, settings, welcome and first run, camera OCR, duplicates, statistics,
system info, network monitor, calculator, launcher and its gadgets, broadcast, scheduled-operations
log, tourist info, and the cloud folder pickers.

---

## Rules this matrix serves

- A layout or screen change that alters what a screen holds updates its row in the same change.
  The matrix is append and append-update only: a row is never deleted to make a claim go away.
- A **preserved** claim for a critical row is executable. `CriticalStateRestoreTest` runs in the
  nightly device contour (`.github/workflows/nightly-device-loop.yml`), so a regression is caught
  by a run rather than by a user.
- A cell that cannot be honestly filled is written as a **contract** naming what the user is left
  with. "Unknown" is not a fate.
