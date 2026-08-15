# Phase 03 - Share stream link

**Strategic spec:** [`../S0631_video-stream-player-view.md`](../S0631_video-stream-player-view.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

For a live video stream, the «Send to..» action shares the stream URL as plain text (a link) instead of
trying to materialize/download the live stream as a file.

> Mechanism: `ShareableContent.text` is the plain-text payload for text-capable receivers
> (`ShareTarget.requiresLocalFile == false`). `SendToMenuManager` only materializes when
> `requiresMaterialization` (uris empty AND `sourcePath` set). A stream payload with `uris=emptyList()`,
> `text=url`, `sourcePath=null` carries the URL and never downloads.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`PlayerState.isLiveVideoStream` exists).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt` | Modified | ≤ 430 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToMenuManager.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/share/handlers/SystemShareTargetHandler.kt` | Modified | ≤ 60 |

---

## Steps

### Step 03.1 - Build a text-link payload for a stream in `buildShareableContent`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `buildShareableContent()`, before the existing local/remote branch, add: when
> `viewModel.state.value.isLiveVideoStream` is true, return
> `ShareableContent(uris = emptyList(), mime = "text/plain", mediaType = file.type, text = file.path, displayName = file.name, mediaFile = null, sourcePath = null)`.
> WHY `sourcePath = null`: it keeps `requiresMaterialization` false so the dispatch never tries to download
> the live stream; `text = file.path` is the stream URL that text receivers send via `ACTION_SEND`/`EXTRA_TEXT`.
> Leave the existing non-stream path unchanged.

**Verification:**

- `Grep` - `isLiveVideoStream` matches in `PlayerCommandPanelCallbackImpl.kt`.
- `Grep` - the stream branch sets `text = file.path` and `sourcePath = null`.

**Status:** `[x]` done

---

### Step 03.2 - Restrict the menu to text-capable receivers and share `content.text`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToMenuManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/share/handlers/SystemShareTargetHandler.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Two changes so a text-only stream payload produces a working, non-dead menu:
> (1) In `SendToMenuManager.show(..)` and `buildOverflowSubMenu(..)`, when the content is text-only
> (`content.uris.isEmpty() && content.sourcePath == null && content.text != null`), include only targets
> with `requiresLocalFile == false` (text-capable receivers - messengers, email, clipboard, system share),
> so file-only receivers do not appear as dead entries for a stream.
> (2) In `SystemShareTargetHandler`, when `content.uris` is empty and `content.text != null`, share the
> text via `SystemShareInvoker.invoke(activity, "text/plain", content.text!!)` (the chooser carries the URL)
> instead of `invokeFiles(..)`. Keep the existing file path for normal content.
> If a distinct user-visible label is needed for the link entry, add it trilingually via
> `scripts/utils/set-android-string.ps1 -Action add` (EN/RU/UK) and pass `docs/COMMUNICATION_POLICY.md` §2/§6;
> otherwise reuse existing receiver labels (no new string).

**Verification:**

- `Grep` - `requiresLocalFile == false` (or equivalent text-only filter) present in `SendToMenuManager.kt`.
- `Grep` - `content.text` consumed in `SystemShareTargetHandler.kt` with a `text/plain` system-share path.
- If a new string was added: `Grep` the key in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (three hits). Otherwise note "no new string" in the dev log.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] If a new string was added: `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<key>"` exits 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

«Send to..» now shares the stream URL as a link for live video streams. Final phase records the capability
and regenerates catalog/dev-log.

---

## Rollback Plan

Revert phase commit(s) - the stream branch is additive and guarded by `isLiveVideoStream`; normal-file
sharing is untouched, so revert restores the prior (file-materializing) behavior for streams.
