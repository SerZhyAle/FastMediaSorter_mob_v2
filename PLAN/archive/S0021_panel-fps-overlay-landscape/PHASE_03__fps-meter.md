# Phase 03 — FPS Meter

**Strategic spec:** [`../S0021_panel-fps-overlay-landscape.md`](../S0021_panel-fps-overlay-landscape.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent of Phase 01/02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** —
**Completed:** —

---

## Objective

Introduce a `PlayerFpsMeter` helper that emits a `StateFlow<Int>` of frames-per-second based on `Choreographer.FrameCallback`. No overlay UI yet; just the data source.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFpsMeter.kt` | New | ≤ 120 |

---

## Steps

### Step 03.1 — Create `PlayerFpsMeter` class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFpsMeter.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file `PlayerFpsMeter.kt` in `ui/player/helpers/`. Class signature:
>
> ```kotlin
> class PlayerFpsMeter {
>     private val _fps = MutableStateFlow(0)
>     val fps: StateFlow<Int> = _fps.asStateFlow()
>     fun start()
>     fun stop()
> }
> ```
>
> `start()` registers a `Choreographer.FrameCallback` that increments a `frameCount` and on every 500 ms (compared with `SystemClock.elapsedRealtime()`) writes `(frameCount * 1000f / elapsedMs).toInt()` to `_fps` and resets the counter. `stop()` removes the callback. The class must be safe to call `start()`/`stop()` repeatedly.
>
> Logging: emit one `Timber.v("PlayerFpsMeter: started")` / `stopped` log line per state change. No `Log.d()`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerFpsMeter.kt` exists.
- `Grep` — `class PlayerFpsMeter` matches exactly once.
- `Grep` — `Choreographer.FrameCallback` matches at least once.
- `Grep` — `MutableStateFlow\(0\)` matches at least once.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

---

### Step 03.2 — Add unit-test-friendly start/stop guard

**Files:** `PlayerFpsMeter.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add an `internal var running: Boolean = false` field and have `start()` early-return if `running == true`; have `stop()` early-return if `running == false`. This makes the class re-entrant-safe and lets future tests assert state without race.

**Verification:**

- `Grep` — `internal var running: Boolean` matches exactly once in `PlayerFpsMeter.kt`.
- `Grep` — `if \(running\)` or `if \(!running\)` matches at least 2 times.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `standard debug`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for `PlayerFpsMeter.kt`.

---

## Handoff Notes to Next Phase

Phase 04 instantiates `PlayerFpsMeter` in `PlayerActivity`, calls `start()`/`stop()` per playback state, binds the `fps` flow to a TextView in the overlay.

---

## Rollback Plan

Delete the new file. No other files touched.
