# Phase 03 - Info dialog

**Strategic spec:** [`../S1474_stream-about-channel.md`](../S1474_stream-about-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Build the window itself - layout, dialog class, the three states of the measured group, the copy action - so that both later phases only have to open it.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] The owner's placement ruling in strategic §3.3 is read: a new separate window, not a branch of the file-properties dialog.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_stream_info.xml` | New | ≤ 180 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/StreamInfoDialog.kt` | New | ≤ 320 |

> Landscape: no `res/layout-land/dialog_stream_info.xml` is created. The window is a single vertically scrolling list of label/value rows inside a `ScrollView`, so the portrait layout is already correct in landscape; CLAUDE.md Rule 11 binds only where a landscape counterpart exists. If review finds the rows cramped in landscape, the counterpart is added in Phase 06, not improvised here.

---

## Steps

### Step 03.1 - Add the dialog layout

**Files:** `app_v2/src/main/res/layout/dialog_stream_info.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the layout: a title, a scrolling container holding three group blocks each with a heading and a vertical container the rows are added into at runtime, a state line for the measured group, and an action pair at the bottom - copy and close. Style the copy button `Widget.FastMediaSorter.Button.DialogConfirm` and the close button `Widget.FastMediaSorter.Button.DialogCancel` per CLAUDE.md §11. Use theme attributes or colour resources for every colour; no hardcoded hex. Make every focusable element reachable by keyboard and D-pad with explicit `nextFocus*` where the default order is wrong.

**Why:**

Strategic §11 criterion 10 requires the window to be fully usable by keyboard and to close with the usual key, and §3.2 requires the waiting and failure states to be distinguishable by text and not only by colour.

**Verification:**

- `Glob` - the layout file exists.
- `Grep` - `="#` returns zero hits in the file.
- `Grep` - `Widget.FastMediaSorter.Button.DialogConfirm` and `Widget.FastMediaSorter.Button.DialogCancel` both present.
- `Grep` - `ScrollView|NestedScrollView` present.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `dialog_stream_info.xml` created (122 LOC, budget 180): title, a weighted `ScrollView` holding the three headings with their runtime row containers, a "not in the list" line, the measured state line, and the confirm/cancel action pair in the named styles. Zero `="#` - every colour is `?attr/`, and every text size a theme text appearance. `.\a.ps1 fr` exit 0.
- Focus: the scroll view and both buttons are focusable with explicit `nextFocusDown` / `nextFocusRight` / `nextFocusLeft`, so a D-pad reaches the actions from the list rather than falling out of the window.
- The waiting and failure states are a text line, not a colour - strategic §3.2 requires them to be readable without colour vision.
- No row layout file was added: the rows are built in code into the three containers, which keeps the change inside this phase's declared Files Touched instead of quietly adding a seventh file.

---

### Step 03.2 - Add the dialog and render the stored groups

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/StreamInfoDialog.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `StreamInfoDialog` as a `Dialog`, mirroring how `FileInfoDialog` is constructed and shown. It takes the channel entity - or its url alone when the channel is not in the catalog - and renders the two stored groups immediately from `StreamPropertiesFormatter` by inflating one label/value row per property. When only a url is known, render the channel group with the address and the "not in the list" line in place of the stored rows, and keep the measured group fully active.

**Why:**

Strategic §11 criterion 3 requires the window to open immediately with every stored property, and the owner ruled on 2026-08-07 that a channel absent from the list still opens the window without its stored part rather than losing the menu item.

**Verification:**

- `Glob` - `.../ui/dialog/StreamInfoDialog.kt` exists.
- `Grep` - `class StreamInfoDialog` matches once.
- `Grep` - a constructor path accepting a url without an entity is present.
- `Grep` - `StreamPropertiesFormatter` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `StreamInfoDialog.kt` created as a `Dialog`, constructed like `FileInfoDialog` with view binding. `entity` is nullable and `url` always present, which is the "url without an entity" path: a null entity shows the address row plus the "not in the list" line, hides the catalog heading, and leaves the measured group fully active. `StreamPropertiesFormatter` is referenced three times.
- The stored half renders in `onCreate`, before any measurement starts, so the reader never waits on the network for data already on the device.
- Bug caught at compile: `private val resources` shadowed `Dialog.getResources()` inside every `TextView.apply { .. }` block, where `resources` resolves to the view's own `android.content.res.Resources`. Renamed to `infoResources` rather than qualifying each call - the shadowing would have bitten again on the next edit.

---

### Step 03.3 - Drive the measurement and its three states

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/StreamInfoDialog.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> On show, put the measured group into its working state and start the measurement in a scope owned by the dialog: read a running engine when one was handed in, otherwise measure the url through `StreamFormatProbeManager`. On completion render the measured rows, rendering each absent value as "unavailable"; on failure or deadline render the "could not be measured" line while leaving the stored groups untouched. Cancel the job and cancel the probe in `dismiss`/`onStop`, and never release an engine the dialog did not open. Collect any flow with the lifecycle-safe helper, never a bare `launch { collect {} }`.

**Why:**

Strategic §11 criteria 5, 7 and 8 require the working state on open, a readable window for an unreachable channel, and a close that stops the work and leaves no engine running; §7 records that every existing background stream path has a timeout, a cancellation and an off-thread teardown precisely because the native engine blocks on stop.

**Verification:**

