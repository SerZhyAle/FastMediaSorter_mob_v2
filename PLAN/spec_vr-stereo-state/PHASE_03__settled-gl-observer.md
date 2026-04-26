# Phase 03 — Settled GL Observer

**Strategic spec:** [`../spec_vr-stereo-state.md`](../spec_vr-stereo-state.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-04-27
**Completed:** 2026-04-27

---

## Objective

Prevent the GL pipeline from restarting on the transient `AUTO` emission that occurs during
`resetStereoModeForNewFile`. After the reset, `_stereoMode` emits `AUTO` (maps to `MONO`/no-crop),
then emits the final detected or overridden mode. Filtering out `AUTO` in the video GL observer
eliminates the intermediate unnecessary GL pipeline reset; the final mode is applied exactly once.

**Why safe:** The `AUTO` value is emitted only during `resetStereoModeForNewFile`. User-triggered
`setStereoMode(AUTO)` internally resolves to the detected mode immediately — `_stereoMode` never
exposes `AUTO` to collectors as a persistent settled value from a user action.

**Scope:** Standard (non-VR) video GL observer only. The image re-render observer intentionally
retains `AUTO` handling so that navigating from a stereo image to a non-stereo image correctly
resets the crop.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt` | Modified | ≤ 770 |

> File is 760 lines — >500 → backup required before editing.

---

## Steps

### Step 3.1 — Backup `PlayerManagerInitializer.kt`

**Files:** none modified — backup only
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup:
>
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt" `
>     "temp/PlayerManagerInitializer_$ts.kt.bak"
> ```

**Verification:**

- `Glob` — `temp/PlayerManagerInitializer_*.kt.bak` returns at least one match.

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 1/1 PASS. Backup: PlayerManagerInitializer_20260427_000208.kt.bak.

---

### Step 3.2 — Filter `AUTO` from video GL stereoMode collector

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> Locate the coroutine block in `PlayerManagerInitializer` that collects `stereoMode` and calls
> `videoPlayerManager.applyStereoEffect(mode)` (approximately line 467–470):
>
> ```kotlin
> // BEFORE:
> activity.lifecycleScope.launch {
>     activity.viewModel.stereoMode.collect { mode ->
>         activity.videoPlayerManager.applyStereoEffect(mode)
>     }
> }
>
> // AFTER:
> activity.lifecycleScope.launch {
>     activity.viewModel.stereoMode
>         .filter { it != StereoMode.AUTO }
>         .collect { mode ->
>             activity.videoPlayerManager.applyStereoEffect(mode)
>         }
> }
> ```
>
> Add `import kotlinx.coroutines.flow.filter` to the import block if not already present.
> Do NOT apply the same filter to the second stereoMode collector (image re-render observer,
> approximately lines 475–481) — that observer must react to `AUTO` to clear stereo crop
> when navigating to non-stereo images.

**Verification:**

- `Grep` — `.filter { it != StereoMode.AUTO }` present exactly once in `PlayerManagerInitializer.kt`.
- `Grep` — `applyStereoEffect` present in `PlayerManagerInitializer.kt` (collector body unchanged).
- `Grep` — `import kotlinx.coroutines.flow.filter` OR `import kotlinx.coroutines.flow.*` present in `PlayerManagerInitializer.kt`.
- Confirm the second `stereoMode.collect` block (for image re-render) does NOT have the `.filter` operator: open file and verify visually that only one of the two collectors has `.filter { it != StereoMode.AUTO }`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerManagerInitializer.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-27 — Verification 5/5 PASS. Files: PlayerManagerInitializer.kt (+2 LOC filter + import). Second collector confirmed without filter.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-04-27.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for `PlayerManagerInitializer.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- The video GL observer now skips the transient `AUTO` emission on file change; `applyStereoEffect` fires exactly once with the final resolved mode.
- Image re-render observer behaviour is unchanged — it still reacts to `AUTO` → clears stereo crop.
- Final phase is docs + catalog cleanup.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed. Backup in `temp/` allows manual recovery.
