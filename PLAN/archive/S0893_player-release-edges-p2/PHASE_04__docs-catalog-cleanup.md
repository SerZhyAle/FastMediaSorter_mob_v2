# Phase 04 - docs-catalog-cleanup

**Strategic spec:** [`../S0893_player-release-edges-p2.md`](../S0893_player-release-edges-p2.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Insert on-device verification probes (this ticket's behavior change is genuinely on-device-only verifiable - background/foreground during active playback), run a final full build across all 12 touched files together, sync catalog/dev-log, and set the closing status.

---

## Prerequisites

- [ ] Phases 01, 02, 03 all ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| (probe tags land in the 12 files already touched by Phases 01-03 - no new files) | Modified | n/a |

---

## Steps

### Step 04.1 - Insert BlockNeedUserTest debug probes at each changed flow entry

**Files:** all `.kt` files touched by Phases 01-03
**Depends on:** Phase 01, 02, 03 all done

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags": this ticket's release/recreate behavior needs on-device confirmation (a build cannot prove a video actually resumes correctly after real backgrounding). Insert exactly one `Timber.d("S0893: <entry-point description>")` at each of the following changed-flow entry points, as the **last** code edits before this phase's build:
>
> - `AudioEmptyStateController.onStop(owner)` - one tag.
> - `AudioEmptyStateController.onStart(owner)` - one tag (only on the branch that actually calls `showVideo()`).
> - `VideoPlayerLifecycleHelper.onStop()` - one tag (only on the branch that actually releases).
> - `VideoPlayerLifecycleHelper.onStart()` - one tag (only on the branch that actually recreates).
> - `PhotoVideoStandaloneActivity.onStart()` - one tag (only on the branch that actually rebuilds).
> - `StandalonePlayerActivity.onStart()` - one tag (only on the branch that actually rebuilds).
>
> These may reuse the `Timber.d("VideoPlayerManager: onStop - ..")`-style lines already added in Phase 02 Step 02.2 by prefixing `S0893: ` - do not add a second, duplicate log line at the same spot. Six tags total (one per changed flow entry, not one per modified line, per CLAUDE.md).

**Verification:**

- `Grep` - `Timber.d\("S0893:` matches exactly 6 times across `app_v2/src/main`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 1/1 PASS. Tags were written proactively during Phases 01-03 as the WHY-comments documenting each new branch (not a separate insertion pass) - confirmed exactly 6 via grep, already covered by Phase 03's combined build (no redundant second build needed, per spec-dev.md "never insert tags after the build").

---

### Step 04.2 - Final build, dev log, catalog sync, status transition

**Files:** n/a (mechanical closure)
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `.\a.ps1 fc` once more (validates all 12 files + the 6 new probe tags in one pass - no further build needed after this). On PASS:
> - Batch dev-log all 12 touched files plus this ticket's 5 `PLAN/` files via `close-and-log.ps1`.
> - Flip journal status to `BlockNeedUserTest` with `-StatusNote` describing exactly what to verify on device: background the app (home button or recents-swipe) while a video is actively playing in (a) the main in-app player (`PlayerActivity`) and (b) `PhotoVideoStandaloneActivity` (open a video via "Open with.."), then return to foreground - confirm playback resumes at the same position without a black screen, crash, or ANR; also confirm the audio-empty-state muted background video (audio track with no cover art) survives the same background/foreground cycle.
> - Regenerate `dev/CATALOG/app_v2.jsonl` (touched-file timestamps).
> - Probe a connected device via `scripts/devtest/device-ready.ps1`; if online, auto-chain `/spec-test-device`-equivalent verification before falling back to leaving `BlockNeedUserTest` for a later sweep.

**Verification:**

- `.\a.ps1 fc` exit 0, `BUILD SUCCESSFUL` present in output.
- Journal status is `BlockNeedUserTest` with a non-empty `-StatusNote` (or `Verified` if a device was online and the sweep passed).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` PASS (single build validates code + all 6 probe tags together).
- [ ] Dev log entry added for every file in "Files Touched" across all 4 phases via `post-change.ps1`/`close-and-log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Ticket lands in `BlockNeedUserTest` (or `Verified` if a device was online and the sweep passed inline) pending device confirmation of the release/recreate behavior; `/spec-check` removes the 6 probe tags on the eventual `Verified` transition.

---

## Rollback Plan

Low-risk: probe tags are trivially removable (`Grep` + delete) without touching any behavior.
