<sub class="doc-stamp">26.11.23 15:08</sub>

# Remove resource - take a folder off the list, files stay

- requires: add-resources
- flavor: standard

## Intent

A user no longer wants a folder in the app. They remove it from the list and expect the folder to leave
the list and nothing more: its files stay exactly where they are, and the dialog says so before they
confirm.

## Walk

Where a Do taps a file by its label, use the README's common moves to select or open it; the app may be on a browse screen when the scenario starts, so go to the main screen first.

### 1. remove.editor - open the resource's settings

- Do: first `walk.ps1 fs -Path dst` and `walk.ps1 fs -Path src` and keep the listings, then from the main
  screen `walk.ps1 step -Name remove.editor -TapLabel dst -Exact -LongPress -Settle 1500`
  (a long press on a resource opens its editor)
- Expect: the resource's name in an editable field (`etName`), its type, the options that apply, a save
  button, and somewhere a way to remove it.
- If the removal is not in the editor, it may sit in a per-row overflow menu that a setting hides;
  record that in the report as how a user would find it, and enable it from the settings.
- Defect if: no way to remove a resource can be found at all, a destructive action beside a harmless one
  with nothing to tell them apart.

### 2. remove.confirm - the question

- Do: `walk.ps1 step -Name remove.confirm -TapLabel "<the remove action seen in the digest>"`
- Expect: a dialog that names the resource and says that the files in the folder are not deleted; the
  destructive button is plainly the destructive one and the safe button is the default.
- Defect if: the dialog does not say what happens to the files, the two buttons look the same, the dialog
  has no focus or is clipped.

### 3. remove.after - the result

- Do: `walk.ps1 step -Name remove.after -TapLabel "<the confirm button>" -Settle 2000`
- Expect: the main screen, `dst` gone from the list, `src` still there with its count, no leftover
  space or a half-drawn row where `dst` was.
- Defect if: `dst` still listed, `src` gone as well, the user left on a blank screen.

### 4. remove.truth - the files stayed

- Do: `walk.ps1 fs -Path dst` and `walk.ps1 fs -Path src`, and compare both with the listings taken
  before step 1 (take them there: the earlier scenarios left these folders in a state of their own).
- Expect: `dst` lists the same files as before the removal (the app only forgets a folder); `src` is
  unchanged. A missing file here is the worst defect this walk can find.

### 5. remove.readd - the folder can come back

- Do: repeat the `add-resources` route for `dst` and capture `walk.ps1 step -Name remove.readd`.
- Expect: the folder is accepted again and lists the files that are in it, with no trace of the old
  registration (no duplicate, no stale count).
- Defect if: it is refused as already added, or comes back empty although `fs` lists files.
