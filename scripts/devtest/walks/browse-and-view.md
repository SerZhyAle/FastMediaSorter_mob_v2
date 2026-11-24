<sub class="doc-stamp">26.11.23 23:53</sub>

# Browse and view - open a folder, look at its files

- requires: add-resources
- flavor: standard

## Intent

A user opens their photo folder, looks around it - another view, another order, a search - and opens a
picture, a note and a clip. They expect every file to look like itself and the way back to be where they
left it.

Opening a file: in the file list a tap on the name strip selects the file; the thumbnail opens it. The
digest's "tap by id" section lists every thumbnail as `ivThumbnail -Index N near '<name>'`, so a file is
opened with `walk.ps1 step -Name <slug> -Tap ivThumbnail -Index N`.

## Walk

### 1. browse.open - open src

- Do: `walk.ps1 step -Name browse.open -TapLabel src -Exact -Settle 2500`, then once more
  `walk.ps1 step -Name browse.open-again -Settle 3000`
- Expect: the file list of `src` with nine files (A01..A04, B01, B02, BIG, the note, the clip), each with a
  thumbnail that shows its own name, readable names and sizes, and a header whose count agrees with the
  disk (`walk.ps1 fs -Path src -Count 9`). Scroll to see the rest: `walk.ps1 step -Name browse.open-end -Scroll down -Bottom`.
- Thumbnails of the images are cropped to the centre in the list view, so orientation is judged in the
  viewer (step 5) and in the grid view (step 2), where the corner squares show.
- Defect if: a count that disagrees with the disk or with the count shown when the folder was added, a
  file that the disk has and the list does not, names broken mid-word, a name or size drawn over the
  thumbnail, grey or empty thumbnails after the settle, the two frames differing (rows that jump).

### 2. browse.view-mode - another view

- Do: `walk.ps1 step -Name browse.view-mode -Tap btnToggleView`
- Expect: the list changes to the other view; the same files, nothing lost; names readable; in the grid
  the thumbnails show their corner squares in the right places (red top-left, green top-right, blue
  bottom-left, yellow bottom-right).
- Defect if: a different number of files, thumbnails that never load, a toggle whose label does not say
  what it will do, a rotated or mirrored thumbnail.

### 3. browse.sort - another order, and back

- Do: `walk.ps1 step -Name browse.sort-menu -Tap btnSort`, then
  `walk.ps1 step -Name browse.sorted -TapLabel "Name (Z-A)" -Exact`, then restore with
  `walk.ps1 act -Tap btnSort` and `walk.ps1 step -Name browse.sort-restored -TapLabel "Name (A-Z)" -Exact`
- Expect: a menu with clear option names and the current one marked; after Z-A the files are in the
  opposite order; after A-Z they are back as at the start.
- Defect if: the order does not follow the option, the menu is clipped, the current option is not marked.

### 4. browse.search - narrow by name

- Do: `walk.ps1 step -Name browse.search-open -Tap btnSearch`, then
  `walk.ps1 step -Name browse.search-a0 -Field etSearchQuery -Text a0`
- Expect: the list narrows live to the four `walk_a0*` files; the keyboard does not cover the rows that
  remain or the filter banner.
- Then close and reopen the search so the field is empty (typing over an existing query is unreliable):
  `walk.ps1 act -Tap btnSearchClose`, `walk.ps1 act -Tap btnSearch`,
  `walk.ps1 step -Name browse.search-none -Field etSearchQuery -Text zzzq` - expect words saying that nothing
  matched the search (not words that read as an empty folder). `walk.ps1 step -Name browse.search-closed -Tap btnSearchClose` -
  expect the full list back and the keyboard gone.
- Defect if: the list does not narrow, the no-match state describes an empty folder, a banner hidden
  under the keyboard, closing search leaves the filter active.

### 5. browse.image - open a picture

- Do: `walk.ps1 step -Name browse.image -Tap ivThumbnail -Index <the one near 'walk_a01.jpg'> -Settle 1500`. A first-run
  hint may cover the picture: read it as a user would, then dismiss it with its own id from the digest and
  capture `walk.ps1 step -Name browse.image-clear`.
- Expect: the viewer shows A01 whole, uncropped, corner squares in their places, with a title or counter
  and a way back; controls do not cover the picture permanently.
- Then: swipe `walk.ps1 step -Name browse.image-swipe -Scroll left` and note whether it pages; the previous
  and next buttons in the digest page too (`walk.ps1 step -Name browse.image-next -Tap <the next button>`).
- Defect if: the picture is cropped, rotated or mirrored, a black frame, a hint that cannot be dismissed,
  a swipe or a button that does nothing, an error line in the log summary on every open.

### 6. browse.big - a large picture

- Do: back to the list (`walk.ps1 act -Key BACK`), then open the BIG file with
  `walk.ps1 step -Name browse.big -Tap ivThumbnail -Index <the one near 'walk_big.jpg'> -Settle 3000`
- Expect: the picture is sharp within the settle, filling the screen with the right proportions.
- Defect if: still a blur or a spinner after three seconds, a crash or a log error.

### 7. browse.text - the note

- Do: `walk.ps1 act -Key BACK`, then `walk.ps1 step -Name browse.text -Tap ivThumbnail -Index <the one near 'walk_notes.txt'> -Settle 1500`
- Expect: the three lines, the second reading "Line two: Привет, мир" - Cyrillic rendered, not squares or
  question marks.
- Defect if: the Cyrillic is broken, the text is cut off or too small to read.

### 8. browse.clip - the clip

- Do: `walk.ps1 act -Key BACK`, open the clip with
  `walk.ps1 step -Name browse.clip -Tap ivThumbnail -Index <the one near 'walk_clip.mp4'> -Settle 2000`, then
  `walk.ps1 step -Name browse.clip-later -Settle 1500`
- Expect: the test pattern is playing - the counter on it differs between the two frames - and the
  controls can be reached.
- If the clip is on the disk (`fs -Path src` lists `walk_clip.mp4`) but not in the list, check the
  configured video size and type filters first. Record a listing defect only if the clip passes them;
  otherwise record the filter exclusion and skip the rest of this step.
- Defect if: the counter is identical in both frames (frozen), a black frame, no controls.

### 9. browse.back - the way back

- Do: `walk.ps1 act -Key BACK` until the list is up, then `walk.ps1 step -Name browse.back`
- Expect: the list in the same view and order as before the viewer, nothing selected.
- Defect if: the view or order reset, a file still looks selected, the app closed instead of going back.

## Afterwards

`walk.ps1 fs -Path src -Count 9` - looking must not have touched a file.
