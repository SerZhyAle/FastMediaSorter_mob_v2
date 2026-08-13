# Phase 01 — Adapter Play-Button Condition

**Strategic spec:** [`../S0105_inline-audio-playback-in-browse.md`](../S0105_inline-audio-playback-in-browse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Change `MediaFileAdapter` so that the inline play button is visible for any audio file in any resource (not just Audio-Library resources), and respects the `hideGridActionButtons` setting.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/` directory exists.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt` | Modified | ≤ 1103 |

> File is 1103 lines — backup step required before edits (Step 01.1).

---

## Steps

### Step 01.1 — Backup MediaFileAdapter.kt

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `MediaFileAdapter.kt` in `temp/` before any edits:
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt" `
>           "temp/MediaFileAdapter_$ts.kt"
> ```

**Verification:**

- `Glob` — `temp/MediaFileAdapter_*.kt` matches at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 1/1 PASS. Files: temp/MediaFileAdapter_20260506_153803.kt. Dev log N/A (backup only).

---

### Step 01.2 — Change `bind()` play-button condition

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `ListViewHolder.bind()`, locate the block starting with the comment `// Inline play button: visible only in Audio Only mode, not for folders` (around line 826). Replace it with the following logic. The condition changes from `isAudioOnlyMode && !isFolder` to "audio file AND grid-action-buttons not suppressed":
>
> ```kotlin
> // Inline play button: visible for any audio file, suppressed by hideGridActionButtons in grid mode
> val showPlayButton = file.type == MediaType.AUDIO && !isFolder && !(isGridMode && hideGridActionButtons)
> if (showPlayButton) {
>     updatePlaybackState(file)
> } else {
>     btnPlayInline.isVisible = false
>     btnPlayInline.isEnabled = true
>     applyInlineHighlight(false)
> }
> ```
>
> Remove the three lines that previously set `tvFileInfo.text = AdapterFileInfoFormatter.buildFileInfo(file)` from this else branch — that value is already correctly set earlier in `bind()`.

**Verification:**

- `Grep` — `file.type == MediaType.AUDIO && !isFolder && !(isGridMode && hideGridActionButtons)` present in `MediaFileAdapter.kt`.
- `Grep` — `isAudioOnlyMode && !isFolder` does NOT appear in `bind()` context (confirm old condition removed from this block; `isAudioOnlyMode` may still appear elsewhere in the file).

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 2/2 PASS. Files: MediaFileAdapter.kt (bind block replaced). Dev log deferred to phase end.

---

### Step 01.3 — Fix `updatePlaybackState()` visibility and info-text guard

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/MediaFileAdapter.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `ListViewHolder.updatePlaybackState(file: MediaFile)`, make two changes:
>
> **Change 1 — Visibility condition** (replaces lines 624–625):
> Replace:
> ```kotlin
> binding.btnPlayInline.isVisible = isAudioOnlyMode
> if (!isAudioOnlyMode) return
> ```
> With:
> ```kotlin
> val shouldShow = file.type == MediaType.AUDIO && !file.isDirectory && !(isGridMode && hideGridActionButtons)
> binding.btnPlayInline.isVisible = shouldShow
> if (!shouldShow) return
> ```
>
> **Change 2 — Guard `tvFileInfo` update to audio-only mode only** (around the `buildAudioDetailLine` block):
> Wrap the existing `tvFileInfo` text update block with `if (isAudioOnlyMode)`:
> ```kotlin
> if (isAudioOnlyMode) {
>     val baseInfo = AdapterFileInfoFormatter.buildAudioDetailLine(file)
>     if (isDownloading) {
>         val progress = state.downloadProgressPercent.coerceIn(0, 100)
>         binding.tvFileInfo.text = if (progress > 0) "$baseInfo • Cache $progress%" else "$baseInfo • Cache..."
>     } else {
>         binding.tvFileInfo.text = baseInfo
>     }
> }
> ```
>
> **Change 3 — Add S0105 debug tag** at the entry of `updatePlaybackState`, after the `shouldShow` guard, to mark the new non-audio-only code path:
> ```kotlin
> if (!isAudioOnlyMode && shouldShow) {
>     Timber.d("S0105: showing inline play button for audio file '${file.name}' in mixed resource")
> }
> ```
> Place this block immediately after `if (!shouldShow) return`.

**Verification:**

- `Grep` — `file.type == MediaType.AUDIO && !file.isDirectory && !(isGridMode && hideGridActionButtons)` present in `updatePlaybackState`.
- `Grep` — `if (isAudioOnlyMode)` wraps the `buildAudioDetailLine` call in `updatePlaybackState`.
- `Grep` — `Timber.d("S0105:` present in `MediaFileAdapter.kt`.
- `Grep` — `Log.d(` returns zero hits in `MediaFileAdapter.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-06 — Verification 4/4 PASS. Files: MediaFileAdapter.kt (updatePlaybackState rewritten). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `MediaFileAdapter.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `MediaFileAdapter` now shows the play button on any audio file card in any resource.
- `hideGridActionButtons = true` in grid mode suppresses the play button, same as copy/move/rename/delete.
- `updatePlaybackState` no longer overwrites `tvFileInfo` in non-audio-only mode.
- The S0105 debug tag fires when the play button becomes visible for a mixed-resource audio file.

---

## Rollback Plan

Restore `temp/MediaFileAdapter_<timestamp>.kt` over the modified file. Revert phase commit(s).
