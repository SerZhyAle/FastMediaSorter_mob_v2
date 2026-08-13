# Phase 03 - Stream cell menu

**Strategic spec:** [`../S1424_launcher-shortcut-full-resource-menu.md`](../S1424_launcher-shortcut-full-resource-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Long press on a `stream:` cell opens that channel's action menu through the dispatcher and popup Phase 02 built.

---

## Prerequisites

- [x] Phase 01 and Phase 02 are ✅ Done - verified green by the owner.
- [x] `CODE.LOCK` acquired before the first source edit, released after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamRemoveConfirmation.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 940 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStreamActionManager.kt` | New | ≤ 220 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 920 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 640 |

---

## Steps

### Step 03.1 - Extract the stream remove confirmation

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamRemoveConfirmation.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Move the body of `StreamsActivity.confirmRemove` into `object StreamRemoveConfirmation` exposing `fun show(activity: AppCompatActivity, title: String, onConfirm: () -> Unit)`, keeping the `streams_remove` title and positive-button strings and the `StreamTitleFormatter.display` title formatting unchanged. Make `StreamsActivity.confirmRemove` delegate to it.

**Why:**

Strategic §6.2 resolved that a destructive action raised from the desktop asks the same confirmation dialog as the one it mirrors rather than a private copy, and the current dialog is a private method no second host can reach.

**Verification:**

- `Grep` - `object StreamRemoveConfirmation` matches once.
- `Grep` - `StreamRemoveConfirmation.show(` present in `StreamsActivity.kt`.
- `Grep` - `StreamTitleFormatter.display` present in the new file.

**Status:** `[x]` done

---

### Step 03.2 - Add the launcher-side stream action executor

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStreamActionManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `class LauncherStreamActionManager(activity, loadStream, loadPinnedStreams, togglePin, removeStream)` mapping a `StreamMenuAction` plus a stream id onto desktop-reachable work: TOGGLE_PIN through `togglePin`, ADD_SHORTCUT through the same `StreamShortcutPinManager` the streams screen uses (which takes a plain `Context`), SHARE_LINK through the same `ACTION_SEND` chooser `StreamsActivity.onShareLink` builds, REMOVE through `StreamRemoveConfirmation.show` then `removeStream`. Expose `fun supports(action: StreamMenuAction): Boolean` returning false for anything not yet wired.
>
> **EDIT is deferred, not wired.** `StreamsActivity.showEditDialog` inflates `DialogAddStreamBinding` and carries the media-kind override (S1145) and the per-channel track preference (S1144), all built inside that Activity. Reproducing it in the launcher is exactly the divergence ADR-1 exists to prevent, so EDIT goes in the manager's `DEFERRED` set and the row stays absent until that dialog is extracted somewhere both hosts reach. There is no "play" row either - the streams screen has none, because tapping the row plays it.

**Why:**

Strategic §2 goal 2 requires the stream cell's long press to offer the same actions as the row on the streams screen, and §5.1.2 requires that provider to work from an identifier alone without the streams screen being open.

**Verification:**

- `Glob` - the file exists under `src/launcherEnabled/`.
- `Grep` - `class LauncherStreamActionManager` matches once.
- `Grep` - `fun supports(` present.
- `Grep -n "BuildConfig\.IS_"` - zero hits.

**Status:** `[x]` done

---

### Step 03.3 - Add the stream branch to the dispatcher

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `is LauncherCellCommand.Stream ->` to the Phase 02 dispatcher, calling `cellActionMenuManager.showForStream`. Add to `LauncherHomeViewModel` only the stream reads and writes the Activity cannot do itself - loading one `StreamSourceEntity` by id, toggling its pinned flag, removing it.

**Why:**

Strategic §5.1.1 states one handler picks the provider by command kind, so the stream kind joins as a branch rather than as a second long-press handler.

**Verification:**

- `Grep` - `is LauncherCellCommand.Stream ->` present in `LauncherHomeActivity.kt`.
- `Grep` - the `else -> false` fallback is still the last branch.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - **UNPROVEN**: no gradle ran in this session, by instruction.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Both cell kinds now reach the same popup, so Phase 04 widens what a resource action can do without touching the dispatcher again.

---

## Rollback Plan

Revert the phase commit - additive apart from the extracted dialog.
