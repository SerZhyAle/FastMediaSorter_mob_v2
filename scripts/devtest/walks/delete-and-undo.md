<sub class="doc-stamp">26.11.23 15:16</sub>

# Delete and undo - remove files, take one back, empty a folder

- requires: add-resources
- flavor: standard (soft delete with an undo bar is the default; a permanent-delete setting is a repeat, see the README)

## Intent

A user deletes a photo, changes their mind and takes it back, deletes it for good, then deletes several
files at once until a folder is empty. They expect the dialogs to say what will happen, undo to work,
and an emptied folder to say it is empty.

Counts assume the state after `copy-files` and `move-files`: `src` seven files, `dst` five or more. Work
from what `walk.ps1 fs -Path src` and `-Path dst` list at the start and adjust the counts.

## Walk

Where a Do taps a file by its label, use the README's common moves to select or open it; the app may be on a browse screen when the scenario starts, so go to the main screen first.

### 1. delete.confirm - delete one file

- Do: `walk.ps1 act -TapLabel src -Exact`, `walk.ps1 act -TapLabel walk_a04.jpg -LongPress`, then
  `walk.ps1 step -Name delete.confirm -Tap actionBrowseDelete`
- Expect: either a confirmation that names the file and says whether it can be taken back, or at once an
  undo bar with the file gone from the list.
- Defect if: a confirmation that does not name what is deleted, wording that hides whether it is
  permanent, a delete with no feedback.

### 2. delete.undo-bar - the way back

- Do: if a confirmation was shown, confirm it; then `walk.ps1 step -Name delete.undo-bar -Settle 500`
- Expect: `walk_a04.jpg` is gone from the list, and a bar with an undo button (`btnUndo`) is on screen
  and readable, not behind a bar or the keyboard.
- Truth: `walk.ps1 fs -Path src -Recurse` - note where `walk_a04.jpg` went (a trash folder, or gone).
- Defect if: the row is still listed, no undo offered, the undo bar covers the last list row for good.

### 3. delete.undone - take it back

- Do: `walk.ps1 step -Name delete.undone -Tap btnUndo -Settle 1500`
- Expect: `walk_a04.jpg` is back in the list, in its old place, with its thumbnail.
- Truth: `walk.ps1 fs -Path src -Expect walk_a04.jpg` - `same-as-fixture`.
- Defect if: the row returns without its thumbnail, in another place, or the file is not on the disk.

### 4. delete.expired - let the undo window run out

- Do: select `walk_a04.jpg` again and delete it, then `walk.ps1 step -Name delete.expired -Settle 8000`
- Expect: the undo bar has gone by itself, the list is steady.
- Truth: `walk.ps1 fs -Path src -Absent walk_a04.jpg` and the recursive listing, to see whether the
  file is in a trash folder or gone.
- Defect if: the bar never goes away, the file is neither deleted nor recoverable.

### 5. delete.several - delete two at once

- Do: `walk.ps1 act -TapLabel walk_a01.jpg -LongPress`, then
  `walk.ps1 step -Name delete.several-selected -TapLabel walk_a02.jpg`, then
  `walk.ps1 step -Name delete.several -Quick -Tap actionBrowseDelete -Settle 300`
- Expect: wording that counts correctly ("2 files", or the language's plural form), both rows gone.
- Truth: `walk.ps1 fs -Path src -Absent walk_a01.jpg,walk_a02.jpg`.
- Defect if: a wrong count in the wording, one row left behind, a file deleted that was not selected.

### 6. delete.to-empty - delete everything in dst

- Do: open `dst` (`walk.ps1 act -Key BACK`, `walk.ps1 act -TapLabel dst -Exact`), long-press the first file,
  select all that remain (tap each, or use select-all if the digest shows one), delete them, then
  `walk.ps1 step -Name delete.empty -Settle 8000`
- Expect: an empty folder is described in words ("no files", or similar) with the way back clear, not a
  blank list and not a spinner.
- Truth: `walk.ps1 fs -Path dst -Count 0`.
- Defect if: a blank frame, a spinner, rows still listed that the disk no longer has, a crash.

### 7. delete.main - the counts

- Do: `walk.ps1 act -Key BACK` to the main screen, then `walk.ps1 step -Name delete.main -Settle 2500`
- Expect: counts that match `walk.ps1 fs` for both folders; `dst` shown as empty in words.
- Defect if: any count that disagrees with the disk.
