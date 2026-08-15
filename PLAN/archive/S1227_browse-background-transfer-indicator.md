# S1227 - A backgrounded transfer has no in-app place to watch its progress

**Status:** Archived
**Priority:** 70

## 0. Raw capture

Owner, 2026-07-27, Quest 3 session:

> "отправил копирование в бэкграунд, но теперь непонятно где отслеживать прогресс"

## 1. Problem

Sending a running copy/move to the background dismisses its progress dialog permanently. From that moment the only place the operation is visible is the system foreground notification - which on a Quest lives in the headset's own notification panel, awkward to reach mid-session and easy to miss entirely.

Browse itself shows nothing: no bar, no chip, no badge, and no way to bring the dialog back.

## 2. Mechanism

`ui/browse/managers/BrowseFileOperationsManager.kt`:

- The active-transfer collector re-shows the dialog only under `if (activeTransferDialog == null && !modalDetachedByUser)`.
- `modalDetachedByUser` is set when the owner backgrounds the dialog and is never cleared while the transfer runs, so the dialog can never return for that operation.

That guard is correct on its own - the owner asked for the modal to go away. What is missing is the non-modal replacement.

## 3. Goals

1. While a transfer is active and its dialog is detached, Browse shows a persistent, non-modal indicator with operation, percent and current file.
2. Activating the indicator re-opens the progress dialog (clearing `modalDetachedByUser` for that operation only).
3. The indicator disappears on terminal state, leaving the existing result handling untouched.

**Non-goals:**

- Changing what the foreground notification shows.
- Any progress/speed computation change - that is **S1225**.
- Backgrounding from the *player* rather than Browse - that is **S1224**.

### 3.3 Owner inputs (Approval gate)

- **Placement:** bottom strip in the `tvFilterWarning` style, not a top chip (owner, 2026-07-28).
- **Coexistence with the filter strip:** both shown at once, stacked, no priority rule (owner, 2026-07-28).
- **Focus and D-pad:** focusable and clickable; resolved from Rule 16, not asked.
- **Localization:** EN/RU/UK via `browse_transfer_indicator` and `browse_transfer_indicator_action`.
- **Flavors:** all - Browse transfers are not flavor-gated.
- **Related tickets:** S1224, S1225, S1226.

## 4. Placement - decide before implementing

The repo already has a thin bottom status strip in this screen, `tvFilterWarning`, constrained to the bottom of the parent. Reusing that pattern is the cheapest path and keeps the visual language consistent.

Owner decisions (2026-07-28):

- **Placement: bottom strip** in the `tvFilterWarning` style, not a top chip. Matches the spec-recommended default.
- **Coexistence: stack both strips.** A filter warning and an active transfer are shown at the same time, one above the other - no priority rule, no suppression. Implication: the bottom area must grow to two rows without pushing the list content off-screen, and the RecyclerView bottom inset (Rule 17) accounts for both rows when both are visible.

Resolved without the owner (2026-07-28, spec-next loop):

- Focusable for D-pad/TV - YES, not a question: Rule 16 mandates keyboard/D-pad/mouse support for interactive UI, and the indicator is activatable (re-opens the dialog), so it gets `focusable`/`clickable` + focus-chain wiring like the neighbouring controls.

## 5. Constraints

- **Layouts:** `activity_browse.xml` exists in `layout/`, `layout-land/` and `layout-w600dp/` - all three must be edited together (Rule 11).
- **Localization:** EN/RU/UK via `scripts/utils/set-android-string.ps1 -Action add`, parity-enforced.
- **Flavor:** all - Browse transfers are not flavor-gated.
- **Accessibility:** content description plus a non-colour cue for state.

## 5.1 Findings that changed the shape of the fix

- **The Browse filter strip is dead.** `BrowseStateUiUpdater.updateFilterBadge` sets `tvFilterWarning.isVisible = false` unconditionally and nothing sets it true; the live filter warning lives in `activity_main.xml`. So the stacking the owner asked for is implemented and correct, but never triggers today. Parked as **S1272**.
- **Consequence for insets.** Because the filter strip never shows, `layoutOperations` is currently the lowest visible bottom strip and correctly receives the navigation-bar inset. Inserting a second strip below it without changing `BrowseEdgeToEdgeHelper` would have handed the inset to a hidden view and pushed the new strip under the nav bar. The helper now gives the inset to the lowest *visible* strip only, which also removes the pre-existing double-padding whenever two strips are shown together.
- **Percent had no single owner.** The dialog computed it inline. The strip must never disagree with the dialog for the same operation, so the formula moved to `transferOverallPercent` unchanged - the second consumer S1225 was told to expect.

