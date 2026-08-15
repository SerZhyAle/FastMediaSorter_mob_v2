# Phase 01 - A source seam behind the Now Playing gadget

**Strategic spec:** [`../S0429_home-screen-google-content.md`](../S0429_home-screen-google-content.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Put an interface between the gadget and where its data comes from, with the current own-session path as the only implementation. Behaviour is identical after this phase.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/NowPlayingSource.kt` | New | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/OwnSessionNowPlayingSource.kt` | New | ≤ 110 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AudioNowPlayingGadget.kt` | Modified | ≤ 160 |

> No layout is edited in this phase, so Rule 11 does not apply. Everything lives in `src/launcherEnabled`, the source set the gadget already lives in, so no flavor guard is needed anywhere (Rule 14).

---

## Steps

### Step 01.1 - Define the source contract

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/NowPlayingSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Declare `data class NowPlayingState(val active: Boolean, val title: String, val artist: String, val isPlaying: Boolean, val canControl: Boolean)` and `interface NowPlayingSource` with `fun read(): NowPlayingState` and `fun send(command: NowPlayingCommand): Boolean`, where `NowPlayingCommand` is an enum of `PREVIOUS`, `PLAY_PAUSE`, `NEXT`. `send` returns whether the command was delivered, because the gadget already hides its transport row when a send fails.

**Why:**

Strategic §5 item 4 states this ticket changes the gadget's data source and not its presentation, so the seam is the whole design: the gadget must stop naming `AudioNowPlayingSnapshotStore` and `AudioPlaybackService` before a second source can exist.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `interface NowPlayingSource` matches exactly once.
- `Grep` - `enum class NowPlayingCommand` present with three entries.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. Files: app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/NowPlayingSource.kt (new, 32 LOC). Closure deferred to the phase-level `post-change.ps1 -Files` run so the CODE.LOCK is held once for all three steps rather than re-queued between them (the queue wait was 848s).

---

### Step 01.2 - Move the current behaviour behind the contract

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/OwnSessionNowPlayingSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `class OwnSessionNowPlayingSource(private val context: Context) : NowPlayingSource`. `read()` maps `AudioNowPlayingSnapshotStore.read(context)` onto `NowPlayingState`, with `canControl = snapshot.active`. `send()` builds the same `AudioPlaybackService` intent the gadget builds today - `ACTION_WIDGET_COMMAND` plus `EXTRA_WIDGET_COMMAND` carrying the service's own `WIDGET_COMMAND_*` constant for the given `NowPlayingCommand` - starts it with `startService` inside `runCatching`, and returns false on failure. Keep the existing comment's reason for never calling `startForegroundService`.

**Why:**

Strategic §3.3 requires the gadget to keep showing the app's own session when notification access is refused, so this path is not a temporary shim - it is the permanent fallback the whole opt-in design leans on.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `AudioNowPlayingSnapshotStore` and `AudioPlaybackService` both present in that file.
- `Grep` - `startForegroundService(` returns zero call sites in that file. Tightened from the bare token on 2026-08-06: the same step's prompt requires keeping the comment that names `startForegroundService` as the thing never to call, so a bare-token predicate contradicted its own prompt. The call site is what the predicate was ever about.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS (`startForegroundService(` call sites: 0; `AudioNowPlayingSnapshotStore` and `AudioPlaybackService` both present). Files: app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/OwnSessionNowPlayingSource.kt (new, 42 LOC). Predicate reconciled with the prompt, see above. Closure deferred to the phase-level `post-change.ps1 -Files` run.

---

### Step 01.3 - Rewire the gadget onto the contract

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/AudioNowPlayingGadget.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `AudioNowPlayingGadgetView`, hold a `NowPlayingSource` constructed as `OwnSessionNowPlayingSource(context)` and have `render()` read from it and the three button listeners call `send()`. Delete the direct `AudioNowPlayingSnapshotStore` and `AudioPlaybackService` imports and uses from this file. Leave the polling loop, the interval, the body-tap command and every view binding exactly as they are.

**Why:**

The strategic spec's §5 item 3 records that the gadget's look, spans and control set are settled by S1170 and are not this ticket's to change - only where the data comes from is.

**Verification:**

- `Grep` - `AudioNowPlayingSnapshotStore` and `AudioPlaybackService` return zero hits in that file.
- `Grep` - `REFRESH_INTERVAL_MS = 2_000L` still present.
- `Grep` - `Log\.d\(` returns zero hits in every file this phase modified.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. `AudioNowPlayingSnapshotStore` 0 hits, `AudioPlaybackService` 0 hits, `REFRESH_INTERVAL_MS = 2_000L` still present, `Log.d(` 0 hits across all three files of this phase. `.\a.ps1 fk` exit 0. Files: AudioNowPlayingGadget.kt (121 -> 120 LOC). The gadget's class KDoc lost its `AudioPlaybackService` reference with the code, which the zero-hit predicate requires; `canControl` replaces `snapshot.active` as the transport-row gate and is defined as exactly that value in `OwnSessionNowPlayingSource`, so behaviour is unchanged as the phase objective demands.
- 2026-08-06 - `fk` reported `compileStandardDebugKotlin UP-TO-DATE`, which alone would not prove the change compiled. Verified directly instead: `built_in_kotlinc/standardDebug/compileStandardDebugKotlin/classes/.../nowplaying/` holds `NowPlayingSource`, `NowPlayingState`, `NowPlayingCommand` (17:38:13) and `OwnSessionNowPlayingSource` (17:39:51), and the gadget classes are re-emitted at 17:39:51 - a sibling session's gradle run compiled them while it held BUILD.LOCK, so the up-to-date verdict was legitimate.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, and the emitted classes were checked directly because the task reported UP-TO-DATE.
- [x] `Grep` for `TODO(phase-01)` returns zero hits in `app_v2/src` - the one match is this criterion's own text.
- [x] Dev log entry added for the phase via `post-change.ps1` - `post-change: PASS (Kotlin, 45501 ms)`, exit 0.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2481 records rendered by the facade's catalog-sync step.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See below.
- [x] `CODE.LOCK` released.

### Phase-boundary audit (Layer 1)

Layer 1 only, as the phase prescribes: no coroutine, lifecycle or Room surface changed - the polling loop, its interval and `onActive` are untouched.

- Layering holds: the two new classes sit in the UI layer beside the gadget they serve, and neither reaches past the seam it defines.
- Behaviour parity confirmed by reading, not by assumption: the transport row's gate moved from `snapshot.active` to `state.canControl`, and `OwnSessionNowPlayingSource` defines `canControl = snapshot.active`, so the rendered result is identical.
- Failure parity confirmed: the old code hid the row from `runCatching {}.onFailure`, the new one hides it when `send()` returns false, and `send()` returns `runCatching {}.isSuccess` - the same predicate, moved one call deeper.
- P3, not fixed here and not a defect: `AudioNowPlayingGadgetView`'s `onActive` KDoc still describes the snapshot as the thing being polled. True while the own-session source is the only one; Phase 02 makes the source a choice and owns that sentence.

---

## Handoff Notes to Next Phase

The gadget no longer knows where its data comes from. Phase 02 adds the second source and the rule that picks between them; nothing user-visible changes until then.

---

## Rollback Plan

Revert the phase commit - a pure extraction, no persisted state and no user-visible surface.
