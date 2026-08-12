---
name: avd-evidence-traps-width-and-logs
description: Two traps that produce false negatives when device-testing on the AVD - the native shape leaves no spare width in the main command bar, and a -Tail logcat harvest can look like a missing log line
metadata:
  type: feedback
---

Two ways the emulator produced evidence that looked like a defect and was not, both learned on S1443 (2026-08-08).

**1. At the AVD's native shape the main-screen command bar is already full, so any "use the spare width" feature reads as broken.**
Measured on emulator-5554 with `wm size 2400x1080` at the native density 420 (= 914dp wide): `available=2400 needed=2375 free=25`. The nine labelled commands consume the row almost exactly, so a feature that only acts on leftover width correctly does nothing, and the screenshot looks identical to the unfixed build.

**Why:** the owner reports spare space in that row on his own large device; the AVD is near-square and its default density makes the labelled buttons proportionally huge. The absence of the effect is a property of the test shape, not of the code.

**How to apply:** to get real headroom, reshape AND drop the density: `wm size 2400x1080` + `wm density 240` gives 1600dp wide, where the same build measured `available=2400 needed=1295 free=1105` and the feature fired. Keep `wm size 1400x1080` at density 240 in the back pocket - it yields a small positive free width (105px) and is the cheapest way to exercise the *fallback* branch without faking anything. Reset with `wm size reset; wm density reset`. Never rotate - see [[reference_emulator_capture_family_testing]].

**2. A `-Tail N` logcat harvest can show a probe line at startup and none afterwards, which reads as "the callback never fired".**
On S1443 the first sweep reported that no probe logged at the moment of the user action, which pointed straight at broken wiring. The wiring was fine; the harvest was the problem.

**Why:** the capture window and the app's chattiness interact in ways that are not obvious from the returned text, and a report that says "found the early lines, none later" invites exactly the wrong conclusion.

**How to apply:** when a probe tag is the evidence, filter at capture time - `logcat -d | grep <tag>` through `adb.ps1 shell -Cmd` - never a fixed `-Tail`. Tell a delegated device operator this explicitly in the brief; they will otherwise reach for `adb.ps1 log -Tail N`. Before treating a missing probe line as a wiring defect, re-capture unfiltered-by-count at least once.