## 6. Implementation state (2026-07-29)

- `res/layout{,-land,-w600dp}/activity_browse.xml` - `tvTransferIndicator` inserted between the operations bar and the filter strip; the operations bar now constrains to it.
- `ui/browse/transfer/TransferProgressPercent.kt` - new, shared percent used by the dialog and the strip.
- `ui/browse/managers/BrowseFileOperationsManager.kt` - mirrors the running operation type and last snapshot, publishes the strip label only while the modal is detached, exposes `reattachTransferDialog()`.
- `ui/browse/managers/BrowseEdgeToEdgeHelper.kt` - `applyBottomInsets` replaces `applyListBottomInset`; the lowest visible strip owns the navigation-bar inset.
- `ui/browse/managers/BrowseManagerInitializer.kt` - renders the strip, wraps the label in a content description, wires the click to `reattachTransferDialog()`.
- `ui/dialog/FileOperationProgressDialog.kt` - consumes the shared percent instead of its own expression.

## 6.1 Device test, 2026-07-29 (emulator-5554, API 36, standard debug)

Proven:

- Browse inflates the modified layout in portrait and landscape without crashing. `BrowseEdgeToEdgeHelper` dereferences `binding.tvTransferIndicator` on every bottom-bar visibility change, so a missing view in either configuration would have thrown - it did not.
- The new inset-owner logic runs against a real non-zero inset: `S1227: strip insets nav=64 indicatorShown=false`, repeatedly, correctly reporting the strip hidden while nothing is detached.
- The copy/background handoff itself still works - "Перенос продолжается в фоне" toast observed.

Not proven, and why: the emulator copies ~700 MB of local files in under two seconds, so a backgrounded transfer terminates before the strip can be photographed or tapped. Three attempts, including a 20-file / 700 MB batch with the background tap scripted at t+2 s, all completed first. The fixture cannot produce a transfer long enough; the resource under test is capped at 20 indexed files and shell-created files are not picked up by its MediaStore-backed listing.

Still needs a real device with a slow source - a network resource (SMB/FTP/cloud) is the natural one, since per-file latency there is orders of magnitude above local storage:

1. Start a copy large enough to run for a minute, then press "В фон".
2. The bottom strip appears with operation, percent and current file name, and updates as the copy proceeds.
3. Tapping the strip re-opens the progress dialog at the current position; the strip disappears while the dialog is up.
4. On completion the strip disappears and the usual result message appears.
5. With the strip visible, the file list's last row stays reachable above the navigation bar, and selecting files (which shows the operations bar above the strip) leaves no empty band inside it.

## 7. Related

- **S1224** `player-copy-move-background` - the sibling gap: from the video player a transfer cannot be backgrounded at all. Same feature area, opposite end.
- **S1226** - the throttle that governs how often this indicator would refresh.
- **S1225** - the unified progress component this indicator should consume once it exists; building the indicator first is deliberate, so the abstraction is designed against a real second consumer.

---

## Remote log pass 2026-08-01/02

Device SM-S731B (Galaxy S25 FE), Android 16 / API 36, noLegal debug 2.60.7302.058. Bundle imported
via `/newlog` from `logs/fastmediasorter_20260729_162305.log` .. `logs/fastmediasorter_20260801_183450.log`.
This is a probe-firing record, not an acceptance verdict - a log proves the code path ran, not that
the screen looked right.

- Probe fired 712 times, every single time `strip insets nav=N indicatorShown=false`. The transfer indicator was never visible in any of the five sessions.
- That is NOT a failure of this ticket - the scenario was never reached. The transfers in this bundle were started from the destination dialog and killed by a Back press (see S1362), and nothing in the log shows the owner ever sent a transfer to the background, which is the precondition for the strip.
- The status note asked for a real device with a slow source. The bundle finally has one - cloud uploads over a mobile link taking 5 to 60 seconds per file - so the scenario is now reproducible on this device. It still has to be run deliberately: start a cloud transfer, send the dialog to the background, and confirm the strip appears and reopens the dialog on tap.
