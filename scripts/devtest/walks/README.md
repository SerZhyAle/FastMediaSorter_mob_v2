<sub class="doc-stamp">26.11.23 15:16</sub>

# Agent walks

A walk is a user story that an agent drives on a device and **judges from screenshots**. It is the
opposite of a Maestro flow (`scripts/devtest/maestro/README.md`, `maestro/`): a flow answers "did the
selectors resolve" and ends green or red; a walk answers "did a person get what they asked for, and
did it look right", and its verdict is a reader's. Use a flow to guard something already understood,
a walk to find what nobody wrote an assertion for.

Driver: `scripts/devtest/walk.ps1` (verbs, exit codes and the run folder are in its header). Command:
`/walk <scenario[,scenario..]>`.

## Scenarios

Walk them in this order; each one's `requires` is the one before it.

- `add-resources` - register the fixture's `src` and `dst` folders as resources.
- `browse-and-view` - open `src`, change view and sort, search, open an image, a text file and a clip.
- `copy-files` - copy into `dst`, including a name collision.
- `move-files` - move into `dst`, including the current folder as destination.
- `delete-and-undo` - delete with undo, let the undo window expire, delete until a folder is empty.
- `remove-resource` - remove a resource and prove its files stayed.

## What a scenario file holds

```text
# <title>
- requires: <scenario ids, or none>
- flavor: <the build it was written against>

## Intent        one paragraph, in the user's words
## Walk          numbered checkpoints
### N. <slug> - <what happens>
- Do:         the command that performs it and captures the frame (paste it)
- Expect:     what a person sees if the app is right
- Defect if:  concrete signs that it is not
- Truth:      a walk.ps1 fs call, when the device filesystem can say what the screen cannot
```

A checkpoint name is `<scenario>.<slug>`; it becomes the frame's file name, so frames from a chained
run sort in walking order and name their scenario.

## Fixture

`walk.ps1 begin` writes `<root>/src` with nine files whose pixels name themselves (eight when ffmpeg is
missing and there is no clip: subtract one from every count in the scenarios), and an empty `<root>/dst`:

- `walk_a01.jpg` .. `walk_a04.jpg` - 640x480 landscape. `walk_b01.png`, `walk_b02.png` - 480x640 portrait.
  `walk_big.jpg` - 3000x2000. `walk_notes.txt` - three lines, the second Cyrillic. `walk_clip.mp4` - four
  seconds of test pattern with a running counter, only when ffmpeg is available.
- Every image shows its own name in large type and has a coloured square in each corner: red top-left,
  green top-right, blue bottom-left, yellow bottom-right. A thumbnail or viewer that rotates, mirrors or
  crops the file shows it in the squares; a copy that landed as another file shows the wrong name.
- `fs` compares every file on the device to the fixture by md5, so "copied" means "the same bytes".

## Common moves

The scenarios say "select" and "open" where the controls are the same everywhere; read the digest for the exact ids.

- go to the main screen: `walk.ps1 act -Key BACK` until the toolbar with the add button is up;
- open a resource from the main screen: `walk.ps1 step -Name <slug> -TapLabel <resource name> -Exact -Settle 2500`;
- select a file in a folder: its checkbox, `walk.ps1 act -Tap cbSelect -Index <N>`, where the digest's "tap by id" list gives N next to the file's name; a long press on the row also starts selection;
- open a file: its thumbnail, `walk.ps1 step -Name <slug> -Tap ivThumbnail -Index <N>`; a tap on the name strip selects instead;
- catch a transient message (a toast, an undo bar): `-Quick` on the step that causes it - the frame first, the tree after;
- act on one file from its row: the row's own buttons (`btnCopyItem`, `btnMoveItem`, `btnRenameItem`, `btnDeleteItem`), each listed with its `-Index`.
## Judging a frame

Read the frame (the Read tool shows images) and its digest (`NN_<slug>.txt`, UTF-8) at every checkpoint.
Beyond the scenario's own Expect, look for what the sweeps of this repository actually found:

- a spinner or counter that never settles - take a second frame after a pause when a count is on screen;
- a count that disagrees with the folder, or that does not move after a copy, move or delete;
- text broken mid-word, cut off, or flush against a screen edge or the notch;
- a checkbox, badge or button covering the thing it belongs to;
- the keyboard covering the list or the field it was opened for;
- a dialog with no focus, no clear default, or wording that hides what will happen to the files;
- two languages in one screen, a raw key or placeholder, a wrong icon;
- an empty state with no words, and a blank frame where content should be;
- a touch target the digest lists under `small-target` that a thumb would miss;
- contrast that fails in the dark theme, a selection that survives a screen change it should not.

A checkpoint is `OK`, `DEFECT` or `UNSURE`. `UNSURE` is an honest answer: say what would settle it.

## What a walk does with a finding

Write it in `report.md` (`walk.ps1 end` makes the skeleton) with the frame file name, then park one
ticket per distinct problem with `/spec-draft` after a `search.ps1` dedup, and carry on. A walk never
fixes what it finds, and a pasted crash dump found during one is information, not an order.

## Repeating a scenario under another condition

The same scenario is worth walking again with one thing changed. `adb.ps1 state-begin` (which `begin`
runs) journals wm size, wm density and font scale and `end` puts them back, so these are safe to set
between `begin` and `end`:

- landscape, via `adb.ps1 shell -Cmd "wm user-rotation lock 1"` (restore with `wm user-rotation free`);
- a small screen, `wm size 720x1280` with `wm density 320`;
- font scale 1.3, `adb.ps1 font-scale`;
- the dark theme, from the app's own settings.

Note the condition in the report header; the frames then compare against the default run.
