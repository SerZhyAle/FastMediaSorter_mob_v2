# Phase 03 - Measure what a probe costs, then fix the constants

**Strategic spec:** [`../S1511_stream-quality-rung-memory-and-probe-up.md`](../S1511_stream-quality-rung-memory-and-probe-up.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-13
**Completed:** 2026-08-13

---

## Objective

Answer strategic section 6 Q2 with a real measurement on a device, and write the resulting wait constants into the policy class.

---

## Prerequisites

- [x] Phase 02 is ✅ Done.
- [x] A device or emulator is attached and a multi-rendition HLS or DASH channel is reachable.

> **Setup notes gathered 2026-08-13, before the measurement starts.** Streams are gated on a runtime
> toggle as well as the compile-time capability: Settings -> Медиа -> Трансляции -> "Включить трансляции",
> which defaults off, and only then does the main menu carry "Ещё" -> "Трансляции". A reliable
> multi-variant HLS to add there is `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`. Confirm the
> ladder arrived by the existing `Stream quality: renditions=N` line before instrumenting anything.
>
> **The step has no trigger yet, and that is the part to plan first.** Nothing in the shipped build
> raises or lowers the ceiling on demand: the step-down needs two real stalls inside 120 s, and the probe
> path added in Phase 02 is not wired to the tick until Phase 04. So step 03.1 has to bring its own
> throwaway trigger - the ≤ 15 lines this phase budgets for `StreamPlaybackHelper.kt` - applying and then
> releasing a cap through the same `selector.setParameters(..)` the step-down path uses, with
> `SystemClock.elapsedRealtime()` logged at the apply and at the first frame reported on the new rung.
> Do not leave that harness in the tree across a session boundary.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt` | Modified | ≤ 10 |

---

## Steps

### Step 03.1 - Measure the gap a ceiling change actually causes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Log, at the existing ceiling-application site, the moment the new parameters are set and the moment the renderer next reports a frame at the new rung, using the frame counter the stats sampler already reads. Play a multi-rendition channel, force the ceiling down, then raise it, and record from logcat how long the picture is interrupted and whether the buffered data of the previous rung is discarded. Repeat on a channel whose upper rung is genuinely marginal, since a probe that always succeeds measures nothing. Remove the temporary logging before the phase closes, keeping only what is worth shipping.

**Why:**

Strategic section 6 Q2 records that the "a probe is nearly free for us" premise is verified only as far as the code showing no re-prepare, and that whether the switch discards the buffer or shows a visible gap is unmeasured - while the source measurement in section 0 shows a probe cadence chosen wrongly costs more than the feature returns.

**Verification:**

- The measured interruption, in milliseconds, is recorded in the phase's Handoff Notes below with the channel and conditions it was taken under.
- `Grep` - no temporary measurement logging remains in `StreamPlaybackHelper.kt` at phase close.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Measured on a Galaxy S21+ over Wi-Fi on the mux multi-variant HLS: releasing the ceiling costs 3.0-3.5 s with no rendered frame and 4.0 s to steady 30 fps, and the frame counter resets across the switch - the renderer is re-initialised despite no re-prepare. Stepping down costs 0.5-1.0 s. Figures and their two qualifications are in the phase Handoff Notes; the throwaway harness is removed (grep for qswitch/MEASURE_ returns 0).

---

### Step 03.2 - Fix the wait constants from that measurement

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamQualityStepDownController.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the placeholder wait constants with values derived from step 03.1, and state the derivation in a comment beside them the way `STALL_DECAY_WINDOW_MS` states its own. If the measured interruption turns out to be comparable to the source player's, adopt their arithmetic and say so; if it is materially cheaper, choose a shorter base and say by how much. Never copy the numbers without the measurement behind them.

**Why:**

Strategic section 11 criterion 7 requires the constants to come from our own measurement rather than being carried over from a player whose quality change costs a media re-open.

**Verification:**

- `Grep` - each new constant carries a comment naming the measurement that set it.
- `.\a.ps1 fu` - the controller suite still passes with the real constants in place.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - Probe wait constants now carry the derivation: the measurement is named beside them and the source player's arithmetic is adopted rather than shortened, because 3.0-3.5 s sits at the bottom of its 3-18 s rather than well under it. Controller suite re-run with the final constants: tests=26 failures=0 errors=0 at 21:45:08. StreamPlaybackHelper.kt ends this phase back at its pre-phase content - the harness was its only change - so it gets no dev-log row of its own.
- 2026-08-13 - Phase-boundary audit (Layer 1 only - the phase's net code change is a comment block on constants, and StreamPlaybackHelper.kt is back at its pre-phase content). No P0/P1. One finding worth carrying: the measurement makes phase 04 step 04.3 more consequential than the plan assumed, because a probe now costs the viewer 3.0-3.5 s rather than being near-free, so the tick path must not be able to start one while the user is already suffering - phase 04 should confirm the probe cannot open during an active stall cluster.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Strategic section 6 Q2 is updated from `Open` to `Resolved` with the measured figure.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

**Measured 2026-08-13 on a Galaxy S21+ (SM-G996U1, Android 15, SDK 35) over home Wi-Fi, on
`https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` - a healthy multi-variant HLS whose top rung the line
carried without trouble.** Instrumentation: the throwaway harness described above, sampling every 500 ms and
logging `SystemClock.elapsedRealtime()`, the renderer's frame counter and the current format height.

**Going up costs about 3 seconds of no picture at all, which refutes the premise this ticket was built on.**
The ceiling was released at `at=214055075`; the first rendered frame on the new rung appeared between
`at=214058105` (frames still 0) and `at=214058610` (frames=4), so the interruption is **3.0-3.5 s**, and the
steady 30 fps returned at `at=214059113`, **4.0 s** after the switch. The frame counter reset to 0 across the
switch, which says the renderer is re-initialised even though no `prepare` is re-issued - the part the
strategic spec had verified only as far as "no re-prepare in the code".

Going down is much cheaper: capped at `at=214024809`, the new rung was already reported at `+505 ms` and
frames were flowing by `+1007 ms` - **0.5-1.0 s**.

Two qualifications, both making the upward figure an **upper bound** rather than a verdict:

- The harness released the cap to unrestricted, so ABR jumped two rungs to 1080 (`h=1080`, frames=0 for three
  samples), found it undeliverable, passed through `h=0`, and settled on 720. A real probe raises the ceiling
  by exactly one rung (`startProbe`), which cannot overshoot this way.
- One channel, one network, one device. The marginal-channel repeat the step also asks for was not produced -
  see the deferred item below.

**Consequence for the constants:** our probe is *not* materially cheaper than the source player's. Its
measured 3-18 s black screen brackets our 3.0-3.5 s at the bottom, so the arithmetic the source derived -
5 min base, doubling per recorded failure of that rung, 1 h cap - is adopted rather than shortened. Their own
figures make the case for keeping it long: a 60 s base cost them 108.9 s of black screen per session against
36.0 s at a 5 min base.

**Deferred, needs a human:** the repeat on a channel whose upper rung is genuinely marginal. Producing one
needs a shaped or congested link, which cannot be arranged on the owner's phone from here. What it would add
is confidence in the *failure* path's cost, not in the number above - a probe that fails is a probe whose cost
we have already measured, plus the step back down at 0.5-1.0 s.

---

## Rollback Plan

Revert phase commit(s) - constants return to their placeholders and no shipped behaviour changes, since nothing is wired until Phase 04.