- `Grep` - `dismiss|onStop` contains a cancel call.
- `Grep` - `GlobalScope` returns zero hits.
- `Grep` - `lifecycleScope\.launch \{[^}]*collect` returns zero hits.
- `Grep` - the "could not be measured" string key is referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. `onStart` puts the measured group into its working state and launches into a scope the dialog owns; `dismiss()` and `onStop()` both call `stopMeasurement()`, which cancels the job and the probe, and `onDetachedFromWindow` cancels the scope itself. `GlobalScope` 0, bare `lifecycleScope.launch { collect }` 0 - there is no flow collection here at all, the measurement is a single suspend call. `stream_info_state_unavailable` referenced twice.
- The engine handed in is read through `probe.readFrom`, never released - the dialog releases only the engine `probe.measure` opened, which it closes in its own `finally`.
- Partial answers are rendered rather than discarded: the failure line shows only when every measured field came back unavailable, so a channel that reported audio but no video still shows its audio.

---

### Step 03.4 - Add the copy action

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/StreamInfoDialog.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Wire the copy button to put the whole readout - labels and values, stored and measured, in the order shown - on the clipboard as plain text through the formatter's text function, then confirm with the copied-confirmation string. Copy whatever the window currently shows, including the "unavailable" and "could not be measured" lines.

**Why:**

Strategic decision 5 exists because the reason to read these numbers is usually to tell someone else - a bug report or a message to the catalog maintainer - and copying them by hand out of a modal window is the friction that stops that happening.

**Verification:**

- `Grep` - `ClipboardManager` referenced.
- `Grep` - the copy-confirmation string key referenced.
- `Grep` - the formatter's text function called from the click handler.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. `ClipboardManager` obtained through `getSystemService<ClipboardManager>()`, `stream_info_copied` referenced once, and `formatter.asPlainText` called from `copyReadout`, which is the copy button's click handler.
- What is copied is `shownGroups` - the list the window actually rendered, appended to as the measured group arrives - so copying before the measurement finishes yields the stored half rather than a promise, and copying after includes the "not reported" lines exactly as shown.
- The copied confirmation is a toast rather than `announceForAccessibility`, which the compiler flags as deprecated; a toast is read aloud by the screen reader anyway, so one call serves both readers and Rule 7 stays satisfied on a file this ticket touched.

---

### Step 03.5 - Keep the dialog free of business logic

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/StreamInfoDialog.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Review the finished class against the layering rule: value formatting, code-to-word mapping and clipboard text belong to the formatter, format extraction belongs to the probe manager, and the dialog holds only inflation, state switching and event wiring. Move anything that drifted. Keep the file under its line budget; if it approaches it, extract a rendering helper under `ui/dialog/helpers/` rather than growing the dialog.

**Why:**

CLAUDE.md requires UI classes to carry zero business logic and caps file size, and strategic §7 warns that this window's failure modes are the ones other stream paths already learned - keeping the logic where it is already tested is how that lesson is kept.

**Verification:**

- `Grep` - no `when (.*mediaKind|when (.*sourceOrigin` mapping code inside the dialog.
- File length under the budget in "Files Touched".
- Run `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1` - passes for the touched files.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. No `when (mediaKind)` or `when (sourceOrigin)` mapping exists in the dialog - both live in `StreamPropertiesFormatter`, where they are unit-tested. The file is 243 LOC against a 320 budget, so no rendering helper had to be extracted. `assert-neuroslop.ps1 -Gate -ChangedFiles <both files>` exit 0, PASS on every dimension.
- Layering as it stands: value formatting and code-to-word mapping in the formatter, format extraction in the probe manager, and the dialog holding inflation, state switching and event wiring. The one piece of formatting that lives here is `DialogStreamInfoResources`, which is the platform adapter the formatter takes as a collaborator - it renders a date and a unit, and holds no rule about which value goes where.
- Its correct parameter names are `-Gate` and `-ChangedFiles` (an array), not the `-ScopeToFile` the closure facade takes; recorded because the step names the script without them.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0 and `.\a.ps1 fr` exit 0, 2026-08-08.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in both files.
- [x] Dev log entry added via `post-change.ps1`, verdict `post-change: PASS`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2592 records.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.
- [x] `screenshot deferred (no device)` - S1338's UI gate. The placement decision it also demands is on record and quoted in this phase's Prerequisites: strategic §3.3, owner ruling 2026-08-07, «Новое отдельное окно».

## Phase-boundary audit (2026-08-08)

- Layer 1 - architecture and budgets. Dialog 243 LOC of 320, layout 122 of 180. No mapping logic in the view; the one formatting class here is the platform adapter the formatter takes as a collaborator.
- Layer 2 - lifecycle and coroutines. The dialog owns a `SupervisorJob` scope on `Dispatchers.Main.immediate`. Three exits are covered rather than one: `dismiss()` and `onStop()` cancel the job and the probe, and `onDetachedFromWindow` cancels the scope. No flow is collected, so the lifecycle-safe-collection rule has nothing to bind to here.
- Layer 3 - listener and engine ownership. Two click listeners on views the dialog owns and that die with it. The measurement never releases an engine it did not open: the handed-in engine goes only through `readFrom`, which does not touch its state. The probe manager releases only the engine it built.
- Layer 4 - Room. `StreamSourceEntity` is read from the caller's hand, never queried or written here.
- P2, fixed in this phase: `private val resources` shadowed `Dialog.getResources()` inside `TextView.apply` blocks - caught by the compiler rather than by review, and renamed to `infoResources` so the trap cannot recur on the next edit. A deprecated `announceForAccessibility` call was removed in favour of the toast, which the screen reader already speaks.

---

## Handoff Notes to Next Phase

The window is openable in three shapes: with an entity, with an entity plus a running engine, and with a bare url. Phases 04 and 05 pick a shape and nothing more.

---

## Rollback Plan

Revert phase commit(s) - the dialog is unreferenced until Phase 04, so no user-facing surface changes.
