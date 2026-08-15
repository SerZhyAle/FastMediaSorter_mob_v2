# Phase 03 - Dialog height binding

**Strategic spec:** [`../S0638_playback-control-adaptive-sections.md`](../S0638_playback-control-adaptive-sections.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Bound the dialog height to the screen from the fragment by feeding `playbackResizableArea.maxHeightPx`, so the selector stays fully visible and only the resizable region scrolls. Add the single `BlockNeedUserTest` debug tag as the final action.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (both layouts expose `playbackResizableArea`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | Modified | ≤ 720 |

> File is >500 LOC - Step 03.1 backs it up before edits.

---

## Steps

### Step 03.1 - Backup the fragment

**Files:** `temp/` (backup copy)
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` to `temp/` with a timestamp suffix (file is >500 LOC).

**Verification:**

- `Glob` - a timestamped `temp/PlaybackControlDialogFragment*.kt` copy exists.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Backed up fragment to `temp/PlaybackControlDialogFragment_*.kt`.

---

### Step 03.2 - Bound dialog height in onStart

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `onStart()`, keep the existing width assignment (95% of display width) and `DialogKeyboardDelegate` call. Set the window height to `WRAP_CONTENT` (let the bounded child cap the total). Compute the screen-relative cap once: `val maxDialogH = (resources.displayMetrics.heightPixels * 0.95f).roundToInt()`. Set an initial conservative cap synchronously so the first frame is already bounded (e.g. `binding.playbackResizableArea.maxHeightPx = maxDialogH`), then refine after first layout via a single `binding.root.doOnPreDraw { .. }` (from `androidx.core.view`) pass: compute `chrome = binding.root.height - binding.playbackResizableArea.height` (title + selector strip + paddings) and set `binding.playbackResizableArea.maxHeightPx = (maxDialogH - chrome).coerceAtLeast(<a sane floor, e.g. 160dp in px>)`. The `maxHeightPx` setter already no-ops when unchanged, so the pre-draw pass settles in one reflow without looping. Do not collect Flows here; no business logic beyond sizing. Keep `android.util.Log` out - this file uses Timber.

**Verification:**

- `Grep` - `playbackResizableArea.maxHeightPx` present in the fragment.
- `Grep` - `doOnPreDraw` present.
- `Grep` - `heightPixels` present (cap derived from screen height).
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - `onStart` now caps `playbackResizableArea.maxHeightPx` from screen height minus chrome via `doOnPreDraw`; added `androidx.core.view.doOnPreDraw` import. Verification 4/4 PASS.

---

### Step 03.3 - Add the BlockNeedUserTest debug tag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> As the final code action of the implementation (the ticket enters `BlockNeedUserTest` right after this phase), add exactly one tag `Timber.d("S0638: playback control dialog shown - adaptive pivot selector (rail portrait / strip landscape), height-bounded")` in `onViewCreated`, next to the existing `Timber.d("S0619: ..")` line. Do NOT remove or alter the S0619 tag (that ticket is independently in `BlockNeedUserTest`). Add no other `S0638:` tags anywhere.

**Verification:**

- `Grep` - `Timber.d("S0638:` matches exactly once in the file.
- `Grep` - `Timber.d("S0619:` still present (untouched).

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Added single `Timber.d("S0638: ..")` tag in `onViewCreated`; S0619 tag untouched. `.\a.ps1 fc` BUILD SUCCESSFUL (code + tag validated in one build).

---

## Phase Done Criteria

- [ ] Steps 03.1-03.3 are `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Exactly one `Timber.d("S0638:` tag exists across the codebase (this file).
- [ ] Dev log entry added for the fragment via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Implementation complete: adaptive pivot selector + screen-bounded height wired end to end. Phase 04 regenerates the catalog (new `MaxHeightLinearLayout`) and finishes the dev log; `/spec-dev` then flips the ticket to `BlockNeedUserTest`.

---

## Rollback Plan

Restore the fragment from the Step 03.1 backup and revert Phase 01/02. No persisted state or migration involved.
