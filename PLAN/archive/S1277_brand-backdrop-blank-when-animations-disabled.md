# S1277 - The brand backdrop goes blank after a rotation when system animations are off

**Status:** Archived
**Priority:** 35

## 0. Raw capture

Found while implementing S1234 (brand animation behind the welcome pages), 2026-07-29, on emulator-5556 / API 36.

Measured, not inferred:

- With `animator_duration_scale = 1.0` the backdrop animates correctly and survives rotation untouched.
- With all three animation scales at `0` - Android's "Remove animations" accessibility setting, and the default state of both project AVDs - the first render still paints one frozen frame, which looks fine.
- After a rotation in that state the backdrop is **pure black** for the rest of the session.

## 1. Mechanism

`ui/player/helpers/AudioWaveParticleView.kt`:

- The whole drawing loop is a `ValueAnimator`; `onDraw` only blits the off-screen bitmap the animator's tick maintains.
- `onSizeChanged` re-allocates that bitmap for the new size and fills it with `Color.BLACK`.
- With animations disabled the animator has already ended, so nothing repaints the freshly-blacked buffer.

Confirmed the obvious workaround does **not** work: calling `startAnimation()` (with or without `stopAndReset()` first) from the host's `onLayoutConfigurationChanged` leaves the screen black. A zero-duration `ValueAnimator` restarted this way produces no update callback at all, so no frame is drawn. That dead end was tried, measured, and reverted - do not retry it.

## 2. Why it needs its own ticket

The fix belongs inside the view, not in its hosts: it needs to render one frame synchronously when the animator cannot be relied on - roughly, run the tick body once directly and `invalidate()`, either from `onSizeChanged` or behind a "static frame" path chosen when `ValueAnimator.areAnimatorsEnabled()` is false.

That touches a view shared by three surfaces (audio player, launcher desktop, welcome), so it needs its own verification pass rather than riding along with a placement ticket.

## 3. Scope

- **Affects:** the audio player backdrop, the launcher desktop wallpaper (**S1101**), the welcome pages (**S1234**) - anywhere `AudioWaveParticleView` is hosted.
- **Only when:** the user has turned system animations off. With animations on there is nothing to fix.
- **Severity:** low. Affected users have explicitly asked for no animation; they get a flat background instead of a still image. Text stays legible because it sits on its own panel.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1234 (discovered here), S1101 (launcher desktop hosts the same view), S1227 (none - unrelated)

## 3.5 Fix decided 2026-07-31 (/spec-next round 4)

Render one frame synchronously from `onSizeChanged` when the animator cannot be relied on, exactly
as §2 proposed. Two details settled while reading the view:

- **How many passes.** One pass is not enough. The look is built by accumulation: every tick lays a
  semi-transparent black overlay over the buffer and draws on top, and the first
  `STARTUP_RAMP_FRAMES` ticks additionally ramp amplitude and alpha from 35 % to full. A single
  pass would leave a nearly empty buffer that reads as broken in a different way. The static path
  therefore runs the ramp to completion, producing the frame the animator would have reached.
- **How to detect.** `ValueAnimator.areAnimatorsEnabled()` is the official signal and exists in the
  compile SDK (verified with `javap`), but it arrived in API 26 and the `legacy` flavor builds
  against minSdk 23. Below 26 the same state is read from `Settings.Global.ANIMATOR_DURATION_SCALE`,
  which is the value §0 measured in the first place.

Not retried: restarting the animator from the host, which §1 already measured as producing no
update callback at all.

## 3.6 On-device verification 2026-07-31

Run on `emulator-5556`, API 33, standard debug `v2.60.7262.102`, welcome host.

The three animation scales were **set to 0 explicitly**, not assumed. §0 records 0 as the AVD
default; the live device answered `1.0` on all three, so the broken state was established by hand -
which is the stricter check anyway.

Sequence: scales to 0 -> stop -> clear data -> launch -> screenshot -> rotate to landscape ->
screenshot -> read the app's own logcat by pid. Scales and rotation restored afterwards.

Result - the causal chain is complete, not just the outcome:

```
11:38:14.386 D BaseActivity: onConfigurationChanged: WelcomeActivity, orientation=LANDSCAPE, screenWidthDp=866
11:38:14.427 D AudioWaveParticleView: S1277: animators off, painting static backdrop 2400x1080
```

The rotation fired, the new detector answered "animators off", and the static path painted at the
new size 41 ms later. The landscape screenshot shows the wave-and-particle backdrop, not the pure
black §0 measured. The probe appears three times in one session, matching the two call sites plus
the initial layout.

Artifacts: `temp/S1277/01-portrait-animations-off.png`,
`temp/S1277/02-landscape-after-rotation.png`.

**Not observed.** Only the welcome host was driven. The audio player backdrop and the launcher
desktop wallpaper (§3) host the same view and reach the fix through the same two call sites inside
it, but they were not opened. The fix is inside the view, so host code is not involved - that is
reasoning, not observation, and is recorded as such.

Found while doing this: `adb.ps1 log -Grep` reports `OK 0 line(s)` for a probe that is present in
the capture file it just wrote, because it filters by package name before applying the grep and a
Timber tag is a class name. Parked as **S1332** - it affects every `BlockNeedUserTest` verification,
not this ticket alone. The evidence above was read with `logcat --pid` instead.

## 4. Related

- **S1234** - discovered here; its spec records the full measurement and the reverted dead end.
- **S1101** - the launcher desktop, same view, same condition.
