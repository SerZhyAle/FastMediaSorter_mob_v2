<sub class="doc-stamp">26.11.23 15:16</sub>

# Copy files - select, copy into another folder, check the bytes

- requires: add-resources
- flavor: standard

## Intent

A user picks three photos and copies them into another folder, then copies one of them again by mistake.
They expect the originals to stay, the copies to be real and complete, and the second attempt to be
handled without losing anything.

`add-resources` marked `dst` as a destination, so the copy chooser offers it. If the chooser lists only
other folders, `dst` was not marked: record that, then reach it through the chooser's `Select Folder`
button and the system picker (`walk.ps1 step -Name copy.picker -System -Tap btnSelectFolder`, navigate to
`Download/FastMediaSorter_Walk/dst` as in `add-resources`, then `-Tap button1` for the confirm and
`-Tap button1` again for the access grant).

## Walk

Where a Do taps a file by its label, use the README's common moves to select or open it; the app may be on
a browse screen when the scenario starts, so go to the main screen first.

Frames of a transient message (a toast, an undo bar) are taken with `-Quick`, which photographs first and
reads the tree after: a toast is gone within about three seconds, and the tree read alone takes longer.

### 1. copy.select - choose three files

- Do: open `src` (`walk.ps1 act -TapLabel src -Exact`), then select `walk_a01.jpg`, `walk_a02.jpg`,
  `walk_a03.jpg` by their checkboxes (`walk.ps1 act -Tap cbSelect -Index <N>` for the first two, the third
  with `walk.ps1 step -Name copy.selected -Tap cbSelect -Index <N>`; N is read from the digest of the list)
- Expect: selection mode with the three files marked, a header that counts three, and the actions (copy,
  move, rename, delete, share, archive) in a bar within reach. In the grid the checkbox sits on the
  thumbnail's corner by design; judge whether it still leaves the picture readable.
- Defect if: a different count, an action missing, a file selected that was not tapped.

### 2. copy.destination - where to

- Do: `walk.ps1 step -Name copy.destination -Tap actionBrowseCopy`
- Expect: a chooser that says "Copying 3 files from src", offers `dst` (and the other destinations) as
  named buttons, and has a way to choose any other folder and to cancel.
- Defect if: `dst` absent although it was marked as a destination, the chooser not naming how many files or
  from where, a button with no name.

### 3. copy.done - the result

- Do: `walk.ps1 step -Name copy.done -Quick -TapLabel dst -Exact -Settle 300`, then
  `walk.ps1 step -Name copy.done-later -Settle 2500`
- Expect: the first frame shows a sign that the copy ran (a message, a progress), the second the list of
  `src` with the selection cleared and the files still there.
- Defect if: nothing at all tells the user it finished, the files stay selected, a file vanished from `src`.

### 4. copy.truth - what is on the disk

- Do: `walk.ps1 fs -Path dst -Expect walk_a01.jpg,walk_a02.jpg,walk_a03.jpg -Count 3` and
  `walk.ps1 fs -Path src -Count 9`
- Expect: exit 0, every file in `dst` marked `same-as-fixture`, and `src` still whole.
- A `SIZE-DIFFERS` or `BYTES-DIFFER` line is a corrupted copy; a `MISSING` line is a copy that reported
  success and wrote nothing. Both are defects whatever the screen said.

### 5. copy.in-dst - the destination as the user sees it

- Do: `walk.ps1 act -Key BACK` to the main screen, then `walk.ps1 step -Name copy.main -Settle 2500 -Scroll down -Bottom`
  and again `walk.ps1 step -Name copy.main-later -Settle 6000`, then open `dst`:
  `walk.ps1 step -Name copy.dst-list -TapLabel dst -Exact -Settle 2500`
- Expect: the main screen shows three files for `dst` (a count that stays zero is stale), and the list of
  `dst` holds exactly the three with correct thumbnails and readable names. View mode is kept per folder,
  so `dst` may open in the other view than `src`.
- Defect if: the count did not update, a thumbnail that is another file's, a missing file.

### 6. copy.collision - the same file again

- Do: go back to `src` (`walk.ps1 act -Key BACK`, `walk.ps1 act -TapLabel src -Exact`), select
  `walk_a01.jpg` only, `walk.ps1 act -Tap actionBrowseCopy`, then
  `walk.ps1 step -Name copy.collision -Quick -TapLabel dst -Exact -Settle 300`, then
  `walk.ps1 step -Name copy.collision-later -Settle 2500`
- Expect: the app notices that `dst` already has a file of that name and tells the user - either by asking
  (replace, keep both, skip) or by saying what it did ("skipped, already exists"), naming the file.
- Truth: `walk.ps1 fs -Path dst` - `walk_a01.jpg` is still `same-as-fixture` and nothing was lost; a
  kept-both copy appears as `same bytes as walk_a01.jpg (renamed)`.
- Defect if: it overwrote or skipped with no visible word at all (this is what S4718 tracks - reference it,
  do not park a copy), the message does not name the file, a crash.
