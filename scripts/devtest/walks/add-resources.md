<sub class="doc-stamp">26.11.23 15:16</sub>

# Add resources - register a folder, see it listed

- requires: none
- flavor: standard (the folder picker is the system's; the quick-folder chips are in the dialog on every flavor)

## Intent

A new user has a folder of photos on the phone and wants the app to manage it. They open the add screen,
pick the folder, add it, and expect to see it on the main screen with the right number of files. Then
they add a second, empty folder to copy into.

`begin` has already launched the app on the main screen with the first-run wizard done. Start the run
with `-Fresh -Yes` on an emulator so no earlier resource is in the list.

## Walk

### 1. add.home - the starting point

- Do: `walk.ps1 step -Name add.home`
- Expect: the main screen of a user with nothing of their own yet; whatever it offers (built-in
  resources, a hint) reads as an invitation to add, and the add button is easy to find.
- Defect if: a spinner that does not end, a counter stuck at zero beside a built-in resource, labels cut
  off, a blank list with no words.

### 2. add.type-picker - choose a kind of resource

- Do: `walk.ps1 step -Name add.type-picker -Tap btnAddResource`
- Expect: a list of resource kinds (local folder, network, cloud..), each with an icon and a plain name.
- Defect if: a kind without a label, two kinds that look the same, anything under the status bar or the
  navigation bar.

### 3. add.local-screen - local folder

- Do: `walk.ps1 step -Name add.local-screen -Tap cardLocalFolder`
- Expect: a screen explaining what a local folder is, with the button that starts the choice.
- Defect if: the screen opens already on something else, jargon a non-technical person cannot read.

### 4. add.folder-dialog - how to choose

- Do: `walk.ps1 step -Name add.folder-dialog -Tap btnAddManually`
- Expect: a sheet or dialog offering ways to pick a folder (quick-folder chips, then the system picker).
  The system-picker button sits below the fold on a phone: capture, then
  `walk.ps1 step -Name add.folder-dialog-scrolled -Scroll down` and judge that the way to the picker can be found.
- Defect if: a button clipped by the sheet edge, a choice whose result is not clear from its label.

### 5. add.picker - the system folder picker

- Do: `walk.ps1 step -Name add.picker -System -Tap btnBrowseWithSAF`
- Expect: the system's picker, in the device language. It may open in whatever folder was last
  browsed; the first breadcrumb is the storage root.

### 6. add.picker-src - inside the walk folder

- Do: three navigation taps, then the frame:
  `walk.ps1 act -TapLabel Download -Exact`, `walk.ps1 act -TapLabel FastMediaSorter_Walk -Exact`,
  `walk.ps1 step -Name add.picker-src -System -TapLabel src -Exact`
- Expect: the picker shows the files of `src` (a grid: scroll to see all nine), and its confirm
  button (use this folder) is enabled, with no "can't use this folder" notice. That notice at the
  storage root is the platform's and is not a finding.
- Defect if: the notice appears for `src`, or the folder lists no files although `fs -Path src` has them.

### 7. add.grant - the access grant

- Do: `walk.ps1 step -Name add.grant -System -Tap android:id/button1`
- Expect: the system's access dialog naming the folder, with an allow button.
- Do next: `walk.ps1 act -TapLabel ALLOW -Exact`

### 8. add.staging - back in the app, before registering

- Do: `walk.ps1 step -Name add.staging`
- Expect: the chosen folder in a list of folders to add, named `src`, with a file count of nine,
  and a button that adds it.
- Defect if: a count of zero or a spinner that does not end, a name nobody could recognise, the add
  button hidden under the keyboard or a bar.
- Wait four seconds and capture again if a count is still moving: `walk.ps1 step -Name add.staging-later -Settle 4000`.

### 9. add.registered - the resource is listed

- Do: `walk.ps1 step -Name add.registered -Tap btnAddToResources -Settle 2500`
- Expect: the main screen listing `src` with its file count and a kind icon; a new resource lands at the
  end of the list, so read the end of the screen:
  `walk.ps1 step -Name add.registered-end -Scroll down -Bottom`. The count matches step 8, and the user is
  told or shown that it worked.
- Defect if: the folder is missing, listed twice, shown with a stale or zero count, or the user is left
  on the add screen with no sign that it worked.

### 10. add.second - an empty folder

- Do: repeat steps 2 to 8 for the `dst` folder (scroll the folder dialog down again before the picker button).
  The picker now reopens inside `src`, so the breadcrumbs `Download` and `FastMediaSorter_Walk` are
  tapped as labels as before, and the route ends in `-TapLabel dst -Exact`. Capture `add.dst-picker` and
  `add.dst-staging`, then `walk.ps1 step -Name add.both-listed -Tap btnAddToResources -Settle 2500` and
  `walk.ps1 step -Name add.both-listed-end -Scroll down -Bottom`
- Also mark dst as a destination (the destination switch on its staging row or in its editor; read the digest for it), so the copy and move choosers offer it. Capture the control as dd.dst-destination.
- Expect: an empty folder is accepted and described as empty - a plain "no files" or a count of zero,
  not a spinner and not an error. The main screen lists both `src` (nine files) and `dst` (empty).
- Defect if: the empty folder is refused without a reason, shown as loading forever, or the two
  resources cannot be told apart.

## Afterwards

`walk.ps1 fs -Path src -Count 9` and `walk.ps1 fs -Path dst -Count 0` - registering a folder must not
have changed a single file.
