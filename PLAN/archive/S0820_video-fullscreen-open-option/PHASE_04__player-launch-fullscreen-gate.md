# Phase 04 - Player-launch fullscreen gate

**Strategic spec:** [`../S0820_video-fullscreen-open-option.md`](../S0820_video-fullscreen-open-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none (Phase 05 waits on all)
**Steps done:** 3 / 3
**Started:** 2026-07-02
**Completed:** 2026-07-02

---

## Objective

Gate the actual "open straight into fullscreen" behavior on `openVideoInFullscreen`, computed in Browse (where the tapped file's `MediaType` is already known) and honored once at `PlayerActivity`'s first launch - generalizing the existing S0694 stream-fullscreen mechanism instead of duplicating it. This is the phase that makes strategic §2 goals 2 and 4, and ADR-1/ADR-2, observable.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `AppSettings.openVideoInFullscreen` exists.
- [ ] Working tree is clean or on a feature branch.
- [ ] Re-read the current `PlayerActivity.kt` and `BrowseEventHandler.kt` before editing - both are large, frequently-touched shared files; do not trust line numbers from earlier research, use `Grep` to locate anchors.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified, >500 LOC - timestamped backup in `temp/` required before editing | ≤ 1500 |

---

## Steps

### Step 04.1 - Compute the gate decision in Browse and thread it through the intent builder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `BrowseEvent.NavigateToPlayer` branch of `handleEvent`, after `file` (the resolved `MediaFile?`) is available, compute:
>
> ```kotlin
> val enterFullscreenOnOpen = file?.type == MediaType.VIDEO &&
>     viewModel.settings.value.openVideoInFullscreen &&
>     viewModel.state.value.resource?.showCommandPanel == null
> ```
>
> `resource?.showCommandPanel == null` means "no explicit per-resource override saved yet" - an existing saved `true`/`false` for this resource must win, per strategic §5/§6 item 1. Pass `enterFullscreen = enterFullscreenOnOpen` to `createStandardPlayerIntent`. Add a matching `enterFullscreen: Boolean = false` parameter to the private `createStandardPlayerIntent` function and forward it into its `PlayerActivity.createPanelIntent(...)` call.

**Verification:**

- `Grep` - `enterFullscreenOnOpen` in `BrowseEventHandler.kt` matches (declaration and the `createStandardPlayerIntent` call site).
- `Grep` - `resource?.showCommandPanel == null` in `BrowseEventHandler.kt` matches once.
- `Grep` - `enterFullscreen: Boolean = false` present in the `createStandardPlayerIntent` function signature.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 3/3 PASS. Files: BrowseEventHandler.kt (+7 LOC).

---

### Step 04.2 - Extend createPanelIntent to accept and forward the flag

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add `enterFullscreen: Boolean = false` to `createPanelIntent`'s parameter list and pass it through to the `createIntent(...)` call it delegates to. `createIntent` already exposes an `enterFullscreen` parameter (currently only reached via the Streams launch path in `StreamsActivity.kt`, which is unaffected by this change - it keeps calling with `enterFullscreen = true` unconditionally).

**Verification:**

- `Grep` - `fun createPanelIntent\(` in `PlayerActivity.kt`, and within that function's body/signature `enterFullscreen` appears both as a parameter and in the delegating `createIntent(...)` call.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification PASS (confirmed via direct Read - enterFullscreen parameter added and forwarded to createIntent). Files: PlayerActivity.kt (+2 LOC).

---

### Step 04.3 - Generalize the launch-time fullscreen gate

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `initEnterFullscreenOnLaunch`, remove the `isStreamResource` re-derivation (`intent.getLongExtra("resourceId", 0L) == SyntheticResourceIds.STREAM`) and its `if (!isStreamResource) return` guard. Trust `EXTRA_ENTER_FULLSCREEN` directly - every producer of that extra (the Streams launch path, and now the Browse video-tap launch path from Step 04.1) already resolves the decision correctly before building the intent, so the resourceId re-check is now redundant rather than protective. Keep the `savedInstanceState != null` early-return (fresh launch only, so a process-death recreation does not re-force fullscreen after a manual exit) and the idempotent `viewModel.enterFullscreenMode()` call unchanged. Update the method's KDoc to describe both producers (S0694 streams, S0820 video-from-Browse) instead of only streams.

**Verification:**

- `Grep` - `isStreamResource` in `PlayerActivity.kt` returns zero hits.
- `Grep` - `if \(!intent.getBooleanExtra\(EXTRA_ENTER_FULLSCREEN, false\)\) return` still present inside `initEnterFullscreenOnLaunch`.
- `Grep` - `S0820` present in the KDoc comment directly above `initEnterFullscreenOnLaunch`.
- `Grep` - `viewModel.enterFullscreenMode\(\)` still present inside `initEnterFullscreenOnLaunch`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-02 - Verification 4/4 PASS. Files: PlayerActivity.kt (KDoc rewritten, isStreamResource guard removed, -3/+3 LOC net).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 d` -> BUILD SUCCESSFUL (1m 48s), APK produced.
- [x] `Grep` for `Log\.d\(` in both touched files returns zero hits.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" (batched, see below).
- [x] Manual confirmation: `StreamsActivity.kt`'s stream-open-to-fullscreen call site untouched - `enterFullscreen = true` unconditional call still present, verified via raw file read.
- [x] Detekt scoped check (`assert-detekt.ps1 -ChangedFiles`): found 1 real new finding (MaxLineLength on the S0820 comment in AppSettings.kt, >120 chars) - fixed by wrapping to two `//` lines, re-verified clean. Remaining flagged files (SettingsRepositoryImpl.kt, PlayerActivity.kt ImportOrdering; ImportSettingsUseCase.kt MaxLineLength on `resourceTypeTabCollapsed`; VideoSettingsFragment.kt ImportOrdering) are pre-existing baseline-signature-resurface noise - none of the flagged lines were touched by this ticket's diff (confirmed via grep/manual read); SettingsRepositoryImpl.kt/PlayerActivity.kt's import blocks were never edited by S0820, and the two MaxLineLength/ImportOrdering entries pre-date this change. Treated as advisory per CLAUDE.md dirty-tree closure policy (Rule 19/S0826) - not fixed, not this ticket's regression.

---

## Handoff Notes to Next Phase

The feature is functionally complete end to end: the Video settings toggle (Phase 03) now gates whether tapping a video file in Browse launches the player straight into fullscreen, honoring a per-resource saved override (Step 04.1) and the per-profile defaults from Phase 02. Final phase only documents and catalogs the shipped state.

---

## Rollback Plan

Medium risk if reverted alone (not the whole feature): reverting only Step 04.3 while Step 04.1 still sets `EXTRA_ENTER_FULLSCREEN=true` for ordinary videos would silently break the Streams fullscreen-on-open path too, since the guard it removes was shared logic. Revert all three steps of this phase together, or the whole feature.
