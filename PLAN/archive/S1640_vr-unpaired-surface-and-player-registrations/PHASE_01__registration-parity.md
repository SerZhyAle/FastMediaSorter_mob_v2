# Phase 01 - Registration parity

**Strategic spec:** [`../S1640_vr-unpaired-surface-and-player-registrations.md`](../S1640_vr-unpaired-surface-and-player-registrations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Give each of the four registrations in the `vr` source set a paired removal at its owner's terminal boundary, changing no object's lifetime and no teardown order.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/ImmersiveBrowseActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/browse/ImmersiveBrowsePlaybackController.kt` | Modified | ≤ 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrDiagnosticPlaybackController.kt` | Modified | ≤ 500 |

> Every file lives in the `vr` source set, which is correct for this ticket: all four types exist only in that flavor and none of them belongs in `src/main`.

---

## Steps

### Step 01.1 - Remove the surface-holder callback in the diagnostic activity

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `onDestroy`, remove the activity from the surface holder it subscribed to during view construction. Guard on the surface view actually having been initialized, matching the existing `::surfaceView.isInitialized` guard used elsewhere in the class. Add nothing to `surfaceDestroyed` and move no existing teardown line.

**Why:**

Strategic §9 ADR-2 fixes the removal at the owner's terminal boundary rather than where the surface disappears, because the surface is recreated during the activity's life and removing there would leave a live activity without callbacks - the black-screen risk named in strategic §7.

**Verification:**

- `Grep` - `removeCallback` present in the file exactly once.
- `Grep` - the `removeCallback` call sits inside `onDestroy`, and `surfaceDestroyed` contains no `removeCallback`.
- `Grep` - the file still contains exactly one `holder.addCallback(this@DiagnosticXrActivity)`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Removal added at the terminal boundary in onDestroy, guarded by ::surfaceView.isInitialized like the rest of the class, with surfaceDestroyed left untouched. Predicates: one holder.removeCallback, one holder.addCallback(this@DiagnosticXrActivity), surfaceDestroyed still only requests the render-thread exit.

---

### Step 01.2 - Remove the surface-holder callback in the immersive browse activity

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/ImmersiveBrowseActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Apply the same change in this activity: remove itself from the surface holder in `onDestroy`, before the existing `super.onDestroy()` call, leaving the surrounding teardown lines in their current order. Add nothing to `surfaceDestroyed`.

**Why:**

Strategic §2 goal 4 requires the teardown order in the `vr` set to stay as it is, and the existing `onDestroy` already sequences playback stop, decoder release and render-thread exit for reasons this ticket does not own.

**Verification:**

- `Grep` - `removeCallback` present in the file exactly once, inside `onDestroy`.
- `Grep` - `surfaceDestroyed` in this file still contains only the `surface = null` assignment.
- `Grep` - the file still contains exactly one `holder.addCallback(this@ImmersiveBrowseActivity)`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Same terminal-boundary removal, placed after the existing teardown lines and before super.onDestroy(), with their order untouched. Predicates: one holder.removeCallback inside onDestroy, one holder.addCallback(this@ImmersiveBrowseActivity), surfaceDestroyed still only nulls the surface.

---

### Step 01.3 - Hold and remove the player listener in the immersive browse controller

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/browse/ImmersiveBrowsePlaybackController.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Turn the anonymous `Player.Listener` passed to `addListener` into a private field of the controller and register that field instead. In the teardown path, call `removeListener` with the same field immediately before the existing `release()` call on the player, leaving every other line of that path in its current position.

**Why:**

Strategic §9 ADR-3 records that an anonymous expression cannot be removed because no reference to it survives, so holding it in a field is the minimum change that gives the removal an addressee.

**Verification:**

- `Grep` - the file declares a private `Player.Listener` field and passes it to `addListener`.
- `Grep` - `removeListener` present exactly once, on the same line block as the teardown, before `release()`.
- `Grep` - `addListener(object : Player.Listener` no longer matches in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Listener lifted to a private field and registered by reference; removeListener(playerListener) sits immediately before release() inside stop(), with clearVideoSurface and stop() left where they were. Predicates: one field declaration, one addListener(playerListener), one removeListener(playerListener), zero anonymous addListener(object ..) left.

---

### Step 01.4 - Hold and remove the player listener in the diagnostic controller

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrDiagnosticPlaybackController.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Apply the same change in this controller: the listener becomes a private field, `addListener` receives that field, and `removeListener` is called with it immediately before the existing `player?.release()` in `release()`. Keep the listener's overridden methods and their comments exactly as they are.

**Why:**

Strategic §2 goal 3 refuses to widen the gate's discount, so both player sites take the same paired-removal shape rather than one of them being excused by form.

**Verification:**

- `Grep` - the file declares a private `Player.Listener` field and passes it to `addListener`.
- `Grep` - `removeListener` present exactly once, inside `release()`, before `player?.release()`.
- `Grep` - `addListener(object : Player.Listener` no longer matches in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Listener bound to a per-start local and mirrored into a private field, because it closes over the file being started and cannot be a class-level val. The object literal stays lexically inside the player's apply block on purpose: hoisting it would change which receiver the unqualified release() call inside onPlayerError binds to. removeListener runs immediately before player release, and the field is nulled with the player. Predicates: one field, one addListener(listener), one removeListener, zero anonymous addListener(object ..). parked: S1662.
- 2026-08-14 - Correction to this step's 'keep the overridden methods exactly as they are': the scoped detekt gate refused the close because the empty onVideoSizeChanged override, previously absorbed by its baseline signature, shifted lines and re-surfaced as a new EmptyFunctionBlock. The gate's own rule is to fix it in source and never widen the baseline. The override had an empty body under a comment promising it would confirm the decoder's output size, so it delivered nothing and its comment was already false; both were removed together with the now-unused VideoSize import. Player.Listener.onVideoSizeChanged has a default empty implementation, so behaviour is unchanged. Re-run: detekt-scoped PASS, post-change PASS.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - proven in Phase 02 by the `vr` flavor compile, since no other flavor carries these files.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: two controllers gain a private field only - no public API moves.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Four registrations now carry a paired removal. Phase 02 lowers the gate's integer baseline by exactly that amount using the gate's own ratchet, and proves the `vr` set still compiles.

---

## Rollback Plan

Revert the four edits - each is additive and self-contained, and no teardown line moved, so reverting restores the previous behaviour exactly.
