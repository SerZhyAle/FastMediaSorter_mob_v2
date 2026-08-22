# S1664 research 01 - the vanishing row is a collapsed section with no visible state

**Date:** 2026-08-15
**Device:** RFCR110NBQJ (Samsung SM-G996U1, Android 15), standard-debug, launcher mode on
**Verdict:** root cause confirmed by direct measurement and reproduced on demand.

---

## 1. What the two earlier passes established

**Pass 1 (2026-08-14, code reading).** The only difference between the resting render path and the
edit-mode render path is the folded-section set: edit mode draws every section expanded, rest applies
the persisted collapsed set. The row arithmetic drops a cell only when its stored row lies inside a
folded section. That pass could not decide between "the whole section is hidden" and "only the gadget
views fail to build", and named the decisive measurement: does the resting tree still contain the
shortcut cells of the same section.

**Pass 2 (2026-08-15, device).** Could not reproduce, and could not take the decisive measurement:
`uiautomator dump` never completes on this screen (`ERROR: could not get idle state`) because the clock
gadget repaints once a second, so the accessibility idle window never opens. Logs around the "Done" tap
were clean - no gadget-registry line, no exception.

Neither pass read the persisted state. That is what this pass did instead, and it settled the question
in two commands.

## 2. The decisive measurement - persisted collapse state

The launcher persists section collapse into the app-wide collapsible-section preferences file, keyed by
orientation plus the section's stable target. Read straight off the device:

```
run-as com.sza.fastmediasorter.debug cat shared_prefs/collapsible_sections_state.xml
```

Relevant entries as found, before anything in this session touched the device:

- `launcher_desktop__LANDSCAPE__sec:everything_else` = `true`
- `launcher_desktop__PORTRAIT__sec:app_functions` = `false`

Two facts follow, and both are mechanical rather than interpretive.

**A key exists only because the toggle ran.** Reading the state never writes it - the default is
"expanded" and is answered from the default, not from the file. So a `launcher_desktop__*` entry is
proof that the header's toggle fired at least once on that section, in that orientation.

**The landscape entry is proof the reported section was folded and later unfolded.** Its value is
`true` (expanded), so the toggle ran an even number of times on `sec:everything_else` in landscape - at
minimum collapsed once and expanded once. `sec:everything_else` is "Всё остальное", the exact section
whose row vanished in the 2026-08-14 report.

**The portrait entry is a live, unnoticed instance of the same defect.** `sec:app_functions`
("Функции приложения") is collapsed right now on the owner's device, and nothing on screen says so.

## 3. Reproduction on demand

Deterministic, two taps, on a device with launcher mode already on. Screenshots were taken at each step
during the 2026-08-15 pass but are deliberately not the audit trail - the commands below reproduce the
verdict on their own, and a disposable path could not carry it (Rule 1).

1. Open the desktop:
   `am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity`
2. Read the persisted state:
   `run-as com.sza.fastmediasorter.debug cat shared_prefs/collapsible_sections_state.xml`
   Expected: no `launcher_desktop__PORTRAIT__sec:everything_else` entry, or one reading `true`, and the
   desktop showing the clock, the search bar, the weather card and the icon rows beneath them.
3. Tap the section header once - portrait, `(400, 205)` on a 1080x2400 screen.
   Expected **before the fix**: the desktop is bare wallpaper. The gadget row is gone, every icon is
   gone, the second header ("Функции приложения") is uncovered and itself collapsed, and nothing on
   screen says either section is folded - two bold captions with an underline each.
   Expected **after the fix**: the same content is hidden, and each caption now carries a chevron turned
   to the collapsed angle, so the empty desktop reads as two folded sections rather than as loss.
   Either way the same `cat` now prints that key with value `false`, which is the mechanical proof the
   toggle - and nothing else - produced the state.
4. Tap the same header again.
   Expected: the content returns exactly as it was, and the key reads `true` again.

Step 3 before the fix is the whole ticket: the screen is indistinguishable from "the app lost my
desktop". The device was left in the state it was found in - `sec:app_functions` stays collapsed in
portrait, landscape untouched.

## 4. Why it reads as "after leaving edit mode"

Folding is deliberately suppressed while arranging - edit mode draws every section expanded, because a
drop maps a pixel row straight to a stored row and folding mid-edit would land each dragged cell off by
the folded height. The consequence is that a fold made at any time is invisible for as long as the user
stays in edit mode, and appears the instant they leave it. The user therefore attributes the loss to
"Done", which is where the ticket title came from, and the resize is a coincidence of timing rather than
the cause.

That also explains why pass 2 failed to reproduce: it varied the resize, which is not the input that
matters.

## 5. What is proven and what is not

**Proven:** the row disappears because its section is folded; the fold is persisted per orientation, so
it survives a force-stop; edit mode ignores folds, so re-entering shows everything intact; the header
carries no visible indication of the folded state; a single tap on a full-width strip enters that state
with no confirmation and no undo affordance.

**Not proven:** the exact input that fired the toggle on 2026-08-14. The header is the largest tap
target on the desktop - full row width, at least 48dp tall, with a touch ripple and no icon - so a stray
tap at rest is the plausible candidate, but the evidence cannot distinguish it from any other route to
the same toggle. It does not need to: every candidate produces the same state, and a visible state plus
a discoverable way back neutralises all of them.

**Closed by reading the code, not by guessing:** the header is covered by the edit-mode touch scrim, so
it cannot be toggled while arranging; a gadget cannot be resized or moved across a header row, because
both the resize and the move paths refuse a footprint that straddles one.

## 6. The convention this violates

The app already ships a canonical collapsible header used by settings and similar sections. It carries a
chevron that turns with the state, and a summary line meant for exactly the collapsed case. The launcher
desktop's own section header has neither - only a bold caption and a rule beneath it - so the folded
state reaches the user through nothing but a screen-reader state description.

A sighted user is given no signal at all.
