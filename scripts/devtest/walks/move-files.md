<sub class="doc-stamp">26.11.23 15:16</sub>

# Move files - select, move into another folder, the list follows

- requires: add-resources
- flavor: standard

## Intent

A user sorts their photos: two portrait pictures go to another folder. They expect the files to leave
the folder they were in, arrive intact in the other, and the list to follow at once without jumping.

Counts below assume `copy-files` ran first (`src` has nine, `dst` has three or four). Without it `dst`
starts empty; use `-Expect` and `-Absent` rather than totals.

## Walk

Where a Do taps a file by its label, use the README's common moves to select or open it; the app may be on a browse screen when the scenario starts, so go to the main screen first.

### 1. move.select - choose two files

- Do: `walk.ps1 act -TapLabel src -Exact`, then `walk.ps1 act -TapLabel walk_b01.png -LongPress` and
  `walk.ps1 step -Name move.selected -TapLabel walk_b02.png`
- Expect: two files marked, a count of two, the move action reachable.
- Defect if: the wrong count, a mark covering a thumbnail.

### 2. move.destination - where to

- Do: `walk.ps1 step -Name move.destination -Tap actionBrowseMove`
- Expect: the same kind of chooser as for copy, with `dst` in it.
- Defect if: wording that does not say the files will leave this folder.

### 3. move.done - the list follows

- Do: `walk.ps1 step -Name move.done -Quick -TapLabel dst -Exact -Settle 300`, then
  `walk.ps1 step -Name move.done-later -Settle 2500`
- Expect: the two files are gone from the list of `src` by the first frame or soon after, the list does
  not jump back to the top, the rest are where they were, and the selection is cleared. The second frame
  matches the first.
- Defect if: the two rows stay until a reload, the list scrolls to the top, the frames differ (the list
  is still changing), a spinner is left.

### 4. move.truth - what is on the disk

- Do: `walk.ps1 fs -Path src -Absent walk_b01.png,walk_b02.png -Count 7` and
  `walk.ps1 fs -Path dst -Expect walk_b01.png,walk_b02.png`
- Expect: exit 0; both files are in `dst` marked `same-as-fixture` and gone from `src`.
- A file in both places is a copy that reported itself as a move; a file in neither is lost data.

### 5. move.same-folder - the folder it is already in

- Do: select `walk_a04.jpg` in `src`, `walk.ps1 step -Name move.same-folder -Tap actionBrowseMove`, and look at the
  chooser: if `src` is offered, choose it with `walk.ps1 step -Name move.same-folder-result -TapLabel src -Exact`.
- Expect: either `src` is not offered, or choosing it is refused with a sentence, or the file stays
  untouched.
- Truth: `walk.ps1 fs -Path src -Expect walk_a04.jpg` and a count of seven.
- Defect if: the file disappears, is duplicated, or the app crashes.

### 6. move.main - the counts

- Do: `walk.ps1 act -Key BACK` to the main screen, then `walk.ps1 step -Name move.main -Settle 2500`
- Expect: `src` shows seven files, `dst` shows what `fs -Path dst` listed.
- Defect if: a count that disagrees with the disk.
